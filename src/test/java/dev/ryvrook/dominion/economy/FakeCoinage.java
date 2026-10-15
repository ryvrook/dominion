package dev.ryvrook.dominion.economy;

import com.google.gson.JsonObject;
import dev.ryvrook.coinage.api.Account;
import dev.ryvrook.coinage.api.AccountPermission;
import dev.ryvrook.coinage.api.AccountStatus;
import dev.ryvrook.coinage.api.AccountType;
import dev.ryvrook.coinage.api.Accounts;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currencies;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.EconomyListener;
import dev.ryvrook.coinage.api.Failure;
import dev.ryvrook.coinage.api.Ledger;
import dev.ryvrook.coinage.api.Mail;
import dev.ryvrook.coinage.api.NetWorthPart;
import dev.ryvrook.coinage.api.NetWorthParts;
import dev.ryvrook.coinage.api.Query;
import dev.ryvrook.coinage.api.Rank;
import dev.ryvrook.coinage.api.Reports;
import dev.ryvrook.coinage.api.Request;
import dev.ryvrook.coinage.api.Result;
import dev.ryvrook.coinage.api.Stats;
import dev.ryvrook.coinage.api.Subscription;
import dev.ryvrook.coinage.api.Transaction;
import dev.ryvrook.coinage.api.TransactionPre;
import dev.ryvrook.coinage.api.Initiator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A small Coinage that keeps everything in maps, published through the real static hooks of the Coinage api jar.
 * It checks what the bridge relies on, funds, frozen accounts, shared account permissions for player initiators
 * and deletion of empty accounts only, and records every transaction.
 */
final class FakeCoinage extends CoinageApi {
    volatile Currency coins = Currency.coins();
    final Map<UUID, Acc> accounts = new ConcurrentHashMap<>();
    final List<Transaction> log = new CopyOnWriteArrayList<>();
    final List<EconomyListener> listeners = new CopyOnWriteArrayList<>();
    final List<NetWorthPart> parts = new CopyOnWriteArrayList<>();
    private long seq;

    FakeCoinage() {
        accounts.put(Accounts.SERVER, new Acc(Accounts.SERVER, "server", "Server", AccountType.SHARED, null));
    }

    void up() {
        enable(this);
    }

    void down() {
        listeners.clear();
        disable(this);
    }

    /** A mutable account. */
    final class Acc implements Account {
        final UUID id;
        final String handle;
        String name;
        final AccountType type;
        UUID owner;
        AccountStatus status = AccountStatus.ACTIVE;
        volatile long seq;
        final Map<String, Long> balances = new ConcurrentHashMap<>();
        final Map<UUID, Set<AccountPermission>> members = new ConcurrentHashMap<>();

        Acc(UUID id, String handle, String name, AccountType type, UUID owner) {
            this.id = id;
            this.handle = handle;
            this.name = name;
            this.type = type;
            this.owner = owner;
        }

        @Override public UUID id() { return id; }
        @Override public String handle() { return handle; }
        @Override public String name() { return name; }
        @Override public AccountType type() { return type; }
        @Override public UUID owner() { return owner; }
        @Override public AccountStatus status() { return status; }
        @Override public long balance(String currency) { return balances.getOrDefault(currency, 0L); }
        @Override public Map<String, Long> balances() { return Map.copyOf(balances); }
        @Override public Set<UUID> members() { return Set.copyOf(members.keySet()); }
        @Override public Set<AccountPermission> permissions(UUID member) {
            if (member != null && member.equals(owner)) {
                return EnumSet.allOf(AccountPermission.class);
            }
            Set<AccountPermission> p = members.get(member);
            return p == null ? Set.of() : Set.copyOf(p);
        }
        @Override public long createdAt() { return 0; }
        @Override public long seq() { return seq; }
    }

    Acc acc(UUID id) {
        return id == null ? null : accounts.get(id);
    }

    long bal(UUID id) {
        Acc a = acc(id);
        return a == null ? 0 : a.balance(coins.id());
    }

    void give(UUID player, long amount) {
        accountsApi.player(player).balances.merge(coins.id(), amount, Long::sum);
    }

    // ---- ledger ---------------------------------------------------------------------------------------

    private synchronized Result move(Request r) {
        if (r.currency() != null && !r.currency().equals(coins.id())) {
            return Result.fail(Failure.UNKNOWN_CURRENCY, "unknown currency");
        }
        TransactionPre pre = new TransactionPre(r);
        for (EconomyListener l : listeners) {
            l.beforeTransaction(pre);
        }
        if (pre.isCancelled()) {
            return Result.fail(Failure.CANCELLED, pre.cancelReason());
        }
        long amount = pre.amount();
        Acc from = acc(r.from());
        Acc to = acc(r.to());
        Transaction.Type k = r.kind();
        if ((k == Transaction.Type.WITHDRAW || k == Transaction.Type.TRANSFER) && from == null
                || (k != Transaction.Type.WITHDRAW && to == null)) {
            return Result.fail(Failure.ACCOUNT_NOT_FOUND, "no account");
        }
        if (k == Transaction.Type.SET ? amount < 0 : amount <= 0) {
            return Result.fail(Failure.INVALID_AMOUNT, "bad amount");
        }
        if (from != null && from.status != AccountStatus.ACTIVE || to != null && to.status == AccountStatus.FROZEN) {
            return Result.fail(Failure.ACCOUNT_FROZEN, "frozen");
        }
        if (r.initiator().isPlayer() && from != null && from.type == AccountType.SHARED
                && !from.permissions(r.initiator().player()).contains(AccountPermission.WITHDRAW)
                && !from.permissions(r.initiator().player()).contains(AccountPermission.OWNER)) {
            return Result.fail(Failure.NOT_PERMITTED, "not permitted");
        }
        String c = coins.id();
        long fromAfter = 0;
        long toAfter;
        if (from != null) {
            if (from.balance(c) < amount) {
                return Result.fail(Failure.INSUFFICIENT_FUNDS, "short");
            }
            fromAfter = from.balance(c) - amount;
            from.balances.put(c, fromAfter);
        }
        if (k == Transaction.Type.SET) {
            fromAfter = to.balance(c);
            toAfter = amount;
            to.balances.put(c, amount);
        } else if (to != null) {
            toAfter = to.balance(c) + amount;
            to.balances.put(c, toAfter);
        } else {
            toAfter = 0;
        }
        Transaction tx = new Transaction(++seq, System.currentTimeMillis(), k, c, r.from(), r.to(), amount, 0, null,
                fromAfter, toAfter, r.initiator(), r.plugin(), r.reason(), 0);
        log.add(tx);
        if (from != null) {
            from.seq = tx.id();
        }
        if (to != null) {
            to.seq = tx.id();
        }
        for (EconomyListener l : listeners) {
            l.afterTransaction(tx);
        }
        return Result.ok("ok", fromAfter, toAfter, tx);
    }

    final Ledger ledgerApi = new Ledger() {
        @Override public Result deposit(Request request) { return move(request); }
        @Override public Result withdraw(Request request) { return move(request); }
        @Override public Result transfer(Request request) { return move(request); }
        @Override public Result set(Request request) { return move(request); }
        @Override public boolean has(UUID account, String currency, long amount) { return bal(account) >= amount; }
        @Override public long balance(UUID account, String currency) { return coins.id().equals(currency) ? bal(account) : 0; }
        @Override public List<Transaction> history(UUID account, int limit) {
            List<Transaction> out = new ArrayList<>();
            for (Transaction t : log) {
                if (t.touches(account)) {
                    out.add(0, t);
                }
            }
            return out;
        }
        @Override public CompletableFuture<List<Transaction>> search(Query query) { return CompletableFuture.completedFuture(List.of()); }
        @Override public Optional<Transaction> find(long id) { return Optional.empty(); }
        @Override public Result reverse(long id, Initiator by, String reason) { return Result.fail(Failure.NOT_PERMITTED, ""); }
        @Override public Stats stats() { return new Stats(0, 0, 0, 0, 0, 0, 0); }
    };

    // ---- accounts -------------------------------------------------------------------------------------

    final class AccountsImpl implements Accounts {
        @Override public Acc player(UUID uuid) {
            return accounts.computeIfAbsent(uuid, id -> new Acc(id, null, "p", AccountType.PLAYER, null));
        }
        @Override public Optional<Account> get(UUID id) { return Optional.ofNullable(acc(id)); }
        @Override public Optional<Account> byHandle(String handle) {
            for (Acc a : accounts.values()) {
                if (handle.equals(a.handle)) {
                    return Optional.of(a);
                }
            }
            return Optional.empty();
        }
        @Override public Account createShared(String handle, String name, UUID owner, String plugin) {
            Optional<Account> known = byHandle(handle);
            if (known.isPresent()) {
                return known.get();
            }
            UUID id = UUID.nameUUIDFromBytes(("shared:" + handle).getBytes(StandardCharsets.UTF_8));
            Acc a = new Acc(id, handle, name, AccountType.SHARED, owner);
            accounts.put(id, a);
            return a;
        }
        @Override public boolean delete(UUID id, Initiator by, String plugin) {
            Acc a = acc(id);
            if (a == null || a.type != AccountType.SHARED || a.balances.values().stream().anyMatch(v -> v != 0)) {
                return false;
            }
            accounts.remove(id);
            return true;
        }
        @Override public boolean rename(UUID id, String name) {
            Acc a = acc(id);
            if (a == null) {
                return false;
            }
            a.name = name;
            return true;
        }
        @Override public boolean setStatus(UUID id, AccountStatus status, Initiator by) {
            Acc a = acc(id);
            if (a == null) {
                return false;
            }
            a.status = status;
            return true;
        }
        @Override public boolean setOwner(UUID id, UUID owner, Initiator by) {
            Acc a = acc(id);
            if (a == null) {
                return false;
            }
            a.owner = owner;
            a.members.remove(owner);
            return true;
        }
        @Override public boolean setPermissions(UUID id, UUID member, Set<AccountPermission> permissions, Initiator by) {
            Acc a = acc(id);
            if (a == null || member.equals(a.owner)) {
                return false;
            }
            if (permissions == null || permissions.isEmpty()) {
                a.members.remove(member);
            } else {
                a.members.put(member, EnumSet.copyOf(permissions));
            }
            return true;
        }
        @Override public boolean hasPermission(UUID id, UUID actor, AccountPermission permission) {
            Acc a = acc(id);
            return a != null && a.permissions(actor).contains(permission);
        }
        @Override public List<Account> all() { return new ArrayList<>(accounts.values()); }
        @Override public List<Account> players() { return List.of(); }
        @Override public List<Account> shared() { return List.of(); }
        @Override public List<Account> memberOf(UUID player) { return List.of(); }
        @Override public Optional<UUID> uuidOf(String name) { return Optional.empty(); }
        @Override public Optional<String> nameOf(UUID uuid) { return Optional.empty(); }
        @Override public List<Rank> top(String currency, int from, int limit) { return List.of(); }
        @Override public int rank(UUID player, String currency) { return 0; }
        @Override public int count() { return accounts.size(); }
    }

    final AccountsImpl accountsApi = new AccountsImpl();

    // ---- the api --------------------------------------------------------------------------------------

    @Override
    public Currencies currencies() {
        return new Currencies() {
            @Override public Currency primary() { return coins; }
            @Override public Optional<Currency> get(String id) { return coins.id().equals(id) ? Optional.of(coins) : Optional.empty(); }
            @Override public List<Currency> all() { return List.of(coins); }
        };
    }

    @Override public Accounts accounts() { return accountsApi; }
    @Override public Ledger ledger() { return ledgerApi; }
    @Override public Mail mail() { return null; }
    @Override public Reports reports() { return null; }
    @Override public <T> Optional<T> stats(Class<T> type) { return Optional.empty(); }
    @Override public <T> Optional<T> service(Class<T> type) { return Optional.empty(); }
    @Override public JsonObject config() { return new JsonObject(); }

    @Override
    public NetWorthParts netWorth() {
        return new NetWorthParts() {
            @Override public void add(NetWorthPart part) { parts.add(part); }
            @Override public void remove(NetWorthPart part) { parts.remove(part); }
            @Override public List<String> names() {
                List<String> out = new ArrayList<>();
                for (NetWorthPart p : parts) {
                    out.add(p.part());
                }
                return out;
            }
        };
    }

    @Override
    public Subscription subscribe(String owner, EconomyListener listener) {
        listeners.add(listener);
        return new Subscription() {
            private boolean open = true;
            @Override public String owner() { return owner; }
            @Override public boolean isOpen() { return open; }
            @Override public void close() {
                open = false;
                listeners.remove(listener);
            }
        };
    }

    @Override public List<Subscription> subscriptions() { return List.of(); }
    @Override public String version() { return "1.0.0"; }

    /** Every transaction made by the plugin, oldest first. */
    List<Transaction> by(String plugin) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : log) {
            if (plugin.equals(t.plugin())) {
                out.add(t);
            }
        }
        return out;
    }

    Map<String, Long> snapshot() {
        Map<String, Long> out = new HashMap<>();
        accounts.forEach((id, a) -> out.put(id.toString(), a.balance(coins.id())));
        return out;
    }
}
