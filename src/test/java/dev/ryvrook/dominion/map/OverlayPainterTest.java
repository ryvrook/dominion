package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.chunk.palette.BitFieldArr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverlayPainterTest {
    private static final int GRASS = 0x40a040ff;
    private static final int WATER = 0x3060c0ff;

    /** A 32 by 32 image with grass on the west half and water on the east half. */
    private static MapImage halves() {
        int[] px = new int[32 * 32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                px[y * 32 + x] = x < 16 ? GRASS : WATER;
            }
        }
        return OverlayPainter.encode(32, 32, px);
    }

    @Test
    void bitsMatchTheEngineSteps() {
        assertEquals(4, OverlayPainter.bitsFor(1));
        assertEquals(4, OverlayPainter.bitsFor(16));
        assertEquals(8, OverlayPainter.bitsFor(17));
        assertEquals(8, OverlayPainter.bitsFor(256));
        assertEquals(12, OverlayPainter.bitsFor(257));
        assertEquals(12, OverlayPainter.bitsFor(4096));
        assertEquals(16, OverlayPainter.bitsFor(4097));
    }

    @Test
    void packingMatchesTheEngineBitField() {
        for (int bits : new int[] {4, 8, 12, 16}) {
            int length = 37;
            BitFieldArr engine = new BitFieldArr(bits, length);
            byte[] ours = new byte[(length * bits + 7) / 8];
            for (int i = 0; i < length; i++) {
                int v = (i * 2654435761L % (1L << bits)) > 0 ? (int) (i * 2654435761L % (1L << bits)) : i;
                engine.set(i, v);
                OverlayPainter.write(ours, bits, i, v);
            }
            assertArrayEquals(engine.get(), ours, "bits " + bits);
            for (int i = 0; i < length; i++) {
                assertEquals(engine.get(i), OverlayPainter.read(ours, bits, i), "bits " + bits + " index " + i);
            }
        }
    }

    @Test
    void encodeAndDecodeRoundTrip() {
        int[] px = new int[40 * 40];
        for (int i = 0; i < px.length; i++) {
            px[i] = (i % 300) << 8 | 0xff;
        }
        MapImage image = OverlayPainter.encode(40, 40, px);
        assertEquals(12, image.bitsPerIndex);
        assertEquals(300, image.palette.length);
        assertArrayEquals(px, OverlayPainter.decode(image));
        MapImage small = halves();
        assertEquals(4, small.bitsPerIndex);
        assertEquals(2, small.palette.length);
    }

    @Test
    void blendMixesColourAndKeepsAlpha() {
        assertEquals(0xff000080, OverlayPainter.blend(0x00000080, 0xff0000, 100));
        assertEquals(0x00000080, OverlayPainter.blend(0x00000080, 0xff0000, 0));
        assertEquals(0x800000ff, OverlayPainter.blend(0x000000ff, 0xff0000, 50));
        assertEquals(0x99, OverlayPainter.shade(0x0000ff));
    }

    @Test
    void borderScalesWithTheImageAndNeverVanishes() {
        assertEquals(0, OverlayPainter.borderPixels(0, 32));
        assertEquals(2, OverlayPainter.borderPixels(2, 32));
        assertEquals(4, OverlayPainter.borderPixels(2, 64));
        assertEquals(1, OverlayPainter.borderPixels(1, 8));
        assertEquals(1, OverlayPainter.borderPixels(2, 4));
    }

    @Test
    void paintFillsEveryPixelAndOutlinesOnlyTheNamedEdges() {
        int red = 0xff0000;
        MapImage out = OverlayPainter.paint(halves(), red, 50, 2, OverlayPainter.NORTH | OverlayPainter.EAST);
        assertEquals(32, out.width);
        assertEquals(32, out.height);
        int[] px = OverlayPainter.decode(out);
        int line = OverlayPainter.shade(red) << 8 | 0xff;
        // north rows and east columns carry the outline
        assertEquals(line, px[0]);
        assertEquals(line, px[32 + 5]);
        assertEquals(line, px[10 * 32 + 31]);
        assertEquals(line, px[10 * 32 + 30]);
        // inside, south and west edges are only tinted
        assertEquals(OverlayPainter.blend(GRASS, red, 50), px[10 * 32]);
        assertEquals(OverlayPainter.blend(GRASS, red, 50), px[2 * 32 + 3]);
        assertEquals(OverlayPainter.blend(WATER, red, 50), px[31 * 32 + 20]);
        // two tinted terrain colours plus the outline
        assertEquals(3, out.palette.length);
    }

    @Test
    void zeroBorderDrawsNoOutline() {
        int[] px = OverlayPainter.decode(OverlayPainter.paint(halves(), 0x00ff00, 30, 0, 15));
        assertEquals(OverlayPainter.blend(GRASS, 0x00ff00, 30), px[0]);
        assertEquals(OverlayPainter.blend(WATER, 0x00ff00, 30), px[32 * 32 - 1]);
    }
}
