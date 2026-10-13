package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Upkeep, dues, interest and the playtime stipend. Mutations run under the ledger lock. */
final class DailyEconomy {
    static final String UPKEEP_UNPAID = "upkeepUnpaidDays";
    static final String LAST_DAY = "eco.lastDailyDay";

    private final DominionContext ctx;
    private final Object lock;
    /** Stipend fractions not yet paid to an external provider. */
    private final Map<UUID, Double> stipendCarry = new ConcurrentHashMap<>();

    DailyEconomy(DominionContext ctx, Object lock) {
        this.ctx = ctx;
        this.lock = lock;
    }

    void run(EconomyServiceImpl svc) {
        Settings s = ctx.settings();
        double upkeepPerClaim = s.decimal("eco.upkeepPerClaimPerDay");
        double dues = s.decimal("eco.duesPerMemberPerDay");
        double interestPercent = s.decimal("eco.bankInterestPercentPerDay");
        for (Faction f : new ArrayList<>(ctx.repo().factions())) {
            if (f.isSystem() || !firstRunToday(f)) {
                continue;
            }
            if (dues > 0) {
                collectDues(svc, f, dues);
            }
            if (upkeepPerClaim > 0) {
                chargeUpkeep(svc, f, upkeepPerClaim);
            }
            if (interestPercent > 0) {
                payInterest(svc, f, interestPercent);
            }
        }
    }

    private void collectDues(EconomyServiceImpl svc, Faction f, double dues) {
        double total = 0;
        int missed = 0;
        List<Member> members = f.members();
        BalanceProvider provider = svc.provider();
        if (provider instanceof InternalLedger) {
            synchronized (lock) {
                for (Member m : members) {
                    PlayerData p = ctx.player(m.uuid());
                    double paid = Math.min(p.balance(), dues);
                    if (paid + 1e-6 < dues) {
                        missed++;
                    }
                    if (paid > 0) {
                        p.setBalance(Money.round(p.balance() - paid));
                        total += paid;
                    }
                }
                if (total > 0) {
                    synchronized (f) {
                        f.setBank(Money.round(f.bank() + total));
                    }
                }
            }
        } else {
            // External wallets are charged outside the ledger lock, then the bank is credited once.
            for (Member m : members) {
                double have = provider.balance(m.uuid());
                double paid = provider.wholeUnits()
                        ? Math.min(Math.floor(have + 1e-6), Math.ceil(dues - 1e-6))
                        : Money.round(Math.min(have, dues));
                if (paid > 0 && provider.withdraw(m.uuid(), paid, "Faction dues").failed()) {
                    paid = 0;
                }
                if (paid + 1e-6 < dues) {
                    missed++;
                }
                total += Math.max(0, paid);
            }
            if (total > 0) {
                synchronized (lock) {
                    synchronized (f) {
                        f.setBank(Money.round(f.bank() + total));
                    }
                }
            }
        }
        if (total > 0 || missed > 0) {
            String text = "Collected " + svc.format(total) + " in daily dues";
            if (missed > 0) {
                text += " and " + missed + (missed == 1 ? " member" : " members") + " could not pay in full";
            }
            svc.bankChanged(f, null, text);
        }
    }

    private void chargeUpkeep(EconomyServiceImpl svc, Faction f, double perClaim) {
        int claims = ctx.repo().claimCount(f.id());
        if (claims <= 0) {
            resetUnpaid(f);
            return;
        }
        double cost = Money.round(claims * perClaim);
        boolean paid;
        double taken;
        synchronized (lock) {
            synchronized (f) {
                paid = Money.covers(f.bank(), cost);
                taken = paid ? cost : f.bank();
                f.setBank(Money.round(f.bank() - taken));
            }
        }
        if (paid) {
            resetUnpaid(f);
            svc.bankChanged(f, null, "Paid " + svc.format(cost) + " upkeep for " + claims + " claims");
        } else {
            long days = f.addStat(UPKEEP_UNPAID, 1);
            svc.bankChanged(f, null, "Upkeep of " + svc.format(cost) + " was not paid, " + days
                    + (days == 1 ? " day" : " days") + " behind");
        }
    }

    /**
     * The daily timer starts an hour after every boot, so a server that restarts often would charge
     * upkeep and pay interest several times a day. The last charged day is kept in faction stats.
     */
    private static boolean firstRunToday(Faction f) {
        long today = WithdrawLimits.today();
        synchronized (f) {
            long last = f.stat(LAST_DAY);
            if (last == today) {
                return false;
            }
            f.addStat(LAST_DAY, today - last);
        }
        return true;
    }

    private static void resetUnpaid(Faction f) {
        long current = f.stat(UPKEEP_UNPAID);
        if (current != 0) {
            f.addStat(UPKEEP_UNPAID, -current);
        }
    }

    private void payInterest(EconomyServiceImpl svc, Faction f, double percent) {
        double interest;
        synchronized (lock) {
            synchronized (f) {
                interest = Money.round(f.bank() * percent / 100.0);
                if (interest <= 0) {
                    return;
                }
                f.setBank(Money.round(f.bank() + interest));
            }
        }
        svc.bankChanged(f, null, "Earned " + svc.format(interest) + " in interest");
    }

    /**
     * Credits one minute of the hourly stipend to every online player. The internal ledger takes
     * the fraction directly. Other providers get it once a whole cent, or a whole item, has built up.
     */
    void stipend(BalanceProvider provider) {
        double perMinute = ctx.settings().decimal("eco.playtimeStipendPerHour") / 60.0;
        if (perMinute <= 0) {
            return;
        }
        if (provider instanceof InternalLedger ledger) {
            for (PlayerData p : new ArrayList<>(ctx.repo().loadedPlayers())) {
                if (Players.isOnline(p.uuid())) {
                    ledger.addRaw(p, perMinute);
                }
            }
            return;
        }
        double step = provider.wholeUnits() ? 1.0 : 0.01;
        for (PlayerData p : new ArrayList<>(ctx.repo().loadedPlayers())) {
            if (!Players.isOnline(p.uuid())) {
                continue;
            }
            double owed = stipendCarry.merge(p.uuid(), perMinute, Double::sum);
            double pay = Math.floor((owed + 1e-9) / step) * step;
            if (pay > 0 && provider.deposit(p.uuid(), Money.round(pay), "Playtime stipend").success()) {
                stipendCarry.put(p.uuid(), Math.max(0, owed - pay));
            }
        }
    }

    /** Drops the stipend fraction kept for a player who left. */
    void forget(UUID player) {
        stipendCarry.remove(player);
    }
}
