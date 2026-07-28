package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic JUnit 5 tests for proposed-fill component diagnostics.
 */
class ProposedFillComponentAnalyzerTest {

    private final ProposedFillComponentAnalyzer analyzer =
            new ProposedFillComponentAnalyzer();
    private final OrderedBoundaryExtractor boundaryExtractor =
            new OrderedBoundaryExtractor();

    private static final double ORIG_CIRC = 0.5;
    private static final double WHOLE_CIRC = 0.6;
    private static final double WHOLE_CHANGE = 0.1;

    // ------------------------------------------------------------------
    // Public analyze() argument validation
    // ------------------------------------------------------------------

    @Test
    void nullByteProcessorRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(null, null, null));
    }

    @Test
    void nullWholePreviewRejected() {
        final ByteProcessor processor = filled(8, 8, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(processor, null, null));
    }

    @Test
    void nullInwardOutlierDetectionRejected() {
        final PublicAnalyzeFixture fixture = publicAnalyzeFixture();
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(fixture.processor, fixture.preview, null));
    }

    @Test
    void detectionWithoutSelectedIntervalRejected() {
        final PublicAnalyzeFixture fixture = publicAnalyzeFixture();
        final InwardOutlierDetectionResult noSelection = new InwardOutlierDetectionResult(
                fixture.detection.getResidualProfile(),
                fixture.detection.getRobustScale(),
                fixture.detection.getCoreThreshold(),
                fixture.detection.getExpansionThreshold(),
                Collections.<InwardOutlierInterval>emptyList(),
                null,
                false,
                InwardOutlierDetectionResult.NO_CORE_POINTS,
                0.0);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(fixture.processor, fixture.preview, noSelection));
    }

    @Test
    void mismatchedImageDimensionsRejected() {
        final PublicAnalyzeFixture fixture = publicAnalyzeFixture();
        final ByteProcessor other = filled(
                fixture.processor.getWidth() + 1,
                fixture.processor.getHeight(),
                ClassifierLabels.BACKGROUND_RESULT_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(other, fixture.preview, fixture.detection));
    }

    // ------------------------------------------------------------------
    // Component extraction
    // ------------------------------------------------------------------

    @Test
    void emptyProposedFillProducesZeroComponents() {
        final BaseFixture base = baseLumenFixture();
        final ProposedFillComponentAnalysisResult result = analyze(
                base, Collections.<Point>emptyList(), range(0, 3));
        assertEquals(0, result.getTotalComponentCount());
        assertEquals(0, result.getTotalProposedFillPixelCount());
        assertNull(result.getPrimaryIntervalAssociatedComponent());
        assertEquals(0.0, result.getIntervalAssociatedFillFraction(), 0.0);
        assertTrue(result.isAllProposedFillIsIntervalAssociated());
        assertFalse(result.isExactlyOneIntervalAssociatedComponent());
    }

    @Test
    void oneProposedFillPixelProducesOneComponent() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getTotalComponentCount());
        assertEquals(1, result.getAllComponents().get(0).getArea());
        assertEquals(1, result.getAllComponents().get(0).getComponentId());
    }

    @Test
    void cardinallyAdjacentFillPixelsFormOneComponent() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        assertEquals(1, result.getTotalComponentCount());
        assertEquals(2, result.getAllComponents().get(0).getArea());
    }

    @Test
    void diagonallyAdjacentFillPixelsFormOneComponentUnder8Connectivity() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 3));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getTotalComponentCount());
        assertEquals(2, result.getAllComponents().get(0).getArea());
    }

    @Test
    void separatedFillRegionsRemainSeparate() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getTotalComponentCount());
    }

    @Test
    void everyFillPixelBelongsToExactlyOneComponent() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(8, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        final Set<Long> seen = new HashSet<Long>();
        int total = 0;
        for (final ProposedFillComponent component : result.getAllComponents()) {
            for (final Point point : component.getPixels()) {
                final long key = pack(point.x, point.y);
                assertTrue(seen.add(Long.valueOf(key)));
                total++;
            }
        }
        assertEquals(fill.size(), total);
        assertEquals(fill.size(), result.getTotalProposedFillPixelCount());
    }

    @Test
    void deterministicComponentIdsFollowYThenXDiscovery() {
        final BaseFixture base = baseLumenFixture();
        // Discovery order: (5,4) first, then (12,12).
        final List<Point> fill = points(p(12, 12), p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(2, result.getTotalComponentCount());
        assertEquals(1, result.getAllComponents().get(0).getComponentId());
        assertEquals(p(5, 4), result.getAllComponents().get(0).getPixels().get(0));
        assertEquals(2, result.getAllComponents().get(1).getComponentId());
        assertEquals(p(12, 12), result.getAllComponents().get(1).getPixels().get(0));
    }

    @Test
    void reversedInputOrderGivesIdenticalComponentResults() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> forward = points(p(5, 4), p(6, 4), p(12, 12));
        final List<Point> reversed = points(p(12, 12), p(6, 4), p(5, 4));
        paintFill(base.processor, forward, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult a = analyze(base, forward, range(0, 1));
        final ProposedFillComponentAnalysisResult b = analyze(base, reversed, range(0, 1));
        assertEquals(a.getAllComponents(), b.getAllComponents());
    }

    @Test
    void componentPixelListsAreSortedYThenX() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(7, 4), p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 2));
        final List<Point> pixels = result.getAllComponents().get(0).getPixels();
        assertEquals(points(p(5, 4), p(6, 4), p(7, 4)), pixels);
    }

    @Test
    void componentBoundsAreExact() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(5, 3));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        final Rectangle bounds = result.getAllComponents().get(0).getBounds();
        assertEquals(5, bounds.x);
        assertEquals(3, bounds.y);
        assertEquals(2, bounds.width);
        assertEquals(2, bounds.height);
    }

    // ------------------------------------------------------------------
    // Selected-Lumen contact
    // ------------------------------------------------------------------

    @Test
    void selectedLumenCardinalContactPixelCountIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        // (5,4) touches (5,5); (6,3) does not touch lumen cardinally (diagonal only to (5,5)/(6,5)).
        final List<Point> fill = points(p(5, 4), p(6, 3));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getAllComponents().get(0).getSelectedLumenContactPixelCount());
    }

    @Test
    void selectedLumenCardinalContactEdgeCountIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        // (5,4) has one south edge to (5,5); (4,5) has one east edge to (5,5).
        final List<Point> fill = points(p(5, 4), p(4, 5));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        // May be one or two components depending on diagonal connectivity: (5,4)-(4,5) are diagonal.
        int edgeSum = 0;
        for (final ProposedFillComponent component : result.getAllComponents()) {
            edgeSum += component.getSelectedLumenContactEdgeCount();
        }
        assertEquals(2, edgeSum);
    }

    @Test
    void diagonalSelectedLumenContactDoesNotCountAsContactEdge() {
        final BaseFixture base = baseLumenFixture();
        // (4,4) is diagonal to lumen (5,5) only.
        final List<Point> fill = points(p(4, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(15, 15));
        assertEquals(0, result.getAllComponents().get(0).getSelectedLumenContactPixelCount());
        assertEquals(0, result.getAllComponents().get(0).getSelectedLumenContactEdgeCount());
    }

    // ------------------------------------------------------------------
    // Exterior seed mappings
    // ------------------------------------------------------------------

    @Test
    void eastIntervalEdgeExteriorSeedMappingIsCorrect() {
        final DirectedPixelEdge edge = new DirectedPixelEdge(5, 5, 6, 5);
        assertEquals(DirectedPixelEdge.Direction.EAST, edge.getDirection());
        assertEquals(p(5, 4), ProposedFillComponentAnalyzer.exteriorSeedFor(edge));
    }

    @Test
    void southIntervalEdgeExteriorSeedMappingIsCorrect() {
        final DirectedPixelEdge edge = new DirectedPixelEdge(9, 5, 9, 6);
        assertEquals(DirectedPixelEdge.Direction.SOUTH, edge.getDirection());
        assertEquals(p(9, 5), ProposedFillComponentAnalyzer.exteriorSeedFor(edge));
    }

    @Test
    void westIntervalEdgeExteriorSeedMappingIsCorrect() {
        final DirectedPixelEdge edge = new DirectedPixelEdge(9, 9, 8, 9);
        assertEquals(DirectedPixelEdge.Direction.WEST, edge.getDirection());
        assertEquals(p(8, 9), ProposedFillComponentAnalyzer.exteriorSeedFor(edge));
    }

    @Test
    void northIntervalEdgeExteriorSeedMappingIsCorrect() {
        final DirectedPixelEdge edge = new DirectedPixelEdge(5, 6, 5, 5);
        assertEquals(DirectedPixelEdge.Direction.NORTH, edge.getDirection());
        assertEquals(p(4, 5), ProposedFillComponentAnalyzer.exteriorSeedFor(edge));
    }

    @Test
    void outOfImageExteriorSeedsAreNotMatched() {
        // Lumen touching left image border: NORTH exterior can go x=-1.
        final List<Point> lumen = rect(0, 5, 2, 2);
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        // Find a NORTH edge whose exterior is out of bounds.
        final List<Integer> northOut = new ArrayList<Integer>();
        final List<DirectedPixelEdge> edges = loop.getDirectedEdges();
        for (int i = 0; i < edges.size(); i++) {
            final Point seed = ProposedFillComponentAnalyzer.exteriorSeedFor(edges.get(i));
            if (seed.x < 0 || seed.y < 0 || seed.x >= 12 || seed.y >= 12) {
                northOut.add(Integer.valueOf(i));
            }
        }
        assertFalse(northOut.isEmpty());
        final List<Point> fill = points(p(3, 5));
        paintFill(processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor, lumen, fill, loop, northOut, ORIG_CIRC, WHOLE_CIRC, WHOLE_CHANGE);
        assertEquals(northOut.size(), result.getIntervalExteriorEdgeCount());
        assertEquals(0, result.getUniqueIntervalExteriorSeedPixelCount());
        assertEquals(0, result.getMatchedIntervalExteriorSeedPixelCount());
        assertEquals(0, result.getIntervalAssociatedComponentCount());
    }

    @Test
    void duplicateExteriorSeedCoordinatesCountedOnceInUniqueSeedList() {
        final LShapeFixture fixture = lShapeFixture();
        // Edges 7 and 8 both exterior to (6,6).
        final List<Integer> interval = range(7, 8);
        final ProposedFillComponentAnalysisResult result = analyze(
                fixture.base, Collections.<Point>emptyList(), interval);
        assertEquals(2, result.getIntervalExteriorEdgeCount());
        assertEquals(1, result.getUniqueIntervalExteriorSeedPixelCount());
        assertEquals(points(p(6, 6)), result.getIntervalExteriorSeedPixels());
        assertEquals(0, result.getMatchedIntervalExteriorSeedPixelCount());
        assertEquals(1, result.getUnmatchedIntervalExteriorSeedPixelCount());
    }

    @Test
    void intervalEdgeHitsPreserveEdgeMultiplicity() {
        final LShapeFixture fixture = lShapeFixture();
        final List<Point> fill = points(p(6, 6));
        paintFill(fixture.base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(
                fixture.base, fill, range(7, 8));
        final ProposedFillComponent component = result.getAllComponents().get(0);
        assertEquals(1, component.getIntervalExteriorSeedPixelCount());
        assertEquals(2, component.getIntervalExteriorEdgeHitCount());
        assertTrue(component.isIntervalAssociated());
    }

    // ------------------------------------------------------------------
    // Interval association
    // ------------------------------------------------------------------

    @Test
    void componentContainingOneMatchedSeedIsIntervalAssociated() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertTrue(result.getAllComponents().get(0).isIntervalAssociated());
        assertEquals(1, result.getIntervalAssociatedComponentCount());
    }

    @Test
    void spatialProximityWithoutMatchedSeedIsNotIntervalAssociation() {
        final BaseFixture base = baseLumenFixture();
        // Fill adjacent to lumen top but interval selects bottom edges (8-11).
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(8, 11));
        assertFalse(result.getAllComponents().get(0).isIntervalAssociated());
        assertEquals(0, result.getIntervalAssociatedComponentCount());
        assertNull(result.getPrimaryIntervalAssociatedComponent());
    }

    @Test
    void componentsAreNotBridged() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        assertEquals(2, result.getTotalComponentCount());
    }

    @Test
    void selectedIntervalIsNotExpanded() {
        final BaseFixture base = baseLumenFixture();
        final List<Integer> interval = range(0, 2);
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, interval);
        assertEquals(interval, result.getInwardOutlierDetection()
                .getSelectedInterval().getOrderedBoundaryIndices());
        assertEquals(3, result.getIntervalExteriorEdgeCount());
        // Seed for edge 3 is (8,4); interval stops at edge 2, so (8,4) is not associated.
        boolean foundAssociatedAt84 = false;
        for (final ProposedFillComponent component : result.getIntervalAssociatedComponents()) {
            for (final Point point : component.getPixels()) {
                if (point.x == 8 && point.y == 4) {
                    foundAssociatedAt84 = true;
                }
            }
        }
        assertFalse(foundAssociatedAt84);
    }

    // ------------------------------------------------------------------
    // Associated-component ordering
    // ------------------------------------------------------------------

    @Test
    void associatedComponentOrderingFirstUsesEdgeHits() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(7, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        // Make (5,4)(6,4)(7,4) one component and (8,4) separate by... they are contiguous.
        // Use gap: (5,4)(6,4)(7,4) and (9,5) with interval covering top+right.
        final List<Point> fill2 = points(p(5, 4), p(6, 4), p(7, 4), p(9, 5));
        paintFill(base.processor, fill2, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill2, range(0, 4));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final ProposedFillComponent primary = result.getPrimaryIntervalAssociatedComponent();
        assertEquals(3, primary.getIntervalExteriorEdgeHitCount());
        assertEquals(1, result.getIntervalAssociatedComponents().get(1)
                .getIntervalExteriorEdgeHitCount());
    }

    @Test
    void seedCountIsSecondOrderingCriterion() {
        final LShapeFixture fixture = lShapeFixture();
        // Contiguous edges 0..8: component at (5,4)+(6,4) gets 2 unique seeds / 2 hits;
        // component at (6,6) gets 1 unique seed but 2 hits (edges 7+8 share exterior).
        // Equal edge hits (2); greater seed count ranks first.
        final List<Point> fill = points(p(6, 6), p(5, 4), p(6, 4));
        paintFill(fixture.base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(
                fixture.base, fill, range(0, 8));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final ProposedFillComponent primary = result.getPrimaryIntervalAssociatedComponent();
        assertEquals(2, primary.getIntervalExteriorEdgeHitCount());
        assertEquals(2, primary.getIntervalExteriorSeedPixelCount());
        assertTrue(contains(primary.getPixels(), p(5, 4)));
        assertEquals(1, result.getIntervalAssociatedComponents().get(1)
                .getIntervalExteriorSeedPixelCount());
    }

    @Test
    void areaIsThirdOrderingCriterion() {
        final BaseFixture base = baseLumenFixture();
        // Equal hits and seeds (1 each), larger area wins.
        final List<Point> fill = points(p(5, 4), p(5, 3), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final ProposedFillComponent primary = result.getPrimaryIntervalAssociatedComponent();
        assertEquals(1, primary.getIntervalExteriorEdgeHitCount());
        assertEquals(1, primary.getIntervalExteriorSeedPixelCount());
        assertEquals(2, primary.getArea());
    }

    @Test
    void selectedLumenContactEdgesAreFourthOrderingCriterion() {
        final BaseFixture base = baseLumenFixture();
        // Interval 0..3. Equal hits/seeds/area; greater lumen-contact edges ranks first.
        // Comp A: (5,4)+(5,3) → 1 contact edge
        // Comp B: (8,4)+(9,5) diagonal → 2 contact edges to lumen
        final List<Point> fill = points(p(5, 4), p(5, 3), p(8, 4), p(9, 5));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final ProposedFillComponent primary = result.getPrimaryIntervalAssociatedComponent();
        assertEquals(1, primary.getIntervalExteriorEdgeHitCount());
        assertEquals(1, primary.getIntervalExteriorSeedPixelCount());
        assertEquals(2, primary.getArea());
        assertEquals(2, primary.getSelectedLumenContactEdgeCount());
        assertTrue(contains(primary.getPixels(), p(8, 4)));
        assertEquals(1, result.getIntervalAssociatedComponents().get(1)
                .getSelectedLumenContactEdgeCount());
    }

    @Test
    void boundsAndComponentIdProvideDeterministicFinalOrdering() {
        final BaseFixture base = baseLumenFixture();
        // Equal hits/seeds/area/contact: (5,4) and (8,4).
        final List<Point> fill = points(p(8, 4), p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final ProposedFillComponent primary = result.getPrimaryIntervalAssociatedComponent();
        assertEquals(p(5, 4), primary.getPixels().get(0));
        assertTrue(primary.getBounds().x < result.getIntervalAssociatedComponents()
                .get(1).getBounds().x
                || primary.getComponentId() < result.getIntervalAssociatedComponents()
                .get(1).getComponentId());
    }

    @Test
    void primaryComponentIsFirstAssociatedComponent() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(result.getIntervalAssociatedComponents().get(0),
                result.getPrimaryIntervalAssociatedComponent());
    }

    @Test
    void noAssociatedComponentProducesNullPrimarySafely() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertNull(result.getPrimaryIntervalAssociatedComponent());
        assertEquals(0, result.getIntervalAssociatedComponentCount());
        assertEquals(0.0, result.getPrimaryComponentFillFraction(), 0.0);
        assertEquals(0.0, result.getPrimaryWithinAssociatedFraction(), 0.0);
    }

    // ------------------------------------------------------------------
    // Class counts
    // ------------------------------------------------------------------

    @Test
    void rawValueZeroCountedAsWood() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getAllComponents().get(0).getWoodPixelCount());
    }

    @Test
    void rawValueOneCountedAsRay() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.RAY_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getAllComponents().get(0).getRayPixelCount());
    }

    @Test
    void rawValueThreeCountedAsBackground() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getAllComponents().get(0).getBackgroundPixelCount());
    }

    @Test
    void unknownNonLumenValueCountedAsOther() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, 7);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(1, result.getAllComponents().get(0).getOtherClassPixelCount());
    }

    @Test
    void rawLumenValueInProposedFillThrowsProposedFillContainsLumen() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.LUMEN_RESULT_VALUE);
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> analyze(base, fill, range(0, 0)));
        assertTrue(ex.getMessage().contains("PROPOSED_FILL_CONTAINS_LUMEN"));
    }

    @Test
    void classCountsSumToComponentArea() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(7, 4), p(8, 4));
        base.processor.set(5, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        base.processor.set(6, 4, ClassifierLabels.RAY_RESULT_VALUE);
        base.processor.set(7, 4, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        base.processor.set(8, 4, 9);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        final ProposedFillComponent component = result.getAllComponents().get(0);
        assertEquals(component.getArea(),
                component.getWoodPixelCount()
                        + component.getRayPixelCount()
                        + component.getBackgroundPixelCount()
                        + component.getOtherClassPixelCount());
        assertEquals(1, component.getWoodPixelCount());
        assertEquals(1, component.getRayPixelCount());
        assertEquals(1, component.getBackgroundPixelCount());
        assertEquals(1, component.getOtherClassPixelCount());
    }

    // ------------------------------------------------------------------
    // Fill unions and preview
    // ------------------------------------------------------------------

    @Test
    void intervalAssociatedFillUnionIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        assertEquals(points(p(5, 4), p(6, 4)), result.getIntervalAssociatedFillPixels());
    }

    @Test
    void nonAssociatedFillUnionIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(12, 12), p(13, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(points(p(12, 12), p(13, 12)), result.getNonAssociatedFillPixels());
    }

    @Test
    void associatedPlusNonAssociatedFillEqualsTotalFill() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(result.getTotalProposedFillPixelCount(),
                result.getIntervalAssociatedFillPixelCount()
                        + result.getNonAssociatedFillPixelCount());
    }

    @Test
    void intervalAssociatedPreviewEqualsObservedLumenUnionAssociatedFill() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        final Set<Long> expected = new HashSet<Long>();
        for (final Point point : base.lumen) {
            expected.add(Long.valueOf(pack(point.x, point.y)));
        }
        expected.add(Long.valueOf(pack(5, 4)));
        final Set<Long> actual = new HashSet<Long>();
        for (final Point point : result.getIntervalAssociatedPreviewLumenPixels()) {
            actual.add(Long.valueOf(pack(point.x, point.y)));
        }
        assertEquals(expected, actual);
        assertFalse(actual.contains(Long.valueOf(pack(12, 12))));
    }

    @Test
    void anotherLumenComponentIsNeverAdded() {
        final BaseFixture base = baseLumenFixture();
        // Separate lumen component at (15,15).
        base.processor.set(15, 15, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertFalse(contains(result.getIntervalAssociatedPreviewLumenPixels(), p(15, 15)));
    }

    @Test
    void intervalAssociatedPreviewAreaIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        assertEquals(base.lumen.size() + 2, result.getIntervalAssociatedPreviewArea());
        assertEquals(result.getIntervalAssociatedPreviewArea(),
                result.getIntervalAssociatedPreviewLumenPixels().size());
    }

    // ------------------------------------------------------------------
    // Perimeter and circularity
    // ------------------------------------------------------------------

    @Test
    void onePixelPerimeterEqualsFour() {
        final List<Point> lumen = points(p(5, 5));
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor,
                lumen,
                Collections.<Point>emptyList(),
                loop,
                range(0, 0),
                ORIG_CIRC,
                WHOLE_CIRC,
                WHOLE_CHANGE);
        assertEquals(4, result.getIntervalAssociatedPreviewPerimeter());
    }

    @Test
    void twoByTwoPerimeterEqualsEight() {
        final List<Point> lumen = rect(5, 5, 2, 2);
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor,
                lumen,
                Collections.<Point>emptyList(),
                loop,
                range(0, 0),
                ORIG_CIRC,
                WHOLE_CIRC,
                WHOLE_CHANGE);
        assertEquals(8, result.getIntervalAssociatedPreviewPerimeter());
    }

    @Test
    void perimeterUsesCardinalExposure() {
        final List<Point> lumen = points(p(5, 5), p(6, 5));
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor,
                lumen,
                Collections.<Point>emptyList(),
                loop,
                range(0, 0),
                ORIG_CIRC,
                WHOLE_CIRC,
                WHOLE_CHANGE);
        // Two adjacent pixels: 6 exposed cardinal edges, not 8.
        assertEquals(6, result.getIntervalAssociatedPreviewPerimeter());
    }

    @Test
    void intervalAssociatedCircularityFormulaIsCorrect() {
        final List<Point> lumen = points(p(5, 5));
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor,
                lumen,
                Collections.<Point>emptyList(),
                loop,
                range(0, 0),
                ORIG_CIRC,
                WHOLE_CIRC,
                WHOLE_CHANGE);
        final double expected = 4.0 * Math.PI * 1.0 / (4.0 * 4.0);
        assertEquals(expected, result.getIntervalAssociatedPreviewCircularity(), 1.0e-12);
    }

    @Test
    void intervalAssociatedCircularityChangeIsCorrect() {
        final List<Point> lumen = points(p(5, 5));
        final ByteProcessor processor = filled(12, 12, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        final double original = 0.4;
        final ProposedFillComponentAnalysisResult result = analyzer.analyzeForTesting(
                processor,
                lumen,
                Collections.<Point>emptyList(),
                loop,
                range(0, 0),
                original,
                WHOLE_CIRC,
                WHOLE_CHANGE);
        assertEquals(
                result.getIntervalAssociatedPreviewCircularity() - original,
                result.getIntervalAssociatedCircularityChange(),
                0.0);
    }

    @Test
    void wholeEllipseMetricsArePreservedExactly() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertEquals(WHOLE_CIRC, result.getWholeEllipseCircularity(), 0.0);
        assertEquals(WHOLE_CHANGE, result.getWholeEllipseCircularityChange(), 0.0);
        assertEquals(WHOLE_CIRC, result.getWholeEllipsePreview().getPreviewCircularity(), 0.0);
        assertEquals(WHOLE_CHANGE, result.getWholeEllipsePreview().getCircularityChange(), 0.0);
    }

    // ------------------------------------------------------------------
    // Fractions and flags
    // ------------------------------------------------------------------

    @Test
    void associatedFillFractionIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        assertEquals(2.0 / 3.0, result.getIntervalAssociatedFillFraction(), 1.0e-12);
    }

    @Test
    void primaryTotalFractionIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        assertEquals(2.0 / 3.0, result.getPrimaryComponentFillFraction(), 1.0e-12);
    }

    @Test
    void primaryAssociatedFractionIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(1.0 / 2.0, result.getPrimaryWithinAssociatedFraction(), 1.0e-12);
    }

    @Test
    void exactlyOneIntervalAssociatedComponentIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> one = points(p(5, 4), p(6, 4));
        paintFill(base.processor, one, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult single = analyze(base, one, range(0, 1));
        assertTrue(single.isExactlyOneIntervalAssociatedComponent());

        final List<Point> two = points(p(5, 4), p(8, 4));
        paintFill(base.processor, two, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult multi = analyze(base, two, range(0, 3));
        assertFalse(multi.isExactlyOneIntervalAssociatedComponent());
    }

    @Test
    void allProposedFillIsIntervalAssociatedIsCorrect() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> all = points(p(5, 4), p(6, 4));
        paintFill(base.processor, all, ClassifierLabels.WOOD_RESULT_VALUE);
        assertTrue(analyze(base, all, range(0, 1)).isAllProposedFillIsIntervalAssociated());

        final List<Point> mixed = points(p(5, 4), p(12, 12));
        paintFill(base.processor, mixed, ClassifierLabels.WOOD_RESULT_VALUE);
        assertFalse(analyze(base, mixed, range(0, 0)).isAllProposedFillIsIntervalAssociated());
    }

    // ------------------------------------------------------------------
    // Immutability / non-mutation
    // ------------------------------------------------------------------

    @Test
    void allResultListsAreImmutable() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getAllComponents().add(result.getAllComponents().get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getIntervalAssociatedComponents()
                        .add(result.getIntervalAssociatedComponents().get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getNonAssociatedComponents().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getIntervalExteriorSeedPixels().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getIntervalAssociatedFillPixels().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getNonAssociatedFillPixels().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getIntervalAssociatedPreviewLumenPixels().clear());
    }

    @Test
    void returnedPointsCannotMutateInternalState() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        final Point seed = result.getIntervalExteriorSeedPixels().get(0);
        final int ox = seed.x;
        seed.x = -99;
        assertEquals(ox, result.getIntervalExteriorSeedPixels().get(0).x);

        final Point componentPixel = result.getAllComponents().get(0).getPixels().get(0);
        final int cx = componentPixel.x;
        componentPixel.x = -88;
        assertEquals(cx, result.getAllComponents().get(0).getPixels().get(0).x);
    }

    @Test
    void returnedRectanglesCannotMutateInternalState() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 1));
        final Rectangle bounds = result.getAllComponents().get(0).getBounds();
        final int ox = bounds.x;
        bounds.x = -50;
        assertEquals(ox, result.getAllComponents().get(0).getBounds().x);
    }

    @Test
    void sourceByteProcessorRemainsUnchanged() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final byte[] before = copyBytes((byte[]) base.processor.getPixels());
        analyze(base, fill, range(0, 0));
        assertTrue(sameBytes(before, (byte[]) base.processor.getPixels()));
    }

    @Test
    void allSuppliedInputObjectsRemainUnchanged() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final List<Point> lumenSnapshot = copyPoints(base.lumen);
        final List<Point> fillSnapshot = copyPoints(fill);
        final List<Integer> interval = range(0, 1);
        final List<Integer> intervalSnapshot = new ArrayList<Integer>(interval);
        final Point lumenPoint = base.lumen.get(0);
        final int lx = lumenPoint.x;
        final int ly = lumenPoint.y;
        analyze(base, fill, interval);
        assertEquals(lumenSnapshot, base.lumen);
        assertEquals(fillSnapshot, fill);
        assertEquals(intervalSnapshot, interval);
        assertEquals(lx, lumenPoint.x);
        assertEquals(ly, lumenPoint.y);
    }

    @Test
    void repeatedAnalysisIsDeterministic() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(12, 12));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult a = analyze(base, fill, range(0, 0));
        final ProposedFillComponentAnalysisResult b = analyze(base, fill, range(0, 0));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void noComponentAcceptanceDecisionExists() {
        for (final Method method : ProposedFillComponentAnalyzer.class.getDeclaredMethods()) {
            final String name = method.getName().toLowerCase();
            assertFalse(name.contains("accept"));
            assertFalse(name.contains("reject"));
            assertFalse(name.contains("threshold"));
        }
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 0));
        assertNotNull(result.getPrimaryIntervalAssociatedComponent());
        // Diagnostic flags only; no acceptance outcome field beyond association diagnostics.
        assertTrue(result.isExactlyOneIntervalAssociatedComponent());
    }

    @Test
    void noSourcePixelModificationOccurs() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final byte[] before = copyBytes((byte[]) base.processor.getPixels());
        analyze(base, fill, range(0, 0));
        assertTrue(sameBytes(before, (byte[]) base.processor.getPixels()));
        for (int i = 0; i < before.length; i++) {
            assertFalse(((before[i] & 0xff) == 3)
                    && (((byte[]) base.processor.getPixels())[i] & 0xff) != (before[i] & 0xff));
        }
    }

    // ------------------------------------------------------------------
    // Synthetic scenarios
    // ------------------------------------------------------------------

    @Test
    void syntheticOneLargeAssociatedPlusSmallNonAssociated() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = new ArrayList<Point>();
        fill.addAll(points(p(5, 4), p(6, 4), p(7, 4), p(8, 4)));
        fill.addAll(points(p(12, 12), p(14, 14), p(16, 10)));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(1, result.getIntervalAssociatedComponentCount());
        assertEquals(3, result.getNonAssociatedComponentCount());
        assertEquals(4, result.getIntervalAssociatedFillPixelCount());
        assertEquals(3, result.getNonAssociatedFillPixelCount());
        assertTrue(result.isExactlyOneIntervalAssociatedComponent());
        assertFalse(result.isAllProposedFillIsIntervalAssociated());
        assertEquals(4, result.getPrimaryIntervalAssociatedComponent().getArea());
    }

    @Test
    void syntheticTwoSeparateIntervalAssociatedComponents() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        assertEquals(0, result.getNonAssociatedComponentCount());
        assertFalse(result.isExactlyOneIntervalAssociatedComponent());
        assertTrue(result.isAllProposedFillIsIntervalAssociated());
    }

    @Test
    void syntheticAllFillInOneAssociatedComponent() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4), p(7, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 2));
        assertEquals(1, result.getTotalComponentCount());
        assertEquals(1, result.getIntervalAssociatedComponentCount());
        assertTrue(result.isAllProposedFillIsIntervalAssociated());
        assertTrue(result.isExactlyOneIntervalAssociatedComponent());
        assertEquals(1.0, result.getIntervalAssociatedFillFraction(), 0.0);
        assertEquals(1.0, result.getPrimaryWithinAssociatedFraction(), 0.0);
    }

    @Test
    void syntheticNoFillMatchingAnyIntervalExteriorSeed() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(12, 12), p(13, 13));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, range(0, 3));
        assertEquals(0, result.getMatchedIntervalExteriorSeedPixelCount());
        assertEquals(0, result.getIntervalAssociatedComponentCount());
        assertNull(result.getPrimaryIntervalAssociatedComponent());
        assertEquals(0.0, result.getIntervalAssociatedFillFraction(), 0.0);
        assertFalse(result.isAllProposedFillIsIntervalAssociated());
    }

    @Test
    void syntheticWrappingSelectedInterval() {
        final BaseFixture base = baseLumenFixture();
        // Edges wrap: 14,15,0,1 → exteriors (4,6),(4,5),(5,4),(6,4).
        // Use non-8-adjacent fills so they remain two components.
        final List<Integer> wrapping = indexList(14, 15, 0, 1);
        final List<Point> fill = points(p(4, 6), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(base, fill, wrapping);
        assertTrue(result.getInwardOutlierDetection().getSelectedInterval().isWrapsAround());
        assertEquals(4, result.getIntervalExteriorEdgeCount());
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        assertEquals(2, result.getMatchedIntervalExteriorSeedPixelCount());
    }

    @Test
    void syntheticFourDirectedEdgeExteriorMappingsViaAnalysis() {
        final BaseFixture base = baseLumenFixture();
        // One seed per cardinal side; spaced so they are not 8-adjacent.
        final List<Point> fill = points(
                p(6, 4),  // EAST
                p(9, 6),  // SOUTH
                p(7, 9),  // WEST
                p(4, 7)); // NORTH
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyze(
                base, fill, range(0, 15));
        assertEquals(4, result.getMatchedIntervalExteriorSeedPixelCount());
        assertEquals(4, result.getIntervalAssociatedComponentCount());
        assertEquals(4, result.getIntervalAssociatedFillPixelCount());
        assertTrue(result.isAllProposedFillIsIntervalAssociated());
    }

    @Test
    void publicAnalyzeMatchesAnalyzeForTestingCoreBehavior() {
        final PublicAnalyzeFixture fixture = publicAnalyzeFixture();
        final ProposedFillComponentAnalysisResult fromPublic = analyzer.analyze(
                fixture.processor, fixture.preview, fixture.detection);
        final ProposedFillComponentAnalysisResult fromHook = analyzer.analyzeForTesting(
                fixture.processor,
                fixture.preview.getSelectedObservedLumenPixels(),
                fixture.preview.getRefittedProposedFillPixels(),
                fixture.detection.getResidualProfile().getBoundaryLoop(),
                fixture.detection.getSelectedInterval().getOrderedBoundaryIndices(),
                fixture.preview.getOriginalCircularity(),
                fixture.preview.getPreviewCircularity(),
                fixture.preview.getCircularityChange());
        assertEquals(fromHook.getTotalComponentCount(), fromPublic.getTotalComponentCount());
        assertEquals(fromHook.getIntervalAssociatedComponentCount(),
                fromPublic.getIntervalAssociatedComponentCount());
        assertEquals(fromHook.getAllComponents(), fromPublic.getAllComponents());
        assertEquals(fromHook.getIntervalAssociatedFillPixels(),
                fromPublic.getIntervalAssociatedFillPixels());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ProposedFillComponentAnalysisResult analyze(
            final BaseFixture base,
            final List<Point> fill,
            final List<Integer> intervalIndices) {
        return analyzer.analyzeForTesting(
                base.processor,
                base.lumen,
                fill,
                base.loop,
                intervalIndices,
                ORIG_CIRC,
                WHOLE_CIRC,
                WHOLE_CHANGE);
    }

    private BaseFixture baseLumenFixture() {
        final List<Point> lumen = rect(5, 5, 4, 4);
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        assertEquals(16, loop.getDirectedEdges().size());
        return new BaseFixture(processor, lumen, loop);
    }

    private LShapeFixture lShapeFixture() {
        final List<Point> lumen = new ArrayList<Point>();
        for (int x = 5; x <= 8; x++) {
            lumen.add(new Point(x, 5));
        }
        for (int y = 6; y <= 8; y++) {
            lumen.add(new Point(5, y));
        }
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        return new LShapeFixture(new BaseFixture(processor, lumen, loop));
    }

    private PublicAnalyzeFixture publicAnalyzeFixture() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult viaHook = analyze(base, fill, range(0, 0));
        return new PublicAnalyzeFixture(
                base.processor,
                viaHook.getWholeEllipsePreview(),
                viaHook.getInwardOutlierDetection());
    }

    /** Inclusive non-wrapping contiguous index run. */
    private static List<Integer> range(final int startInclusive, final int endInclusive) {
        if (endInclusive < startInclusive) {
            throw new IllegalArgumentException("endInclusive < startInclusive");
        }
        final List<Integer> list = new ArrayList<Integer>(endInclusive - startInclusive + 1);
        for (int i = startInclusive; i <= endInclusive; i++) {
            list.add(Integer.valueOf(i));
        }
        return list;
    }

    /** Explicit contiguous circular index list (may wrap). */
    private static List<Integer> indexList(final int... values) {
        final List<Integer> list = new ArrayList<Integer>(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(Integer.valueOf(values[i]));
        }
        return list;
    }

    private static Point p(final int x, final int y) {
        return new Point(x, y);
    }

    private static List<Point> points(final Point... pts) {
        final List<Point> list = new ArrayList<Point>(pts.length);
        for (int i = 0; i < pts.length; i++) {
            list.add(new Point(pts[i].x, pts[i].y));
        }
        return list;
    }

    private static List<Point> rect(
            final int x0, final int y0, final int width, final int height) {
        final List<Point> list = new ArrayList<Point>(width * height);
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                list.add(new Point(x, y));
            }
        }
        return list;
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) value;
        }
        return processor;
    }

    private static void paint(
            final ByteProcessor processor, final List<Point> points, final int value) {
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            processor.set(point.x, point.y, value);
        }
    }

    private static void paintFill(
            final ByteProcessor processor, final List<Point> fill, final int value) {
        paint(processor, fill, value);
    }

    private static List<Point> copyPoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            copy.add(new Point(point.x, point.y));
        }
        return copy;
    }

    private static byte[] copyBytes(final byte[] source) {
        final byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static boolean sameBytes(final byte[] a, final byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(final List<Point> points, final Point target) {
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            if (point.x == target.x && point.y == target.y) {
                return true;
            }
        }
        return false;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static final class BaseFixture {
        private final ByteProcessor processor;
        private final List<Point> lumen;
        private final OrderedBoundaryLoop loop;

        private BaseFixture(
                final ByteProcessor processor,
                final List<Point> lumen,
                final OrderedBoundaryLoop loop) {
            this.processor = processor;
            this.lumen = lumen;
            this.loop = loop;
        }
    }

    private static final class LShapeFixture {
        private final BaseFixture base;

        private LShapeFixture(final BaseFixture base) {
            this.base = base;
        }
    }

    private static final class PublicAnalyzeFixture {
        private final ByteProcessor processor;
        private final RefittedEllipseFillPreviewResult preview;
        private final InwardOutlierDetectionResult detection;

        private PublicAnalyzeFixture(
                final ByteProcessor processor,
                final RefittedEllipseFillPreviewResult preview,
                final InwardOutlierDetectionResult detection) {
            this.processor = processor;
            this.preview = preview;
            this.detection = detection;
        }
    }
}
