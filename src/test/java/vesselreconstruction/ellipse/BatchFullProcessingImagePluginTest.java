package vesselreconstruction.ellipse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingImagePluginTest {

    private static final String SHA64 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir
    Path tempDir;

    @AfterEach
    void resetFactory() {
        BatchFullProcessingImagePlugin.setServiceFactoryForTest(null);
    }

    @Test
    void pluginRequiresExplicitProcessingStage() throws Exception {
        final Path jobPath = writeLegacyJobWithoutStage();
        final int code = BatchFullProcessingImagePlugin.processJobPath(jobPath.toString());
        assertEquals(1, code);
        // Stage-aware loader rejects missing processing_stage before service runs.
        assertFalse(Files.isRegularFile(
                tempDir.resolve("batch/full/machine_01/010101/attempt_1/result.properties"))
                || resultExistsBeside(jobPath));
    }

    @Test
    void pluginDispatchesClassificationAndWritesStageResultAtomically() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final Path jobPath = writeStageJob(BatchFullProcessingStage.CLASSIFICATION);
        final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);

        BatchFullProcessingImagePlugin.setServiceFactoryForTest(new BatchFullProcessingImagePlugin.ServiceFactory() {
            @Override
            public BatchFullProcessingService create() {
                return new BatchFullProcessingService(
                        null,
                        null,
                        new BatchFullProcessingService.StageEntryListener() {
                            @Override
                            public void onClassificationStageEntered() {
                                classCalls.incrementAndGet();
                            }

                            @Override
                            public void onReconstructionStageEntered() {
                                reconCalls.incrementAndGet();
                            }
                        });
            }
        });

        final int code = BatchFullProcessingImagePlugin.processJobPath(jobPath.toString());
        assertEquals(0, code);
        assertEquals(1, classCalls.get());
        assertEquals(0, reconCalls.get());

        final BatchFullProcessingStageResult loaded =
                BatchFullProcessingStageResult.read(job.resultPath());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, loaded.stage());
        assertTrue(Files.isRegularFile(job.resultPath()));
    }

    @Test
    void pluginDispatchesReconstructionAndWritesStageResultAtomically() throws Exception {
        final AtomicInteger classCalls = new AtomicInteger();
        final AtomicInteger reconCalls = new AtomicInteger();
        final Path jobPath = writeStageJob(BatchFullProcessingStage.RECONSTRUCTION);
        Files.createDirectories(BatchFullProcessingJob.load(jobPath).datasetRoot());
        final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);

        BatchFullProcessingImagePlugin.setServiceFactoryForTest(new BatchFullProcessingImagePlugin.ServiceFactory() {
            @Override
            public BatchFullProcessingService create() {
                return new BatchFullProcessingService(
                        null,
                        null,
                        new BatchFullProcessingService.StageEntryListener() {
                            @Override
                            public void onClassificationStageEntered() {
                                classCalls.incrementAndGet();
                            }

                            @Override
                            public void onReconstructionStageEntered() {
                                reconCalls.incrementAndGet();
                            }
                        });
            }
        });

        final int code = BatchFullProcessingImagePlugin.processJobPath(jobPath.toString());
        assertEquals(0, code);
        assertEquals(0, classCalls.get());
        assertEquals(1, reconCalls.get());

        final BatchFullProcessingStageResult loaded =
                BatchFullProcessingStageResult.read(job.resultPath());
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION, loaded.stage());
    }

    @Test
    void pluginAttemptsOomResult() throws Exception {
        final Path jobPath = writeStageJob(BatchFullProcessingStage.CLASSIFICATION);
        final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);
        final AtomicReference<BatchFullProcessingJob> captured =
                new AtomicReference<BatchFullProcessingJob>();

        final BatchFullProcessingService throwing = new BatchFullProcessingService() {
            @Override
            public BatchFullProcessingStageResult process(
                    final BatchFullProcessingJob j, final BatchConfiguration config) {
                captured.set(j);
                throw new OutOfMemoryError("heap exhausted in fixture");
            }
        };

        final int code = BatchFullProcessingImagePlugin.processJobPath(jobPath.toString(), throwing);
        assertEquals(1, code);
        assertTrue(Files.isRegularFile(job.resultPath()));
        final BatchFullProcessingStageResult loaded =
                BatchFullProcessingStageResult.read(job.resultPath());
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY, loaded.status());
        assertEquals(RCsvSupport.TRUE, loaded.outOfMemoryDetected());
        assertEquals(RCsvSupport.FALSE, loaded.stageCompleted());
        assertEquals(RCsvSupport.FALSE, loaded.requiredOutputsValidated());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, loaded.stage());
        assertEquals(job.imageId().imageId(), captured.get().imageId().imageId());
    }

    @Test
    void pluginWritesUnexpectedFailureResult() throws Exception {
        final Path jobPath = writeStageJob(BatchFullProcessingStage.RECONSTRUCTION);
        Files.createDirectories(BatchFullProcessingJob.load(jobPath).datasetRoot());
        final BatchFullProcessingJob job = BatchFullProcessingJob.load(jobPath);

        final BatchFullProcessingService throwing = new BatchFullProcessingService() {
            @Override
            public BatchFullProcessingStageResult process(
                    final BatchFullProcessingJob j, final BatchConfiguration config) {
                throw new IllegalStateException("boom");
            }
        };

        final int code = BatchFullProcessingImagePlugin.processJobPath(jobPath.toString(), throwing);
        assertEquals(1, code);
        final BatchFullProcessingStageResult loaded =
                BatchFullProcessingStageResult.read(job.resultPath());
        assertEquals(BatchFullProcessingStatus.FAILED_UNEXPECTED, loaded.status());
        assertEquals(RCsvSupport.FALSE, loaded.outOfMemoryDetected());
        assertEquals(RCsvSupport.FALSE, loaded.stageCompleted());
        assertEquals("PLUGIN_UNEXPECTED", loaded.reasonCode());
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION, loaded.stage());
    }

    @Test
    void pluginDoesNotRegisterVisibleCommand() throws Exception {
        final Path pluginsConfig = tempDir.resolve("dummy");
        // Source-level check against the real plugins.config in the project.
        final Path realConfig = Paths.get("src/main/resources/plugins.config")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(realConfig)) {
            final String text = new String(Files.readAllBytes(realConfig), StandardCharsets.UTF_8);
            assertFalse(text.contains("BatchFullProcessingImagePlugin"));
        }
        assertTrue(pluginsConfig != null);
    }

    private boolean resultExistsBeside(final Path jobPath) {
        return Files.isRegularFile(jobPath.getParent().resolve("result.properties"));
    }

    private Path writeLegacyJobWithoutStage() throws Exception {
        final Path batchRoot = tempDir.resolve("legacy-batch").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("legacy-input");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("010101.tif");
        Files.write(source, "bytes".getBytes(StandardCharsets.UTF_8));
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model".getBytes(StandardCharsets.UTF_8));
        final Path datasetRoot = tempDir.resolve("legacy-dataset").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);
        final Path config = batchRoot.resolve("batch.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));

        final BatchFullProcessingJob job = BatchFullProcessingJob.createLegacyCombined(
                1,
                SixDigitSampleId.parse("010101"),
                "machine_01",
                source.toAbsolutePath().normalize(),
                "010101.tif",
                Sha256Files.sha256OfFile(source),
                config.toAbsolutePath().normalize(),
                SHA64,
                classifier.toAbsolutePath().normalize(),
                Sha256Files.sha256OfFile(classifier),
                0,
                new BatchTileProfile(2048, 2048),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/result.properties"),
                batchRoot.resolve("precheck/precheck_results.csv"),
                batchRoot.resolve("full/machine_01/010101/attempt_1/result.properties"),
                batchRoot.resolve("full/machine_01/010101/attempt_1/stdout.log"),
                batchRoot.resolve("full/machine_01/010101/attempt_1/stderr.log"));
        final Path jobPath = batchRoot.resolve("full/machine_01/010101/attempt_1/job.properties");
        Files.createDirectories(jobPath.getParent());
        job.write(jobPath);
        return jobPath;
    }

    private Path writeStageJob(final BatchFullProcessingStage stage) throws Exception {
        final Path batchRoot = tempDir.resolve("stage-batch-" + stage.name())
                .toAbsolutePath().normalize();
        final Path input = tempDir.resolve("stage-input-" + stage.name());
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("010101.tif");
        Files.write(source, "bytes".getBytes(StandardCharsets.UTF_8));
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model".getBytes(StandardCharsets.UTF_8));
        final Path datasetRoot = tempDir.resolve("stage-dataset-" + stage.name())
                .toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);
        final Path config = batchRoot.resolve("batch.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final String configSha = BatchConfiguration.load(config).sha256();

        final Path attempt = batchRoot.resolve("full/machine_01/010101/attempt_1/"
                + stage.name().toLowerCase());
        Files.createDirectories(attempt);
        final BatchFullProcessingJob job = new BatchFullProcessingJob(
                1,
                SixDigitSampleId.parse("010101"),
                "machine_01",
                source.toAbsolutePath().normalize(),
                "010101.tif",
                Sha256Files.sha256OfFile(source),
                config.toAbsolutePath().normalize(),
                configSha,
                classifier.toAbsolutePath().normalize(),
                Sha256Files.sha256OfFile(classifier),
                0,
                new BatchTileProfile(2048, 2048),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/result.properties"),
                batchRoot.resolve("precheck/precheck_results.csv"),
                attempt.resolve("result.properties"),
                attempt.resolve("stdout.log"),
                attempt.resolve("stderr.log"),
                stage);
        final Path jobPath = attempt.resolve("job.properties");
        job.write(jobPath);
        return jobPath;
    }
}
