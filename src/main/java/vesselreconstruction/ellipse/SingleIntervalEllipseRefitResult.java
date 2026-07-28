package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for one single-interval ellipse refit after excluding
 * a selected inward-outlier interval from outer-boundary fit support.
 */
public final class SingleIntervalEllipseRefitResult {

    private static final double FRACTION_SUM_TOLERANCE = 1.0e-12;

    private final EllipseFitResult outerBoundaryBaselineFit;
    private final EllipseFitResult intervalExcludedFit;
    private final InwardOutlierInterval excludedInterval;
    private final List<Point2D.Double> allOuterBoundarySupportPoints;
    private final List<Point2D.Double> retainedSupportPoints;
    private final List<Point2D.Double> excludedSupportPoints;
    private final int totalSupportPointCount;
    private final int retainedSupportPointCount;
    private final int excludedSupportPointCount;
    private final double retainedSupportFraction;
    private final double excludedSupportFraction;
    private final double centerShift;
    private final double semiMajorAxisAbsoluteChange;
    private final double semiMajorAxisRelativeChange;
    private final double semiMinorAxisAbsoluteChange;
    private final double semiMinorAxisRelativeChange;
    private final double axisRatioAbsoluteChange;
    private final double rotationChangeRadians;
    private final double rotationChangeDegrees;
    private final double ellipseAreaAbsoluteChange;
    private final double ellipseAreaRelativeChange;
    private final double baselineAllBoundaryRmsResidual;
    private final double refittedAllBoundaryRmsResidual;
    private final double baselineRetainedRmsResidual;
    private final double refittedRetainedRmsResidual;
    private final double baselineExcludedRmsResidual;
    private final double refittedExcludedRmsResidual;
    private final double baselineExcludedMeanResidual;
    private final double refittedExcludedMeanResidual;
    private final double baselineExcludedMinimumResidual;
    private final double refittedExcludedMinimumResidual;

    public SingleIntervalEllipseRefitResult(
            final EllipseFitResult outerBoundaryBaselineFit,
            final EllipseFitResult intervalExcludedFit,
            final InwardOutlierInterval excludedInterval,
            final List<Point2D.Double> allOuterBoundarySupportPoints,
            final List<Point2D.Double> retainedSupportPoints,
            final List<Point2D.Double> excludedSupportPoints,
            final int totalSupportPointCount,
            final int retainedSupportPointCount,
            final int excludedSupportPointCount,
            final double retainedSupportFraction,
            final double excludedSupportFraction,
            final double centerShift,
            final double semiMajorAxisAbsoluteChange,
            final double semiMajorAxisRelativeChange,
            final double semiMinorAxisAbsoluteChange,
            final double semiMinorAxisRelativeChange,
            final double axisRatioAbsoluteChange,
            final double rotationChangeRadians,
            final double rotationChangeDegrees,
            final double ellipseAreaAbsoluteChange,
            final double ellipseAreaRelativeChange,
            final double baselineAllBoundaryRmsResidual,
            final double refittedAllBoundaryRmsResidual,
            final double baselineRetainedRmsResidual,
            final double refittedRetainedRmsResidual,
            final double baselineExcludedRmsResidual,
            final double refittedExcludedRmsResidual,
            final double baselineExcludedMeanResidual,
            final double refittedExcludedMeanResidual,
            final double baselineExcludedMinimumResidual,
            final double refittedExcludedMinimumResidual) {
        if (outerBoundaryBaselineFit == null || intervalExcludedFit == null) {
            throw new IllegalArgumentException("Both fit results must be non-null");
        }
        if (excludedInterval == null) {
            throw new IllegalArgumentException("excludedInterval must not be null");
        }
        if (allOuterBoundarySupportPoints == null
                || retainedSupportPoints == null
                || excludedSupportPoints == null) {
            throw new IllegalArgumentException("Support point lists must not be null");
        }
        if (totalSupportPointCount != allOuterBoundarySupportPoints.size()) {
            throw new IllegalArgumentException(
                    "totalSupportPointCount must equal allOuterBoundarySupportPoints.size()");
        }
        if (retainedSupportPointCount != retainedSupportPoints.size()) {
            throw new IllegalArgumentException(
                    "retainedSupportPointCount must equal retainedSupportPoints.size()");
        }
        if (excludedSupportPointCount != excludedSupportPoints.size()) {
            throw new IllegalArgumentException(
                    "excludedSupportPointCount must equal excludedSupportPoints.size()");
        }
        if (retainedSupportPointCount + excludedSupportPointCount != totalSupportPointCount) {
            throw new IllegalArgumentException(
                    "retained + excluded counts must equal total count");
        }
        if (retainedSupportPointCount < 6) {
            throw new IllegalArgumentException(
                    "At least six retained support points are required");
        }
        if (Math.abs(retainedSupportFraction + excludedSupportFraction - 1.0)
                > FRACTION_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "retained and excluded fractions must sum to 1 within 1.0e-12");
        }

        requireFinite("retainedSupportFraction", retainedSupportFraction);
        requireFinite("excludedSupportFraction", excludedSupportFraction);
        requireFinite("centerShift", centerShift);
        requireFinite("semiMajorAxisAbsoluteChange", semiMajorAxisAbsoluteChange);
        requireFinite("semiMajorAxisRelativeChange", semiMajorAxisRelativeChange);
        requireFinite("semiMinorAxisAbsoluteChange", semiMinorAxisAbsoluteChange);
        requireFinite("semiMinorAxisRelativeChange", semiMinorAxisRelativeChange);
        requireFinite("axisRatioAbsoluteChange", axisRatioAbsoluteChange);
        requireFinite("rotationChangeRadians", rotationChangeRadians);
        requireFinite("rotationChangeDegrees", rotationChangeDegrees);
        requireFinite("ellipseAreaAbsoluteChange", ellipseAreaAbsoluteChange);
        requireFinite("ellipseAreaRelativeChange", ellipseAreaRelativeChange);
        requireFinite("baselineAllBoundaryRmsResidual", baselineAllBoundaryRmsResidual);
        requireFinite("refittedAllBoundaryRmsResidual", refittedAllBoundaryRmsResidual);
        requireFinite("baselineRetainedRmsResidual", baselineRetainedRmsResidual);
        requireFinite("refittedRetainedRmsResidual", refittedRetainedRmsResidual);
        requireFinite("baselineExcludedRmsResidual", baselineExcludedRmsResidual);
        requireFinite("refittedExcludedRmsResidual", refittedExcludedRmsResidual);
        requireFinite("baselineExcludedMeanResidual", baselineExcludedMeanResidual);
        requireFinite("refittedExcludedMeanResidual", refittedExcludedMeanResidual);
        requireFinite("baselineExcludedMinimumResidual", baselineExcludedMinimumResidual);
        requireFinite("refittedExcludedMinimumResidual", refittedExcludedMinimumResidual);

        this.outerBoundaryBaselineFit = outerBoundaryBaselineFit;
        this.intervalExcludedFit = intervalExcludedFit;
        this.excludedInterval = excludedInterval;
        this.allOuterBoundarySupportPoints = copyPoints(allOuterBoundarySupportPoints);
        this.retainedSupportPoints = copyPoints(retainedSupportPoints);
        this.excludedSupportPoints = copyPoints(excludedSupportPoints);
        this.totalSupportPointCount = totalSupportPointCount;
        this.retainedSupportPointCount = retainedSupportPointCount;
        this.excludedSupportPointCount = excludedSupportPointCount;
        this.retainedSupportFraction = retainedSupportFraction;
        this.excludedSupportFraction = excludedSupportFraction;
        this.centerShift = centerShift;
        this.semiMajorAxisAbsoluteChange = semiMajorAxisAbsoluteChange;
        this.semiMajorAxisRelativeChange = semiMajorAxisRelativeChange;
        this.semiMinorAxisAbsoluteChange = semiMinorAxisAbsoluteChange;
        this.semiMinorAxisRelativeChange = semiMinorAxisRelativeChange;
        this.axisRatioAbsoluteChange = axisRatioAbsoluteChange;
        this.rotationChangeRadians = rotationChangeRadians;
        this.rotationChangeDegrees = rotationChangeDegrees;
        this.ellipseAreaAbsoluteChange = ellipseAreaAbsoluteChange;
        this.ellipseAreaRelativeChange = ellipseAreaRelativeChange;
        this.baselineAllBoundaryRmsResidual = baselineAllBoundaryRmsResidual;
        this.refittedAllBoundaryRmsResidual = refittedAllBoundaryRmsResidual;
        this.baselineRetainedRmsResidual = baselineRetainedRmsResidual;
        this.refittedRetainedRmsResidual = refittedRetainedRmsResidual;
        this.baselineExcludedRmsResidual = baselineExcludedRmsResidual;
        this.refittedExcludedRmsResidual = refittedExcludedRmsResidual;
        this.baselineExcludedMeanResidual = baselineExcludedMeanResidual;
        this.refittedExcludedMeanResidual = refittedExcludedMeanResidual;
        this.baselineExcludedMinimumResidual = baselineExcludedMinimumResidual;
        this.refittedExcludedMinimumResidual = refittedExcludedMinimumResidual;
    }

    private static List<Point2D.Double> copyPoints(final List<Point2D.Double> source) {
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
        return Collections.unmodifiableList(copy);
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static List<Point2D.Double> defensivePointList(final List<Point2D.Double> source) {
        final List<Point2D.Double> copy = new ArrayList<Point2D.Double>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point2D.Double point = source.get(i);
            copy.add(new Point2D.Double(point.x, point.y));
        }
        return Collections.unmodifiableList(copy);
    }

    public EllipseFitResult getOuterBoundaryBaselineFit() {
        return outerBoundaryBaselineFit;
    }

    public EllipseFitResult getIntervalExcludedFit() {
        return intervalExcludedFit;
    }

    public InwardOutlierInterval getExcludedInterval() {
        return excludedInterval;
    }

    public List<Point2D.Double> getAllOuterBoundarySupportPoints() {
        return defensivePointList(allOuterBoundarySupportPoints);
    }

    public List<Point2D.Double> getRetainedSupportPoints() {
        return defensivePointList(retainedSupportPoints);
    }

    public List<Point2D.Double> getExcludedSupportPoints() {
        return defensivePointList(excludedSupportPoints);
    }

    public int getTotalSupportPointCount() {
        return totalSupportPointCount;
    }

    public int getRetainedSupportPointCount() {
        return retainedSupportPointCount;
    }

    public int getExcludedSupportPointCount() {
        return excludedSupportPointCount;
    }

    public double getRetainedSupportFraction() {
        return retainedSupportFraction;
    }

    public double getExcludedSupportFraction() {
        return excludedSupportFraction;
    }

    public double getCenterShift() {
        return centerShift;
    }

    public double getSemiMajorAxisAbsoluteChange() {
        return semiMajorAxisAbsoluteChange;
    }

    public double getSemiMajorAxisRelativeChange() {
        return semiMajorAxisRelativeChange;
    }

    public double getSemiMinorAxisAbsoluteChange() {
        return semiMinorAxisAbsoluteChange;
    }

    public double getSemiMinorAxisRelativeChange() {
        return semiMinorAxisRelativeChange;
    }

    public double getAxisRatioAbsoluteChange() {
        return axisRatioAbsoluteChange;
    }

    public double getRotationChangeRadians() {
        return rotationChangeRadians;
    }

    public double getRotationChangeDegrees() {
        return rotationChangeDegrees;
    }

    public double getEllipseAreaAbsoluteChange() {
        return ellipseAreaAbsoluteChange;
    }

    public double getEllipseAreaRelativeChange() {
        return ellipseAreaRelativeChange;
    }

    public double getBaselineAllBoundaryRmsResidual() {
        return baselineAllBoundaryRmsResidual;
    }

    public double getRefittedAllBoundaryRmsResidual() {
        return refittedAllBoundaryRmsResidual;
    }

    public double getBaselineRetainedRmsResidual() {
        return baselineRetainedRmsResidual;
    }

    public double getRefittedRetainedRmsResidual() {
        return refittedRetainedRmsResidual;
    }

    public double getBaselineExcludedRmsResidual() {
        return baselineExcludedRmsResidual;
    }

    public double getRefittedExcludedRmsResidual() {
        return refittedExcludedRmsResidual;
    }

    public double getBaselineExcludedMeanResidual() {
        return baselineExcludedMeanResidual;
    }

    public double getRefittedExcludedMeanResidual() {
        return refittedExcludedMeanResidual;
    }

    public double getBaselineExcludedMinimumResidual() {
        return baselineExcludedMinimumResidual;
    }

    public double getRefittedExcludedMinimumResidual() {
        return refittedExcludedMinimumResidual;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SingleIntervalEllipseRefitResult)) {
            return false;
        }
        final SingleIntervalEllipseRefitResult other = (SingleIntervalEllipseRefitResult) obj;
        return totalSupportPointCount == other.totalSupportPointCount
                && retainedSupportPointCount == other.retainedSupportPointCount
                && excludedSupportPointCount == other.excludedSupportPointCount
                && Objects.equals(outerBoundaryBaselineFit, other.outerBoundaryBaselineFit)
                && Objects.equals(intervalExcludedFit, other.intervalExcludedFit)
                && Objects.equals(excludedInterval, other.excludedInterval)
                && Objects.equals(allOuterBoundarySupportPoints, other.allOuterBoundarySupportPoints)
                && Objects.equals(retainedSupportPoints, other.retainedSupportPoints)
                && Objects.equals(excludedSupportPoints, other.excludedSupportPoints)
                && doubleEquals(retainedSupportFraction, other.retainedSupportFraction)
                && doubleEquals(excludedSupportFraction, other.excludedSupportFraction)
                && doubleEquals(centerShift, other.centerShift)
                && doubleEquals(semiMajorAxisAbsoluteChange, other.semiMajorAxisAbsoluteChange)
                && doubleEquals(semiMajorAxisRelativeChange, other.semiMajorAxisRelativeChange)
                && doubleEquals(semiMinorAxisAbsoluteChange, other.semiMinorAxisAbsoluteChange)
                && doubleEquals(semiMinorAxisRelativeChange, other.semiMinorAxisRelativeChange)
                && doubleEquals(axisRatioAbsoluteChange, other.axisRatioAbsoluteChange)
                && doubleEquals(rotationChangeRadians, other.rotationChangeRadians)
                && doubleEquals(rotationChangeDegrees, other.rotationChangeDegrees)
                && doubleEquals(ellipseAreaAbsoluteChange, other.ellipseAreaAbsoluteChange)
                && doubleEquals(ellipseAreaRelativeChange, other.ellipseAreaRelativeChange)
                && doubleEquals(baselineAllBoundaryRmsResidual, other.baselineAllBoundaryRmsResidual)
                && doubleEquals(refittedAllBoundaryRmsResidual, other.refittedAllBoundaryRmsResidual)
                && doubleEquals(baselineRetainedRmsResidual, other.baselineRetainedRmsResidual)
                && doubleEquals(refittedRetainedRmsResidual, other.refittedRetainedRmsResidual)
                && doubleEquals(baselineExcludedRmsResidual, other.baselineExcludedRmsResidual)
                && doubleEquals(refittedExcludedRmsResidual, other.refittedExcludedRmsResidual)
                && doubleEquals(baselineExcludedMeanResidual, other.baselineExcludedMeanResidual)
                && doubleEquals(refittedExcludedMeanResidual, other.refittedExcludedMeanResidual)
                && doubleEquals(
                        baselineExcludedMinimumResidual, other.baselineExcludedMinimumResidual)
                && doubleEquals(
                        refittedExcludedMinimumResidual, other.refittedExcludedMinimumResidual);
    }

    private static boolean doubleEquals(final double a, final double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                outerBoundaryBaselineFit,
                intervalExcludedFit,
                excludedInterval,
                allOuterBoundarySupportPoints,
                retainedSupportPoints,
                excludedSupportPoints,
                Integer.valueOf(totalSupportPointCount),
                Integer.valueOf(retainedSupportPointCount),
                Integer.valueOf(excludedSupportPointCount),
                Double.valueOf(retainedSupportFraction),
                Double.valueOf(excludedSupportFraction),
                Double.valueOf(centerShift),
                Double.valueOf(semiMajorAxisAbsoluteChange),
                Double.valueOf(semiMajorAxisRelativeChange),
                Double.valueOf(semiMinorAxisAbsoluteChange),
                Double.valueOf(semiMinorAxisRelativeChange),
                Double.valueOf(axisRatioAbsoluteChange),
                Double.valueOf(rotationChangeRadians),
                Double.valueOf(rotationChangeDegrees),
                Double.valueOf(ellipseAreaAbsoluteChange),
                Double.valueOf(ellipseAreaRelativeChange),
                Double.valueOf(baselineAllBoundaryRmsResidual),
                Double.valueOf(refittedAllBoundaryRmsResidual),
                Double.valueOf(baselineRetainedRmsResidual),
                Double.valueOf(refittedRetainedRmsResidual),
                Double.valueOf(baselineExcludedRmsResidual),
                Double.valueOf(refittedExcludedRmsResidual),
                Double.valueOf(baselineExcludedMeanResidual),
                Double.valueOf(refittedExcludedMeanResidual),
                Double.valueOf(baselineExcludedMinimumResidual),
                Double.valueOf(refittedExcludedMinimumResidual));
    }

    @Override
    public String toString() {
        return "SingleIntervalEllipseRefitResult{total=" + totalSupportPointCount
                + ", retained=" + retainedSupportPointCount
                + ", excluded=" + excludedSupportPointCount
                + ", centerShift=" + centerShift
                + ", rotationChangeDegrees=" + rotationChangeDegrees
                + '}';
    }
}
