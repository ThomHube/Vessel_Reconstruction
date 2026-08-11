package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsDefaultKeys() throws Exception {
        final Path config = writeConfig(BatchTestConfigs.validPropertiesText(false, 1, false));
        final BatchConfiguration loaded = BatchConfiguration.load(config);
        assertEquals(1, loaded.schemaVersion());
        assertEquals(false, loaded.recursive());
        assertEquals(false, loaded.requireCompleteExpectedSet());
        assertEquals(1, loaded.machinesCount());
        assertEquals(8, loaded.maskDownsampleFactor());
        assertEquals(ScaleBarDetectionConfidence.MEDIUM, loaded.scaleBarMinimumConfidence());
        assertEquals(60, loaded.precheckTimeoutMinutes());
    }

    @Test
    void rejectUnknownKey() {
        final Map<String, String> map = BatchTestConfigs.validMap();
        map.put("extra.key", "1");
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
        assertTrue(ex.getMessage().contains("Unknown"));
    }

    @Test
    void rejectMissingKey() {
        final Map<String, String> map = BatchTestConfigs.validMap();
        map.remove(BatchConfiguration.KEY_MACHINES_COUNT);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
        assertTrue(ex.getMessage().contains("Missing"));
    }

    @Test
    void strictBooleanParsing() {
        final Map<String, String> map = BatchTestConfigs.validMap();
        map.put(BatchConfiguration.KEY_INPUT_RECURSIVE, "True");
        assertThrows(IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
        map.put(BatchConfiguration.KEY_INPUT_RECURSIVE, "yes");
        assertThrows(IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
        map.put(BatchConfiguration.KEY_INPUT_RECURSIVE, "false");
        BatchConfiguration.fromMap(map);
    }

    @Test
    void invalidMachineCount() {
        final Map<String, String> map = BatchTestConfigs.validMap();
        map.put(BatchConfiguration.KEY_MACHINES_COUNT, "0");
        assertThrows(IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
        map.put(BatchConfiguration.KEY_MACHINES_COUNT, "-1");
        assertThrows(IllegalArgumentException.class, () -> BatchConfiguration.fromMap(map));
    }

    @Test
    void configurationHashIndependentOfKeyOrder() throws Exception {
        final Path a = writeConfig(
                "machines.count=3\n"
                        + "batch.schema_version=1\n"
                        + "input.require_complete_expected_set=false\n"
                        + "input.recursive=true\n"
                        + phase2Tail());
        final Path b = writeConfig(
                "batch.schema_version=1\n"
                        + "input.recursive=true\n"
                        + "input.require_complete_expected_set=false\n"
                        + "machines.count=3\n"
                        + phase2Tail());
        final BatchConfiguration ca = BatchConfiguration.load(a);
        final BatchConfiguration cb = BatchConfiguration.load(b);
        assertEquals(ca.canonicalText(), cb.canonicalText());
        assertEquals(ca.sha256(), cb.sha256());
        assertTrue(ca.canonicalText().endsWith("\n"));
        assertTrue(ca.canonicalText().contains("mask.downsample_factor=8\n"));
        assertTrue(ca.canonicalText().contains("precheck.timeout_minutes=60\n"));
    }

    @Test
    void configurationShaIncludesNewKeys() {
        final Map<String, String> map = BatchTestConfigs.validMap();
        final String sha1 = BatchConfiguration.fromMap(map).sha256();
        map.put(BatchConfiguration.KEY_PRECHECK_TIMEOUT, "61");
        final String sha2 = BatchConfiguration.fromMap(map).sha256();
        assertTrue(!sha1.equals(sha2));
    }

    @Test
    void rejectDuplicateKeysInFile() throws Exception {
        final Path config = writeConfig(
                BatchTestConfigs.validPropertiesText() + "machines.count=2\n");
        assertThrows(IllegalArgumentException.class, () -> BatchConfiguration.load(config));
    }

    private Path writeConfig(final String text) throws Exception {
        final Path path = tempDir.resolve("batch-" + System.nanoTime() + ".properties");
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String phase2Tail() {
        return "mask.downsample_factor=8\n"
                + "mask.expansion_margin_px=32\n"
                + "mask.crop_padding_px=0\n"
                + "mask.minimum_included_fraction=0.02\n"
                + "mask.maximum_included_fraction=0.85\n"
                + "mask.minimum_major_span_fraction=0.50\n"
                + "mask.maximum_secondary_component_area_fraction=0.30\n"
                + "scale_bar.physical_length_um=500.0\n"
                + "scale_bar.minimum_confidence=MEDIUM\n"
                + "scale_bar.require_terminal_tick_centerlines=true\n"
                + "scale_bar.minimum_shaft_continuity=0.95\n"
                + "scale_bar.expected_pixel_size_um=1.0954306217917742\n"
                + "scale_bar.maximum_relative_pixel_size_deviation=0.01\n"
                + "precheck.timeout_minutes=60\n"
                + "precheck.continue_after_failure=true\n"
                + "classification.tile_profiles=2048x2048;1536x1536;1024x1024\n"
                + "classification.threads_per_tile=1\n"
                + "classification.seam_audit_half_width_px=16\n"
                + "classification.run_untiled_reference_comparison=false\n"
                + "reconstruction.minimum_lumen_area_px=1000\n"
                + "full.timeout_minutes=720\n"
                + "full.refresh_aggregate_tables=true\n";
    }
}
