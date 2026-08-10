package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenCoordinateContextTest {

    private static final double PIXEL_SIZE = 1.0964912280701755;
    private static final int ORIGINAL_WIDTH = 6672;
    private static final int ORIGINAL_HEIGHT = 1650;
    private static final int CROP_X = 2744;
    private static final int CROP_Y = 0;
    private static final int CROP_WIDTH = 3928;
    private static final int CROP_HEIGHT = 1650;

    @Test
    void cropLocalMapsToOriginalCoordinates() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.UNSPECIFIED, null);
        assertEquals(2744.0, context.cropXToOriginal(0.0), 0.0);
        assertEquals(0.0, context.cropYToOriginal(0.0), 0.0);
        assertEquals(2844.0, context.cropXToOriginal(100.0), 0.0);
        assertEquals(50.0, context.cropYToOriginal(50.0), 0.0);
    }

    @Test
    void physicalConversionUsesPixelSize() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.UNSPECIFIED, null);
        assertEquals(0, Double.compare(
                2744.0 * PIXEL_SIZE,
                context.originalXToPhysicalUm(2744.0)));
        assertEquals(0, Double.compare(
                50.0 * PIXEL_SIZE,
                context.originalYToPhysicalUm(50.0)));
    }

    @Test
    void topToBottomUsesOriginalY() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.TOP_TO_BOTTOM, null);
        final Optional<Double> axis = context.segmentAxisPositionUm(100.0, 50.0);
        assertTrue(axis.isPresent());
        assertEquals(0, Double.compare(50.0 * PIXEL_SIZE, axis.get().doubleValue()));
    }

    @Test
    void bottomToTopUsesDimensionMinusOneMinusY() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.BOTTOM_TO_TOP, null);
        final Optional<Double> axis = context.segmentAxisPositionUm(100.0, 0.0);
        assertTrue(axis.isPresent());
        assertEquals(0, Double.compare(
                (ORIGINAL_HEIGHT - 1) * PIXEL_SIZE,
                axis.get().doubleValue()));
    }

    @Test
    void leftToRightUsesOriginalX() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.LEFT_TO_RIGHT, null);
        final Optional<Double> axis = context.segmentAxisPositionUm(100.0, 50.0);
        assertTrue(axis.isPresent());
        assertEquals(0, Double.compare(100.0 * PIXEL_SIZE, axis.get().doubleValue()));
    }

    @Test
    void rightToLeftUsesDimensionMinusOneMinusX() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.RIGHT_TO_LEFT, null);
        final Optional<Double> axis = context.segmentAxisPositionUm(0.0, 50.0);
        assertTrue(axis.isPresent());
        assertEquals(0, Double.compare(
                (ORIGINAL_WIDTH - 1) * PIXEL_SIZE,
                axis.get().doubleValue()));
    }

    @Test
    void unspecifiedOrientationYieldsEmptySegmentAxis() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.UNSPECIFIED, null);
        assertFalse(context.segmentAxisPositionUm(10.0, 20.0).isPresent());
    }

    @Test
    void blankGlobalStartYieldsEmptyGlobalAxis() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.TOP_TO_BOTTOM, null);
        assertFalse(context.getOptionalGlobalSegmentStartUm().isPresent());
        assertFalse(context.globalAxisPositionUm(10.0, 20.0).isPresent());
    }

    @Test
    void unspecifiedWithGlobalStartStillYieldsEmptyGlobalAxis() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.UNSPECIFIED, Double.valueOf(1000.0));
        assertFalse(context.segmentAxisPositionUm(10.0, 20.0).isPresent());
        assertFalse(context.globalAxisPositionUm(10.0, 20.0).isPresent());
    }

    @Test
    void globalAxisCombinesStartAndLocalPosition() {
        final double globalStart = 2500.0;
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.TOP_TO_BOTTOM, Double.valueOf(globalStart));
        final Optional<Double> global = context.globalAxisPositionUm(0.0, 100.0);
        assertTrue(global.isPresent());
        assertEquals(0, Double.compare(
                globalStart + 100.0 * PIXEL_SIZE,
                global.get().doubleValue()));
    }

    @Test
    void neverFabricatesZeroForUnavailableGlobal() {
        final LumenCoordinateContext context = baseContext(
                SegmentOrientation.LEFT_TO_RIGHT, null);
        final Optional<Double> global = context.globalAxisPositionUm(0.0, 0.0);
        assertFalse(global.isPresent());
    }

    private static LumenCoordinateContext baseContext(
            final SegmentOrientation orientation,
            final Double globalStartUm) {
        return new LumenCoordinateContext(
                "sample-a",
                "segment-1",
                1,
                orientation,
                ORIGINAL_WIDTH,
                ORIGINAL_HEIGHT,
                CROP_X,
                CROP_Y,
                CROP_WIDTH,
                CROP_HEIGHT,
                PIXEL_SIZE,
                PIXEL_SIZE,
                globalStartUm);
    }
}
