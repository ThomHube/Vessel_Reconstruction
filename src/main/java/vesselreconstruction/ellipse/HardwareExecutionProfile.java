package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Safe execution parameters for the single analysis host.
 *
 * <p>Tile geometry is a scientific invariant. The hardware benchmark may
 * vary internal Weka concurrency ({@code classification.threads_per_tile}) and
 * JVM/Fiji memory; it must never change production tile dimensions or the
 * production tile-profile index.</p>
 */
public final class HardwareExecutionProfile {

    public static final int PRODUCTION_MACHINE_COUNT = 1;
    public static final String PRODUCTION_MACHINE_ID = "machine_01";
    public static final int PRODUCTION_TILE_PROFILE_INDEX = 2;
    public static final int PRODUCTION_TILE_WIDTH_PX = 1024;
    public static final int PRODUCTION_TILE_HEIGHT_PX = 1024;
    public static final String PRODUCTION_TILE_PROFILE_TOKEN = "1024x1024";
    public static final int BENCHMARK_VERSION = 2;
    public static final String KEY_TILE_WIDTH = "tile_width";
    public static final String KEY_TILE_HEIGHT = "tile_height";
    public static final String KEY_FIJI_HEAP_MB = "fiji.heap_mb";
    public static final String KEY_THREADS = BatchConfiguration.KEY_CLASSIFICATION_THREADS_PER_TILE;
    public static final String WORKLOAD_SYNTHETIC = "SYNTHETIC_PROBE";
    public static final String WORKLOAD_REAL = "REAL_LUMEN_CLASSIFICATION";
    public static final String STATUS_SYNTHETIC_ONLY = "SYNTHETIC_ONLY";
    public static final String STATUS_REAL_VALIDATED = "REAL_CLASSIFICATION_VALIDATED";
    public static final String KEY_BENCHMARK_TYPE = "benchmark_type";
    public static final String KEY_BENCHMARK_STATUS = "benchmark_status";
    public static final String KEY_BENCHMARK_WORKLOAD = "benchmark_workload";
    public static final String ENV_HARDWARE_PROFILE = "LUMEN_HARDWARE_PROFILE";

    private final int threadsPerTile;
    private final int fijiHeapMb;
    private final Map<String, String> metadata;

    public HardwareExecutionProfile(final int threadsPerTile) {
        this(threadsPerTile, 0, new LinkedHashMap<String, String>());
    }

    public HardwareExecutionProfile(final int threadsPerTile, final int fijiHeapMb) {
        this(threadsPerTile, fijiHeapMb, new LinkedHashMap<String, String>());
    }

    public HardwareExecutionProfile(
            final int threadsPerTile,
            final int fijiHeapMb,
            final Map<String, String> metadata) {
        if (threadsPerTile < 1) {
            throw new IllegalArgumentException(
                    "threadsPerTile must be >= 1: " + threadsPerTile);
        }
        if (fijiHeapMb < 0) {
            throw new IllegalArgumentException("fijiHeapMb must be >= 0: " + fijiHeapMb);
        }
        this.threadsPerTile = threadsPerTile;
        this.fijiHeapMb = fijiHeapMb;
        this.metadata = copyMetadata(metadata);
        this.metadata.put(KEY_TILE_WIDTH, Integer.toString(PRODUCTION_TILE_WIDTH_PX));
        this.metadata.put(KEY_TILE_HEIGHT, Integer.toString(PRODUCTION_TILE_HEIGHT_PX));
        this.metadata.put("machines.count", Integer.toString(PRODUCTION_MACHINE_COUNT));
        this.metadata.put("machine_id", PRODUCTION_MACHINE_ID);
        this.metadata.put(KEY_THREADS, Integer.toString(threadsPerTile));
        if (fijiHeapMb > 0) {
            this.metadata.put(KEY_FIJI_HEAP_MB, Integer.toString(fijiHeapMb));
        }
    }

    public int threadsPerTile() {
        return threadsPerTile;
    }

    public int fijiHeapMb() {
        return fijiHeapMb;
    }

    public Map<String, String> metadata() {
        return new LinkedHashMap<String, String>(metadata);
    }

    public BatchTileProfile productionTileProfile() {
        return new BatchTileProfile(PRODUCTION_TILE_WIDTH_PX, PRODUCTION_TILE_HEIGHT_PX);
    }

    /**
     * Writes only the internal-concurrency knob. Tile profiles are left unchanged.
     */
    public void applyThreadsPerTile(final Map<String, String> configurationValues) {
        Objects.requireNonNull(configurationValues, "configurationValues");
        configurationValues.put(KEY_THREADS, Integer.toString(threadsPerTile));
    }

    /**
     * Fiji Jaunch heap argument ({@code --heap={N}m}). Empty when heap was
     * left unspecified. Production application requires a positive heap.
     */
    public List<String> fijiHeapArguments() {
        final List<String> args = new ArrayList<String>();
        if (fijiHeapMb > 0) {
            args.add("--heap=" + fijiHeapMb + "m");
        }
        return args;
    }

    /**
     * Weka threads for this run: overlay from a validated profile, otherwise
     * the SHA-locked production configuration value.
     */
    public static int classificationThreadsPerTile(
            final BatchConfiguration config,
            final HardwareExecutionProfile profile) {
        Objects.requireNonNull(config, "config");
        if (profile == null) {
            return config.threadsPerTile();
        }
        return profile.threadsPerTile();
    }

    /**
     * Copies {@code source} to {@code dest}, replacing only
     * {@code classification.threads_per_tile}. Tile profiles are left
     * unchanged. {@code source} is never modified.
     */
    public void applyThreadsToConfigFile(final Path source, final Path dest) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dest, "dest");
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("config file must exist: " + source);
        }
        if (source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "refusing to overwrite the source production configuration: " + source);
        }
        final List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder();
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line.startsWith(KEY_THREADS + "=")) {
                sb.append(KEY_THREADS).append('=').append(threadsPerTile).append('\n');
                replaced = true;
            } else {
                sb.append(line).append('\n');
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("config missing " + KEY_THREADS);
        }
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        Sha256Files.writeBytesAtomically(dest, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void writeProperties(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        final StringBuilder sb = new StringBuilder();
        sb.append("# LUMEN HARDWARE EXECUTION PROFILE\n");
        sb.append("# Generated by automated hardware benchmark v").append(BENCHMARK_VERSION)
                .append(".\n");
        sb.append("# BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n");
        sb.append("# Tile geometry is a scientific invariant and must remain 1024x1024.\n");
        final Map<String, String> ordered = new LinkedHashMap<String, String>(metadata);
        if (!ordered.containsKey("profile.schema_version")) {
            ordered.put("profile.schema_version", "1");
        }
        if (!ordered.containsKey("benchmark.version")) {
            ordered.put("benchmark.version", Integer.toString(BENCHMARK_VERSION));
        }
        ordered.put(KEY_TILE_WIDTH, Integer.toString(PRODUCTION_TILE_WIDTH_PX));
        ordered.put(KEY_TILE_HEIGHT, Integer.toString(PRODUCTION_TILE_HEIGHT_PX));
        ordered.put(KEY_THREADS, Integer.toString(threadsPerTile));
        if (fijiHeapMb > 0) {
            ordered.put(KEY_FIJI_HEAP_MB, Integer.toString(fijiHeapMb));
        }
        ordered.put("machines.count", Integer.toString(PRODUCTION_MACHINE_COUNT));
        ordered.put("machine_id", PRODUCTION_MACHINE_ID);
        for (final Map.Entry<String, String> entry : ordered.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Sha256Files.writeBytesAtomically(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static HardwareExecutionProfile load(final Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("profile file must exist: " + path);
        }
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        final Map<String, String> values = BatchConfiguration.parseStrictProperties(lines);
        return fromMap(values);
    }

    /**
     * Load a profile that is safe to apply to single-machine production.
     * Requires {@link #STATUS_REAL_VALIDATED}, a positive thread count, and a
     * positive {@code fiji.heap_mb}. Synthetic-only or incomplete results fail.
     */
    public static HardwareExecutionProfile loadValidatedForProduction(final Path path)
            throws IOException {
        final HardwareExecutionProfile loaded;
        try {
            loaded = load(path);
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "invalid hardware profile number: " + ex.getMessage(), ex);
        }
        loaded.requireValidatedForProduction();
        return loaded;
    }

    /**
     * {@code LUMEN_HARDWARE_PROFILE} overlay. {@code null} when unset so
     * production keeps its normal configuration. Invalid values fail closed.
     */
    public static HardwareExecutionProfile resolveFromEnvironment() {
        return resolveFromEnvironment(System.getenv(ENV_HARDWARE_PROFILE));
    }

    public static HardwareExecutionProfile resolveFromEnvironment(final String pathRaw) {
        if (pathRaw == null || pathRaw.trim().isEmpty()) {
            return null;
        }
        try {
            return loadValidatedForProduction(Paths.get(pathRaw.trim()));
        } catch (final IOException ex) {
            throw new IllegalArgumentException(
                    "cannot read hardware profile: " + ex.getMessage(), ex);
        }
    }

    public void requireValidatedForProduction() {
        final String status = metadata.get(KEY_BENCHMARK_STATUS);
        if (!STATUS_REAL_VALIDATED.equals(status)) {
            throw new IllegalArgumentException(
                    "hardware profile is not " + STATUS_REAL_VALIDATED + ": " + status);
        }
        if (threadsPerTile < 1) {
            throw new IllegalArgumentException(
                    KEY_THREADS + " must be a positive integer: " + threadsPerTile);
        }
        if (fijiHeapMb < 1) {
            throw new IllegalArgumentException(
                    KEY_FIJI_HEAP_MB + " must be a positive integer, found " + fijiHeapMb);
        }
        requireTileInvariant(metadata);
    }

    public static HardwareExecutionProfile fromMap(final Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        requireTileInvariant(values);
        final String threadsRaw = values.get(KEY_THREADS);
        if (threadsRaw == null || threadsRaw.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + KEY_THREADS);
        }
        final int threads = Integer.parseInt(threadsRaw.trim());
        int heap = 0;
        if (values.containsKey(KEY_FIJI_HEAP_MB)) {
            heap = Integer.parseInt(values.get(KEY_FIJI_HEAP_MB).trim());
        }
        return new HardwareExecutionProfile(threads, heap, values);
    }

    public static void requireTileInvariant(final Map<String, String> values) {
        final String width = values.get(KEY_TILE_WIDTH);
        final String height = values.get(KEY_TILE_HEIGHT);
        if (width == null || height == null) {
            throw new IllegalArgumentException(
                    "profile must contain tile_width=1024 and tile_height=1024");
        }
        if (!Integer.toString(PRODUCTION_TILE_WIDTH_PX).equals(width.trim())
                || !Integer.toString(PRODUCTION_TILE_HEIGHT_PX).equals(height.trim())) {
            throw new IllegalArgumentException(
                    "tile geometry must remain "
                            + PRODUCTION_TILE_PROFILE_TOKEN
                            + ", found " + width + "x" + height);
        }
    }

    public static BatchTileProfile requireProductionTileProfile(
            final List<BatchTileProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("tile profiles must not be null");
        }
        if (profiles.size() <= PRODUCTION_TILE_PROFILE_INDEX) {
            throw new IllegalArgumentException(
                    "classification.tile_profiles lacks production index "
                            + PRODUCTION_TILE_PROFILE_INDEX);
        }
        final BatchTileProfile profile = profiles.get(PRODUCTION_TILE_PROFILE_INDEX);
        if (profile.widthPx() != PRODUCTION_TILE_WIDTH_PX
                || profile.heightPx() != PRODUCTION_TILE_HEIGHT_PX) {
            throw new IllegalArgumentException(
                    "production tile profile index " + PRODUCTION_TILE_PROFILE_INDEX
                            + " must be " + PRODUCTION_TILE_PROFILE_TOKEN
                            + ", found " + profile.toToken());
        }
        return profile;
    }

    private static Map<String, String> copyMetadata(final Map<String, String> metadata) {
        final Map<String, String> copy = new LinkedHashMap<String, String>();
        if (metadata != null) {
            copy.putAll(metadata);
        }
        return copy;
    }
}
