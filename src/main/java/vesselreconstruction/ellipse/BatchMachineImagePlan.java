package vesselreconstruction.ellipse;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable plan entry for one assigned image on one machine.
 */
public final class BatchMachineImagePlan {

    private final int sequenceNumber;
    private final String imageId;
    private final String machineId;
    private final Path sourceTiff;
    private final String sourceSha256;
    private final BatchMachineImageState state;
    private final BatchMachinePlannedAction plannedAction;
    private final String reasonCode;
    private final String message;
    private final String classificationRunId;
    private final String reconstructionRunId;
    private final Integer tileProfileIndex;

    private BatchMachineImagePlan(final Builder builder) {
        this.sequenceNumber = builder.sequenceNumber;
        this.imageId = builder.imageId;
        this.machineId = builder.machineId;
        this.sourceTiff = builder.sourceTiff;
        this.sourceSha256 = builder.sourceSha256;
        this.state = builder.state;
        this.plannedAction = builder.plannedAction;
        this.reasonCode = builder.reasonCode;
        this.message = builder.message;
        this.classificationRunId = builder.classificationRunId;
        this.reconstructionRunId = builder.reconstructionRunId;
        this.tileProfileIndex = builder.tileProfileIndex;
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

    public String machineId() {
        return machineId;
    }

    public Path sourceTiff() {
        return sourceTiff;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public BatchMachineImageState state() {
        return state;
    }

    public BatchMachinePlannedAction plannedAction() {
        return plannedAction;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public String message() {
        return message;
    }

    public String classificationRunId() {
        return classificationRunId;
    }

    public String reconstructionRunId() {
        return reconstructionRunId;
    }

    public Integer tileProfileIndex() {
        return tileProfileIndex;
    }

    public static final class Builder {
        private int sequenceNumber;
        private String imageId;
        private String machineId;
        private Path sourceTiff;
        private String sourceSha256;
        private BatchMachineImageState state;
        private BatchMachinePlannedAction plannedAction;
        private String reasonCode = RCsvSupport.NA;
        private String message = RCsvSupport.NA;
        private String classificationRunId = RCsvSupport.NA;
        private String reconstructionRunId = RCsvSupport.NA;
        private Integer tileProfileIndex;

        public Builder sequenceNumber(final int sequenceNumber) {
            if (sequenceNumber < 1) {
                throw new IllegalArgumentException(
                        "sequenceNumber must be >= 1: " + sequenceNumber);
            }
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder imageId(final String imageId) {
            this.imageId = imageId;
            return this;
        }

        public Builder machineId(final String machineId) {
            this.machineId = machineId;
            return this;
        }

        public Builder sourceTiff(final Path sourceTiff) {
            this.sourceTiff = sourceTiff;
            return this;
        }

        public Builder sourceSha256(final String sourceSha256) {
            this.sourceSha256 = sourceSha256;
            return this;
        }

        public Builder state(final BatchMachineImageState state) {
            this.state = state;
            return this;
        }

        public Builder plannedAction(final BatchMachinePlannedAction plannedAction) {
            this.plannedAction = plannedAction;
            return this;
        }

        public Builder reasonCode(final String reasonCode) {
            this.reasonCode = reasonCode == null || reasonCode.trim().isEmpty()
                    ? RCsvSupport.NA : reasonCode.trim();
            return this;
        }

        public Builder message(final String message) {
            this.message = message == null || message.trim().isEmpty()
                    ? RCsvSupport.NA : message.trim();
            return this;
        }

        public Builder classificationRunId(final String classificationRunId) {
            this.classificationRunId = classificationRunId == null
                    || classificationRunId.trim().isEmpty()
                    || RCsvSupport.NA.equals(classificationRunId.trim())
                    ? RCsvSupport.NA : classificationRunId.trim();
            return this;
        }

        public Builder reconstructionRunId(final String reconstructionRunId) {
            this.reconstructionRunId = reconstructionRunId == null
                    || reconstructionRunId.trim().isEmpty()
                    || RCsvSupport.NA.equals(reconstructionRunId.trim())
                    ? RCsvSupport.NA : reconstructionRunId.trim();
            return this;
        }

        public Builder tileProfileIndex(final Integer tileProfileIndex) {
            this.tileProfileIndex = tileProfileIndex;
            return this;
        }

        public BatchMachineImagePlan build() {
            if (sequenceNumber < 1) {
                throw new IllegalArgumentException("sequenceNumber must be set");
            }
            SixDigitSampleId.parse(imageId);
            if (machineId == null || machineId.trim().isEmpty()) {
                throw new IllegalArgumentException("machineId must not be blank");
            }
            if (sourceTiff == null) {
                throw new IllegalArgumentException("sourceTiff must not be null");
            }
            if (sourceSha256 == null || sourceSha256.trim().isEmpty()) {
                throw new IllegalArgumentException("sourceSha256 must not be blank");
            }
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
            if (plannedAction == null) {
                plannedAction = BatchMachinePlannedAction.forState(state);
            }
            return new BatchMachineImagePlan(this);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchMachineImagePlan)) {
            return false;
        }
        final BatchMachineImagePlan other = (BatchMachineImagePlan) obj;
        return sequenceNumber == other.sequenceNumber
                && Objects.equals(imageId, other.imageId)
                && Objects.equals(machineId, other.machineId)
                && Objects.equals(sourceTiff, other.sourceTiff)
                && Objects.equals(sourceSha256, other.sourceSha256)
                && state == other.state
                && plannedAction == other.plannedAction
                && Objects.equals(reasonCode, other.reasonCode)
                && Objects.equals(message, other.message)
                && Objects.equals(classificationRunId, other.classificationRunId)
                && Objects.equals(reconstructionRunId, other.reconstructionRunId)
                && Objects.equals(tileProfileIndex, other.tileProfileIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(sequenceNumber), imageId, machineId, sourceTiff, sourceSha256,
                state, plannedAction, reasonCode, message, classificationRunId,
                reconstructionRunId, tileProfileIndex);
    }
}
