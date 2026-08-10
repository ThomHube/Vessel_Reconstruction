package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticScaleBarDetectorTest {

    private static final int RED = (220 << 16) | (20 << 8) | 20;
    private static final int PINK = (180 << 16) | (90 << 8) | 90;
    private static final int BG = (230 << 16) | (230 << 8) | 225;

    private final AutomaticScaleBarDetector detector = new AutomaticScaleBarDetector();

    @Test
    void idealScaleBarIgnoresTextAndUsesTerminalCenters() {
        final int width = 600;
        final int height = 200;
        final ColorProcessor rgb = pale(width, height);
        drawBar(rgb, 50, 140, 50 + 456, 4, 7);
        drawTextLike(rgb, 80, 100, 12);
        drawTextLike(rgb, 120, 95, 10);
        drawTextLike(rgb, 160, 100, 14);

        final DownsampledSampleMask mask = exteriorMask(width, height, 8, new Rectangle(20, 20, 200, 60));
        final ImagePlus source = new ImagePlus("ideal", rgb);
        final int checksumBefore = checksum(rgb);

        final ScaleBarDetection detection = detector.detect(source, mask, null);
        assertTrue(detection.isSuccessful());
        assertEquals(ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES, detection.getMethod());
        assertTrue(detection.isAcceptableForConfirmation());
        assertEquals(456.0, detection.getMeasuredLengthPx(), 1.0);
        assertTrue(detection.getOuterComponentWidthPx() > detection.getMeasuredLengthPx());
        assertEquals(checksumBefore, checksum((ColorProcessor) source.getProcessor()));
    }

    @Test
    void thickTerminalTicksReturnCenterDistanceNotOuterWidth() {
        // Outer width 469, centers 456 apart.
        final int width = 500;
        final int height = 80;
        final ColorProcessor rgb = pale(width, height);
        final int left0 = 10;
        final int tickThickness = 13;
        final int left1 = left0 + tickThickness - 1; // 22
        final int right0 = left0 + 456; // 466
        final int right1 = right0 + tickThickness - 1; // 478
        assertEquals(469, right1 - left0 + 1);

        fillRect(rgb, left0, 30, left1, 50, RED); // left tick
        fillRect(rgb, left1 + 1, 38, right0 - 1, 42, RED); // shaft
        fillRect(rgb, right0, 30, right1, 50, RED); // right tick

        final DownsampledSampleMask mask = allExteriorMask(width, height, 4);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("thick", rgb), mask, null);

        assertTrue(detection.isSuccessful());
        assertEquals(ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES, detection.getMethod());
        assertEquals(456.0, detection.getMeasuredLengthPx(), 0.75);
        assertNotEquals(469.0, detection.getMeasuredLengthPx(), 0.01);
        assertTrue(Math.abs(detection.getOuterComponentWidthPx() - 469.0) <= 2.0
                || detection.getOuterComponentWidthPx() > detection.getMeasuredLengthPx());
    }

    @Test
    void antialiasedBarStillMeasuresTerminalCenters() {
        final int width = 520;
        final int height = 100;
        final ColorProcessor rgb = pale(width, height);
        drawBar(rgb, 40, 50, 40 + 456, 3, 6);
        // Antialiased pink edges around shaft.
        for (int x = 40; x <= 40 + 456; x++) {
            rgb.set(x, 47, PINK);
            rgb.set(x, 53, PINK);
        }
        final DownsampledSampleMask mask = allExteriorMask(width, height, 8);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("aa", rgb), mask, null);
        assertTrue(detection.isSuccessful());
        assertEquals(456.0, detection.getMeasuredLengthPx(), 2.0);
    }

    @Test
    void textLargerThanBarAreaStillSelectsBar() {
        final int width = 700;
        final int height = 220;
        final ColorProcessor rgb = pale(width, height);
        drawBar(rgb, 80, 170, 80 + 456, 3, 5);
        for (int i = 0; i < 40; i++) {
            drawTextLike(rgb, 20 + (i % 10) * 18, 20 + (i / 10) * 20, 8 + (i % 3));
        }
        final DownsampledSampleMask mask = allExteriorMask(width, height, 8);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("texty", rgb), mask, null);
        assertTrue(detection.isSuccessful());
        assertEquals(456.0, detection.getMeasuredLengthPx(), 2.5);
    }

    @Test
    void prefersExteriorBarOverRedObjectsInsideSample() {
        final int width = 640;
        final int height = 240;
        final ColorProcessor rgb = pale(width, height);
        drawBar(rgb, 60, 200, 60 + 456, 3, 6);
        fillRect(rgb, 120, 40, 180, 90, RED); // inside sample
        final DownsampledSampleMask mask = exteriorMask(
                width, height, 8, new Rectangle(80, 20, 300, 120));
        final ScaleBarDetection detection = detector.detect(new ImagePlus("inside", rgb), mask, null);
        assertTrue(detection.isSuccessful());
        assertTrue(detection.getLeftTickCenterY() > 150);
        assertEquals(456.0, detection.getMeasuredLengthPx(), 2.5);
    }

    @Test
    void trueBarWinsAmongMultipleExteriorRedObjects() {
        final int width = 700;
        final int height = 220;
        final ColorProcessor rgb = pale(width, height);
        fillRect(rgb, 20, 20, 50, 50, RED); // compact blob
        fillRect(rgb, 70, 30, 75, 120, RED); // vertical line
        fillRect(rgb, 100, 40, 140, 44, RED); // short horizontal
        drawBar(rgb, 160, 160, 160 + 456, 3, 6);
        final DownsampledSampleMask mask = allExteriorMask(width, height, 8);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("multi", rgb), mask, null);
        assertTrue(detection.isSuccessful());
        assertEquals(456.0, detection.getMeasuredLengthPx(), 2.5);
    }

    @Test
    void slightRotationMeasuresAlongShaftAxis() {
        final int width = 600;
        final int height = 200;
        final ColorProcessor rgb = pale(width, height);
        // Approximate slightly tilted bar using stepped rows.
        for (int i = 0; i <= 456; i++) {
            final int x = 50 + i;
            final int y = 120 + i / 80;
            rgb.set(x, y, RED);
            rgb.set(x, y + 1, RED);
            rgb.set(x, y + 2, RED);
        }
        fillRect(rgb, 48, 112, 54, 132, RED);
        fillRect(rgb, 50 + 456 - 3, 112 + 456 / 80, 50 + 456 + 3, 132 + 456 / 80, RED);
        final DownsampledSampleMask mask = allExteriorMask(width, height, 8);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("rot", rgb), mask, null);
        assertTrue(detection.isSuccessful());
        assertTrue(detection.getMeasuredLengthPx() > 440.0);
        assertTrue(detection.getMeasuredLengthPx() < 480.0);
    }

    @Test
    void missingLeftTickIsLowOrFailed() {
        final int width = 500;
        final int height = 80;
        final ColorProcessor rgb = pale(width, height);
        fillRect(rgb, 20, 38, 20 + 456, 42, RED); // shaft only, right stub tick
        fillRect(rgb, 20 + 456, 30, 20 + 456 + 8, 50, RED);
        final DownsampledSampleMask mask = allExteriorMask(width, height, 4);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("noLeft", rgb), mask, null);
        assertFalse(detection.isAcceptableForConfirmation());
        assertTrue(detection.getConfidence() == ScaleBarDetectionConfidence.LOW
                || detection.getConfidence() == ScaleBarDetectionConfidence.FAILED
                || !detection.isSuccessful());
    }

    @Test
    void missingRightTickIsLowOrFailed() {
        final int width = 500;
        final int height = 80;
        final ColorProcessor rgb = pale(width, height);
        fillRect(rgb, 20, 30, 28, 50, RED);
        fillRect(rgb, 28, 38, 28 + 456, 42, RED);
        final DownsampledSampleMask mask = allExteriorMask(width, height, 4);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("noRight", rgb), mask, null);
        assertFalse(detection.isAcceptableForConfirmation());
    }

    @Test
    void noRedBarFailsClearly() {
        final ColorProcessor rgb = pale(400, 200);
        final DownsampledSampleMask mask = allExteriorMask(400, 200, 8);
        final ScaleBarDetection detection = detector.detect(new ImagePlus("none", rgb), mask, null);
        assertFalse(detection.isSuccessful());
        assertEquals(ScaleBarDetectionConfidence.FAILED, detection.getConfidence());
        assertFalse(detection.getFailureReason().isEmpty());
    }

    @Test
    void invalidPhysicalScaleRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromAutoDetectedScaleBar(0.0, 456.0));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromAutoDetectedScaleBar(-1.0, 456.0));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromAutoDetectedScaleBar(Double.NaN, 456.0));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromAutoDetectedScaleBar(
                        Double.POSITIVE_INFINITY, 456.0));
    }

    @Test
    void downsampleMappingSearchRectangleStaysInBounds() {
        final Rectangle mapped = new Rectangle(10 * 8, 3 * 8, (20 - 10 + 1) * 8, (5 - 3 + 1) * 8);
        final Rectangle search = AutomaticScaleBarDetector.expandAndClip(
                mapped, 24, 6672, 1650);
        assertTrue(search.x >= 0);
        assertTrue(search.y >= 0);
        assertTrue(search.x + search.width <= 6672);
        assertTrue(search.y + search.height <= 1650);
    }

    @Test
    void autoCalibrationMatchesExpectedPixelSize() {
        final PhysicalCalibration calibration =
                PhysicalCalibration.fromAutoDetectedScaleBar(500.0, 456.0);
        assertEquals(0, Double.compare(1.0964912280701755, calibration.getPixelWidthUm()));
        assertEquals(CalibrationMode.AUTO_DETECT_RED_SCALE_BAR,
                calibration.getCalibrationMethod());
        assertEquals(Double.valueOf(456.0), calibration.getScaleBarMeasuredLengthPx());
    }

    @Test
    void sourceChecksumUnchangedByDetection() {
        final ColorProcessor rgb = pale(500, 120);
        drawBar(rgb, 40, 60, 40 + 456, 3, 6);
        final ImagePlus source = new ImagePlus("chk", rgb);
        final int before = checksum(rgb);
        detector.detect(source, allExteriorMask(500, 120, 8), null);
        assertEquals(before, checksum((ColorProcessor) source.getProcessor()));
    }

    private static ColorProcessor pale(final int width, final int height) {
        final ColorProcessor rgb = new ColorProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rgb.set(x, y, BG);
            }
        }
        return rgb;
    }

    private static void drawBar(
            final ColorProcessor rgb,
            final int leftCenterX,
            final int shaftY,
            final int rightCenterX,
            final int shaftHalfHeight,
            final int tickHalfWidth) {
        final int left0 = leftCenterX - tickHalfWidth;
        final int left1 = leftCenterX + tickHalfWidth;
        final int right0 = rightCenterX - tickHalfWidth;
        final int right1 = rightCenterX + tickHalfWidth;
        fillRect(rgb, left0, shaftY - 10, left1, shaftY + 10, RED);
        fillRect(rgb, left1 + 1, shaftY - shaftHalfHeight, right0 - 1, shaftY + shaftHalfHeight, RED);
        fillRect(rgb, right0, shaftY - 10, right1, shaftY + 10, RED);
    }

    private static void drawTextLike(
            final ColorProcessor rgb,
            final int x0,
            final int y0,
            final int size) {
        fillRect(rgb, x0, y0, x0 + size, y0 + size + 2, RED);
        fillRect(rgb, x0 + size + 2, y0, x0 + size + 6, y0 + size, RED);
    }

    private static void fillRect(
            final ColorProcessor rgb,
            final int x0,
            final int y0,
            final int x1,
            final int y1,
            final int color) {
        for (int y = Math.max(0, y0); y <= Math.min(rgb.getHeight() - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(rgb.getWidth() - 1, x1); x++) {
                rgb.set(x, y, color);
            }
        }
    }

    private static DownsampledSampleMask allExteriorMask(
            final int width,
            final int height,
            final int factor) {
        final int mw = DownsampledSampleMask.maskDimension(width, factor);
        final int mh = DownsampledSampleMask.maskDimension(height, factor);
        return new DownsampledSampleMask(width, height, factor, new byte[mw * mh], mw, mh);
    }

    private static DownsampledSampleMask exteriorMask(
            final int width,
            final int height,
            final int factor,
            final Rectangle includedOriginal) {
        final int mw = DownsampledSampleMask.maskDimension(width, factor);
        final int mh = DownsampledSampleMask.maskDimension(height, factor);
        final byte[] pixels = new byte[mw * mh];
        for (int y = includedOriginal.y; y < includedOriginal.y + includedOriginal.height; y++) {
            for (int x = includedOriginal.x; x < includedOriginal.x + includedOriginal.width; x++) {
                final int mx = Math.min(mw - 1, x / factor);
                final int my = Math.min(mh - 1, y / factor);
                pixels[my * mw + mx] = (byte) 255;
            }
        }
        return new DownsampledSampleMask(width, height, factor, pixels, mw, mh);
    }

    private static int checksum(final ColorProcessor rgb) {
        int hash = 1;
        for (int y = 0; y < rgb.getHeight(); y++) {
            for (int x = 0; x < rgb.getWidth(); x++) {
                hash = 31 * hash + rgb.get(x, y);
            }
        }
        return hash;
    }
}
