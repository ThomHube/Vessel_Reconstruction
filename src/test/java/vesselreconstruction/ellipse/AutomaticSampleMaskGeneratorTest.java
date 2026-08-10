package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticSampleMaskGeneratorTest {

    private final AutomaticSampleMaskGenerator generator = new AutomaticSampleMaskGenerator();

    @Test
    void verticalSampleWithScaleBarAndInternalHoles() {
        final ColorProcessor rgb = new ColorProcessor(160, 320);
        fill(rgb, new Color(230, 230, 220));
        // Long vertical sample
        fillRect(rgb, 50, 20, 60, 280, new Color(70, 55, 40));
        // Pale circular-ish holes inside sample
        fillRect(rgb, 70, 80, 16, 16, new Color(230, 230, 220));
        fillRect(rgb, 72, 180, 14, 14, new Color(230, 230, 220));
        // Scale bar outside sample
        fillRect(rgb, 10, 290, 30, 8, new Color(20, 20, 20));
        // Small annotation
        fillRect(rgb, 130, 40, 12, 12, new Color(40, 40, 40));

        final SampleMaskGenerationResult result = generator.generate(
                new ImagePlus("synthetic-vertical", rgb), 4, 8);
        final DownsampledSampleMask mask = result.getMask();

        assertTrue(mask.containsOriginalPixel(80, 100));
        // Internal holes should be filled into the sample envelope.
        assertTrue(mask.containsOriginalPixel(78, 88));
        assertTrue(mask.containsOriginalPixel(79, 187));
        // Exterior and scale bar region should remain excluded.
        assertFalse(mask.containsOriginalPixel(15, 294));
        assertFalse(mask.containsOriginalPixel(136, 46));
        assertFalse(mask.containsOriginalPixel(5, 5));
    }

    @Test
    void diagonalSampleIsSelected() {
        final ColorProcessor rgb = new ColorProcessor(200, 200);
        fill(rgb, new Color(235, 235, 230));
        for (int i = 10; i < 180; i++) {
            fillRect(rgb, i, i, 18, 18, new Color(60, 50, 40));
        }
        final SampleMaskGenerationResult result = generator.generate(
                new ImagePlus("synthetic-diagonal", rgb), 4, 4);
        final DownsampledSampleMask mask = result.getMask();
        assertTrue(mask.containsOriginalPixel(100, 100));
        assertFalse(mask.containsOriginalPixel(10, 180));
        assertTrue(mask.getIncludedMaskPixels() > 20L);
    }

    @Test
    void shortHorizontalBandLosesToLongVerticalSample() {
        final ColorProcessor rgb = new ColorProcessor(180, 360);
        fill(rgb, new Color(230, 230, 225));
        // Long vertical sample (kept clear of the short band).
        fillRect(rgb, 70, 80, 40, 260, new Color(65, 50, 35));
        // Wide but short dark band, disconnected from the sample.
        fillRect(rgb, 10, 20, 160, 18, new Color(30, 30, 30));

        final SampleMaskGenerationResult result = generator.generate(
                new ImagePlus("synthetic-competition", rgb), 4, 0);
        final DownsampledSampleMask mask = result.getMask();
        assertTrue(mask.containsOriginalPixel(90, 200));
        // Points unique to the short band should be excluded.
        assertFalse(mask.containsOriginalPixel(20, 28));
        assertFalse(mask.containsOriginalPixel(150, 28));
    }

    @Test
    void nearlyUniformPaleImageFailsSafely() {
        final ColorProcessor rgb = new ColorProcessor(120, 120);
        fill(rgb, new Color(240, 240, 235));
        final SampleMaskGenerationException ex = assertThrows(
                SampleMaskGenerationException.class,
                () -> generator.generate(new ImagePlus("uniform", rgb), 4, 8));
        assertTrue(ex.getMessage().contains("no credible sample component"));
    }

    @Test
    void expansionMarginUsesCeilingDivision() {
        assertEquals(1, CoreTileLayout.ceilDiv(1, 8));
        assertEquals(1, CoreTileLayout.ceilDiv(8, 8));
        assertEquals(2, CoreTileLayout.ceilDiv(9, 8));
        assertEquals(4, CoreTileLayout.ceilDiv(32, 8));
    }

    @Test
    void holeFillingIncludesEnclosedBackground() {
        final ByteProcessor binary = new ByteProcessor(20, 20);
        for (int y = 2; y < 18; y++) {
            for (int x = 2; x < 18; x++) {
                binary.set(x, y, 255);
            }
        }
        // enclosed holes
        binary.set(8, 8, 0);
        binary.set(9, 8, 0);
        binary.set(8, 9, 0);
        binary.set(12, 12, 0);
        AutomaticSampleMaskGenerator.fillInternalHoles(binary);
        assertEquals(255, binary.get(8, 8));
        assertEquals(255, binary.get(12, 12));
        assertEquals(0, binary.get(0, 0));
    }

    private static void fill(final ColorProcessor rgb, final Color color) {
        rgb.setColor(color);
        rgb.fill();
    }

    private static void fillRect(
            final ColorProcessor rgb,
            final int x,
            final int y,
            final int width,
            final int height,
            final Color color) {
        rgb.setColor(color);
        rgb.setRoi(x, y, width, height);
        rgb.fill();
        rgb.setRoi((java.awt.Rectangle) null);
    }
}
