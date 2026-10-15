package dev.ryvrook.dominion.integration.atlas;

import dev.ryvrook.atlas.api.AtlasApi;
import dev.ryvrook.atlas.api.MarkerLayer;
import dev.ryvrook.atlas.api.Tier;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.integration.Sibling;
import dev.ryvrook.dominion.integration.TestHosts;
import dev.ryvrook.dominion.model.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Atlas bridge over a stand in AtlasApi published through the real static hooks of the Atlas jar. */
class AtlasBridgeTest {
    /** Keeps layers in a map and records removals. */
    static final class FakeAtlas extends AtlasApi {
        final Map<String, MarkerLayer> layers = new HashMap<>();
        final List<String> removed = new ArrayList<>();

        @Override
        public MarkerLayer layer(String pluginId, String layerId) {
            return layers.computeIfAbsent(pluginId + ":" + layerId, k -> make(pluginId, layerId));
        }

        private static MarkerLayer make(String pluginId, String layerId) {
            try {
                Class<?> listener = Class.forName("dev.ryvrook.atlas.api.AtlasApi$LayerListener");
                Object quiet = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                        (proxy, method, args) -> null);
                Constructor<MarkerLayer> c = MarkerLayer.class.getDeclaredConstructor(String.class, String.class, listener);
                c.setAccessible(true);
                return c.newInstance(pluginId, layerId, quiet);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override public List<MarkerLayer> layers() { return new ArrayList<>(layers.values()); }
        @Override public List<MarkerLayer> layers(String world) { return layers(); }
        @Override public boolean removeLayer(String pluginId, String layerId) {
            removed.add(layerId);
            return layers.remove(pluginId + ":" + layerId) != null;
        }
        @Override public void setHidden(UUID player, boolean hidden, String reason) { }
        @Override public boolean isHidden(UUID player) { return false; }

        void up() { enable(this); }
        void down() { disable(this); }
    }

    final Fixtures fx = Fixtures.create();
    final FakeAtlas atlas = new FakeAtlas();

    @AfterEach
    void close() {
        atlas.down();
        fx.close();
    }

    @Test
    void theSiegeLayerIsOnlyDrawnWhileTheSnapshotIsOff() {
        atlas.up();
        Link link = AtlasBridge.install(TestHosts.host(fx.ctx, Sibling.ATLAS, "1.6.0"));
        assertTrue(atlas.layers.isEmpty(), "the snapshot draws the sieges, so no layer is made");

        fx.set("export.enabled", false);
        fx.set("integrations.atlasSiegeTier", "linked");
        link.poll();
        MarkerLayer layer = atlas.layers.get(AtlasBridge.PLUGIN_ID + ":" + AtlasBridge.SIEGES);
        assertTrue(layer != null, "with the export off the bridge draws the sieges");
        assertEquals(Tier.LINKED, layer.tier());

        fx.set("export.enabled", true);
        link.poll();
        assertFalse(atlas.layers.containsKey(AtlasBridge.PLUGIN_ID + ":" + AtlasBridge.SIEGES), "turning the export on drops the layer");
        link.stop();
    }

    @Test
    void theTierFallsBackToPublic() {
        assertEquals(Tier.FACTION, AtlasBridge.tier("Faction"));
        assertEquals(Tier.PUBLIC, AtlasBridge.tier("nobody"));
        assertEquals(Tier.PUBLIC, AtlasBridge.tier(null));
    }

    @Test
    void stopRemovesTheHooksWhenAtlasCan() {
        boolean has;
        try {
            AtlasApi.class.getMethod("removeHooks", Consumer.class, Runnable.class);
            has = true;
        } catch (NoSuchMethodException e) {
            has = false;
        }
        assertEquals(has, AtlasBridge.removeHooks(api -> { }, () -> { }), "found by reflection, never thrown");
    }
}
