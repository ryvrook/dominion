package dev.ryvrook.dominion.integration.feats;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.integration.Host;
import dev.ryvrook.dominion.integration.IntegrationText;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.integration.StatKeys;
import dev.ryvrook.dominion.integration.StatSink;
import dev.ryvrook.dominion.integration.VersionCompare;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.feats.api.FeatUnlockedEvent;
import dev.ryvrook.feats.api.FeatsApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mirrors Dominion's faction counters into Feats with {@code FeatsApi.addStat} and posts feat unlocks to the
 * player's faction chat. The only class of Dominion that uses Feats types, loaded by name once the plugin manager
 * listed Feats.
 *
 * <p>Feats publishes its API in its own setup and has no enable hook, so the bridge links at once when the API
 * is there and otherwise tries again on every watch poll. A Feats reload drops the unlock listener on the Feats
 * side and leaves this class with the old API, which the integration watch reports.
 */
public final class FeatsBridge implements Link, StatSink {
    static final String ANNOUNCE = "integrations.featsAnnounce";
    /** The first Feats that keeps the keys added in Dominion 0.7.1. An older Feats gets the original ten only. */
    static final String ALL_KEYS_SINCE = "1.7.2";

    private final Host host;
    private volatile FeatsApi api;
    private volatile Runnable removeListener;
    private volatile boolean stopped;
    private volatile boolean warned;

    private FeatsBridge(Host host) {
        this.host = host;
    }

    /** Called by name from the integration module. */
    public static Link install(Host host) {
        FeatsBridge b = new FeatsBridge(host);
        host.stats().add(b);
        b.poll();
        return b;
    }

    /** Links to the running Feats API when it is not linked yet. */
    @Override
    public synchronized void poll() {
        if (stopped) {
            return;
        }
        FeatsApi live;
        try {
            live = FeatsApi.get();
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (live == null || live == api) {
            return;
        }
        dropListener();
        api = live;
        try {
            removeListener = live.onUnlock(this::onUnlock);
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Dominion could not listen to Feats unlocks, faction chat will not show them");
        }
        host.info("Dominion is bound to Feats " + host.foundVersion() + " and mirrors its faction counters there");
    }

    @Override
    public void add(UUID player, String key, long amount) {
        FeatsApi a = api;
        if (stopped || a == null || player == null || amount <= 0 || !keeps(host.foundVersion(), key)) {
            return;
        }
        try {
            once(a, FeatsBridge::currentApi, x -> x.addStat(player, key, amount), x -> poll());
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                host.warn(e, "Feats refused a Dominion counter, later failures are not logged");
            }
        }
    }

    /**
     * True when a Feats of this version keeps the key. Feats logs a warning once a minute for a key it does not
     * keep, so the keys added in 0.7.1 are held back from a Feats older than {@link #ALL_KEYS_SINCE}. A version
     * the plugin manager did not report counts as new enough.
     */
    static boolean keeps(String featsVersion, String key) {
        return featsVersion == null || featsVersion.isBlank() || !VersionCompare.below(featsVersion, ALL_KEYS_SINCE)
                || StatKeys.ORIGINAL.contains(key);
    }

    /**
     * Calls {@code add} on {@code held}. Feats 1.5.1 answers false from an API object left over from before a Feats
     * reload, so on false the running API is fetched again, and when it is a different object the bridge relinks to
     * it with {@code relink} and the add is tried once more on it. A false for any other reason, such as a key Feats
     * does not keep, costs one read of the running API and no retry.
     */
    static <A> boolean once(A held, java.util.function.Supplier<A> running, java.util.function.Predicate<A> add,
                            Consumer<A> relink) {
        if (add.test(held)) {
            return true;
        }
        A fresh = running.get();
        if (fresh == null || fresh == held) {
            return false;
        }
        relink.accept(fresh);
        return add.test(fresh);
    }

    /** Runs on the thread that caused the unlock. Only sends a chat line to online members. */
    private void onUnlock(FeatUnlockedEvent e) {
        if (stopped || e == null || e.catchUp() || e.uuid() == null) {
            return;
        }
        try {
            DominionContext ctx = host.ctx();
            if (!ctx.settings().bool(ANNOUNCE)) {
                return;
            }
            FactionService factions = ctx.serviceOrNull(FactionService.class);
            ChatService chat = ctx.serviceOrNull(ChatService.class);
            Faction f = factions == null ? null : factions.factionOf(e.uuid());
            if (f == null || chat == null) {
                return;
            }
            String name = e.playerName() == null || e.playerName().isBlank() ? ctx.player(e.uuid()).name() : e.playerName();
            chat.sendToFaction(f, Message.join(Msg.prefix(), Msg.colored(name, Msg.GOLD),
                    Msg.colored(Messages.get(IntegrationText.FEAT_EARNED), Msg.TEXT), Msg.colored(e.featName(), Msg.GOLD),
                    Msg.colored(".", Msg.TEXT)), false);
        } catch (RuntimeException | LinkageError ignored) {
            // an announcement is never worth an error in the Feats unlock path
        }
    }

    private void dropListener() {
        Runnable r = removeListener;
        removeListener = null;
        if (r != null) {
            try {
                r.run();
            } catch (RuntimeException | LinkageError ignored) {
                // Feats already dropped its listeners
            }
        }
    }

    @Override
    public boolean bound() {
        return !stopped && api != null && api == currentApi();
    }

    private static FeatsApi currentApi() {
        try {
            return FeatsApi.get();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public String version() {
        return host.foundVersion();
    }

    @Override
    public synchronized void stop() {
        stopped = true;
        host.stats().remove(this);
        dropListener();
        api = null;
    }
}
