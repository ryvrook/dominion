package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Economy service. Player money lives with the provider chosen by eco.provider, which is the
 * internal ledger in PlayerData.balance, a VaultUnlocked economy, an item currency or Coinage, and
 * auto picks Coinage, then VaultUnlocked, then the internal ledger. Faction money lives in
 * Faction.bank, or in a Coinage shared account mirrored into Faction.bank while the provider is
 * coinage and eco.coinageBanks is on, see {@link CoinageBanks}. Bank changes run under one ledger lock and the faction monitor, so
 * check then set is atomic. Lock order is always ledger first, then factions by id. External
 * providers are only called outside the ledger lock, so a slow provider never stalls the banks.
 */
public final class EconomyServiceImpl implements EconomyService {

    private final DominionContext ctx;
    private final Object lock = new Object();
    private final DailyEconomy daily;
    private final InternalLedger internal;
    private final ItemCurrency items;
    private volatile VaultUnlockedBridge vault;
    private volatile long vaultRetryAt;
    private volatile boolean vaultWarned;
    private volatile boolean unknownWarned;
    private final LedgerMigration migration;
    private final Books books;
    private final CoinageSeam seam = new CoinageSeam(this);
    private final CoinageBridge coinage;
    private final CoinageBanks coinageBanks;
    private final LedgerMigration coinageMigration;
    private volatile boolean coinageWarned;
    /**
     * Allowance booked by {@link #coinageVeto} for a withdrawal a player started in Coinage, by the thread that runs
     * it. Coinage calls the veto and, when the move went through, the after listener on that same thread, so
     * {@link #coinageMoved} settles the booking. A booking that is still here when its thread asks again, or after
     * {@link #HELD_MILLIS}, belongs to a move that failed after the veto and is given back. Every booking left at
     * shutdown is given back too, since the next instance never sees it.
     */
    private final Map<Long, Held> held = new java.util.concurrent.ConcurrentHashMap<>();
    static final long HELD_MILLIS = 10_000L;
    /** Set at shutdown, after which the veto books nothing. */
    private volatile boolean closed;
    /** Wall clock of the bookings, replaced in tests. */
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    private record Held(Faction faction, UUID player, double amount, long at) {}

    /** Player to faction for the Coinage net worth part, rebuilt every minute so the part reads memory only. */
    private volatile Map<UUID, UUID> memberIndex = Map.of();

    public EconomyServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.books = new Books(lock, this::gone, this::format, line -> ctx.logger().at(Level.SEVERE).log("%s", line),
                new Books.Wallets() {
                    @Override
                    public PlayerData load(UUID player) {
                        return ctx.player(player);
                    }

                    @Override
                    public boolean live(PlayerData p) {
                        return ctx.repo().playerIfLoaded(p.uuid()) == p;
                    }
                }, this::park);
        this.daily = new DailyEconomy(ctx, books);
        this.internal = new InternalLedger(ctx, books);
        this.items = new ItemCurrency(ctx, this::format);
        this.migration = new LedgerMigration(ctx, lock, this::format);
        this.coinage = new CoinageBridge(seam::port, () -> ctx.settings().text("eco.coinageCurrency"), this::format);
        this.coinageBanks = new CoinageBanks(coinage, lock, this::gone, this::format,
                line -> ctx.logger().at(Level.WARNING).log("%s", line));
        this.coinageMigration = new LedgerMigration(ctx, lock, this::format, "Coinage", "coinage-migrated.json");
    }

    /** Where the Coinage link attaches, registered as a service under its own class. */
    CoinageSeam seam() {
        return seam;
    }

    @Override
    public boolean enabled() {
        return ctx.settings().bool("eco.enabled");
    }

    @Override
    public String currencyName() {
        return ctx.settings().text("eco.currencyName");
    }

    @Override
    public String format(double amount) {
        return Money.format(amount, ctx.settings().text("eco.currencySymbol"));
    }

    @Override
    public double balance(UUID player) {
        if (!enabled() || player == null) {
            return 0;
        }
        return provider().balance(player);
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        Result check = validate(amount);
        if (check != null) {
            return check;
        }
        Result r = provider().deposit(player, amount, reason);
        return r.failed() ? r : Result.pass(Messages.get(EconomyText.RECEIVED, "amount", format(amount)));
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        Result check = validate(amount);
        if (check != null) {
            return check;
        }
        Result r = provider().withdraw(player, amount, reason);
        return r.failed() ? r : Result.pass(Messages.get(EconomyText.PAID, "amount", format(amount)));
    }

    @Override
    public Result transfer(UUID from, UUID to, double amount, String reason) {
        Result check = validateWallet(amount);
        if (check != null) {
            return check;
        }
        if (from.equals(to)) {
            return Result.fail(Messages.get(EconomyText.PAY_YOURSELF));
        }
        BalanceProvider p = provider();
        String failed = p == internal
                ? books.transferInternal(from, to, amount)
                : books.transferExternal(p, from, to, amount, reason);
        return failed != null ? Result.fail(failed) : Result.pass(Messages.get(EconomyText.SENT, "amount", format(amount)));
    }

    @Override
    public Result bankDeposit(UUID actor, double amount) {
        Result check = validateWallet(amount);
        if (check != null) {
            return check;
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!factions().can(actor, FactionAction.DEPOSIT)) {
            return Result.fail(Messages.get(EconomyText.RANK_NO_DEPOSIT));
        }
        BalanceProvider p = provider();
        Route route = route(f);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.deposit(actor, f, amount)
                : p == internal ? books.depositInternal(actor, f, amount)
                : books.depositExternal(p, actor, f, amount);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, Messages.get(EconomyText.DEPOSITED_LOG, "amount", format(amount)));
        ctx.bus().publish(new DominionEvent.BankDeposited(f.id(), actor, amount));
        return Result.pass(Messages.get(EconomyText.DEPOSITED, "amount", format(amount)));
    }

    @Override
    public Result bankWithdraw(UUID actor, double amount) {
        Result check = validateWallet(amount);
        if (check != null) {
            return check;
        }
        Faction f = factions().factionOf(actor);
        Result denied = withdrawDenied(f, actor);
        if (denied != null) {
            return denied;
        }
        BalanceProvider p = provider();
        long limit = limitCents(f, actor);
        Route route = route(f);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.withdraw(f, actor, amount, limit)
                : p == internal ? books.withdrawInternal(f, actor, amount, limit)
                : books.withdrawExternal(p, f, actor, amount, limit);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, Messages.get(EconomyText.WITHDREW_LOG, "amount", format(amount)));
        ctx.bus().publish(new DominionEvent.BankWithdrawn(f.id(), actor, amount));
        return Result.pass(Messages.get(EconomyText.WITHDREW, "amount", format(amount)));
    }

    @Override
    public Result bankPay(UUID actor, String targetTag, double amount) {
        Result check = validate(amount);
        if (check != null) {
            return check;
        }
        Faction f = factions().factionOf(actor);
        Result denied = withdrawDenied(f, actor);
        if (denied != null) {
            return denied;
        }
        Faction target = factions().byTag(targetTag);
        if (target == null) {
            return Result.fail(Messages.get(EconomyText.NO_FACTION_WITH_TAG));
        }
        if (target.id().equals(f.id())) {
            return Result.fail(Messages.get(EconomyText.PAY_OWN_FACTION));
        }
        Route route = route(f, target);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.pay(f, target, actor, amount, limitCents(f, actor))
                : books.pay(f, target, actor, amount, limitCents(f, actor));
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, Messages.get(EconomyText.PAID_TO, "amount", format(amount), "faction", target.tag()));
        bankChanged(target, actor, Messages.get(EconomyText.RECEIVED_FROM, "amount", format(amount), "faction", f.tag()));
        return Result.pass(Messages.get(EconomyText.PAID_TO, "amount", format(amount), "faction", target.tag()));
    }

    @Override
    public void set(UUID player, double amount) {
        provider().set(player, amount);
    }

    @Override
    public List<Faction> richestFactions(int limit) {
        List<Faction> list = new ArrayList<>();
        for (Faction f : ctx.repo().factions()) {
            if (!f.isSystem()) {
                list.add(f);
            }
        }
        list.sort(Comparator.comparingDouble(Faction::bank).reversed());
        return list.subList(0, Math.min(Math.max(0, limit), list.size()));
    }

    /** Ranks players that are loaded in memory, which covers online players and faction members touched by ticks. */
    @Override
    public List<UUID> richestPlayers(int limit) {
        BalanceProvider p = provider();
        List<UUID> list = new ArrayList<>();
        for (PlayerData pd : ctx.repo().loadedPlayers()) {
            list.add(pd.uuid());
        }
        if (p == internal) {
            list.sort(Comparator.comparingDouble((UUID u) -> ctx.player(u).balance()).reversed());
        } else {
            Map<UUID, Double> known = new HashMap<>();
            for (UUID u : list) {
                known.put(u, p.cachedBalance(u));
            }
            list.sort(Comparator.comparingDouble((UUID u) -> known.get(u)).reversed());
        }
        return new ArrayList<>(list.subList(0, Math.min(Math.max(0, limit), list.size())));
    }

    @Override
    public void dailyTick() {
        if (enabled()) {
            daily.run(this);
        }
    }

    /** Called every minute. Credits the playtime stipend to online players. */
    void stipendTick() {
        if (enabled()) {
            daily.stipend(provider());
        }
    }

    /**
     * Called every minute. While the active provider is VaultUnlocked or Coinage, moves internal
     * balances above zero into it at most once a day. A balance is zeroed and saved before it is
     * deposited, so no balance ever moves twice.
     */
    void migrationTick() {
        if (!enabled()) {
            return;
        }
        BalanceProvider p = provider();
        if (p == coinage) {
            coinageMigration.maybeRun(coinage);
        } else if (p instanceof VaultUnlockedBridge v) {
            migration.maybeRun(v);
        }
    }

    void forget(UUID player) {
        daily.forget(player);
    }

    /** Lets the provider hand out anything it owes the player. */
    void onReady(UUID player) {
        if (enabled()) {
            provider().onReady(player);
        }
    }

    // ---- helpers ---------------------------------------------------------

    private Result validate(double amount) {
        if (!enabled()) {
            return Result.fail(Messages.get(EconomyText.DISABLED));
        }
        String reason = Money.invalid(amount);
        return reason == null ? null : Result.fail(reason);
    }

    /** Validation for amounts a player moves by hand, which must be whole when the currency is an item. */
    private Result validateWallet(double amount) {
        Result r = validate(amount);
        if (r != null) {
            return r;
        }
        if (provider().wholeUnits() && Math.abs(amount - Math.rint(amount)) > 1e-6) {
            return Result.fail(Messages.get(EconomyText.WHOLE_AMOUNTS));
        }
        return null;
    }

    private Result withdrawDenied(Faction f, UUID actor) {
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        Member m = f.member(actor);
        if (m != null && !actor.equals(f.leader()) && m.onHold(Time.now())) {
            return Result.fail(Messages.get(EconomyText.NEW_MEMBER_HOLD, "time", Time.remaining(m.holdUntil() - Time.now())));
        }
        if (!factions().can(actor, FactionAction.WITHDRAW)) {
            return Result.fail(Messages.get(EconomyText.RANK_NO_WITHDRAW));
        }
        return null;
    }

    /** The actor's daily bank allowance in cents, or zero when the leader or the setting sets no limit. */
    private long limitCents(Faction f, UUID actor) {
        if (actor.equals(f.leader())) {
            return 0;
        }
        double limit = ctx.settings().decimal("eco.withdrawDailyLimitOfficer");
        return limit > 0 ? Money.cents(limit) : 0;
    }

    void bankChanged(Faction f, UUID who, String text) {
        String name = who == null ? Messages.get(CommonText.SERVER) : ctx.player(who).name();
        factions().audit(f, who, name, "bank", text);
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    // ---- faction banks for other modules -------------------------------------

    @Override
    public Result bankCharge(Faction faction, double amount, String reason) {
        if (faction == null) {
            return Result.fail(Messages.get(EconomyText.NO_FACTION_TO_CHARGE));
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        Route route = route(faction);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.charge(faction, a, describe(reason))
                : books.charge(faction, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(faction, null, Messages.get(EconomyText.PAID_FOR, "amount", format(a), "reason", describe(reason)));
        return Result.pass(Messages.get(EconomyText.PAID_FROM_BANK, "amount", format(a), "faction", faction.tag()));
    }

    @Override
    public Result bankCredit(Faction faction, double amount, String reason) {
        if (faction == null) {
            return Result.fail(Messages.get(EconomyText.NO_FACTION_TO_PAY));
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        Route route = route(faction);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.credit(faction, a, describe(reason))
                : books.credit(faction, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(faction, null, Messages.get(EconomyText.RECEIVED_FOR, "amount", format(a), "reason", describe(reason)));
        return Result.pass(Messages.get(EconomyText.ADDED_TO_BANK, "amount", format(a), "faction", faction.tag()));
    }

    @Override
    public double bankDrain(Faction faction, String reason) {
        if (faction == null) {
            return 0;
        }
        if (CoinageBanks.moved(faction)) {
            if (coinage.live() != null && !coinageBanksOn()) {
                coinageBanks.moveBack(faction);
            }
            if (CoinageBanks.moved(faction)) {
                boolean toServer = "server".equalsIgnoreCase(ctx.settings().text("eco.coinageDisbandTo"));
                CoinageBanks.Drain d = coinageBanks.drain(faction, toServer, describe(reason));
                if (d.held() != 0) {
                    bankChanged(faction, null, Messages.get(EconomyText.EMPTIED_FOR, "amount", format(d.held()), "reason", describe(reason)));
                }
                return d.owed();
            }
        }
        double held = books.drain(faction);
        if (held != 0) {
            bankChanged(faction, null, Messages.get(EconomyText.EMPTIED_FOR, "amount", format(held), "reason", describe(reason)));
        }
        return held;
    }

    @Override
    public Result bankTransfer(Faction from, Faction to, double amount, String reason) {
        if (from == null || to == null) {
            return Result.fail(Messages.get(EconomyText.BOTH_MUST_EXIST));
        }
        if (from.id().equals(to.id())) {
            return Result.fail(Messages.get(EconomyText.PAY_ITSELF));
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        Route route = route(from, to);
        String failed = route == Route.DOWN ? Messages.get(EconomyText.NOT_RESPONDING)
                : route == Route.COINAGE ? coinageBanks.move(from, to, a, describe(reason))
                : books.move(from, to, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        String why = describe(reason);
        bankChanged(from, null, Messages.get(EconomyText.PAID_TO_FOR, "amount", format(a), "faction", to.tag(), "reason", why));
        bankChanged(to, null, Messages.get(EconomyText.RECEIVED_FROM_FOR, "amount", format(a), "faction", from.tag(), "reason", why));
        return Result.pass(Messages.get(EconomyText.MOVED, "amount", format(a), "from", from.tag(), "to", to.tag()));
    }

    @Override
    public String providerName() {
        return provider().name();
    }

    /**
     * True once the faction left the repository. Checked under the ledger lock, because disband
     * removes the faction before it drains the bank, so a credit either lands before the drain or
     * is refused.
     */
    /** Keeps coins that could not be put back into a wallet in the player's market mailbox. */
    private boolean park(UUID player, double amount) {
        dev.ryvrook.dominion.service.MarketService market = ctx.serviceOrNull(dev.ryvrook.dominion.service.MarketService.class);
        if (market == null) {
            return false;
        }
        try {
            market.mail(player, List.of(), amount);
            return true;
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not keep %s for %s in the mailbox", format(amount), player);
            return false;
        }
    }

    boolean gone(Faction f) {
        return ctx.repo().faction(f.id()) == null;
    }

    private static String describe(String reason) {
        return reason == null || reason.isBlank() ? Messages.get(EconomyText.UNNAMED_REASON) : reason.strip();
    }

    // ---- faction banks in Coinage ----------------------------------------------

    /** Where a faction's bank moves go. DOWN refuses them while a bank that lives in Coinage cannot be reached. */
    enum Route { BOOKS, COINAGE, DOWN }

    /** True while the provider is coinage and eco.coinageBanks is on. */
    boolean coinageBanksOn() {
        return ctx.settings().bool("eco.coinageBanks") && provider() == coinage;
    }

    CoinageBanks coinageBanks() {
        return coinageBanks;
    }

    /**
     * Where the bank of a faction lives now. A bank that moved into Coinage comes back to Dominion when Coinage
     * banks were turned off while Coinage is linked, and is refused while Coinage is not linked, so the mirror is
     * never spent twice.
     */
    Route route(Faction f) {
        boolean on = coinageBanksOn();
        if (CoinageBanks.moved(f)) {
            if (coinage.live() == null) {
                return Route.DOWN;
            }
            if (on) {
                return Route.COINAGE;
            }
            return coinageBanks.moveBack(f) ? Route.BOOKS : Route.DOWN;
        }
        return on ? Route.COINAGE : Route.BOOKS;
    }

    /** The route of a move between two banks, which both have to agree on. */
    private Route route(Faction a, Faction b) {
        Route ra = route(a);
        Route rb = route(b);
        return ra == rb ? ra : Route.DOWN;
    }

    /**
     * Called every minute. While Coinage is linked, refreshes the mirror of every faction bank that lives there,
     * keeps the account owner and member rights in step with the faction, and moves banks back when Coinage banks
     * were turned off. Also rebuilds the member index of the net worth part.
     */
    void coinageTick() {
        releaseStaleHolds(clock.getAsLong());
        if (coinage.live() == null || !enabled()) {
            return;
        }
        boolean on = coinageBanksOn();
        Map<UUID, UUID> index = new HashMap<>();
        for (Faction f : new ArrayList<>(ctx.repo().factions())) {
            if (f.isSystem()) {
                continue;
            }
            try {
                if (CoinageBanks.moved(f)) {
                    if (!on) {
                        coinageBanks.moveBack(f);
                        continue;
                    }
                    coinageBanks.refresh(f);
                    List<UUID> ids = new ArrayList<>();
                    for (Member m : f.members()) {
                        ids.add(m.uuid());
                        index.put(m.uuid(), f.id());
                    }
                    coinageBanks.sync(f, ids, this::bankRights);
                }
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not keep the Coinage bank of %s in step", f.tag());
            }
        }
        memberIndex = index;
    }

    /** The rights a member gets on the faction account in Coinage's own pages, from their rank and hold. */
    private java.util.Set<CoinagePort.BankRight> bankRights(UUID member) {
        java.util.EnumSet<CoinagePort.BankRight> out = java.util.EnumSet.noneOf(CoinagePort.BankRight.class);
        Faction f = factions().factionOf(member);
        if (f == null) {
            return out;
        }
        boolean deposit = factions().can(member, FactionAction.DEPOSIT);
        boolean withdraw = withdrawDenied(f, member) == null;
        if (deposit || withdraw) {
            out.add(CoinagePort.BankRight.BALANCE);
        }
        if (deposit) {
            out.add(CoinagePort.BankRight.DEPOSIT);
        }
        if (withdraw) {
            out.add(CoinagePort.BankRight.WITHDRAW);
        }
        return out;
    }

    /** The faction whose bank is this Coinage account, or null. */
    private Faction factionOfAccount(UUID account) {
        UUID id = coinageBanks.factionOf(account);
        return id == null ? null : ctx.repo().faction(id);
    }

    /**
     * Before a move a player started in Coinage's own pages. Taking money out of a faction account runs the same
     * checks as /f withdraw, membership, rank, the new member hold and the daily allowance. Null lets it through.
     */
    String coinageVeto(UUID from, UUID player, long minor) {
        Faction f = factionOfAccount(from);
        if (f == null || player == null) {
            return null;
        }
        if (!f.isMember(player)) {
            return Messages.get(EconomyText.COINAGE_NOT_MEMBER);
        }
        Result denied = withdrawDenied(f, player);
        if (denied != null) {
            return denied.message();
        }
        long limit = limitCents(f, player);
        if (limit <= 0) {
            return null;
        }
        double amount = CoinageAmounts.toAmount(minor, Math.max(0, coinage.decimals()));
        long thread = Thread.currentThread().threadId();
        long now = clock.getAsLong();
        synchronized (lock) {
            release(held.remove(thread));
            // A move that failed on another thread gives its booking back here too, not only on the minute tick.
            releaseStaleHolds(now);
            // Booked now, under the same lock as the check, so two withdrawals at once cannot both fit the allowance.
            String over = CoinageBanks.limitDenied(f, player, amount, limit, this::format);
            // After shutdown nothing books, the after listener counts the move in full as it did before bookings.
            if (over == null && !closed) {
                WithdrawLimits.record(f, player, amount);
                held.put(thread, new Held(f, player, amount, now));
            }
            return over;
        }
    }

    /** Gives back allowance booked for a move that never happened. Caller holds the ledger lock. */
    private static void release(Held h) {
        if (h != null) {
            WithdrawLimits.record(h.faction(), h.player(), -h.amount());
        }
    }

    /** Gives back bookings older than {@link #HELD_MILLIS}, from the veto and the minute tick. */
    void releaseStaleHolds(long now) {
        if (held.isEmpty()) {
            return;
        }
        synchronized (lock) {
            held.values().removeIf(h -> {
                if (now - h.at() > HELD_MILLIS) {
                    release(h);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Gives back every booking, at shutdown before the factions are saved. A booking still here then belongs to a move
     * that failed, or to one in flight whose after listener the next instance counts in full.
     */
    void releaseAllHolds() {
        synchronized (lock) {
            closed = true;
            held.values().removeIf(h -> {
                release(h);
                return true;
            });
        }
    }

    /**
     * After any Coinage move. A side that is a faction account gets its mirror refreshed. A move another plugin or
     * a player made in Coinage is written to the faction log, and a member's withdrawal counts against their daily
     * allowance. Dominion's own moves are already logged by the service.
     *
     * @param set true for an admin set, where {@code toAfter} is the new balance
     */
    void coinageMoved(UUID from, String fromName, long fromAfter, UUID to, String toName, long toAfter, long amount,
                      boolean set, String plugin, String reason, UUID player) {
        int d = coinage.decimals();
        Faction payer = factionOfAccount(from);
        Faction payee = factionOfAccount(to);
        if (payer == null && payee == null) {
            return;
        }
        if (payer != null) {
            coinageBanks.mirror(payer, fromAfter, d);
        }
        if (payee != null) {
            coinageBanks.mirror(payee, toAfter, d);
        }
        if ("Dominion".equals(plugin) || d < 0) {
            return;
        }
        String money = format(CoinageAmounts.toAmount(amount, d));
        String why = describe(reason);
        if (set && payee != null) {
            bankChanged(payee, player, Messages.get(EconomyText.COINAGE_SET_LOG, "amount", money));
            return;
        }
        if (payer != null) {
            bankChanged(payer, player, Messages.get(EconomyText.COINAGE_OUT_LOG, "amount", money, "to", named(toName), "reason", why));
            if (player != null && payer.isMember(player)) {
                settle(payer, player, CoinageAmounts.toAmount(amount, d));
                ctx.bus().publish(new DominionEvent.BankWithdrawn(payer.id(), player, CoinageAmounts.toAmount(amount, d)));
            }
        }
        if (payee != null) {
            bankChanged(payee, player, Messages.get(EconomyText.COINAGE_IN_LOG, "amount", money, "from", named(fromName), "reason", why));
        }
    }

    /**
     * Counts a member's Coinage withdrawal against the allowance. The veto already booked it on this thread, so only
     * a change of amount by another listener is added, and a move with no booking is counted in full.
     */
    private void settle(Faction payer, UUID player, double amount) {
        Held h = held.remove(Thread.currentThread().threadId());
        synchronized (lock) {
            if (h != null && h.faction() == payer && h.player().equals(player)) {
                if (Money.cents(amount) != Money.cents(h.amount())) {
                    WithdrawLimits.record(payer, player, amount - h.amount());
                }
                return;
            }
            release(h);
            if (limitCents(payer, player) > 0) {
                WithdrawLimits.record(payer, player, amount);
            }
        }
    }

    private static String named(String name) {
        return name == null || name.isBlank() ? Messages.get(EconomyText.COINAGE_SOMEONE) : name;
    }

    /**
     * The faction net worth part. A member's share of their faction bank, the bank divided by the member count in
     * minor units of the Coinage currency, for banks that live in Coinage. Reads memory only.
     */
    long coinageShare(UUID player, String currency) {
        CoinagePort p = coinage.live();
        UUID fid = player == null ? null : memberIndex.get(player);
        if (p == null || fid == null) {
            return 0;
        }
        String cur = p.currencyId(coinage.currency());
        int d = p.decimals(coinage.currency());
        Faction f = ctx.repo().faction(fid);
        if (cur == null || d < 0 || !cur.equals(currency) || f == null || !CoinageBanks.moved(f)) {
            return 0;
        }
        int n = f.memberCount();
        return n <= 0 ? 0 : CoinageAmounts.floorMinor(f.bank(), d) / n;
    }

    // ---- provider selection ---------------------------------------------------

    /** The provider kinds eco.provider can pick. */
    enum Kind { INTERNAL, VAULT, ITEM, COINAGE, UNKNOWN }

    /**
     * What eco.provider picks. auto takes Coinage when it is linked, else VaultUnlocked when one is found, else
     * the internal ledger. The VaultUnlocked lookup only runs when auto gets that far. An explicit choice is
     * returned as asked, the caller falls back when it is missing. Pure, so the order is tested on its own.
     */
    static Kind select(String setting, boolean coinageLinked, java.util.function.BooleanSupplier vaultFound) {
        String key = setting == null ? "" : setting.strip().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "item", "items" -> Kind.ITEM;
            case "vaultunlocked", "vault" -> Kind.VAULT;
            case "coinage" -> Kind.COINAGE;
            case "internal" -> Kind.INTERNAL;
            case "", "auto" -> coinageLinked ? Kind.COINAGE : vaultFound.getAsBoolean() ? Kind.VAULT : Kind.INTERNAL;
            default -> Kind.UNKNOWN;
        };
    }

    /** The wallet provider chosen by eco.provider, read live. Falls back to the internal ledger. */
    BalanceProvider provider() {
        String name = ctx.settings().text("eco.provider");
        boolean auto = name == null || name.isBlank() || name.strip().equalsIgnoreCase("auto");
        switch (select(name, coinage.live() != null, () -> vault(true) != null)) {
            case ITEM -> {
                return items;
            }
            case VAULT -> {
                VaultUnlockedBridge v = vault(auto);
                return v != null ? v : internal;
            }
            case COINAGE -> {
                if (coinage.live() != null) {
                    return coinage;
                }
                if (!coinageWarned) {
                    coinageWarned = true;
                    ctx.logger().at(Level.WARNING).log("eco.provider is coinage but Coinage is not linked, so Dominion uses its internal ledger until it is");
                }
                return internal;
            }
            case INTERNAL -> {
                return internal;
            }
            default -> {
                if (!unknownWarned) {
                    unknownWarned = true;
                    ctx.logger().at(Level.WARNING).log("Unknown eco.provider %s, using the internal ledger", name);
                }
                return internal;
            }
        }
    }

    /** Discovers VaultUnlocked once. A miss logs one warning unless quiet and is retried once a minute. */
    private VaultUnlockedBridge vault(boolean quiet) {
        VaultUnlockedBridge v = vault;
        if (v != null) {
            return v;
        }
        long now = System.currentTimeMillis();
        if (now < vaultRetryAt) {
            return null;
        }
        synchronized (this) {
            if (vault != null) {
                return vault;
            }
            if (now < vaultRetryAt) {
                return null;
            }
            vault = VaultUnlockedBridge.discover(ctx, this::format);
            if (vault != null) {
                migration.maybeRun(vault);
            }
            if (vault == null) {
                vaultRetryAt = now + 60_000L;
                if (!vaultWarned && !quiet) {
                    vaultWarned = true;
                    ctx.logger().at(Level.WARNING).log(
                            "eco.provider is vaultunlocked but no VaultUnlocked economy was found, so Dominion uses its internal ledger");
                }
            }
            return vault;
        }
    }
}
