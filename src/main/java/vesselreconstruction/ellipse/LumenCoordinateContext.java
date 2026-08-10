package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable coordinate context linking crop-local, original-source, and physical
 * micrometre coordinates for one classified segment.
 */
public final class LumenCoordinateContext {

    private final String sampleId;
    private final String segmentId;
    private final int segmentOrder;
    private final SegmentOrientation segmentOrientation;
    private final int originalWidthPx;
    private final int originalHeightPx;
    private final int cropX;
    private final int cropY;
    private final int cropWidth;
    private final int cropHeight;
    private final double pixelWidthUm;
    private final double pixelHeightUm;
    private final Double optionalGlobalSegmentStartUm;

    public LumenCoordinateContext(
            final String sampleId,
            final String segmentId,
            final int segmentOrder,
            final SegmentOrientation segmentOrientation,
            final int originalWidthPx,
            final int originalHeightPx,
            final int cropX,
            final int cropY,
            final int cropWidth,
            final int cropHeight,
            final double pixelWidthUm,
            final double pixelHeightUm,
            final Double optionalGlobalSegmentStartUm) {
        if (sampleId == null || sampleId.trim().isEmpty()) {
            throw new IllegalArgumentException("sampleId must not be blank");
        }
        if (segmentId == null || segmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("segmentId must not be blank");
        }
        if (segmentOrder < 1) {
            throw new IllegalArgumentException("segmentOrder must be >= 1");
        }
        if (segmentOrientation == null) {
            throw new IllegalArgumentException("segmentOrientation must not be null");
        }
        if (originalWidthPx <= 0 || originalHeightPx <= 0) {
            throw new IllegalArgumentException("original dimensions must be positive");
        }
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("crop dimensions must be positive");
        }
        if (cropX < 0 || cropY < 0
                || cropX + cropWidth > originalWidthPx
                || cropY + cropHeight > originalHeightPx) {
            throw new IllegalArgumentException("crop must stay inside original source");
        }
        if (!(pixelWidthUm > 0.0) || Double.isNaN(pixelWidthUm) || Double.isInfinite(pixelWidthUm)
                || !(pixelHeightUm > 0.0) || Double.isNaN(pixelHeightUm)
                || Double.isInfinite(pixelHeightUm)) {
            throw new IllegalArgumentException("pixel sizes must be finite and > 0");
        }
        if (optionalGlobalSegmentStartUm != null
                && (Double.isNaN(optionalGlobalSegmentStartUm.doubleValue())
                || Double.isInfinite(optionalGlobalSegmentStartUm.doubleValue()))) {
            throw new IllegalArgumentException(
                    "optionalGlobalSegmentStartUm must be finite when present");
        }
        this.sampleId = sampleId;
        this.segmentId = segmentId;
        this.segmentOrder = segmentOrder;
        this.segmentOrientation = segmentOrientation;
        this.originalWidthPx = originalWidthPx;
        this.originalHeightPx = originalHeightPx;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.pixelWidthUm = pixelWidthUm;
        this.pixelHeightUm = pixelHeightUm;
        this.optionalGlobalSegmentStartUm = optionalGlobalSegmentStartUm;
    }

    public String getSampleId() {
        return sampleId;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public int getSegmentOrder() {
        return segmentOrder;
    }

    public SegmentOrientation getSegmentOrientation() {
        return segmentOrientation;
    }

    public int getOriginalWidthPx() {
        return originalWidthPx;
    }

    public int getOriginalHeightPx() {
        return originalHeightPx;
    }

    public int getCropX() {
        return cropX;
    }

    public int getCropY() {
        return cropY;
    }

    public int getCropWidth() {
        return cropWidth;
    }

    public int getCropHeight() {
        return cropHeight;
    }

    public double getPixelWidthUm() {
        return pixelWidthUm;
    }

    public double getPixelHeightUm() {
        return pixelHeightUm;
    }

    public Optional<Double> getOptionalGlobalSegmentStartUm() {
        return Optional.ofNullable(optionalGlobalSegmentStartUm);
    }

    public double cropXToOriginal(final double cropLocalX) {
        return cropLocalX + cropX;
    }

    public double cropYToOriginal(final double cropLocalY) {
        return cropLocalY + cropY;
    }

    public double originalXToPhysicalUm(final double originalX) {
        return originalX * pixelWidthUm;
    }

    public double originalYToPhysicalUm(final double originalY) {
        return originalY * pixelHeightUm;
    }

    /**
     * Optional local segment-axis position in micrometres.
     * Empty when orientation is {@link SegmentOrientation#UNSPECIFIED}.
     */
    public Optional<Double> segmentAxisPositionUm(
            final double originalX,
            final double originalY) {
        switch (segmentOrientation) {
            case TOP_TO_BOTTOM:
                return Optional.of(Double.valueOf(originalY * pixelHeightUm));
            case BOTTOM_TO_TOP:
                return Optional.of(Double.valueOf(
                        ((originalHeightPx - 1) - originalY) * pixelHeightUm));
            case LEFT_TO_RIGHT:
                return Optional.of(Double.valueOf(originalX * pixelWidthUm));
            case RIGHT_TO_LEFT:
                return Optional.of(Double.valueOf(
                        ((originalWidthPx - 1) - originalX) * pixelWidthUm));
            case UNSPECIFIED:
            default:
                return Optional.empty();
        }
    }

    /**
     * Optional global axis position. Requires orientation and global start.
     */
    public Optional<Double> globalAxisPositionUm(
            final double originalX,
            final double originalY) {
        if (optionalGlobalSegmentStartUm == null) {
            return Optional.empty();
        }
        final Optional<Double> local = segmentAxisPositionUm(originalX, originalY);
        if (!local.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(Double.valueOf(
                optionalGlobalSegmentStartUm.doubleValue() + local.get().doubleValue()));
    }

    public double areaPxToUm2(final double areaPx) {
        return areaPx * pixelWidthUm * pixelHeightUm;
    }

    public Optional<Double> isotropicLengthPxToUm(final double lengthPx) {
        if (!isIsotropic(1e-12)) {
            return Optional.empty();
        }
        return Optional.of(Double.valueOf(lengthPx * pixelWidthUm));
    }

    public boolean isIsotropic(final double relativeTolerance) {
        final double mean = 0.5 * (pixelWidthUm + pixelHeightUm);
        return mean > 0.0
                && Math.abs(pixelWidthUm - pixelHeightUm) / mean <= relativeTolerance;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "LumenCoordinateContext{sample=%s,segment=%s,order=%d,orient=%s,"
                        + "crop=(%d,%d,%d,%d),px=(%.12f,%.12f)}",
                sampleId,
                segmentId,
                Integer.valueOf(segmentOrder),
                segmentOrientation.name(),
                Integer.valueOf(cropX),
                Integer.valueOf(cropY),
                Integer.valueOf(cropWidth),
                Integer.valueOf(cropHeight),
                Double.valueOf(pixelWidthUm),
                Double.valueOf(pixelHeightUm));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LumenCoordinateContext)) {
            return false;
        }
        final LumenCoordinateContext o = (LumenCoordinateContext) obj;
        return sampleId.equals(o.sampleId)
                && segmentId.equals(o.segmentId)
                && segmentOrder == o.segmentOrder
                && segmentOrientation == o.segmentOrientation
                && originalWidthPx == o.originalWidthPx
                && originalHeightPx == o.originalHeightPx
                && cropX == o.cropX
                && cropY == o.cropY
                && cropWidth == o.cropWidth
                && cropHeight == o.cropHeight
                && Double.compare(pixelWidthUm, o.pixelWidthUm) == 0
                && Double.compare(pixelHeightUm, o.pixelHeightUm) == 0
                && Objects.equals(optionalGlobalSegmentStartUm, o.optionalGlobalSegmentStartUm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sampleId,
                segmentId,
                Integer.valueOf(segmentOrder),
                segmentOrientation,
                Integer.valueOf(originalWidthPx),
                Integer.valueOf(originalHeightPx),
                Integer.valueOf(cropX),
                Integer.valueOf(cropY),
                Integer.valueOf(cropWidth),
                Integer.valueOf(cropHeight),
                Double.valueOf(pixelWidthUm),
                Double.valueOf(pixelHeightUm),
                optionalGlobalSegmentStartUm);
    }
}
