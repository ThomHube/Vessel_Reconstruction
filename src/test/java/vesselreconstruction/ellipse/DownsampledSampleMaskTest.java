package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownsampledSampleMaskTest {

    @Test
    void mappingCoversRemainderPixels() {
        final int factor = 8;
        final int originalWidth = 17;
        final int originalHeight = 23;
        final int maskWidth = DownsampledSampleMask.maskDimension(originalWidth, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(originalHeight, factor);
        assertEquals(3, maskWidth);
        assertEquals(3, maskHeight);

        final byte[] pixels = new byte[maskWidth * maskHeight];
        pixels[0] = (byte) 255;
        pixels[maskWidth * maskHeight - 1] = (byte) 255;
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                originalWidth, originalHeight, factor, pixels, maskWidth, maskHeight);

        assertTrue(mask.containsOriginalPixel(0, 0));
        assertTrue(mask.containsOriginalPixel(7, 7));
        assertFalse(mask.containsOriginalPixel(8, 0));
        assertTrue(mask.containsOriginalPixel(originalWidth - 1, originalHeight - 1));
        assertEquals(maskWidth - 1,
                DownsampledSampleMask.toMaskCoordinate(originalWidth - 1, factor, maskWidth));
        assertEquals(maskHeight - 1,
                DownsampledSampleMask.toMaskCoordinate(originalHeight - 1, factor, maskHeight));
    }

    @Test
    void coreTileIntersectionUsesDownsampledQuery() {
        final byte[] pixels = new byte[] {
                0, 0, 0,
                0, (byte) 255, 0,
                0, 0, 0
        };
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                24, 24, 8, pixels, 3, 3);
        assertFalse(mask.coreTileContainsIncludedPixels(0, 0, 8, 8));
        assertTrue(mask.coreTileContainsIncludedPixels(8, 8, 8, 8));
        assertFalse(mask.coreTileContainsIncludedPixels(16, 16, 8, 8));
    }

    @Test
    void emptyMaskIsDetected() {
        final byte[] pixels = new byte[4];
        final DownsampledSampleMask mask = new DownsampledSampleMask(8, 8, 4, pixels, 2, 2);
        assertTrue(mask.isEmpty());
        assertEquals(0L, mask.getIncludedMaskPixels());
    }

    @Test
    void invalidConstructionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DownsampledSampleMask(10, 10, 0, new byte[4], 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new DownsampledSampleMask(10, 10, 8, new byte[2], 2, 2));
    }
}
