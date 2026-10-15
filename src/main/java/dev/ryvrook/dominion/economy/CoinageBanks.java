package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.text.Messages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Faction banks held in Coinage shared accounts, used while the provider is coinage and eco.coinageBanks is on.
 * Each faction gets the account {@code dominion:<faction id>} the first time its bank is used, and the balance
 * Dominion kept so far moves into it once. From then on the account is the bank and {@code Faction.bank} is a
 * mirror of it, refreshed after every move, by the Coinage listener and once a minute, so the panel, the snapshot,
 * Atlas and Chronicle read the right number. Dominion's own checks, ranks, holds and the daily allowance, stay in
 * front of every move. Free of server and Coinage types, so it is tested over a fake {@link CoinagePort}.
 *
 * <p>Every method that moves money returns null on success or the reason the player reads. Coinage moves are
 * atomic on its side, so nothing here needs the ledger lock except the daily allowance.
 */
final class CoinageBanks {
    static final String PREFIX = "dominion:";

    private final CoinageBridge wallets;
    private final Object lock;
    private final Object seedLock = new Object();
    private final Predicate<Faction> gone;
    private final DoubleFunction<String> format;
    private final Consumer<String> warn;
    /** Coinage account id to faction id, for the listener. */
    private final Map<UUID, UUID> owners = new ConcurrentHashMap<>();
    /** The last name given to each account, so an unchanged name is not written again. */
    private final Map<UUID, String> labels = new ConcurrentHashMap<>();

    /**
     * @param wallets the coinage provider, which knows the port and the currency
     * @param lock    the ledger lock that guards the daily allowance
     * @param gone    true once a faction left the repository
     * @param format  money formatting for messages
     * @param warn    receives a log line when money could not be moved where it belongs
     */
    CoinageBanks(CoinageBridge wallets, Object lock, Predicate<Faction> gone, DoubleFunction<String> format, Consumer<String> warn) {
        this.wallets = wallets;
        this.lock = lock;
        this.gone = gone;
        this.format = format;
        this.warn = warn;
    }

    static String handle(Faction f) {
        return PREFIX + f.id();
    }

    /** True once the faction's bank moved into Coinage. */
    static boolean moved(Faction f) {
        return f.coinageAccount() != null;
    }

    /** The name the Coinage pages show for the account. */
    static String label(Faction f) {
        String name = f.name() == null || f.name().isBlank() ? f.tag() : f.name();
        return "[" + f.tag() + "] " + name;
    }

    /** The faction id of a Coinage account id, or null when it is not a faction bank seen since start. */
    UUID factionOf(UUID account) {
        return account == null ? null : owners.get(account);
    }

    // ---- the account -----------------------------------------------------------------------------------

    /**
     * The faction's account, created when missing. The first time, the balance Dominion kept moves in. An account
     * that already holds money while the faction has no handle yet was seeded before a crash, so it is not seeded
     * again. Null when Coinage is not linked or the account could not be made.
     */
    UUID account(Faction f) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return null;
        }
        String handle = handle(f);
        if (moved(f)) {
            UUID id = p.account(handle);
            if (id != null) {
                owners.put(id, f.id());
            }
            return id;
        }
        if (gone.test(f)) {
            return null;
        }
        synchronized (seedLock) {
            if (moved(f)) {
                return account(f);
            }
            int d = p.decimals(wallets.currency());
            if (d < 0) {
                return null;
            }
            UUID id = p.account(handle);
            boolean seed = id == null || p.balance(id, wallets.currency()) == 0;
            if (id == null) {
                id = p.createShared(handle, label(f), f.leader());
            }
            if (id == null) {
                return null;
            }
            double held;
            synchronized (f) {
                held = f.bank();
            }
            long minor = CoinageAmounts.floorMinor(held, d);
            if (seed && minor > 0) {
                CoinagePort.Move m = p.deposit(id, wallets.currency(), minor, Messages.get(EconomyText.REASON_BANK_TO_COINAGE));
                if (!m.ok()) {
                    warn.accept("Dominion could not move the bank of " + f.tag() + " into Coinage, " + m.failure()
                            + ". It stays in Dominion and is tried again on the next bank move");
                    return null;
                }
            }
            owners.put(id, f.id());
            f.setCoinageAccount(handle);
            mirror(f, p.balance(id, wallets.currency()), d);
            return id;
        }
    }

    /**
     * Moves a faction bank back out of Coinage, for a server that turned eco.coinageBanks off or left the coinage
     * provider while Coinage is still linked. True when the bank is Dominion's again.
     */
    boolean moveBack(Faction f) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return false;
        }
        synchronized (seedLock) {
            if (!moved(f)) {
                return true;
            }
            int d = p.decimals(wallets.currency());
            UUID id = p.account(f.coinageAccount());
            if (d < 0) {
                return false;
            }
            long bal = id == null ? 0 : p.balance(id, wallets.currency());
            if (bal > 0) {
                CoinagePort.Move m = p.withdraw(id, wallets.currency(), bal, Messages.get(EconomyText.REASON_BANK_FROM_COINAGE));
                if (!m.ok()) {
                    return false;
                }
            }
            if (id != null) {
                p.delete(id);
                owners.remove(id);
            }
            synchronized (f) {
                f.setBank(CoinageAmounts.toAmount(Math.max(0, bal), d));
                f.setCoinageAccount(null);
            }
            return true;
        }
    }

    /** Copies an account balance into Faction.bank. */
    void mirror(Faction f, long minor, int decimals) {
        if (decimals < 0) {
            return;
        }
        double v = CoinageAmounts.toAmount(Math.max(0, minor), decimals);
        synchronized (f) {
            if (Math.abs(f.bank() - v) > 1e-9) {
                f.setBank(v);
            }
        }
    }

    /** Reads the account and copies its balance into Faction.bank. */
    void refresh(Faction f) {
        CoinagePort p = wallets.live();
        if (p == null || !moved(f)) {
            return;
        }
        UUID id = p.account(f.coinageAccount());
        if (id != null) {
            owners.put(id, f.id());
            mirror(f, p.balance(id, wallets.currency()), p.decimals(wallets.currency()));
        }
    }

    /**
     * Makes the account's owner the leader and gives each other member the rights {@code rights} names, removing
     * members that have none or left. Rights are compared first, so a quiet minute changes nothing.
     */
    void sync(Faction f, List<UUID> members, Function<UUID, Set<CoinagePort.BankRight>> rights) {
        CoinagePort p = wallets.live();
        if (p == null || !moved(f)) {
            return;
        }
        UUID id = p.account(f.coinageAccount());
        if (id == null) {
            return;
        }
        UUID leader = f.leader();
        if (leader != null && !leader.equals(p.owner(id))) {
            p.setOwner(id, leader);
        }
        String name = label(f);
        Map<UUID, Set<CoinagePort.BankRight>> want = new HashMap<>();
        for (UUID m : members) {
            if (m.equals(leader)) {
                continue;
            }
            Set<CoinagePort.BankRight> r = rights.apply(m);
            if (r != null && !r.isEmpty()) {
                want.put(m, EnumSet.copyOf(r));
            }
        }
        Map<UUID, Set<CoinagePort.BankRight>> have = p.members(id);
        for (Map.Entry<UUID, Set<CoinagePort.BankRight>> e : want.entrySet()) {
            if (!e.getValue().equals(have.get(e.getKey()))) {
                p.setRights(id, e.getKey(), e.getValue());
            }
        }
        for (UUID m : have.keySet()) {
            if (!want.containsKey(m) && !m.equals(leader)) {
                p.setRights(id, m, Set.of());
            }
        }
        if (!name.equals(labels.get(id)) && p.rename(id, name)) {
            labels.put(id, name);
        }
    }

    // ---- moves -----------------------------------------------------------------------------------------

    /** A wallet into the faction bank. */
    String deposit(UUID actor, Faction f, double amount) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        if (gone.test(f)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        UUID id = account(f);
        if (id == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        p.ensurePlayer(actor);
        CoinagePort.Move m = p.transfer(actor, id, wallets.currency(), c.minor(), Messages.get(EconomyText.REASON_BANK_DEPOSIT));
        if (!m.ok()) {
            return CoinageBridge.reason(m, amount, () -> wallets.balance(actor), null, wallets.currency(), format);
        }
        mirror(f, m.toAfter(), p.decimals(wallets.currency()));
        return null;
    }

    /** The faction bank into a wallet. A positive limit is the actor's daily allowance in cents, zero for none. */
    String withdraw(Faction f, UUID actor, double amount, long limitCents) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        if (gone.test(f)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        UUID id = account(f);
        if (id == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        String denied = reserve(f, actor, amount, limitCents);
        if (denied != null) {
            return denied;
        }
        p.ensurePlayer(actor);
        CoinagePort.Move m = p.transfer(id, actor, wallets.currency(), c.minor(), Messages.get(EconomyText.REASON_BANK_WITHDRAWAL));
        if (!m.ok()) {
            release(f, actor, amount, limitCents);
            return bankReason(m, f, amount, null);
        }
        mirror(f, m.fromAfter(), p.decimals(wallets.currency()));
        return null;
    }

    /** A member pays another faction from their own bank, against their daily allowance. */
    String pay(Faction from, Faction to, UUID actor, double amount, long limitCents) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        if (gone.test(from) || gone.test(to)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        UUID a = account(from);
        UUID b = account(to);
        if (a == null || b == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        String denied = reserve(from, actor, amount, limitCents);
        if (denied != null) {
            return denied;
        }
        CoinagePort.Move m = p.transfer(a, b, wallets.currency(), c.minor(), Messages.get(EconomyText.REASON_BANK_PAYMENT));
        if (!m.ok()) {
            release(from, actor, amount, limitCents);
            return bankReason(m, from, amount, () -> Messages.get(EconomyText.BANK_OF_ONLY_HOLDS, "faction", from.tag(),
                    "amount", format.apply(from.bank())));
        }
        int d = p.decimals(wallets.currency());
        mirror(from, m.fromAfter(), d);
        mirror(to, m.toAfter(), d);
        return null;
    }

    /** Moves between two faction banks. */
    String move(Faction from, Faction to, double amount, String reason) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        if (gone.test(from) || gone.test(to)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        UUID a = account(from);
        UUID b = account(to);
        if (a == null || b == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        CoinagePort.Move m = p.transfer(a, b, wallets.currency(), c.minor(), reason);
        if (!m.ok()) {
            return bankReason(m, from, amount, () -> Messages.get(EconomyText.BANK_OF_ONLY_HOLDS, "faction", from.tag(),
                    "amount", format.apply(from.bank())));
        }
        int d = p.decimals(wallets.currency());
        mirror(from, m.fromAfter(), d);
        mirror(to, m.toAfter(), d);
        return null;
    }

    /** Takes from a bank, failing when it cannot cover the amount. The money leaves the economy. */
    String charge(Faction f, double amount, String reason) {
        CoinagePort p = wallets.live();
        UUID id = p == null ? null : account(f);
        if (id == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        CoinagePort.Move m = p.withdraw(id, wallets.currency(), c.minor(), reason);
        if (!m.ok()) {
            return bankReason(m, f, amount, () -> Messages.get(EconomyText.CHARGE_BANK_SHORT, "cost", format.apply(amount),
                    "faction", f.tag(), "amount", format.apply(f.bank())));
        }
        mirror(f, m.fromAfter(), p.decimals(wallets.currency()));
        return null;
    }

    /** Adds to a bank unless the faction is gone. */
    String credit(Faction f, double amount, String reason) {
        CoinagePort p = wallets.live();
        if (p == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        if (gone.test(f)) {
            return Messages.get(EconomyText.FACTION_GONE);
        }
        UUID id = account(f);
        if (id == null) {
            return Messages.get(EconomyText.NOT_RESPONDING);
        }
        CoinageBridge.Converted c = wallets.convert(p, amount);
        if (c.failure() != null) {
            return c.failure();
        }
        CoinagePort.Move m = p.deposit(id, wallets.currency(), c.minor(), reason);
        if (!m.ok()) {
            return CoinageBridge.reason(m, amount, () -> 0, null, wallets.currency(), format);
        }
        mirror(f, m.toAfter(), p.decimals(wallets.currency()));
        return null;
    }

    /**
     * What emptying a disbanded faction's account did. {@code held} is what the account held, {@code owed} the
     * part the caller still has to pay the leader, which is only more than zero when Coinage refused the direct
     * transfer.
     */
    record Drain(double held, double owed) {}

    /**
     * Empties the account of a faction that left the repository into its owner, the leader, or into the server
     * treasury, then deletes it. A deposit that lands in between is moved too, up to three rounds.
     */
    Drain drain(Faction f, boolean toServer, String reason) {
        CoinagePort p = wallets.live();
        String handle = f.coinageAccount();
        if (p == null || handle == null) {
            // The money stays in the Coinage account, which keeps the leader as owner, so the mirror must not pay it twice.
            if (handle != null) {
                warn.accept("Coinage was not linked when " + f.tag() + " was disbanded, so its bank stays in the Coinage account "
                        + handle + " owned by its leader");
            }
            synchronized (f) {
                f.setBank(0);
            }
            return new Drain(0, 0);
        }
        int d = p.decimals(wallets.currency());
        UUID id = p.account(handle);
        long heldMinor = 0;
        double owed = 0;
        for (int round = 0; id != null && d >= 0 && round < 3; round++) {
            long bal = p.balance(id, wallets.currency());
            if (bal > 0) {
                UUID target = toServer ? p.serverAccount() : p.owner(id);
                if (target == null) {
                    target = p.serverAccount();
                } else if (!toServer) {
                    p.ensurePlayer(target);
                }
                CoinagePort.Move m = p.transfer(id, target, wallets.currency(), bal, reason);
                if (m.ok()) {
                    heldMinor += bal;
                } else {
                    CoinagePort.Move w = p.withdraw(id, wallets.currency(), bal, reason);
                    if (w.ok()) {
                        heldMinor += bal;
                        owed += CoinageAmounts.toAmount(bal, d);
                    } else {
                        warn.accept("Dominion could not empty the Coinage account " + handle + " of the disbanded faction "
                                + f.tag() + ", " + w.failure());
                        break;
                    }
                }
            }
            if (p.delete(id)) {
                owners.remove(id);
                break;
            }
        }
        synchronized (f) {
            f.setBank(0);
        }
        return new Drain(d < 0 ? 0 : CoinageAmounts.toAmount(heldMinor, d), Money.round(owed));
    }

    // ---- daily cycle -----------------------------------------------------------------------------------

    /** Collects up to the dues from each member into the bank. missed[0] counts members who could not pay in full. */
    double dues(Faction f, List<UUID> payers, double dues, int[] missed) {
        CoinagePort p = wallets.live();
        UUID id = p == null ? null : account(f);
        int d = p == null ? -1 : p.decimals(wallets.currency());
        if (id == null || d < 0) {
            missed[0] += payers.size();
            return 0;
        }
        long owed = CoinageAmounts.floorMinor(dues, d);
        long total = 0;
        for (UUID m : payers) {
            long have = p.balance(m, wallets.currency());
            long take = Math.max(0, Math.min(have, owed));
            if (take > 0) {
                CoinagePort.Move mv = p.transfer(m, id, wallets.currency(), take, Messages.get(EconomyText.REASON_DUES));
                if (mv.ok()) {
                    mirror(f, mv.toAfter(), d);
                } else {
                    take = 0;
                }
            }
            if (take < owed) {
                missed[0]++;
            }
            total += take;
        }
        return CoinageAmounts.toAmount(total, d);
    }

    /** Charges the upkeep. A bank that cannot cover it gives what it holds. Null when Coinage is not linked. */
    Books.Upkeep upkeep(Faction f, double cost) {
        CoinagePort p = wallets.live();
        UUID id = p == null ? null : account(f);
        int d = p == null ? -1 : p.decimals(wallets.currency());
        if (id == null || d < 0) {
            return null;
        }
        long want = CoinageAmounts.floorMinor(cost, d);
        long bal = p.balance(id, wallets.currency());
        long take = Math.max(0, Math.min(bal, want));
        if (take > 0) {
            CoinagePort.Move m = p.withdraw(id, wallets.currency(), take, Messages.get(EconomyText.REASON_UPKEEP));
            if (!m.ok()) {
                return new Books.Upkeep(false, 0);
            }
            mirror(f, m.fromAfter(), d);
        }
        return new Books.Upkeep(bal >= want, CoinageAmounts.toAmount(take, d));
    }

    /** Pays interest on the bank, rounded down to whole minor units, and returns it. */
    double interest(Faction f, double percent) {
        CoinagePort p = wallets.live();
        UUID id = p == null ? null : account(f);
        int d = p == null ? -1 : p.decimals(wallets.currency());
        if (id == null || d < 0) {
            return 0;
        }
        long bal = p.balance(id, wallets.currency());
        long interest = interestOf(bal, percent);
        if (interest <= 0) {
            return 0;
        }
        CoinagePort.Move m = p.deposit(id, wallets.currency(), interest, Messages.get(EconomyText.REASON_INTEREST));
        if (!m.ok()) {
            return 0;
        }
        mirror(f, m.toAfter(), d);
        return CoinageAmounts.toAmount(interest, d);
    }

    static long interestOf(long balance, double percent) {
        if (balance <= 0 || percent <= 0 || Double.isNaN(percent) || Double.isInfinite(percent)) {
            return 0;
        }
        return BigDecimal.valueOf(balance).multiply(BigDecimal.valueOf(percent)).movePointLeft(2)
                .setScale(0, RoundingMode.DOWN).longValue();
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    /** Checks the daily allowance and books the amount against it. Null when it fits. */
    private String reserve(Faction f, UUID actor, double amount, long limitCents) {
        if (limitCents <= 0) {
            return null;
        }
        synchronized (lock) {
            String denied = limitDenied(f, actor, amount, limitCents, format);
            if (denied == null) {
                WithdrawLimits.record(f, actor, amount);
            }
            return denied;
        }
    }

    private void release(Faction f, UUID actor, double amount, long limitCents) {
        if (limitCents > 0) {
            synchronized (lock) {
                WithdrawLimits.record(f, actor, -amount);
            }
        }
    }

    /** Why the amount is over what the actor may still take today, or null. Caller holds the ledger lock. */
    static String limitDenied(Faction f, UUID actor, double amount, long limitCents, DoubleFunction<String> format) {
        if (limitCents <= 0) {
            return null;
        }
        long left = limitCents - WithdrawLimits.usedToday(f, actor);
        if (Money.cents(amount) > left) {
            return Messages.get(EconomyText.WITHDRAW_LEFT_TODAY, "amount", format.apply(Math.max(0, left) / 100.0));
        }
        return null;
    }

    /**
     * The reason for a failed move out of a bank. A short bank refreshes the mirror first so the sentence names
     * what the bank holds now.
     */
    private String bankReason(CoinagePort.Move m, Faction f, double amount, java.util.function.Supplier<String> shortBank) {
        if (m.failure() == CoinagePort.Failure.INSUFFICIENT_FUNDS) {
            refresh(f);
            return shortBank != null ? shortBank.get()
                    : Messages.get(EconomyText.BANK_ONLY_HOLDS, "amount", format.apply(f.bank()));
        }
        return CoinageBridge.reason(m, amount, () -> 0, null, wallets.currency(), format);
    }
}
