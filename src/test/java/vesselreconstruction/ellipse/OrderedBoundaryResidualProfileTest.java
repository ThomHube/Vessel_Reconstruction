package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ordered outer-boundary residual profiles.
 */
class OrderedBoundaryResidualProfileTest {

    private final OrderedBoundaryExtractor extractor = new OrderedBoundaryExtractor();

    @Test
    void nullLoopRejected() {
        final EllipseModel ellipse = new EllipseModel(1, 1, 2, 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> OrderedBoundaryResidualProfile.create(null, ellipse));
    }

    @Test
    void nullEllipseRejected() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OrderedBoundaryResidualProfile.create(loop, null));
    }

    @Test
    void oneSamplePerLoopEdge() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 3, 2));
        final OrderedBoundaryResidualProfile profile = OrderedBoundaryResidualProfile.create(
                loop, new EllipseModel(1.5, 1.0, 3.0, 2.0, 0.0));
        assertEquals(loop.getDirectedEdges().size(), profile.getSamples().size());
    }

    @Test
    void sampleOrderEqualsLoopOrder() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(1, 1, 2, 2));
        final OrderedBoundaryResidualProfile profile = OrderedBoundaryResidualProfile.create(
                loop, new EllipseModel(2, 2, 2, 2, 0));
        for (int i = 0; i < profile.getSamples().size(); i++) {
            assertEquals(i, profile.getSamples().get(i).getBoundaryIndex());
            final DirectedPixelEdge edge = loop.getDirectedEdges().get(i);
            assertEquals(edge.getStartX(), profile.getSamples().get(i).getEdgeStartX());
            assertEquals(edge.getStartY(), profile.getSamples().get(i).getEdgeStartY());
            assertEquals(edge.getEndX(), profile.getSamples().get(i).getEdgeEndX());
            assertEquals(edge.getEndY(), profile.getSamples().get(i).getEdgeEndY());
        }
    }

    @Test
    void midpointUsedForResidualCalculation() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        final EllipseModel ellipse = new EllipseModel(0.5, 0.5, 1.0, 1.0, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        for (int i = 0; i < profile.getSamples().size(); i++) {
            final OrderedBoundaryResidualProfile.Sample sample = profile.getSamples().get(i);
            final Point2D.Double mid = sample.getSupportMidpoint();
            final double expected = ellipse.normalizedRadius(mid.x, mid.y) - 1.0;
            assertEquals(expected, sample.getResidual(), 0.0);
        }
    }

    @Test
    void negativeResidualMeansInward() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        // Large ellipse containing the pixel: residuals negative (inward from ellipse? 
        // Wait: residual = radius - 1. Point inside ellipse has radius < 1, residual < 0 = inward.
        final EllipseModel large = new EllipseModel(0.5, 0.5, 5.0, 5.0, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, large);
        assertTrue(profile.getMinimumResidual() < 0.0);
        assertTrue(profile.getNegativeResidualCount() > 0);
    }

    @Test
    void positiveResidualMeansOutward() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 4, 4));
        final EllipseModel tiny = new EllipseModel(2.0, 2.0, 0.5, 0.5, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, tiny);
        assertTrue(profile.getMaximumResidual() > 0.0);
        assertTrue(profile.getPositiveResidualCount() > 0);
    }

    @Test
    void minimumResidualAndIndexAreCorrect() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 3, 3));
        final EllipseModel ellipse = new EllipseModel(1.5, 1.5, 2.0, 1.2, 0.3);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        double min = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        for (int i = 0; i < profile.getSamples().size(); i++) {
            final double r = profile.getSamples().get(i).getResidual();
            if (r < min) {
                min = r;
                minIndex = i;
            }
        }
        assertEquals(min, profile.getMinimumResidual(), 0.0);
        assertEquals(minIndex, profile.getMinimumResidualIndex());
    }

    @Test
    void maximumResidualAndIndexAreCorrect() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 3, 3));
        final EllipseModel ellipse = new EllipseModel(1.5, 1.5, 2.0, 1.2, 0.3);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        double max = Double.NEGATIVE_INFINITY;
        int maxIndex = -1;
        for (int i = 0; i < profile.getSamples().size(); i++) {
            final double r = profile.getSamples().get(i).getResidual();
            if (r > max) {
                max = r;
                maxIndex = i;
            }
        }
        assertEquals(max, profile.getMaximumResidual(), 0.0);
        assertEquals(maxIndex, profile.getMaximumResidualIndex());
    }

    @Test
    void medianResidualIsCorrect() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        final EllipseModel ellipse = new EllipseModel(0.5, 0.5, 1.0, 1.0, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        final List<Double> values = new ArrayList<Double>();
        for (int i = 0; i < profile.getSamples().size(); i++) {
            values.add(Double.valueOf(profile.getSamples().get(i).getResidual()));
        }
        assertEquals(manualMedian(values), profile.getMedianResidual(), 1.0e-15);
    }

    @Test
    void madIsCorrect() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 2, 2));
        final EllipseModel ellipse = new EllipseModel(1.0, 1.0, 2.0, 1.5, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        final List<Double> absDev = new ArrayList<Double>();
        final double median = profile.getMedianResidual();
        for (int i = 0; i < profile.getSamples().size(); i++) {
            absDev.add(Double.valueOf(Math.abs(
                    profile.getSamples().get(i).getResidual() - median)));
        }
        assertEquals(manualMedian(absDev), profile.getMedianAbsoluteDeviation(), 1.0e-15);
    }

    @Test
    void signCountsAreCorrect() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 3, 3));
        final EllipseModel ellipse = new EllipseModel(1.5, 1.5, 2.0, 1.5, 0.0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        int neg = 0;
        int pos = 0;
        int zero = 0;
        for (int i = 0; i < profile.getSamples().size(); i++) {
            final double r = profile.getSamples().get(i).getResidual();
            if (r < 0.0) {
                neg++;
            } else if (r > 0.0) {
                pos++;
            } else {
                zero++;
            }
        }
        assertEquals(neg, profile.getNegativeResidualCount());
        assertEquals(pos, profile.getPositiveResidualCount());
        assertEquals(zero, profile.getZeroResidualCount());
        assertEquals(profile.getSamples().size(), neg + pos + zero);
    }

    @Test
    void noSmoothingIsPerformed() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 2, 2));
        final EllipseModel ellipse = new EllipseModel(1, 1, 2, 1, 0);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        for (int i = 0; i < profile.getSamples().size(); i++) {
            final OrderedBoundaryResidualProfile.Sample s = profile.getSamples().get(i);
            final Point2D.Double mid = loop.getDirectedEdges().get(i).getMidpoint();
            assertEquals(ellipse.normalizedRadius(mid.x, mid.y) - 1.0, s.getResidual(), 0.0);
        }
    }

    @Test
    void noPointIsExcluded() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 3, 2));
        final OrderedBoundaryResidualProfile profile = OrderedBoundaryResidualProfile.create(
                loop, new EllipseModel(1.5, 1.0, 3, 2, 0));
        assertEquals(loop.getDirectedEdges().size(), profile.getSamples().size());
    }

    @Test
    void ellipseParametersRemainUnchanged() {
        final EllipseModel ellipse = new EllipseModel(2, 3, 4, 2, 0.5);
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 4, 4));
        OrderedBoundaryResidualProfile.create(loop, ellipse);
        assertEquals(2.0, ellipse.getCenterX(), 0.0);
        assertEquals(3.0, ellipse.getCenterY(), 0.0);
        assertEquals(4.0, ellipse.getSemiMajorAxis(), 0.0);
        assertEquals(2.0, ellipse.getSemiMinorAxis(), 0.0);
        assertEquals(0.5, ellipse.getRotationRadians(), 0.0);
    }

    @Test
    void deterministicRepeatedProfileCreation() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(1, 1, 3, 3));
        final EllipseModel ellipse = new EllipseModel(2.5, 2.5, 3, 2, 0.2);
        final OrderedBoundaryResidualProfile a =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        final OrderedBoundaryResidualProfile b =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        assertEquals(a, b);
    }

    @Test
    void profileListsAreImmutable() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        final OrderedBoundaryResidualProfile profile = OrderedBoundaryResidualProfile.create(
                loop, new EllipseModel(0.5, 0.5, 1, 1, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> profile.getSamples().add(profile.getSamples().get(0)));
    }

    @Test
    void sampleCoordinateObjectsAreDefensiveCopies() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        final OrderedBoundaryResidualProfile profile = OrderedBoundaryResidualProfile.create(
                loop, new EllipseModel(0.5, 0.5, 1, 1, 0));
        final Point2D.Double mid = profile.getSamples().get(0).getSupportMidpoint();
        final double ox = mid.x;
        mid.x = -99;
        assertEquals(ox, profile.getSamples().get(0).getSupportMidpoint().x, 0.0);
        final Point2D.Double minPt = profile.getMinimumResidualPoint();
        final double mx = minPt.x;
        minPt.x = -1;
        assertEquals(mx, profile.getMinimumResidualPoint().x, 0.0);
    }

    private static double manualMedian(final List<Double> values) {
        final double[] sorted = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            sorted[i] = values.get(i).doubleValue();
        }
        java.util.Arrays.sort(sorted);
        final int n = sorted.length;
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static List<Point> singleton(final int x, final int y) {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(x, y));
        return pixels;
    }

    private static List<Point> rect(final int x0, final int y0, final int w, final int h) {
        final List<Point> pixels = new ArrayList<Point>();
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                pixels.add(new Point(x, y));
            }
        }
        return pixels;
    }
}
