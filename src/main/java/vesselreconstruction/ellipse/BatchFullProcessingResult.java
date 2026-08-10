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
 * Atomic one-image Phase-3 full-processing result properties.
 */
public final class BatchFullProcessingResult {

    public static final String KEY_SCHEMA = "result.schema_version";
    public static final String KEY_STATUS = "status";
    public static final String KEY_REASON = "reason_code";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_IMAGE_ID = "image_id";
    public static final String KEY_MACHINE_ID = "machine_id";
    public static final String KEY_SOURCE_FILENAME = "source_filename";
    public static final String KEY_SOURCE_SHA256 = "source_sha256";
    public static final String KEY_CONFIG_SHA256 = "configuration_sha256";
    public static final String KEY_CLASSIFIER_FILENAME = "classifier_filename";
    public static final String KEY_CLASSIFIER_SHA256 = "classifier_sha256";
    public static final String KEY_ATTEMPT_NUMBER = "attempt_number";
    public static final String KEY_TILE_PROFILE_INDEX = "tile_profile_index";
    public static final String KEY_TILE_WIDTH_PX = "tile_width_px";
    public static final String KEY_TILE_HEIGHT_PX = "tile_height_px";
    public static final String KEY_PRECHECK_VERIFIED = "precheck_verified";
    public static final String KEY_MASK_REPRODUCED = "mask_reproduced";
    public static final String KEY_SCALE_BAR_REPRODUCED = "scale_bar_reproduced";
    public static final String KEY_CLASSIFICATION_COMPLETED = "classification_completed";
    public static final String KEY_RECONSTRUCTION_COMPLETED = "reconstruction_completed";
    public static final String KEY_REQUIRED_OUTPUTS_VALIDATED = "required_outputs_validated";
    public static final String KEY_SOURCE_UNCHANGED = "source_unchanged";
    public static final String KEY_OUT_OF_MEMORY_DETECTED = "out_of_memory_detected";
    public static final String KEY_CLASSIFICATION_RUN_ID = "classification_run_id";
    public static final String KEY_RECONSTRUCTION_RUN_ID = "reconstruction_run_id";
    public static final String KEY_DATASET_ROOT = "dataset_root";
    public static final String KEY_ORIGINAL_RGB_PATH = "original_rgb_path";
    public static final String KEY_APPROVED_MASK_PATH = "approved_mask_path";
    public static final String KEY_MASKED_RGB_PATH = "masked_rgb_path";
    public static final String KEY_MASKED_CLASSIFIED_PATH = "masked_classified_path";
    public static final String KEY_RECONSTRUCTED_CLASSIFIED_PATH = "reconstructed_classified_path";
    public static final String KEY_LUMEN_MEASUREMENTS_PATH = "lumen_measurements_path";
    public static final String KEY_ARTIFACT_DIAGNOSTICS_PATH = "artifact_diagnostics_path";
    public static final String KEY_IMAGE_MANIFEST_PATH = "image_manifest_path";
    public static final String KEY_SEGMENT_MANIFEST_PATH = "segment_manifest_path";
    public static final String KEY_PROCESSING_RUNS_PATH = "processing_runs_path";
    public static final String KEY_TOTAL_COMPONENTS = "total_components";
    public static final String KEY_ELIGIBLE_COMPONENTS = "eligible_components";
    public static final String KEY_RECONSTRUCTED_COMPONENTS = "reconstructed_components";
    public static final String KEY_CHANGED_PIXELS = "changed_pixels";
    public static final String KEY_CSUM_BEFORE = "source_checksum_before";
    public static final String KEY_CSUM_AFTER = "source_checksum_after";
    public static final String KEY_TIME_MS = "processing_time_ms";

    // Combined (attempt-level, two-process architecture) fields. Stage-only
    // child results leave these NA; only the parent CLI's combined
    // result.properties fills every one of them.
    public static final String KEY_FAILED_STAGE = "failed_stage";
    public static final String KEY_CLASSIFICATION_STAGE_REUSED = "classification_stage_reused";
    public static final String KEY_CLASSIFICATION_PROCESS_LAUNCHED =
            "classification_process_launched";
    public static final String KEY_CLASSIFICATION_EXIT_CODE = "classification_exit_code";
    public static final String KEY_CLASSIFICATION_OUTPUTS_VALIDATED =
            "classification_outputs_validated";
    public static final String KEY_CLASSIFICATION_STAGE_RESULT_PATH =
            "classification_stage_result_path";
    public static final String KEY_RECONSTRUCTION_PROCESS_LAUNCHED =
            "reconstruction_process_launched";
    public static final String KEY_RECONSTRUCTION_EXIT_CODE = "reconstruction_exit_code";
    public static final String KEY_RECONSTRUCTION_OUTPUTS_VALIDATED =
            "reconstruction_outputs_validated";
    public static final String KEY_RECONSTRUCTION_STAGE_RESULT_PATH =
            "reconstruction_stage_result_path";

    public static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA, KEY_STATUS, KEY_REASON, KEY_MESSAGE, KEY_IMAGE_ID, KEY_MACHINE_ID,
                    KEY_SOURCE_FILENAME, KEY_SOURCE_SHA256, KEY_CONFIG_SHA256,
                    KEY_CLASSIFIER_FILENAME, KEY_CLASSIFIER_SHA256,
                    KEY_ATTEMPT_NUMBER, KEY_TILE_PROFILE_INDEX, KEY_TILE_WIDTH_PX, KEY_TILE_HEIGHT_PX,
                    KEY_PRECHECK_VERIFIED, KEY_MASK_REPRODUCED, KEY_SCALE_BAR_REPRODUCED,
                    KEY_CLASSIFICATION_COMPLETED, KEY_RECONSTRUCTION_COMPLETED,
                    KEY_REQUIRED_OUTPUTS_VALIDATED, KEY_SOURCE_UNCHANGED, KEY_OUT_OF_MEMORY_DETECTED,
                    KEY_CLASSIFICATION_RUN_ID, KEY_RECONSTRUCTION_RUN_ID,
                    KEY_DATASET_ROOT, KEY_ORIGINAL_RGB_PATH, KEY_APPROVED_MASK_PATH, KEY_MASKED_RGB_PATH,
                    KEY_MASKED_CLASSIFIED_PATH, KEY_RECONSTRUCTED_CLASSIFIED_PATH,
                    KEY_LUMEN_MEASUREMENTS_PATH, KEY_ARTIFACT_DIAGNOSTICS_PATH,
                    KEY_IMAGE_MANIFEST_PATH, KEY_SEGMENT_MANIFEST_PATH, KEY_PROCESSING_RUNS_PATH,
                    KEY_TOTAL_COMPONENTS, KEY_ELIGIBLE_COMPONENTS, KEY_RECONSTRUCTED_COMPONENTS,
                    KEY_CHANGED_PIXELS, KEY_CSUM_BEFORE, KEY_CSUM_AFTER, KEY_TIME_MS,
                    KEY_FAILED_STAGE, KEY_CLASSIFICATION_STAGE_REUSED,
                    KEY_CLASSIFICATION_PROCESS_LAUNCHED, KEY_CLASSIFICATION_EXIT_CODE,
                    KEY_CLASSIFICATION_OUTPUTS_VALIDATED, KEY_CLASSIFICATION_STAGE_RESULT_PATH,
                    KEY_RECONSTRUCTION_PROCESS_LAUNCHED, KEY_RECONSTRUCTION_EXIT_CODE,
                    KEY_RECONSTRUCTION_OUTPUTS_VALIDATED, KEY_RECONSTRUCTION_STAGE_RESULT_PATH)));

    private final Map<String, String> values;

    private BatchFullProcessingResult(final Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<String, String>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BatchFullProcessingResult load(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return fromMap(BatchConfiguration.parseStrictProperties(lines));
    }

    public static BatchFullProcessingResult fromMap(final Map<String, String> raw) {
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
        BatchFullProcessingStatus.parse(normalized.get(KEY_STATUS));
        return new BatchFullProcessingResult(normalized);
    }

    public void write(final Path target) throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        Sha256Files.writeBytesAtomically(target, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public BatchFullProcessingStatus status() {
        return BatchFullProcessingStatus.parse(values.get(KEY_STATUS));
    }

    public String get(final String key) {
        return values.get(key);
    }

    public Map<String, String> asMap() {
        return values;
    }

    public Map<String, String> toMap() {
        return values;
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

    static String boolOrNa(final Boolean value) {
        return value == null ? RCsvSupport.NA : (value.booleanValue() ? RCsvSupport.TRUE : RCsvSupport.FALSE);
    }

    public static final class Builder {
        private final Map<String, String> map = new TreeMap<String, String>();

        public Builder() {
            for (final String key : REQUIRED_KEYS) {
                map.put(key, RCsvSupport.NA);
            }
            map.put(KEY_SCHEMA, "1");
        }

        public Builder status(final BatchFullProcessingStatus status) {
            map.put(KEY_STATUS, status.name());
            return this;
        }

        public Builder reasonCode(final String reason) {
            map.put(KEY_REASON, textOrNa(reason));
            return this;
        }

        public Builder message(final String message) {
            map.put(KEY_MESSAGE, textOrNa(message));
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

        public Builder sourceSha256(final String sha) {
            map.put(KEY_SOURCE_SHA256, textOrNa(sha));
            return this;
        }

        public Builder configurationSha256(final String sha) {
            map.put(KEY_CONFIG_SHA256, textOrNa(sha));
            return this;
        }

        public Builder classifierFilename(final String filename) {
            map.put(KEY_CLASSIFIER_FILENAME, textOrNa(filename));
            return this;
        }

        public Builder classifierSha256(final String sha) {
            map.put(KEY_CLASSIFIER_SHA256, textOrNa(sha));
            return this;
        }

        public Builder attemptNumber(final Integer attemptNumber) {
            map.put(KEY_ATTEMPT_NUMBER, intOrNa(attemptNumber));
            return this;
        }

        public Builder tileProfile(final Integer index, final Integer widthPx, final Integer heightPx) {
            map.put(KEY_TILE_PROFILE_INDEX, intOrNa(index));
            map.put(KEY_TILE_WIDTH_PX, intOrNa(widthPx));
            map.put(KEY_TILE_HEIGHT_PX, intOrNa(heightPx));
            return this;
        }

        public Builder datasetRoot(final String path) {
            map.put(KEY_DATASET_ROOT, textOrNa(path));
            return this;
        }

        public Builder precheckVerified(final Boolean value) {
            map.put(KEY_PRECHECK_VERIFIED, boolOrNa(value));
            return this;
        }

        public Builder maskReproduced(final Boolean value) {
            map.put(KEY_MASK_REPRODUCED, boolOrNa(value));
            return this;
        }

        public Builder scaleBarReproduced(final Boolean value) {
            map.put(KEY_SCALE_BAR_REPRODUCED, boolOrNa(value));
            return this;
        }

        public Builder classificationCompleted(final Boolean value) {
            map.put(KEY_CLASSIFICATION_COMPLETED, boolOrNa(value));
            return this;
        }

        public Builder reconstructionCompleted(final Boolean value) {
            map.put(KEY_RECONSTRUCTION_COMPLETED, boolOrNa(value));
            return this;
        }

        public Builder requiredOutputsValidated(final Boolean value) {
            map.put(KEY_REQUIRED_OUTPUTS_VALIDATED, boolOrNa(value));
            return this;
        }

        public Builder sourceUnchanged(final Boolean value) {
            map.put(KEY_SOURCE_UNCHANGED, boolOrNa(value));
            return this;
        }

        public Builder outOfMemoryDetected(final Boolean value) {
            map.put(KEY_OUT_OF_MEMORY_DETECTED, boolOrNa(value));
            return this;
        }

        public Builder classificationRunId(final String runId) {
            map.put(KEY_CLASSIFICATION_RUN_ID, textOrNa(runId));
            return this;
        }

        public Builder reconstructionRunId(final String runId) {
            map.put(KEY_RECONSTRUCTION_RUN_ID, textOrNa(runId));
            return this;
        }

        public Builder originalRgbPath(final String relativePath) {
            map.put(KEY_ORIGINAL_RGB_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder approvedMaskPath(final String relativePath) {
            map.put(KEY_APPROVED_MASK_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder maskedRgbPath(final String relativePath) {
            map.put(KEY_MASKED_RGB_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder maskedClassifiedPath(final String relativePath) {
            map.put(KEY_MASKED_CLASSIFIED_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder reconstructedClassifiedPath(final String relativePath) {
            map.put(KEY_RECONSTRUCTED_CLASSIFIED_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder lumenMeasurementsPath(final String relativePath) {
            map.put(KEY_LUMEN_MEASUREMENTS_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder artifactDiagnosticsPath(final String relativePath) {
            map.put(KEY_ARTIFACT_DIAGNOSTICS_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder imageManifestPath(final String relativePath) {
            map.put(KEY_IMAGE_MANIFEST_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder segmentManifestPath(final String relativePath) {
            map.put(KEY_SEGMENT_MANIFEST_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder processingRunsPath(final String relativePath) {
            map.put(KEY_PROCESSING_RUNS_PATH, textOrNa(relativePath));
            return this;
        }

        public Builder components(
                final Integer totalComponents,
                final Integer eligibleComponents,
                final Integer reconstructedComponents,
                final Integer changedPixels) {
            map.put(KEY_TOTAL_COMPONENTS, intOrNa(totalComponents));
            map.put(KEY_ELIGIBLE_COMPONENTS, intOrNa(eligibleComponents));
            map.put(KEY_RECONSTRUCTED_COMPONENTS, intOrNa(reconstructedComponents));
            map.put(KEY_CHANGED_PIXELS, intOrNa(changedPixels));
            return this;
        }

        public Builder sourceChecksums(final Integer before, final Integer after) {
            map.put(KEY_CSUM_BEFORE, intOrNa(before));
            map.put(KEY_CSUM_AFTER, intOrNa(after));
            return this;
        }

        public Builder processingTimeMs(final long ms) {
            map.put(KEY_TIME_MS, Long.toString(ms));
            return this;
        }

        public Builder failedStage(final String stage) {
            map.put(KEY_FAILED_STAGE, textOrNa(stage));
            return this;
        }

        public Builder classificationStageReused(final Boolean value) {
            map.put(KEY_CLASSIFICATION_STAGE_REUSED, boolOrNa(value));
            return this;
        }

        public Builder classificationProcessLaunched(final Boolean value) {
            map.put(KEY_CLASSIFICATION_PROCESS_LAUNCHED, boolOrNa(value));
            return this;
        }

        public Builder classificationExitCode(final Integer exitCode) {
            map.put(KEY_CLASSIFICATION_EXIT_CODE, intOrNa(exitCode));
            return this;
        }

        public Builder classificationOutputsValidated(final Boolean value) {
            map.put(KEY_CLASSIFICATION_OUTPUTS_VALIDATED, boolOrNa(value));
            return this;
        }

        public Builder classificationStageResultPath(final String path) {
            map.put(KEY_CLASSIFICATION_STAGE_RESULT_PATH, textOrNa(path));
            return this;
        }

        public Builder reconstructionProcessLaunched(final Boolean value) {
            map.put(KEY_RECONSTRUCTION_PROCESS_LAUNCHED, boolOrNa(value));
            return this;
        }

        public Builder reconstructionExitCode(final Integer exitCode) {
            map.put(KEY_RECONSTRUCTION_EXIT_CODE, intOrNa(exitCode));
            return this;
        }

        public Builder reconstructionOutputsValidated(final Boolean value) {
            map.put(KEY_RECONSTRUCTION_OUTPUTS_VALIDATED, boolOrNa(value));
            return this;
        }

        public Builder reconstructionStageResultPath(final String path) {
            map.put(KEY_RECONSTRUCTION_STAGE_RESULT_PATH, textOrNa(path));
            return this;
        }

        /** Copies dataset-relative artifact/manifest fields from a stage child result, skipping NA values. */
        public Builder mergeStageArtifacts(final BatchFullProcessingResult child) {
            if (child == null) {
                return this;
            }
            copyIfPresent(child, KEY_ORIGINAL_RGB_PATH);
            copyIfPresent(child, KEY_APPROVED_MASK_PATH);
            copyIfPresent(child, KEY_MASKED_RGB_PATH);
            copyIfPresent(child, KEY_MASKED_CLASSIFIED_PATH);
            copyIfPresent(child, KEY_RECONSTRUCTED_CLASSIFIED_PATH);
            copyIfPresent(child, KEY_LUMEN_MEASUREMENTS_PATH);
            copyIfPresent(child, KEY_ARTIFACT_DIAGNOSTICS_PATH);
            copyIfPresent(child, KEY_IMAGE_MANIFEST_PATH);
            copyIfPresent(child, KEY_SEGMENT_MANIFEST_PATH);
            copyIfPresent(child, KEY_PROCESSING_RUNS_PATH);
            copyIfPresent(child, KEY_TOTAL_COMPONENTS);
            copyIfPresent(child, KEY_ELIGIBLE_COMPONENTS);
            copyIfPresent(child, KEY_RECONSTRUCTED_COMPONENTS);
            copyIfPresent(child, KEY_CHANGED_PIXELS);
            copyIfPresent(child, KEY_DATASET_ROOT);
            return this;
        }

        private void copyIfPresent(final BatchFullProcessingResult child, final String key) {
            final String value = child.get(key);
            if (value != null && !RCsvSupport.NA.equals(value)) {
                map.put(key, value);
            }
        }

        public BatchFullProcessingResult build() {
            return fromMap(map);
        }
    }
}
