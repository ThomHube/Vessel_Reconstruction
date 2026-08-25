package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkRankerTest {

    @Test
    void selectsHighestStableThroughputNotHighestCpu() {
        final HardwareBenchmarkRunResult lowCpuFast = HardwareBenchmarkFixtures.ok(
                "fast", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 2048,
                10.0d, 40.0d, 1L << 30, 0L);
        final HardwareBenchmarkRunResult highCpuSlow = HardwareBenchmarkFixtures.ok(
                "busy", HardwareBenchmarkPlanner.PHASE_THREADS, 8, 2048,
                6.0d, 99.0d, 2L << 30, 0L);
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkRanker.select(
                Arrays.asList(highCpuSlow, lowCpuFast));
        assertEquals("fast", selected.configurationId);
        assertEquals(4, selected.threadsPerTile);
    }

    @Test
    void rejectsUnsafeConfigurationsEvenWithHighThroughput() {
        final HardwareBenchmarkRunResult safe = HardwareBenchmarkFixtures.ok(
                "safe", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 2048,
                3.0d, 20.0d, 1L << 30, 0L);
        final HardwareBenchmarkRunResult oom = HardwareBenchmarkFixtures.unsafe(
                "oom", HardwareBenchmarkPlanner.PHASE_THREADS, 8, 8192,
                "OutOfMemoryError", 99.0d);
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkRanker.select(
                Arrays.asList(safe, oom));
        assertEquals("safe", selected.configurationId);
        assertTrue(selected.safeSuccess());
        assertFalse(oom.safeSuccess());
    }

    @Test
    void returnsNullWhenEveryConfigurationIsUnsafe() {
        final List<HardwareBenchmarkRunResult> results = Arrays.asList(
                HardwareBenchmarkFixtures.unsafe("a", HardwareBenchmarkPlanner.PHASE_BASELINE,
                        1, 512, "timeout", 0.0d),
                HardwareBenchmarkFixtures.unsafe("b", HardwareBenchmarkPlanner.PHASE_THREADS,
                        2, 512, "non-zero exit 1", 0.0d));
        assertNull(HardwareBenchmarkRanker.select(results));
        assertTrue(HardwareBenchmarkRanker.successfulOnly(results).isEmpty());
    }

    @Test
    void stopsThreadSweepWithoutMeaningfulGain() {
        final HardwareBenchmarkRunResult previous = HardwareBenchmarkFixtures.ok(
                "prev", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 2048,
                10.0d, 30.0d, 1L << 30, 0L);
        final HardwareBenchmarkRunResult plateau = HardwareBenchmarkFixtures.ok(
                "next", HardwareBenchmarkPlanner.PHASE_THREADS, 2, 2048,
                10.2d, 80.0d, 1L << 30, 0L);
        assertTrue(HardwareBenchmarkRanker.shouldStopThreadSweep(previous, plateau));
        final HardwareBenchmarkRunResult improved = HardwareBenchmarkFixtures.ok(
                "gain", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 2048,
                12.0d, 50.0d, 1L << 30, 0L);
        assertFalse(HardwareBenchmarkRanker.shouldStopThreadSweep(previous, improved));
        assertTrue(HardwareBenchmarkRanker.shouldStopThreadSweep(previous,
                HardwareBenchmarkFixtures.unsafe("bad", HardwareBenchmarkPlanner.PHASE_THREADS,
                        8, 2048, "swap growth", 20.0d)));
    }

    @Test
    void similarThroughputPrefersLowerHeapAndThreads() {
        final HardwareBenchmarkRunResult heavier = HardwareBenchmarkFixtures.ok(
                "heavy", HardwareBenchmarkPlanner.PHASE_HEAP, 4, 8192,
                10.0d, 70.0d, 3L << 30, 0L);
        final HardwareBenchmarkRunResult lighter = HardwareBenchmarkFixtures.ok(
                "light", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 4096,
                10.1d, 40.0d, 2L << 30, 0L);
        assertTrue(HardwareBenchmarkRanker.isBetter(lighter, heavier));
        assertFalse(HardwareBenchmarkRanker.isBetter(heavier, lighter));
    }

    @Test
    void realClassificationRanksByPixelThroughputNotCpu() {
        final HardwareBenchmarkRunResult lowCpuFast = HardwareBenchmarkFixtures.okReal(
                "fast", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 2048,
                3_000_000.0d, 35.0d, 1L << 30, 0L, 2304L * 2304L);
        final HardwareBenchmarkRunResult highCpuSlow = HardwareBenchmarkFixtures.okReal(
                "busy", HardwareBenchmarkPlanner.PHASE_THREADS, 8, 2048,
                1_000_000.0d, 99.0d, 2L << 30, 0L, 2304L * 2304L);
        final HardwareBenchmarkRunResult selected = HardwareBenchmarkRanker.select(
                Arrays.asList(highCpuSlow, lowCpuFast));
        assertEquals("fast", selected.configurationId);
        assertEquals(HardwareExecutionProfile.WORKLOAD_REAL, selected.workloadType);
        assertTrue(HardwareBenchmarkRanker.describeSelection(selected,
                HardwareBenchmarkFixtures.eightCoreSixteenGib()).contains("pixels/s"));
    }

    @Test
    void realInvalidOutputIsNotRanked() {
        final HardwareBenchmarkRunResult invalid = new HardwareBenchmarkRunResult(
                "bad", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 2048,
                true, false, RCsvSupport.NA, 0, 1000L, 8.0d, 8, 20.0d, 20.0d,
                1L << 30, 1L << 30, 4L << 30, 0L, 0L, 1.0d, "exited",
                HardwareExecutionProfile.WORKLOAD_REAL, 2304L * 2304L, 2_000_000.0d,
                false, "classifier");
        assertFalse(invalid.safeSuccess());
        assertNull(HardwareBenchmarkRanker.select(Arrays.asList(invalid)));
    }

    @Test
    void hostSwapGrowthAloneDoesNotExcludeASuccessfulResult() {
        final long start = 510L * 1024L * 1024L;
        final long peak = 617L * 1024L * 1024L;
        final HardwareBenchmarkRunResult hostGrew = new HardwareBenchmarkRunResult(
                "host", HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 2048,
                true, false, RCsvSupport.NA, 0, 1000L, 8.0d, 8, 20.0d, 20.0d,
                1L << 30, 1L << 30, 4L << 30, peak, start, 1.0d, RCsvSupport.NA,
                HardwareExecutionProfile.WORKLOAD_REAL, 2304L * 2304L, 2_000_000.0d,
                true, "classifier", 0L, 0L);
        assertTrue(hostGrew.hostSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
        assertEquals(0L, hostGrew.processTreeSwapGrowthBytes());
        assertTrue(hostGrew.safeSuccess());
        assertEquals("host", HardwareBenchmarkRanker.select(Arrays.asList(hostGrew)).configurationId);
    }
}
