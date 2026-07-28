package vesselreconstruction.ellipse;

/**
 * Fixed Trainable Weka Segmentation class mapping.
 *
 * Human-facing class numbers are one-based and cannot be renamed.
 * Direct "Create result" 8-bit images store zero-based class indices as pixel values.
 */
public final class ClassifierLabels {

    /** Human-facing Weka class number for Wood. */
    public static final int WOOD = 1;
    /** Human-facing Weka class number for Ray. */
    public static final int RAY = 2;
    /** Human-facing Weka class number for Lumen. */
    public static final int LUMEN = 3;
    /** Human-facing Weka class number for Background. */
    public static final int BACKGROUND = 4;

    /** Direct Weka result pixel value for Class 1 Wood. */
    public static final int WOOD_RESULT_VALUE = 0;
    /** Direct Weka result pixel value for Class 2 Ray. */
    public static final int RAY_RESULT_VALUE = 1;
    /** Direct Weka result pixel value for Class 3 Lumen. */
    public static final int LUMEN_RESULT_VALUE = 2;
    /** Direct Weka result pixel value for Class 4 Background. */
    public static final int BACKGROUND_RESULT_VALUE = 3;

    private ClassifierLabels() {
        throw new AssertionError("ClassifierLabels cannot be instantiated");
    }

    /**
     * Returns the display name for a fixed human-facing classifier class number.
     *
     * @param classValue one-based class number
     * @return display name for the class
     * @throws IllegalArgumentException if {@code classValue} is not supported
     */
    public static String nameOf(int classValue) {
        switch (classValue) {
            case WOOD:
                return "Wood";
            case RAY:
                return "Ray";
            case LUMEN:
                return "Lumen";
            case BACKGROUND:
                return "Background";
            default:
                throw new IllegalArgumentException(
                        "Unsupported classifier class value: " + classValue);
        }
    }

    /**
     * Converts a human-facing one-based class number to the direct Weka result pixel value.
     */
    public static int resultValueOfClassNumber(int classNumber) {
        switch (classNumber) {
            case WOOD:
                return WOOD_RESULT_VALUE;
            case RAY:
                return RAY_RESULT_VALUE;
            case LUMEN:
                return LUMEN_RESULT_VALUE;
            case BACKGROUND:
                return BACKGROUND_RESULT_VALUE;
            default:
                throw new IllegalArgumentException(
                        "Unsupported classifier class number: " + classNumber);
        }
    }

    /**
     * Converts a direct Weka result pixel value to the human-facing one-based class number.
     */
    public static int classNumberOfResultValue(int resultValue) {
        switch (resultValue) {
            case WOOD_RESULT_VALUE:
                return WOOD;
            case RAY_RESULT_VALUE:
                return RAY;
            case LUMEN_RESULT_VALUE:
                return LUMEN;
            case BACKGROUND_RESULT_VALUE:
                return BACKGROUND;
            default:
                throw new IllegalArgumentException(
                        "Unsupported Weka result pixel value: " + resultValue);
        }
    }

    /**
     * Returns the semantic class name for a direct Weka result pixel value.
     */
    public static String nameOfResultValue(int resultValue) {
        return nameOf(classNumberOfResultValue(resultValue));
    }
}
