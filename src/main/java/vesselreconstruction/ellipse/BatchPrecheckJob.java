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
 * One-image Phase-2 precheck job properties file.
 */
public final class BatchPrecheckJob {

    public static final String KEY_SCHEMA = "job.schema_version";
    public static final String KEY_IMAGE_ID = "image_id";
    public static final String KEY_MACHINE_ID = "machine_id";
    public static final String KEY_SOURCE_PATH = "source_path";
    public static final String KEY_SOURCE_RELATIVE = "source_relative_path";
    public static final String KEY_SOURCE_SHA256 = "source_sha256";
    public static final String KEY_CONFIG_PATH = "configuration_path";
    public static final String KEY_CONFIG_SHA256 = "configuration_sha256";
    public static final String KEY_BATCH_ROOT = "batch_root";
    public static final String KEY_RESULT_PATH = "result_path";
    public static final String KEY_STDOUT_PATH = "stdout_path";
    public static final String KEY_STDERR_PATH = "stderr_path";
    public static final String KEY_MASK_PREVIEW = "mask_preview_path";
    public static final String KEY_SCALE_PREVIEW = "scale_bar_preview_path";

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
                    KEY_BATCH_ROOT,
                    KEY_RESULT_PATH,
                    KEY_STDOUT_PATH,
                    KEY_STDERR_PATH,
                    KEY_MASK_PREVIEW,
                    KEY_SCALE_PREVIEW)));

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final int schemaVersion;
    private final SixDigitSampleId imageId;
    private final String machineId;
    private final Path sourcePath;
    private final String sourceRelativePath;
    private final String sourceSha256;
    private final Path configurationPath;
    private final String configurationSha256;
    private final Path batchRoot;
    private final Path resultPath;
    private final Path stdoutPath;
    private final Path stderrPath;
    private final Path maskPreviewPath;
    private final Path scaleBarPreviewPath;

    public BatchPrecheckJob(
            final int schemaVersion,
            final SixDigitSampleId imageId,
            final String machineId,
            final Path sourcePath,
            final String sourceRelativePath,
            final String sourceSha256,
            final Path configurationPath,
            final String configurationSha256,
            final Path batchRoot,
            final Path resultPath,
            final Path stdoutPath,
            final Path stderrPath,
            final Path maskPreviewPath,
            final Path scaleBarPreviewPath) {
        this.schemaVersion = schemaVersion;
        this.imageId = imageId;
        this.machineId = machineId;
        this.sourcePath = sourcePath;
        this.sourceRelativePath = sourceRelativePath;
        this.sourceSha256 = sourceSha256;
        this.configurationPath = configurationPath;
        this.configurationSha256 = configurationSha256;
        this.batchRoot = batchRoot;
        this.resultPath = resultPath;
        this.stdoutPath = stdoutPath;
        this.stderrPath = stderrPath;
        this.maskPreviewPath = maskPreviewPath;
        this.scaleBarPreviewPath = scaleBarPreviewPath;
    }

    public static BatchPrecheckJob load(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return fromMap(BatchConfiguration.parseStrictProperties(lines));
    }

    public static BatchPrecheckJob fromMap(final Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        for (final String key : values.keySet()) {
            if (!REQUIRED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown job key: " + key);
            }
        }
        for (final String required : REQUIRED_KEYS) {
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
        final SixDigitSampleId imageId = SixDigitSampleId.parse(values.get(KEY_IMAGE_ID));
        final Path batchRoot = Paths.get(values.get(KEY_BATCH_ROOT)).toAbsolutePath().normalize();
        final Path sourcePath = Paths.get(values.get(KEY_SOURCE_PATH)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("source file must exist: " + sourcePath);
        }
        final String sha = values.get(KEY_SOURCE_SHA256).trim().toLowerCase(Locale.ROOT);
        if (!SHA256_HEX.matcher(sha).matches()) {
            throw new IllegalArgumentException("invalid source SHA-256: " + sha);
        }
        final String configSha = values.get(KEY_CONFIG_SHA256).trim().toLowerCase(Locale.ROOT);
        if (!SHA256_HEX.matcher(configSha).matches()) {
            throw new IllegalArgumentException("invalid configuration SHA-256: " + configSha);
        }
        final Path resultPath = requireUnder(batchRoot, values.get(KEY_RESULT_PATH));
        final Path stdoutPath = requireUnder(batchRoot, values.get(KEY_STDOUT_PATH));
        final Path stderrPath = requireUnder(batchRoot, values.get(KEY_STDERR_PATH));
        final Path maskPreview = requireUnder(batchRoot, values.get(KEY_MASK_PREVIEW));
        final Path scalePreview = requireUnder(batchRoot, values.get(KEY_SCALE_PREVIEW));
        return new BatchPrecheckJob(
                schema,
                imageId,
                values.get(KEY_MACHINE_ID).trim(),
                sourcePath,
                values.get(KEY_SOURCE_RELATIVE).trim().replace('\\', '/'),
                sha,
                Paths.get(values.get(KEY_CONFIG_PATH)).toAbsolutePath().normalize(),
                configSha,
                batchRoot,
                resultPath,
                stdoutPath,
                stderrPath,
                maskPreview,
                scalePreview);
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
        map.put(KEY_BATCH_ROOT, batchRoot.toString());
        map.put(KEY_RESULT_PATH, resultPath.toString());
        map.put(KEY_STDOUT_PATH, stdoutPath.toString());
        map.put(KEY_STDERR_PATH, stderrPath.toString());
        map.put(KEY_MASK_PREVIEW, maskPreviewPath.toString());
        map.put(KEY_SCALE_PREVIEW, scaleBarPreviewPath.toString());
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        Sha256Files.writeBytesAtomically(target, sb.toString().getBytes(StandardCharsets.UTF_8));
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

    public SixDigitSampleId imageId() {
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

    public Path batchRoot() {
        return batchRoot;
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

    public Path maskPreviewPath() {
        return maskPreviewPath;
    }

    public Path scaleBarPreviewPath() {
        return scaleBarPreviewPath;
    }
}
