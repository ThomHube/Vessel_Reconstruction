package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisCropGeometryTest {

    @Test
    void nonDivisibleMappingUsesInclusiveMaskBounds() {
        final int originalWidth = 100;
        final int originalHeight = 50;
        final int factor = 8;
        final int maskWidth = DownsampledSampleMask.maskDimension(originalWidth, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(originalHeight, factor);

        final AnalysisCropGeometry geometry = AnalysisCropGeometry.fromIncludedMaskBounds(
                originalWidth,
                originalHeight,
                factor,
                maskWidth,
                maskHeight,
                1,
                1,
                3,
                2,
                0);

        assertEquals(8, geometry.getCropXPx());
        assertEquals(8, geometry.getCropYPx());
        assertEquals(24, geometry.getCropWidthPx());
        assertEquals(16, geometry.getCropHeightPx());
        assertEquals(1, geometry.getMaskMinX());
        assertEquals(1, geometry.getMaskMinY());
        assertEquals(3, geometry.getMaskMaxX());
        assertEquals(2, geometry.getMaskMaxY());
    }

    @Test
    void paddingExpandsCropWithinSourceBounds() {
        final int originalWidth = 100;
        final int originalHeight = 50;
        final int factor = 8;
        final int maskWidth = DownsampledSampleMask.maskDimension(originalWidth, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(originalHeight, factor);
        final int padding = 4;

        final AnalysisCropGeometry geometry = AnalysisCropGeometry.fromIncludedMaskBounds(
                originalWidth,
                originalHeight,
                factor,
                maskWidth,
                maskHeight,
                1,
                1,
                3,
                2,
                padding);

        assertEquals(4, geometry.getCropXPx());
        assertEquals(4, geometry.getCropYPx());
        assertEquals(32, geometry.getCropWidthPx());
        assertEquals(24, geometry.getCropHeightPx());
        assertEquals(padding, geometry.getCropPaddingPx());
    }

    @Test
    void paddingClipsAtImageBoundaries() {
        final int originalWidth = 100;
        final int originalHeight = 50;
        final int factor = 8;
        final int maskWidth = DownsampledSampleMask.maskDimension(originalWidth, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(originalHeight, factor);
        final int padding = 16;

        final AnalysisCropGeometry geometry = AnalysisCropGeometry.fromIncludedMaskBounds(
                originalWidth,
                originalHeight,
                factor,
                maskWidth,
                maskHeight,
                0,
                0,
                2,
                1,
                padding);

        assertEquals(0, geometry.getCropXPx());
        assertEquals(0, geometry.getCropYPx());
        assertEquals(40, geometry.getCropWidthPx());
        assertEquals(32, geometry.getCropHeightPx());
    }

    @Test
    void validatedCropFromApprovedMaskMatchesKnownRectangle() {
        final int originalWidth = 6672;
        final int originalHeight = 1650;
        final int factor = 8;
        final int maskWidth = DownsampledSampleMask.maskDimension(originalWidth, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(originalHeight, factor);
        final int maskMinX = 343;
        final int maskMinY = 0;
        final int maskMaxX = 833;
        final int maskMaxY = 206;

        final byte[] maskPixels = new byte[maskWidth * maskHeight];
        for (int my = maskMinY; my <= maskMaxY; my++) {
            for (int mx = maskMinX; mx <= maskMaxX; mx++) {
                maskPixels[my * maskWidth + mx] = (byte) 255;
            }
        }
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                originalWidth,
                originalHeight,
                factor,
                maskPixels,
                maskWidth,
                maskHeight);

        assertEquals(2744, mask.getIncludedBoundsOriginal().x);
        assertEquals(0, mask.getIncludedBoundsOriginal().y);
        assertEquals(3928, mask.getIncludedBoundsOriginal().width);
        assertEquals(1650, mask.getIncludedBoundsOriginal().height);

        final AnalysisCropGeometry geometry = AnalysisCropGeometry.fromApprovedMask(mask, 0);
        assertEquals(2744, geometry.getCropXPx());
        assertEquals(0, geometry.getCropYPx());
        assertEquals(3928, geometry.getCropWidthPx());
        assertEquals(1650, geometry.getCropHeightPx());
        assertEquals(maskMinX, geometry.getMaskMinX());
        assertEquals(maskMinY, geometry.getMaskMinY());
        assertEquals(maskMaxX, geometry.getMaskMaxX());
        assertEquals(maskMaxY, geometry.getMaskMaxY());
    }
}
