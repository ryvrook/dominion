package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.text.Messages;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Predicate;

/**
 * The money moves behind the economy service, kept free of the server so they can be tested on
 * their own. Wallet and bank changes in the internal ledger run under the one ledger lock with the
 * faction monitor inside it, so every check and set is atomic. Faction monitors are always taken
 * in id order. External providers are only called outside the ledger lock, so a slow provider
 * never stalls the banks. Every method that moves money either moves all of it or none of it, and
 * returns null on success or the reason the player reads.
 *
 * Internal wallets are reached through {@link Wallets} by player id inside the lock, never as a
 * record fetched earlier. A wallet is changed while holding the record's monitor and only after
 * checking it is still the loaded record, so a record unloaded in between is fetched again rather
 * than changed after its last save.
 */
final class Books {

    /** Where internal wallet records come from. */
    interface Wallets {
        /** The record of a player, loading it when needed. */
        PlayerData load(UUID player);

        /** True while the record is the one the repository holds, so a change to it will be saved. */
        boolean live(PlayerData p);
    }

    /** Somewhere to keep coins that could not be put back into a wallet, such as the market mailbox. */
    interface Stranded {
        /** True when the coins were kept for the player. */
        boolean park(UUID player, double amount);
    }

    private final Object lock;
    private final Predicate<Faction> gone;
    private final DoubleFunction<String> format;
    private final Consumer<String> severe;
    private final Wallets wallets;
    private final Stranded stranded;

    /**
     * @param lock the shared ledger lock
     * @param gone true once a faction left the repository, checked under the ledger lock
     * @param format money formatting for messages
     * @param severe receives a log line when money could not be put back or kept anywhere
     * @param wallets the internal wallet records
     * @param stranded keeps coins that could not be put back into a wallet
     */
    Books(Object lock, Predicate<Faction> gone, DoubleFunction<String> format, Consumer<String> severe,
          Wallets wallets, Stranded stranded) {
        this.lock = lock;
        this.gone = gone;
        this.format = format;
        this.severe = severe;
        this.wallets = wallets;
        this.stranded = stranded;
    }

    Object lock() {
        return lock;
    }

    /** Runs the change on the live record of one player. Caller holds the ledger lock. */
    private <R> R withWallet(UUID id, java.util.function.Function<PlayerData, R> change) {
        while (true) {
            PlayerData p = wallets.load(id);
            synchronized (p) {
                if (wallets.live(p)) {
                    return change.apply(p);
                }
            }
        }
    }

    /** Runs the change on the live records of two different players, monitors taken in id order. */
    private <R> R withWallets(UUID a, UUID b, java.util.function.BiFunction<PlayerData, PlayerData, R> change) {
        boolean aFirst = a.compareTo(b) < 0;
        while (true) {
            PlayerData pa = wallets.load(a);
            PlayerData pb = wallets.load(b);
            PlayerData first = aFirst ? pa : pb;
            PlayerData second = aFirst ? pb : pa;
            synchronized (first) {
                synchronized (second) {
                    if (wallets.live(pa) && wallets.live(pb)) {
                        return change.apply(pa, pb);
                    }
                }
            }
        }
    }

    /** Puts coins back that a provider refused, parking them when even the refund fails. */
    private void refund(BalanceProvider p, UUID player, double amount, String reason, String context) {
        if (p.deposit(player, amount, reason).success()) {
            return;
        }
        if (stranded.park(player, amount)) {
            severe.accept("Dominion could not refund " + format.apply(amount) + " to " + player + " " + context
                    + ", so it was kept in their market mailbox");
            return;
        }
        severe.accept("Dominion could not refund " + format.apply(amount) + " to " + player + " " + context);
    }

    // ---- internal wallets -------------------------------------------------------

    /** Takes from an internal wallet. */
    String walletWithdraw(UUID player, double amount) {
        synchronized (lock) {
            return withWallet(player, p -> {
                if (!Money.covers(p.balance(), amount)) {
                    return need(amount, p.balance());
                }
                p.setBalance(Money.round(p.balance() - amount));
                return null;
            });
        }
    }

    /** Adds to an internal wallet. */
    void walletDeposit(UUID player, double amount) {
        synchronized (lock) {
            withWallet(player, p -> {
                p.setBalance(Money.round(p.balance() + amount));
                return null;
            });
        }
    }

    /** Sets an internal wallet, never below zero. */
    void walletSet(UUID player, double amount) {
        synchronized (lock) {
            withWallet(player, p -> {
                p.setBalance(Money.round(Math.max(0, amount)));
                return null;
            });
        }
    }

    private String need(double amount, double have) {
        return Messages.get(EconomyText.NEED_HAVE, "amount", format.apply(amount), "have", format.apply(have));
    }

    // ---- wallets ------------------------------------------------------------

    /** Moves between two internal wallets under the ledger lock. */
    String transferInternal(UUID fromId, UUID toId, double amount) {
        if (fromId.equals(toId)) {
            return Messages.get(EconomyText.PAY_YOURSELF);
        }
        synchronized (lock) {
            return withWallets(fromId, toId, (from, to) -> {
                if (!Money.covers(from.balance(), amount)) {
                    return need(amount, from.balance());
                }
                from.setBalance(Money.round(from.balance() - amount));
                to.setBalance(Money.round(to.balance() + amount));
                return null;
            });
        }
    }

    /** Moves between two wallets of an external provider, refunding the payer when the payee refuses. */
    String transferExternal(BalanceProvider p, UUID from, UUID to, double amount, String reason) {
        dev.ryvrook.dominion.api.Result taken = p.withdraw(from, amount, reason);
        if (taken.failed()) {
            return taken.message();
        }
        dev.ryvrook.dominion.api.Result given = p.deposit(to, amount, reason);
        if (given.failed()) {
            refund(p, from, amount, Messages.get(EconomyText.REASON_REFUND_FAILED), "after a failed payment to " + to);
            return given.message();
        }
        return null;
    }

    // ---- wallet and bank ------------------------------------------------------

    /** Internal wallet into the faction bank. */
    String depositInternal(UUID payerId, Faction f, double amount) {
        synchronized (lock) {
            return withWallet(payerId, payer -> {
                if (!Money.covers(payer.balance(), amount)) {
                    return need(amount, payer.balance());
                }
                if (gone.test(f)) {
                    return Messages.get(EconomyText.FACTION_GONE);
                }
                payer.setBalance(Money.round(payer.balance() - amount));
                synchronized (f) {
                    f.setBank(Money.round(f.bank() + amount));
                }
                return null;
            });
        }
    }

    /** External wallet into the faction bank. The wallet is refunded when the faction is gone by then. */
    String depositExternal(BalanceProvider p, UUID actor, Faction f, double amount) {
        dev.ryvrook.dominion.api.Result taken = p.withdraw(actor, amount, Messages.get(EconomyText.REASON_BANK_DEPOSIT));
        if (taken.failed()) {
            return taken.message();
        }
        boolean credited = false;
        synchronized (lock) {
            if (!gone.test(f)) {
                synchronized (f) {
                    f.setBank(Money.round(f.bank() + amount));
                }
                credited = true;
            }
        }
        if (!credited) {
            refund(p, actor, amount, Messages.get(EconomyText.REASON_REFUND_DISBANDED), "after their faction was disbanded");
            return Messages.get(EconomyText.FACTION_GONE);
        }
        return null;
    }

    /**
     * Faction bank into an internal wallet. A positive limit is the actor's daily allowance in
     * cents, zero means no limit.
     */
    String withdrawInternal(Faction f, UUID actor, double amount, long limitCents) {
        synchronized (lock) {
            return withWallet(actor, receiver -> {
                String denied = debit(f, actor, amount, limitCents);
                if (denied != null) {
                    return denied;
                }
                receiver.setBalance(Money.round(receiver.balance() + amount));
                return null;
            });
        }
    }

    /**
     * Faction bank into an external wallet. The bank is debited first, then the wallet is paid
     * outside the lock. A refused payment puts the money and the allowance back.
     */
    String withdrawExternal(BalanceProvider p, Faction f, UUID actor, double amount, long limitCents) {
        synchronized (lock) {
            String denied = debit(f, actor, amount, limitCents);
            if (denied != null) {
                return denied;
            }
        }
        dev.ryvrook.dominion.api.Result given = p.deposit(actor, amount, Messages.get(EconomyText.REASON_BANK_WITHDRAWAL));
        if (given.failed()) {
            boolean refunded = false;
            synchronized (lock) {
                if (!gone.test(f)) {
                    synchronized (f) {
                        f.setBank(Money.round(f.bank() + amount));
                    }
                    refunded = true;
                }
                if (limitCents > 0) {
                    WithdrawLimits.record(f, actor, -amount);
                }
            }
            if (!refunded) {
                // The bank is gone, so the money goes to the member who withdrew it rather than vanishing.
                if (stranded.park(actor, amount)) {
                    severe.accept("Dominion could not pay " + format.apply(amount) + " withdrawn from the bank of " + f.tag()
                            + " which was disbanded meanwhile, so it was kept in the market mailbox of " + actor);
                } else {
                    severe.accept("Dominion could not return " + format.apply(amount) + " to the bank of " + f.tag()
                            + " because it was disbanded during the withdrawal");
                }
            }
            return given.message();
        }
        return null;
    }

    /** Takes from the bank against the daily allowance. Caller holds the ledger lock. Null on success. */
    private String debit(Faction f, UUID actor, double amount, long limitCents) {
        String limit = limitDenied(f, actor, amount, limitCents);
        if (limit != null) {
            return limit;
        }
        if (gone.test(f)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        synchronized (f) {
            if (!Money.covers(f.bank(), amount)) {
                return Messages.get(EconomyText.BANK_ONLY_HOLDS, "amount", format.apply(f.bank()));
            }
            f.setBank(Money.round(f.bank() - amount));
        }
        if (limitCents > 0) {
            WithdrawLimits.record(f, actor, amount);
        }
        return null;
    }

    private String limitDenied(Faction f, UUID actor, double amount, long limitCents) {
        if (limitCents <= 0) {
            return null;
        }
        long left = limitCents - WithdrawLimits.usedToday(f, actor);
        if (Money.cents(amount) > left) {
            return Messages.get(EconomyText.WITHDRAW_LEFT_TODAY, "amount", format.apply(Math.max(0, left) / 100.0));
        }
        return null;
    }

    // ---- bank to bank -----------------------------------------------------------

    /** A member pays another faction from their own bank, against their daily allowance. */
    String pay(Faction from, Faction to, UUID actor, double amount, long limitCents) {
        synchronized (lock) {
            String limit = limitDenied(from, actor, amount, limitCents);
            if (limit != null) {
                return limit;
            }
            String moved = moveLocked(from, to, amount);
            if (moved != null) {
                return moved;
            }
            if (limitCents > 0) {
                WithdrawLimits.record(from, actor, amount);
            }
        }
        return null;
    }

    /** Moves between two faction banks. */
    String move(Faction from, Faction to, double amount) {
        synchronized (lock) {
            return moveLocked(from, to, amount);
        }
    }

    /** Caller holds the ledger lock. Faction monitors are taken in id order so crossing payments cannot deadlock. */
    private String moveLocked(Faction from, Faction to, double amount) {
        if (gone.test(from) || gone.test(to)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        Faction first = from.id().compareTo(to.id()) < 0 ? from : to;
        Faction second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!Money.covers(from.bank(), amount)) {
                    return Messages.get(EconomyText.BANK_OF_ONLY_HOLDS, "faction", from.tag(), "amount", format.apply(from.bank()));
                }
                from.setBank(Money.round(from.bank() - amount));
                to.setBank(Money.round(to.bank() + amount));
            }
        }
        return null;
    }

    // ---- bank charges for other modules --------------------------------------------

    /** Takes from a bank, failing when it cannot cover the amount. */
    String charge(Faction f, double amount) {
        synchronized (lock) {
            synchronized (f) {
                if (!Money.covers(f.bank(), amount)) {
                    return Messages.get(EconomyText.CHARGE_BANK_SHORT, "cost", format.apply(amount), "faction", f.tag(),
                            "amount", format.apply(f.bank()));
                }
                f.setBank(Money.round(f.bank() - amount));
            }
        }
        return null;
    }

    /** Adds to a bank unless the faction is gone. */
    String credit(Faction f, double amount) {
        synchronized (lock) {
            if (gone.test(f)) {
                return Messages.get(EconomyText.FACTION_GONE);
            }
            synchronized (f) {
                f.setBank(Money.round(f.bank() + amount));
            }
        }
        return null;
    }

    /** Zeroes a bank and returns what it held. */
    double drain(Faction f) {
        synchronized (lock) {
            synchronized (f) {
                double held = Money.round(f.bank());
                f.setBank(0);
                return held;
            }
        }
    }

    // ---- daily cycle ------------------------------------------------------------------

    /**
     * Collects up to the dues from each internal wallet into the bank in whole cents, so what
     * leaves the wallets is exactly what reaches the bank. missed[0] counts members who could not
     * pay in full.
     * Returns the total collected.
     */
    double duesInternal(Faction f, List<UUID> payers, double dues, int[] missed) {
        long owed = Money.cents(Money.round(dues));
        long total = 0;
        synchronized (lock) {
            for (UUID id : payers) {
                long paid = withWallet(id, p -> {
                    long have = (long) Math.floor(p.balance() * 100.0 + 1e-6);
                    long take = Math.max(0, Math.min(have, owed));
                    if (take > 0) {
                        p.setBalance(Money.round(p.balance() - take / 100.0));
                    }
                    return take;
                });
                if (paid < owed) {
                    missed[0]++;
                }
                total += paid;
            }
            if (total > 0) {
                synchronized (f) {
                    f.setBank(Money.round(f.bank() + total / 100.0));
                }
            }
        }
        return total / 100.0;
    }

    /**
     * Collects dues from external wallets, then credits the bank once. A whole unit provider is
     * charged whole items. missed[0] counts members who could not pay in full. Returns the total.
     */
    double duesExternal(BalanceProvider provider, Faction f, List<UUID> members, double dues, int[] missed) {
        double total = 0;
        for (UUID m : members) {
            double have = provider.balance(m);
            double step = provider.step();
            double paid = provider.wholeUnits()
                    ? Math.min(Math.floor(have + 1e-6), Math.ceil(dues - 1e-6))
                    : step > 0.01 ? DailyEconomy.payable(Math.min(have, dues), step)
                    : Money.round(Math.min(have, dues));
            if (paid > 0 && provider.withdraw(m, paid, Messages.get(EconomyText.REASON_DUES)).failed()) {
                paid = 0;
            }
            if (paid + 1e-6 < dues) {
                missed[0]++;
            }
            total += Math.max(0, paid);
        }
        total = Money.round(total);
        if (total > 0) {
            synchronized (lock) {
                synchronized (f) {
                    f.setBank(Money.round(f.bank() + total));
                }
            }
        }
        return total;
    }

    /** What an upkeep charge did. Paid is false when the bank could only give part, which it then gives. */
    record Upkeep(boolean paid, double taken) {}

    /** Charges the upkeep. A bank that cannot cover it is emptied instead. */
    Upkeep upkeep(Faction f, double cost) {
        synchronized (lock) {
            synchronized (f) {
                boolean paid = Money.covers(f.bank(), cost);
                // A bank at or below zero gives nothing, so upkeep never lifts a negative bank to zero.
                double taken = f.bank() <= 0 ? 0 : Money.round(paid ? Math.min(cost, f.bank()) : f.bank());
                f.setBank(Money.round(f.bank() - taken));
                return new Upkeep(paid, taken);
            }
        }
    }

    /** Pays interest on the bank and returns it, zero when nothing was paid. */
    double interest(Faction f, double percent) {
        synchronized (lock) {
            synchronized (f) {
                double interest = Money.round(f.bank() * percent / 100.0);
                if (interest <= 0) {
                    return 0;
                }
                f.setBank(Money.round(f.bank() + interest));
                return interest;
            }
        }
    }
}
