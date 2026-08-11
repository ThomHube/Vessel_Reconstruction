package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineSupervisorTest {

    private static final String MACHINE_ID = "machine_01";

    @TempDir
    Path tempDir;

    @Test
    void executesInStrictCoreSegmentAscendingOrder() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0201A2", "0102A1", "0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0102A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0201A2", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);

        assertEquals(Arrays.asList("0101A1", "0102A1", "0201A2"), launcher.launchedImageIds());
        assertEquals(BatchMachineSupervisorStatus.MACHINE_SUCCESS, result.status());
        assertEquals(3, result.succeededThisRunCount());
        assertEquals(100.0d, result.finalProgressPercent(), 0.001d);
    }

    @Test
    void fullSuccessIsSkippedWithoutLaunch() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        seedImageComplete(fixture, "0101A1");

        final RecordingLauncher launcher = RecordingLauncher.noop();
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);

        assertTrue(launcher.launches.isEmpty());
        assertEquals(1, result.skippedCompleteCount());
        assertEquals(0, result.attemptedCount());
        assertEquals(1, result.initialCompletedCount());
        assertEquals(1, result.finalCompletedCount());
        assertTrue(result.imageResults().get(0).skippedComplete());
        assertFalse(Files.exists(fixture.supervisorDir.resolve("logs")
                .resolve("0101A1__controller.log")));
        assertEquals(BatchMachineSupervisorStatus.MACHINE_SUCCESS, result.status());
    }

    @Test
    void readyLaunchesOnceAndClassificationCompleteLaunchesOnce() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0101A1", "0101A2"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0101A2", BatchPrecheckStatus.PRECHECK_SUCCESS);
        seedCompleteForImage(fixture.datasetRoot, "0101A2",
                Files.readAllBytes(fixture.inputRoot.resolve("0101A2.tif")));
        final CoreSegmentId resumeId = CoreSegmentId.parse("0101A2");
        Files.delete(fixture.datasetRoot.resolve("images/reconstructed_classified")
                .resolve(FilenameSanitizer.reconstructedClassifiedFilename(
                        resumeId.sampleId(), resumeId.segmentId())));

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);

        assertEquals(2, launcher.launches.size());
        assertEquals("0101A1", launcher.launches.get(0).imageId());
        assertEquals("0101A2", launcher.launches.get(1).imageId());
        assertEquals(BatchMachinePlannedAction.RUN_FULL_PROCESSING,
                result.imageResults().get(0).plannedAction());
        assertEquals(BatchMachinePlannedAction.RESUME_RECONSTRUCTION,
                result.imageResults().get(1).plannedAction());
        assertEquals(1, launcher.maxConcurrent.get());
    }

    @Test
    void blockedStatesDoNotLaunchAndContinue() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList(
                "0101A1", "0101A2", "0102A1", "0102A2", "0201A1", "0201A2"));
        // 0101A1: PRECHECK_MISSING (no result file)
        writePrecheck(fixture, "0101A2", BatchPrecheckStatus.FAILED_MASK_GENERATION);
        writePrecheck(fixture, "0102A1", BatchPrecheckStatus.REVIEW_REQUIRED_MASK);
        writePrecheck(fixture, "0102A2", BatchPrecheckStatus.PRECHECK_SUCCESS);
        seedCompleteForImage(fixture.datasetRoot, "0102A2",
                "conflict-bytes".getBytes(StandardCharsets.UTF_8));
        writeUnreadablePrecheck(fixture, "0201A1");
        writePrecheck(fixture, "0201A2", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);

        assertEquals(1, launcher.launches.size());
        assertEquals(Collections.singletonList("0201A2"), launcher.launchedImageIds());
        assertEquals(5, result.blockedCount());
        assertEquals(1, result.attemptedCount());
        assertEquals(BatchMachineImageState.CANONICAL_CONFLICT,
                result.imageResults().get(3).initialState());
        assertEquals(BatchMachineImageState.UNKNOWN_INVALID_STATE,
                result.imageResults().get(4).initialState());
        assertEquals(BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_BLOCKED_IMAGES,
                result.status());
        assertTrue(result.finalProgressPercent() < 100.0d);
    }

    @Test
    void oneControlledFailureDoesNotStopLaterImages() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0101A1", "0101A2"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0101A2", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = new RecordingLauncher();
        launcher.handler = new LaunchHandler() {
            @Override
            public int handle(final BatchMachineImageProcessLauncher.Request request)
                    throws Exception {
                touchControllerLog(request);
                if ("0101A1".equals(request.imageId())) {
                    writeAttemptResult(request, BatchFullProcessingStatus.FAILED_CLASSIFICATION,
                            1);
                    return 22;
                }
                seedImageComplete(fixture, request.imageId());
                writeAttemptResult(request, BatchFullProcessingStatus.FULL_SUCCESS, 1);
                return 0;
            }
        };

        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);
        assertEquals(2, launcher.launches.size());
        assertEquals(1, result.failedCount());
        assertEquals(1, result.succeededThisRunCount());
        assertEquals(BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES,
                result.status());
        assertEquals(50.0d, result.finalProgressPercent(), 0.001d);
    }

    @Test
    void exitCodeZeroAloneDoesNotCountAsComplete() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = new RecordingLauncher();
        launcher.handler = new LaunchHandler() {
            @Override
            public int handle(final BatchMachineImageProcessLauncher.Request request)
                    throws Exception {
                touchControllerLog(request);
                writeAttemptResult(request, BatchFullProcessingStatus.FULL_SUCCESS, 1);
                // deliberately do NOT seed complete dataset
                return 0;
            }
        };

        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);
        assertEquals(1, result.attemptedCount());
        assertEquals(0, result.succeededThisRunCount());
        assertEquals(0, result.finalCompletedCount());
        assertEquals(1, result.failedCount());
        assertEquals(BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES,
                result.status());
        assertTrue(result.imageResults().get(0).message().toLowerCase(Locale.ROOT)
                .contains("planner"));
    }

    @Test
    void replansAfterSuccessfulExecutionAndUpdatesProgressFile() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0101A1", "0101A2"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0101A2", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);

        assertEquals(0, result.initialCompletedCount());
        assertEquals(2, result.finalCompletedCount());
        assertEquals(100.0d, result.finalProgressPercent(), 0.001d);

        final Path progress = fixture.supervisorDir.resolve(
                BatchMachineSupervisor.PROGRESS_FILE);
        assertTrue(Files.isRegularFile(progress));
        final String text = new String(Files.readAllBytes(progress), StandardCharsets.UTF_8);
        assertFalse(text.toUpperCase(Locale.ROOT).contains("NAN"));
        assertTrue(text.contains("completed_count=2"));
        assertTrue(text.contains("progress_percent=100.0"));
        assertTrue(text.contains("current_image_id=NA"));
        assertTrue(text.contains("current_action=NA"));
    }

    @Test
    void summaryCsvHasOneRowPerAssignedImageInSequence() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0101A2", "0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        writePrecheck(fixture, "0101A2", BatchPrecheckStatus.FAILED_OPEN_IMAGE);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        runSupervisor(fixture, launcher);

        final Path summary = fixture.supervisorDir.resolve(BatchMachineSupervisor.SUMMARY_CSV);
        final List<String> lines = Files.readAllLines(summary, StandardCharsets.UTF_8);
        assertEquals(3, lines.size()); // header + 2
        assertTrue(lines.get(1).contains("0101A1"));
        assertTrue(lines.get(2).contains("0101A2"));
    }

    @Test
    void configurationFailurePreventsProcessingLoop() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        Files.delete(fixture.fijiLauncher);

        final RecordingLauncher launcher = RecordingLauncher.noop();
        assertThrows(BatchMachineSupervisor.ConfigurationException.class,
                () -> runSupervisor(fixture, launcher));
        assertTrue(launcher.launches.isEmpty());
        assertFalse(Files.exists(fixture.supervisorDir.resolve(
                BatchMachineSupervisor.PROGRESS_FILE)));
    }

    @Test
    void pathsContainingSpacesWorkAndCommandUsesJavaHomeAndClassesOnly() throws Exception {
        final Path spaced = tempDir.resolve("path with spaces");
        Files.createDirectories(spaced);
        final Fixture fixture = buildFixtureUnder(spaced, Collections.singletonList("0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);
        assertEquals(BatchMachineSupervisorStatus.MACHINE_SUCCESS, result.status());

        final List<String> command = launcher.launches.get(0).buildCommand();
        assertEquals(fixture.javaExecutable.toAbsolutePath().normalize().toString(),
                command.get(0));
        assertEquals("-cp", command.get(1));
        assertEquals(fixture.classesDir.toAbsolutePath().normalize().toString(),
                command.get(2));
        assertFalse(String.join(" ", command).contains("jars/fiji"));
        assertTrue(command.contains("--fiji-launcher"));
        assertEquals(fixture.fijiLauncher.toAbsolutePath().normalize().toString(),
                command.get(command.indexOf("--fiji-launcher") + 1));
        assertEquals("0", command.get(command.indexOf("--tile-profile-index") + 1));
        assertEquals(fixture.classifierPath.toAbsolutePath().normalize().toString(),
                command.get(command.indexOf("--classifier") + 1));
        assertEquals(MACHINE_ID, command.get(command.indexOf("--machine-id") + 1));
        assertEquals("0101A1", command.get(command.indexOf("--image-id") + 1));
        assertTrue(Files.isRegularFile(fixture.supervisorDir.resolve("logs")
                .resolve("0101A1__controller.log")));
    }

    @Test
    void zeroAssignedDoesNotProduceNan() throws Exception {
        final Fixture fixture = buildFixture(Collections.<String>emptyList());
        final RecordingLauncher launcher = RecordingLauncher.noop();
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);
        assertEquals(0.0d, result.initialProgressPercent(), 0.0d);
        assertEquals(0.0d, result.finalProgressPercent(), 0.0d);
        assertEquals(BatchMachineSupervisorStatus.MACHINE_SUCCESS, result.status());
        final String text = new String(Files.readAllBytes(
                fixture.supervisorDir.resolve(BatchMachineSupervisor.PROGRESS_FILE)),
                StandardCharsets.UTF_8);
        assertFalse(text.toUpperCase(Locale.ROOT).contains("NAN"));
    }

    @Test
    void supervisorDoesNotAlterCanonicalOutputsDirectlyAndDoesNotLoadImageJ() throws Exception {
        final Fixture fixture = buildFixture(Collections.singletonList("0101A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        seedImageComplete(fixture, "0101A1");
        final Path processingRuns = fixture.datasetRoot.resolve(
                "data/manifests/processing_runs.csv");
        final byte[] before = Files.readAllBytes(processingRuns);

        runSupervisor(fixture, RecordingLauncher.noop());

        assertEquals(
                Arrays.toString(before),
                Arrays.toString(Files.readAllBytes(processingRuns)));
        assertFalse(isImageJLoaded());
    }

    @Test
    void remainingAndCountsAreConsistent() throws Exception {
        final Fixture fixture = buildFixture(Arrays.asList("0101A1", "0101A2", "0102A1"));
        writePrecheck(fixture, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        seedImageComplete(fixture, "0101A1");
        // 0101A2 missing precheck
        writePrecheck(fixture, "0102A1", BatchPrecheckStatus.PRECHECK_SUCCESS);

        final RecordingLauncher launcher = RecordingLauncher.successSeeding(fixture);
        final BatchMachineSupervisorResult result = runSupervisor(fixture, launcher);
        assertEquals(1, result.initialCompletedCount());
        assertEquals(1, result.skippedCompleteCount());
        assertEquals(1, result.attemptedCount());
        assertEquals(1, result.succeededThisRunCount());
        assertEquals(1, result.blockedCount());
        assertEquals(0, result.failedCount());
        assertEquals(2, result.finalCompletedCount());
        assertEquals(1, result.remainingCount());
        assertEquals(BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_BLOCKED_IMAGES,
                result.status());
    }

    private BatchMachineSupervisorResult runSupervisor(
            final Fixture fixture,
            final BatchMachineImageProcessLauncher launcher) throws Exception {
        final StringBuilder terminal = new StringBuilder();
        final BatchMachineSupervisor.Request request = new BatchMachineSupervisor.Request(
                fixture.inputRoot,
                fixture.batchRoot,
                MACHINE_ID,
                fixture.configPath,
                fixture.classifierPath,
                fixture.fijiLauncher,
                0,
                fixture.classesDir,
                fixture.javaExecutable,
                launcher,
                terminal);
        return BatchMachineSupervisor.run(request);
    }

    private Fixture buildFixture(final List<String> imageIds) throws Exception {
        return buildFixtureUnder(tempDir.resolve("root-" + System.nanoTime()), imageIds);
    }

    private Fixture buildFixtureUnder(final Path root, final List<String> imageIds)
            throws Exception {
        final Path inputRoot = root.resolve("input");
        final Path batchRoot = root.resolve("batch");
        final Path classesDir = root.resolve("classes");
        Files.createDirectories(inputRoot);
        Files.createDirectories(batchRoot);
        Files.createDirectories(classesDir);

        final Path classifierPath = inputRoot.resolve("classifier.model");
        Files.write(classifierPath, "model-bytes".getBytes(StandardCharsets.UTF_8));

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final BatchConfiguration config = BatchConfiguration.load(configPath);

        final Path fijiLauncher = root.resolve("fiji-launcher.sh");
        Files.write(fijiLauncher, "#!/bin/sh\necho fake\n".getBytes(StandardCharsets.UTF_8));
        setExecutable(fijiLauncher);

        final Path javaExecutable = BatchMachineSupervisor.resolveJavaExecutable();

        final Fixture fixture = new Fixture(
                inputRoot, batchRoot, classifierPath, configPath, config,
                fijiLauncher, classesDir, javaExecutable);
        for (int i = 0; i < imageIds.size(); i++) {
            final String imageId = imageIds.get(i);
            Files.write(inputRoot.resolve(imageId + ".tif"),
                    ("tiff-" + imageId).getBytes(StandardCharsets.UTF_8));
        }
        writeManifest(fixture, imageIds);
        return fixture;
    }

    private void writeManifest(final Fixture fixture, final List<String> imageIds)
            throws Exception {
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < imageIds.size(); i++) {
            rows.add(manifestCells(fixture, imageIds.get(i), imageIds.get(i) + ".tif"));
        }
        final Path manifestPath = fixture.batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(MACHINE_ID));
        Files.createDirectories(manifestPath.getParent());
        ProductionCsvStore.writeTableAtomically(manifestPath, BatchManifestSchema.HEADER, rows);
    }

    private List<String> manifestCells(
            final Fixture fixture, final String imageId, final String relativePath)
            throws Exception {
        final Path source = fixture.inputRoot.resolve(relativePath);
        final String sha = Sha256Files.sha256OfFile(source);
        final CoreSegmentId id = CoreSegmentId.parse(imageId);
        return new ArrayList<String>(Arrays.asList(
                Integer.toString(BatchManifestSchema.SCHEMA_VERSION),
                "1", imageId, id.plotId(), id.treeId(),
                String.valueOf(id.coreLabel()), id.coreId(), Integer.toString(id.segmentOrder()),
                relativePath, relativePath, sha, "10",
                MACHINE_ID, "0", "PENDING", "0", "NA", "NA", "NA", "NA",
                "0", BatchManifestSchema.datasetShardRelativePath(MACHINE_ID),
                fixture.config.sha256()));
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

    private void writeUnreadablePrecheck(final Fixture fixture, final String imageId)
            throws Exception {
        final Path path = fixture.batchRoot
                .resolve(BatchPrecheckCsvSchema.machinePrecheckRoot(MACHINE_ID))
                .resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(imageId)
                .resolve("result.properties");
        Files.createDirectories(path.getParent());
        Files.write(path, "not=valid\nproperties\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void seedImageComplete(final Fixture fixture, final String imageId)
            throws Exception {
        seedCompleteForImage(
                fixture.datasetRoot,
                imageId,
                Files.readAllBytes(fixture.inputRoot.resolve(imageId + ".tif")));
    }

    /**
     * Seeds a finalized production dataset row for an arbitrary PPTTCS image ID.
     * Unlike {@link BatchProductionOutputValidatorTest#seedCompleteDataset}, this
     * upserts so multiple images can coexist in one dataset shard.
     */
    static void seedCompleteForImage(
            final Path datasetRoot,
            final String imageId,
            final byte[] sourceBytes) throws Exception {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        layout.ensureDirectoriesExist();

        final CoreSegmentId parsedId = CoreSegmentId.parse(imageId);
        final String sampleId = FilenameSanitizer.sanitizeForFilename(parsedId.sampleId());
        final String segmentId = FilenameSanitizer.sanitizeForFilename(parsedId.segmentId());

        final Path originalRgb = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(sampleId, segmentId));
        Files.write(originalRgb, sourceBytes);
        final String sha = Sha256Files.sha256OfFile(originalRgb);

        Files.write(layout.imagesApprovedMasks().resolve(
                        FilenameSanitizer.approvedMaskFilename(sampleId, segmentId, 8)),
                "mask".getBytes(StandardCharsets.UTF_8));
        Files.write(layout.imagesMaskedRgb().resolve(
                        FilenameSanitizer.maskedRgbFilename(sampleId, segmentId)),
                "masked-rgb".getBytes(StandardCharsets.UTF_8));
        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(sampleId, segmentId));
        Files.write(maskedClassified, "masked-classified".getBytes(StandardCharsets.UTF_8));
        final String maskedSha = Sha256Files.sha256OfFile(maskedClassified);
        Files.write(layout.imagesReconstructedClassified().resolve(
                        FilenameSanitizer.reconstructedClassifiedFilename(sampleId, segmentId)),
                "reconstructed".getBytes(StandardCharsets.UTF_8));

        final List<String> imageRow = blankRow(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("source_rgb_sha256"), sha);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("masked_classified_filename"),
                FilenameSanitizer.maskedClassifiedFilename(sampleId, segmentId));
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("masked_classified_sha256"),
                maskedSha);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_x_px"), "0");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_y_px"), "0");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_width_px"), "100");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_height_px"), "80");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_width_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_height_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("reconstructed_classified_filename"),
                FilenameSanitizer.reconstructedClassifiedFilename(sampleId, segmentId));
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("classification_run_id"),
                "class-run-" + imageId);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("reconstruction_run_id"),
                "recon-run-" + imageId);
        ProductionCsvStore.upsertByKeys(
                layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER, imageRow,
                "sample_id", "segment_id");

        final List<String> segmentRow = blankRow(ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        ProductionCsvStore.upsertByKeys(
                layout.segmentManifest(), ProductionSchemas.SEGMENT_MANIFEST_HEADER, segmentRow,
                "sample_id", "segment_id");

        final List<String> classRun = blankRow(ProductionSchemas.PROCESSING_RUNS_HEADER);
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("run_id"),
                "class-run-" + imageId);
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("stage"),
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("status"),
                ProductionSchemas.RunStatus.SUCCESS.name());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("input_filename"),
                FilenameSanitizer.originalRgbFilename(sampleId, segmentId));
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("output_filename"),
                FilenameSanitizer.maskedClassifiedFilename(sampleId, segmentId));
        ProductionCsvStore.upsertByKeys(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER, classRun,
                "sample_id", "segment_id", "stage");

        final List<String> reconRun = blankRow(ProductionSchemas.PROCESSING_RUNS_HEADER);
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("run_id"),
                "recon-run-" + imageId);
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("stage"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name());
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("status"),
                ProductionSchemas.RunStatus.SUCCESS.name());
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("input_filename"),
                FilenameSanitizer.maskedClassifiedFilename(sampleId, segmentId));
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("output_filename"),
                FilenameSanitizer.reconstructedClassifiedFilename(sampleId, segmentId));
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("minimum_lumen_area_px"),
                "1000");
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("total_components"), "12");
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("eligible_components"), "10");
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("reconstructed_components"),
                "3");
        reconRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("changed_pixels"), "42");
        ProductionCsvStore.upsertByKeys(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER, reconRun,
                "sample_id", "segment_id", "stage");

        final List<String> lumen = blankRow(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
        lumen.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("lumen_uid"), "L000001");
        lumen.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        lumen.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        lumen.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("area_um2"), "12.5");
        ProductionCsvStore.writeTableAtomically(
                layout.dataLumenMeasurementsPerSegment().resolve(
                        FilenameSanitizer.lumenMeasurementsFilename(sampleId, segmentId)),
                ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                Collections.singletonList(lumen));
        ProductionCsvStore.appendRow(
                layout.dataLumenMeasurementsAll(),
                ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                lumen);

        final List<String> diagnostics = blankRow(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER);
        diagnostics.set(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER.indexOf("sample_id"),
                parsedId.sampleId());
        diagnostics.set(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER.indexOf("segment_id"),
                parsedId.segmentId());
        diagnostics.set(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER.indexOf("lumen_uid"),
                "L000001");
        diagnostics.set(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER.indexOf("status"),
                "NO_INTERVAL_SELECTED");
        ProductionCsvStore.writeTableAtomically(
                layout.dataDiagnosticsPerSegment().resolve(
                        FilenameSanitizer.artifactDiagnosticsFilename(sampleId, segmentId)),
                ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER,
                Collections.singletonList(diagnostics));
        ProductionCsvStore.appendRow(
                layout.dataDiagnosticsAll(),
                ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER,
                diagnostics);
    }

    private static List<String> blankRow(final List<String> header) {
        final List<String> row = new ArrayList<String>(header.size());
        for (int i = 0; i < header.size(); i++) {
            row.add(RCsvSupport.NA);
        }
        return row;
    }

    private static void touchControllerLog(final BatchMachineImageProcessLauncher.Request request)
            throws Exception {
        Files.createDirectories(request.controllerLogPath().getParent());
        Files.write(request.controllerLogPath(), "controller\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAttemptResult(
            final BatchMachineImageProcessLauncher.Request request,
            final BatchFullProcessingStatus status,
            final int attemptNumber) throws Exception {
        final Path attemptRoot = request.batchRoot().resolve("full")
                .resolve(request.machineId()).resolve(request.imageId())
                .resolve("attempt_" + attemptNumber);
        Files.createDirectories(attemptRoot);
        BatchFullProcessingResult.builder()
                .status(status)
                .reasonCode(status.name())
                .message("fake launcher result")
                .imageId(request.imageId())
                .machineId(request.machineId())
                .sourceFilename(request.imageId() + ".tif")
                .sourceSha256("a")
                .configurationSha256("b")
                .classifierFilename("classifier.model")
                .classifierSha256("c")
                .attemptNumber(Integer.valueOf(attemptNumber))
                .tileProfile(Integer.valueOf(request.tileProfileIndex()),
                        Integer.valueOf(512), Integer.valueOf(512))
                .processingTimeMs(1L)
                .build()
                .write(attemptRoot.resolve("result.properties"));
    }

    private static void setExecutable(final Path path) throws Exception {
        try {
            final Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (final UnsupportedOperationException ex) {
            path.toFile().setExecutable(true);
        }
    }

    private static boolean isImageJLoaded() {
        for (final Class<?> clazz : new Class<?>[] {
                BatchMachineSupervisor.class,
                BatchMachineSupervisorCli.class,
                BatchMachineDefaultImageProcessLauncher.class
        }) {
            // Touch class names only — if ImageJ were a hard dependency of these
            // classes, loading them would already have failed earlier in the suite.
            assertNotNull(clazz.getName());
        }
        final ClassLoader cl = BatchMachineSupervisor.class.getClassLoader();
        try {
            Class.forName("ij.IJ", false, cl);
            // ImageJ may be on the test classpath from other tests; that is fine.
            // The assertion is that supervisor source does not require it to run.
            return false;
        } catch (final ClassNotFoundException ex) {
            return false;
        }
    }

    private interface LaunchHandler {
        int handle(BatchMachineImageProcessLauncher.Request request) throws Exception;
    }

    private static final class RecordingLauncher implements BatchMachineImageProcessLauncher {
        final List<Request> launches = new ArrayList<Request>();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        LaunchHandler handler;

        static RecordingLauncher noop() {
            final RecordingLauncher launcher = new RecordingLauncher();
            launcher.handler = new LaunchHandler() {
                @Override
                public int handle(final Request request) {
                    throw new AssertionError("launcher must not be called");
                }
            };
            return launcher;
        }

        static RecordingLauncher successSeeding(final Fixture fixture) {
            final RecordingLauncher launcher = new RecordingLauncher();
            launcher.handler = new LaunchHandler() {
                @Override
                public int handle(final Request request) throws Exception {
                    touchControllerLog(request);
                    seedImageComplete(fixture, request.imageId());
                    writeAttemptResult(request, BatchFullProcessingStatus.FULL_SUCCESS, 1);
                    return 0;
                }
            };
            return launcher;
        }

        @Override
        public LaunchResult launch(final Request request) throws IOException {
            launches.add(request);
            final int now = concurrent.incrementAndGet();
            int prev;
            do {
                prev = maxConcurrent.get();
                if (now <= prev) {
                    break;
                }
            } while (!maxConcurrent.compareAndSet(prev, now));
            try {
                Thread.sleep(5L); // make overlap detectable if concurrency ever appears
                final int code = handler.handle(request);
                return new LaunchResult(code, request.buildCommand());
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            } catch (final Exception ex) {
                if (ex instanceof IOException) {
                    throw (IOException) ex;
                }
                throw new IOException(ex);
            } finally {
                concurrent.decrementAndGet();
            }
        }

        List<String> launchedImageIds() {
            final List<String> ids = new ArrayList<String>();
            for (int i = 0; i < launches.size(); i++) {
                ids.add(launches.get(i).imageId());
            }
            return ids;
        }
    }

    private static final class Fixture {
        final Path inputRoot;
        final Path batchRoot;
        final Path classifierPath;
        final Path configPath;
        final BatchConfiguration config;
        final Path datasetRoot;
        final Path fijiLauncher;
        final Path classesDir;
        final Path javaExecutable;
        final Path supervisorDir;

        Fixture(
                final Path inputRoot,
                final Path batchRoot,
                final Path classifierPath,
                final Path configPath,
                final BatchConfiguration config,
                final Path fijiLauncher,
                final Path classesDir,
                final Path javaExecutable) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.classifierPath = classifierPath;
            this.configPath = configPath;
            this.config = config;
            this.datasetRoot = batchRoot.resolve(
                    BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
            this.fijiLauncher = fijiLauncher;
            this.classesDir = classesDir;
            this.javaExecutable = javaExecutable;
            this.supervisorDir = BatchMachineSupervisor.supervisorRoot(batchRoot, MACHINE_ID);
        }
    }
}
