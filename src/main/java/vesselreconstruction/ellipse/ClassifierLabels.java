package vesselreconstruction.ellipse;

/**
 * Fixed Trainable Weka Segmentation class mapping.
 * Class values cannot be renamed in the classifier.
 */
public final class ClassifierLabels {

    public static final int WOOD = 1;
    public static final int RAY = 2;
    public static final int LUMEN = 3;
    public static final int BACKGROUND = 4;

    private ClassifierLabels() {
        throw new AssertionError("ClassifierLabels cannot be instantiated");
    }

    /**
     * Returns the display name for a fixed classifier class value.
     *
     * @param classValue classifier class index
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
}
