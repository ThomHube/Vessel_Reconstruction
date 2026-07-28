package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostic comparison of ImageJ core boundary references against the
 * project's expected exposed directed pixel-edge set at an ambiguous vertex.
 */
public final class ImageJBoundaryReferenceResult {

    public enum ReferenceMethod {
        THRESHOLD_TO_SELECTION,
        WAND_FOUR_CONNECTED,
        WAND_EIGHT_CONNECTED
    }

    public enum ReferenceTurnRelation {
        STRAIGHT,
        RIGHT_TURN,
        LEFT_TURN,
        REVERSE,
        NOT_FOUND,
        MULTIPLE_MATCHES
    }

    public static final String REFERENCE_METHODS_AGREE = "REFERENCE_METHODS_AGREE";
    public static final String REFERENCE_METHODS_DISAGREE_AT_DIAGONAL_CONTACT =
            "REFERENCE_METHODS_DISAGREE_AT_DIAGONAL_CONTACT";
    public static final String REFERENCE_CONTOUR_INCOMPLETE = "REFERENCE_CONTOUR_INCOMPLETE";
    public static final String REFERENCE_CONVERSION_FAILED = "REFERENCE_CONVERSION_FAILED";
    public static final String AMBIGUOUS_INCOMING_EDGE_NOT_FOUND =
            "AMBIGUOUS_INCOMING_EDGE_NOT_FOUND";
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * One normalized reference outline produced by an ImageJ core method.
     */
    public static final class ReferenceOutline {
        private final ReferenceMethod method;
        private final int outlineNumber;
        private final List<Point> latticeVertices;
        private final List<DirectedPixelEdge> expandedUnitEdges;
        private final boolean closed;
        private final double signedArea;
        private final double absoluteArea;
        private final int repeatedNonClosingVertexCount;
        private final int distinctVertexCount;
        private final Rectangle latticeBounds;
        private final int expectedExposedEdgeMatchCount;
        private final int missingExpectedExposedEdgeCount;
        private final int extraReferenceEdgeCount;
        private final double expectedExposedEdgeCoverageFraction;
        private final boolean containsAmbiguousVertex;
        private final int ambiguousVertexOccurrenceCount;
        private final boolean matchingIncomingEdgeFound;
        private final int matchingIncomingEdgeOccurrenceCount;
        private final DirectedPixelEdge.Direction successorDirectionAtAmbiguity;
        private final ReferenceTurnRelation successorTurnRelationAtAmbiguity;

        public ReferenceOutline(
                final ReferenceMethod method,
                final int outlineNumber,
                final List<Point> latticeVertices,
                final List<DirectedPixelEdge> expandedUnitEdges,
                final boolean closed,
                final double signedArea,
                final double absoluteArea,
                final int repeatedNonClosingVertexCount,
                final int distinctVertexCount,
                final Rectangle latticeBounds,
                final int expectedExposedEdgeMatchCount,
                final int missingExpectedExposedEdgeCount,
                final int extraReferenceEdgeCount,
                final double expectedExposedEdgeCoverageFraction,
                final boolean containsAmbiguousVertex,
                final int ambiguousVertexOccurrenceCount,
                final boolean matchingIncomingEdgeFound,
                final int matchingIncomingEdgeOccurrenceCount,
                final DirectedPixelEdge.Direction successorDirectionAtAmbiguity,
                final ReferenceTurnRelation successorTurnRelationAtAmbiguity) {
            if (method == null) {
                throw new IllegalArgumentException("method must not be null");
            }
            if (outlineNumber <= 0) {
                throw new IllegalArgumentException(
                        "outlineNumber must be positive: " + outlineNumber);
            }
            if (latticeVertices == null) {
                throw new IllegalArgumentException("latticeVertices must not be null");
            }
            if (expandedUnitEdges == null) {
                throw new IllegalArgumentException("expandedUnitEdges must not be null");
            }
            if (latticeBounds == null) {
                throw new IllegalArgumentException("latticeBounds must not be null");
            }
            if (successorTurnRelationAtAmbiguity == null) {
                throw new IllegalArgumentException(
                        "successorTurnRelationAtAmbiguity must not be null");
            }
            requireFinite("signedArea", signedArea);
            requireFinite("absoluteArea", absoluteArea);
            requireFinite("expectedExposedEdgeCoverageFraction",
                    expectedExposedEdgeCoverageFraction);
            if (absoluteArea < 0.0) {
                throw new IllegalArgumentException(
                        "absoluteArea must be non-negative: " + absoluteArea);
            }
            requireNonNegative("repeatedNonClosingVertexCount", repeatedNonClosingVertexCount);
            requireNonNegative("distinctVertexCount", distinctVertexCount);
            requireNonNegative("expectedExposedEdgeMatchCount", expectedExposedEdgeMatchCount);
            requireNonNegative("missingExpectedExposedEdgeCount",
                    missingExpectedExposedEdgeCount);
            requireNonNegative("extraReferenceEdgeCount", extraReferenceEdgeCount);
            requireNonNegative("ambiguousVertexOccurrenceCount",
                    ambiguousVertexOccurrenceCount);
            requireNonNegative("matchingIncomingEdgeOccurrenceCount",
                    matchingIncomingEdgeOccurrenceCount);
            if (expectedExposedEdgeCoverageFraction < 0.0
                    || expectedExposedEdgeCoverageFraction > 1.0) {
                throw new IllegalArgumentException(
                        "expectedExposedEdgeCoverageFraction must be in [0,1]: "
                                + expectedExposedEdgeCoverageFraction);
            }
            if (matchingIncomingEdgeFound && matchingIncomingEdgeOccurrenceCount <= 0) {
                throw new IllegalArgumentException(
                        "matchingIncomingEdgeFound requires positive occurrence count");
            }
            if (!matchingIncomingEdgeFound && matchingIncomingEdgeOccurrenceCount != 0) {
                throw new IllegalArgumentException(
                        "matchingIncomingEdgeOccurrenceCount must be 0 when not found");
            }
            if (successorTurnRelationAtAmbiguity == ReferenceTurnRelation.NOT_FOUND
                    && matchingIncomingEdgeOccurrenceCount != 0) {
                throw new IllegalArgumentException(
                        "NOT_FOUND requires zero incoming-edge occurrences");
            }
            if (successorTurnRelationAtAmbiguity == ReferenceTurnRelation.MULTIPLE_MATCHES
                    && matchingIncomingEdgeOccurrenceCount < 2) {
                throw new IllegalArgumentException(
                        "MULTIPLE_MATCHES requires at least two incoming-edge occurrences");
            }
            if ((successorTurnRelationAtAmbiguity == ReferenceTurnRelation.STRAIGHT
                    || successorTurnRelationAtAmbiguity == ReferenceTurnRelation.RIGHT_TURN
                    || successorTurnRelationAtAmbiguity == ReferenceTurnRelation.LEFT_TURN
                    || successorTurnRelationAtAmbiguity == ReferenceTurnRelation.REVERSE)
                    && successorDirectionAtAmbiguity == null) {
                throw new IllegalArgumentException(
                        "resolved turn relation requires successorDirectionAtAmbiguity");
            }

            this.method = method;
            this.outlineNumber = outlineNumber;
            this.latticeVertices = copyPoints(latticeVertices);
            this.expandedUnitEdges = copyEdges(expandedUnitEdges);
            this.closed = closed;
            this.signedArea = signedArea;
            this.absoluteArea = absoluteArea;
            this.repeatedNonClosingVertexCount = repeatedNonClosingVertexCount;
            this.distinctVertexCount = distinctVertexCount;
            this.latticeBounds = new Rectangle(latticeBounds);
            this.expectedExposedEdgeMatchCount = expectedExposedEdgeMatchCount;
            this.missingExpectedExposedEdgeCount = missingExpectedExposedEdgeCount;
            this.extraReferenceEdgeCount = extraReferenceEdgeCount;
            this.expectedExposedEdgeCoverageFraction = expectedExposedEdgeCoverageFraction;
            this.containsAmbiguousVertex = containsAmbiguousVertex;
            this.ambiguousVertexOccurrenceCount = ambiguousVertexOccurrenceCount;
            this.matchingIncomingEdgeFound = matchingIncomingEdgeFound;
            this.matchingIncomingEdgeOccurrenceCount = matchingIncomingEdgeOccurrenceCount;
            this.successorDirectionAtAmbiguity = successorDirectionAtAmbiguity;
            this.successorTurnRelationAtAmbiguity = successorTurnRelationAtAmbiguity;
        }

        public ReferenceMethod getMethod() {
            return method;
        }

        public int getOutlineNumber() {
            return outlineNumber;
        }

        public List<Point> getLatticeVertices() {
            return copyPointsExposed(latticeVertices);
        }

        public List<DirectedPixelEdge> getExpandedUnitEdges() {
            return expandedUnitEdges;
        }

        public boolean isClosed() {
            return closed;
        }

        public double getSignedArea() {
            return signedArea;
        }

        public double getAbsoluteArea() {
            return absoluteArea;
        }

        public int getRepeatedNonClosingVertexCount() {
            return repeatedNonClosingVertexCount;
        }

        public int getDistinctVertexCount() {
            return distinctVertexCount;
        }

        public Rectangle getLatticeBounds() {
            return new Rectangle(latticeBounds);
        }

        public int getExpectedExposedEdgeMatchCount() {
            return expectedExposedEdgeMatchCount;
        }

        public int getMissingExpectedExposedEdgeCount() {
            return missingExpectedExposedEdgeCount;
        }

        public int getExtraReferenceEdgeCount() {
            return extraReferenceEdgeCount;
        }

        public double getExpectedExposedEdgeCoverageFraction() {
            return expectedExposedEdgeCoverageFraction;
        }

        public boolean isContainsAmbiguousVertex() {
            return containsAmbiguousVertex;
        }

        public int getAmbiguousVertexOccurrenceCount() {
            return ambiguousVertexOccurrenceCount;
        }

        public boolean isMatchingIncomingEdgeFound() {
            return matchingIncomingEdgeFound;
        }

        public int getMatchingIncomingEdgeOccurrenceCount() {
            return matchingIncomingEdgeOccurrenceCount;
        }

        public DirectedPixelEdge.Direction getSuccessorDirectionAtAmbiguity() {
            return successorDirectionAtAmbiguity;
        }

        public ReferenceTurnRelation getSuccessorTurnRelationAtAmbiguity() {
            return successorTurnRelationAtAmbiguity;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReferenceOutline)) {
                return false;
            }
            final ReferenceOutline other = (ReferenceOutline) obj;
            return method == other.method
                    && outlineNumber == other.outlineNumber
                    && closed == other.closed
                    && Double.compare(signedArea, other.signedArea) == 0
                    && Double.compare(absoluteArea, other.absoluteArea) == 0
                    && repeatedNonClosingVertexCount == other.repeatedNonClosingVertexCount
                    && distinctVertexCount == other.distinctVertexCount
                    && expectedExposedEdgeMatchCount == other.expectedExposedEdgeMatchCount
                    && missingExpectedExposedEdgeCount == other.missingExpectedExposedEdgeCount
                    && extraReferenceEdgeCount == other.extraReferenceEdgeCount
                    && Double.compare(expectedExposedEdgeCoverageFraction,
                    other.expectedExposedEdgeCoverageFraction) == 0
                    && containsAmbiguousVertex == other.containsAmbiguousVertex
                    && ambiguousVertexOccurrenceCount == other.ambiguousVertexOccurrenceCount
                    && matchingIncomingEdgeFound == other.matchingIncomingEdgeFound
                    && matchingIncomingEdgeOccurrenceCount
                    == other.matchingIncomingEdgeOccurrenceCount
                    && successorDirectionAtAmbiguity == other.successorDirectionAtAmbiguity
                    && successorTurnRelationAtAmbiguity == other.successorTurnRelationAtAmbiguity
                    && Objects.equals(latticeVertices, other.latticeVertices)
                    && Objects.equals(expandedUnitEdges, other.expandedUnitEdges)
                    && Objects.equals(latticeBounds, other.latticeBounds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    method,
                    Integer.valueOf(outlineNumber),
                    latticeVertices,
                    expandedUnitEdges,
                    Boolean.valueOf(closed),
                    Double.valueOf(signedArea),
                    Double.valueOf(absoluteArea),
                    Integer.valueOf(repeatedNonClosingVertexCount),
                    Integer.valueOf(distinctVertexCount),
                    latticeBounds,
                    Integer.valueOf(expectedExposedEdgeMatchCount),
                    Integer.valueOf(missingExpectedExposedEdgeCount),
                    Integer.valueOf(extraReferenceEdgeCount),
                    Double.valueOf(expectedExposedEdgeCoverageFraction),
                    Boolean.valueOf(containsAmbiguousVertex),
                    Integer.valueOf(ambiguousVertexOccurrenceCount),
                    Boolean.valueOf(matchingIncomingEdgeFound),
                    Integer.valueOf(matchingIncomingEdgeOccurrenceCount),
                    successorDirectionAtAmbiguity,
                    successorTurnRelationAtAmbiguity);
        }

        @Override
        public String toString() {
            return "ReferenceOutline{"
                    + "method=" + method
                    + ", outlineNumber=" + outlineNumber
                    + ", vertices=" + latticeVertices.size()
                    + ", unitEdges=" + expandedUnitEdges.size()
                    + ", closed=" + closed
                    + ", coverage=" + expectedExposedEdgeCoverageFraction
                    + ", successor=" + successorDirectionAtAmbiguity
                    + ", turn=" + successorTurnRelationAtAmbiguity
                    + '}';
        }
    }

    private final int imageWidth;
    private final int imageHeight;
    private final int selectedPixelCount;
    private final int expectedExposedEdgeCount;
    private final List<DirectedPixelEdge> expectedExposedDirectedEdges;
    private final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic;
    private final List<ReferenceOutline> thresholdToSelectionOutlines;
    private final List<ReferenceOutline> wandFourConnectedOutlines;
    private final List<ReferenceOutline> wandEightConnectedOutlines;
    private final boolean sourceMaskUnchanged;
    private final String overallDiagnosticConclusion;

    public ImageJBoundaryReferenceResult(
            final int imageWidth,
            final int imageHeight,
            final int selectedPixelCount,
            final int expectedExposedEdgeCount,
            final List<DirectedPixelEdge> expectedExposedDirectedEdges,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic,
            final List<ReferenceOutline> thresholdToSelectionOutlines,
            final List<ReferenceOutline> wandFourConnectedOutlines,
            final List<ReferenceOutline> wandEightConnectedOutlines,
            final boolean sourceMaskUnchanged,
            final String overallDiagnosticConclusion) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (selectedPixelCount <= 0) {
            throw new IllegalArgumentException(
                    "selectedPixelCount must be positive: " + selectedPixelCount);
        }
        if (expectedExposedDirectedEdges == null) {
            throw new IllegalArgumentException(
                    "expectedExposedDirectedEdges must not be null");
        }
        if (expectedExposedEdgeCount != expectedExposedDirectedEdges.size()) {
            throw new IllegalArgumentException(
                    "expectedExposedEdgeCount must match expected edge list size: count="
                            + expectedExposedEdgeCount + ", list="
                            + expectedExposedDirectedEdges.size());
        }
        if (ambiguityDiagnostic == null) {
            throw new IllegalArgumentException("ambiguityDiagnostic must not be null");
        }
        if (thresholdToSelectionOutlines == null) {
            throw new IllegalArgumentException(
                    "thresholdToSelectionOutlines must not be null");
        }
        if (wandFourConnectedOutlines == null) {
            throw new IllegalArgumentException(
                    "wandFourConnectedOutlines must not be null");
        }
        if (wandEightConnectedOutlines == null) {
            throw new IllegalArgumentException(
                    "wandEightConnectedOutlines must not be null");
        }
        if (!sourceMaskUnchanged) {
            throw new IllegalArgumentException(
                    "sourceMaskUnchanged must be true for a successfully constructed result");
        }
        if (overallDiagnosticConclusion == null || overallDiagnosticConclusion.isEmpty()) {
            throw new IllegalArgumentException(
                    "overallDiagnosticConclusion must not be null or empty");
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.selectedPixelCount = selectedPixelCount;
        this.expectedExposedEdgeCount = expectedExposedEdgeCount;
        this.expectedExposedDirectedEdges = copyEdges(expectedExposedDirectedEdges);
        this.ambiguityDiagnostic = ambiguityDiagnostic;
        this.thresholdToSelectionOutlines =
                Collections.unmodifiableList(new ArrayList<ReferenceOutline>(
                        thresholdToSelectionOutlines));
        this.wandFourConnectedOutlines =
                Collections.unmodifiableList(new ArrayList<ReferenceOutline>(
                        wandFourConnectedOutlines));
        this.wandEightConnectedOutlines =
                Collections.unmodifiableList(new ArrayList<ReferenceOutline>(
                        wandEightConnectedOutlines));
        this.sourceMaskUnchanged = true;
        this.overallDiagnosticConclusion = overallDiagnosticConclusion;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getSelectedPixelCount() {
        return selectedPixelCount;
    }

    public int getExpectedExposedEdgeCount() {
        return expectedExposedEdgeCount;
    }

    public List<DirectedPixelEdge> getExpectedExposedDirectedEdges() {
        return expectedExposedDirectedEdges;
    }

    public AmbiguousBoundaryVertexDiagnostic getAmbiguityDiagnostic() {
        return ambiguityDiagnostic;
    }

    public List<ReferenceOutline> getThresholdToSelectionOutlines() {
        return thresholdToSelectionOutlines;
    }

    public List<ReferenceOutline> getWandFourConnectedOutlines() {
        return wandFourConnectedOutlines;
    }

    public List<ReferenceOutline> getWandEightConnectedOutlines() {
        return wandEightConnectedOutlines;
    }

    public boolean isSourceMaskUnchanged() {
        return sourceMaskUnchanged;
    }

    public String getOverallDiagnosticConclusion() {
        return overallDiagnosticConclusion;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ImageJBoundaryReferenceResult)) {
            return false;
        }
        final ImageJBoundaryReferenceResult other = (ImageJBoundaryReferenceResult) obj;
        return imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && selectedPixelCount == other.selectedPixelCount
                && expectedExposedEdgeCount == other.expectedExposedEdgeCount
                && sourceMaskUnchanged == other.sourceMaskUnchanged
                && Objects.equals(expectedExposedDirectedEdges, other.expectedExposedDirectedEdges)
                && Objects.equals(ambiguityDiagnostic, other.ambiguityDiagnostic)
                && Objects.equals(thresholdToSelectionOutlines, other.thresholdToSelectionOutlines)
                && Objects.equals(wandFourConnectedOutlines, other.wandFourConnectedOutlines)
                && Objects.equals(wandEightConnectedOutlines, other.wandEightConnectedOutlines)
                && Objects.equals(overallDiagnosticConclusion, other.overallDiagnosticConclusion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(selectedPixelCount),
                Integer.valueOf(expectedExposedEdgeCount),
                expectedExposedDirectedEdges,
                ambiguityDiagnostic,
                thresholdToSelectionOutlines,
                wandFourConnectedOutlines,
                wandEightConnectedOutlines,
                Boolean.valueOf(sourceMaskUnchanged),
                overallDiagnosticConclusion);
    }

    @Override
    public String toString() {
        return "ImageJBoundaryReferenceResult{"
                + "size=" + imageWidth + "x" + imageHeight
                + ", selected=" + selectedPixelCount
                + ", expectedEdges=" + expectedExposedEdgeCount
                + ", tts=" + thresholdToSelectionOutlines.size()
                + ", wand4=" + wandFourConnectedOutlines.size()
                + ", wand8=" + wandEightConnectedOutlines.size()
                + ", conclusion=" + overallDiagnosticConclusion
                + '}';
    }

    private static void requireFinite(final String name, final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static List<DirectedPixelEdge> copyEdges(final List<DirectedPixelEdge> edges) {
        final List<DirectedPixelEdge> copy = new ArrayList<DirectedPixelEdge>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge edge = edges.get(i);
            if (edge == null) {
                throw new IllegalArgumentException("edge list must not contain null");
            }
            copy.add(edge);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Point> copyPoints(final List<Point> points) {
        final List<Point> copy = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            if (p == null) {
                throw new IllegalArgumentException("latticeVertices must not contain null");
            }
            copy.add(new Point(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Point> copyPointsExposed(final List<Point> points) {
        final List<Point> copy = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            copy.add(new Point(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }
}
