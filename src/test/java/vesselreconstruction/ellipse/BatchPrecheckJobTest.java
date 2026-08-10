package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckJobTest {

    private static final String SHA64 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path tempDir;

    @Test
    void roundTripWriteAndLoad() throws Exception {
        final Path batchRoot = tempDir.resolve("batch").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("010101.tif");
        Files.write(source, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final String sourceSha = Sha256Files.sha256OfFile(source);
        final Path config = batchRoot.resolve("batch.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));

        final Path jobPath = batchRoot.resolve("precheck/machine_01/jobs/010101/job.properties");
        Files.createDirectories(jobPath.getParent());
        final BatchPrecheckJob original = new BatchPrecheckJob(
                1,
                SixDigitSampleId.parse("010101"),
                "machine_01",
                source.toAbsolutePath().normalize(),
                "010101.tif",
                sourceSha,
                config.toAbsolutePath().normalize(),
                SHA64,
                batchRoot,
                batchRoot.resolve("precheck/machine_01/jobs/010101/result.properties"),
                batchRoot.resolve("precheck/machine_01/logs/010101__stdout.log"),
                batchRoot.resolve("precheck/machine_01/logs/010101__stderr.log"),
                batchRoot.resolve("precheck/machine_01/previews/010101__mask_preview.png"),
                batchRoot.resolve("precheck/machine_01/previews/010101__scale_bar_preview.png"));
        original.write(jobPath);

        final BatchPrecheckJob loaded = BatchPrecheckJob.load(jobPath);
        assertEquals(original.schemaVersion(), loaded.schemaVersion());
        assertEquals(original.imageId(), loaded.imageId());
        assertEquals(original.machineId(), loaded.machineId());
        assertEquals(original.sourcePath(), loaded.sourcePath());
        assertEquals(original.sourceRelativePath(), loaded.sourceRelativePath());
        assertEquals(original.sourceSha256(), loaded.sourceSha256());
        assertEquals(original.configurationPath(), loaded.configurationPath());
        assertEquals(original.configurationSha256(), loaded.configurationSha256());
        assertEquals(original.batchRoot(), loaded.batchRoot());
        assertEquals(original.resultPath(), loaded.resultPath());
        assertEquals(original.stdoutPath(), loaded.stdoutPath());
        assertEquals(original.stderrPath(), loaded.stderrPath());
        assertEquals(original.maskPreviewPath(), loaded.maskPreviewPath());
        assertEquals(original.scaleBarPreviewPath(), loaded.scaleBarPreviewPath());
    }

    @Test
    void unknownKeyRejected() throws Exception {
        final Map<String, String> map = validJobMap();
        map.put("extra.key", "1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchPrecheckJob.fromMap(map));
        assertTrue(ex.getMessage().contains("Unknown job key"));
    }

    @Test
    void missingKeyRejected() throws Exception {
        final Map<String, String> map = validJobMap();
        map.remove(BatchPrecheckJob.KEY_RESULT_PATH);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchPrecheckJob.fromMap(map));
        assertTrue(ex.getMessage().contains("Missing job key"));
    }

    @Test
    void unsafeOutputPathOutsideBatchRootRejected() throws Exception {
        final Map<String, String> map = validJobMap();
        final Path outside = tempDir.resolve("outside").toAbsolutePath().normalize();
        Files.createDirectories(outside);
        map.put(BatchPrecheckJob.KEY_RESULT_PATH,
                outside.resolve("result.properties").toString());
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchPrecheckJob.fromMap(map));
        assertTrue(ex.getMessage().contains("output path escapes batch root"));
    }

    private Map<String, String> validJobMap() throws Exception {
        final Path batchRoot = tempDir.resolve("batch-map").toAbsolutePath().normalize();
        final Path input = tempDir.resolve("input-map");
        Files.createDirectories(batchRoot);
        Files.createDirectories(input);
        final Path source = input.resolve("010102.tif");
        Files.write(source, "bytes".getBytes(StandardCharsets.UTF_8));
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(BatchPrecheckJob.KEY_SCHEMA, "1");
        map.put(BatchPrecheckJob.KEY_IMAGE_ID, "010102");
        map.put(BatchPrecheckJob.KEY_MACHINE_ID, "machine_01");
        map.put(BatchPrecheckJob.KEY_SOURCE_PATH, source.toAbsolutePath().normalize().toString());
        map.put(BatchPrecheckJob.KEY_SOURCE_RELATIVE, "010102.tif");
        map.put(BatchPrecheckJob.KEY_SOURCE_SHA256, Sha256Files.sha256OfFile(source));
        map.put(BatchPrecheckJob.KEY_CONFIG_PATH,
                batchRoot.resolve("cfg.properties").toAbsolutePath().normalize().toString());
        map.put(BatchPrecheckJob.KEY_CONFIG_SHA256, SHA64);
        map.put(BatchPrecheckJob.KEY_BATCH_ROOT, batchRoot.toString());
        map.put(BatchPrecheckJob.KEY_RESULT_PATH,
                batchRoot.resolve("precheck/result.properties").toString());
        map.put(BatchPrecheckJob.KEY_STDOUT_PATH,
                batchRoot.resolve("precheck/out.log").toString());
        map.put(BatchPrecheckJob.KEY_STDERR_PATH,
                batchRoot.resolve("precheck/err.log").toString());
        map.put(BatchPrecheckJob.KEY_MASK_PREVIEW,
                batchRoot.resolve("precheck/mask.png").toString());
        map.put(BatchPrecheckJob.KEY_SCALE_PREVIEW,
                batchRoot.resolve("precheck/scale.png").toString());
        // Touch Paths.get for Java-8 style path handling in assertions.
        assertEquals(batchRoot, Paths.get(map.get(BatchPrecheckJob.KEY_BATCH_ROOT))
                .toAbsolutePath().normalize());
        return map;
    }
}
