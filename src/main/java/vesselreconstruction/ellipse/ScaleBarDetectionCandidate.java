package vesselreconstruction.ellipse;

import java.util.Locale;

/**
 * One low-resolution red-component candidate considered for scale-bar detection.
 */
public final class ScaleBarDetectionCandidate {

    private final int maskMinX;
    private final int maskMinY;
    private final int maskMaxX;
    private final int maskMaxY;
    private final int pixelCount;
    private final double geometryScore;
    private final double aspectRatio;
    private final double horizontalAngleDegrees;
    private final double exteriorFraction;
    private final boolean accepted;
    private final String reason;

    public ScaleBarDetectionCandidate(
            final int maskMinX,
            final int maskMinY,
            final int maskMaxX,
            final int maskMaxY,
            final int pixelCount,
            final double geometryScore,
            final double aspectRatio,
            final double horizontalAngleDegrees,
            final double exteriorFraction,
            final boolean accepted,
            final String reason) {
        this.maskMinX = maskMinX;
        this.maskMinY = maskMinY;
        this.maskMaxX = maskMaxX;
        this.maskMaxY = maskMaxY;
        this.pixelCount = pixelCount;
        this.geometryScore = geometryScore;
        this.aspectRatio = aspectRatio;
        this.horizontalAngleDegrees = horizontalAngleDegrees;
        this.exteriorFraction = exteriorFraction;
        this.accepted = accepted;
        this.reason = reason == null ? "" : reason;
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

    public int getMaskWidth() {
        return maskMaxX - maskMinX + 1;
    }

    public int getMaskHeight() {
        return maskMaxY - maskMinY + 1;
    }

    public int getPixelCount() {
        return pixelCount;
    }

    public double getGeometryScore() {
        return geometryScore;
    }

    public double getAspectRatio() {
        return aspectRatio;
    }

    public double getHorizontalAngleDegrees() {
        return horizontalAngleDegrees;
    }

    public double getExteriorFraction() {
        return exteriorFraction;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "ScaleBarDetectionCandidate{maskBBox=[%d,%d]-[%d,%d],pixels=%d,score=%.4f,"
                        + "aspect=%.3f,angle=%.2f,exterior=%.3f,accepted=%s,reason=%s}",
                Integer.valueOf(maskMinX),
                Integer.valueOf(maskMinY),
                Integer.valueOf(maskMaxX),
                Integer.valueOf(maskMaxY),
                Integer.valueOf(pixelCount),
                Double.valueOf(geometryScore),
                Double.valueOf(aspectRatio),
                Double.valueOf(horizontalAngleDegrees),
                Double.valueOf(exteriorFraction),
                Boolean.valueOf(accepted),
                reason);
    }
}
