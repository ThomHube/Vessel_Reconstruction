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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckProcessorTest {

    private static final String SHA64 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @TempDir
    Path tempDir;

    @Test
    void relativeToBatchProducesForwardSlashRelativePath() {
        final Path batchRoot = Paths.get("/tmp/batch-root").toAbsolutePath().normalize();
        final Path absolute = batchRoot.resolve("precheck/machine_01/previews/010101__mask_preview.png");
        final String relative = BatchPrecheckProcessor.relativeToBatch(batchRoot, absolute);
        assertEquals("precheck/machine_01/previews/010101__mask_preview.png", relative.replace('\\', '/'));
    }

    @Test
    void relativeToBatchHandlesNulls() {
        assertEquals(RCsvSupport.NA, BatchPrecheckProcessor.relativeToBatch(null, Paths.get("/a")));
        assertEquals(RCsvSupport.NA, BatchPrecheckProcessor.relativeToBatch(Paths.get("/a"), null));
    }

    @Test
    void processFailsFastForMissingSourceFile() throws Exception {
        final Path batchRoot = tempDir.resolve("batch").toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        final Path missing = tempDir.resolve("missing/010101.tif").toAbsolutePath().normalize();
        final Path configPath = batchRoot.resolve("batch.properties");
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        Files.write(configPath, config.canonicalText().getBytes(StandardCharsets.UTF_8));

        // Job constructor does not require source to exist; fromMap does.
        final BatchPrecheckJob job = new BatchPrecheckJob(
                1,
                SixDigitSampleId.parse("010101"),
                "machine_01",
                missing,
                "010101.tif",
                SHA64,
                configPath,
                config.sha256(),
                batchRoot,
                batchRoot.resolve("precheck/result.properties"),
                batchRoot.resolve("precheck/out.log"),
                batchRoot.resolve("precheck/err.log"),
                batchRoot.resolve("precheck/mask.png"),
                batchRoot.resolve("precheck/scale.png"));

        final BatchPrecheckResult result = new BatchPrecheckProcessor().process(job, config);
        assertTrue(result.status().isFailure()
                || result.status() == BatchPrecheckStatus.INVALID_SOURCE
                || result.status() == BatchPrecheckStatus.STALE_SOURCE
                || result.status() == BatchPrecheckStatus.FAILED_OPEN_IMAGE
                || result.status() == BatchPrecheckStatus.FAILED_UNEXPECTED);
        assertEquals("010101", result.get(BatchPrecheckResult.KEY_IMAGE_ID));
    }

    @Test
    void optionalImageJSmokeWhenAvailable() {
        boolean available = false;
        try {
            Class.forName("ij.ImagePlus");
            Class.forName("ij.process.ColorProcessor");
            available = true;
        } catch (final ClassNotFoundException ignored) {
            available = false;
        }
        if (!available) {
            return;
        }
        final ij.process.ColorProcessor cp = new ij.process.ColorProcessor(4, 4);
        final ij.ImagePlus image = new ij.ImagePlus("smoke", cp);
        assertEquals(4, image.getWidth());
        assertEquals(4, image.getHeight());
        image.close();
    }

    @Test
    void fromMapRejectsMissingSourcePathFile() throws Exception {
        final Path batchRoot = tempDir.resolve("batch2").toAbsolutePath().normalize();
        Files.createDirectories(batchRoot);
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(BatchPrecheckJob.KEY_SCHEMA, "1");
        map.put(BatchPrecheckJob.KEY_IMAGE_ID, "010101");
        map.put(BatchPrecheckJob.KEY_MACHINE_ID, "machine_01");
        map.put(BatchPrecheckJob.KEY_SOURCE_PATH,
                tempDir.resolve("no-such.tif").toAbsolutePath().normalize().toString());
        map.put(BatchPrecheckJob.KEY_SOURCE_RELATIVE, "no-such.tif");
        map.put(BatchPrecheckJob.KEY_SOURCE_SHA256, SHA64);
        map.put(BatchPrecheckJob.KEY_CONFIG_PATH, batchRoot.resolve("c.properties").toString());
        map.put(BatchPrecheckJob.KEY_CONFIG_SHA256, SHA64);
        map.put(BatchPrecheckJob.KEY_BATCH_ROOT, batchRoot.toString());
        map.put(BatchPrecheckJob.KEY_RESULT_PATH, batchRoot.resolve("r.properties").toString());
        map.put(BatchPrecheckJob.KEY_STDOUT_PATH, batchRoot.resolve("o.log").toString());
        map.put(BatchPrecheckJob.KEY_STDERR_PATH, batchRoot.resolve("e.log").toString());
        map.put(BatchPrecheckJob.KEY_MASK_PREVIEW, batchRoot.resolve("m.png").toString());
        map.put(BatchPrecheckJob.KEY_SCALE_PREVIEW, batchRoot.resolve("s.png").toString());
        try {
            BatchPrecheckJob.fromMap(map);
            throw new AssertionError("expected missing source rejection");
        } catch (final IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("source file must exist"));
        }
    }
}
