package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckResultTest {

    private static final String SHA64 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir
    Path tempDir;

    @Test
    void roundTripWriteAndLoad() throws Exception {
        final BatchPrecheckResult original = BatchPrecheckResult.builder()
                .status(BatchPrecheckStatus.PRECHECK_SUCCESS)
                .message("ok")
                .reasonCode(RCsvSupport.NA)
                .imageId("0101A1")
                .machineId("machine_01")
                .sourceFilename("0101A1.tif")
                .sourceRelativePath("0101A1.tif")
                .sourceSha256(SHA64)
                .configurationSha256(SHA64)
                .imageSize(100, 80)
                .maskStats(Integer.valueOf(40), Integer.valueOf(2), 0.25, 0.7,
                        Long.valueOf(1000L), 0.1, Integer.valueOf(99))
                .crop(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(50), Integer.valueOf(40))
                .scaleBar("TERMINAL_TICK_CENTERLINES", "MEDIUM", 0.9, 456.0, 12.0, 0.98,
                        1.0954306217917742, 0.0)
                .sourceChecksums(Integer.valueOf(1), Integer.valueOf(1), Boolean.TRUE)
                .previews("precheck/mask.png", "precheck/scale.png")
                .processingTimeMs(12L)
                .build();

        final Path path = tempDir.resolve("result.properties");
        original.write(path);
        final BatchPrecheckResult loaded = BatchPrecheckResult.load(path);
        assertEquals(BatchPrecheckStatus.PRECHECK_SUCCESS, loaded.status());
        assertEquals(original.asMap(), loaded.asMap());
    }

    @Test
    void numberOrNaAndBuilderEmitNaForNonFinite() {
        assertEquals(RCsvSupport.NA, BatchPrecheckResult.numberOrNa(Double.NaN));
        assertEquals(RCsvSupport.NA, BatchPrecheckResult.numberOrNa(Double.POSITIVE_INFINITY));
        assertEquals(RCsvSupport.NA, BatchPrecheckResult.numberOrNa(Double.NEGATIVE_INFINITY));
        assertTrue(BatchPrecheckResult.numberOrNa(1.25).contains("1.25"));

        final BatchPrecheckResult result = BatchPrecheckResult.builder()
                .status(BatchPrecheckStatus.REVIEW_REQUIRED_MASK)
                .imageId("0101A1")
                .machineId("machine_01")
                .sourceFilename("0101A1.tif")
                .sourceRelativePath("0101A1.tif")
                .sourceSha256(SHA64)
                .configurationSha256(SHA64)
                .maskStats(null, null, Double.NaN, Double.POSITIVE_INFINITY,
                        null, Double.NEGATIVE_INFINITY, null)
                .scaleBar(null, null, Double.NaN, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN)
                .build();

        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_MASK_INCLUDED));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_MASK_MAJOR));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_MASK_SECONDARY));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_SB_SCORE));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_SB_CENTER));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_SB_OUTER));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_SB_CONT));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_PX_SIZE));
        assertEquals(RCsvSupport.NA, result.get(BatchPrecheckResult.KEY_PX_DEV));
    }

    @Test
    void statusParseViaFromMap() {
        final BatchPrecheckResult ok = BatchPrecheckResult.builder()
                .status(BatchPrecheckStatus.FAILED_TIMEOUT)
                .imageId("0101A1")
                .machineId("machine_01")
                .build();
        assertEquals(BatchPrecheckStatus.FAILED_TIMEOUT, ok.status());

        final java.util.Map<String, String> map =
                new java.util.TreeMap<String, String>(ok.asMap());
        map.put(BatchPrecheckResult.KEY_STATUS, "NOT_A_REAL_STATUS");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchPrecheckResult.fromMap(map));
        assertTrue(ex.getMessage().contains("Unknown precheck status")
                || (ex.getCause() != null
                && ex.getCause().getMessage() != null));
    }
}
