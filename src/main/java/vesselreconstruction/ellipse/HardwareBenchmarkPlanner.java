package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Builds a small, deterministic, hardware-derived search space.
 *
 * <p>Never varies tile geometry. Never hard-codes a particular PC.</p>
 */
public final class HardwareBenchmarkPlanner {

    public static final String PHASE_BASELINE = "A_BASELINE";
    public static final String PHASE_THREADS = "B_THREADS";
    public static final String PHASE_HEAP = "C_HEAP";
    public static final String PHASE_CONFIRM = "D_CONFIRM";

    public static final class Candidate {
        public final String id;
        public final String phase;
        public final int threadsPerTile;
        public final int fijiHeapMb;

        public Candidate(
                final String id,
                final String phase,
                final int threadsPerTile,
                final int fijiHeapMb) {
            this.id = id;
            this.phase = phase;
            this.threadsPerTile = threadsPerTile;
            this.fijiHeapMb = fijiHeapMb;
        }
    }

    private HardwareBenchmarkPlanner() {
    }

    public static List<Candidate> plan(final HardwareInventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        final int baselineHeap = baselineHeapMb(inventory);
        final int[] threadSteps = threadSteps(inventory);
        final List<Candidate> planned = new ArrayList<Candidate>();
        planned.add(new Candidate(
                idFor(PHASE_BASELINE, threadSteps[0], baselineHeap),
                PHASE_BASELINE,
                threadSteps[0],
                baselineHeap));
        for (int i = 1; i < threadSteps.length; i++) {
            planned.add(new Candidate(
                    idFor(PHASE_THREADS, threadSteps[i], baselineHeap),
                    PHASE_THREADS,
                    threadSteps[i],
                    baselineHeap));
        }
        return Collections.unmodifiableList(planned);
    }

    /**
     * Nearby heap refinement around a successful candidate. Empty when the
     * nearby heap would duplicate an already-planned value.
     */
    public static Candidate refinement(
            final HardwareInventory inventory,
            final Candidate best) {
        final int medium = mediumHeapMb(inventory, best.fijiHeapMb);
        if (medium <= best.fijiHeapMb) {
            return null;
        }
        return new Candidate(
                idFor(PHASE_HEAP, best.threadsPerTile, medium),
                PHASE_HEAP,
                best.threadsPerTile,
                medium);
    }

    public static Candidate confirmation(final Candidate best) {
        return new Candidate(
                "D_confirm_" + best.threadsPerTile + "t_" + best.fijiHeapMb + "m",
                PHASE_CONFIRM,
                best.threadsPerTile,
                best.fijiHeapMb);
    }

    static int[] threadSteps(final HardwareInventory inventory) {
        final int physical = inventory.physicalCores();
        final int logical = inventory.logicalCpus();
        final LinkedHashSet<Integer> unique = new LinkedHashSet<Integer>();
        unique.add(Integer.valueOf(1));
        unique.add(Integer.valueOf(Math.max(1, physical / 4)));
        unique.add(Integer.valueOf(Math.max(1, physical / 2)));
        unique.add(Integer.valueOf(physical));
        if (logical > physical) {
            unique.add(Integer.valueOf(logical));
        }
        final List<Integer> ordered = new ArrayList<Integer>(unique);
        Collections.sort(ordered);
        final int[] steps = new int[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            steps[i] = ordered.get(i).intValue();
        }
        return steps;
    }

    static int baselineHeapMb(final HardwareInventory inventory) {
        final int ramMb = inventory.ramMegabytes();
        final int leaveForOs = Math.min(2048, Math.max(512, ramMb / 8));
        final int quarter = Math.max(512, ramMb / 4);
        return capHeap(quarter, ramMb, leaveForOs);
    }

    static int mediumHeapMb(final HardwareInventory inventory, final int baselineHeapMb) {
        final int ramMb = inventory.ramMegabytes();
        final int leaveForOs = Math.min(2048, Math.max(512, ramMb / 8));
        final int half = Math.max(baselineHeapMb, ramMb / 2);
        return capHeap(half, ramMb, leaveForOs);
    }

    static int capHeap(final int requestedMb, final int ramMb, final int leaveForOs) {
        final int max = Math.max(512, ramMb - leaveForOs);
        int heap = requestedMb;
        if (heap < 512) {
            heap = 512;
        }
        if (heap > max) {
            heap = max;
        }
        return heap;
    }

    static String idFor(final String phase, final int threads, final int heapMb) {
        return String.format(Locale.US, "%s_%dt_%dm", phase, Integer.valueOf(threads),
                Integer.valueOf(heapMb));
    }
}
