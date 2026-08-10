package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenImageMetadataTest {

    private static final double PIXEL_SIZE = 1.0964912280701755;

    @Test
    void roundTripSerializeAndParseRequiredFields() {
        final LumenImageMetadata original = sampleMetadata(null);
        final String serialized = original.serialize();
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(serialized);

        assertTrue(parsed.isAvailable());
        assertEquals(original, parsed.getMetadata().get());
        final LumenCoordinateContext context = parsed.getMetadata().get().toCoordinateContext();
        assertEquals("sample-a", context.getSampleId());
        assertEquals("segment-1", context.getSegmentId());
        assertEquals(1, context.getSegmentOrder());
        assertEquals(SegmentOrientation.UNSPECIFIED, context.getSegmentOrientation());
        assertEquals(2744, context.getCropX());
        assertEquals(0, Double.compare(PIXEL_SIZE, context.getPixelWidthUm()));
    }

    @Test
    void malformedRequiredFieldsAreRejected() {
        final Map<String, String> values = requiredFields();
        values.put(LumenImageMetadata.KEY_SEGMENT_ORDER, "not-an-int");
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                new LumenImageMetadata(values).serialize());
        assertFalse(parsed.isAvailable());
        assertTrue(parsed.getUnavailableReason().contains("Malformed required"));
    }

    @Test
    void missingMetadataIsUnavailable() {
        final LumenImageMetadata.ParseResult empty = LumenImageMetadata.parse("");
        assertFalse(empty.isAvailable());
        assertEquals("No Lumen metadata present", empty.getUnavailableReason());

        final LumenImageMetadata.ParseResult unrelated = LumenImageMetadata.parse(
                "ImageJ=1.54\nfoo=bar");
        assertFalse(unrelated.isAvailable());
    }

    @Test
    void optionalBlankGlobalStartIsPreserved() {
        final LumenImageMetadata withBlankGlobal = sampleMetadata(null);
        assertFalse(withBlankGlobal.getOptional(
                LumenImageMetadata.KEY_GLOBAL_SEGMENT_START_UM).isPresent());

        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                withBlankGlobal.serialize());
        assertTrue(parsed.isAvailable());
        assertFalse(parsed.getMetadata().get().getOptional(
                LumenImageMetadata.KEY_GLOBAL_SEGMENT_START_UM).isPresent());
        assertFalse(parsed.getMetadata().get().toCoordinateContext()
                .getOptionalGlobalSegmentStartUm().isPresent());
    }

    @Test
    void optionalGlobalStartRoundTripsWhenPresent() {
        final LumenImageMetadata withGlobal = sampleMetadata(Double.valueOf(1234.5));
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                withGlobal.serialize());
        assertTrue(parsed.isAvailable());
        assertEquals(Double.valueOf(1234.5),
                parsed.getMetadata().get().getOptionalDouble(
                        LumenImageMetadata.KEY_GLOBAL_SEGMENT_START_UM).get());
    }

    @Test
    void olderMetadataWithoutDetectorKeysRemainsReadable() {
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                new LumenImageMetadata(requiredFields()).serialize());
        assertTrue(parsed.isAvailable());
        assertFalse(parsed.getMetadata().get().getOptional(
                LumenImageMetadata.KEY_SCALE_BAR_DETECTION_CONFIDENCE).isPresent());
    }

    @Test
    void automaticDetectorOptionalKeysRoundTrip() {
        final ScaleBarDetection detection = ScaleBarDetection.measured(
                ScaleBarDetectionConfidence.HIGH,
                12.5,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                10.0,
                40.0,
                466.0,
                40.0,
                456.0,
                469.0,
                0.5,
                0.95,
                new java.awt.Rectangle(5, 30, 470, 25),
                new java.awt.Rectangle(0, 20, 490, 45),
                new java.awt.Rectangle(10, 30, 469, 25),
                java.util.Collections.<ScaleBarDetectionCandidate>emptyList());
        final AnalysisCropGeometry crop = AnalysisCropGeometry.fullSource(500, 80);
        final PhysicalCalibration calibration =
                PhysicalCalibration.fromAutoDetectedScaleBar(500.0, 456.0);
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
                Integer.valueOf(1),
                "/m.model",
                "m.model",
                2048,
                2048,
                0,
                Integer.valueOf(1),
                Integer.valueOf(2),
                "ts",
                detection);
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(metadata.serialize());
        assertTrue(parsed.isAvailable());
        assertEquals(CalibrationMode.AUTO_DETECT_RED_SCALE_BAR.name(),
                parsed.getMetadata().get().getRequired(
                        LumenImageMetadata.KEY_CALIBRATION_METHOD));
        assertEquals("HIGH", parsed.getMetadata().get().getRequired(
                LumenImageMetadata.KEY_SCALE_BAR_DETECTION_CONFIDENCE));
        assertEquals("456", parsed.getMetadata().get().getRequired(
                LumenImageMetadata.KEY_SCALE_BAR_LENGTH_PX).substring(0, 3));
    }

    private static LumenImageMetadata sampleMetadata(final Double globalStartUm) {
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
        return LumenImageMetadata.create(
                "source.tif",
                "/data/source.tif",
                "sample-a",
                "segment-1",
                1,
                SegmentOrientation.UNSPECIFIED,
                crop,
                calibration,
                globalStartUm,
                8,
                Integer.valueOf(42),
                "/models/classifier.model",
                "classifier.model",
                2048,
                2048,
                0,
                Integer.valueOf(99),
                Integer.valueOf(100),
                "2026-08-01T12:00:00Z");
    }

    private static Map<String, String> requiredFields() {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(LumenImageMetadata.KEY_SCHEMA_VERSION, "1");
        map.put(LumenImageMetadata.KEY_SAMPLE_ID, "sample-a");
        map.put(LumenImageMetadata.KEY_SEGMENT_ID, "segment-1");
        map.put(LumenImageMetadata.KEY_SEGMENT_ORDER, "1");
        map.put(LumenImageMetadata.KEY_SEGMENT_ORIENTATION,
                SegmentOrientation.UNSPECIFIED.name());
        map.put(LumenImageMetadata.KEY_ORIGINAL_WIDTH_PX, "6672");
        map.put(LumenImageMetadata.KEY_ORIGINAL_HEIGHT_PX, "1650");
        map.put(LumenImageMetadata.KEY_CROP_X_PX, "2744");
        map.put(LumenImageMetadata.KEY_CROP_Y_PX, "0");
        map.put(LumenImageMetadata.KEY_CROP_WIDTH_PX, "3928");
        map.put(LumenImageMetadata.KEY_CROP_HEIGHT_PX, "1650");
        map.put(LumenImageMetadata.KEY_PIXEL_WIDTH_UM,
                LumenImageMetadata.formatDouble(PIXEL_SIZE));
        map.put(LumenImageMetadata.KEY_PIXEL_HEIGHT_UM,
                LumenImageMetadata.formatDouble(PIXEL_SIZE));
        map.put(LumenImageMetadata.KEY_CALIBRATION_METHOD,
                CalibrationMode.MEASURE_SCALE_BAR_LINE.name());
        return map;
    }
}
