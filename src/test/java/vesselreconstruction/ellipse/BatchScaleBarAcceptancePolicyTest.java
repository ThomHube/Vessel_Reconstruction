package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchScaleBarAcceptancePolicyTest {

    private static final int SOURCE_W = 800;
    private static final int SOURCE_H = 600;
    private static final int FACTOR = 8;
    private static final double EXPECTED_PX = 1.0954306217917742;
    private static final double PHYSICAL = 500.0;

    @Test
    void acceptsMediumTerminalTickCenterlinesWithinOnePercent() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double measured = PHYSICAL / EXPECTED_PX;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.MEDIUM,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                10.0, 590.0,
                10.0 + measured, 590.0,
                measured,
                0.99);
        final DownsampledSampleMask mask = emptyishMask();
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, mask, config);
        assertTrue(decision.accepted);
        assertTrue(decision.relativeDeviation <= 0.01);
    }

    @Test
    void rejectsLowConfidence() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double measured = PHYSICAL / EXPECTED_PX;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.LOW,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                10.0, 590.0,
                10.0 + measured, 590.0,
                measured,
                0.99);
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, emptyishMask(), config);
        assertFalse(decision.accepted);
        assertEquals("SCALE_BAR_CONFIDENCE_TOO_LOW", decision.reasonCode);
    }

    @Test
    void rejectsWrongMethod() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double measured = PHYSICAL / EXPECTED_PX;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.MEDIUM,
                ScaleBarDetectionMethod.SHAFT_ENDPOINTS,
                10.0, 590.0,
                10.0 + measured, 590.0,
                measured,
                0.99);
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, emptyishMask(), config);
        assertFalse(decision.accepted);
        assertEquals("SCALE_BAR_WRONG_METHOD", decision.reasonCode);
    }

    @Test
    void rejectsLowContinuity() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double measured = PHYSICAL / EXPECTED_PX;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.MEDIUM,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                10.0, 590.0,
                10.0 + measured, 590.0,
                measured,
                0.50);
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, emptyishMask(), config);
        assertFalse(decision.accepted);
        assertEquals("SCALE_BAR_SHAFT_CONTINUITY_TOO_LOW", decision.reasonCode);
    }

    @Test
    void rejectsDeviationOutsideOnePercent() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double badPixelSize = EXPECTED_PX * 1.05;
        final double measured = PHYSICAL / badPixelSize;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.MEDIUM,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                10.0, 590.0,
                10.0 + measured, 590.0,
                measured,
                0.99);
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, emptyishMask(), config);
        assertFalse(decision.accepted);
        assertEquals("SCALE_BAR_PIXEL_SIZE_DEVIATION_TOO_LARGE", decision.reasonCode);
    }

    @Test
    void rejectsScaleBarInsideSample() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final double measured = PHYSICAL / EXPECTED_PX;
        final double leftX = 80.0;
        final double leftY = 80.0;
        final double rightX = leftX + measured;
        final double rightY = 80.0;
        final ScaleBarDetection detection = measuredDetection(
                ScaleBarDetectionConfidence.MEDIUM,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                leftX, leftY,
                rightX, rightY,
                measured,
                0.99);
        final DownsampledSampleMask mask = maskContainingTicks(leftX, leftY, rightX, rightY);
        final BatchScaleBarAcceptancePolicy.Decision decision =
                BatchScaleBarAcceptancePolicy.evaluate(detection, mask, config);
        assertFalse(decision.accepted);
        assertEquals("SCALE_BAR_INSIDE_SAMPLE", decision.reasonCode);
    }

    private static ScaleBarDetection measuredDetection(
            final ScaleBarDetectionConfidence confidence,
            final ScaleBarDetectionMethod method,
            final double lx,
            final double ly,
            final double rx,
            final double ry,
            final double measuredLength,
            final double continuity) {
        final Rectangle bounds = new Rectangle(
                (int) Math.min(lx, rx) - 5,
                (int) Math.min(ly, ry) - 5,
                (int) Math.abs(rx - lx) + 10,
                20);
        return ScaleBarDetection.measured(
                confidence,
                0.9,
                method,
                lx, ly, rx, ry,
                measuredLength,
                12.0,
                0.0,
                continuity,
                bounds,
                bounds,
                bounds,
                Collections.<ScaleBarDetectionCandidate>emptyList());
    }

    private static DownsampledSampleMask emptyishMask() {
        final int maskW = DownsampledSampleMask.maskDimension(SOURCE_W, FACTOR);
        final int maskH = DownsampledSampleMask.maskDimension(SOURCE_H, FACTOR);
        final byte[] pixels = new byte[maskW * maskH];
        // Sample region away from bottom scale-bar ticks.
        for (int y = 10; y < 40; y++) {
            for (int x = 20; x < 80; x++) {
                pixels[y * maskW + x] = (byte) 255;
            }
        }
        return new DownsampledSampleMask(SOURCE_W, SOURCE_H, FACTOR, pixels, maskW, maskH);
    }

    private static DownsampledSampleMask maskContainingTicks(
            final double lx,
            final double ly,
            final double rx,
            final double ry) {
        final int maskW = DownsampledSampleMask.maskDimension(SOURCE_W, FACTOR);
        final int maskH = DownsampledSampleMask.maskDimension(SOURCE_H, FACTOR);
        final byte[] pixels = new byte[maskW * maskH];
        mark(pixels, maskW, (int) Math.round(lx), (int) Math.round(ly));
        mark(pixels, maskW, (int) Math.round(rx), (int) Math.round(ry));
        return new DownsampledSampleMask(SOURCE_W, SOURCE_H, FACTOR, pixels, maskW, maskH);
    }

    private static void mark(final byte[] pixels, final int maskW, final int ox, final int oy) {
        final int mx = Math.min(maskW - 1, ox / FACTOR);
        final int my = Math.min((pixels.length / maskW) - 1, oy / FACTOR);
        pixels[my * maskW + mx] = (byte) 255;
    }
}
