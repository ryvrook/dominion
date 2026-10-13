package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.PowerService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classic power mode. Player power lives in PlayerData.power, faction power is the member sum
 * plus the faction boost. With power.dtrMode on, faction power is the deaths pool from {@link Dtr}
 * instead, so claims per power and the raid checks keep working on the pool.
 */
public final class PowerServiceImpl implements PowerService {
    /** Extra maximum power per player for each level of the maxpower upgrade. */
    static final double UPGRADE_STEP = 2.0;
    /** Smallest change that is worth an event. */
    static final double EVENT_THRESHOLD = 0.01;

    private final DominionContext ctx;
    private final PowerTicker ticker;
    private final Dtr dtr;
    /** Last value announced per player so slow decay still publishes once it adds up. */
    private final Map<UUID, Double> published = new ConcurrentHashMap<>();

    public PowerServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.ticker = new PowerTicker(ctx, this);
        this.dtr = new Dtr(ctx);
    }

    @Override
    public double powerOf(UUID player) {
        return ctx.player(player).power();
    }

    @Override
    public double maxPowerOf(UUID player) {
        PlayerData p = ctx.player(player);
        return maxFor(ctx.repo().faction(p.factionId()));
    }

    /** Per player maximum inside the given faction, or the base maximum when factionless. */
    double maxFor(Faction faction) {
        double max = ctx.settings().decimal("power.max");
        return faction == null ? max : max + UPGRADE_STEP * faction.upgrade("maxpower");
    }

    double minPower() {
        return ctx.settings().decimal("power.min");
    }

    @Override
    public double factionPower(Faction faction) {
        if (faction == null) {
            return 0;
        }
        if (dtr.on() && !faction.isSystem()) {
            return dtr.pool(faction);
        }
        double sum = 0;
        for (Member m : faction.members()) {
            sum += ctx.player(m.uuid()).power();
        }
        return sum + faction.powerBoost();
    }

    @Override
    public double factionMaxPower(Faction faction) {
        if (faction == null) {
            return 0;
        }
        if (dtr.on() && !faction.isSystem()) {
            return Dtr.max(faction);
        }
        return faction.memberCount() * maxFor(faction) + faction.powerBoost();
    }

    @Override
    public double factionRatio(Faction faction) {
        double max = factionMaxPower(faction);
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, factionPower(faction) / max));
    }

    @Override
    public void setPower(UUID player, double value) {
        PlayerData p = ctx.player(player);
        double max = maxFor(ctx.repo().faction(p.factionId()));
        apply(p, value, max);
    }

    @Override
    public void addPower(UUID player, double delta) {
        PlayerData p = ctx.player(player);
        double max = maxFor(ctx.repo().faction(p.factionId()));
        synchronized (p) {
            apply(p, p.power() + delta, max);
        }
    }

    @Override
    public void onDeath(UUID player, String world, boolean inWarzone) {
        Settings s = ctx.settings();
        if (s.bool("grace.noPowerLoss") && graceActive(s)) {
            return;
        }
        if (inWarzone && !s.bool("power.lossInWarzone")) {
            return;
        }
        if (dtr.on()) {
            // The pool replaces player power, so the death only counts against the faction.
            PlayerData p = ctx.player(player);
            Faction f = ctx.repo().faction(p.factionId());
            if (f != null && f.isMember(player)) {
                dtr.onMemberDeath(f);
            }
            return;
        }
        double loss = s.decimal("power.lossPerDeath");
        if (loss > 0) {
            addPower(player, -loss);
        }
    }

    /** Grace counts only until its end time, matching the rules and land modules. */
    private static boolean graceActive(Settings s) {
        if (!s.bool("grace.active")) {
            return false;
        }
        long ends = s.longValue("grace.endsAt");
        return ends <= 0 || System.currentTimeMillis() < ends;
    }

    @Override
    public void tick() {
        if (dtr.on()) {
            dtr.tick();
            return;
        }
        ticker.tick();
    }

    /** Clamps, stores and publishes once the value moved at least 0.01 since the last event. */
    void apply(PlayerData p, double value, double max) {
        double min = minPower();
        double clamped = Math.max(min, Math.min(Math.max(min, max), value));
        double before;
        synchronized (p) {
            before = p.power();
            if (before == clamped) {
                return;
            }
            p.setPower(clamped);
        }
        double last = published.getOrDefault(p.uuid(), before);
        if (Math.abs(clamped - last) >= EVENT_THRESHOLD) {
            published.put(p.uuid(), clamped);
            ctx.bus().publish(new DominionEvent.PowerChanged(p.uuid(), clamped));
        } else {
            published.putIfAbsent(p.uuid(), before);
        }
    }
}
