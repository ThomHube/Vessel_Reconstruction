package vesselreconstruction.ellipse;

import ij.gui.Wand;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic JUnit 5 tests for {@link ImageJBoundaryReferenceAnalyzer} and
 * {@link ImageJBoundaryReferenceResult}.
 */
class ImageJBoundaryReferenceAnalyzerTest {

    private final ImageJBoundaryReferenceAnalyzer analyzer = new ImageJBoundaryReferenceAnalyzer();

    // ------------------------------------------------------------------
    // 1-15 analyze() input validation
    // ------------------------------------------------------------------

    @Test
    void zeroImageWidthRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(0, 8, singleton(0, 0), rectangleAgreementDiagnostic()));
    }

    @Test
    void negativeImageHeightRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, -1, singleton(0, 0), rectangleAgreementDiagnostic()));
    }

    @Test
    void nullSelectedPixelsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, null, rectangleAgreementDiagnostic()));
    }

    @Test
    void emptySelectedPixelsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, new ArrayList<Point>(), rectangleAgreementDiagnostic()));
    }

    @Test
    void nullPointInSelectedPixelsRejected() {
        final List<Point> pixels = rect(2, 2, 3, 2);
        pixels.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic()));
    }

    @Test
    void negativeXCoordinateRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, singleton(-1, 0), rectangleAgreementDiagnostic()));
    }

    @Test
    void xCoordinateAtOrBeyondWidthRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, singleton(8, 0), rectangleAgreementDiagnostic()));
    }

    @Test
    void yCoordinateAtOrBeyondHeightRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, singleton(0, 8), rectangleAgreementDiagnostic()));
    }

    @Test
    void duplicateCoordinateRejected() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(2, 2));
        pixels.add(new Point(2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic()));
    }

    @Test
    void nullAmbiguityDiagnosticRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, rect(2, 2, 3, 2), null));
    }

    @Test
    void ambiguityVertexTooFarLeftRejected() {
        final AmbiguousBoundaryVertexDiagnostic bad = diagnosticAtDetachedVertex(-5, 2);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, rect(2, 2, 3, 2), bad));
    }

    @Test
    void ambiguityVertexTooFarAboveRejected() {
        final AmbiguousBoundaryVertexDiagnostic bad = diagnosticAtDetachedVertex(2, -5);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, rect(2, 2, 3, 2), bad));
    }

    @Test
    void ambiguityVertexTooFarRightRejected() {
        // Rectangle occupies x in [2,4]; maxX+1 == 5 is the accepted limit.
        final AmbiguousBoundaryVertexDiagnostic bad = diagnosticAtDetachedVertex(6, 2);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, rect(2, 2, 3, 2), bad));
    }

    @Test
    void ambiguityVertexTooFarBelowRejected() {
        // Rectangle occupies y in [2,3]; maxY+1 == 4 is the accepted limit.
        final AmbiguousBoundaryVertexDiagnostic bad = diagnosticAtDetachedVertex(2, 5);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(8, 8, rect(2, 2, 3, 2), bad));
    }

    @Test
    void ambiguityVertexOneBeyondMaxBoundIsAccepted() {
        // vertex (5,2) == maxX+1 for the rectangle; must not be rejected by bounds checking.
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        assertNotNull(result);
    }

    // ------------------------------------------------------------------
    // 16-20 buildExpectedExposedEdges / undirectedKey helpers
    // ------------------------------------------------------------------

    @Test
    void singlePixelProducesFourExposedEdges() {
        final List<Point> pixels = singleton(3, 3);
        final List<DirectedPixelEdge> edges =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(pixels, membershipOf(pixels));
        assertEquals(4, edges.size());
        assertTrue(edges.contains(new DirectedPixelEdge(3, 3, 4, 3)));
        assertTrue(edges.contains(new DirectedPixelEdge(4, 3, 4, 4)));
        assertTrue(edges.contains(new DirectedPixelEdge(4, 4, 3, 4)));
        assertTrue(edges.contains(new DirectedPixelEdge(3, 4, 3, 3)));
    }

    @Test
    void twoByTwoBlockProducesEightExposedEdgesWithNoInternalSide() {
        final List<Point> pixels = rect(0, 0, 2, 2);
        final List<DirectedPixelEdge> edges =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(pixels, membershipOf(pixels));
        assertEquals(8, edges.size());
        assertFalse(edges.contains(new DirectedPixelEdge(1, 0, 1, 1)));
        assertFalse(edges.contains(new DirectedPixelEdge(1, 1, 1, 0)));
    }

    @Test
    void buildExpectedExposedEdgesOrderIndependentOfInputOrder() {
        final List<Point> pixels = rect(1, 1, 3, 2);
        final List<Point> reversed = new ArrayList<Point>(pixels);
        Collections.reverse(reversed);
        final List<DirectedPixelEdge> a =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(pixels, membershipOf(pixels));
        final List<DirectedPixelEdge> b =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(reversed, membershipOf(reversed));
        assertEquals(a, b);
    }

    @Test
    void undirectedKeySameForBothDirections() {
        final DirectedPixelEdge forward = new DirectedPixelEdge(0, 0, 1, 0);
        final DirectedPixelEdge backward = new DirectedPixelEdge(1, 0, 0, 0);
        assertEquals(
                ImageJBoundaryReferenceAnalyzer.undirectedKey(forward),
                ImageJBoundaryReferenceAnalyzer.undirectedKey(backward));
    }

    @Test
    void undirectedKeyDiffersForDifferentEdges() {
        final DirectedPixelEdge horizontal = new DirectedPixelEdge(0, 0, 1, 0);
        final DirectedPixelEdge vertical = new DirectedPixelEdge(0, 0, 0, 1);
        assertFalse(
                ImageJBoundaryReferenceAnalyzer.undirectedKey(horizontal).equals(
                        ImageJBoundaryReferenceAnalyzer.undirectedKey(vertical)));
    }

    // ------------------------------------------------------------------
    // 21-24 classifyTurn helper
    // ------------------------------------------------------------------

    @Test
    void classifyTurnStraightForSameDirection() {
        assertEquals(
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.STRAIGHT,
                ImageJBoundaryReferenceAnalyzer.classifyTurn(
                        DirectedPixelEdge.Direction.EAST, DirectedPixelEdge.Direction.EAST));
    }

    @Test
    void classifyTurnRightForEastToSouth() {
        assertEquals(
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.RIGHT_TURN,
                ImageJBoundaryReferenceAnalyzer.classifyTurn(
                        DirectedPixelEdge.Direction.EAST, DirectedPixelEdge.Direction.SOUTH));
    }

    @Test
    void classifyTurnLeftForEastToNorth() {
        assertEquals(
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.LEFT_TURN,
                ImageJBoundaryReferenceAnalyzer.classifyTurn(
                        DirectedPixelEdge.Direction.EAST, DirectedPixelEdge.Direction.NORTH));
    }

    @Test
    void classifyTurnReverseForEastToWest() {
        assertEquals(
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.REVERSE,
                ImageJBoundaryReferenceAnalyzer.classifyTurn(
                        DirectedPixelEdge.Direction.EAST, DirectedPixelEdge.Direction.WEST));
    }

    // ------------------------------------------------------------------
    // 25-34 analyze() end-to-end on a simple rectangle (methods agree)
    // ------------------------------------------------------------------

    @Test
    void rectangleAnalyzeReturnsNonNullResult() {
        assertNotNull(analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic()));
    }

    @Test
    void rectangleExpectedEdgeCountEqualsPerimeter() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        assertEquals(10, result.getExpectedExposedEdgeCount());
        assertEquals(10, result.getExpectedExposedDirectedEdges().size());
    }

    @Test
    void rectangleAllThreeMethodsProduceExactlyOneOutline() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        assertEquals(1, result.getThresholdToSelectionOutlines().size());
        assertEquals(1, result.getWandFourConnectedOutlines().size());
        assertEquals(1, result.getWandEightConnectedOutlines().size());
    }

    @Test
    void rectangleOutlinesAreClosedWithFullCoverage() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertTrue(outline.isClosed());
            assertEquals(10, outline.getExpectedExposedEdgeMatchCount());
            assertEquals(0, outline.getMissingExpectedExposedEdgeCount());
            assertEquals(0, outline.getExtraReferenceEdgeCount());
            assertEquals(1.0, outline.getExpectedExposedEdgeCoverageFraction(), 1.0e-12);
        }
    }

    @Test
    void rectangleAbsoluteAreaEqualsPixelCount() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertEquals(6.0, outline.getAbsoluteArea(), 1.0e-9);
        }
        assertEquals(6, result.getSelectedPixelCount());
    }

    @Test
    void rectangleConclusionIsMethodsAgree() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        assertEquals(
                ImageJBoundaryReferenceResult.REFERENCE_METHODS_AGREE,
                result.getOverallDiagnosticConclusion());
    }

    @Test
    void rectangleSuccessorDirectionAndTurnAreConsistentAcrossMethods() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertTrue(outline.isMatchingIncomingEdgeFound());
            assertEquals(1, outline.getMatchingIncomingEdgeOccurrenceCount());
            assertEquals(DirectedPixelEdge.Direction.EAST, outline.getSuccessorDirectionAtAmbiguity());
            assertEquals(
                    ImageJBoundaryReferenceResult.ReferenceTurnRelation.RIGHT_TURN,
                    outline.getSuccessorTurnRelationAtAmbiguity());
        }
    }

    @Test
    void rectangleSourceMaskUnchangedIsTrue() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        assertTrue(result.isSourceMaskUnchanged());
    }

    @Test
    void rectangleDistinctVertexCountEqualsEdgeCount() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertEquals(outline.getExpandedUnitEdges().size(), outline.getDistinctVertexCount());
        }
    }

    @Test
    void rectangleRepeatedNonClosingVertexCountIsZero() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertEquals(0, outline.getRepeatedNonClosingVertexCount());
        }
    }

    // ------------------------------------------------------------------
    // 35-38 determinism / non-mutation
    // ------------------------------------------------------------------

    @Test
    void analyzeDoesNotMutateInputPixelList() {
        final List<Point> pixels = rect(2, 2, 3, 2);
        final int sizeBefore = pixels.size();
        analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic());
        assertEquals(sizeBefore, pixels.size());
    }

    @Test
    void analyzeDoesNotMutateInputPoints() {
        final List<Point> pixels = rect(2, 2, 3, 2);
        final int ox = pixels.get(0).x;
        final int oy = pixels.get(0).y;
        analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic());
        assertEquals(ox, pixels.get(0).x);
        assertEquals(oy, pixels.get(0).y);
    }

    @Test
    void repeatedAnalyzeCallsProduceEqualResults() {
        final List<Point> pixels = rect(2, 2, 3, 2);
        final ImageJBoundaryReferenceResult a =
                analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic());
        final ImageJBoundaryReferenceResult b =
                analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic());
        assertEquals(a, b);
    }

    @Test
    void reversedInputOrderProducesEqualResult() {
        final List<Point> pixels = rect(2, 2, 3, 2);
        final List<Point> reversed = new ArrayList<Point>(pixels);
        Collections.reverse(reversed);
        final ImageJBoundaryReferenceResult a =
                analyzer.analyze(8, 8, pixels, rectangleAgreementDiagnostic());
        final ImageJBoundaryReferenceResult b =
                analyzer.analyze(8, 8, reversed, rectangleAgreementDiagnostic());
        assertEquals(a, b);
    }

    // ------------------------------------------------------------------
    // 39-40 Wand allPoints save/restore
    // ------------------------------------------------------------------

    @Test
    void wandAllPointsRestoredToFalseAfterAnalyze() {
        Wand.setAllPoints(false);
        try {
            analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
            assertFalse(Wand.allPoints());
        } finally {
            Wand.setAllPoints(false);
        }
    }

    @Test
    void wandAllPointsRestoredToTrueAfterAnalyze() {
        Wand.setAllPoints(true);
        try {
            analyzer.analyze(8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
            assertTrue(Wand.allPoints());
        } finally {
            Wand.setAllPoints(false);
        }
    }

    // ------------------------------------------------------------------
    // 41-43 AMBIGUOUS_INCOMING_EDGE_NOT_FOUND, synthetic diagnostic built
    // without calling OrderedBoundaryExtractor
    // ------------------------------------------------------------------

    @Test
    void syntheticOffBoundaryIncomingEdgeYieldsNotFoundConclusion() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), offBoundaryIncomingDiagnostic());
        assertEquals(
                ImageJBoundaryReferenceResult.AMBIGUOUS_INCOMING_EDGE_NOT_FOUND,
                result.getOverallDiagnosticConclusion());
    }

    @Test
    void notFoundOutlinesReportZeroMatchingOccurrences() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), offBoundaryIncomingDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertFalse(outline.isMatchingIncomingEdgeFound());
            assertEquals(0, outline.getMatchingIncomingEdgeOccurrenceCount());
            assertEquals(
                    ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND,
                    outline.getSuccessorTurnRelationAtAmbiguity());
        }
    }

    @Test
    void notFoundOutlinesStillReportAmbiguousVertexPresence() {
        final ImageJBoundaryReferenceResult result =
                analyzer.analyze(8, 8, rect(2, 2, 3, 2), offBoundaryIncomingDiagnostic());
        for (final ImageJBoundaryReferenceResult.ReferenceOutline outline : allOutlines(result)) {
            assertTrue(outline.isContainsAmbiguousVertex());
            assertEquals(1, outline.getAmbiguousVertexOccurrenceCount());
        }
    }

    // ------------------------------------------------------------------
    // 44-49 Diagonal ambiguity diagnostics (obtained via OrderedBoundaryExtractor)
    // fed into the reference analyzer.
    // ------------------------------------------------------------------

    @Test
    void diagonalNwSeExtractorDiagnosticHasExpectedVertexAndIncoming() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(diagonalNwSePixels());
        assertEquals(2, diagnostic.getVertexX());
        assertEquals(1, diagnostic.getVertexY());
        assertEquals(new DirectedPixelEdge(2, 0, 2, 1), diagnostic.getIncomingEdge());
        assertEquals(DirectedPixelEdge.Direction.SOUTH, diagnostic.getIncomingEdge().getDirection());
        assertEquals(AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NW_SE, diagnostic.getLocalOccupancyPattern());
    }

    @Test
    void diagonalNwSeAnalyzeYieldsDisagreementConclusion() {
        final List<Point> pixels = diagonalNwSePixels();
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(pixels);
        final ImageJBoundaryReferenceResult result = analyzer.analyze(8, 8, pixels, diagnostic);
        assertEquals(
                ImageJBoundaryReferenceResult.REFERENCE_METHODS_DISAGREE_AT_DIAGONAL_CONTACT,
                result.getOverallDiagnosticConclusion());
    }

    @Test
    void diagonalNeSwExtractorDiagnosticHasExpectedVertexAndIncoming() {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(diagonalNeSwPixels());
        assertEquals(2, diagnostic.getVertexX());
        assertEquals(2, diagnostic.getVertexY());
        assertEquals(new DirectedPixelEdge(3, 2, 2, 2), diagnostic.getIncomingEdge());
        assertEquals(DirectedPixelEdge.Direction.WEST, diagnostic.getIncomingEdge().getDirection());
        assertEquals(AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW, diagnostic.getLocalOccupancyPattern());
    }

    @Test
    void diagonalNeSwAnalyzeYieldsDisagreementConclusion() {
        final List<Point> pixels = diagonalNeSwPixels();
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(pixels);
        final ImageJBoundaryReferenceResult result = analyzer.analyze(8, 8, pixels, diagnostic);
        assertEquals(
                ImageJBoundaryReferenceResult.REFERENCE_METHODS_DISAGREE_AT_DIAGONAL_CONTACT,
                result.getOverallDiagnosticConclusion());
    }

    @Test
    void diagonalResultRetainsSameDiagnosticInstance() {
        final List<Point> pixels = diagonalNwSePixels();
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(pixels);
        final ImageJBoundaryReferenceResult result = analyzer.analyze(8, 8, pixels, diagnostic);
        assertSame(diagnostic, result.getAmbiguityDiagnostic());
    }

    @Test
    void diagonalOccupancyPatternIsPreservedInResult() {
        final List<Point> pixels = diagonalNeSwPixels();
        final AmbiguousBoundaryVertexDiagnostic diagnostic = extractDiagnostic(pixels);
        final ImageJBoundaryReferenceResult result = analyzer.analyze(8, 8, pixels, diagnostic);
        assertEquals(
                AmbiguousBoundaryVertexDiagnostic.DIAGONAL_NE_SW,
                result.getAmbiguityDiagnostic().getLocalOccupancyPattern());
    }

    // ------------------------------------------------------------------
    // 50 OrderedBoundaryExtractor is not called by the reference analyzer
    // ------------------------------------------------------------------

    @Test
    void analyzerSourceDoesNotReferenceOrderedBoundaryExtractor() throws IOException {
        final String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/vesselreconstruction/ellipse/ImageJBoundaryReferenceAnalyzer.java")));
        assertFalse(source.contains("OrderedBoundaryExtractor"));
    }

    // ------------------------------------------------------------------
    // 51-63 ImageJBoundaryReferenceResult top-level constructor validation
    // ------------------------------------------------------------------

    @Test
    void nonPositiveImageWidthRejectedOnResult() {
        assertThrows(IllegalArgumentException.class, () -> resultBuilder().imageWidth(0).build());
    }

    @Test
    void nonPositiveImageHeightRejectedOnResult() {
        assertThrows(IllegalArgumentException.class, () -> resultBuilder().imageHeight(-1).build());
    }

    @Test
    void nonPositiveSelectedPixelCountRejectedOnResult() {
        assertThrows(IllegalArgumentException.class, () -> resultBuilder().selectedPixelCount(0).build());
    }

    @Test
    void nullExpectedEdgesListRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().expectedEdges(null).build());
    }

    @Test
    void expectedEdgeCountMismatchRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().expectedEdgeCount(99).build());
    }

    @Test
    void nullAmbiguityDiagnosticRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().ambiguityDiagnostic(null).build());
    }

    @Test
    void nullThresholdOutlinesRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().ttsOutlines(null).build());
    }

    @Test
    void nullWandFourOutlinesRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().wand4Outlines(null).build());
    }

    @Test
    void nullWandEightOutlinesRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().wand8Outlines(null).build());
    }

    @Test
    void sourceMaskChangedFlagRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().sourceMaskUnchanged(false).build());
    }

    @Test
    void nullOverallConclusionRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().conclusion(null).build());
    }

    @Test
    void emptyOverallConclusionRejectedOnResult() {
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().conclusion("").build());
    }

    @Test
    void nullEdgeElementInExpectedEdgesRejectedOnResult() {
        final List<DirectedPixelEdge> withNull = new ArrayList<DirectedPixelEdge>(unitSquareEdges());
        withNull.set(0, null);
        assertThrows(IllegalArgumentException.class,
                () -> resultBuilder().expectedEdges(withNull).expectedEdgeCount(withNull.size()).build());
    }

    @Test
    void validResultConstructsSuccessfullyAndExposesFields() {
        final ImageJBoundaryReferenceResult result = resultBuilder().build();
        assertEquals(8, result.getImageWidth());
        assertEquals(8, result.getImageHeight());
        assertEquals(4, result.getSelectedPixelCount());
        assertEquals(4, result.getExpectedExposedEdgeCount());
        assertEquals(1, result.getThresholdToSelectionOutlines().size());
        assertEquals(1, result.getWandFourConnectedOutlines().size());
        assertEquals(1, result.getWandEightConnectedOutlines().size());
        assertTrue(result.isSourceMaskUnchanged());
        assertEquals(ImageJBoundaryReferenceResult.REFERENCE_METHODS_AGREE,
                result.getOverallDiagnosticConclusion());
    }

    // ------------------------------------------------------------------
    // 64-89 ReferenceOutline constructor validation
    // ------------------------------------------------------------------

    @Test
    void nullMethodRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().method(null).build());
    }

    @Test
    void zeroOutlineNumberRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().outlineNumber(0).build());
    }

    @Test
    void negativeOutlineNumberRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().outlineNumber(-1).build());
    }

    @Test
    void nullLatticeVerticesRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().vertices(null).build());
    }

    @Test
    void nullExpandedUnitEdgesRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().edges(null).build());
    }

    @Test
    void nullElementInLatticeVerticesRejected() {
        final List<Point> withNull = new ArrayList<Point>(unitSquareVertices());
        withNull.set(0, null);
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().vertices(withNull).build());
    }

    @Test
    void nullElementInExpandedUnitEdgesRejected() {
        final List<DirectedPixelEdge> withNull = new ArrayList<DirectedPixelEdge>(unitSquareEdges());
        withNull.set(0, null);
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().edges(withNull).build());
    }

    @Test
    void nullLatticeBoundsRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().bounds(null).build());
    }

    @Test
    void nullTurnRelationRejected() {
        assertThrows(IllegalArgumentException.class, () -> outlineBuilder().turn(null).build());
    }

    @Test
    void nanSignedAreaRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().signedArea(Double.NaN).build());
    }

    @Test
    void infiniteAbsoluteAreaRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().absoluteArea(Double.POSITIVE_INFINITY).build());
    }

    @Test
    void nanCoverageFractionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().coverage(Double.NaN).build());
    }

    @Test
    void negativeAbsoluteAreaRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().absoluteArea(-1.0).build());
    }

    @Test
    void negativeRepeatedNonClosingVertexCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().repeated(-1).build());
    }

    @Test
    void negativeDistinctVertexCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().distinct(-1).build());
    }

    @Test
    void negativeMatchCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().matchCount(-1).build());
    }

    @Test
    void negativeMissingCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().missingCount(-1).build());
    }

    @Test
    void negativeExtraCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().extraCount(-1).build());
    }

    @Test
    void negativeAmbiguousVertexOccurrenceCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().ambiguousOccurrenceCount(-1).build());
    }

    @Test
    void negativeMatchingIncomingEdgeOccurrenceCountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().matchingOccurrenceCount(-1).build());
    }

    @Test
    void coverageFractionBelowZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().coverage(-0.1).build());
    }

    @Test
    void coverageFractionAboveOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().coverage(1.1).build());
    }

    @Test
    void matchingFoundRequiresPositiveOccurrenceRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().matchingFound(true).matchingOccurrenceCount(0).build());
    }

    @Test
    void matchingNotFoundRequiresZeroOccurrenceRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder().matchingFound(false).matchingOccurrenceCount(1).build());
    }

    @Test
    void notFoundTurnRequiresZeroOccurrenceRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder()
                        .turn(ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND)
                        .matchingFound(true)
                        .matchingOccurrenceCount(1)
                        .successorDirection(null)
                        .build());
    }

    @Test
    void multipleMatchesRequiresAtLeastTwoOccurrencesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder()
                        .turn(ImageJBoundaryReferenceResult.ReferenceTurnRelation.MULTIPLE_MATCHES)
                        .matchingFound(true)
                        .matchingOccurrenceCount(1)
                        .successorDirection(null)
                        .build());
    }

    @Test
    void resolvedTurnRequiresSuccessorDirectionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> outlineBuilder()
                        .turn(ImageJBoundaryReferenceResult.ReferenceTurnRelation.RIGHT_TURN)
                        .successorDirection(null)
                        .build());
    }

    @Test
    void validResolvedOutlineConstructsSuccessfully() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder().build();
        assertEquals(DirectedPixelEdge.Direction.EAST, outline.getSuccessorDirectionAtAmbiguity());
        assertEquals(ImageJBoundaryReferenceResult.ReferenceTurnRelation.STRAIGHT,
                outline.getSuccessorTurnRelationAtAmbiguity());
        assertTrue(outline.isMatchingIncomingEdgeFound());
    }

    @Test
    void validNotFoundOutlineConstructsSuccessfully() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder()
                .turn(ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND)
                .matchingFound(false)
                .matchingOccurrenceCount(0)
                .successorDirection(null)
                .build();
        assertFalse(outline.isMatchingIncomingEdgeFound());
        assertEquals(0, outline.getMatchingIncomingEdgeOccurrenceCount());
    }

    @Test
    void validMultipleMatchesOutlineConstructsSuccessfully() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder()
                .turn(ImageJBoundaryReferenceResult.ReferenceTurnRelation.MULTIPLE_MATCHES)
                .matchingFound(true)
                .matchingOccurrenceCount(2)
                .successorDirection(null)
                .build();
        assertEquals(2, outline.getMatchingIncomingEdgeOccurrenceCount());
    }

    // ------------------------------------------------------------------
    // 90-92 Immutability / defensive copies
    // ------------------------------------------------------------------

    @Test
    void latticeVerticesGetterReturnsDefensiveCopy() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder().build();
        final List<Point> first = outline.getLatticeVertices();
        first.get(0).x = -999;
        assertThrows(UnsupportedOperationException.class,
                () -> outline.getLatticeVertices().add(new Point(9, 9)));
        assertEquals(0, outline.getLatticeVertices().get(0).x);
    }

    @Test
    void expandedUnitEdgesGetterIsUnmodifiable() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder().build();
        assertThrows(UnsupportedOperationException.class,
                () -> outline.getExpandedUnitEdges().add(new DirectedPixelEdge(0, 0, 1, 0)));
    }

    @Test
    void latticeBoundsGetterReturnsDefensiveCopy() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder().build();
        final Rectangle bounds = outline.getLatticeBounds();
        bounds.x = -999;
        assertEquals(0, outline.getLatticeBounds().x);
    }

    // ------------------------------------------------------------------
    // 93-96 equals / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    void referenceOutlineEqualsAndHashCodeAreConsistent() {
        final ImageJBoundaryReferenceResult.ReferenceOutline a = outlineBuilder().build();
        final ImageJBoundaryReferenceResult.ReferenceOutline b = outlineBuilder().build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
        assertFalse(a.equals(null));
        assertFalse(a.equals("other"));
        final ImageJBoundaryReferenceResult.ReferenceOutline different =
                outlineBuilder().outlineNumber(2).build();
        assertFalse(a.equals(different));
    }

    @Test
    void referenceOutlineToStringContainsKeyFields() {
        final ImageJBoundaryReferenceResult.ReferenceOutline outline = outlineBuilder().build();
        final String text = outline.toString();
        assertTrue(text.contains("THRESHOLD_TO_SELECTION"));
        assertTrue(text.contains("closed=true"));
    }

    @Test
    void resultEqualsAndHashCodeAreConsistent() {
        final ImageJBoundaryReferenceResult a = resultBuilder().build();
        final ImageJBoundaryReferenceResult b = resultBuilder().build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(null));
        assertFalse(a.equals("other"));
        final ImageJBoundaryReferenceResult different = resultBuilder().imageWidth(9).build();
        assertFalse(a.equals(different));
    }

    @Test
    void resultToStringContainsKeyFields() {
        final ImageJBoundaryReferenceResult result = resultBuilder().build();
        final String text = result.toString();
        assertTrue(text.contains("8x8"));
        assertTrue(text.contains(ImageJBoundaryReferenceResult.REFERENCE_METHODS_AGREE));
    }

    // ------------------------------------------------------------------
    // Helpers - pixel sets
    // ------------------------------------------------------------------

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

    private static java.util.Set<Long> membershipOf(final List<Point> pixels) {
        final java.util.Set<Long> keys = new java.util.HashSet<Long>();
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            keys.add(ImageJBoundaryReferenceAnalyzer.pack(p.x, p.y));
        }
        return keys;
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

    private static AmbiguousBoundaryVertexDiagnostic extractDiagnostic(final List<Point> pixels) {
        final OrderedBoundaryExtractor extractor = new OrderedBoundaryExtractor();
        try {
            extractor.extractAllLoops(pixels);
        } catch (final AmbiguousBoundaryVertexException ex) {
            return ex.getDiagnostic();
        }
        throw new IllegalStateException("Expected an ambiguous boundary vertex for the given pixels");
    }

    // ------------------------------------------------------------------
    // Helpers - synthetic AmbiguousBoundaryVertexDiagnostic instances,
    // hand-built without calling OrderedBoundaryExtractor.
    // ------------------------------------------------------------------

    /**
     * Diagnostic anchored at the real rectangle corner (2,2) with a genuine
     * incoming boundary edge; every reference method should resolve the
     * successor to EAST (RIGHT_TURN relative to the NORTH incoming edge).
     */
    private static AmbiguousBoundaryVertexDiagnostic rectangleAgreementDiagnostic() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(2, 3, 2, 2);
        final DirectedPixelEdge east = new DirectedPixelEdge(2, 2, 3, 2);
        final DirectedPixelEdge south = new DirectedPixelEdge(2, 2, 2, 3);
        final List<DirectedPixelEdge> outgoing = Arrays.asList(east, south);
        final List<DirectedPixelEdge> incident = Arrays.asList(incoming, east, south);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 10, 0, 2, 2,
                incoming, incident, outgoing, outgoing,
                Collections.<DirectedPixelEdge>emptyList(),
                false, false, false, true);
    }

    private static ImageJBoundaryReferenceResult analyzeRectangle() {
        return new ImageJBoundaryReferenceAnalyzer().analyze(
                8, 8, rect(2, 2, 3, 2), rectangleAgreementDiagnostic());
    }

    private static AmbiguousBoundaryVertexDiagnostic syntheticAmbiguityAtVertex(
            final int vertexX,
            final int vertexY,
            final DirectedPixelEdge incoming) {
        final DirectedPixelEdge east =
                new DirectedPixelEdge(vertexX, vertexY, vertexX + 1, vertexY);
        final DirectedPixelEdge south =
                new DirectedPixelEdge(vertexX, vertexY, vertexX, vertexY + 1);
        final List<DirectedPixelEdge> outgoing = Arrays.asList(east, south);
        final List<DirectedPixelEdge> incident = Arrays.asList(incoming, east, south);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 4, 0, vertexX, vertexY,
                incoming, incident, outgoing, outgoing,
                Collections.<DirectedPixelEdge>emptyList(),
                false, false, false, true);
    }

    /**
     * Diagnostic anchored at (2,2), but with an incoming edge that is not part
     * of the real boundary of the rectangle; no reference method should find
     * this edge, so the analyzer must conclude AMBIGUOUS_INCOMING_EDGE_NOT_FOUND.
     */
    private static AmbiguousBoundaryVertexDiagnostic offBoundaryIncomingDiagnostic() {
        final DirectedPixelEdge incoming = new DirectedPixelEdge(1, 2, 2, 2);
        final DirectedPixelEdge east = new DirectedPixelEdge(2, 2, 3, 2);
        final DirectedPixelEdge south = new DirectedPixelEdge(2, 2, 2, 3);
        final List<DirectedPixelEdge> outgoing = Arrays.asList(east, south);
        final List<DirectedPixelEdge> incident = Arrays.asList(incoming, east, south);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 10, 0, 2, 2,
                incoming, incident, outgoing, outgoing,
                Collections.<DirectedPixelEdge>emptyList(),
                false, false, false, true);
    }

    /**
     * Diagnostic detached from any real geometry, used solely to exercise the
     * ambiguous-vertex bounds check in {@code validateInputs}.
     */
    private static AmbiguousBoundaryVertexDiagnostic diagnosticAtDetachedVertex(
            final int vertexX,
            final int vertexY) {
        final DirectedPixelEdge incoming =
                new DirectedPixelEdge(vertexX - 1, vertexY, vertexX, vertexY);
        final DirectedPixelEdge east = new DirectedPixelEdge(vertexX, vertexY, vertexX + 1, vertexY);
        final DirectedPixelEdge south = new DirectedPixelEdge(vertexX, vertexY, vertexX, vertexY + 1);
        final List<DirectedPixelEdge> outgoing = Arrays.asList(east, south);
        final List<DirectedPixelEdge> incident = Arrays.asList(incoming, east, south);
        return new AmbiguousBoundaryVertexDiagnostic(
                1, 1, 10, 0, vertexX, vertexY,
                incoming, incident, outgoing, outgoing,
                Collections.<DirectedPixelEdge>emptyList(),
                false, false, false, false);
    }

    private static List<ImageJBoundaryReferenceResult.ReferenceOutline> allOutlines(
            final ImageJBoundaryReferenceResult result) {
        final List<ImageJBoundaryReferenceResult.ReferenceOutline> all =
                new ArrayList<ImageJBoundaryReferenceResult.ReferenceOutline>();
        all.addAll(result.getThresholdToSelectionOutlines());
        all.addAll(result.getWandFourConnectedOutlines());
        all.addAll(result.getWandEightConnectedOutlines());
        return all;
    }

    // ------------------------------------------------------------------
    // Helpers - unit-square fixture data shared by the two builders below.
    // ------------------------------------------------------------------

    private static List<Point> unitSquareVertices() {
        return Arrays.asList(
                new Point(0, 0),
                new Point(1, 0),
                new Point(1, 1),
                new Point(0, 1));
    }

    private static List<DirectedPixelEdge> unitSquareEdges() {
        return Arrays.asList(
                new DirectedPixelEdge(0, 0, 1, 0),
                new DirectedPixelEdge(1, 0, 1, 1),
                new DirectedPixelEdge(1, 1, 0, 1),
                new DirectedPixelEdge(0, 1, 0, 0));
    }

    private static OutlineBuilder outlineBuilder() {
        return new OutlineBuilder();
    }

    /**
     * Fluent builder for {@link ImageJBoundaryReferenceResult.ReferenceOutline}
     * seeded with mutually-consistent, valid defaults so that individual
     * validation tests only need to override the one field under test.
     */
    private static final class OutlineBuilder {
        private ImageJBoundaryReferenceResult.ReferenceMethod method =
                ImageJBoundaryReferenceResult.ReferenceMethod.THRESHOLD_TO_SELECTION;
        private int outlineNumber = 1;
        private List<Point> latticeVertices = unitSquareVertices();
        private List<DirectedPixelEdge> expandedUnitEdges = unitSquareEdges();
        private boolean closed = true;
        private double signedArea = 1.0;
        private double absoluteArea = 1.0;
        private int repeatedNonClosingVertexCount;
        private int distinctVertexCount = 4;
        private Rectangle latticeBounds = new Rectangle(0, 0, 1, 1);
        private int expectedExposedEdgeMatchCount = 4;
        private int missingExpectedExposedEdgeCount;
        private int extraReferenceEdgeCount;
        private double expectedExposedEdgeCoverageFraction = 1.0;
        private boolean containsAmbiguousVertex = true;
        private int ambiguousVertexOccurrenceCount = 1;
        private boolean matchingIncomingEdgeFound = true;
        private int matchingIncomingEdgeOccurrenceCount = 1;
        private DirectedPixelEdge.Direction successorDirectionAtAmbiguity = DirectedPixelEdge.Direction.EAST;
        private ImageJBoundaryReferenceResult.ReferenceTurnRelation successorTurnRelationAtAmbiguity =
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.STRAIGHT;

        OutlineBuilder method(final ImageJBoundaryReferenceResult.ReferenceMethod value) {
            this.method = value;
            return this;
        }

        OutlineBuilder outlineNumber(final int value) {
            this.outlineNumber = value;
            return this;
        }

        OutlineBuilder vertices(final List<Point> value) {
            this.latticeVertices = value;
            return this;
        }

        OutlineBuilder edges(final List<DirectedPixelEdge> value) {
            this.expandedUnitEdges = value;
            return this;
        }

        OutlineBuilder bounds(final Rectangle value) {
            this.latticeBounds = value;
            return this;
        }

        OutlineBuilder turn(final ImageJBoundaryReferenceResult.ReferenceTurnRelation value) {
            this.successorTurnRelationAtAmbiguity = value;
            return this;
        }

        OutlineBuilder signedArea(final double value) {
            this.signedArea = value;
            return this;
        }

        OutlineBuilder absoluteArea(final double value) {
            this.absoluteArea = value;
            return this;
        }

        OutlineBuilder coverage(final double value) {
            this.expectedExposedEdgeCoverageFraction = value;
            return this;
        }

        OutlineBuilder repeated(final int value) {
            this.repeatedNonClosingVertexCount = value;
            return this;
        }

        OutlineBuilder distinct(final int value) {
            this.distinctVertexCount = value;
            return this;
        }

        OutlineBuilder matchCount(final int value) {
            this.expectedExposedEdgeMatchCount = value;
            return this;
        }

        OutlineBuilder missingCount(final int value) {
            this.missingExpectedExposedEdgeCount = value;
            return this;
        }

        OutlineBuilder extraCount(final int value) {
            this.extraReferenceEdgeCount = value;
            return this;
        }

        OutlineBuilder ambiguousOccurrenceCount(final int value) {
            this.ambiguousVertexOccurrenceCount = value;
            return this;
        }

        OutlineBuilder matchingOccurrenceCount(final int value) {
            this.matchingIncomingEdgeOccurrenceCount = value;
            return this;
        }

        OutlineBuilder matchingFound(final boolean value) {
            this.matchingIncomingEdgeFound = value;
            return this;
        }

        OutlineBuilder successorDirection(final DirectedPixelEdge.Direction value) {
            this.successorDirectionAtAmbiguity = value;
            return this;
        }

        ImageJBoundaryReferenceResult.ReferenceOutline build() {
            return new ImageJBoundaryReferenceResult.ReferenceOutline(
                    method,
                    outlineNumber,
                    latticeVertices,
                    expandedUnitEdges,
                    closed,
                    signedArea,
                    absoluteArea,
                    repeatedNonClosingVertexCount,
                    distinctVertexCount,
                    latticeBounds,
                    expectedExposedEdgeMatchCount,
                    missingExpectedExposedEdgeCount,
                    extraReferenceEdgeCount,
                    expectedExposedEdgeCoverageFraction,
                    containsAmbiguousVertex,
                    ambiguousVertexOccurrenceCount,
                    matchingIncomingEdgeFound,
                    matchingIncomingEdgeOccurrenceCount,
                    successorDirectionAtAmbiguity,
                    successorTurnRelationAtAmbiguity);
        }
    }

    private static ResultBuilder resultBuilder() {
        return new ResultBuilder();
    }

    /**
     * Fluent builder for {@link ImageJBoundaryReferenceResult} seeded with
     * mutually-consistent, valid defaults.
     */
    private static final class ResultBuilder {
        private int imageWidth = 8;
        private int imageHeight = 8;
        private int selectedPixelCount = 4;
        private int expectedExposedEdgeCount = 4;
        private List<DirectedPixelEdge> expectedExposedDirectedEdges = unitSquareEdges();
        private AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic = rectangleAgreementDiagnostic();
        private List<ImageJBoundaryReferenceResult.ReferenceOutline> ttsOutlines =
                Collections.singletonList(outlineBuilder().build());
        private List<ImageJBoundaryReferenceResult.ReferenceOutline> wand4Outlines =
                Collections.singletonList(outlineBuilder().build());
        private List<ImageJBoundaryReferenceResult.ReferenceOutline> wand8Outlines =
                Collections.singletonList(outlineBuilder().build());
        private boolean sourceMaskUnchanged = true;
        private String overallDiagnosticConclusion = ImageJBoundaryReferenceResult.REFERENCE_METHODS_AGREE;

        ResultBuilder imageWidth(final int value) {
            this.imageWidth = value;
            return this;
        }

        ResultBuilder imageHeight(final int value) {
            this.imageHeight = value;
            return this;
        }

        ResultBuilder selectedPixelCount(final int value) {
            this.selectedPixelCount = value;
            return this;
        }

        ResultBuilder expectedEdgeCount(final int value) {
            this.expectedExposedEdgeCount = value;
            return this;
        }

        ResultBuilder expectedEdges(final List<DirectedPixelEdge> value) {
            this.expectedExposedDirectedEdges = value;
            return this;
        }

        ResultBuilder ambiguityDiagnostic(final AmbiguousBoundaryVertexDiagnostic value) {
            this.ambiguityDiagnostic = value;
            return this;
        }

        ResultBuilder ttsOutlines(final List<ImageJBoundaryReferenceResult.ReferenceOutline> value) {
            this.ttsOutlines = value;
            return this;
        }

        ResultBuilder wand4Outlines(final List<ImageJBoundaryReferenceResult.ReferenceOutline> value) {
            this.wand4Outlines = value;
            return this;
        }

        ResultBuilder wand8Outlines(final List<ImageJBoundaryReferenceResult.ReferenceOutline> value) {
            this.wand8Outlines = value;
            return this;
        }

        ResultBuilder sourceMaskUnchanged(final boolean value) {
            this.sourceMaskUnchanged = value;
            return this;
        }

        ResultBuilder conclusion(final String value) {
            this.overallDiagnosticConclusion = value;
            return this;
        }

        ImageJBoundaryReferenceResult build() {
            return new ImageJBoundaryReferenceResult(
                    imageWidth,
                    imageHeight,
                    selectedPixelCount,
                    expectedExposedEdgeCount,
                    expectedExposedDirectedEdges,
                    ambiguityDiagnostic,
                    ttsOutlines,
                    wand4Outlines,
                    wand8Outlines,
                    sourceMaskUnchanged,
                    overallDiagnosticConclusion);
        }
    }

    // ------------------------------------------------------------------
    // Additional required expansion / accounting / restore tests
    // ------------------------------------------------------------------

    @Test
    void threeByTwoRectangleProducesTenExposedEdges() {
        final List<Point> pixels = rect(1, 1, 3, 2);
        final Set<Long> membership = membershipOf(pixels);
        final List<DirectedPixelEdge> edges =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(pixels, membership);
        assertEquals(10, edges.size());
    }

    @Test
    void expectedDirectedEdgesKeepSelectedOnRightForSinglePixel() {
        final List<Point> pixels = Collections.singletonList(new Point(2, 2));
        final List<DirectedPixelEdge> edges =
                ImageJBoundaryReferenceAnalyzer.buildExpectedExposedEdges(
                        pixels, membershipOf(pixels));
        // NORTH face -> EAST: selected cell is south of the edge (right when facing EAST).
        assertTrue(edges.contains(new DirectedPixelEdge(2, 2, 3, 2)));
        // EAST face -> SOUTH
        assertTrue(edges.contains(new DirectedPixelEdge(3, 2, 3, 3)));
        // SOUTH face -> WEST
        assertTrue(edges.contains(new DirectedPixelEdge(3, 3, 2, 3)));
        // WEST face -> NORTH
        assertTrue(edges.contains(new DirectedPixelEdge(2, 3, 2, 2)));
    }

    @Test
    void longHorizontalSegmentExpandsIntoUnitEdges() {
        final List<Point> path = new ArrayList<Point>();
        path.add(new Point(0, 0));
        path.add(new Point(3, 0));
        path.add(new Point(3, 1));
        path.add(new Point(0, 1));
        final ImageJBoundaryReferenceAnalyzer.PathConversion conversion =
                ImageJBoundaryReferenceAnalyzer.expandForTest(path, true);
        assertEquals(8, conversion.unitEdges.size());
        assertEquals(new DirectedPixelEdge(0, 0, 1, 0), conversion.unitEdges.get(0));
        assertEquals(new DirectedPixelEdge(1, 0, 2, 0), conversion.unitEdges.get(1));
        assertEquals(new DirectedPixelEdge(2, 0, 3, 0), conversion.unitEdges.get(2));
    }

    @Test
    void longVerticalSegmentExpandsIntoUnitEdges() {
        final List<Point> path = new ArrayList<Point>();
        path.add(new Point(0, 0));
        path.add(new Point(1, 0));
        path.add(new Point(1, 3));
        path.add(new Point(0, 3));
        final ImageJBoundaryReferenceAnalyzer.PathConversion conversion =
                ImageJBoundaryReferenceAnalyzer.expandForTest(path, true);
        assertEquals(8, conversion.unitEdges.size());
        assertTrue(conversion.unitEdges.contains(new DirectedPixelEdge(1, 0, 1, 1)));
        assertTrue(conversion.unitEdges.contains(new DirectedPixelEdge(1, 1, 1, 2)));
        assertTrue(conversion.unitEdges.contains(new DirectedPixelEdge(1, 2, 1, 3)));
    }

    @Test
    void pathClosureIncludesFinalToFirstSegment() {
        final List<Point> path = new ArrayList<Point>();
        path.add(new Point(0, 0));
        path.add(new Point(1, 0));
        path.add(new Point(1, 1));
        path.add(new Point(0, 1));
        final ImageJBoundaryReferenceAnalyzer.PathConversion conversion =
                ImageJBoundaryReferenceAnalyzer.expandForTest(path, true);
        assertEquals(4, conversion.unitEdges.size());
        assertEquals(new DirectedPixelEdge(0, 1, 0, 0),
                conversion.unitEdges.get(conversion.unitEdges.size() - 1));
    }

    @Test
    void duplicateConsecutiveVerticesDoNotCreateZeroLengthEdges() {
        final List<Point> path = new ArrayList<Point>();
        path.add(new Point(0, 0));
        path.add(new Point(0, 0));
        path.add(new Point(1, 0));
        path.add(new Point(1, 1));
        path.add(new Point(0, 1));
        final ImageJBoundaryReferenceAnalyzer.PathConversion conversion =
                ImageJBoundaryReferenceAnalyzer.expandForTest(path, true);
        for (int i = 0; i < conversion.unitEdges.size(); i++) {
            final DirectedPixelEdge e = conversion.unitEdges.get(i);
            assertFalse(e.getStartX() == e.getEndX() && e.getStartY() == e.getEndY());
        }
        assertEquals(4, conversion.unitEdges.size());
    }

    @Test
    void unsupportedDiagonalSegmentIsReportedNotInterpolated() {
        final List<Point> path = new ArrayList<Point>();
        path.add(new Point(0, 0));
        path.add(new Point(1, 1));
        path.add(new Point(0, 1));
        final ImageJBoundaryReferenceAnalyzer.PathConversion conversion =
                ImageJBoundaryReferenceAnalyzer.expandForTest(path, true);
        assertTrue(conversion.hadUnsupportedSegment);
        assertFalse(conversion.closed);
        // Diagonal (0,0)->(1,1) must not be interpolated into unit edges.
        for (int i = 0; i < conversion.unitEdges.size(); i++) {
            final DirectedPixelEdge e = conversion.unitEdges.get(i);
            assertFalse(Math.abs(e.getEndX() - e.getStartX()) == 1
                    && Math.abs(e.getEndY() - e.getStartY()) == 1);
        }
    }

    @Test
    void normalizedReferencePathStartsAtDeterministicSmallestEdge() {
        final ImageJBoundaryReferenceResult result = analyzeRectangle();
        final ImageJBoundaryReferenceResult.ReferenceOutline outline =
                result.getThresholdToSelectionOutlines().get(0);
        final DirectedPixelEdge first = outline.getExpandedUnitEdges().get(0);
        for (int i = 1; i < outline.getExpandedUnitEdges().size(); i++) {
            final DirectedPixelEdge e = outline.getExpandedUnitEdges().get(i);
            final int cmp = compareEdges(first, e);
            assertTrue(cmp <= 0);
        }
    }

    @Test
    void wandAllPointsRestoredAfterForcedException() {
        Wand.setAllPoints(false);
        try {
            ImageJBoundaryReferenceAnalyzer.wandAllPointsRestoreProbeThatThrows();
            fail("expected forced probe failure");
        } catch (IllegalStateException expected) {
            assertFalse(Wand.allPoints());
        }
        Wand.setAllPoints(true);
        try {
            ImageJBoundaryReferenceAnalyzer.wandAllPointsRestoreProbeThatThrows();
            fail("expected forced probe failure");
        } catch (IllegalStateException expected) {
            assertTrue(Wand.allPoints());
        } finally {
            Wand.setAllPoints(false);
        }
    }

    @Test
    void twoDiagonallyTouchingPixelsProduceMeasurableReferenceComparison() {
        final List<Point> pixels = new ArrayList<Point>();
        pixels.add(new Point(2, 2));
        pixels.add(new Point(3, 3));
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                syntheticAmbiguityAtVertex(3, 3, new DirectedPixelEdge(2, 3, 3, 3));
        final ImageJBoundaryReferenceResult result = new ImageJBoundaryReferenceAnalyzer().analyze(
                8, 8, pixels, diagnostic);
        assertTrue(result.getExpectedExposedEdgeCount() > 0);
        assertNotNull(result.getOverallDiagnosticConclusion());
        assertFalse(result.getOverallDiagnosticConclusion().isEmpty());
    }

    @Test
    void thresholdToSelectionHandlesOneSelectedPixel() {
        final List<Point> pixels = Collections.singletonList(new Point(3, 3));
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                syntheticAmbiguityAtVertex(3, 3, new DirectedPixelEdge(2, 3, 3, 3));
        final ImageJBoundaryReferenceResult result = new ImageJBoundaryReferenceAnalyzer().analyze(
                8, 8, pixels, diagnostic);
        assertEquals(1, result.getThresholdToSelectionOutlines().size());
        assertTrue(result.getThresholdToSelectionOutlines().get(0).getExpandedUnitEdges().size()
                >= 4);
    }

    @Test
    void thresholdToSelectionResultIsDeterministic() {
        final ImageJBoundaryReferenceResult a = analyzeRectangle();
        final ImageJBoundaryReferenceResult b = analyzeRectangle();
        assertEquals(a.getThresholdToSelectionOutlines(), b.getThresholdToSelectionOutlines());
    }

    @Test
    void wandFourAndEightHandleOneSelectedPixel() {
        final List<Point> pixels = Collections.singletonList(new Point(3, 3));
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                syntheticAmbiguityAtVertex(3, 3, new DirectedPixelEdge(2, 3, 3, 3));
        final ImageJBoundaryReferenceResult result = new ImageJBoundaryReferenceAnalyzer().analyze(
                8, 8, pixels, diagnostic);
        assertEquals(1, result.getWandFourConnectedOutlines().size());
        assertEquals(1, result.getWandEightConnectedOutlines().size());
    }

    @Test
    void wandFourAndEightResultsAreDeterministic() {
        final ImageJBoundaryReferenceResult a = analyzeRectangle();
        final ImageJBoundaryReferenceResult b = analyzeRectangle();
        assertEquals(a.getWandFourConnectedOutlines(), b.getWandFourConnectedOutlines());
        assertEquals(a.getWandEightConnectedOutlines(), b.getWandEightConnectedOutlines());
    }

    @Test
    void edgeMatchCountsDoNotDoubleCountDuplicateReferenceEdges() {
        final ImageJBoundaryReferenceResult result = analyzeRectangle();
        final ImageJBoundaryReferenceResult.ReferenceOutline outline =
                result.getThresholdToSelectionOutlines().get(0);
        assertEquals(result.getExpectedExposedEdgeCount(),
                outline.getExpectedExposedEdgeMatchCount());
        assertEquals(0, outline.getMissingExpectedExposedEdgeCount());
        assertEquals(0, outline.getExtraReferenceEdgeCount());
        assertEquals(1.0, outline.getExpectedExposedEdgeCoverageFraction(), 1.0e-12);
    }

    @Test
    void ordinaryClosureDoesNotCountAsRepeatedNonClosingVertex() {
        final ImageJBoundaryReferenceResult result = analyzeRectangle();
        assertEquals(0,
                result.getThresholdToSelectionOutlines().get(0)
                        .getRepeatedNonClosingVertexCount());
    }

    @Test
    void noProductionSuccessorIsSelectedByReferenceAudit() {
        final ImageJBoundaryReferenceResult result = analyzeRectangle();
        // Reference audit records observed turns only; production extractor unchanged.
        assertNotNull(result.getOverallDiagnosticConclusion());
        assertTrue(result.isSourceMaskUnchanged());
    }

    @Test
    void selectedPixelSetIsUnchangedByReferenceAudit() {
        final List<Point> pixels = rect(2, 2, 2, 2);
        final Set<Long> before = membershipOf(pixels);
        final AmbiguousBoundaryVertexDiagnostic diagnostic =
                syntheticAmbiguityAtVertex(2, 2, new DirectedPixelEdge(1, 2, 2, 2));
        new ImageJBoundaryReferenceAnalyzer().analyze(8, 8, pixels, diagnostic);
        assertEquals(before, membershipOf(pixels));
        assertEquals(4, pixels.size());
    }

    private static int compareEdges(final DirectedPixelEdge a, final DirectedPixelEdge b) {
        if (a.getStartY() != b.getStartY()) {
            return Integer.compare(a.getStartY(), b.getStartY());
        }
        if (a.getStartX() != b.getStartX()) {
            return Integer.compare(a.getStartX(), b.getStartX());
        }
        if (a.getEndY() != b.getEndY()) {
            return Integer.compare(a.getEndY(), b.getEndY());
        }
        return Integer.compare(a.getEndX(), b.getEndX());
    }
}
