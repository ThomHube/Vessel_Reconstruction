package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingResultTest {

    private static final String SHA64 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir
    Path tempDir;

    @Test
    void roundTripWriteAndLoad() throws Exception {
        final BatchFullProcessingResult original = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .reasonCode(RCsvSupport.NA)
                .message("full processing committed")
                .imageId("0101A1")
                .machineId("machine_01")
                .sourceFilename("0101A1.tif")
                .sourceSha256(SHA64)
                .configurationSha256(SHA64)
                .classifierFilename("classifier.model")
                .classifierSha256(SHA64)
                .attemptNumber(Integer.valueOf(1))
                .tileProfile(Integer.valueOf(0), Integer.valueOf(2048), Integer.valueOf(2048))
                .precheckVerified(Boolean.TRUE)
                .maskReproduced(Boolean.TRUE)
                .scaleBarReproduced(Boolean.TRUE)
                .classificationCompleted(Boolean.TRUE)
                .reconstructionCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .sourceUnchanged(Boolean.TRUE)
                .outOfMemoryDetected(Boolean.FALSE)
                .classificationRunId("class-run-1")
                .reconstructionRunId("recon-run-1")
                .datasetRoot("/tmp/dataset")
                .originalRgbPath("images/original_rgb/0101A__0101A1__original_rgb.tif")
                .approvedMaskPath("images/approved_masks/0101A__0101A1__approved_mask_ds8.tif")
                .maskedRgbPath("images/masked_rgb/0101A__0101A1__masked_rgb.tif")
                .maskedClassifiedPath("images/masked_classified/0101A__0101A1__masked_classified.tif")
                .reconstructedClassifiedPath(
                        "images/reconstructed_classified/0101A__0101A1__reconstructed_classified.tif")
                .lumenMeasurementsPath(
                        "data/lumen_measurements/per_segment/0101A__0101A1__lumen_measurements.csv")
                .artifactDiagnosticsPath(
                        "data/diagnostics/per_segment/0101A__0101A1__artifact_diagnostics.csv")
                .imageManifestPath("data/manifests/image_manifest.csv")
                .segmentManifestPath("data/manifests/segment_manifest.csv")
                .processingRunsPath("data/manifests/processing_runs.csv")
                .components(Integer.valueOf(10), Integer.valueOf(8), Integer.valueOf(3),
                        Integer.valueOf(500))
                .sourceChecksums(Integer.valueOf(1), Integer.valueOf(1))
                .processingTimeMs(4321L)
                .build();

        final Path path = tempDir.resolve("result.properties");
        original.write(path);
        final BatchFullProcessingResult loaded = BatchFullProcessingResult.load(path);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, loaded.status());
        assertEquals(original.asMap(), loaded.asMap());
        assertEquals(original.toMap(), loaded.toMap());
    }

    @Test
    void unsetFieldsDefaultToNa() {
        final BatchFullProcessingResult result = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.INVALID_JOB)
                .build();
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_IMAGE_ID));
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_CLASSIFICATION_RUN_ID));
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID));
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_PRECHECK_VERIFIED));
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_MASK_REPRODUCED));
    }

    @Test
    void booleanFlagsAreTrueFalseOrNa() {
        final BatchFullProcessingResult result = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION)
                .precheckVerified(Boolean.TRUE)
                .maskReproduced(Boolean.FALSE)
                .build();
        assertEquals(RCsvSupport.TRUE, result.get(BatchFullProcessingResult.KEY_PRECHECK_VERIFIED));
        assertEquals(RCsvSupport.FALSE, result.get(BatchFullProcessingResult.KEY_MASK_REPRODUCED));
        assertEquals(RCsvSupport.NA, result.get(BatchFullProcessingResult.KEY_SCALE_BAR_REPRODUCED));
    }

    @Test
    void unknownKeyRejectedByFromMap() {
        final BatchFullProcessingResult ok = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FAILED_TIMEOUT)
                .build();
        final TreeMap<String, String> map = new TreeMap<String, String>(ok.asMap());
        map.put("bogus.key", "1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingResult.fromMap(map));
        assertTrue(ex.getMessage().contains("Unknown result key"));
    }

    @Test
    void missingKeyRejectedByFromMap() {
        final BatchFullProcessingResult ok = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FAILED_TIMEOUT)
                .build();
        final TreeMap<String, String> map = new TreeMap<String, String>(ok.asMap());
        map.remove(BatchFullProcessingResult.KEY_TIME_MS);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingResult.fromMap(map));
        assertTrue(ex.getMessage().contains("Missing result key"));
    }

    @Test
    void invalidStatusRejectedByFromMap() {
        final BatchFullProcessingResult ok = BatchFullProcessingResult.builder()
                .status(BatchFullProcessingStatus.FAILED_TIMEOUT)
                .build();
        final TreeMap<String, String> map = new TreeMap<String, String>(ok.asMap());
        map.put(BatchFullProcessingResult.KEY_STATUS, "NOT_A_REAL_STATUS");
        assertThrows(IllegalArgumentException.class, () -> BatchFullProcessingResult.fromMap(map));
    }
}
