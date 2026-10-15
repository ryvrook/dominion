package dev.ryvrook.dominion.config;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The permission nodes Dominion checks, renamed through the permissions settings section so they fit
 * the server's permissions plugin. Commands require their node when they are registered, so a new
 * name applies to them at the next start or /plugin reload. Checks made while the game runs read the
 * setting every time.
 */
public final class Perms {
    private Perms() {}

    public static final String ADMIN = "permissions.admin";
    public static final String PAYOUTS = "permissions.adminPayouts";

    /** Every node key with its shipped node name, in the order the settings page lists them. */
    public static final Map<String, String> DEFAULTS = defaults();

    private static volatile Settings settings;

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ADMIN, "dominion.admin");
        m.put(PAYOUTS, "dominion.admin.payouts");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The settings the nodes are read from. Settings binds itself when it is made. */
    static void bind(Settings s) {
        settings = s;
    }

    /** The node for a permissions key, its default when the setting is blank or unknown. */
    public static String node(String key) {
        Settings s = settings;
        String v = s == null || !s.known(key) ? null : s.text(key).trim();
        return v == null || v.isEmpty() ? DEFAULTS.getOrDefault(key, key) : v;
    }

    /** The node of every admin command, page and tool. */
    public static String admin() {
        return node(ADMIN);
    }

    /** The node that allows running season payout console commands, on top of the admin node. */
    public static String payouts() {
        return node(PAYOUTS);
    }

    /** True when the player holds the node of the key. False for a null player. */
    public static boolean has(PlayerRef player, String key) {
        return player != null && player.hasPermission(node(key));
    }
}
