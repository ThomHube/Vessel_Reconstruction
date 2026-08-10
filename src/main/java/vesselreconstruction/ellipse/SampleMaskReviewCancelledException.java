package vesselreconstruction.ellipse;

/**
 * Thrown when the user cancels sample-mask review before classification.
 */
public final class SampleMaskReviewCancelledException extends RuntimeException {

    public SampleMaskReviewCancelledException(final String message) {
        super(message);
    }
}
