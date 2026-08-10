package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable fixed-pixel core-tile layout for an image.
 *
 * <p>Core tiles have constant configured size except for the final column and
 * final row, which may be smaller. Classification halo expands each crop for
 * logging/diagnostics; the official TWS ROI classifier applies the same clipped
 * halo internally and returns only core pixels for stitching.</p>
 */
public final class CoreTileLayout {

    private final int imageWidth;
    private final int imageHeight;
    private final int coreTileWidth;
    private final int coreTileHeight;
    private final int classificationHaloPixels;
    private final int tilesX;
    private final int tilesY;
    private final long totalTiles;
    private final List<CoreTileGeometry> tiles;
    private final int[] verticalSeams;
    private final int[] horizontalSeams;

    private CoreTileLayout(
            final int imageWidth,
            final int imageHeight,
            final int coreTileWidth,
            final int coreTileHeight,
            final int classificationHaloPixels,
            final int tilesX,
            final int tilesY,
            final long totalTiles,
            final List<CoreTileGeometry> tiles,
            final int[] verticalSeams,
            final int[] horizontalSeams) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.coreTileWidth = coreTileWidth;
        this.coreTileHeight = coreTileHeight;
        this.classificationHaloPixels = classificationHaloPixels;
        this.tilesX = tilesX;
        this.tilesY = tilesY;
        this.totalTiles = totalTiles;
        this.tiles = Collections.unmodifiableList(new ArrayList<CoreTileGeometry>(tiles));
        this.verticalSeams = copyInts(verticalSeams);
        this.horizontalSeams = copyInts(horizontalSeams);
    }

    public static CoreTileLayout create(
            final int imageWidth,
            final int imageHeight,
            final int coreTileWidth,
            final int coreTileHeight,
            final int classificationHaloPixels) {
        if (imageWidth <= 0) {
            throw new IllegalArgumentException("imageWidth must be > 0: " + imageWidth);
        }
        if (imageHeight <= 0) {
            throw new IllegalArgumentException("imageHeight must be > 0: " + imageHeight);
        }
        if (coreTileWidth <= 0 || coreTileHeight <= 0) {
            throw new IllegalArgumentException(
                    "Core tile width and height must both be greater than zero.");
        }
        if (classificationHaloPixels < 0) {
            throw new IllegalArgumentException(
                    "classificationHaloPixels must be >= 0: " + classificationHaloPixels);
        }

        final int tilesX = ceilDiv(imageWidth, coreTileWidth);
        final int tilesY = ceilDiv(imageHeight, coreTileHeight);
        final long totalTiles = ((long) tilesX) * ((long) tilesY);
        if (totalTiles > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "total tile count overflows int: tilesX=" + tilesX
                            + ", tilesY=" + tilesY);
        }

        final List<CoreTileGeometry> tiles =
                new ArrayList<CoreTileGeometry>((int) totalTiles);
        int tileIndex = 1;
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                final int coreX = checkedMultiply(tx, coreTileWidth);
                final int coreY = checkedMultiply(ty, coreTileHeight);
                final int coreWidth = Math.min(coreTileWidth, imageWidth - coreX);
                final int coreHeight = Math.min(coreTileHeight, imageHeight - coreY);
                if (coreWidth <= 0 || coreHeight <= 0) {
                    throw new IllegalStateException(
                            "non-positive core size at tile [" + tx + "," + ty + "]");
                }

                final int cropX0 = Math.max(0, coreX - classificationHaloPixels);
                final int cropY0 = Math.max(0, coreY - classificationHaloPixels);
                final int cropX1 = Math.min(
                        imageWidth, coreX + coreWidth + classificationHaloPixels);
                final int cropY1 = Math.min(
                        imageHeight, coreY + coreHeight + classificationHaloPixels);
                final int cropWidth = cropX1 - cropX0;
                final int cropHeight = cropY1 - cropY0;
                final int sourceCoreX = coreX - cropX0;
                final int sourceCoreY = coreY - cropY0;

                tiles.add(new CoreTileGeometry(
                        tileIndex,
                        tx,
                        ty,
                        coreX,
                        coreY,
                        coreWidth,
                        coreHeight,
                        cropX0,
                        cropY0,
                        cropWidth,
                        cropHeight,
                        sourceCoreX,
                        sourceCoreY));
                tileIndex++;
            }
        }

        return new CoreTileLayout(
                imageWidth,
                imageHeight,
                coreTileWidth,
                coreTileHeight,
                classificationHaloPixels,
                tilesX,
                tilesY,
                totalTiles,
                tiles,
                computeInteriorSeams(coreTileWidth, imageWidth),
                computeInteriorSeams(coreTileHeight, imageHeight));
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getCoreTileWidth() {
        return coreTileWidth;
    }

    public int getCoreTileHeight() {
        return coreTileHeight;
    }

    public int getClassificationHaloPixels() {
        return classificationHaloPixels;
    }

    public int getTilesX() {
        return tilesX;
    }

    public int getTilesY() {
        return tilesY;
    }

    public long getTotalTiles() {
        return totalTiles;
    }

    public int getMaximumExpandedCropWidth() {
        return coreTileWidth + 2 * classificationHaloPixels;
    }

    public int getMaximumExpandedCropHeight() {
        return coreTileHeight + 2 * classificationHaloPixels;
    }

    public List<CoreTileGeometry> getTiles() {
        return tiles;
    }

    public int[] getVerticalSeams() {
        return copyInts(verticalSeams);
    }

    public int[] getHorizontalSeams() {
        return copyInts(horizontalSeams);
    }

    /**
     * Rectangle-based verification that every image pixel belongs to exactly one
     * core tile and that no core rectangle extends outside the image.
     */
    public void verifyExactCoverage() {
        long covered = 0L;
        for (int i = 0; i < tiles.size(); i++) {
            final CoreTileGeometry tile = tiles.get(i);
            if (tile.getCoreX() < 0
                    || tile.getCoreY() < 0
                    || tile.getCoreX() + tile.getCoreWidth() > imageWidth
                    || tile.getCoreY() + tile.getCoreHeight() > imageHeight) {
                throw new IllegalStateException(
                        "core rectangle outside image: " + tile);
            }
            covered += ((long) tile.getCoreWidth()) * ((long) tile.getCoreHeight());
            for (int j = i + 1; j < tiles.size(); j++) {
                if (rectsOverlap(tile, tiles.get(j))) {
                    throw new IllegalStateException(
                            "overlapping core rectangles: " + tile + " and " + tiles.get(j));
                }
            }
        }
        final long expected = ((long) imageWidth) * ((long) imageHeight);
        if (covered != expected) {
            throw new IllegalStateException(
                    "core tiles cover " + covered + " pixels, expected " + expected);
        }
    }

    static int ceilDiv(final int numerator, final int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be > 0");
        }
        if (numerator <= 0) {
            throw new IllegalArgumentException("numerator must be > 0");
        }
        return (int) ((((long) numerator) + ((long) denominator) - 1L) / denominator);
    }

    static int[] computeInteriorSeams(final int coreTileSize, final int imageSize) {
        if (coreTileSize <= 0 || imageSize <= 0) {
            return new int[0];
        }
        final List<Integer> seams = new ArrayList<Integer>();
        long seam = coreTileSize;
        while (seam > 0L && seam < imageSize) {
            seams.add(Integer.valueOf((int) seam));
            seam += coreTileSize;
            if (seam > Integer.MAX_VALUE) {
                break;
            }
        }
        final int[] result = new int[seams.size()];
        for (int i = 0; i < seams.size(); i++) {
            result[i] = seams.get(i).intValue();
        }
        return result;
    }

    private static int checkedMultiply(final int a, final int b) {
        final long product = ((long) a) * ((long) b);
        if (product > Integer.MAX_VALUE || product < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "integer overflow multiplying " + a + " * " + b);
        }
        return (int) product;
    }

    private static boolean rectsOverlap(
            final CoreTileGeometry a,
            final CoreTileGeometry b) {
        final int aRight = a.getCoreX() + a.getCoreWidth();
        final int aBottom = a.getCoreY() + a.getCoreHeight();
        final int bRight = b.getCoreX() + b.getCoreWidth();
        final int bBottom = b.getCoreY() + b.getCoreHeight();
        return a.getCoreX() < bRight
                && b.getCoreX() < aRight
                && a.getCoreY() < bBottom
                && b.getCoreY() < aBottom;
    }

    private static int[] copyInts(final int[] source) {
        if (source == null || source.length == 0) {
            return new int[0];
        }
        final int[] copy = new int[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CoreTileLayout)) {
            return false;
        }
        final CoreTileLayout other = (CoreTileLayout) obj;
        return imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && coreTileWidth == other.coreTileWidth
                && coreTileHeight == other.coreTileHeight
                && classificationHaloPixels == other.classificationHaloPixels
                && tilesX == other.tilesX
                && tilesY == other.tilesY
                && totalTiles == other.totalTiles
                && tiles.equals(other.tiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(coreTileWidth),
                Integer.valueOf(coreTileHeight),
                Integer.valueOf(classificationHaloPixels),
                Integer.valueOf(tilesX),
                Integer.valueOf(tilesY),
                Long.valueOf(totalTiles),
                tiles);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "CoreTileLayout{%dx%d,core=%dx%d,grid=%dx%d,halo=%d,total=%d}",
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(coreTileWidth),
                Integer.valueOf(coreTileHeight),
                Integer.valueOf(tilesX),
                Integer.valueOf(tilesY),
                Integer.valueOf(classificationHaloPixels),
                Long.valueOf(totalTiles));
    }
}
