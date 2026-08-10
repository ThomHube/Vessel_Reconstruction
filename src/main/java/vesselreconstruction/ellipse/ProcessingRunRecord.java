package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Named-column processing-run row for the canonical 24-column schema.
 *
 * <p>Rows are built by column name, never by positional omission.</p>
 */
public final class ProcessingRunRecord {

    public static final int COLUMN_COUNT = 24;

    public static final List<String> HEADER = ProductionSchemas.PROCESSING_RUNS_HEADER;

    static {
        if (HEADER.size() != COLUMN_COUNT) {
            throw new ExceptionInInitializerError(
                    "PROCESSING_RUNS_HEADER size " + HEADER.size()
                            + " != expected " + COLUMN_COUNT);
        }
        final Set<String> unique = new LinkedHashSet<String>(HEADER);
        if (unique.size() != HEADER.size()) {
            throw new ExceptionInInitializerError(
                    "PROCESSING_RUNS_HEADER contains duplicate column names");
        }
    }

    private final Map<String, String> values;

    private ProcessingRunRecord(final Map<String, String> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String get(final String column) {
        return values.get(column);
    }

    /**
     * Serializes to exactly {@link #COLUMN_COUNT} fields in canonical header order.
     */
    public List<String> toRow() {
        validateComplete();
        final List<String> row = new ArrayList<String>(COLUMN_COUNT);
        for (int i = 0; i < HEADER.size(); i++) {
            final String column = HEADER.get(i);
            final String value = values.get(column);
            row.add(sanitizeCell(value));
        }
        if (row.size() != COLUMN_COUNT) {
            throw new IllegalStateException(
                    "Serialized processing-run row has " + row.size()
                            + " fields; expected " + COLUMN_COUNT);
        }
        return row;
    }

    public void validateComplete() {
        final List<String> missing = new ArrayList<String>();
        for (int i = 0; i < HEADER.size(); i++) {
            final String column = HEADER.get(i);
            if (!values.containsKey(column)) {
                missing.add(column);
            }
        }
        final List<String> unknown = new ArrayList<String>();
        for (final String key : values.keySet()) {
            if (!HEADER.contains(key)) {
                unknown.add(key);
            }
        }
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid processing-run record: missing=" + missing
                            + ", unknown=" + unknown
                            + ", expectedHeader=" + HEADER);
        }
        if (values.size() != COLUMN_COUNT) {
            throw new IllegalStateException(
                    "Processing-run record has " + values.size()
                            + " columns; expected " + COLUMN_COUNT);
        }
    }

    public static void validateHeader(final List<String> header, final String path) {
        if (header == null) {
            throw new IllegalArgumentException("header must not be null for " + path);
        }
        final Set<String> seen = new LinkedHashSet<String>();
        final List<String> duplicates = new ArrayList<String>();
        for (int i = 0; i < header.size(); i++) {
            final String name = header.get(i);
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty() || header.size() != COLUMN_COUNT || !header.equals(HEADER)) {
            throw new IllegalArgumentException(describeHeaderMismatch(
                    path, header, HEADER, Collections.<String>emptyList()));
        }
    }

    public static String describeHeaderMismatch(
            final String path,
            final List<String> existingHeader,
            final List<String> expectedHeader,
            final List<String> row) {
        final List<String> existing = existingHeader == null
                ? Collections.<String>emptyList() : existingHeader;
        final List<String> expected = expectedHeader == null
                ? Collections.<String>emptyList() : expectedHeader;
        final Set<String> existingSet = new LinkedHashSet<String>(existing);
        final Set<String> expectedSet = new LinkedHashSet<String>(expected);
        final List<String> missing = new ArrayList<String>();
        for (final String col : expected) {
            if (!existingSet.contains(col)) {
                missing.add(col);
            }
        }
        final List<String> extra = new ArrayList<String>();
        for (final String col : existing) {
            if (!expectedSet.contains(col)) {
                extra.add(col);
            }
        }
        final List<String> duplicates = findDuplicates(existing);
        final StringBuilder sb = new StringBuilder();
        sb.append("Row/header column mismatch for processing_runs.csv\n");
        sb.append("manifest path: ").append(path).append('\n');
        sb.append("existing header column count: ").append(existing.size()).append('\n');
        sb.append("expected header column count: ").append(expected.size()).append('\n');
        sb.append("row column count: ").append(row == null ? 0 : row.size()).append('\n');
        sb.append("existing header names: ").append(existing).append('\n');
        sb.append("expected header names: ").append(expected).append('\n');
        sb.append("missing columns: ").append(missing).append('\n');
        sb.append("extra columns: ").append(extra).append('\n');
        sb.append("duplicate columns: ").append(duplicates);
        return sb.toString();
    }

    public static List<String> findDuplicates(final List<String> header) {
        final Set<String> seen = new LinkedHashSet<String>();
        final List<String> duplicates = new ArrayList<String>();
        if (header == null) {
            return duplicates;
        }
        for (int i = 0; i < header.size(); i++) {
            final String name = header.get(i);
            if (!seen.add(name) && !duplicates.contains(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }

    private static String sanitizeCell(final String value) {
        if (value == null || value.isEmpty()) {
            return RCsvSupport.NA;
        }
        if ("NaN".equalsIgnoreCase(value) || "Infinity".equalsIgnoreCase(value)
                || "-Infinity".equalsIgnoreCase(value)) {
            return RCsvSupport.NA;
        }
        return value;
    }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private Builder() {
            for (int i = 0; i < HEADER.size(); i++) {
                values.put(HEADER.get(i), RCsvSupport.NA);
            }
        }

        public Builder put(final String column, final String value) {
            if (!HEADER.contains(column)) {
                throw new IllegalArgumentException("Unknown processing-run column: " + column);
            }
            values.put(column, value == null || value.isEmpty() ? RCsvSupport.NA : value);
            return this;
        }

        public Builder putInt(final String column, final int value) {
            return put(column, Integer.toString(value));
        }

        public Builder putLong(final String column, final long value) {
            return put(column, Long.toString(value));
        }

        public Builder putNumber(final String column, final double value) {
            return put(column, RCsvSupport.numberOrNa(value));
        }

        public Builder schemaVersion(final int version) {
            return putInt("schema_version", version);
        }

        public Builder runId(final String runId) {
            return put("run_id", runId);
        }

        public Builder timestamp(final String timestamp) {
            return put("timestamp", timestamp);
        }

        public Builder stage(final ProductionSchemas.ProcessingStage stage) {
            return put("stage", stage == null ? RCsvSupport.NA : stage.name());
        }

        public Builder status(final ProductionSchemas.RunStatus status) {
            return put("status", status == null ? RCsvSupport.NA : status.name());
        }

        public Builder sampleId(final String sampleId) {
            return put("sample_id", sampleId);
        }

        public Builder segmentId(final String segmentId) {
            return put("segment_id", segmentId);
        }

        public Builder inputFilename(final String filename) {
            return put("input_filename", filename);
        }

        public Builder outputFilename(final String filename) {
            return put("output_filename", filename);
        }

        public Builder pluginVersion(final String version) {
            return put("plugin_version", version);
        }

        public Builder gitCommit(final String commit) {
            return put("git_commit", commit);
        }

        public Builder classifierFilename(final String filename) {
            return put("classifier_filename", filename);
        }

        public Builder coreTileWidthPx(final Integer value) {
            return put("core_tile_width_px", value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder coreTileHeightPx(final Integer value) {
            return put("core_tile_height_px", value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder threadsPerTile(final Integer value) {
            return put("threads_per_tile", value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder maskDownsampleFactor(final Integer value) {
            return put("mask_downsample_factor",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder maskExpansionMarginPx(final Integer value) {
            return put("mask_expansion_margin_px",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder minimumLumenAreaPx(final Integer value) {
            return put("minimum_lumen_area_px",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder totalComponents(final Integer value) {
            return put("total_components",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder eligibleComponents(final Integer value) {
            return put("eligible_components",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder reconstructedComponents(final Integer value) {
            return put("reconstructed_components",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder changedPixels(final Integer value) {
            return put("changed_pixels",
                    value == null ? RCsvSupport.NA : Integer.toString(value));
        }

        public Builder processingTimeMs(final Double value) {
            return put("processing_time_ms", RCsvSupport.numberOrNa(value));
        }

        public Builder message(final String message) {
            return put("message", message == null || message.isEmpty() ? RCsvSupport.NA : message);
        }

        public ProcessingRunRecord build() {
            final ProcessingRunRecord record = new ProcessingRunRecord(
                    new LinkedHashMap<String, String>(values));
            record.validateComplete();
            return record;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "ProcessingRunRecord%s", values);
    }
}
