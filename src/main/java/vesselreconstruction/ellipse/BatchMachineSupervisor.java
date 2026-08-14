package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase-4B sequential multi-image supervisor. Orchestration only: plans with
 * {@link BatchMachineRunPlanner}, launches one {@link BatchFullProcessingCli}
 * subprocess at a time for ready/resume images, replans after each attempt,
 * and persists machine progress from canonical FULL_SUCCESS state.
 *
 * <p>After every assigned image is {@code FULL_SUCCESS}, the supervisor runs
 * {@link PhysicalCoreCoordinateAssembler} on the local machine dataset before
 * {@code MACHINE_SUCCESS} becomes authoritative. Does not run precheck, does
 * not compute ETA, and never loads ImageJ.</p>
 */
public final class BatchMachineSupervisor {

    public static final String SUPERVISOR_DIR = "machine_supervisor";
    public static final String PROGRESS_FILE = "machine_progress.properties";
    public static final String SUMMARY_CSV = "machine_run_summary.csv";
    public static final String LOGS_DIR = "logs";

    public static final List<String> SUMMARY_HEADER = Collections.unmodifiableList(Arrays.asList(
            "schema_version",
            "machine_id",
            "supervisor_run_id",
            "sequence_number",
            "image_id",
            "initial_state",
            "planned_action",
            "processing_attempted",
            "skipped_complete",
            "blocked",
            "process_exit_code",
            "final_status",
            "reason_code",
            "elapsed_ms",
            "attempt_result_path",
            "message"));

    private static final Pattern ATTEMPT_DIR_PATTERN = Pattern.compile("attempt_(\\d+)");
    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^machine_\\d+$");

    /**
     * Immutable configuration for one supervisor run.
     */
    public static final class Request {
        public final Path inputRoot;
        public final Path batchRoot;
        public final String machineId;
        public final Path configPath;
        public final Path classifierPath;
        public final Path fijiLauncher;
        public final int tileProfileIndex;
        public final Path classesDirectory;
        public final Path javaExecutable;
        public final BatchMachineImageProcessLauncher launcher;
        public final Appendable terminal;

        public Request(
                final Path inputRoot,
                final Path batchRoot,
                final String machineId,
                final Path configPath,
                final Path classifierPath,
                final Path fijiLauncher,
                final int tileProfileIndex,
                final Path classesDirectory,
                final Path javaExecutable,
                final BatchMachineImageProcessLauncher launcher,
                final Appendable terminal) {
            this.inputRoot = Objects.requireNonNull(inputRoot, "inputRoot")
                    .toAbsolutePath().normalize();
            this.batchRoot = Objects.requireNonNull(batchRoot, "batchRoot")
                    .toAbsolutePath().normalize();
            if (machineId == null || !MACHINE_ID_PATTERN.matcher(machineId.trim()).matches()) {
                throw new IllegalArgumentException(
                        "machineId must match machine_\\d+: " + machineId);
            }
            this.machineId = machineId.trim();
            this.configPath = Objects.requireNonNull(configPath, "configPath")
                    .toAbsolutePath().normalize();
            this.classifierPath = Objects.requireNonNull(classifierPath, "classifierPath")
                    .toAbsolutePath().normalize();
            this.fijiLauncher = Objects.requireNonNull(fijiLauncher, "fijiLauncher")
                    .toAbsolutePath().normalize();
            if (tileProfileIndex < 0) {
                throw new IllegalArgumentException(
                        "tileProfileIndex must be non-negative: " + tileProfileIndex);
            }
            this.tileProfileIndex = tileProfileIndex;
            this.classesDirectory = Objects.requireNonNull(classesDirectory, "classesDirectory")
                    .toAbsolutePath().normalize();
            this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable")
                    .toAbsolutePath().normalize();
            this.launcher = Objects.requireNonNull(launcher, "launcher");
            this.terminal = terminal == null ? System.out : terminal;
        }
    }

    /**
     * Configuration / validation failure before the processing loop.
     */
    public static final class ConfigurationException extends Exception {
        public ConfigurationException(final String message) {
            super(message);
        }

        public ConfigurationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private BatchMachineSupervisor() {
    }

    public static Path resolveJavaExecutable() {
        final String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.trim().isEmpty()) {
            throw new IllegalStateException("java.home is not set");
        }
        return java.nio.file.Paths.get(javaHome, "bin", "java").toAbsolutePath().normalize();
    }

    public static Path supervisorRoot(final Path batchRoot, final String machineId) {
        return batchRoot.resolve(SUPERVISOR_DIR).resolve(machineId);
    }

    public static BatchMachineSupervisorResult run(final Request request)
            throws ConfigurationException {
        Objects.requireNonNull(request, "request");
        final long startEpochMs = System.currentTimeMillis();
        final String supervisorRunId = request.machineId + "_" + startEpochMs;

        validateConfiguration(request);

        final BatchMachineRunPlanner.Request planRequest = new BatchMachineRunPlanner.Request(
                request.inputRoot,
                request.batchRoot,
                request.machineId,
                request.configPath,
                request.classifierPath,
                request.tileProfileIndex);

        final BatchMachineRunPlan initialPlan;
        try {
            initialPlan = BatchMachineRunPlanner.plan(planRequest);
        } catch (final IllegalArgumentException ex) {
            throw new ConfigurationException(ex.getMessage(), ex);
        } catch (final IOException ex) {
            throw new ConfigurationException("Unable to build machine run plan: " + ex.getMessage(),
                    ex);
        }

        final Path supervisorDir = supervisorRoot(request.batchRoot, request.machineId);
        try {
            Files.createDirectories(supervisorDir.resolve(LOGS_DIR));
        } catch (final IOException ex) {
            throw new ConfigurationException(
                    "Unable to create machine supervisor directory: " + ex.getMessage(), ex);
        }

        final int assigned = initialPlan.assignedCount();
        final int initialCompleted = initialPlan.completedCount();
        final double initialProgress = BatchMachineSupervisorResult.progressPercent(
                initialCompleted, assigned);

        println(request.terminal, "MACHINE SUPERVISOR START");
        println(request.terminal, "machine: " + request.machineId);
        println(request.terminal, "assigned: " + assigned);
        println(request.terminal, "already complete: " + initialCompleted);
        println(request.terminal, "initial progress: "
                + formatPercent(initialProgress));
        println(request.terminal, "");

        final List<BatchMachineImageExecutionResult> imageResults =
                new ArrayList<BatchMachineImageExecutionResult>();
        int skippedCompleteCount = 0;
        int attemptedCount = 0;
        int succeededThisRunCount = 0;
        int blockedCount = 0;
        int failedCount = 0;

        final AtomicBoolean inFlight = new AtomicBoolean(false);
        final List<BatchMachineImagePlan> ordered = initialPlan.images();

        for (int i = 0; i < ordered.size(); i++) {
            final BatchMachineImagePlan snapshot = ordered.get(i);
            final BatchMachineRunPlan livePlan;
            final BatchMachineImagePlan imagePlan;
            try {
                livePlan = BatchMachineRunPlanner.plan(planRequest);
                imagePlan = findImage(livePlan, snapshot.imageId());
            } catch (final Exception ex) {
                return finishUnexpected(
                        request, supervisorDir, supervisorRunId, startEpochMs,
                        assigned, initialCompleted, initialProgress, imageResults,
                        skippedCompleteCount, attemptedCount, succeededThisRunCount,
                        blockedCount, failedCount, ex);
            }

            final double progressBefore = BatchMachineSupervisorResult.progressPercent(
                    livePlan.completedCount(), assigned);

            println(request.terminal, String.format(Locale.ROOT,
                    "[%03d/%d] IMAGE %s",
                    Integer.valueOf(imagePlan.sequenceNumber()),
                    Integer.valueOf(assigned),
                    imagePlan.imageId()));
            println(request.terminal, "state: " + imagePlan.state().name());
            println(request.terminal, "action: " + imagePlan.plannedAction().name());
            println(request.terminal, "progress before: " + formatPercent(progressBefore));

            if (shouldWriteInterimProgress(
                    assigned, blockedCount, failedCount, livePlan.completedCount())) {
                writeProgress(
                        supervisorDir,
                        request.machineId,
                        interimStatus(blockedCount, failedCount, livePlan),
                        assigned,
                        livePlan.completedCount(),
                        progressBefore,
                        imagePlan.sequenceNumber(),
                        imagePlan.imageId(),
                        imagePlan.plannedAction().name(),
                        attemptedCount,
                        succeededThisRunCount,
                        skippedCompleteCount,
                        blockedCount,
                        failedCount,
                        assigned - livePlan.completedCount(),
                        System.currentTimeMillis() - startEpochMs);
            }

            final long imageStart = System.currentTimeMillis();
            final BatchMachineImageExecutionResult imageResult;
            try {
                imageResult = handleImage(
                        request,
                        supervisorDir,
                        imagePlan,
                        inFlight,
                        imageStart);
            } catch (final RuntimeException ex) {
                return finishUnexpected(
                        request, supervisorDir, supervisorRunId, startEpochMs,
                        assigned, initialCompleted, initialProgress, imageResults,
                        skippedCompleteCount, attemptedCount, succeededThisRunCount,
                        blockedCount, failedCount, ex);
            }

            imageResults.add(imageResult);
            if (imageResult.skippedComplete()) {
                skippedCompleteCount++;
                println(request.terminal, "SKIP_COMPLETE");
            } else if (imageResult.blocked()) {
                blockedCount++;
                println(request.terminal, "BLOCKED: " + imageResult.reasonCode()
                        + " — " + imageResult.message());
            } else if (imageResult.processingAttempted()) {
                attemptedCount++;
            }

            final BatchMachineRunPlan afterPlan;
            try {
                afterPlan = BatchMachineRunPlanner.plan(planRequest);
            } catch (final Exception ex) {
                return finishUnexpected(
                        request, supervisorDir, supervisorRunId, startEpochMs,
                        assigned, initialCompleted, initialProgress, imageResults,
                        skippedCompleteCount, attemptedCount, succeededThisRunCount,
                        blockedCount, failedCount, ex);
            }
            final BatchMachineImagePlan afterImage = findImage(afterPlan, imagePlan.imageId());
            final boolean nowComplete =
                    afterImage.state() == BatchMachineImageState.FULL_SUCCESS;

            BatchMachineImageExecutionResult reconciled = imageResult;
            if (imageResult.processingAttempted()) {
                if (nowComplete
                        && imagePlan.state() != BatchMachineImageState.FULL_SUCCESS) {
                    succeededThisRunCount++;
                    reconciled = BatchMachineImageExecutionResult.builder()
                            .sequenceNumber(imageResult.sequenceNumber())
                            .imageId(imageResult.imageId())
                            .initialState(imageResult.initialState())
                            .plannedAction(imageResult.plannedAction())
                            .processingAttempted(true)
                            .skippedComplete(false)
                            .blocked(false)
                            .processExitCode(imageResult.processExitCode())
                            .finalStatus(preferStatus(imageResult.finalStatus(), "FULL_SUCCESS"))
                            .reasonCode(imageResult.reasonCode())
                            .message(imageResult.message())
                            .timing(imageResult.startEpochMs(), imageResult.endEpochMs())
                            .attemptResultPath(imageResult.attemptResultPath())
                            .build();
                    imageResults.set(imageResults.size() - 1, reconciled);
                } else if (!nowComplete) {
                    failedCount++;
                    final String failureStatus = !"NA".equals(imageResult.finalStatus())
                            && !"FULL_SUCCESS".equals(imageResult.finalStatus())
                            ? imageResult.finalStatus()
                            : "FAILED_OUTPUT_VALIDATION";
                    final String failureReason = !"NA".equals(imageResult.reasonCode())
                            ? imageResult.reasonCode()
                            : "PLANNER_NOT_FULL_SUCCESS";
                    final String failureMessage = imageResult.processExitCode() == 0
                            ? "process exit 0 but planner did not report FULL_SUCCESS"
                            : imageResult.message();
                    reconciled = BatchMachineImageExecutionResult.builder()
                            .sequenceNumber(imageResult.sequenceNumber())
                            .imageId(imageResult.imageId())
                            .initialState(imageResult.initialState())
                            .plannedAction(imageResult.plannedAction())
                            .processingAttempted(true)
                            .skippedComplete(false)
                            .blocked(false)
                            .processExitCode(imageResult.processExitCode())
                            .finalStatus(failureStatus)
                            .reasonCode(failureReason)
                            .message(failureMessage)
                            .timing(imageResult.startEpochMs(), imageResult.endEpochMs())
                            .attemptResultPath(imageResult.attemptResultPath())
                            .build();
                    imageResults.set(imageResults.size() - 1, reconciled);
                }
            }

            final double progressAfter = BatchMachineSupervisorResult.progressPercent(
                    afterPlan.completedCount(), assigned);
            if (reconciled.processingAttempted()) {
                println(request.terminal, "image result: " + reconciled.finalStatus());
                println(request.terminal, "elapsed: " + formatElapsed(reconciled.elapsedMs()));
                println(request.terminal, "progress after: " + formatPercent(progressAfter));
            }
            println(request.terminal, "");

            if (shouldWriteInterimProgress(
                    assigned, blockedCount, failedCount, afterPlan.completedCount())) {
                writeProgress(
                        supervisorDir,
                        request.machineId,
                        interimStatus(blockedCount, failedCount, afterPlan),
                        assigned,
                        afterPlan.completedCount(),
                        progressAfter,
                        imagePlan.sequenceNumber(),
                        imagePlan.imageId(),
                        imagePlan.plannedAction().name(),
                        attemptedCount,
                        succeededThisRunCount,
                        skippedCompleteCount,
                        blockedCount,
                        failedCount,
                        assigned - afterPlan.completedCount(),
                        System.currentTimeMillis() - startEpochMs);
            }

            writeSummaryCsv(supervisorDir, supervisorRunId, request.machineId, imageResults);
        }

        final BatchMachineRunPlan finalPlan;
        try {
            finalPlan = BatchMachineRunPlanner.plan(planRequest);
        } catch (final Exception ex) {
            return finishUnexpected(
                    request, supervisorDir, supervisorRunId, startEpochMs,
                    assigned, initialCompleted, initialProgress, imageResults,
                    skippedCompleteCount, attemptedCount, succeededThisRunCount,
                    blockedCount, failedCount, ex);
        }

        final int finalCompleted = finalPlan.completedCount();
        final double finalProgress = BatchMachineSupervisorResult.progressPercent(
                finalCompleted, assigned);
        final int remaining = assigned - finalCompleted;

        if (shouldAssemblePhysicalCoreCoordinates(
                assigned, blockedCount, failedCount, finalCompleted)) {
            final Path datasetRoot = request.batchRoot
                    .resolve(BatchManifestSchema.datasetShardRelativePath(request.machineId))
                    .toAbsolutePath().normalize();
            try {
                assemblePhysicalCoreCoordinates(datasetRoot);
            } catch (final IOException ex) {
                return finishAssemblyFailure(
                        request, supervisorDir, supervisorRunId, startEpochMs,
                        assigned, initialCompleted, initialProgress, finalCompleted,
                        finalProgress, remaining, imageResults,
                        skippedCompleteCount, attemptedCount, succeededThisRunCount,
                        blockedCount, failedCount, datasetRoot, ex);
            }
        }

        final long endEpochMs = System.currentTimeMillis();
        final BatchMachineSupervisorStatus status = resolveStatus(
                assigned, finalCompleted, blockedCount, failedCount);

        writeProgress(
                supervisorDir,
                request.machineId,
                status,
                assigned,
                finalCompleted,
                finalProgress,
                0,
                "NA",
                "NA",
                attemptedCount,
                succeededThisRunCount,
                skippedCompleteCount,
                blockedCount,
                failedCount,
                remaining,
                endEpochMs - startEpochMs);
        writeSummaryCsv(supervisorDir, supervisorRunId, request.machineId, imageResults);

        final BatchMachineSupervisorResult result = BatchMachineSupervisorResult.builder()
                .machineId(request.machineId)
                .timing(startEpochMs, endEpochMs)
                .assignedCount(assigned)
                .initialCompletedCount(initialCompleted)
                .finalCompletedCount(finalCompleted)
                .skippedCompleteCount(skippedCompleteCount)
                .attemptedCount(attemptedCount)
                .succeededThisRunCount(succeededThisRunCount)
                .blockedCount(blockedCount)
                .failedCount(failedCount)
                .remainingCount(remaining)
                .initialProgressPercent(initialProgress)
                .finalProgressPercent(finalProgress)
                .status(status)
                .imageResults(imageResults)
                .build();

        println(request.terminal, "MACHINE SUPERVISOR FINISHED");
        println(request.terminal, "machine: " + request.machineId);
        println(request.terminal, "assigned: " + assigned);
        println(request.terminal, "completed: " + finalCompleted);
        println(request.terminal, "progress: " + formatPercent(finalProgress));
        println(request.terminal, "succeeded this run: " + succeededThisRunCount);
        println(request.terminal, "already complete/skipped: " + skippedCompleteCount);
        println(request.terminal, "blocked: " + blockedCount);
        println(request.terminal, "failed: " + failedCount);
        println(request.terminal, "remaining: " + remaining);
        println(request.terminal, "elapsed: " + formatElapsed(result.elapsedMs()));
        println(request.terminal, "status: " + status.name());

        return result;
    }

    private static BatchMachineSupervisorResult finishUnexpected(
            final Request request,
            final Path supervisorDir,
            final String supervisorRunId,
            final long startEpochMs,
            final int assigned,
            final int initialCompleted,
            final double initialProgress,
            final List<BatchMachineImageExecutionResult> imageResults,
            final int skippedCompleteCount,
            final int attemptedCount,
            final int succeededThisRunCount,
            final int blockedCount,
            final int failedCount,
            final Exception ex) {
        final long endEpochMs = System.currentTimeMillis();
        println(request.terminal, "ERROR: unexpected supervisor failure: " + ex.getMessage());
        writeProgress(
                supervisorDir,
                request.machineId,
                BatchMachineSupervisorStatus.MACHINE_FAILED_UNEXPECTED,
                assigned,
                initialCompleted,
                initialProgress,
                0,
                "NA",
                "NA",
                attemptedCount,
                succeededThisRunCount,
                skippedCompleteCount,
                blockedCount,
                failedCount,
                Math.max(0, assigned - initialCompleted),
                endEpochMs - startEpochMs);
        writeSummaryCsv(supervisorDir, supervisorRunId, request.machineId, imageResults);
        return BatchMachineSupervisorResult.builder()
                .machineId(request.machineId)
                .timing(startEpochMs, endEpochMs)
                .assignedCount(assigned)
                .initialCompletedCount(initialCompleted)
                .finalCompletedCount(initialCompleted)
                .skippedCompleteCount(skippedCompleteCount)
                .attemptedCount(attemptedCount)
                .succeededThisRunCount(succeededThisRunCount)
                .blockedCount(blockedCount)
                .failedCount(failedCount)
                .remainingCount(Math.max(0, assigned - initialCompleted))
                .initialProgressPercent(initialProgress)
                .finalProgressPercent(initialProgress)
                .status(BatchMachineSupervisorStatus.MACHINE_FAILED_UNEXPECTED)
                .imageResults(imageResults)
                .build();
    }

    private static BatchMachineSupervisorResult finishAssemblyFailure(
            final Request request,
            final Path supervisorDir,
            final String supervisorRunId,
            final long startEpochMs,
            final int assigned,
            final int initialCompleted,
            final double initialProgress,
            final int finalCompleted,
            final double finalProgress,
            final int remaining,
            final List<BatchMachineImageExecutionResult> imageResults,
            final int skippedCompleteCount,
            final int attemptedCount,
            final int succeededThisRunCount,
            final int blockedCount,
            final int failedCount,
            final Path datasetRoot,
            final IOException ex) {
        final long endEpochMs = System.currentTimeMillis();
        final String assemblerMessage = ex.getMessage() == null
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
        System.err.println("Physical-core coordinate assembly failed for machine "
                + request.machineId + ":");
        System.err.println(datasetRoot);
        System.err.println(assemblerMessage);
        println(request.terminal, "ERROR: Physical-core coordinate assembly failed for machine "
                + request.machineId + ":");
        println(request.terminal, assemblerMessage);
        writeProgress(
                supervisorDir,
                request.machineId,
                BatchMachineSupervisorStatus.MACHINE_FAILED_UNEXPECTED,
                assigned,
                finalCompleted,
                finalProgress,
                0,
                "NA",
                "NA",
                attemptedCount,
                succeededThisRunCount,
                skippedCompleteCount,
                blockedCount,
                failedCount,
                remaining,
                endEpochMs - startEpochMs);
        writeSummaryCsv(supervisorDir, supervisorRunId, request.machineId, imageResults);
        return BatchMachineSupervisorResult.builder()
                .machineId(request.machineId)
                .timing(startEpochMs, endEpochMs)
                .assignedCount(assigned)
                .initialCompletedCount(initialCompleted)
                .finalCompletedCount(finalCompleted)
                .skippedCompleteCount(skippedCompleteCount)
                .attemptedCount(attemptedCount)
                .succeededThisRunCount(succeededThisRunCount)
                .blockedCount(blockedCount)
                .failedCount(failedCount)
                .remainingCount(remaining)
                .initialProgressPercent(initialProgress)
                .finalProgressPercent(finalProgress)
                .status(BatchMachineSupervisorStatus.MACHINE_FAILED_UNEXPECTED)
                .imageResults(imageResults)
                .build();
    }

    /**
     * Suppress in-loop progress writes that would already be export-eligible
     * before physical-core coordinate assembly has succeeded.
     */
    private static boolean shouldWriteInterimProgress(
            final int assigned,
            final int blockedCount,
            final int failedCount,
            final int completedCount) {
        return !isPreAssemblyCompletionCandidate(
                assigned, blockedCount, failedCount, completedCount);
    }

    private static boolean shouldAssemblePhysicalCoreCoordinates(
            final int assigned,
            final int blockedCount,
            final int failedCount,
            final int completedCount) {
        return isPreAssemblyCompletionCandidate(
                assigned, blockedCount, failedCount, completedCount);
    }

    private static boolean isPreAssemblyCompletionCandidate(
            final int assigned,
            final int blockedCount,
            final int failedCount,
            final int completedCount) {
        return assigned > 0
                && failedCount == 0
                && blockedCount == 0
                && completedCount == assigned;
    }

    private static void assemblePhysicalCoreCoordinates(final Path datasetRoot)
            throws IOException {
        new PhysicalCoreCoordinateAssembler().assemble(datasetRoot);
    }

    private static void validateConfiguration(final Request request) throws ConfigurationException {
        if (!Files.isDirectory(request.inputRoot)) {
            throw new ConfigurationException("input-root is not a directory: " + request.inputRoot);
        }
        if (!Files.isDirectory(request.batchRoot)) {
            throw new ConfigurationException("batch-root is not a directory: " + request.batchRoot);
        }
        if (!Files.isRegularFile(request.configPath)) {
            throw new ConfigurationException("config file missing: " + request.configPath);
        }
        if (!Files.isRegularFile(request.classifierPath)) {
            throw new ConfigurationException("classifier file missing: " + request.classifierPath);
        }
        try {
            Sha256Files.sha256OfFile(request.classifierPath);
        } catch (final IOException ex) {
            throw new ConfigurationException(
                    "Unable to hash classifier: " + ex.getMessage(), ex);
        }
        if (!Files.isRegularFile(request.fijiLauncher)) {
            throw new ConfigurationException("fiji-launcher missing: " + request.fijiLauncher);
        }
        if (!Files.isExecutable(request.fijiLauncher)) {
            throw new ConfigurationException(
                    "fiji-launcher is not executable: " + request.fijiLauncher);
        }
        if (!Files.isDirectory(request.classesDirectory)) {
            throw new ConfigurationException(
                    "classes directory missing: " + request.classesDirectory);
        }
        if (!Files.isRegularFile(request.javaExecutable)
                && !Files.isExecutable(request.javaExecutable)) {
            // On some systems java may be a symlink; still require existence.
            if (!Files.exists(request.javaExecutable)) {
                throw new ConfigurationException(
                        "java executable missing: " + request.javaExecutable);
            }
        }
        try {
            final BatchConfiguration config = BatchConfiguration.load(request.configPath);
            if (request.tileProfileIndex >= config.tileProfiles().size()) {
                throw new ConfigurationException(
                        "tileProfileIndex out of range [0,"
                                + (config.tileProfiles().size() - 1) + "]: "
                                + request.tileProfileIndex);
            }
        } catch (final ConfigurationException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw new ConfigurationException("Invalid configuration: " + ex.getMessage(), ex);
        }
        final Path manifest = request.batchRoot
                .resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(request.machineId));
        if (!Files.isRegularFile(manifest)) {
            throw new ConfigurationException("machine manifest missing: " + manifest);
        }
    }

    private static BatchMachineImageExecutionResult handleImage(
            final Request request,
            final Path supervisorDir,
            final BatchMachineImagePlan imagePlan,
            final AtomicBoolean inFlight,
            final long imageStart) {
        final BatchMachinePlannedAction action = imagePlan.plannedAction();
        if (action == BatchMachinePlannedAction.SKIP_COMPLETE
                || imagePlan.state() == BatchMachineImageState.FULL_SUCCESS) {
            final long end = System.currentTimeMillis();
            return BatchMachineImageExecutionResult.builder()
                    .sequenceNumber(imagePlan.sequenceNumber())
                    .imageId(imagePlan.imageId())
                    .initialState(imagePlan.state())
                    .plannedAction(BatchMachinePlannedAction.SKIP_COMPLETE)
                    .processingAttempted(false)
                    .skippedComplete(true)
                    .blocked(false)
                    .processExitCode(-1)
                    .finalStatus(BatchMachineImageState.FULL_SUCCESS.name())
                    .reasonCode("SKIP_COMPLETE")
                    .message("Already FULL_SUCCESS; skipped")
                    .timing(imageStart, end)
                    .attemptResultPath(null)
                    .build();
        }

        if (action == BatchMachinePlannedAction.RUN_PRECHECK
                || action == BatchMachinePlannedAction.WAIT_FOR_REVIEW
                || action == BatchMachinePlannedAction.STOP_FOR_CANONICAL_CONFLICT
                || action == BatchMachinePlannedAction.STOP_FOR_INVALID_STATE
                || imagePlan.state() == BatchMachineImageState.PRECHECK_MISSING
                || imagePlan.state() == BatchMachineImageState.PRECHECK_FAILED
                || imagePlan.state() == BatchMachineImageState.PRECHECK_REVIEW_REQUIRED
                || imagePlan.state() == BatchMachineImageState.CANONICAL_CONFLICT
                || imagePlan.state() == BatchMachineImageState.UNKNOWN_INVALID_STATE) {
            final long end = System.currentTimeMillis();
            return BatchMachineImageExecutionResult.builder()
                    .sequenceNumber(imagePlan.sequenceNumber())
                    .imageId(imagePlan.imageId())
                    .initialState(imagePlan.state())
                    .plannedAction(action)
                    .processingAttempted(false)
                    .skippedComplete(false)
                    .blocked(true)
                    .processExitCode(-1)
                    .finalStatus(imagePlan.state().name())
                    .reasonCode(imagePlan.reasonCode())
                    .message(imagePlan.message())
                    .timing(imageStart, end)
                    .attemptResultPath(null)
                    .build();
        }

        if (action != BatchMachinePlannedAction.RUN_FULL_PROCESSING
                && action != BatchMachinePlannedAction.RESUME_RECONSTRUCTION) {
            final long end = System.currentTimeMillis();
            return BatchMachineImageExecutionResult.builder()
                    .sequenceNumber(imagePlan.sequenceNumber())
                    .imageId(imagePlan.imageId())
                    .initialState(imagePlan.state())
                    .plannedAction(action)
                    .processingAttempted(false)
                    .skippedComplete(false)
                    .blocked(true)
                    .processExitCode(-1)
                    .finalStatus(imagePlan.state().name())
                    .reasonCode("UNSUPPORTED_ACTION")
                    .message("Unsupported planned action for Phase-4B: " + action)
                    .timing(imageStart, end)
                    .attemptResultPath(null)
                    .build();
        }

        if (!inFlight.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Concurrent image processing detected; supervisor must be sequential");
        }
        final Path controllerLog = supervisorDir.resolve(LOGS_DIR)
                .resolve(imagePlan.imageId() + "__controller.log");
        final Path attemptsRoot = request.batchRoot.resolve("full")
                .resolve(request.machineId).resolve(imagePlan.imageId());
        final int minAttemptNumber;
        try {
            minAttemptNumber = BatchFullProcessingCli.nextAttemptNumber(attemptsRoot);
        } catch (final IOException ex) {
            inFlight.set(false);
            final long end = System.currentTimeMillis();
            return BatchMachineImageExecutionResult.builder()
                    .sequenceNumber(imagePlan.sequenceNumber())
                    .imageId(imagePlan.imageId())
                    .initialState(imagePlan.state())
                    .plannedAction(action)
                    .processingAttempted(true)
                    .skippedComplete(false)
                    .blocked(false)
                    .processExitCode(-1)
                    .finalStatus("FAILED_UNEXPECTED")
                    .reasonCode("ATTEMPT_SCAN_FAILED")
                    .message(ex.getMessage())
                    .timing(imageStart, end)
                    .attemptResultPath(null)
                    .build();
        }

        int exitCode = -1;
        Path attemptResultPath = null;
        String finalStatus = RCsvSupport.NA;
        String reasonCode = RCsvSupport.NA;
        String message = RCsvSupport.NA;
        try {
            final BatchMachineImageProcessLauncher.Request launchRequest =
                    new BatchMachineImageProcessLauncher.Request(
                            request.javaExecutable,
                            request.classesDirectory,
                            request.inputRoot,
                            request.batchRoot,
                            request.machineId,
                            imagePlan.imageId(),
                            request.configPath,
                            request.classifierPath,
                            request.fijiLauncher,
                            request.tileProfileIndex,
                            controllerLog);
            final BatchMachineImageProcessLauncher.LaunchResult launchResult =
                    request.launcher.launch(launchRequest);
            exitCode = launchResult.exitCode();
            attemptResultPath = findNewestAttemptResult(
                    attemptsRoot, minAttemptNumber, imageStart);
            if (attemptResultPath != null && Files.isRegularFile(attemptResultPath)) {
                try {
                    final BatchFullProcessingResult loaded =
                            BatchFullProcessingResult.load(attemptResultPath);
                    finalStatus = loaded.status().name();
                    reasonCode = loaded.get(BatchFullProcessingResult.KEY_REASON);
                    message = loaded.get(BatchFullProcessingResult.KEY_MESSAGE);
                } catch (final Exception parseEx) {
                    finalStatus = RCsvSupport.NA;
                    reasonCode = "RESULT_PARSE_FAILED";
                    message = parseEx.getMessage();
                }
            } else {
                reasonCode = "ATTEMPT_RESULT_MISSING";
                message = "No new attempt result.properties found after subprocess";
            }
        } catch (final Exception ex) {
            exitCode = -1;
            finalStatus = "FAILED_UNEXPECTED";
            reasonCode = "PROCESS_LAUNCH_FAILED";
            message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        } finally {
            inFlight.set(false);
        }

        final long end = System.currentTimeMillis();
        return BatchMachineImageExecutionResult.builder()
                .sequenceNumber(imagePlan.sequenceNumber())
                .imageId(imagePlan.imageId())
                .initialState(imagePlan.state())
                .plannedAction(action)
                .processingAttempted(true)
                .skippedComplete(false)
                .blocked(false)
                .processExitCode(exitCode)
                .finalStatus(finalStatus)
                .reasonCode(reasonCode)
                .message(message)
                .timing(imageStart, end)
                .attemptResultPath(attemptResultPath)
                .build();
    }

    static Path findNewestAttemptResult(
            final Path attemptsRoot,
            final int minAttemptNumberInclusive,
            final long notBeforeEpochMs) throws IOException {
        if (!Files.isDirectory(attemptsRoot)) {
            return null;
        }
        Path best = null;
        int bestN = -1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(attemptsRoot, "attempt_*")) {
            for (final Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                final Matcher matcher = ATTEMPT_DIR_PATTERN.matcher(entry.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                final int n = Integer.parseInt(matcher.group(1));
                if (n < minAttemptNumberInclusive) {
                    continue;
                }
                final Path result = entry.resolve("result.properties");
                if (!Files.isRegularFile(result)) {
                    continue;
                }
                // Prefer highest attempt number created for this launch window.
                if (n > bestN) {
                    bestN = n;
                    best = result;
                }
            }
        }
        return best;
    }

    private static BatchMachineImagePlan findImage(
            final BatchMachineRunPlan plan, final String imageId) {
        final List<BatchMachineImagePlan> images = plan.images();
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).imageId().equals(imageId)) {
                return images.get(i);
            }
        }
        throw new IllegalStateException("Image missing from replanned run: " + imageId);
    }

    private static BatchMachineSupervisorStatus resolveStatus(
            final int assigned,
            final int finalCompleted,
            final int blockedCount,
            final int failedCount) {
        if (failedCount > 0) {
            return BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES;
        }
        if (blockedCount > 0) {
            return BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_BLOCKED_IMAGES;
        }
        if (assigned == finalCompleted) {
            return BatchMachineSupervisorStatus.MACHINE_SUCCESS;
        }
        // Remaining ready/resume images that were not processed should not happen
        // in a full pass; treat as failures for visibility.
        return BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES;
    }

    private static BatchMachineSupervisorStatus interimStatus(
            final int blockedCount,
            final int failedCount,
            final BatchMachineRunPlan plan) {
        if (failedCount > 0) {
            return BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES;
        }
        if (blockedCount > 0) {
            return BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_BLOCKED_IMAGES;
        }
        if (plan.completedCount() == plan.assignedCount()) {
            return BatchMachineSupervisorStatus.MACHINE_SUCCESS;
        }
        return BatchMachineSupervisorStatus.MACHINE_SUCCESS;
    }

    private static String preferStatus(final String fromResult, final String fallback) {
        if (fromResult != null && !"NA".equals(fromResult) && !fromResult.isEmpty()) {
            return fromResult;
        }
        return fallback;
    }

    static void writeProgress(
            final Path supervisorDir,
            final String machineId,
            final BatchMachineSupervisorStatus status,
            final int assignedCount,
            final int completedCount,
            final double progressPercent,
            final int currentSequence,
            final String currentImageId,
            final String currentAction,
            final int attemptedCount,
            final int succeededThisRunCount,
            final int skippedCompleteCount,
            final int blockedCount,
            final int failedCount,
            final int remainingCount,
            final long elapsedMs) {
        if (Double.isNaN(progressPercent) || Double.isInfinite(progressPercent)) {
            throw new IllegalStateException("progressPercent must not be NaN/Infinity");
        }
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("schema_version", "1");
        map.put("machine_id", machineId);
        map.put("timestamp_epoch_ms", Long.toString(System.currentTimeMillis()));
        map.put("status", status.name());
        map.put("assigned_count", Integer.toString(assignedCount));
        map.put("completed_count", Integer.toString(completedCount));
        map.put("progress_percent", String.format(Locale.ROOT, "%.1f",
                Double.valueOf(progressPercent)));
        map.put("current_sequence", Integer.toString(currentSequence));
        map.put("current_image_id",
                currentImageId == null || currentImageId.isEmpty() ? "NA" : currentImageId);
        map.put("current_action",
                currentAction == null || currentAction.isEmpty() ? "NA" : currentAction);
        map.put("attempted_count", Integer.toString(attemptedCount));
        map.put("succeeded_this_run_count", Integer.toString(succeededThisRunCount));
        map.put("skipped_complete_count", Integer.toString(skippedCompleteCount));
        map.put("blocked_count", Integer.toString(blockedCount));
        map.put("failed_count", Integer.toString(failedCount));
        map.put("remaining_count", Integer.toString(remainingCount));
        map.put("elapsed_ms", Long.toString(elapsedMs));

        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            final String value = entry.getValue();
            if (value != null && value.toUpperCase(Locale.ROOT).contains("NAN")) {
                throw new IllegalStateException("Refusing to write NaN into progress file");
            }
            sb.append(entry.getKey()).append('=').append(value).append('\n');
        }
        try {
            Sha256Files.writeBytesAtomically(
                    supervisorDir.resolve(PROGRESS_FILE),
                    sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException ex) {
            throw new IllegalStateException("Unable to write machine progress: " + ex.getMessage(),
                    ex);
        }
    }

    static void writeSummaryCsv(
            final Path supervisorDir,
            final String supervisorRunId,
            final String machineId,
            final List<BatchMachineImageExecutionResult> imageResults) {
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < imageResults.size(); i++) {
            final BatchMachineImageExecutionResult r = imageResults.get(i);
            final String attemptPath = r.attemptResultPath() == null
                    ? RCsvSupport.NA
                    : relativizeBestEffort(supervisorDir.getParent().getParent(),
                    r.attemptResultPath());
            rows.add(Arrays.asList(
                    "1",
                    machineId,
                    supervisorRunId,
                    Integer.toString(r.sequenceNumber()),
                    r.imageId(),
                    r.initialState().name(),
                    r.plannedAction().name(),
                    RCsvSupport.logical(r.processingAttempted()),
                    RCsvSupport.logical(r.skippedComplete()),
                    RCsvSupport.logical(r.blocked()),
                    Integer.toString(r.processExitCode()),
                    r.finalStatus(),
                    r.reasonCode(),
                    Long.toString(r.elapsedMs()),
                    attemptPath,
                    r.message()));
        }
        try {
            ProductionCsvStore.writeTableAtomically(
                    supervisorDir.resolve(SUMMARY_CSV), SUMMARY_HEADER, rows);
        } catch (final IOException ex) {
            throw new IllegalStateException("Unable to write machine summary CSV: "
                    + ex.getMessage(), ex);
        }
    }

    private static String relativizeBestEffort(final Path batchRoot, final Path absolute) {
        try {
            final Path abs = absolute.toAbsolutePath().normalize();
            final Path root = batchRoot.toAbsolutePath().normalize();
            if (abs.startsWith(root)) {
                return root.relativize(abs).toString().replace('\\', '/');
            }
            return abs.toString();
        } catch (final Exception ex) {
            return absolute.toString();
        }
    }

    static String formatPercent(final double percent) {
        return String.format(Locale.ROOT, "%.1f%%", Double.valueOf(percent));
    }

    static String formatElapsed(final long elapsedMs) {
        if (elapsedMs < 1000L) {
            return elapsedMs + " ms";
        }
        final double seconds = elapsedMs / 1000.0d;
        if (seconds < 60.0d) {
            return String.format(Locale.ROOT, "%.1f s", Double.valueOf(seconds));
        }
        final long totalSeconds = elapsedMs / 1000L;
        final long minutes = totalSeconds / 60L;
        final long remSeconds = totalSeconds % 60L;
        if (minutes < 60L) {
            return String.format(Locale.ROOT, "%d m %d s",
                    Long.valueOf(minutes), Long.valueOf(remSeconds));
        }
        final long hours = minutes / 60L;
        final long remMinutes = minutes % 60L;
        return String.format(Locale.ROOT, "%d h %d m",
                Long.valueOf(hours), Long.valueOf(remMinutes));
    }

    private static void println(final Appendable out, final String line) {
        try {
            out.append(line).append('\n');
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
