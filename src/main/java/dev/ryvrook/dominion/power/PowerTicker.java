package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;

/** The minute cycle. Online players regenerate and long absent faction members decay. */
final class PowerTicker {
    private static final long DAY = 86_400_000L;
    private static final double MINUTES_PER_DAY = 1440.0;
    static final double REGEN_STEP = 0.25;

    private final DominionContext ctx;
    private final PowerServiceImpl power;

    PowerTicker(DominionContext ctx, PowerServiceImpl power) {
        this.ctx = ctx;
        this.power = power;
    }

    void tick() {
        Settings s = ctx.settings();
        double regen = s.decimal("power.regenPerMinute");
        if (regen > 0) {
            regenOnline(regen);
        }
        int afterDays = s.integer("power.offlineDecayAfterDays");
        double perDay = s.decimal("power.offlineDecayPerDay");
        if (perDay > 0) {
            decayOffline(Math.max(0, afterDays), perDay / MINUTES_PER_DAY);
        }
    }

    private void regenOnline(double regen) {
        for (PlayerData p : new ArrayList<>(ctx.repo().loadedPlayers())) {
            if (!Players.isOnline(p.uuid())) {
                continue;
            }
            dev.ryvrook.dominion.model.Faction f = ctx.repo().faction(p.factionId());
            double max = power.maxFor(f);
            // The regen upgrade adds a quarter of the base rate per level.
            double rate = regen * (1.0 + REGEN_STEP * (f == null ? 0 : Math.max(0, f.upgrade("regen"))));
            synchronized (p) {
                if (p.power() < max) {
                    power.apply(p, Math.min(max, p.power() + rate), max);
                }
            }
        }
    }

    private void decayOffline(int afterDays, double perMinute) {
        long cutoff = Time.now() - afterDays * DAY;
        double min = power.minPower();
        for (Faction f : new ArrayList<>(ctx.repo().factions())) {
            if (f.isSystem()) {
                continue;
            }
            double max = power.maxFor(f);
            for (Member m : f.members()) {
                if (Players.isOnline(m.uuid())) {
                    continue;
                }
                PlayerData p = ctx.player(m.uuid());
                synchronized (p) {
                    if (dev.ryvrook.dominion.land.Outage.credited(ctx, p.lastSeen()) < cutoff && p.power() > min) {
                        power.apply(p, Math.max(min, p.power() - perMinute), max);
                    }
                }
            }
        }
    }
}
