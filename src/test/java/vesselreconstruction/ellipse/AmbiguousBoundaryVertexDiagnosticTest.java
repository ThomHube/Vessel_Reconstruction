package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic JUnit 5 tests for AmbiguousBoundaryVertexDiagnostic.
 */
class AmbiguousBoundaryVertexDiagnosticTest {

    private static final int VX = 5;
    private static final int VY = 7;

    // ------------------------------------------------------------------
    // 1-6 Constructor validation
    // ------------------------------------------------------------------

    @Test
    void nullIncomingEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder().incomingEdge(null).build());
    }

    @Test
    void incomingEdgeMustEndAtDiagnosticVertex() {
        final DirectedPixelEdge badIncoming = new DirectedPixelEdge(4, 7, 4, 8);
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder().incomingEdge(badIncoming).rebuildIncident().build());
    }

    @Test
    void everyOutgoingEdgeMustStartAtDiagnosticVertex() {
        final DirectedPixelEdge badOutgoing = new DirectedPixelEdge(6, 7, 7, 7);
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder()
                        .outgoing(Arrays.asList(
                                new DirectedPixelEdge(VX, VY, VX + 1, VY),
                                badOutgoing))
                        .unused(Arrays.asList(
                                new DirectedPixelEdge(VX, VY, VX + 1, VY),
                                badOutgoing))
                        .used(Collections.<DirectedPixelEdge>emptyList())
                        .rebuildIncident()
                        .build());
    }

    @Test
    void atLeastTwoUnusedOutgoingEdgesAreRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder()
                        .outgoing(Collections.singletonList(
                                new DirectedPixelEdge(VX, VY, VX + 1, VY)))
                        .unused(Collections.singletonList(
                                new DirectedPixelEdge(VX, VY, VX + 1, VY)))
                        .used(Collections.<DirectedPixelEdge>emptyList())
                        .rebuildIncident()
                        .build());
    }

    @Test
    void usedAndUnusedOutgoingSetsAreDisjoint() {
        final DirectedPixelEdge shared = new DirectedPixelEdge(VX, VY, VX + 1, VY);
        final DirectedPixelEdge other = new DirectedPixelEdge(VX, VY, VX, VY + 1);
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder()
                        .outgoing(Arrays.asList(shared, other))
                        .unused(Arrays.asList(shared, other))
                        .used(Collections.singletonList(shared))
                        .rebuildIncident()
                        .build());
    }

    @Test
    void usedPlusUnusedOutgoingEdgesEqualOutgoingEdges() {
        final DirectedPixelEdge a = new DirectedPixelEdge(VX, VY, VX + 1, VY);
        final DirectedPixelEdge b = new DirectedPixelEdge(VX, VY, VX, VY + 1);
        final DirectedPixelEdge c = new DirectedPixelEdge(VX, VY, VX - 1, VY);
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder()
                        .outgoing(Arrays.asList(a, b, c))
                        .unused(Arrays.asList(a, b))
                        .used(Collections.<DirectedPixelEdge>emptyList())
                        .rebuildIncident()
                        .build());
    }

    // ------------------------------------------------------------------
    // 7-8 Immutability
    // ------------------------------------------------------------------

    @Test
    void listGettersAreImmutable() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder().build();
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getAllIncidentEdges().add(
                        new DirectedPixelEdge(VX, VY, VX + 1, VY)));
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getOutgoingEdges().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getUnusedOutgoingEdges().remove(0));
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getUsedOutgoingEdges().add(
                        new DirectedPixelEdge(VX, VY, VX - 1, VY)));
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getOutgoingDirections().add("WEST"));
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getUnusedOutgoingDirections().set(0, "NORTH"));
        assertThrows(UnsupportedOperationException.class,
                () -> diagnostic.getCandidateTurnRelations().add("REVERSE"));
    }

    @Test
    void containedEdgeObjectsAreImmutable() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder().build();
        final DirectedPixelEdge incoming = diagnostic.getIncomingEdge();
        final int startX = incoming.getStartX();
        final int startY = incoming.getStartY();
        final int endX = incoming.getEndX();
        final int endY = incoming.getEndY();
        final Point start = incoming.getStart();
        start.x = -99;
        start.y = -88;
        final Point end = incoming.getEnd();
        end.x = -77;
        end.y = -66;
        assertEquals(startX, diagnostic.getIncomingEdge().getStartX());
        assertEquals(startY, diagnostic.getIncomingEdge().getStartY());
        assertEquals(endX, diagnostic.getIncomingEdge().getEndX());
        assertEquals(endY, diagnostic.getIncomingEdge().getEndY());

        final DirectedPixelEdge unused = diagnostic.getUnusedOutgoingEdges().get(0);
        final Point unusedStart = unused.getStart();
        final int ux = unused.getStartX();
        unusedStart.x = -55;
        assertEquals(ux, diagnostic.getUnusedOutgoingEdges().get(0).getStartX());
    }

    // ------------------------------------------------------------------
    // 9-14 Occupancy patterns
    // ------------------------------------------------------------------

    @Test
    void occupancyPatternDiagonalNwSeIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NW_SE,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, false, false, true));
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder()
                .occupancy(true, false, false, true)
                .build();
        assertEquals(AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NW_SE,
                diagnostic.getLocalOccupancyPattern());
        assertEquals(VX - 1, diagnostic.getNorthWestX());
        assertEquals(VY - 1, diagnostic.getNorthWestY());
        assertEquals(VX, diagnostic.getNorthEastX());
        assertEquals(VY - 1, diagnostic.getNorthEastY());
        assertEquals(VX - 1, diagnostic.getSouthWestX());
        assertEquals(VY, diagnostic.getSouthWestY());
        assertEquals(VX, diagnostic.getSouthEastX());
        assertEquals(VY, diagnostic.getSouthEastY());
    }

    @Test
    void occupancyPatternDiagonalNeSwIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, true, true, false));
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder()
                .occupancy(false, true, true, false)
                .build();
        assertEquals(AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                diagnostic.getLocalOccupancyPattern());
    }

    @Test
    void threeSelectedIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.THREE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, true, true, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.THREE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, true, false, true));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.ALL_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, true, true, true));
    }

    @Test
    void twoCardinalSelectedIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.TWO_CARDINAL_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, true, false, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.TWO_CARDINAL_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, false, true, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.TWO_CARDINAL_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, true, false, true));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.TWO_CARDINAL_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, false, true, true));
        // Diagonals must win over generic two-selected logic.
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NW_SE,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, false, false, true));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, true, true, false));
    }

    @Test
    void oneSelectedIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.ONE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        true, false, false, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.ONE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, true, false, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.ONE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, false, true, false));
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.ONE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, false, false, true));
    }

    @Test
    void noneSelectedIsClassifiedCorrectly() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.NONE_SELECTED,
                AmbiguousBoundaryVertexDiagnostic.classifyOccupancyPattern(
                        false, false, false, false));
    }

    // ------------------------------------------------------------------
    // 15-21 Turn relations
    // ------------------------------------------------------------------

    @Test
    void eastToSouthIsRightTurn() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.EAST,
                        DirectedPixelEdge.Direction.SOUTH));
    }

    @Test
    void southToWestIsRightTurn() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.SOUTH,
                        DirectedPixelEdge.Direction.WEST));
    }

    @Test
    void westToNorthIsRightTurn() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.WEST,
                        DirectedPixelEdge.Direction.NORTH));
    }

    @Test
    void northToEastIsRightTurn() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.NORTH,
                        DirectedPixelEdge.Direction.EAST));
    }

    @Test
    void eastToNorthIsLeftTurn() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.LEFT_TURN,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.EAST,
                        DirectedPixelEdge.Direction.NORTH));
    }

    @Test
    void eastToEastIsStraight() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.STRAIGHT,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.EAST,
                        DirectedPixelEdge.Direction.EAST));
    }

    @Test
    void eastToWestIsReverse() {
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.REVERSE,
                AmbiguousBoundaryVertexDiagnostic.classifyTurnRelation(
                        DirectedPixelEdge.Direction.EAST,
                        DirectedPixelEdge.Direction.WEST));
    }

    // ------------------------------------------------------------------
    // 22 Candidate turn order
    // ------------------------------------------------------------------

    @Test
    void candidateTurnOrderMatchesUnusedCandidateOrder() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(VX - 1, VY, VX, VY);
        final DirectedPixelEdge south = new DirectedPixelEdge(VX, VY, VX, VY + 1);
        final DirectedPixelEdge north = new DirectedPixelEdge(VX, VY, VX, VY - 1);
        final DirectedPixelEdge west = new DirectedPixelEdge(VX, VY, VX - 1, VY);
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder()
                .incomingEdge(incoming)
                .outgoing(Arrays.asList(south, north, west))
                .unused(Arrays.asList(south, north, west))
                .used(Collections.<DirectedPixelEdge>emptyList())
                .rebuildIncident()
                .build();

        assertEquals(Arrays.asList("SOUTH", "NORTH", "WEST"),
                diagnostic.getUnusedOutgoingDirections());
        assertEquals(
                Arrays.asList(
                        AmbiguousBoundaryVertexDiagnostic.RIGHT_TURN,
                        AmbiguousBoundaryVertexDiagnostic.LEFT_TURN,
                        AmbiguousBoundaryVertexDiagnostic.REVERSE),
                diagnostic.getCandidateTurnRelations());
        assertEquals(diagnostic.getUnusedOutgoingEdges().size(),
                diagnostic.getCandidateTurnRelations().size());
    }

    // ------------------------------------------------------------------
    // 23 Equals / hashCode
    // ------------------------------------------------------------------

    @Test
    void equalsAndHashCodeAreDeterministic() {
        final AmbiguousBoundaryVertexDiagnostic a = validBuilder().build();
        final AmbiguousBoundaryVertexDiagnostic b = validBuilder().build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
        assertFalse(a.equals(null));
        assertFalse(a.equals("other"));

        final AmbiguousBoundaryVertexDiagnostic differentStep =
                validBuilder().traversalStep(9).build();
        assertFalse(a.equals(differentStep));
    }

    // ------------------------------------------------------------------
    // 24-25 Exception
    // ------------------------------------------------------------------

    @Test
    void exceptionMessageBeginsWithAmbiguousBoundaryVertex() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder().build();
        final AmbiguousBoundaryVertexException exception =
                new AmbiguousBoundaryVertexException(diagnostic);
        assertTrue(exception.getMessage().startsWith("AMBIGUOUS_BOUNDARY_VERTEX"));
        assertTrue(exception.getMessage().contains("loopNumber=" + diagnostic.getLoopNumber()));
        assertTrue(exception.getMessage().contains(
                "traversalStep=" + diagnostic.getTraversalStep()));
        assertTrue(exception.getMessage().contains(
                "vertex=(" + diagnostic.getVertexX() + "," + diagnostic.getVertexY() + ")"));
        assertTrue(exception.getMessage().contains(
                "occupancyPattern=" + diagnostic.getLocalOccupancyPattern()));
        assertTrue(exception.getMessage().contains(
                "unusedOutgoingEdgeCount=" + diagnostic.getUnusedOutgoingEdges().size()));
        assertTrue(exception.getMessage().contains(
                "unusedOutgoingDirections=" + diagnostic.getUnusedOutgoingDirections()));
        assertTrue(exception.getMessage().contains(
                "candidateTurnRelations=" + diagnostic.getCandidateTurnRelations()));
        assertTrue(exception.getMessage().contains(
                "usedEdgeCountBeforeFailure=" + diagnostic.getUsedEdgeCountBeforeFailure()));
        assertTrue(exception.getMessage().contains(
                "totalExposedEdgeCount=" + diagnostic.getTotalExposedEdgeCount()));
    }

    @Test
    void exceptionExposesTheExactDiagnostic() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = validBuilder().build();
        final AmbiguousBoundaryVertexException exception =
                new AmbiguousBoundaryVertexException(diagnostic);
        assertSame(diagnostic, exception.getDiagnostic());
        assertThrows(IllegalArgumentException.class,
                () -> new AmbiguousBoundaryVertexException(null));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Builder validBuilder() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(VX - 1, VY, VX, VY);
        final DirectedPixelEdge outEast = new DirectedPixelEdge(VX, VY, VX + 1, VY);
        final DirectedPixelEdge outSouth = new DirectedPixelEdge(VX, VY, VX, VY + 1);
        return new Builder()
                .loopNumber(1)
                .traversalStep(3)
                .totalExposedEdgeCount(12)
                .usedEdgeCountBeforeFailure(4)
                .vertex(VX, VY)
                .incomingEdge(incoming)
                .outgoing(Arrays.asList(outEast, outSouth))
                .unused(Arrays.asList(outEast, outSouth))
                .used(Collections.<DirectedPixelEdge>emptyList())
                .occupancy(true, false, false, true)
                .rebuildIncident();
    }

    private static final class Builder {
        private int loopNumber = 1;
        private int traversalStep;
        private int totalExposedEdgeCount;
        private int usedEdgeCountBeforeFailure;
        private int vertexX = VX;
        private int vertexY = VY;
        private DirectedPixelEdge incomingEdge;
        private List<DirectedPixelEdge> allIncidentEdges =
                Collections.<DirectedPixelEdge>emptyList();
        private List<DirectedPixelEdge> outgoingEdges =
                Collections.<DirectedPixelEdge>emptyList();
        private List<DirectedPixelEdge> unusedOutgoingEdges =
                Collections.<DirectedPixelEdge>emptyList();
        private List<DirectedPixelEdge> usedOutgoingEdges =
                Collections.<DirectedPixelEdge>emptyList();
        private boolean northWestSelected;
        private boolean northEastSelected;
        private boolean southWestSelected;
        private boolean southEastSelected;

        Builder loopNumber(final int value) {
            this.loopNumber = value;
            return this;
        }

        Builder traversalStep(final int value) {
            this.traversalStep = value;
            return this;
        }

        Builder totalExposedEdgeCount(final int value) {
            this.totalExposedEdgeCount = value;
            return this;
        }

        Builder usedEdgeCountBeforeFailure(final int value) {
            this.usedEdgeCountBeforeFailure = value;
            return this;
        }

        Builder vertex(final int x, final int y) {
            this.vertexX = x;
            this.vertexY = y;
            return this;
        }

        Builder incomingEdge(final DirectedPixelEdge edge) {
            this.incomingEdge = edge;
            return this;
        }

        Builder outgoing(final List<DirectedPixelEdge> edges) {
            this.outgoingEdges = edges;
            return this;
        }

        Builder unused(final List<DirectedPixelEdge> edges) {
            this.unusedOutgoingEdges = edges;
            return this;
        }

        Builder used(final List<DirectedPixelEdge> edges) {
            this.usedOutgoingEdges = edges;
            return this;
        }

        Builder occupancy(
                final boolean nw,
                final boolean ne,
                final boolean sw,
                final boolean se) {
            this.northWestSelected = nw;
            this.northEastSelected = ne;
            this.southWestSelected = sw;
            this.southEastSelected = se;
            return this;
        }

        Builder rebuildIncident() {
            final List<DirectedPixelEdge> incident = new ArrayList<DirectedPixelEdge>();
            if (incomingEdge != null) {
                incident.add(incomingEdge);
            }
            for (int i = 0; i < outgoingEdges.size(); i++) {
                final DirectedPixelEdge edge = outgoingEdges.get(i);
                if (!incident.contains(edge)) {
                    incident.add(edge);
                }
            }
            this.allIncidentEdges = incident;
            return this;
        }

        AmbiguousBoundaryVertexDiagnostic build() {
            return new AmbiguousBoundaryVertexDiagnostic(
                    loopNumber,
                    traversalStep,
                    totalExposedEdgeCount,
                    usedEdgeCountBeforeFailure,
                    vertexX,
                    vertexY,
                    incomingEdge,
                    allIncidentEdges,
                    outgoingEdges,
                    unusedOutgoingEdges,
                    usedOutgoingEdges,
                    northWestSelected,
                    northEastSelected,
                    southWestSelected,
                    southEastSelected);
        }
    }
}
