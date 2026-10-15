package dev.ryvrook.dominion.map;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Registers the claim marker provider and the claims overlay on every world, including worlds
 * added later, and keeps the map state in step with claim and faction changes.
 */
public final class MapModule implements DominionModule {
    private final MapState state = new MapState();
    private ClaimMarkerProvider provider;
    private MapOverlay overlay;
    private DominionContext ctx;

    public MapModule() {}

    @Override
    public String name() {
        return "map";
    }

    @Override
    public void init(DominionContext ctx) {
        this.ctx = ctx;
        this.overlay = new MapOverlay(ctx, state);
        this.provider = new ClaimMarkerProvider(ctx, state, overlay);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.bus().subscribe(state::onEvent);
        ctx.plugin().getEventRegistry().registerGlobal(AddWorldEvent.class, (AddWorldEvent e) -> {
            World world = e.getWorld();
            register(world);
            // the world may still be starting, so check again shortly after
            world.scheduleAfter(() -> register(world), 2, TimeUnit.SECONDS);
        });
        scanWorlds();
        ctx.everyMinute(this::scanWorlds);
        ctx.everySecond(this::refreshOverlay);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (overlay != null) {
            overlay.uninstallAll();
        }
    }

    private void refreshOverlay() {
        try {
            overlay.tick();
        } catch (Exception ex) {
            ctx.logger().at(Level.FINE).withCause(ex).log("Dominion claims overlay refresh failed");
        }
    }

    private void scanWorlds() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (World world : universe.getWorlds().values()) {
            register(world);
        }
    }

    private void register(World world) {
        overlay.install(world);
        if (world == null || !(ctx.settings().bool("map.enabled") && ctx.settings().bool("map.markers"))) {
            return;
        }
        try {
            var manager = world.getWorldMapManager();
            if (manager == null || manager.getMarkerProviders().containsKey(ClaimMarkerProvider.ID)) {
                return;
            }
            manager.addMarkerProvider(ClaimMarkerProvider.ID, provider);
            state.bump(world.getName());
            ctx.logger().at(Level.INFO).log("Dominion claim markers enabled in %s", world.getName());
        } catch (Exception ex) {
            ctx.logger().at(Level.FINE).withCause(ex).log("Dominion could not add map markers to %s yet", world.getName());
        }
    }
}
