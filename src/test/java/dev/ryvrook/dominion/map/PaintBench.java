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
}
