package vesselreconstruction.ellipse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolated per-configuration probe execution. Kills only the child process
 * it started. Never uses {@code pkill}/{@code killall}.
 */
public final class HardwareBenchmarkRunner {

    public static final int TILE_COUNT = HardwareBenchmarkProbe.DEFAULT_TILE_COUNT;
    public static final int PASSES = HardwareBenchmarkProbe.DEFAULT_PASSES;
    public static final long SAMPLE_PERIOD_MS = 200L;
    public static final long MIN_TIMEOUT_MS = 15_000L;
    public static final long MAX_TIMEOUT_MS = 180_000L;
    /** First real Weka run is far slower than the synthetic probe. */
    public static final long REAL_MIN_TIMEOUT_MS = 900_000L;
    public static final long REAL_MAX_TIMEOUT_MS = 5_400_000L;
    public static final int TIMEOUT_MULTIPLIER = 8;
    /** Owned process-tree {@code VmSwap} growth at or above this is unsafe. */
    public static final long SWAP_UNSAFE_BYTES = HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES;
    public static final long MIN_AVAILABLE_RAM_BYTES = 256L * 1024L * 1024L;

    public interface ProbeProcessFactory {
        Process start(List<String> command, Path workDir) throws IOException;
    }

    public static final class DefaultProbeProcessFactory implements ProbeProcessFactory {
        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir.toFile());
            builder.redirectErrorStream(true);
            return builder.start();
        }
    }

    public static final class Request {
        public final Path outputRoot;
        public final Path javaExecutable;
        public final Path classesDirectory;
        public final Path procRoot;
        public final HardwareInventory inventory;
        public final ProbeProcessFactory processFactory;
        public final Appendable log;
        public final Path optionalInputRoot;
        public final Path fijiLauncher;
        public final Path classifier;
        public final Path configTemplate;
        public final HardwareBenchmarkWorkload.Prepared workload;
        public final Path pluginsDir;

        public Request(
                final Path outputRoot,
                final Path javaExecutable,
                final Path classesDirectory,
                final Path procRoot,
                final HardwareInventory inventory,
                final ProbeProcessFactory processFactory,
                final Appendable log,
                final Path optionalInputRoot) {
            this(outputRoot, javaExecutable, classesDirectory, procRoot, inventory,
                    processFactory, log, optionalInputRoot, null, null, null, null, null);
        }

        public Request(
                final Path outputRoot,
                final Path javaExecutable,
                final Path classesDirectory,
                final Path procRoot,
                final HardwareInventory inventory,
                final ProbeProcessFactory processFactory,
                final Appendable log,
                final Path optionalInputRoot,
                final Path fijiLauncher,
                final Path classifier,
                final Path configTemplate,
                final HardwareBenchmarkWorkload.Prepared workload) {
            this(outputRoot, javaExecutable, classesDirectory, procRoot, inventory,
                    processFactory, log, optionalInputRoot, fijiLauncher, classifier,
                    configTemplate, workload, null);
        }

        public Request(
                final Path outputRoot,
                final Path javaExecutable,
                final Path classesDirectory,
                final Path procRoot,
                final HardwareInventory inventory,
                final ProbeProcessFactory processFactory,
                final Appendable log,
                final Path optionalInputRoot,
                final Path fijiLauncher,
                final Path classifier,
                final Path configTemplate,
                final HardwareBenchmarkWorkload.Prepared workload,
                final Path pluginsDir) {
            this.outputRoot = outputRoot;
            this.javaExecutable = javaExecutable;
            this.classesDirectory = classesDirectory;
            this.procRoot = procRoot == null ? HardwareBenchmarkMonitor.defaultProcRoot() : procRoot;
            this.inventory = inventory;
            this.processFactory = processFactory == null
                    ? new DefaultProbeProcessFactory() : processFactory;
            this.log = log == null ? System.out : log;
            this.optionalInputRoot = optionalInputRoot;
            this.fijiLauncher = fijiLauncher;
            this.classifier = classifier;
            this.configTemplate = configTemplate;
            this.workload = workload;
            this.pluginsDir = pluginsDir;
        }
    }

    private HardwareBenchmarkRunner() {
    }

    public static HardwareBenchmarkRunResult runOne(
            final Request request,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final long timeoutMs) throws IOException, InterruptedException {
        final Path runDir = request.outputRoot.resolve("runs").resolve(candidate.id);
        Files.createDirectories(runDir);
        final Path inputDir = runDir.resolve("input");
        final Path outputProps = runDir.resolve("probe_result.properties");
        final Path logFile = runDir.resolve("probe.log");
        final Path checksums = runDir.resolve("input_checksums.sha256");

        HardwareBenchmarkProbe.writeDeterministicInputs(inputDir, TILE_COUNT);
        final Map<String, String> before = checksumTree(inputDir);
        writeChecksumFile(checksums, before);
        recordOptionalScientificInputs(request.optionalInputRoot, runDir);

        final List<String> command = buildCommand(
                request.javaExecutable,
                request.classesDirectory,
                candidate,
                inputDir,
                outputProps);
        writeText(runDir.resolve("command.txt"), joinCommand(command));

        final long startMs = System.currentTimeMillis();
        Process process = null;
        int pid = 0;
        final HardwareBenchmarkMonitor.Accumulator acc = new HardwareBenchmarkMonitor.Accumulator();
        HardwareBenchmarkMonitor.captureHostSwapBeforeStart(request.procRoot, acc);
        int exit = -1;
        boolean timedOut = false;
        String logText = "";
        try {
            process = request.processFactory.start(command, runDir);
            pid = HardwareBenchmarkMonitor.readPid(process);
            writeText(runDir.resolve("child.pid"), Integer.toString(pid));
            log(request.log, "HARDWARE BENCHMARK configuration " + candidate.id
                    + " threads=" + candidate.threadsPerTile
                    + " heap_mb=" + candidate.fijiHeapMb
                    + " pid=" + pid);

            final StringBuilder captured = new StringBuilder();
            final Thread gobbler = gobble(process, captured);
            while (true) {
                final HardwareBenchmarkMonitor.Sample sample =
                        HardwareBenchmarkMonitor.sample(request.procRoot, pid, acc);
                log(request.log, HardwareBenchmarkMonitor.formatSampleLine(candidate.id, sample));
                final long waited = System.currentTimeMillis() - startMs;
                if (waited > timeoutMs) {
                    timedOut = true;
                    destroyOwnedProcess(process);
                    break;
                }
                try {
                    exit = process.exitValue();
                    break;
                } catch (final IllegalThreadStateException running) {
                    Thread.sleep(SAMPLE_PERIOD_MS);
                }
            }
            gobbler.join(2000L);
            logText = captured.toString();
            writeText(logFile, logText);
        } finally {
            HardwareBenchmarkProcessTree.terminateOwnedTree(process, request.procRoot, pid);
        }
        final long wall = Math.max(1L, System.currentTimeMillis() - startMs);

        final Map<String, String> after = checksumTree(inputDir);
        final boolean checksumOk = before.equals(after);
        final Map<String, String> probe = readPropsIfPresent(outputProps);
        final boolean oom = logText.toLowerCase(Locale.ROOT).contains("outofmemoryerror")
                || exit == HardwareBenchmarkProbeCli.EXIT_OOM;
        final int tiles = parseInt(probe.get("tiles_completed"), 0);
        final double tps = parseDouble(probe.get("throughput_tiles_per_second"),
                tiles > 0 ? (1000.0d * tiles) / wall : 0.0d);
        final boolean success = !timedOut && exit == 0 && checksumOk && tiles > 0 && !oom;
        String unsafeReason = RCsvSupport.NA;
        boolean unsafe = false;
        if (timedOut) {
            unsafe = true;
            unsafeReason = "timeout";
        } else if (oom) {
            unsafe = true;
            unsafeReason = "OutOfMemoryError";
        } else if (exit != 0) {
            unsafe = true;
            unsafeReason = "non-zero exit " + exit;
        } else if (!checksumOk) {
            unsafe = true;
            unsafeReason = "input checksum changed";
        } else if (acc.processTreeSwapGrowthBytes() >= SWAP_UNSAFE_BYTES) {
            unsafe = true;
            unsafeReason = "swap growth";
        } else if (acc.peakUsedRam > 0L
                && acc.minAvailableRam != Long.MAX_VALUE
                && acc.minAvailableRam < MIN_AVAILABLE_RAM_BYTES) {
            unsafe = true;
            unsafeReason = "severe memory pressure";
        }

        return new HardwareBenchmarkRunResult(
                candidate.id,
                candidate.phase,
                candidate.threadsPerTile,
                candidate.fijiHeapMb,
                success,
                unsafe,
                unsafeReason,
                exit,
                wall,
                tps,
                tiles,
                acc.meanCpu(),
                acc.meanProcessCpu(),
                acc.peakRss,
                acc.peakUsedRam,
                acc.minAvailableRam == Long.MAX_VALUE ? 0L : acc.minAvailableRam,
                acc.peakSwap,
                Math.max(0L, acc.startSwap),
                acc.peakLoad,
                unsafe ? unsafeReason : probe.get("message"),
                HardwareExecutionProfile.WORKLOAD_SYNTHETIC,
                (long) tiles
                        * HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX
                        * HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX,
                0.0d,
                success && !unsafe,
                RCsvSupport.NA,
                Math.max(0L, acc.processTreeStartSwap),
                acc.processTreePeakSwap);
    }

    public static long timeoutMsReal(final long baselineWallMs) {
        return clampTimeout(baselineWallMs, REAL_MIN_TIMEOUT_MS, REAL_MAX_TIMEOUT_MS);
    }

    public static long timeoutMs(final long baselineWallMs) {
        return clampTimeout(baselineWallMs, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
    }

    private static long clampTimeout(final long baselineWallMs, final long min, final long max) {
        if (baselineWallMs <= 0L) {
            return min;
        }
        long timeout = baselineWallMs * TIMEOUT_MULTIPLIER;
        if (timeout < min) {
            timeout = min;
        }
        if (timeout > max) {
            timeout = max;
        }
        return timeout;
    }

    public static HardwareBenchmarkRunResult runOneClassification(
            final Request request,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final long timeoutMs) throws Exception {
        final Path runDir = request.outputRoot.resolve("runs").resolve("real").resolve(candidate.id);
        Files.createDirectories(runDir);
        if (request.workload == null || request.fijiLauncher == null
                || request.classifier == null || request.configTemplate == null) {
            throw new IllegalArgumentException(
                    "real classification requires fiji, classifier, config, and workload");
        }
        final HardwareBenchmarkClassificationSupport.PreparedRun prepared;
        try {
            prepared = HardwareBenchmarkClassificationSupport.prepare(
                    runDir, request.workload, candidate, request.configTemplate,
                    request.classifier, request.fijiLauncher, request.pluginsDir);
        } catch (final Exception ex) {
            log(request.log, "real classification setup failed: " + ex.getMessage());
            return unsafeSetup(candidate, ex.getMessage());
        }
        writeText(runDir.resolve("DISCLAIMER.txt"),
                HardwareBenchmarkWorkload.DISCLAIMER + "\n"
                        + "host=DEVELOPMENT MACHINE unless otherwise labelled\n");
        return executeClassification(request, candidate, timeoutMs, prepared);
    }

    static HardwareBenchmarkRunResult executeClassification(
            final Request request,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final long timeoutMs,
            final HardwareBenchmarkClassificationSupport.PreparedRun prepared)
            throws Exception {
        final Path runDir = prepared.runDir;
        final Map<String, String> before = checksumTree(prepared.inputDir);
        writeChecksumFile(runDir.resolve("input_checksums.sha256"), before);
        writeText(runDir.resolve("command.txt"), joinCommand(prepared.command));

        final long startMs = System.currentTimeMillis();
        Process process = null;
        int pid = 0;
        final HardwareBenchmarkMonitor.Accumulator acc = new HardwareBenchmarkMonitor.Accumulator();
        HardwareBenchmarkMonitor.captureHostSwapBeforeStart(request.procRoot, acc);
        int exit = -1;
        boolean timedOut = false;
        String logText = "";
        try {
            process = request.processFactory.start(prepared.command, runDir);
            pid = HardwareBenchmarkMonitor.readPid(process);
            writeText(runDir.resolve("child.pid"), Integer.toString(pid));
            log(request.log, "HARDWARE BENCHMARK REAL_LUMEN_CLASSIFICATION " + candidate.id
                    + " threads=" + candidate.threadsPerTile
                    + " heap_mb=" + candidate.fijiHeapMb
                    + " pid=" + pid);
            final StringBuilder captured = new StringBuilder();
            final Thread gobbler = gobble(process, captured);
            while (true) {
                final HardwareBenchmarkMonitor.Sample sample =
                        HardwareBenchmarkMonitor.sample(request.procRoot, pid, acc);
                log(request.log, HardwareBenchmarkMonitor.formatSampleLine(candidate.id, sample));
                final long waited = System.currentTimeMillis() - startMs;
                if (waited > timeoutMs) {
                    timedOut = true;
                    HardwareBenchmarkProcessTree.terminateOwnedTree(
                            process, request.procRoot, pid);
                    break;
                }
                try {
                    exit = process.exitValue();
                    break;
                } catch (final IllegalThreadStateException running) {
                    Thread.sleep(SAMPLE_PERIOD_MS);
                }
            }
            gobbler.join(2000L);
            logText = captured.toString();
            writeText(prepared.stdoutPath, logText);
        } finally {
            HardwareBenchmarkProcessTree.terminateOwnedTree(process, request.procRoot, pid);
        }
        final long wall = Math.max(1L, System.currentTimeMillis() - startMs);
        final Map<String, String> after = checksumTree(prepared.inputDir);
        final boolean checksumOk = before.equals(after);
        final boolean originalOk = request.workload.originalSha256.equalsIgnoreCase(
                Sha256Files.sha256OfFile(request.workload.originalPath));
        final boolean oom = logText.toLowerCase(Locale.ROOT).contains("outofmemoryerror")
                || exit == HardwareBenchmarkProbeCli.EXIT_OOM;
        final HardwareBenchmarkClassificationSupport.OutputCheck output =
                HardwareBenchmarkClassificationSupport.validateOutput(
                        prepared.datasetRoot, prepared.resultPath, prepared.inputSha256, exit);
        final double pixelsPerSec = (1000.0d * request.workload.processedPixels()) / wall;
        final int tiles = (int) Math.max(1L,
                request.workload.processedPixels()
                        / (HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX
                        * HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX));
        boolean unsafe = false;
        String unsafeReason = RCsvSupport.NA;
        if (timedOut) {
            unsafe = true;
            unsafeReason = "timeout";
        } else if (oom) {
            unsafe = true;
            unsafeReason = "OutOfMemoryError";
        } else if (!checksumOk || !originalOk) {
            unsafe = true;
            unsafeReason = "input checksum changed";
        } else if (acc.processTreeSwapGrowthBytes() >= SWAP_UNSAFE_BYTES) {
            unsafe = true;
            unsafeReason = "swap growth";
        } else if (acc.peakUsedRam > 0L
                && acc.minAvailableRam != Long.MAX_VALUE
                && acc.minAvailableRam < MIN_AVAILABLE_RAM_BYTES) {
            unsafe = true;
            unsafeReason = "severe memory pressure";
        } else if (!output.valid) {
            unsafe = true;
            unsafeReason = output.reason;
        }
        final boolean success = !timedOut && exit == 0 && checksumOk && originalOk
                && output.valid && !oom;
        return new HardwareBenchmarkRunResult(
                candidate.id,
                candidate.phase,
                candidate.threadsPerTile,
                candidate.fijiHeapMb,
                success,
                unsafe,
                unsafeReason,
                exit,
                wall,
                tiles / (wall / 1000.0d),
                tiles,
                acc.meanCpu(),
                acc.meanProcessCpu(),
                acc.peakRss,
                acc.peakUsedRam,
                acc.minAvailableRam == Long.MAX_VALUE ? 0L : acc.minAvailableRam,
                acc.peakSwap,
                Math.max(0L, acc.startSwap),
                acc.peakLoad,
                unsafe ? unsafeReason : output.reason,
                HardwareExecutionProfile.WORKLOAD_REAL,
                request.workload.processedPixels(),
                pixelsPerSec,
                output.valid,
                prepared.classifierSha256,
                Math.max(0L, acc.processTreeStartSwap),
                acc.processTreePeakSwap);
    }

    private static HardwareBenchmarkRunResult unsafeSetup(
            final HardwareBenchmarkPlanner.Candidate candidate,
            final String message) {
        return new HardwareBenchmarkRunResult(
                candidate.id,
                candidate.phase,
                candidate.threadsPerTile,
                candidate.fijiHeapMb,
                false,
                true,
                message,
                -1,
                1L,
                0.0d,
                0,
                0.0d,
                0.0d,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0d,
                message,
                HardwareExecutionProfile.WORKLOAD_REAL,
                0L,
                0.0d,
                false,
                RCsvSupport.NA);
    }

    static List<String> buildCommand(
            final Path java,
            final Path classes,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final Path inputDir,
            final Path outputProps) {
        final List<String> command = new ArrayList<String>();
        command.add(java.toAbsolutePath().normalize().toString());
        command.add("-Xmx" + candidate.fijiHeapMb + "m");
        command.add("-Xms" + Math.min(256, candidate.fijiHeapMb) + "m");
        command.add("-cp");
        command.add(classes.toAbsolutePath().normalize().toString());
        command.add("vesselreconstruction.ellipse.HardwareBenchmarkProbeCli");
        command.add("--input-dir");
        command.add(inputDir.toAbsolutePath().normalize().toString());
        command.add("--output");
        command.add(outputProps.toAbsolutePath().normalize().toString());
        command.add("--threads");
        command.add(Integer.toString(candidate.threadsPerTile));
        command.add("--passes");
        command.add(Integer.toString(PASSES));
        return command;
    }

    static Map<String, String> checksumTree(final Path dir) throws IOException {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        if (!Files.isDirectory(dir)) {
            return map;
        }
        final List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (final Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        files.sort(null);
        for (int i = 0; i < files.size(); i++) {
            map.put(files.get(i).getFileName().toString(), Sha256Files.sha256OfFile(files.get(i)));
        }
        return map;
    }

    static void writeChecksumFile(final Path path, final Map<String, String> checksums)
            throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : checksums.entrySet()) {
            sb.append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        Sha256Files.writeBytesAtomically(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void recordOptionalScientificInputs(final Path inputRoot, final Path runDir)
            throws IOException {
        if (inputRoot == null || !Files.isDirectory(inputRoot)) {
            return;
        }
        final Path note = runDir.resolve("reference_dataset_checksums.sha256");
        final Map<String, String> sums = new LinkedHashMap<String, String>();
        collectTiffs(inputRoot, inputRoot, sums);
        writeChecksumFile(note, sums);
        writeText(runDir.resolve("REFERENCE_DATASET.txt"),
                "BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n"
                        + "Original TIFFs were checksummed and not modified.\n"
                        + "input_root=" + inputRoot.toAbsolutePath().normalize() + "\n");
    }

    private static void collectTiffs(
            final Path root,
            final Path current,
            final Map<String, String> sums) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    collectTiffs(root, path, sums);
                } else {
                    final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".tif") || name.endsWith(".tiff")) {
                        final String rel = root.relativize(path).toString().replace('\\', '/');
                        sums.put(rel, Sha256Files.sha256OfFile(path));
                    }
                }
            }
        }
    }

    static void destroyOwnedProcess(final Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5L, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    static boolean isAliveCompat(final Process process) {
        try {
            process.exitValue();
            return false;
        } catch (final IllegalThreadStateException ex) {
            return true;
        }
    }

    private static Thread gobble(final Process process, final StringBuilder captured) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        captured.append(line).append('\n');
                    }
                } catch (final IOException ignored) {
                    // process ended
                }
            }
        }, "lumen-hw-benchmark-gobbler");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static void writeText(final Path path, final String text) throws IOException {
        Files.createDirectories(path.getParent());
        Sha256Files.writeBytesAtomically(path, text.getBytes(StandardCharsets.UTF_8));
    }

    static void log(final Appendable log, final String line) {
        try {
            log.append(line).append('\n');
        } catch (final IOException ignored) {
            // best effort
        }
    }

    private static String joinCommand(final List<String> command) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < command.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(command.get(i));
        }
        return sb.toString();
    }

    private static Map<String, String> readPropsIfPresent(final Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<String, String>();
        }
        return BatchConfiguration.parseStrictProperties(
                Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    private static int parseInt(final String raw, final int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDouble(final String raw, final double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }
}
