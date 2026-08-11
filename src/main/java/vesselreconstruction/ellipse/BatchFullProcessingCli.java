package vesselreconstruction.ellipse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java CLI orchestrating the two-process Phase-3 architecture for a
 * single image: a classification-stage Fiji process and a
 * reconstruction-stage Fiji process, each launched fresh so that an
 * out-of-memory reconstruction can never destroy an already-committed
 * classification.
 *
 * <p>Flow for one invocation:</p>
 *
 * <ol>
 * <li>Parse and validate arguments, the machine manifest row, source and
 * classifier hashes, and the tile profile (unchanged from the single-process
 * CLI).</li>
 * <li>Determine the next attempt number by scanning
 * {@code full/<machine>/<image>/attempt_*} and create
 * {@code attempt_<N>/}.</li>
 * <li>Ask {@link BatchClassificationResumeInspector} whether the production
 * dataset already has a valid, matching classification for this image. If
 * so, the classification Fiji process is skipped entirely.</li>
 * <li>Otherwise, write {@code attempt_<N>/classification/job.properties}
 * with {@code processing_stage=CLASSIFICATION} and launch a fresh Fiji
 * process for it.</li>
 * <li>Always, after the classification stage resolves (by reuse or by a
 * fresh run), write {@code attempt_<N>/reconstruction/job.properties} with
 * {@code processing_stage=RECONSTRUCTION} and launch a second, fresh Fiji
 * process for it.</li>
 * <li>Always write a combined {@code attempt_<N>/result.properties}
 * describing both stages, even when one or both stages failed, OOM'd, or
 * never wrote their own {@code result.properties}.</li>
 * </ol>
 */
public final class BatchFullProcessingCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_PRECHECK_NOT_READY = 20;
    public static final int EXIT_INVALID_INPUT = 21;
    public static final int EXIT_CLASSIFICATION_FAILURE = 22;
    public static final int EXIT_RECONSTRUCTION_FAILURE = 23;
    public static final int EXIT_OUTPUT_VALIDATION_FAILURE = 24;
    public static final int EXIT_CANONICAL_CONFLICT = 25;
    public static final int EXIT_OUT_OF_MEMORY = 26;
    public static final int EXIT_TIMEOUT = 27;
    public static final int EXIT_UNEXPECTED = 28;

    private static final Pattern ATTEMPT_DIR_PATTERN = Pattern.compile("attempt_(\\d+)");

    public interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    private static final class DefaultProcessLauncher implements ProcessLauncher {
        @Override
        public Process start(final List<String> command) throws IOException {
            return new ProcessBuilder(command).redirectErrorStream(false).start();
        }
    }

    private BatchFullProcessingCli() {
    }

    public static void main(final String[] args) {
        try {
            System.exit(run(args, new DefaultProcessLauncher()));
        } catch (final LinkageError err) {
            System.err.println("ERROR: parent linkage failure: " + err);
            System.exit(EXIT_UNEXPECTED);
        }
    }

    /**
     * Maps a terminal {@link BatchFullProcessingStatus} to the CLI's stable
     * exit-code contract. {@code FAILED_MASK_REPRODUCTION} and
     * {@code FAILED_SCALE_BAR_REPRODUCTION} indicate that the source image no
     * longer reproduces the automatically-approved precheck geometry, which is
     * treated as invalid input (21) rather than a classification/reconstruction
     * failure.
     */
    static int mapExitCode(final BatchFullProcessingStatus status) {
        if (status == null) {
            return EXIT_UNEXPECTED;
        }
        switch (status) {
            case FULL_SUCCESS:
                return EXIT_SUCCESS;
            case PRECHECK_NOT_FOUND:
            case PRECHECK_NOT_SUCCESSFUL:
            case PRECHECK_STALE_SOURCE:
            case PRECHECK_STALE_CONFIGURATION:
                return EXIT_PRECHECK_NOT_READY;
            case INVALID_JOB:
            case INVALID_SOURCE:
            case INVALID_CLASSIFIER:
            case FAILED_MASK_REPRODUCTION:
            case FAILED_SCALE_BAR_REPRODUCTION:
                return EXIT_INVALID_INPUT;
            case FAILED_CLASSIFICATION:
            case FAILED_CLASSIFICATION_SAVE:
                return EXIT_CLASSIFICATION_FAILURE;
            case FAILED_RECONSTRUCTION:
            case FAILED_RECONSTRUCTION_SAVE:
                return EXIT_RECONSTRUCTION_FAILURE;
            case FAILED_OUTPUT_VALIDATION:
                return EXIT_OUTPUT_VALIDATION_FAILURE;
            case CANONICAL_CONFLICT:
                return EXIT_CANONICAL_CONFLICT;
            case FAILED_OUT_OF_MEMORY:
                return EXIT_OUT_OF_MEMORY;
            case FAILED_TIMEOUT:
                return EXIT_TIMEOUT;
            case FAILED_UNEXPECTED:
            default:
                return EXIT_UNEXPECTED;
        }
    }

    static int run(final String[] args, final ProcessLauncher launcher) {
        final Map<String, String> values;
        try {
            values = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_INVALID_INPUT;
        }

        final Path inputRoot = Paths.get(values.get("--input-root")).toAbsolutePath().normalize();
        final Path batchRoot = Paths.get(values.get("--batch-root")).toAbsolutePath().normalize();
        final String machineId = values.get("--machine-id").trim();
        final String imageId = values.get("--image-id").trim();
        final Path configPath = Paths.get(values.get("--config")).toAbsolutePath().normalize();
        final Path classifierPath = Paths.get(values.get("--classifier")).toAbsolutePath().normalize();
        final Path fijiLauncher = Paths.get(values.get("--fiji-launcher")).toAbsolutePath().normalize();
        final int tileProfileIndex;
        try {
            tileProfileIndex = Integer.parseInt(values.get("--tile-profile-index").trim());
        } catch (final NumberFormatException ex) {
            System.err.println("ERROR: --tile-profile-index must be an integer");
            return EXIT_INVALID_INPUT;
        }

        final BatchConfiguration config;
        try {
            config = BatchConfiguration.load(configPath);
        } catch (final Exception ex) {
            System.err.println("ERROR: invalid configuration: " + ex.getMessage());
            return EXIT_INVALID_INPUT;
        }

        final BatchPrecheckSupervisor.ManifestRow row;
        try {
            final Path manifest = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                    .resolve(BatchManifestSchema.machineManifestFilename(machineId));
            row = findManifestRow(manifest, machineId, imageId, config.sha256());
        } catch (final Exception ex) {
            System.err.println("ERROR: invalid machine manifest: " + ex.getMessage());
            return EXIT_INVALID_INPUT;
        }

        final Path source = inputRoot.resolve(row.sourceRelativePath).toAbsolutePath().normalize();
        if (!source.startsWith(inputRoot) || !Files.isRegularFile(source)) {
            System.err.println("ERROR: source TIFF missing on machine: " + source);
            return EXIT_INVALID_INPUT;
        }
        final String sourceSha;
        try {
            sourceSha = Sha256Files.sha256OfFile(source);
            if (!sourceSha.equalsIgnoreCase(row.sourceSha256)) {
                System.err.println("ERROR: source SHA-256 mismatch before launch");
                return EXIT_INVALID_INPUT;
            }
        } catch (final IOException ex) {
            System.err.println("ERROR: failed hashing source: " + ex.getMessage());
            return EXIT_INVALID_INPUT;
        }

        if (!Files.isRegularFile(classifierPath)) {
            System.err.println("ERROR: classifier file missing: " + classifierPath);
            return EXIT_INVALID_INPUT;
        }
        final String classifierSha;
        try {
            classifierSha = Sha256Files.sha256OfFile(classifierPath);
        } catch (final IOException ex) {
            System.err.println("ERROR: failed hashing classifier: " + ex.getMessage());
            return EXIT_INVALID_INPUT;
        }

        final List<BatchTileProfile> tileProfiles = config.tileProfiles();
        if (tileProfileIndex < 0 || tileProfileIndex >= tileProfiles.size()) {
            System.err.println("ERROR: --tile-profile-index out of range [0,"
                    + (tileProfiles.size() - 1) + "]: " + tileProfileIndex);
            return EXIT_INVALID_INPUT;
        }
        final BatchTileProfile tileProfile = tileProfiles.get(tileProfileIndex);

        final Path datasetRoot = batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(machineId)).toAbsolutePath().normalize();
        final Path imageAttemptsRoot = batchRoot.resolve("full").resolve(machineId).resolve(imageId);
        final int attemptNumber;
        try {
            attemptNumber = nextAttemptNumber(imageAttemptsRoot);
        } catch (final IOException ex) {
            System.err.println("ERROR: failed scanning previous attempts: " + ex.getMessage());
            return EXIT_UNEXPECTED;
        }
        final Path attemptRoot = imageAttemptsRoot.resolve("attempt_" + attemptNumber);
        try {
            Files.createDirectories(attemptRoot);
        } catch (final IOException ex) {
            System.err.println("ERROR: failed creating attempt directory: " + ex.getMessage());
            return EXIT_UNEXPECTED;
        }

        final Path precheckMachineRoot = batchRoot.resolve(
                BatchPrecheckCsvSchema.machinePrecheckRoot(machineId));
        final Path precheckResultPath = precheckMachineRoot.resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(imageId).resolve("result.properties");
        final Path precheckCsvPath = batchRoot.resolve(
                BatchPrecheckCsvSchema.resultsCsvRelativePath(machineId));

        final long overallStarted = System.currentTimeMillis();

        try {
            return runAfterAttemptCreated(
                    batchRoot, machineId, imageId, configPath, classifierPath,
                    fijiLauncher, tileProfileIndex, config, row, source, sourceSha, classifierSha,
                    tileProfile, datasetRoot, attemptNumber, attemptRoot,
                    precheckResultPath, precheckCsvPath, overallStarted, launcher);
        } catch (final LinkageError err) {
            System.err.println("ERROR: parent linkage failure: " + err);
            final BatchFullProcessingResult failure = BatchFullProcessingResult.builder()
                    .status(BatchFullProcessingStatus.FAILED_UNEXPECTED)
                    .reasonCode("PARENT_VALIDATION_FAILED")
                    .message(err.getClass().getName() + ": " + err.getMessage())
                    .imageId(imageId)
                    .machineId(machineId)
                    .sourceFilename(source.getFileName().toString())
                    .sourceSha256(sourceSha)
                    .configurationSha256(config.sha256())
                    .classifierFilename(classifierPath.getFileName().toString())
                    .classifierSha256(classifierSha)
                    .attemptNumber(Integer.valueOf(attemptNumber))
                    .tileProfile(Integer.valueOf(tileProfileIndex), Integer.valueOf(tileProfile.widthPx()),
                            Integer.valueOf(tileProfile.heightPx()))
                    .datasetRoot(datasetRoot.toString())
                    .classificationProcessLaunched(Boolean.FALSE)
                    .reconstructionProcessLaunched(Boolean.FALSE)
                    .outOfMemoryDetected(Boolean.FALSE)
                    .processingTimeMs(System.currentTimeMillis() - overallStarted)
                    .build();
            return writeCombinedAndExit(attemptRoot, failure, imageId, machineId, attemptNumber);
        }
    }

    private static int runAfterAttemptCreated(
            final Path batchRoot,
            final String machineId,
            final String imageId,
            final Path configPath,
            final Path classifierPath,
            final Path fijiLauncher,
            final int tileProfileIndex,
            final BatchConfiguration config,
            final BatchPrecheckSupervisor.ManifestRow row,
            final Path source,
            final String sourceSha,
            final String classifierSha,
            final BatchTileProfile tileProfile,
            final Path datasetRoot,
            final int attemptNumber,
            final Path attemptRoot,
            final Path precheckResultPath,
            final Path precheckCsvPath,
            final long overallStarted,
            final ProcessLauncher launcher) {
        // --- Canonical dataset inspection before any Fiji launch. ---
        final BatchProductionOutputValidator.FinalizedDatasetInspection finalized =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot,
                        imageId,
                        sourceSha,
                        classifierSha,
                        config.reconstructionMinimumLumenAreaPx(),
                        config.refreshAggregateTables());

        if (finalized.isCanonicalConflict()) {
            System.out.println("CANONICAL_CONFLICT: " + finalized.reasonCode);
            final BatchFullProcessingResult conflict = buildConflictResult(
                    imageId, machineId, source, sourceSha, config, classifierPath, classifierSha,
                    tileProfileIndex, tileProfile, attemptNumber, datasetRoot, finalized,
                    overallStarted);
            return writeCombinedAndExit(attemptRoot, conflict, imageId, machineId, attemptNumber);
        }

        if (finalized.isFinalDatasetComplete()) {
            System.out.println(
                    "FULL_DATASET_ALREADY_COMPLETE: reusing classification and reconstruction");
            final StageOutcome classification = StageOutcome.reused(finalized.classificationRunId);
            final StageOutcome reconstruction = StageOutcome.reused(finalized.reconstructionRunId);
            final BatchFullProcessingResult combined = buildCombinedResult(
                    imageId, machineId, source, sourceSha, config, classifierPath, classifierSha,
                    tileProfileIndex, tileProfile, attemptNumber, datasetRoot,
                    classification, reconstruction, overallStarted, finalized);
            return writeCombinedAndExit(attemptRoot, combined, imageId, machineId, attemptNumber);
        }

        // --- Classification stage: reuse from dataset when valid, else run fresh. ---
        final BatchClassificationResumeInspector.Inspection inspection =
                BatchClassificationResumeInspector.inspect(datasetRoot, imageId, sourceSha, classifierSha);

        final StageOutcome classification;
        if (inspection.resumable) {
            System.out.println("CLASSIFICATION_ALREADY_COMPLETE: reusing dataset classification "
                    + "(classification_run_id=" + inspection.classificationRunId + ")");
            classification = StageOutcome.reused(inspection.classificationRunId);
        } else {
            System.out.println("CLASSIFICATION_STAGE_REQUIRED: " + inspection.reasonCode);
            final Path classificationDir = attemptRoot.resolve("classification");
            final Path classificationJobPath = classificationDir.resolve("job.properties");
            final Path classificationResultPath = classificationDir.resolve("result.properties");
            final Path classificationStdout = classificationDir.resolve("stdout.log");
            final Path classificationStderr = classificationDir.resolve("stderr.log");
            StageOutcome outcome;
            try {
                Files.createDirectories(classificationDir);
                Files.deleteIfExists(classificationResultPath);
                final BatchFullProcessingJob classificationJob = buildStageJob(
                        imageId, machineId, source, row.sourceRelativePath, sourceSha, configPath,
                        config.sha256(), classifierPath, classifierSha, tileProfileIndex, tileProfile,
                        attemptNumber, batchRoot, datasetRoot, precheckResultPath, precheckCsvPath,
                        classificationResultPath, classificationStdout, classificationStderr,
                        BatchFullProcessingStage.CLASSIFICATION);
                classificationJob.write(classificationJobPath);
                outcome = launchStage(launcher, fijiLauncher, classificationJobPath,
                        classificationJob, classificationStdout, classificationStderr,
                        config.fullTimeoutMinutes());
            } catch (final Exception ex) {
                System.err.println("ERROR: failed creating classification job: " + ex.getMessage());
                outcome = StageOutcome.notLaunched();
            }
            classification = outcome;
        }

        // --- Reconstruction stage: launched when the final dataset is not already complete. ---
        final Path reconstructionDir = attemptRoot.resolve("reconstruction");
        final Path reconstructionJobPath = reconstructionDir.resolve("job.properties");
        final Path reconstructionResultPath = reconstructionDir.resolve("result.properties");
        final Path reconstructionStdout = reconstructionDir.resolve("stdout.log");
        final Path reconstructionStderr = reconstructionDir.resolve("stderr.log");
        StageOutcome reconstruction;
        try {
            Files.createDirectories(reconstructionDir);
            Files.deleteIfExists(reconstructionResultPath);
            final BatchFullProcessingJob reconstructionJob = buildStageJob(
                    imageId, machineId, source, row.sourceRelativePath, sourceSha, configPath,
                    config.sha256(), classifierPath, classifierSha, tileProfileIndex, tileProfile,
                    attemptNumber, batchRoot, datasetRoot, precheckResultPath, precheckCsvPath,
                    reconstructionResultPath, reconstructionStdout, reconstructionStderr,
                    BatchFullProcessingStage.RECONSTRUCTION);
            reconstructionJob.write(reconstructionJobPath);
            reconstruction = launchStage(launcher, fijiLauncher, reconstructionJobPath,
                    reconstructionJob, reconstructionStdout, reconstructionStderr,
                    config.fullTimeoutMinutes());
        } catch (final Exception ex) {
            System.err.println("ERROR: failed creating reconstruction job: " + ex.getMessage());
            reconstruction = StageOutcome.notLaunched();
        }

        // --- Combined attempt-level result, written unconditionally. ---
        final BatchFullProcessingResult combined = buildCombinedResult(
                imageId, machineId, source, sourceSha, config, classifierPath, classifierSha,
                tileProfileIndex, tileProfile, attemptNumber, datasetRoot,
                classification, reconstruction, overallStarted, null);
        return writeCombinedAndExit(attemptRoot, combined, imageId, machineId, attemptNumber);
    }

    private static int writeCombinedAndExit(
            final Path attemptRoot,
            final BatchFullProcessingResult combined,
            final String imageId,
            final String machineId,
            final int attemptNumber) {
        final Path combinedResultPath = attemptRoot.resolve("result.properties");
        try {
            combined.write(combinedResultPath);
        } catch (final IOException ex) {
            System.err.println("ERROR: failed writing combined result: " + ex.getMessage());
        }
        System.out.println("image: " + imageId);
        System.out.println("machine: " + machineId);
        System.out.println("attempt: " + attemptNumber);
        System.out.println("status: " + combined.status().name());
        System.out.println("reason: " + combined.get(BatchFullProcessingResult.KEY_REASON));
        System.out.println("failed_stage: " + combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
        System.out.println("result: " + combinedResultPath);
        return mapExitCode(combined.status());
    }

    private static BatchFullProcessingJob buildStageJob(
            final String imageId,
            final String machineId,
            final Path source,
            final String sourceRelativePath,
            final String sourceSha,
            final Path configPath,
            final String configSha,
            final Path classifierPath,
            final String classifierSha,
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
            final BatchFullProcessingStage stage) {
        return new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse(imageId),
                machineId,
                source,
                sourceRelativePath,
                sourceSha.toLowerCase(Locale.ROOT),
                configPath,
                configSha.toLowerCase(Locale.ROOT),
                classifierPath,
                classifierSha.toLowerCase(Locale.ROOT),
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
                stage);
    }

    /**
     * Launches one Fiji process for a stage job, waits for it (subject to the
     * configured timeout), captures its stdout/stderr, and loads the
     * {@link BatchFullProcessingStageResult} from the job's authoritative
     * {@code result_path}. A valid structured child result is authoritative
     * even when the Fiji launcher exits with code 0. Missing/unreadable/
     * mismatched results and timeouts are reported via {@link StageOutcome}
     * rather than thrown, so the caller can always attempt the next stage and
     * always write a combined result.
     */
    private static StageOutcome launchStage(
            final ProcessLauncher launcher,
            final Path fijiLauncher,
            final Path jobPath,
            final BatchFullProcessingJob writtenJob,
            final Path stdoutPath,
            final Path stderrPath,
            final long timeoutMinutes) {
        final List<String> command = buildFijiCommand(fijiLauncher, jobPath);
        final Process process;
        try {
            process = launcher.start(command);
        } catch (final IOException ex) {
            appendBestEffort(stderrPath, "ERROR: Fiji launch failure: " + ex.getMessage());
            return StageOutcome.launchFailed();
        }

        final StreamGobbler outGobbler = new StreamGobbler(process.getInputStream());
        final StreamGobbler errGobbler = new StreamGobbler(process.getErrorStream());
        outGobbler.start();
        errGobbler.start();

        final long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        final boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            writeLog(stdoutPath, outGobbler);
            writeLog(stderrPath, errGobbler);
            return StageOutcome.launchFailed();
        }
        if (!finished) {
            destroyProcess(process);
            writeLog(stdoutPath, outGobbler);
            writeLog(stderrPath, errGobbler);
            return StageOutcome.timedOut();
        }
        writeLog(stdoutPath, outGobbler);
        writeLog(stderrPath, errGobbler);
        final Integer exitCode = Integer.valueOf(process.exitValue());

        BatchFullProcessingJob job;
        try {
            job = BatchFullProcessingJob.load(jobPath);
        } catch (final Exception ex) {
            job = writtenJob;
        }
        return loadStageOutcome(job, jobPath, exitCode, stdoutPath, stderrPath);
    }

    /**
     * Reads and validates the child-stage result from the job's
     * {@link BatchFullProcessingJob#resultPath()}. Package-visible for tests.
     */
    static StageOutcome loadStageOutcome(
            final BatchFullProcessingJob job,
            final Path jobPath,
            final Integer exitCode,
            final Path stdoutPath,
            final Path stderrPath) {
        if (job == null || job.resultPath() == null) {
            final boolean oom = fileContainsOutOfMemory(stderrPath) || fileContainsOutOfMemory(stdoutPath);
            return StageOutcome.missingResult(exitCode, oom);
        }
        final Path resultPath = job.resultPath().toAbsolutePath().normalize();
        final Path stageDir = jobPath == null
                ? resultPath.getParent()
                : jobPath.toAbsolutePath().normalize().getParent();
        if (stageDir == null || !resultPath.startsWith(stageDir)) {
            final boolean oom = fileContainsOutOfMemory(stderrPath) || fileContainsOutOfMemory(stdoutPath);
            return StageOutcome.missingResult(exitCode, oom);
        }
        if (!Files.isRegularFile(resultPath)) {
            final boolean oom = fileContainsOutOfMemory(stderrPath) || fileContainsOutOfMemory(stdoutPath);
            return StageOutcome.missingResult(exitCode, oom);
        }
        final BatchFullProcessingStageResult stageResult;
        try {
            stageResult = BatchFullProcessingStageResult.read(resultPath);
            validateStageResultIdentity(job, stageResult);
        } catch (final Exception ex) {
            final boolean oom = fileContainsOutOfMemory(stderrPath) || fileContainsOutOfMemory(stdoutPath);
            return StageOutcome.missingResult(exitCode, oom);
        }
        final boolean oom = detectOutOfMemory(stageResult, stdoutPath, stderrPath);
        return StageOutcome.withResult(exitCode, stageResult, resultPath, oom);
    }

    static void validateStageResultIdentity(
            final BatchFullProcessingJob job,
            final BatchFullProcessingStageResult stageResult) {
        if (job.processingStage() == null
                || stageResult.stage() != job.processingStage()) {
            throw new IllegalArgumentException(
                    "stage-result stage mismatch: expected " + job.processingStage()
                            + ", found " + stageResult.stage());
        }
        if (!job.imageId().imageId().equals(stageResult.imageId())) {
            throw new IllegalArgumentException(
                    "stage-result image_id mismatch: expected " + job.imageId().imageId()
                            + ", found " + stageResult.imageId());
        }
        if (!job.machineId().equals(stageResult.machineId())) {
            throw new IllegalArgumentException(
                    "stage-result machine_id mismatch: expected " + job.machineId()
                            + ", found " + stageResult.machineId());
        }
        final String expectedAttempt = Integer.toString(job.attemptNumber());
        if (!expectedAttempt.equals(stageResult.attemptNumber())) {
            throw new IllegalArgumentException(
                    "stage-result attempt_number mismatch: expected " + expectedAttempt
                            + ", found " + stageResult.attemptNumber());
        }
    }

    private static BatchFullProcessingResult buildConflictResult(
            final String imageId,
            final String machineId,
            final Path source,
            final String sourceSha,
            final BatchConfiguration config,
            final Path classifierPath,
            final String classifierSha,
            final int tileProfileIndex,
            final BatchTileProfile tileProfile,
            final int attemptNumber,
            final Path datasetRoot,
            final BatchProductionOutputValidator.FinalizedDatasetInspection finalized,
            final long overallStarted) {
        return BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.CANONICAL_CONFLICT)
                .reasonCode(finalized.reasonCode == null ? "CANONICAL_CONFLICT" : finalized.reasonCode)
                .message(finalized.failures.isEmpty()
                        ? "canonical conflict detected in production dataset"
                        : finalized.failures.get(0))
                .imageId(imageId)
                .machineId(machineId)
                .sourceFilename(source.getFileName().toString())
                .sourceSha256(sourceSha)
                .configurationSha256(config.sha256())
                .classifierFilename(classifierPath.getFileName().toString())
                .classifierSha256(classifierSha)
                .attemptNumber(Integer.valueOf(attemptNumber))
                .tileProfile(Integer.valueOf(tileProfileIndex), Integer.valueOf(tileProfile.widthPx()),
                        Integer.valueOf(tileProfile.heightPx()))
                .datasetRoot(datasetRoot.toString())
                .failedStage(RCsvSupport.NA)
                .classificationStageReused(Boolean.FALSE)
                .classificationProcessLaunched(Boolean.FALSE)
                .classificationCompleted(Boolean.FALSE)
                .classificationOutputsValidated(Boolean.FALSE)
                .reconstructionProcessLaunched(Boolean.FALSE)
                .reconstructionCompleted(Boolean.FALSE)
                .reconstructionOutputsValidated(Boolean.FALSE)
                .requiredOutputsValidated(Boolean.FALSE)
                .sourceUnchanged(Boolean.TRUE)
                .outOfMemoryDetected(Boolean.FALSE)
                .processingTimeMs(System.currentTimeMillis() - overallStarted)
                .build();
    }

    private static BatchFullProcessingResult buildCombinedResult(
            final String imageId,
            final String machineId,
            final Path source,
            final String sourceSha,
            final BatchConfiguration config,
            final Path classifierPath,
            final String classifierSha,
            final int tileProfileIndex,
            final BatchTileProfile tileProfile,
            final int attemptNumber,
            final Path datasetRoot,
            final StageOutcome classification,
            final StageOutcome reconstruction,
            final long overallStarted,
            final BatchProductionOutputValidator.FinalizedDatasetInspection reusedFinalDataset) {
        final boolean finalReuse = reusedFinalDataset != null && reusedFinalDataset.isFinalDatasetComplete();
        final boolean classificationCompleted = classification.reused
                || (classification.stageResult != null
                        && classification.stageResult.status().isSuccess());
        final boolean classificationValidated = classification.reused
                || (classification.stageResult != null
                        && RCsvSupport.TRUE.equals(classification.stageResult.requiredOutputsValidated()));
        final boolean reconstructionCompleted = reconstruction.reused
                || (reconstruction.stageResult != null
                        && reconstruction.stageResult.status().isSuccess());
        final boolean reconstructionValidated = reconstruction.reused
                || (reconstruction.stageResult != null
                        && RCsvSupport.TRUE.equals(reconstruction.stageResult.requiredOutputsValidated()));

        final String failedStage;
        final BatchFullProcessingStatus overallStatus;
        final String reasonCode;
        final String message;
        if (!classificationCompleted) {
            failedStage = "CLASSIFICATION";
            overallStatus = statusForFailedStage(classification);
            reasonCode = classification.stageResult != null
                    ? classification.stageResult.reasonCode()
                    : (classification.oomDetected ? "CLASSIFICATION_OUT_OF_MEMORY"
                            : (classification.timedOut ? "CLASSIFICATION_TIMEOUT"
                                    : "CLASSIFICATION_STAGE_FAILED"));
            message = classification.stageResult != null
                    ? classification.stageResult.message()
                    : "classification stage did not produce a result.properties";
        } else if (!reconstructionCompleted) {
            failedStage = "RECONSTRUCTION";
            overallStatus = statusForFailedStage(reconstruction);
            reasonCode = reconstruction.stageResult != null
                    ? reconstruction.stageResult.reasonCode()
                    : (reconstruction.oomDetected ? "RECONSTRUCTION_OUT_OF_MEMORY"
                            : (reconstruction.timedOut ? "RECONSTRUCTION_TIMEOUT"
                                    : "RECONSTRUCTION_STAGE_FAILED"));
            message = reconstruction.stageResult != null
                    ? reconstruction.stageResult.message()
                    : "reconstruction stage did not produce a result.properties";
        } else {
            failedStage = RCsvSupport.NA;
            overallStatus = BatchFullProcessingStatus.FULL_SUCCESS;
            reasonCode = RCsvSupport.NA;
            message = finalReuse
                    ? "full dataset already complete; reusing classification and reconstruction"
                    : "full processing committed";
        }

        final String classificationRunId = classification.reused
                ? classification.reusedRunId
                : extractStageRunId(classification.stageResult);
        final String reconstructionRunId = reconstruction.reused
                ? reconstruction.reusedRunId
                : extractStageRunId(reconstruction.stageResult);

        final BatchFullProcessingResult.Builder builder = BatchFullProcessingResult.builder()
                .status(overallStatus)
                .reasonCode(reasonCode)
                .message(message)
                .imageId(imageId)
                .machineId(machineId)
                .sourceFilename(source.getFileName().toString())
                .sourceSha256(sourceSha)
                .configurationSha256(config.sha256())
                .classifierFilename(classifierPath.getFileName().toString())
                .classifierSha256(classifierSha)
                .attemptNumber(Integer.valueOf(attemptNumber))
                .tileProfile(Integer.valueOf(tileProfileIndex), Integer.valueOf(tileProfile.widthPx()),
                        Integer.valueOf(tileProfile.heightPx()))
                .datasetRoot(datasetRoot.toString())
                .failedStage(failedStage)
                .classificationStageReused(Boolean.valueOf(classification.reused))
                .classificationProcessLaunched(Boolean.valueOf(classification.launched))
                .classificationExitCode(classification.exitCode)
                .classificationCompleted(Boolean.valueOf(classificationCompleted))
                .classificationOutputsValidated(Boolean.valueOf(classificationValidated))
                .classificationRunId(classificationRunId)
                .classificationStageResultPath(
                        classification.resultPath == null ? null : classification.resultPath.toString())
                .reconstructionProcessLaunched(Boolean.valueOf(reconstruction.launched))
                .reconstructionExitCode(reconstruction.exitCode)
                .reconstructionCompleted(Boolean.valueOf(reconstructionCompleted))
                .reconstructionOutputsValidated(Boolean.valueOf(reconstructionValidated))
                .reconstructionRunId(reconstructionRunId)
                .reconstructionStageResultPath(
                        reconstruction.resultPath == null ? null : reconstruction.resultPath.toString())
                .outOfMemoryDetected(Boolean.valueOf(classification.oomDetected || reconstruction.oomDetected))
                .processingTimeMs(System.currentTimeMillis() - overallStarted);
        mergeStageResultArtifacts(builder, classification.stageResult,
                BatchFullProcessingStage.CLASSIFICATION);
        mergeStageResultArtifacts(builder, reconstruction.stageResult,
                BatchFullProcessingStage.RECONSTRUCTION);
        if (finalReuse) {
            applyFinalizedDatasetArtifacts(builder, reusedFinalDataset);
        }
        return builder.build();
    }

    private static void applyFinalizedDatasetArtifacts(
            final BatchFullProcessingResult.Builder builder,
            final BatchProductionOutputValidator.FinalizedDatasetInspection finalized) {
        builder.precheckVerified(Boolean.TRUE)
                .classificationCompleted(Boolean.TRUE)
                .classificationOutputsValidated(Boolean.TRUE)
                .reconstructionCompleted(Boolean.TRUE)
                .reconstructionOutputsValidated(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .sourceUnchanged(Boolean.TRUE)
                .outOfMemoryDetected(Boolean.FALSE)
                .originalRgbPath(finalized.originalRgbPath)
                .approvedMaskPath(finalized.approvedMaskPath)
                .maskedRgbPath(finalized.maskedRgbPath)
                .maskedClassifiedPath(finalized.maskedClassifiedPath)
                .reconstructedClassifiedPath(finalized.reconstructedClassifiedPath)
                .lumenMeasurementsPath(finalized.lumenMeasurementsPath)
                .artifactDiagnosticsPath(finalized.artifactDiagnosticsPath)
                .imageManifestPath(finalized.imageManifestPath)
                .segmentManifestPath(finalized.segmentManifestPath)
                .processingRunsPath(finalized.processingRunsPath)
                .classificationRunId(finalized.classificationRunId)
                .reconstructionRunId(finalized.reconstructionRunId)
                .components(
                        finalized.totalComponents,
                        finalized.eligibleComponents,
                        finalized.reconstructedComponents,
                        finalized.changedPixels);
    }

    private static void mergeStageResultArtifacts(
            final BatchFullProcessingResult.Builder builder,
            final BatchFullProcessingStageResult stageResult,
            final BatchFullProcessingStage stage) {
        if (stageResult == null) {
            return;
        }
        final String primary = stageResult.primaryOutputPath();
        if (primary != null && !RCsvSupport.NA.equals(primary)) {
            if (stage == BatchFullProcessingStage.CLASSIFICATION) {
                builder.maskedClassifiedPath(primary);
            } else {
                builder.reconstructedClassifiedPath(primary);
            }
        }
        if (stage == BatchFullProcessingStage.RECONSTRUCTION) {
            final Integer total = parseIntOrNull(stageResult.totalComponents());
            final Integer eligible = parseIntOrNull(stageResult.eligibleComponents());
            final Integer reconstructed = parseIntOrNull(stageResult.reconstructedComponents());
            final Integer changed = parseIntOrNull(stageResult.changedPixels());
            if (total != null || eligible != null || reconstructed != null || changed != null) {
                builder.components(total, eligible, reconstructed, changed);
            }
        }
    }

    private static Integer parseIntOrNull(final String raw) {
        if (raw == null || raw.trim().isEmpty() || RCsvSupport.NA.equals(raw.trim())) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static BatchFullProcessingStatus statusForFailedStage(final StageOutcome outcome) {
        if (outcome.stageResult != null) {
            return outcome.stageResult.status();
        }
        if (outcome.timedOut) {
            return BatchFullProcessingStatus.FAILED_TIMEOUT;
        }
        if (outcome.oomDetected) {
            return BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY;
        }
        return BatchFullProcessingStatus.FAILED_UNEXPECTED;
    }

    private static String extractStageRunId(final BatchFullProcessingStageResult result) {
        if (result == null) {
            return null;
        }
        final String value = result.runId();
        return value == null || RCsvSupport.NA.equals(value) ? null : value;
    }

    /** Case-insensitive text scan for {@code OutOfMemoryError} mentions. */
    private static boolean containsOutOfMemoryText(final String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("outofmemoryerror");
    }

    private static boolean fileContainsOutOfMemory(final Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            return containsOutOfMemoryText(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (final IOException ex) {
            return false;
        }
    }

    private static boolean detectOutOfMemory(
            final BatchFullProcessingStageResult result,
            final Path stdoutPath,
            final Path stderrPath) {
        if (result != null) {
            if (result.status() == BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY) {
                return true;
            }
            if (RCsvSupport.TRUE.equals(result.outOfMemoryDetected())) {
                return true;
            }
            if (containsOutOfMemoryText(result.message())) {
                return true;
            }
        }
        return fileContainsOutOfMemory(stderrPath) || fileContainsOutOfMemory(stdoutPath);
    }

    /**
     * Scans {@code imageAttemptsRoot} for {@code attempt_<N>} directories and
     * returns one greater than the highest existing {@code N} (or {@code 1}
     * if none exist).
     */
    static int nextAttemptNumber(final Path imageAttemptsRoot) throws IOException {
        if (!Files.isDirectory(imageAttemptsRoot)) {
            return 1;
        }
        int max = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(imageAttemptsRoot, "attempt_*")) {
            for (final Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                final Matcher matcher = ATTEMPT_DIR_PATTERN.matcher(entry.getFileName().toString());
                if (matcher.matches()) {
                    try {
                        final int n = Integer.parseInt(matcher.group(1));
                        if (n > max) {
                            max = n;
                        }
                    } catch (final NumberFormatException ignored) {
                        // skip malformed attempt directory name
                    }
                }
            }
        }
        return max + 1;
    }

    private static void appendBestEffort(final Path path, final String line) {
        try {
            Files.createDirectories(path.getParent());
            Sha256Files.writeBytesAtomically(path, (line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (final IOException ignored) {
            // best effort
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
        command.add("vesselreconstruction.ellipse.BatchFullProcessingImagePlugin");
        command.add(jobPath.toAbsolutePath().normalize().toString());
        return command;
    }

    private static BatchPrecheckSupervisor.ManifestRow findManifestRow(
            final Path manifest,
            final String machineId,
            final String imageId,
            final String expectedConfigSha) throws IOException {
        final List<BatchPrecheckSupervisor.ManifestRow> rows =
                BatchPrecheckSupervisor.readMachineManifest(manifest, machineId, expectedConfigSha);
        for (int i = 0; i < rows.size(); i++) {
            if (imageId.equals(rows.get(i).imageId)) {
                return rows.get(i);
            }
        }
        throw new IllegalArgumentException(
                "image " + imageId + " not found on machine " + machineId + " manifest");
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

    static Map<String, String> parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        int i = 0;
        while (i < args.length) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException(usage("Unexpected positional: " + option));
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(usage("Missing value for " + option));
            }
            if (values.containsKey(option)) {
                throw new IllegalArgumentException(usage("Duplicate option: " + option));
            }
            if (!isSupported(option)) {
                throw new IllegalArgumentException(usage("Unknown option: " + option));
            }
            values.put(option, args[i + 1]);
            i += 2;
        }
        require(values, "--input-root");
        require(values, "--batch-root");
        require(values, "--machine-id");
        require(values, "--image-id");
        require(values, "--config");
        require(values, "--classifier");
        require(values, "--fiji-launcher");
        require(values, "--tile-profile-index");
        if (values.size() != 8) {
            throw new IllegalArgumentException(usage("Unexpected options: " + values.keySet()));
        }
        return values;
    }

    private static boolean isSupported(final String option) {
        return "--input-root".equals(option)
                || "--batch-root".equals(option)
                || "--machine-id".equals(option)
                || "--image-id".equals(option)
                || "--config".equals(option)
                || "--classifier".equals(option)
                || "--fiji-launcher".equals(option)
                || "--tile-profile-index".equals(option);
    }

    private static void require(final Map<String, String> values, final String key) {
        if (!values.containsKey(key) || values.get(key).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + key));
        }
    }

    private static String usage(final String reason) {
        return reason + "\n\nUsage:\n"
                + "java -cp target/classes vesselreconstruction.ellipse.BatchFullProcessingCli \\\n"
                + "  --input-root <path> \\\n"
                + "  --batch-root <path> \\\n"
                + "  --machine-id <machine_XX> \\\n"
                + "  --image-id <PPTTCS> \\\n"
                + "  --config <path> \\\n"
                + "  --classifier <path> \\\n"
                + "  --fiji-launcher <path> \\\n"
                + "  --tile-profile-index <int>\n";
    }

    /** Result of attempting (or reusing) a single stage's Fiji process. */
    static final class StageOutcome {
        final boolean reused;
        final boolean launched;
        final Integer exitCode;
        final BatchFullProcessingStageResult stageResult;
        final Path resultPath;
        final boolean oomDetected;
        final boolean timedOut;
        final String reusedRunId;

        private StageOutcome(
                final boolean reused,
                final boolean launched,
                final Integer exitCode,
                final BatchFullProcessingStageResult stageResult,
                final Path resultPath,
                final boolean oomDetected,
                final boolean timedOut,
                final String reusedRunId) {
            this.reused = reused;
            this.launched = launched;
            this.exitCode = exitCode;
            this.stageResult = stageResult;
            this.resultPath = resultPath;
            this.oomDetected = oomDetected;
            this.timedOut = timedOut;
            this.reusedRunId = reusedRunId;
        }

        static StageOutcome reused(final String runId) {
            return new StageOutcome(true, false, null, null, null, false, false, runId);
        }

        static StageOutcome notLaunched() {
            return new StageOutcome(false, false, null, null, null, false, false, null);
        }

        static StageOutcome launchFailed() {
            return new StageOutcome(false, true, null, null, null, false, false, null);
        }

        static StageOutcome timedOut() {
            return new StageOutcome(false, true, null, null, null, false, true, null);
        }

        static StageOutcome missingResult(final Integer exitCode, final boolean oomDetected) {
            return new StageOutcome(false, true, exitCode, null, null, oomDetected, false, null);
        }

        static StageOutcome withResult(
                final Integer exitCode,
                final BatchFullProcessingStageResult stageResult,
                final Path resultPath,
                final boolean oomDetected) {
            return new StageOutcome(false, true, exitCode, stageResult, resultPath, oomDetected, false, null);
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
