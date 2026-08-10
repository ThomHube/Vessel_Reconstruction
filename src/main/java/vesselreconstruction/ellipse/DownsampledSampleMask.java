package vesselreconstruction.ellipse;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Compact downsampled binary sample-analysis mask.
 *
 * <p>White/255 = included sample envelope; black/0 = excluded exterior.
 * The mask is stored only at downsampled resolution. Original-image coordinates
 * are mapped with integer division:</p>
 *
 * <pre>
 * maskX = min(maskWidth - 1, originalX / downsampleFactor)
 * maskY = min(maskHeight - 1, originalY / downsampleFactor)
 * </pre>
 *
 * <p>No full-resolution mask array is allocated.</p>
 */
public final class DownsampledSampleMask {

    private final int originalWidth;
    private final int originalHeight;
    private final int maskWidth;
    private final int maskHeight;
    private final int downsampleFactor;
    private final byte[] pixels;
    private final long includedMaskPixels;
    private final int checksum;
    private final Rectangle includedBoundsOriginal;

    public DownsampledSampleMask(
            final int originalWidth,
            final int originalHeight,
            final int downsampleFactor,
            final byte[] maskPixels,
            final int maskWidth,
            final int maskHeight) {
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("original dimensions must be positive");
        }
        if (downsampleFactor <= 0) {
            throw new IllegalArgumentException(
                    "downsampleFactor must be > 0: " + downsampleFactor);
        }
        if (maskWidth <= 0 || maskHeight <= 0) {
            throw new IllegalArgumentException("mask dimensions must be positive");
        }
        if (maskPixels == null || maskPixels.length != maskWidth * maskHeight) {
            throw new IllegalArgumentException("maskPixels length must equal maskWidth*maskHeight");
        }
        final int expectedWidth = maskDimension(originalWidth, downsampleFactor);
        final int expectedHeight = maskDimension(originalHeight, downsampleFactor);
        if (maskWidth != expectedWidth || maskHeight != expectedHeight) {
            throw new IllegalArgumentException(
                    "mask dimensions must match original/downsample mapping: expected "
                            + expectedWidth + "x" + expectedHeight
                            + ", got " + maskWidth + "x" + maskHeight);
        }

        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.maskWidth = maskWidth;
        this.maskHeight = maskHeight;
        this.downsampleFactor = downsampleFactor;
        this.pixels = Arrays.copyOf(maskPixels, maskPixels.length);

        long included = 0L;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        int hash = 1;
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                final int index = y * maskWidth + x;
                final int value = this.pixels[index] & 0xff;
                hash = 31 * hash + value;
                final boolean includedPixel = value != 0;
                if (includedPixel) {
                    this.pixels[index] = (byte) 255;
                    included++;
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
                } else {
                    this.pixels[index] = 0;
                }
            }
        }
        this.includedMaskPixels = included;
        this.checksum = hash;
        if (included == 0L) {
            this.includedBoundsOriginal = new Rectangle(0, 0, 0, 0);
        } else {
            final int origMinX = minX * downsampleFactor;
            final int origMinY = minY * downsampleFactor;
            final int origMaxXExclusive = Math.min(
                    originalWidth, (maxX + 1) * downsampleFactor);
            final int origMaxYExclusive = Math.min(
                    originalHeight, (maxY + 1) * downsampleFactor);
            this.includedBoundsOriginal = new Rectangle(
                    origMinX,
                    origMinY,
                    origMaxXExclusive - origMinX,
                    origMaxYExclusive - origMinY);
        }
    }

    public static int maskDimension(final int originalSize, final int downsampleFactor) {
        if (originalSize <= 0 || downsampleFactor <= 0) {
            throw new IllegalArgumentException("sizes must be positive");
        }
        return (originalSize + downsampleFactor - 1) / downsampleFactor;
    }

    public static int toMaskCoordinate(
            final int originalCoordinate,
            final int downsampleFactor,
            final int maskSize) {
        if (originalCoordinate < 0) {
            throw new IllegalArgumentException("originalCoordinate must be >= 0");
        }
        if (maskSize <= 0 || downsampleFactor <= 0) {
            throw new IllegalArgumentException("maskSize and factor must be > 0");
        }
        final int mapped = originalCoordinate / downsampleFactor;
        return mapped >= maskSize ? maskSize - 1 : mapped;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public int getMaskWidth() {
        return maskWidth;
    }

    public int getMaskHeight() {
        return maskHeight;
    }

    public int getDownsampleFactor() {
        return downsampleFactor;
    }

    public long getIncludedMaskPixels() {
        return includedMaskPixels;
    }

    public double getIncludedMaskFraction() {
        final long total = ((long) maskWidth) * ((long) maskHeight);
        return total == 0L ? 0.0 : ((double) includedMaskPixels) / ((double) total);
    }

    public int getChecksum() {
        return checksum;
    }

    public Rectangle getIncludedBoundsOriginal() {
        return new Rectangle(includedBoundsOriginal);
    }

    public boolean isEmpty() {
        return includedMaskPixels == 0L;
    }

    public boolean containsOriginalPixel(final int originalX, final int originalY) {
        if (originalX < 0 || originalY < 0
                || originalX >= originalWidth || originalY >= originalHeight) {
            return false;
        }
        final int mx = toMaskCoordinate(originalX, downsampleFactor, maskWidth);
        final int my = toMaskCoordinate(originalY, downsampleFactor, maskHeight);
        return (pixels[my * maskWidth + mx] & 0xff) != 0;
    }

    public boolean coreTileContainsIncludedPixels(
            final int coreX,
            final int coreY,
            final int coreWidth,
            final int coreHeight) {
        if (coreWidth <= 0 || coreHeight <= 0) {
            return false;
        }
        if (coreX >= originalWidth || coreY >= originalHeight) {
            return false;
        }
        final int x0 = Math.max(0, coreX);
        final int y0 = Math.max(0, coreY);
        final int x1 = Math.min(originalWidth, coreX + coreWidth);
        final int y1 = Math.min(originalHeight, coreY + coreHeight);
        if (x0 >= x1 || y0 >= y1) {
            return false;
        }

        final int mx0 = toMaskCoordinate(x0, downsampleFactor, maskWidth);
        final int my0 = toMaskCoordinate(y0, downsampleFactor, maskHeight);
        final int mx1 = toMaskCoordinate(x1 - 1, downsampleFactor, maskWidth);
        final int my1 = toMaskCoordinate(y1 - 1, downsampleFactor, maskHeight);
        for (int my = my0; my <= my1; my++) {
            for (int mx = mx0; mx <= mx1; mx++) {
                if ((pixels[my * maskWidth + mx] & 0xff) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public byte[] copyMaskPixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DownsampledSampleMask)) {
            return false;
        }
        final DownsampledSampleMask other = (DownsampledSampleMask) obj;
        return originalWidth == other.originalWidth
                && originalHeight == other.originalHeight
                && maskWidth == other.maskWidth
                && maskHeight == other.maskHeight
                && downsampleFactor == other.downsampleFactor
                && includedMaskPixels == other.includedMaskPixels
                && checksum == other.checksum
                && includedBoundsOriginal.equals(other.includedBoundsOriginal)
                && Arrays.equals(pixels, other.pixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(originalWidth),
                Integer.valueOf(originalHeight),
                Integer.valueOf(maskWidth),
                Integer.valueOf(maskHeight),
                Integer.valueOf(downsampleFactor),
                Long.valueOf(includedMaskPixels),
                Integer.valueOf(checksum),
                includedBoundsOriginal,
                Integer.valueOf(Arrays.hashCode(pixels)));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "DownsampledSampleMask{orig=%dx%d,mask=%dx%d,factor=%d,included=%d,checksum=%d}",
                Integer.valueOf(originalWidth),
                Integer.valueOf(originalHeight),
                Integer.valueOf(maskWidth),
                Integer.valueOf(maskHeight),
                Integer.valueOf(downsampleFactor),
                Long.valueOf(includedMaskPixels),
                Integer.valueOf(checksum));
    }
}
