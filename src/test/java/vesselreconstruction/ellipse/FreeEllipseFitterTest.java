package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mathematical tests for the free-ellipse model and deterministic fitter.
 * Uses only Java 8 APIs.
 */
class FreeEllipseFitterTest {

    private static final double CENTER_TOL = 1.0e-6;
    private static final double AXIS_TOL = 1.0e-6;
    private static final double ANGLE_TOL = 1.0e-6;
    private static final double RMS_TOL = 1.0e-8;
    private static final double GEOM_TOL = 1.0e-9;
    private static final int SAMPLE_COUNT = 180;

    private final FreeEllipseFitter fitter = new FreeEllipseFitter();

    @Test
    void ellipseModelRejectsNonPositiveAxes() {
        assertThrows(IllegalArgumentException.class,
                () -> new EllipseModel(0, 0, 0.0, 2.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EllipseModel(0, 0, 2.0, -1.0, 0.0));
    }

    @Test
    void ellipseModelRejectsNonFiniteParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new EllipseModel(Double.NaN, 0, 2, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EllipseModel(0, Double.POSITIVE_INFINITY, 2, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EllipseModel(0, 0, 2, 1, Double.NEGATIVE_INFINITY));
    }

    @Test
    void ellipseModelStoresMajorGreaterOrEqualMinor() {
        final EllipseModel model = new EllipseModel(1, 2, 3.0, 5.0, 0.1);
        assertTrue(model.getSemiMajorAxis() >= model.getSemiMinorAxis());
        assertEquals(5.0, model.getSemiMajorAxis(), 0.0);
        assertEquals(3.0, model.getSemiMinorAxis(), 0.0);
    }

    @Test
    void swappingAxesProducesEquivalentGeometryWithHalfPiRotation() {
        final double theta = 0.25;
        final EllipseModel swapped = new EllipseModel(0, 0, 3.0, 5.0, theta);
        final EllipseModel equivalent = new EllipseModel(0, 0, 5.0, 3.0, theta + Math.PI / 2.0);
        assertEquals(5.0, swapped.getSemiMajorAxis(), 0.0);
        assertEquals(3.0, swapped.getSemiMinorAxis(), 0.0);
        assertEquals(equivalent.getSemiMajorAxis(), swapped.getSemiMajorAxis(), 0.0);
        assertEquals(equivalent.getSemiMinorAxis(), swapped.getSemiMinorAxis(), 0.0);
        assertEquals(equivalent.getRotationRadians(), swapped.getRotationRadians(), 1.0e-15);
        for (int i = 0; i < 16; i++) {
            final double t = i * Math.PI / 8.0;
            final Point2D.Double p = equivalent.pointAt(t);
            assertEquals(1.0, swapped.normalizedRadius(p.x, p.y), 1.0e-12);
        }
    }

    @Test
    void rotationNormalizedToHalfOpenPi() {
        final EllipseModel model = new EllipseModel(0, 0, 4, 2, -0.1);
        assertTrue(model.getRotationRadians() >= 0.0);
        assertTrue(model.getRotationRadians() < Math.PI);

        final EllipseModel wrapped = new EllipseModel(0, 0, 4, 2, Math.PI + 0.3);
        assertTrue(wrapped.getRotationRadians() >= 0.0);
        assertTrue(wrapped.getRotationRadians() < Math.PI);
        assertEquals(0.3, wrapped.getRotationRadians(), 1.0e-12);
    }

    @Test
    void pointAtHasUnitNormalizedRadius() {
        final EllipseModel model = new EllipseModel(10, -4, 7.0, 2.5, 0.7);
        for (int i = 0; i < 36; i++) {
            final Point2D.Double p = model.pointAt(i * Math.PI / 18.0);
            assertEquals(1.0, model.normalizedRadius(p.x, p.y), GEOM_TOL);
        }
    }

    @Test
    void residualNegativeInsideEllipse() {
        final EllipseModel model = new EllipseModel(0, 0, 5, 3, 0.2);
        final Point2D.Double on = model.pointAt(0.4);
        final double inwardX = model.getCenterX() + 0.5 * (on.x - model.getCenterX());
        final double inwardY = model.getCenterY() + 0.5 * (on.y - model.getCenterY());
        assertTrue(model.normalizedRadialResidual(inwardX, inwardY) < 0.0);
    }

    @Test
    void residualPositiveOutsideEllipse() {
        final EllipseModel model = new EllipseModel(0, 0, 5, 3, 0.2);
        final Point2D.Double on = model.pointAt(1.1);
        final double outwardX = model.getCenterX() + 1.5 * (on.x - model.getCenterX());
        final double outwardY = model.getCenterY() + 1.5 * (on.y - model.getCenterY());
        assertTrue(model.normalizedRadialResidual(outwardX, outwardY) > 0.0);
    }

    @Test
    void areaIsPiAb() {
        final EllipseModel model = new EllipseModel(0, 0, 4.0, 2.5, 0.0);
        assertEquals(Math.PI * 4.0 * 2.5, model.getArea(), 0.0);
    }

    @Test
    void axisRatioIsAOverB() {
        final EllipseModel model = new EllipseModel(0, 0, 6.0, 2.0, 0.0);
        assertEquals(3.0, model.getAxisRatio(), 0.0);
    }

    @Test
    void fitPerfectCenteredCircle() {
        final EllipseModel truth = new EllipseModel(0, 0, 5, 5, 0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertEllipseAxes(result.getFittedEllipse(), 5, 5);
        assertEquals(0.0, result.getFittedEllipse().getCenterX(), CENTER_TOL);
        assertEquals(0.0, result.getFittedEllipse().getCenterY(), CENTER_TOL);
        assertTrue(result.getRootMeanSquaredResidual() < RMS_TOL);
    }

    @Test
    void fitTranslatedCircle() {
        final EllipseModel truth = new EllipseModel(12.5, -8.25, 3.5, 3.5, 0.0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertEquals(12.5, result.getFittedEllipse().getCenterX(), CENTER_TOL);
        assertEquals(-8.25, result.getFittedEllipse().getCenterY(), CENTER_TOL);
        assertEllipseAxes(result.getFittedEllipse(), 3.5, 3.5);
        assertTrue(result.getRootMeanSquaredResidual() < RMS_TOL);
    }

    @Test
    void fitPerfectAxisAlignedEllipse() {
        final EllipseModel truth = new EllipseModel(0, 0, 8.0, 3.0, 0.0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertRecoveredEllipse(result, truth, true);
    }

    @Test
    void fitTranslatedAxisAlignedEllipse() {
        final EllipseModel truth = new EllipseModel(15.0, 22.0, 9.0, 4.0, 0.0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertRecoveredEllipse(result, truth, true);
    }

    @Test
    void fitRotatedEllipseRecoversOrientationModuloPi() {
        final EllipseModel truth = new EllipseModel(3.0, -2.0, 7.0, 2.5, 0.8);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertRecoveredEllipse(result, truth, true);
    }

    @Test
    void nearEqualAxesAreNotForcedEqual() {
        final EllipseModel truth = new EllipseModel(1.0, 1.0, 5.05, 5.00, 0.3);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        final EllipseModel fitted = result.getFittedEllipse();
        assertTrue(Math.abs(fitted.getSemiMajorAxis() - fitted.getSemiMinorAxis()) > 1.0e-4);
        assertEquals(truth.getSemiMajorAxis(), fitted.getSemiMajorAxis(), 1.0e-4);
        assertEquals(truth.getSemiMinorAxis(), fitted.getSemiMinorAxis(), 1.0e-4);
    }

    @Test
    void elongatedEllipseIsNotForcedTowardCircle() {
        final EllipseModel truth = new EllipseModel(0, 0, 12.0, 2.0, 0.4);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        final EllipseModel fitted = result.getFittedEllipse();
        assertEquals(truth.getAxisRatio(), fitted.getAxisRatio(), 1.0e-5);
        assertTrue(fitted.getAxisRatio() > 5.0);
    }

    @Test
    void fitIsDeterministicAcrossRepeatedRuns() {
        final EllipseModel truth = new EllipseModel(4, 5, 6, 2, 1.1);
        final List<Point2D.Double> points = samplePoints(truth, SAMPLE_COUNT);
        final EllipseFitResult first = fitter.fit(points);
        final EllipseFitResult second = fitter.fit(points);
        assertEquals(first, second);
    }

    @Test
    void reversingInputOrderPreservesEquivalentGeometry() {
        final EllipseModel truth = new EllipseModel(-3, 7, 6.5, 2.2, 0.55);
        final List<Point2D.Double> points = samplePoints(truth, SAMPLE_COUNT);
        final List<Point2D.Double> reversed = new ArrayList<Point2D.Double>(points);
        Collections.reverse(reversed);
        final EllipseFitResult forward = fitter.fit(points);
        final EllipseFitResult backward = fitter.fit(reversed);
        assertEquivalentGeometry(forward.getFittedEllipse(), backward.getFittedEllipse());
    }

    @Test
    void cyclicShiftPreservesEquivalentGeometry() {
        final EllipseModel truth = new EllipseModel(2, 3, 5, 3, 0.9);
        final List<Point2D.Double> points = samplePoints(truth, SAMPLE_COUNT);
        final List<Point2D.Double> shifted = new ArrayList<Point2D.Double>();
        shifted.addAll(points.subList(17, points.size()));
        shifted.addAll(points.subList(0, 17));
        final EllipseFitResult original = fitter.fit(points);
        final EllipseFitResult cycled = fitter.fit(shifted);
        assertEquivalentGeometry(original.getFittedEllipse(), cycled.getFittedEllipse());
    }

    @Test
    void inputListRemainsUnchanged() {
        final EllipseModel truth = new EllipseModel(0, 0, 4, 2, 0.1);
        final List<Point2D.Double> points = samplePoints(truth, SAMPLE_COUNT);
        final List<Point2D.Double> snapshot = copyPoints(points);
        fitter.fit(points);
        assertEquals(snapshot.size(), points.size());
        for (int i = 0; i < points.size(); i++) {
            assertEquals(snapshot.get(i).x, points.get(i).x, 0.0);
            assertEquals(snapshot.get(i).y, points.get(i).y, 0.0);
        }
    }

    @Test
    void inputPointObjectsRemainUnchanged() {
        final EllipseModel truth = new EllipseModel(0, 0, 4, 2, 0.2);
        final List<Point2D.Double> points = samplePoints(truth, 64);
        final double[] xs = new double[points.size()];
        final double[] ys = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).x;
            ys[i] = points.get(i).y;
        }
        fitter.fit(points);
        for (int i = 0; i < points.size(); i++) {
            assertEquals(xs[i], points.get(i).x, 0.0);
            assertEquals(ys[i], points.get(i).y, 0.0);
        }
    }

    @Test
    void residualListPreservesCountAndOrder() {
        final EllipseModel truth = new EllipseModel(1, 2, 5, 3, 0.3);
        final List<Point2D.Double> points = samplePoints(truth, 48);
        final EllipseFitResult result = fitter.fit(points);
        assertEquals(points.size(), result.getResiduals().size());
        for (int i = 0; i < points.size(); i++) {
            final double expected = result.getFittedEllipse()
                    .normalizedRadialResidual(points.get(i).x, points.get(i).y);
            assertEquals(expected, result.getResiduals().get(i).doubleValue(), 0.0);
        }
    }

    @Test
    void residualListIsImmutable() {
        final EllipseModel truth = new EllipseModel(0, 0, 4, 2, 0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, 36));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getResiduals().add(Double.valueOf(0.0)));
    }

    @Test
    void perfectSyntheticDataHasVerySmallRms() {
        final EllipseModel truth = new EllipseModel(5, -3, 8, 3, 1.0);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertTrue(result.getRootMeanSquaredResidual() < RMS_TOL);
    }

    @Test
    void finalObjectiveNotGreaterThanInitial() {
        final EllipseModel truth = new EllipseModel(0, 0, 6, 2.5, 0.6);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertTrue(result.getFinalMeanSquaredResidual()
                <= result.getInitialMeanSquaredResidual() + 1.0e-18);
    }

    @Test
    void iterationCountWithinBounds() {
        final EllipseModel truth = new EllipseModel(0, 0, 5, 3, 0.2);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, SAMPLE_COUNT));
        assertTrue(result.getIterationCount() >= 0);
        assertTrue(result.getIterationCount() <= FreeEllipseFitter.MAX_ITERATIONS);
    }

    @Test
    void nullInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(null));
    }

    @Test
    void fewerThanSixPointsRejected() {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        for (int i = 0; i < 5; i++) {
            points.add(new Point2D.Double(i, i * i));
        }
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(points));
    }

    @Test
    void nullPointsRejected() {
        final List<Point2D.Double> points = samplePoints(new EllipseModel(0, 0, 4, 2, 0), 12);
        points.set(3, null);
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(points));
    }

    @Test
    void nonFiniteCoordinatesRejected() {
        final List<Point2D.Double> points = samplePoints(new EllipseModel(0, 0, 4, 2, 0), 12);
        points.set(2, new Point2D.Double(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(points));
    }

    @Test
    void fewerThanSixDistinctPairsRejected() {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        for (int i = 0; i < 10; i++) {
            points.add(new Point2D.Double(i % 5, (i % 5) * 2.0));
        }
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(points));
    }

    @Test
    void collinearPointsRejected() {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        for (int i = 0; i < 12; i++) {
            points.add(new Point2D.Double(i, 2.0 * i + 1.0));
        }
        assertThrows(IllegalArgumentException.class, () -> fitter.fit(points));
    }

    @Test
    void repeatedCallsDoNotShareMutableState() {
        final FreeEllipseFitter local = new FreeEllipseFitter();
        final EllipseModel a = new EllipseModel(0, 0, 5, 2, 0.1);
        final EllipseModel b = new EllipseModel(10, 10, 8, 3, 1.0);
        final EllipseFitResult first = local.fit(samplePoints(a, SAMPLE_COUNT));
        final EllipseFitResult second = local.fit(samplePoints(b, SAMPLE_COUNT));
        assertNotEquals(first.getFittedEllipse(), second.getFittedEllipse());
        assertRecoveredEllipse(first, a, true);
        assertRecoveredEllipse(second, b, true);
    }

    @Test
    void ellipseFitResultIsImmutable() {
        final EllipseModel truth = new EllipseModel(0, 0, 4, 2, 0.5);
        final EllipseFitResult result = fitter.fit(samplePoints(truth, 40));
        final List<Double> residuals = result.getResiduals();
        assertThrows(UnsupportedOperationException.class, () -> residuals.set(0, Double.valueOf(99)));
        assertNotNull(result.getFittedEllipse());
        assertFalse(result.getConvergenceReason().isEmpty());
    }

    @Test
    void mildlyNoisyEllipseRecoversNearbyGeometry() {
        final EllipseModel truth = new EllipseModel(2.0, -1.5, 6.0, 2.5, 0.65);
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(SAMPLE_COUNT);
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            final double t = 2.0 * Math.PI * i / SAMPLE_COUNT;
            final Point2D.Double on = truth.pointAt(t);
            final double multiplier = 1.0 + 0.002 * Math.sin(7.0 * t);
            final double x = truth.getCenterX() + multiplier * (on.x - truth.getCenterX());
            final double y = truth.getCenterY() + multiplier * (on.y - truth.getCenterY());
            points.add(new Point2D.Double(x, y));
        }
        final EllipseFitResult result = fitter.fit(points);
        final EllipseModel fitted = result.getFittedEllipse();
        assertEquals(truth.getCenterX(), fitted.getCenterX(), 5.0e-3);
        assertEquals(truth.getCenterY(), fitted.getCenterY(), 5.0e-3);
        assertEquals(truth.getSemiMajorAxis(), fitted.getSemiMajorAxis(), 5.0e-3);
        assertEquals(truth.getSemiMinorAxis(), fitted.getSemiMinorAxis(), 5.0e-3);
        assertTrue(Double.isFinite(result.getRootMeanSquaredResidual()));
        assertTrue(Double.isFinite(result.getMedianResidual()));
        assertTrue(Double.isFinite(result.getMedianAbsoluteDeviation()));
        assertTrue(Double.isFinite(result.getMaximumInwardResidual()));
        assertTrue(Double.isFinite(result.getMaximumOutwardResidual()));
        assertTrue(Double.isFinite(result.getInitialMeanSquaredResidual()));
        assertTrue(Double.isFinite(result.getFinalMeanSquaredResidual()));
    }

    private static List<Point2D.Double> samplePoints(
            final EllipseModel model,
            final int count) {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(count);
        for (int i = 0; i < count; i++) {
            final double t = 2.0 * Math.PI * i / count;
            final Point2D.Double p = model.pointAt(t);
            points.add(new Point2D.Double(p.x, p.y));
        }
        return points;
    }

    private static List<Point2D.Double> copyPoints(final List<Point2D.Double> points) {
        final List<Point2D.Double> copy = new ArrayList<Point2D.Double>(points.size());
        for (int i = 0; i < points.size(); i++) {
            copy.add(new Point2D.Double(points.get(i).x, points.get(i).y));
        }
        return copy;
    }

    private static void assertEllipseAxes(
            final EllipseModel fitted,
            final double major,
            final double minor) {
        assertEquals(major, fitted.getSemiMajorAxis(), AXIS_TOL);
        assertEquals(minor, fitted.getSemiMinorAxis(), AXIS_TOL);
    }

    private static void assertRecoveredEllipse(
            final EllipseFitResult result,
            final EllipseModel truth,
            final boolean checkOrientation) {
        final EllipseModel fitted = result.getFittedEllipse();
        assertEquals(truth.getCenterX(), fitted.getCenterX(), CENTER_TOL);
        assertEquals(truth.getCenterY(), fitted.getCenterY(), CENTER_TOL);
        assertEquals(truth.getSemiMajorAxis(), fitted.getSemiMajorAxis(), AXIS_TOL);
        assertEquals(truth.getSemiMinorAxis(), fitted.getSemiMinorAxis(), AXIS_TOL);
        if (checkOrientation) {
            assertEquals(0.0, smallestRotationDifference(
                    fitted.getRotationRadians() - truth.getRotationRadians()), ANGLE_TOL);
        }
        assertTrue(result.getRootMeanSquaredResidual() < RMS_TOL);
    }

    private static void assertEquivalentGeometry(
            final EllipseModel a,
            final EllipseModel b) {
        assertEquals(a.getCenterX(), b.getCenterX(), CENTER_TOL);
        assertEquals(a.getCenterY(), b.getCenterY(), CENTER_TOL);
        assertEquals(a.getSemiMajorAxis(), b.getSemiMajorAxis(), AXIS_TOL);
        assertEquals(a.getSemiMinorAxis(), b.getSemiMinorAxis(), AXIS_TOL);
        assertEquals(0.0, smallestRotationDifference(
                a.getRotationRadians() - b.getRotationRadians()), ANGLE_TOL);
    }

    private static double smallestRotationDifference(final double delta) {
        double x = delta % Math.PI;
        if (x > Math.PI / 2.0) {
            x -= Math.PI;
        }
        if (x < -Math.PI / 2.0) {
            x += Math.PI;
        }
        return Math.abs(x);
    }
}
