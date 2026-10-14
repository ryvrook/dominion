package dev.ryvrook.dominion.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultUnlockedBridgeTest {
    private static String fmt(double a) {
        return String.format(Locale.ROOT, "%.2f", a);
    }

    /** Shaped like a VaultUnlocked 2 economy with plugin names and BigDecimal amounts. */
    public static final class Vu2Economy {
        final Map<UUID, BigDecimal> wallets = new HashMap<>();
        String lastPlugin;

        public BigDecimal balance(String plugin, UUID id) {
            lastPlugin = plugin;
            return wallets.getOrDefault(id, BigDecimal.ZERO);
        }

        public Response withdraw(String plugin, UUID id, BigDecimal amount) {
            BigDecimal have = wallets.getOrDefault(id, BigDecimal.ZERO);
            if (have.compareTo(amount) < 0) {
                return new Response(false, "Insufficient funds");
            }
            wallets.put(id, have.subtract(amount));
            return new Response(true, "");
        }

        public Response deposit(String plugin, UUID id, BigDecimal amount) {
            if (amount.scale() > 2) {
                return new Response(false, "Bad scale");
            }
            wallets.merge(id, amount, BigDecimal::add);
            return new Response(true, "");
        }
    }

    public static final class Response {
        public final String errorMessage;
        private final boolean ok;

        Response(boolean ok, String errorMessage) {
            this.ok = ok;
            this.errorMessage = errorMessage;
        }

        public boolean transactionSuccess() {
            return ok;
        }
    }

    /** Shaped like the old Vault economy with doubles and boolean answers. */
    public static final class LegacyEconomy {
        final Map<UUID, Double> wallets = new HashMap<>();
        boolean refuse;

        public double getBalance(UUID id) {
            return wallets.getOrDefault(id, 0.0);
        }

        public boolean withdraw(UUID id, double amount) {
            if (refuse) {
                return false;
            }
            wallets.merge(id, -amount, Double::sum);
            return true;
        }

        public boolean deposit(UUID id, double amount) {
            wallets.merge(id, amount, Double::sum);
            return true;
        }
    }

    public static final class NoDeposit {
        public double getBalance(UUID id) {
            return 0;
        }

        public boolean withdraw(UUID id, double amount) {
            return true;
        }
    }

    public static final class TypedResponse {
        public final String type;

        TypedResponse(String type) {
            this.type = type;
        }
    }

    @Test
    void vaultUnlockedTwoCallsPassThePluginNameAndCents() {
        Vu2Economy eco = new Vu2Economy();
        VaultUnlockedBridge b = VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, eco);
        assertNotNull(b);
        UUID p = UUID.randomUUID();
        assertTrue(b.deposit(p, 10.1, "test").success());
        assertEquals(10.1, b.balance(p));
        assertEquals("Dominion", eco.lastPlugin);
        assertEquals(new BigDecimal("10.10"), eco.wallets.get(p));
        assertTrue(b.withdraw(p, 0.1, "test").success());
        assertEquals(10.0, b.balance(p));
        String r = b.withdraw(p, 20, "test").message();
        assertEquals("You need 20.00 but only have 10.00", r);
    }

    @Test
    void legacyCallsWithBooleansWork() {
        LegacyEconomy eco = new LegacyEconomy();
        VaultUnlockedBridge b = VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, eco);
        assertNotNull(b);
        UUID p = UUID.randomUUID();
        b.deposit(p, 5, "x");
        assertTrue(b.withdraw(p, 2, "x").success());
        assertEquals(3, b.balance(p));
        eco.refuse = true;
        assertEquals("The payment was refused by the economy", b.withdraw(p, 1, "x").message());
        assertEquals(3, b.balance(p));
    }

    @Test
    void adminSetMovesTheDifference() {
        LegacyEconomy eco = new LegacyEconomy();
        VaultUnlockedBridge b = VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, eco);
        UUID p = UUID.randomUUID();
        b.set(p, 12.5);
        assertEquals(12.5, b.balance(p));
        b.set(p, 2);
        assertEquals(2, b.balance(p), 1e-9);
        b.set(p, -4);
        assertEquals(0, b.balance(p), 1e-9);
    }

    @Test
    void anEconomyWithoutDepositIsNotUsable() {
        assertNull(VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, new NoDeposit()));
        assertNull(VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, null));
    }

    @Test
    void responsesAreReadFromEveryKnownShape() {
        assertTrue(VaultUnlockedBridge.success(null));
        assertTrue(VaultUnlockedBridge.success(true));
        assertFalse(VaultUnlockedBridge.success(false));
        assertTrue(VaultUnlockedBridge.success(new Response(true, "")));
        assertFalse(VaultUnlockedBridge.success(new Response(false, "no")));
        assertFalse(VaultUnlockedBridge.success(new TypedResponse("FAILURE")));
        assertTrue(VaultUnlockedBridge.success(new TypedResponse("SUCCESS")));
        assertTrue(VaultUnlockedBridge.success("something else"));
        assertEquals("no", VaultUnlockedBridge.errorOf(new Response(false, "no"), "fallback"));
        assertEquals("fallback", VaultUnlockedBridge.errorOf(new Response(false, " "), "fallback"));
        assertEquals("fallback", VaultUnlockedBridge.errorOf(null, "fallback"));
    }

    @Test
    void anEconomyThatThrowsAnswersWithAReason() {
        VaultUnlockedBridge b = VaultUnlockedBridge.forEconomy(null, VaultUnlockedBridgeTest::fmt, new Object() {
            @SuppressWarnings("unused")
            public double getBalance(UUID id) {
                throw new IllegalStateException("down");
            }

            @SuppressWarnings("unused")
            public boolean withdraw(UUID id, double amount) {
                throw new IllegalStateException("down");
            }

            @SuppressWarnings("unused")
            public boolean deposit(UUID id, double amount) {
                throw new IllegalStateException("down");
            }
        });
        assertNotNull(b);
        UUID p = UUID.randomUUID();
        assertEquals(0, b.balance(p));
        assertEquals("The economy is not responding right now", b.deposit(p, 1, "x").message());
    }
}
