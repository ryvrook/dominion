package dev.ryvrook.dominion.integration.coinage;

import dev.ryvrook.coinage.api.Account;
import dev.ryvrook.coinage.api.AccountPermission;
import dev.ryvrook.coinage.api.Accounts;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.EconomyListener;
import dev.ryvrook.coinage.api.Initiator;
import dev.ryvrook.coinage.api.NetWorthPart;
import dev.ryvrook.coinage.api.Request;
import dev.ryvrook.coinage.api.Result;
import dev.ryvrook.coinage.api.Subscription;
import dev.ryvrook.coinage.api.Transaction;
import dev.ryvrook.coinage.api.TransactionPre;
import dev.ryvrook.coinage.api.service.MarketService;
import dev.ryvrook.dominion.economy.CoinagePort;
import dev.ryvrook.dominion.economy.CoinageSeam;
import dev.ryvrook.dominion.integration.Host;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.market.MarketHandoff;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The link to Coinage. The only class of Dominion that uses Coinage types, loaded by name from the integration
 * module once the plugin manager listed Coinage.
 *
 * <p>It is the {@link CoinagePort} the economy uses for wallets and faction banks, attached to the economy's
 * {@link CoinageSeam} while Coinage is up. It subscribes one listener that refuses withdrawals from a faction
 * account that Dominion's rules would refuse and mirrors every move that touches a faction account into the
 * faction's log, adds the {@code faction} net worth part and hands the panel Market tab to Coinage's market.
 *
 * <p>Binding. {@link #install} adds the Coinage enable and disable hooks. The enable hook runs at once when
 * Coinage is already up and again whenever it starts. {@link #stop} sets the stopped flag first, removes both
 * hooks and unbinds, so Coinage never calls into a closed Dominion loader. Every Coinage call is guarded, a call
 * that throws answers as if Coinage were stopping.
 */
public final class CoinageLink implements Link, CoinagePort {
    static final String PLUGIN = "Dominion";
    /** The lowest Coinage API that has the service facades. */
    static final int MIN_API = 3;
    /** The Coinage API that added net worth parts. */
    static final int NET_WORTH_API = 4;

    private static final Initiator BY = Initiator.plugin(PLUGIN);

    private final Host host;
    private final Consumer<CoinageApi> enableHook = this::bind;
    private final Runnable disableHook = this::unbind;
    private final NetWorthPart part = new NetWorthPart() {
        @Override
        public String part() {
            return "faction";
        }

        @Override
        public long held(UUID player, String currency) {
            CoinageSeam s = seam;
            return s == null || stopped ? 0 : s.share(player, currency);
        }
    };
    private final MarketHandoff.Target market = new MarketHandoff.Target() {
        @Override
        public boolean available() {
            CoinageApi a = live();
            return a != null && a.service(MarketService.class).isPresent();
        }

        @Override
        public boolean open(UUID player) {
            CoinageApi a = live();
            return a != null && a.service(MarketService.class).map(m -> m.openPage(player)).orElse(false);
        }
    };
    private volatile CoinageApi api;
    private volatile Subscription subscription;
    private volatile CoinageSeam seam;
    private volatile MarketHandoff handoff;
    private volatile boolean netWorthAdded;
    private volatile String version;
    private volatile boolean stopped;

    CoinageLink(Host host) {
        this.host = host;
    }

    /** Called by name from the integration module. */
    public static Link install(Host host) {
        CoinageLink l = new CoinageLink(host);
        l.start(host.ctx().serviceOrNull(CoinageSeam.class), host.ctx().serviceOrNull(MarketHandoff.class));
        return l;
    }

    /** Adds the hooks. The seam and hand off are null when the economy or market module is not running. */
    void start(CoinageSeam seam, MarketHandoff handoff) {
        this.seam = seam;
        this.handoff = handoff;
        CoinageApi.onDisable(disableHook);
        CoinageApi.onEnable(enableHook);
    }

    /** The API version of the Coinage that is running, read at run time rather than the one compiled against. */
    static int apiVersion() {
        try {
            return CoinageApi.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    // ---- binding ----------------------------------------------------------------------------------------

    synchronized void bind(CoinageApi live) {
        if (stopped || live == null || live == api) {
            return;
        }
        int v = apiVersion();
        if (v < MIN_API) {
            host.warn("Dominion needs Coinage API " + MIN_API + " or newer and found " + v + ", so it runs without Coinage");
            return;
        }
        drop();
        api = live;
        version = guard(live::version, host.foundVersion());
        try {
            subscription = live.subscribe(PLUGIN, new Listener());
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Dominion could not subscribe to Coinage, so moves made in Coinage do not reach the faction logs");
        }
        if (v >= NET_WORTH_API) {
            try {
                live.netWorth().add(part);
                netWorthAdded = true;
            } catch (RuntimeException | LinkageError e) {
                host.warn(e, "Dominion could not add the faction net worth part to Coinage");
            }
        }
        CoinageSeam s = seam;
        if (s != null) {
            s.attach(this);
        }
        MarketHandoff h = handoff;
        if (h != null) {
            h.attach(market);
        }
        String provider = s == null ? "none" : s.providerName();
        host.info("Dominion is bound to Coinage " + version + " and its wallets use the " + provider + " provider");
    }

    /** Coinage's disable hook, and the last step of {@link #stop}. */
    synchronized void unbind() {
        boolean was = api != null;
        drop();
        if (was && !stopped) {
            host.info("Dominion stopped using Coinage because it stopped");
        }
    }

    private void drop() {
        CoinageSeam s = seam;
        if (s != null) {
            s.detach(this);
        }
        MarketHandoff h = handoff;
        if (h != null) {
            h.detach(market);
        }
        CoinageApi a = api;
        if (a != null && netWorthAdded) {
            try {
                a.netWorth().remove(part);
            } catch (RuntimeException | LinkageError ignored) {
                // Coinage drops every part itself when it stops
            }
        }
        netWorthAdded = false;
        Subscription sub = subscription;
        subscription = null;
        if (sub != null) {
            try {
                sub.close();
            } catch (RuntimeException | LinkageError ignored) {
                // Coinage closes every subscription itself when it stops
            }
        }
        api = null;
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            CoinageApi.removeHooks(enableHook, disableHook);
        } catch (RuntimeException | LinkageError ignored) {
            // Coinage is already gone
        }
        unbind();
    }

    @Override
    public boolean bound() {
        return !stopped && api != null;
    }

    @Override
    public String version() {
        return version;
    }

    private CoinageApi live() {
        return stopped ? null : api;
    }

    private static <T> T guard(java.util.function.Supplier<T> call, T fallback) {
        try {
            T v = call.get();
            return v == null ? fallback : v;
        } catch (RuntimeException | LinkageError e) {
            return fallback;
        }
    }

    // ---- the listener -----------------------------------------------------------------------------------

    /** Refuses faction withdrawals Dominion would refuse and mirrors moves on faction accounts. */
    private final class Listener implements EconomyListener {
        @Override
        public void beforeTransaction(TransactionPre pre) {
            CoinageSeam s = seam;
            Request r = pre.request();
            if (s == null || stopped || r.from() == null || !r.initiator().isPlayer() || PLUGIN.equals(r.plugin())
                    || r.kind() == Transaction.Type.SET || r.kind() == Transaction.Type.DEPOSIT) {
                return;
            }
            String why = s.veto(r.from(), r.initiator().player(), pre.amount());
            if (why != null) {
                pre.cancel(why);
            }
        }

        @Override
        public void afterTransaction(Transaction tx) {
            CoinageSeam s = seam;
            CoinageApi a = live();
            if (s == null || a == null || !s.touches(tx.from(), tx.to()) || !tx.currency().equals(currencyId(configured()))) {
                return;
            }
            boolean set = tx.type() == Transaction.Type.SET;
            UUID player = tx.initiator().isPlayer() ? tx.initiator().player() : null;
            s.moved(tx.from(), nameOf(a, tx.from()), tx.fromAfter(), tx.to(), nameOf(a, tx.to()), tx.toAfter(), tx.amount(),
                    set, tx.plugin(), tx.reason(), player);
        }
    }

    /** The eco.coinageCurrency setting, null for the primary currency. */
    private String configured() {
        String c = host.ctx().settings().text("eco.coinageCurrency");
        return c == null || c.isBlank() ? null : c.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static String nameOf(CoinageApi a, UUID id) {
        if (id == null) {
            return null;
        }
        if (Accounts.SERVER.equals(id)) {
            return "Server";
        }
        try {
            return a.accounts().get(id).map(Account::name).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ---- the port ---------------------------------------------------------------------------------------

    private Optional<Currency> currency(String id) {
        CoinageApi a = live();
        if (a == null) {
            return Optional.empty();
        }
        try {
            return a.currencies().resolve(id);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    @Override
    public String currencyId(String currency) {
        return currency(currency).map(Currency::id).orElse(null);
    }

    @Override
    public int decimals(String currency) {
        return currency(currency).map(Currency::decimals).orElse(-1);
    }

    @Override
    public long balance(UUID account, String currency) {
        CoinageApi a = live();
        String id = currencyId(currency);
        if (a == null || id == null || account == null) {
            return 0;
        }
        try {
            return a.ledger().balance(account, id);
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    @Override
    public void ensurePlayer(UUID player) {
        CoinageApi a = live();
        if (a == null || player == null) {
            return;
        }
        try {
            if (a.accounts().get(player).isEmpty()) {
                a.accounts().player(player);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // the move that follows reports the missing account
        }
    }

    @Override
    public Move deposit(UUID to, String currency, long amount, String reason) {
        return move(currency, c -> Request.deposit(to, c, amount), reason, Kind.DEPOSIT);
    }

    @Override
    public Move withdraw(UUID from, String currency, long amount, String reason) {
        return move(currency, c -> Request.withdraw(from, c, amount), reason, Kind.WITHDRAW);
    }

    @Override
    public Move transfer(UUID from, UUID to, String currency, long amount, String reason) {
        return move(currency, c -> Request.transfer(from, to, c, amount), reason, Kind.TRANSFER);
    }

    @Override
    public Move set(UUID to, String currency, long amount, String reason) {
        return move(currency, c -> Request.set(to, c, amount), reason, Kind.SET);
    }

    private enum Kind { DEPOSIT, WITHDRAW, TRANSFER, SET }

    private Move move(String currency, Function<String, Request> build, String reason, Kind kind) {
        CoinageApi a = live();
        if (a == null) {
            return Move.failed(Failure.STOPPING, null);
        }
        String id = currencyId(currency);
        if (id == null) {
            return Move.failed(Failure.UNKNOWN_CURRENCY, null);
        }
        try {
            Request r = build.apply(id).plugin(PLUGIN).reason(reason).by(BY);
            Result res = switch (kind) {
                case DEPOSIT -> a.ledger().deposit(r);
                case WITHDRAW -> a.ledger().withdraw(r);
                case TRANSFER -> a.ledger().transfer(r);
                case SET -> a.ledger().set(r);
            };
            return toMove(res);
        } catch (RuntimeException | LinkageError e) {
            return Move.failed(Failure.STOPPING, null);
        }
    }

    static Move toMove(Result r) {
        if (r == null) {
            return Move.failed(Failure.CANCELLED, null);
        }
        return r.ok() ? new Move(true, Failure.NONE, r.message(), r.fromAfter(), r.toAfter())
                : Move.failed(Failure.of(r.failure() == null ? null : r.failure().name()), r.message());
    }

    @Override
    public UUID serverAccount() {
        return Accounts.SERVER;
    }

    @Override
    public UUID account(String handle) {
        CoinageApi a = live();
        if (a == null || handle == null) {
            return null;
        }
        try {
            return a.accounts().byHandle(handle).map(Account::id).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public UUID createShared(String handle, String name, UUID owner) {
        CoinageApi a = live();
        if (a == null) {
            return null;
        }
        try {
            Account acc = a.accounts().createShared(handle, name, owner, PLUGIN);
            return acc == null ? null : acc.id();
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Coinage refused the faction account " + handle);
            return null;
        }
    }

    @Override
    public UUID owner(UUID account) {
        CoinageApi a = live();
        if (a == null || account == null) {
            return null;
        }
        try {
            return a.accounts().get(account).map(Account::owner).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public boolean setOwner(UUID account, UUID owner) {
        CoinageApi a = live();
        try {
            return a != null && a.accounts().setOwner(account, owner, BY);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public Map<UUID, Set<BankRight>> members(UUID account) {
        Map<UUID, Set<BankRight>> out = new HashMap<>();
        CoinageApi a = live();
        if (a == null || account == null) {
            return out;
        }
        try {
            Optional<Account> acc = a.accounts().get(account);
            if (acc.isEmpty()) {
                return out;
            }
            UUID owner = acc.get().owner();
            for (UUID m : acc.get().members()) {
                if (!m.equals(owner)) {
                    out.put(m, rights(acc.get().permissions(m)));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // an empty map makes the next sync set every right again
        }
        return out;
    }

    @Override
    public boolean setRights(UUID account, UUID member, Set<BankRight> rights) {
        CoinageApi a = live();
        try {
            return a != null && a.accounts().setPermissions(account, member, permissions(rights), BY);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean delete(UUID account) {
        CoinageApi a = live();
        try {
            return a != null && a.accounts().delete(account, BY, PLUGIN);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean rename(UUID account, String name) {
        CoinageApi a = live();
        try {
            return a != null && a.accounts().rename(account, name);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Dominion's rights as Coinage permissions. */
    static Set<AccountPermission> permissions(Set<BankRight> rights) {
        Set<AccountPermission> out = EnumSet.noneOf(AccountPermission.class);
        if (rights != null) {
            for (BankRight r : rights) {
                out.add(switch (r) {
                    case BALANCE -> AccountPermission.BALANCE;
                    case DEPOSIT -> AccountPermission.DEPOSIT;
                    case WITHDRAW -> AccountPermission.WITHDRAW;
                });
            }
        }
        return out;
    }

    /** The Dominion rights among a member's Coinage permissions. */
    static Set<BankRight> rights(Set<AccountPermission> permissions) {
        Set<BankRight> out = EnumSet.noneOf(BankRight.class);
        if (permissions != null) {
            for (AccountPermission p : permissions) {
                switch (p) {
                    case BALANCE -> out.add(BankRight.BALANCE);
                    case DEPOSIT -> out.add(BankRight.DEPOSIT);
                    case WITHDRAW -> out.add(BankRight.WITHDRAW);
                    default -> { }
                }
            }
        }
        return out;
    }
}
