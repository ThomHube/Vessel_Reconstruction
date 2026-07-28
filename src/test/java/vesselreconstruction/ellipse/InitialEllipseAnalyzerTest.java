package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Image-analysis tests for the initial free-ellipse prototype.
 * Uses only Java 8 APIs and does not open Fiji windows.
 */
class InitialEllipseAnalyzerTest {

    private final InitialEllipseAnalyzer analyzer = new InitialEllipseAnalyzer();

    @Test
    void nullProcessorRejected() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(null));
    }

    @Test
    void noLumenProducesNoLumenComponent() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyze(processor));
        assertTrue(ex.getMessage().contains("NO_LUMEN_COMPONENT"));
        assertTrue(ex.getMessage().contains("pixel value 2")
                || ex.getMessage().contains("pixel value "
                + ClassifierLabels.LUMEN_RESULT_VALUE));
    }

    @Test
    void oneIsolatedComponentIsFound() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 10, 12, 12, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(1, result.getLumenComponentCount());
        assertEquals(12 * 12, result.getSelectedComponentArea());
    }

    @Test
    void twoSeparateComponentsAreCounted() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 2, 2, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 20, 20, 8, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(2, result.getLumenComponentCount());
        assertEquals(64, result.getSelectedComponentArea());
    }

    @Test
    void diagonalPixelsBelongToOneComponent() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(5, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(7, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 10, 10, 8, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(8, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(9, 9, ClassifierLabels.LUMEN_RESULT_VALUE);

        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(1, result.getLumenComponentCount());
        assertEquals(3 + 2 + 64, result.getSelectedComponentArea());
    }

    @Test
    void cardinallySeparatedComponentsRemainSeparate() {
        final ByteProcessor processor = filled(30, 30, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 2, 2, 5, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 9, 2, 5, 5, ClassifierLabels.LUMEN_RESULT_VALUE); // gap of one wood column at x=7
        processor.set(7, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(8, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        // Make second larger so selection is clear, and ensure they are separate.
        fillRect(processor, 15, 15, 8, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getLumenComponentCount() >= 2);
    }

    @Test
    void largestComponentIsSelected() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 2, 2, 4, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 20, 20, 10, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(100, result.getSelectedComponentArea());
        assertEquals(20, result.getSelectedComponentBounds().x);
        assertEquals(20, result.getSelectedComponentBounds().y);
    }

    @Test
    void equalAreaTieSelectsSmallerMinY() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 20, 20, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 5, 5, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(36, result.getSelectedComponentArea());
        assertEquals(5, result.getSelectedComponentBounds().y);
        assertEquals(5, result.getSelectedComponentBounds().x);
    }

    @Test
    void equalAreaTieThenSelectsSmallerMinX() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 20, 10, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 5, 10, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(5, result.getSelectedComponentBounds().x);
        assertEquals(10, result.getSelectedComponentBounds().y);
    }

    @Test
    void selectedPixelsOrderedByYThenX() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 12, 5, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final List<Point> pixels = result.getSelectedLumenPixels();
        for (int i = 1; i < pixels.size(); i++) {
            final Point prev = pixels.get(i - 1);
            final Point curr = pixels.get(i);
            assertTrue(prev.y < curr.y || (prev.y == curr.y && prev.x <= curr.x));
        }
    }

    @Test
    void selectedComponentBoundsAreCorrect() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 8, 11, 7, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final Rectangle bounds = result.getSelectedComponentBounds();
        assertEquals(8, bounds.x);
        assertEquals(11, bounds.y);
        assertEquals(7, bounds.width);
        assertEquals(5, bounds.height);
    }

    @Test
    void singlePixelHasFourExposedEdges() {
        final List<Point> pixels = new java.util.ArrayList<Point>();
        pixels.add(new Point(4, 7));
        final List<Point2D.Double> support =
                InitialEllipseAnalyzer.supportPointsForSelectedPixels(pixels, 20, 20);
        assertEquals(4, support.size());
    }

    @Test
    void singleSelectedPixelSupportMidpoints() {
        final int x = 4;
        final int y = 7;
        final List<Point> pixels = new java.util.ArrayList<Point>();
        pixels.add(new Point(x, y));
        final List<Point2D.Double> support =
                InitialEllipseAnalyzer.supportPointsForSelectedPixels(pixels, 20, 20);
        assertEquals(4, support.size());
        assertEquals(x + 0.5, support.get(0).x, 0.0);
        assertEquals(y, support.get(0).y, 0.0);
        assertEquals(x + 1.0, support.get(1).x, 0.0);
        assertEquals(y + 0.5, support.get(1).y, 0.0);
        assertEquals(x + 0.5, support.get(2).x, 0.0);
        assertEquals(y + 1.0, support.get(2).y, 0.0);
        assertEquals(x, support.get(3).x, 0.0);
        assertEquals(y + 0.5, support.get(3).y, 0.0);
    }

    @Test
    void exactSinglePixelMidpointsWhenGeometricallyValid() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 10, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(9, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 9.5, 10.0));
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 9.0, 10.5));
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 9.5, 11.0));
        assertFalse(containsPoint(result.getBoundarySupportPoints(), 10.0, 10.5));
    }

    @Test
    void block2x2HasPerimeter8() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 12, 12, 2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        // Enlarge with connected area so fit succeeds, but measure a pure 2x2 separately
        // via dedicated component-only setup that still fits: use larger disk + assert on
        // a pure 2x2 image that is large enough? 2x2 has perimeter 8 and 8 support points.
        final ByteProcessor small = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(small, 8, 8, 2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        // 8 support points >= 6, distinct should be 8.
        final InitialEllipseAnalysisResult result = analyzer.analyze(small);
        assertEquals(8, result.getExposedEdgePerimeter());
        assertEquals(8, result.getBoundarySupportPoints().size());
    }

    @Test
    void rect3x2HasPerimeter10() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 5, 5, 3, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(10, result.getExposedEdgePerimeter());
    }

    @Test
    void cardinalSharedEdgesAreNotExposed() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 5, 5, 2, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        // Horizontal digon: shared vertical edge between (5,5) and (6,5) not exposed.
        // Perimeter should be 6 (each has 3 exposed).
        assertEquals(6, result.getExposedEdgePerimeter());
        assertFalse(containsPoint(result.getBoundarySupportPoints(), 6.0, 5.5));
    }

    @Test
    void diagonalContactDoesNotHideCardinalEdges() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(5, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        // Add more to allow fitting while keeping these two connected diagonally.
        fillRect(processor, 8, 8, 6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(7, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        // Pixel (5,5) should still expose all four cardinal edges (diagonal neighbor doesn't hide).
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 5.5, 5.0));
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 6.0, 5.5));
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 5.5, 6.0));
        assertTrue(containsPoint(result.getBoundarySupportPoints(), 5.0, 5.5));
    }

    @Test
    void supportPointsFollowDeterministicOrder() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 4, 4, 2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult first = analyzer.analyze(processor);
        final InitialEllipseAnalysisResult second = analyzer.analyze(processor);
        assertEquals(first.getBoundarySupportPoints(), second.getBoundarySupportPoints());
        // First pixel is (4,4); first exposed sides N,E,S,W -> N and W first among its edges,
        // but E shared? For (4,4): N yes, E shared with (5,4) no, S shared with (4,5) no, W yes.
        final List<Point2D.Double> support = first.getBoundarySupportPoints();
        assertEquals(4.5, support.get(0).x, 0.0);
        assertEquals(4.0, support.get(0).y, 0.0);
        assertEquals(4.0, support.get(1).x, 0.0);
        assertEquals(4.5, support.get(1).y, 0.0);
    }

    @Test
    void supportPointsAreEdgeMidpointsNotCenters() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 6, 6, 3, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        for (final Point2D.Double p : result.getBoundarySupportPoints()) {
            final boolean halfX = Math.abs(p.x - Math.rint(p.x)) > 0.1;
            final boolean halfY = Math.abs(p.y - Math.rint(p.y)) > 0.1;
            // Midpoints have exactly one half-integer coordinate.
            assertTrue(halfX ^ halfY);
            assertFalse(Math.abs(p.x - Math.floor(p.x) - 0.5) < 1e-9
                    && Math.abs(p.y - Math.floor(p.y) - 0.5) < 1e-9);
        }
    }

    @Test
    void insufficientBoundarySupportRejected() {
        // Create a lumen with fewer than 6 distinct support points - hard with connected pixels.
        // One pixel has 4; two cardinally connected have 6 perimeter - borderline.
        // Use one pixel only.
        final ByteProcessor processor = filled(10, 10, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(3, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyze(processor));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_BOUNDARY_SUPPORT"));
    }

    @Test
    void rasterizedCircularLumenProducesFiniteFit() {
        final ByteProcessor processor = filled(60, 60, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 30, 30, 12, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertFiniteFit(result);
        assertEquals(1, result.getLumenComponentCount());
    }

    @Test
    void rasterizedEllipticalLumenProducesFiniteFit() {
        final ByteProcessor processor = filled(80, 60, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillEllipseMask(processor, 40, 30, 22, 10, 0.0, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertFiniteFit(result);
        assertTrue(result.getEllipseFitResult().getFittedEllipse().getAxisRatio() > 1.2);
    }

    @Test
    void rasterizedRotatedEllipticalLumenProducesFiniteFit() {
        final ByteProcessor processor = filled(80, 80, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillEllipseMask(processor, 40, 40, 20, 9, 0.6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertFiniteFit(result);
    }

    @Test
    void nonCircularMaskNotForcedToEqualAxes() {
        final ByteProcessor processor = filled(80, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillEllipseMask(processor, 40, 25, 24, 8, 0.0, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final EllipseModel fitted = result.getEllipseFitResult().getFittedEllipse();
        assertTrue(Math.abs(fitted.getSemiMajorAxis() - fitted.getSemiMinorAxis()) > 1.0);
    }

    @Test
    void analyzerUsesEveryBoundarySupportPoint() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(
                result.getBoundarySupportPoints().size(),
                result.getEllipseFitResult().getResiduals().size());
    }

    @Test
    void noSupportPointIsExcluded() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(result.getExposedEdgePerimeter(), result.getBoundarySupportPoints().size());
        assertEquals(
                result.getBoundarySupportPoints().size(),
                result.getEllipseFitResult().getResiduals().size());
    }

    @Test
    void exposedEdgePerimeterEqualsSupportSize() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 10, 8, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(result.getBoundarySupportPoints().size(), result.getExposedEdgePerimeter());
    }

    @Test
    void selectedAreaEqualsSelectedPixelCount() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 10, 7, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(result.getSelectedLumenPixels().size(), result.getSelectedComponentArea());
    }

    @Test
    void proposedFillExcludesSelectedLumenPixels() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final Set<Long> selected = toKeys(result.getSelectedLumenPixels());
        for (final Point p : result.getProposedFillPixels()) {
            assertFalse(selected.contains(pack(p.x, p.y)));
        }
    }

    @Test
    void otherLumenInsideEllipseCountedSeparately() {
        final ByteProcessor processor = filled(60, 60, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 30, 30, 14, ClassifierLabels.LUMEN_RESULT_VALUE);
        // Small second lumen near center - will be inside fitted ellipse of outer? 
        // Better: large lumen ring-ish: outer disk and a small separate lumen nearby inside expected ellipse.
        fillDisk(processor, 30, 30, 3, ClassifierLabels.WOOD_RESULT_VALUE); // hole replaced by wood first
        // Create outer lumen annulus approximately by filling disk then clearing center with wood,
        // then place a second lumen blob inside the cleared center.
        final ByteProcessor ann = filled(70, 70, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(ann, 35, 35, 16, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillDisk(ann, 35, 35, 6, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(ann, 35, 35, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(ann);
        assertTrue(result.getLumenComponentCount() >= 2);
        assertTrue(result.getOtherLumenPixelsInsideEllipse() > 0);
    }

    @Test
    void otherLumenNotIncludedInProposedFill() {
        final ByteProcessor ann = filled(70, 70, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(ann, 35, 35, 16, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillDisk(ann, 35, 35, 6, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(ann, 35, 35, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(ann);
        for (final Point p : result.getProposedFillPixels()) {
            assertFalse((ann.get(p.x, p.y) & 0xff) == ClassifierLabels.LUMEN_RESULT_VALUE);
        }
    }

    @Test
    void proposedWoodPixelsCounted() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedWoodPixelCount() > 0);
        assertEquals(
                result.getProposedFillPixelCount(),
                result.getProposedWoodPixelCount()
                        + result.getProposedRayPixelCount()
                        + result.getProposedBackgroundPixelCount()
                        + result.getProposedOtherClassPixelCount());
    }

    @Test
    void proposedRayPixelsCounted() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.RAY_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedRayPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedRayPixelCount());
    }

    @Test
    void proposedBackgroundPixelsCounted() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedBackgroundPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedBackgroundPixelCount());
    }

    @Test
    void unknownNonLumenValuesCountedSeparately() {
        final ByteProcessor processor = filled(50, 50, 9);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedOtherClassPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedOtherClassPixelCount());
    }

    @Test
    void proposedFillClassCountsSumToTotal() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(20, 20, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(21, 20, ClassifierLabels.RAY_RESULT_VALUE);
        processor.set(22, 20, 9);
        // Ensure those pixels remain non-lumen and likely inside ellipse: put them near center.
        // Center area is lumen - place outside lumen but inside expected ellipse is hard.
        // Use wood background with mixed classes outside the disk.
        final ByteProcessor mixed = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(mixed, 25, 25, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 50; x++) {
                if ((mixed.get(x, y) & 0xff) == ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }
                final int v = (x + y) % 4;
                if (v == 0) {
                    mixed.set(x, y, ClassifierLabels.WOOD_RESULT_VALUE);
                } else if (v == 1) {
                    mixed.set(x, y, ClassifierLabels.RAY_RESULT_VALUE);
                } else if (v == 2) {
                    mixed.set(x, y, ClassifierLabels.BACKGROUND_RESULT_VALUE);
                } else {
                    mixed.set(x, y, 9);
                }
            }
        }
        fillDisk(mixed, 25, 25, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(mixed);
        assertEquals(
                result.getProposedFillPixelCount(),
                result.getProposedWoodPixelCount()
                        + result.getProposedRayPixelCount()
                        + result.getProposedBackgroundPixelCount()
                        + result.getProposedOtherClassPixelCount());
    }

    @Test
    void proposedFillOrderedByYThenX() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final List<Point> fill = result.getProposedFillPixels();
        for (int i = 1; i < fill.size(); i++) {
            final Point prev = fill.get(i - 1);
            final Point curr = fill.get(i);
            assertTrue(prev.y < curr.y || (prev.y == curr.y && prev.x <= curr.x));
        }
    }

    @Test
    void totalPixelCentersInsideEllipseConsistent() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final int expectedMin = result.getSelectedComponentArea()
                + result.getProposedFillPixelCount()
                + result.getOtherLumenPixelsInsideEllipse();
        assertEquals(expectedMin, result.getTotalPixelCentersInsideEllipse());
    }

    @Test
    void inputPixelsRemainUnchanged() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final byte[] before = copyBytes((byte[]) processor.getPixels());
        analyzer.analyze(processor);
        final byte[] after = (byte[]) processor.getPixels();
        assertEquals(before.length, after.length);
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], after[i]);
        }
    }

    @Test
    void repeatedAnalysisIsDeterministic() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillEllipseMask(processor, 25, 25, 14, 8, 0.4, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult a = analyzer.analyze(processor);
        final InitialEllipseAnalysisResult b = analyzer.analyze(processor);
        assertEquals(a, b);
    }

    @Test
    void resultListsCannotModifyInternalState() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 20, 20, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertThrows(UnsupportedOperationException.class,
                () -> result.getSelectedLumenPixels().add(new Point(0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getBoundarySupportPoints().add(new Point2D.Double(0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getProposedFillPixels().add(new Point(0, 0)));
    }

    @Test
    void returnedPointsCannotModifyInternalState() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 20, 20, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final Point p = result.getSelectedLumenPixels().get(0);
        final int ox = p.x;
        final int oy = p.y;
        p.x = -99;
        p.y = -99;
        assertEquals(ox, result.getSelectedLumenPixels().get(0).x);
        assertEquals(oy, result.getSelectedLumenPixels().get(0).y);
    }

    @Test
    void returnedSupportPointsCannotModifyInternalState() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 20, 20, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final Point2D.Double p = result.getBoundarySupportPoints().get(0);
        final double ox = p.x;
        final double oy = p.y;
        p.x = -1.0;
        p.y = -1.0;
        assertEquals(ox, result.getBoundarySupportPoints().get(0).x, 0.0);
        assertEquals(oy, result.getBoundarySupportPoints().get(0).y, 0.0);
    }

    @Test
    void returnedRectangleCannotModifyInternalState() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 20, 20, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        final Rectangle r = result.getSelectedComponentBounds();
        final int ox = r.x;
        r.x = -50;
        assertEquals(ox, result.getSelectedComponentBounds().x);
    }

    @Test
    void allFitDiagnosticsAreFinite() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillEllipseMask(processor, 25, 25, 14, 7, 0.3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertFiniteFit(result);
    }

    @Test
    void noPixelReconstructionPerformed() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final byte[] before = copyBytes((byte[]) processor.getPixels());
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedFillPixelCount() >= 0);
        final byte[] after = (byte[]) processor.getPixels();
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], after[i]);
        }
    }

    @Test
    void pixelValueTwoIsDetectedAsLumen() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 10, 10, 8, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(1, result.getLumenComponentCount());
        assertEquals(64, result.getSelectedComponentArea());
    }

    @Test
    void pixelValueThreeIsNotDetectedAsLumen() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.WOOD_RESULT_VALUE);
        fillRect(processor, 10, 10, 8, 8, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyze(processor));
        assertTrue(ex.getMessage().contains("NO_LUMEN_COMPONENT"));
    }

    @Test
    void imageContainingOnlyPixelValueThreeProducesNoLumen() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyze(processor));
        assertTrue(ex.getMessage().contains("NO_LUMEN_COMPONENT"));
        assertTrue(ex.getMessage().contains(
                String.valueOf(ClassifierLabels.LUMEN_RESULT_VALUE)));
    }

    @Test
    void pixelValueTwoComponentIsSelectedCorrectly() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillRect(processor, 2, 2, 4, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        fillRect(processor, 20, 20, 10, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertEquals(100, result.getSelectedComponentArea());
        assertEquals(20, result.getSelectedComponentBounds().x);
        assertEquals(20, result.getSelectedComponentBounds().y);
    }

    @Test
    void resultValueZeroCountedAsProposedWood() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedWoodPixelCount() > 0);
        assertEquals(0, result.getProposedRayPixelCount());
        assertEquals(0, result.getProposedBackgroundPixelCount());
    }

    @Test
    void resultValueOneCountedAsProposedRay() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.RAY_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedRayPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedRayPixelCount());
    }

    @Test
    void resultValueThreeCountedAsProposedBackground() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedBackgroundPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedBackgroundPixelCount());
    }

    @Test
    void resultValueFourCountedAsProposedUnknown() {
        final ByteProcessor processor = filled(50, 50, 4);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalysisResult result = analyzer.analyze(processor);
        assertTrue(result.getProposedOtherClassPixelCount() > 0);
        assertEquals(result.getProposedFillPixelCount(), result.getProposedOtherClassPixelCount());
    }

    private static byte[] copyBytes(final byte[] source) {
        final byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static void assertFiniteFit(final InitialEllipseAnalysisResult result) {
        final EllipseFitResult fit = result.getEllipseFitResult();
        final EllipseModel ellipse = fit.getFittedEllipse();
        assertTrue(Double.isFinite(ellipse.getCenterX()));
        assertTrue(Double.isFinite(ellipse.getCenterY()));
        assertTrue(Double.isFinite(ellipse.getSemiMajorAxis()));
        assertTrue(Double.isFinite(ellipse.getSemiMinorAxis()));
        assertTrue(Double.isFinite(ellipse.getRotationRadians()));
        assertTrue(Double.isFinite(fit.getRootMeanSquaredResidual()));
        assertTrue(Double.isFinite(fit.getMedianResidual()));
        assertTrue(Double.isFinite(fit.getMedianAbsoluteDeviation()));
        assertTrue(Double.isFinite(fit.getMaximumInwardResidual()));
        assertTrue(Double.isFinite(fit.getMaximumOutwardResidual()));
        assertTrue(Double.isFinite(fit.getInitialMeanSquaredResidual()));
        assertTrue(Double.isFinite(fit.getFinalMeanSquaredResidual()));
        assertNotNull(fit.getConvergenceReason());
        assertFalse(fit.getConvergenceReason().isEmpty());
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        final byte b = (byte) value;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = b;
        }
        return processor;
    }

    private static void fillRect(
            final ByteProcessor processor,
            final int x0,
            final int y0,
            final int w,
            final int h,
            final int value) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                processor.set(x, y, value);
            }
        }
    }

    private static void fillDisk(
            final ByteProcessor processor,
            final int cx,
            final int cy,
            final int radius,
            final int value) {
        final int r2 = radius * radius;
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                final int dx = x - cx;
                final int dy = y - cy;
                if (dx * dx + dy * dy <= r2) {
                    processor.set(x, y, value);
                }
            }
        }
    }

    private static void fillEllipseMask(
            final ByteProcessor processor,
            final double cx,
            final double cy,
            final double a,
            final double b,
            final double rotation,
            final int value) {
        final EllipseModel model = new EllipseModel(cx, cy, a, b, rotation);
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                if (model.normalizedRadius(x + 0.5, y + 0.5) <= 1.0) {
                    processor.set(x, y, value);
                }
            }
        }
    }

    private static boolean containsPoint(
            final List<Point2D.Double> points,
            final double x,
            final double y) {
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double p = points.get(i);
            if (p.x == x && p.y == y) {
                return true;
            }
        }
        return false;
    }

    private static Set<Long> toKeys(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>();
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            keys.add(pack(p.x, p.y));
        }
        return keys;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
