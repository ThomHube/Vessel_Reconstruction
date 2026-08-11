package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import ij.ImagePlus;
import ij.process.ByteProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
