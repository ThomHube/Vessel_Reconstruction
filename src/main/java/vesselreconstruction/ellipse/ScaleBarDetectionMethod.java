package vesselreconstruction.ellipse;

/**
 * Measurement method used by {@link AutomaticScaleBarDetector}.
 */
public enum ScaleBarDetectionMethod {
    /** Preferred: distance between terminal-tick centerlines. */
    TERMINAL_TICK_CENTERLINES,
    /** Explicit lower-confidence fallback when ticks are absent. */
    SHAFT_ENDPOINTS,
    NONE
}
