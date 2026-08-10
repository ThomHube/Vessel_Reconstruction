package vesselreconstruction.ellipse;

/**
 * Failure while generating an automatic sample-analysis mask.
 */
public final class SampleMaskGenerationException extends RuntimeException {

    private final String sourceTitle;
    private final int maskWidth;
    private final int maskHeight;
    private final String stage;

    public SampleMaskGenerationException(
            final String message,
            final String sourceTitle,
            final int maskWidth,
            final int maskHeight,
            final String stage,
            final Throwable cause) {
        super(formatMessage(message, sourceTitle, maskWidth, maskHeight, stage, cause), cause);
        this.sourceTitle = sourceTitle == null ? "" : sourceTitle;
        this.maskWidth = maskWidth;
        this.maskHeight = maskHeight;
        this.stage = stage == null ? "" : stage;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public int getMaskWidth() {
        return maskWidth;
    }

    public int getMaskHeight() {
        return maskHeight;
    }

    public String getStage() {
        return stage;
    }

    private static String formatMessage(
            final String message,
            final String sourceTitle,
            final int maskWidth,
            final int maskHeight,
            final String stage,
            final Throwable cause) {
        final StringBuilder builder = new StringBuilder();
        if (message != null && !message.trim().isEmpty()) {
            builder.append(message);
        } else {
            builder.append("Automatic sample-mask generation failed");
        }
        builder.append(" [source=").append(sourceTitle == null ? "" : sourceTitle);
        builder.append(", mask=").append(maskWidth).append('x').append(maskHeight);
        builder.append(", stage=").append(stage == null ? "" : stage);
        if (cause != null) {
            builder.append(", exception=").append(cause.getClass().getName());
            final String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.trim().isEmpty()) {
                builder.append(", causeMessage=").append(causeMessage);
            }
        }
        builder.append(']');
        return builder.toString();
    }
}
