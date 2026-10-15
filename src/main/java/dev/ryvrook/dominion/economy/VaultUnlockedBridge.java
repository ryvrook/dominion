package dev.ryvrook.dominion.economy;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.text.Messages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.logging.Level;

/**
 * Player balances held by a VaultUnlocked economy. Everything goes through reflection so Dominion
 * has no compile dependency. Discovery tries the known VaultUnlocked economy interfaces in every
 * plugin class loader, then the known service holders, then any loaded plugin that exposes an
 * economy through a no argument method. The bridge supports the VaultUnlocked 2 calls that take a
 * plugin name, UUID and BigDecimal, and the older calls that take a UUID and a double.
 */
final class VaultUnlockedBridge implements BalanceProvider {
    static final String[] ECONOMY_CLASSES = {
            "net.milkbowl.vault2.economy.Economy",
            "net.cfh.vault.economy.Economy",
            "net.milkbowl.vault.economy.Economy"
    };

    /** Holder class, then the chain of no argument methods that leads to the economy. */
    static final String[][] HOLDERS = {
            { "net.cfh.vault.VaultUnlockedServicesManager", "get", "economyObj" },
            { "net.cfh.vault.VaultUnlockedServicesManager", "get", "economy" },
            { "net.cfh.vault.VaultUnlocked", "economy" },
            { "net.cfh.vault.VaultUnlocked", "getEconomy" },
            { "net.milkbowl.vault2.VaultUnlocked", "economy" },
            { "net.milkbowl.vault2.VaultUnlocked", "getEconomy" },
            { "net.milkbowl.vault2.VaultUnlockedServicesManager", "get", "economyObj" }
    };

    private static final String PLUGIN = "Dominion";

    private final DominionContext ctx;
    private final DoubleFunction<String> format;
    private final Object economy;
    private final Method balance;
    private final Method withdraw;
    private final Method deposit;
    private final Method hasAccount;
    private final Method createAccount;

    private VaultUnlockedBridge(DominionContext ctx, DoubleFunction<String> format, Object economy) {
        this.ctx = ctx;
        this.format = format;
        this.economy = economy;
        Class<?> c = economy.getClass();
        this.balance = first(c, new String[] { "balance", "getBalance" },
                new Class<?>[][] { { String.class, UUID.class }, { UUID.class } });
        this.withdraw = first(c, new String[] { "withdraw" },
                new Class<?>[][] { { String.class, UUID.class, BigDecimal.class }, { UUID.class, BigDecimal.class }, { UUID.class, double.class } });
        this.deposit = first(c, new String[] { "deposit" },
                new Class<?>[][] { { String.class, UUID.class, BigDecimal.class }, { UUID.class, BigDecimal.class }, { UUID.class, double.class } });
        this.hasAccount = first(c, new String[] { "hasAccount" }, new Class<?>[][] { { UUID.class } });
        this.createAccount = first(c, new String[] { "createAccount" }, new Class<?>[][] { { UUID.class, String.class } });
    }

    /** The bridge when a usable VaultUnlocked economy is loaded, otherwise null. */
    static VaultUnlockedBridge discover(DominionContext ctx, DoubleFunction<String> format) {
        Object eco = findEconomy(ctx);
        if (eco == null) {
            return null;
        }
        VaultUnlockedBridge bridge = forEconomy(ctx, format, eco);
        if (bridge == null) {
            ctx.logger().at(Level.WARNING).log("Found a VaultUnlocked economy in %s but it has no usable balance, withdraw and deposit calls",
                    eco.getClass().getName());
            return null;
        }
        ctx.logger().at(Level.INFO).log("Dominion uses the VaultUnlocked economy %s", eco.getClass().getName());
        return bridge;
    }

    /**
     * A bridge over an economy object that was already found, or null when it lacks a usable
     * balance, withdraw or deposit call. The context may be null in tests, which only skips logging
     * and account creation.
     */
    static VaultUnlockedBridge forEconomy(DominionContext ctx, DoubleFunction<String> format, Object economy) {
        if (economy == null) {
            return null;
        }
        VaultUnlockedBridge bridge = new VaultUnlockedBridge(ctx, format, economy);
        return bridge.balance == null || bridge.withdraw == null || bridge.deposit == null ? null : bridge;
    }

    @Override
    public String name() {
        return "vaultunlocked";
    }

    // ---- discovery --------------------------------------------------------

    private static Object findEconomy(DominionContext ctx) {
        List<ClassLoader> loaders = loaders();
        List<Class<?>> types = new ArrayList<>();
        for (String name : ECONOMY_CLASSES) {
            Class<?> t = load(name, loaders);
            if (t != null) {
                types.add(t);
            }
        }
        for (String[] holder : HOLDERS) {
            Object eco = fromHolder(holder, loaders, types);
            if (eco != null) {
                return eco;
            }
        }
        if (types.isEmpty()) {
            return null;
        }
        try {
            for (PluginBase plugin : PluginManager.get().getPlugins()) {
                Object eco = fromObject(plugin, types);
                if (eco != null) {
                    return eco;
                }
            }
        } catch (RuntimeException e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Could not scan plugins for an economy");
        }
        return null;
    }

    private static List<ClassLoader> loaders() {
        Set<ClassLoader> set = new LinkedHashSet<>();
        set.add(VaultUnlockedBridge.class.getClassLoader());
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            set.add(context);
        }
        try {
            for (PluginBase plugin : PluginManager.get().getPlugins()) {
                set.add(plugin.getClass().getClassLoader());
            }
        } catch (RuntimeException ignored) {
            // plugin manager not ready
        }
        set.remove(null);
        return new ArrayList<>(set);
    }

    private static Class<?> load(String name, List<ClassLoader> loaders) {
        for (ClassLoader l : loaders) {
            try {
                return Class.forName(name, true, l);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next loader
            }
        }
        return null;
    }

    private static Object fromHolder(String[] holder, List<ClassLoader> loaders, List<Class<?>> types) {
        Class<?> c = load(holder[0], loaders);
        if (c == null) {
            return null;
        }
        try {
            Object current = null;
            Class<?> type = c;
            for (int i = 1; i < holder.length; i++) {
                Method m = type.getMethod(holder[i]);
                if (current == null && !Modifier.isStatic(m.getModifiers())) {
                    return null;
                }
                accessible(m);
                current = m.invoke(current);
                if (current == null) {
                    return null;
                }
                type = current.getClass();
            }
            return types.isEmpty() || matches(current, types) ? current : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** An object that is an economy, or exposes one through a public no argument method or field. */
    private static Object fromObject(Object o, List<Class<?>> types) {
        if (o == null) {
            return null;
        }
        if (matches(o, types)) {
            return o;
        }
        for (Method m : o.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && returnsEconomy(m.getReturnType(), types)) {
                try {
                    accessible(m);
                    Object eco = m.invoke(o);
                    if (eco != null && matches(eco, types)) {
                        return eco;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // keep looking
                }
            }
        }
        for (Field f : o.getClass().getFields()) {
            if (returnsEconomy(f.getType(), types)) {
                try {
                    Object eco = f.get(Modifier.isStatic(f.getModifiers()) ? null : o);
                    if (eco != null && matches(eco, types)) {
                        return eco;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // keep looking
                }
            }
        }
        return null;
    }

    /** Public methods of a hidden implementation class need this before they can be invoked. */
    private static void accessible(Method m) {
        try {
            m.setAccessible(true);
        } catch (RuntimeException ignored) {
            // a public method on a public type works without it
        }
    }

    private static boolean matches(Object o, List<Class<?>> types) {
        for (Class<?> t : types) {
            if (t.isInstance(o)) {
                return true;
            }
        }
        return false;
    }

    private static boolean returnsEconomy(Class<?> r, List<Class<?>> types) {
        for (Class<?> t : types) {
            if (t.isAssignableFrom(r)) {
                return true;
            }
        }
        return false;
    }

    private static Method first(Class<?> c, String[] names, Class<?>[][] signatures) {
        for (Class<?>[] sig : signatures) {
            for (String n : names) {
                try {
                    Method m = c.getMethod(n, sig);
                    accessible(m);
                    return m;
                } catch (NoSuchMethodException | RuntimeException ignored) {
                    // try the next signature
                }
            }
        }
        return null;
    }

    // ---- calls ------------------------------------------------------------

    @Override
    public double balance(UUID player) {
        ensureAccount(player);
        try {
            Object r = balance.getParameterCount() == 2 ? balance.invoke(economy, PLUGIN, player) : balance.invoke(economy, player);
            return r instanceof Number n ? n.doubleValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return 0;
        }
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        double have = balance(player);
        if (!Money.covers(have, amount)) {
            return Result.fail(Messages.get(EconomyText.NEED_HAVE, "amount", format.apply(amount), "have", format.apply(have)));
        }
        return move(withdraw, player, amount, Messages.get(EconomyText.PAYMENT_REFUSED));
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        return move(deposit, player, amount, Messages.get(EconomyText.PAYMENT_NOT_ACCEPTED));
    }

    @Override
    public void set(UUID player, double amount) {
        double current = balance(player);
        double target = Math.max(0, amount);
        if (target > current + 1e-6) {
            deposit(player, Money.round(target - current), Messages.get(EconomyText.REASON_ADMIN_SET));
        } else if (target < current - 1e-6) {
            move(withdraw, player, Money.round(current - target), "");
        }
    }

    private Result move(Method m, UUID player, double amount, String refused) {
        ensureAccount(player);
        try {
            Class<?>[] p = m.getParameterTypes();
            Object value = p[p.length - 1] == BigDecimal.class
                    ? BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP) : (Object) amount;
            Object r = p.length == 3 ? m.invoke(economy, PLUGIN, player, value) : m.invoke(economy, player, value);
            return success(r) ? Result.pass() : Result.fail(errorOf(r, refused));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return Result.fail(Messages.get(EconomyText.NOT_RESPONDING));
        }
    }

    private void ensureAccount(UUID player) {
        if (hasAccount == null || createAccount == null || ctx == null) {
            return;
        }
        try {
            if (Boolean.FALSE.equals(hasAccount.invoke(economy, player))) {
                String name = ctx.player(player).name();
                createAccount.invoke(economy, player, name == null || name.isEmpty() ? player.toString() : name);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the economy may create accounts on its own
        }
    }

    /**
     * Reads transactionSuccess from an EconomyResponse, then its type field or method, and treats a
     * boolean or a missing response as the answer. A response with none of these counts as a
     * success, because the call itself returned without an error.
     */
    static boolean success(Object response) {
        if (response == null) {
            return true;
        }
        if (response instanceof Boolean b) {
            return b;
        }
        try {
            Method m = response.getClass().getMethod("transactionSuccess");
            accessible(m);
            return Boolean.TRUE.equals(m.invoke(response));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older or newer response shapes below
        }
        Object type = null;
        try {
            Field f = response.getClass().getField("type");
            type = f.get(response);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Method m = response.getClass().getMethod("type");
                accessible(m);
                type = m.invoke(response);
            } catch (ReflectiveOperationException | RuntimeException ignoredToo) {
                // no type either
            }
        }
        return type == null || "SUCCESS".equalsIgnoreCase(type.toString());
    }

    static String errorOf(Object response, String fallback) {
        if (response == null) {
            return fallback;
        }
        try {
            Field f = response.getClass().getField("errorMessage");
            Object v = f.get(response);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no message available
        }
        return fallback;
    }

    private volatile long lastWarn;

    private void warn(Exception e) {
        long now = System.currentTimeMillis();
        if (ctx != null && now - lastWarn > 60_000L) {
            lastWarn = now;
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not reach the VaultUnlocked economy");
        }
    }
}
