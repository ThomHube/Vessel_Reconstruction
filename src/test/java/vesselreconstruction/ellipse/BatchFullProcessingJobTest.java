package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingJobTest {

    private static final String SHA64_A =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SHA64_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @TempDir
    Path tempDir;

    @Test
    void stageAwareRoundTripWriteAndLoad() throws Exception {
        final Path batchRoot = tempDir.resolve("batch").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("0101A1.tif");
        Files.write(source, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final String sourceSha = Sha256Files.sha256OfFile(source);
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model-bytes".getBytes(StandardCharsets.UTF_8));
        final String classifierSha = Sha256Files.sha256OfFile(classifier);
        final Path config = batchRoot.resolve("batch.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final Path datasetRoot = tempDir.resolve("dataset").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);

        final Path jobPath = batchRoot.resolve("full/machine_01/0101A1/attempt_1/job.properties");
        Files.createDirectories(jobPath.getParent());
        final BatchFullProcessingJob original = new BatchFullProcessingJob(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                source.toAbsolutePath().normalize(),
                "0101A1.tif",
                sourceSha,
                config.toAbsolutePath().normalize(),
                SHA64_A,
                classifier.toAbsolutePath().normalize(),
                classifierSha,
                0,
                new BatchTileProfile(2048, 2048),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/machine_01/jobs/0101A1/result.properties"),
                batchRoot.resolve("precheck/machine_01/precheck_results.csv"),
                batchRoot.resolve("full/machine_01/0101A1/attempt_1/result.properties"),
                batchRoot.resolve("full/machine_01/0101A1/attempt_1/stdout.log"),
                batchRoot.resolve("full/machine_01/0101A1/attempt_1/stderr.log"),
                BatchFullProcessingStage.CLASSIFICATION);
        original.write(jobPath);

        final BatchFullProcessingJob loaded = BatchFullProcessingJob.load(jobPath);
        assertEquals(original.schemaVersion(), loaded.schemaVersion());
        assertEquals(original.imageId(), loaded.imageId());
        assertEquals(original.machineId(), loaded.machineId());
        assertEquals(original.sourcePath(), loaded.sourcePath());
        assertEquals(original.sourceRelativePath(), loaded.sourceRelativePath());
        assertEquals(original.sourceSha256(), loaded.sourceSha256());
        assertEquals(original.configurationPath(), loaded.configurationPath());
        assertEquals(original.configurationSha256(), loaded.configurationSha256());
        assertEquals(original.classifierPath(), loaded.classifierPath());
        assertEquals(original.classifierSha256(), loaded.classifierSha256());
        assertEquals(original.tileProfileIndex(), loaded.tileProfileIndex());
        assertEquals(original.tileProfile(), loaded.tileProfile());
        assertEquals(original.attemptNumber(), loaded.attemptNumber());
        assertEquals(original.batchRoot(), loaded.batchRoot());
        assertEquals(original.datasetRoot(), loaded.datasetRoot());
        assertEquals(original.precheckResultPath(), loaded.precheckResultPath());
        assertEquals(original.precheckCsvPath(), loaded.precheckCsvPath());
        assertEquals(original.resultPath(), loaded.resultPath());
        assertEquals(original.stdoutPath(), loaded.stdoutPath());
        assertEquals(original.stderrPath(), loaded.stderrPath());
        assertEquals(BatchFullProcessingStage.CLASSIFICATION, loaded.processingStage());
    }

    @Test
    void classificationJobSerializesProcessingStage() throws Exception {
        final Map<String, String> map = validJobMap();
        map.put(BatchFullProcessingJob.KEY_PROCESSING_STAGE, "CLASSIFICATION");
        final BatchFullProcessingJob job = BatchFullProcessingJob.fromMap(map);
        final Path jobPath = tempDir.resolve("class-job.properties");
        job.write(jobPath);
        final String text = new String(Files.readAllBytes(jobPath), StandardCharsets.UTF_8);
        assertTrue(text.contains("processing_stage=CLASSIFICATION"));
        assertEquals(BatchFullProcessingStage.CLASSIFICATION,
                BatchFullProcessingJob.load(jobPath).processingStage());
    }

    @Test
    void reconstructionJobSerializesProcessingStage() throws Exception {
        final Map<String, String> map = validJobMap();
        map.put(BatchFullProcessingJob.KEY_PROCESSING_STAGE, "RECONSTRUCTION");
        final BatchFullProcessingJob job = BatchFullProcessingJob.fromMap(map);
        final Path jobPath = tempDir.resolve("recon-job.properties");
        job.write(jobPath);
        final String text = new String(Files.readAllBytes(jobPath), StandardCharsets.UTF_8);
        assertTrue(text.contains("processing_stage=RECONSTRUCTION"));
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION,
                BatchFullProcessingJob.load(jobPath).processingStage());
    }

    @Test
    void stageAwareLoaderRejectsMissingProcessingStage() throws Exception {
        final Map<String, String> map = validJobMap();
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("Missing job key"));
        assertTrue(ex.getMessage().contains(BatchFullProcessingJob.KEY_PROCESSING_STAGE));
    }

    @Test
    void explicitLegacyLoaderSupportsCombinedPathWithoutProcessingStage() throws Exception {
        final Map<String, String> map = validJobMap();
        final BatchFullProcessingJob job = BatchFullProcessingJob.fromMapLegacyCombined(map);
        assertNull(job.processingStage());

        final Path jobPath = tempDir.resolve("legacy-job.properties");
        job.write(jobPath);
        final String text = new String(Files.readAllBytes(jobPath), StandardCharsets.UTF_8);
        assertTrue(!text.contains("processing_stage="));

        final BatchFullProcessingJob loaded = BatchFullProcessingJob.loadLegacyCombined(jobPath);
        assertNull(loaded.processingStage());
        assertEquals(job.imageId(), loaded.imageId());
        assertEquals(job.resultPath(), loaded.resultPath());
    }

    @Test
    void createLegacyCombinedOmitsProcessingStage() throws Exception {
        final Path batchRoot = tempDir.resolve("legacy-batch").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("legacy-input");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("0101A1.tif");
        Files.write(source, "bytes".getBytes(StandardCharsets.UTF_8));
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model".getBytes(StandardCharsets.UTF_8));
        final Path datasetRoot = tempDir.resolve("legacy-dataset").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);

        final BatchFullProcessingJob job = BatchFullProcessingJob.createLegacyCombined(
                1,
                CoreSegmentId.parse("0101A1"),
                "machine_01",
                source.toAbsolutePath().normalize(),
                "0101A1.tif",
                Sha256Files.sha256OfFile(source),
                batchRoot.resolve("cfg.properties").toAbsolutePath().normalize(),
                SHA64_A,
                classifier.toAbsolutePath().normalize(),
                Sha256Files.sha256OfFile(classifier),
                0,
                new BatchTileProfile(2048, 2048),
                1,
                batchRoot,
                datasetRoot,
                batchRoot.resolve("precheck/result.properties"),
                batchRoot.resolve("precheck/precheck_results.csv"),
                batchRoot.resolve("full/result.properties"),
                batchRoot.resolve("full/out.log"),
                batchRoot.resolve("full/err.log"));
        assertNull(job.processingStage());
        final Path jobPath = batchRoot.resolve("job.properties");
        job.write(jobPath);
        assertTrue(!new String(Files.readAllBytes(jobPath), StandardCharsets.UTF_8)
                .contains("processing_stage="));
    }

    @Test
    void unknownKeyRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put("extra.key", "1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("Unknown job key"));
    }

    @Test
    void missingKeyRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.remove(BatchFullProcessingJob.KEY_RESULT_PATH);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("Missing job key"));
    }

    @Test
    void invalidMachineIdRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put(BatchFullProcessingJob.KEY_MACHINE_ID, "worker-1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("machine_"));
    }

    @Test
    void invalidShaRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put(BatchFullProcessingJob.KEY_CLASSIFIER_SHA256, "not-a-sha");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("invalid"));
    }

    @Test
    void unsafeOutputPathOutsideBatchRootRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        final Path outside = tempDir.resolve("outside-result").toAbsolutePath().normalize();
        Files.createDirectories(outside);
        map.put(BatchFullProcessingJob.KEY_RESULT_PATH,
                outside.resolve("result.properties").toString());
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("output path escapes batch root"));
    }

    @Test
    void datasetRootAllowedOutsideBatchRoot() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        final Path outsideDataset = tempDir.resolve("standalone-dataset").toAbsolutePath().normalize();
        Files.createDirectories(outsideDataset);
        map.put(BatchFullProcessingJob.KEY_DATASET_ROOT, outsideDataset.toString());
        final BatchFullProcessingJob job = BatchFullProcessingJob.fromMap(map);
        assertEquals(outsideDataset, job.datasetRoot());
    }

    @Test
    void invalidTileDimensionsRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put(BatchFullProcessingJob.KEY_TILE_WIDTH_PX, "100");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains("invalid tile profile"));
    }

    @Test
    void negativeTileProfileIndexRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put(BatchFullProcessingJob.KEY_TILE_PROFILE_INDEX, "-1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains(BatchFullProcessingJob.KEY_TILE_PROFILE_INDEX));
    }

    @Test
    void nonPositiveAttemptNumberRejected() throws Exception {
        final Map<String, String> map = validStageAwareJobMap();
        map.put(BatchFullProcessingJob.KEY_ATTEMPT_NUMBER, "0");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingJob.fromMap(map));
        assertTrue(ex.getMessage().contains(BatchFullProcessingJob.KEY_ATTEMPT_NUMBER));
    }

    private Map<String, String> validStageAwareJobMap() throws Exception {
        final Map<String, String> map = validJobMap();
        map.put(BatchFullProcessingJob.KEY_PROCESSING_STAGE, "CLASSIFICATION");
        return map;
    }

    /** Legacy map without {@code processing_stage}. */
    private Map<String, String> validJobMap() throws Exception {
        final Path batchRoot = tempDir.resolve("batch-map").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input-map");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("0101A2.tif");
        Files.write(source, "bytes".getBytes(StandardCharsets.UTF_8));
        final Path classifier = input.resolve("classifier.model");
        Files.write(classifier, "model".getBytes(StandardCharsets.UTF_8));
        final Path datasetRoot = tempDir.resolve("dataset-map").toAbsolutePath().normalize();
        Files.createDirectories(datasetRoot);

        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(BatchFullProcessingJob.KEY_SCHEMA, "1");
        map.put(BatchFullProcessingJob.KEY_IMAGE_ID, "0101A2");
        map.put(BatchFullProcessingJob.KEY_MACHINE_ID, "machine_01");
        map.put(BatchFullProcessingJob.KEY_SOURCE_PATH, source.toAbsolutePath().normalize().toString());
        map.put(BatchFullProcessingJob.KEY_SOURCE_RELATIVE, "0101A2.tif");
        map.put(BatchFullProcessingJob.KEY_SOURCE_SHA256, Sha256Files.sha256OfFile(source));
        map.put(BatchFullProcessingJob.KEY_CONFIG_PATH,
                batchRoot.resolve("cfg.properties").toAbsolutePath().normalize().toString());
        map.put(BatchFullProcessingJob.KEY_CONFIG_SHA256, SHA64_A);
        map.put(BatchFullProcessingJob.KEY_CLASSIFIER_PATH,
                classifier.toAbsolutePath().normalize().toString());
        map.put(BatchFullProcessingJob.KEY_CLASSIFIER_SHA256, SHA64_B);
        map.put(BatchFullProcessingJob.KEY_TILE_PROFILE_INDEX, "0");
        map.put(BatchFullProcessingJob.KEY_TILE_WIDTH_PX, "2048");
        map.put(BatchFullProcessingJob.KEY_TILE_HEIGHT_PX, "2048");
        map.put(BatchFullProcessingJob.KEY_ATTEMPT_NUMBER, "1");
        map.put(BatchFullProcessingJob.KEY_BATCH_ROOT, batchRoot.toString());
        map.put(BatchFullProcessingJob.KEY_DATASET_ROOT, datasetRoot.toString());
        map.put(BatchFullProcessingJob.KEY_PRECHECK_RESULT_PATH,
                batchRoot.resolve("precheck/machine_01/jobs/0101A2/result.properties").toString());
        map.put(BatchFullProcessingJob.KEY_PRECHECK_CSV_PATH,
                batchRoot.resolve("precheck/machine_01/precheck_results.csv").toString());
        map.put(BatchFullProcessingJob.KEY_RESULT_PATH,
                batchRoot.resolve("full/result.properties").toString());
        map.put(BatchFullProcessingJob.KEY_STDOUT_PATH,
                batchRoot.resolve("full/out.log").toString());
        map.put(BatchFullProcessingJob.KEY_STDERR_PATH,
                batchRoot.resolve("full/err.log").toString());
        return map;
    }
}
