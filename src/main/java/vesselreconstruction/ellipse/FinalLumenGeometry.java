package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds final lumen geometry after accepted reconstruction and validates consistency.
 *
 * <p>Each {@link ComponentGeometry} retains only a compact local mask covering
 * the component's final bounding rectangle plus a one-pixel background border.
 * Overlap across components is validated with one shared image-sized
 * {@link BitSet}. Scientific centroid and measurement coordinates remain
 * global crop-local values.</p>
 *
 * <p>{@link ComponentGeometry#centroidX} / {@link ComponentGeometry#centroidY}
 * are the area centroid of final lumen pixel centers
 * {@code (x + 0.5, y + 0.5)} over every pixel in the final local mask
 * (original lumen pixels plus accepted reconstruction pixels).</p>
 */
public final class FinalLumenGeometry {

    public static final class ComponentGeometry {
        public final int lumenIdSegment;
        /** Local mask: true = lumen pixel within the local frame. */
        public final boolean[] mask;
        public final int maskWidth;
        public final int maskHeight;
        /** Global crop-local X of local mask coordinate (0,0). May be -1. */
        public final int maskOriginX;
        /** Global crop-local Y of local mask coordinate (0,0). May be -1. */
        public final int maskOriginY;
        public final int imageWidth;
        public final int imageHeight;
        public final int areaPx;
        /**
         * Area centroid X of final lumen pixel centers (global crop-local),
         * i.e. mean of {@code x + 0.5} over every final-mask lumen pixel.
         */
        public final double centroidX;
        /**
         * Area centroid Y of final lumen pixel centers (global crop-local),
         * i.e. mean of {@code y + 0.5} over every final-mask lumen pixel.
         */
        public final double centroidY;
        public final Rectangle bounds;
        public final boolean reconstructed;
        public final int changedPixels;
        public final boolean touchesBorder;
        public final LumenBatchItemResult sourceItem;

        public ComponentGeometry(
                final int lumenIdSegment,
                final boolean[] mask,
                final int maskWidth,
                final int maskHeight,
                final int maskOriginX,
                final int maskOriginY,
                final int imageWidth,
                final int imageHeight,
                final int areaPx,
                final double centroidX,
                final double centroidY,
                final Rectangle bounds,
                final boolean reconstructed,
                final int changedPixels,
                final boolean touchesBorder,
                final LumenBatchItemResult sourceItem) {
            this.lumenIdSegment = lumenIdSegment;
            this.mask = mask;
            this.maskWidth = maskWidth;
            this.maskHeight = maskHeight;
            this.maskOriginX = maskOriginX;
            this.maskOriginY = maskOriginY;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.areaPx = areaPx;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
            this.bounds = bounds;
            this.reconstructed = reconstructed;
            this.changedPixels = changedPixels;
            this.touchesBorder = touchesBorder;
            this.sourceItem = sourceItem;
        }

        /** Translates a local-mask pixel into global crop-local coordinates. */
        public Point localToGlobal(final Point local) {
            if (local == null) {
                throw new IllegalArgumentException("local point must not be null");
            }
            return new Point(local.x + maskOriginX, local.y + maskOriginY);
        }

        /**
         * Boundary pixels in global crop-local coordinates (Moore chain
         * extraction on the local mask, then translated).
         */
        public List<Point> boundaryPixelsGlobal() {
            final List<Point> local = MooreChainPerimeter.extractBoundaryPixels(
                    mask, maskWidth, maskHeight);
            final List<Point> global = new ArrayList<Point>(local.size());
            for (int i = 0; i < local.size(); i++) {
                global.add(localToGlobal(local.get(i)));
            }
            return global;
        }

        /** True when the global crop-local pixel is a lumen pixel in this mask. */
        public boolean containsGlobal(final int globalX, final int globalY) {
            final int localX = globalX - maskOriginX;
            final int localY = globalY - maskOriginY;
            if (localX < 0 || localY < 0 || localX >= maskWidth || localY >= maskHeight) {
                return false;
            }
            return mask[localY * maskWidth + localX];
        }

        public int retainedMaskCells() {
            return mask.length;
        }
    }

    private FinalLumenGeometry() {
    }

    public static List<ComponentGeometry> buildAll(
            final ByteProcessor maskedClassified,
            final AllLumenBatchResult batchResult,
            final int minimumArea) {
        if (maskedClassified == null || batchResult == null) {
            throw new IllegalArgumentException("maskedClassified and batchResult must not be null");
        }
        final int width = maskedClassified.getWidth();
        final int height = maskedClassified.getHeight();
        if (width != batchResult.getImageWidth() || height != batchResult.getImageHeight()) {
            throw new IllegalStateException("Batch result dimensions do not match classified image");
        }
        final long imagePixelsLong = (long) width * (long) height;
        if (imagePixelsLong <= 0L || imagePixelsLong > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Image pixel count is not representable: " + width + "x" + height);
        }
        final int imagePixels = (int) imagePixelsLong;

        final List<LumenComponentDescriptor> extracted =
                new LumenComponentExtractor().extract(maskedClassified);
        final List<ComponentGeometry> geometries = new ArrayList<ComponentGeometry>();
        final List<LumenBatchItemResult> items = batchResult.getItemResults();
        if (extracted.size() != items.size()) {
            throw new IllegalStateException("Original component count does not match batch result");
        }
        final Map<Integer, LumenComponentDescriptor> byId =
                new HashMap<Integer, LumenComponentDescriptor>();
        for (int i = 0; i < extracted.size(); i++) {
            final LumenComponentDescriptor component = extracted.get(i);
            byId.put(Integer.valueOf(component.getComponentId()), component);
        }

        // One shared overlap structure for the whole image (not per component).
        final BitSet claimedPixels = new BitSet(imagePixels);
        long retainedLocalMaskCells = 0L;

        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            final LumenComponentDescriptor component =
                    byId.get(Integer.valueOf(item.getComponentId()));
            if (component == null || component.getArea() != item.getSourceArea()
                    || !component.getBounds().equals(item.getSourceBounds())) {
                throw new IllegalStateException("Component ID no longer matches source geometry: "
                        + item.getComponentId());
            }

            final List<Point> originalPixels = component.getPixels();
            final Set<Long> originalKeys = new HashSet<Long>(originalPixels.size() * 2);
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int p = 0; p < originalPixels.size(); p++) {
                final Point point = originalPixels.get(p);
                requireInBounds(point, width, height, item.getComponentId());
                originalKeys.add(pack(point.x, point.y));
                if (point.x < minX) {
                    minX = point.x;
                }
                if (point.y < minY) {
                    minY = point.y;
                }
                if (point.x > maxX) {
                    maxX = point.x;
                }
                if (point.y > maxY) {
                    maxY = point.y;
                }
            }

            int changed = 0;
            final List<Point> acceptedCandidates;
            if (item.isReconstructed()) {
                acceptedCandidates = item.getCandidatePixels();
                for (int p = 0; p < acceptedCandidates.size(); p++) {
                    final Point pt = acceptedCandidates.get(p);
                    requireInBounds(pt, width, height, item.getComponentId());
                    if (originalKeys.contains(pack(pt.x, pt.y))) {
                        throw new IllegalStateException(
                                "Reconstruction candidate overlaps original component "
                                        + item.getComponentId() + " at (" + pt.x + "," + pt.y + ")");
                    }
                    if ((maskedClassified.get(pt.x, pt.y) & 0xff)
                            != ClassifierLabels.WOOD_RESULT_VALUE) {
                        throw new IllegalStateException(
                                "Reconstruction candidate is not Wood for component "
                                        + item.getComponentId() + " at (" + pt.x + "," + pt.y + ")");
                    }
                    if (pt.x < minX) {
                        minX = pt.x;
                    }
                    if (pt.y < minY) {
                        minY = pt.y;
                    }
                    if (pt.x > maxX) {
                        maxX = pt.x;
                    }
                    if (pt.y > maxY) {
                        maxY = pt.y;
                    }
                    changed++;
                }
            } else {
                acceptedCandidates = null;
            }

            if (minX == Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Empty final geometry for component " + item.getComponentId());
            }

            final int maskOriginX = minX - 1;
            final int maskOriginY = minY - 1;
            final long maskWidthLong = (long) maxX - (long) minX + 1L + 2L;
            final long maskHeightLong = (long) maxY - (long) minY + 1L + 2L;
            if (maskWidthLong <= 0L || maskHeightLong <= 0L
                    || maskWidthLong > Integer.MAX_VALUE
                    || maskHeightLong > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Impossible local mask dimensions for component " + item.getComponentId()
                                + ": " + maskWidthLong + "x" + maskHeightLong);
            }
            final int maskWidth = (int) maskWidthLong;
            final int maskHeight = (int) maskHeightLong;
            final long maskCellsLong = (long) maskWidth * (long) maskHeight;
            if (maskCellsLong <= 0L || maskCellsLong > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Impossible local mask cell count for component " + item.getComponentId()
                                + ": " + maskCellsLong);
            }
            final boolean[] mask = new boolean[(int) maskCellsLong];

            for (int p = 0; p < originalPixels.size(); p++) {
                final Point point = originalPixels.get(p);
                setLocalMask(mask, maskWidth, maskHeight, maskOriginX, maskOriginY, point.x, point.y);
            }
            if (acceptedCandidates != null) {
                for (int p = 0; p < acceptedCandidates.size(); p++) {
                    final Point pt = acceptedCandidates.get(p);
                    setLocalMask(mask, maskWidth, maskHeight, maskOriginX, maskOriginY, pt.x, pt.y);
                }
            }

            final int finalArea = originalPixels.size() + changed;
            if (countTrue(mask) != finalArea) {
                throw new IllegalStateException(
                        "Final geometry area inconsistent for component " + item.getComponentId());
            }

            boolean touches = false;
            for (int p = 0; p < originalPixels.size(); p++) {
                claimPixel(claimedPixels, width, originalPixels.get(p), item.getComponentId());
                touches = touches || touchesImageBorder(originalPixels.get(p), width, height);
            }
            if (acceptedCandidates != null) {
                for (int p = 0; p < acceptedCandidates.size(); p++) {
                    claimPixel(claimedPixels, width, acceptedCandidates.get(p), item.getComponentId());
                    touches = touches || touchesImageBorder(acceptedCandidates.get(p), width, height);
                }
            }

            final double[] centroid = centroidOfFinalMask(
                    mask, maskWidth, maskHeight, maskOriginX, maskOriginY,
                    finalArea, item.getComponentId());

            retainedLocalMaskCells += maskCellsLong;
            geometries.add(new ComponentGeometry(
                    item.getComponentId(),
                    mask,
                    maskWidth,
                    maskHeight,
                    maskOriginX,
                    maskOriginY,
                    width,
                    height,
                    finalArea,
                    centroid[0],
                    centroid[1],
                    new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1),
                    item.isReconstructed(),
                    changed,
                    touches || item.isTouchesImageBorder(),
                    item));
        }
        if (geometries.size() != extracted.size()) {
            throw new IllegalStateException("Final component count is not stable");
        }

        final long fullImageEquivalent = (long) geometries.size() * imagePixelsLong;
        final double reduction = fullImageEquivalent <= 0L
                ? 0.0
                : 1.0 - ((double) retainedLocalMaskCells / (double) fullImageEquivalent);
        System.out.println(String.format(Locale.US,
                "FINAL_GEOMETRY_MEMORY: components=%d image_pixels=%d "
                        + "full_image_equivalent_cells=%d retained_local_mask_cells=%d "
                        + "reduction_fraction=%.6f",
                Integer.valueOf(geometries.size()),
                Integer.valueOf(imagePixels),
                Long.valueOf(fullImageEquivalent),
                Long.valueOf(retainedLocalMaskCells),
                Double.valueOf(reduction)));

        return geometries;
    }

    /** Sum of retained local-mask cells across all components (test-visible). */
    public static long totalRetainedMaskCells(final List<ComponentGeometry> geometry) {
        if (geometry == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < geometry.size(); i++) {
            total += geometry.get(i).retainedMaskCells();
        }
        return total;
    }

    public static ByteProcessor buildReconstructedProcessor(
            final ByteProcessor source,
            final AllLumenBatchResult batchResult) {
        final ByteProcessor copy = (ByteProcessor) source.duplicate();
        if (source.getWidth() != batchResult.getImageWidth()
                || source.getHeight() != batchResult.getImageHeight()) {
            throw new IllegalStateException("Batch result dimensions do not match source image");
        }
        final List<LumenBatchItemResult> items = batchResult.getItemResults();
        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            if (!item.isReconstructed()) {
                continue;
            }
            final List<Point> candidates = item.getCandidatePixels();
            for (int p = 0; p < candidates.size(); p++) {
                final Point pt = candidates.get(p);
                requireInBounds(pt, source.getWidth(), source.getHeight(), item.getComponentId());
                if ((source.get(pt.x, pt.y) & 0xff) != ClassifierLabels.WOOD_RESULT_VALUE) {
                    throw new IllegalStateException("Candidate is not Wood at (" + pt.x + "," + pt.y + ")");
                }
                copy.set(pt.x, pt.y, ClassifierLabels.LUMEN_RESULT_VALUE);
            }
        }
        return copy;
    }

    /**
     * Area centroid of final lumen pixel centers from the local mask.
     *
     * <p>For each set mask pixel at global integer indices {@code (x, y)},
     * the pixel center is {@code (x + 0.5, y + 0.5)}. Returns
     * {@code [centroidX, centroidY]} in global crop-local coordinates.</p>
     */
    private static double[] centroidOfFinalMask(
            final boolean[] mask,
            final int maskWidth,
            final int maskHeight,
            final int maskOriginX,
            final int maskOriginY,
            final int expectedAreaPx,
            final int componentId) {
        long sumX = 0L;
        long sumY = 0L;
        int count = 0;
        final int cells = maskWidth * maskHeight;
        for (int bitIndex = 0; bitIndex < cells; bitIndex++) {
            if (!mask[bitIndex]) {
                continue;
            }
            final int localY = bitIndex / maskWidth;
            final int localX = bitIndex % maskWidth;
            final int globalX = maskOriginX + localX;
            final int globalY = maskOriginY + localY;
            sumX += globalX;
            sumY += globalY;
            count++;
        }
        if (count == 0) {
            throw new IllegalStateException(
                    "Final lumen mask has zero pixels for component " + componentId);
        }
        if (count != expectedAreaPx) {
            throw new IllegalStateException(
                    "Final-mask pixel count does not match areaPx for component "
                            + componentId + ": maskPixels=" + count
                            + " areaPx=" + expectedAreaPx);
        }
        return new double[] {
                ((double) sumX / (double) count) + 0.5,
                ((double) sumY / (double) count) + 0.5
        };
    }

    private static void setLocalMask(
            final boolean[] mask,
            final int maskWidth,
            final int maskHeight,
            final int maskOriginX,
            final int maskOriginY,
            final int globalX,
            final int globalY) {
        final int localX = globalX - maskOriginX;
        final int localY = globalY - maskOriginY;
        if (localX < 0 || localY < 0 || localX >= maskWidth || localY >= maskHeight) {
            throw new IllegalStateException(
                    "Global pixel (" + globalX + "," + globalY
                            + ") falls outside local mask frame origin=("
                            + maskOriginX + "," + maskOriginY + ") size="
                            + maskWidth + "x" + maskHeight);
        }
        mask[localY * maskWidth + localX] = true;
    }

    private static void claimPixel(
            final BitSet claimedPixels,
            final int imageWidth,
            final Point point,
            final int componentId) {
        final long indexLong = (long) point.y * (long) imageWidth + (long) point.x;
        if (indexLong < 0L || indexLong > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Pixel index overflow at (" + point.x + "," + point.y + ")");
        }
        final int index = (int) indexLong;
        if (claimedPixels.get(index)) {
            throw new IllegalStateException(
                    "Final lumen masks overlap at (" + point.x + "," + point.y + ")");
        }
        claimedPixels.set(index);
    }

    private static boolean touchesImageBorder(
            final Point point, final int width, final int height) {
        return point.x == 0 || point.y == 0 || point.x == width - 1 || point.y == height - 1;
    }

    private static long pack(final int x, final int y) {
        return (((long) y) << 32) ^ (x & 0xffffffffL);
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

    private static void requireInBounds(
            final Point point,
            final int width,
            final int height,
            final int componentId) {
        if (point == null || point.x < 0 || point.y < 0
                || point.x >= width || point.y >= height) {
            throw new IllegalStateException("Candidate outside image for component " + componentId);
        }
    }
}
