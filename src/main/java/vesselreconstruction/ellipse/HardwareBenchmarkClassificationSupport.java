package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Prepares an isolated classification workspace and reuses the production
 * Fiji classification launch path.
 */
public final class HardwareBenchmarkClassificationSupport {

    public static final class PreparedRun {
        public final Path runDir;
        public final Path inputDir;
        public final Path inputTiff;
        public final Path configPath;
        public final Path batchRoot;
        public final Path datasetRoot;
        public final Path jobPath;
        public final Path resultPath;
        public final Path stdoutPath;
        public final Path stderrPath;
        public final List<String> command;
        public final String configSha256;
        public final String classifierSha256;
        public final String inputSha256;
        public final BatchTileProfile tileProfile;

        public PreparedRun(
                final Path runDir,
                final Path inputDir,
                final Path inputTiff,
                final Path configPath,
                final Path batchRoot,
                final Path datasetRoot,
                final Path jobPath,
                final Path resultPath,
                final Path stdoutPath,
                final Path stderrPath,
                final List<String> command,
                final String configSha256,
                final String classifierSha256,
                final String inputSha256,
                final BatchTileProfile tileProfile) {
            this.runDir = runDir;
            this.inputDir = inputDir;
            this.inputTiff = inputTiff;
            this.configPath = configPath;
            this.batchRoot = batchRoot;
            this.datasetRoot = datasetRoot;
            this.jobPath = jobPath;
            this.resultPath = resultPath;
            this.stdoutPath = stdoutPath;
            this.stderrPath = stderrPath;
            this.command = command;
            this.configSha256 = configSha256;
            this.classifierSha256 = classifierSha256;
            this.inputSha256 = inputSha256;
            this.tileProfile = tileProfile;
        }
    }

    public static final class OutputCheck {
        public final boolean valid;
        public final String reason;

        public OutputCheck(final boolean valid, final String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    private HardwareBenchmarkClassificationSupport() {
    }

    public static PreparedRun prepare(
            final Path runDir,
            final HardwareBenchmarkWorkload.Prepared workload,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final Path configTemplate,
            final Path classifier,
            final Path fijiLauncher) throws Exception {
        return prepare(runDir, workload, candidate, configTemplate, classifier, fijiLauncher, null);
    }

    public static PreparedRun prepare(
            final Path runDir,
            final HardwareBenchmarkWorkload.Prepared workload,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final Path configTemplate,
            final Path classifier,
            final Path fijiLauncher,
            final Path pluginsDir) throws Exception {
        Files.createDirectories(runDir);
        final Path inputDir = runDir.resolve("input");
        final Path outputDir = runDir.resolve("output");
        final Path logsDir = runDir.resolve("logs");
        final Path metricsDir = runDir.resolve("metrics");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
        Files.createDirectories(logsDir);
        Files.createDirectories(metricsDir);
        writeText(runDir.resolve("DISCLAIMER.txt"), HardwareBenchmarkWorkload.DISCLAIMER + "\n");

        final Path inputTiff = inputDir.resolve(HardwareBenchmarkWorkload.SOURCE_FILENAME);
        Files.copy(workload.workloadTiff, inputTiff, StandardCopyOption.REPLACE_EXISTING);
        final String inputSha = Sha256Files.sha256OfFile(inputTiff);

        final Path configPath = runDir.resolve("production_batch.properties");
        writeConfigWithThreads(configTemplate, configPath, candidate.threadsPerTile);
        final BatchConfiguration config = BatchConfiguration.load(configPath);
        HardwareExecutionProfile.requireProductionTileProfile(config.tileProfiles());
        final BatchTileProfile tile = HardwareExecutionProfile.requireProductionTileProfile(
                config.tileProfiles());
        if (tile.widthPx() != HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX
                || tile.heightPx() != HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX) {
            throw new IllegalStateException("tile geometry must remain 1024x1024");
        }

        final Path batchRoot = runDir.resolve("batch");
        final Path datasetRoot = outputDir.resolve("dataset");
        new ProductionDatasetLayout(datasetRoot).ensureDirectoriesExist();

        BatchManifestPlanner.plan(new BatchManifestPlanner.PlanRequest(
                inputDir, batchRoot, configPath));

        final BatchPrecheckSupervisor.Summary precheck = BatchPrecheckSupervisor.run(
                new BatchPrecheckSupervisor.Request(
                        inputDir,
                        batchRoot,
                        HardwareExecutionProfile.PRODUCTION_MACHINE_ID,
                        configPath,
                        fijiLauncher,
                        new HeapProcessLauncher(candidate.fijiHeapMb, pluginsDir)));
        if (precheck.success < 1) {
            throw new IllegalStateException(
                    "precheck did not succeed for benchmark workload (success="
                            + precheck.success
                            + " review=" + precheck.reviewRequired
                            + " failed=" + precheck.failed + ")");
        }

        final Path precheckResult = batchRoot
                .resolve(BatchPrecheckCsvSchema.machinePrecheckRoot(
                        HardwareExecutionProfile.PRODUCTION_MACHINE_ID))
                .resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(HardwareBenchmarkWorkload.IMAGE_ID)
                .resolve("result.properties");
        final Path precheckCsv = batchRoot
                .resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath(
                        HardwareExecutionProfile.PRODUCTION_MACHINE_ID));
        final Path classificationDir = batchRoot.resolve("classification");
        Files.createDirectories(classificationDir);
        Files.createDirectories(batchRoot.resolve("logs"));
        final Path jobPath = classificationDir.resolve("job.properties");
        final Path resultPath = classificationDir.resolve("result.properties");
        final Path stdoutPath = batchRoot.resolve("logs").resolve("classification_stdout.log");
        final Path stderrPath = batchRoot.resolve("logs").resolve("classification_stderr.log");
        final String classifierSha = Sha256Files.sha256OfFile(classifier);
        final BatchFullProcessingJob job = new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse(HardwareBenchmarkWorkload.IMAGE_ID),
                HardwareExecutionProfile.PRODUCTION_MACHINE_ID,
                inputTiff.toAbsolutePath().normalize(),
                HardwareBenchmarkWorkload.SOURCE_FILENAME,
                inputSha,
                configPath.toAbsolutePath().normalize(),
                config.sha256(),
                classifier.toAbsolutePath().normalize(),
                classifierSha,
                HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_INDEX,
                tile,
                1,
                batchRoot.toAbsolutePath().normalize(),
                datasetRoot.toAbsolutePath().normalize(),
                precheckResult.toAbsolutePath().normalize(),
                precheckCsv.toAbsolutePath().normalize(),
                resultPath.toAbsolutePath().normalize(),
                stdoutPath.toAbsolutePath().normalize(),
                stderrPath.toAbsolutePath().normalize(),
                BatchFullProcessingStage.CLASSIFICATION);
        job.write(jobPath);
        writeText(metricsDir.resolve("classifier.sha256"), classifierSha + "  " + classifier.getFileName() + "\n");
        writeText(metricsDir.resolve("tile.txt"),
                HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN + "\n");

        final List<String> command = HardwareBenchmarkFijiCommands.classificationCommand(
                fijiLauncher, jobPath, candidate.fijiHeapMb, pluginsDir);
        return new PreparedRun(
                runDir, inputDir, inputTiff, configPath, batchRoot, datasetRoot,
                jobPath, resultPath, stdoutPath, stderrPath, command,
                config.sha256(), classifierSha, inputSha, tile);
    }

    /**
     * Runs production precheck only, to decide whether a cropped TEST
     * workload still has a usable scale bar.
     */
    static BatchPrecheckSupervisor.Summary probePrecheck(
            final Path probeDir,
            final HardwareBenchmarkWorkload.Prepared workload,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final Path configTemplate,
            final Path fijiLauncher) throws Exception {
        return probePrecheck(probeDir, workload, candidate, configTemplate, fijiLauncher, null);
    }

    static BatchPrecheckSupervisor.Summary probePrecheck(
            final Path probeDir,
            final HardwareBenchmarkWorkload.Prepared workload,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final Path configTemplate,
            final Path fijiLauncher,
            final Path pluginsDir) throws Exception {
        Files.createDirectories(probeDir);
        writeText(probeDir.resolve("DISCLAIMER.txt"), HardwareBenchmarkWorkload.DISCLAIMER + "\n");
        final Path inputDir = probeDir.resolve("input");
        Files.createDirectories(inputDir);
        final Path inputTiff = inputDir.resolve(HardwareBenchmarkWorkload.SOURCE_FILENAME);
        Files.copy(workload.workloadTiff, inputTiff, StandardCopyOption.REPLACE_EXISTING);
        final Path configPath = probeDir.resolve("production_batch.properties");
        writeConfigWithThreads(configTemplate, configPath, candidate.threadsPerTile);
        final Path batchRoot = probeDir.resolve("batch");
        BatchManifestPlanner.plan(new BatchManifestPlanner.PlanRequest(
                inputDir, batchRoot, configPath));
        return BatchPrecheckSupervisor.run(new BatchPrecheckSupervisor.Request(
                inputDir,
                batchRoot,
                HardwareExecutionProfile.PRODUCTION_MACHINE_ID,
                configPath,
                fijiLauncher,
                new HeapProcessLauncher(candidate.fijiHeapMb, pluginsDir)));
    }

    public static OutputCheck validateOutput(
            final Path datasetRoot,
            final Path resultPath,
            final String sourceSha256,
            final int exitCode) {
        if (exitCode != 0) {
            return new OutputCheck(false, "non-zero Fiji exit " + exitCode);
        }
        if (!Files.isRegularFile(resultPath)) {
            return new OutputCheck(false, "PROCESS_EXITED_BUT_OUTPUT_INVALID: missing result.properties");
        }
        try {
            final BatchFullProcessingStageResult stage = BatchFullProcessingStageResult.read(resultPath);
            if (stage.status() != BatchFullProcessingStatus.FULL_SUCCESS) {
                return new OutputCheck(false,
                        "PROCESS_EXITED_BUT_OUTPUT_INVALID: " + stage.status().name()
                                + " " + stage.reasonCode());
            }
        } catch (final Exception ex) {
            return new OutputCheck(false,
                    "PROCESS_EXITED_BUT_OUTPUT_INVALID: unreadable result " + ex.getMessage());
        }
        final BatchProductionOutputValidator.ValidationResult validation =
                BatchProductionOutputValidator.validateClassificationOnly(
                        datasetRoot, HardwareBenchmarkWorkload.IMAGE_ID, sourceSha256);
        if (!validation.ok) {
            return new OutputCheck(false,
                    "PROCESS_EXITED_BUT_OUTPUT_INVALID: " + join(validation.failures));
        }
        return new OutputCheck(true, "SUCCESSFUL_CLASSIFICATION");
    }

    static void writeConfigWithThreads(
            final Path template,
            final Path dest,
            final int threadsPerTile) throws IOException {
        final List<String> lines = Files.readAllLines(template, StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder();
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line.startsWith(BatchConfiguration.KEY_CLASSIFICATION_THREADS_PER_TILE + "=")) {
                sb.append(BatchConfiguration.KEY_CLASSIFICATION_THREADS_PER_TILE)
                        .append('=').append(threadsPerTile).append('\n');
                replaced = true;
            } else {
                sb.append(line).append('\n');
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException(
                    "config template missing " + BatchConfiguration.KEY_CLASSIFICATION_THREADS_PER_TILE);
        }
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        Sha256Files.writeBytesAtomically(dest, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String join(final List<String> failures) {
        if (failures == null || failures.isEmpty()) {
            return "classification outputs invalid";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(failures.get(i));
        }
        return sb.toString();
    }

    static void writeText(final Path path, final String text) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Sha256Files.writeBytesAtomically(path, text.getBytes(StandardCharsets.UTF_8));
    }

    static final class HeapProcessLauncher implements BatchPrecheckSupervisor.ProcessLauncher {
        private final int heapMb;
        private final Path pluginsDir;

        HeapProcessLauncher(final int heapMb) {
            this(heapMb, null);
        }

        HeapProcessLauncher(final int heapMb, final Path pluginsDir) {
            this.heapMb = heapMb;
            this.pluginsDir = pluginsDir;
        }

        @Override
        public Process start(final List<String> command) throws IOException {
            return new ProcessBuilder(HardwareBenchmarkFijiCommands.withLaunchOptions(
                    command, heapMb, pluginsDir))
                    .redirectErrorStream(false)
                    .start();
        }
    }
}
