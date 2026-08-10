package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Atomic one-image Phase-2 precheck result properties.
 */
public final class BatchPrecheckResult {

    public static final String KEY_SCHEMA = "result.schema_version";
    public static final String KEY_STATUS = "status";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_REASON = "reason_code";
    public static final String KEY_IMAGE_ID = "image_id";
    public static final String KEY_MACHINE_ID = "machine_id";
    public static final String KEY_SOURCE_FILENAME = "source_filename";
    public static final String KEY_SOURCE_RELATIVE = "source_relative_path";
    public static final String KEY_SOURCE_SHA256 = "source_sha256";
    public static final String KEY_CONFIG_SHA256 = "configuration_sha256";
    public static final String KEY_WIDTH = "image_width_px";
    public static final String KEY_HEIGHT = "image_height_px";
    public static final String KEY_MASK_THRESHOLD = "mask_threshold";
    public static final String KEY_MASK_CANDIDATES = "mask_candidate_count";
    public static final String KEY_MASK_INCLUDED = "mask_included_fraction";
    public static final String KEY_MASK_MAJOR = "mask_major_span_fraction";
    public static final String KEY_MASK_SELECTED = "mask_selected_component_area";
    public static final String KEY_MASK_SECONDARY = "mask_secondary_component_area_fraction";
    public static final String KEY_MASK_CHECKSUM = "mask_checksum";
    public static final String KEY_CROP_X = "crop_x_px";
    public static final String KEY_CROP_Y = "crop_y_px";
    public static final String KEY_CROP_W = "crop_width_px";
    public static final String KEY_CROP_H = "crop_height_px";
    public static final String KEY_SB_METHOD = "scale_bar_detection_method";
    public static final String KEY_SB_CONF = "scale_bar_confidence";
    public static final String KEY_SB_SCORE = "scale_bar_score";
    public static final String KEY_SB_CENTER = "scale_bar_center_distance_px";
    public static final String KEY_SB_OUTER = "scale_bar_outer_width_px";
    public static final String KEY_SB_CONT = "scale_bar_shaft_continuity";
    public static final String KEY_PX_SIZE = "pixel_size_um";
    public static final String KEY_PX_DEV = "pixel_size_relative_deviation";
    public static final String KEY_CSUM_BEFORE = "source_checksum_before";
    public static final String KEY_CSUM_AFTER = "source_checksum_after";
    public static final String KEY_SOURCE_UNCHANGED = "source_unchanged";
    public static final String KEY_MASK_PREVIEW = "mask_preview_path";
    public static final String KEY_SCALE_PREVIEW = "scale_bar_preview_path";
    public static final String KEY_TIME_MS = "processing_time_ms";

    public static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA, KEY_STATUS, KEY_MESSAGE, KEY_REASON, KEY_IMAGE_ID, KEY_MACHINE_ID,
                    KEY_SOURCE_FILENAME, KEY_SOURCE_RELATIVE, KEY_SOURCE_SHA256, KEY_CONFIG_SHA256,
                    KEY_WIDTH, KEY_HEIGHT, KEY_MASK_THRESHOLD, KEY_MASK_CANDIDATES, KEY_MASK_INCLUDED,
                    KEY_MASK_MAJOR, KEY_MASK_SELECTED, KEY_MASK_SECONDARY, KEY_MASK_CHECKSUM,
                    KEY_CROP_X, KEY_CROP_Y, KEY_CROP_W, KEY_CROP_H,
                    KEY_SB_METHOD, KEY_SB_CONF, KEY_SB_SCORE, KEY_SB_CENTER, KEY_SB_OUTER, KEY_SB_CONT,
                    KEY_PX_SIZE, KEY_PX_DEV, KEY_CSUM_BEFORE, KEY_CSUM_AFTER, KEY_SOURCE_UNCHANGED,
                    KEY_MASK_PREVIEW, KEY_SCALE_PREVIEW, KEY_TIME_MS)));

    private final Map<String, String> values;

    private BatchPrecheckResult(final Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<String, String>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BatchPrecheckResult load(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return fromMap(BatchConfiguration.parseStrictProperties(lines));
    }

    public static BatchPrecheckResult fromMap(final Map<String, String> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        for (final String key : raw.keySet()) {
            if (!REQUIRED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown result key: " + key);
            }
        }
        for (final String required : REQUIRED_KEYS) {
            if (!raw.containsKey(required)) {
                throw new IllegalArgumentException("Missing result key: " + required);
            }
        }
        final Map<String, String> normalized = new TreeMap<String, String>();
        for (final String key : REQUIRED_KEYS) {
            normalized.put(key, sanitize(raw.get(key)));
        }
        BatchPrecheckStatus.parse(normalized.get(KEY_STATUS));
        return new BatchPrecheckResult(normalized);
    }

    public void write(final Path target) throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        Sha256Files.writeBytesAtomically(target, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public BatchPrecheckStatus status() {
        return BatchPrecheckStatus.parse(values.get(KEY_STATUS));
    }

    public String get(final String key) {
        return values.get(key);
    }

    public Map<String, String> asMap() {
        return values;
    }

    public List<String> toCsvRow(final int orderIndex) {
        return RCsvSupport.mutableCopy(
                Integer.toString(BatchPrecheckCsvSchema.SCHEMA_VERSION),
                Integer.toString(orderIndex),
                values.get(KEY_IMAGE_ID),
                values.get(KEY_MACHINE_ID),
                values.get(KEY_SOURCE_FILENAME),
                values.get(KEY_SOURCE_RELATIVE),
                values.get(KEY_SOURCE_SHA256),
                values.get(KEY_CONFIG_SHA256),
                values.get(KEY_STATUS),
                values.get(KEY_REASON),
                values.get(KEY_MESSAGE),
                values.get(KEY_WIDTH),
                values.get(KEY_HEIGHT),
                values.get(KEY_MASK_THRESHOLD),
                values.get(KEY_MASK_CANDIDATES),
                values.get(KEY_MASK_INCLUDED),
                values.get(KEY_MASK_MAJOR),
                values.get(KEY_MASK_SELECTED),
                values.get(KEY_MASK_SECONDARY),
                values.get(KEY_MASK_CHECKSUM),
                values.get(KEY_CROP_X),
                values.get(KEY_CROP_Y),
                values.get(KEY_CROP_W),
                values.get(KEY_CROP_H),
                values.get(KEY_SB_METHOD),
                values.get(KEY_SB_CONF),
                values.get(KEY_SB_SCORE),
                values.get(KEY_SB_CENTER),
                values.get(KEY_SB_OUTER),
                values.get(KEY_SB_CONT),
                values.get(KEY_PX_SIZE),
                values.get(KEY_PX_DEV),
                values.get(KEY_CSUM_BEFORE),
                values.get(KEY_CSUM_AFTER),
                values.get(KEY_SOURCE_UNCHANGED),
                values.get(KEY_MASK_PREVIEW),
                values.get(KEY_SCALE_PREVIEW),
                values.get(KEY_TIME_MS));
    }

    static String sanitize(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return RCsvSupport.NA;
        }
        final String trimmed = raw.trim();
        if ("NaN".equalsIgnoreCase(trimmed)
                || "Infinity".equalsIgnoreCase(trimmed)
                || "-Infinity".equalsIgnoreCase(trimmed)) {
            return RCsvSupport.NA;
        }
        return trimmed;
    }

    static String numberOrNa(final double value) {
        if (!Double.isFinite(value)) {
            return RCsvSupport.NA;
        }
        return String.format(Locale.US, "%.16g", Double.valueOf(value));
    }

    static String intOrNa(final Integer value) {
        return value == null ? RCsvSupport.NA : Integer.toString(value.intValue());
    }

    static String longOrNa(final Long value) {
        return value == null ? RCsvSupport.NA : Long.toString(value.longValue());
    }

    static String textOrNa(final String value) {
        return value == null || value.trim().isEmpty() ? RCsvSupport.NA : value.trim();
    }

    public static final class Builder {
        private final Map<String, String> map = new TreeMap<String, String>();

        public Builder() {
            for (final String key : REQUIRED_KEYS) {
                map.put(key, RCsvSupport.NA);
            }
            map.put(KEY_SCHEMA, "1");
        }

        public Builder status(final BatchPrecheckStatus status) {
            map.put(KEY_STATUS, status.name());
            return this;
        }

        public Builder message(final String message) {
            map.put(KEY_MESSAGE, textOrNa(message));
            return this;
        }

        public Builder reasonCode(final String reason) {
            map.put(KEY_REASON, textOrNa(reason));
            return this;
        }

        public Builder imageId(final String imageId) {
            map.put(KEY_IMAGE_ID, textOrNa(imageId));
            return this;
        }

        public Builder machineId(final String machineId) {
            map.put(KEY_MACHINE_ID, textOrNa(machineId));
            return this;
        }

        public Builder sourceFilename(final String filename) {
            map.put(KEY_SOURCE_FILENAME, textOrNa(filename));
            return this;
        }

        public Builder sourceRelativePath(final String relative) {
            map.put(KEY_SOURCE_RELATIVE, textOrNa(relative));
            return this;
        }

        public Builder sourceSha256(final String sha) {
            map.put(KEY_SOURCE_SHA256, textOrNa(sha));
            return this;
        }

        public Builder configurationSha256(final String sha) {
            map.put(KEY_CONFIG_SHA256, textOrNa(sha));
            return this;
        }

        public Builder imageSize(final int width, final int height) {
            map.put(KEY_WIDTH, Integer.toString(width));
            map.put(KEY_HEIGHT, Integer.toString(height));
            return this;
        }

        public Builder maskStats(
                final Integer threshold,
                final Integer candidates,
                final double included,
                final double majorSpan,
                final Long selectedArea,
                final double secondary,
                final Integer checksum) {
            map.put(KEY_MASK_THRESHOLD, intOrNa(threshold));
            map.put(KEY_MASK_CANDIDATES, intOrNa(candidates));
            map.put(KEY_MASK_INCLUDED, numberOrNa(included));
            map.put(KEY_MASK_MAJOR, numberOrNa(majorSpan));
            map.put(KEY_MASK_SELECTED, longOrNa(selectedArea));
            map.put(KEY_MASK_SECONDARY, numberOrNa(secondary));
            map.put(KEY_MASK_CHECKSUM, intOrNa(checksum));
            return this;
        }

        public Builder crop(final Integer x, final Integer y, final Integer w, final Integer h) {
            map.put(KEY_CROP_X, intOrNa(x));
            map.put(KEY_CROP_Y, intOrNa(y));
            map.put(KEY_CROP_W, intOrNa(w));
            map.put(KEY_CROP_H, intOrNa(h));
            return this;
        }

        public Builder scaleBar(
                final String method,
                final String confidence,
                final double score,
                final double centerDistance,
                final double outerWidth,
                final double continuity,
                final double pixelSizeUm,
                final double relativeDeviation) {
            map.put(KEY_SB_METHOD, textOrNa(method));
            map.put(KEY_SB_CONF, textOrNa(confidence));
            map.put(KEY_SB_SCORE, numberOrNa(score));
            map.put(KEY_SB_CENTER, numberOrNa(centerDistance));
            map.put(KEY_SB_OUTER, numberOrNa(outerWidth));
            map.put(KEY_SB_CONT, numberOrNa(continuity));
            map.put(KEY_PX_SIZE, numberOrNa(pixelSizeUm));
            map.put(KEY_PX_DEV, numberOrNa(relativeDeviation));
            return this;
        }

        public Builder sourceChecksums(
                final Integer before,
                final Integer after,
                final Boolean unchanged) {
            map.put(KEY_CSUM_BEFORE, intOrNa(before));
            map.put(KEY_CSUM_AFTER, intOrNa(after));
            map.put(KEY_SOURCE_UNCHANGED, unchanged == null
                    ? RCsvSupport.NA
                    : (unchanged.booleanValue() ? RCsvSupport.TRUE : RCsvSupport.FALSE));
            return this;
        }

        public Builder previews(final String maskPreview, final String scalePreview) {
            map.put(KEY_MASK_PREVIEW, textOrNa(maskPreview));
            map.put(KEY_SCALE_PREVIEW, textOrNa(scalePreview));
            return this;
        }

        public Builder processingTimeMs(final long ms) {
            map.put(KEY_TIME_MS, Long.toString(ms));
            return this;
        }

        public BatchPrecheckResult build() {
            return fromMap(map);
        }
    }
}
