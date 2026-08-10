package vesselreconstruction.ellipse;

import java.awt.Rectangle;
import java.util.Locale;
import java.util.Objects;

/**
 * Diagnostics produced while generating an automatic downsampled sample mask.
 */
public final class SampleMaskStatistics {

    private final int backgroundRed;
    private final int backgroundGreen;
    private final int backgroundBlue;
    private final int automaticThreshold;
    private final int candidateComponentCount;
    private final long selectedComponentAreaMaskPixels;
    private final Rectangle selectedComponentBoundsMask;
    private final int selectedComponentWidthSpan;
    private final int selectedComponentHeightSpan;
    private final long secondLargestCredibleAreaMaskPixels;
    private final long automaticIncludedMaskPixels;
    private final double automaticIncludedFraction;
    private final int automaticMaskChecksum;
    private final int expansionRadiusMaskPixels;

    public SampleMaskStatistics(
            final int backgroundRed,
            final int backgroundGreen,
            final int backgroundBlue,
            final int automaticThreshold,
            final int candidateComponentCount,
            final long selectedComponentAreaMaskPixels,
            final Rectangle selectedComponentBoundsMask,
            final int selectedComponentWidthSpan,
            final int selectedComponentHeightSpan,
            final long secondLargestCredibleAreaMaskPixels,
            final long automaticIncludedMaskPixels,
            final double automaticIncludedFraction,
            final int automaticMaskChecksum,
            final int expansionRadiusMaskPixels) {
        this.backgroundRed = backgroundRed;
        this.backgroundGreen = backgroundGreen;
        this.backgroundBlue = backgroundBlue;
        this.automaticThreshold = automaticThreshold;
        this.candidateComponentCount = candidateComponentCount;
        this.selectedComponentAreaMaskPixels = selectedComponentAreaMaskPixels;
        this.selectedComponentBoundsMask = selectedComponentBoundsMask == null
                ? new Rectangle(0, 0, 0, 0)
                : new Rectangle(selectedComponentBoundsMask);
        this.selectedComponentWidthSpan = selectedComponentWidthSpan;
        this.selectedComponentHeightSpan = selectedComponentHeightSpan;
        this.secondLargestCredibleAreaMaskPixels = secondLargestCredibleAreaMaskPixels;
        this.automaticIncludedMaskPixels = automaticIncludedMaskPixels;
        this.automaticIncludedFraction = automaticIncludedFraction;
        this.automaticMaskChecksum = automaticMaskChecksum;
        this.expansionRadiusMaskPixels = expansionRadiusMaskPixels;
    }

    public int getBackgroundRed() {
        return backgroundRed;
    }

    public int getBackgroundGreen() {
        return backgroundGreen;
    }

    public int getBackgroundBlue() {
        return backgroundBlue;
    }

    public int getAutomaticThreshold() {
        return automaticThreshold;
    }

    public int getCandidateComponentCount() {
        return candidateComponentCount;
    }

    public long getSelectedComponentAreaMaskPixels() {
        return selectedComponentAreaMaskPixels;
    }

    public Rectangle getSelectedComponentBoundsMask() {
        return new Rectangle(selectedComponentBoundsMask);
    }

    public int getSelectedComponentWidthSpan() {
        return selectedComponentWidthSpan;
    }

    public int getSelectedComponentHeightSpan() {
        return selectedComponentHeightSpan;
    }

    public long getSecondLargestCredibleAreaMaskPixels() {
        return secondLargestCredibleAreaMaskPixels;
    }

    /**
     * Second-largest credible candidate area / selected component area, or 0
     * when no second credible component exists.
     */
    public double getSecondaryComponentAreaFraction() {
        if (selectedComponentAreaMaskPixels <= 0L || secondLargestCredibleAreaMaskPixels <= 0L) {
            return 0.0;
        }
        return ((double) secondLargestCredibleAreaMaskPixels)
                / ((double) selectedComponentAreaMaskPixels);
    }

    public long getAutomaticIncludedMaskPixels() {
        return automaticIncludedMaskPixels;
    }

    public double getAutomaticIncludedFraction() {
        return automaticIncludedFraction;
    }

    public int getAutomaticMaskChecksum() {
        return automaticMaskChecksum;
    }

    public int getExpansionRadiusMaskPixels() {
        return expansionRadiusMaskPixels;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "SampleMaskStatistics{bgRGB=(%d,%d,%d),threshold=%d,candidates=%d,"
                        + "selectedArea=%d,span=%dx%d,included=%d,checksum=%d}",
                Integer.valueOf(backgroundRed),
                Integer.valueOf(backgroundGreen),
                Integer.valueOf(backgroundBlue),
                Integer.valueOf(automaticThreshold),
                Integer.valueOf(candidateComponentCount),
                Long.valueOf(selectedComponentAreaMaskPixels),
                Integer.valueOf(selectedComponentWidthSpan),
                Integer.valueOf(selectedComponentHeightSpan),
                Long.valueOf(automaticIncludedMaskPixels),
                Integer.valueOf(automaticMaskChecksum));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SampleMaskStatistics)) {
            return false;
        }
        final SampleMaskStatistics other = (SampleMaskStatistics) obj;
        return backgroundRed == other.backgroundRed
                && backgroundGreen == other.backgroundGreen
                && backgroundBlue == other.backgroundBlue
                && automaticThreshold == other.automaticThreshold
                && candidateComponentCount == other.candidateComponentCount
                && selectedComponentAreaMaskPixels == other.selectedComponentAreaMaskPixels
                && selectedComponentBoundsMask.equals(other.selectedComponentBoundsMask)
                && selectedComponentWidthSpan == other.selectedComponentWidthSpan
                && selectedComponentHeightSpan == other.selectedComponentHeightSpan
                && secondLargestCredibleAreaMaskPixels
                == other.secondLargestCredibleAreaMaskPixels
                && automaticIncludedMaskPixels == other.automaticIncludedMaskPixels
                && Double.compare(automaticIncludedFraction, other.automaticIncludedFraction) == 0
                && automaticMaskChecksum == other.automaticMaskChecksum
                && expansionRadiusMaskPixels == other.expansionRadiusMaskPixels;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(backgroundRed),
                Integer.valueOf(backgroundGreen),
                Integer.valueOf(backgroundBlue),
                Integer.valueOf(automaticThreshold),
                Integer.valueOf(candidateComponentCount),
                Long.valueOf(selectedComponentAreaMaskPixels),
                selectedComponentBoundsMask,
                Integer.valueOf(selectedComponentWidthSpan),
                Integer.valueOf(selectedComponentHeightSpan),
                Long.valueOf(secondLargestCredibleAreaMaskPixels),
                Long.valueOf(automaticIncludedMaskPixels),
                Double.valueOf(automaticIncludedFraction),
                Integer.valueOf(automaticMaskChecksum),
                Integer.valueOf(expansionRadiusMaskPixels));
    }
}
