package dev.ryvrook.dominion.integration.atlas;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.command.PlayerToggles;
import dev.ryvrook.dominion.integration.Host;
import dev.ryvrook.dominion.integration.IntegrationText;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.atlas.api.AtlasApi;
import dev.ryvrook.atlas.api.Marker;
import dev.ryvrook.atlas.api.MarkerLayer;
import dev.ryvrook.atlas.api.Tier;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Pushes what the map snapshot lacks to Atlas through its API, and keeps the two web map hide switches in step.
 * The only class of Dominion that uses Atlas types, loaded by name once the plugin manager listed Atlas.
 *
 * <p>Atlas already draws claims, homes, warps, siege pins, hills, outposts and envoys from export/snapshot.json, so
 * none of those are sent again. The snapshot pin of a siege has no phase and no end time, so the bridge keeps one
 * live layer, {@code Ryvrook:Dominion:sieges}, with a circle over each besieged chunk whose popup names the phase
 * and when it ends. The layer is redrawn on siege events only and removed when Dominion stops.
 *
 * <p>Atlas has no way to remove its enable and disable hooks, so they stay registered after a stop and do nothing
 * once the stopped flag is set.
 */
public final class AtlasBridge implements Link {
    public static final String PLUGIN_ID = "Ryvrook:Dominion";
    public static final String SIEGES = "sieges";
    /** Circle radius in chunks, a little over half a chunk so the whole chunk sits inside. */
    private static final double RADIUS_CHUNKS = 0.75;

    private final Host host;
    private final DominionContext ctx;
    private final Consumer<DominionEvent> listener = this::onEvent;
    private final BiConsumer<UUID, Boolean> toggle = this::onToggle;
    private volatile AtlasApi api;
    private volatile String version;
    private volatile boolean stopped;

    private AtlasBridge(Host host) {
        this.host = host;
        this.ctx = host.ctx();
    }

    /** Called by name from the integration module. */
    public static Link install(Host host) {
        AtlasBridge b = new AtlasBridge(host);
        host.ctx().bus().subscribe(b.listener);
        PlayerToggles.onWebMap(b.toggle);
        host.ctx().onPlayerReady(b::onReady);
        AtlasApi.onDisable(b::unbind);
        AtlasApi.onEnable(b::bind);
        return b;
    }

    private synchronized void bind(AtlasApi live) {
        if (stopped || live == null || live == api) {
            return;
        }
        api = live;
        version = host.foundVersion();
        try {
            live.layer(PLUGIN_ID, SIEGES).label(Messages.get(IntegrationText.ATLAS_LAYER)).tier(Tier.PUBLIC).priority(50);
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Dominion could not make its Atlas siege layer");
        }
        redrawSieges();
        int hidden = syncHidden();
        host.info("Dominion is bound to Atlas " + version + " with a live siege layer and the /f map hide switch"
                + (hidden > 0 ? ", " + hidden + " hidden players passed on" : ""));
    }

    private synchronized void unbind() {
        api = null;
    }

    /** Siege phases, breaches and disbands change the layer. Everything else is left to the snapshot. */
    private void onEvent(DominionEvent event) {
        if (stopped || api == null) {
            return;
        }
        if (event instanceof DominionEvent.SiegePhase || event instanceof DominionEvent.BreachChanged
                || event instanceof DominionEvent.FactionDisbanded) {
            redrawSieges();
        }
    }

    /** Puts every active siege on the layer and removes the ones that ended. */
    private synchronized void redrawSieges() {
        AtlasApi a = api;
        if (stopped || a == null) {
            return;
        }
        try {
            SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
            List<Siege> active = sieges == null ? List.of() : sieges.active();
            MarkerLayer layer = a.layer(PLUGIN_ID, SIEGES);
            Map<String, Set<String>> keep = new HashMap<>();
            int size = Keys.chunkSize();
            ZoneId zone = ZoneId.systemDefault();
            for (Siege s : active) {
                if (s == null || s.world() == null) {
                    continue;
                }
                String id = "siege-" + s.id();
                keep.computeIfAbsent(s.world(), w -> new HashSet<>()).add(id);
                int cx = Keys.chunkX(s.chunkIndex());
                int cz = Keys.chunkZ(s.chunkIndex());
                Faction attacker = ctx.repo().faction(s.attacker());
                Faction defender = ctx.repo().faction(s.defender());
                String at = attacker == null ? null : attacker.tag();
                String dt = defender == null ? null : defender.tag();
                String phase = s.phase() == null ? "" : s.phase().name();
                layer.circle(s.world(), id, (cx + 0.5) * size, (cz + 0.5) * size, size * RADIUS_CHUNKS)
                        .label(SiegeText.label(at, dt))
                        .popup(SiegeText.popup(at, dt, phase, cx, cz, s.phaseEndsAt(), zone))
                        .tint("BREACHED".equals(phase) ? "#ff6b6b" : attacker == null ? "#ffb347" : attacker.color());
            }
            for (String world : new ArrayList<>(layer.worlds())) {
                Set<String> ids = keep.getOrDefault(world, Set.of());
                for (Marker<?> m : layer.markers(world)) {
                    if (!ids.contains(m.id())) {
                        layer.remove(world, m.id());
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Dominion could not update its Atlas siege layer");
        }
    }

    /** /f map hide and /f map show, from the command and from the panel. */
    private void onToggle(UUID player, Boolean hidden) {
        AtlasApi a = api;
        if (stopped || a == null || player == null || hidden == null) {
            return;
        }
        try {
            a.setHidden(player, hidden, Messages.get(IntegrationText.ATLAS_HIDE_REASON));
        } catch (RuntimeException | LinkageError ignored) {
            // Atlas keeps its own switch, the snapshot still hides the player
        }
    }

    /** Atlas forgets API hides on restart, so a hidden player is passed on again whenever they join. */
    private void onReady(PlayerRef player) {
        if (stopped || api == null || player == null || player.getUuid() == null) {
            return;
        }
        if (ctx.player(player.getUuid()).mapHidden()) {
            onToggle(player.getUuid(), true);
        }
    }

    /** Passes every online hidden player on. Returns how many. */
    private int syncHidden() {
        int n = 0;
        try {
            Universe u = Universe.get();
            if (u == null) {
                return 0;
            }
            for (PlayerRef p : new ArrayList<>(u.getPlayers())) {
                if (p != null && p.getUuid() != null && ctx.player(p.getUuid()).mapHidden()) {
                    onToggle(p.getUuid(), true);
                    n++;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // the universe is not ready, players are passed on as they join
        }
        return n;
    }

    @Override
    public boolean bound() {
        return !stopped && api != null;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public synchronized void stop() {
        stopped = true;
        ctx.bus().unsubscribe(listener);
        PlayerToggles.removeWebMapListener(toggle);
        AtlasApi a = api;
        api = null;
        if (a != null) {
            try {
                a.removeLayer(PLUGIN_ID, SIEGES);
            } catch (RuntimeException | LinkageError ignored) {
                // Atlas drops its layers itself when it stops
            }
        }
    }
}
