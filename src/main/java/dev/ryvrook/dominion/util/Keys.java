package dev.ryvrook.dominion.util;

import com.hypixel.hytale.math.util.ChunkUtil;

/** Chunk key helpers shared by every module. Chunks are keyed per world by a packed long. */
public final class Keys {
    private Keys() {}

    public static long chunkAtBlock(int blockX, int blockZ) {
        return ChunkUtil.indexChunkFromBlock(blockX, blockZ);
    }

    public static long chunkAt(double x, double z) {
        return ChunkUtil.indexChunkFromBlock(x, z);
    }

    public static long chunk(int chunkX, int chunkZ) {
        return ChunkUtil.indexChunk(chunkX, chunkZ);
    }

    public static int chunkX(long index) {
        return ChunkUtil.xOfChunkIndex(index);
    }

    public static int chunkZ(long index) {
        return ChunkUtil.zOfChunkIndex(index);
    }

    public static int chunkSize() {
        return ChunkUtil.SIZE;
    }

    /** Block coordinate of the chunk's minimum corner. */
    public static int minBlock(int chunkCoord) {
        return chunkCoord * ChunkUtil.SIZE;
    }

    /** Four edge neighbours of a chunk index. */
    public static long[] neighbours(long index) {
        int x = chunkX(index);
        int z = chunkZ(index);
        return new long[] { chunk(x + 1, z), chunk(x - 1, z), chunk(x, z + 1), chunk(x, z - 1) };
    }

    public static boolean adjacent(long a, long b) {
        int dx = Math.abs(chunkX(a) - chunkX(b));
        int dz = Math.abs(chunkZ(a) - chunkZ(b));
        return dx + dz == 1;
    }

    public static String describe(long index) {
        return chunkX(index) + ", " + chunkZ(index);
    }
}
