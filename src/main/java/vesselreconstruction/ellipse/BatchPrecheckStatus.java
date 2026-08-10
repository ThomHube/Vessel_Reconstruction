package vesselreconstruction.ellipse;

/**
 * Stable Phase-2 precheck status values. Free text must not be used as the
 * authoritative status.
 */
public enum BatchPrecheckStatus {
    PRECHECK_SUCCESS,
    REVIEW_REQUIRED_MASK,
    REVIEW_REQUIRED_SCALE_BAR,
    INVALID_FILENAME,
    INVALID_SOURCE,
    STALE_SOURCE,
    INVALID_CONFIGURATION,
    FAILED_OPEN_IMAGE,
    FAILED_MASK_GENERATION,
    FAILED_SCALE_BAR_DETECTION,
    FAILED_PREVIEW_SAVE,
    FAILED_TIMEOUT,
    FAILED_UNEXPECTED;

    public boolean isSuccess() {
        return this == PRECHECK_SUCCESS;
    }

    public boolean isReviewRequired() {
        return this == REVIEW_REQUIRED_MASK || this == REVIEW_REQUIRED_SCALE_BAR;
    }

    public boolean isFailure() {
        return !isSuccess() && !isReviewRequired();
    }

    public static BatchPrecheckStatus parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        try {
            return BatchPrecheckStatus.valueOf(text.trim());
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown precheck status: " + text, ex);
        }
    }
}
