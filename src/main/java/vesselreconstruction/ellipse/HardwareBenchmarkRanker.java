package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selects the fastest <em>stable</em> configuration. CPU percent is recorded
 * but is not the objective.
 */
public final class HardwareBenchmarkRanker {

    public static final double MIN_THROUGHPUT_GAIN = 0.05d;
    public static final long MIN_SWAP_GROWTH_BYTES = 64L * 1024L * 1024L;

    private HardwareBenchmarkRanker() {
    }

    public static HardwareBenchmarkRunResult select(final List<HardwareBenchmarkRunResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results must not be empty");
        }
        HardwareBenchmarkRunResult best = null;
        for (int i = 0; i < results.size(); i++) {
            final HardwareBenchmarkRunResult candidate = results.get(i);
            if (!candidate.safeSuccess()) {
                continue;
            }
            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Stops a concurrency sweep when the next result is unsafe or not a
     * meaningful throughput improvement.
     */
    public static boolean shouldStopThreadSweep(
            final HardwareBenchmarkRunResult previousSuccessful,
            final HardwareBenchmarkRunResult current) {
        if (current == null || !current.safeSuccess()) {
            return true;
        }
        if (previousSuccessful == null) {
            return false;
        }
        final double prev = previousSuccessful.rankingThroughput();
        if (prev <= 0.0d) {
            return false;
        }
        return current.rankingThroughput() < prev * (1.0d + MIN_THROUGHPUT_GAIN);
    }

    public static boolean isBetter(
            final HardwareBenchmarkRunResult candidate,
            final HardwareBenchmarkRunResult incumbent) {
        if (candidate.rankingThroughput() > incumbent.rankingThroughput()
                * (1.0d + MIN_THROUGHPUT_GAIN)) {
            return true;
        }
        if (incumbent.rankingThroughput() > candidate.rankingThroughput()
                * (1.0d + MIN_THROUGHPUT_GAIN)) {
            return false;
        }
        if (candidate.peakSwapUsedBytes < incumbent.peakSwapUsedBytes) {
            return true;
        }
        if (candidate.fijiHeapMb < incumbent.fijiHeapMb) {
            return true;
        }
        return candidate.threadsPerTile < incumbent.threadsPerTile;
    }

    public static String describeSelection(
            final HardwareBenchmarkRunResult selected,
            final HardwareInventory inventory) {
        if (selected == null) {
            return "No safe successful configuration. Keep classification.threads_per_tile=1.";
        }
        final long headroom = Math.max(0L, inventory.ramBytes() - selected.peakRssBytes);
        final String headroomMb = Long.toString(headroom / (1024L * 1024L));
        final String unit = HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)
                ? "pixels/s" : "tiles/s";
        return String.format(Locale.US,
                "Highest stable throughput %.3f %s at threads_per_tile=%d and fiji.heap_mb=%d "
                        + "with RAM headroom %s MiB and swap growth %d bytes.",
                Double.valueOf(selected.rankingThroughput()),
                unit,
                Integer.valueOf(selected.threadsPerTile),
                Integer.valueOf(selected.fijiHeapMb),
                headroomMb,
                Long.valueOf(selected.swapGrowthBytes()));
    }

    public static String safetyMargin(
            final HardwareBenchmarkRunResult selected,
            final HardwareInventory inventory) {
        if (selected == null) {
            return "none; no safe configuration";
        }
        final long headroom = Math.max(0L, inventory.ramBytes() - selected.peakRssBytes);
        final double headroomFraction = inventory.ramBytes() <= 0L
                ? 0.0d
                : (double) headroom / (double) inventory.ramBytes();
        return String.format(Locale.US,
                "RAM headroom %.1f%% (%d MiB); swap growth %d bytes; not selected for peak CPU%%.",
                Double.valueOf(headroomFraction * 100.0d),
                Long.valueOf(headroom / (1024L * 1024L)),
                Long.valueOf(selected.swapGrowthBytes()));
    }

    public static List<HardwareBenchmarkRunResult> successfulOnly(
            final List<HardwareBenchmarkRunResult> results) {
        final List<HardwareBenchmarkRunResult> ok = new ArrayList<HardwareBenchmarkRunResult>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).safeSuccess()) {
                ok.add(results.get(i));
            }
        }
        return ok;
    }
}
