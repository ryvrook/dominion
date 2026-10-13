package dev.ryvrook.dominion.export;

import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared territory and point data used by the snapshot and the public territory provider. */
final class MapData {
    /** One faction's land in one world. */
    record Zone(Faction faction, long[] chunks, long[] breached, boolean raidable) {}

    private final DominionContext ctx;

    MapData(DominionContext ctx) {
        this.ctx = ctx;
    }

    /** Every world that is loaded or holds claims. */
    Set<String> worlds() {
        Set<String> out = new LinkedHashSet<>();
        try {
            out.addAll(Universe.get().getWorlds().keySet());
        } catch (Exception ignored) {
            // universe not ready yet
        }
        out.addAll(ctx.repo().claimWorlds());
        return out;
    }

    List<Siege> activeSieges() {
        SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
        if (sieges == null) {
            return List.of();
        }
        try {
            return sieges.active();
        } catch (Exception e) {
            return List.of();
        }
    }

    List<Zone> zones(String world) {
        return zones(world, new java.util.HashMap<>());
    }

    /**
     * Zones of a world. Raid state is the costliest part, so callers that walk every world pass one
     * map to compute it once per faction.
     */
    List<Zone> zones(String world, Map<UUID, Boolean> raidableMemo) {
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn(world);
        Map<UUID, List<Long>> byFaction = new LinkedHashMap<>();
        for (Long2ObjectMap.Entry<ClaimEntry> e : claims.long2ObjectEntrySet()) {
            byFaction.computeIfAbsent(e.getValue().factionId(), k -> new ArrayList<>()).add(e.getLongKey());
        }
        Map<UUID, Set<Long>> breached = new LinkedHashMap<>();
        for (Siege s : activeSieges()) {
            if (s.phase() == Siege.Phase.BREACHED && world.equals(s.world())) {
                // A breach opens the target chunk and the neighbours the defender owns.
                Set<Long> set = breached.computeIfAbsent(s.defender(), k -> new LinkedHashSet<>());
                set.add(s.chunkIndex());
                for (long n : Keys.neighbours(s.chunkIndex())) {
                    ClaimEntry c = claims.get(n);
                    if (c != null && c.factionId().equals(s.defender())) {
                        set.add(n);
                    }
                }
            }
        }
        ClaimService claimService = ctx.serviceOrNull(ClaimService.class);
        List<Zone> out = new ArrayList<>();
        for (Map.Entry<UUID, List<Long>> e : byFaction.entrySet()) {
            Faction f = ctx.repo().faction(e.getKey());
            if (f == null) {
                continue;
            }
            boolean raidable = raidableMemo.computeIfAbsent(f.id(), id -> {
                if (f.type() != FactionType.NORMAL || claimService == null) {
                    return false;
                }
                try {
                    return claimService.raidable(f);
                } catch (Exception ignored) {
                    return false;
                }
            });
            out.add(new Zone(f, toArray(e.getValue()), toArray(breached.getOrDefault(f.id(), Set.of())), raidable));
        }
        return out;
    }

    /** Homes, warps and siege banners in the world. Consumers filter homes and warps by viewer. */
    List<PointOfInterest> points(String world) {
        List<PointOfInterest> out = new ArrayList<>();
        for (Faction f : ctx.repo().factions()) {
            Location home = f.home();
            if (home != null && world.equals(home.world())) {
                out.add(new PointOfInterest("home:" + f.id(), "home", f.tag() + " home", world,
                        home.x(), home.y(), home.z(), f.id(), f.color(), 0L));
            }
            for (Map.Entry<String, Faction.Warp> w : f.warps().entrySet()) {
                Location l = w.getValue().location();
                if (l != null && world.equals(l.world())) {
                    out.add(new PointOfInterest("warp:" + f.id() + ":" + w.getKey(), "warp", w.getKey(), world,
                            l.x(), l.y(), l.z(), f.id(), f.color(), 0L));
                }
            }
        }
        for (Siege s : activeSieges()) {
            if (!world.equals(s.world())) {
                continue;
            }
            Faction attacker = ctx.repo().faction(s.attacker());
            Faction defender = ctx.repo().faction(s.defender());
            String label = (attacker == null ? "Unknown" : attacker.tag()) + " besieging "
                    + (defender == null ? "unknown" : defender.tag());
            out.add(new PointOfInterest("siege:" + s.id(), "siege", label, world, s.x(), s.y(), s.z(),
                    s.attacker(), attacker == null ? "#ff6b6b" : attacker.color(), s.phaseEndsAt()));
        }
        // KOTH hills, outposts and envoys from the events module.
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events != null) {
            try {
                List<PointOfInterest> extra = events.points(world);
                if (extra != null) {
                    for (PointOfInterest p : extra) {
                        if (p != null) {
                            out.add(p);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // a failing events module must not break the map
            }
        }
        return out;
    }

    private static long[] toArray(Collection<Long> values) {
        long[] out = new long[values.size()];
        int i = 0;
        for (long v : values) {
            out[i++] = v;
        }
        return out;
    }
}
