package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memory-focused and reference-equivalence tests for local ComponentGeometry masks.
 */
class FinalLumenGeometryMemoryTest {

    private static final int LARGE_WIDTH = 3688;
    private static final int LARGE_HEIGHT = 2076;
    private static final int LARGE_COMPONENT_COUNT = 1000;

    @Test
    void largeImageWithManyTinyComponentsDoesNotRetainFullImageMasks() {
        final ByteProcessor image = new ByteProcessor(LARGE_WIDTH, LARGE_HEIGHT);
        // Place separated 1-pixel lumina on a sparse grid (spacing 8 avoids merge).
        final int spacing = 8;
        int painted = 0;
        for (int y = 4; y < LARGE_HEIGHT - 4 && painted < LARGE_COMPONENT_COUNT; y += spacing) {
            for (int x = 4; x < LARGE_WIDTH - 4 && painted < LARGE_COMPONENT_COUNT; x += spacing) {
                image.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                painted++;
            }
        }
        assertEquals(LARGE_COMPONENT_COUNT, painted);

        final List<LumenComponentDescriptor> components =
                new LumenComponentExtractor().extract(image);
        assertEquals(LARGE_COMPONENT_COUNT, components.size());

        final List<LumenBatchItemResult> items = new ArrayList<LumenBatchItemResult>(components.size());
        for (int i = 0; i < components.size(); i++) {
            items.add(LumenBatchItemResult.skipped(components.get(i),
                    LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L));
        }
        final AllLumenBatchResult batch = new AllLumenBatchResult(
                LARGE_WIDTH, LARGE_HEIGHT, LARGE_COMPONENT_COUNT, LARGE_COMPONENT_COUNT,
                items, Collections.<Point>emptyList(), 0, 0L);

        final List<FinalLumenGeometry.ComponentGeometry> geometry =
                FinalLumenGeometry.buildAll(image, batch, 1);

        assertEquals(LARGE_COMPONENT_COUNT, geometry.size());
        final long imagePixels = (long) LARGE_WIDTH * (long) LARGE_HEIGHT;
        final long fullImageEquivalent = (long) LARGE_COMPONENT_COUNT * imagePixels;
        final long retained = FinalLumenGeometry.totalRetainedMaskCells(geometry);

        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            assertTrue(g.mask.length < imagePixels,
                    "component " + g.lumenIdSegment + " retained a full-image mask");
            // 1-pixel lumen → local mask 3x3 with border.
            assertEquals(3, g.maskWidth);
            assertEquals(3, g.maskHeight);
            assertEquals(9, g.mask.length);
        }
        assertTrue(retained < fullImageEquivalent / 1000L,
                "retained=" + retained + " fullEquivalent=" + fullImageEquivalent);
        // Explicit: never allocate the old reference full-image mask for every component.
        // (This test itself must not do that either.)
        assertTrue(retained <= (long) LARGE_COMPONENT_COUNT * 9L);
    }

    @Test
    void referenceEquivalenceAgainstExpandedFullImageMasks() throws Exception {
        final ByteProcessor image = new ByteProcessor(40, 30);
        paintRect(image, 5, 6, 6, 5);
        paintRect(image, 22, 8, 5, 6);
        image.set(12, 8, ClassifierLabels.WOOD_RESULT_VALUE);

        final List<LumenComponentDescriptor> components =
                new LumenComponentExtractor().extract(image);
        assertEquals(2, components.size());
        final List<LumenBatchItemResult> items = new ArrayList<LumenBatchItemResult>();
        items.add(LumenBatchItemResult.withStatus(
                LumenBatchItemResult.skipped(components.get(0),
                        LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "expand", Collections.singletonList(new Point(12, 8))));
        items.add(LumenBatchItemResult.skipped(components.get(1),
                LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, "none", 0L));
        final AllLumenBatchResult batch = new AllLumenBatchResult(
                40, 30, 2, 2, items, Collections.singletonList(new Point(12, 8)), 0, 0L);

        final List<FinalLumenGeometry.ComponentGeometry> geometry =
                FinalLumenGeometry.buildAll(image, batch, 1);

        final FreeEllipseFitter fitter = new FreeEllipseFitter();
        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            final boolean[] full = expandToFullImageMask(g);

            assertEquals(countTrue(full), g.areaPx);
            assertEquals(g.bounds, boundsOf(full, g.imageWidth, g.imageHeight));
            assertEquals(touchesBorder(full, g.imageWidth, g.imageHeight), g.touchesBorder);

            final MooreChainPerimeter.Result localWall = MooreChainPerimeter.measure(
                    g.mask, g.maskWidth, g.maskHeight, 1.0, 1.0);
            final MooreChainPerimeter.Result fullWall = MooreChainPerimeter.measure(
                    full, g.imageWidth, g.imageHeight, 1.0, 1.0);
            assertEquals(fullWall.lengthPx, localWall.lengthPx, 1.0e-12);
            assertEquals(fullWall.lengthUm, localWall.lengthUm, 1.0e-12);

            final List<Point> globalBoundary = g.boundaryPixelsGlobal();
            assertEquals(new java.util.HashSet<Point>(fullWall.boundaryPixels),
                    new java.util.HashSet<Point>(globalBoundary));

            if (globalBoundary.size() >= 6 && fullWall.boundaryPixels.size() >= 6) {
                final EllipseFitResult localFit = fitter.fit(toHalfPixel(globalBoundary));
                final EllipseFitResult fullFit = fitter.fit(toHalfPixel(fullWall.boundaryPixels));
                assertEquals(fullFit.getFittedEllipse().getSemiMajorAxis() * 2.0,
                        localFit.getFittedEllipse().getSemiMajorAxis() * 2.0, 1.0e-9);
                assertEquals(fullFit.getFittedEllipse().getSemiMinorAxis() * 2.0,
                        localFit.getFittedEllipse().getSemiMinorAxis() * 2.0, 1.0e-9);
                final double fullRatio = fullFit.getFittedEllipse().getSemiMinorAxis()
                        / fullFit.getFittedEllipse().getSemiMajorAxis();
                final double localRatio = localFit.getFittedEllipse().getSemiMinorAxis()
                        / localFit.getFittedEllipse().getSemiMajorAxis();
                assertEquals(fullRatio, localRatio, 1.0e-9);
                assertEquals(fullFit.getRootMeanSquaredResidual(),
                        localFit.getRootMeanSquaredResidual(), 1.0e-9);
            }
        }

        // Nearest-lumen distances: local translated vs full-image boundaries.
        final List<NearestLumenDistance.LumenGeometry> localValid =
                new ArrayList<NearestLumenDistance.LumenGeometry>();
        final List<NearestLumenDistance.LumenGeometry> fullValid =
                new ArrayList<NearestLumenDistance.LumenGeometry>();
        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            if (g.areaPx < 1 || g.touchesBorder) {
                continue;
            }
            localValid.add(new NearestLumenDistance.LumenGeometry(
                    "L" + g.lumenIdSegment, g.centroidX, g.centroidY,
                    g.boundaryPixelsGlobal()));
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
        assertEquals(fullResults.length, localResults.length);
        for (int i = 0; i < localResults.length; i++) {
            assertEquals(fullResults[i].nearestCentroidDistancePx,
                    localResults[i].nearestCentroidDistancePx, 1.0e-12);
            assertEquals(fullResults[i].nearestWallDistancePx,
                    localResults[i].nearestWallDistancePx, 1.0e-12);
        }
    }

    private static List<Point2D.Double> toHalfPixel(final List<Point> pixels) {
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(pixels.size());
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            points.add(new Point2D.Double(p.x + 0.5, p.y + 0.5));
        }
        return points;
    }

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

    private static int countTrue(final boolean[] mask) {
        int n = 0;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                n++;
            }
        }
        return n;
    }

    private static Rectangle boundsOf(final boolean[] full, final int width, final int height) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (full[y * width + x]) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean touchesBorder(final boolean[] full, final int width, final int height) {
        for (int x = 0; x < width; x++) {
            if (full[x] || full[(height - 1) * width + x]) {
                return true;
            }
        }
        for (int y = 0; y < height; y++) {
            if (full[y * width] || full[y * width + (width - 1)]) {
                return true;
            }
        }
        return false;
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
