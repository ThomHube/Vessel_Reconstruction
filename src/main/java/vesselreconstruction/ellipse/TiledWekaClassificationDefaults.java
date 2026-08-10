package vesselreconstruction.ellipse;

/**
 * Default dialog values for
 * {@link TiledWekaClassificationValidationPlugin}.
 *
 * <p>Kept as an explicit immutable source of truth so unit tests can verify the
 * values used by the real plugin without opening ImageJ UI windows.</p>
 */
public final class TiledWekaClassificationDefaults {

    public static final int CORE_TILE_WIDTH_PIXELS = 2048;
    public static final int CORE_TILE_HEIGHT_PIXELS = 2048;
    public static final int THREADS_PER_TILE = 1;
    public static final int SEAM_AUDIT_HALF_WIDTH_PIXELS = 16;
    public static final boolean RUN_UNTILED_REFERENCE_COMPARISON = false;
    public static final int SAMPLE_MASK_DOWNSAMPLE_FACTOR = 8;
    public static final int SAMPLE_MASK_EXPANSION_MARGIN_ORIGINAL_PIXELS = 32;
    public static final boolean REVIEW_AND_EDIT_SAMPLE_MASK = true;

    public static final boolean CROP_OUTPUT_TO_APPROVED_MASK_EXTENT = true;
    public static final int ADDITIONAL_CROP_PADDING_ORIGINAL_PIXELS = 0;
    public static final boolean SAVE_CROPPED_CLASSIFIED_TIFF_AUTOMATICALLY = true;
    public static final boolean SAVE_APPROVED_DOWNSAMPLED_MASK_AUTOMATICALLY = true;
    public static final boolean SAVE_CROPPED_RGB_COPY = true;

    public static final CalibrationMode CALIBRATION_MODE =
            CalibrationMode.AUTO_DETECT_RED_SCALE_BAR;
    public static final double SCALE_BAR_PHYSICAL_LENGTH_UM = 500.0;
    public static final int SEGMENT_ORDER = 1;
    public static final SegmentOrientation SEGMENT_ORIENTATION =
            SegmentOrientation.UNSPECIFIED;
    public static final String OUTPUT_DIRECTORY_NAME = "lumen_dataset";

    public static final String LABEL_CORE_TILE_WIDTH = "Core tile width in pixels:";
    public static final String LABEL_CORE_TILE_HEIGHT = "Core tile height in pixels:";
    public static final String LABEL_THREADS_PER_TILE = "Threads per tile:";
    public static final String LABEL_SEAM_AUDIT_HALF_WIDTH =
            "Seam audit half-width in pixels:";
    public static final String LABEL_RUN_UNTILED_REFERENCE =
            "Run untiled reference comparison";
    public static final String LABEL_SAMPLE_MASK_DOWNSAMPLE_FACTOR =
            "Sample mask downsample factor:";
    public static final String LABEL_SAMPLE_MASK_EXPANSION_MARGIN =
            "Sample mask expansion margin in original pixels:";
    public static final String LABEL_REVIEW_SAMPLE_MASK =
            "Review and edit sample mask before classification";

    public static final String LABEL_CROP_OUTPUT =
            "Crop output to approved mask extent";
    public static final String LABEL_CROP_PADDING =
            "Additional crop padding in original pixels:";
    public static final String LABEL_SAVE_CLASSIFIED =
            "Save cropped classified TIFF automatically";
    public static final String LABEL_SAVE_MASK =
            "Save approved downsampled mask automatically";
    public static final String LABEL_SAVE_RGB_CROP =
            "Save cropped RGB copy";

    public static final String LABEL_SAMPLE_ID = "Sample ID:";
    public static final String LABEL_SEGMENT_ID = "Segment ID:";
    public static final String LABEL_SEGMENT_ORDER = "Segment order:";
    public static final String LABEL_SEGMENT_ORIENTATION = "Segment orientation:";
    public static final String LABEL_GLOBAL_SEGMENT_START =
            "Optional global segment start in micrometres:";
    public static final String LABEL_OUTPUT_DIRECTORY = "Dataset root directory:";

    public static final String LABEL_CALIBRATION_MODE = "Calibration mode:";
    public static final String LABEL_SCALE_BAR_LENGTH =
            "Scale bar physical length in micrometres:";
    public static final String LABEL_ENTERED_PIXEL_WIDTH =
            "Entered pixel width in micrometres:";
    public static final String LABEL_ENTERED_PIXEL_HEIGHT =
            "Entered pixel height in micrometres:";

    /** Classifier index forced for every pixel outside the approved sample mask. */
    public static final int OUTSIDE_MASK_BACKGROUND_CLASS = 3;

    public static final String[] ORIENTATION_CHOICES = new String[] {
            SegmentOrientation.UNSPECIFIED.name(),
            SegmentOrientation.TOP_TO_BOTTOM.name(),
            SegmentOrientation.BOTTOM_TO_TOP.name(),
            SegmentOrientation.LEFT_TO_RIGHT.name(),
            SegmentOrientation.RIGHT_TO_LEFT.name()
    };

    public static final String[] CALIBRATION_MODE_CHOICES = new String[] {
            CalibrationMode.AUTO_DETECT_RED_SCALE_BAR.name(),
            CalibrationMode.MEASURE_SCALE_BAR_LINE.name(),
            CalibrationMode.ENTER_PIXEL_SIZE.name(),
            CalibrationMode.USE_VALID_MICROMETRE_METADATA.name()
    };

    private TiledWekaClassificationDefaults() {
    }
}
