package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The deaths pool mode behind power.dtrMode. Each faction has one pool stored in the faction stat
 * "dtr" as hundredths. The pool starts at power.dtrBase plus power.dtrPerMember per member, capped at
 * power.dtrCap, loses power.dtrLossPerDeath per member death, never drops below power.dtrFloor and
 * regains power.dtrRegenPerOnline per minute for each online member, at most power.dtrRegenCap per
 * minute. The defaults are 2, 1, 10, 1, minus 3, 0.05 and 0.1. A faction is raidable while its pool
 * is at or below 0.
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
    private final Predicate<UUID> isOnline;

    Dtr(DominionContext ctx, Predicate<UUID> online) {
        this.ctx = ctx;
        this.isOnline = online;
    }

    boolean on() {
        return ctx.settings().bool("power.dtrMode");
    }

    /** The largest pool with the shipped defaults. */
    static double max(Faction f) {
        return max(f, BASE, PER_MEMBER, CAP);
    }

    static double max(Faction f, double base, double perMember, double cap) {
        return Math.min(cap, base + perMember * Math.max(0, f.memberCount()));
    }

    /** The largest pool with the current settings. */
    double maxOf(Faction f) {
        Settings s = ctx.settings();
        return max(f, s.decimal("power.dtrBase"), s.decimal("power.dtrPerMember"), s.decimal("power.dtrCap"));
    }

    /** Current pool, starting it at the maximum the first time it is read. */
    double pool(Faction f) {
        synchronized (f) {
            if (f.stat(STAT_SET) == 0) {
                store(f, maxOf(f));
                f.addStat(STAT_SET, 1);
            }
            return f.stat(STAT) / 100.0;
        }
    }

    /** Writes the pool clamped to the floor and the maximum. Caller holds the faction lock. */
    private double store(Faction f, double value) {
        double clamped = Math.max(ctx.settings().decimal("power.dtrFloor"), Math.min(maxOf(f), value));
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
            after = store(f, before - ctx.settings().decimal("power.dtrLossPerDeath"));
        }
        if (after != before) {
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
        }
    }

    /** Minute regen. Also pulls pools down to a lower maximum after members left. */
    void tick() {
        double perOnline = ctx.settings().decimal("power.dtrRegenPerOnline");
        double regenCap = ctx.settings().decimal("power.dtrRegenCap");
        for (Faction f : new ArrayList<>(ctx.repo().factions())) {
            if (f.isSystem()) {
                continue;
            }
            int online = 0;
            for (Member m : f.members()) {
                if (isOnline.test(m.uuid())) {
                    online++;
                }
            }
            double rate = Math.min(regenCap, perOnline * online);
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
