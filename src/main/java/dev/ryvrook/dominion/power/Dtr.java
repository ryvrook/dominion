package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;

/**
 * The deaths pool mode behind power.dtrMode. Each faction has one pool stored in the faction stat
 * "dtr" as hundredths. The pool starts at 2 plus 1 per member, capped at 10, loses 1 per member
 * death, never drops below minus 3 and regains 0.05 per minute for each online member, at most
 * 0.1 per minute. A faction is raidable while its pool is at or below 0.
 */
final class Dtr {
    static final String STAT = "dtr";
    /** Set to 1 once the pool holds a real value, because a stored 0 is a valid pool. */
    static final String STAT_SET = "dtrSet";
    static final double BASE = 2.0;
    static final double PER_MEMBER = 1.0;
    static final double CAP = 10.0;
    static final double FLOOR = -3.0;
    static final double LOSS_PER_DEATH = 1.0;
    static final double REGEN_PER_ONLINE = 0.05;
    static final double REGEN_CAP = 0.1;

    private final DominionContext ctx;

    Dtr(DominionContext ctx) {
        this.ctx = ctx;
    }

    boolean on() {
        return ctx.settings().bool("power.dtrMode");
    }

    static double max(Faction f) {
        return Math.min(CAP, BASE + PER_MEMBER * Math.max(0, f.memberCount()));
    }

    /** Current pool, starting it at the maximum the first time it is read. */
    double pool(Faction f) {
        synchronized (f) {
            if (f.stat(STAT_SET) == 0) {
                store(f, max(f));
                f.addStat(STAT_SET, 1);
            }
            return f.stat(STAT) / 100.0;
        }
    }

    /** Writes the pool clamped to the floor and the maximum. Caller holds the faction lock. */
    private static double store(Faction f, double value) {
        double clamped = Math.max(FLOOR, Math.min(max(f), value));
        long target = Math.round(clamped * 100.0);
        long current = f.stat(STAT);
        if (target != current) {
            f.addStat(STAT, target - current);
        }
        return clamped;
    }

    void onMemberDeath(Faction f) {
        if (f == null || f.isSystem()) {
            return;
        }
        double before;
        double after;
        synchronized (f) {
            before = pool(f);
            after = store(f, before - LOSS_PER_DEATH);
        }
        if (after != before) {
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
        }
    }

    /** Minute regen. Also pulls pools down to a lower maximum after members left. */
    void tick() {
        for (Faction f : new ArrayList<>(ctx.repo().factions())) {
            if (f.isSystem()) {
                continue;
            }
            int online = 0;
            for (Member m : f.members()) {
                if (Players.isOnline(m.uuid())) {
                    online++;
                }
            }
            double rate = Math.min(REGEN_CAP, REGEN_PER_ONLINE * online);
            boolean crossed;
            synchronized (f) {
                double before = pool(f);
                double after = store(f, before + rate);
                crossed = (before <= 0) != (after <= 0);
            }
            if (crossed) {
                ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            }
        }
    }
}
