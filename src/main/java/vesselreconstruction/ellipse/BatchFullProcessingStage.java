package vesselreconstruction.ellipse;

/**
 * Explicit Phase-3 child-stage types for later two-process orchestration.
 *
 * <p>{@code CLASSIFICATION} and {@code RECONSTRUCTION} identify which Fiji
 * child process a stage-specific job or stage result belongs to. Scientific
 * behavior is unchanged in the mini-iteration that introduced this enum; the
 * current runtime may still use the temporary combined execution path until
 * orchestration is wired to launch each stage in its own Fiji process.</p>
 */
public enum BatchFullProcessingStage {
    CLASSIFICATION,
    RECONSTRUCTION;

    /**
     * Strict parser: rejects {@code null}, blank, lowercase, and any alias.
     * Accepts exactly {@code CLASSIFICATION} or {@code RECONSTRUCTION}
     * (leading/trailing whitespace is trimmed before the exact match).
     */
    public static BatchFullProcessingStage parse(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("processing_stage must not be null");
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("processing_stage must not be blank");
        }
        if (CLASSIFICATION.name().equals(trimmed)) {
            return CLASSIFICATION;
        }
        if (RECONSTRUCTION.name().equals(trimmed)) {
            return RECONSTRUCTION;
        }
        throw new IllegalArgumentException(
                "Unknown processing_stage: " + value
                        + " (expected exactly CLASSIFICATION or RECONSTRUCTION)");
    }
}
