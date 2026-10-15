package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upkeep, dues, interest and the playtime stipend. The money moves themselves live in {@link Books},
 * or in {@link CoinageBanks} for a bank that lives in Coinage.
 */
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
            if (f.isSystem()) {
                continue;
            }
            // A bank in Coinage while Coinage is not linked waits for a later run rather than being charged twice.
            EconomyServiceImpl.Route route = svc.route(f);
            if (route == EconomyServiceImpl.Route.DOWN || !firstRunToday(f)) {
                continue;
            }
            CoinageBanks coinage = route == EconomyServiceImpl.Route.COINAGE ? svc.coinageBanks() : null;
            if (dues > 0) {
                collectDues(svc, coinage, f, dues);
            }
            if (upkeepPerClaim > 0) {
                chargeUpkeep(svc, coinage, f, upkeepPerClaim);
            }
            if (interestPercent > 0) {
                payInterest(svc, coinage, f, interestPercent);
            }
        }
    }

    private void collectDues(EconomyServiceImpl svc, CoinageBanks coinage, Faction f, double dues) {
        int[] missed = {0};
        List<Member> members = f.members();
        BalanceProvider provider = svc.provider();
        double total;
        if (coinage != null) {
            List<UUID> payers = new ArrayList<>();
            for (Member m : members) {
                payers.add(m.uuid());
            }
            total = coinage.dues(f, payers, dues, missed);
        } else if (provider instanceof InternalLedger) {
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
            String text = missed[0] == 0 ? Messages.get(EconomyText.DUES_COLLECTED, "amount", svc.format(total))
                    : missed[0] == 1 ? Messages.get(EconomyText.DUES_COLLECTED_ONE_MISSED, "amount", svc.format(total))
                    : Messages.get(EconomyText.DUES_COLLECTED_MISSED, "amount", svc.format(total), "count", missed[0]);
            svc.bankChanged(f, null, text);
        }
    }

    private void chargeUpkeep(EconomyServiceImpl svc, CoinageBanks coinage, Faction f, double perClaim) {
        int claims = ctx.repo().claimCount(f.id());
        if (claims <= 0) {
            resetUnpaid(f);
            return;
        }
        double cost = Money.round(claims * perClaim);
        Books.Upkeep u = coinage != null ? coinage.upkeep(f, cost) : books.upkeep(f, cost);
        if (u == null) {
            return;
        }
        if (u.paid()) {
            resetUnpaid(f);
            svc.bankChanged(f, null, claims == 1
                    ? Messages.get(EconomyText.UPKEEP_PAID_ONE, "amount", svc.format(cost))
                    : Messages.get(EconomyText.UPKEEP_PAID, "amount", svc.format(cost), "claims", claims));
        } else {
            long days = f.addStat(UPKEEP_UNPAID, 1);
            String text;
            if (u.taken() > 0) {
                text = days == 1
                        ? Messages.get(EconomyText.UPKEEP_PARTIAL_ONE_DAY, "amount", svc.format(cost), "taken", svc.format(u.taken()))
                        : Messages.get(EconomyText.UPKEEP_PARTIAL, "amount", svc.format(cost), "taken", svc.format(u.taken()), "days", days);
            } else {
                text = days == 1
                        ? Messages.get(EconomyText.UPKEEP_UNPAID_ONE_DAY, "amount", svc.format(cost))
                        : Messages.get(EconomyText.UPKEEP_UNPAID, "amount", svc.format(cost), "days", days);
            }
            svc.bankChanged(f, null, text);
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

    private void payInterest(EconomyServiceImpl svc, CoinageBanks coinage, Faction f, double percent) {
        double interest = coinage != null ? coinage.interest(f, percent) : books.interest(f, percent);
        if (interest <= 0) {
            return;
        }
        svc.bankChanged(f, null, Messages.get(EconomyText.INTEREST_EARNED, "amount", svc.format(interest)));
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
        double step = provider.step();
        for (PlayerData p : new ArrayList<>(ctx.repo().loadedPlayers())) {
            if (!Players.isOnline(p.uuid())) {
                continue;
            }
            double owed = stipendCarry.merge(p.uuid(), perMinute, Double::sum);
            double pay = payable(owed, step);
            if (pay > 0 && provider.deposit(p.uuid(), pay, Messages.get(EconomyText.REASON_STIPEND)).success()) {
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
