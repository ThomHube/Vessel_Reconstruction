package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HardwareBenchmarkSingleMachineCompatibilityTest {

    @Test
    void selectedProfileStaysOnSingleAnalysisHost() {
        final HardwareExecutionProfile profile = HardwareBenchmarkReport.selectedProfile(
                HardwareBenchmarkFixtures.eightCoreSixteenGib(),
                HardwareBenchmarkFixtures.ok(
                        "B_THREADS_4t_4096m", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 4096,
                        9.0d, 40.0d, 1L << 30, 0L),
                "commit",
                "stable",
                "headroom");
        assertEquals("1", profile.metadata().get("machines.count"));
        assertEquals(HardwareExecutionProfile.PRODUCTION_MACHINE_ID,
                profile.metadata().get("machine_id"));
        assertEquals(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN,
                profile.productionTileProfile().toToken());
    }

    @Test
    void assignmentStillPlacesEveryImageOnMachine01() {
        final List<BatchTiffFile> files = java.util.Arrays.asList(
                fake("9999A1"), fake("9999A2"));
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, HardwareExecutionProfile.PRODUCTION_MACHINE_COUNT);
        assertEquals(1, slices.size());
        assertEquals(HardwareExecutionProfile.PRODUCTION_MACHINE_ID, slices.get(0).machineId());
        assertEquals(2, slices.get(0).assignedCount());
    }

    private static BatchTiffFile fake(final String imageId) {
        final CoreSegmentId id = CoreSegmentId.parse(imageId);
        final String name = imageId + ".tif";
        return new BatchTiffFile(
                id,
                java.nio.file.Paths.get("/abs", name),
                java.nio.file.Paths.get(name),
                name,
                ".tif",
                10L,
                String.format("%064d", Integer.valueOf(1)));
    }
}
