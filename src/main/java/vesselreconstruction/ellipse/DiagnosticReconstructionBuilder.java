package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic builder of a diagnostic (non-production) reconstructed image
 * copy.
 *
 * <p>Only ever converts raw {@link ClassifierLabels#WOOD_RESULT_VALUE} pixels
 * belonging to the single primary interval-associated proposed-fill
 * component to raw {@link ClassifierLabels#LUMEN_RESULT_VALUE}, and only when
 * conservative artifact evidence and structural association checks all
 * pass. Never mutates the supplied source {@link ByteProcessor}. Holds no
 * mutable static state.</p>
 */
public final class DiagnosticReconstructionBuilder {

    private static final double CIRCULARITY_OVERSHOOT = 1.0e-9;

    private static final int[] CARDINAL_DX = {0, 1, 0, -1};
    private static final int[] CARDINAL_DY = {-1, 0, 1, 0};

    public DiagnosticReconstructionCandidate evaluate(
            final ByteProcessor classifiedProcessor,
            final ArtifactEvidenceAssessment evidenceAssessment,
            final ProposedFillComponentAnalysisResult componentResult) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (evidenceAssessment == null) {
            throw new IllegalArgumentException("evidenceAssessment must not be null");
        }
        if (componentResult == null) {
            throw new IllegalArgumentException("componentResult must not be null");
        }

        final RefittedEllipseFillPreviewResult wholePreview =
                componentResult.getWholeEllipsePreview();
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width != wholePreview.getImageWidth() || height != wholePreview.getImageHeight()) {
            throw new IllegalArgumentException(
                    "classifiedProcessor dimensions must match componentResult: processor="
                            + width + "x" + height + ", componentResult="
                            + wholePreview.getImageWidth() + "x" + wholePreview.getImageHeight());
        }

        final byte[] sourcePixels = (byte[]) classifiedProcessor.getPixels();
        if (sourcePixels == null || sourcePixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }
        final int checksumBefore = computeChecksum(sourcePixels);
        final int originalSelectedLumenArea = wholePreview.getSelectedObservedLumenArea();

        if (!evidenceAssessment.isStrongArtifactEvidence()) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE,
                    "Evidence assessment is not STRONG_ARTIFACT_EVIDENCE: "
                            + evidenceAssessment.getDecision()
                            + " (" + evidenceAssessment.getDecisionReason() + ")",
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final int associatedCount = componentResult.getIntervalAssociatedComponentCount();
        final ProposedFillComponent primary =
                componentResult.getPrimaryIntervalAssociatedComponent();
        if (associatedCount != 1
                || !componentResult.isExactlyOneIntervalAssociatedComponent()
                || primary == null) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                    "Exactly one interval-associated proposed-fill component is required:"
                            + " associatedCount=" + associatedCount
                            + ", exactlyOne=" + componentResult.isExactlyOneIntervalAssociatedComponent()
                            + ", primaryPresent=" + (primary != null),
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final int unmatchedSeeds = componentResult.getUnmatchedIntervalExteriorSeedPixelCount();
        final int matchedSeeds = componentResult.getMatchedIntervalExteriorSeedPixelCount();
        if (unmatchedSeeds != 0 || matchedSeeds <= 0) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                    "Interval exterior seeds are incomplete: matched=" + matchedSeeds
                            + ", unmatched=" + unmatchedSeeds,
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final int otherLumenCount = wholePreview.getOtherLumenInsideRefittedEllipseCount();
        if (otherLumenCount > 0) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.OTHER_LUMEN_OVERLAP,
                    "Other Lumen pixels overlap the refitted ellipse: count=" + otherLumenCount,
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final List<Point> primaryPixels = primary.getPixels();
        final List<Point> associatedFillPixels = componentResult.getIntervalAssociatedFillPixels();
        if (!primaryPixels.equals(associatedFillPixels)) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                    "Primary component pixels do not equal the complete"
                            + " interval-associated fill pixel set",
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }
        if (primary.getArea() != componentResult.getIntervalAssociatedFillPixelCount()) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                    "Primary component area does not equal the interval-associated"
                            + " fill pixel count: " + primary.getArea() + " vs "
                            + componentResult.getIntervalAssociatedFillPixelCount(),
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final List<Point> candidatePixels = new ArrayList<Point>(primaryPixels.size());
        for (int i = 0; i < primaryPixels.size(); i++) {
            final Point point = primaryPixels.get(i);
            if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) {
                throw new IllegalStateException(
                        "Candidate pixel out of image bounds: (" + point.x + ", " + point.y + ")");
            }
            candidatePixels.add(new Point(point.x, point.y));
        }
        sortPointsInPlace(candidatePixels);

        int wood = 0;
        int ray = 0;
        int lumen = 0;
        int background = 0;
        int other = 0;
        for (int i = 0; i < candidatePixels.size(); i++) {
            final Point point = candidatePixels.get(i);
            final int rawValue = unsigned(sourcePixels[point.y * width + point.x]);
            if (rawValue == ClassifierLabels.WOOD_RESULT_VALUE) {
                wood++;
            } else if (rawValue == ClassifierLabels.RAY_RESULT_VALUE) {
                ray++;
            } else if (rawValue == ClassifierLabels.LUMEN_RESULT_VALUE) {
                lumen++;
            } else if (rawValue == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                background++;
            } else {
                other++;
            }
        }

        if (lumen != 0
                || wood != primary.getWoodPixelCount()
                || ray != primary.getRayPixelCount()
                || background != primary.getBackgroundPixelCount()
                || other != primary.getOtherClassPixelCount()) {
            return buildNonReady(
                    DiagnosticReconstructionCandidate.Decision.SOURCE_PIXEL_VALUE_MISMATCH,
                    "Original raw pixel values no longer match the supplied component analysis",
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }
        if (wood != candidatePixels.size()) {
            return buildNonReadyWithCounts(
                    DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS,
                    "Not every candidate pixel has raw Wood value 0: wood=" + wood
                            + ", ray=" + ray + ", lumen=" + lumen
                            + ", background=" + background + ", other=" + other,
                    width, height, checksumBefore, originalSelectedLumenArea, sourcePixels);
        }

        final List<Point> selectedLumenPixels = wholePreview.getSelectedObservedLumenPixels();
        final Set<Long> unionKeys = toKeySet(selectedLumenPixels);
        unionKeys.addAll(toKeySet(candidatePixels));
        final int diagnosticReconstructedArea = unionKeys.size();
        final int diagnosticReconstructedPerimeter =
                exposedCardinalPerimeter(unionKeys, width, height);
        final double diagnosticReconstructedCircularity =
                circularity(diagnosticReconstructedArea, diagnosticReconstructedPerimeter);
        final double diagnosticCircularityChange =
                diagnosticReconstructedCircularity - wholePreview.getOriginalCircularity();
        final double addedAreaFraction =
                candidatePixels.size() / (double) originalSelectedLumenArea;

        final int checksumAfter = computeChecksum((byte[]) classifiedProcessor.getPixels());
        final boolean sourceUnchanged = checksumAfter == checksumBefore;
        if (!sourceUnchanged) {
            throw new IllegalStateException(
                    "classifiedProcessor was modified during diagnostic evaluation");
        }

        return new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "All conservative evidence and structural association checks passed",
                true,
                candidatePixels,
                candidatePixels.size(),
                wood,
                ray,
                lumen,
                background,
                other,
                width,
                height,
                checksumBefore,
                checksumAfter,
                sourceUnchanged,
                originalSelectedLumenArea,
                diagnosticReconstructedArea,
                diagnosticReconstructedPerimeter,
                diagnosticReconstructedCircularity,
                diagnosticCircularityChange,
                addedAreaFraction,
                primary.getComponentId(),
                primary.getArea());
    }

    public ByteProcessor createDiagnosticCopy(
            final ByteProcessor classifiedProcessor,
            final DiagnosticReconstructionCandidate candidate) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (!candidate.isReadyForDiagnosticCopy()) {
            throw new IllegalArgumentException(
                    "candidate must be ready for diagnostic copy: "
                            + candidate.getDecision());
        }
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width != candidate.getSourceWidth() || height != candidate.getSourceHeight()) {
            throw new IllegalArgumentException(
                    "classifiedProcessor dimensions must match candidate: processor="
                            + width + "x" + height + ", candidate=" + candidate.getSourceWidth()
                            + "x" + candidate.getSourceHeight());
        }

        final byte[] sourcePixels = (byte[]) classifiedProcessor.getPixels();
        if (sourcePixels == null || sourcePixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }
        final int checksumBeforeCopy = computeChecksum(sourcePixels);

        final List<Point> pixelsToChange = candidate.getPixelsToChange();
        for (int i = 0; i < pixelsToChange.size(); i++) {
            final Point point = pixelsToChange.get(i);
            final int rawValue = unsigned(sourcePixels[point.y * width + point.x]);
            if (rawValue != ClassifierLabels.WOOD_RESULT_VALUE) {
                throw new IllegalStateException(
                        "Candidate source pixel is no longer raw Wood value 0 at ("
                                + point.x + ", " + point.y + "): " + rawValue);
            }
        }

        final byte[] outputPixels = new byte[sourcePixels.length];
        System.arraycopy(sourcePixels, 0, outputPixels, 0, sourcePixels.length);
        for (int i = 0; i < pixelsToChange.size(); i++) {
            final Point point = pixelsToChange.get(i);
            outputPixels[point.y * width + point.x] = (byte) ClassifierLabels.LUMEN_RESULT_VALUE;
        }

        final int checksumAfterCopy = computeChecksum((byte[]) classifiedProcessor.getPixels());
        if (checksumAfterCopy != checksumBeforeCopy) {
            throw new IllegalStateException(
                    "classifiedProcessor was modified while creating the diagnostic copy");
        }

        int changedCount = 0;
        for (int i = 0; i < sourcePixels.length; i++) {
            if (sourcePixels[i] != outputPixels[i]) {
                changedCount++;
                if (unsigned(outputPixels[i]) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    throw new IllegalStateException(
                            "A changed output pixel is not raw Lumen value 2 at index " + i);
                }
            }
        }
        if (changedCount != candidate.getPixelCount()) {
            throw new IllegalStateException(
                    "Unexpected changed pixel count: " + changedCount + " vs expected "
                            + candidate.getPixelCount());
        }

        final ColorModel colorModel = classifiedProcessor.getColorModel();
        if (colorModel == null) {
            return new ByteProcessor(width, height, outputPixels);
        }
        return new ByteProcessor(width, height, outputPixels, colorModel);
    }

    public ByteProcessor createChangeMask(final DiagnosticReconstructionCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (!candidate.isReadyForDiagnosticCopy()) {
            throw new IllegalArgumentException(
                    "candidate must be ready for diagnostic copy: "
                            + candidate.getDecision());
        }
        final int width = candidate.getSourceWidth();
        final int height = candidate.getSourceHeight();
        final byte[] maskPixels = new byte[width * height];
        final List<Point> pixelsToChange = candidate.getPixelsToChange();
        for (int i = 0; i < pixelsToChange.size(); i++) {
            final Point point = pixelsToChange.get(i);
            maskPixels[point.y * width + point.x] = (byte) 255;
        }

        int foregroundCount = 0;
        for (int i = 0; i < maskPixels.length; i++) {
            if (unsigned(maskPixels[i]) == 255) {
                foregroundCount++;
            }
        }
        if (foregroundCount != candidate.getPixelCount()) {
            throw new IllegalStateException(
                    "Unexpected change-mask foreground count: " + foregroundCount
                            + " vs expected " + candidate.getPixelCount());
        }

        return new ByteProcessor(width, height, maskPixels);
    }

    private DiagnosticReconstructionCandidate buildNonReady(
            final DiagnosticReconstructionCandidate.Decision decision,
            final String reason,
            final int width,
            final int height,
            final int checksumBefore,
            final int originalSelectedLumenArea,
            final byte[] sourcePixels) {
        return buildNonReadyWithCounts(
                decision, reason, width, height, checksumBefore, originalSelectedLumenArea,
                sourcePixels);
    }

    private DiagnosticReconstructionCandidate buildNonReadyWithCounts(
            final DiagnosticReconstructionCandidate.Decision decision,
            final String reason,
            final int width,
            final int height,
            final int checksumBefore,
            final int originalSelectedLumenArea,
            final byte[] sourcePixels) {
        final int checksumAfter = computeChecksum(sourcePixels);
        return new DiagnosticReconstructionCandidate(
                decision,
                reason,
                false,
                Collections.<Point>emptyList(),
                0,
                0,
                0,
                0,
                0,
                0,
                width,
                height,
                checksumBefore,
                checksumAfter,
                checksumAfter == checksumBefore,
                originalSelectedLumenArea,
                0,
                0,
                0.0,
                0.0,
                0.0,
                0,
                0);
    }

    static int computeChecksum(final byte[] pixels) {
        int result = 1;
        for (int i = 0; i < pixels.length; i++) {
            result = 31 * result + (pixels[i] & 0xff);
        }
        return result;
    }

    private static int exposedCardinalPerimeter(
            final Set<Long> memberKeys, final int width, final int height) {
        int perimeter = 0;
        for (final Long keyObj : memberKeys) {
            final long key = keyObj.longValue();
            final int x = (int) (key >> 32);
            final int y = (int) key;
            for (int i = 0; i < CARDINAL_DX.length; i++) {
                final int nx = x + CARDINAL_DX[i];
                final int ny = y + CARDINAL_DY[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !memberKeys.contains(Long.valueOf(pack(nx, ny)))) {
                    perimeter++;
                }
            }
        }
        return perimeter;
    }

    private static double circularity(final int area, final int perimeter) {
        if (perimeter <= 0) {
            throw new IllegalStateException("Perimeter must be positive for circularity");
        }
        final double value = 4.0 * Math.PI * area / (perimeter * (double) perimeter);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite circularity");
        }
        if (value > 1.0 && value < 1.0 + CIRCULARITY_OVERSHOOT) {
            return 1.0;
        }
        return value;
    }

    private static Set<Long> toKeySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            keys.add(Long.valueOf(pack(point.x, point.y)));
        }
        return keys;
    }

    private static void sortPointsInPlace(final List<Point> points) {
        Collections.sort(points, new java.util.Comparator<Point>() {
            @Override
            public int compare(final Point a, final Point b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static int unsigned(final byte value) {
        return value & 0xff;
    }
}
