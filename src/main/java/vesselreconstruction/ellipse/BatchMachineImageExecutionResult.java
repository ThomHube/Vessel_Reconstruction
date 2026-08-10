package vesselreconstruction.ellipse;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable per-image outcome from one Phase-4 machine supervisor pass.
 */
public final class BatchMachineImageExecutionResult {

    private final int sequenceNumber;
    private final String imageId;
    private final BatchMachineImageState initialState;
    private final BatchMachinePlannedAction plannedAction;
    private final boolean processingAttempted;
    private final boolean skippedComplete;
    private final boolean blocked;
    private final int processExitCode;
    private final String finalStatus;
    private final String reasonCode;
    private final String message;
    private final long startEpochMs;
    private final long endEpochMs;
    private final long elapsedMs;
    private final Path attemptResultPath;

    private BatchMachineImageExecutionResult(final Builder builder) {
        this.sequenceNumber = builder.sequenceNumber;
        this.imageId = builder.imageId;
        this.initialState = builder.initialState;
        this.plannedAction = builder.plannedAction;
        this.processingAttempted = builder.processingAttempted;
        this.skippedComplete = builder.skippedComplete;
        this.blocked = builder.blocked;
        this.processExitCode = builder.processExitCode;
        this.finalStatus = builder.finalStatus;
        this.reasonCode = builder.reasonCode;
        this.message = builder.message;
        this.startEpochMs = builder.startEpochMs;
        this.endEpochMs = builder.endEpochMs;
        this.elapsedMs = builder.elapsedMs;
        this.attemptResultPath = builder.attemptResultPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int sequenceNumber() {
        return sequenceNumber;
    }

    public String imageId() {
        return imageId;
    }

    public BatchMachineImageState initialState() {
        return initialState;
    }

    public BatchMachinePlannedAction plannedAction() {
        return plannedAction;
    }

    public boolean processingAttempted() {
        return processingAttempted;
    }

    public boolean skippedComplete() {
        return skippedComplete;
    }

    public boolean blocked() {
        return blocked;
    }

    public int processExitCode() {
        return processExitCode;
    }

    public String finalStatus() {
        return finalStatus;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public String message() {
        return message;
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

    public Path attemptResultPath() {
        return attemptResultPath;
    }

    public static final class Builder {
        private int sequenceNumber;
        private String imageId;
        private BatchMachineImageState initialState;
        private BatchMachinePlannedAction plannedAction;
        private boolean processingAttempted;
        private boolean skippedComplete;
        private boolean blocked;
        private int processExitCode = -1;
        private String finalStatus = RCsvSupport.NA;
        private String reasonCode = RCsvSupport.NA;
        private String message = RCsvSupport.NA;
        private long startEpochMs;
        private long endEpochMs;
        private long elapsedMs;
        private Path attemptResultPath;

        public Builder sequenceNumber(final int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder imageId(final String imageId) {
            this.imageId = imageId;
            return this;
        }

        public Builder initialState(final BatchMachineImageState initialState) {
            this.initialState = initialState;
            return this;
        }

        public Builder plannedAction(final BatchMachinePlannedAction plannedAction) {
            this.plannedAction = plannedAction;
            return this;
        }

        public Builder processingAttempted(final boolean processingAttempted) {
            this.processingAttempted = processingAttempted;
            return this;
        }

        public Builder skippedComplete(final boolean skippedComplete) {
            this.skippedComplete = skippedComplete;
            return this;
        }

        public Builder blocked(final boolean blocked) {
            this.blocked = blocked;
            return this;
        }

        public Builder processExitCode(final int processExitCode) {
            this.processExitCode = processExitCode;
            return this;
        }

        public Builder finalStatus(final String finalStatus) {
            this.finalStatus = textOrNa(finalStatus);
            return this;
        }

        public Builder reasonCode(final String reasonCode) {
            this.reasonCode = textOrNa(reasonCode);
            return this;
        }

        public Builder message(final String message) {
            this.message = textOrNa(message);
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

        public Builder attemptResultPath(final Path attemptResultPath) {
            this.attemptResultPath = attemptResultPath;
            return this;
        }

        public BatchMachineImageExecutionResult build() {
            if (sequenceNumber < 1) {
                throw new IllegalArgumentException("sequenceNumber must be >= 1");
            }
            SixDigitSampleId.parse(imageId);
            if (initialState == null || plannedAction == null) {
                throw new IllegalArgumentException("state and action must not be null");
            }
            return new BatchMachineImageExecutionResult(this);
        }

        private static String textOrNa(final String value) {
            return value == null || value.trim().isEmpty() ? RCsvSupport.NA : value.trim();
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchMachineImageExecutionResult)) {
            return false;
        }
        final BatchMachineImageExecutionResult other = (BatchMachineImageExecutionResult) obj;
        return sequenceNumber == other.sequenceNumber
                && Objects.equals(imageId, other.imageId)
                && initialState == other.initialState
                && plannedAction == other.plannedAction
                && processingAttempted == other.processingAttempted
                && skippedComplete == other.skippedComplete
                && blocked == other.blocked
                && processExitCode == other.processExitCode
                && Objects.equals(finalStatus, other.finalStatus)
                && Objects.equals(reasonCode, other.reasonCode)
                && Objects.equals(message, other.message)
                && startEpochMs == other.startEpochMs
                && endEpochMs == other.endEpochMs
                && elapsedMs == other.elapsedMs
                && Objects.equals(attemptResultPath, other.attemptResultPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(sequenceNumber), imageId, initialState, plannedAction,
                Boolean.valueOf(processingAttempted), Boolean.valueOf(skippedComplete),
                Boolean.valueOf(blocked), Integer.valueOf(processExitCode), finalStatus,
                reasonCode, message, Long.valueOf(startEpochMs), Long.valueOf(endEpochMs),
                Long.valueOf(elapsedMs), attemptResultPath);
    }
}
