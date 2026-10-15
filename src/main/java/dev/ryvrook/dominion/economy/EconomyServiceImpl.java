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
 * internal ledger in PlayerData.balance, a VaultUnlocked economy or an item currency. Faction money
 * always lives in Faction.bank. Bank changes run under one ledger lock and the faction monitor, so
 * check then set is atomic. Lock order is always ledger first, then factions by id. External
 * providers are only called outside the ledger lock, so a slow provider never stalls the banks.
 */
public final class EconomyServiceImpl implements EconomyService {
    static final String DISABLED = "The economy is disabled";

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
        return r.failed() ? r : Result.pass("Received " + format(amount));
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        Result check = validate(amount);
        if (check != null) {
            return check;
        }
        Result r = provider().withdraw(player, amount, reason);
        return r.failed() ? r : Result.pass("Paid " + format(amount));
    }

    @Override
    public Result transfer(UUID from, UUID to, double amount, String reason) {
        Result check = validateWallet(amount);
        if (check != null) {
            return check;
        }
        if (from.equals(to)) {
            return Result.fail("You cannot pay yourself");
        }
        BalanceProvider p = provider();
        String failed = p == internal
                ? books.transferInternal(from, to, amount)
                : books.transferExternal(p, from, to, amount, reason);
        return failed != null ? Result.fail(failed) : Result.pass("Sent " + format(amount));
    }

    @Override
    public Result bankDeposit(UUID actor, double amount) {
        Result check = validateWallet(amount);
        if (check != null) {
            return check;
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction");
        }
        if (!factions().can(actor, FactionAction.DEPOSIT)) {
            return Result.fail("Your rank cannot deposit to the bank");
        }
        BalanceProvider p = provider();
        String failed = p == internal
                ? books.depositInternal(actor, f, amount)
                : books.depositExternal(p, actor, f, amount);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, "Deposited " + format(amount));
        ctx.bus().publish(new DominionEvent.BankDeposited(f.id(), actor, amount));
        return Result.pass("Deposited " + format(amount) + " to the bank");
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
        String failed = p == internal
                ? books.withdrawInternal(f, actor, amount, limit)
                : books.withdrawExternal(p, f, actor, amount, limit);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, "Withdrew " + format(amount));
        return Result.pass("Withdrew " + format(amount) + " from the bank");
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
            return Result.fail("No faction has that tag");
        }
        if (target.id().equals(f.id())) {
            return Result.fail("You cannot pay your own faction");
        }
        String failed = books.pay(f, target, actor, amount, limitCents(f, actor));
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(f, actor, "Paid " + format(amount) + " to " + target.tag());
        bankChanged(target, actor, "Received " + format(amount) + " from " + f.tag());
        return Result.pass("Paid " + format(amount) + " to " + target.tag());
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
     * Called every minute. While eco.provider is vaultunlocked and the bridge is connected, moves
     * internal balances above zero into VaultUnlocked at most once a day.
     */
    void migrationTick() {
        if (!enabled()) {
            return;
        }
        String name = ctx.settings().text("eco.provider");
        String key = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        if (!key.equals("vaultunlocked") && !key.equals("vault")) {
            return;
        }
        VaultUnlockedBridge v = vault();
        if (v != null) {
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
            return Result.fail(DISABLED);
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
            return Result.fail("Only whole amounts can be moved because the currency is an item");
        }
        return null;
    }

    private Result withdrawDenied(Faction f, UUID actor) {
        if (f == null) {
            return Result.fail("You are not in a faction");
        }
        Member m = f.member(actor);
        if (m != null && !actor.equals(f.leader()) && m.onHold(Time.now())) {
            return Result.fail("New members cannot withdraw until their hold ends in " + Time.remaining(m.holdUntil() - Time.now()));
        }
        if (!factions().can(actor, FactionAction.WITHDRAW)) {
            return Result.fail("Your rank cannot withdraw from the bank");
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
        String name = who == null ? "Server" : ctx.player(who).name();
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
            return Result.fail("There is no faction to charge");
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        String failed = books.charge(faction, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(faction, null, "Paid " + format(a) + " for " + describe(reason));
        return Result.pass("Paid " + format(a) + " from the bank of " + faction.tag());
    }

    @Override
    public Result bankCredit(Faction faction, double amount, String reason) {
        if (faction == null) {
            return Result.fail("There is no faction to pay");
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        String failed = books.credit(faction, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        bankChanged(faction, null, "Received " + format(a) + " for " + describe(reason));
        return Result.pass("Added " + format(a) + " to the bank of " + faction.tag());
    }

    @Override
    public double bankDrain(Faction faction, String reason) {
        if (faction == null) {
            return 0;
        }
        double held = books.drain(faction);
        if (held != 0) {
            bankChanged(faction, null, "Emptied the bank of " + format(held) + " for " + describe(reason));
        }
        return held;
    }

    @Override
    public Result bankTransfer(Faction from, Faction to, double amount, String reason) {
        if (from == null || to == null) {
            return Result.fail("Both factions must exist");
        }
        if (from.id().equals(to.id())) {
            return Result.fail("A faction cannot pay itself");
        }
        double a = Money.round(amount);
        Result check = validate(a);
        if (check != null) {
            return check;
        }
        String failed = books.move(from, to, a);
        if (failed != null) {
            return Result.fail(failed);
        }
        String why = describe(reason);
        bankChanged(from, null, "Paid " + format(a) + " to " + to.tag() + " for " + why);
        bankChanged(to, null, "Received " + format(a) + " from " + from.tag() + " for " + why);
        return Result.pass("Moved " + format(a) + " from " + from.tag() + " to " + to.tag());
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
        return reason == null || reason.isBlank() ? "an unnamed reason" : reason.strip();
    }

    // ---- provider selection ---------------------------------------------------

    /** The wallet provider chosen by eco.provider, read live. Falls back to the internal ledger. */
    BalanceProvider provider() {
        String name = ctx.settings().text("eco.provider");
        String key = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        switch (key) {
            case "item", "items" -> {
                return items;
            }
            case "vaultunlocked", "vault" -> {
                VaultUnlockedBridge v = vault();
                return v != null ? v : internal;
            }
            case "", "internal" -> {
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

    /** Discovers VaultUnlocked once. A miss logs one warning and is retried quietly once a minute. */
    private VaultUnlockedBridge vault() {
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
                if (!vaultWarned) {
                    vaultWarned = true;
                    ctx.logger().at(Level.WARNING).log(
                            "eco.provider is vaultunlocked but no VaultUnlocked economy was found, so Dominion uses its internal ledger");
                }
            }
            return vault;
        }
    }
}
