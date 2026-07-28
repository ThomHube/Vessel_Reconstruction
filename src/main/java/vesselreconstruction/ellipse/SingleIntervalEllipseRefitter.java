package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs exactly one diagnostic free-ellipse refit after excluding a selected
 * inward-outlier interval from outer-boundary support points.
 *
 * <p>Does not iterate exclusion, detect a second interval, accept the ellipse,
 * or modify image pixels.
 */
public final class SingleIntervalEllipseRefitter {

    public static final String NO_SELECTED_INWARD_INTERVAL = "NO_SELECTED_INWARD_INTERVAL";

    private static final int MIN_SUPPORT_POINTS = 6;
    private static final double PI = Math.PI;

    private final FreeEllipseFitter fitter;

    public SingleIntervalEllipseRefitter() {
        this.fitter = new FreeEllipseFitter();
    }

    /**
     * Fits an outer-boundary baseline ellipse and one interval-excluded refit.
     */
    public SingleIntervalEllipseRefitResult refit(final InwardOutlierDetectionResult detectionResult) {
        if (detectionResult == null) {
            throw new IllegalArgumentException("detectionResult must not be null");
        }
        if (!detectionResult.isIntervalSelected() || detectionResult.getSelectedInterval() == null) {
            throw new IllegalArgumentException(NO_SELECTED_INWARD_INTERVAL);
        }

        final InwardOutlierInterval selected = detectionResult.getSelectedInterval();
        boolean found = false;
        final List<InwardOutlierInterval> candidates = detectionResult.getCandidateIntervals();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).equals(selected)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "selectedInterval must belong to detectionResult candidate intervals");
        }

        final OrderedBoundaryResidualProfile profile = detectionResult.getResidualProfile();
        if (profile == null) {
            throw new IllegalArgumentException("residualProfile must not be null");
        }
        final OrderedBoundaryLoop loop = profile.getBoundaryLoop();
        if (loop == null) {
            throw new IllegalArgumentException("ordered boundary loop must not be null");
        }

        final List<Point2D.Double> allPoints = loop.getOrderedSupportMidpoints();
        if (allPoints.size() < MIN_SUPPORT_POINTS) {
            throw new IllegalArgumentException(
                    "ordered boundary loop must contain at least six support points");
        }

        final List<Integer> excludedIndices = selected.getOrderedBoundaryIndices();
        validateExcludedIndices(excludedIndices, allPoints.size());
        if (excludedIndices.size() != selected.getEdgeCount()) {
            throw new IllegalArgumentException(
                    "excluded count must equal selectedInterval.edgeCount");
        }

        return refitCore(allPoints, excludedIndices, selected);
    }

    /**
     * Package-visible test hook using the same baseline, exclusion, refit and metric logic.
     */
    SingleIntervalEllipseRefitResult refitSupportPointsForTesting(
            final List<Point2D.Double> orderedSupportPoints,
            final List<Integer> excludedBoundaryIndices) {
        if (orderedSupportPoints == null || excludedBoundaryIndices == null) {
            throw new IllegalArgumentException("Support points and excluded indices must not be null");
        }
        if (orderedSupportPoints.size() < MIN_SUPPORT_POINTS) {
            throw new IllegalArgumentException(
                    "At least six total support points are required");
        }
        if (excludedBoundaryIndices.isEmpty()) {
            throw new IllegalArgumentException("At least one excluded index is required");
        }

        final List<Point2D.Double> allPoints = copyPointsImmutable(orderedSupportPoints);
        validateExcludedIndices(excludedBoundaryIndices, allPoints.size());

        final InwardOutlierInterval diagnosticInterval =
                createDiagnosticInterval(allPoints, excludedBoundaryIndices);
        return refitCore(allPoints, excludedBoundaryIndices, diagnosticInterval);
    }

    private SingleIntervalEllipseRefitResult refitCore(
            final List<Point2D.Double> allPoints,
            final List<Integer> excludedBoundaryIndices,
            final InwardOutlierInterval excludedInterval) {
        final int n = allPoints.size();
        final boolean[] excludedFlags = new boolean[n];
        for (int i = 0; i < excludedBoundaryIndices.size(); i++) {
            excludedFlags[excludedBoundaryIndices.get(i).intValue()] = true;
        }

        final List<Point2D.Double> retained = new ArrayList<Point2D.Double>();
        for (int i = 0; i < n; i++) {
            if (!excludedFlags[i]) {
                retained.add(new Point2D.Double(allPoints.get(i).x, allPoints.get(i).y));
            }
        }

        final List<Point2D.Double> excluded = new ArrayList<Point2D.Double>();
        for (int i = 0; i < excludedBoundaryIndices.size(); i++) {
            final int index = excludedBoundaryIndices.get(i).intValue();
            final Point2D.Double point = allPoints.get(index);
            excluded.add(new Point2D.Double(point.x, point.y));
        }

        if (retained.size() + excluded.size() != n) {
            throw new IllegalArgumentException(
                    "retained + excluded must equal total outer-boundary count");
        }
        if (excluded.size() != excludedInterval.getEdgeCount()) {
            throw new IllegalArgumentException(
                    "excluded count must equal selectedInterval.edgeCount");
        }

        final int distinctRetained = countDistinctCoordinates(retained);
        if (distinctRetained < MIN_SUPPORT_POINTS) {
            throw new IllegalArgumentException(
                    "At least six retained distinct support points are required; got "
                            + distinctRetained);
        }

        // Exactly one baseline fit and one interval-excluded fit.
        final EllipseFitResult baselineFit = fitter.fit(allPoints);
        final EllipseFitResult excludedFit = fitter.fit(retained);

        final EllipseModel baseline = baselineFit.getFittedEllipse();
        final EllipseModel refitted = excludedFit.getFittedEllipse();

        final double centerShift = Math.hypot(
                refitted.getCenterX() - baseline.getCenterX(),
                refitted.getCenterY() - baseline.getCenterY());

        final double majorAbs = refitted.getSemiMajorAxis() - baseline.getSemiMajorAxis();
        final double majorRel = Math.abs(majorAbs) / baseline.getSemiMajorAxis();
        final double minorAbs = refitted.getSemiMinorAxis() - baseline.getSemiMinorAxis();
        final double minorRel = Math.abs(minorAbs) / baseline.getSemiMinorAxis();
        final double ratioAbs = refitted.getAxisRatio() - baseline.getAxisRatio();

        final double rotationChangeRadians = smallestRotationDifferenceRadians(
                baseline.getRotationRadians(), refitted.getRotationRadians());
        final double rotationChangeDegrees = Math.toDegrees(rotationChangeRadians);

        final double areaAbs = refitted.getArea() - baseline.getArea();
        final double areaRel = Math.abs(areaAbs) / baseline.getArea();

        final double baselineAllRms = rmsResidual(baseline, allPoints);
        final double refittedAllRms = rmsResidual(refitted, allPoints);
        final double baselineRetainedRms = rmsResidual(baseline, retained);
        final double refittedRetainedRms = rmsResidual(refitted, retained);
        final double baselineExcludedRms = rmsResidual(baseline, excluded);
        final double refittedExcludedRms = rmsResidual(refitted, excluded);

        final double baselineExcludedMean = meanResidual(baseline, excluded);
        final double refittedExcludedMean = meanResidual(refitted, excluded);
        final double baselineExcludedMin = minimumResidual(baseline, excluded);
        final double refittedExcludedMin = minimumResidual(refitted, excluded);

        final double retainedFraction = retained.size() / (double) n;
        final double excludedFraction = excluded.size() / (double) n;

        return new SingleIntervalEllipseRefitResult(
                baselineFit,
                excludedFit,
                excludedInterval,
                allPoints,
                retained,
                excluded,
                n,
                retained.size(),
                excluded.size(),
                retainedFraction,
                excludedFraction,
                centerShift,
                majorAbs,
                majorRel,
                minorAbs,
                minorRel,
                ratioAbs,
                rotationChangeRadians,
                rotationChangeDegrees,
                areaAbs,
                areaRel,
                baselineAllRms,
                refittedAllRms,
                baselineRetainedRms,
                refittedRetainedRms,
                baselineExcludedRms,
                refittedExcludedRms,
                baselineExcludedMean,
                refittedExcludedMean,
                baselineExcludedMin,
                refittedExcludedMin);
    }

    private static void validateExcludedIndices(
            final List<Integer> excludedBoundaryIndices,
            final int totalCount) {
        final Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < excludedBoundaryIndices.size(); i++) {
            final Integer value = excludedBoundaryIndices.get(i);
            if (value == null) {
                throw new IllegalArgumentException("Excluded indices must not contain null");
            }
            final int index = value.intValue();
            if (index < 0 || index >= totalCount) {
                throw new IllegalArgumentException(
                        "Excluded index out of range: " + index + " for size " + totalCount);
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        "Duplicate excluded indices are not allowed: " + index);
            }
        }
    }

    private static List<Point2D.Double> copyPointsImmutable(final List<Point2D.Double> source) {
        final List<Point2D.Double> copy = new ArrayList<Point2D.Double>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point2D.Double point = source.get(i);
            if (point == null) {
                throw new IllegalArgumentException(
                        "Support points must not contain null entries at index " + i);
            }
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                throw new IllegalArgumentException(
                        "Support points must be finite at index " + i);
            }
            copy.add(new Point2D.Double(point.x, point.y));
        }
        return copy;
    }

    private static int countDistinctCoordinates(final List<Point2D.Double> points) {
        final Set<String> keys = new HashSet<String>();
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double point = points.get(i);
            keys.add(Double.doubleToLongBits(point.x) + ":" + Double.doubleToLongBits(point.y));
        }
        return keys.size();
    }

    static double smallestRotationDifferenceRadians(
            final double baselineRadians,
            final double refittedRadians) {
        double delta = Math.abs(refittedRadians - baselineRadians);
        delta = delta % PI;
        if (delta < 0.0) {
            delta += PI;
        }
        if (delta > PI / 2.0) {
            delta = PI - delta;
        }
        return delta;
    }

    private static double rmsResidual(
            final EllipseModel ellipse,
            final List<Point2D.Double> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute RMS for empty point list");
        }
        double sumSq = 0.0;
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double point = points.get(i);
            final double residual = ellipse.normalizedRadialResidual(point.x, point.y);
            sumSq += residual * residual;
        }
        final double rms = Math.sqrt(sumSq / points.size());
        if (!Double.isFinite(rms)) {
            throw new IllegalStateException("Non-finite RMS residual");
        }
        return rms;
    }

    private static double meanResidual(
            final EllipseModel ellipse,
            final List<Point2D.Double> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute mean residual for empty point list");
        }
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double point = points.get(i);
            sum += ellipse.normalizedRadialResidual(point.x, point.y);
        }
        final double mean = sum / points.size();
        if (!Double.isFinite(mean)) {
            throw new IllegalStateException("Non-finite mean residual");
        }
        return mean;
    }

    private static double minimumResidual(
            final EllipseModel ellipse,
            final List<Point2D.Double> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot compute minimum residual for empty point list");
        }
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double point = points.get(i);
            final double residual = ellipse.normalizedRadialResidual(point.x, point.y);
            if (residual < min) {
                min = residual;
            }
        }
        if (!Double.isFinite(min)) {
            throw new IllegalStateException("Non-finite minimum residual");
        }
        return min;
    }

    private static InwardOutlierInterval createDiagnosticInterval(
            final List<Point2D.Double> allPoints,
            final List<Integer> excludedBoundaryIndices) {
        final List<Integer> ordered = new ArrayList<Integer>(excludedBoundaryIndices.size());
        for (int i = 0; i < excludedBoundaryIndices.size(); i++) {
            ordered.add(Integer.valueOf(excludedBoundaryIndices.get(i).intValue()));
        }

        final int startIndex = ordered.get(0).intValue();
        final int endIndex = ordered.get(ordered.size() - 1).intValue();
        boolean wrapsAround = false;
        for (int i = 1; i < ordered.size(); i++) {
            final int prev = ordered.get(i - 1).intValue();
            final int curr = ordered.get(i).intValue();
            if (curr != prev + 1) {
                wrapsAround = true;
                break;
            }
        }
        if (!wrapsAround && startIndex > endIndex) {
            wrapsAround = true;
        }

        double minResidual = -1.0;
        int minIndex = startIndex;
        final Point2D.Double start = allPoints.get(startIndex);
        final Point2D.Double end = allPoints.get(endIndex);
        return new InwardOutlierInterval(
                startIndex,
                endIndex,
                wrapsAround,
                ordered.size(),
                ordered.size() / (double) allPoints.size(),
                Math.max(1, ordered.size()),
                minResidual,
                minIndex,
                minResidual,
                1.0,
                true,
                start,
                end,
                ordered);
    }
}
