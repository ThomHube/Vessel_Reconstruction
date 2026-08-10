package vesselreconstruction.ellipse;

/**
 * Terminal status of one Phase-4 machine supervisor execution.
 */
public enum BatchMachineSupervisorStatus {
    MACHINE_SUCCESS,
    MACHINE_COMPLETED_WITH_BLOCKED_IMAGES,
    MACHINE_COMPLETED_WITH_FAILURES,
    MACHINE_FAILED_CONFIGURATION,
    MACHINE_FAILED_UNEXPECTED;

    public static BatchMachineSupervisorStatus parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        try {
            return BatchMachineSupervisorStatus.valueOf(text.trim());
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown supervisor status: " + text, ex);
        }
    }

    public boolean isSuccess() {
        return this == MACHINE_SUCCESS;
    }
}
