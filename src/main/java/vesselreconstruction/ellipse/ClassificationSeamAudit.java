package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable tiled-versus-untiled classification seam audit result.
 */
public final class ClassificationSeamAudit {

    private final int width;
    private final int height;
    private final int calculatedTilesX;
    private final int calculatedTilesY;
    private final int coreTileWidthPixels;
    private final int coreTileHeightPixels;
    private final int seamAuditHalfWidthPixels;
    private final long totalPixelCount;
    private final long differingPixelCount;
    private final double differingPixelFraction;
    private final long seamBandPixelCount;
    private final long seamBandDifferingPixelCount;
    private final double seamBandDifferingFraction;
    private final long nonSeamPixelCount;
    private final long nonSeamDifferingPixelCount;
    private final double nonSeamDifferingFraction;
    private final boolean exactMatch;
    private final long[] tiledClassCounts;
    private final long[] referenceClassCounts;
    private final long[][] confusionMatrix;
    private final ImagePlus differenceMask;
    private final ImagePlus seamAuditMask;

    public ClassificationSeamAudit(
            final int width,
            final int height,
            final int calculatedTilesX,
            final int calculatedTilesY,
            final int coreTileWidthPixels,
            final int coreTileHeightPixels,
            final int seamAuditHalfWidthPixels,
            final long totalPixelCount,
            final long differingPixelCount,
            final double differingPixelFraction,
            final long seamBandPixelCount,
            final long seamBandDifferingPixelCount,
            final double seamBandDifferingFraction,
            final long nonSeamPixelCount,
            final long nonSeamDifferingPixelCount,
            final double nonSeamDifferingFraction,
            final boolean exactMatch,
            final long[] tiledClassCounts,
            final long[] referenceClassCounts,
            final long[][] confusionMatrix,
            final ImagePlus differenceMask,
            final ImagePlus seamAuditMask) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        if (tiledClassCounts == null || tiledClassCounts.length != 4) {
            throw new IllegalArgumentException("tiledClassCounts must have length 4");
        }
        if (referenceClassCounts == null || referenceClassCounts.length != 4) {
            throw new IllegalArgumentException("referenceClassCounts must have length 4");
        }
        if (confusionMatrix == null || confusionMatrix.length != 4) {
            throw new IllegalArgumentException("confusionMatrix must be 4x4");
        }
        for (int i = 0; i < 4; i++) {
            if (confusionMatrix[i] == null || confusionMatrix[i].length != 4) {
                throw new IllegalArgumentException("confusionMatrix must be 4x4");
            }
        }
        if (differenceMask == null || seamAuditMask == null) {
            throw new IllegalArgumentException("differenceMask and seamAuditMask must not be null");
        }

        this.width = width;
        this.height = height;
        this.calculatedTilesX = calculatedTilesX;
        this.calculatedTilesY = calculatedTilesY;
        this.coreTileWidthPixels = coreTileWidthPixels;
        this.coreTileHeightPixels = coreTileHeightPixels;
        this.seamAuditHalfWidthPixels = seamAuditHalfWidthPixels;
        this.totalPixelCount = totalPixelCount;
        this.differingPixelCount = differingPixelCount;
        this.differingPixelFraction = differingPixelFraction;
        this.seamBandPixelCount = seamBandPixelCount;
        this.seamBandDifferingPixelCount = seamBandDifferingPixelCount;
        this.seamBandDifferingFraction = seamBandDifferingFraction;
        this.nonSeamPixelCount = nonSeamPixelCount;
        this.nonSeamDifferingPixelCount = nonSeamDifferingPixelCount;
        this.nonSeamDifferingFraction = nonSeamDifferingFraction;
        this.exactMatch = exactMatch;
        this.tiledClassCounts = Arrays.copyOf(tiledClassCounts, 4);
        this.referenceClassCounts = Arrays.copyOf(referenceClassCounts, 4);
        this.confusionMatrix = copyMatrix(confusionMatrix);
        this.differenceMask = duplicateImage(differenceMask);
        this.seamAuditMask = duplicateImage(seamAuditMask);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCalculatedTilesX() {
        return calculatedTilesX;
    }

    public int getCalculatedTilesY() {
        return calculatedTilesY;
    }

    public int getCoreTileWidthPixels() {
        return coreTileWidthPixels;
    }

    public int getCoreTileHeightPixels() {
        return coreTileHeightPixels;
    }

    public int getSeamAuditHalfWidthPixels() {
        return seamAuditHalfWidthPixels;
    }

    public long getTotalPixelCount() {
        return totalPixelCount;
    }

    public long getDifferingPixelCount() {
        return differingPixelCount;
    }

    public double getDifferingPixelFraction() {
        return differingPixelFraction;
    }

    public long getSeamBandPixelCount() {
        return seamBandPixelCount;
    }

    public long getSeamBandDifferingPixelCount() {
        return seamBandDifferingPixelCount;
    }

    public double getSeamBandDifferingFraction() {
        return seamBandDifferingFraction;
    }

    public long getNonSeamPixelCount() {
        return nonSeamPixelCount;
    }

    public long getNonSeamDifferingPixelCount() {
        return nonSeamDifferingPixelCount;
    }

    public double getNonSeamDifferingFraction() {
        return nonSeamDifferingFraction;
    }

    public boolean isExactMatch() {
        return exactMatch;
    }

    public long[] getTiledClassCounts() {
        return Arrays.copyOf(tiledClassCounts, 4);
    }

    public long[] getReferenceClassCounts() {
        return Arrays.copyOf(referenceClassCounts, 4);
    }

    public long[][] getConfusionMatrix() {
        return copyMatrix(confusionMatrix);
    }

    /**
     * Returns an independent duplicate of the difference mask.
     * Encoding: 0=same, 255=different.
     */
    public ImagePlus getDifferenceMask() {
        return duplicateImage(differenceMask);
    }

    /**
     * Returns an independent duplicate of the seam-audit mask.
     * Encoding: 0=same outside seam, 64=same inside seam,
     * 192=different outside seam, 255=different inside seam.
     */
    public ImagePlus getSeamAuditMask() {
        return duplicateImage(seamAuditMask);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClassificationSeamAudit)) {
            return false;
        }
        final ClassificationSeamAudit other = (ClassificationSeamAudit) obj;
        return width == other.width
                && height == other.height
                && calculatedTilesX == other.calculatedTilesX
                && calculatedTilesY == other.calculatedTilesY
                && coreTileWidthPixels == other.coreTileWidthPixels
                && coreTileHeightPixels == other.coreTileHeightPixels
                && seamAuditHalfWidthPixels == other.seamAuditHalfWidthPixels
                && totalPixelCount == other.totalPixelCount
                && differingPixelCount == other.differingPixelCount
                && Double.compare(differingPixelFraction, other.differingPixelFraction) == 0
                && seamBandPixelCount == other.seamBandPixelCount
                && seamBandDifferingPixelCount == other.seamBandDifferingPixelCount
                && Double.compare(seamBandDifferingFraction, other.seamBandDifferingFraction) == 0
                && nonSeamPixelCount == other.nonSeamPixelCount
                && nonSeamDifferingPixelCount == other.nonSeamDifferingPixelCount
                && Double.compare(nonSeamDifferingFraction, other.nonSeamDifferingFraction) == 0
                && exactMatch == other.exactMatch
                && Arrays.equals(tiledClassCounts, other.tiledClassCounts)
                && Arrays.equals(referenceClassCounts, other.referenceClassCounts)
                && Arrays.deepEquals(confusionMatrix, other.confusionMatrix)
                && sameBytes(differenceMask, other.differenceMask)
                && sameBytes(seamAuditMask, other.seamAuditMask);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                Integer.valueOf(width),
                Integer.valueOf(height),
                Integer.valueOf(calculatedTilesX),
                Integer.valueOf(calculatedTilesY),
                Integer.valueOf(coreTileWidthPixels),
                Integer.valueOf(coreTileHeightPixels),
                Integer.valueOf(seamAuditHalfWidthPixels),
                Long.valueOf(totalPixelCount),
                Long.valueOf(differingPixelCount),
                Double.valueOf(differingPixelFraction),
                Long.valueOf(seamBandPixelCount),
                Long.valueOf(seamBandDifferingPixelCount),
                Double.valueOf(seamBandDifferingFraction),
                Long.valueOf(nonSeamPixelCount),
                Long.valueOf(nonSeamDifferingPixelCount),
                Double.valueOf(nonSeamDifferingFraction),
                Boolean.valueOf(exactMatch));
        result = 31 * result + Arrays.hashCode(tiledClassCounts);
        result = 31 * result + Arrays.hashCode(referenceClassCounts);
        result = 31 * result + Arrays.deepHashCode(confusionMatrix);
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "ClassificationSeamAudit{%dx%d,core=%dx%d,grid=%dx%d,diff=%d/%.6f,exact=%s}",
                Integer.valueOf(width),
                Integer.valueOf(height),
                Integer.valueOf(coreTileWidthPixels),
                Integer.valueOf(coreTileHeightPixels),
                Integer.valueOf(calculatedTilesX),
                Integer.valueOf(calculatedTilesY),
                Long.valueOf(differingPixelCount),
                Double.valueOf(differingPixelFraction),
                Boolean.valueOf(exactMatch));
    }

    private static long[][] copyMatrix(final long[][] source) {
        final long[][] copy = new long[4][4];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, 4);
        }
        return copy;
    }

    private static ImagePlus duplicateImage(final ImagePlus source) {
        final ByteProcessor processor = (ByteProcessor) source.getProcessor().duplicate();
        final ImagePlus copy = new ImagePlus(source.getTitle(), processor);
        if (source.getCalibration() != null) {
            copy.setCalibration(source.getCalibration().copy());
        }
        return copy;
    }

    private static boolean sameBytes(final ImagePlus a, final ImagePlus b) {
        final byte[] pa = (byte[]) a.getProcessor().getPixels();
        final byte[] pb = (byte[]) b.getProcessor().getPixels();
        return Arrays.equals(pa, pb);
    }
}
