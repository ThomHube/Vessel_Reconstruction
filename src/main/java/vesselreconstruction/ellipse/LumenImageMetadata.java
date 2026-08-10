package vesselreconstruction.ellipse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable Lumen coordinate/calibration metadata stored in ImageJ Info properties.
 *
 * <p>Schema version 1. Shared by tiled classification outputs, optional RGB crop,
 * All Lumina, and tests.</p>
 */
public final class LumenImageMetadata {

    public static final int SCHEMA_VERSION = 1;
    public static final String KEY_PREFIX = "lumen.";

    public static final String KEY_SCHEMA_VERSION = "lumen.schema_version";
    public static final String KEY_SOURCE_FILENAME = "lumen.source_filename";
    public static final String KEY_SOURCE_PATH = "lumen.source_path";
    public static final String KEY_SAMPLE_ID = "lumen.sample_id";
    public static final String KEY_SEGMENT_ID = "lumen.segment_id";
    public static final String KEY_SEGMENT_ORDER = "lumen.segment_order";
    public static final String KEY_SEGMENT_ORIENTATION = "lumen.segment_orientation";
    public static final String KEY_ORIGINAL_WIDTH_PX = "lumen.original_width_px";
    public static final String KEY_ORIGINAL_HEIGHT_PX = "lumen.original_height_px";
    public static final String KEY_CROP_X_PX = "lumen.crop_x_px";
    public static final String KEY_CROP_Y_PX = "lumen.crop_y_px";
    public static final String KEY_CROP_WIDTH_PX = "lumen.crop_width_px";
    public static final String KEY_CROP_HEIGHT_PX = "lumen.crop_height_px";
    public static final String KEY_PIXEL_WIDTH_UM = "lumen.pixel_width_um";
    public static final String KEY_PIXEL_HEIGHT_UM = "lumen.pixel_height_um";
    public static final String KEY_CALIBRATION_METHOD = "lumen.calibration_method";
    public static final String KEY_SCALE_BAR_VALUE_UM = "lumen.scale_bar_value_um";
    public static final String KEY_SCALE_BAR_LENGTH_PX = "lumen.scale_bar_length_px";
    public static final String KEY_GLOBAL_SEGMENT_START_UM = "lumen.global_segment_start_um";
    public static final String KEY_MASK_DOWNSAMPLE_FACTOR = "lumen.mask_downsample_factor";
    public static final String KEY_MASK_CHECKSUM = "lumen.mask_checksum";
    public static final String KEY_CLASSIFIER_PATH = "lumen.classifier_path";
    public static final String KEY_CLASSIFIER_FILENAME = "lumen.classifier_filename";
    public static final String KEY_CORE_TILE_WIDTH_PX = "lumen.core_tile_width_px";
    public static final String KEY_CORE_TILE_HEIGHT_PX = "lumen.core_tile_height_px";
    public static final String KEY_CLASSIFICATION_HALO_PX = "lumen.classification_halo_px";
    public static final String KEY_SOURCE_CHECKSUM = "lumen.source_checksum";
    public static final String KEY_OUTPUT_CHECKSUM = "lumen.output_checksum";
    public static final String KEY_CREATED_TIMESTAMP = "lumen.created_timestamp";

    public static final String KEY_SCALE_BAR_DETECTOR_VERSION = "lumen.scale_bar_detector_version";
    public static final String KEY_SCALE_BAR_DETECTION_CONFIDENCE =
            "lumen.scale_bar_detection_confidence";
    public static final String KEY_SCALE_BAR_DETECTION_SCORE = "lumen.scale_bar_detection_score";
    public static final String KEY_SCALE_BAR_LEFT_X_PX = "lumen.scale_bar_left_x_px";
    public static final String KEY_SCALE_BAR_LEFT_Y_PX = "lumen.scale_bar_left_y_px";
    public static final String KEY_SCALE_BAR_RIGHT_X_PX = "lumen.scale_bar_right_x_px";
    public static final String KEY_SCALE_BAR_RIGHT_Y_PX = "lumen.scale_bar_right_y_px";
    public static final String KEY_SCALE_BAR_CANDIDATE_X_PX = "lumen.scale_bar_candidate_x_px";
    public static final String KEY_SCALE_BAR_CANDIDATE_Y_PX = "lumen.scale_bar_candidate_y_px";
    public static final String KEY_SCALE_BAR_CANDIDATE_WIDTH_PX =
            "lumen.scale_bar_candidate_width_px";
    public static final String KEY_SCALE_BAR_CANDIDATE_HEIGHT_PX =
            "lumen.scale_bar_candidate_height_px";

    public static final class ParseResult {
        private final LumenImageMetadata metadata;
        private final boolean available;
        private final String unavailableReason;

        private ParseResult(
                final LumenImageMetadata metadata,
                final boolean available,
                final String unavailableReason) {
            this.metadata = metadata;
            this.available = available;
            this.unavailableReason = unavailableReason;
        }

        public static ParseResult available(final LumenImageMetadata metadata) {
            return new ParseResult(metadata, true, null);
        }

        public static ParseResult unavailable(final String reason) {
            return new ParseResult(null, false, reason);
        }

        public boolean isAvailable() {
            return available;
        }

        public Optional<LumenImageMetadata> getMetadata() {
            return Optional.ofNullable(metadata);
        }

        public String getUnavailableReason() {
            return unavailableReason;
        }
    }

    private final Map<String, String> values;

    public LumenImageMetadata(final Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    public Map<String, String> asMap() {
        return values;
    }

    public String getRequired(final String key) {
        final String value = values.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required metadata key: " + key);
        }
        return value;
    }

    public Optional<String> getOptional(final String key) {
        final String value = values.get(key);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public int getIntRequired(final String key) {
        final String raw = getRequired(key);
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ex) {
            throw new IllegalStateException(
                    "Malformed integer for " + key + ": " + raw, ex);
        }
    }

    public double getDoubleRequired(final String key) {
        final String raw = getRequired(key);
        try {
            final double value = Double.parseDouble(raw);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalStateException(
                        "Non-finite double for " + key + ": " + raw);
            }
            return value;
        } catch (final NumberFormatException ex) {
            throw new IllegalStateException(
                    "Malformed double for " + key + ": " + raw, ex);
        }
    }

    public Optional<Double> getOptionalDouble(final String key) {
        final Optional<String> raw = getOptional(key);
        if (!raw.isPresent()) {
            return Optional.empty();
        }
        try {
            final double value = Double.parseDouble(raw.get());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalStateException(
                        "Non-finite optional double for " + key + ": " + raw.get());
            }
            return Optional.of(Double.valueOf(value));
        } catch (final NumberFormatException ex) {
            throw new IllegalStateException(
                    "Malformed optional double for " + key + ": " + raw.get(), ex);
        }
    }

    public LumenCoordinateContext toCoordinateContext() {
        final Double globalStart = getOptionalDouble(KEY_GLOBAL_SEGMENT_START_UM).orElse(null);
        return new LumenCoordinateContext(
                getRequired(KEY_SAMPLE_ID),
                getRequired(KEY_SEGMENT_ID),
                getIntRequired(KEY_SEGMENT_ORDER),
                SegmentOrientation.valueOf(getRequired(KEY_SEGMENT_ORIENTATION)),
                getIntRequired(KEY_ORIGINAL_WIDTH_PX),
                getIntRequired(KEY_ORIGINAL_HEIGHT_PX),
                getIntRequired(KEY_CROP_X_PX),
                getIntRequired(KEY_CROP_Y_PX),
                getIntRequired(KEY_CROP_WIDTH_PX),
                getIntRequired(KEY_CROP_HEIGHT_PX),
                getDoubleRequired(KEY_PIXEL_WIDTH_UM),
                getDoubleRequired(KEY_PIXEL_HEIGHT_UM),
                globalStart);
    }

    public String serialize() {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return sb.toString();
    }

    public static ParseResult parse(final String infoProperty) {
        if (infoProperty == null || infoProperty.trim().isEmpty()) {
            return ParseResult.unavailable("No Lumen metadata present");
        }
        final Map<String, String> map = new LinkedHashMap<String, String>();
        final String[] lines = infoProperty.split("\\r?\\n");
        boolean sawLumenKey = false;
        for (final String line : lines) {
            if (line == null) {
                continue;
            }
            final String trimmed = line.trim();
            if (!trimmed.startsWith(KEY_PREFIX)) {
                continue;
            }
            sawLumenKey = true;
            final int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                return ParseResult.unavailable("Malformed Lumen metadata line: " + trimmed);
            }
            final String key = trimmed.substring(0, eq).trim();
            final String value = trimmed.substring(eq + 1);
            map.put(key, value);
        }
        if (!sawLumenKey) {
            return ParseResult.unavailable("No Lumen metadata present");
        }
        try {
            requireInt(map, KEY_SCHEMA_VERSION);
            requireNonBlank(map, KEY_SAMPLE_ID);
            requireNonBlank(map, KEY_SEGMENT_ID);
            requireInt(map, KEY_SEGMENT_ORDER);
            requireNonBlank(map, KEY_SEGMENT_ORIENTATION);
            SegmentOrientation.valueOf(map.get(KEY_SEGMENT_ORIENTATION));
            requireInt(map, KEY_ORIGINAL_WIDTH_PX);
            requireInt(map, KEY_ORIGINAL_HEIGHT_PX);
            requireInt(map, KEY_CROP_X_PX);
            requireInt(map, KEY_CROP_Y_PX);
            requireInt(map, KEY_CROP_WIDTH_PX);
            requireInt(map, KEY_CROP_HEIGHT_PX);
            requireFiniteDouble(map, KEY_PIXEL_WIDTH_UM);
            requireFiniteDouble(map, KEY_PIXEL_HEIGHT_UM);
            requireNonBlank(map, KEY_CALIBRATION_METHOD);
            CalibrationMode.valueOf(map.get(KEY_CALIBRATION_METHOD));
            final LumenImageMetadata metadata = new LumenImageMetadata(map);
            // Validate coordinate context construction.
            metadata.toCoordinateContext();
            return ParseResult.available(metadata);
        } catch (final RuntimeException ex) {
            return ParseResult.unavailable(
                    "Malformed required Lumen metadata: " + ex.getMessage());
        }
    }

    public static LumenImageMetadata create(
            final String sourceFilename,
            final String sourcePath,
            final String sampleId,
            final String segmentId,
            final int segmentOrder,
            final SegmentOrientation orientation,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final Double globalSegmentStartUm,
            final int maskDownsampleFactor,
            final Integer maskChecksum,
            final String classifierPath,
            final String classifierFilename,
            final int coreTileWidthPx,
            final int coreTileHeightPx,
            final int classificationHaloPx,
            final Integer sourceChecksum,
            final Integer outputChecksum,
            final String createdTimestamp) {
        return create(
                sourceFilename,
                sourcePath,
                sampleId,
                segmentId,
                segmentOrder,
                orientation,
                crop,
                calibration,
                globalSegmentStartUm,
                maskDownsampleFactor,
                maskChecksum,
                classifierPath,
                classifierFilename,
                coreTileWidthPx,
                coreTileHeightPx,
                classificationHaloPx,
                sourceChecksum,
                outputChecksum,
                createdTimestamp,
                null);
    }

    public static LumenImageMetadata create(
            final String sourceFilename,
            final String sourcePath,
            final String sampleId,
            final String segmentId,
            final int segmentOrder,
            final SegmentOrientation orientation,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final Double globalSegmentStartUm,
            final int maskDownsampleFactor,
            final Integer maskChecksum,
            final String classifierPath,
            final String classifierFilename,
            final int coreTileWidthPx,
            final int coreTileHeightPx,
            final int classificationHaloPx,
            final Integer sourceChecksum,
            final Integer outputChecksum,
            final String createdTimestamp,
            final ScaleBarDetection scaleBarDetection) {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(KEY_SCHEMA_VERSION, Integer.toString(SCHEMA_VERSION));
        map.put(KEY_SOURCE_FILENAME, nullToEmpty(sourceFilename));
        map.put(KEY_SOURCE_PATH, nullToEmpty(sourcePath));
        map.put(KEY_SAMPLE_ID, sampleId);
        map.put(KEY_SEGMENT_ID, segmentId);
        map.put(KEY_SEGMENT_ORDER, Integer.toString(segmentOrder));
        map.put(KEY_SEGMENT_ORIENTATION, orientation.name());
        map.put(KEY_ORIGINAL_WIDTH_PX, Integer.toString(crop.getOriginalWidthPx()));
        map.put(KEY_ORIGINAL_HEIGHT_PX, Integer.toString(crop.getOriginalHeightPx()));
        map.put(KEY_CROP_X_PX, Integer.toString(crop.getCropXPx()));
        map.put(KEY_CROP_Y_PX, Integer.toString(crop.getCropYPx()));
        map.put(KEY_CROP_WIDTH_PX, Integer.toString(crop.getCropWidthPx()));
        map.put(KEY_CROP_HEIGHT_PX, Integer.toString(crop.getCropHeightPx()));
        map.put(KEY_PIXEL_WIDTH_UM, formatDouble(calibration.getPixelWidthUm()));
        map.put(KEY_PIXEL_HEIGHT_UM, formatDouble(calibration.getPixelHeightUm()));
        map.put(KEY_CALIBRATION_METHOD, calibration.getCalibrationMethod().name());
        map.put(KEY_SCALE_BAR_VALUE_UM, optionalDouble(calibration.getScaleBarPhysicalLengthUm()));
        map.put(KEY_SCALE_BAR_LENGTH_PX, optionalDouble(calibration.getScaleBarMeasuredLengthPx()));
        map.put(KEY_GLOBAL_SEGMENT_START_UM, optionalDouble(globalSegmentStartUm));
        map.put(KEY_MASK_DOWNSAMPLE_FACTOR, Integer.toString(maskDownsampleFactor));
        map.put(KEY_MASK_CHECKSUM, maskChecksum == null ? "" : Integer.toString(maskChecksum.intValue()));
        map.put(KEY_CLASSIFIER_PATH, nullToEmpty(classifierPath));
        map.put(KEY_CLASSIFIER_FILENAME, nullToEmpty(classifierFilename));
        map.put(KEY_CORE_TILE_WIDTH_PX, Integer.toString(coreTileWidthPx));
        map.put(KEY_CORE_TILE_HEIGHT_PX, Integer.toString(coreTileHeightPx));
        map.put(KEY_CLASSIFICATION_HALO_PX, Integer.toString(classificationHaloPx));
        map.put(KEY_SOURCE_CHECKSUM, sourceChecksum == null ? "" : Integer.toString(sourceChecksum.intValue()));
        map.put(KEY_OUTPUT_CHECKSUM, outputChecksum == null ? "" : Integer.toString(outputChecksum.intValue()));
        map.put(KEY_CREATED_TIMESTAMP, nullToEmpty(createdTimestamp));
        if (scaleBarDetection != null && scaleBarDetection.isSuccessful()) {
            map.put(KEY_SCALE_BAR_DETECTOR_VERSION, ScaleBarDetection.DETECTOR_VERSION);
            map.put(KEY_SCALE_BAR_DETECTION_CONFIDENCE, scaleBarDetection.getConfidence().name());
            map.put(KEY_SCALE_BAR_DETECTION_SCORE, formatDouble(scaleBarDetection.getScore()));
            map.put(KEY_SCALE_BAR_LEFT_X_PX, formatDouble(scaleBarDetection.getLeftTickCenterX()));
            map.put(KEY_SCALE_BAR_LEFT_Y_PX, formatDouble(scaleBarDetection.getLeftTickCenterY()));
            map.put(KEY_SCALE_BAR_RIGHT_X_PX, formatDouble(scaleBarDetection.getRightTickCenterX()));
            map.put(KEY_SCALE_BAR_RIGHT_Y_PX, formatDouble(scaleBarDetection.getRightTickCenterY()));
            if (scaleBarDetection.getFullResolutionComponentBounds() != null) {
                final java.awt.Rectangle box = scaleBarDetection.getFullResolutionComponentBounds();
                map.put(KEY_SCALE_BAR_CANDIDATE_X_PX, Integer.toString(box.x));
                map.put(KEY_SCALE_BAR_CANDIDATE_Y_PX, Integer.toString(box.y));
                map.put(KEY_SCALE_BAR_CANDIDATE_WIDTH_PX, Integer.toString(box.width));
                map.put(KEY_SCALE_BAR_CANDIDATE_HEIGHT_PX, Integer.toString(box.height));
            }
        }
        return new LumenImageMetadata(map);
    }

    public static String formatDouble(final double value) {
        return String.format(Locale.US, "%.17g", Double.valueOf(value));
    }

    private static String optionalDouble(final Double value) {
        return value == null ? "" : formatDouble(value.doubleValue());
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    private static void requireNonBlank(final Map<String, String> map, final String key) {
        final String value = map.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
    }

    private static int requireInt(final Map<String, String> map, final String key) {
        requireNonBlank(map, key);
        try {
            return Integer.parseInt(map.get(key));
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed int for " + key + ": " + map.get(key));
        }
    }

    private static double requireFiniteDouble(final Map<String, String> map, final String key) {
        requireNonBlank(map, key);
        try {
            final double value = Double.parseDouble(map.get(key));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Non-finite double for " + key);
            }
            return value;
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed double for " + key + ": " + map.get(key));
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LumenImageMetadata)) {
            return false;
        }
        return values.equals(((LumenImageMetadata) obj).values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
