package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Raid eligibility, dominance cooldowns and breached chunks. Breaches live in memory only. */
final class Raids {
    /** Outcome of an overclaim check. Dominance is true when only dominance mode allowed it. */
    record Verdict(Result result, boolean dominance) {
        static Verdict fail(String message) {
            return new Verdict(Result.fail(message), false);
        }
    }

    private final Land land;
    private final ClaimServiceImpl claims;
    private final Map<PairKey, Long> dominanceUsed = new ConcurrentHashMap<>();
    private final Map<BreachKey, Long> breaches = new ConcurrentHashMap<>();
    /** Pair verdicts keyed attacker then defender. Protection events hit this many times a second. */
    private final Map<PairKey, CachedVerdict> pairCache = new ConcurrentHashMap<>();

    private record CachedVerdict(Verdict verdict, long expires) {}

    /** Record keys avoid building strings on every block event. */
    private record PairKey(UUID attacker, UUID defender) {}

    private record BreachKey(String world, long chunk, UUID attacker) {}

    static final long PAIR_CACHE_MILLIS = 5_000L;

    Raids(Land land, ClaimServiceImpl claims) {
        this.land = land;
        this.claims = claims;
    }

    /** Raidable by anyone under the enabled state modes. Dominance is attacker specific so it is not included. */
    boolean raidable(Faction defender) {
        Settings s = land.settings();
        if (defender == null || defender.isSystem() || !s.bool("raid.enabled") || land.graceActive()) {
            return false;
        }
        if (land.onlineMembers(defender) < s.integer("raid.minDefendersOnline")) {
            return false;
        }
        return modes(defender);
    }

    private boolean modes(Faction d) {
        Settings s = land.settings();
        if (s.bool("power.dtrMode")) {
            // In the deaths pool mode faction power is the pool, and an empty pool means raidable.
            return land.power(d) <= 0;
        }
        int enabled = 0;
        int met = 0;
        if (s.bool("raid.modeOverextended")) {
            enabled++;
            if (claims.claimCount(d) > claims.maxClaims(d)) {
                met++;
            }
        }
        if (s.bool("raid.modeLowRatio")) {
            enabled++;
            if (land.ratio(d) * 100.0 < s.decimal("raid.lowRatioPercent")) {
                met++;
            }
        }
        if (s.bool("raid.modeUpkeep")) {
            enabled++;
            if (d.stat("upkeepUnpaidDays") >= 3) {
                met++;
            }
        }
        if (enabled == 0) {
            return false;
        }
        return s.bool("raid.combineAny") ? met > 0 : met == enabled;
    }

    /**
     * Full raid check for one chunk. The chunk independent part, which sums power and counts online
     * members, can come from a five second cache keyed by the faction pair. Claims always ask with
     * a fresh pair answer so land never changes hands on stale data.
     */
    Verdict check(Faction attacker, Faction defender, String world, long chunk, boolean cached) {
        if (attacker == null || defender == null) {
            return Verdict.fail("There is no faction to raid here.");
        }
        if (attacker.id().equals(defender.id())) {
            return Verdict.fail("You cannot raid your own faction.");
        }
        Verdict pair = cached ? cachedPair(attacker, defender) : freshPair(attacker, defender);
        if (pair.result().failed()) {
            return pair;
        }
        if (land.settings().bool("raid.protectHomeChunkUntilLast") && isHomeChunk(defender, world, chunk)
                && claims.claimCount(defender) > 1) {
            return Verdict.fail("The home chunk of " + defender.tag() + " can only be taken last.");
        }
        if (land.shielded(defender, world, chunk)) {
            return Verdict.fail("The shield of " + defender.tag() + " protects this land right now.");
        }
        if (pair.dominance() && !touchesWildOrOwn(world, chunk, attacker)) {
            return Verdict.fail("You can only take chunks at the edge of " + defender.tag() + " land.");
        }
        return pair;
    }

    private Verdict cachedPair(Faction attacker, Faction defender) {
        PairKey k = new PairKey(attacker.id(), defender.id());
        long now = Time.now();
        CachedVerdict c = pairCache.get(k);
        if (c != null && c.expires() > now) {
            return c.verdict();
        }
        Verdict v = freshPair(attacker, defender);
        if (pairCache.size() > 4096) {
            pairCache.values().removeIf(e -> e.expires() <= now);
        }
        pairCache.put(k, new CachedVerdict(v, now + PAIR_CACHE_MILLIS));
        return v;
    }

    /** Everything in the raid check that depends only on the two factions. */
    private Verdict freshPair(Faction attacker, Faction defender) {
        Settings s = land.settings();
        if (!s.bool("raid.enabled")) {
            return Verdict.fail("Raiding is turned off on this server.");
        }
        if (land.graceActive()) {
            return Verdict.fail("Raiding is paused during the grace period.");
        }
        if (defender.isSystem()) {
            return Verdict.fail("Server zones cannot be raided.");
        }
        int min = s.integer("raid.minDefendersOnline");
        if (min > 0 && land.onlineMembers(defender) < min) {
            return Verdict.fail(defender.tag() + " needs " + min + " members online before it can be raided.");
        }
        if (modes(defender)) {
            return new Verdict(Result.pass(), false);
        }
        if (s.bool("raid.modeDominance")) {
            Result r = dominance(attacker, defender);
            return new Verdict(r, r.success());
        }
        return Verdict.fail(defender.tag() + " is not raidable right now.");
    }

    private Result dominance(Faction attacker, Faction defender) {
        Settings s = land.settings();
        double margin = s.decimal("raid.dominanceMarginPercent");
        double ap = land.power(attacker);
        double dp = land.power(defender);
        if (!dominates(ap, dp, margin)) {
            return Result.fail("Your faction needs " + Msg.number(margin) + " percent more power than "
                    + defender.tag() + " to take its land.");
        }
        if (!(land.ratio(attacker) > land.ratio(defender))) {
            return Result.fail("Your power ratio must be higher than the ratio of " + defender.tag() + " to take its land.");
        }
        Long last = dominanceUsed.get(new PairKey(attacker.id(), defender.id()));
        long cooldown = s.integer("raid.dominanceCooldownMinutes") * 60_000L;
        if (last != null && Time.now() - last < cooldown) {
            return Result.fail("You can take land from " + defender.tag() + " again in "
                    + Time.remaining(last + cooldown - Time.now()) + ".");
        }
        return Result.pass();
    }

    /**
     * True when the attacker leads the defender by the margin. The lead is a share of the size of
     * the defender's power, so a defender below zero still needs a real lead. Multiplying a negative
     * total by the margin would lower the bar instead.
     */
    static boolean dominates(double attacker, double defender, double marginPercent) {
        double lead = Math.abs(defender) * Math.max(0, marginPercent) / 100.0;
        return attacker > defender + lead;
    }

    private boolean touchesWildOrOwn(String world, long chunk, Faction attacker) {
        for (long n : Keys.neighbours(chunk)) {
            Faction o = claims.ownerAt(world, n);
            if (o == null || o.id().equals(attacker.id())) {
                return true;
            }
        }
        return false;
    }

    static boolean isHomeChunk(Faction f, String world, long chunk) {
        Location h = f.home();
        return h != null && world != null && world.equals(h.world()) && h.chunkIndex() == chunk;
    }

    void markDominance(Faction attacker, Faction defender) {
        PairKey k = new PairKey(attacker.id(), defender.id());
        dominanceUsed.put(k, Time.now());
        // The cooldown starts now, so a cached pass for this pair is no longer true.
        pairCache.remove(k);
    }

    boolean breached(String world, long chunk, UUID attackerFaction) {
        if (world == null || attackerFaction == null) {
            return false;
        }
        BreachKey k = new BreachKey(world, chunk, attackerFaction);
        Long until = breaches.get(k);
        if (until == null) {
            return false;
        }
        if (Time.now() >= until) {
            breaches.remove(k, until);
            return false;
        }
        return true;
    }

    void setBreached(String world, long chunk, UUID attackerFaction, long until) {
        if (world == null || attackerFaction == null) {
            return;
        }
        long now = Time.now();
        breaches.values().removeIf(v -> v <= now);
        BreachKey k = new BreachKey(world, chunk, attackerFaction);
        if (until <= now) {
            breaches.remove(k);
        } else {
            breaches.put(k, until);
        }
    }

    /** Drops cooldowns and breaches that mention a faction that no longer exists. */
    void forgetFaction(UUID factionId) {
        dominanceUsed.keySet().removeIf(k -> k.attacker().equals(factionId) || k.defender().equals(factionId));
        pairCache.keySet().removeIf(k -> k.attacker().equals(factionId) || k.defender().equals(factionId));
        breaches.keySet().removeIf(k -> k.attacker().equals(factionId));
    }
}
