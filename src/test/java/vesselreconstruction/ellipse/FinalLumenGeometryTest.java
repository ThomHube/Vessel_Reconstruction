package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalLumenGeometryTest {

    @Test
    void keepsExactOriginalPixelsAndAddsAcceptedCandidates() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(component,
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "accepted", Collections.singletonList(new Point(5, 3)));
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.singletonList(new Point(5, 3)), 0, 0L);

        final List<FinalLumenGeometry.ComponentGeometry> geometry =
                FinalLumenGeometry.buildAll(image, batch, 1);
        assertEquals(1, geometry.size());
        final FinalLumenGeometry.ComponentGeometry g = geometry.get(0);
        assertEquals(component.getArea() + 1, g.areaPx);
        assertTrue(g.containsGlobal(3, 3));
        assertTrue(g.containsGlobal(5, 3));
        assertTrue(g.mask.length < 9 * 9);
        assertEquals(ClassifierLabels.WOOD_RESULT_VALUE, image.get(5, 3));
        assertEquals(ClassifierLabels.LUMEN_RESULT_VALUE,
                FinalLumenGeometry.buildReconstructedProcessor(image, batch).get(5, 3));
    }

    @Test
    void localMaskIncludesOnePixelFalseBorder() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertEquals(component.getBounds().width + 2, g.maskWidth);
        assertEquals(component.getBounds().height + 2, g.maskHeight);
        assertEquals(component.getBounds().x - 1, g.maskOriginX);
        assertEquals(component.getBounds().y - 1, g.maskOriginY);
        // Border cells are false.
        assertFalse(g.mask[0]);
        assertFalse(g.mask[g.maskWidth - 1]);
        assertFalse(g.mask[(g.maskHeight - 1) * g.maskWidth]);
    }

    @Test
    void localGlobalCoordinateConversionIsCorrect() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        final Point local = new Point(1, 1);
        final Point global = g.localToGlobal(local);
        assertEquals(g.maskOriginX + 1, global.x);
        assertEquals(g.maskOriginY + 1, global.y);
        assertTrue(g.containsGlobal(global.x, global.y));
    }

    @Test
    void componentAtGlobalOriginUsesNegativeLocalOrigin() {
        final ByteProcessor image = new ByteProcessor(8, 8);
        image.set(0, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(1, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(8, 8, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertEquals(-1, g.maskOriginX);
        assertEquals(-1, g.maskOriginY);
        assertTrue(g.touchesBorder);
        assertTrue(g.containsGlobal(0, 0));
    }

    @Test
    void componentAtBottomRightEdgeWorks() {
        final ByteProcessor image = new ByteProcessor(8, 8);
        image.set(6, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(7, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(8, 8, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertTrue(g.containsGlobal(7, 7));
        assertTrue(g.touchesBorder);
        assertEquals(component.getBounds().x - 1, g.maskOriginX);
        assertEquals(component.getBounds().y - 1, g.maskOriginY);
    }

    @Test
    void reconstructionCandidatesExpandFinalLocalBounds() {
        final ByteProcessor image = source();
        // Wood at (5,3) for accepted candidate.
        image.set(5, 3, ClassifierLabels.WOOD_RESULT_VALUE);
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(component,
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "accepted", Collections.singletonList(new Point(5, 3)));
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.singletonList(new Point(5, 3)), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertEquals(new Rectangle(3, 3, 3, 2), g.bounds);
        assertEquals(5, g.maskWidth);
        assertEquals(4, g.maskHeight);
        assertEquals(2, g.maskOriginX);
        assertEquals(2, g.maskOriginY);
    }

    @Test
    void rejectsCandidateThatOverlapsOriginalLumen() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(component,
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "invalid", Collections.singletonList(new Point(3, 3)));
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.singletonList(new Point(3, 3)), 0, 0L);
        assertThrows(IllegalStateException.class,
                () -> FinalLumenGeometry.buildAll(image, batch, 1));
    }

    @Test
    void rejectsOverlapBetweenTwoFinalComponentsViaSharedBitSet() {
        final ByteProcessor image = new ByteProcessor(12, 8);
        image.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(8, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(3, 2, ClassifierLabels.WOOD_RESULT_VALUE);
        final List<LumenComponentDescriptor> components =
                new LumenComponentExtractor().extract(image);
        assertEquals(2, components.size());
        final LumenComponentDescriptor a = components.get(0);
        final LumenComponentDescriptor b = components.get(1);
        final LumenBatchItemResult itemA = LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(a,
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "expand", Collections.singletonList(new Point(3, 2)));
        // Force overlap: B also claims (3,2) via a fabricated reconstructed candidate.
        // Component B is at x=8; give it a candidate that collides with A's expansion.
        final LumenBatchItemResult itemB = LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(b,
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "overlap", Collections.singletonList(new Point(3, 2)));
        final AllLumenBatchResult batch = new AllLumenBatchResult(12, 8, 2, 2,
                Arrays.asList(itemA, itemB),
                Collections.singletonList(new Point(3, 2)), 0, 0L);
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> FinalLumenGeometry.buildAll(image, batch, 1));
        assertTrue(ex.getMessage().contains("overlap"));
    }

    @Test
    void preservesAreaBoundsBorderTouchingAndCentroidSemantics() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertEquals(component.getArea(), g.areaPx);
        assertEquals(component.getBounds(), g.bounds);
        assertFalse(g.touchesBorder);
        assertEquals(component.getBounds().x + component.getBounds().width / 2.0, g.centroidX, 0.0);
        assertEquals(component.getBounds().y + component.getBounds().height / 2.0, g.centroidY, 0.0);
    }

    @Test
    void wallLengthAndEllipseMatchLegacyFullImageMaskRepresentation() {
        final ByteProcessor image = filledDisk(21, 21, 10, 10, 4);
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(21, 21, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry local =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);

        final boolean[] full = expandToFullImageMask(local);
        final MooreChainPerimeter.Result localWall = MooreChainPerimeter.measure(
                local.mask, local.maskWidth, local.maskHeight, 1.0, 1.0);
        final MooreChainPerimeter.Result fullWall = MooreChainPerimeter.measure(
                full, local.imageWidth, local.imageHeight, 1.0, 1.0);
        assertEquals(fullWall.lengthPx, localWall.lengthPx, 1.0e-12);
        assertEquals(fullWall.lengthUm, localWall.lengthUm, 1.0e-12);

        final List<Point> localGlobalBoundary = translate(local, localWall.boundaryPixels);
        assertEquals(fullWall.boundaryPixels.size(), localGlobalBoundary.size());
        assertEquals(new java.util.HashSet<Point>(fullWall.boundaryPixels),
                new java.util.HashSet<Point>(localGlobalBoundary));
    }

    @Test
    void nearestWallUsesTranslatedGlobalBoundaryCoordinates() {
        final ByteProcessor image = new ByteProcessor(30, 20);
        paintRect(image, 3, 5, 4, 4);
        paintRect(image, 18, 5, 4, 4);
        final List<LumenComponentDescriptor> components =
                new LumenComponentExtractor().extract(image);
        assertEquals(2, components.size());
        final List<LumenBatchItemResult> items = new ArrayList<LumenBatchItemResult>();
        for (int i = 0; i < components.size(); i++) {
            items.add(LumenBatchItemResult.skipped(components.get(i),
                    LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L));
        }
        final AllLumenBatchResult batch = new AllLumenBatchResult(30, 20, 2, 2,
                items, Collections.<Point>emptyList(), 0, 0L);
        final List<FinalLumenGeometry.ComponentGeometry> geometry =
                FinalLumenGeometry.buildAll(image, batch, 1);

        final List<NearestLumenDistance.LumenGeometry> localValid =
                new ArrayList<NearestLumenDistance.LumenGeometry>();
        final List<NearestLumenDistance.LumenGeometry> fullValid =
                new ArrayList<NearestLumenDistance.LumenGeometry>();
        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            final MooreChainPerimeter.Result wall = MooreChainPerimeter.measure(
                    g.mask, g.maskWidth, g.maskHeight, 1.0, 1.0);
            localValid.add(new NearestLumenDistance.LumenGeometry(
                    "L" + g.lumenIdSegment, g.centroidX, g.centroidY,
                    translate(g, wall.boundaryPixels)));
            final boolean[] full = expandToFullImageMask(g);
            final MooreChainPerimeter.Result fullWall = MooreChainPerimeter.measure(
                    full, g.imageWidth, g.imageHeight, 1.0, 1.0);
            fullValid.add(new NearestLumenDistance.LumenGeometry(
                    "L" + g.lumenIdSegment, g.centroidX, g.centroidY, fullWall.boundaryPixels));
        }
        final NearestLumenDistance.Result[] localResults =
                NearestLumenDistance.computeAll(localValid, 1.0, 1.0);
        final NearestLumenDistance.Result[] fullResults =
                NearestLumenDistance.computeAll(fullValid, 1.0, 1.0);
        assertEquals(fullResults[0].nearestCentroidDistancePx,
                localResults[0].nearestCentroidDistancePx, 1.0e-12);
        assertEquals(fullResults[0].nearestWallDistancePx,
                localResults[0].nearestWallDistancePx, 1.0e-12);
        assertEquals(fullResults[1].nearestWallDistancePx,
                localResults[1].nearestWallDistancePx, 1.0e-12);
    }

    @Test
    void zeroReconstructedComponentsRemainsValid() {
        final ByteProcessor image = source();
        final LumenComponentDescriptor component = new LumenComponentExtractor().extract(image).get(0);
        final LumenBatchItemResult item = LumenBatchItemResult.skipped(component,
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L);
        final AllLumenBatchResult batch = new AllLumenBatchResult(9, 9, 1, 1,
                Collections.singletonList(item), Collections.<Point>emptyList(), 0, 0L);
        final FinalLumenGeometry.ComponentGeometry g =
                FinalLumenGeometry.buildAll(image, batch, 1).get(0);
        assertFalse(g.reconstructed);
        assertEquals(0, g.changedPixels);
    }

    private static List<Point> translate(
            final FinalLumenGeometry.ComponentGeometry g, final List<Point> local) {
        final List<Point> global = new ArrayList<Point>(local.size());
        for (int i = 0; i < local.size(); i++) {
            global.add(g.localToGlobal(local.get(i)));
        }
        return global;
    }

    /** Test-only expansion of a local mask into the former full-image representation. */
    private static boolean[] expandToFullImageMask(final FinalLumenGeometry.ComponentGeometry g) {
        final boolean[] full = new boolean[g.imageWidth * g.imageHeight];
        for (int ly = 0; ly < g.maskHeight; ly++) {
            for (int lx = 0; lx < g.maskWidth; lx++) {
                if (!g.mask[ly * g.maskWidth + lx]) {
                    continue;
                }
                final int gx = lx + g.maskOriginX;
                final int gy = ly + g.maskOriginY;
                if (gx >= 0 && gy >= 0 && gx < g.imageWidth && gy < g.imageHeight) {
                    full[gy * g.imageWidth + gx] = true;
                }
            }
        }
        return full;
    }

    private static ByteProcessor source() {
        final ByteProcessor image = new ByteProcessor(9, 9);
        image.set(3, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(4, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(3, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(4, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        return image;
    }

    private static ByteProcessor filledDisk(
            final int width, final int height, final int cx, final int cy, final int radius) {
        final ByteProcessor image = new ByteProcessor(width, height);
        final int r2 = radius * radius;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int dx = x - cx;
                final int dy = y - cy;
                if (dx * dx + dy * dy <= r2) {
                    image.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                }
            }
        }
        return image;
    }

    private static void paintRect(
            final ByteProcessor image, final int x0, final int y0, final int w, final int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                image.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
            }
        }
    }
}
