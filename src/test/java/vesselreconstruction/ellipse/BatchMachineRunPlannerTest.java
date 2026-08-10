package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineRunPlannerTest {

    private static final String MACHINE_ID = "machine_01";

    @TempDir
    Path tempDir;

    @Test
    void sortsInStrictSixDigitAscendingOrderIndependentOfInputOrder() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList(
                "020102", "010201", "010101", "010102"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "010102", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "010201", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "020102", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(Arrays.asList("010101", "010102", "010201", "020102"), imageIds(plan));
        assertEquals(1, plan.images().get(0).sequenceNumber());
        assertEquals(4, plan.images().get(3).sequenceNumber());
    }

    @Test
    void duplicateImageIdsRejected() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("010101", "010102"));
        // Corrupt manifest by rewriting with a duplicate ID.
        writeManifest(fixture, Arrays.asList("010101", "010101"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanner.plan(fixture.request()));
    }

    @Test
    void malformedImageIdRejected() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writeManifestRaw(fixture, Arrays.asList(manifestCells(fixture, "999999", "999999.tif")));
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanner.plan(fixture.request()));
        assertTrue(ex.getMessage().toLowerCase().contains("plot")
                || ex.getMessage().toLowerCase().contains("image"));
    }

    @Test
    void wrongMachineAssignmentRejected() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        final List<String> cells = manifestCells(fixture, "010101", "010101.tif");
        cells.set(BatchManifestSchema.HEADER.indexOf("assigned_machine"), "machine_02");
        writeManifestRaw(fixture, Arrays.asList(cells));
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanner.plan(fixture.request()));
    }

    @Test
    void missingTiffRejected() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        Files.delete(fixture.inputRoot.resolve("010101.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanner.plan(fixture.request()));
    }

    @Test
    void sourceShaMismatchRejected() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        Files.write(fixture.inputRoot.resolve("010101.tif"),
                "corrupted".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanner.plan(fixture.request()));
    }

    @Test
    void precheckMissingMapsToRunPrecheck() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.PRECHECK_MISSING, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.RUN_PRECHECK, plan.images().get(0).plannedAction());
    }

    @Test
    void precheckFailedMapsToRunPrecheck() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.FAILED_MASK_GENERATION);
        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.PRECHECK_FAILED, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.RUN_PRECHECK, plan.images().get(0).plannedAction());
    }

    @Test
    void reviewRequiredMapsToWaitForReview() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.REVIEW_REQUIRED_MASK);
        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.PRECHECK_REVIEW_REQUIRED, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.WAIT_FOR_REVIEW, plan.images().get(0).plannedAction());
    }

    @Test
    void precheckSuccessWithoutClassificationIsReadyForFullProcessing() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.READY_FOR_FULL_PROCESSING, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.RUN_FULL_PROCESSING,
                plan.images().get(0).plannedAction());
    }

    @Test
    void classificationCompleteMapsToResumeReconstruction() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                fixture.datasetRoot, Files.readAllBytes(fixture.inputRoot.resolve("010101.tif")));
        Files.delete(fixture.datasetRoot.resolve("images/reconstructed_classified")
                .resolve(FilenameSanitizer.reconstructedClassifiedFilename("010101", "010101")));

        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.CLASSIFICATION_COMPLETE, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.RESUME_RECONSTRUCTION,
                plan.images().get(0).plannedAction());
    }

    @Test
    void finalizedValidDatasetMapsToFullSuccessSkip() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                fixture.datasetRoot, Files.readAllBytes(fixture.inputRoot.resolve("010101.tif")));

        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.FULL_SUCCESS, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.SKIP_COMPLETE, plan.images().get(0).plannedAction());
        assertEquals("class-run-1", plan.images().get(0).classificationRunId());
        assertEquals("recon-run-1", plan.images().get(0).reconstructionRunId());
    }

    @Test
    void canonicalConflictMapsCorrectly() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                fixture.datasetRoot, "different-bytes".getBytes(StandardCharsets.UTF_8));

        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(BatchMachineImageState.CANONICAL_CONFLICT, plan.images().get(0).state());
        assertEquals(BatchMachinePlannedAction.STOP_FOR_CANONICAL_CONFLICT,
                plan.images().get(0).plannedAction());
    }

    @Test
    void plannerIsReadOnly() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("010101"));
        writePrecheck(fixture, "010101", BatchPrecheckStatus.PRECHECK_SUCCESS);
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                fixture.datasetRoot, Files.readAllBytes(fixture.inputRoot.resolve("010101.tif")));

        final Path processingRuns = fixture.datasetRoot.resolve(
                "data/manifests/processing_runs.csv");
        final byte[] beforeRuns = Files.readAllBytes(processingRuns);
        final Path fullRoot = fixture.batchRoot.resolve("full");
        assertFalse(Files.exists(fullRoot));

        BatchMachineRunPlanner.plan(fixture.request());
        BatchMachineRunPlanner.plan(fixture.request());

        assertEquals(java.util.Arrays.toString(beforeRuns),
                java.util.Arrays.toString(Files.readAllBytes(processingRuns)));
        assertFalse(Files.exists(fullRoot));
        assertFalse(Files.exists(fixture.datasetRoot.resolve("logs")
                .resolve("should-not-exist.log")));
    }

    @Test
    void everyImageAppearsOnceWithContiguousSequence() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("010101", "010102", "020101"));
        // intentionally omit 010101 and 010102 precheck files
        writePrecheck(fixture, "020101", BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR);
        final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(fixture.request());
        assertEquals(3, plan.assignedCount());
        assertEquals(3, plan.images().size());
        for (int i = 0; i < plan.images().size(); i++) {
            assertEquals(i + 1, plan.images().get(i).sequenceNumber());
        }
    }

    private static List<String> imageIds(final BatchMachineRunPlan plan) {
        final List<String> ids = new ArrayList<String>();
        for (int i = 0; i < plan.images().size(); i++) {
            ids.add(plan.images().get(i).imageId());
        }
        return ids;
    }

    private Fixture buildFixture(final List<String> imageIds) throws Exception {
        final Path inputRoot = tempDir.resolve("input-" + System.nanoTime());
        final Path batchRoot = tempDir.resolve("batch-" + System.nanoTime());
        Files.createDirectories(inputRoot);
        Files.createDirectories(batchRoot);

        final Path classifierPath = inputRoot.resolve("classifier.model");
        Files.write(classifierPath, "model-bytes".getBytes(StandardCharsets.UTF_8));

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final BatchConfiguration config = BatchConfiguration.load(configPath);

        final Fixture fixture = new Fixture(inputRoot, batchRoot, classifierPath, configPath, config);
        for (int i = 0; i < imageIds.size(); i++) {
            final String imageId = imageIds.get(i);
            Files.write(inputRoot.resolve(imageId + ".tif"),
                    ("tiff-" + imageId).getBytes(StandardCharsets.UTF_8));
        }
        writeManifest(fixture, imageIds);
        return fixture;
    }

    private void writeManifest(final Fixture fixture, final List<String> imageIds) throws Exception {
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < imageIds.size(); i++) {
            rows.add(manifestCells(fixture, imageIds.get(i), imageIds.get(i) + ".tif"));
        }
        writeManifestRaw(fixture, rows);
    }

    private void writeManifestRaw(final Fixture fixture, final List<List<String>> rows)
            throws Exception {
        final Path manifestPath = fixture.batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(MACHINE_ID));
        Files.createDirectories(manifestPath.getParent());
        ProductionCsvStore.writeTableAtomically(manifestPath, BatchManifestSchema.HEADER, rows);
    }

    private List<String> manifestCells(
            final Fixture fixture, final String imageId, final String relativePath) throws Exception {
        final Path source = fixture.inputRoot.resolve(relativePath);
        final String sha = Files.isRegularFile(source)
                ? Sha256Files.sha256OfFile(source)
                : "0000000000000000000000000000000000000000000000000000000000000000";
        final SixDigitSampleId id = imageId.matches("\\d{6}") && isLikelyValid(imageId)
                ? SixDigitSampleId.parse(imageId)
                : null;
        final String plot = id == null ? "99" : id.plotId();
        final String tree = id == null ? "99" : id.treeId();
        final String replicate = id == null ? "99" : id.replicateId();
        return new ArrayList<String>(Arrays.asList(
                "1", "1", imageId, plot, tree, replicate,
                relativePath, relativePath, sha, "10",
                MACHINE_ID, "0", "PENDING", "0", "NA", "NA", "NA", "NA",
                "0", BatchManifestSchema.datasetShardRelativePath(MACHINE_ID),
                fixture.config.sha256()));
    }

    private static boolean isLikelyValid(final String imageId) {
        try {
            SixDigitSampleId.parse(imageId);
            return true;
        } catch (final RuntimeException ex) {
            return false;
        }
    }

    private void writePrecheck(
            final Fixture fixture,
            final String imageId,
            final BatchPrecheckStatus status) throws Exception {
        final Path path = fixture.batchRoot
                .resolve(BatchPrecheckCsvSchema.machinePrecheckRoot(MACHINE_ID))
                .resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(imageId)
                .resolve("result.properties");
        Files.createDirectories(path.getParent());
        final String sourceSha = Sha256Files.sha256OfFile(fixture.inputRoot.resolve(imageId + ".tif"));
        BatchPrecheckResult.builder()
                .status(status)
                .message("precheck fixture")
                .reasonCode(status.isSuccess() ? RCsvSupport.NA : status.name())
                .imageId(imageId)
                .machineId(MACHINE_ID)
                .sourceFilename(imageId + ".tif")
                .sourceRelativePath(imageId + ".tif")
                .sourceSha256(sourceSha)
                .configurationSha256(fixture.config.sha256())
                .imageSize(1000, 800)
                .maskStats(Integer.valueOf(120), Integer.valueOf(1), 0.4, 0.7,
                        Long.valueOf(500000L), 0.05, Integer.valueOf(12345))
                .crop(Integer.valueOf(10), Integer.valueOf(20), Integer.valueOf(500),
                        Integer.valueOf(400))
                .scaleBar("TERMINAL_TICK_CENTERLINES", "HIGH", 0.9, 456.0, 20.0, 0.98,
                        1.0954306217917742, 0.0)
                .sourceChecksums(Integer.valueOf(1), Integer.valueOf(1), Boolean.TRUE)
                .previews(RCsvSupport.NA, RCsvSupport.NA)
                .processingTimeMs(10L)
                .build()
                .write(path);
    }

    private static final class Fixture {
        final Path inputRoot;
        final Path batchRoot;
        final Path classifierPath;
        final Path configPath;
        final BatchConfiguration config;
        final Path datasetRoot;

        Fixture(
                final Path inputRoot,
                final Path batchRoot,
                final Path classifierPath,
                final Path configPath,
                final BatchConfiguration config) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.classifierPath = classifierPath;
            this.configPath = configPath;
            this.config = config;
            this.datasetRoot = batchRoot.resolve(
                    BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        }

        BatchMachineRunPlanner.Request request() {
            return new BatchMachineRunPlanner.Request(
                    inputRoot, batchRoot, MACHINE_ID, configPath, classifierPath, 0);
        }
    }
}
