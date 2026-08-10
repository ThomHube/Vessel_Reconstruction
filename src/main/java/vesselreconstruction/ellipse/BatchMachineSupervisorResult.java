package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable aggregate result of one Phase-4 machine supervisor execution.
 */
public final class BatchMachineSupervisorResult {

    private final String machineId;
    private final long startEpochMs;
    private final long endEpochMs;
    private final long elapsedMs;
    private final int assignedCount;
    private final int initialCompletedCount;
    private final int finalCompletedCount;
    private final int skippedCompleteCount;
    private final int attemptedCount;
    private final int succeededThisRunCount;
    private final int blockedCount;
    private final int failedCount;
    private final int remainingCount;
    private final double initialProgressPercent;
    private final double finalProgressPercent;
    private final BatchMachineSupervisorStatus status;
    private final List<BatchMachineImageExecutionResult> imageResults;

    private BatchMachineSupervisorResult(final Builder builder) {
        this.machineId = builder.machineId;
        this.startEpochMs = builder.startEpochMs;
        this.endEpochMs = builder.endEpochMs;
        this.elapsedMs = builder.elapsedMs;
        this.assignedCount = builder.assignedCount;
        this.initialCompletedCount = builder.initialCompletedCount;
        this.finalCompletedCount = builder.finalCompletedCount;
        this.skippedCompleteCount = builder.skippedCompleteCount;
        this.attemptedCount = builder.attemptedCount;
        this.succeededThisRunCount = builder.succeededThisRunCount;
        this.blockedCount = builder.blockedCount;
        this.failedCount = builder.failedCount;
        this.remainingCount = builder.remainingCount;
        this.initialProgressPercent = builder.initialProgressPercent;
        this.finalProgressPercent = builder.finalProgressPercent;
        this.status = builder.status;
        this.imageResults = Collections.unmodifiableList(new ArrayList<BatchMachineImageExecutionResult>(builder.imageResults));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static double progressPercent(final int completedCount, final int assignedCount) {
        if (assignedCount <= 0) {
            return 0.0d;
        }
        return 100.0d * completedCount / assignedCount;
    }

    public String machineId() {
        return machineId;
    }

    public long startEpochMs() {
        return startEpochMs;
    }

    public long endEpochMs() {
        return endEpochMs;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public int assignedCount() {
        return assignedCount;
    }

    public int initialCompletedCount() {
        return initialCompletedCount;
    }

    public int finalCompletedCount() {
        return finalCompletedCount;
    }

    public int skippedCompleteCount() {
        return skippedCompleteCount;
    }

    public int attemptedCount() {
        return attemptedCount;
    }

    public int succeededThisRunCount() {
        return succeededThisRunCount;
    }

    public int blockedCount() {
        return blockedCount;
    }

    public int failedCount() {
        return failedCount;
    }

    public int remainingCount() {
        return remainingCount;
    }

    public double initialProgressPercent() {
        return initialProgressPercent;
    }

    public double finalProgressPercent() {
        return finalProgressPercent;
    }

    public BatchMachineSupervisorStatus status() {
        return status;
    }

    public List<BatchMachineImageExecutionResult> imageResults() {
        return imageResults;
    }

    public String formatProgress(final double percent) {
        return String.format(Locale.ROOT, "%.1f%%", Double.valueOf(percent));
    }

    public static final class Builder {
        private String machineId;
        private long startEpochMs;
        private long endEpochMs;
        private long elapsedMs;
        private int assignedCount;
        private int initialCompletedCount;
        private int finalCompletedCount;
        private int skippedCompleteCount;
        private int attemptedCount;
        private int succeededThisRunCount;
        private int blockedCount;
        private int failedCount;
        private int remainingCount;
        private double initialProgressPercent;
        private double finalProgressPercent;
        private BatchMachineSupervisorStatus status;
        private final List<BatchMachineImageExecutionResult> imageResults =
                new ArrayList<BatchMachineImageExecutionResult>();

        public Builder machineId(final String machineId) {
            this.machineId = machineId;
            return this;
        }

        public Builder timing(final long startEpochMs, final long endEpochMs) {
            if (endEpochMs < startEpochMs) {
                throw new IllegalArgumentException("endEpochMs before startEpochMs");
            }
            this.startEpochMs = startEpochMs;
            this.endEpochMs = endEpochMs;
            this.elapsedMs = endEpochMs - startEpochMs;
            return this;
        }

        public Builder assignedCount(final int assignedCount) {
            this.assignedCount = assignedCount;
            return this;
        }

        public Builder initialCompletedCount(final int initialCompletedCount) {
            this.initialCompletedCount = initialCompletedCount;
            return this;
        }

        public Builder finalCompletedCount(final int finalCompletedCount) {
            this.finalCompletedCount = finalCompletedCount;
            return this;
        }

        public Builder skippedCompleteCount(final int skippedCompleteCount) {
            this.skippedCompleteCount = skippedCompleteCount;
            return this;
        }

        public Builder attemptedCount(final int attemptedCount) {
            this.attemptedCount = attemptedCount;
            return this;
        }

        public Builder succeededThisRunCount(final int succeededThisRunCount) {
            this.succeededThisRunCount = succeededThisRunCount;
            return this;
        }

        public Builder blockedCount(final int blockedCount) {
            this.blockedCount = blockedCount;
            return this;
        }

        public Builder failedCount(final int failedCount) {
            this.failedCount = failedCount;
            return this;
        }

        public Builder remainingCount(final int remainingCount) {
            this.remainingCount = remainingCount;
            return this;
        }

        public Builder initialProgressPercent(final double initialProgressPercent) {
            this.initialProgressPercent = requireFinite(initialProgressPercent, "initialProgressPercent");
            return this;
        }

        public Builder finalProgressPercent(final double finalProgressPercent) {
            this.finalProgressPercent = requireFinite(finalProgressPercent, "finalProgressPercent");
            return this;
        }

        public Builder status(final BatchMachineSupervisorStatus status) {
            this.status = status;
            return this;
        }

        public Builder addImageResult(final BatchMachineImageExecutionResult result) {
            this.imageResults.add(Objects.requireNonNull(result, "result"));
            return this;
        }

        public Builder imageResults(final List<BatchMachineImageExecutionResult> results) {
            this.imageResults.clear();
            if (results != null) {
                this.imageResults.addAll(results);
            }
            return this;
        }

        public BatchMachineSupervisorResult build() {
            if (machineId == null || !machineId.trim().matches("^machine_\\d+$")) {
                throw new IllegalArgumentException(
                        "machineId must match machine_\\d+: " + machineId);
            }
            this.machineId = machineId.trim();
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
            if (assignedCount < 0 || initialCompletedCount < 0 || finalCompletedCount < 0
                    || skippedCompleteCount < 0 || attemptedCount < 0 || succeededThisRunCount < 0
                    || blockedCount < 0 || failedCount < 0 || remainingCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            return new BatchMachineSupervisorResult(this);
        }

        private static double requireFinite(final double value, final String name) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            return value;
        }
    }
}
