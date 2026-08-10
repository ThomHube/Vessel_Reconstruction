package vesselreconstruction.ellipse;

/**
 * Stable Phase-3 full single-image processing status values.
 */
public enum BatchFullProcessingStatus {
    FULL_SUCCESS,
    PRECHECK_NOT_FOUND,
    PRECHECK_NOT_SUCCESSFUL,
    PRECHECK_STALE_SOURCE,
    PRECHECK_STALE_CONFIGURATION,
    INVALID_JOB,
    INVALID_SOURCE,
    INVALID_CLASSIFIER,
    FAILED_MASK_REPRODUCTION,
    FAILED_SCALE_BAR_REPRODUCTION,
    FAILED_CLASSIFICATION,
    FAILED_CLASSIFICATION_SAVE,
    FAILED_RECONSTRUCTION,
    FAILED_RECONSTRUCTION_SAVE,
    FAILED_OUTPUT_VALIDATION,
    CANONICAL_CONFLICT,
    FAILED_OUT_OF_MEMORY,
    FAILED_TIMEOUT,
    FAILED_UNEXPECTED;

    public boolean isSuccess() {
        return this == FULL_SUCCESS;
    }

    public boolean isFailure() {
        return !isSuccess();
    }

    public boolean isPrecheckProblem() {
        return this == PRECHECK_NOT_FOUND
                || this == PRECHECK_NOT_SUCCESSFUL
                || this == PRECHECK_STALE_SOURCE
                || this == PRECHECK_STALE_CONFIGURATION;
    }

    public static BatchFullProcessingStatus parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        try {
            return BatchFullProcessingStatus.valueOf(text.trim());
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown full-processing status: " + text, ex);
        }
    }
}
