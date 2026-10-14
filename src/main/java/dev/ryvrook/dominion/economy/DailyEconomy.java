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

/** Upkeep, dues, interest and the playtime stipend. The money moves themselves live in {@link Books}. */
final class DailyEconomy {
    static final String UPKEEP_UNPAID = "upkeepUnpaidDays";
    static final String LAST_DAY = "eco.lastDailyDay";

    private final DominionContext ctx;
    private final Books books;
    /** Stipend fractions not yet paid, kept until they add up to a whole cent or a whole item. */
    private final Map<UUID, Double> stipendCarry = new ConcurrentHashMap<>();

    DailyEconomy(DominionContext ctx, Books books) {
        this.ctx = ctx;
        this.books = books;
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
        int[] missed = {0};
        List<Member> members = f.members();
        BalanceProvider provider = svc.provider();
        double total;
        if (provider instanceof InternalLedger) {
            List<UUID> payers = new ArrayList<>();
            for (Member m : members) {
                payers.add(m.uuid());
            }
            total = books.duesInternal(f, payers, dues, missed);
        } else {
            List<UUID> ids = new ArrayList<>();
            for (Member m : members) {
                ids.add(m.uuid());
            }
            total = books.duesExternal(provider, f, ids, dues, missed);
        }
        if (total > 0 || missed[0] > 0) {
            String text = "Collected " + svc.format(total) + " in daily dues";
            if (missed[0] > 0) {
                text += " and " + missed[0] + (missed[0] == 1 ? " member" : " members") + " could not pay in full";
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
        Books.Upkeep u = books.upkeep(f, cost);
        if (u.paid()) {
            resetUnpaid(f);
            svc.bankChanged(f, null, "Paid " + svc.format(cost) + " upkeep for " + claims + (claims == 1 ? " claim" : " claims"));
        } else {
            long days = f.addStat(UPKEEP_UNPAID, 1);
            String behind = days + (days == 1 ? " day" : " days") + " behind";
            svc.bankChanged(f, null, u.taken() > 0
                    ? "Upkeep of " + svc.format(cost) + " was not paid in full. The bank gave the " + svc.format(u.taken())
                            + " it held and is now " + behind
                    : "Upkeep of " + svc.format(cost) + " was not paid. The bank is now " + behind);
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
        double interest = books.interest(f, percent);
        if (interest <= 0) {
            return;
        }
        svc.bankChanged(f, null, "Earned " + svc.format(interest) + " in interest");
    }

    /**
     * Credits one minute of the hourly stipend to every online player. The minute's share is kept
     * aside until a whole cent, or a whole item for an item currency, has built up, so wallets
     * only ever hold whole cents.
     */
    void stipend(BalanceProvider provider) {
        double perMinute = ctx.settings().decimal("eco.playtimeStipendPerHour") / 60.0;
        if (perMinute <= 0) {
            return;
        }
        double step = provider.wholeUnits() ? 1.0 : 0.01;
        for (PlayerData p : new ArrayList<>(ctx.repo().loadedPlayers())) {
            if (!Players.isOnline(p.uuid())) {
                continue;
            }
            double owed = stipendCarry.merge(p.uuid(), perMinute, Double::sum);
            double pay = payable(owed, step);
            if (pay > 0 && provider.deposit(p.uuid(), pay, "Playtime stipend").success()) {
                stipendCarry.put(p.uuid(), Math.max(0, owed - pay));
            }
        }
    }

    /** The part of an owed stipend that can be paid now, in whole steps. */
    static double payable(double owed, double step) {
        return Money.round(Math.floor((owed + 1e-9) / step) * step);
    }

    /** Drops the stipend fraction kept for a player who left. */
    void forget(UUID player) {
        stipendCarry.remove(player);
    }
}
