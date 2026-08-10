package vesselreconstruction.ellipse;

import java.awt.Rectangle;
import java.util.Locale;

/**
 * Exact rectangular analysis crop derived from an approved downsampled mask.
 *
 * <p>Coordinates use exclusive maximums internally and expose standard x, y,
 * width, height in original-source pixels.</p>
 */
public final class AnalysisCropGeometry {

    private final int originalWidthPx;
    private final int originalHeightPx;
    private final int cropXPx;
    private final int cropYPx;
    private final int cropWidthPx;
    private final int cropHeightPx;
    private final int cropPaddingPx;
    private final int maskMinX;
    private final int maskMinY;
    private final int maskMaxX;
    private final int maskMaxY;
    private final int downsampleFactor;

    private AnalysisCropGeometry(
            final int originalWidthPx,
            final int originalHeightPx,
            final int cropXPx,
            final int cropYPx,
            final int cropWidthPx,
            final int cropHeightPx,
            final int cropPaddingPx,
            final int maskMinX,
            final int maskMinY,
            final int maskMaxX,
            final int maskMaxY,
            final int downsampleFactor) {
        this.originalWidthPx = originalWidthPx;
        this.originalHeightPx = originalHeightPx;
        this.cropXPx = cropXPx;
        this.cropYPx = cropYPx;
        this.cropWidthPx = cropWidthPx;
        this.cropHeightPx = cropHeightPx;
        this.cropPaddingPx = cropPaddingPx;
        this.maskMinX = maskMinX;
        this.maskMinY = maskMinY;
        this.maskMaxX = maskMaxX;
        this.maskMaxY = maskMaxY;
        this.downsampleFactor = downsampleFactor;
    }

    /**
     * Builds crop geometry from included mask-cell bounds (inclusive).
     */
    public static AnalysisCropGeometry fromIncludedMaskBounds(
            final int originalWidthPx,
            final int originalHeightPx,
            final int downsampleFactor,
            final int maskWidth,
            final int maskHeight,
            final int maskMinXInclusive,
            final int maskMinYInclusive,
            final int maskMaxXInclusive,
            final int maskMaxYInclusive,
            final int cropPaddingPx) {
        if (originalWidthPx <= 0 || originalHeightPx <= 0) {
            throw new IllegalArgumentException(
                    "Source dimensions must be positive: "
                            + originalWidthPx + "x" + originalHeightPx);
        }
        if (downsampleFactor < 1) {
            throw new IllegalArgumentException(
                    "downsampleFactor must be >= 1: " + downsampleFactor);
        }
        if (maskWidth <= 0 || maskHeight <= 0) {
            throw new IllegalArgumentException(
                    "Mask dimensions must be positive: " + maskWidth + "x" + maskHeight);
        }
        if (cropPaddingPx < 0) {
            throw new IllegalArgumentException(
                    "cropPaddingPx must be >= 0: " + cropPaddingPx);
        }
        if (maskMinXInclusive < 0 || maskMinYInclusive < 0
                || maskMaxXInclusive < maskMinXInclusive
                || maskMaxYInclusive < maskMinYInclusive
                || maskMaxXInclusive >= maskWidth
                || maskMaxYInclusive >= maskHeight) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Invalid included mask bounds [%d,%d]-[%d,%d] for mask %dx%d",
                    Integer.valueOf(maskMinXInclusive),
                    Integer.valueOf(maskMinYInclusive),
                    Integer.valueOf(maskMaxXInclusive),
                    Integer.valueOf(maskMaxYInclusive),
                    Integer.valueOf(maskWidth),
                    Integer.valueOf(maskHeight)));
        }

        final int originalX0 = maskMinXInclusive * downsampleFactor;
        final int originalY0 = maskMinYInclusive * downsampleFactor;
        final int originalX1Exclusive = Math.min(
                originalWidthPx,
                (maskMaxXInclusive + 1) * downsampleFactor);
        final int originalY1Exclusive = Math.min(
                originalHeightPx,
                (maskMaxYInclusive + 1) * downsampleFactor);

        if (originalX0 >= originalX1Exclusive || originalY0 >= originalY1Exclusive) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Mapped mask bounds produce empty crop: source=%dx%d factor=%d "
                            + "maskBounds=[%d,%d]-[%d,%d] mapped=[%d,%d)-[%d,%d)",
                    Integer.valueOf(originalWidthPx),
                    Integer.valueOf(originalHeightPx),
                    Integer.valueOf(downsampleFactor),
                    Integer.valueOf(maskMinXInclusive),
                    Integer.valueOf(maskMinYInclusive),
                    Integer.valueOf(maskMaxXInclusive),
                    Integer.valueOf(maskMaxYInclusive),
                    Integer.valueOf(originalX0),
                    Integer.valueOf(originalY0),
                    Integer.valueOf(originalX1Exclusive),
                    Integer.valueOf(originalY1Exclusive)));
        }

        final int paddedX0 = Math.max(0, originalX0 - cropPaddingPx);
        final int paddedY0 = Math.max(0, originalY0 - cropPaddingPx);
        final int paddedX1 = Math.min(originalWidthPx, originalX1Exclusive + cropPaddingPx);
        final int paddedY1 = Math.min(originalHeightPx, originalY1Exclusive + cropPaddingPx);
        final int width = paddedX1 - paddedX0;
        final int height = paddedY1 - paddedY0;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Crop became empty after padding/clipping: source=%dx%d factor=%d "
                            + "mask=%dx%d padding=%d",
                    Integer.valueOf(originalWidthPx),
                    Integer.valueOf(originalHeightPx),
                    Integer.valueOf(downsampleFactor),
                    Integer.valueOf(maskWidth),
                    Integer.valueOf(maskHeight),
                    Integer.valueOf(cropPaddingPx)));
        }

        return new AnalysisCropGeometry(
                originalWidthPx,
                originalHeightPx,
                paddedX0,
                paddedY0,
                width,
                height,
                cropPaddingPx,
                maskMinXInclusive,
                maskMinYInclusive,
                maskMaxXInclusive,
                maskMaxYInclusive,
                downsampleFactor);
    }

    public static AnalysisCropGeometry fromApprovedMask(
            final DownsampledSampleMask mask,
            final int cropPaddingPx) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (mask.isEmpty()) {
            throw new IllegalArgumentException("Approved mask is empty; cannot compute crop");
        }
        if (cropPaddingPx < 0) {
            throw new IllegalArgumentException(
                    "cropPaddingPx must be >= 0: " + cropPaddingPx);
        }
        final Rectangle included = mask.getIncludedBoundsOriginal();
        final int factor = mask.getDownsampleFactor();
        final int originalX0 = included.x;
        final int originalY0 = included.y;
        final int originalX1Exclusive = included.x + included.width;
        final int originalY1Exclusive = included.y + included.height;
        final int maskMinX = originalX0 / factor;
        final int maskMinY = originalY0 / factor;
        final int maskMaxX = (originalX1Exclusive - 1) / factor;
        final int maskMaxY = (originalY1Exclusive - 1) / factor;

        final int paddedX0 = Math.max(0, originalX0 - cropPaddingPx);
        final int paddedY0 = Math.max(0, originalY0 - cropPaddingPx);
        final int paddedX1 = Math.min(mask.getOriginalWidth(), originalX1Exclusive + cropPaddingPx);
        final int paddedY1 = Math.min(mask.getOriginalHeight(), originalY1Exclusive + cropPaddingPx);
        final int width = paddedX1 - paddedX0;
        final int height = paddedY1 - paddedY0;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Crop became empty after padding/clipping: source=%dx%d factor=%d "
                            + "mask=%dx%d padding=%d included=%s",
                    Integer.valueOf(mask.getOriginalWidth()),
                    Integer.valueOf(mask.getOriginalHeight()),
                    Integer.valueOf(factor),
                    Integer.valueOf(mask.getMaskWidth()),
                    Integer.valueOf(mask.getMaskHeight()),
                    Integer.valueOf(cropPaddingPx),
                    included));
        }
        return new AnalysisCropGeometry(
                mask.getOriginalWidth(),
                mask.getOriginalHeight(),
                paddedX0,
                paddedY0,
                width,
                height,
                cropPaddingPx,
                maskMinX,
                maskMinY,
                maskMaxX,
                maskMaxY,
                factor);
    }

    /** Full-size validation mode: crop equals the entire source. */
    public static AnalysisCropGeometry fullSource(
            final int originalWidthPx,
            final int originalHeightPx) {
        if (originalWidthPx <= 0 || originalHeightPx <= 0) {
            throw new IllegalArgumentException(
                    "Source dimensions must be positive: "
                            + originalWidthPx + "x" + originalHeightPx);
        }
        return new AnalysisCropGeometry(
                originalWidthPx,
                originalHeightPx,
                0,
                0,
                originalWidthPx,
                originalHeightPx,
                0,
                0,
                0,
                0,
                0,
                1);
    }

    public int getOriginalWidthPx() {
        return originalWidthPx;
    }

    public int getOriginalHeightPx() {
        return originalHeightPx;
    }

    public int getCropXPx() {
        return cropXPx;
    }

    public int getCropYPx() {
        return cropYPx;
    }

    public int getCropWidthPx() {
        return cropWidthPx;
    }

    public int getCropHeightPx() {
        return cropHeightPx;
    }

    public int getCropPaddingPx() {
        return cropPaddingPx;
    }

    public int getMaskMinX() {
        return maskMinX;
    }

    public int getMaskMinY() {
        return maskMinY;
    }

    public int getMaskMaxX() {
        return maskMaxX;
    }

    public int getMaskMaxY() {
        return maskMaxY;
    }

    public int getDownsampleFactor() {
        return downsampleFactor;
    }

    public Rectangle asRectangle() {
        return new Rectangle(cropXPx, cropYPx, cropWidthPx, cropHeightPx);
    }

    public int cropLocalXToOriginal(final int cropLocalX) {
        return cropXPx + cropLocalX;
    }

    public int cropLocalYToOriginal(final int cropLocalY) {
        return cropYPx + cropLocalY;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "AnalysisCropGeometry{x=%d,y=%d,w=%d,h=%d,padding=%d,source=%dx%d}",
                Integer.valueOf(cropXPx),
                Integer.valueOf(cropYPx),
                Integer.valueOf(cropWidthPx),
                Integer.valueOf(cropHeightPx),
                Integer.valueOf(cropPaddingPx),
                Integer.valueOf(originalWidthPx),
                Integer.valueOf(originalHeightPx));
    }
}
