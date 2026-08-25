package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkReportTest {

    @Test
    void selectedProfileContainsFixedTileGeometryAndReason() {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkFixtures.ok(
                "B_THREADS_4t_4096m", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 4096,
                11.5d, 45.0d, 2L << 30, 0L);
        final HardwareExecutionProfile profile = HardwareBenchmarkReport.selectedProfile(
                inventory, selected, "abc123", "highest stable throughput", "RAM headroom 50%");
        assertEquals(4, profile.threadsPerTile());
        assertEquals(4096, profile.fijiHeapMb());
        assertEquals("1024", profile.metadata().get("tile_width"));
        assertEquals("1024", profile.metadata().get("tile_height"));
        assertEquals("1", profile.metadata().get("machines.count"));
        assertEquals("machine_01", profile.metadata().get("machine_id"));
        assertEquals("abc123", profile.metadata().get("source.commit"));
        assertTrue(profile.metadata().get("disclaimer").contains("NOT FOR SCIENTIFIC ANALYSIS"));
        assertEquals("highest stable throughput", profile.metadata().get("benchmark.reason"));
    }

    @Test
    void writeProducesRequiredReportFiles(@TempDir final Path root) throws Exception {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkFixtures.ok(
                "A_BASELINE_1t_4096m", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 4096,
                5.0d, 25.0d, 1L << 30, 0L);
        final List<HardwareBenchmarkRunResult> results = Arrays.asList(
                selected,
                HardwareBenchmarkFixtures.unsafe("bad", HardwareBenchmarkPlanner.PHASE_THREADS,
                        8, 4096, "timeout", 0.0d));
        final String reason = HardwareBenchmarkRanker.describeSelection(selected, inventory);
        final String safety = HardwareBenchmarkRanker.safetyMargin(selected, inventory);
        HardwareBenchmarkReport.write(root, inventory, results, selected, "deadbeef", reason, safety);

        assertTrue(Files.isRegularFile(root.resolve("hardware.json")));
        assertTrue(Files.isRegularFile(root.resolve("benchmark_configurations.csv")));
        assertTrue(Files.isRegularFile(root.resolve("benchmark_measurements.csv")));
        assertTrue(Files.isRegularFile(root.resolve("selected_profile.properties")));
        assertTrue(Files.isRegularFile(root.resolve("README.txt")));
        assertTrue(Files.isRegularFile(root.resolve("SUCCESS")));
        assertTrue(Files.isRegularFile(root.resolve("STATUS.txt")));

        final HardwareExecutionProfile synthetic =
                HardwareExecutionProfile.load(root.resolve("selected_profile.properties"));
        assertEquals(HardwareExecutionProfile.STATUS_SYNTHETIC_ONLY,
                synthetic.metadata().get("benchmark_status"));
        assertEquals(RCsvSupport.FALSE, synthetic.metadata().get("production_ready"));

        final String hardware = new String(Files.readAllBytes(root.resolve("hardware.json")),
                StandardCharsets.UTF_8);
        assertTrue(hardware.contains("\"physical_cores\": 8"));
        final String readme = new String(Files.readAllBytes(root.resolve("README.txt")),
                StandardCharsets.UTF_8);
        assertTrue(readme.contains("NOT FOR SCIENTIFIC ANALYSIS"));
        assertTrue(readme.contains("tile=1024x1024"));
        final HardwareExecutionProfile loaded =
                HardwareExecutionProfile.load(root.resolve("selected_profile.properties"));
        assertEquals(1024, Integer.parseInt(loaded.metadata().get("tile_width")));
        assertEquals(1024, Integer.parseInt(loaded.metadata().get("tile_height")));
        assertEquals(1, loaded.threadsPerTile());

        final String summary = HardwareBenchmarkReport.formatSummary(
                inventory, results, selected, reason, safety,
                root.resolve("selected_profile.properties"));
        assertTrue(summary.contains("LUMEN AUTOMATED HARDWARE BENCHMARK"));
        assertTrue(summary.contains("tile: 1024x1024"));
        assertTrue(summary.contains("SYNTHETIC_ONLY"));
        assertTrue(summary.contains("NOT PRODUCTION-READY"));
        assertTrue(summary.contains("Configurations tested: 2"));
        assertTrue(summary.contains("Successful: 1"));
        assertTrue(summary.contains("Rejected: 1"));
    }

    @Test
    void failedBenchmarkDoesNotWriteSuccessMarker(@TempDir final Path root) throws Exception {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final List<HardwareBenchmarkRunResult> results = Arrays.asList(
                HardwareBenchmarkFixtures.unsafe("a", HardwareBenchmarkPlanner.PHASE_BASELINE,
                        1, 512, "timeout", 0.0d));
        HardwareBenchmarkReport.write(root, inventory, results, null, "commit",
                "No safe successful configuration", "none");
        assertFalse(Files.exists(root.resolve("SUCCESS")));
        assertFalse(Files.exists(root.resolve("selected_profile.properties")));
        final String summary = HardwareBenchmarkReport.formatSummary(
                inventory, results, null, "No safe successful configuration", "none",
                root.resolve("selected_profile.properties"));
        assertTrue(summary.contains("BENCHMARK FAILED"));
    }

    @Test
    void realClassificationProfileIsProductionReady() {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkFixtures.okReal(
                "B_THREADS_4t_4096m", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 4096,
                2_500_000.0d, 40.0d, 1L << 30, 0L, 2304L * 2304L);
        final HardwareExecutionProfile profile = HardwareBenchmarkReport.selectedProfile(
                inventory, selected, "abc123", "highest stable throughput", "RAM headroom 50%");
        assertEquals(HardwareExecutionProfile.WORKLOAD_REAL,
                profile.metadata().get("benchmark_workload"));
        assertEquals(HardwareExecutionProfile.STATUS_REAL_VALIDATED,
                profile.metadata().get("benchmark_status"));
        assertEquals(RCsvSupport.TRUE, profile.metadata().get("production_ready"));
        assertEquals("1024", profile.metadata().get("tile_width"));
        assertEquals("1024", profile.metadata().get("tile_height"));
        assertEquals("classifier-sha-test", profile.metadata().get("classifier_sha256"));
        final String summary = HardwareBenchmarkReport.formatSummary(
                inventory, java.util.Collections.singletonList(selected), selected,
                "highest stable throughput", "RAM headroom 50%",
                java.nio.file.Paths.get("selected_profile.properties"));
        assertTrue(summary.contains("BENCHMARK SUCCESS"));
        assertTrue(summary.contains("REAL_CLASSIFICATION_VALIDATED"));
        assertTrue(summary.contains("pixels/s"));
    }

    @Test
    void syntheticOnlyProfileIsNotProductionReady() {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkFixtures.ok(
                "A_BASELINE_1t_4096m", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 4096,
                5.0d, 25.0d, 1L << 30, 0L);
        final HardwareExecutionProfile profile = HardwareBenchmarkReport.selectedProfile(
                inventory, selected, "abc123", "synthetic probe", "none");
        assertEquals(HardwareExecutionProfile.WORKLOAD_SYNTHETIC,
                profile.metadata().get("benchmark_type"));
        assertEquals(HardwareExecutionProfile.STATUS_SYNTHETIC_ONLY,
                profile.metadata().get("benchmark_status"));
        assertEquals(RCsvSupport.FALSE, profile.metadata().get("production_ready"));
        assertEquals(HardwareExecutionProfile.WORKLOAD_SYNTHETIC,
                profile.metadata().get("benchmark_workload"));
    }
}
