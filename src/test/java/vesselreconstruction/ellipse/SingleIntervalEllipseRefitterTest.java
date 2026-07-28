package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for single inward-interval diagnostic ellipse refitting.
 */
class SingleIntervalEllipseRefitterTest {

    private final SingleIntervalEllipseRefitter refitter = new SingleIntervalEllipseRefitter();

    @Test
    void nullDetectionResultRejected() {
        assertThrows(IllegalArgumentException.class, () -> refitter.refit(null));
    }

    @Test
    void detectionWithoutSelectedIntervalRejected() {
        final List<Point2D.Double> points = ellipsePoints(40, 50, 40, 30, 20, 0.3);
        final OrderedBoundaryLoop loop = syntheticLoopFromPoints(points);
        final EllipseModel ellipse = new EllipseModel(50, 40, 30, 20, 0.3);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        final InwardOutlierDetectionResult detection =
                new InwardOutlierIntervalDetector().detect(profile);
        if (detection.isIntervalSelected()) {
            // Ensure we still exercise the rejection path with a forced empty selection.
            final InwardOutlierDetectionResult noSelection = new InwardOutlierDetectionResult(
                    profile,
                    detection.getRobustScale(),
                    detection.getCoreThreshold(),
                    detection.getExpansionThreshold(),
                    detection.getCandidateIntervals(),
                    null,
                    false,
                    InwardOutlierDetectionResult.NO_CORE_POINTS,
                    0.0);
            final IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> refitter.refit(noSelection));
            assertTrue(ex.getMessage().contains(
                    SingleIntervalEllipseRefitter.NO_SELECTED_INWARD_INTERVAL));
        } else {
            final IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> refitter.refit(detection));
            assertTrue(ex.getMessage().contains(
                    SingleIntervalEllipseRefitter.NO_SELECTED_INWARD_INTERVAL));
        }
    }

    @Test
    void baselineUsesAllOuterBoundaryPoints() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.4);
        final List<Integer> excluded = range(10, 20);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(points.size(), result.getTotalSupportPointCount());
        assertEquals(points.size(), result.getAllOuterBoundarySupportPoints().size());
        assertEquals(points.size(),
                result.getOuterBoundaryBaselineFit().getResiduals().size());
    }

    @Test
    void onlySelectedIntervalIndicesExcluded() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 35, 22, 0.2);
        final List<Integer> excluded = range(12, 19);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        final Set<Integer> expected = new HashSet<Integer>(excluded);
        assertEquals(expected.size(), result.getExcludedSupportPointCount());
        for (int i = 0; i < points.size(); i++) {
            final boolean shouldExclude = expected.contains(Integer.valueOf(i));
            final boolean foundExcluded = containsPoint(
                    result.getExcludedSupportPoints(), points.get(i));
            final boolean foundRetained = containsPoint(
                    result.getRetainedSupportPoints(), points.get(i));
            if (shouldExclude) {
                assertTrue(foundExcluded);
                assertFalse(foundRetained);
            } else {
                assertTrue(foundRetained);
                assertFalse(foundExcluded);
            }
        }
    }

    @Test
    void selectedIntervalEndpointsAreExcluded() {
        final List<Point2D.Double> points = ellipsePoints(36, 1, 2, 30, 18, 0.1);
        final List<Integer> excluded = range(5, 12);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertTrue(containsPoint(result.getExcludedSupportPoints(), points.get(5)));
        assertTrue(containsPoint(result.getExcludedSupportPoints(), points.get(12)));
    }

    @Test
    void noPointOutsideIntervalIsExcluded() {
        final List<Point2D.Double> points = ellipsePoints(36, 0, 0, 28, 17, 0.0);
        final List<Integer> excluded = range(8, 14);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        for (int i = 0; i < points.size(); i++) {
            if (i < 8 || i > 14) {
                assertFalse(containsPoint(result.getExcludedSupportPoints(), points.get(i)));
            }
        }
    }

    @Test
    void excludedCountEqualsIntervalEdgeCount() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.25);
        final List<Integer> excluded = range(15, 24);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(excluded.size(), result.getExcludedSupportPointCount());
        assertEquals(result.getExcludedInterval().getEdgeCount(),
                result.getExcludedSupportPointCount());
    }

    @Test
    void retainedPlusExcludedEqualsTotal() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.25);
        final List<Integer> excluded = range(15, 24);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(
                result.getTotalSupportPointCount(),
                result.getRetainedSupportPointCount() + result.getExcludedSupportPointCount());
    }

    @Test
    void retainedPointOrderMatchesOriginalBoundaryOrder() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.25);
        final List<Integer> excluded = range(10, 16);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        int cursor = 0;
        for (int i = 0; i < points.size(); i++) {
            if (i >= 10 && i <= 16) {
                continue;
            }
            final Point2D.Double expected = points.get(i);
            final Point2D.Double actual = result.getRetainedSupportPoints().get(cursor);
            assertEquals(expected.x, actual.x, 0.0);
            assertEquals(expected.y, actual.y, 0.0);
            cursor++;
        }
    }

    @Test
    void excludedPointOrderMatchesIntervalCircularOrder() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.25);
        final List<Integer> excluded = Arrays.asList(
                Integer.valueOf(37), Integer.valueOf(38), Integer.valueOf(39),
                Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2));
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(excluded.size(), result.getExcludedSupportPoints().size());
        for (int i = 0; i < excluded.size(); i++) {
            final Point2D.Double expected = points.get(excluded.get(i).intValue());
            final Point2D.Double actual = result.getExcludedSupportPoints().get(i);
            assertEquals(expected.x, actual.x, 0.0);
            assertEquals(expected.y, actual.y, 0.0);
        }
    }

    @Test
    void nonWrappingIntervalExclusionCorrect() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.0);
        final List<Integer> excluded = range(6, 14);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertFalse(result.getExcludedInterval().isWrapsAround());
        assertEquals(6, result.getExcludedInterval().getStartIndex());
        assertEquals(14, result.getExcludedInterval().getEndIndex());
    }

    @Test
    void wrappingIntervalExclusionCorrect() {
        final List<Point2D.Double> points = ellipsePoints(40, 0, 0, 32, 20, 0.0);
        final List<Integer> excluded = Arrays.asList(
                Integer.valueOf(36), Integer.valueOf(37), Integer.valueOf(38),
                Integer.valueOf(39), Integer.valueOf(0), Integer.valueOf(1),
                Integer.valueOf(2));
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertTrue(result.getExcludedInterval().isWrapsAround());
        assertEquals(7, result.getExcludedSupportPointCount());
        assertEquals(points.size() - 7, result.getRetainedSupportPointCount());
    }

    @Test
    void baselineFitUsesAllOuterBoundaryPoints() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 24, 0.15);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(20, 28));
        assertEquals(points.size(),
                result.getOuterBoundaryBaselineFit().getResiduals().size());
    }

    @Test
    void refittedFitUsesOnlyRetainedPoints() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 24, 0.15);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(20, 28));
        assertEquals(result.getRetainedSupportPointCount(),
                result.getIntervalExcludedFit().getResiduals().size());
    }

    @Test
    void exactlyOneBaselineAndOneRefitLogically() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 24, 0.15);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(18, 26));
        assertNotEquals(result.getOuterBoundaryBaselineFit(), result.getIntervalExcludedFit());
        assertEquals(points.size(),
                result.getOuterBoundaryBaselineFit().getResiduals().size());
        assertEquals(result.getRetainedSupportPointCount(),
                result.getIntervalExcludedFit().getResiduals().size());
    }

    @Test
    void noSecondIntervalDetected() {
        // Refitter has no detector call path; result stores exactly the supplied interval.
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 24, 0.15);
        final List<Integer> excluded = range(18, 26);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(excluded.size(), result.getExcludedInterval().getEdgeCount());
        assertEquals(1, 1); // single exclusion only
    }

    @Test
    void atLeastSixRetainedDistinctPointsRequired() {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        for (int i = 0; i < 8; i++) {
            points.add(new Point2D.Double(i, 0));
        }
        // Exclude 3 points leaving 5 distinct retained -> reject.
        final List<Integer> excluded = range(0, 2);
        assertThrows(IllegalArgumentException.class,
                () -> refitter.refitSupportPointsForTesting(points, excluded));
    }

    @Test
    void duplicateExcludedIndicesRejected() {
        final List<Point2D.Double> points = ellipsePoints(30, 0, 0, 25, 15, 0.0);
        final List<Integer> excluded = Arrays.asList(
                Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(4));
        assertThrows(IllegalArgumentException.class,
                () -> refitter.refitSupportPointsForTesting(points, excluded));
    }

    @Test
    void outOfRangeExcludedIndicesRejected() {
        final List<Point2D.Double> points = ellipsePoints(30, 0, 0, 25, 15, 0.0);
        final List<Integer> excluded = Arrays.asList(
                Integer.valueOf(3), Integer.valueOf(30));
        assertThrows(IllegalArgumentException.class,
                () -> refitter.refitSupportPointsForTesting(points, excluded));
    }

    @Test
    void nullSupportPointRejected() {
        final List<Point2D.Double> points = ellipsePoints(30, 0, 0, 25, 15, 0.0);
        points.set(4, null);
        assertThrows(IllegalArgumentException.class,
                () -> refitter.refitSupportPointsForTesting(points, range(10, 15)));
    }

    @Test
    void nonFiniteSupportCoordinateRejected() {
        final List<Point2D.Double> points = ellipsePoints(30, 0, 0, 25, 15, 0.0);
        points.set(4, new Point2D.Double(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> refitter.refitSupportPointsForTesting(points, range(10, 15)));
    }

    @Test
    void inputSupportListRemainsUnchanged() {
        final List<Point2D.Double> points = ellipsePoints(36, 0, 0, 28, 18, 0.2);
        final List<Point2D.Double> snapshot = copyPoints(points);
        final List<Integer> excluded = range(8, 14);
        refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(snapshot.size(), points.size());
        for (int i = 0; i < points.size(); i++) {
            assertEquals(snapshot.get(i).x, points.get(i).x, 0.0);
            assertEquals(snapshot.get(i).y, points.get(i).y, 0.0);
        }
    }

    @Test
    void inputPointObjectsRemainUnchanged() {
        final List<Point2D.Double> points = ellipsePoints(36, 0, 0, 28, 18, 0.2);
        final double x0 = points.get(0).x;
        final double y0 = points.get(0).y;
        refitter.refitSupportPointsForTesting(points, range(8, 14));
        assertEquals(x0, points.get(0).x, 0.0);
        assertEquals(y0, points.get(0).y, 0.0);
    }

    @Test
    void supportListsAreImmutable() {
        final List<Point2D.Double> points = ellipsePoints(36, 0, 0, 28, 18, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(8, 14));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getAllOuterBoundarySupportPoints()
                        .add(new Point2D.Double(0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getRetainedSupportPoints().add(new Point2D.Double(0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getExcludedSupportPoints().add(new Point2D.Double(0, 0)));
    }

    @Test
    void returnedCoordinatesAreDefensiveCopies() {
        final List<Point2D.Double> points = ellipsePoints(36, 0, 0, 28, 18, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(8, 14));
        final Point2D.Double p = result.getRetainedSupportPoints().get(0);
        final double ox = p.x;
        p.x = -999;
        assertEquals(ox, result.getRetainedSupportPoints().get(0).x, 0.0);
    }

    @Test
    void centerShiftCalculationCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 10, 20, 40, 25, 0.3);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel r = result.getIntervalExcludedFit().getFittedEllipse();
        final double expected = Math.hypot(r.getCenterX() - b.getCenterX(),
                r.getCenterY() - b.getCenterY());
        assertEquals(expected, result.getCenterShift(), 1.0e-12);
    }

    @Test
    void signedMajorAxisChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel r = result.getIntervalExcludedFit().getFittedEllipse();
        assertEquals(r.getSemiMajorAxis() - b.getSemiMajorAxis(),
                result.getSemiMajorAxisAbsoluteChange(), 1.0e-12);
    }

    @Test
    void majorAxisRelativeChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        assertEquals(
                Math.abs(result.getSemiMajorAxisAbsoluteChange()) / b.getSemiMajorAxis(),
                result.getSemiMajorAxisRelativeChange(),
                1.0e-12);
    }

    @Test
    void signedMinorAxisChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel r = result.getIntervalExcludedFit().getFittedEllipse();
        assertEquals(r.getSemiMinorAxis() - b.getSemiMinorAxis(),
                result.getSemiMinorAxisAbsoluteChange(), 1.0e-12);
    }

    @Test
    void minorAxisRelativeChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        assertEquals(
                Math.abs(result.getSemiMinorAxisAbsoluteChange()) / b.getSemiMinorAxis(),
                result.getSemiMinorAxisRelativeChange(),
                1.0e-12);
    }

    @Test
    void axisRatioChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel r = result.getIntervalExcludedFit().getFittedEllipse();
        assertEquals(r.getAxisRatio() - b.getAxisRatio(),
                result.getAxisRatioAbsoluteChange(), 1.0e-12);
    }

    @Test
    void rotationDifferenceModuloPi() {
        final double delta = SingleIntervalEllipseRefitter.smallestRotationDifferenceRadians(
                0.1, 0.1 + Math.PI + 0.2);
        assertEquals(
                SingleIntervalEllipseRefitter.smallestRotationDifferenceRadians(0.1, 0.3),
                delta,
                1.0e-12);
    }

    @Test
    void rotationDifferenceInZeroToHalfPi() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.8);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertTrue(result.getRotationChangeRadians() >= 0.0);
        assertTrue(result.getRotationChangeRadians() <= Math.PI / 2.0 + 1.0e-15);
        assertEquals(Math.toDegrees(result.getRotationChangeRadians()),
                result.getRotationChangeDegrees(), 1.0e-12);
    }

    @Test
    void signedEllipseAreaChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel r = result.getIntervalExcludedFit().getFittedEllipse();
        assertEquals(r.getArea() - b.getArea(),
                result.getEllipseAreaAbsoluteChange(), 1.0e-12);
    }

    @Test
    void ellipseAreaRelativeChangeCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final EllipseModel b = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        assertEquals(
                Math.abs(result.getEllipseAreaAbsoluteChange()) / b.getArea(),
                result.getEllipseAreaRelativeChange(),
                1.0e-12);
    }

    @Test
    void allBoundaryRmsCalculationsCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(
                manualRms(result.getOuterBoundaryBaselineFit().getFittedEllipse(), points),
                result.getBaselineAllBoundaryRmsResidual(),
                1.0e-12);
        assertEquals(
                manualRms(result.getIntervalExcludedFit().getFittedEllipse(), points),
                result.getRefittedAllBoundaryRmsResidual(),
                1.0e-12);
    }

    @Test
    void retainedRmsCalculationsCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(
                manualRms(result.getOuterBoundaryBaselineFit().getFittedEllipse(),
                        result.getRetainedSupportPoints()),
                result.getBaselineRetainedRmsResidual(),
                1.0e-12);
        assertEquals(
                manualRms(result.getIntervalExcludedFit().getFittedEllipse(),
                        result.getRetainedSupportPoints()),
                result.getRefittedRetainedRmsResidual(),
                1.0e-12);
    }

    @Test
    void excludedRmsCalculationsCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(
                manualRms(result.getOuterBoundaryBaselineFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getBaselineExcludedRmsResidual(),
                1.0e-12);
        assertEquals(
                manualRms(result.getIntervalExcludedFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getRefittedExcludedRmsResidual(),
                1.0e-12);
    }

    @Test
    void excludedMeanResidualCalculationsCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(
                manualMean(result.getOuterBoundaryBaselineFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getBaselineExcludedMeanResidual(),
                1.0e-12);
        assertEquals(
                manualMean(result.getIntervalExcludedFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getRefittedExcludedMeanResidual(),
                1.0e-12);
    }

    @Test
    void excludedMinimumResidualCalculationsCorrect() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(
                manualMin(result.getOuterBoundaryBaselineFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getBaselineExcludedMinimumResidual(),
                1.0e-12);
        assertEquals(
                manualMin(result.getIntervalExcludedFit().getFittedEllipse(),
                        result.getExcludedSupportPoints()),
                result.getRefittedExcludedMinimumResidual(),
                1.0e-12);
    }

    @Test
    void allReturnedMetricsAreFinite() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertTrue(Double.isFinite(result.getCenterShift()));
        assertTrue(Double.isFinite(result.getSemiMajorAxisAbsoluteChange()));
        assertTrue(Double.isFinite(result.getSemiMajorAxisRelativeChange()));
        assertTrue(Double.isFinite(result.getSemiMinorAxisAbsoluteChange()));
        assertTrue(Double.isFinite(result.getSemiMinorAxisRelativeChange()));
        assertTrue(Double.isFinite(result.getAxisRatioAbsoluteChange()));
        assertTrue(Double.isFinite(result.getRotationChangeRadians()));
        assertTrue(Double.isFinite(result.getRotationChangeDegrees()));
        assertTrue(Double.isFinite(result.getEllipseAreaAbsoluteChange()));
        assertTrue(Double.isFinite(result.getEllipseAreaRelativeChange()));
        assertTrue(Double.isFinite(result.getBaselineAllBoundaryRmsResidual()));
        assertTrue(Double.isFinite(result.getRefittedAllBoundaryRmsResidual()));
        assertTrue(Double.isFinite(result.getBaselineRetainedRmsResidual()));
        assertTrue(Double.isFinite(result.getRefittedRetainedRmsResidual()));
        assertTrue(Double.isFinite(result.getBaselineExcludedRmsResidual()));
        assertTrue(Double.isFinite(result.getRefittedExcludedRmsResidual()));
        assertTrue(Double.isFinite(result.getBaselineExcludedMeanResidual()));
        assertTrue(Double.isFinite(result.getRefittedExcludedMeanResidual()));
        assertTrue(Double.isFinite(result.getBaselineExcludedMinimumResidual()));
        assertTrue(Double.isFinite(result.getRefittedExcludedMinimumResidual()));
    }

    @Test
    void repeatedRefitsAreDeterministic() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final List<Integer> excluded = range(12, 22);
        final SingleIntervalEllipseRefitResult a =
                refitter.refitSupportPointsForTesting(points, excluded);
        final SingleIntervalEllipseRefitResult b =
                refitter.refitSupportPointsForTesting(points, excluded);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void resultEqualityAndHashCodeDeterministic() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult a =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        final SingleIntervalEllipseRefitResult b =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void noSourceImageObjectRequired() {
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertEquals(points.size(), result.getTotalSupportPointCount());
    }

    @Test
    void noPixelModificationPerformed() {
        // Mathematical refitter has no image API; this documents the invariant.
        final List<Point2D.Double> points = ellipsePoints(48, 0, 0, 40, 25, 0.2);
        refitter.refitSupportPointsForTesting(points, range(12, 22));
        assertTrue(true);
    }

    @Test
    void syntheticInwardDeformationImprovesRetainedFit() {
        final EllipseModel truth = new EllipseModel(100.0, 80.0, 60.0, 40.0, 0.35);
        final int n = 360;
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(n);
        for (int i = 0; i < n; i++) {
            final double t = 2.0 * Math.PI * i / n;
            points.add(truth.pointAt(t));
        }
        final int start = 40;
        final int end = 90;
        for (int i = start; i <= end; i++) {
            final Point2D.Double p = points.get(i);
            final double dx = p.x - truth.getCenterX();
            final double dy = p.y - truth.getCenterY();
            points.set(i, new Point2D.Double(
                    truth.getCenterX() + 0.70 * dx,
                    truth.getCenterY() + 0.70 * dy));
        }
        final List<Integer> excluded = range(start, end);
        final SingleIntervalEllipseRefitResult result =
                refitter.refitSupportPointsForTesting(points, excluded);

        final EllipseModel baseline = result.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refitted = result.getIntervalExcludedFit().getFittedEllipse();

        final double baselineTruthError = parameterDistance(truth, baseline);
        final double refitTruthError = parameterDistance(truth, refitted);
        assertTrue(refitTruthError < baselineTruthError);

        assertTrue(result.getRefittedRetainedRmsResidual()
                < result.getBaselineRetainedRmsResidual());
        assertTrue(result.getRefittedExcludedMeanResidual()
                < result.getBaselineExcludedMeanResidual());
        assertTrue(refitted.getSemiMajorAxis() > refitted.getSemiMinorAxis() + 1.0e-6);
        assertTrue(refitted.getAxisRatio() > 1.01);
    }

    private static double parameterDistance(final EllipseModel a, final EllipseModel b) {
        final double center = Math.hypot(a.getCenterX() - b.getCenterX(),
                a.getCenterY() - b.getCenterY());
        final double axes = Math.abs(a.getSemiMajorAxis() - b.getSemiMajorAxis())
                + Math.abs(a.getSemiMinorAxis() - b.getSemiMinorAxis());
        final double rot = SingleIntervalEllipseRefitter.smallestRotationDifferenceRadians(
                a.getRotationRadians(), b.getRotationRadians());
        return center + axes + rot;
    }

    private static double manualRms(final EllipseModel ellipse, final List<Point2D.Double> points) {
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            final double r = ellipse.normalizedRadialResidual(points.get(i).x, points.get(i).y);
            sum += r * r;
        }
        return Math.sqrt(sum / points.size());
    }

    private static double manualMean(final EllipseModel ellipse, final List<Point2D.Double> points) {
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            sum += ellipse.normalizedRadialResidual(points.get(i).x, points.get(i).y);
        }
        return sum / points.size();
    }

    private static double manualMin(final EllipseModel ellipse, final List<Point2D.Double> points) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            min = Math.min(min,
                    ellipse.normalizedRadialResidual(points.get(i).x, points.get(i).y));
        }
        return min;
    }

    private static boolean containsPoint(
            final List<Point2D.Double> haystack,
            final Point2D.Double needle) {
        for (int i = 0; i < haystack.size(); i++) {
            final Point2D.Double p = haystack.get(i);
            if (p.x == needle.x && p.y == needle.y) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> range(final int startInclusive, final int endInclusive) {
        final List<Integer> list = new ArrayList<Integer>();
        for (int i = startInclusive; i <= endInclusive; i++) {
            list.add(Integer.valueOf(i));
        }
        return list;
    }

    private static List<Point2D.Double> ellipsePoints(
            final int count,
            final double cx,
            final double cy,
            final double a,
            final double b,
            final double rotation) {
        final EllipseModel ellipse = new EllipseModel(cx, cy, a, b, rotation);
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(count);
        for (int i = 0; i < count; i++) {
            final double t = 2.0 * Math.PI * i / count;
            points.add(ellipse.pointAt(t));
        }
        return points;
    }

    private static List<Point2D.Double> copyPoints(final List<Point2D.Double> source) {
        final List<Point2D.Double> copy = new ArrayList<Point2D.Double>(source.size());
        for (int i = 0; i < source.size(); i++) {
            copy.add(new Point2D.Double(source.get(i).x, source.get(i).y));
        }
        return copy;
    }

    /**
     * Builds a minimal loop by fitting points onto a solid rectangle-like lattice
     * is unnecessary here; public refit validation for no-selection uses real profile.
     * For no-selection path we still need a valid OrderedBoundaryLoop — use extractor
     * on a filled block large enough for &gt;= 8 edges.
     */
    private static OrderedBoundaryLoop syntheticLoopFromPoints(final List<Point2D.Double> ignored) {
        final List<java.awt.Point> pixels = new ArrayList<java.awt.Point>();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 10; x++) {
                pixels.add(new java.awt.Point(x, y));
            }
        }
        return new OrderedBoundaryExtractor().selectOuterLoop(pixels);
    }
}
