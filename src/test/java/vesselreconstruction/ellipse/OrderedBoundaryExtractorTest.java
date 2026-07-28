package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for deterministic ordered boundary extraction.
 */
class OrderedBoundaryExtractorTest {

    private final OrderedBoundaryExtractor extractor = new OrderedBoundaryExtractor();

    @Test
    void nullPixelListRejected() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extractAllLoops(null));
    }

    @Test
    void emptyPixelListRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> extractor.extractAllLoops(new ArrayList<Point>()));
    }

    @Test
    void nullPointRejected() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(1, 1));
        pixels.add(null);
        assertThrows(IllegalArgumentException.class, () -> extractor.extractAllLoops(pixels));
    }

    @Test
    void duplicateCoordinateRejected() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(1, 1));
        pixels.add(new Point(1, 1));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractAllLoops(pixels));
    }

    @Test
    void negativeCoordinateRejected() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractAllLoops(pixels));
    }

    @Test
    void onePixelCreatesFourClockwiseEdges() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(3, 5));
        assertEquals(4, loop.getDirectedEdges().size());
    }

    @Test
    void onePixelEdgeOrderClosesExactly() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(2, 4));
        assertClosed(loop);
    }

    @Test
    void northEdgeGeometryExact() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(2, 4));
        final DirectedPixelEdge edge = loop.getDirectedEdges().get(0);
        assertEquals(2, edge.getStartX());
        assertEquals(4, edge.getStartY());
        assertEquals(3, edge.getEndX());
        assertEquals(4, edge.getEndY());
        assertEquals(DirectedPixelEdge.Direction.EAST, edge.getDirection());
    }

    @Test
    void eastEdgeGeometryExact() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(2, 4));
        final DirectedPixelEdge edge = loop.getDirectedEdges().get(1);
        assertEquals(3, edge.getStartX());
        assertEquals(4, edge.getStartY());
        assertEquals(3, edge.getEndX());
        assertEquals(5, edge.getEndY());
        assertEquals(DirectedPixelEdge.Direction.SOUTH, edge.getDirection());
    }

    @Test
    void southEdgeGeometryExact() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(2, 4));
        final DirectedPixelEdge edge = loop.getDirectedEdges().get(2);
        assertEquals(3, edge.getStartX());
        assertEquals(5, edge.getStartY());
        assertEquals(2, edge.getEndX());
        assertEquals(5, edge.getEndY());
        assertEquals(DirectedPixelEdge.Direction.WEST, edge.getDirection());
    }

    @Test
    void westEdgeGeometryExact() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(2, 4));
        final DirectedPixelEdge edge = loop.getDirectedEdges().get(3);
        assertEquals(2, edge.getStartX());
        assertEquals(5, edge.getStartY());
        assertEquals(2, edge.getEndX());
        assertEquals(4, edge.getEndY());
        assertEquals(DirectedPixelEdge.Direction.NORTH, edge.getDirection());
    }

    @Test
    void eachEdgeIsOneLatticeUnit() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(1, 1, 3, 2));
        for (final DirectedPixelEdge edge : loop.getDirectedEdges()) {
            final int len = Math.abs(edge.getEndX() - edge.getStartX())
                    + Math.abs(edge.getEndY() - edge.getStartY());
            assertEquals(1, len);
        }
    }

    @Test
    void block2x2ProducesEightEdges() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(5, 5, 2, 2));
        assertEquals(8, loop.getDirectedEdges().size());
    }

    @Test
    void rect3x2ProducesTenEdges() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(2, 3, 3, 2));
        assertEquals(10, loop.getDirectedEdges().size());
    }

    @Test
    void everyEdgeEndEqualsNextStart() {
        assertClosed(extractor.selectOuterLoop(rect(4, 4, 4, 3)));
    }

    @Test
    void finalEdgeEndEqualsFirstStart() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 2, 2));
        final List<DirectedPixelEdge> edges = loop.getDirectedEdges();
        final DirectedPixelEdge first = edges.get(0);
        final DirectedPixelEdge last = edges.get(edges.size() - 1);
        assertEquals(first.getStartX(), last.getEndX());
        assertEquals(first.getStartY(), last.getEndY());
    }

    @Test
    void startingEdgeNotDuplicated() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        assertEquals(4, loop.getDirectedEdges().size());
        assertEquals(1, Collections.frequency(loop.getDirectedEdges(), loop.getDirectedEdges().get(0)));
    }

    @Test
    void supportMidpointCountEqualsEdgeCount() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(1, 1, 3, 3));
        assertEquals(loop.getDirectedEdges().size(), loop.getOrderedSupportMidpoints().size());
    }

    @Test
    void supportMidpointsCorrespondToEdges() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(2, 2, 2, 2));
        final List<DirectedPixelEdge> edges = loop.getDirectedEdges();
        final List<Point2D.Double> mids = loop.getOrderedSupportMidpoints();
        for (int i = 0; i < edges.size(); i++) {
            final Point2D.Double expected = edges.get(i).getMidpoint();
            assertEquals(expected.x, mids.get(i).x, 0.0);
            assertEquals(expected.y, mids.get(i).y, 0.0);
        }
    }

    @Test
    void onePixelSignedAreaMagnitudeIsOne() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(7, 8));
        assertEquals(1.0, loop.getAbsoluteArea(), 1.0e-12);
    }

    @Test
    void block2x2SignedAreaMagnitudeIsFour() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 2, 2));
        assertEquals(4.0, loop.getAbsoluteArea(), 1.0e-12);
    }

    @Test
    void internalSharedSidesAbsent() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(0, 0, 2, 1));
        // Two horizontal pixels share a vertical side; perimeter should be 6.
        assertEquals(6, loop.getDirectedEdges().size());
        assertFalse(containsEdge(loop, 1, 0, 1, 1));
        assertFalse(containsEdge(loop, 1, 1, 1, 0));
    }

    @Test
    void enclosedOnePixelHoleCreatesSecondLoop() {
        final List<Point> ring = new ArrayList<Point>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1) {
                    continue;
                }
                ring.add(new Point(x, y));
            }
        }
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(ring);
        assertEquals(2, loops.size());
    }

    @Test
    void largerAreaOuterLoopIsSelected() {
        final List<Point> ring = new ArrayList<Point>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1) {
                    continue;
                }
                ring.add(new Point(x, y));
            }
        }
        final OrderedBoundaryLoop outer = extractor.selectOuterLoop(ring);
        assertEquals(9.0, outer.getAbsoluteArea(), 1.0e-12);
        assertTrue(outer.getDirectedEdges().size() > 4);
    }

    @Test
    void loopNumberingIsDeterministic() {
        final List<Point> ring = ringWithHole();
        final List<OrderedBoundaryLoop> a = extractor.extractAllLoops(ring);
        final List<OrderedBoundaryLoop> b = extractor.extractAllLoops(ring);
        assertEquals(a.get(0).getLoopNumber(), b.get(0).getLoopNumber());
        assertEquals(a.get(1).getLoopNumber(), b.get(1).getLoopNumber());
    }

    @Test
    void edgeDiscoveryIsDeterministic() {
        final OrderedBoundaryLoop a = extractor.selectOuterLoop(rect(3, 3, 4, 3));
        final OrderedBoundaryLoop b = extractor.selectOuterLoop(rect(3, 3, 4, 3));
        assertEquals(a.getDirectedEdges(), b.getDirectedEdges());
    }

    @Test
    void repeatedExtractionIsDeterministic() {
        final List<Point> pixels = rect(1, 2, 5, 4);
        assertEquals(extractor.extractAllLoops(pixels), extractor.extractAllLoops(pixels));
    }

    @Test
    void reversedInputGivesIdenticalLoops() {
        final List<Point> pixels = rect(2, 2, 3, 3);
        final List<Point> reversed = new ArrayList<Point>(pixels);
        Collections.reverse(reversed);
        assertEquals(
                extractor.selectOuterLoop(pixels).getDirectedEdges(),
                extractor.selectOuterLoop(reversed).getDirectedEdges());
    }

    @Test
    void shuffledDeterministicInputGivesIdenticalLoops() {
        final List<Point> pixels = rect(1, 1, 4, 2);
        final List<Point> shuffled = new ArrayList<Point>(pixels);
        // Deterministic shuffle: rotate by 3.
        Collections.rotate(shuffled, 3);
        assertEquals(
                extractor.selectOuterLoop(pixels).getDirectedEdges(),
                extractor.selectOuterLoop(shuffled).getDirectedEdges());
    }

    @Test
    void allExposedEdgesAccountedExactlyOnce() {
        final List<Point> ring = ringWithHole();
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(ring);
        int total = 0;
        for (int i = 0; i < loops.size(); i++) {
            total += loops.get(i).getDirectedEdges().size();
        }
        // 8 outer ring edge contributions: each of 8 pixels has some exposed sides.
        // Ring of 8 pixels around one hole has outer perimeter 12 and hole perimeter 4 = 16.
        assertEquals(16, total);
    }

    @Test
    void diagonalHighDegreeJunctionRejected() {
        // 8-connected diagonal pinch that creates an ambiguous lattice vertex.
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(0, 0));
        pixels.add(new Point(1, 0));
        pixels.add(new Point(0, 1));
        pixels.add(new Point(2, 1));
        pixels.add(new Point(1, 2));
        pixels.add(new Point(2, 2));
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extractAllLoops(pixels));
        assertTrue(ex.getMessage().contains("AMBIGUOUS_BOUNDARY_VERTEX"));
    }

    @Test
    void inputListRemainsUnchanged() {
        final List<Point> pixels = rect(0, 0, 2, 2);
        final List<Point> snapshot = copyPoints(pixels);
        extractor.selectOuterLoop(pixels);
        assertEquals(snapshot.size(), pixels.size());
        for (int i = 0; i < pixels.size(); i++) {
            assertEquals(snapshot.get(i).x, pixels.get(i).x);
            assertEquals(snapshot.get(i).y, pixels.get(i).y);
        }
    }

    @Test
    void inputPointsRemainUnchanged() {
        final List<Point> pixels = rect(1, 1, 2, 2);
        final int ox = pixels.get(0).x;
        final int oy = pixels.get(0).y;
        extractor.selectOuterLoop(pixels);
        assertEquals(ox, pixels.get(0).x);
        assertEquals(oy, pixels.get(0).y);
    }

    @Test
    void loopCollectionsAreImmutable() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(singleton(0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> loop.getDirectedEdges().add(
                        new DirectedPixelEdge(0, 0, 1, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> loop.getOrderedSupportMidpoints().add(new Point2D.Double(0, 0)));
    }

    @Test
    void returnedPointsAndRectanglesCannotMutateInternalState() {
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(rect(2, 2, 2, 2));
        final Rectangle r = loop.getLatticeBounds();
        final int ox = r.x;
        r.x = -99;
        assertEquals(ox, loop.getLatticeBounds().x);
        final Point2D.Double mid = loop.getOrderedSupportMidpoints().get(0);
        final double mx = mid.x;
        mid.x = -1;
        assertEquals(mx, loop.getOrderedSupportMidpoints().get(0).x, 0.0);
        final Point start = loop.getDirectedEdges().get(0).getStart();
        final int sx = start.x;
        start.x = -5;
        assertEquals(sx, loop.getDirectedEdges().get(0).getStart().x);
    }

    private static void assertClosed(final OrderedBoundaryLoop loop) {
        final List<DirectedPixelEdge> edges = loop.getDirectedEdges();
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge edge = edges.get(i);
            final DirectedPixelEdge next = edges.get((i + 1) % edges.size());
            assertEquals(edge.getEndX(), next.getStartX());
            assertEquals(edge.getEndY(), next.getStartY());
        }
    }

    private static boolean containsEdge(
            final OrderedBoundaryLoop loop,
            final int sx,
            final int sy,
            final int ex,
            final int ey) {
        for (final DirectedPixelEdge edge : loop.getDirectedEdges()) {
            if (edge.getStartX() == sx && edge.getStartY() == sy
                    && edge.getEndX() == ex && edge.getEndY() == ey) {
                return true;
            }
        }
        return false;
    }

    private static List<Point> singleton(final int x, final int y) {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(x, y));
        return pixels;
    }

    private static List<Point> rect(
            final int x0,
            final int y0,
            final int w,
            final int h) {
        final List<Point> pixels = new ArrayList<Point>();
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                pixels.add(new Point(x, y));
            }
        }
        return pixels;
    }

    private static List<Point> ringWithHole() {
        final List<Point> ring = new ArrayList<Point>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1) {
                    continue;
                }
                ring.add(new Point(x, y));
            }
        }
        return ring;
    }

    private static List<Point> copyPoints(final List<Point> points) {
        final List<Point> copy = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            copy.add(new Point(points.get(i).x, points.get(i).y));
        }
        return copy;
    }
}
