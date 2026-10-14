package dev.ryvrook.dominion.faction;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Custom rank names and zones inside a faction's claims. Renaming ranks and creating, deleting or
 * assigning zones needs the SETTINGS permission. Editing or resetting a zone's permission grid needs
 * the PERMS permission, the same as the faction grid.
 */
final class Zones {
    static final int RANK_NAME_MAX = 16;
    static final int ZONE_NAME_MAX = 24;
    static final int SLUG_MAX = 16;
    static final int ZONE_LIMIT = 20;

    private final FactionSupport s;

    Zones(FactionSupport s) {
        this.s = s;
    }

    Result setRankName(UUID actor, Rank rank, String name) {
        if (rank == null) {
            return Result.fail("That is not a rank");
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > RANK_NAME_MAX) {
            return Result.fail("Rank names must be 1 to " + RANK_NAME_MAX + " characters long");
        }
        String problem = TagRules.text(clean, RANK_NAME_MAX, "Rank names");
        if (problem != null) {
            return Result.fail(problem);
        }
        for (Rank other : Rank.values()) {
            if (other != rank && f.rankName(other).equalsIgnoreCase(clean)) {
                return Result.fail("Another rank is already called " + clean);
            }
        }
        String old = f.rankName(rank);
        // Using the built in name clears the custom one.
        f.setRankName(rank, clean.equals(rank.displayName()) ? null : clean);
        s.audit(f, actor, "rank", "Renamed the " + old + " rank to " + clean);
        s.changed(f);
        return Result.pass("The " + old + " rank is now called " + clean);
    }

    Result create(UUID actor, String name) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > ZONE_NAME_MAX) {
            return Result.fail("Zone names must be 1 to " + ZONE_NAME_MAX + " characters long");
        }
        String problem = TagRules.text(clean, ZONE_NAME_MAX, "Zone names");
        if (problem != null) {
            return Result.fail(problem);
        }
        String base = slug(clean);
        if (base.isEmpty()) {
            return Result.fail("Zone names need at least one letter or digit");
        }
        Zone zone;
        synchronized (f) {
            List<Zone> zones = f.zones();
            if (zones.size() >= ZONE_LIMIT) {
                return Result.fail("Your faction already has " + ZONE_LIMIT + " zones");
            }
            for (Zone z : zones) {
                if (z.name().equalsIgnoreCase(clean)) {
                    return Result.fail("Your faction already has a zone called " + z.name());
                }
            }
            String id = base;
            for (int n = 2; f.zone(id) != null; n++) {
                String suffix = "-" + n;
                id = base.substring(0, Math.min(base.length(), SLUG_MAX - suffix.length())) + suffix;
            }
            zone = new Zone(id, clean);
            f.putZone(zone);
        }
        s.audit(f, actor, "zone", "Created the zone " + clean);
        s.changed(f);
        return Result.pass("Created the zone " + clean + " with id " + zone.id());
    }

    Result delete(UUID actor, String zoneRef) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        Zone zone = find(f, zoneRef);
        if (zone == null) {
            return Result.fail("Your faction has no zone called " + zoneRef);
        }
        f.removeZone(zone.id());
        int cleared = 0;
        for (String world : new ArrayList<>(s.ctx.repo().claimWorlds())) {
            for (Long2ObjectMap.Entry<ClaimEntry> e : s.ctx.repo().claimsIn(world).long2ObjectEntrySet()) {
                ClaimEntry c = e.getValue();
                if (!f.id().equals(c.factionId()) || !zone.id().equals(c.zoneId())) {
                    continue;
                }
                // Only rewrite the entry when nothing replaced it since the snapshot.
                if (s.ctx.repo().replaceClaim(world, e.getLongKey(), c,
                        new ClaimEntry(c.factionId(), c.claimedAt(), c.claimedBy(), null))) {
                    cleared++;
                }
            }
        }
        s.audit(f, actor, "zone", "Deleted the zone " + zone.name() + " and returned " + cleared
                + (cleared == 1 ? " chunk" : " chunks") + " to the faction rules");
        s.changed(f);
        return Result.pass("Deleted the zone " + zone.name());
    }

    Result assign(UUID actor, String zoneRef) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        PlayerRef ref = FactionSupport.liveRef(actor);
        if (ref == null) {
            return Result.fail("You need to be online to assign a zone");
        }
        return assignAt(actor, f, Players.worldName(ref), Players.chunkOf(ref), zoneRef);
    }

    /** Runs between reading the chunk and writing it. Tests use it to change the chunk in between. */
    Runnable beforeWrite = () -> { };

    /** Assigns a known chunk of the actor's faction to a zone, or clears it when zoneRef is blank. */
    Result assignAt(UUID actor, Faction f, String world, long chunk, String zoneRef) {
        ClaimEntry c = world == null ? null : s.ctx.repo().claimAt(world, chunk);
        if (c == null || !f.id().equals(c.factionId())) {
            return Result.fail("Stand in your own faction's land to assign a zone");
        }
        Zone zone = null;
        if (zoneRef != null && !zoneRef.isBlank()) {
            zone = find(f, zoneRef);
            if (zone == null) {
                return Result.fail("Your faction has no zone called " + zoneRef);
            }
        }
        String newId = zone == null ? null : zone.id();
        if (java.util.Objects.equals(newId, c.zoneId())) {
            return Result.fail(zone == null ? "This chunk is not in a zone" : "This chunk is already in " + zone.name());
        }
        beforeWrite.run();
        // A claim made on another thread may have replaced the entry since it was read. Writing the
        // old owner back would undo that claim, so the write only happens while the entry is unchanged.
        ClaimEntry updated = new ClaimEntry(c.factionId(), c.claimedAt(), c.claimedBy(), newId);
        if (!s.ctx.repo().replaceClaim(world, chunk, c, updated)) {
            return Result.fail("This chunk changed hands a moment ago. Try again");
        }
        f.touchZones();
        String where = Keys.describe(chunk) + " in " + world;
        String text = zone == null ? "Removed chunk " + where + " from its zone" : "Added chunk " + where + " to the zone " + zone.name();
        s.audit(f, actor, "zone", text);
        s.changed(f);
        return Result.pass(zone == null ? "This chunk now follows the faction rules" : "This chunk is now part of " + zone.name());
    }

    Result setPerm(UUID actor, String zoneRef, Rank rank, Relation relation, FactionAction action, Boolean allowed) {
        if (action == null || (rank == null) == (relation == null)) {
            return Result.fail("Pick a permission and either a rank or a relation");
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        Zone zone = find(f, zoneRef);
        if (zone == null) {
            return Result.fail("Your faction has no zone called " + zoneRef);
        }
        String who;
        if (rank != null) {
            if (rank == Rank.LEADER) {
                return Result.fail("The leader always has every permission");
            }
            if (!action.rankScoped()) {
                return Result.fail("That permission cannot be given to a rank");
            }
            // The same limits as the faction grid, so a zone cannot be used to raise your own rank.
            boolean leader = actor.equals(f.leader());
            Rank own = f.rankOf(actor);
            if (!leader && (own == null || rank.ordinal() >= own.ordinal())) {
                return Result.fail("You can only change permissions for ranks below your own");
            }
            if (!leader && Boolean.TRUE.equals(allowed) && !Perms.can(f, actor, action, dev.ryvrook.dominion.util.Time.now())) {
                return Result.fail("You cannot grant a permission you do not have yourself");
            }
            String n = f.rankName(rank);
            who = n.endsWith("s") ? n : n + "s";
        } else {
            if (!relation.isDiplomatic()) {
                return Result.fail("Use rank permissions for your own members");
            }
            if (!action.relationScoped()) {
                return Result.fail("That permission cannot be given to other factions");
            }
            who = relation.displayName() + " factions";
        }
        String verb = action.description().toLowerCase(Locale.ROOT);
        String text;
        synchronized (f) {
            // Re-read under the monitor, since a concurrent edit may have replaced the zone object.
            Zone live = f.zone(zone.id());
            if (live == null) {
                return Result.fail("Your faction has no zone called " + zoneRef);
            }
            if (allowed == null) {
                if (!removeOverride(f, live, rank, relation, action)) {
                    return Result.fail(zone.name() + " has no override for that");
                }
                text = "In " + zone.name() + " " + who.toLowerCase(Locale.ROOT) + " now follow the faction rules to " + verb;
            } else {
                // A zone entry replaces the whole faction set for that rank or relation, so a new
                // entry starts as a copy of the faction set and only this action differs.
                if (rank != null) {
                    if (!live.hasRankOverride(rank)) {
                        f.rankPerms(rank).forEach(a -> live.setRankPerm(rank, a, true));
                    }
                    live.setRankPerm(rank, action, allowed);
                } else {
                    if (!live.hasRelationOverride(relation)) {
                        f.relationPerms(relation).forEach(a -> live.setRelationPerm(relation, a, true));
                    }
                    live.setRelationPerm(relation, action, allowed);
                }
                f.touchZones();
                text = "In " + zone.name() + " " + who.toLowerCase(Locale.ROOT) + (allowed ? " may " : " may not ") + verb;
            }
        }
        s.audit(f, actor, "zone", text);
        s.changed(f);
        return Result.pass(text);
    }

    Result reset(UUID actor, String zoneRef) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        Zone zone = find(f, zoneRef);
        if (zone == null) {
            return Result.fail("Your faction has no zone called " + zoneRef);
        }
        synchronized (f) {
            Zone current = f.zone(zone.id());
            if (current == null) {
                return Result.fail("Your faction has no zone called " + zoneRef);
            }
            Document d = current.toDocument();
            Document rp = d.get("RankPerms", Document.class);
            Document lp = d.get("RelationPerms", Document.class);
            if ((rp == null || rp.isEmpty()) && (lp == null || lp.isEmpty())) {
                return Result.fail(zone.name() + " already follows the faction rules");
            }
            current.clearOverrides();
            f.touchZones();
        }
        s.audit(f, actor, "zone", "Reset every permission in " + zone.name() + " to the faction rules");
        s.changed(f);
        return Result.pass(zone.name() + " now follows the faction rules for everything");
    }

    /**
     * Zone keeps one set per rank or relation, so a single action override is removed by dropping
     * that action from the stored list and rebuilding the zone. Removing the last action of a set that
     * only existed for this override drops the whole entry so the faction grid applies again.
     */
    @SuppressWarnings("unchecked")
    private static boolean removeOverride(Faction f, Zone zone, Rank rank, Relation relation, FactionAction action) {
        Document d = zone.toDocument();
        Document grid = d.get(rank != null ? "RankPerms" : "RelationPerms", Document.class);
        String key = rank != null ? rank.name() : relation.name();
        if (grid == null || !grid.containsKey(key)) {
            return false;
        }
        List<String> list = new ArrayList<>((List<String>) grid.get(key));
        Zone probe = Zone.fromDocument(zone.toDocument());
        boolean faction = rank != null ? f.rankCan(rank, action) : f.relationCan(relation, action);
        Boolean current = rank != null ? probe.rankCan(rank, action) : probe.relationCan(relation, action);
        if (current != null && current == faction) {
            return false;
        }
        if (faction) {
            list.add(action.name());
        } else {
            list.remove(action.name());
        }
        // An entry that now matches the faction grid exactly is dropped.
        java.util.EnumSet<FactionAction> factionSet = rank != null ? f.rankPerms(rank) : f.relationPerms(relation);
        java.util.Set<String> names = new java.util.HashSet<>(list);
        java.util.Set<String> base = new java.util.HashSet<>();
        factionSet.forEach(a -> base.add(a.name()));
        if (names.equals(base)) {
            grid.remove(key);
        } else {
            grid.put(key, new ArrayList<>(names));
        }
        Zone rebuilt = Zone.fromDocument(d);
        f.putZone(rebuilt);
        return true;
    }

    /** Finds a zone by id or by name, ignoring case. */
    static Zone find(Faction f, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String key = ref.strip();
        Zone z = f.zone(key.toLowerCase(Locale.ROOT));
        if (z != null) {
            return z;
        }
        for (Zone each : f.zones()) {
            if (each.name().equalsIgnoreCase(key)) {
                return each;
            }
        }
        return null;
    }

    static String slug(String name) {
        StringBuilder b = new StringBuilder();
        boolean dash = false;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c < 128 && Character.isLetterOrDigit(c)) {
                b.append(c);
                dash = false;
            } else if (!dash && b.length() > 0) {
                b.append('-');
                dash = true;
            }
            if (b.length() >= SLUG_MAX) {
                break;
            }
        }
        String out = b.toString();
        while (out.endsWith("-")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
