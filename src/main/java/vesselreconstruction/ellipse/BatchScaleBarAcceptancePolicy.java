package vesselreconstruction.ellipse;

import java.awt.Rectangle;

/**
 * Conservative automatic acceptance policy for Phase-2 scale-bar detection.
 */
public final class BatchScaleBarAcceptancePolicy {

    public static final class Decision {
        public final boolean accepted;
        public final String reasonCode;
        public final String message;
        public final double pixelSizeUm;
        public final double relativeDeviation;

        public Decision(
                final boolean accepted,
                final String reasonCode,
                final String message,
                final double pixelSizeUm,
                final double relativeDeviation) {
            this.accepted = accepted;
            this.reasonCode = reasonCode == null ? RCsvSupport.NA : reasonCode;
            this.message = message == null ? RCsvSupport.NA : message;
            this.pixelSizeUm = pixelSizeUm;
            this.relativeDeviation = relativeDeviation;
        }

        public static Decision accept(final double pixelSizeUm, final double relativeDeviation) {
            return new Decision(true, RCsvSupport.NA, "scale bar accepted",
                    pixelSizeUm, relativeDeviation);
        }

        public static Decision reject(
                final String reasonCode,
                final String message,
                final double pixelSizeUm,
                final double relativeDeviation) {
            return new Decision(false, reasonCode, message, pixelSizeUm, relativeDeviation);
        }
    }

    private BatchScaleBarAcceptancePolicy() {
    }

    public static Decision evaluate(
            final ScaleBarDetection detection,
            final DownsampledSampleMask approvedMask,
            final BatchConfiguration config) {
        if (detection == null || !detection.isSuccessful()) {
            return Decision.reject(
                    "SCALE_BAR_NOT_FOUND",
                    detection == null ? "detection missing" : detection.getFailureReason(),
                    Double.NaN,
                    Double.NaN);
        }
        final double physical = config.scaleBarPhysicalLengthUm();
        final double measured = detection.getMeasuredLengthPx();
        if (!Double.isFinite(physical) || !(physical > 0.0)) {
            return Decision.reject(
                    "SCALE_BAR_PHYSICAL_LENGTH_INVALID",
                    "physical bar length invalid",
                    Double.NaN,
                    Double.NaN);
        }
        if (!Double.isFinite(measured) || !(measured > 0.0)) {
            return Decision.reject(
                    "SCALE_BAR_CENTER_DISTANCE_INVALID",
                    "measured center-to-center distance invalid",
                    Double.NaN,
                    Double.NaN);
        }
        final double pixelSize = physical / measured;
        if (!Double.isFinite(pixelSize) || !(pixelSize > 0.0)) {
            return Decision.reject(
                    "SCALE_BAR_PIXEL_SIZE_INVALID",
                    "pixel size invalid",
                    Double.NaN,
                    Double.NaN);
        }
        final double relativeDeviation = relativeDeviation(
                pixelSize, config.scaleBarExpectedPixelSizeUm());

        if (!confidenceAtLeast(detection.getConfidence(), config.scaleBarMinimumConfidence())) {
            return Decision.reject(
                    "SCALE_BAR_CONFIDENCE_TOO_LOW",
                    "confidence below configured minimum",
                    pixelSize,
                    relativeDeviation);
        }
        if (!(detection.getShaftContinuity() >= config.scaleBarMinimumShaftContinuity())) {
            return Decision.reject(
                    "SCALE_BAR_SHAFT_CONTINUITY_TOO_LOW",
                    "shaft continuity below configured minimum",
                    pixelSize,
                    relativeDeviation);
        }
        if (config.scaleBarRequireTerminalTickCenterlines()
                && detection.getMethod() != ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES) {
            return Decision.reject(
                    "SCALE_BAR_WRONG_METHOD",
                    "detection method is not TERMINAL_TICK_CENTERLINES",
                    pixelSize,
                    relativeDeviation);
        }
        if (geometryIntersectsSample(detection, approvedMask)) {
            return Decision.reject(
                    "SCALE_BAR_INSIDE_SAMPLE",
                    "scale-bar geometry intersects included sample mask",
                    pixelSize,
                    relativeDeviation);
        }
        if (!(relativeDeviation <= config.scaleBarMaximumRelativePixelSizeDeviation())) {
            return Decision.reject(
                    "SCALE_BAR_PIXEL_SIZE_DEVIATION_TOO_LARGE",
                    "relative pixel-size deviation exceeds maximum",
                    pixelSize,
                    relativeDeviation);
        }
        return Decision.accept(pixelSize, relativeDeviation);
    }

    public static double relativeDeviation(
            final double measuredPixelSizeUm,
            final double expectedPixelSizeUm) {
        if (!Double.isFinite(measuredPixelSizeUm)
                || !Double.isFinite(expectedPixelSizeUm)
                || !(expectedPixelSizeUm > 0.0)) {
            return Double.NaN;
        }
        return Math.abs(measuredPixelSizeUm - expectedPixelSizeUm) / expectedPixelSizeUm;
    }

    static boolean confidenceAtLeast(
            final ScaleBarDetectionConfidence actual,
            final ScaleBarDetectionConfidence minimum) {
        return rank(actual) >= rank(minimum);
    }

    private static int rank(final ScaleBarDetectionConfidence confidence) {
        if (confidence == null) {
            return -1;
        }
        switch (confidence) {
            case FAILED:
                return 0;
            case LOW:
                return 1;
            case MEDIUM:
                return 2;
            case HIGH:
                return 3;
            default:
                return -1;
        }
    }

    private static boolean geometryIntersectsSample(
            final ScaleBarDetection detection,
            final DownsampledSampleMask mask) {
        if (mask == null) {
            return true;
        }
        if (pointInside(mask, detection.getLeftTickCenterX(), detection.getLeftTickCenterY())
                || pointInside(mask, detection.getRightTickCenterX(), detection.getRightTickCenterY())) {
            return true;
        }
        final Rectangle bounds = detection.getFullResolutionComponentBounds() != null
                ? detection.getFullResolutionComponentBounds()
                : detection.getCandidateBoundsOriginal();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        // Sample a coarse grid over the component bounds.
        final int stepX = Math.max(1, bounds.width / 8);
        final int stepY = Math.max(1, bounds.height / 8);
        for (int y = bounds.y; y < bounds.y + bounds.height; y += stepY) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x += stepX) {
                if (mask.containsOriginalPixel(x, y)) {
                    return true;
                }
            }
        }
        return mask.containsOriginalPixel(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1);
    }

    private static boolean pointInside(
            final DownsampledSampleMask mask,
            final double x,
            final double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }
        return mask.containsOriginalPixel((int) Math.round(x), (int) Math.round(y));
    }
}
