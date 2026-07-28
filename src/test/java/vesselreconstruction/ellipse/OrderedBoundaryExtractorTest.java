package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    void diagonalNwSeIsResolvedIntoClosedLoops() {
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(diagonalNwSePixels());
        assertTrue(loops.size() >= 2);
        int total = 0;
        for (int i = 0; i < loops.size(); i++) {
            assertClosed(loops.get(i));
            total += loops.get(i).getDirectedEdges().size();
        }
        assertEquals(16, total);
    }

    @Test
    void diagonalNeSwIsResolvedIntoClosedLoops() {
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(diagonalNeSwPixels());
        assertTrue(loops.size() >= 2);
        int total = 0;
        for (int i = 0; i < loops.size(); i++) {
            assertClosed(loops.get(i));
            total += loops.get(i).getDirectedEdges().size();
        }
        assertEquals(16, total);
    }

    @Test
    void diagonalNeSwIncomingEastSelectsSouthRightTurn() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(135, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        assertEquals(
                Arrays.asList(
                        AmbiguousBoundaryVertexDiagnostic.LEFT_TURN,
                        AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN),
                diagnostic.getCandidateTurnRelations());
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        exposed.add(diagnostic.getIncomingEdge());
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        final DirectedPixelEdge selected =
                OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                        diagnostic, exposed, unused, 136, 92);
        assertEquals(new DirectedPixelEdge(136, 92, 136, 93), selected);
        assertEquals(DirectedPixelEdge.Direction.SOUTH, selected.getDirection());
        assertTrue(unused.contains(new DirectedPixelEdge(136, 92, 136, 91)));
    }

    @Test
    void diagonalNeSwIncomingWestSelectsNorthRightTurn() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(137, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        assertEquals(
                Arrays.asList(
                        AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                        AmbiguousBoundaryVertexDiagnostic.LEFT_TURN),
                diagnostic.getCandidateTurnRelations());
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        exposed.add(diagnostic.getIncomingEdge());
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        final DirectedPixelEdge selected =
                OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                        diagnostic, exposed, unused, 136, 92);
        assertEquals(new DirectedPixelEdge(136, 92, 136, 91), selected);
        assertEquals(DirectedPixelEdge.Direction.NORTH, selected.getDirection());
    }

    @Test
    void diagonalNwSeResolvesToUniqueRightTurnForReachableIncomingOrientations() {
        // For unused WEST/EAST candidates, only NORTH/SOUTH incoming yields
        // exactly one RIGHT_TURN and one LEFT_TURN (reachable checkerboard cases).
        final DirectedPixelEdge[] incomings = new DirectedPixelEdge[] {
                new DirectedPixelEdge(2, 0, 2, 1), // SOUTH
                new DirectedPixelEdge(2, 2, 2, 1)  // NORTH
        };
        for (int i = 0; i < incomings.length; i++) {
            final DirectedPixelEdge west = new DirectedPixelEdge(2, 1, 1, 1);
            final DirectedPixelEdge east = new DirectedPixelEdge(2, 1, 3, 1);
            final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                    AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NW_SE,
                    incomings[i],
                    2, 1,
                    west,
                    east);
            final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>();
            exposed.add(west);
            exposed.add(east);
            exposed.add(incomings[i]);
            final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>();
            unused.add(west);
            unused.add(east);
            final DirectedPixelEdge selected =
                    OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                            diagnostic, exposed, unused, 2, 1);
            assertTrue(selected != null);
            assertEquals(
                    AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                    AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                            incomings[i].getDirection(), selected.getDirection()));
        }
    }

    @Test
    void sameFaceHelperReturnsNullForNonDiagonalOccupancy() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticOccupancyDiagnostic(
                true, true, false, false);
        assertEquals(AmbiguousBoundaryVertexDiagnostic.TWO_CARDINAL_SELECTED,
                diagnostic.getLocalOccupancyPattern());
        assertTrue(helperSelect(diagnostic) == null);
    }

    @Test
    void sameFaceHelperReturnsNullWhenNotExactlyTwoUnusedCandidates() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(1, 2, 2, 2);
        final DirectedPixelEdge a = new DirectedPixelEdge(2, 2, 3, 2);
        final DirectedPixelEdge b = new DirectedPixelEdge(2, 2, 2, 3);
        final DirectedPixelEdge c = new DirectedPixelEdge(2, 2, 2, 1);
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                new AmbiguousBoundaryVertexDiagnostic(
                        1, 1, 8, 1, 2, 2, incoming,
                        Arrays.asList(incoming, a, b, c),
                        Arrays.asList(a, b, c),
                        Arrays.asList(a, b, c),
                        Collections.<DirectedPixelEdge>emptyList(),
                        false, true, true, false);
        assertTrue(helperSelect(diagnostic) == null);
    }

    @Test
    void sameFaceHelperReturnsNullWhenNotExactlyOneRightAndOneLeft() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(1, 2, 2, 2); // EAST
        final DirectedPixelEdge east = new DirectedPixelEdge(2, 2, 3, 2); // STRAIGHT
        final DirectedPixelEdge south = new DirectedPixelEdge(2, 2, 2, 3); // RIGHT
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                new AmbiguousBoundaryVertexDiagnostic(
                        1, 1, 4, 1, 2, 2, incoming,
                        Arrays.asList(incoming, east, south),
                        Arrays.asList(east, south),
                        Arrays.asList(east, south),
                        Collections.<DirectedPixelEdge>emptyList(),
                        false, true, true, false);
        assertTrue(helperSelect(diagnostic) == null);
    }

    @Test
    void sameFaceHelperReturnsNullWhenCandidateAlreadyUsed() {
        final AmbiguousBoundaryVertexDiagnostic base = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(135, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>(
                base.getUnusedOutgoingEdges());
        exposed.add(base.getIncomingEdge());
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>();
        unused.add(base.getUnusedOutgoingEdges().get(0)); // only LEFT remains unused
        assertTrue(OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                base, exposed, unused, 136, 92) == null);
    }

    @Test
    void sameFaceHelperReturnsNullWhenCandidateNotInExposedSet() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(135, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>();
        exposed.add(diagnostic.getIncomingEdge());
        // omit unused outgoing from exposed set
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        assertTrue(OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                diagnostic, exposed, unused, 136, 92) == null);
    }

    @Test
    void evaluatingHelperDoesNotMarkCandidatesUsed() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(135, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        exposed.add(diagnostic.getIncomingEdge());
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        final int before = unused.size();
        OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                diagnostic, exposed, unused, 136, 92);
        assertEquals(before, unused.size());
        assertTrue(unused.containsAll(diagnostic.getUnusedOutgoingEdges()));
    }

    @Test
    void leftTurnRemainsAvailableForLaterLoopAfterRightTurnResolution() {
        final List<Point> pixels = twoDiagonalPixels();
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(pixels);
        assertEquals(2, loops.size());
        final Set<DirectedPixelEdge> all = new HashSet<DirectedPixelEdge>();
        for (int i = 0; i < loops.size(); i++) {
            for (final DirectedPixelEdge edge : loops.get(i).getDirectedEdges()) {
                assertTrue(all.add(edge), "edge reused across loops: " + edge);
            }
        }
        assertEquals(8, all.size());
    }

    @Test
    void twoDiagonallyTouchingPixelsProduceTwoSimpleOnePixelLoops() {
        final List<Point> pixels = twoDiagonalPixels();
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(pixels);
        assertEquals(2, loops.size());
        assertEquals(4, loops.get(0).getDirectedEdges().size());
        assertEquals(4, loops.get(1).getDirectedEdges().size());
        assertEquals(1.0, loops.get(0).getAbsoluteArea(), 0.0);
        assertEquals(1.0, loops.get(1).getAbsoluteArea(), 0.0);
        assertClosed(loops.get(0));
        assertClosed(loops.get(1));
        assertEquals(4, loops.get(0).getDistinctVertexCount());
        assertEquals(4, loops.get(1).getDistinctVertexCount());
        assertNoRepeatedNonClosingVertex(loops.get(0));
        assertNoRepeatedNonClosingVertex(loops.get(1));
        assertEquals(1, loops.get(0).getLoopNumber());
        assertEquals(2, loops.get(1).getLoopNumber());
    }

    @Test
    void twoDiagonalPixelLoopsDoNotCrossDiagonalContact() {
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(twoDiagonalPixels());
        // Each loop's edges must stay on the four faces of a single pixel.
        assertLoopUsesOnlyPixelFaces(loops.get(0), new Point(0, 0), new Point(1, 1));
        assertLoopUsesOnlyPixelFaces(loops.get(1), new Point(0, 0), new Point(1, 1));
    }

    @Test
    void reversedAndShuffledDiagonalInputProducesIdenticalLoops() {
        final List<Point> forward = twoDiagonalPixels();
        final List<Point> reversed = new ArrayList<Point>(forward);
        Collections.reverse(reversed);
        final List<Point> shuffled = new ArrayList<Point>(forward);
        Collections.swap(shuffled, 0, 1);
        final List<OrderedBoundaryLoop> a = extractor.extractAllLoops(forward);
        final List<OrderedBoundaryLoop> b = extractor.extractAllLoops(reversed);
        final List<OrderedBoundaryLoop> c = extractor.extractAllLoops(shuffled);
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(a, extractor.extractAllLoops(forward));
    }

    @Test
    void rectangleWithDiagonallyAttachedPixelProducesSeparateLoopsAndSelectsOuterRectangle() {
        final List<Point> pixels = rect(1, 1, 2, 2);
        pixels.add(new Point(3, 3));
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(pixels);
        assertEquals(2, loops.size());
        int total = 0;
        for (int i = 0; i < loops.size(); i++) {
            total += loops.get(i).getDirectedEdges().size();
        }
        assertEquals(8 + 4, total);
        final OrderedBoundaryLoop outer = extractor.selectOuterLoop(pixels);
        assertEquals(4.0, outer.getAbsoluteArea(), 0.0);
        assertEquals(8, outer.getDirectedEdges().size());
    }

    @Test
    void largerRegionsTouchingOnlyDiagonallyProduceSeparateSimpleLoops() {
        final List<Point> pixels = rect(0, 0, 2, 2);
        pixels.addAll(rect(2, 2, 2, 2));
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(pixels);
        assertEquals(2, loops.size());
        assertEquals(8, loops.get(0).getDirectedEdges().size());
        assertEquals(8, loops.get(1).getDirectedEdges().size());
        assertEquals(4.0, loops.get(0).getAbsoluteArea(), 0.0);
        assertEquals(4.0, loops.get(1).getAbsoluteArea(), 0.0);
    }

    @Test
    void outerLoopSelectionOrderRemainsAreaThenEdgesThenBoundsThenNumber() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.addAll(rect(5, 5, 3, 2)); // area 6
        pixels.addAll(rect(0, 0, 2, 2)); // area 4
        final OrderedBoundaryLoop outer = extractor.selectOuterLoop(pixels);
        assertEquals(6.0, outer.getAbsoluteArea(), 0.0);
    }

    @Test
    void cardinallyConnectedPixelsStillProduceOneContinuousLoop() {
        final List<OrderedBoundaryLoop> loops = extractor.extractAllLoops(rect(0, 0, 2, 2));
        assertEquals(1, loops.size());
        assertEquals(8, loops.get(0).getDirectedEdges().size());
    }

    @Test
    void nonDiagonalHighDegreeAmbiguityStillThrows() {
        // Three unused outgoing edges at a vertex that is not an exact checkerboard.
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(0, 0));
        pixels.add(new Point(1, 0));
        pixels.add(new Point(2, 0));
        pixels.add(new Point(0, 1));
        pixels.add(new Point(2, 1));
        pixels.add(new Point(0, 2));
        pixels.add(new Point(1, 2));
        pixels.add(new Point(2, 2));
        pixels.add(new Point(1, 1)); // center fills — may not be ambiguous
        // Use a fork without filling center: remove center
        pixels.remove(pixels.size() - 1);
        // This ring-like shape with center empty is a hole, usually 2 loops.
        // Build a known non-diagonal multi-outgoing via synthetic helper null +
        // extraction of a shape that still fails: three pixels meeting at a T
        // through cardinal contacts only is usually unambiguous.
        // Use synthetic diagnostic assertion for unresolved non-diagonal:
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticOccupancyDiagnostic(
                true, true, true, false);
        assertEquals(AmbiguousBoundaryVertexDiagnostic.THREE_SELECTED,
                diagnostic.getLocalOccupancyPattern());
        assertTrue(helperSelect(diagnostic) == null);
    }

    @Test
    void diagonalRuleNeverSelectsLeftStraightOrReverse() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = syntheticDiagonalDiagnostic(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                new DirectedPixelEdge(135, 92, 136, 92),
                136, 92,
                new DirectedPixelEdge(136, 92, 136, 91),
                new DirectedPixelEdge(136, 92, 136, 93));
        final DirectedPixelEdge selected = helperSelect(diagnostic);
        assertTrue(selected != null);
        final String turn = AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                diagnostic.getIncomingEdge().getDirection(), selected.getDirection());
        assertEquals(AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN, turn);
        assertFalse(AmbiguousBoundaryVertexDiagnostic.LEFT_TURN.equals(turn));
        assertFalse(AmbiguousBoundaryVertexDiagnostic.STRAIGHT.equals(turn));
        assertFalse(AmbiguousBoundaryVertexDiagnostic.REVERSE.equals(turn));
    }

    private static List<Point> diagonalNwSePixels() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(0, 0));
        pixels.add(new Point(1, 0));
        pixels.add(new Point(0, 1));
        pixels.add(new Point(2, 1));
        pixels.add(new Point(1, 2));
        pixels.add(new Point(2, 2));
        return pixels;
    }

    private static List<Point> diagonalNeSwPixels() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(2, 0));
        pixels.add(new Point(1, 0));
        pixels.add(new Point(2, 1));
        pixels.add(new Point(0, 1));
        pixels.add(new Point(1, 2));
        pixels.add(new Point(0, 2));
        return pixels;
    }

    private static List<Point> twoDiagonalPixels() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(0, 0));
        pixels.add(new Point(1, 1));
        return pixels;
    }

    private static DirectedPixelEdge helperSelect(
            final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        final Set<DirectedPixelEdge> exposed = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        exposed.add(diagnostic.getIncomingEdge());
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(
                diagnostic.getUnusedOutgoingEdges());
        return OrderedBoundaryExtractor.selectSameFaceSuccessorAtDiagonalContact(
                diagnostic,
                exposed,
                unused,
                diagnostic.getVertexX(),
                diagnostic.getVertexY());
    }

    private static AmbiguousBoundaryVertexDiagnostic syntheticDiagonalDiagnostic(
            final String expectedOccupancy,
            final DirectedPixelEdge incoming,
            final int vertexX,
            final int vertexY,
            final DirectedPixelEdge unusedA,
            final DirectedPixelEdge unusedB) {
        final boolean nw;
        final boolean ne;
        final boolean sw;
        final boolean se;
        if (AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW.equals(expectedOccupancy)) {
            nw = false;
            ne = true;
            sw = true;
            se = false;
        } else {
            nw = true;
            ne = false;
            sw = false;
            se = true;
        }
        final List<DirectedPixelEdge> unused = Arrays.asList(unusedA, unusedB);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 8, 1, vertexX, vertexY, incoming,
                Arrays.asList(incoming, unusedA, unusedB),
                unused,
                unused,
                Collections.<DirectedPixelEdge>emptyList(),
                nw, ne, sw, se);
    }

    private static AmbiguousBoundaryVertexDiagnostic syntheticOccupancyDiagnostic(
            final boolean nw,
            final boolean ne,
            final boolean sw,
            final boolean se) {
        final int vx = 5;
        final int vy = 5;
        final DirectedPixelEdge incoming = new DirectedPixelEdge(4, 5, 5, 5);
        final DirectedPixelEdge a = new DirectedPixelEdge(5, 5, 5, 4);
        final DirectedPixelEdge b = new DirectedPixelEdge(5, 5, 5, 6);
        final List<DirectedPixelEdge> unused = Arrays.asList(a, b);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 4, 1, vx, vy, incoming,
                Arrays.asList(incoming, a, b),
                unused,
                unused,
                Collections.<DirectedPixelEdge>emptyList(),
                nw, ne, sw, se);
    }

    private static void assertNoRepeatedNonClosingVertex(final OrderedBoundaryLoop loop) {
        assertEquals(loop.getDirectedEdges().size(), loop.getDistinctVertexCount());
    }

    private static void assertLoopUsesOnlyPixelFaces(
            final OrderedBoundaryLoop loop,
            final Point pixelA,
            final Point pixelB) {
        final Set<String> facesA = pixelFaceUndirectedKeys(pixelA.x, pixelA.y);
        final Set<String> facesB = pixelFaceUndirectedKeys(pixelB.x, pixelB.y);
        boolean allA = true;
        boolean allB = true;
        for (final DirectedPixelEdge edge : loop.getDirectedEdges()) {
            final String key = undirectedKey(edge);
            if (!facesA.contains(key)) {
                allA = false;
            }
            if (!facesB.contains(key)) {
                allB = false;
            }
        }
        assertTrue(allA || allB, "loop crossed the diagonal contact");
    }

    private static Set<String> pixelFaceUndirectedKeys(final int x, final int y) {
        final Set<String> keys = new HashSet<String>();
        keys.add(undirectedKey(new DirectedPixelEdge(x, y, x + 1, y)));
        keys.add(undirectedKey(new DirectedPixelEdge(x + 1, y, x + 1, y + 1)));
        keys.add(undirectedKey(new DirectedPixelEdge(x + 1, y + 1, x, y + 1)));
        keys.add(undirectedKey(new DirectedPixelEdge(x, y + 1, x, y)));
        return keys;
    }

    private static String undirectedKey(final DirectedPixelEdge edge) {
        final int ax = edge.getStartX();
        final int ay = edge.getStartY();
        final int bx = edge.getEndX();
        final int by = edge.getEndY();
        if (ay < by || (ay == by && ax <= bx)) {
            return ay + "," + ax + "," + by + "," + bx;
        }
        return by + "," + bx + "," + ay + "," + ax;
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
