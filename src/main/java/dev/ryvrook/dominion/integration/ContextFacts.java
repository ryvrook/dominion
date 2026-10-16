package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mapper facts read from the repository and the relation service. Memory only. The invite and deposit memories
 * live in the faction's stats under {@link #PREFIX}, which /f stats leaves out, so they survive a restart. The
 * upgrade level, foreign entry and power maximum memories live on the player, since they follow the player from
 * faction to faction. The breach memory is a time window kept in this object.
 */
final class ContextFacts implements StatMapper.Facts {
    /** Faction stat keys of the counter memories. */
    static final String PREFIX = "counter.";
    /** Player counter memory keys. */
    static final String UPGRADE_LEVEL = "upgradeLevel";
    static final String POWER_MAX = "powerMax";
    static final String FOREIGN = "foreign.";
    /** How many factions one player's foreign entry memory holds. Past this no new faction counts. */
    static final int FOREIGN_CAP = 128;
    /** BreachChanged events of one attacker against one defender this close together are one breach. */
    static final long BREACH_WINDOW_MILLIS = 10_000L;

    private final DominionContext ctx;
    private final Map<String, Long> lastBreach = new ConcurrentHashMap<>();

    ContextFacts(DominionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isMember(UUID factionId, UUID player) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        return f != null && !f.isSystem() && f.isMember(player);
    }

    @Override
    public List<UUID> members(UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || f.isSystem()) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Member m : f.members()) {
            out.add(m.uuid());
        }
        return out;
    }

    @Override
    public boolean atWar(UUID factionA, UUID factionB) {
        Faction a = factionA == null ? null : ctx.repo().faction(factionA);
        Faction b = factionB == null ? null : ctx.repo().faction(factionB);
        if (a == null || b == null || a.isSystem() || b.isSystem()) {
            return false;
        }
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs != null) {
            try {
                return rs.between(a, b) == Relation.ENEMY;
            } catch (RuntimeException e) {
                // fall back to the stored relations below
            }
        }
        return a.relationTo(b.id()) == Relation.ENEMY || b.relationTo(a.id()) == Relation.ENEMY;
    }

    @Override
    public UUID founder(UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || f.isSystem()) {
            return null;
        }
        long age = System.currentTimeMillis() - f.createdAt();
        long window = ctx.settings().integer("integrations.foundedWindowSeconds") * 1000L;
        return age >= -window && age <= window ? f.leader() : null;
    }

    @Override
    public boolean firstInvite(UUID factionId, UUID target) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || target == null) {
            return false;
        }
        synchronized (f) {
            String key = PREFIX + "invited." + target;
            if (f.stat(key) != 0) {
                return false;
            }
            f.addStat(key, 1);
            return true;
        }
    }

    @Override
    public long depositCredit(UUID factionId, UUID player, double amount) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || player == null) {
            return 0;
        }
        synchronized (f) {
            long day = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
            reset(f, player, day);
            long net = f.addStat(netKey(player), Math.round(amount * 100));
            long coins = Math.max(0, net) / 100;
            long credit = coins - f.stat(creditedKey(player));
            if (credit <= 0) {
                return 0;
            }
            f.addStat(creditedKey(player), credit);
            return credit;
        }
    }

    @Override
    public void withdrew(UUID factionId, UUID player, double amount) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || player == null) {
            return;
        }
        synchronized (f) {
            long day = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
            reset(f, player, day);
            long net = f.stat(netKey(player));
            f.addStat(netKey(player), -Math.min(net, Math.round(amount * 100)));
        }
    }

    @Override
    public long upgradeLevelCredit(UUID player, int level) {
        PlayerData p = player == null ? null : ctx.player(player);
        if (p == null) {
            return 0;
        }
        synchronized (p) {
            long raise = level - p.counter(UPGRADE_LEVEL);
            if (raise <= 0) {
                return 0;
            }
            p.setCounter(UPGRADE_LEVEL, level);
            return raise;
        }
    }

    @Override
    public boolean firstVisit(UUID player, UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null || f.isSystem() || player == null) {
            return false;
        }
        PlayerData p = ctx.player(player);
        synchronized (p) {
            String key = FOREIGN + factionId;
            if (p.counter(key) != 0 || p.counterCount(FOREIGN) >= FOREIGN_CAP) {
                return false;
            }
            p.setCounter(key, 1);
            return true;
        }
    }

    @Override
    public double maxPower(UUID player) {
        PowerService power = ctx.serviceOrNull(PowerService.class);
        if (power == null || player == null) {
            return Double.POSITIVE_INFINITY;
        }
        try {
            return power.maxPowerOf(player);
        } catch (RuntimeException e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public boolean firstPowerMax(UUID player) {
        PlayerData p = player == null ? null : ctx.player(player);
        if (p == null) {
            return false;
        }
        synchronized (p) {
            if (p.counter(POWER_MAX) != 0) {
                return false;
            }
            p.setCounter(POWER_MAX, 1);
            return true;
        }
    }

    @Override
    public boolean newBreach(UUID attacker, UUID defender) {
        String key = attacker + ">" + defender;
        long now = System.currentTimeMillis();
        Long last = lastBreach.put(key, now);
        return last == null || now - last > BREACH_WINDOW_MILLIS;
    }

    /** Starts the player's day over when the stored day is not today. Caller holds the faction lock. */
    private static void reset(Faction f, UUID player, long day) {
        String dayKey = PREFIX + "depositDay." + player;
        if (f.stat(dayKey) != day) {
            f.addStat(dayKey, day - f.stat(dayKey));
            f.removeStat(netKey(player));
            f.removeStat(creditedKey(player));
        }
    }

    private static String netKey(UUID player) {
        return PREFIX + "depositNet." + player;
    }

    private static String creditedKey(UUID player) {
        return PREFIX + "depositCredited." + player;
    }
}
