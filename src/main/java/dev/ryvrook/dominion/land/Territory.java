package dev.ryvrook.dominion.land;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Watches every online player's chunk for territory banners, enter events and auto claim. */
final class Territory {
    private record Spot(String world, long chunk, UUID owner) {}

    private final Land land;
    private final ClaimServiceImpl claims;
    private final Map<UUID, Spot> last = new ConcurrentHashMap<>();
    private final Map<UUID, String> autoFailed = new ConcurrentHashMap<>();

    Territory(Land land, ClaimServiceImpl claims) {
        this.land = land;
        this.claims = claims;
    }

    void tick() {
        Universe u = Universe.get();
        if (u == null) {
            return;
        }
        for (PlayerRef p : new ArrayList<>(u.getPlayers())) {
            try {
                visit(p);
            } catch (RuntimeException e) {
                land.ctx.logger().at(Level.FINE).withCause(e).log("Territory check failed for %s", p.getUsername());
            }
        }
    }

    private void visit(PlayerRef p) {
        UUID id = p.getUuid();
        String world = Players.worldName(p);
        long chunk = Players.chunkOf(p);
        if (world == null || chunk == Long.MIN_VALUE) {
            return;
        }
        Spot prev = last.get(id);
        if (prev != null && prev.world().equals(world) && prev.chunk() == chunk) {
            return;
        }
        PlayerData pd = land.repo().playerIfLoaded(id);
        if (pd != null && pd.autoClaim()) {
            autoClaim(p, world, chunk);
        }
        Faction owner = claims.ownerAt(world, chunk);
        UUID ownerId = owner == null ? null : owner.id();
        last.put(id, new Spot(world, chunk, ownerId));
        UUID prevOwner = prev == null ? null : prev.owner();
        boolean worldChanged = prev == null || !prev.world().equals(world);
        if (!worldChanged && Objects.equals(prevOwner, ownerId)) {
            return;
        }
        if (prev == null && owner == null) {
            return;
        }
        land.publish(new DominionEvent.PlayerEnteredTerritory(id, world, prevOwner, ownerId));
        banner(p, owner, world, chunk);
    }

    private void autoClaim(PlayerRef p, String world, long chunk) {
        UUID id = p.getUuid();
        Faction own = land.factionOf(id);
        Faction here = claims.ownerAt(world, chunk);
        if (own != null && here != null && own.id().equals(here.id())) {
            return;
        }
        Result r = claims.claim(id, world, chunk);
        if (r.success()) {
            autoFailed.remove(id);
            return;
        }
        String key = world + "|" + chunk;
        if (!key.equals(autoFailed.put(id, key))) {
            Msg.send(p, Msg.bad(r.message()));
        }
    }

    private void banner(PlayerRef p, Faction owner, String world, long chunk) {
        if (!land.settings().bool("land.territoryBanners")) {
            return;
        }
        UiService ui = land.ctx.serviceOrNull(UiService.class);
        if (ui == null) {
            return;
        }
        String title = owner == null ? "Wilderness" : owner.name();
        String subtitle = subtitle(p.getUuid(), owner, world, chunk);
        String color = land.colorFor(p.getUuid(), owner);
        Runnable show = () -> {
            try {
                ui.territoryBanner(p, title, subtitle, color);
            } catch (RuntimeException e) {
                land.ctx.logger().at(Level.FINE).withCause(e).log("Territory banner failed");
            }
        };
        World w = Players.world(p);
        if (w != null) {
            w.execute(show);
        } else {
            show.run();
        }
    }

    private String subtitle(UUID viewer, Faction owner, String world, long chunk) {
        if (owner == null) {
            return "Unclaimed land";
        }
        List<String> flags = new ArrayList<>();
        if (owner.type() == FactionType.SAFEZONE) {
            flags.add("SafeZone");
        } else if (owner.type() == FactionType.WARZONE) {
            flags.add("WarZone");
        } else {
            if (claims.raidable(owner)) {
                flags.add("Raidable");
            }
            Faction own = land.factionOf(viewer);
            if (own != null && claims.breached(world, chunk, own.id())) {
                flags.add("Breached");
            }
        }
        String desc = owner.description() == null ? "" : owner.description().trim();
        String flagText = String.join(", ", flags);
        if (flagText.isEmpty()) {
            return desc;
        }
        return desc.isEmpty() ? flagText : desc + " (" + flagText + ")";
    }

    /** Forces a fresh banner on the next tick, for example after the player changes faction. */
    void reset(UUID player) {
        last.remove(player);
        autoFailed.remove(player);
    }
}
