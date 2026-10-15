package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.text.Messages;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooksTest {
    private final Set<UUID> gone = ConcurrentHashMap.newKeySet();
    private final List<String> severe = new CopyOnWriteArrayList<>();
    /** The loaded records, as the repository holds them. */
    private final Map<UUID, PlayerData> loaded = new ConcurrentHashMap<>();
    /** Coins parked for players, as the market mailbox would keep them. */
    private final Map<UUID, Double> parked = new ConcurrentHashMap<>();
    private boolean parking = true;
    private final Books books = new Books(new Object(), f -> gone.contains(f.id()),
            a -> String.format(Locale.ROOT, "%.2f", a), severe::add,
            new Books.Wallets() {
                @Override
                public PlayerData load(UUID player) {
                    return loaded.computeIfAbsent(player, id -> new PlayerData(id, "p", 0, 0));
                }

                @Override
                public boolean live(PlayerData p) {
                    return loaded.get(p.uuid()) == p;
                }
            },
            (player, amount) -> {
                if (!parking) {
                    return false;
                }
                parked.merge(player, amount, Double::sum);
                return true;
            });

    private PlayerData player(double balance) {
        PlayerData p = new PlayerData(UUID.randomUUID(), "p", 0, balance);
        loaded.put(p.uuid(), p);
        return p;
    }

    static PlayerData loose(double balance) {
        return new PlayerData(UUID.randomUUID(), "p", 0, balance);
    }

    static Faction faction(double bank) {
        Faction f = new Faction(UUID.randomUUID(), "T" + (int) (Math.random() * 1000), "Test", UUID.randomUUID(), 0);
        f.setBank(bank);
        return f;
    }

    static long cents(double v) {
        return Math.round(v * 100.0);
    }

    static boolean wholeCents(double v) {
        return Math.abs(v * 100.0 - Math.rint(v * 100.0)) < 1e-6;
    }

    /** A provider holding whole cents in memory that refuses deposits to the accounts in refuse. */
    static class FakeWallets implements BalanceProvider {
        final Map<UUID, Long> cents = new HashMap<>();
        final Set<UUID> refuse = new HashSet<>();

        @Override public String name() { return "fake"; }

        @Override
        public synchronized double balance(UUID player) {
            return cents.getOrDefault(player, 0L) / 100.0;
        }

        @Override
        public synchronized Result withdraw(UUID player, double amount, String reason) {
            long have = cents.getOrDefault(player, 0L);
            if (have < Money.cents(amount)) {
                return Result.fail("short");
            }
            cents.put(player, have - Money.cents(amount));
            return Result.pass();
        }

        @Override
        public synchronized Result deposit(UUID player, double amount, String reason) {
            if (refuse.contains(player)) {
                return Result.fail("refused");
            }
            cents.merge(player, Money.cents(amount), Long::sum);
            return Result.pass();
        }

        @Override
        public synchronized void set(UUID player, double amount) {
            cents.put(player, Money.cents(amount));
        }

        synchronized long total() {
            long t = 0;
            for (long v : cents.values()) {
                t += v;
            }
            return t;
        }
    }

    // ---- single moves ------------------------------------------------------------

    @Test
    void internalTransferMovesExactlyAndRefusesShortfalls() {
        PlayerData a = player(10);
        PlayerData b = player(0);
        assertNull(books.transferInternal(a.uuid(), b.uuid(), 2.5));
        assertEquals(7.5, a.balance());
        assertEquals(2.5, b.balance());
        String r = books.transferInternal(b.uuid(), a.uuid(), 3);
        assertNotNull(r);
        assertTrue(r.contains("only have 2.50"), r);
        assertEquals(7.5, a.balance());
        assertEquals(2.5, b.balance());
    }

    @Test
    void depositToAFactionThatIsGoneMovesNothing() {
        PlayerData p = player(10);
        Faction f = faction(0);
        gone.add(f.id());
        assertEquals(Messages.get(EconomyText.FACTION_GONE), books.depositInternal(p.uuid(), f, 5));
        assertEquals(10, p.balance());
        assertEquals(0, f.bank());
    }

    @Test
    void externalDepositToAGoneFactionIsRefunded() {
        FakeWallets w = new FakeWallets();
        UUID actor = UUID.randomUUID();
        w.set(actor, 10);
        Faction f = faction(0);
        gone.add(f.id());
        assertEquals(Messages.get(EconomyText.FACTION_GONE), books.depositExternal(w, actor, f, 4));
        assertEquals(10, w.balance(actor));
        assertEquals(0, f.bank());
        assertTrue(severe.isEmpty());
    }

    @Test
    void officerAllowanceIsEnforcedAndCountsPaymentsToo() {
        Faction f = faction(100);
        Faction other = faction(0);
        PlayerData officer = player(0);
        long limit = 1000;
        assertNull(books.withdrawInternal(f, officer.uuid(), 6, limit));
        String r = books.withdrawInternal(f, officer.uuid(), 5, limit);
        assertEquals("You can withdraw 4.00 more today", r);
        r = books.pay(f, other, officer.uuid(), 4.01, limit);
        assertNotNull(r);
        assertNull(books.pay(f, other, officer.uuid(), 4, limit));
        assertEquals(1000, WithdrawLimits.usedToday(f, officer.uuid()));
        assertEquals(90, f.bank());
        assertEquals(4, other.bank());
        assertEquals(6, officer.balance());
        // No limit when the service passes zero, as it does for the leader.
        assertNull(books.withdrawInternal(f, officer.uuid(), 50, 0));
    }

    @Test
    void refusedExternalWithdrawalPutsBankAndAllowanceBack() {
        FakeWallets w = new FakeWallets();
        Faction f = faction(50);
        UUID officer = UUID.randomUUID();
        w.refuse.add(officer);
        assertEquals("refused", books.withdrawExternal(w, f, officer, 20, 3000));
        assertEquals(50, f.bank());
        assertEquals(0, WithdrawLimits.usedToday(f, officer));
        assertTrue(severe.isEmpty());
    }

    @Test
    void refusedWithdrawalForAFactionDisbandedMeanwhileGoesToTheMemberMailbox() {
        Faction f = faction(50);
        UUID officer = UUID.randomUUID();
        BalanceProvider disbandsThenRefuses = new FakeWallets() {
            @Override
            public synchronized Result deposit(UUID player, double amount, String reason) {
                gone.add(f.id());
                return Result.fail("refused");
            }
        };
        assertEquals("refused", books.withdrawExternal(disbandsThenRefuses, f, officer, 20, 0));
        assertEquals(20, parked.get(officer));
        assertEquals(1, severe.size());
        assertTrue(severe.get(0).contains("20.00") && severe.get(0).contains("mailbox"), severe.get(0));

        parking = false;
        Faction g = faction(50);
        BalanceProvider again = new FakeWallets() {
            @Override
            public synchronized Result deposit(UUID player, double amount, String reason) {
                gone.add(g.id());
                return Result.fail("refused");
            }
        };
        books.withdrawExternal(again, g, officer, 5, 0);
        assertEquals(2, severe.size(), "with nowhere to keep it the loss is logged");
    }

    @Test
    void aFailedRefundIsParkedInsteadOfLost() {
        FakeWallets w = new FakeWallets();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        w.set(payer, 10);
        w.refuse.add(payee);
        w.refuse.add(payer);
        assertEquals("refused", books.transferExternal(w, payer, payee, 4, "x"));
        assertEquals(4, parked.get(payer));
        assertEquals(6, w.balance(payer));
    }

    @Test
    void aRecordUnloadedMeanwhileIsFetchedAgain() {
        PlayerData stale = player(10);
        UUID id = stale.uuid();
        // A reload after an unload puts a new record with the same id in the repository.
        PlayerData reloaded = new PlayerData(id, "p", 0, 10);
        loaded.put(id, reloaded);
        assertNull(books.walletWithdraw(id, 4));
        books.walletDeposit(id, 1);
        assertEquals(7, reloaded.balance());
        assertEquals(10, stale.balance(), "the record that left the repository is never changed");
    }

    /** A record swapped out while a change waits on it is retried on the new record. */
    @Test
    void aRecordSwappedDuringTheChangeIsRetried() {
        UUID id = UUID.randomUUID();
        PlayerData first = new PlayerData(id, "p", 0, 10);
        PlayerData second = new PlayerData(id, "p", 0, 10);
        int[] loads = {0};
        Books swapping = new Books(new Object(), f -> false, a -> String.valueOf(a), severe::add, new Books.Wallets() {
            @Override
            public PlayerData load(UUID player) {
                return loads[0]++ == 0 ? first : second;
            }

            @Override
            public boolean live(PlayerData p) {
                return p == second;
            }
        }, (p, a) -> false);
        assertNull(swapping.walletWithdraw(id, 3));
        assertEquals(10, first.balance());
        assertEquals(7, second.balance());
    }

    @Test
    void upkeepTakesNothingFromAnEmptyBank() {
        Faction f = faction(0);
        Books.Upkeep u = books.upkeep(f, 5);
        assertEquals(0, u.taken());
        assertEquals(0, f.bank());
    }

    @Test
    void bankToBankNeedsBothFactionsAndEnoughMoney() {
        Faction a = faction(10);
        Faction b = faction(0);
        assertNotNull(books.move(a, b, 10.01));
        assertNull(books.move(a, b, 10));
        assertEquals(0, a.bank());
        assertEquals(10, b.bank());
        gone.add(a.id());
        assertEquals(Messages.get(EconomyText.FACTION_GONE), books.move(b, a, 1));
        assertEquals(10, b.bank());
    }

    @Test
    void chargeCreditAndDrain() {
        Faction f = faction(10);
        String r = books.charge(f, 12);
        assertEquals("This costs 12.00 but the bank of " + f.tag() + " only holds 10.00", r);
        assertNull(books.charge(f, 4));
        assertNull(books.credit(f, 1.25));
        assertEquals(7.25, f.bank());
        assertEquals(7.25, books.drain(f));
        assertEquals(0, f.bank());
        gone.add(f.id());
        assertEquals(Messages.get(EconomyText.FACTION_GONE), books.credit(f, 1));
    }

    @Test
    void duesAreCollectedInWholeCentsAndMissesCounted() {
        Faction f = faction(0);
        PlayerData rich = player(10);
        PlayerData poor = player(0.5);
        PlayerData broke = player(0);
        int[] missed = {0};
        double total = books.duesInternal(f, List.of(rich.uuid(), poor.uuid(), broke.uuid()), 1.0, missed);
        assertEquals(1.5, total);
        assertEquals(1.5, f.bank());
        assertEquals(9, rich.balance());
        assertEquals(0, poor.balance());
        assertEquals(2, missed[0]);
    }

    @Test
    void externalDuesCreditTheBankWithWhatWasTaken() {
        FakeWallets w = new FakeWallets();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        w.set(a, 5);
        w.set(b, 0.4);
        Faction f = faction(0);
        int[] missed = {0};
        assertEquals(1.4, books.duesExternal(w, f, List.of(a, b), 1, missed));
        assertEquals(1.4, f.bank());
        assertEquals(1, missed[0]);
        assertEquals(400, w.total());
    }

    @Test
    void upkeepEmptiesABankThatCannotCoverIt() {
        Faction f = faction(3);
        Books.Upkeep u = books.upkeep(f, 5);
        assertEquals(false, u.paid());
        assertEquals(3, u.taken());
        assertEquals(0, f.bank());
        f.setBank(8);
        u = books.upkeep(f, 5);
        assertTrue(u.paid());
        assertEquals(5, u.taken());
        assertEquals(3, f.bank());
    }

    @Test
    void interestIsRoundedToCents() {
        Faction f = faction(33.33);
        assertEquals(0.33, books.interest(f, 1));
        assertEquals(33.66, f.bank());
        assertEquals(0, books.interest(faction(0.4), 1));
    }

    // ---- property tests ------------------------------------------------------------

    /**
     * Random sequences of every internal operation. Moves never change the total, a failed move
     * changes nothing, and only charges, credits, drains, upkeep and interest change the total, by
     * exactly the amount they report. Balances stay whole cents and never go negative.
     */
    @Test
    void randomInternalSequencesConserveMoney() {
        for (int seed = 0; seed < 200; seed++) {
            Random rnd = new Random(seed);
            gone.clear();
            List<PlayerData> players = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                players.add(player(rnd.nextInt(5000) / 100.0));
            }
            List<Faction> factions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                factions.add(faction(rnd.nextInt(5000) / 100.0));
            }
            long expected = total(players, factions);
            for (int step = 0; step < 400; step++) {
                PlayerData a = players.get(rnd.nextInt(players.size()));
                PlayerData b = players.get(rnd.nextInt(players.size()));
                Faction f = factions.get(rnd.nextInt(factions.size()));
                Faction g = factions.get(rnd.nextInt(factions.size()));
                double amount = (1 + rnd.nextInt(rnd.nextBoolean() ? 3000 : 300)) / 100.0;
                long limit = rnd.nextInt(4) == 0 ? 1 + rnd.nextInt(2000) : 0;
                long before = total(players, factions);
                Map<Object, Double> snapshot = snapshot(players, factions);
                String failed;
                switch (rnd.nextInt(12)) {
                    case 0 -> failed = a == b ? "same" : books.transferInternal(a.uuid(), b.uuid(), amount);
                    case 1 -> failed = books.depositInternal(a.uuid(), f, amount);
                    case 2 -> failed = books.withdrawInternal(f, a.uuid(), amount, limit);
                    case 3 -> failed = f == g ? "same" : books.pay(f, g, a.uuid(), amount, limit);
                    case 4 -> failed = f == g ? "same" : books.move(f, g, amount);
                    case 5 -> {
                        failed = books.charge(f, amount);
                        if (failed == null) {
                            expected -= cents(amount);
                        }
                    }
                    case 6 -> {
                        failed = books.credit(f, amount);
                        if (failed == null) {
                            expected += cents(amount);
                        }
                    }
                    case 7 -> {
                        expected -= cents(books.drain(f));
                        failed = null;
                    }
                    case 8 -> {
                        int[] missed = {0};
                        books.duesInternal(f, List.of(a.uuid(), b.uuid()), amount, missed);
                        failed = null;
                    }
                    case 9 -> {
                        expected -= cents(books.upkeep(f, amount).taken());
                        failed = null;
                    }
                    case 10 -> {
                        expected += cents(books.interest(f, rnd.nextInt(10)));
                        failed = null;
                    }
                    default -> {
                        UUID id = f.id();
                        if (!gone.remove(id)) {
                            gone.add(id);
                        }
                        failed = null;
                    }
                }
                long after = total(players, factions);
                assertEquals(expected, after, "seed " + seed + " step " + step);
                if (failed != null) {
                    assertEquals(before, after);
                    assertEquals(snapshot, snapshot(players, factions), "a failed move changed a balance, seed " + seed);
                }
                for (PlayerData p : players) {
                    assertTrue(p.balance() >= 0 && wholeCents(p.balance()), "seed " + seed);
                }
                for (Faction x : factions) {
                    assertTrue(x.bank() >= 0 && wholeCents(x.bank()), "seed " + seed);
                }
            }
        }
        assertTrue(severe.isEmpty());
    }

    /**
     * Random sequences against an external provider that refuses deposits to one account. The
     * total only drops when a refund could not be made, and that is always logged with the amount.
     */
    @Test
    void randomExternalSequencesConserveMoneyOrLogTheLoss() {
        for (int seed = 0; seed < 200; seed++) {
            Random rnd = new Random(1000 + seed);
            gone.clear();
            severe.clear();
            FakeWallets w = new FakeWallets();
            List<UUID> people = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                UUID u = UUID.randomUUID();
                people.add(u);
                w.set(u, rnd.nextInt(5000) / 100.0);
            }
            w.refuse.add(people.get(0));
            List<Faction> factions = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                factions.add(faction(rnd.nextInt(5000) / 100.0));
            }
            long expected = w.total() + banks(factions);
            for (int step = 0; step < 300; step++) {
                UUID a = people.get(rnd.nextInt(people.size()));
                UUID b = people.get(rnd.nextInt(people.size()));
                Faction f = factions.get(rnd.nextInt(factions.size()));
                double amount = (1 + rnd.nextInt(3000)) / 100.0;
                int logged = severe.size();
                switch (rnd.nextInt(5)) {
                    case 0 -> {
                        if (a != b) {
                            books.transferExternal(w, a, b, amount, "test");
                        }
                    }
                    case 1 -> books.depositExternal(w, a, f, amount);
                    case 2 -> books.withdrawExternal(w, f, a, amount, rnd.nextBoolean() ? 2000 : 0);
                    case 3 -> books.duesExternal(w, f, List.of(a, b), amount, new int[1]);
                    default -> {
                        if (!gone.remove(f.id())) {
                            gone.add(f.id());
                        }
                    }
                }
                if (severe.size() > logged) {
                    assertEquals(logged + 1, severe.size());
                    assertTrue(severe.get(logged).contains(String.format(Locale.ROOT, "%.2f", amount)), severe.get(logged));
                    expected -= cents(amount);
                }
                assertEquals(expected, w.total() + banks(factions), "seed " + seed + " step " + step);
            }
        }
    }

    /** Many threads moving money between the same wallets and banks never lose a cent or deadlock. */
    @Test
    void concurrentMovesConserveMoneyAndNeverDeadlock() {
        List<PlayerData> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            players.add(player(500));
        }
        List<Faction> factions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            factions.add(faction(500));
        }
        long start = total(players, factions);
        AtomicReference<Throwable> error = new AtomicReference<>();
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CountDownLatch go = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                Thread th = new Thread(() -> {
                    Random rnd = new Random(seed);
                    try {
                        go.await();
                        for (int i = 0; i < 20_000; i++) {
                            PlayerData a = players.get(rnd.nextInt(players.size()));
                            PlayerData b = players.get(rnd.nextInt(players.size()));
                            Faction f = factions.get(rnd.nextInt(factions.size()));
                            Faction g = factions.get(rnd.nextInt(factions.size()));
                            double amount = (1 + rnd.nextInt(2000)) / 100.0;
                            switch (rnd.nextInt(5)) {
                                case 0 -> { if (a != b) books.transferInternal(a.uuid(), b.uuid(), amount); }
                                case 1 -> books.depositInternal(a.uuid(), f, amount);
                                case 2 -> books.withdrawInternal(f, a.uuid(), amount, 0);
                                case 3 -> { if (f != g) books.pay(f, g, a.uuid(), amount, 0); }
                                default -> { if (f != g) books.move(f, g, amount); }
                            }
                        }
                    } catch (Throwable e) {
                        error.compareAndSet(null, e);
                    }
                });
                threads.add(th);
                th.start();
            }
            go.countDown();
            for (Thread th : threads) {
                th.join();
            }
        });
        assertNull(error.get());
        assertEquals(start, total(players, factions));
    }

    private static long total(List<PlayerData> players, List<Faction> factions) {
        long t = banks(factions);
        for (PlayerData p : players) {
            t += cents(p.balance());
        }
        return t;
    }

    private static long banks(List<Faction> factions) {
        long t = 0;
        for (Faction f : factions) {
            t += cents(f.bank());
        }
        return t;
    }

    private static Map<Object, Double> snapshot(List<PlayerData> players, List<Faction> factions) {
        Map<Object, Double> m = new HashMap<>();
        players.forEach(p -> m.put(p.uuid(), p.balance()));
        factions.forEach(f -> m.put(f.id(), f.bank()));
        return m;
    }
}
