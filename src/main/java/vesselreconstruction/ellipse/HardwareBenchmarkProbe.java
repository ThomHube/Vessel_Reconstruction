package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Representative 1024x1024 tile probe. BENCHMARK / TEST ONLY.
 * Does not open production TIFFs and does not run Weka.
 */
public final class HardwareBenchmarkProbe {

    public static final int TILE_WIDTH = HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX;
    public static final int TILE_HEIGHT = HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX;
    public static final int DEFAULT_TILE_COUNT = 8;
    public static final int DEFAULT_PASSES = 20;
    public static final String INPUT_PREFIX = "tile_";
    public static final String INPUT_SUFFIX = ".bin";

    public static final class Result {
        public final boolean success;
        public final int tilesCompleted;
        public final long elapsedMs;
        public final double tilesPerSecond;
        public final String message;

        public Result(
                final boolean success,
                final int tilesCompleted,
                final long elapsedMs,
                final double tilesPerSecond,
                final String message) {
            this.success = success;
            this.tilesCompleted = tilesCompleted;
            this.elapsedMs = elapsedMs;
            this.tilesPerSecond = tilesPerSecond;
            this.message = message;
        }
    }

    private HardwareBenchmarkProbe() {
    }

    public static List<Path> writeDeterministicInputs(final Path inputDir, final int tileCount)
            throws IOException {
        Files.createDirectories(inputDir);
        final List<Path> files = new ArrayList<Path>();
        for (int i = 0; i < tileCount; i++) {
            final Path file = inputDir.resolve(
                    String.format(Locale.US, "%s%02d%s", INPUT_PREFIX, Integer.valueOf(i),
                            INPUT_SUFFIX));
            Files.write(file, tileBytes(i));
            files.add(file);
        }
        return files;
    }

    public static Result run(
            final Path inputDir,
            final int threadsPerTile,
            final int passes) throws Exception {
        if (threadsPerTile < 1) {
            throw new IllegalArgumentException("threadsPerTile must be >= 1");
        }
        if (passes < 1) {
            throw new IllegalArgumentException("passes must be >= 1");
        }
        final List<Path> tiles = listTiles(inputDir);
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("no probe tiles in " + inputDir);
        }
        final ExecutorService pool = Executors.newFixedThreadPool(threadsPerTile);
        final long start = System.nanoTime();
        try {
            final List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < tiles.size(); i++) {
                final Path tile = tiles.get(i);
                futures.add(pool.submit(new TileTask(tile, passes)));
            }
            int completed = 0;
            for (int i = 0; i < futures.size(); i++) {
                completed += futures.get(i).get().intValue();
            }
            final long elapsedMs = Math.max(1L, (System.nanoTime() - start) / 1000000L);
            final double tps = (1000.0d * completed) / elapsedMs;
            return new Result(true, completed, elapsedMs, tps, "SUCCESS");
        } finally {
            pool.shutdownNow();
        }
    }

    public static void writeResultProperties(final Path path, final Result result)
            throws IOException {
        final String text = "status=" + (result.success ? "SUCCESS" : "FAILED") + "\n"
                + "tiles_completed=" + result.tilesCompleted + "\n"
                + "elapsed_ms=" + result.elapsedMs + "\n"
                + "throughput_tiles_per_second="
                + String.format(Locale.US, "%.6f", Double.valueOf(result.tilesPerSecond)) + "\n"
                + "tile_width=" + TILE_WIDTH + "\n"
                + "tile_height=" + TILE_HEIGHT + "\n"
                + "message=" + result.message + "\n"
                + "workload=SYNTHETIC_TILE_PROBE\n"
                + "disclaimer=BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n";
        Files.createDirectories(path.getParent());
        Sha256Files.writeBytesAtomically(path, text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] tileBytes(final int seed) {
        final int pixels = TILE_WIDTH * TILE_HEIGHT;
        final ByteBuffer buffer = ByteBuffer.allocate(pixels * 4).order(ByteOrder.LITTLE_ENDIAN);
        int value = 1 + seed * 17;
        for (int i = 0; i < pixels; i++) {
            value = 1103515245 * value + 12345;
            buffer.putInt(value);
        }
        return buffer.array();
    }

    static int processTile(final byte[] bytes, final int passes) {
        if (bytes.length != TILE_WIDTH * TILE_HEIGHT * 4) {
            throw new IllegalArgumentException(
                    "probe tile must be 1024x1024 x 4 bytes, found " + bytes.length);
        }
        final int pixels = TILE_WIDTH * TILE_HEIGHT;
        final int[] src = new int[pixels];
        final int[] dst = new int[pixels];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < pixels; i++) {
            src[i] = buffer.getInt();
        }
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 1; y < TILE_HEIGHT - 1; y++) {
                for (int x = 1; x < TILE_WIDTH - 1; x++) {
                    final int idx = y * TILE_WIDTH + x;
                    final int acc = src[idx - 1] + src[idx + 1]
                            + src[idx - TILE_WIDTH] + src[idx + TILE_WIDTH] + src[idx];
                    dst[idx] = acc / 5;
                }
            }
            final int[] swap = src;
            System.arraycopy(dst, 0, swap, 0, pixels);
        }
        return 1;
    }

    private static List<Path> listTiles(final Path inputDir) throws IOException {
        final List<Path> tiles = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "tile_*.bin")) {
            for (final Path path : stream) {
                tiles.add(path);
            }
        }
        Collections.sort(tiles);
        return tiles;
    }

    private static final class TileTask implements Callable<Integer> {
        private final Path tile;
        private final int passes;

        private TileTask(final Path tile, final int passes) {
            this.tile = tile;
            this.passes = passes;
        }

        @Override
        public Integer call() throws Exception {
            final byte[] bytes = Files.readAllBytes(tile);
            return Integer.valueOf(processTile(bytes, passes));
        }
    }
}
