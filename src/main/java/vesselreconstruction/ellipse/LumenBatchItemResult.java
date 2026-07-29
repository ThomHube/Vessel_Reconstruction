package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-Lumen batch diagnostic result.
 */
public final class LumenBatchItemResult {

    public enum Status {
        BELOW_MINIMUM_AREA,
        BORDER_TOUCHING_SKIPPED,
        INITIAL_ANALYSIS_FAILED,
        UNSUPPORTED_BOUNDARY,
        NO_INTERVAL_SELECTED,
        INSUFFICIENT_ARTIFACT_EVIDENCE,
        NO_INTERVAL_ASSOCIATED_FILL,
        AMBIGUOUS_INTERVAL_ASSOCIATION,
        UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
        OTHER_LUMEN_OVERLAP,
        NON_WOOD_CANDIDATE_PIXELS,
        INVALID_STRUCTURAL_ASSOCIATION,
        SOURCE_PIXEL_VALUE_MISMATCH,
        CANDIDATE_CONFLICT,
        RECONSTRUCTED_SINGLE_ARTIFACT,
        PROCESSING_ERROR
    }

    private final int componentId;
    private final int sourceArea;
    private final Rectangle sourceBounds;
    private final boolean touchesImageBorder;

    private final Status status;
    private final String reason;
    private final boolean reconstructed;

    private final List<Point> candidatePixels;
    private final int candidatePixelCount;

    private final boolean intervalSelected;
    private final String intervalSelectionReason;
    private final String artifactEvidenceDecision;
    private final String reconstructionCandidateDecision;

    private final int boundaryEdgeCount;
    private final int intervalEdgeCount;
    private final int corePointCount;

    private final double minimumDepthSigma;
    private final double meanDepthSigma;
    private final double coreFraction;
    private final double normalizedIntegratedInwardExcess;
    private final double retainedRmsImprovementFraction;

    private final int intervalAssociatedComponentCount;
    private final int matchedExteriorSeedPixelCount;
    private final int unmatchedExteriorSeedPixelCount;
    private final int otherLumenInsideRefittedEllipseCount;

    private final double originalCircularity;
    private final double diagnosticReconstructedCircularity;
    private final double diagnosticCircularityChange;
    private final double addedAreaFraction;

    private final long processingTimeNanoseconds;

    public LumenBatchItemResult(
            final int componentId,
            final int sourceArea,
            final Rectangle sourceBounds,
            final boolean touchesImageBorder,
            final Status status,
            final String reason,
            final List<Point> candidatePixels,
            final boolean intervalSelected,
            final String intervalSelectionReason,
            final String artifactEvidenceDecision,
            final String reconstructionCandidateDecision,
            final int boundaryEdgeCount,
            final int intervalEdgeCount,
            final int corePointCount,
            final double minimumDepthSigma,
            final double meanDepthSigma,
            final double coreFraction,
            final double normalizedIntegratedInwardExcess,
            final double retainedRmsImprovementFraction,
            final int intervalAssociatedComponentCount,
            final int matchedExteriorSeedPixelCount,
            final int unmatchedExteriorSeedPixelCount,
            final int otherLumenInsideRefittedEllipseCount,
            final double originalCircularity,
            final double diagnosticReconstructedCircularity,
            final double diagnosticCircularityChange,
            final double addedAreaFraction,
            final long processingTimeNanoseconds) {
        if (componentId <= 0) {
            throw new IllegalArgumentException("componentId must be positive: " + componentId);
        }
        if (sourceArea <= 0) {
            throw new IllegalArgumentException("sourceArea must be positive: " + sourceArea);
        }
        if (sourceBounds == null) {
            throw new IllegalArgumentException("sourceBounds must not be null");
        }
        if (sourceBounds.width <= 0 || sourceBounds.height <= 0) {
            throw new IllegalArgumentException("sourceBounds must be valid: " + sourceBounds);
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be null or empty");
        }
        if (processingTimeNanoseconds < 0L) {
            throw new IllegalArgumentException(
                    "processingTimeNanoseconds must be non-negative: "
                            + processingTimeNanoseconds);
        }

        final List<Point> sortedCandidates = normalizeCandidates(candidatePixels);
        final boolean reconstructedFlag = status == Status.RECONSTRUCTED_SINGLE_ARTIFACT;
        if (reconstructedFlag && sortedCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "RECONSTRUCTED_SINGLE_ARTIFACT requires a non-empty candidate list");
        }
        if (!reconstructedFlag && !sortedCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "non-reconstructed status requires an empty candidate list: " + status);
        }

        requireFiniteOrNaN("minimumDepthSigma", minimumDepthSigma);
        requireFiniteOrNaN("meanDepthSigma", meanDepthSigma);
        requireFiniteOrNaN("coreFraction", coreFraction);
        requireFiniteOrNaN("normalizedIntegratedInwardExcess",
                normalizedIntegratedInwardExcess);
        requireFiniteOrNaN("retainedRmsImprovementFraction",
                retainedRmsImprovementFraction);
        requireFiniteOrNaN("originalCircularity", originalCircularity);
        requireFiniteOrNaN("diagnosticReconstructedCircularity",
                diagnosticReconstructedCircularity);
        requireFiniteOrNaN("diagnosticCircularityChange", diagnosticCircularityChange);
        requireFiniteOrNaN("addedAreaFraction", addedAreaFraction);

        this.componentId = componentId;
        this.sourceArea = sourceArea;
        this.sourceBounds = new Rectangle(sourceBounds);
        this.touchesImageBorder = touchesImageBorder;
        this.status = status;
        this.reason = reason;
        this.reconstructed = reconstructedFlag;
        this.candidatePixels = Collections.unmodifiableList(sortedCandidates);
        this.candidatePixelCount = sortedCandidates.size();
        this.intervalSelected = intervalSelected;
        this.intervalSelectionReason = intervalSelectionReason == null
                ? "" : intervalSelectionReason;
        this.artifactEvidenceDecision = artifactEvidenceDecision == null
                ? "" : artifactEvidenceDecision;
        this.reconstructionCandidateDecision = reconstructionCandidateDecision == null
                ? "" : reconstructionCandidateDecision;
        this.boundaryEdgeCount = boundaryEdgeCount;
        this.intervalEdgeCount = intervalEdgeCount;
        this.corePointCount = corePointCount;
        this.minimumDepthSigma = minimumDepthSigma;
        this.meanDepthSigma = meanDepthSigma;
        this.coreFraction = coreFraction;
        this.normalizedIntegratedInwardExcess = normalizedIntegratedInwardExcess;
        this.retainedRmsImprovementFraction = retainedRmsImprovementFraction;
        this.intervalAssociatedComponentCount = intervalAssociatedComponentCount;
        this.matchedExteriorSeedPixelCount = matchedExteriorSeedPixelCount;
        this.unmatchedExteriorSeedPixelCount = unmatchedExteriorSeedPixelCount;
        this.otherLumenInsideRefittedEllipseCount = otherLumenInsideRefittedEllipseCount;
        this.originalCircularity = originalCircularity;
        this.diagnosticReconstructedCircularity = diagnosticReconstructedCircularity;
        this.diagnosticCircularityChange = diagnosticCircularityChange;
        this.addedAreaFraction = addedAreaFraction;
        this.processingTimeNanoseconds = processingTimeNanoseconds;
    }

    public static LumenBatchItemResult skipped(
            final LumenComponentDescriptor component,
            final Status status,
            final String reason,
            final long processingTimeNanoseconds) {
        return new LumenBatchItemResult(
                component.getComponentId(),
                component.getArea(),
                component.getBounds(),
                component.isTouchesImageBorder(),
                status,
                reason,
                Collections.<Point>emptyList(),
                false,
                "",
                "",
                "",
                -1,
                -1,
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                -1,
                -1,
                -1,
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                processingTimeNanoseconds);
    }

    public static LumenBatchItemResult withStatus(
            final LumenBatchItemResult source,
            final Status status,
            final String reason,
            final List<Point> candidatePixels) {
        return new LumenBatchItemResult(
                source.componentId,
                source.sourceArea,
                source.sourceBounds,
                source.touchesImageBorder,
                status,
                reason,
                candidatePixels,
                source.intervalSelected,
                source.intervalSelectionReason,
                source.artifactEvidenceDecision,
                source.reconstructionCandidateDecision,
                source.boundaryEdgeCount,
                source.intervalEdgeCount,
                source.corePointCount,
                source.minimumDepthSigma,
                source.meanDepthSigma,
                source.coreFraction,
                source.normalizedIntegratedInwardExcess,
                source.retainedRmsImprovementFraction,
                source.intervalAssociatedComponentCount,
                source.matchedExteriorSeedPixelCount,
                source.unmatchedExteriorSeedPixelCount,
                source.otherLumenInsideRefittedEllipseCount,
                source.originalCircularity,
                source.diagnosticReconstructedCircularity,
                source.diagnosticCircularityChange,
                source.addedAreaFraction,
                source.processingTimeNanoseconds);
    }

    public int getComponentId() {
        return componentId;
    }

    public int getSourceArea() {
        return sourceArea;
    }

    public Rectangle getSourceBounds() {
        return new Rectangle(sourceBounds);
    }

    public boolean isTouchesImageBorder() {
        return touchesImageBorder;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public boolean isReconstructed() {
        return reconstructed;
    }

    public List<Point> getCandidatePixels() {
        return defensivePoints(candidatePixels);
    }

    public int getCandidatePixelCount() {
        return candidatePixelCount;
    }

    public boolean isIntervalSelected() {
        return intervalSelected;
    }

    public String getIntervalSelectionReason() {
        return intervalSelectionReason;
    }

    public String getArtifactEvidenceDecision() {
        return artifactEvidenceDecision;
    }

    public String getReconstructionCandidateDecision() {
        return reconstructionCandidateDecision;
    }

    public int getBoundaryEdgeCount() {
        return boundaryEdgeCount;
    }

    public int getIntervalEdgeCount() {
        return intervalEdgeCount;
    }

    public int getCorePointCount() {
        return corePointCount;
    }

    public double getMinimumDepthSigma() {
        return minimumDepthSigma;
    }

    public double getMeanDepthSigma() {
        return meanDepthSigma;
    }

    public double getCoreFraction() {
        return coreFraction;
    }

    public double getNormalizedIntegratedInwardExcess() {
        return normalizedIntegratedInwardExcess;
    }

    public double getRetainedRmsImprovementFraction() {
        return retainedRmsImprovementFraction;
    }

    public int getIntervalAssociatedComponentCount() {
        return intervalAssociatedComponentCount;
    }

    public int getMatchedExteriorSeedPixelCount() {
        return matchedExteriorSeedPixelCount;
    }

    public int getUnmatchedExteriorSeedPixelCount() {
        return unmatchedExteriorSeedPixelCount;
    }

    public int getOtherLumenInsideRefittedEllipseCount() {
        return otherLumenInsideRefittedEllipseCount;
    }

    public double getOriginalCircularity() {
        return originalCircularity;
    }

    public double getDiagnosticReconstructedCircularity() {
        return diagnosticReconstructedCircularity;
    }

    public double getDiagnosticCircularityChange() {
        return diagnosticCircularityChange;
    }

    public double getAddedAreaFraction() {
        return addedAreaFraction;
    }

    public long getProcessingTimeNanoseconds() {
        return processingTimeNanoseconds;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LumenBatchItemResult)) {
            return false;
        }
        final LumenBatchItemResult other = (LumenBatchItemResult) obj;
        return componentId == other.componentId
                && sourceArea == other.sourceArea
                && sourceBounds.equals(other.sourceBounds)
                && touchesImageBorder == other.touchesImageBorder
                && status == other.status
                && reason.equals(other.reason)
                && reconstructed == other.reconstructed
                && candidatePixelCount == other.candidatePixelCount
                && pixelsEqual(candidatePixels, other.candidatePixels)
                && intervalSelected == other.intervalSelected
                && intervalSelectionReason.equals(other.intervalSelectionReason)
                && artifactEvidenceDecision.equals(other.artifactEvidenceDecision)
                && reconstructionCandidateDecision.equals(other.reconstructionCandidateDecision)
                && boundaryEdgeCount == other.boundaryEdgeCount
                && intervalEdgeCount == other.intervalEdgeCount
                && corePointCount == other.corePointCount
                && doubleEquals(minimumDepthSigma, other.minimumDepthSigma)
                && doubleEquals(meanDepthSigma, other.meanDepthSigma)
                && doubleEquals(coreFraction, other.coreFraction)
                && doubleEquals(normalizedIntegratedInwardExcess,
                other.normalizedIntegratedInwardExcess)
                && doubleEquals(retainedRmsImprovementFraction,
                other.retainedRmsImprovementFraction)
                && intervalAssociatedComponentCount == other.intervalAssociatedComponentCount
                && matchedExteriorSeedPixelCount == other.matchedExteriorSeedPixelCount
                && unmatchedExteriorSeedPixelCount == other.unmatchedExteriorSeedPixelCount
                && otherLumenInsideRefittedEllipseCount
                == other.otherLumenInsideRefittedEllipseCount
                && doubleEquals(originalCircularity, other.originalCircularity)
                && doubleEquals(diagnosticReconstructedCircularity,
                other.diagnosticReconstructedCircularity)
                && doubleEquals(diagnosticCircularityChange, other.diagnosticCircularityChange)
                && doubleEquals(addedAreaFraction, other.addedAreaFraction)
                && processingTimeNanoseconds == other.processingTimeNanoseconds;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                Integer.valueOf(componentId),
                Integer.valueOf(sourceArea),
                sourceBounds,
                Boolean.valueOf(touchesImageBorder),
                status,
                reason,
                Boolean.valueOf(reconstructed),
                Integer.valueOf(candidatePixelCount),
                Boolean.valueOf(intervalSelected),
                intervalSelectionReason,
                artifactEvidenceDecision,
                reconstructionCandidateDecision,
                Integer.valueOf(boundaryEdgeCount),
                Integer.valueOf(intervalEdgeCount),
                Integer.valueOf(corePointCount),
                Double.valueOf(minimumDepthSigma),
                Double.valueOf(meanDepthSigma),
                Double.valueOf(coreFraction),
                Double.valueOf(normalizedIntegratedInwardExcess),
                Double.valueOf(retainedRmsImprovementFraction),
                Integer.valueOf(intervalAssociatedComponentCount),
                Integer.valueOf(matchedExteriorSeedPixelCount),
                Integer.valueOf(unmatchedExteriorSeedPixelCount),
                Integer.valueOf(otherLumenInsideRefittedEllipseCount),
                Double.valueOf(originalCircularity),
                Double.valueOf(diagnosticReconstructedCircularity),
                Double.valueOf(diagnosticCircularityChange),
                Double.valueOf(addedAreaFraction),
                Long.valueOf(processingTimeNanoseconds));
        for (int i = 0; i < candidatePixels.size(); i++) {
            final Point p = candidatePixels.get(i);
            result = 31 * result + p.x;
            result = 31 * result + p.y;
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "LumenBatchItemResult{id=%d,area=%d,status=%s,reconstructed=%s,candidates=%d}",
                Integer.valueOf(componentId),
                Integer.valueOf(sourceArea),
                status,
                Boolean.valueOf(reconstructed),
                Integer.valueOf(candidatePixelCount));
    }

    private static List<Point> normalizeCandidates(final List<Point> candidatePixels) {
        if (candidatePixels == null || candidatePixels.isEmpty()) {
            return new ArrayList<Point>(0);
        }
        final List<Point> sorted = new ArrayList<Point>(candidatePixels.size());
        final Set<Long> seen = new HashSet<Long>(candidatePixels.size() * 2);
        for (int i = 0; i < candidatePixels.size(); i++) {
            final Point raw = candidatePixels.get(i);
            if (raw == null) {
                throw new IllegalArgumentException("candidatePixels must not contain null");
            }
            final Long key = Long.valueOf((((long) raw.x) << 32) ^ (raw.y & 0xffffffffL));
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "duplicate candidate pixel: (" + raw.x + "," + raw.y + ")");
            }
            sorted.add(new Point(raw.x, raw.y));
        }
        Collections.sort(sorted, POINT_ORDER);
        return sorted;
    }

    private static List<Point> defensivePoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point p = source.get(i);
            copy.add(new Point(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static boolean pixelsEqual(final List<Point> a, final List<Point> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            final Point pa = a.get(i);
            final Point pb = b.get(i);
            if (pa.x != pb.x || pa.y != pb.y) {
                return false;
            }
        }
        return true;
    }

    private static void requireFiniteOrNaN(final String name, final double value) {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite or NaN: " + value);
        }
    }

    private static boolean doubleEquals(final double a, final double b) {
        return Double.compare(a, b) == 0;
    }

    private static final Comparator<Point> POINT_ORDER = new Comparator<Point>() {
        @Override
        public int compare(final Point a, final Point b) {
            if (a.y != b.y) {
                return Integer.compare(a.y, b.y);
            }
            return Integer.compare(a.x, b.x);
        }
    };
}
