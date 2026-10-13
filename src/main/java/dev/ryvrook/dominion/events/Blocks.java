package dev.ryvrook.dominion.events;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.ryvrook.dominion.util.Keys;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Raw block access that differs between server versions, so it goes through reflection.
 * On 0.6.8 World hands out loaded WorldChunk objects with setBlock and getHeight. On 0.7 those
 * are gone and blocks are written with BlockOperations.setBlock against a chunk section
 * reference from ChunkStore.getChunkSectionReferenceAtBlockAsync, the same path the vanilla
 * block set command uses. 0.7 has no simple height lookup, so callers fall back to a known height.
 * Every method must run on the world thread.
 */
final class Blocks {
    private Blocks() {}

    private static final String OPS = "com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations";

    /** Index of a block type, or -1 when the id is unknown. */
    static int index(String blockId) {
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        int i = map.getIndexOrDefault(blockId, -1);
        return i < 0 ? -1 : i;
    }

    /** Height of the highest block in a column on 0.6.8 when the chunk is loaded, otherwise null. */
    static Integer height(World world, int x, int z) {
        try {
            Object chunk = loadedChunk(world, x, z);
            if (chunk == null) {
                return null;
            }
            Method m = chunk.getClass().getMethod("getHeight", int.class, int.class);
            Object h = m.invoke(chunk, x, z);
            return h instanceof Number n && n.intValue() > 0 ? n.intValue() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Places a block by id. The result is passed true when the write was accepted, or false.
     * On 0.7 the write happens a moment later once the chunk section reference resolves.
     */
    static void set(World world, int x, int y, int z, String blockId, Consumer<Boolean> done) {
        int idx = index(blockId);
        if (idx < 0) {
            done.accept(false);
            return;
        }
        BlockType type = BlockType.getAssetMap().getAsset(idx);
        try {
            Class<?> ops = Class.forName(OPS);
            setModern(world, ops, x, y, z, idx, type, done);
            return;
        } catch (ClassNotFoundException e) {
            // 0.6.8 has no BlockOperations, use the chunk directly.
        } catch (ReflectiveOperationException | RuntimeException e) {
            done.accept(false);
            return;
        }
        try {
            Object chunk = loadedChunk(world, x, z);
            if (chunk == null) {
                done.accept(false);
                return;
            }
            Method m = chunk.getClass().getMethod("setBlock", int.class, int.class, int.class, String.class);
            Object r = m.invoke(chunk, x, y, z, blockId);
            done.accept(Boolean.TRUE.equals(r));
        } catch (ReflectiveOperationException | RuntimeException e) {
            done.accept(false);
        }
    }

    /** Clears a block back to empty air, best effort. */
    static void clear(World world, int x, int y, int z) {
        String empty = "Empty";
        if (index(empty) < 0) {
            BlockType first = BlockType.getAssetMap().getAsset(0);
            if (first == null) {
                return;
            }
            empty = first.getId();
        }
        set(world, x, y, z, empty, ok -> {});
    }

    private static void setModern(World world, Class<?> ops, int x, int y, int z, int idx, BlockType type,
                                  Consumer<Boolean> done) throws ReflectiveOperationException {
        ChunkStore store = world.getChunkStore();
        Method ref = store.getClass().getMethod("getChunkSectionReferenceAtBlockAsync",
                int.class, int.class, int.class, int.class);
        Method set = ops.getMethod("setBlock", ChunkStore.class, Ref.class, int.class, int.class, int.class, int.class,
                BlockType.class, int.class, int.class, int.class);
        CompletableFuture<?> future = (CompletableFuture<?>) ref.invoke(store, x, y, z, 4);
        future.thenAcceptAsync(r -> {
            try {
                if (!(r instanceof Ref<?> section) || !section.isValid()) {
                    done.accept(false);
                    return;
                }
                Object ok = set.invoke(null, store, section, x, y, z, idx, type, 0, 0, 0);
                done.accept(Boolean.TRUE.equals(ok));
            } catch (ReflectiveOperationException | RuntimeException e) {
                done.accept(false);
            }
        }, world).exceptionally(t -> {
            done.accept(false);
            return null;
        });
    }

    private static Object loadedChunk(World world, int x, int z) throws ReflectiveOperationException {
        Method get;
        try {
            get = world.getClass().getMethod("getChunkIfLoaded", long.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
        return get.invoke(world, Keys.chunkAtBlock(x, z));
    }
}
