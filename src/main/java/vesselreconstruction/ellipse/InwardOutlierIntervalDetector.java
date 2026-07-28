package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Diagnostic detector for a contiguous inward-outlier interval on an ordered
 * residual profile.
 *
 * <p>Prototype constants are not final biological thresholds. Detection does not
 * exclude support points, refit the ellipse, or modify pixels.
 */
public final class InwardOutlierIntervalDetector {

    /** Converts MAD of a normal distribution to an approximate standard deviation. */
    private static final double NORMALIZED_MAD_SCALE = 1.4826;

    /** Strict inward core threshold multiplier (prototype constant). */
    private static final double CORE_SIGMA_MULTIPLIER = 3.0;

    /** Expansion threshold multiplier (prototype constant). */
    private static final double EXPANSION_SIGMA_MULTIPLIER = 1.5;

    private static final double SCALE_EPSILON = 1.0e-12;
    private static final int MIN_INTERVAL_EDGE_COUNT = 5;
    private static final double MAX_INTERVAL_BOUNDARY_FRACTION = 0.35;
    private static final double MIN_DOMINANCE_RATIO = 1.50;
    private static final int MIN_SAMPLES = 8;

    public InwardOutlierDetectionResult detect(final OrderedBoundaryResidualProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        final List<OrderedBoundaryResidualProfile.Sample> samples = profile.getSamples();
        if (samples.size() < MIN_SAMPLES) {
            throw new IllegalArgumentException(
                    "At least " + MIN_SAMPLES + " residual samples are required; got "
                            + samples.size());
        }

        final double[] residuals = new double[samples.size()];
        final Point2D.Double[] midpoints = new Point2D.Double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(i);
            residuals[i] = sample.getResidual();
            if (!Double.isFinite(residuals[i])) {
                throw new IllegalArgumentException(
                        "Residual must be finite at index " + i + ": " + residuals[i]);
            }
            midpoints[i] = sample.getSupportMidpoint();
        }

        requireFinite("medianResidual", profile.getMedianResidual());
        requireFinite("medianAbsoluteDeviation", profile.getMedianAbsoluteDeviation());
        requireFinite("minimumResidual", profile.getMinimumResidual());
        requireFinite("maximumResidual", profile.getMaximumResidual());

        return detectCore(
                profile,
                residuals,
                midpoints,
                profile.getMedianResidual(),
                profile.getMedianAbsoluteDeviation(),
                profile.getMinimumResidualIndex());
    }

    /**
     * Package-visible test hook using the same threshold and interval logic.
     */
    InwardOutlierDetectionResult detectResidualsForTesting(final List<Double> residuals) {
        if (residuals == null) {
            throw new IllegalArgumentException("residuals must not be null");
        }
        if (residuals.size() < MIN_SAMPLES) {
            throw new IllegalArgumentException(
                    "At least " + MIN_SAMPLES + " residuals are required; got "
                            + residuals.size());
        }
        final int n = residuals.size();
        if ((n & 1) == 1) {
            throw new IllegalArgumentException(
                    "Test residual sequences must have even length for lattice loop construction");
        }

        final double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            final Double value = residuals.get(i);
            if (value == null) {
                throw new IllegalArgumentException("residuals must not contain null entries");
            }
            if (!Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException(
                        "residuals must be finite at index " + i + ": " + value);
            }
            values[i] = value.doubleValue();
        }

        final double median = medianOf(values);
        final double[] absDev = new double[n];
        for (int i = 0; i < n; i++) {
            absDev[i] = Math.abs(values[i] - median);
        }
        final double mad = medianOf(absDev);

        int minIndex = 0;
        double minResidual = values[0];
        for (int i = 1; i < n; i++) {
            if (values[i] < minResidual) {
                minResidual = values[i];
                minIndex = i;
            }
        }

        final OrderedBoundaryLoop loop = buildSyntheticLoop(n);
        final EllipseModel ellipse = syntheticEllipseForLoop(loop);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);

        final Point2D.Double[] midpoints = new Point2D.Double[n];
        final List<Point2D.Double> loopMids = loop.getOrderedSupportMidpoints();
        for (int i = 0; i < n; i++) {
            midpoints[i] = new Point2D.Double(loopMids.get(i).x, loopMids.get(i).y);
        }

        return detectCore(profile, values, midpoints, median, mad, minIndex);
    }

    private static InwardOutlierDetectionResult detectCore(
            final OrderedBoundaryResidualProfile profile,
            final double[] residuals,
            final Point2D.Double[] midpoints,
            final double medianResidual,
            final double mad,
            final int globalMinimumIndex) {
        final double robustScale = NORMALIZED_MAD_SCALE * mad;
        if (!(robustScale > SCALE_EPSILON)) {
            return new InwardOutlierDetectionResult(
                    profile,
                    robustScale,
                    medianResidual,
                    medianResidual,
                    Collections.<InwardOutlierInterval>emptyList(),
                    null,
                    false,
                    InwardOutlierDetectionResult.ROBUST_SCALE_TOO_SMALL,
                    0.0);
        }

        final double coreThreshold =
                medianResidual - CORE_SIGMA_MULTIPLIER * robustScale;
        final double expansionThreshold =
                medianResidual - EXPANSION_SIGMA_MULTIPLIER * robustScale;
        if (!(coreThreshold < expansionThreshold)) {
            throw new IllegalStateException(
                    "Invariant violated: coreThreshold must be more negative than "
                            + "expansionThreshold");
        }

        int coreCount = 0;
        for (int i = 0; i < residuals.length; i++) {
            if (residuals[i] <= coreThreshold) {
                coreCount++;
            }
        }
        if (coreCount == 0) {
            return new InwardOutlierDetectionResult(
                    profile,
                    robustScale,
                    coreThreshold,
                    expansionThreshold,
                    Collections.<InwardOutlierInterval>emptyList(),
                    null,
                    false,
                    InwardOutlierDetectionResult.NO_CORE_POINTS,
                    0.0);
        }

        final List<int[]> expansionRuns = findCircularExpansionRuns(residuals, expansionThreshold);
        final List<InwardOutlierInterval> candidates = new ArrayList<InwardOutlierInterval>();
        for (int r = 0; r < expansionRuns.size(); r++) {
            final int[] run = expansionRuns.get(r);
            if (!runContainsCorePoint(
                    residuals, run[0], run[1], run[2] == 1, coreThreshold)) {
                continue;
            }
            candidates.add(buildInterval(
                    residuals,
                    midpoints,
                    run[0],
                    run[1],
                    run[2] == 1,
                    coreThreshold,
                    expansionThreshold,
                    globalMinimumIndex));
        }

        // Sort all candidates for diagnostic listing using selection order.
        sortCandidates(candidates);

        final List<InwardOutlierInterval> valid = new ArrayList<InwardOutlierInterval>();
        boolean sawTooShort = false;
        boolean sawTooLarge = false;
        for (int i = 0; i < candidates.size(); i++) {
            final InwardOutlierInterval interval = candidates.get(i);
            if (interval.getEdgeCount() < MIN_INTERVAL_EDGE_COUNT) {
                sawTooShort = true;
                continue;
            }
            if (interval.getBoundaryFraction() > MAX_INTERVAL_BOUNDARY_FRACTION) {
                sawTooLarge = true;
                continue;
            }
            valid.add(interval);
        }

        if (valid.isEmpty()) {
            final String reason;
            if (candidates.isEmpty()) {
                reason = InwardOutlierDetectionResult.NO_VALID_INTERVAL;
            } else if (sawTooShort && !sawTooLarge) {
                reason = InwardOutlierDetectionResult.INTERVAL_TOO_SHORT;
            } else if (sawTooLarge && !sawTooShort) {
                reason = InwardOutlierDetectionResult.INTERVAL_TOO_LARGE;
            } else if (sawTooShort || sawTooLarge) {
                // Prefer the more specific single cause when only one type occurred;
                // when both occurred, still report NO_VALID_INTERVAL after filtering.
                reason = InwardOutlierDetectionResult.NO_VALID_INTERVAL;
            } else {
                reason = InwardOutlierDetectionResult.NO_VALID_INTERVAL;
            }
            return new InwardOutlierDetectionResult(
                    profile,
                    robustScale,
                    coreThreshold,
                    expansionThreshold,
                    candidates,
                    null,
                    false,
                    reason,
                    0.0);
        }

        final InwardOutlierInterval best = valid.get(0);
        if (!best.isContainsGlobalMinimum()) {
            return new InwardOutlierDetectionResult(
                    profile,
                    robustScale,
                    coreThreshold,
                    expansionThreshold,
                    candidates,
                    null,
                    false,
                    InwardOutlierDetectionResult.NO_VALID_INTERVAL,
                    0.0);
        }

        final double dominanceRatio;
        if (valid.size() == 1) {
            dominanceRatio = Double.POSITIVE_INFINITY;
        } else {
            final double bestExcess = best.getIntegratedInwardExcess();
            final double secondExcess = valid.get(1).getIntegratedInwardExcess();
            if (Math.abs(secondExcess) <= SCALE_EPSILON) {
                dominanceRatio = Double.POSITIVE_INFINITY;
            } else {
                dominanceRatio = bestExcess / secondExcess;
            }
        }

        if (valid.size() > 1 && dominanceRatio < MIN_DOMINANCE_RATIO) {
            return new InwardOutlierDetectionResult(
                    profile,
                    robustScale,
                    coreThreshold,
                    expansionThreshold,
                    candidates,
                    null,
                    false,
                    InwardOutlierDetectionResult.MULTIPLE_COMPETING_INTERVALS,
                    dominanceRatio);
        }

        return new InwardOutlierDetectionResult(
                profile,
                robustScale,
                coreThreshold,
                expansionThreshold,
                candidates,
                best,
                true,
                InwardOutlierDetectionResult.SELECTED_DOMINANT_INWARD_INTERVAL,
                dominanceRatio);
    }

    private static void sortCandidates(final List<InwardOutlierInterval> candidates) {
        Collections.sort(candidates, new Comparator<InwardOutlierInterval>() {
            @Override
            public int compare(final InwardOutlierInterval a, final InwardOutlierInterval b) {
                final int excessCmp = Double.compare(
                        b.getIntegratedInwardExcess(), a.getIntegratedInwardExcess());
                if (excessCmp != 0) {
                    return excessCmp;
                }
                // More negative minimum residual first.
                final int minCmp = Double.compare(a.getMinimumResidual(), b.getMinimumResidual());
                if (minCmp != 0) {
                    return minCmp;
                }
                final int coreCmp = Integer.compare(b.getCorePointCount(), a.getCorePointCount());
                if (coreCmp != 0) {
                    return coreCmp;
                }
                final int fracCmp = Double.compare(
                        a.getBoundaryFraction(), b.getBoundaryFraction());
                if (fracCmp != 0) {
                    return fracCmp;
                }
                final int startCmp = Integer.compare(a.getStartIndex(), b.getStartIndex());
                if (startCmp != 0) {
                    return startCmp;
                }
                return Integer.compare(a.getEndIndex(), b.getEndIndex());
            }
        });
    }

    /**
     * Returns list of int[]{start, end, wrapsFlag} for maximal circular expansion runs.
     */
    private static List<int[]> findCircularExpansionRuns(
            final double[] residuals,
            final double expansionThreshold) {
        final int n = residuals.length;
        final boolean[] expansion = new boolean[n];
        for (int i = 0; i < n; i++) {
            expansion[i] = residuals[i] <= expansionThreshold;
        }

        final List<int[]> linearRuns = new ArrayList<int[]>();
        int i = 0;
        while (i < n) {
            if (!expansion[i]) {
                i++;
                continue;
            }
            final int start = i;
            while (i < n && expansion[i]) {
                i++;
            }
            final int end = i - 1;
            linearRuns.add(new int[] {start, end, 0});
        }

        if (linearRuns.isEmpty()) {
            return linearRuns;
        }

        // Merge first and last when both touch the circular join and both are expansion.
        if (linearRuns.size() >= 2 && expansion[0] && expansion[n - 1]) {
            final int[] first = linearRuns.get(0);
            final int[] last = linearRuns.get(linearRuns.size() - 1);
            if (first[0] == 0 && last[1] == n - 1) {
                final List<int[]> merged = new ArrayList<int[]>();
                merged.add(new int[] {last[0], first[1], 1});
                for (int r = 1; r < linearRuns.size() - 1; r++) {
                    merged.add(linearRuns.get(r));
                }
                return merged;
            }
        }

        // Entire circle is one expansion run.
        if (linearRuns.size() == 1 && linearRuns.get(0)[0] == 0 && linearRuns.get(0)[1] == n - 1) {
            // Non-wrapping full coverage represented as start=0,end=n-1,wraps=false.
            return linearRuns;
        }

        return linearRuns;
    }

    private static boolean runContainsCorePoint(
            final double[] residuals,
            final int startIndex,
            final int endIndex,
            final boolean wrapsAround,
            final double coreThreshold) {
        final int n = residuals.length;
        if (wrapsAround) {
            for (int i = startIndex; i < n; i++) {
                if (residuals[i] <= coreThreshold) {
                    return true;
                }
            }
            for (int i = 0; i <= endIndex; i++) {
                if (residuals[i] <= coreThreshold) {
                    return true;
                }
            }
            return false;
        }
        for (int i = startIndex; i <= endIndex; i++) {
            if (residuals[i] <= coreThreshold) {
                return true;
            }
        }
        return false;
    }

    private static InwardOutlierInterval buildInterval(
            final double[] residuals,
            final Point2D.Double[] midpoints,
            final int startIndex,
            final int endIndex,
            final boolean wrapsAround,
            final double coreThreshold,
            final double expansionThreshold,
            final int globalMinimumIndex) {
        final int n = residuals.length;
        final List<Integer> indices = new ArrayList<Integer>();
        if (wrapsAround) {
            for (int i = startIndex; i < n; i++) {
                indices.add(Integer.valueOf(i));
            }
            for (int i = 0; i <= endIndex; i++) {
                indices.add(Integer.valueOf(i));
            }
        } else {
            for (int i = startIndex; i <= endIndex; i++) {
                indices.add(Integer.valueOf(i));
            }
        }

        final int edgeCount = indices.size();
        double sum = 0.0;
        double minResidual = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        int corePoints = 0;
        double excess = 0.0;
        boolean containsGlobal = false;

        for (int k = 0; k < indices.size(); k++) {
            final int idx = indices.get(k).intValue();
            final double residual = residuals[idx];
            sum += residual;
            if (residual < minResidual) {
                minResidual = residual;
                minIndex = idx;
            }
            if (residual <= coreThreshold) {
                corePoints++;
            }
            excess += Math.max(0.0, expansionThreshold - residual);
            if (idx == globalMinimumIndex) {
                containsGlobal = true;
            }
        }

        final int first = indices.get(0).intValue();
        final int last = indices.get(indices.size() - 1).intValue();
        return new InwardOutlierInterval(
                first,
                last,
                wrapsAround,
                edgeCount,
                edgeCount / (double) n,
                corePoints,
                minResidual,
                minIndex,
                sum / edgeCount,
                excess,
                containsGlobal,
                midpoints[first],
                midpoints[last],
                indices);
    }

    private static OrderedBoundaryLoop buildSyntheticLoop(final int edgeCount) {
        // Even perimeter rectangle: 2*(w+h)=edgeCount.
        final int half = edgeCount / 2;
        int width = Math.max(1, half / 2);
        int height = half - width;
        if (height < 1) {
            height = 1;
            width = half - height;
        }
        final List<Point> pixels = new ArrayList<Point>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.add(new Point(x, y));
            }
        }
        final OrderedBoundaryLoop loop = new OrderedBoundaryExtractor().selectOuterLoop(pixels);
        if (loop.getDirectedEdges().size() != edgeCount) {
            // Fallback: single-row of length L gives perimeter 2*(L+1).
            final int length = edgeCount / 2 - 1;
            final List<Point> row = new ArrayList<Point>();
            for (int x = 0; x < length; x++) {
                row.add(new Point(x, 0));
            }
            final OrderedBoundaryLoop rowLoop =
                    new OrderedBoundaryExtractor().selectOuterLoop(row);
            if (rowLoop.getDirectedEdges().size() != edgeCount) {
                throw new IllegalStateException(
                        "Unable to synthesize loop with edgeCount=" + edgeCount
                                + " (got " + rowLoop.getDirectedEdges().size() + ")");
            }
            return rowLoop;
        }
        return loop;
    }

    private static EllipseModel syntheticEllipseForLoop(final OrderedBoundaryLoop loop) {
        final java.awt.Rectangle bounds = loop.getLatticeBounds();
        final double cx = bounds.getCenterX();
        final double cy = bounds.getCenterY();
        final double a = Math.max(1.0, bounds.getWidth());
        final double b = Math.max(1.0, bounds.getHeight());
        return new EllipseModel(cx, cy, a, b, 0.0);
    }

    private static double medianOf(final double[] values) {
        final double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        final int n = sorted.length;
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
