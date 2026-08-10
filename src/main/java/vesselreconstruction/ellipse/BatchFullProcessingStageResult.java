package vesselreconstruction.ellipse;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable atomic result of exactly one Phase-3 Fiji child stage
 * ({@link BatchFullProcessingStage#CLASSIFICATION} or
 * {@link BatchFullProcessingStage#RECONSTRUCTION}).
 *
 * <p>Serialized as a UTF-8 Java {@code .properties} document. Booleans use
 * {@code TRUE}/{@code FALSE}; unavailable values use {@code NA}; {@code NaN}
 * is never written. Text values survive spaces, Unicode, equals signs, colons,
 * backslashes, and line breaks via {@link Properties} escaping.</p>
 */
public final class BatchFullProcessingStageResult {

    public static final String KEY_SCHEMA = "result.schema_version";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_STATUS = "status";
    public static final String KEY_REASON = "reason_code";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_IMAGE_ID = "image_id";
    public static final String KEY_MACHINE_ID = "machine_id";
    public static final String KEY_ATTEMPT_NUMBER = "attempt_number";
    public static final String KEY_PROCESS_EXIT_CODE = "process_exit_code";
    public static final String KEY_OUT_OF_MEMORY_DETECTED = "out_of_memory_detected";
    public static final String KEY_STAGE_COMPLETED = "stage_completed";
    public static final String KEY_REQUIRED_OUTPUTS_VALIDATED = "required_outputs_validated";
    public static final String KEY_RUN_ID = "run_id";
    public static final String KEY_PRIMARY_OUTPUT_PATH = "primary_output_path";
    public static final String KEY_TOTAL_COMPONENTS = "total_components";
    public static final String KEY_ELIGIBLE_COMPONENTS = "eligible_components";
    public static final String KEY_RECONSTRUCTED_COMPONENTS = "reconstructed_components";
    public static final String KEY_CHANGED_PIXELS = "changed_pixels";
    public static final String KEY_SOURCE_UNCHANGED = "source_unchanged";
    public static final String KEY_TIME_MS = "processing_time_ms";

    public static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA,
                    KEY_STAGE,
                    KEY_STATUS,
                    KEY_REASON,
                    KEY_MESSAGE,
                    KEY_IMAGE_ID,
                    KEY_MACHINE_ID,
                    KEY_ATTEMPT_NUMBER,
                    KEY_PROCESS_EXIT_CODE,
                    KEY_OUT_OF_MEMORY_DETECTED,
                    KEY_STAGE_COMPLETED,
                    KEY_REQUIRED_OUTPUTS_VALIDATED,
                    KEY_RUN_ID,
                    KEY_PRIMARY_OUTPUT_PATH,
                    KEY_TOTAL_COMPONENTS,
                    KEY_ELIGIBLE_COMPONENTS,
                    KEY_RECONSTRUCTED_COMPONENTS,
                    KEY_CHANGED_PIXELS,
                    KEY_SOURCE_UNCHANGED,
                    KEY_TIME_MS)));

    private final Map<String, String> values;

    private BatchFullProcessingStageResult(final Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<String, String>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BatchFullProcessingStageResult read(final Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        final Properties props = new Properties();
        final Reader reader = new InputStreamReader(
                new BufferedInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8);
        try {
            props.load(reader);
        } finally {
            reader.close();
        }
        final Map<String, String> map = new TreeMap<String, String>();
        for (final String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return fromMap(map);
    }

    public static BatchFullProcessingStageResult fromMap(final Map<String, String> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        for (final String key : raw.keySet()) {
            if (!REQUIRED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown stage-result key: " + key);
            }
        }
        for (final String required : REQUIRED_KEYS) {
            if (!raw.containsKey(required)) {
                throw new IllegalArgumentException("Missing stage-result key: " + required);
            }
        }
        final Map<String, String> normalized = new TreeMap<String, String>();
        for (final String key : REQUIRED_KEYS) {
            if (KEY_MESSAGE.equals(key)) {
                normalized.put(key, sanitizeMessage(raw.get(key)));
            } else {
                normalized.put(key, sanitize(raw.get(key)));
            }
        }
        if (!"1".equals(normalized.get(KEY_SCHEMA))) {
            throw new IllegalArgumentException(KEY_SCHEMA + " must be 1");
        }
        BatchFullProcessingStage.parse(normalized.get(KEY_STAGE));
        BatchFullProcessingStatus.parse(normalized.get(KEY_STATUS));
        requireTrueFalseOrNa(normalized.get(KEY_OUT_OF_MEMORY_DETECTED), KEY_OUT_OF_MEMORY_DETECTED);
        requireTrueFalseOrNa(normalized.get(KEY_STAGE_COMPLETED), KEY_STAGE_COMPLETED);
        requireTrueFalseOrNa(
                normalized.get(KEY_REQUIRED_OUTPUTS_VALIDATED), KEY_REQUIRED_OUTPUTS_VALIDATED);
        requireTrueFalseOrNa(normalized.get(KEY_SOURCE_UNCHANGED), KEY_SOURCE_UNCHANGED);
        return new BatchFullProcessingStageResult(normalized);
    }

    /**
     * Serializes the complete document in memory, writes a temporary sibling
     * in the same directory, closes it, then moves it into place (atomic when
     * supported, with a safe replace fallback). On any failure the previous
     * target file is left untouched and no partial result remains.
     */
    public void writeAtomic(final Path target) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        final Path absolute = target.toAbsolutePath().normalize();
        final Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("stage-result path has no parent: " + absolute);
        }
        final byte[] content = serializeUtf8();
        Files.createDirectories(parent);
        final Path temp = Files.createTempFile(parent, absolute.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            final OutputStream raw = new BufferedOutputStream(Files.newOutputStream(temp));
            try {
                raw.write(content);
                raw.flush();
            } finally {
                raw.close();
            }
            try {
                Files.move(temp, absolute,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final IOException atomicFailed) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            }
        } catch (final IOException ex) {
            throw ex;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temp);
                } catch (final IOException ignored) {
                    // best-effort cleanup of the temp sibling only
                }
            }
        }
    }

    byte[] serializeUtf8() throws IOException {
        final Properties props = new Properties();
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8);
        try {
            props.store(writer, null);
            writer.flush();
        } finally {
            writer.close();
        }
        final String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        final StringBuilder cleaned = new StringBuilder();
        final String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.startsWith("#")) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(line);
        }
        if (cleaned.length() > 0 && cleaned.charAt(cleaned.length() - 1) != '\n') {
            cleaned.append('\n');
        }
        return cleaned.toString().getBytes(StandardCharsets.UTF_8);
    }

    public BatchFullProcessingStage stage() {
        return BatchFullProcessingStage.parse(values.get(KEY_STAGE));
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

    public String reasonCode() {
        return values.get(KEY_REASON);
    }

    public String message() {
        return values.get(KEY_MESSAGE);
    }

    public String imageId() {
        return values.get(KEY_IMAGE_ID);
    }

    public String machineId() {
        return values.get(KEY_MACHINE_ID);
    }

    public String attemptNumber() {
        return values.get(KEY_ATTEMPT_NUMBER);
    }

    public String processExitCode() {
        return values.get(KEY_PROCESS_EXIT_CODE);
    }

    public String outOfMemoryDetected() {
        return values.get(KEY_OUT_OF_MEMORY_DETECTED);
    }

    public String stageCompleted() {
        return values.get(KEY_STAGE_COMPLETED);
    }

    public String requiredOutputsValidated() {
        return values.get(KEY_REQUIRED_OUTPUTS_VALIDATED);
    }

    public String runId() {
        return values.get(KEY_RUN_ID);
    }

    public String primaryOutputPath() {
        return values.get(KEY_PRIMARY_OUTPUT_PATH);
    }

    public String totalComponents() {
        return values.get(KEY_TOTAL_COMPONENTS);
    }

    public String eligibleComponents() {
        return values.get(KEY_ELIGIBLE_COMPONENTS);
    }

    public String reconstructedComponents() {
        return values.get(KEY_RECONSTRUCTED_COMPONENTS);
    }

    public String changedPixels() {
        return values.get(KEY_CHANGED_PIXELS);
    }

    public String sourceUnchanged() {
        return values.get(KEY_SOURCE_UNCHANGED);
    }

    public String processingTimeMs() {
        return values.get(KEY_TIME_MS);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchFullProcessingStageResult)) {
            return false;
        }
        final BatchFullProcessingStageResult other = (BatchFullProcessingStageResult) obj;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "BatchFullProcessingStageResult" + values;
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

    static String sanitizeMessage(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return RCsvSupport.NA;
        }
        if ("NaN".equalsIgnoreCase(raw.trim())
                || "Infinity".equalsIgnoreCase(raw.trim())
                || "-Infinity".equalsIgnoreCase(raw.trim())) {
            return RCsvSupport.NA;
        }
        return raw;
    }

    static String textOrNa(final String value) {
        return value == null || value.trim().isEmpty() ? RCsvSupport.NA : value.trim();
    }

    static String boolOrNa(final Boolean value) {
        if (value == null) {
            return RCsvSupport.NA;
        }
        return value.booleanValue() ? RCsvSupport.TRUE : RCsvSupport.FALSE;
    }

    static String intOrNa(final Integer value) {
        return value == null ? RCsvSupport.NA : Integer.toString(value.intValue());
    }

    static String longOrNa(final Long value) {
        return value == null ? RCsvSupport.NA : Long.toString(value.longValue());
    }

    static String pathOrNa(final Path path) {
        if (path == null) {
            return RCsvSupport.NA;
        }
        return path.toAbsolutePath().normalize().toString();
    }

    static String pathTextOrNa(final String pathText) {
        if (pathText == null || pathText.trim().isEmpty() || RCsvSupport.NA.equals(pathText.trim())) {
            return RCsvSupport.NA;
        }
        return Paths.get(pathText.trim()).toAbsolutePath().normalize().toString();
    }

    private static void requireTrueFalseOrNa(final String value, final String key) {
        if (RCsvSupport.TRUE.equals(value)
                || RCsvSupport.FALSE.equals(value)
                || RCsvSupport.NA.equals(value)) {
            return;
        }
        throw new IllegalArgumentException(
                key + " must be TRUE, FALSE, or NA, found: " + value);
    }

    public static final class Builder {
        private final Map<String, String> map = new TreeMap<String, String>();

        public Builder() {
            for (final String key : REQUIRED_KEYS) {
                map.put(key, RCsvSupport.NA);
            }
            map.put(KEY_SCHEMA, "1");
        }

        public Builder stage(final BatchFullProcessingStage stage) {
            if (stage == null) {
                throw new IllegalArgumentException("stage must not be null");
            }
            map.put(KEY_STAGE, stage.name());
            return this;
        }

        public Builder status(final BatchFullProcessingStatus status) {
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
            map.put(KEY_STATUS, status.name());
            return this;
        }

        public Builder reasonCode(final String reason) {
            map.put(KEY_REASON, textOrNa(reason));
            return this;
        }

        public Builder message(final String message) {
            if (message == null || message.trim().isEmpty()) {
                map.put(KEY_MESSAGE, RCsvSupport.NA);
            } else {
                map.put(KEY_MESSAGE, message);
            }
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

        public Builder attemptNumber(final Integer attemptNumber) {
            map.put(KEY_ATTEMPT_NUMBER, intOrNa(attemptNumber));
            return this;
        }

        public Builder processExitCode(final Integer exitCode) {
            map.put(KEY_PROCESS_EXIT_CODE, intOrNa(exitCode));
            return this;
        }

        public Builder outOfMemoryDetected(final Boolean value) {
            map.put(KEY_OUT_OF_MEMORY_DETECTED, boolOrNa(value));
            return this;
        }

        public Builder stageCompleted(final Boolean value) {
            map.put(KEY_STAGE_COMPLETED, boolOrNa(value));
            return this;
        }

        public Builder requiredOutputsValidated(final Boolean value) {
            map.put(KEY_REQUIRED_OUTPUTS_VALIDATED, boolOrNa(value));
            return this;
        }

        public Builder runId(final String runId) {
            map.put(KEY_RUN_ID, textOrNa(runId));
            return this;
        }

        public Builder primaryOutputPath(final Path path) {
            map.put(KEY_PRIMARY_OUTPUT_PATH, pathOrNa(path));
            return this;
        }

        public Builder primaryOutputPath(final String pathText) {
            map.put(KEY_PRIMARY_OUTPUT_PATH, pathTextOrNa(pathText));
            return this;
        }

        public Builder totalComponents(final Integer value) {
            map.put(KEY_TOTAL_COMPONENTS, intOrNa(value));
            return this;
        }

        public Builder eligibleComponents(final Integer value) {
            map.put(KEY_ELIGIBLE_COMPONENTS, intOrNa(value));
            return this;
        }

        public Builder reconstructedComponents(final Integer value) {
            map.put(KEY_RECONSTRUCTED_COMPONENTS, intOrNa(value));
            return this;
        }

        public Builder changedPixels(final Integer value) {
            map.put(KEY_CHANGED_PIXELS, intOrNa(value));
            return this;
        }

        public Builder sourceUnchanged(final Boolean value) {
            map.put(KEY_SOURCE_UNCHANGED, boolOrNa(value));
            return this;
        }

        public Builder processingTimeMs(final Long value) {
            map.put(KEY_TIME_MS, longOrNa(value));
            return this;
        }

        public BatchFullProcessingStageResult build() {
            return fromMap(map);
        }
    }
}
