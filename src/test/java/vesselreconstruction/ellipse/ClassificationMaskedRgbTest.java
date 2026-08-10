package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassificationMaskedRgbTest {

    @Test
    void masksOutsidePixelsAppliesCropOffsetAndLeavesSourceUntouched() {
        final ColorProcessor sourceProcessor = new ColorProcessor(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                sourceProcessor.set(x, y, rgb(x, y));
            }
        }
        final ImagePlus source = new ImagePlus("source", sourceProcessor);
        final byte[] maskPixels = new byte[] {
                0, 0, 0, 0,
                0, (byte) 255, 0, 0,
                0, 0, (byte) 255, 0,
                0, 0, 0, 0
        };
        final DownsampledSampleMask mask =
                new DownsampledSampleMask(4, 4, 1, maskPixels, 4, 4);
        final AnalysisCropGeometry crop =
                AnalysisCropGeometry.fromIncludedMaskBounds(4, 4, 1, 4, 4, 1, 1, 2, 2, 0);

        final ImagePlus masked = ClassificationDatasetWriter.createMaskedRgb(
                source, mask, crop, PhysicalCalibration.fromEnteredPixelSize(1.0, 1.0));

        assertEquals(2, masked.getWidth());
        assertEquals(2, masked.getHeight());
        assertEquals(rgb(1, 1), masked.getProcessor().get(0, 0));
        assertEquals(0, masked.getProcessor().get(1, 0));
        assertEquals(0, masked.getProcessor().get(0, 1));
        assertEquals(rgb(2, 2), masked.getProcessor().get(1, 1));
        assertEquals(rgb(1, 1), sourceProcessor.get(1, 1));
        assertEquals(rgb(2, 1), sourceProcessor.get(2, 1));
        masked.close();
    }

    private static int rgb(final int x, final int y) {
        return (x << 16) | (y << 8) | (x + y);
    }
}
