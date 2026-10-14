package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Last seen tracking, login alerts, inactive member removal and inactive faction disbanding. */
final class Activity {
    /** No inactivity kicks or disbands until the plugin has run this long, so players can log back in after downtime. */
    static final long BOOT_GRACE = 10 * 60_000L;

    private final FactionSupport s;
    private final Membership membership;
    private final Ranks ranks;
    final long bootAt = Time.now();
    /** Estimated length of the outage before this boot, added to last seen times older than the boot. */
    private long downtime = -1;

    Activity(FactionSupport s, Membership membership, Ranks ranks) {
        this.s = s;
        this.membership = membership;
        this.ranks = ranks;
    }

    void onPlayerSeen(UUID player, String name, boolean online) {
        long now = Time.now();
        PlayerData p = s.ctx.player(player);
        if (name != null && !name.isBlank()) {
            p.setName(name);
        }
        p.setLastSeen(now);
        Faction f = s.factionOf(player);
        if (f == null) {
            if (online) {
                membership.rosterJoin(player, name);
            }
            return;
        }
        synchronized (f) {
            Member m = f.member(player);
            if (m != null) {
                m.setLastSeen(now);
                m.setName(name);
                f.putMember(m);
            }
        }
        if (online && f.setting("loginAlerts", s.settings().bool("chat.loginAlerts"))) {
            ChatService chat = s.ctx.serviceOrNull(ChatService.class);
            if (chat != null) {
                chat.alert(f, Msg.info(s.nameOf(player) + " is now online"));
            }
        }
    }

    void inactivityTick() {
        inactivityTick(Time.now());
    }

    /** The minute check at a given time, so tests can run it days ahead of the boot. */
    void inactivityTick(long now) {
        int memberDays = s.settings().integer("faction.memberInactiveDays");
        int disbandDays = s.settings().integer("faction.disbandInactiveDays");
        if (memberDays <= 0 && disbandDays <= 0) {
            return;
        }
        if (now - bootAt < BOOT_GRACE) {
            return;
        }
        if (downtime < 0) {
            downtime = estimateDowntime();
        }
        for (Faction f : new ArrayList<>(s.ctx.repo().factions())) {
            if (f.isSystem()) {
                continue;
            }
            synchronized (s.lock) {
                if (s.ctx.repo().faction(f.id()) == null) {
                    continue;
                }
                if (disbandDays > 0 && newestSeen(f, now) < now - disbandDays * FactionSupport.DAY) {
                    s.disband(f, null, "no member logged in for " + disbandDays + " days");
                    continue;
                }
                if (memberDays > 0) {
                    kickInactive(f, now, memberDays);
                }
            }
        }
    }

    /**
     * Time between the last recorded activity before this boot and the boot itself. Nobody could log
     * in while the server was down, so that time does not count against anyone. Players online at a
     * crash keep their login time as last seen, which only makes the estimate more lenient.
     */
    private long estimateDowntime() {
        long latest = 0;
        for (Faction f : s.ctx.repo().factions()) {
            for (Member m : f.members()) {
                if (m.lastSeen() < bootAt) {
                    latest = Math.max(latest, m.lastSeen());
                }
            }
        }
        return latest <= 0 ? 0 : Math.max(0, bootAt - latest);
    }

    /** Last seen with the outage before this boot credited back. */
    private long seen(Member m) {
        long t = m.lastSeen();
        return t < bootAt ? t + Math.max(0, downtime) : t;
    }

    private long newestSeen(Faction f, long now) {
        long newest = 0;
        for (Member m : f.members()) {
            newest = Math.max(newest, s.isOnline(m.uuid()) ? now : seen(m));
        }
        return newest;
    }

    private void kickInactive(Faction f, long now, int days) {
        long cutoff = now - days * FactionSupport.DAY;
        List<Member> stale = new ArrayList<>();
        Member successor = null;
        for (Member m : f.members()) {
            boolean active = s.isOnline(m.uuid()) || seen(m) >= cutoff;
            if (!active) {
                stale.add(m);
            } else if (!m.uuid().equals(f.leader()) && better(f, m, successor)) {
                successor = m;
            }
        }
        for (Member m : stale) {
            if (m.uuid().equals(f.leader())) {
                if (successor == null) {
                    continue;
                }
                ranks.handOver(f, m.uuid(), successor.uuid());
                s.toFaction(f, Msg.info(successor.name() + " now leads the faction because the leader was inactive"), false);
            }
            s.removeMember(f, m.uuid(), null, "inactive", s.nameOf(m.uuid()) + " was removed after " + days + " days offline");
        }
    }

    private static boolean better(Faction f, Member candidate, Member current) {
        if (current == null) {
            return true;
        }
        int a = f.rankOf(candidate.uuid()).ordinal();
        int b = f.rankOf(current.uuid()).ordinal();
        return a != b ? a > b : candidate.lastSeen() > current.lastSeen();
    }
}
