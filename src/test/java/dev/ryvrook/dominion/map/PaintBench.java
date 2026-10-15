package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Random;

class PaintBench {
    @Test
    @EnabledIfEnvironmentVariable(named = "DOMINION_BENCH", matches = "1")
    void paint() {
        Random r = new Random(1);
        for (int size : new int[]{32, 64}) {
            int[] px = new int[size * size];
            for (int i = 0; i < px.length; i++) {
                px[i] = (r.nextInt(40) * 0x050301) << 8 | 0xff;
            }
            MapImage img = OverlayPainter.encode(size, size, px);
            long sink = 0;
            for (int i = 0; i < 20000; i++) {
                sink += OverlayPainter.paint(img, 0x7fd6ff, 35, 2, OverlayPainter.NORTH | OverlayPainter.EAST).packedIndices.length;
            }
            long t = System.nanoTime();
            int n = 50000;
            for (int i = 0; i < n; i++) {
                sink += OverlayPainter.paint(img, 0x7fd6ff, 35, 2, OverlayPainter.NORTH | OverlayPainter.EAST).packedIndices.length;
            }
            System.out.println("BENCH paint " + size + "x" + size + " palette " + img.palette.length + " " + (System.nanoTime() - t) / n + " ns " + sink);
        }
    }

    /** The per pixel version this replaced, kept to prove the palette version draws the same image. */
    static MapImage perPixel(MapImage image, int rgb, int opacity, int border, int edges) {
        int w = image.width;
        int h = image.height;
        int[] px = OverlayPainter.decode(image);
        int line = OverlayPainter.shade(rgb);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                boolean edge = border > 0 && (((edges & OverlayPainter.NORTH) != 0 && y < border)
                        || ((edges & OverlayPainter.SOUTH) != 0 && y >= h - border)
                        || ((edges & OverlayPainter.WEST) != 0 && x < border)
                        || ((edges & OverlayPainter.EAST) != 0 && x >= w - border));
                px[i] = edge ? (line << 8 | (px[i] & 0xff)) : OverlayPainter.blend(px[i], rgb, opacity);
            }
        }
        return OverlayPainter.encode(w, h, px);
    }

    @Test
    void thePaletteVersionDrawsWhatThePerPixelVersionDrew() {
        Random r = new Random(3);
        for (int round = 0; round < 300; round++) {
            int size = 1 + r.nextInt(64);
            int colours = 1 + r.nextInt(r.nextBoolean() ? 20 : 600);
            int[] px = new int[size * size];
            for (int i = 0; i < px.length; i++) {
                px[i] = r.nextInt(colours) * 0x01020301 | (r.nextBoolean() ? 0xff : 0x80);
            }
            MapImage img = OverlayPainter.encode(size, size, px);
            int rgb = r.nextInt(0x1000000);
            int opacity = r.nextInt(101);
            int border = r.nextInt(4);
            int edges = r.nextInt(16);
            MapImage a = OverlayPainter.paint(img, rgb, opacity, border, edges);
            MapImage b = perPixel(img, rgb, opacity, border, edges);
            org.junit.jupiter.api.Assertions.assertArrayEquals(b.palette, a.palette);
            org.junit.jupiter.api.Assertions.assertEquals(b.bitsPerIndex, a.bitsPerIndex);
            org.junit.jupiter.api.Assertions.assertArrayEquals(b.packedIndices, a.packedIndices);
        }
    }

    @Test
    void theSameHoldsForAPaletteWithRepeatsAndUnusedEntries() {
        // The engine image builder can list a colour twice or keep entries no pixel uses, unlike encode.
        Random r = new Random(5);
        for (int round = 0; round < 300; round++) {
            int size = 1 + r.nextInt(64);
            int n = 1 + r.nextInt(r.nextBoolean() ? 16 : 5000);
            int[] palette = new int[n];
            for (int i = 0; i < n; i++) {
                palette[i] = i > 0 && r.nextInt(4) == 0 ? palette[r.nextInt(i)] : r.nextInt();
            }
            int bits = OverlayPainter.bitsFor(n);
            byte[] packed = new byte[(size * size * bits + 7) / 8];
            int used = 1 + r.nextInt(n);
            for (int i = 0; i < size * size; i++) {
                OverlayPainter.write(packed, bits, i, r.nextInt(used));
            }
            MapImage img = new MapImage(size, size, palette, (byte) bits, packed);
            int rgb = r.nextInt(0x1000000);
            int opacity = r.nextInt(101);
            int border = r.nextInt(4);
            int edges = r.nextInt(16);
            MapImage a = OverlayPainter.paint(img, rgb, opacity, border, edges);
            MapImage b = perPixel(img, rgb, opacity, border, edges);
            org.junit.jupiter.api.Assertions.assertArrayEquals(b.palette, a.palette);
            org.junit.jupiter.api.Assertions.assertEquals(b.bitsPerIndex, a.bitsPerIndex);
            org.junit.jupiter.api.Assertions.assertArrayEquals(b.packedIndices, a.packedIndices);
        }
    }

    @Test
    void anIndexPastThePaletteFailsAsBefore() {
        // The overlay leaves a chunk it cannot paint as the engine drew it, so a bad image must throw, not draw wrong.
        int[] palette = {0x112233ff, 0x445566ff};
        byte[] packed = new byte[2];
        OverlayPainter.write(packed, 4, 0, 1);
        OverlayPainter.write(packed, 4, 1, 3);
        MapImage img = new MapImage(2, 1, palette, (byte) 4, packed);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> perPixel(img, 0xff0000, 50, 0, 0));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> OverlayPainter.paint(img, 0xff0000, 50, 0, 0));
    }
}
