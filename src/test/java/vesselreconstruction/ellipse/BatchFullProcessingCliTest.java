package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two-process orchestration: a classification-stage Fiji launch
 * (or its reuse) always followed by a reconstruction-stage Fiji launch, and
 * an always-written combined {@code attempt_N/result.properties}.
 */
class BatchFullProcessingCliTest {

    private static final String MACHINE_ID = "machine_01";
    private static final String IMAGE_ID = "010101";

    @TempDir
    Path tempDir;

    @Test
    void parseArgsRequiresAllOptions() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingCli.parseArgs(new String[] {"--input-root", "/tmp"}));
        assertTrue(ex.getMessage().contains("Missing required option"));
    }

    @Test
    void parseArgsRejectsUnknownOption() {
        final String[] args = fullArgArray("--bogus", "value");
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingCli.parseArgs(args));
        assertTrue(ex.getMessage().contains("Unknown option") || ex.getMessage().contains("Unexpected"));
    }

    @Test
    void parseArgsAcceptsAllRequiredOptions() {
        final Map<String, String> values = BatchFullProcessingCli.parseArgs(fullArgArray());
        assertEquals(8, values.size());
        assertEquals("/input", values.get("--input-root"));
        assertEquals("0", values.get("--tile-profile-index"));
    }

    @Test
    void buildFijiCommandUsesFullProcessingPluginMainClass() {
        final Path launcher = tempDir.resolve("ImageJ-linux64");
        final Path jobPath = tempDir.resolve("job.properties");
        final List<String> command = BatchFullProcessingCli.buildFijiCommand(launcher, jobPath);
        assertTrue(command.contains("--main-class"));
        assertTrue(command.contains("vesselreconstruction.ellipse.BatchFullProcessingImagePlugin"));
        assertTrue(command.contains("--headless"));
        assertEquals(jobPath.toString(), command.get(command.size() - 1));
    }

    @Test
    void mapExitCodeCoversEveryStatus() {
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.FULL_SUCCESS));
        for (final BatchFullProcessingStatus status : new BatchFullProcessingStatus[] {
                BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                BatchFullProcessingStatus.PRECHECK_NOT_SUCCESSFUL,
                BatchFullProcessingStatus.PRECHECK_STALE_SOURCE,
                BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION}) {
            assertEquals(BatchFullProcessingCli.EXIT_PRECHECK_NOT_READY,
                    BatchFullProcessingCli.mapExitCode(status), status.name());
        }
        for (final BatchFullProcessingStatus status : new BatchFullProcessingStatus[] {
                BatchFullProcessingStatus.INVALID_JOB,
                BatchFullProcessingStatus.INVALID_SOURCE,
                BatchFullProcessingStatus.INVALID_CLASSIFIER}) {
            assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT,
                    BatchFullProcessingCli.mapExitCode(status), status.name());
        }
        for (final BatchFullProcessingStatus status : new BatchFullProcessingStatus[] {
                BatchFullProcessingStatus.FAILED_CLASSIFICATION,
                BatchFullProcessingStatus.FAILED_CLASSIFICATION_SAVE}) {
            assertEquals(BatchFullProcessingCli.EXIT_CLASSIFICATION_FAILURE,
                    BatchFullProcessingCli.mapExitCode(status), status.name());
        }
        for (final BatchFullProcessingStatus status : new BatchFullProcessingStatus[] {
                BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                BatchFullProcessingStatus.FAILED_RECONSTRUCTION_SAVE}) {
            assertEquals(BatchFullProcessingCli.EXIT_RECONSTRUCTION_FAILURE,
                    BatchFullProcessingCli.mapExitCode(status), status.name());
        }
        assertEquals(BatchFullProcessingCli.EXIT_OUTPUT_VALIDATION_FAILURE,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.FAILED_OUTPUT_VALIDATION));
        assertEquals(BatchFullProcessingCli.EXIT_CANONICAL_CONFLICT,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.CANONICAL_CONFLICT));
        assertEquals(BatchFullProcessingCli.EXIT_OUT_OF_MEMORY,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY));
        assertEquals(BatchFullProcessingCli.EXIT_TIMEOUT,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.FAILED_TIMEOUT));
        assertEquals(BatchFullProcessingCli.EXIT_UNEXPECTED,
                BatchFullProcessingCli.mapExitCode(BatchFullProcessingStatus.FAILED_UNEXPECTED));
    }

    @Test
    void nextAttemptNumberStartsAtOneWhenNoPriorAttempts() throws Exception {
        final Path root = tempDir.resolve("no-attempts");
        assertEquals(1, BatchFullProcessingCli.nextAttemptNumber(root));
    }

    @Test
    void nextAttemptNumberIsMaxPlusOne() throws Exception {
        final Path root = tempDir.resolve("some-attempts");
        Files.createDirectories(root.resolve("attempt_1"));
        Files.createDirectories(root.resolve("attempt_2"));
        Files.createDirectories(root.resolve("attempt_10"));
        Files.createDirectories(root.resolve("not-an-attempt-dir"));
        assertEquals(11, BatchFullProcessingCli.nextAttemptNumber(root));
    }

    @Test
    void successfulRunLaunchesBothStagesAndReturnsZeroExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final AtomicInteger launches = new AtomicInteger(0);
        final BatchFullProcessingCli.ProcessLauncher launcher = countingLauncher(launches,
                stagedLauncher(succeeds(), succeeds()));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(2, launches.get(), "expected one classification launch and one reconstruction launch");

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, combined.status());
        assertEquals(RCsvSupport.NA, combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_PROCESS_LAUNCHED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_REUSED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_PROCESS_LAUNCHED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_COMPLETED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
    }

    @Test
    void classificationFailureStillLaunchesReconstructionAndReportsClassificationAsFailedStage()
            throws Exception {
        final Fixture fixture = buildFixture();
        final AtomicInteger launches = new AtomicInteger(0);
        final BatchFullProcessingCli.ProcessLauncher launcher = countingLauncher(launches,
                stagedLauncher(
                        fails(BatchFullProcessingStatus.FAILED_CLASSIFICATION, "classification broke"),
                        succeeds()));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_CLASSIFICATION_FAILURE, exitCode);
        assertEquals(2, launches.get(), "reconstruction must always be attempted, even after failure");

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals("CLASSIFICATION", combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_COMPLETED));
    }

    @Test
    void nonSuccessReconstructionResultReturnsMappedExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(), fails(BatchFullProcessingStatus.FAILED_OUTPUT_VALIDATION, "bad output"));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_OUTPUT_VALIDATION_FAILURE, exitCode);

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals("RECONSTRUCTION", combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
    }

    @Test
    void outOfMemoryResultReturnsOutOfMemoryExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(), fails(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, "oom"));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_OUT_OF_MEMORY, exitCode);

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(RCsvSupport.TRUE, combined.get(BatchFullProcessingResult.KEY_OUT_OF_MEMORY_DETECTED));
    }

    @Test
    void reconstructionOomStageResultWithLauncherExitZeroPropagatesToCombinedResult() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(),
                failsWithExitCode(
                        BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                        "Java heap space",
                        "RECONSTRUCTION_OUT_OF_MEMORY",
                        true,
                        0));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_OUT_OF_MEMORY, exitCode);

        final Path attemptRoot = fixture.attemptRoot(1);
        final Path stageResultPath = attemptRoot.resolve("reconstruction/result.properties");
        assertTrue(Files.isRegularFile(stageResultPath));
        final BatchFullProcessingStageResult stage =
                BatchFullProcessingStageResult.read(stageResultPath);
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, stage.status());

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                attemptRoot.resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, combined.status());
        assertEquals("RECONSTRUCTION", combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
        assertEquals("RECONSTRUCTION_OUT_OF_MEMORY",
                combined.get(BatchFullProcessingResult.KEY_REASON));
        assertEquals("Java heap space", combined.get(BatchFullProcessingResult.KEY_MESSAGE));
        assertEquals(RCsvSupport.TRUE, combined.get(BatchFullProcessingResult.KEY_OUT_OF_MEMORY_DETECTED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_OUTPUTS_VALIDATED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_PROCESS_LAUNCHED));
        assertEquals("0", combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_EXIT_CODE));
        assertEquals(stageResultPath.toAbsolutePath().normalize().toString(),
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_STAGE_RESULT_PATH));
    }

    @Test
    void classificationFailureStageResultIsPropagated() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                failsWithExitCode(
                        BatchFullProcessingStatus.FAILED_CLASSIFICATION,
                        "classification broke",
                        "CLASSIFICATION_FAILED",
                        false,
                        0),
                succeeds());
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_CLASSIFICATION_FAILURE, exitCode);

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FAILED_CLASSIFICATION, combined.status());
        assertEquals("CLASSIFICATION", combined.get(BatchFullProcessingResult.KEY_FAILED_STAGE));
        assertEquals("CLASSIFICATION_FAILED", combined.get(BatchFullProcessingResult.KEY_REASON));
        assertEquals("classification broke", combined.get(BatchFullProcessingResult.KEY_MESSAGE));
        assertTrue(Files.isRegularFile(
                fixture.attemptRoot(1).resolve("classification/result.properties")));
        assertEquals(
                fixture.attemptRoot(1).resolve("classification/result.properties")
                        .toAbsolutePath().normalize().toString(),
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_RESULT_PATH));
    }

    @Test
    void successfulChildStageResultRemainsSuccessful() throws Exception {
        final Fixture fixture = buildFixture();
        final int exitCode = BatchFullProcessingCli.run(fixture.args(),
                stagedLauncher(succeeds(), succeeds()));
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, combined.status());
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_COMPLETED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
        assertEquals("run-reconstruction-fixture",
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID));
    }

    @Test
    void stageResultIdentityMismatchesAreRejectedAsMissing() throws Exception {
        assertIdentityMismatchRejected(result -> result.imageId("999999"), "image_id");
        assertIdentityMismatchRejected(result -> result.machineId("machine_99"), "machine_id");
        assertIdentityMismatchRejected(result -> result.attemptNumber(Integer.valueOf(9)), "attempt");
        assertIdentityMismatchRejected(
                result -> result.stage(BatchFullProcessingStage.CLASSIFICATION), "stage");
    }

    private void assertIdentityMismatchRejected(
            final java.util.function.Function<BatchFullProcessingStageResult.Builder,
                    BatchFullProcessingStageResult.Builder> mutator,
            final String label) throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(succeeds(), job -> {
            BatchFullProcessingStageResult.Builder builder = BatchFullProcessingStageResult.builder()
                    .stage(BatchFullProcessingStage.RECONSTRUCTION)
                    .status(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY)
                    .reasonCode("RECONSTRUCTION_OUT_OF_MEMORY")
                    .message("Java heap space")
                    .imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .attemptNumber(Integer.valueOf(job.attemptNumber()))
                    .outOfMemoryDetected(Boolean.TRUE)
                    .stageCompleted(Boolean.FALSE)
                    .requiredOutputsValidated(Boolean.FALSE);
            builder = mutator.apply(builder);
            // Force CLASSIFICATION stage mismatch without changing other fields when requested.
            if ("stage".equals(label)) {
                builder.stage(BatchFullProcessingStage.CLASSIFICATION);
            }
            builder.build().writeAtomic(job.resultPath());
            return new FakeProcess("out", "err", 0, false);
        });
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_UNEXPECTED, exitCode, label);
        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FAILED_UNEXPECTED, combined.status(), label);
        assertEquals("RECONSTRUCTION_STAGE_FAILED",
                combined.get(BatchFullProcessingResult.KEY_REASON), label);
        assertEquals(RCsvSupport.NA,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_STAGE_RESULT_PATH), label);
    }

    @Test
    void loadStageOutcomeAcceptsPathsContainingSpaces() throws Exception {
        final Path stageDir = tempDir.resolve("stage dir with spaces").toAbsolutePath().normalize();
        Files.createDirectories(stageDir);
        final Path jobPath = stageDir.resolve("job.properties");
        final Path resultPath = stageDir.resolve("result.properties");
        final Path source = tempDir.resolve("src.tif");
        Files.write(source, "x".getBytes(StandardCharsets.UTF_8));
        final Path classifier = tempDir.resolve("c.model");
        Files.write(classifier, "m".getBytes(StandardCharsets.UTF_8));
        final Path batchRoot = tempDir.resolve("batch spaces").toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        final Path datasetRoot = tempDir.resolve("dataset spaces").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);

        final BatchFullProcessingJob job = new BatchFullProcessingJob(
                1,
                SixDigitSampleId.parse(IMAGE_ID),
                MACHINE_ID,
                source.toAbsolutePath().normalize(),
                IMAGE_ID + ".tif",
                Sha256Files.sha256OfFile(source),
                batchRoot.resolve("cfg.properties").toAbsolutePath().normalize(),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                classifier.toAbsolutePath().normalize(),
                Sha256Files.sha256OfFile(classifier),
                0,
                new BatchTileProfile(1024, 1024),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/result.properties"),
                batchRoot.resolve("precheck/results.csv"),
                resultPath,
                stageDir.resolve("stdout.log"),
                stageDir.resolve("stderr.log"),
                BatchFullProcessingStage.RECONSTRUCTION);
        job.write(jobPath);
        BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.RECONSTRUCTION)
                .status(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY)
                .reasonCode("RECONSTRUCTION_OUT_OF_MEMORY")
                .message("Java heap space")
                .imageId(IMAGE_ID)
                .machineId(MACHINE_ID)
                .attemptNumber(Integer.valueOf(1))
                .outOfMemoryDetected(Boolean.TRUE)
                .stageCompleted(Boolean.FALSE)
                .requiredOutputsValidated(Boolean.FALSE)
                .build()
                .writeAtomic(resultPath);

        final BatchFullProcessingCli.StageOutcome outcome = BatchFullProcessingCli.loadStageOutcome(
                job, jobPath, Integer.valueOf(0), stageDir.resolve("stdout.log"),
                stageDir.resolve("stderr.log"));
        assertTrue(outcome.stageResult != null);
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, outcome.stageResult.status());
        assertEquals(resultPath.toAbsolutePath().normalize(), outcome.resultPath);
        assertTrue(outcome.oomDetected);
    }

    @Test
    void oomTextInStderrWithoutResultFileMapsToOutOfMemoryExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(),
                job -> new FakeProcess("out",
                        "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
                        1, false));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_OUT_OF_MEMORY, exitCode);

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, combined.status());
        assertEquals(RCsvSupport.TRUE, combined.get(BatchFullProcessingResult.KEY_OUT_OF_MEMORY_DETECTED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
    }

    @Test
    void missingReconstructionResultFileReturnsUnexpectedExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(), job -> new FakeProcess("out", "err", 1, false));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_UNEXPECTED, exitCode);
    }

    @Test
    void reconstructionTimeoutReturnsTimeoutExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(
                succeeds(), job -> new FakeProcess("out", "err", 0, true));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_TIMEOUT, exitCode);
    }

    @Test
    void classificationLaunchFailureReturnsUnexpectedExitCodeButStillAttemptsReconstruction()
            throws Exception {
        final Fixture fixture = buildFixture();
        final AtomicInteger launches = new AtomicInteger(0);
        final BatchFullProcessingCli.ProcessLauncher launcher = countingLauncher(launches, command -> {
            final Path jobPath = Paths.get(command.get(command.size() - 1));
            final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);
            if (job.processingStage() == BatchFullProcessingStage.CLASSIFICATION) {
                throw new IOException("no such launcher");
            }
            return succeeds().launch(job);
        });
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_UNEXPECTED, exitCode);
        assertEquals(2, launches.get(), "reconstruction must still be attempted after a launch failure");
    }

    @Test
    void reusablePriorClassificationSkipsClassificationLaunch() throws Exception {
        final Fixture fixture = buildFixture();
        final String sourceSha = Sha256Files.sha256OfFile(fixture.sourcePath);
        final String classifierSha = Sha256Files.sha256OfFile(fixture.classifierPath);
        seedReusableClassificationDataset(fixture, sourceSha, classifierSha);

        final AtomicInteger launches = new AtomicInteger(0);
        final BatchFullProcessingCli.ProcessLauncher launcher = countingLauncher(launches, command -> {
            final Path jobPath = Paths.get(command.get(command.size() - 1));
            final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);
            if (job.processingStage() == BatchFullProcessingStage.CLASSIFICATION) {
                throw new AssertionError("classification must not be launched when reusable: " + command);
            }
            return succeeds().launch(job);
        });

        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(1, launches.get(), "only the reconstruction stage should launch a Fiji process");

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_REUSED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_PROCESS_LAUNCHED));
        assertEquals("run-classification-1",
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_RUN_ID));
    }

    @Test
    void completeFinalDatasetSkipsBothStagesAndWritesAttemptResultOnly() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        Files.write(fixture.sourcePath, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(fixture.sourcePath));

        final AtomicInteger launches = new AtomicInteger(0);
        final int exitCode = BatchFullProcessingCli.run(
                fixture.args(), countingLauncher(launches, unusedLauncher()));
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(0, launches.get(), "completed dataset must launch no Fiji child");

        final Path attemptRoot = fixture.attemptRoot(1);
        assertTrue(Files.isRegularFile(attemptRoot.resolve("result.properties")));
        assertFalse(Files.exists(attemptRoot.resolve("classification")));
        assertFalse(Files.exists(attemptRoot.resolve("reconstruction")));

        final BatchFullProcessingResult combined =
                BatchFullProcessingResult.load(attemptRoot.resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, combined.status());
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_REUSED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_PROCESS_LAUNCHED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_PROCESS_LAUNCHED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_COMPLETED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_OUTPUTS_VALIDATED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_OUTPUTS_VALIDATED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_REQUIRED_OUTPUTS_VALIDATED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_PRECHECK_VERIFIED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_SOURCE_UNCHANGED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_OUT_OF_MEMORY_DETECTED));
        assertEquals(RCsvSupport.NA,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_RESULT_PATH));
        assertEquals(RCsvSupport.NA,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_STAGE_RESULT_PATH));
        assertEquals(RCsvSupport.NA,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_EXIT_CODE));
        assertEquals(RCsvSupport.NA,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_EXIT_CODE));
        assertEquals("class-run-1",
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_RUN_ID));
        assertEquals("recon-run-1",
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID));
        assertEquals("12", combined.get(BatchFullProcessingResult.KEY_TOTAL_COMPONENTS));
        assertEquals("10", combined.get(BatchFullProcessingResult.KEY_ELIGIBLE_COMPONENTS));
        assertEquals("3", combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_COMPONENTS));
        assertEquals("42", combined.get(BatchFullProcessingResult.KEY_CHANGED_PIXELS));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_ORIGINAL_RGB_PATH)));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_CLASSIFIED_PATH)));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_LUMEN_MEASUREMENTS_PATH)));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_ARTIFACT_DIAGNOSTICS_PATH)));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_IMAGE_MANIFEST_PATH)));
        assertFalse(RCsvSupport.NA.equals(
                combined.get(BatchFullProcessingResult.KEY_PROCESSING_RUNS_PATH)));

        // Idempotence: production manifests unchanged (still exactly two SUCCESS rows).
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        final List<List<String>> runs = ProductionCsvStore.readTable(layout.processingRuns());
        assertEquals(3, runs.size()); // header + class + recon
    }

    @Test
    void missingReconstructedTiffLaunchesReconstructionOnly() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(fixture.sourcePath));
        Files.delete(new ProductionDatasetLayout(datasetRoot).imagesReconstructedClassified()
                .resolve(FilenameSanitizer.reconstructedClassifiedFilename(IMAGE_ID, IMAGE_ID)));

        final AtomicInteger launches = new AtomicInteger(0);
        final BatchFullProcessingCli.ProcessLauncher launcher = countingLauncher(launches, command -> {
            final Path jobPath = Paths.get(command.get(command.size() - 1));
            final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);
            if (job.processingStage() == BatchFullProcessingStage.CLASSIFICATION) {
                throw new AssertionError("classification must not launch: " + command);
            }
            return succeeds().launch(job);
        });
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(1, launches.get());
        assertEquals(RCsvSupport.TRUE, BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"))
                .get(BatchFullProcessingResult.KEY_CLASSIFICATION_STAGE_REUSED));
    }

    @Test
    void missingPerSegmentMeasurementCsvLaunchesReconstructionOnly() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(fixture.sourcePath));
        Files.delete(new ProductionDatasetLayout(datasetRoot).dataLumenMeasurementsPerSegment()
                .resolve(FilenameSanitizer.lumenMeasurementsFilename(IMAGE_ID, IMAGE_ID)));

        final AtomicInteger launches = new AtomicInteger(0);
        final int exitCode = BatchFullProcessingCli.run(fixture.args(),
                countingLauncher(launches, stagedLauncher(
                        job -> {
                            throw new AssertionError("classification must not launch");
                        },
                        succeeds())));
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(1, launches.get());
    }

    @Test
    void missingDiagnosticsCsvLaunchesReconstructionOnly() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(fixture.sourcePath));
        Files.delete(new ProductionDatasetLayout(datasetRoot).dataDiagnosticsPerSegment()
                .resolve(FilenameSanitizer.artifactDiagnosticsFilename(IMAGE_ID, IMAGE_ID)));

        final AtomicInteger launches = new AtomicInteger(0);
        final int exitCode = BatchFullProcessingCli.run(fixture.args(),
                countingLauncher(launches, stagedLauncher(
                        job -> {
                            throw new AssertionError("classification must not launch");
                        },
                        succeeds())));
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(1, launches.get());
    }

    @Test
    void missingMaskedClassifiedLaunchesBothStages() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(fixture.sourcePath));
        Files.delete(new ProductionDatasetLayout(datasetRoot).imagesMaskedClassified()
                .resolve(FilenameSanitizer.maskedClassifiedFilename(IMAGE_ID, IMAGE_ID)));

        final AtomicInteger launches = new AtomicInteger(0);
        final int exitCode = BatchFullProcessingCli.run(fixture.args(),
                countingLauncher(launches, stagedLauncher(succeeds(), succeeds())));
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertEquals(2, launches.get());
    }

    @Test
    void canonicalConflictReturnsConflictExitWithoutLaunch() throws Exception {
        final Fixture fixture = buildFixture();
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, "different-bytes".getBytes(StandardCharsets.UTF_8));

        final int exitCode = BatchFullProcessingCli.run(fixture.args(), unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_CANONICAL_CONFLICT, exitCode);
        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(
                fixture.attemptRoot(1).resolve("result.properties"));
        assertEquals(BatchFullProcessingStatus.CANONICAL_CONFLICT, combined.status());
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_PROCESS_LAUNCHED));
        assertEquals(RCsvSupport.FALSE,
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_PROCESS_LAUNCHED));
    }

    @Test
    void secondAttemptUsesNextAttemptNumber() throws Exception {
        final Fixture fixture = buildFixture();
        Files.createDirectories(fixture.attemptRoot(1));
        final BatchFullProcessingCli.ProcessLauncher launcher = stagedLauncher(succeeds(), succeeds());
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), launcher);
        assertEquals(BatchFullProcessingCli.EXIT_SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(fixture.attemptRoot(2).resolve("result.properties")));
    }

    @Test
    void invalidConfigurationReturnsInvalidInputExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        Files.write(fixture.configPath, "not.a=valid.config\n".getBytes(StandardCharsets.UTF_8));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT, exitCode);
    }

    @Test
    void missingMachineManifestReturnsInvalidInputExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        Files.delete(fixture.manifestPath);
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT, exitCode);
    }

    @Test
    void sourceShaMismatchReturnsInvalidInputExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        // Mutate the source file after the manifest was written with the original SHA-256.
        Files.write(fixture.sourcePath, "corrupted-bytes".getBytes(StandardCharsets.UTF_8));
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT, exitCode);
    }

    @Test
    void missingClassifierReturnsInvalidInputExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        Files.delete(fixture.classifierPath);
        final int exitCode = BatchFullProcessingCli.run(fixture.args(), unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT, exitCode);
    }

    @Test
    void tileProfileIndexOutOfRangeReturnsInvalidInputExitCode() throws Exception {
        final Fixture fixture = buildFixture();
        final String[] args = fixture.args();
        args[args.length - 1] = "99";
        final int exitCode = BatchFullProcessingCli.run(args, unusedLauncher());
        assertEquals(BatchFullProcessingCli.EXIT_INVALID_INPUT, exitCode);
    }

    private interface StageAction {
        Process launch(BatchFullProcessingJob job) throws IOException;
    }

    private static BatchFullProcessingCli.ProcessLauncher stagedLauncher(
            final StageAction classificationAction, final StageAction reconstructionAction) {
        return command -> {
            final Path jobPath = Paths.get(command.get(command.size() - 1));
            final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);
            if (job.processingStage() == BatchFullProcessingStage.CLASSIFICATION) {
                return classificationAction.launch(job);
            }
            return reconstructionAction.launch(job);
        };
    }

    private static BatchFullProcessingCli.ProcessLauncher countingLauncher(
            final AtomicInteger counter, final BatchFullProcessingCli.ProcessLauncher delegate) {
        return command -> {
            counter.incrementAndGet();
            return delegate.start(command);
        };
    }

    private static StageAction succeeds() {
        return job -> {
            writeStageResultForJob(job, BatchFullProcessingStatus.FULL_SUCCESS, "stage committed",
                    true, false);
            return new FakeProcess("out", "err", 0, false);
        };
    }

    private static StageAction fails(final BatchFullProcessingStatus status, final String message) {
        return job -> {
            writeStageResultForJob(job, status, message, false,
                    status == BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY);
            return new FakeProcess("out", "err", 1, false);
        };
    }

    private static StageAction failsWithExitCode(
            final BatchFullProcessingStatus status,
            final String message,
            final String reasonCode,
            final boolean oom,
            final int exitCode) {
        return job -> {
            writeStageResultForJob(job, status, message, reasonCode, false, oom, null);
            return new FakeProcess("out", "err", exitCode, false);
        };
    }

    private static BatchFullProcessingCli.ProcessLauncher unusedLauncher() {
        return command -> {
            throw new AssertionError("Fiji should not have been launched: " + command);
        };
    }

    private static void writeStageResultForJob(
            final BatchFullProcessingJob job,
            final BatchFullProcessingStatus status,
            final String message,
            final boolean completed,
            final boolean oom) throws IOException {
        final String reason = status.isSuccess()
                ? RCsvSupport.NA
                : (status == BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY
                        ? (job.processingStage() == BatchFullProcessingStage.RECONSTRUCTION
                                ? "RECONSTRUCTION_OUT_OF_MEMORY"
                                : "CLASSIFICATION_OUT_OF_MEMORY")
                        : status.name());
        writeStageResultForJob(job, status, message, reason, completed, oom,
                completed ? (job.processingStage() == BatchFullProcessingStage.CLASSIFICATION
                        ? "run-classification-fixture" : "run-reconstruction-fixture") : null);
    }

    private static void writeStageResultForJob(
            final BatchFullProcessingJob job,
            final BatchFullProcessingStatus status,
            final String message,
            final String reasonCode,
            final boolean completed,
            final boolean oom,
            final String runId) throws IOException {
        final BatchFullProcessingStageResult.Builder builder = BatchFullProcessingStageResult.builder()
                .stage(job.processingStage())
                .status(status)
                .reasonCode(reasonCode)
                .message(message)
                .imageId(job.imageId().imageId())
                .machineId(job.machineId())
                .attemptNumber(Integer.valueOf(job.attemptNumber()))
                .outOfMemoryDetected(Boolean.valueOf(oom))
                .stageCompleted(Boolean.valueOf(completed && status.isSuccess()))
                .requiredOutputsValidated(Boolean.valueOf(completed && status.isSuccess()))
                .runId(runId)
                .processingTimeMs(1L);
        if (job.processingStage() == BatchFullProcessingStage.RECONSTRUCTION && status.isSuccess()) {
            builder.totalComponents(Integer.valueOf(10))
                    .eligibleComponents(Integer.valueOf(8))
                    .reconstructedComponents(Integer.valueOf(3))
                    .changedPixels(Integer.valueOf(100));
        }
        builder.build().writeAtomic(job.resultPath());
    }

    private void seedReusableClassificationDataset(
            final Fixture fixture, final String sourceSha, final String classifierSha) throws Exception {
        final Path datasetRoot = fixture.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        layout.ensureDirectoriesExist();

        final String safe = FilenameSanitizer.sanitizeForFilename(IMAGE_ID);
        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(safe, safe));
        Files.write(maskedClassified, "masked-classified-bytes".getBytes(StandardCharsets.UTF_8));
        final String maskedClassifiedSha = Sha256Files.sha256OfFile(maskedClassified);

        final List<String> imageRow = naRow(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        final Map<String, Integer> imageIdx =
                ProductionCsvStore.headerIndex(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        imageRow.set(imageIdx.get("schema_version").intValue(), "1");
        imageRow.set(imageIdx.get("sample_id").intValue(), IMAGE_ID);
        imageRow.set(imageIdx.get("segment_id").intValue(), IMAGE_ID);
        imageRow.set(imageIdx.get("source_rgb_sha256").intValue(), sourceSha);
        imageRow.set(imageIdx.get("masked_classified_sha256").intValue(), maskedClassifiedSha);
        imageRow.set(imageIdx.get("classifier_sha256").intValue(), classifierSha);
        imageRow.set(imageIdx.get("classification_run_id").intValue(), "run-classification-1");
        ProductionCsvStore.writeTableAtomically(
                layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                Collections.singletonList(imageRow));

        final List<String> runRow = naRow(ProductionSchemas.PROCESSING_RUNS_HEADER);
        final Map<String, Integer> runIdx =
                ProductionCsvStore.headerIndex(ProductionSchemas.PROCESSING_RUNS_HEADER);
        runRow.set(runIdx.get("schema_version").intValue(), "1");
        runRow.set(runIdx.get("run_id").intValue(), "run-classification-1");
        runRow.set(runIdx.get("stage").intValue(),
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name());
        runRow.set(runIdx.get("status").intValue(), ProductionSchemas.RunStatus.SUCCESS.name());
        runRow.set(runIdx.get("sample_id").intValue(), IMAGE_ID);
        runRow.set(runIdx.get("segment_id").intValue(), IMAGE_ID);
        ProductionCsvStore.writeTableAtomically(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER,
                Collections.singletonList(runRow));
    }

    private static List<String> naRow(final List<String> header) {
        return new ArrayList<String>(Collections.nCopies(header.size(), RCsvSupport.NA));
    }

    private String[] fullArgArray(final String... overrideLastPair) {
        final List<String> args = new ArrayList<String>(Arrays.asList(
                "--input-root", "/input",
                "--batch-root", "/batch",
                "--machine-id", MACHINE_ID,
                "--image-id", IMAGE_ID,
                "--config", "/batch/batch.properties",
                "--classifier", "/input/classifier.model",
                "--fiji-launcher", "/opt/fiji/ImageJ-linux64",
                "--tile-profile-index", "0"));
        if (overrideLastPair.length == 2) {
            args.add(overrideLastPair[0]);
            args.add(overrideLastPair[1]);
        }
        return args.toArray(new String[0]);
    }

    private Fixture buildFixture() throws Exception {
        final Path inputRoot = tempDir.resolve("input");
        final Path batchRoot = tempDir.resolve("batch");
        Files.createDirectories(inputRoot);
        Files.createDirectories(batchRoot);

        final Path sourcePath = inputRoot.resolve(IMAGE_ID + ".tif");
        Files.write(sourcePath, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final String sourceSha = Sha256Files.sha256OfFile(sourcePath);

        final Path classifierPath = inputRoot.resolve("classifier.model");
        Files.write(classifierPath, "model-bytes".getBytes(StandardCharsets.UTF_8));

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final BatchConfiguration config = BatchConfiguration.load(configPath);

        final Path manifestPath = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(MACHINE_ID));
        Files.createDirectories(manifestPath.getParent());
        final List<String> row = Arrays.asList(
                "1", "1", IMAGE_ID, "01", "01", "01",
                IMAGE_ID + ".tif", IMAGE_ID + ".tif", sourceSha, "10",
                MACHINE_ID, "0", "PENDING", "0", "NA", "NA", "NA", "NA",
                "0", BatchManifestSchema.datasetShardRelativePath(MACHINE_ID), config.sha256());
        ProductionCsvStore.writeTableAtomically(
                manifestPath, BatchManifestSchema.HEADER, Arrays.asList(row));

        final Path fijiLauncher = tempDir.resolve("ImageJ-linux64");
        Files.write(fijiLauncher, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));

        return new Fixture(
                inputRoot, batchRoot, sourcePath, classifierPath, configPath,
                manifestPath, fijiLauncher);
    }

    private static final class Fixture {
        final Path inputRoot;
        final Path batchRoot;
        final Path sourcePath;
        final Path classifierPath;
        final Path configPath;
        final Path manifestPath;
        final Path fijiLauncher;

        Fixture(
                final Path inputRoot,
                final Path batchRoot,
                final Path sourcePath,
                final Path classifierPath,
                final Path configPath,
                final Path manifestPath,
                final Path fijiLauncher) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.sourcePath = sourcePath;
            this.classifierPath = classifierPath;
            this.configPath = configPath;
            this.manifestPath = manifestPath;
            this.fijiLauncher = fijiLauncher;
        }

        Path attemptRoot(final int attemptNumber) {
            return batchRoot.resolve("full").resolve(MACHINE_ID).resolve(IMAGE_ID)
                    .resolve("attempt_" + attemptNumber);
        }

        String[] args() {
            return new String[] {
                "--input-root", inputRoot.toString(),
                "--batch-root", batchRoot.toString(),
                "--machine-id", MACHINE_ID,
                "--image-id", IMAGE_ID,
                "--config", configPath.toString(),
                "--classifier", classifierPath.toString(),
                "--fiji-launcher", fijiLauncher.toString(),
                "--tile-profile-index", "0"
            };
        }
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr;
        private final int exitCode;
        private final boolean neverFinishes;
        private volatile boolean destroyed;

        FakeProcess(
                final String stdoutText,
                final String stderrText,
                final int exitCode,
                final boolean neverFinishes) {
            this.stdout = new ByteArrayInputStream(stdoutText.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderrText.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
            this.neverFinishes = neverFinishes;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(final long timeout, final TimeUnit unit) {
            return !neverFinishes || destroyed;
        }

        @Override
        public int exitValue() {
            if (neverFinishes && !destroyed) {
                throw new IllegalThreadStateException("process has not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
