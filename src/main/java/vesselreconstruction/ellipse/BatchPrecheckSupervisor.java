package vesselreconstruction.ellipse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pure-Java Phase-2 precheck supervisor: one fresh Fiji process per TIFF.
 */
public final class BatchPrecheckSupervisor {

    public interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    public static final class Request {
        public final Path inputRoot;
        public final Path batchRoot;
        public final String machineId;
        public final Path configPath;
        public final Path fijiLauncher;
        public final ProcessLauncher launcher;

        public Request(
                final Path inputRoot,
                final Path batchRoot,
                final String machineId,
                final Path configPath,
                final Path fijiLauncher,
                final ProcessLauncher launcher) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.machineId = machineId;
            this.configPath = configPath;
            this.fijiLauncher = fijiLauncher;
            this.launcher = launcher == null ? new DefaultProcessLauncher() : launcher;
        }
    }

    public static final class Summary {
        public final int total;
        public final int success;
        public final int reviewRequired;
        public final int failed;
        public final Path resultsCsv;
        public final int exitCode;

        public Summary(
                final int total,
                final int success,
                final int reviewRequired,
                final int failed,
                final Path resultsCsv,
                final int exitCode) {
            this.total = total;
            this.success = success;
            this.reviewRequired = reviewRequired;
            this.failed = failed;
            this.resultsCsv = resultsCsv;
            this.exitCode = exitCode;
        }
    }

    public static final class SupervisorException extends Exception {
        public final int exitCode;

        public SupervisorException(final int exitCode, final String message) {
            super(message);
            this.exitCode = exitCode;
        }

        public SupervisorException(final int exitCode, final String message, final Throwable cause) {
            super(message, cause);
            this.exitCode = exitCode;
        }
    }

    private static final class DefaultProcessLauncher implements ProcessLauncher {
        @Override
        public Process start(final List<String> command) throws IOException {
            return new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start();
        }
    }

    private BatchPrecheckSupervisor() {
    }

    public static Summary run(final Request request) throws SupervisorException {
        if (request == null
                || request.inputRoot == null
                || request.batchRoot == null
                || request.machineId == null
                || request.configPath == null
                || request.fijiLauncher == null) {
            throw new SupervisorException(12, "missing required supervisor arguments");
        }
        final BatchConfiguration config;
        try {
            config = BatchConfiguration.load(request.configPath);
        } catch (final Exception ex) {
            throw new SupervisorException(13, "invalid configuration: " + ex.getMessage(), ex);
        }

        final Path batchRoot = request.batchRoot.toAbsolutePath().normalize();
        final Path inputRoot = request.inputRoot.toAbsolutePath().normalize();
        final Path manifest = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(request.machineId));
        final List<ManifestRow> rows;
        try {
            rows = readMachineManifest(manifest, request.machineId, config.sha256());
        } catch (final Exception ex) {
            throw new SupervisorException(14, "invalid machine manifest: " + ex.getMessage(), ex);
        }

        final Path machineRoot = batchRoot.resolve(
                BatchPrecheckCsvSchema.machinePrecheckRoot(request.machineId));
        final Path resultsCsv = machineRoot.resolve(BatchPrecheckCsvSchema.FILE_RESULTS);
        try {
            Files.createDirectories(machineRoot.resolve(BatchPrecheckCsvSchema.DIR_JOBS));
            Files.createDirectories(machineRoot.resolve(BatchPrecheckCsvSchema.DIR_LOGS));
            Files.createDirectories(machineRoot.resolve(BatchPrecheckCsvSchema.DIR_PREVIEWS));
        } catch (final IOException ex) {
            throw new SupervisorException(16, "failed to create precheck directories: " + ex.getMessage(), ex);
        }

        final Map<String, List<String>> existingById = loadExistingRows(resultsCsv);
        final List<List<String>> aggregate = new ArrayList<List<String>>();

        int success = 0;
        int review = 0;
        int failed = 0;

        for (int i = 0; i < rows.size(); i++) {
            final ManifestRow row = rows.get(i);
            final List<String> prior = existingById.get(row.imageId);
            if (prior != null && canSkip(prior, row, config.sha256())) {
                aggregate.add(prior);
                success++;
                continue;
            }

            BatchPrecheckResult result;
            try {
                result = processOne(request, config, batchRoot, inputRoot, machineRoot, row);
            } catch (final SupervisorException ex) {
                if (ex.exitCode == 15) {
                    throw ex;
                }
                result = BatchPrecheckResult.builder()
                        .status(BatchPrecheckStatus.FAILED_UNEXPECTED)
                        .reasonCode("SUPERVISOR_IMAGE_FAILURE")
                        .message(ex.getMessage())
                        .imageId(row.imageId)
                        .machineId(request.machineId)
                        .sourceFilename(row.sourceFilename)
                        .sourceRelativePath(row.sourceRelativePath)
                        .sourceSha256(row.sourceSha256)
                        .configurationSha256(config.sha256())
                        .build();
            }

            final List<String> csvRow = result.toCsvRow(row.orderIndex);
            aggregate.add(csvRow);
            existingById.put(row.imageId, csvRow);
            rewriteCsvAtomically(resultsCsv, aggregate);

            if (result.status().isSuccess()) {
                success++;
            } else if (result.status().isReviewRequired()) {
                review++;
                if (!config.precheckContinueAfterFailure()) {
                    // review is not a hard failure; continue always for review
                }
            } else {
                failed++;
                if (!config.precheckContinueAfterFailure()) {
                    break;
                }
            }
        }

        final int exit;
        if (failed > 0) {
            exit = 11;
        } else if (review > 0) {
            exit = 10;
        } else {
            exit = 0;
        }
        return new Summary(rows.size(), success, review, failed, resultsCsv, exit);
    }

    private static BatchPrecheckResult processOne(
            final Request request,
            final BatchConfiguration config,
            final Path batchRoot,
            final Path inputRoot,
            final Path machineRoot,
            final ManifestRow row) throws SupervisorException {
        final Path source = inputRoot.resolve(row.sourceRelativePath).toAbsolutePath().normalize();
        if (!source.startsWith(inputRoot) || !Files.isRegularFile(source)) {
            return BatchPrecheckResult.builder()
                    .status(BatchPrecheckStatus.INVALID_SOURCE)
                    .reasonCode("SOURCE_MISSING")
                    .message("source TIFF missing: " + source)
                    .imageId(row.imageId)
                    .machineId(request.machineId)
                    .sourceFilename(row.sourceFilename)
                    .sourceRelativePath(row.sourceRelativePath)
                    .sourceSha256(row.sourceSha256)
                    .configurationSha256(config.sha256())
                    .build();
        }
        try {
            final String actual = Sha256Files.sha256OfFile(source);
            if (!actual.equalsIgnoreCase(row.sourceSha256)) {
                return BatchPrecheckResult.builder()
                        .status(BatchPrecheckStatus.STALE_SOURCE)
                        .reasonCode("STALE_SOURCE")
                        .message("source SHA-256 mismatch before Fiji launch")
                        .imageId(row.imageId)
                        .machineId(request.machineId)
                        .sourceFilename(row.sourceFilename)
                        .sourceRelativePath(row.sourceRelativePath)
                        .sourceSha256(row.sourceSha256)
                        .configurationSha256(config.sha256())
                        .build();
            }
        } catch (final IOException ex) {
            throw new SupervisorException(16, "failed hashing source: " + ex.getMessage(), ex);
        }

        final Path jobDir = machineRoot.resolve(BatchPrecheckCsvSchema.DIR_JOBS).resolve(row.imageId);
        final Path logsDir = machineRoot.resolve(BatchPrecheckCsvSchema.DIR_LOGS);
        final Path previewsDir = machineRoot.resolve(BatchPrecheckCsvSchema.DIR_PREVIEWS);
        try {
            Files.createDirectories(jobDir);
            Files.createDirectories(logsDir);
            Files.createDirectories(previewsDir);
        } catch (final IOException ex) {
            throw new SupervisorException(16, ex.getMessage(), ex);
        }

        final Path jobPath = jobDir.resolve("job.properties");
        final Path resultPath = jobDir.resolve("result.properties");
        final Path stdoutPath = logsDir.resolve(row.imageId + "__stdout.log");
        final Path stderrPath = logsDir.resolve(row.imageId + "__stderr.log");
        final Path maskPreview = previewsDir.resolve(row.imageId + "__mask_preview.png");
        final Path scalePreview = previewsDir.resolve(row.imageId + "__scale_bar_preview.png");

        final BatchPrecheckJob job = new BatchPrecheckJob(
                1,
                SixDigitSampleId.parse(row.imageId),
                request.machineId,
                source,
                row.sourceRelativePath,
                row.sourceSha256.toLowerCase(Locale.ROOT),
                request.configPath.toAbsolutePath().normalize(),
                config.sha256().toLowerCase(Locale.ROOT),
                batchRoot,
                resultPath,
                stdoutPath,
                stderrPath,
                maskPreview,
                scalePreview);
        try {
            Files.deleteIfExists(resultPath);
            job.write(jobPath);
        } catch (final IOException ex) {
            throw new SupervisorException(16, "failed writing job: " + ex.getMessage(), ex);
        }

        final List<String> command = buildFijiCommand(request.fijiLauncher, jobPath);
        final Process process;
        try {
            process = request.launcher.start(command);
        } catch (final IOException ex) {
            throw new SupervisorException(15, "Fiji launch failure: " + ex.getMessage(), ex);
        }

        final StreamGobbler outGobbler = new StreamGobbler(process.getInputStream());
        final StreamGobbler errGobbler = new StreamGobbler(process.getErrorStream());
        outGobbler.start();
        errGobbler.start();

        final long timeoutMs = TimeUnit.MINUTES.toMillis(config.precheckTimeoutMinutes());
        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            throw new SupervisorException(16, "interrupted while waiting for Fiji", ex);
        }
        if (!finished) {
            destroyProcess(process);
            writeLog(stdoutPath, outGobbler);
            writeLog(stderrPath, errGobbler);
            return BatchPrecheckResult.builder()
                    .status(BatchPrecheckStatus.FAILED_TIMEOUT)
                    .reasonCode("TIMEOUT")
                    .message("Fiji precheck exceeded timeout of "
                            + config.precheckTimeoutMinutes() + " minutes")
                    .imageId(row.imageId)
                    .machineId(request.machineId)
                    .sourceFilename(row.sourceFilename)
                    .sourceRelativePath(row.sourceRelativePath)
                    .sourceSha256(row.sourceSha256)
                    .configurationSha256(config.sha256())
                    .build();
        }
        writeLog(stdoutPath, outGobbler);
        writeLog(stderrPath, errGobbler);

        if (!Files.isRegularFile(resultPath)) {
            return BatchPrecheckResult.builder()
                    .status(BatchPrecheckStatus.FAILED_UNEXPECTED)
                    .reasonCode("RESULT_MISSING")
                    .message("Fiji exited without writing result.properties; exit="
                            + process.exitValue())
                    .imageId(row.imageId)
                    .machineId(request.machineId)
                    .sourceFilename(row.sourceFilename)
                    .sourceRelativePath(row.sourceRelativePath)
                    .sourceSha256(row.sourceSha256)
                    .configurationSha256(config.sha256())
                    .build();
        }
        try {
            return BatchPrecheckResult.load(resultPath);
        } catch (final Exception ex) {
            return BatchPrecheckResult.builder()
                    .status(BatchPrecheckStatus.FAILED_UNEXPECTED)
                    .reasonCode("RESULT_INVALID")
                    .message(ex.getMessage())
                    .imageId(row.imageId)
                    .machineId(request.machineId)
                    .sourceFilename(row.sourceFilename)
                    .sourceRelativePath(row.sourceRelativePath)
                    .sourceSha256(row.sourceSha256)
                    .configurationSha256(config.sha256())
                    .build();
        }
    }

    public static List<String> buildFijiCommand(final Path fijiLauncher, final Path jobPath) {
        final List<String> command = new ArrayList<String>();
        command.add(fijiLauncher.toAbsolutePath().normalize().toString());
        command.add("--headless");
        command.add("--no-splash");
        command.add("--allow-multiple");
        command.add("--full-classpath");
        command.add("--main-class");
        command.add("vesselreconstruction.ellipse.BatchPrecheckImagePlugin");
        command.add(jobPath.toAbsolutePath().normalize().toString());
        return command;
    }

    private static void destroyProcess(final Process process) {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void writeLog(final Path path, final StreamGobbler gobbler) {
        try {
            gobbler.join(5000L);
            Files.createDirectories(path.getParent());
            Sha256Files.writeBytesAtomically(path, gobbler.bytes());
        } catch (final Exception ignored) {
            // best effort
        }
    }

    private static void rewriteCsvAtomically(
            final Path resultsCsv,
            final List<List<String>> rows) throws SupervisorException {
        final byte[] previous = readIfExists(resultsCsv);
        try {
            ProductionCsvStore.writeTableAtomically(
                    resultsCsv, BatchPrecheckCsvSchema.HEADER, rows);
            final List<List<String>> parsed = ProductionCsvStore.readTable(resultsCsv);
            if (parsed.isEmpty() || !parsed.get(0).equals(BatchPrecheckCsvSchema.HEADER)) {
                throw new IOException("precheck CSV header revalidation failed");
            }
            if (parsed.size() - 1 != rows.size()) {
                throw new IOException("precheck CSV row-count revalidation failed");
            }
        } catch (final IOException ex) {
            restoreBytes(resultsCsv, previous);
            throw new SupervisorException(16, "atomic CSV update failed: " + ex.getMessage(), ex);
        }
    }

    private static byte[] readIfExists(final Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (final IOException ignored) {
            // absent
        }
        return null;
    }

    private static void restoreBytes(final Path path, final byte[] previous) {
        try {
            if (previous == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, previous);
            }
        } catch (final IOException ignored) {
            // best effort
        }
    }

    private static Map<String, List<String>> loadExistingRows(final Path resultsCsv)
            throws SupervisorException {
        final Map<String, List<String>> map = new HashMap<String, List<String>>();
        if (!Files.isRegularFile(resultsCsv)) {
            return map;
        }
        try {
            final List<List<String>> table = ProductionCsvStore.readTable(resultsCsv);
            if (table.isEmpty()) {
                return map;
            }
            if (!table.get(0).equals(BatchPrecheckCsvSchema.HEADER)) {
                throw new SupervisorException(16, "existing precheck CSV has wrong header");
            }
            for (int i = 1; i < table.size(); i++) {
                final List<String> row = table.get(i);
                if (row.size() > 2) {
                    map.put(row.get(2), row);
                }
            }
            return map;
        } catch (final SupervisorException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw new SupervisorException(16, "failed reading existing precheck CSV: "
                    + ex.getMessage(), ex);
        }
    }

    private static boolean canSkip(
            final List<String> prior,
            final ManifestRow row,
            final String configSha) {
        if (prior.size() < BatchPrecheckCsvSchema.HEADER.size()) {
            return false;
        }
        final String status = prior.get(8);
        final String sourceSha = prior.get(6);
        final String confSha = prior.get(7);
        return BatchPrecheckStatus.PRECHECK_SUCCESS.name().equals(status)
                && row.sourceSha256.equalsIgnoreCase(sourceSha)
                && configSha.equalsIgnoreCase(confSha);
    }

    static List<ManifestRow> readMachineManifest(
            final Path manifest,
            final String machineId,
            final String expectedConfigSha) throws IOException {
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("machine manifest missing: " + manifest);
        }
        final List<List<String>> table = ProductionCsvStore.readTable(manifest);
        if (table.isEmpty() || !table.get(0).equals(BatchManifestSchema.HEADER)) {
            throw new IllegalArgumentException("machine manifest header mismatch");
        }
        final List<ManifestRow> rows = new ArrayList<ManifestRow>();
        for (int i = 1; i < table.size(); i++) {
            final List<String> r = table.get(i);
            if (r.size() < BatchManifestSchema.HEADER.size()) {
                throw new IllegalArgumentException("short manifest row at " + i);
            }
            final String assigned = r.get(10);
            if (!machineId.equals(assigned)) {
                throw new IllegalArgumentException(
                        "manifest row assigned to " + assigned + " not " + machineId);
            }
            final String configSha = r.get(20);
            if (!expectedConfigSha.equalsIgnoreCase(configSha)) {
                throw new IllegalArgumentException(
                        "manifest configuration SHA-256 mismatch for image " + r.get(2));
            }
            rows.add(new ManifestRow(
                    Integer.parseInt(r.get(1)),
                    r.get(2),
                    r.get(6),
                    r.get(7).replace('\\', '/'),
                    r.get(8).toLowerCase(Locale.ROOT)));
        }
        return rows;
    }

    static final class ManifestRow {
        final int orderIndex;
        final String imageId;
        final String sourceFilename;
        final String sourceRelativePath;
        final String sourceSha256;

        ManifestRow(
                final int orderIndex,
                final String imageId,
                final String sourceFilename,
                final String sourceRelativePath,
                final String sourceSha256) {
            this.orderIndex = orderIndex;
            this.imageId = imageId;
            this.sourceFilename = sourceFilename;
            this.sourceRelativePath = sourceRelativePath;
            this.sourceSha256 = sourceSha256;
        }
    }

    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamGobbler(final InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            final byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    if (n > 0) {
                        buffer.write(chunk, 0, n);
                    }
                }
            } catch (final IOException ignored) {
                // process ended
            }
        }

        byte[] bytes() {
            return buffer.toByteArray();
        }
    }
}
