package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable dry-run plan for every image assigned to one laboratory machine.
 */
public final class BatchMachineRunPlan {

    private final String machineId;
    private final List<BatchMachineImagePlan> images;
    private final int assignedCount;
    private final int fullSuccessCount;
    private final int precheckMissingCount;
    private final int precheckFailedCount;
    private final int reviewRequiredCount;
    private final int readyForFullProcessingCount;
    private final int classificationCompleteCount;
    private final int canonicalConflictCount;
    private final int invalidStateCount;
    private final int completedCount;
    private final double progressFraction;
    private final double progressPercent;
    private final int plannedProcessingRemaining;

    private BatchMachineRunPlan(
            final String machineId,
            final List<BatchMachineImagePlan> images) {
        this.machineId = machineId;
        this.images = Collections.unmodifiableList(new ArrayList<BatchMachineImagePlan>(images));
        this.assignedCount = this.images.size();

        int fullSuccess = 0;
        int precheckMissing = 0;
        int precheckFailed = 0;
        int reviewRequired = 0;
        int ready = 0;
        int classificationComplete = 0;
        int conflict = 0;
        int invalid = 0;
        int remaining = 0;
        for (int i = 0; i < this.images.size(); i++) {
            final BatchMachineImagePlan plan = this.images.get(i);
            switch (plan.state()) {
                case FULL_SUCCESS:
                    fullSuccess++;
                    break;
                case PRECHECK_MISSING:
                    precheckMissing++;
                    break;
                case PRECHECK_FAILED:
                    precheckFailed++;
                    break;
                case PRECHECK_REVIEW_REQUIRED:
                    reviewRequired++;
                    break;
                case READY_FOR_FULL_PROCESSING:
                    ready++;
                    break;
                case CLASSIFICATION_COMPLETE:
                    classificationComplete++;
                    break;
                case CANONICAL_CONFLICT:
                    conflict++;
                    break;
                case UNKNOWN_INVALID_STATE:
                default:
                    invalid++;
                    break;
            }
            final BatchMachinePlannedAction action = plan.plannedAction();
            if (action == BatchMachinePlannedAction.RUN_PRECHECK
                    || action == BatchMachinePlannedAction.RUN_FULL_PROCESSING
                    || action == BatchMachinePlannedAction.RESUME_RECONSTRUCTION) {
                remaining++;
            }
        }
        this.fullSuccessCount = fullSuccess;
        this.precheckMissingCount = precheckMissing;
        this.precheckFailedCount = precheckFailed;
        this.reviewRequiredCount = reviewRequired;
        this.readyForFullProcessingCount = ready;
        this.classificationCompleteCount = classificationComplete;
        this.canonicalConflictCount = conflict;
        this.invalidStateCount = invalid;
        this.completedCount = fullSuccess;
        this.progressFraction = assignedCount == 0
                ? 0.0
                : ((double) completedCount) / ((double) assignedCount);
        this.progressPercent = progressFraction * 100.0;
        this.plannedProcessingRemaining = remaining;
    }

    public static BatchMachineRunPlan of(
            final String machineId, final List<BatchMachineImagePlan> images) {
        if (machineId == null || machineId.trim().isEmpty()) {
            throw new IllegalArgumentException("machineId must not be blank");
        }
        if (images == null) {
            throw new IllegalArgumentException("images must not be null");
        }
        return new BatchMachineRunPlan(machineId.trim(), images);
    }

    public String machineId() {
        return machineId;
    }

    public List<BatchMachineImagePlan> images() {
        return images;
    }

    public int assignedCount() {
        return assignedCount;
    }

    public int fullSuccessCount() {
        return fullSuccessCount;
    }

    public int precheckMissingCount() {
        return precheckMissingCount;
    }

    public int precheckFailedCount() {
        return precheckFailedCount;
    }

    public int reviewRequiredCount() {
        return reviewRequiredCount;
    }

    public int readyForFullProcessingCount() {
        return readyForFullProcessingCount;
    }

    public int classificationCompleteCount() {
        return classificationCompleteCount;
    }

    public int canonicalConflictCount() {
        return canonicalConflictCount;
    }

    public int invalidStateCount() {
        return invalidStateCount;
    }

    /** Only {@link BatchMachineImageState#FULL_SUCCESS} images. */
    public int completedCount() {
        return completedCount;
    }

    /** {@code completedCount / assignedCount}, or {@code 0.0} when assigned is zero. */
    public double progressFraction() {
        return progressFraction;
    }

    /** {@code progressFraction * 100.0}; never {@link Double#NaN}. */
    public double progressPercent() {
        return progressPercent;
    }

    public int plannedProcessingRemaining() {
        return plannedProcessingRemaining;
    }

    public String formatProgressPercent() {
        return String.format(Locale.ROOT, "%.1f%%", Double.valueOf(progressPercent));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchMachineRunPlan)) {
            return false;
        }
        final BatchMachineRunPlan other = (BatchMachineRunPlan) obj;
        return Objects.equals(machineId, other.machineId)
                && Objects.equals(images, other.images);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineId, images);
    }
}
