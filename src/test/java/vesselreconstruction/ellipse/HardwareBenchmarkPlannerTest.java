package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkPlannerTest {

    @Test
    void threadStepsAreDerivedFromPhysicalCoresNotAPersonalPc() {
        final List<Integer> eightPhysicalSixteenLogical = toList(
                HardwareBenchmarkPlanner.threadSteps(
                        HardwareBenchmarkFixtures.inventory(8, 16, 16L << 30)));
        assertEquals(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2),
                        Integer.valueOf(4), Integer.valueOf(8), Integer.valueOf(16)),
                eightPhysicalSixteenLogical);
        assertTrue(!eightPhysicalSixteenLogical.contains(Integer.valueOf(20)));
        assertTrue(!eightPhysicalSixteenLogical.contains(Integer.valueOf(24)));
        assertTrue(!eightPhysicalSixteenLogical.contains(Integer.valueOf(32)));
        assertEquals(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2),
                        Integer.valueOf(4), Integer.valueOf(8)),
                toList(HardwareBenchmarkPlanner.threadSteps(
                        HardwareBenchmarkFixtures.inventory(8, 8, 16L << 30))));
        assertEquals(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(4),
                        Integer.valueOf(8), Integer.valueOf(16), Integer.valueOf(32)),
                toList(HardwareBenchmarkPlanner.threadSteps(
                        HardwareBenchmarkFixtures.inventory(16, 32, 64L << 30))));
        assertEquals(
                java.util.Arrays.asList(Integer.valueOf(1)),
                toList(HardwareBenchmarkPlanner.threadSteps(
                        HardwareBenchmarkFixtures.inventory(1, 1, 2L << 30))));
    }

    @Test
    void planIsDeterministicAndStartsAtBaselineOneThread() {
        final HardwareInventory inventory = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final List<HardwareBenchmarkPlanner.Candidate> first = HardwareBenchmarkPlanner.plan(inventory);
        final List<HardwareBenchmarkPlanner.Candidate> second = HardwareBenchmarkPlanner.plan(inventory);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).id, second.get(i).id);
            assertEquals(first.get(i).threadsPerTile, second.get(i).threadsPerTile);
            assertEquals(first.get(i).fijiHeapMb, second.get(i).fijiHeapMb);
        }
        assertEquals(HardwareBenchmarkPlanner.PHASE_BASELINE, first.get(0).phase);
        assertEquals(1, first.get(0).threadsPerTile);
        assertTrue(first.size() >= 2);
        assertTrue(first.size() <= 5);
    }

    @Test
    void planNeverVariesTileGeometry() {
        final List<HardwareBenchmarkPlanner.Candidate> planned =
                HardwareBenchmarkPlanner.plan(HardwareBenchmarkFixtures.eightCoreSixteenGib());
        for (int i = 0; i < planned.size(); i++) {
            assertTrue(planned.get(i).threadsPerTile >= 1);
            assertTrue(planned.get(i).fijiHeapMb >= 512);
            assertTrue(planned.get(i).id.indexOf("512x512") < 0);
            assertTrue(planned.get(i).id.indexOf("2048x2048") < 0);
        }
    }

    @Test
    void heapIsCappedToLeaveOperatingSystemHeadroom() {
        final HardwareInventory small = HardwareBenchmarkFixtures.inventory(4, 4, 2L << 30);
        final int baseline = HardwareBenchmarkPlanner.baselineHeapMb(small);
        assertTrue(baseline <= small.ramMegabytes() - 512);
        assertTrue(baseline >= 512);
        final HardwareInventory large = HardwareBenchmarkFixtures.eightCoreSixteenGib();
        final int medium = HardwareBenchmarkPlanner.mediumHeapMb(large,
                HardwareBenchmarkPlanner.baselineHeapMb(large));
        assertTrue(medium > HardwareBenchmarkPlanner.baselineHeapMb(large));
        assertTrue(medium <= large.ramMegabytes() - 2048);
    }

    @Test
    void refinementIsNullWhenMediumHeapWouldNotIncrease() {
        final HardwareInventory tiny = HardwareBenchmarkFixtures.inventory(2, 2, 512L * 1024L * 1024L);
        final HardwareBenchmarkPlanner.Candidate best = new HardwareBenchmarkPlanner.Candidate(
                "x", HardwareBenchmarkPlanner.PHASE_BASELINE, 1,
                HardwareBenchmarkPlanner.baselineHeapMb(tiny));
        final HardwareBenchmarkPlanner.Candidate refine =
                HardwareBenchmarkPlanner.refinement(tiny, best);
        if (refine != null) {
            assertTrue(refine.fijiHeapMb > best.fijiHeapMb);
            assertEquals(best.threadsPerTile, refine.threadsPerTile);
        } else {
            assertNull(refine);
        }
    }

    @Test
    void confirmationKeepsTheSameKnobs() {
        final HardwareBenchmarkPlanner.Candidate best = new HardwareBenchmarkPlanner.Candidate(
                "B_THREADS_4t_4096m", HardwareBenchmarkPlanner.PHASE_THREADS, 4, 4096);
        final HardwareBenchmarkPlanner.Candidate confirm = HardwareBenchmarkPlanner.confirmation(best);
        assertEquals(HardwareBenchmarkPlanner.PHASE_CONFIRM, confirm.phase);
        assertEquals(4, confirm.threadsPerTile);
        assertEquals(4096, confirm.fijiHeapMb);
    }

    private static List<Integer> toList(final int[] values) {
        final List<Integer> list = new java.util.ArrayList<Integer>();
        for (int i = 0; i < values.length; i++) {
            list.add(Integer.valueOf(values[i]));
        }
        return list;
    }
}
