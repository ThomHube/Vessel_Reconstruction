package vesselreconstruction.ellipse;

/**
 * How physical pixel size was established for a classification run.
 */
public enum CalibrationMode {
    AUTO_DETECT_RED_SCALE_BAR,
    MEASURE_SCALE_BAR_LINE,
    ENTER_PIXEL_SIZE,
    USE_VALID_MICROMETRE_METADATA
}
