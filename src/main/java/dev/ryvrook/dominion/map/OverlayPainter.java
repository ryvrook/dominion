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
     * outline of border pixels along each edge named in edges.
     */
    public static MapImage paint(MapImage image, int rgb, int opacity, int border, int edges) {
        int w = image.width;
        int h = image.height;
        int[] px = decode(image);
        int line = shade(rgb);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                boolean edge = border > 0 && (((edges & NORTH) != 0 && y < border)
                        || ((edges & SOUTH) != 0 && y >= h - border)
                        || ((edges & WEST) != 0 && x < border)
                        || ((edges & EAST) != 0 && x >= w - border));
                px[i] = edge ? (line << 8 | (px[i] & 0xff)) : blend(px[i], rgb, opacity);
            }
        }
        return encode(w, h, px);
    }
}
