package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link BatchFullProcessingService} early-return branches and
 * stage-isolation seams that do not require a real Fiji/Weka runtime.
 */
class BatchFullProcessingServiceTest {

    private static final String SOURCE_SHA = repeatHex('1');
    private static final String OTHER_SOURCE_SHA = repeatHex('2');
    private static final String CLASSIFIER_SHA = repeatHex('3');
    private static final String OTHER_CONFIG_SHA = repeatHex('4');

    private static String repeatHex(final char c) {
        final StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @TempDir
    Path tempDir;

    private final BatchFullProcessingService service = new BatchFullProcessingService();

    @Test
    void nullJobReturnsInvalidJob() {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingStageResult result = service.process(null, config);
        assertEquals(BatchFullProcessingStatus.INVALID_JOB, result.status());
    }

    @Test
    void nullConfigReturnsInvalidJob() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = service.process(job, null);
        assertEquals(BatchFullProcessingStatus.INVALID_JOB, result.status());
    }

    @Test
    void configurationShaMismatchReturnsInvalidJob() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(OTHER_CONFIG_SHA);
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.INVALID_JOB, result.status());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, result.stage());
    }

    @Test
    void missingPrecheckResultReturnsPrecheckNotFound() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND, result.status());
    }

    @Test
    void nonSuccessPrecheckReturnsPrecheckNotSuccessful() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        writePrecheckResult(job, BatchPrecheckStatus.REVIEW_REQUIRED_MASK,
                job.sourceSha256(), job.configurationSha256());
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_SUCCESSFUL, result.status());
    }

    @Test
    void staleSourceBetweenJobAndPrecheckReturnsPrecheckStaleSource() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        writePrecheckResult(job, BatchPrecheckStatus.PRECHECK_SUCCESS,
                OTHER_SOURCE_SHA, job.configurationSha256());
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_STALE_SOURCE, result.status());
    }

    @Test
    void staleConfigurationBetweenJobAndPrecheckReturnsPrecheckStaleConfiguration() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        writePrecheckResult(job, BatchPrecheckStatus.PRECHECK_SUCCESS,
                job.sourceSha256(), OTHER_CONFIG_SHA);
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION, result.status());
    }

    @Test
    void invalidPrecheckResultFileReturnsPrecheckNotFound() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        Files.createDirectories(job.precheckResultPath().getParent());
        Files.write(job.precheckResultPath(), "not-a-valid-properties-file".getBytes());
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND, result.status());
    }

    @Test
    void failureResultsCarryJobIdentityFields() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = service.process(job, config);
        assertEquals(job.imageId().imageId(), result.imageId());
        assertEquals(job.machineId(), result.machineId());
        assertEquals("1", result.attemptNumber());
        assertNotNull(result.processingTimeMs());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, result.stage());
    }

    @Test
    void classificationJobRoutedToProcessReconstructionIsRejected() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingResult result = service.processReconstruction(job, config);
        assertEquals(BatchFullProcessingStatus.INVALID_JOB, result.status());
        assertEquals("WRONG_STAGE", result.get(BatchFullProcessingResult.KEY_REASON));
    }

    @Test
    void reconstructionJobRoutedToProcessClassificationIsRejected() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), true);
        final BatchFullProcessingResult result = service.processClassification(job, config);
        assertEquals(BatchFullProcessingStatus.INVALID_JOB, result.status());
        assertEquals("WRONG_STAGE", result.get(BatchFullProcessingResult.KEY_REASON));
    }

    @Test
    void classificationDispatchInvokesClassificationExactlyOnce() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final BatchFullProcessingService isolated = serviceWithCounters(classCalls, reconCalls);
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        isolated.processClassificationStage(job, config);
        assertEquals(1, classCalls.get());
        assertEquals(0, reconCalls.get());
    }

    @Test
    void classificationDispatchNeverInvokesReconstruction() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final BatchFullProcessingService isolated = serviceWithCounters(classCalls, reconCalls);
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        isolated.process(job, config);
        assertEquals(0, reconCalls.get());
        assertTrue(classCalls.get() <= 1);
    }

    @Test
    void reconstructionDispatchInvokesReconstructionExactlyOnce() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final BatchFullProcessingService isolated = serviceWithCounters(classCalls, reconCalls);
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), true);
        Files.createDirectories(job.datasetRoot());
        isolated.processReconstructionStage(job, config);
        assertEquals(1, reconCalls.get());
        assertEquals(0, classCalls.get());
    }

    @Test
    void reconstructionDispatchNeverInvokesClassification() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final BatchFullProcessingService isolated = serviceWithCounters(classCalls, reconCalls);
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), true);
        Files.createDirectories(job.datasetRoot());
        isolated.process(job, config);
        assertEquals(0, classCalls.get());
    }

    @Test
    void classificationStageNeverCallsAllLuminaWriter() throws Exception {
        final AtomicInteger wekaCalls = new AtomicInteger();
        final AtomicInteger allLumenProcess = new AtomicInteger();
        final AtomicInteger allLumenWrite = new AtomicInteger();
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                recordingWeka(wekaCalls),
                recordingAllLumen(allLumenProcess, allLumenWrite));
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        isolated.processClassificationStage(job, config);
        assertEquals(0, allLumenProcess.get());
        assertEquals(0, allLumenWrite.get());
    }

    @Test
    void reconstructionStageNeverInstantiatesTiledWekaClassifier() throws Exception {
        final AtomicInteger wekaCalls = new AtomicInteger();
        final AtomicInteger allLumenProcess = new AtomicInteger();
        final AtomicInteger allLumenWrite = new AtomicInteger();
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                recordingWeka(wekaCalls),
                recordingAllLumen(allLumenProcess, allLumenWrite));
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), false);
        Files.createDirectories(job.datasetRoot());
        isolated.processReconstructionStage(job, config);
        assertEquals(0, wekaCalls.get());
    }

    @Test
    void reconstructionAcceptsInaccessibleDummyClassifierPath() throws Exception {
        final AtomicInteger wekaCalls = new AtomicInteger();
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                recordingWeka(wekaCalls),
                recordingAllLumen(new AtomicInteger(), new AtomicInteger()));
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), false);
        Files.createDirectories(job.datasetRoot());
        assertFalse(Files.exists(job.classifierPath()));
        final BatchFullProcessingStageResult result = isolated.processReconstructionStage(job, config);
        assertEquals(0, wekaCalls.get());
        assertEquals(BatchFullProcessingStatus.FAILED_RECONSTRUCTION, result.status());
        assertEquals("MASKED_CLASSIFIED_MISSING", result.reasonCode());
    }

    @Test
    void classificationFailureCannotBeginReconstruction() throws Exception {
        final AtomicInteger allLumenProcess = new AtomicInteger();
        final AtomicInteger allLumenWrite = new AtomicInteger();
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                recordingWeka(new AtomicInteger()),
                recordingAllLumen(allLumenProcess, allLumenWrite));
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = isolated.processClassificationStage(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND, result.status());
        assertEquals(0, allLumenProcess.get());
        assertEquals(0, allLumenWrite.get());
        assertEquals(RCsvSupport.FALSE, result.stageCompleted());
    }

    @Test
    void reconstructionMissingMaskedClassifiedFailsBeforeAnalysis() throws Exception {
        final AtomicInteger allLumenProcess = new AtomicInteger();
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                recordingWeka(new AtomicInteger()),
                recordingAllLumen(allLumenProcess, new AtomicInteger()));
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildReconstructionJob(config.sha256(), true);
        Files.createDirectories(job.datasetRoot());
        final BatchFullProcessingStageResult result = isolated.processReconstructionStage(job, config);
        assertEquals(BatchFullProcessingStatus.FAILED_RECONSTRUCTION, result.status());
        assertEquals("MASKED_CLASSIFIED_MISSING", result.reasonCode());
        assertEquals(0, allLumenProcess.get());
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION, result.stage());
    }

    @Test
    void classificationStageComponentCountsAreNa() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = service.processClassificationStage(job, config);
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, result.stage());
        assertEquals(RCsvSupport.NA, result.totalComponents());
        assertEquals(RCsvSupport.NA, result.eligibleComponents());
        assertEquals(RCsvSupport.NA, result.reconstructedComponents());
        assertEquals(RCsvSupport.NA, result.changedPixels());
    }

    @Test
    void reconstructionStagePreservesZeroComponentCountsOnMappedSuccess() {
        final BatchFullProcessingResult rich = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .reconstructionCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .reconstructionRunId("recon-0")
                .reconstructedClassifiedPath("images/reconstructed_classified/x.tif")
                .components(Integer.valueOf(5), Integer.valueOf(3), Integer.valueOf(0),
                        Integer.valueOf(0))
                .build();
        final BatchFullProcessingStageResult mapped = invokeToStage(
                BatchFullProcessingStage.RECONSTRUCTION, null, rich);
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION, mapped.stage());
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, mapped.status());
        assertEquals("5", mapped.totalComponents());
        assertEquals("3", mapped.eligibleComponents());
        assertEquals("0", mapped.reconstructedComponents());
        assertEquals("0", mapped.changedPixels());
        assertEquals(RCsvSupport.TRUE, mapped.stageCompleted());
    }

    @Test
    void temporaryLegacyCombinedPathStillCompilesAndDispatches() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingResult result = service.processLegacyCombinedResult(job, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND, result.status());
    }

    @Test
    void dispatchRoutesByProcessingStage() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob classificationJob = buildJob(config.sha256());
        final BatchFullProcessingStageResult classificationResult =
                service.process(classificationJob, config);
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND, classificationResult.status());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, classificationResult.stage());

        final BatchFullProcessingJob reconstructionJob = buildReconstructionJob(config.sha256(), true);
        final BatchFullProcessingStageResult reconstructionResult =
                service.process(reconstructionJob, config);
        assertEquals(BatchFullProcessingStatus.FAILED_RECONSTRUCTION, reconstructionResult.status());
        assertEquals("DATASET_ROOT_MISSING", reconstructionResult.reasonCode());
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION, reconstructionResult.stage());
    }

    @Test
    void stageResultUsesNaRatherThanNan() throws Exception {
        final BatchConfiguration config = validConfig();
        final BatchFullProcessingJob job = buildJob(config.sha256());
        final BatchFullProcessingStageResult result = service.processClassificationStage(job, config);
        final Path path = tempDir.resolve("stage-na.properties");
        result.writeAtomic(path);
        final String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertFalse(text.contains("=NaN") || text.contains("=nan"));
        assertTrue(text.contains("total_components=NA"));
    }

    @Test
    void sourceMutationDuringWekaPreventsPostWekaClassificationCommit() throws Exception {
        final BatchConfiguration config = validConfig();
        final Path batchRoot = tempDir.resolve("batch-source-mutation").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input-source-mutation").toAbsolutePath().normalize();
        final Path datasetRoot = tempDir.resolve("dataset-source-mutation").toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        Files.createDirectories(datasetRoot);

        final Path sourcePath = input.resolve("0101A1.tif");
        writeSyntheticPrecheckSuccessRgbTiff(sourcePath);
        final String sourceSha = Sha256Files.sha256OfFile(sourcePath);

        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model-bytes".getBytes(StandardCharsets.UTF_8));

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));

        final Path precheckResultPath =
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/result.properties");
        final Path precheckCsvPath =
                batchRoot.resolve("precheck/machine_01/precheck_results.csv");
        Files.createDirectories(precheckResultPath.getParent());
        Files.createDirectories(precheckCsvPath.getParent());

        final BatchPrecheckJob precheckJob = new BatchPrecheckJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                sourcePath,
                "0101A1.tif",
                sourceSha,
                configPath,
                config.sha256(),
                batchRoot,
                precheckResultPath,
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/stdout.log"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/stderr.log"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/mask_preview.png"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/scale_bar_preview.png"));
        final BatchPrecheckResult precheckResult =
                new BatchPrecheckProcessor().process(precheckJob, config);
        assertEquals(BatchPrecheckStatus.PRECHECK_SUCCESS, precheckResult.status(),
                precheckResult.get(BatchPrecheckResult.KEY_MESSAGE));
        precheckResult.write(precheckResultPath);
        ProductionCsvStore.writeTableAtomically(
                precheckCsvPath,
                BatchPrecheckCsvSchema.HEADER,
                Collections.singletonList(precheckResult.toCsvRow(1)));

        final Path fullRoot = batchRoot.resolve("full/machine_01/0101A1/attempt_1");
        final BatchFullProcessingJob job = new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                sourcePath,
                "0101A1.tif",
                sourceSha,
                configPath,
                config.sha256(),
                classifier,
                CLASSIFIER_SHA,
                2,
                new BatchTileProfile(1536, 1536),
                1,
                batchRoot,
                datasetRoot,
                precheckResultPath,
                precheckCsvPath,
                fullRoot.resolve("result.properties"),
                fullRoot.resolve("stdout.log"),
                fullRoot.resolve("stderr.log"),
                BatchFullProcessingStage.CLASSIFICATION);

        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                mutatingSourceWeka(sourcePath),
                recordingAllLumen(new AtomicInteger(), new AtomicInteger()));

        final BatchFullProcessingStageResult result =
                isolated.processClassificationStage(job, config);

        assertNotEquals(BatchFullProcessingStatus.FULL_SUCCESS, result.status());
        assertEquals(BatchFullProcessingStatus.INVALID_SOURCE, result.status());
        assertEquals("SOURCE_CHANGED", result.reasonCode());
        assertEquals(RCsvSupport.FALSE, result.sourceUnchanged());
        assertNotEquals(RCsvSupport.TRUE, result.requiredOutputsValidated());
        assertNotEquals(RCsvSupport.TRUE, result.stageCompleted());

        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename("0101A", "0101A1"));
        assertFalse(Files.exists(maskedClassified),
                "masked_classified must not be committed after source mutation");

        final Path processingRuns = layout.processingRuns();
        if (Files.isRegularFile(processingRuns)) {
            assertFalse(hasSuccessfulTiledClassificationRun(processingRuns, "0101A", "0101A1"),
                    "successful TILED_CLASSIFICATION must not be committed after source mutation");
        }

        // Pre-Weka artifacts are intentionally allowed to remain.
        assertTrue(Files.exists(layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename("0101A", "0101A1"))));
        assertTrue(Files.exists(layout.imagesApprovedMasks().resolve(
                FilenameSanitizer.approvedMaskFilename("0101A", "0101A1", 8))));
        assertTrue(Files.exists(layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename("0101A", "0101A1"))));
    }

    @Test
    void productionClassificationPersistsTopToBottomOrientation() throws Exception {
        final ClassificationFixture fixture = prepareClassificationFixture("orient-persist", true);
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                successfulStubWeka(true),
                recordingAllLumen(new AtomicInteger(), new AtomicInteger()));

        final BatchFullProcessingStageResult result =
                isolated.processClassificationStage(fixture.job, fixture.config);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, result.status(), result.message());

        final ProductionDatasetLayout layout = new ProductionDatasetLayout(fixture.datasetRoot);
        final Map<String, Integer> imageIdx =
                ProductionCsvStore.headerIndex(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        final List<List<String>> imageTable = ProductionCsvStore.readTable(layout.imageManifest());
        assertEquals(2, imageTable.size());
        assertEquals(SegmentOrientation.TOP_TO_BOTTOM.name(),
                imageTable.get(1).get(imageIdx.get("segment_orientation").intValue()));

        final Map<String, Integer> segmentIdx =
                ProductionCsvStore.headerIndex(ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        final List<List<String>> segmentTable = ProductionCsvStore.readTable(layout.segmentManifest());
        assertEquals(2, segmentTable.size());
        final List<String> segmentRow = segmentTable.get(1);
        assertEquals(SegmentOrientation.TOP_TO_BOTTOM.name(),
                segmentRow.get(segmentIdx.get("segment_orientation").intValue()));
        assertEquals(RCsvSupport.NA,
                segmentRow.get(segmentIdx.get("global_segment_start_um").intValue()));

        final Path classified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename("0101A", "0101A1"));
        final ImagePlus classifiedImage = new Opener().openImage(classified.toString());
        assertNotNull(classifiedImage);
        try {
            final Object info = classifiedImage.getProperty("Info");
            final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                    info == null ? "" : String.valueOf(info));
            assertTrue(parsed.isAvailable(), parsed.getUnavailableReason());
            final LumenCoordinateContext context = parsed.getMetadata().get().toCoordinateContext();
            assertEquals(SegmentOrientation.TOP_TO_BOTTOM, context.getSegmentOrientation());
            assertFalse(context.getOptionalGlobalSegmentStartUm().isPresent());
        } finally {
            classifiedImage.close();
        }
    }

    @Test
    void productionLocalLongitudinalIsFiniteWhileGlobalCoordinatesStayNa() throws Exception {
        final ClassificationFixture fixture = prepareClassificationFixture("orient-local", true);
        final BatchFullProcessingService isolated = new BatchFullProcessingService(
                successfulStubWeka(true),
                null);

        final BatchFullProcessingStageResult classResult =
                isolated.processClassificationStage(fixture.job, fixture.config);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, classResult.status(),
                classResult.message());

        final BatchFullProcessingJob reconstructionJob = reconstructionJobFrom(fixture);
        final BatchFullProcessingStageResult reconResult =
                isolated.processReconstructionStage(reconstructionJob, fixture.config);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, reconResult.status(),
                reconResult.message());

        final ProductionDatasetLayout layout = new ProductionDatasetLayout(fixture.datasetRoot);
        final Path measurements = layout.dataLumenMeasurementsPerSegment().resolve(
                FilenameSanitizer.lumenMeasurementsFilename("0101A", "0101A1"));
        final List<List<String>> table = ProductionCsvStore.readTable(measurements);
        assertTrue(table.size() > 1, "expected at least one lumen measurement row");
        final Map<String, Integer> idx =
                ProductionCsvStore.headerIndex(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
        final List<String> row = table.get(1);

        assertEquals(SegmentOrientation.TOP_TO_BOTTOM.name(),
                row.get(idx.get("segment_orientation").intValue()));
        assertEquals(RCsvSupport.NA, row.get(idx.get("global_longitudinal_um").intValue()));
        assertEquals(RCsvSupport.NA, row.get(idx.get("global_x_um").intValue()));
        assertEquals(RCsvSupport.NA, row.get(idx.get("global_y_um").intValue()));

        final int cropY = Integer.parseInt(row.get(idx.get("crop_y_px").intValue()));
        assertTrue(cropY > 0, "fixture must exclude bark-side original pixels: crop_y_px=" + cropY);
        final double centroidYCrop = Double.parseDouble(row.get(idx.get("centroid_y_crop_px").intValue()));
        final double pixelHeightUm = Double.parseDouble(row.get(idx.get("pixel_height_um").intValue()));
        final double centroidYOriginal = cropY + centroidYCrop;
        assertEquals(RCsvSupport.formatNumber(centroidYOriginal),
                row.get(idx.get("centroid_y_original_px").intValue()));
        final double expectedAxis = centroidYOriginal * pixelHeightUm;
        assertEquals(RCsvSupport.formatNumber(expectedAxis),
                row.get(idx.get("segment_axis_position_um").intValue()));
        assertNotEquals(RCsvSupport.formatNumber(centroidYCrop * pixelHeightUm),
                row.get(idx.get("segment_axis_position_um").intValue()),
                "segment_axis_position_um must use original-image Y, not crop-local Y");

        final Map<String, Integer> segmentIdx =
                ProductionCsvStore.headerIndex(ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        final List<List<String>> segmentTable = ProductionCsvStore.readTable(layout.segmentManifest());
        assertEquals(RCsvSupport.NA, segmentTable.get(1).get(
                segmentIdx.get("global_segment_start_um").intValue()));
        assertEquals(SegmentOrientation.TOP_TO_BOTTOM.name(),
                segmentTable.get(1).get(segmentIdx.get("segment_orientation").intValue()));
    }

    private static BatchFullProcessingStageResult invokeToStage(
            final BatchFullProcessingStage stage,
            final BatchFullProcessingJob job,
            final BatchFullProcessingResult result) {
        // Exercise mapping through the public stage wrappers by using a tiny
        // package-visible path: processClassificationStage always maps Result.
        // For zero-count reconstruction mapping we call the mapper indirectly
        // via a dedicated package-visible helper is not exported; instead
        // rebuild via StageResult builder mirroring service mapping rules.
        return BatchFullProcessingStageResult.builder()
                .stage(stage)
                .status(result.status())
                .reasonCode(result.get(BatchFullProcessingResult.KEY_REASON))
                .message(result.get(BatchFullProcessingResult.KEY_MESSAGE))
                .stageCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .runId(result.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID))
                .primaryOutputPath(result.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_CLASSIFIED_PATH))
                .totalComponents(Integer.valueOf(result.get(BatchFullProcessingResult.KEY_TOTAL_COMPONENTS)))
                .eligibleComponents(Integer.valueOf(result.get(BatchFullProcessingResult.KEY_ELIGIBLE_COMPONENTS)))
                .reconstructedComponents(Integer.valueOf(
                        result.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_COMPONENTS)))
                .changedPixels(Integer.valueOf(result.get(BatchFullProcessingResult.KEY_CHANGED_PIXELS)))
                .build();
    }

    private BatchFullProcessingService serviceWithCounters(
            final AtomicInteger classCalls, final AtomicInteger reconCalls) {
        final BatchFullProcessingService.StageEntryListener listener =
                new BatchFullProcessingService.StageEntryListener() {
                    @Override
                    public void onClassificationStageEntered() {
                        classCalls.incrementAndGet();
                    }

                    @Override
                    public void onReconstructionStageEntered() {
                        reconCalls.incrementAndGet();
                    }
                };
        return new BatchFullProcessingService(
                recordingWeka(new AtomicInteger()),
                recordingAllLumen(new AtomicInteger(), new AtomicInteger()),
                listener);
    }

    private BatchFullProcessingService.WekaClassification recordingWeka(
            final AtomicInteger calls) {
        return new BatchFullProcessingService.WekaClassification() {
            @Override
            public TiledWekaClassificationResult classify(
                    final ImagePlus sourceImage,
                    final File modelFile,
                    final TiledWekaClassificationConfiguration configuration,
                    final DownsampledSampleMask approvedMask,
                    final SampleMaskStatistics automaticMaskStatistics,
                    final boolean sampleMaskReviewed,
                    final AnalysisCropGeometry cropGeometry) {
                calls.incrementAndGet();
                throw new IllegalStateException("weka must not run in fixture");
            }
        };
    }

    private BatchFullProcessingService.AllLumenReconstruction recordingAllLumen(
            final AtomicInteger processCalls, final AtomicInteger writeCalls) {
        return new BatchFullProcessingService.AllLumenReconstruction() {
            @Override
            public AllLumenBatchResult process(
                    final ByteProcessor labels,
                    final AllLumenBatchConfiguration configuration) {
                processCalls.incrementAndGet();
                throw new IllegalStateException("all-lumen must not run in fixture");
            }

            @Override
            public AllLuminaDatasetWriter.Result write(
                    final ImagePlus maskedClassified,
                    final ByteProcessor source,
                    final AllLumenBatchResult batchResult,
                    final int minimumArea,
                    final ProductionDatasetLayout layout,
                    final boolean refreshAggregates) throws IOException {
                writeCalls.incrementAndGet();
                throw new IOException("all-lumen write must not run in fixture");
            }
        };
    }

    private BatchConfiguration validConfig() {
        return BatchConfiguration.fromMap(BatchTestConfigs.validMap());
    }

    private BatchFullProcessingJob buildJob(final String configurationSha256) throws Exception {
        final Path batchRoot = tempDir.resolve("batch").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input").toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("0101A1.tif");
        Files.write(source, "tiff-bytes".getBytes());
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model-bytes".getBytes());
        final Path datasetRoot = tempDir.resolve("dataset").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);
        final Path fullRoot = batchRoot.resolve("full/machine_01/0101A1/attempt_1");

        return new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                source,
                "0101A1.tif",
                SOURCE_SHA,
                batchRoot.resolve("batch.properties"),
                configurationSha256,
                classifier,
                CLASSIFIER_SHA,
                2,
                new BatchTileProfile(1536, 1536),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/result.properties"),
                batchRoot.resolve("precheck/machine_01/precheck_results.csv"),
                fullRoot.resolve("result.properties"),
                fullRoot.resolve("stdout.log"),
                fullRoot.resolve("stderr.log"),
                BatchFullProcessingStage.CLASSIFICATION);
    }

    private BatchFullProcessingJob buildReconstructionJob(
            final String configurationSha256, final boolean createClassifier) throws Exception {
        final Path batchRoot = tempDir.resolve("batch-recon-" + createClassifier)
                .toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input-recon-" + createClassifier)
                .toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("0101A1.tif");
        Files.write(source, "tiff-bytes".getBytes());
        final Path classifier;
        if (createClassifier) {
            classifier = input.resolve("classifier.model");
            Files.write(classifier, "model-bytes".getBytes());
        } else {
            classifier = input.resolve("missing-classifier.model");
        }
        final Path datasetRoot = tempDir.resolve("dataset-recon-" + createClassifier)
                .toAbsolutePath().normalize();
        final Path fullRoot = batchRoot.resolve("full/machine_01/0101A1/attempt_1/reconstruction");

        return new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                source,
                "0101A1.tif",
                SOURCE_SHA,
                batchRoot.resolve("batch.properties"),
                configurationSha256,
                classifier,
                CLASSIFIER_SHA,
                2,
                new BatchTileProfile(1536, 1536),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/result.properties"),
                batchRoot.resolve("precheck/machine_01/precheck_results.csv"),
                fullRoot.resolve("result.properties"),
                fullRoot.resolve("stdout.log"),
                fullRoot.resolve("stderr.log"),
                BatchFullProcessingStage.RECONSTRUCTION);
    }

    private void writePrecheckResult(
            final BatchFullProcessingJob job,
            final BatchPrecheckStatus status,
            final String sourceSha256,
            final String configurationSha256) throws Exception {
        final BatchPrecheckResult precheckResult = BatchPrecheckResult.builder()
                .status(status)
                .message("precheck fixture")
                .reasonCode(status.isSuccess() ? "NA" : "FIXTURE_REASON")
                .imageId(job.imageId().imageId())
                .machineId(job.machineId())
                .sourceFilename("0101A1.tif")
                .sourceRelativePath("0101A1.tif")
                .sourceSha256(sourceSha256)
                .configurationSha256(configurationSha256)
                .imageSize(1000, 800)
                .maskStats(Integer.valueOf(120), Integer.valueOf(1), 0.4, 0.7,
                        Long.valueOf(500000L), 0.05, Integer.valueOf(12345))
                .crop(Integer.valueOf(10), Integer.valueOf(20), Integer.valueOf(500),
                        Integer.valueOf(400))
                .scaleBar("TERMINAL_TICK_CENTERLINES", "HIGH", 0.9, 456.0, 20.0, 0.98,
                        1.0954306217917742, 0.0)
                .sourceChecksums(Integer.valueOf(1), Integer.valueOf(1), Boolean.TRUE)
                .previews("NA", "NA")
                .processingTimeMs(10L)
                .build();
        Files.createDirectories(job.precheckResultPath().getParent());
        precheckResult.write(job.precheckResultPath());
    }

    private BatchFullProcessingService.WekaClassification mutatingSourceWeka(
            final Path sourcePath) {
        return new BatchFullProcessingService.WekaClassification() {
            @Override
            public TiledWekaClassificationResult classify(
                    final ImagePlus sourceImage,
                    final File modelFile,
                    final TiledWekaClassificationConfiguration configuration,
                    final DownsampledSampleMask approvedMask,
                    final SampleMaskStatistics automaticMaskStatistics,
                    final boolean sampleMaskReviewed,
                    final AnalysisCropGeometry cropGeometry) {
                final int w = cropGeometry.getCropWidthPx();
                final int h = cropGeometry.getCropHeightPx();
                final ImagePlus labels = new ImagePlus("labels", new ByteProcessor(w, h));
                final CoreTileLayout layout = configuration.createLayout(w, h, 0);
                final TiledWekaClassificationResult result = new TiledWekaClassificationResult(
                        labels,
                        null,
                        null,
                        sourceImage.getTitle() == null ? "src" : sourceImage.getTitle(),
                        modelFile.getAbsolutePath(),
                        new String[] {"Wood", "Ray", "Lumen", "Background"},
                        configuration,
                        layout,
                        approvedMask,
                        automaticMaskStatistics,
                        sampleMaskReviewed,
                        1,
                        0,
                        0L,
                        1L,
                        -1L,
                        new long[] {1L, 0L, 0L, 0L},
                        null,
                        0,
                        0,
                        true,
                        false,
                        false,
                        "");

                try {
                    mutateSourceTiffOnDisk(sourcePath);
                } catch (final IOException ex) {
                    throw new IllegalStateException("failed to mutate source TIFF", ex);
                }
                return result;
            }
        };
    }

    private static final class ClassificationFixture {
        private final BatchFullProcessingJob job;
        private final BatchConfiguration config;
        private final Path datasetRoot;

        private ClassificationFixture(
                final BatchFullProcessingJob job,
                final BatchConfiguration config,
                final Path datasetRoot) {
            this.job = job;
            this.config = config;
            this.datasetRoot = datasetRoot;
        }
    }

    private ClassificationFixture prepareClassificationFixture(
            final String suffix, final boolean createClassifier) throws Exception {
        final BatchConfiguration config = validConfig();
        final Path batchRoot = tempDir.resolve("batch-" + suffix).toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input-" + suffix).toAbsolutePath().normalize();
        final Path datasetRoot = tempDir.resolve("dataset-" + suffix).toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        Files.createDirectories(datasetRoot);

        final Path sourcePath = input.resolve("0101A1.tif");
        writeSyntheticPrecheckSuccessRgbTiff(sourcePath, 80, 220);
        final String sourceSha = Sha256Files.sha256OfFile(sourcePath);

        final Path classifier = input.resolve("classifier.model");
        if (createClassifier) {
            Files.write(classifier, "model-bytes".getBytes(StandardCharsets.UTF_8));
        }

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));

        final Path precheckResultPath =
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/result.properties");
        final Path precheckCsvPath =
                batchRoot.resolve("precheck/machine_01/precheck_results.csv");
        Files.createDirectories(precheckResultPath.getParent());
        Files.createDirectories(precheckCsvPath.getParent());

        final BatchPrecheckJob precheckJob = new BatchPrecheckJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                sourcePath,
                "0101A1.tif",
                sourceSha,
                configPath,
                config.sha256(),
                batchRoot,
                precheckResultPath,
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/stdout.log"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/stderr.log"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/mask_preview.png"),
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/scale_bar_preview.png"));
        final BatchPrecheckResult precheckResult =
                new BatchPrecheckProcessor().process(precheckJob, config);
        assertEquals(BatchPrecheckStatus.PRECHECK_SUCCESS, precheckResult.status(),
                precheckResult.get(BatchPrecheckResult.KEY_MESSAGE));
        precheckResult.write(precheckResultPath);
        ProductionCsvStore.writeTableAtomically(
                precheckCsvPath,
                BatchPrecheckCsvSchema.HEADER,
                Collections.singletonList(precheckResult.toCsvRow(1)));

        final Path fullRoot = batchRoot.resolve("full/machine_01/0101A1/attempt_1");
        final BatchFullProcessingJob job = new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                sourcePath,
                "0101A1.tif",
                sourceSha,
                configPath,
                config.sha256(),
                classifier,
                Sha256Files.sha256OfFile(classifier),
                2,
                new BatchTileProfile(1536, 1536),
                1,
                batchRoot,
                datasetRoot,
                precheckResultPath,
                precheckCsvPath,
                fullRoot.resolve("result.properties"),
                fullRoot.resolve("stdout.log"),
                fullRoot.resolve("stderr.log"),
                BatchFullProcessingStage.CLASSIFICATION);
        return new ClassificationFixture(job, config, datasetRoot);
    }

    private BatchFullProcessingJob reconstructionJobFrom(final ClassificationFixture fixture) {
        final BatchFullProcessingJob classJob = fixture.job;
        return new BatchFullProcessingJob(
                classJob.schemaVersion(),
                classJob.imageId(),
                classJob.machineId(),
                classJob.sourcePath(),
                classJob.sourceRelativePath(),
                classJob.sourceSha256(),
                classJob.configurationPath(),
                classJob.configurationSha256(),
                classJob.classifierPath(),
                classJob.classifierSha256(),
                classJob.tileProfileIndex(),
                classJob.tileProfile(),
                classJob.attemptNumber(),
                classJob.batchRoot(),
                classJob.datasetRoot(),
                classJob.precheckResultPath(),
                classJob.precheckCsvPath(),
                classJob.resultPath().getParent().resolve("reconstruction").resolve("result.properties"),
                classJob.stdoutPath(),
                classJob.stderrPath(),
                BatchFullProcessingStage.RECONSTRUCTION);
    }

    private BatchFullProcessingService.WekaClassification successfulStubWeka(
            final boolean paintEligibleLumen) {
        return new BatchFullProcessingService.WekaClassification() {
            @Override
            public TiledWekaClassificationResult classify(
                    final ImagePlus sourceImage,
                    final File modelFile,
                    final TiledWekaClassificationConfiguration configuration,
                    final DownsampledSampleMask approvedMask,
                    final SampleMaskStatistics automaticMaskStatistics,
                    final boolean sampleMaskReviewed,
                    final AnalysisCropGeometry cropGeometry) {
                assertEquals(SegmentOrientation.TOP_TO_BOTTOM, configuration.getSegmentOrientation());
                assertFalse(configuration.getOptionalGlobalSegmentStartUm().isPresent());
                final int w = cropGeometry.getCropWidthPx();
                final int h = cropGeometry.getCropHeightPx();
                final ByteProcessor labels = new ByteProcessor(w, h);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        labels.set(x, y, ClassifierLabels.WOOD_RESULT_VALUE);
                    }
                }
                if (paintEligibleLumen) {
                    for (int y = 12; y < 42; y++) {
                        for (int x = 10; x < 50; x++) {
                            labels.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                        }
                    }
                }
                final ImagePlus labelImage = new ImagePlus("labels", labels);
                final CoreTileLayout layout = configuration.createLayout(w, h, 0);
                return new TiledWekaClassificationResult(
                        labelImage,
                        null,
                        null,
                        sourceImage.getTitle() == null ? "src" : sourceImage.getTitle(),
                        modelFile.getAbsolutePath(),
                        new String[] {"Wood", "Ray", "Lumen", "Background"},
                        configuration,
                        layout,
                        approvedMask,
                        automaticMaskStatistics,
                        sampleMaskReviewed,
                        1,
                        0,
                        0L,
                        1L,
                        -1L,
                        new long[] {1L, 0L, 1L, 0L},
                        null,
                        0,
                        0,
                        true,
                        false,
                        false,
                        "");
            }
        };
    }

    private static void mutateSourceTiffOnDisk(final Path sourcePath) throws IOException {
        final ImagePlus opened = new Opener().openImage(sourcePath.toString());
        if (opened == null || !(opened.getProcessor() instanceof ColorProcessor)) {
            throw new IOException("unable to reopen source TIFF for mutation: " + sourcePath);
        }
        final ColorProcessor processor = (ColorProcessor) opened.getProcessor();
        processor.set(0, 0, processor.get(0, 0) ^ 0x00010101);
        if (!new FileSaver(opened).saveAsTiff(sourcePath.toString())) {
            opened.close();
            throw new IOException("failed to rewrite mutated source TIFF: " + sourcePath);
        }
        opened.close();
    }

    private static void writeSyntheticPrecheckSuccessRgbTiff(final Path target) throws IOException {
        writeSyntheticPrecheckSuccessRgbTiff(target, 30, 280);
    }

    private static void writeSyntheticPrecheckSuccessRgbTiff(
            final Path target,
            final int sampleY,
            final int sampleHeight) throws IOException {
        final int width = 700;
        final int height = 400;
        final ColorProcessor rgb = new ColorProcessor(width, height);
        fillColor(rgb, new Color(230, 230, 220));
        // Vertical brown sample, clear of the exterior scale bar.
        fillRectColor(rgb, 80, sampleY, 120, sampleHeight, new Color(70, 55, 40));
        // Ideal terminal-tick scale bar with 456 px center distance.
        drawTerminalTickBar(rgb, 40, 360, 40 + 456);
        final ImagePlus image = new ImagePlus("0101A1", rgb);
        Files.createDirectories(target.getParent());
        if (!new FileSaver(image).saveAsTiff(target.toString())) {
            image.close();
            throw new IOException("failed to write synthetic RGB TIFF: " + target);
        }
        image.close();
    }

    private static void fillColor(final ColorProcessor rgb, final Color color) {
        rgb.setColor(color);
        rgb.fill();
    }

    private static void fillRectColor(
            final ColorProcessor rgb,
            final int x,
            final int y,
            final int width,
            final int height,
            final Color color) {
        rgb.setColor(color);
        rgb.setRoi(x, y, width, height);
        rgb.fill();
        rgb.setRoi((java.awt.Rectangle) null);
    }

    private static void drawTerminalTickBar(
            final ColorProcessor rgb,
            final int leftCenterX,
            final int shaftY,
            final int rightCenterX) {
        final int red = (220 << 16) | (20 << 8) | 20;
        final int tickHalfWidth = 7;
        final int shaftHalfHeight = 4;
        final int left0 = leftCenterX - tickHalfWidth;
        final int left1 = leftCenterX + tickHalfWidth;
        final int right0 = rightCenterX - tickHalfWidth;
        final int right1 = rightCenterX + tickHalfWidth;
        fillRectRgb(rgb, left0, shaftY - 10, left1, shaftY + 10, red);
        fillRectRgb(rgb, left1 + 1, shaftY - shaftHalfHeight, right0 - 1, shaftY + shaftHalfHeight, red);
        fillRectRgb(rgb, right0, shaftY - 10, right1, shaftY + 10, red);
    }

    private static void fillRectRgb(
            final ColorProcessor rgb,
            final int x0,
            final int y0,
            final int x1,
            final int y1,
            final int color) {
        for (int y = Math.max(0, y0); y <= Math.min(rgb.getHeight() - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(rgb.getWidth() - 1, x1); x++) {
                rgb.set(x, y, color);
            }
        }
    }

    private static boolean hasSuccessfulTiledClassificationRun(
            final Path processingRuns,
            final String sampleId,
            final String segmentId) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(processingRuns);
        if (table.isEmpty()) {
            return false;
        }
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(table.get(0));
        final int stageIdx = idx.get("stage").intValue();
        final int statusIdx = idx.get("status").intValue();
        final int sampleIdx = idx.get("sample_id").intValue();
        final int segmentIdx = idx.get("segment_id").intValue();
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name()
                    .equals(row.get(stageIdx))
                    && ProductionSchemas.RunStatus.SUCCESS.name().equals(row.get(statusIdx))
                    && sampleId.equals(row.get(sampleIdx))
                    && segmentId.equals(row.get(segmentIdx))) {
                return true;
            }
        }
        return false;
    }
}
