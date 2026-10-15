package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Pixel work for the claims overlay on one world map chunk image. A MapImage is palette indexed.
 * Each palette entry is an RGBA colour packed as r, g, b, a from the high byte down, the same order
 * the engine ImageBuilder packs. Pixels are row major from the north west corner, so x grows to the
 * east and y grows to the south. Indexes are packed little end first into bytes with the bit count
 * the engine picks for the palette size, 4, 8, 12 or 16, so an index may straddle two bytes.
 */
public final class OverlayPainter {
    /** Edge bits for {@link #paint}. */
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    private OverlayPainter() {}

    /** Bits per palette index the engine uses for a palette of this size. */
    public static int bitsFor(int paletteSize) {
        if (paletteSize <= 16) {
            return 4;
        }
        if (paletteSize <= 256) {
            return 8;
        }
        if (paletteSize <= 4096) {
            return 12;
        }
        return 16;
    }

    /** Unpacks an image into one RGBA int per pixel. */
    public static int[] decode(MapImage image) {
        int count = image.width * image.height;
        int bits = image.bitsPerIndex & 0xff;
        int[] palette = image.palette;
        byte[] packed = image.packedIndices;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = palette[read(packed, bits, i)];
        }
        return out;
    }

    /** Packs RGBA pixels into a new palette indexed image. */
    public static MapImage encode(int width, int height, int[] pixels) {
        Int2IntOpenHashMap index = new Int2IntOpenHashMap();
        index.defaultReturnValue(-1);
        int[] palette = new int[Math.min(pixels.length, 64)];
        int size = 0;
        for (int p : pixels) {
            if (index.get(p) < 0) {
                if (size == palette.length) {
                    palette = java.util.Arrays.copyOf(palette, palette.length * 2);
                }
                index.put(p, size);
                palette[size++] = p;
            }
        }
        palette = java.util.Arrays.copyOf(palette, size);
        int bits = bitsFor(size);
        byte[] packed = new byte[(pixels.length * bits + 7) / 8];
        for (int i = 0; i < pixels.length; i++) {
            write(packed, bits, i, index.get(pixels[i]));
        }
        return new MapImage(width, height, palette, (byte) bits, packed);
    }

    /** Reads index i from a little end first bit field. */
    static int read(byte[] packed, int bits, int i) {
        if (bits == 8) {
            return packed[i] & 0xff;
        }
        if (bits == 4) {
            return (packed[i >> 1] & 0xff) >>> ((i & 1) << 2) & 0xf;
        }
        int bit = i * bits;
        int value = 0;
        int got = 0;
        while (got < bits) {
            int b = bit >> 3;
            int shift = bit & 7;
            int take = Math.min(8 - shift, bits - got);
            value |= ((packed[b] & 0xff) >>> shift & ((1 << take) - 1)) << got;
            got += take;
            bit += take;
        }
        return value;
    }

    /** Writes index i into a little end first bit field. */
    static void write(byte[] packed, int bits, int i, int value) {
        if (bits == 8) {
            packed[i] = (byte) value;
            return;
        }
        int bit = i * bits;
        int left = bits;
        while (left > 0) {
            int b = bit >> 3;
            int shift = bit & 7;
            int take = Math.min(8 - shift, left);
            int mask = ((1 << take) - 1) << shift;
            packed[b] = (byte) ((packed[b] & ~mask) | ((value << shift) & mask));
            value >>>= take;
            left -= take;
            bit += take;
        }
    }

    /** Blends rgb (0xRRGGBB) over an RGBA pixel at the given percent and keeps the pixel's alpha. */
    public static int blend(int rgba, int rgb, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        int r = mix(rgba >>> 24 & 0xff, rgb >> 16 & 0xff, p);
        int g = mix(rgba >>> 16 & 0xff, rgb >> 8 & 0xff, p);
        int b = mix(rgba >>> 8 & 0xff, rgb & 0xff, p);
        return r << 24 | g << 16 | b << 8 | (rgba & 0xff);
    }

    private static int mix(int under, int over, int percent) {
        return (under * (100 - percent) + over * percent + 50) / 100;
    }

    /** A darker shade of rgb for the outline. */
    public static int shade(int rgb) {
        int r = (rgb >> 16 & 0xff) * 3 / 5;
        int g = (rgb >> 8 & 0xff) * 3 / 5;
        int b = (rgb & 0xff) * 3 / 5;
        return r << 16 | g << 8 | b;
    }

    /** Border width in pixels for an image of this width, scaled from a 32 pixel chunk and at least 1. */
    public static int borderPixels(int borderSetting, int width) {
        if (borderSetting <= 0 || width <= 0) {
            return 0;
        }
        int scaled = Math.round(borderSetting * width / 32f);
        return Math.max(1, Math.min(scaled, Math.max(1, width / 2)));
    }

    /**
     * Returns a new image with rgb blended over every pixel at opacity percent and a solid darker
     * outline of border pixels along each edge named in edges. The colours are worked out once per
     * palette entry, a fill and an outline variant of each, and the pixels only carry indexes, so a
     * 32 by 32 image costs a read and a write per pixel. The palette of the result lists the colours
     * in the order the pixels first use them, the same as {@link #encode} gives.
     */
    public static MapImage paint(MapImage image, int rgb, int opacity, int border, int edges) {
        int w = image.width;
        int h = image.height;
        int[] palette = image.palette;
        int n = palette.length;
        int bits = image.bitsPerIndex & 0xff;
        byte[] packed = image.packedIndices;
        int line = shade(rgb);
        int[] colour = new int[2 * n];
        for (int i = 0; i < n; i++) {
            colour[i] = blend(palette[i], rgb, opacity);
            colour[n + i] = line << 8 | (palette[i] & 0xff);
        }
        int[] remap = new int[2 * n];
        java.util.Arrays.fill(remap, -1);
        Int2IntOpenHashMap seen = new Int2IntOpenHashMap();
        seen.defaultReturnValue(-1);
        int[] out = new int[Math.min(2 * n, 64)];
        int size = 0;
        int[] ids = new int[w * h];
        boolean north = border > 0 && (edges & NORTH) != 0;
        boolean south = border > 0 && (edges & SOUTH) != 0;
        boolean west = border > 0 && (edges & WEST) != 0;
        boolean east = border > 0 && (edges & EAST) != 0;
        for (int y = 0; y < h; y++) {
            boolean rowEdge = north && y < border || south && y >= h - border;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int old = read(packed, bits, i);
                boolean edge = rowEdge || west && x < border || east && x >= w - border;
                int c = edge ? n + old : old;
                int id = remap[c];
                if (id < 0) {
                    id = seen.get(colour[c]);
                    if (id < 0) {
                        if (size == out.length) {
                            out = java.util.Arrays.copyOf(out, out.length * 2);
                        }
                        id = size;
                        out[size++] = colour[c];
                        seen.put(colour[c], id);
                    }
                    remap[c] = id;
                }
                ids[i] = id;
            }
        }
        int outBits = bitsFor(size);
        byte[] result = new byte[(ids.length * outBits + 7) / 8];
        for (int i = 0; i < ids.length; i++) {
            write(result, outBits, i, ids[i]);
        }
        return new MapImage(w, h, java.util.Arrays.copyOf(out, size), (byte) outBits, result);
    }
}
