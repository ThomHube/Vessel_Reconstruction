package vesselreconstruction.ellipse;

/**
 * Planned next action for one assigned image in a Phase-4 machine run plan.
 *
 * <p>Planning information only; the planner never executes these actions.</p>
 */
public enum BatchMachinePlannedAction {
    RUN_PRECHECK,
    WAIT_FOR_REVIEW,
    RUN_FULL_PROCESSING,
    RESUME_RECONSTRUCTION,
    SKIP_COMPLETE,
    STOP_FOR_CANONICAL_CONFLICT,
    STOP_FOR_INVALID_STATE;

    public static BatchMachinePlannedAction parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("planned action must not be blank");
        }
        try {
            return BatchMachinePlannedAction.valueOf(text.trim());
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown planned action: " + text, ex);
        }
    }

    /**
     * Maps a persisted image state to the corresponding planned action.
     */
    public static BatchMachinePlannedAction forState(final BatchMachineImageState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        switch (state) {
            case PRECHECK_MISSING:
            case PRECHECK_FAILED:
                return RUN_PRECHECK;
            case PRECHECK_REVIEW_REQUIRED:
                return WAIT_FOR_REVIEW;
            case READY_FOR_FULL_PROCESSING:
                return RUN_FULL_PROCESSING;
            case CLASSIFICATION_COMPLETE:
                return RESUME_RECONSTRUCTION;
            case FULL_SUCCESS:
                return SKIP_COMPLETE;
            case CANONICAL_CONFLICT:
                return STOP_FOR_CANONICAL_CONFLICT;
            case UNKNOWN_INVALID_STATE:
            default:
                return STOP_FOR_INVALID_STATE;
        }
    }
}
