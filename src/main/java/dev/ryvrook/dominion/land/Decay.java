package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.storage.Repository;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

/** Daily land decay for factions whose members have all been away too long. */
final class Decay {
    private record Pick(String world, long chunk) {}

    static final String LAST_DAY = "land.lastDecayDay";

    private final Land land;
    private final ClaimServiceImpl claims;

    Decay(Land land, ClaimServiceImpl claims) {
        this.land = land;
        this.claims = claims;
    }

    void tick() {
        int days = land.settings().integer("land.decayAfterDays");
        int perDay = land.settings().integer("land.decayChunksPerDay");
        if (days <= 0 || perDay <= 0) {
            return;
        }
        long cutoff = Time.now() - days * 86_400_000L;
        Repository repo = land.repo();
        for (Faction f : new ArrayList<>(repo.factions())) {
            if (f.isSystem() || repo.claimCount(f.id()) <= 1 || active(land, f, cutoff) || !firstRunToday(f)) {
                continue;
            }
            for (int i = 0; i < perDay && repo.claimCount(f.id()) > 1; i++) {
                Pick p = weakest(f);
                if (p == null) {
                    break;
                }
                claims.decayRemove(f, p.world(), p.chunk(), days);
            }
        }
    }

    /**
     * The daily timer restarts with every boot, so frequent restarts would decay land several times
     * a day. The last decay day is kept in faction stats so it survives restarts.
     */
    private static boolean firstRunToday(Faction f) {
        long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        synchronized (f) {
            long last = f.stat(LAST_DAY);
            if (last == today) {
                return false;
            }
            f.addStat(LAST_DAY, today - last);
        }
        return true;
    }

    /** Active when a member is online or was seen after the cutoff, with server downtime credited back. */
    private static boolean active(Land land, Faction f, long cutoff) {
        for (Member m : f.members()) {
            if (Outage.credited(land.ctx, m.lastSeen()) >= cutoff || Players.isOnline(m.uuid())) {
                return true;
            }
        }
        return false;
    }

    /** The edge chunk with the fewest own neighbours. The home chunk goes last. */
    private Pick weakest(Faction f) {
        Pick best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String world : new ArrayList<>(land.repo().claimWorlds())) {
            long[] own = land.repo().claimsOf(world, f.id());
            LongOpenHashSet set = new LongOpenHashSet(own);
            for (long c : own) {
                int score = 0;
                for (long n : Keys.neighbours(c)) {
                    if (set.contains(n)) {
                        score++;
                    }
                }
                if (Raids.isHomeChunk(f, world, c)) {
                    score += 100;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new Pick(world, c);
                }
            }
        }
        return best;
    }
}
