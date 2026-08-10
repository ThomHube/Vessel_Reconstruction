package vesselreconstruction.ellipse;

/**
 * Persisted status of one assigned image for Phase-4 machine-run planning.
 *
 * <p>This enum describes what is already known from precheck and production
 * outputs. It does not describe a live running process.</p>
 */
public enum BatchMachineImageState {
    PRECHECK_MISSING,
    PRECHECK_FAILED,
    PRECHECK_REVIEW_REQUIRED,
    READY_FOR_FULL_PROCESSING,
    CLASSIFICATION_COMPLETE,
    FULL_SUCCESS,
    CANONICAL_CONFLICT,
    UNKNOWN_INVALID_STATE;

    public static BatchMachineImageState parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        try {
            return BatchMachineImageState.valueOf(text.trim());
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown machine image state: " + text, ex);
        }
    }
}
