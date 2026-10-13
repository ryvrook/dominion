package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.util.Time;

/**
 * Credits server downtime back to last seen times, the same way the faction inactivity check does.
 * Nobody could log in while the server was down, so a last seen time from before this boot is
 * moved forward by the length of the outage. The outage is the gap between the newest last seen
 * time recorded before the boot and the boot itself. Players who were online at a crash keep
 * their login time, which only makes the estimate more lenient. Power decay can use this too.
 */
public final class Outage {
    private static volatile long bootAt = Time.now();
    private static volatile long downtime = -1;

    private Outage() {}

    /** Marks the start of this boot. The land module calls it from init. */
    public static void boot() {
        bootAt = Time.now();
        downtime = -1;
    }

    public static long bootAt() {
        return bootAt;
    }

    /** Last seen with the outage before this boot credited back. Times after the boot are unchanged. */
    public static long credited(DominionContext ctx, long lastSeen) {
        long boot = bootAt;
        if (lastSeen <= 0 || lastSeen >= boot) {
            return lastSeen;
        }
        return lastSeen + downtime(ctx);
    }

    /** Estimated length of the outage before this boot, computed once from member records. */
    public static long downtime(DominionContext ctx) {
        long d = downtime;
        if (d >= 0) {
            return d;
        }
        synchronized (Outage.class) {
            if (downtime >= 0) {
                return downtime;
            }
            long boot = bootAt;
            long latest = 0;
            for (Faction f : ctx.repo().factions()) {
                for (Member m : f.members()) {
                    if (m.lastSeen() < boot) {
                        latest = Math.max(latest, m.lastSeen());
                    }
                }
            }
            downtime = latest <= 0 ? 0 : Math.max(0, boot - latest);
            return downtime;
        }
    }
}
