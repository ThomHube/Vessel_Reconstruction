package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of diagnostic inward-outlier interval detection.
 */
public final class InwardOutlierDetectionResult {

    public static final String SELECTED_DOMINANT_INWARD_INTERVAL =
            "SELECTED_DOMINANT_INWARD_INTERVAL";
    public static final String NO_CORE_POINTS = "NO_CORE_POINTS";
    public static final String ROBUST_SCALE_TOO_SMALL = "ROBUST_SCALE_TOO_SMALL";
    public static final String INTERVAL_TOO_SHORT = "INTERVAL_TOO_SHORT";
    public static final String INTERVAL_TOO_LARGE = "INTERVAL_TOO_LARGE";
    public static final String MULTIPLE_COMPETING_INTERVALS =
            "MULTIPLE_COMPETING_INTERVALS";
    public static final String NO_VALID_INTERVAL = "NO_VALID_INTERVAL";
    public static final String UNKNOWN = "UNKNOWN";

    private final OrderedBoundaryResidualProfile residualProfile;
    private final double robustScale;
    private final double coreThreshold;
    private final double expansionThreshold;
    private final List<InwardOutlierInterval> candidateIntervals;
    private final InwardOutlierInterval selectedInterval;
    private final boolean intervalSelected;
    private final String selectionReason;
    private final double dominanceRatio;

    public InwardOutlierDetectionResult(
            final OrderedBoundaryResidualProfile residualProfile,
            final double robustScale,
            final double coreThreshold,
            final double expansionThreshold,
            final List<InwardOutlierInterval> candidateIntervals,
            final InwardOutlierInterval selectedInterval,
            final boolean intervalSelected,
            final String selectionReason,
            final double dominanceRatio) {
        if (residualProfile == null) {
            throw new IllegalArgumentException("residualProfile must not be null");
        }
        requireFinite("robustScale", robustScale);
        requireFinite("coreThreshold", coreThreshold);
        requireFinite("expansionThreshold", expansionThreshold);
        if (!(Double.isFinite(dominanceRatio) || Double.isInfinite(dominanceRatio))) {
            throw new IllegalArgumentException(
                    "dominanceRatio must be finite or infinite: " + dominanceRatio);
        }
        if (candidateIntervals == null) {
            throw new IllegalArgumentException("candidateIntervals must not be null");
        }
        if (selectionReason == null || selectionReason.isEmpty()) {
            throw new IllegalArgumentException("selectionReason must not be null or empty");
        }
        if (intervalSelected && selectedInterval == null) {
            throw new IllegalArgumentException(
                    "selectedInterval must be non-null when intervalSelected is true");
        }
        if (!intervalSelected && selectedInterval != null) {
            throw new IllegalArgumentException(
                    "selectedInterval must be null when intervalSelected is false");
        }

        final List<InwardOutlierInterval> copy =
                new ArrayList<InwardOutlierInterval>(candidateIntervals.size());
        for (int i = 0; i < candidateIntervals.size(); i++) {
            final InwardOutlierInterval interval = candidateIntervals.get(i);
            if (interval == null) {
                throw new IllegalArgumentException(
                        "candidateIntervals must not contain null entries");
            }
            copy.add(interval);
        }
        if (intervalSelected && !copy.contains(selectedInterval)) {
            // Identity may differ; compare by equals.
            boolean found = false;
            for (int i = 0; i < copy.size(); i++) {
                if (copy.get(i).equals(selectedInterval)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException(
                        "selectedInterval must belong to candidateIntervals");
            }
        }

        this.residualProfile = residualProfile;
        this.robustScale = robustScale;
        this.coreThreshold = coreThreshold;
        this.expansionThreshold = expansionThreshold;
        this.candidateIntervals = Collections.unmodifiableList(copy);
        this.selectedInterval = selectedInterval;
        this.intervalSelected = intervalSelected;
        this.selectionReason = selectionReason;
        this.dominanceRatio = dominanceRatio;
    }

    public OrderedBoundaryResidualProfile getResidualProfile() {
        return residualProfile;
    }

    public double getRobustScale() {
        return robustScale;
    }

    public double getCoreThreshold() {
        return coreThreshold;
    }

    public double getExpansionThreshold() {
        return expansionThreshold;
    }

    public List<InwardOutlierInterval> getCandidateIntervals() {
        return candidateIntervals;
    }

    public InwardOutlierInterval getSelectedInterval() {
        return selectedInterval;
    }

    public boolean isIntervalSelected() {
        return intervalSelected;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public double getDominanceRatio() {
        return dominanceRatio;
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InwardOutlierDetectionResult)) {
            return false;
        }
        final InwardOutlierDetectionResult other = (InwardOutlierDetectionResult) obj;
        return intervalSelected == other.intervalSelected
                && Double.doubleToLongBits(robustScale)
                == Double.doubleToLongBits(other.robustScale)
                && Double.doubleToLongBits(coreThreshold)
                == Double.doubleToLongBits(other.coreThreshold)
                && Double.doubleToLongBits(expansionThreshold)
                == Double.doubleToLongBits(other.expansionThreshold)
                && Double.doubleToLongBits(dominanceRatio)
                == Double.doubleToLongBits(other.dominanceRatio)
                && Objects.equals(residualProfile, other.residualProfile)
                && Objects.equals(candidateIntervals, other.candidateIntervals)
                && Objects.equals(selectedInterval, other.selectedInterval)
                && Objects.equals(selectionReason, other.selectionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                residualProfile,
                Double.valueOf(robustScale),
                Double.valueOf(coreThreshold),
                Double.valueOf(expansionThreshold),
                candidateIntervals,
                selectedInterval,
                Boolean.valueOf(intervalSelected),
                selectionReason,
                Double.valueOf(dominanceRatio));
    }

    @Override
    public String toString() {
        return "InwardOutlierDetectionResult{selected=" + intervalSelected
                + ", reason=" + selectionReason
                + ", candidates=" + candidateIntervals.size()
                + ", dominanceRatio=" + dominanceRatio
                + '}';
    }
}
