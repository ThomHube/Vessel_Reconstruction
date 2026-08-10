package vesselreconstruction.ellipse;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable result of automatic red scale-bar detection.
 */
public final class ScaleBarDetection {

    public static final String DETECTOR_VERSION = "1";

    private final boolean successful;
    private final ScaleBarDetectionConfidence confidence;
    private final double score;
    private final ScaleBarDetectionMethod method;
    private final double leftTickCenterX;
    private final double leftTickCenterY;
    private final double rightTickCenterX;
    private final double rightTickCenterY;
    private final double measuredLengthPx;
    private final double outerComponentWidthPx;
    private final double shaftAngleDegrees;
    private final double shaftContinuity;
    private final Rectangle candidateBoundsOriginal;
    private final Rectangle fullResolutionSearchBounds;
    private final Rectangle fullResolutionComponentBounds;
    private final String failureReason;
    private final List<ScaleBarDetectionCandidate> candidates;

    private ScaleBarDetection(
            final boolean successful,
            final ScaleBarDetectionConfidence confidence,
            final double score,
            final ScaleBarDetectionMethod method,
            final double leftTickCenterX,
            final double leftTickCenterY,
            final double rightTickCenterX,
            final double rightTickCenterY,
            final double measuredLengthPx,
            final double outerComponentWidthPx,
            final double shaftAngleDegrees,
            final double shaftContinuity,
            final Rectangle candidateBoundsOriginal,
            final Rectangle fullResolutionSearchBounds,
            final Rectangle fullResolutionComponentBounds,
            final String failureReason,
            final List<ScaleBarDetectionCandidate> candidates) {
        this.successful = successful;
        this.confidence = confidence;
        this.score = score;
        this.method = method;
        this.leftTickCenterX = leftTickCenterX;
        this.leftTickCenterY = leftTickCenterY;
        this.rightTickCenterX = rightTickCenterX;
        this.rightTickCenterY = rightTickCenterY;
        this.measuredLengthPx = measuredLengthPx;
        this.outerComponentWidthPx = outerComponentWidthPx;
        this.shaftAngleDegrees = shaftAngleDegrees;
        this.shaftContinuity = shaftContinuity;
        this.candidateBoundsOriginal = candidateBoundsOriginal == null
                ? null : new Rectangle(candidateBoundsOriginal);
        this.fullResolutionSearchBounds = fullResolutionSearchBounds == null
                ? null : new Rectangle(fullResolutionSearchBounds);
        this.fullResolutionComponentBounds = fullResolutionComponentBounds == null
                ? null : new Rectangle(fullResolutionComponentBounds);
        this.failureReason = failureReason == null ? "" : failureReason;
        this.candidates = candidates == null
                ? Collections.<ScaleBarDetectionCandidate>emptyList()
                : Collections.unmodifiableList(candidates);
    }

    public static ScaleBarDetection failed(
            final String reason,
            final List<ScaleBarDetectionCandidate> candidates) {
        return new ScaleBarDetection(
                false,
                ScaleBarDetectionConfidence.FAILED,
                0.0,
                ScaleBarDetectionMethod.NONE,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                0.0,
                null,
                null,
                null,
                reason,
                candidates);
    }

    public static ScaleBarDetection measured(
            final ScaleBarDetectionConfidence confidence,
            final double score,
            final ScaleBarDetectionMethod method,
            final double leftTickCenterX,
            final double leftTickCenterY,
            final double rightTickCenterX,
            final double rightTickCenterY,
            final double measuredLengthPx,
            final double outerComponentWidthPx,
            final double shaftAngleDegrees,
            final double shaftContinuity,
            final Rectangle candidateBoundsOriginal,
            final Rectangle fullResolutionSearchBounds,
            final Rectangle fullResolutionComponentBounds,
            final List<ScaleBarDetectionCandidate> candidates) {
        return new ScaleBarDetection(
                true,
                confidence,
                score,
                method,
                leftTickCenterX,
                leftTickCenterY,
                rightTickCenterX,
                rightTickCenterY,
                measuredLengthPx,
                outerComponentWidthPx,
                shaftAngleDegrees,
                shaftContinuity,
                candidateBoundsOriginal,
                fullResolutionSearchBounds,
                fullResolutionComponentBounds,
                "",
                candidates);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean isAcceptableForConfirmation() {
        return successful
                && (confidence == ScaleBarDetectionConfidence.HIGH
                || confidence == ScaleBarDetectionConfidence.MEDIUM);
    }

    public ScaleBarDetectionConfidence getConfidence() {
        return confidence;
    }

    public double getScore() {
        return score;
    }

    public ScaleBarDetectionMethod getMethod() {
        return method;
    }

    public double getLeftTickCenterX() {
        return leftTickCenterX;
    }

    public double getLeftTickCenterY() {
        return leftTickCenterY;
    }

    public double getRightTickCenterX() {
        return rightTickCenterX;
    }

    public double getRightTickCenterY() {
        return rightTickCenterY;
    }

    public double getMeasuredLengthPx() {
        return measuredLengthPx;
    }

    public double getOuterComponentWidthPx() {
        return outerComponentWidthPx;
    }

    public double getShaftAngleDegrees() {
        return shaftAngleDegrees;
    }

    public double getShaftContinuity() {
        return shaftContinuity;
    }

    public Rectangle getCandidateBoundsOriginal() {
        return candidateBoundsOriginal == null ? null : new Rectangle(candidateBoundsOriginal);
    }

    public Rectangle getFullResolutionSearchBounds() {
        return fullResolutionSearchBounds == null
                ? null : new Rectangle(fullResolutionSearchBounds);
    }

    public Rectangle getFullResolutionComponentBounds() {
        return fullResolutionComponentBounds == null
                ? null : new Rectangle(fullResolutionComponentBounds);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<ScaleBarDetectionCandidate> getCandidates() {
        return candidates;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "ScaleBarDetection{success=%s,confidence=%s,score=%.4f,method=%s,"
                        + "length=%.6f,outerWidth=%.6f,reason=%s}",
                Boolean.valueOf(successful),
                confidence.name(),
                Double.valueOf(score),
                method.name(),
                Double.valueOf(measuredLengthPx),
                Double.valueOf(outerComponentWidthPx),
                failureReason);
    }
}
