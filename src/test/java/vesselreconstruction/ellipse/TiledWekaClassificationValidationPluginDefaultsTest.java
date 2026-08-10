package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiledWekaClassificationValidationPluginDefaultsTest {

    @Test
    void dialogDefaultsMatchRequiredValues() {
        assertEquals(2048, TiledWekaClassificationDefaults.CORE_TILE_WIDTH_PIXELS);
        assertEquals(2048, TiledWekaClassificationDefaults.CORE_TILE_HEIGHT_PIXELS);
        assertEquals(1, TiledWekaClassificationDefaults.THREADS_PER_TILE);
        assertEquals(16, TiledWekaClassificationDefaults.SEAM_AUDIT_HALF_WIDTH_PIXELS);
        assertFalse(TiledWekaClassificationDefaults.RUN_UNTILED_REFERENCE_COMPARISON);
        assertEquals(8, TiledWekaClassificationDefaults.SAMPLE_MASK_DOWNSAMPLE_FACTOR);
        assertEquals(32,
                TiledWekaClassificationDefaults.SAMPLE_MASK_EXPANSION_MARGIN_ORIGINAL_PIXELS);
        assertTrue(TiledWekaClassificationDefaults.REVIEW_AND_EDIT_SAMPLE_MASK);
        assertTrue(TiledWekaClassificationDefaults.CROP_OUTPUT_TO_APPROVED_MASK_EXTENT);
        assertEquals(0, TiledWekaClassificationDefaults.ADDITIONAL_CROP_PADDING_ORIGINAL_PIXELS);
        assertTrue(TiledWekaClassificationDefaults.SAVE_CROPPED_CLASSIFIED_TIFF_AUTOMATICALLY);
        assertTrue(TiledWekaClassificationDefaults.SAVE_APPROVED_DOWNSAMPLED_MASK_AUTOMATICALLY);
        assertTrue(TiledWekaClassificationDefaults.SAVE_CROPPED_RGB_COPY);
        assertTrue("lumen_dataset".equals(TiledWekaClassificationDefaults.OUTPUT_DIRECTORY_NAME)
                || TiledWekaClassificationDefaults.LABEL_OUTPUT_DIRECTORY.contains("Dataset root"));
        assertEquals(CalibrationMode.AUTO_DETECT_RED_SCALE_BAR,
                TiledWekaClassificationDefaults.CALIBRATION_MODE);
        assertEquals(CalibrationMode.AUTO_DETECT_RED_SCALE_BAR.name(),
                TiledWekaClassificationDefaults.CALIBRATION_MODE_CHOICES[0]);
        assertEquals(500.0, TiledWekaClassificationDefaults.SCALE_BAR_PHYSICAL_LENGTH_UM, 0.0);
        assertEquals(1, TiledWekaClassificationDefaults.SEGMENT_ORDER);
        assertEquals(SegmentOrientation.UNSPECIFIED,
                TiledWekaClassificationDefaults.SEGMENT_ORIENTATION);
    }

    @Test
    void pluginSourceUsesMaskLabelsAndDefaults() throws IOException {
        final Path pluginPath = Paths.get(
                "src/main/java/vesselreconstruction/ellipse/"
                        + "TiledWekaClassificationValidationPlugin.java");
        final String source = new String(Files.readAllBytes(pluginPath), StandardCharsets.UTF_8);
        assertFalse(source.contains("Tiles in X"));
        assertFalse(source.contains("Tiles in Y"));
        assertTrue(source.contains("LABEL_SAMPLE_MASK_DOWNSAMPLE_FACTOR"));
        assertTrue(source.contains("LABEL_SAMPLE_MASK_EXPANSION_MARGIN"));
        assertTrue(source.contains("LABEL_REVIEW_SAMPLE_MASK"));
        assertTrue(source.contains("NonBlockingGenericDialog")
                || source.contains("SampleMaskReviewController"));
    }

    @Test
    void wekaStartsOnlyAfterCalibrationAcceptanceInPluginSource() throws IOException {
        final Path pluginPath = Paths.get(
                "src/main/java/vesselreconstruction/ellipse/"
                        + "TiledWekaClassificationValidationPlugin.java");
        final String source = new String(Files.readAllBytes(pluginPath), StandardCharsets.UTF_8);
        final int calibrationIdx = source.indexOf("new CalibrationWorkflow().acquire(");
        final int classifyIdx = source.indexOf("classifier.classify(");
        assertTrue(calibrationIdx >= 0);
        assertTrue(classifyIdx >= 0);
        assertTrue(calibrationIdx < classifyIdx);
        assertTrue(source.contains("Weka was not started"));
    }

    @Test
    void maskedRgbSavedBeforeWekaInPluginSource() throws IOException {
        final Path pluginPath = Paths.get(
                "src/main/java/vesselreconstruction/ellipse/"
                        + "TiledWekaClassificationValidationPlugin.java");
        final String source = new String(Files.readAllBytes(pluginPath), StandardCharsets.UTF_8);
        final int preIdx = source.indexOf("savePreClassificationArtifacts(");
        final int classifyIdx = source.indexOf("classifier.classify(");
        final int finalizeIdx = source.indexOf("finalizeClassificationStage(");
        assertTrue(preIdx >= 0);
        assertTrue(classifyIdx >= 0);
        assertTrue(finalizeIdx >= 0);
        assertTrue(preIdx < classifyIdx);
        assertTrue(classifyIdx < finalizeIdx);
        assertTrue(source.contains("Dataset root directory")
                || source.contains("LABEL_OUTPUT_DIRECTORY"));
    }

    @Test
    void pluginsConfigRegistersSingleCorrectClass() throws IOException {
        final Path config = Paths.get("src/main/resources/plugins.config");
        final String text = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        int count = 0;
        final String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("Robust Ellipse Reconstruction - Tiled Weka Validation")) {
                count++;
                assertTrue(lines[i].contains(
                        "vesselreconstruction.ellipse.TiledWekaClassificationValidationPlugin"));
            }
        }
        assertEquals(1, count);
    }
}
