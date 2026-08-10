package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;

import java.util.Locale;

/**
 * Assembles label images under an approved downsampled sample mask.
 *
 * <p>Output is initialized to Background class 3. Completely empty core tiles are
 * skipped. Classified tile labels are copied only where the mask includes the
 * corresponding original-source coordinate.</p>
 *
 * <p>When a non-zero crop offset is supplied, tile coordinates are crop-local and
 * the output dimensions equal the crop. Mask queries always use original-source
 * coordinates: {@code original = cropOffset + cropLocal}.</p>
 */
public final class MaskedLabelAssembler {

    public interface TileClassifier {
        ImagePlus classifyTile(CoreTileGeometry tile);
    }

    public static final class AssemblyResult {
        private final ImagePlus labels;
        private final int classifiedTiles;
        private final int skippedTiles;
        private final long outsideMaskForcedBackgroundPixels;

        public AssemblyResult(
                final ImagePlus labels,
                final int classifiedTiles,
                final int skippedTiles,
                final long outsideMaskForcedBackgroundPixels) {
            this.labels = labels;
            this.classifiedTiles = classifiedTiles;
            this.skippedTiles = skippedTiles;
            this.outsideMaskForcedBackgroundPixels = outsideMaskForcedBackgroundPixels;
        }

        public ImagePlus getLabels() {
            return labels;
        }

        public int getClassifiedTiles() {
            return classifiedTiles;
        }

        public int getSkippedTiles() {
            return skippedTiles;
        }

        public long getOutsideMaskForcedBackgroundPixels() {
            return outsideMaskForcedBackgroundPixels;
        }
    }

    public AssemblyResult assemble(
            final CoreTileLayout layout,
            final DownsampledSampleMask mask,
            final TileClassifier tileClassifier) {
        return assemble(layout, mask, tileClassifier, 0, 0);
    }

    public AssemblyResult assemble(
            final CoreTileLayout layout,
            final DownsampledSampleMask mask,
            final TileClassifier tileClassifier,
            final int cropOffsetOriginalX,
            final int cropOffsetOriginalY) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (tileClassifier == null) {
            throw new IllegalArgumentException("tileClassifier must not be null");
        }
        if (cropOffsetOriginalX < 0 || cropOffsetOriginalY < 0) {
            throw new IllegalArgumentException("crop offsets must be >= 0");
        }
        if (cropOffsetOriginalX + layout.getImageWidth() > mask.getOriginalWidth()
                || cropOffsetOriginalY + layout.getImageHeight() > mask.getOriginalHeight()) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Crop-local layout %dx%d at offset (%d,%d) exceeds mask original %dx%d",
                    Integer.valueOf(layout.getImageWidth()),
                    Integer.valueOf(layout.getImageHeight()),
                    Integer.valueOf(cropOffsetOriginalX),
                    Integer.valueOf(cropOffsetOriginalY),
                    Integer.valueOf(mask.getOriginalWidth()),
                    Integer.valueOf(mask.getOriginalHeight())));
        }

        final int width = layout.getImageWidth();
        final int height = layout.getImageHeight();
        final ByteProcessor output = new ByteProcessor(width, height);
        final byte[] outPixels = (byte[]) output.getPixels();
        for (int i = 0; i < outPixels.length; i++) {
            outPixels[i] = (byte) TiledWekaClassificationDefaults.OUTSIDE_MASK_BACKGROUND_CLASS;
        }

        int classified = 0;
        int skipped = 0;
        long forcedBackground = 0L;

        for (final CoreTileGeometry tile : layout.getTiles()) {
            final int originalCoreX = cropOffsetOriginalX + tile.getCoreX();
            final int originalCoreY = cropOffsetOriginalY + tile.getCoreY();
            final boolean intersects = mask.coreTileContainsIncludedPixels(
                    originalCoreX,
                    originalCoreY,
                    tile.getCoreWidth(),
                    tile.getCoreHeight());
            if (!intersects) {
                skipped++;
                forcedBackground += ((long) tile.getCoreWidth()) * ((long) tile.getCoreHeight());
                IJ.log(String.format(Locale.US,
                        "Tile %d/%d [%d,%d] cropLocalCore=(%d,%d,%d,%d) "
                                + "originalCore=(%d,%d,%d,%d): skipped, no sample-mask pixels",
                        Integer.valueOf(tile.getTileIndex()),
                        Long.valueOf(layout.getTotalTiles()),
                        Integer.valueOf(tile.getTileXIndex()),
                        Integer.valueOf(tile.getTileYIndex()),
                        Integer.valueOf(tile.getCoreX()),
                        Integer.valueOf(tile.getCoreY()),
                        Integer.valueOf(tile.getCoreWidth()),
                        Integer.valueOf(tile.getCoreHeight()),
                        Integer.valueOf(originalCoreX),
                        Integer.valueOf(originalCoreY),
                        Integer.valueOf(tile.getCoreWidth()),
                        Integer.valueOf(tile.getCoreHeight())));
                continue;
            }

            final ImagePlus tileResult = tileClassifier.classifyTile(tile);
            if (tileResult == null) {
                throw new IllegalStateException(
                        "Tile classifier returned null for tile " + tile.getTileIndex());
            }
            try {
                if (tileResult.getWidth() != tile.getCoreWidth()
                        || tileResult.getHeight() != tile.getCoreHeight()) {
                    throw new IllegalStateException(
                            "Tile result dimensions mismatch for tile "
                                    + tile.getTileIndex());
                }
                classified++;
                for (int y = 0; y < tile.getCoreHeight(); y++) {
                    final int cropLocalY = tile.getCoreY() + y;
                    final int originalY = cropOffsetOriginalY + cropLocalY;
                    for (int x = 0; x < tile.getCoreWidth(); x++) {
                        final int cropLocalX = tile.getCoreX() + x;
                        final int originalX = cropOffsetOriginalX + cropLocalX;
                        final int outIndex = cropLocalY * width + cropLocalX;
                        if (mask.containsOriginalPixel(originalX, originalY)) {
                            final int label = (int) Math.round(tileResult.getProcessor().getf(x, y));
                            if (label < 0 || label > 3) {
                                throw new IllegalStateException(
                                        "Invalid Weka label " + label
                                                + " at tile " + tile.getTileIndex());
                            }
                            outPixels[outIndex] = (byte) label;
                        } else {
                            outPixels[outIndex] =
                                    (byte) TiledWekaClassificationDefaults.OUTSIDE_MASK_BACKGROUND_CLASS;
                            forcedBackground++;
                        }
                    }
                }
            } finally {
                try {
                    tileResult.close();
                } catch (final Exception ignored) {
                    // ignore
                }
            }
        }

        return new AssemblyResult(
                new ImagePlus("Tiled Weka Assembly", output),
                classified,
                skipped,
                forcedBackground);
    }

    /**
     * Forces every pixel outside the approved mask to Background class 3.
     *
     * @return number of pixels forced to background
     */
    public static long applyMaskToLabels(
            final ImagePlus labels,
            final DownsampledSampleMask mask) {
        return applyMaskToLabels(labels, mask, 0, 0);
    }

    public static long applyMaskToLabels(
            final ImagePlus labels,
            final DownsampledSampleMask mask,
            final int cropOffsetOriginalX,
            final int cropOffsetOriginalY) {
        if (labels == null) {
            throw new IllegalArgumentException("labels must not be null");
        }
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (cropOffsetOriginalX < 0 || cropOffsetOriginalY < 0) {
            throw new IllegalArgumentException("crop offsets must be >= 0");
        }
        if (cropOffsetOriginalX + labels.getWidth() > mask.getOriginalWidth()
                || cropOffsetOriginalY + labels.getHeight() > mask.getOriginalHeight()) {
            throw new IllegalArgumentException(
                    "label image + crop offset exceeds mask original dimensions");
        }
        final ByteProcessor processor;
        if (labels.getProcessor() instanceof ByteProcessor) {
            processor = (ByteProcessor) labels.getProcessor();
        } else {
            throw new IllegalArgumentException("labels must be an 8-bit ByteProcessor");
        }
        final byte[] pixels = (byte[]) processor.getPixels();
        final int width = labels.getWidth();
        final int height = labels.getHeight();
        long forced = 0L;
        for (int y = 0; y < height; y++) {
            final int originalY = cropOffsetOriginalY + y;
            for (int x = 0; x < width; x++) {
                final int originalX = cropOffsetOriginalX + x;
                if (!mask.containsOriginalPixel(originalX, originalY)) {
                    final int index = y * width + x;
                    if ((pixels[index] & 0xff)
                            != TiledWekaClassificationDefaults.OUTSIDE_MASK_BACKGROUND_CLASS) {
                        forced++;
                    }
                    pixels[index] =
                            (byte) TiledWekaClassificationDefaults.OUTSIDE_MASK_BACKGROUND_CLASS;
                }
            }
        }
        return forced;
    }
}
