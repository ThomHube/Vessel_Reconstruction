package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of evaluating whether a single interval-associated
 * proposed-fill component qualifies for a diagnostic reconstructed image
 * copy.
 *
 * <p>Never stores a mutable ImageJ object. Only the coordinates and counts
 * required to describe a candidate pixel change are retained.</p>
 */
public final class DiagnosticReconstructionCandidate {

    public enum Decision {
        READY_FOR_DIAGNOSTIC_COPY,
        INSUFFICIENT_ARTIFACT_EVIDENCE,
        INVALID_STRUCTURAL_ASSOCIATION,
        NON_WOOD_CANDIDATE_PIXELS,
        SOURCE_PIXEL_VALUE_MISMATCH,
        OTHER_LUMEN_OVERLAP,
        UNKNOWN
    }

    private final Decision decision;
    private final String decisionReason;
    private final boolean readyForDiagnosticCopy;
    private final List<Point> pixelsToChange;
    private final int pixelCount;
    private final int originalWoodPixelCount;
    private final int originalRayPixelCount;
    private final int originalLumenPixelCount;
    private final int originalBackgroundPixelCount;
    private final int originalOtherPixelCount;
    private final int sourceWidth;
    private final int sourceHeight;
    private final int sourceChecksumBeforeEvaluation;
    private final int sourceChecksumAfterEvaluation;
    private final boolean sourceUnchangedDuringEvaluation;
    private final int originalSelectedLumenArea;
    private final int diagnosticReconstructedArea;
    private final int diagnosticReconstructedPerimeter;
    private final double diagnosticReconstructedCircularity;
    private final double diagnosticCircularityChange;
    private final double addedAreaFraction;
    private final int primaryAssociatedComponentId;
    private final int primaryAssociatedComponentArea;

    public DiagnosticReconstructionCandidate(
            final Decision decision,
            final String decisionReason,
            final boolean readyForDiagnosticCopy,
            final List<Point> pixelsToChange,
            final int pixelCount,
            final int originalWoodPixelCount,
            final int originalRayPixelCount,
            final int originalLumenPixelCount,
            final int originalBackgroundPixelCount,
            final int originalOtherPixelCount,
            final int sourceWidth,
            final int sourceHeight,
            final int sourceChecksumBeforeEvaluation,
            final int sourceChecksumAfterEvaluation,
            final boolean sourceUnchangedDuringEvaluation,
            final int originalSelectedLumenArea,
            final int diagnosticReconstructedArea,
            final int diagnosticReconstructedPerimeter,
            final double diagnosticReconstructedCircularity,
            final double diagnosticCircularityChange,
            final double addedAreaFraction,
            final int primaryAssociatedComponentId,
            final int primaryAssociatedComponentArea) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (decisionReason == null || decisionReason.isEmpty()) {
            throw new IllegalArgumentException("decisionReason must not be null or empty");
        }
        if (pixelsToChange == null) {
            throw new IllegalArgumentException("pixelsToChange must not be null");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException(
                    "Source dimensions must be positive: " + sourceWidth + "x" + sourceHeight);
        }
        requireNonNegative("pixelCount", pixelCount);
        requireNonNegative("originalWoodPixelCount", originalWoodPixelCount);
        requireNonNegative("originalRayPixelCount", originalRayPixelCount);
        requireNonNegative("originalLumenPixelCount", originalLumenPixelCount);
        requireNonNegative("originalBackgroundPixelCount", originalBackgroundPixelCount);
        requireNonNegative("originalOtherPixelCount", originalOtherPixelCount);
        requireNonNegative("originalSelectedLumenArea", originalSelectedLumenArea);
        requireNonNegative("diagnosticReconstructedArea", diagnosticReconstructedArea);
        requireNonNegative("diagnosticReconstructedPerimeter", diagnosticReconstructedPerimeter);
        requireNonNegative("primaryAssociatedComponentId", primaryAssociatedComponentId);
        requireNonNegative("primaryAssociatedComponentArea", primaryAssociatedComponentArea);
        requireFinite("diagnosticReconstructedCircularity", diagnosticReconstructedCircularity);
        requireFinite("diagnosticCircularityChange", diagnosticCircularityChange);
        requireFinite("addedAreaFraction", addedAreaFraction);
        if (addedAreaFraction < 0.0) {
            throw new IllegalArgumentException(
                    "addedAreaFraction must be non-negative: " + addedAreaFraction);
        }

        final List<Point> copiedPixels =
                copySortedPointsInBounds(pixelsToChange, sourceWidth, sourceHeight);

        if (pixelCount != copiedPixels.size()) {
            throw new IllegalArgumentException(
                    "pixelCount must equal pixelsToChange.size(): "
                            + pixelCount + " vs " + copiedPixels.size());
        }
        final int classCountSum = originalWoodPixelCount + originalRayPixelCount
                + originalLumenPixelCount + originalBackgroundPixelCount
                + originalOtherPixelCount;
        if (classCountSum != pixelCount) {
            throw new IllegalArgumentException(
                    "Original class counts must sum to pixelCount: "
                            + classCountSum + " vs " + pixelCount);
        }

        final boolean expectedReady = decision == Decision.READY_FOR_DIAGNOSTIC_COPY;
        if (readyForDiagnosticCopy != expectedReady) {
            throw new IllegalArgumentException(
                    "readyForDiagnosticCopy must equal decision == READY_FOR_DIAGNOSTIC_COPY");
        }

        if (expectedReady) {
            if (pixelCount <= 0) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires pixelCount > 0");
            }
            if (originalWoodPixelCount != pixelCount) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires every candidate pixel to be Wood");
            }
            if (originalRayPixelCount != 0 || originalLumenPixelCount != 0
                    || originalBackgroundPixelCount != 0 || originalOtherPixelCount != 0) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires all non-Wood candidate counts to be zero");
            }
            if (!sourceUnchangedDuringEvaluation) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires sourceUnchangedDuringEvaluation");
            }
            if (primaryAssociatedComponentId <= 0) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires a positive primaryAssociatedComponentId");
            }
            if (primaryAssociatedComponentArea <= 0) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires a positive primaryAssociatedComponentArea");
            }
            if (primaryAssociatedComponentArea != pixelCount) {
                throw new IllegalArgumentException(
                        "READY_FOR_DIAGNOSTIC_COPY requires primaryAssociatedComponentArea"
                                + " to equal pixelCount");
            }
        } else if (!copiedPixels.isEmpty()) {
            throw new IllegalArgumentException(
                    "A non-ready candidate must have an empty pixelsToChange list");
        }

        this.decision = decision;
        this.decisionReason = decisionReason;
        this.readyForDiagnosticCopy = readyForDiagnosticCopy;
        this.pixelsToChange = Collections.unmodifiableList(copiedPixels);
        this.pixelCount = pixelCount;
        this.originalWoodPixelCount = originalWoodPixelCount;
        this.originalRayPixelCount = originalRayPixelCount;
        this.originalLumenPixelCount = originalLumenPixelCount;
        this.originalBackgroundPixelCount = originalBackgroundPixelCount;
        this.originalOtherPixelCount = originalOtherPixelCount;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.sourceChecksumBeforeEvaluation = sourceChecksumBeforeEvaluation;
        this.sourceChecksumAfterEvaluation = sourceChecksumAfterEvaluation;
        this.sourceUnchangedDuringEvaluation = sourceUnchangedDuringEvaluation;
        this.originalSelectedLumenArea = originalSelectedLumenArea;
        this.diagnosticReconstructedArea = diagnosticReconstructedArea;
        this.diagnosticReconstructedPerimeter = diagnosticReconstructedPerimeter;
        this.diagnosticReconstructedCircularity = diagnosticReconstructedCircularity;
        this.diagnosticCircularityChange = diagnosticCircularityChange;
        this.addedAreaFraction = addedAreaFraction;
        this.primaryAssociatedComponentId = primaryAssociatedComponentId;
        this.primaryAssociatedComponentArea = primaryAssociatedComponentArea;
    }

    private static List<Point> copySortedPointsInBounds(
            final List<Point> source, final int width, final int height) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            if (point == null) {
                throw new IllegalArgumentException("pixelsToChange must not contain null entries");
            }
            if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) {
                throw new IllegalArgumentException(
                        "pixelsToChange coordinate out of image bounds: ("
                                + point.x + ", " + point.y + ")");
            }
            if (i > 0) {
                final Point prev = source.get(i - 1);
                if (point.y < prev.y || (point.y == prev.y && point.x < prev.x)) {
                    throw new IllegalArgumentException(
                            "pixelsToChange must be sorted by Y ascending, then X ascending");
                }
            }
            copy.add(new Point(point.x, point.y));
        }
        return copy;
    }

    private static List<Point> defensivePoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            copy.add(new Point(point.x, point.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static void requireFinite(final String name, final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    public Decision getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public boolean isReadyForDiagnosticCopy() {
        return readyForDiagnosticCopy;
    }

    public List<Point> getPixelsToChange() {
        return defensivePoints(pixelsToChange);
    }

    public int getPixelCount() {
        return pixelCount;
    }

    public int getOriginalWoodPixelCount() {
        return originalWoodPixelCount;
    }

    public int getOriginalRayPixelCount() {
        return originalRayPixelCount;
    }

    public int getOriginalLumenPixelCount() {
        return originalLumenPixelCount;
    }

    public int getOriginalBackgroundPixelCount() {
        return originalBackgroundPixelCount;
    }

    public int getOriginalOtherPixelCount() {
        return originalOtherPixelCount;
    }

    public int getSourceWidth() {
        return sourceWidth;
    }

    public int getSourceHeight() {
        return sourceHeight;
    }

    public int getSourceChecksumBeforeEvaluation() {
        return sourceChecksumBeforeEvaluation;
    }

    public int getSourceChecksumAfterEvaluation() {
        return sourceChecksumAfterEvaluation;
    }

    public boolean isSourceUnchangedDuringEvaluation() {
        return sourceUnchangedDuringEvaluation;
    }

    public int getOriginalSelectedLumenArea() {
        return originalSelectedLumenArea;
    }

    public int getDiagnosticReconstructedArea() {
        return diagnosticReconstructedArea;
    }

    public int getDiagnosticReconstructedPerimeter() {
        return diagnosticReconstructedPerimeter;
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

    public int getPrimaryAssociatedComponentId() {
        return primaryAssociatedComponentId;
    }

    public int getPrimaryAssociatedComponentArea() {
        return primaryAssociatedComponentArea;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DiagnosticReconstructionCandidate)) {
            return false;
        }
        final DiagnosticReconstructionCandidate other = (DiagnosticReconstructionCandidate) obj;
        return readyForDiagnosticCopy == other.readyForDiagnosticCopy
                && pixelCount == other.pixelCount
                && originalWoodPixelCount == other.originalWoodPixelCount
                && originalRayPixelCount == other.originalRayPixelCount
                && originalLumenPixelCount == other.originalLumenPixelCount
                && originalBackgroundPixelCount == other.originalBackgroundPixelCount
                && originalOtherPixelCount == other.originalOtherPixelCount
                && sourceWidth == other.sourceWidth
                && sourceHeight == other.sourceHeight
                && sourceChecksumBeforeEvaluation == other.sourceChecksumBeforeEvaluation
                && sourceChecksumAfterEvaluation == other.sourceChecksumAfterEvaluation
                && sourceUnchangedDuringEvaluation == other.sourceUnchangedDuringEvaluation
                && originalSelectedLumenArea == other.originalSelectedLumenArea
                && diagnosticReconstructedArea == other.diagnosticReconstructedArea
                && diagnosticReconstructedPerimeter == other.diagnosticReconstructedPerimeter
                && primaryAssociatedComponentId == other.primaryAssociatedComponentId
                && primaryAssociatedComponentArea == other.primaryAssociatedComponentArea
                && Double.compare(diagnosticReconstructedCircularity,
                        other.diagnosticReconstructedCircularity) == 0
                && Double.compare(diagnosticCircularityChange,
                        other.diagnosticCircularityChange) == 0
                && Double.compare(addedAreaFraction, other.addedAreaFraction) == 0
                && decision == other.decision
                && Objects.equals(decisionReason, other.decisionReason)
                && Objects.equals(pixelsToChange, other.pixelsToChange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                decision,
                decisionReason,
                Boolean.valueOf(readyForDiagnosticCopy),
                pixelsToChange,
                Integer.valueOf(pixelCount),
                Integer.valueOf(originalWoodPixelCount),
                Integer.valueOf(originalRayPixelCount),
                Integer.valueOf(originalLumenPixelCount),
                Integer.valueOf(originalBackgroundPixelCount),
                Integer.valueOf(originalOtherPixelCount),
                Integer.valueOf(sourceWidth),
                Integer.valueOf(sourceHeight),
                Integer.valueOf(sourceChecksumBeforeEvaluation),
                Integer.valueOf(sourceChecksumAfterEvaluation),
                Boolean.valueOf(sourceUnchangedDuringEvaluation),
                Integer.valueOf(originalSelectedLumenArea),
                Integer.valueOf(diagnosticReconstructedArea),
                Integer.valueOf(diagnosticReconstructedPerimeter),
                Double.valueOf(diagnosticReconstructedCircularity),
                Double.valueOf(diagnosticCircularityChange),
                Double.valueOf(addedAreaFraction),
                Integer.valueOf(primaryAssociatedComponentId),
                Integer.valueOf(primaryAssociatedComponentArea));
    }

    @Override
    public String toString() {
        return "DiagnosticReconstructionCandidate{"
                + "decision=" + decision
                + ", ready=" + readyForDiagnosticCopy
                + ", reason=" + decisionReason
                + ", pixelCount=" + pixelCount
                + ", primaryComponentId=" + primaryAssociatedComponentId
                + ", primaryComponentArea=" + primaryAssociatedComponentArea
                + ", reconstructedArea=" + diagnosticReconstructedArea
                + ", reconstructedCircularity=" + diagnosticReconstructedCircularity
                + '}';
    }
}
