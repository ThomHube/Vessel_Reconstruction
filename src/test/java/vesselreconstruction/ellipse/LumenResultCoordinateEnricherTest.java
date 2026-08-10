package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenResultCoordinateEnricherTest {

    private static final double PIXEL_SIZE = 1.0964912280701755;
    private static final int CROP_X = 2744;
    private static final int CROP_Y = 0;

    private final LumenResultCoordinateEnricher enricher = new LumenResultCoordinateEnricher();

    @Test
    void legacyImageWithoutMetadataFillsCropCentroidsOnly() {
        final Rectangle bounds = new Rectangle(10, 20, 30, 40);
        final LumenBatchItemResult item = skippedItem(bounds, 1200, 0.82, 0.91, 0.09);
        final ImagePlus image = new ImagePlus("legacy", new ByteProcessor(100, 100));

        final LumenResultCoordinateEnricher.EnrichedValues values =
                enricher.enrich(image, item);

        assertFalse(values.contextAvailable);
        assertEquals("", values.sampleId);
        assertEquals("", values.segmentId);
        assertEquals("", values.centroidXOriginalPx);
        assertEquals("", values.centroidYOriginalPx);
        assertEquals("", values.centroidXOriginalUm);
        assertEquals("", values.areaUm2);
        assertEquals(LumenImageMetadata.formatDouble(bounds.x + bounds.width / 2.0),
                values.centroidXCropPx);
        assertEquals(LumenImageMetadata.formatDouble(bounds.y + bounds.height / 2.0),
                values.centroidYCropPx);
    }

    @Test
    void metadataBearingImageEnrichesPhysicalCoordinates() {
        final Rectangle bounds = new Rectangle(10, 20, 30, 40);
        final int sourceArea = 1200;
        final LumenBatchItemResult item = skippedItem(bounds, sourceArea, 0.82, 0.91, 0.09);
        final ImagePlus image = metadataImage();

        final LumenResultCoordinateEnricher.EnrichedValues values =
                enricher.enrich(image, item);

        assertTrue(values.contextAvailable);
        assertEquals("sample-a", values.sampleId);
        assertEquals("segment-1", values.segmentId);
        assertEquals("1", values.segmentOrder);
        assertEquals(SegmentOrientation.UNSPECIFIED.name(), values.segmentOrientation);
        assertEquals(Integer.toString(CROP_X), values.cropXOffsetPx);
        assertEquals(Integer.toString(CROP_Y), values.cropYOffsetPx);

        final double centroidXCrop = bounds.x + bounds.width / 2.0;
        final double centroidYCrop = bounds.y + bounds.height / 2.0;
        final double originalX = CROP_X + centroidXCrop;
        final double originalY = CROP_Y + centroidYCrop;

        assertEquals(LumenImageMetadata.formatDouble(originalX),
                values.centroidXOriginalPx);
        assertEquals(LumenImageMetadata.formatDouble(originalY),
                values.centroidYOriginalPx);
        assertEquals(LumenImageMetadata.formatDouble(originalX * PIXEL_SIZE),
                values.centroidXOriginalUm);
        assertEquals(LumenImageMetadata.formatDouble(originalY * PIXEL_SIZE),
                values.centroidYOriginalUm);
        assertEquals(LumenImageMetadata.formatDouble(
                sourceArea * PIXEL_SIZE * PIXEL_SIZE),
                values.areaUm2);
        assertEquals(LumenImageMetadata.formatDouble(bounds.width * PIXEL_SIZE),
                values.boundingBoxWidthUm);
        assertEquals(LumenImageMetadata.formatDouble(bounds.height * PIXEL_SIZE),
                values.boundingBoxHeightUm);
        assertEquals("", values.segmentAxisPositionUm);
        assertEquals("", values.globalAxisPositionUm);
    }

    private static LumenBatchItemResult skippedItem(
            final Rectangle bounds,
            final int sourceArea,
            final double originalCircularity,
            final double reconstructedCircularity,
            final double circularityChange) {
        return new LumenBatchItemResult(
                1,
                sourceArea,
                bounds,
                false,
                LumenBatchItemResult.Status.BELOW_MINIMUM_AREA,
                "below",
                java.util.Collections.<java.awt.Point>emptyList(),
                false,
                "",
                "",
                "",
                -1,
                -1,
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                -1,
                -1,
                -1,
                -1,
                originalCircularity,
                reconstructedCircularity,
                circularityChange,
                Double.NaN,
                1L);
    }

    private static ImagePlus metadataImage() {
        final AnalysisCropGeometry crop = AnalysisCropGeometry.fromIncludedMaskBounds(
                6672,
                1650,
                8,
                DownsampledSampleMask.maskDimension(6672, 8),
                DownsampledSampleMask.maskDimension(1650, 8),
                343,
                0,
                833,
                206,
                0);
        final PhysicalCalibration calibration = PhysicalCalibration.fromScaleBar(
                500.0, 456.0);
        final LumenImageMetadata metadata = LumenImageMetadata.create(
                "source.tif",
                "/data/source.tif",
                "sample-a",
                "segment-1",
                1,
                SegmentOrientation.UNSPECIFIED,
                crop,
                calibration,
                null,
                8,
                null,
                "",
                "",
                2048,
                2048,
                0,
                null,
                null,
                "2026-08-01T12:00:00Z");
        final ImagePlus image = new ImagePlus("classified", new ByteProcessor(100, 100));
        image.setProperty("Info", metadata.serialize());
        return image;
    }
}
