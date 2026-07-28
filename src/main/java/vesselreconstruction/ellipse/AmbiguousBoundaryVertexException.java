package vesselreconstruction.ellipse;

/**
 * Thrown when ordered-boundary extraction stops at an ambiguous lattice vertex
 * with multiple unused outgoing exposed directed edges.
 */
public final class AmbiguousBoundaryVertexException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final AmbiguousBoundaryVertexDiagnostic diagnostic;

    public AmbiguousBoundaryVertexException(
            final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        super(buildMessage(diagnostic));
        if (diagnostic == null) {
            throw new IllegalArgumentException("diagnostic must not be null");
        }
        this.diagnostic = diagnostic;
    }

    public AmbiguousBoundaryVertexDiagnostic getDiagnostic() {
        return diagnostic;
    }

    private static String buildMessage(final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("diagnostic must not be null");
        }
        return "AMBIGUOUS_BOUNDARY_VERTEX"
                + ": loopNumber=" + diagnostic.getLoopNumber()
                + ", traversalStep=" + diagnostic.getTraversalStep()
                + ", vertex=(" + diagnostic.getVertexX() + "," + diagnostic.getVertexY() + ")"
                + ", incomingEdge=" + diagnostic.getIncomingEdge()
                + ", occupancyPattern=" + diagnostic.getLocalOccupancyPattern()
                + ", unusedOutgoingEdgeCount=" + diagnostic.getUnusedOutgoingEdges().size()
                + ", unusedOutgoingDirections=" + diagnostic.getUnusedOutgoingDirections()
                + ", candidateTurnRelations=" + diagnostic.getCandidateTurnRelations()
                + ", usedEdgeCountBeforeFailure=" + diagnostic.getUsedEdgeCountBeforeFailure()
                + ", totalExposedEdgeCount=" + diagnostic.getTotalExposedEdgeCount();
    }
}
