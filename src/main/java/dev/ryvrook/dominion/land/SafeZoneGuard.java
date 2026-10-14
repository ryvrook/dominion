package dev.ryvrook.dominion.land;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Keeps combat tagged players out of SafeZones. The territory tick reports where each player
 * stands and whether that chunk belongs to a SafeZone. The guard remembers the last spot each
 * player stood outside one, and when a tagged player crosses in on foot it answers PUSH_BACK so
 * the caller can move them back to that spot. Free of the server so the rules can be tested alone.
 */
final class SafeZoneGuard {
    /**
     * A move longer than this between two ticks is a teleport or a respawn rather than a walk
     * across the border, so it is never pushed back. Measured in blocks on the ground plane.
     */
    static final double MAX_STEP = 64.0;

    /** Where a player stands, with facing so a push back keeps the view they had. */
    record Spot(String world, double x, double y, double z, float yaw, float pitch) {
        double flatDistance(Spot o) {
            double dx = x - o.x;
            double dz = z - o.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    enum Verdict { ALLOW, PUSH_BACK }

    private final Map<UUID, Spot> lastOutside = new ConcurrentHashMap<>();
    /** Players who were let into the SafeZone they stand in, so a tag that starts inside never pushes them out. */
    private final Set<UUID> settled = ConcurrentHashMap.newKeySet();

    /**
     * The pure rule. A player standing in a SafeZone is pushed back when the rule is on, they are
     * tagged, a spot outside is known in the same world, and that spot is within one step.
     */
    static Verdict decide(boolean enabled, boolean tagged, boolean inSafeZone, Spot anchor, Spot now) {
        if (!inSafeZone || !enabled || !tagged || anchor == null || now == null) {
            return Verdict.ALLOW;
        }
        if (anchor.world() == null || !anchor.world().equals(now.world())) {
            return Verdict.ALLOW;
        }
        return anchor.flatDistance(now) <= MAX_STEP ? Verdict.PUSH_BACK : Verdict.ALLOW;
    }

    /**
     * Records one tick for a player. Outside a SafeZone the spot becomes the new anchor. Inside one
     * the crossing is judged once. A player who is let in stays allowed until they leave again,
     * while a player who is pushed back is judged again on every tick until they are outside.
     * The tag is only asked for when a crossing is judged.
     */
    Verdict observe(UUID player, Spot now, boolean inSafeZone, boolean enabled, BooleanSupplier tagged) {
        if (player == null || now == null) {
            return Verdict.ALLOW;
        }
        if (!inSafeZone) {
            lastOutside.put(player, now);
            settled.remove(player);
            return Verdict.ALLOW;
        }
        if (settled.contains(player)) {
            return Verdict.ALLOW;
        }
        Spot anchor = lastOutside.get(player);
        boolean isTagged = enabled && anchor != null && tagged.getAsBoolean();
        Verdict v = decide(enabled, isTagged, true, anchor, now);
        if (v == Verdict.ALLOW) {
            settled.add(player);
        }
        return v;
    }

    /** The last spot outside a SafeZone, or null. */
    Spot anchor(UUID player) {
        return player == null ? null : lastOutside.get(player);
    }

    void forget(UUID player) {
        if (player != null) {
            lastOutside.remove(player);
            settled.remove(player);
        }
    }
}
