package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.model.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerMigrationTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private final Object lock = new Object();

    private static PlayerData player(double balance) {
        PlayerData p = new PlayerData(UUID.randomUUID(), "p", 0, balance);
        p.clearDirty();
        return p;
    }

    @Test
    void runsOnceAtStartThenDaily() {
        assertTrue(LedgerMigration.due(0, 5));
        assertFalse(LedgerMigration.due(1000, 1000 + DAY - 1));
        assertTrue(LedgerMigration.due(1000, 1000 + DAY));
        // A clock that moved backwards does not block the next run for a day.
        assertTrue(LedgerMigration.due(5000, 4000));
    }

    @Test
    void movesTheBalanceAfterTheZeroIsSaved() {
        PlayerData p = player(12.34);
        BooksTest.FakeWallets w = new BooksTest.FakeWallets();
        List<Double> savedBalances = new ArrayList<>();
        LedgerMigration.Step s = LedgerMigration.moveOne(p, lock, pd -> true, pd -> {
            savedBalances.add(pd.balance());
            return true;
        }, w);
        assertEquals(LedgerMigration.Outcome.MOVED, s.outcome());
        assertEquals(12.34, s.amount());
        assertEquals(0, p.balance());
        assertEquals(12.34, w.balance(p.uuid()));
        assertEquals(List.of(0.0), savedBalances);
    }

    @Test
    void onlyATouchedBalanceMoves() {
        PlayerData untouched = player(100);
        PlayerData touched = player(0);
        touched.setBalance(100);
        BooksTest.FakeWallets w = new BooksTest.FakeWallets();
        assertEquals(LedgerMigration.Outcome.SKIPPED,
                LedgerMigration.moveOne(untouched, lock, pd -> true, pd -> true, w, 100).outcome());
        assertEquals(LedgerMigration.Outcome.MOVED,
                LedgerMigration.moveOne(touched, lock, pd -> true, pd -> true, w, 100).outcome());
        assertEquals(100, untouched.balance());
        assertEquals(0, w.balance(untouched.uuid()));
        assertEquals(100, w.balance(touched.uuid()));

        // An old file has no flag, so it counts as untouched only while it still holds the starting balance.
        PlayerData oldStart = PlayerData.fromDocument(UUID.randomUUID(), new org.bson.Document("Balance", 100.0));
        PlayerData oldEarned = PlayerData.fromDocument(UUID.randomUUID(), new org.bson.Document("Balance", 150.0));
        assertEquals(LedgerMigration.Outcome.SKIPPED, LedgerMigration.moveOne(oldStart, lock, pd -> true, pd -> true, w, 100).outcome());
        assertEquals(LedgerMigration.Outcome.MOVED, LedgerMigration.moveOne(oldEarned, lock, pd -> true, pd -> true, w, 100).outcome());
        // The flag survives a save and a load.
        assertTrue(PlayerData.fromDocument(touched.uuid(), touched.toDocument()).balanceTouched());
        assertFalse(PlayerData.fromDocument(untouched.uuid(), untouched.toDocument()).balanceTouched());
    }

    @Test
    void aFailedSaveDepositsNothingAndKeepsTheBalance() {
        PlayerData p = player(7);
        BooksTest.FakeWallets w = new BooksTest.FakeWallets();
        LedgerMigration.Step s = LedgerMigration.moveOne(p, lock, pd -> true, pd -> false, w);
        assertEquals(LedgerMigration.Outcome.FAILED, s.outcome());
        assertEquals(7, p.balance());
        assertEquals(0, w.total());
        assertTrue(p.isDirty());
    }

    @Test
    void aRefusedOrThrowingDepositPutsTheBalanceBack() {
        PlayerData p = player(3);
        BooksTest.FakeWallets w = new BooksTest.FakeWallets();
        w.refuse.add(p.uuid());
        assertEquals(LedgerMigration.Outcome.FAILED, LedgerMigration.moveOne(p, lock, pd -> true, pd -> true, w).outcome());
        assertEquals(3, p.balance());

        BooksTest.FakeWallets broken = new BooksTest.FakeWallets() {
            @Override
            public synchronized dev.ryvrook.dominion.api.Result deposit(UUID player, double amount, String reason) {
                throw new IllegalStateException("down");
            }
        };
        LedgerMigration.Step s = LedgerMigration.moveOne(p, lock, pd -> true, pd -> true, broken);
        assertEquals(LedgerMigration.Outcome.FAILED, s.outcome());
        assertEquals("down", s.reason());
        assertEquals(3, p.balance());
    }

    @Test
    void aRecordThatIsNoLongerLoadedIsLeftAlone() {
        PlayerData p = player(5);
        BooksTest.FakeWallets w = new BooksTest.FakeWallets();
        assertEquals(LedgerMigration.Outcome.SKIPPED, LedgerMigration.moveOne(p, lock, pd -> false, pd -> true, w).outcome());
        assertEquals(5, p.balance());
        assertEquals(0, w.total());
    }

    @Test
    void emptyBalancesAreSkipped() {
        PlayerData p = player(0.004);
        assertEquals(LedgerMigration.Outcome.SKIPPED, LedgerMigration.moveOne(p, lock, pd -> true, pd -> {
            throw new AssertionError("nothing to save");
        }, new BooksTest.FakeWallets()).outcome());
    }

    /** Random failures never create or lose money between the ledger and the external wallets. */
    @Test
    void randomFailuresConserveMoney() {
        Random rnd = new Random(7);
        for (int round = 0; round < 500; round++) {
            PlayerData p = player(rnd.nextInt(100_000) / 100.0);
            BooksTest.FakeWallets w = new BooksTest.FakeWallets();
            if (rnd.nextBoolean()) {
                w.refuse.add(p.uuid());
            }
            boolean saves = rnd.nextInt(4) != 0;
            long before = BooksTest.cents(p.balance());
            LedgerMigration.moveOne(p, lock, pd -> true, pd -> saves, w);
            assertEquals(before, BooksTest.cents(p.balance()) + w.total());
        }
    }
}
