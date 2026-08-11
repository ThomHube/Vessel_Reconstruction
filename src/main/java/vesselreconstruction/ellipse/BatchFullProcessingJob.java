package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Strict one-image Phase-3 full-processing job properties file.
 *
 * <p>Stage-aware jobs require {@code processing_stage=CLASSIFICATION} or
 * {@code processing_stage=RECONSTRUCTION} and are loaded via {@link #load}
 * / {@link #fromMap}.</p>
 *
 * <p><strong>Temporary legacy path:</strong> {@link #createLegacyCombined},
 * {@link #loadLegacyCombined}, and {@link #fromMapLegacyCombined} support the
 * current combined Phase-3 execution that does not yet write
 * {@code processing_stage}. Do not use the legacy factories for new
 * stage-specific production jobs; they exist only until orchestration launches
 * each child stage in its own Fiji process.</p>
 */
public final class BatchFullProcessingJob {

    public static final String KEY_SCHEMA = "job.schema_version";
    public static final String KEY_IMAGE_ID = "image_id";
    public static final String KEY_MACHINE_ID = "machine_id";
    public static final String KEY_SOURCE_PATH = "source_path";
    public static final String KEY_SOURCE_RELATIVE = "source_relative_path";
    public static final String KEY_SOURCE_SHA256 = "source_sha256";
    public static final String KEY_CONFIG_PATH = "configuration_path";
    public static final String KEY_CONFIG_SHA256 = "configuration_sha256";
    public static final String KEY_CLASSIFIER_PATH = "classifier_path";
    public static final String KEY_CLASSIFIER_SHA256 = "classifier_sha256";
    public static final String KEY_BATCH_ROOT = "batch_root";
    public static final String KEY_DATASET_ROOT = "dataset_root";
    public static final String KEY_PRECHECK_CSV_PATH = "precheck_csv_path";
    public static final String KEY_PRECHECK_RESULT_PATH = "precheck_result_path";
    public static final String KEY_TILE_PROFILE_INDEX = "tile_profile_index";
    public static final String KEY_TILE_WIDTH_PX = "tile_width_px";
    public static final String KEY_TILE_HEIGHT_PX = "tile_height_px";
    public static final String KEY_ATTEMPT_NUMBER = "attempt_number";
    public static final String KEY_RESULT_PATH = "result_path";
    public static final String KEY_STDOUT_PATH = "stdout_path";
    public static final String KEY_STDERR_PATH = "stderr_path";
    public static final String KEY_PROCESSING_STAGE = "processing_stage";

    /** Keys required by stage-aware jobs (includes {@link #KEY_PROCESSING_STAGE}). */
    public static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA,
                    KEY_IMAGE_ID,
                    KEY_MACHINE_ID,
                    KEY_SOURCE_PATH,
                    KEY_SOURCE_RELATIVE,
                    KEY_SOURCE_SHA256,
                    KEY_CONFIG_PATH,
                    KEY_CONFIG_SHA256,
                    KEY_CLASSIFIER_PATH,
                    KEY_CLASSIFIER_SHA256,
                    KEY_BATCH_ROOT,
                    KEY_DATASET_ROOT,
                    KEY_PRECHECK_CSV_PATH,
                    KEY_PRECHECK_RESULT_PATH,
                    KEY_TILE_PROFILE_INDEX,
                    KEY_TILE_WIDTH_PX,
                    KEY_TILE_HEIGHT_PX,
                    KEY_ATTEMPT_NUMBER,
                    KEY_RESULT_PATH,
                    KEY_STDOUT_PATH,
                    KEY_STDERR_PATH,
                    KEY_PROCESSING_STAGE)));

    /**
     * Keys accepted by the temporary legacy combined-path loader. Does not
     * include {@link #KEY_PROCESSING_STAGE}.
     */
    public static final Set<String> LEGACY_COMBINED_REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA,
                    KEY_IMAGE_ID,
                    KEY_MACHINE_ID,
                    KEY_SOURCE_PATH,
                    KEY_SOURCE_RELATIVE,
                    KEY_SOURCE_SHA256,
                    KEY_CONFIG_PATH,
                    KEY_CONFIG_SHA256,
                    KEY_CLASSIFIER_PATH,
                    KEY_CLASSIFIER_SHA256,
                    KEY_BATCH_ROOT,
                    KEY_DATASET_ROOT,
                    KEY_PRECHECK_CSV_PATH,
                    KEY_PRECHECK_RESULT_PATH,
                    KEY_TILE_PROFILE_INDEX,
                    KEY_TILE_WIDTH_PX,
                    KEY_TILE_HEIGHT_PX,
                    KEY_ATTEMPT_NUMBER,
                    KEY_RESULT_PATH,
                    KEY_STDOUT_PATH,
                    KEY_STDERR_PATH)));

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^machine_\\d+$");

    private final int schemaVersion;
    private final CoreSegmentId imageId;
    private final String machineId;
    private final Path sourcePath;
    private final String sourceRelativePath;
    private final String sourceSha256;
    private final Path configurationPath;
    private final String configurationSha256;
    private final Path classifierPath;
    private final String classifierSha256;
    private final int tileProfileIndex;
    private final BatchTileProfile tileProfile;
    private final int attemptNumber;
    private final Path batchRoot;
    private final Path datasetRoot;
    private final Path precheckResultPath;
    private final Path precheckCsvPath;
    private final Path resultPath;
    private final Path stdoutPath;
    private final Path stderrPath;
    /** Non-null for stage-aware jobs; null only for temporary legacy combined jobs. */
    private final BatchFullProcessingStage processingStage;

    /**
     * Stage-aware job. {@code processingStage} is required and is serialized as
     * {@code processing_stage}.
     */
    public BatchFullProcessingJob(
            final int schemaVersion,
            final CoreSegmentId imageId,
            final String machineId,
            final Path sourcePath,
            final String sourceRelativePath,
            final String sourceSha256,
            final Path configurationPath,
            final String configurationSha256,
            final Path classifierPath,
            final String classifierSha256,
            final int tileProfileIndex,
            final BatchTileProfile tileProfile,
            final int attemptNumber,
            final Path batchRoot,
            final Path datasetRoot,
            final Path precheckResultPath,
            final Path precheckCsvPath,
            final Path resultPath,
            final Path stdoutPath,
            final Path stderrPath,
            final BatchFullProcessingStage processingStage) {
        this(schemaVersion, imageId, machineId, sourcePath, sourceRelativePath, sourceSha256,
                configurationPath, configurationSha256, classifierPath, classifierSha256,
                tileProfileIndex, tileProfile, attemptNumber, batchRoot, datasetRoot,
                precheckResultPath, precheckCsvPath, resultPath, stdoutPath, stderrPath,
                processingStage, true);
    }

    /**
     * Temporary factory for the current combined Phase-3 path that does not yet
     * set {@code processing_stage}. Prefer the stage-aware constructor for new
     * child-stage jobs.
     */
    public static BatchFullProcessingJob createLegacyCombined(
            final int schemaVersion,
            final CoreSegmentId imageId,
            final String machineId,
            final Path sourcePath,
            final String sourceRelativePath,
            final String sourceSha256,
            final Path configurationPath,
            final String configurationSha256,
            final Path classifierPath,
            final String classifierSha256,
            final int tileProfileIndex,
            final BatchTileProfile tileProfile,
            final int attemptNumber,
            final Path batchRoot,
            final Path datasetRoot,
            final Path precheckResultPath,
            final Path precheckCsvPath,
            final Path resultPath,
            final Path stdoutPath,
            final Path stderrPath) {
        return new BatchFullProcessingJob(
                schemaVersion, imageId, machineId, sourcePath, sourceRelativePath, sourceSha256,
                configurationPath, configurationSha256, classifierPath, classifierSha256,
                tileProfileIndex, tileProfile, attemptNumber, batchRoot, datasetRoot,
                precheckResultPath, precheckCsvPath, resultPath, stdoutPath, stderrPath,
                null, false);
    }

    private BatchFullProcessingJob(
            final int schemaVersion,
            final CoreSegmentId imageId,
            final String machineId,
            final Path sourcePath,
            final String sourceRelativePath,
            final String sourceSha256,
            final Path configurationPath,
            final String configurationSha256,
            final Path classifierPath,
            final String classifierSha256,
            final int tileProfileIndex,
            final BatchTileProfile tileProfile,
            final int attemptNumber,
            final Path batchRoot,
            final Path datasetRoot,
            final Path precheckResultPath,
            final Path precheckCsvPath,
            final Path resultPath,
            final Path stdoutPath,
            final Path stderrPath,
            final BatchFullProcessingStage processingStage,
            final boolean requireStage) {
        if (machineId == null || !MACHINE_ID_PATTERN.matcher(machineId.trim()).matches()) {
            throw new IllegalArgumentException(
                    "machineId must match machine_\\d+: " + machineId);
        }
        if (tileProfile == null) {
            throw new IllegalArgumentException("tileProfile must not be null");
        }
        if (tileProfileIndex < 0) {
            throw new IllegalArgumentException("tileProfileIndex must be >= 0: " + tileProfileIndex);
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1: " + attemptNumber);
        }
        if (requireStage && processingStage == null) {
            throw new IllegalArgumentException("processingStage must not be null");
        }
        this.schemaVersion = schemaVersion;
        this.imageId = imageId;
        this.machineId = machineId.trim();
        this.sourcePath = sourcePath;
        this.sourceRelativePath = sourceRelativePath;
        this.sourceSha256 = sourceSha256;
        this.configurationPath = configurationPath;
        this.configurationSha256 = configurationSha256;
        this.classifierPath = classifierPath;
        this.classifierSha256 = classifierSha256;
        this.tileProfileIndex = tileProfileIndex;
        this.tileProfile = tileProfile;
        this.attemptNumber = attemptNumber;
        this.batchRoot = batchRoot;
        this.datasetRoot = datasetRoot;
        this.precheckResultPath = precheckResultPath;
        this.precheckCsvPath = precheckCsvPath;
        this.resultPath = resultPath;
        this.stdoutPath = stdoutPath;
        this.stderrPath = stderrPath;
        this.processingStage = processingStage;
    }

    /** Stage-aware loader: requires {@code processing_stage}. */
    public static BatchFullProcessingJob load(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return fromMap(BatchConfiguration.parseStrictProperties(lines));
    }

    /**
     * Temporary legacy loader for combined-path job files that omit
     * {@code processing_stage}. Prefer {@link #load} for stage-specific jobs.
     */
    public static BatchFullProcessingJob loadLegacyCombined(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return fromMapLegacyCombined(BatchConfiguration.parseStrictProperties(lines));
    }

    /** Stage-aware map loader: requires {@code processing_stage}. */
    public static BatchFullProcessingJob fromMap(final Map<String, String> values) {
        return fromMapInternal(values, true);
    }

    /**
     * Temporary legacy map loader for the combined path. Rejects maps that
     * already contain {@code processing_stage} so callers cannot silently mix
     * paths; use {@link #fromMap} for stage-aware jobs.
     */
    public static BatchFullProcessingJob fromMapLegacyCombined(final Map<String, String> values) {
        if (values != null && values.containsKey(KEY_PROCESSING_STAGE)) {
            throw new IllegalArgumentException(
                    "Legacy combined loader rejects " + KEY_PROCESSING_STAGE
                            + "; use BatchFullProcessingJob.fromMap for stage-aware jobs");
        }
        return fromMapInternal(values, false);
    }

    private static BatchFullProcessingJob fromMapInternal(
            final Map<String, String> values, final boolean stageAware) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        final Set<String> allowed = stageAware ? REQUIRED_KEYS : LEGACY_COMBINED_REQUIRED_KEYS;
        for (final String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown job key: " + key);
            }
        }
        for (final String required : allowed) {
            if (!values.containsKey(required)
                    || values.get(required) == null
                    || values.get(required).trim().isEmpty()) {
                throw new IllegalArgumentException("Missing job key: " + required);
            }
        }
        final int schema = Integer.parseInt(values.get(KEY_SCHEMA).trim());
        if (schema != 1) {
            throw new IllegalArgumentException(KEY_SCHEMA + " must be 1");
        }
        final String machineId = values.get(KEY_MACHINE_ID).trim();
        if (!MACHINE_ID_PATTERN.matcher(machineId).matches()) {
            throw new IllegalArgumentException(
                    KEY_MACHINE_ID + " must match machine_\\d+: " + machineId);
        }
        final CoreSegmentId imageId = CoreSegmentId.parse(values.get(KEY_IMAGE_ID));

        final Path batchRoot = Paths.get(values.get(KEY_BATCH_ROOT)).toAbsolutePath().normalize();
        final Path datasetRoot = Paths.get(values.get(KEY_DATASET_ROOT)).toAbsolutePath().normalize();

        final Path sourcePath = Paths.get(values.get(KEY_SOURCE_PATH)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("source file must exist: " + sourcePath);
        }
        final String sourceSha = requireSha256(values.get(KEY_SOURCE_SHA256), KEY_SOURCE_SHA256);
        final String configSha = requireSha256(values.get(KEY_CONFIG_SHA256), KEY_CONFIG_SHA256);

        final Path classifierPath = Paths.get(values.get(KEY_CLASSIFIER_PATH))
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(classifierPath)) {
            throw new IllegalArgumentException("classifier file must exist: " + classifierPath);
        }
        final String classifierSha = requireSha256(
                values.get(KEY_CLASSIFIER_SHA256), KEY_CLASSIFIER_SHA256);

        final int tileProfileIndex = parseNonNegativeInt(
                values.get(KEY_TILE_PROFILE_INDEX), KEY_TILE_PROFILE_INDEX);
        final int tileWidth = parsePositiveInt(values.get(KEY_TILE_WIDTH_PX), KEY_TILE_WIDTH_PX);
        final int tileHeight = parsePositiveInt(values.get(KEY_TILE_HEIGHT_PX), KEY_TILE_HEIGHT_PX);
        final BatchTileProfile tileProfile;
        try {
            tileProfile = new BatchTileProfile(tileWidth, tileHeight);
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "invalid tile profile dimensions: " + ex.getMessage(), ex);
        }
        final int attemptNumber = parsePositiveInt(
                values.get(KEY_ATTEMPT_NUMBER), KEY_ATTEMPT_NUMBER);

        final Path precheckResultPath = requireUnder(batchRoot, values.get(KEY_PRECHECK_RESULT_PATH));
        final Path precheckCsvPath = requireUnder(
                batchRoot, values.get(KEY_PRECHECK_CSV_PATH));
        final Path resultPath = requireUnder(batchRoot, values.get(KEY_RESULT_PATH));
        final Path stdoutPath = requireUnder(batchRoot, values.get(KEY_STDOUT_PATH));
        final Path stderrPath = requireUnder(batchRoot, values.get(KEY_STDERR_PATH));
        final BatchFullProcessingStage processingStage = stageAware
                ? BatchFullProcessingStage.parse(values.get(KEY_PROCESSING_STAGE))
                : null;

        return new BatchFullProcessingJob(
                schema,
                imageId,
                machineId,
                sourcePath,
                values.get(KEY_SOURCE_RELATIVE).trim().replace('\\', '/'),
                sourceSha,
                Paths.get(values.get(KEY_CONFIG_PATH)).toAbsolutePath().normalize(),
                configSha,
                classifierPath,
                classifierSha,
                tileProfileIndex,
                tileProfile,
                attemptNumber,
                batchRoot,
                datasetRoot,
                precheckResultPath,
                precheckCsvPath,
                resultPath,
                stdoutPath,
                stderrPath,
                processingStage,
                stageAware);
    }

    public void write(final Path target) throws IOException {
        final Map<String, String> map = new TreeMap<String, String>();
        map.put(KEY_SCHEMA, Integer.toString(schemaVersion));
        map.put(KEY_IMAGE_ID, imageId.imageId());
        map.put(KEY_MACHINE_ID, machineId);
        map.put(KEY_SOURCE_PATH, sourcePath.toString());
        map.put(KEY_SOURCE_RELATIVE, sourceRelativePath);
        map.put(KEY_SOURCE_SHA256, sourceSha256);
        map.put(KEY_CONFIG_PATH, configurationPath.toString());
        map.put(KEY_CONFIG_SHA256, configurationSha256);
        map.put(KEY_CLASSIFIER_PATH, classifierPath.toString());
        map.put(KEY_CLASSIFIER_SHA256, classifierSha256);
        map.put(KEY_BATCH_ROOT, batchRoot.toString());
        map.put(KEY_DATASET_ROOT, datasetRoot.toString());
        map.put(KEY_PRECHECK_CSV_PATH, precheckCsvPath.toString());
        map.put(KEY_PRECHECK_RESULT_PATH, precheckResultPath.toString());
        map.put(KEY_TILE_PROFILE_INDEX, Integer.toString(tileProfileIndex));
        map.put(KEY_TILE_WIDTH_PX, Integer.toString(tileProfile.widthPx()));
        map.put(KEY_TILE_HEIGHT_PX, Integer.toString(tileProfile.heightPx()));
        map.put(KEY_ATTEMPT_NUMBER, Integer.toString(attemptNumber));
        map.put(KEY_RESULT_PATH, resultPath.toString());
        map.put(KEY_STDOUT_PATH, stdoutPath.toString());
        map.put(KEY_STDERR_PATH, stderrPath.toString());
        if (processingStage != null) {
            map.put(KEY_PROCESSING_STAGE, processingStage.name());
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        Sha256Files.writeBytesAtomically(target, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String requireSha256(final String raw, final String key) {
        final String sha = raw.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_HEX.matcher(sha).matches()) {
            throw new IllegalArgumentException("invalid " + key + ": " + raw);
        }
        return sha;
    }

    private static int parsePositiveInt(final String raw, final String key) {
        final int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, ex);
        }
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive: " + value);
        }
        return value;
    }

    private static int parseNonNegativeInt(final String raw, final String key) {
        final int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, ex);
        }
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be >= 0: " + value);
        }
        return value;
    }

    private static Path requireUnder(final Path batchRoot, final String raw) {
        final Path path = Paths.get(raw.trim()).toAbsolutePath().normalize();
        if (!path.startsWith(batchRoot)) {
            throw new IllegalArgumentException(
                    "output path escapes batch root: " + path + " not under " + batchRoot);
        }
        return path;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public CoreSegmentId imageId() {
        return imageId;
    }

    public String machineId() {
        return machineId;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public String sourceRelativePath() {
        return sourceRelativePath;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public Path configurationPath() {
        return configurationPath;
    }

    public String configurationSha256() {
        return configurationSha256;
    }

    public Path classifierPath() {
        return classifierPath;
    }

    public String classifierSha256() {
        return classifierSha256;
    }

    public int tileProfileIndex() {
        return tileProfileIndex;
    }

    public BatchTileProfile tileProfile() {
        return tileProfile;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Path batchRoot() {
        return batchRoot;
    }

    public Path datasetRoot() {
        return datasetRoot;
    }

    public Path precheckResultPath() {
        return precheckResultPath;
    }

    public Path precheckCsvPath() {
        return precheckCsvPath;
    }

    public Path resultPath() {
        return resultPath;
    }

    public Path stdoutPath() {
        return stdoutPath;
    }

    public Path stderrPath() {
        return stderrPath;
    }

    /**
     * Stage for stage-aware jobs. Returns {@code null} only for temporary
     * legacy combined jobs created via {@link #createLegacyCombined} /
     * {@link #loadLegacyCombined}.
     */
    public BatchFullProcessingStage processingStage() {
        return processingStage;
    }
}
