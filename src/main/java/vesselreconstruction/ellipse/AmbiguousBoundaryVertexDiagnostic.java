package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable diagnostic snapshot of the first ambiguous boundary lattice vertex
 * encountered during ordered-boundary extraction.
 */
public final class AmbiguousBoundaryVertexDiagnostic {

    public static final String DIAGONAL_NW_SE = "DIAGONAL_NW_SE";
    public static final String DIAGONAL_NE_SW = "DIAGONAL_NE_SW";
    public static final String ALL_SELECTED = "ALL_SELECTED";
    public static final String THREE_SELECTED = "THREE_SELECTED";
    public static final String TWO_CARDINAL_SELECTED = "TWO_CARDINAL_SELECTED";
    public static final String ONE_SELECTED = "ONE_SELECTED";
    public static final String NONE_SELECTED = "NONE_SELECTED";
    public static final String OTHER = "OTHER";

    public static final String STRAIGHT = "STRAIGHT";
    public static final String RIGHT_TURN = "RIGHT_TURN";
    public static final String LEFT_TURN = "LEFT_TURN";
    public static final String REVERSE = "REVERSE";

    public static final String MULTIPLE_UNUSED_OUTGOING_EDGES_AT_CURRENT_VERTEX =
            "MULTIPLE_UNUSED_OUTGOING_EDGES_AT_CURRENT_VERTEX";

    private final int loopNumber;
    private final int traversalStep;
    private final int totalExposedEdgeCount;
    private final int usedEdgeCountBeforeFailure;

    private final int vertexX;
    private final int vertexY;

    private final DirectedPixelEdge incomingEdge;

    private final List<DirectedPixelEdge> allIncidentEdges;
    private final List<DirectedPixelEdge> outgoingEdges;
    private final List<DirectedPixelEdge> unusedOutgoingEdges;
    private final List<DirectedPixelEdge> usedOutgoingEdges;

    private final boolean northWestSelected;
    private final boolean northEastSelected;
    private final boolean southWestSelected;
    private final boolean southEastSelected;

    private final int northWestX;
    private final int northWestY;
    private final int northEastX;
    private final int northEastY;
    private final int southWestX;
    private final int southWestY;
    private final int southEastX;
    private final int southEastY;

    private final String localOccupancyPattern;

    private final String incomingDirection;
    private final List<String> outgoingDirections;
    private final List<String> unusedOutgoingDirections;

    private final List<String> candidateTurnRelations;

    private final String failureInvariant;

    public AmbiguousBoundaryVertexDiagnostic(
            final int loopNumber,
            final int traversalStep,
            final int totalExposedEdgeCount,
            final int usedEdgeCountBeforeFailure,
            final int vertexX,
            final int vertexY,
            final DirectedPixelEdge incomingEdge,
            final List<DirectedPixelEdge> allIncidentEdges,
            final List<DirectedPixelEdge> outgoingEdges,
            final List<DirectedPixelEdge> unusedOutgoingEdges,
            final List<DirectedPixelEdge> usedOutgoingEdges,
            final boolean northWestSelected,
            final boolean northEastSelected,
            final boolean southWestSelected,
            final boolean southEastSelected) {
        if (loopNumber <= 0) {
            throw new IllegalArgumentException("loopNumber must be positive: " + loopNumber);
        }
        if (traversalStep < 0) {
            throw new IllegalArgumentException(
                    "traversalStep must be non-negative: " + traversalStep);
        }
        requireNonNegative("totalExposedEdgeCount", totalExposedEdgeCount);
        requireNonNegative("usedEdgeCountBeforeFailure", usedEdgeCountBeforeFailure);
        if (incomingEdge == null) {
            throw new IllegalArgumentException("incomingEdge must not be null");
        }
        if (incomingEdge.getEndX() != vertexX || incomingEdge.getEndY() != vertexY) {
            throw new IllegalArgumentException(
                    "incomingEdge end must equal the diagnostic vertex: edge end=("
                            + incomingEdge.getEndX() + "," + incomingEdge.getEndY()
                            + "), vertex=(" + vertexX + "," + vertexY + ")");
        }
        if (allIncidentEdges == null) {
            throw new IllegalArgumentException("allIncidentEdges must not be null");
        }
        if (outgoingEdges == null) {
            throw new IllegalArgumentException("outgoingEdges must not be null");
        }
        if (unusedOutgoingEdges == null) {
            throw new IllegalArgumentException("unusedOutgoingEdges must not be null");
        }
        if (usedOutgoingEdges == null) {
            throw new IllegalArgumentException("usedOutgoingEdges must not be null");
        }
        if (unusedOutgoingEdges.size() < 2) {
            throw new IllegalArgumentException(
                    "unusedOutgoingEdges must contain at least two edges: "
                            + unusedOutgoingEdges.size());
        }

        final List<DirectedPixelEdge> incidentCopy =
                copyEdgeList("allIncidentEdges", allIncidentEdges);
        final List<DirectedPixelEdge> outgoingCopy =
                copyEdgeList("outgoingEdges", outgoingEdges);
        final List<DirectedPixelEdge> unusedCopy =
                copyEdgeList("unusedOutgoingEdges", unusedOutgoingEdges);
        final List<DirectedPixelEdge> usedCopy =
                copyEdgeList("usedOutgoingEdges", usedOutgoingEdges);

        for (int i = 0; i < outgoingCopy.size(); i++) {
            final DirectedPixelEdge edge = outgoingCopy.get(i);
            if (edge.getStartX() != vertexX || edge.getStartY() != vertexY) {
                throw new IllegalArgumentException(
                        "every outgoing edge must start at the diagnostic vertex: "
                                + edge);
            }
        }
        for (int i = 0; i < unusedCopy.size(); i++) {
            final DirectedPixelEdge edge = unusedCopy.get(i);
            if (edge.getStartX() != vertexX || edge.getStartY() != vertexY) {
                throw new IllegalArgumentException(
                        "every unused outgoing edge must start at the diagnostic vertex: "
                                + edge);
            }
        }
        for (int i = 0; i < usedCopy.size(); i++) {
            final DirectedPixelEdge edge = usedCopy.get(i);
            if (edge.getStartX() != vertexX || edge.getStartY() != vertexY) {
                throw new IllegalArgumentException(
                        "every used outgoing edge must start at the diagnostic vertex: "
                                + edge);
            }
        }

        validateUsedUnusedPartition(outgoingCopy, unusedCopy, usedCopy);
        validateIncidentCoverage(vertexX, vertexY, incomingEdge, incidentCopy, outgoingCopy);

        this.loopNumber = loopNumber;
        this.traversalStep = traversalStep;
        this.totalExposedEdgeCount = totalExposedEdgeCount;
        this.usedEdgeCountBeforeFailure = usedEdgeCountBeforeFailure;
        this.vertexX = vertexX;
        this.vertexY = vertexY;
        this.incomingEdge = incomingEdge;
        this.allIncidentEdges = Collections.unmodifiableList(incidentCopy);
        this.outgoingEdges = Collections.unmodifiableList(outgoingCopy);
        this.unusedOutgoingEdges = Collections.unmodifiableList(unusedCopy);
        this.usedOutgoingEdges = Collections.unmodifiableList(usedCopy);
        this.northWestSelected = northWestSelected;
        this.northEastSelected = northEastSelected;
        this.southWestSelected = southWestSelected;
        this.southEastSelected = southEastSelected;

        this.northWestX = vertexX - 1;
        this.northWestY = vertexY - 1;
        this.northEastX = vertexX;
        this.northEastY = vertexY - 1;
        this.southWestX = vertexX - 1;
        this.southWestY = vertexY;
        this.southEastX = vertexX;
        this.southEastY = vertexY;

        this.localOccupancyPattern = classifyOccupancyPattern(
                northWestSelected, northEastSelected, southWestSelected, southEastSelected);
        this.incomingDirection = incomingEdge.getDirection().name();
        this.outgoingDirections = Collections.unmodifiableList(
                directionNames(outgoingCopy));
        this.unusedOutgoingDirections = Collections.unmodifiableList(
                directionNames(unusedCopy));
        this.candidateTurnRelations = Collections.unmodifiableList(
                turnRelationsForUnused(incomingEdge.getDirection(), unusedCopy));
        this.failureInvariant = MULTIPLE_UNUSED_OUTGOING_EDGES_AT_CURRENT_VERTEX;
    }

    /**
     * Classifies the four surrounding selected-cell occupancy pattern.
     * Diagonal patterns are checked before generic two-selected logic.
     */
    public static String classifyOccupancyPattern(
            final boolean northWestSelected,
            final boolean northEastSelected,
            final boolean southWestSelected,
            final boolean southEastSelected) {
        if (northWestSelected && southEastSelected
                && !northEastSelected && !southWestSelected) {
            return DIAGONAL_NW_SE;
        }
        if (northEastSelected && southWestSelected
                && !northWestSelected && !southEastSelected) {
            return DIAGONAL_NE_SW;
        }

        int selectedCount = 0;
        if (northWestSelected) {
            selectedCount++;
        }
        if (northEastSelected) {
            selectedCount++;
        }
        if (southWestSelected) {
            selectedCount++;
        }
        if (southEastSelected) {
            selectedCount++;
        }

        if (selectedCount == 4) {
            return ALL_SELECTED;
        }
        if (selectedCount == 3) {
            return THREE_SELECTED;
        }
        if (selectedCount == 2) {
            final boolean cardinal =
                    (northWestSelected && northEastSelected)
                            || (northWestSelected && southWestSelected)
                            || (northEastSelected && southEastSelected)
                            || (southWestSelected && southEastSelected);
            if (cardinal) {
                return TWO_CARDINAL_SELECTED;
            }
            return OTHER;
        }
        if (selectedCount == 1) {
            return ONE_SELECTED;
        }
        if (selectedCount == 0) {
            return NONE_SELECTED;
        }
        return OTHER;
    }

    /**
     * Classifies the outgoing direction relative to incoming travel using
     * Y-down image coordinates and direction order EAST, SOUTH, WEST, NORTH.
     */
    public static String classifyTurnRelation(
            final DirectedPixelEdge.Direction incoming,
            final DirectedPixelEdge.Direction outgoing) {
        if (incoming == null || outgoing == null) {
            throw new IllegalArgumentException("incoming and outgoing must not be null");
        }
        final int delta = (outgoing.ordinal() - incoming.ordinal() + 4) % 4;
        if (delta == 0) {
            return STRAIGHT;
        }
        if (delta == 1) {
            return RIGHT_TURN;
        }
        if (delta == 2) {
            return REVERSE;
        }
        return LEFT_TURN;
    }

    public static int surroundingNorthWestX(final int vertexX) {
        return vertexX - 1;
    }

    public static int surroundingNorthWestY(final int vertexY) {
        return vertexY - 1;
    }

    public static int surroundingNorthEastX(final int vertexX) {
        return vertexX;
    }

    public static int surroundingNorthEastY(final int vertexY) {
        return vertexY - 1;
    }

    public static int surroundingSouthWestX(final int vertexX) {
        return vertexX - 1;
    }

    public static int surroundingSouthWestY(final int vertexY) {
        return vertexY;
    }

    public static int surroundingSouthEastX(final int vertexX) {
        return vertexX;
    }

    public static int surroundingSouthEastY(final int vertexY) {
        return vertexY;
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static List<DirectedPixelEdge> copyEdgeList(
            final String name,
            final List<DirectedPixelEdge> edges) {
        final List<DirectedPixelEdge> copy = new ArrayList<DirectedPixelEdge>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge edge = edges.get(i);
            if (edge == null) {
                throw new IllegalArgumentException(name + " must not contain null");
            }
            copy.add(edge);
        }
        return copy;
    }

    private static void validateUsedUnusedPartition(
            final List<DirectedPixelEdge> outgoing,
            final List<DirectedPixelEdge> unused,
            final List<DirectedPixelEdge> used) {
        final Set<DirectedPixelEdge> unusedSet = new HashSet<DirectedPixelEdge>(unused);
        final Set<DirectedPixelEdge> usedSet = new HashSet<DirectedPixelEdge>(used);
        if (unusedSet.size() != unused.size()) {
            throw new IllegalArgumentException("unusedOutgoingEdges must not contain duplicates");
        }
        if (usedSet.size() != used.size()) {
            throw new IllegalArgumentException("usedOutgoingEdges must not contain duplicates");
        }
        for (int i = 0; i < unused.size(); i++) {
            if (usedSet.contains(unused.get(i))) {
                throw new IllegalArgumentException(
                        "unused and used outgoing lists must be disjoint");
            }
        }
        if (unused.size() + used.size() != outgoing.size()) {
            throw new IllegalArgumentException(
                    "used plus unused outgoing edges must equal outgoingEdges");
        }
        final Set<DirectedPixelEdge> union = new HashSet<DirectedPixelEdge>(unusedSet);
        union.addAll(usedSet);
        if (union.size() != outgoing.size()) {
            throw new IllegalArgumentException(
                    "used plus unused outgoing edges must equal outgoingEdges");
        }
        for (int i = 0; i < outgoing.size(); i++) {
            if (!union.contains(outgoing.get(i))) {
                throw new IllegalArgumentException(
                        "used plus unused outgoing edges must equal outgoingEdges");
            }
        }
    }

    private static void validateIncidentCoverage(
            final int vertexX,
            final int vertexY,
            final DirectedPixelEdge incomingEdge,
            final List<DirectedPixelEdge> incident,
            final List<DirectedPixelEdge> outgoing) {
        boolean incomingFound = false;
        for (int i = 0; i < incident.size(); i++) {
            final DirectedPixelEdge edge = incident.get(i);
            final boolean touches =
                    (edge.getStartX() == vertexX && edge.getStartY() == vertexY)
                            || (edge.getEndX() == vertexX && edge.getEndY() == vertexY);
            if (!touches) {
                throw new IllegalArgumentException(
                        "allIncidentEdges entries must touch the diagnostic vertex: "
                                + edge);
            }
            if (edge.equals(incomingEdge)) {
                incomingFound = true;
            }
        }
        if (!incomingFound) {
            throw new IllegalArgumentException(
                    "allIncidentEdges must contain the incoming edge");
        }
        for (int i = 0; i < outgoing.size(); i++) {
            final DirectedPixelEdge outgoingEdge = outgoing.get(i);
            boolean found = false;
            for (int j = 0; j < incident.size(); j++) {
                if (incident.get(j).equals(outgoingEdge)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException(
                        "allIncidentEdges must contain every outgoing edge: "
                                + outgoingEdge);
            }
        }
    }

    private static List<String> directionNames(final List<DirectedPixelEdge> edges) {
        final List<String> names = new ArrayList<String>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            names.add(edges.get(i).getDirection().name());
        }
        return names;
    }

    private static List<String> turnRelationsForUnused(
            final DirectedPixelEdge.Direction incoming,
            final List<DirectedPixelEdge> unusedOutgoing) {
        final List<String> relations = new ArrayList<String>(unusedOutgoing.size());
        for (int i = 0; i < unusedOutgoing.size(); i++) {
            relations.add(classifyTurnRelation(
                    incoming, unusedOutgoing.get(i).getDirection()));
        }
        return relations;
    }

    public int getLoopNumber() {
        return loopNumber;
    }

    public int getTraversalStep() {
        return traversalStep;
    }

    public int getTotalExposedEdgeCount() {
        return totalExposedEdgeCount;
    }

    public int getUsedEdgeCountBeforeFailure() {
        return usedEdgeCountBeforeFailure;
    }

    public int getVertexX() {
        return vertexX;
    }

    public int getVertexY() {
        return vertexY;
    }

    public DirectedPixelEdge getIncomingEdge() {
        return incomingEdge;
    }

    public List<DirectedPixelEdge> getAllIncidentEdges() {
        return allIncidentEdges;
    }

    public List<DirectedPixelEdge> getOutgoingEdges() {
        return outgoingEdges;
    }

    public List<DirectedPixelEdge> getUnusedOutgoingEdges() {
        return unusedOutgoingEdges;
    }

    public List<DirectedPixelEdge> getUsedOutgoingEdges() {
        return usedOutgoingEdges;
    }

    public boolean isNorthWestSelected() {
        return northWestSelected;
    }

    public boolean isNorthEastSelected() {
        return northEastSelected;
    }

    public boolean isSouthWestSelected() {
        return southWestSelected;
    }

    public boolean isSouthEastSelected() {
        return southEastSelected;
    }

    public int getNorthWestX() {
        return northWestX;
    }

    public int getNorthWestY() {
        return northWestY;
    }

    public int getNorthEastX() {
        return northEastX;
    }

    public int getNorthEastY() {
        return northEastY;
    }

    public int getSouthWestX() {
        return southWestX;
    }

    public int getSouthWestY() {
        return southWestY;
    }

    public int getSouthEastX() {
        return southEastX;
    }

    public int getSouthEastY() {
        return southEastY;
    }

    public String getLocalOccupancyPattern() {
        return localOccupancyPattern;
    }

    public String getIncomingDirection() {
        return incomingDirection;
    }

    public List<String> getOutgoingDirections() {
        return outgoingDirections;
    }

    public List<String> getUnusedOutgoingDirections() {
        return unusedOutgoingDirections;
    }

    public List<String> getCandidateTurnRelations() {
        return candidateTurnRelations;
    }

    public String getFailureInvariant() {
        return failureInvariant;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AmbiguousBoundaryVertexDiagnostic)) {
            return false;
        }
        final AmbiguousBoundaryVertexDiagnostic other =
                (AmbiguousBoundaryVertexDiagnostic) obj;
        return loopNumber == other.loopNumber
                && traversalStep == other.traversalStep
                && totalExposedEdgeCount == other.totalExposedEdgeCount
                && usedEdgeCountBeforeFailure == other.usedEdgeCountBeforeFailure
                && vertexX == other.vertexX
                && vertexY == other.vertexY
                && northWestSelected == other.northWestSelected
                && northEastSelected == other.northEastSelected
                && southWestSelected == other.southWestSelected
                && southEastSelected == other.southEastSelected
                && northWestX == other.northWestX
                && northWestY == other.northWestY
                && northEastX == other.northEastX
                && northEastY == other.northEastY
                && southWestX == other.southWestX
                && southWestY == other.southWestY
                && southEastX == other.southEastX
                && southEastY == other.southEastY
                && Objects.equals(incomingEdge, other.incomingEdge)
                && Objects.equals(allIncidentEdges, other.allIncidentEdges)
                && Objects.equals(outgoingEdges, other.outgoingEdges)
                && Objects.equals(unusedOutgoingEdges, other.unusedOutgoingEdges)
                && Objects.equals(usedOutgoingEdges, other.usedOutgoingEdges)
                && Objects.equals(localOccupancyPattern, other.localOccupancyPattern)
                && Objects.equals(incomingDirection, other.incomingDirection)
                && Objects.equals(outgoingDirections, other.outgoingDirections)
                && Objects.equals(unusedOutgoingDirections, other.unusedOutgoingDirections)
                && Objects.equals(candidateTurnRelations, other.candidateTurnRelations)
                && Objects.equals(failureInvariant, other.failureInvariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(loopNumber),
                Integer.valueOf(traversalStep),
                Integer.valueOf(totalExposedEdgeCount),
                Integer.valueOf(usedEdgeCountBeforeFailure),
                Integer.valueOf(vertexX),
                Integer.valueOf(vertexY),
                incomingEdge,
                allIncidentEdges,
                outgoingEdges,
                unusedOutgoingEdges,
                usedOutgoingEdges,
                Boolean.valueOf(northWestSelected),
                Boolean.valueOf(northEastSelected),
                Boolean.valueOf(southWestSelected),
                Boolean.valueOf(southEastSelected),
                Integer.valueOf(northWestX),
                Integer.valueOf(northWestY),
                Integer.valueOf(northEastX),
                Integer.valueOf(northEastY),
                Integer.valueOf(southWestX),
                Integer.valueOf(southWestY),
                Integer.valueOf(southEastX),
                Integer.valueOf(southEastY),
                localOccupancyPattern,
                incomingDirection,
                outgoingDirections,
                unusedOutgoingDirections,
                candidateTurnRelations,
                failureInvariant);
    }

    @Override
    public String toString() {
        return "AmbiguousBoundaryVertexDiagnostic{"
                + "loopNumber=" + loopNumber
                + ", traversalStep=" + traversalStep
                + ", vertex=(" + vertexX + "," + vertexY + ")"
                + ", incoming=" + incomingDirection
                + ", occupancy=" + localOccupancyPattern
                + ", unusedOutgoing=" + unusedOutgoingDirections
                + ", turns=" + candidateTurnRelations
                + ", usedEdges=" + usedEdgeCountBeforeFailure
                + "/" + totalExposedEdgeCount
                + ", failureInvariant=" + failureInvariant
                + '}';
    }
}
