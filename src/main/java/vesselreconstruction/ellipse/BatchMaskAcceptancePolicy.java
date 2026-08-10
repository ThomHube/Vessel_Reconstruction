package vesselreconstruction.ellipse;

/**
 * Conservative automatic acceptance policy for Phase-2 sample masks.
 */
public final class BatchMaskAcceptancePolicy {

    public static final class Decision {
        public final boolean accepted;
        public final String reasonCode;
        public final String message;
        public final double majorSpanFraction;
        public final double secondaryComponentAreaFraction;

        public Decision(
                final boolean accepted,
                final String reasonCode,
                final String message,
                final double majorSpanFraction,
                final double secondaryComponentAreaFraction) {
            this.accepted = accepted;
            this.reasonCode = reasonCode == null ? RCsvSupport.NA : reasonCode;
            this.message = message == null ? RCsvSupport.NA : message;
            this.majorSpanFraction = majorSpanFraction;
            this.secondaryComponentAreaFraction = secondaryComponentAreaFraction;
        }

        public static Decision accept(
                final double majorSpanFraction,
                final double secondaryComponentAreaFraction) {
            return new Decision(
                    true,
                    RCsvSupport.NA,
                    "mask accepted",
                    majorSpanFraction,
                    secondaryComponentAreaFraction);
        }

        public static Decision reject(
                final String reasonCode,
                final String message,
                final double majorSpanFraction,
                final double secondaryComponentAreaFraction) {
            return new Decision(
                    false,
                    reasonCode,
                    message,
                    majorSpanFraction,
                    secondaryComponentAreaFraction);
        }
    }

    private BatchMaskAcceptancePolicy() {
    }

    public static Decision evaluate(
            final SampleMaskGenerationResult generation,
            final AnalysisCropGeometry crop,
            final int sourceWidth,
            final int sourceHeight,
            final BatchConfiguration config) {
        if (generation == null || generation.getMask() == null) {
            return Decision.reject(
                    "MASK_GENERATION_MISSING",
                    "automatic mask generation result missing",
                    Double.NaN,
                    Double.NaN);
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return Decision.reject(
                    "MASK_SOURCE_DIMENSIONS_INVALID",
                    "source image dimensions must be positive",
                    Double.NaN,
                    Double.NaN);
        }
        final DownsampledSampleMask mask = generation.getMask();
        final SampleMaskStatistics stats = generation.getStatistics();
        if (stats == null) {
            return Decision.reject(
                    "MASK_STATISTICS_MISSING",
                    "mask statistics missing",
                    Double.NaN,
                    Double.NaN);
        }
        if (stats.getSelectedComponentAreaMaskPixels() <= 0L
                || stats.getSelectedComponentBoundsMask().width <= 0
                || stats.getSelectedComponentBoundsMask().height <= 0) {
            return Decision.reject(
                    "MASK_SELECTED_COMPONENT_MISSING",
                    "selected component missing",
                    Double.NaN,
                    Double.NaN);
        }
        if (mask.isEmpty()) {
            return Decision.reject(
                    "MASK_EMPTY",
                    "approved mask is empty",
                    Double.NaN,
                    Double.NaN);
        }
        final int expectedMaskW = DownsampledSampleMask.maskDimension(
                sourceWidth, config.maskDownsampleFactor());
        final int expectedMaskH = DownsampledSampleMask.maskDimension(
                sourceHeight, config.maskDownsampleFactor());
        if (mask.getOriginalWidth() != sourceWidth
                || mask.getOriginalHeight() != sourceHeight
                || mask.getDownsampleFactor() != config.maskDownsampleFactor()
                || mask.getMaskWidth() != expectedMaskW
                || mask.getMaskHeight() != expectedMaskH) {
            return Decision.reject(
                    "MASK_DIMENSION_MISMATCH",
                    "mask dimensions disagree with source and downsample factor",
                    Double.NaN,
                    Double.NaN);
        }
        // checksum is int; always "available" when mask exists
        final double majorSpan = majorSpanFraction(stats, mask);
        final double secondary = stats.getSecondaryComponentAreaFraction();

        if (crop == null
                || crop.getCropWidthPx() <= 0
                || crop.getCropHeightPx() <= 0) {
            return Decision.reject(
                    "MASK_CROP_INVALID",
                    "crop rectangle is empty",
                    majorSpan,
                    secondary);
        }
        if (crop.getCropXPx() < 0
                || crop.getCropYPx() < 0
                || crop.getCropXPx() + crop.getCropWidthPx() > sourceWidth
                || crop.getCropYPx() + crop.getCropHeightPx() > sourceHeight) {
            return Decision.reject(
                    "MASK_CROP_INVALID",
                    "crop rectangle escapes source image",
                    majorSpan,
                    secondary);
        }

        final double included = stats.getAutomaticIncludedFraction();
        if (!(included >= config.maskMinimumIncludedFraction())) {
            return Decision.reject(
                    "MASK_INCLUDED_FRACTION_TOO_SMALL",
                    "included fraction below minimum",
                    majorSpan,
                    secondary);
        }
        if (!(included <= config.maskMaximumIncludedFraction())) {
            return Decision.reject(
                    "MASK_INCLUDED_FRACTION_TOO_LARGE",
                    "included fraction above maximum",
                    majorSpan,
                    secondary);
        }
        if (!(majorSpan >= config.maskMinimumMajorSpanFraction())) {
            return Decision.reject(
                    "MASK_MAJOR_SPAN_TOO_SMALL",
                    "selected component major span below minimum",
                    majorSpan,
                    secondary);
        }
        if (secondary > config.maskMaximumSecondaryComponentAreaFraction()) {
            return Decision.reject(
                    "MASK_COMPETING_COMPONENT_TOO_LARGE",
                    "secondary competing component too large",
                    majorSpan,
                    secondary);
        }
        return Decision.accept(majorSpan, secondary);
    }

    public static double majorSpanFraction(
            final SampleMaskStatistics stats,
            final DownsampledSampleMask mask) {
        if (mask.getMaskWidth() <= 0 || mask.getMaskHeight() <= 0) {
            return Double.NaN;
        }
        final double widthFrac =
                ((double) stats.getSelectedComponentWidthSpan()) / ((double) mask.getMaskWidth());
        final double heightFrac =
                ((double) stats.getSelectedComponentHeightSpan()) / ((double) mask.getMaskHeight());
        return Math.max(widthFrac, heightFrac);
    }
}
