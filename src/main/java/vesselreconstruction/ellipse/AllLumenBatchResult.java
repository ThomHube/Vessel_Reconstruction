package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable aggregate result of an all-lumen diagnostic batch.
 */
public final class AllLumenBatchResult {

    private final int imageWidth;
    private final int imageHeight;
    private final int sourceChecksumBefore;
    private final int sourceChecksumAfter;
    private final boolean sourceUnchanged;
    private final List<LumenBatchItemResult> itemResults;
    private final int totalComponentCount;
    private final int belowMinimumAreaCount;
    private final int borderSkippedCount;
    private final int eligibleComponentCount;
    private final int reconstructedComponentCount;
    private final int noIntervalCount;
    private final int insufficientEvidenceCount;
    private final int overlapRejectedCount;
    private final int unsupportedCount;
    private final int processingErrorCount;
    private final int candidateConflictComponentCount;
    private final List<Point> combinedChangedPixels;
    private final int combinedChangedPixelCount;
    private final int conflictingPixelCount;
    private final long totalProcessingTimeNanoseconds;

    public AllLumenBatchResult(
            final int imageWidth,
            final int imageHeight,
            final int sourceChecksumBefore,
            final int sourceChecksumAfter,
            final List<LumenBatchItemResult> itemResults,
            final List<Point> combinedChangedPixels,
            final int conflictingPixelCount,
            final long totalProcessingTimeNanoseconds) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (itemResults == null) {
            throw new IllegalArgumentException("itemResults must not be null");
        }
        if (combinedChangedPixels == null) {
            throw new IllegalArgumentException("combinedChangedPixels must not be null");
        }
        if (conflictingPixelCount < 0) {
            throw new IllegalArgumentException(
                    "conflictingPixelCount must be non-negative: " + conflictingPixelCount);
        }
        if (totalProcessingTimeNanoseconds < 0L) {
            throw new IllegalArgumentException(
                    "totalProcessingTimeNanoseconds must be non-negative");
        }

        final List<LumenBatchItemResult> sortedItems =
                new ArrayList<LumenBatchItemResult>(itemResults);
        Collections.sort(sortedItems, ITEM_ORDER);
        final Set<Integer> seenIds = new HashSet<Integer>(sortedItems.size() * 2);
        for (int i = 0; i < sortedItems.size(); i++) {
            final LumenBatchItemResult item = sortedItems.get(i);
            if (item == null) {
                throw new IllegalArgumentException("itemResults must not contain null");
            }
            if (!seenIds.add(Integer.valueOf(item.getComponentId()))) {
                throw new IllegalArgumentException(
                        "duplicate component ID: " + item.getComponentId());
            }
        }

        final List<Point> sortedChanged = normalizeChangedPixels(combinedChangedPixels);
        validateChangedBelongToReconstructed(sortedItems, sortedChanged);

        int belowMin = 0;
        int border = 0;
        int reconstructed = 0;
        int noInterval = 0;
        int insufficient = 0;
        int overlap = 0;
        int unsupported = 0;
        int processingError = 0;
        int conflict = 0;
        int eligible = 0;

        for (int i = 0; i < sortedItems.size(); i++) {
            final LumenBatchItemResult.Status status = sortedItems.get(i).getStatus();
            if (status == LumenBatchItemResult.Status.BELOW_MINIMUM_AREA) {
                belowMin++;
            } else if (status == LumenBatchItemResult.Status.BORDER_TOUCHING_SKIPPED) {
                border++;
            } else if (status == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT) {
                reconstructed++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.NO_INTERVAL_SELECTED) {
                noInterval++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE) {
                insufficient++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP) {
                overlap++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.UNSUPPORTED_BOUNDARY
                    || status == LumenBatchItemResult.Status.INITIAL_ANALYSIS_FAILED) {
                unsupported++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.PROCESSING_ERROR) {
                processingError++;
                eligible++;
            } else if (status == LumenBatchItemResult.Status.CANDIDATE_CONFLICT) {
                conflict++;
                eligible++;
            } else {
                eligible++;
                if (status == LumenBatchItemResult.Status.NO_INTERVAL_ASSOCIATED_FILL
                        || status == LumenBatchItemResult.Status.AMBIGUOUS_INTERVAL_ASSOCIATION
                        || status == LumenBatchItemResult.Status.UNMATCHED_INTERVAL_EXTERIOR_SEEDS
                        || status == LumenBatchItemResult.Status.NON_WOOD_CANDIDATE_PIXELS
                        || status == LumenBatchItemResult.Status.INVALID_STRUCTURAL_ASSOCIATION
                        || status == LumenBatchItemResult.Status.SOURCE_PIXEL_VALUE_MISMATCH) {
                    // counted in eligible above; structural/candidate rejections
                }
            }
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.sourceChecksumBefore = sourceChecksumBefore;
        this.sourceChecksumAfter = sourceChecksumAfter;
        this.sourceUnchanged = sourceChecksumBefore == sourceChecksumAfter;
        this.itemResults = Collections.unmodifiableList(sortedItems);
        this.totalComponentCount = sortedItems.size();
        this.belowMinimumAreaCount = belowMin;
        this.borderSkippedCount = border;
        this.eligibleComponentCount = eligible;
        this.reconstructedComponentCount = reconstructed;
        this.noIntervalCount = noInterval;
        this.insufficientEvidenceCount = insufficient;
        this.overlapRejectedCount = overlap;
        this.unsupportedCount = unsupported;
        this.processingErrorCount = processingError;
        this.candidateConflictComponentCount = conflict;
        this.combinedChangedPixels = Collections.unmodifiableList(sortedChanged);
        this.combinedChangedPixelCount = sortedChanged.size();
        this.conflictingPixelCount = conflictingPixelCount;
        this.totalProcessingTimeNanoseconds = totalProcessingTimeNanoseconds;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getSourceChecksumBefore() {
        return sourceChecksumBefore;
    }

    public int getSourceChecksumAfter() {
        return sourceChecksumAfter;
    }

    public boolean isSourceUnchanged() {
        return sourceUnchanged;
    }

    public List<LumenBatchItemResult> getItemResults() {
        return itemResults;
    }

    public int getTotalComponentCount() {
        return totalComponentCount;
    }

    public int getBelowMinimumAreaCount() {
        return belowMinimumAreaCount;
    }

    public int getBorderSkippedCount() {
        return borderSkippedCount;
    }

    public int getEligibleComponentCount() {
        return eligibleComponentCount;
    }

    public int getReconstructedComponentCount() {
        return reconstructedComponentCount;
    }

    public int getNoIntervalCount() {
        return noIntervalCount;
    }

    public int getInsufficientEvidenceCount() {
        return insufficientEvidenceCount;
    }

    public int getOverlapRejectedCount() {
        return overlapRejectedCount;
    }

    public int getUnsupportedCount() {
        return unsupportedCount;
    }

    public int getProcessingErrorCount() {
        return processingErrorCount;
    }

    public int getCandidateConflictComponentCount() {
        return candidateConflictComponentCount;
    }

    public List<Point> getCombinedChangedPixels() {
        return defensivePoints(combinedChangedPixels);
    }

    public int getCombinedChangedPixelCount() {
        return combinedChangedPixelCount;
    }

    public int getConflictingPixelCount() {
        return conflictingPixelCount;
    }

    public long getTotalProcessingTimeNanoseconds() {
        return totalProcessingTimeNanoseconds;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AllLumenBatchResult)) {
            return false;
        }
        final AllLumenBatchResult other = (AllLumenBatchResult) obj;
        return imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && sourceChecksumBefore == other.sourceChecksumBefore
                && sourceChecksumAfter == other.sourceChecksumAfter
                && sourceUnchanged == other.sourceUnchanged
                && itemResults.equals(other.itemResults)
                && totalComponentCount == other.totalComponentCount
                && belowMinimumAreaCount == other.belowMinimumAreaCount
                && borderSkippedCount == other.borderSkippedCount
                && eligibleComponentCount == other.eligibleComponentCount
                && reconstructedComponentCount == other.reconstructedComponentCount
                && noIntervalCount == other.noIntervalCount
                && insufficientEvidenceCount == other.insufficientEvidenceCount
                && overlapRejectedCount == other.overlapRejectedCount
                && unsupportedCount == other.unsupportedCount
                && processingErrorCount == other.processingErrorCount
                && candidateConflictComponentCount == other.candidateConflictComponentCount
                && combinedChangedPixelCount == other.combinedChangedPixelCount
                && conflictingPixelCount == other.conflictingPixelCount
                && totalProcessingTimeNanoseconds == other.totalProcessingTimeNanoseconds
                && pixelsEqual(combinedChangedPixels, other.combinedChangedPixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(sourceChecksumBefore),
                Integer.valueOf(sourceChecksumAfter),
                Boolean.valueOf(sourceUnchanged),
                itemResults,
                Integer.valueOf(totalComponentCount),
                Integer.valueOf(belowMinimumAreaCount),
                Integer.valueOf(borderSkippedCount),
                Integer.valueOf(eligibleComponentCount),
                Integer.valueOf(reconstructedComponentCount),
                Integer.valueOf(noIntervalCount),
                Integer.valueOf(insufficientEvidenceCount),
                Integer.valueOf(overlapRejectedCount),
                Integer.valueOf(unsupportedCount),
                Integer.valueOf(processingErrorCount),
                Integer.valueOf(candidateConflictComponentCount),
                Integer.valueOf(combinedChangedPixelCount),
                Integer.valueOf(conflictingPixelCount),
                Long.valueOf(totalProcessingTimeNanoseconds),
                combinedChangedPixels);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "AllLumenBatchResult{%dx%d,total=%d,reconstructed=%d,changed=%d,conflicts=%d}",
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(totalComponentCount),
                Integer.valueOf(reconstructedComponentCount),
                Integer.valueOf(combinedChangedPixelCount),
                Integer.valueOf(candidateConflictComponentCount));
    }

    private static void validateChangedBelongToReconstructed(
            final List<LumenBatchItemResult> items,
            final List<Point> changed) {
        final Set<Long> allowed = new HashSet<Long>();
        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            if (item.getStatus() != LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT) {
                continue;
            }
            final List<Point> candidates = item.getCandidatePixels();
            for (int j = 0; j < candidates.size(); j++) {
                final Point p = candidates.get(j);
                allowed.add(Long.valueOf(pack(p.x, p.y)));
            }
        }
        final Set<Long> seen = new HashSet<Long>(changed.size() * 2);
        for (int i = 0; i < changed.size(); i++) {
            final Point p = changed.get(i);
            final Long key = Long.valueOf(pack(p.x, p.y));
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "combined changed pixel not in any reconstructed candidate: ("
                                + p.x + "," + p.y + ")");
            }
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "duplicate combined changed pixel: (" + p.x + "," + p.y + ")");
            }
        }
    }

    private static List<Point> normalizeChangedPixels(final List<Point> pixels) {
        final List<Point> sorted = new ArrayList<Point>(pixels.size());
        final Set<Long> seen = new HashSet<Long>(Math.max(16, pixels.size() * 2));
        for (int i = 0; i < pixels.size(); i++) {
            final Point raw = pixels.get(i);
            if (raw == null) {
                throw new IllegalArgumentException("combinedChangedPixels must not contain null");
            }
            final Long key = Long.valueOf(pack(raw.x, raw.y));
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "duplicate combined changed pixel: (" + raw.x + "," + raw.y + ")");
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
            if (a.get(i).x != b.get(i).x || a.get(i).y != b.get(i).y) {
                return false;
            }
        }
        return true;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static final Comparator<LumenBatchItemResult> ITEM_ORDER =
            new Comparator<LumenBatchItemResult>() {
                @Override
                public int compare(final LumenBatchItemResult a, final LumenBatchItemResult b) {
                    return Integer.compare(a.getComponentId(), b.getComponentId());
                }
            };

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
