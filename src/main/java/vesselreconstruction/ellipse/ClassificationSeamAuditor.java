package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Compares tiled and untiled direct Weka label images and reports
 * seam-band versus non-seam differences.
 *
 * <p>Interior seams are derived from fixed core-tile boundaries:</p>
 * <pre>
 * vertical seams at coreTileWidth, 2*coreTileWidth, ... (while &lt; imageWidth)
 * horizontal seams at coreTileHeight, 2*coreTileHeight, ... (while &lt; imageHeight)
 * </pre>
 *
 * <p>A pixel is inside a vertical seam band when its X coordinate lies within
 * {@code seamAuditHalfWidthPixels} on either side of an internal vertical
 * seam coordinate {@code s}, i.e. {@code |x - s| <= halfWidth}. The same rule
 * applies to Y against horizontal seams. A pixel is counted once in the seam
 * band if it belongs to either a vertical or horizontal seam band.</p>
 */
public final class ClassificationSeamAuditor {

    public ClassificationSeamAudit compare(
            final ImagePlus tiledDirectLabels,
            final ImagePlus untiledDirectLabels,
            final CoreTileLayout layout,
            final int seamAuditHalfWidthPixels) {
        if (tiledDirectLabels == null) {
            throw new IllegalArgumentException("tiledDirectLabels must not be null");
        }
        if (untiledDirectLabels == null) {
            throw new IllegalArgumentException("untiledDirectLabels must not be null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        requireOnePlaneByte(tiledDirectLabels, "tiledDirectLabels");
        requireOnePlaneByte(untiledDirectLabels, "untiledDirectLabels");

        final int width = tiledDirectLabels.getWidth();
        final int height = tiledDirectLabels.getHeight();
        if (untiledDirectLabels.getWidth() != width
                || untiledDirectLabels.getHeight() != height) {
            throw new IllegalArgumentException(
                    "tiled and untiled images must have equal dimensions");
        }
        if (width != layout.getImageWidth() || height != layout.getImageHeight()) {
            throw new IllegalArgumentException(
                    "layout dimensions must match image dimensions");
        }
        if (seamAuditHalfWidthPixels < 0) {
            throw new IllegalArgumentException(
                    "seamAuditHalfWidthPixels must be >= 0: " + seamAuditHalfWidthPixels);
        }

        final byte[] tiledPixels = (byte[]) tiledDirectLabels.getProcessor().getPixels();
        final byte[] referencePixels = (byte[]) untiledDirectLabels.getProcessor().getPixels();
        validateRawValues(tiledPixels, "tiledDirectLabels");
        validateRawValues(referencePixels, "untiledDirectLabels");

        final int[] verticalSeams = layout.getVerticalSeams();
        final int[] horizontalSeams = layout.getHorizontalSeams();

        final long[] tiledCounts = new long[4];
        final long[] referenceCounts = new long[4];
        final long[][] confusion = new long[4][4];
        final ByteProcessor difference = new ByteProcessor(width, height);
        final ByteProcessor seamAudit = new ByteProcessor(width, height);
        final byte[] differencePixels = (byte[]) difference.getPixels();
        final byte[] seamAuditPixels = (byte[]) seamAudit.getPixels();

        long differing = 0L;
        long seamBand = 0L;
        long seamDiffering = 0L;
        long nonSeam = 0L;
        long nonSeamDiffering = 0L;
        final long total = (long) width * (long) height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = y * width + x;
                final int tiled = tiledPixels[index] & 0xff;
                final int reference = referencePixels[index] & 0xff;
                tiledCounts[tiled]++;
                referenceCounts[reference]++;
                confusion[reference][tiled]++;

                final boolean different = tiled != reference;
                final boolean inSeam = isInSeamBand(
                        x, y, verticalSeams, horizontalSeams, seamAuditHalfWidthPixels);
                if (inSeam) {
                    seamBand++;
                } else {
                    nonSeam++;
                }
                if (different) {
                    differing++;
                    differencePixels[index] = (byte) 255;
                    if (inSeam) {
                        seamDiffering++;
                        seamAuditPixels[index] = (byte) 255;
                    } else {
                        nonSeamDiffering++;
                        seamAuditPixels[index] = (byte) 192;
                    }
                } else {
                    differencePixels[index] = 0;
                    if (inSeam) {
                        seamAuditPixels[index] = (byte) 64;
                    } else {
                        seamAuditPixels[index] = 0;
                    }
                }
            }
        }

        final boolean exactMatch = differing == 0L;
        final double differingFraction = total == 0L ? 0.0 : ((double) differing) / total;
        final double seamFraction = seamBand == 0L
                ? 0.0 : ((double) seamDiffering) / seamBand;
        final double nonSeamFraction = nonSeam == 0L
                ? 0.0 : ((double) nonSeamDiffering) / nonSeam;

        final ImagePlus differenceImage = new ImagePlus(
                "Tiled vs Untiled Difference Mask", difference);
        final ImagePlus seamAuditImage = new ImagePlus(
                "Tiled Seam Audit", seamAudit);

        return new ClassificationSeamAudit(
                width,
                height,
                layout.getTilesX(),
                layout.getTilesY(),
                layout.getCoreTileWidth(),
                layout.getCoreTileHeight(),
                seamAuditHalfWidthPixels,
                total,
                differing,
                differingFraction,
                seamBand,
                seamDiffering,
                seamFraction,
                nonSeam,
                nonSeamDiffering,
                nonSeamFraction,
                exactMatch,
                tiledCounts,
                referenceCounts,
                confusion,
                differenceImage,
                seamAuditImage);
    }

    static boolean isInSeamBand(
            final int x,
            final int y,
            final int[] verticalSeams,
            final int[] horizontalSeams,
            final int halfWidth) {
        for (int i = 0; i < verticalSeams.length; i++) {
            if (Math.abs(x - verticalSeams[i]) <= halfWidth) {
                return true;
            }
        }
        for (int i = 0; i < horizontalSeams.length; i++) {
            if (Math.abs(y - horizontalSeams[i]) <= halfWidth) {
                return true;
            }
        }
        return false;
    }

    private static void requireOnePlaneByte(final ImagePlus image, final String name) {
        if (image.getStackSize() != 1) {
            throw new IllegalArgumentException(name + " must be one image plane");
        }
        final ImageProcessor processor = image.getProcessor();
        if (!(processor instanceof ByteProcessor)) {
            throw new IllegalArgumentException(name + " must be an 8-bit ByteProcessor");
        }
    }

    private static void validateRawValues(final byte[] pixels, final String name) {
        for (int i = 0; i < pixels.length; i++) {
            final int value = pixels[i] & 0xff;
            if (value > 3) {
                throw new IllegalArgumentException(
                        name + " contains invalid raw value " + value + " at index " + i);
            }
        }
    }
}
