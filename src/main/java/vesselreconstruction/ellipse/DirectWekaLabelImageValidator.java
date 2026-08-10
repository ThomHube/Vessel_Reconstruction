package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Validates Trainable Weka label results into the project's direct raw encoding:
 * 0=Wood, 1=Ray, 2=Lumen, 3=Background.
 *
 * <p>Does not shift labels and does not infer encoding from display colors.</p>
 */
public final class DirectWekaLabelImageValidator {

    public ImagePlus validateAndCopy(
            final ImagePlus wekaResult,
            final int expectedWidth,
            final int expectedHeight,
            final String outputTitle) {
        if (wekaResult == null) {
            throw new IllegalArgumentException("wekaResult must not be null");
        }
        if (expectedWidth <= 0) {
            throw new IllegalArgumentException(
                    "expectedWidth must be positive: " + expectedWidth);
        }
        if (expectedHeight <= 0) {
            throw new IllegalArgumentException(
                    "expectedHeight must be positive: " + expectedHeight);
        }
        if (outputTitle == null || outputTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("outputTitle must not be null or blank");
        }
        if (wekaResult.getStackSize() != 1) {
            throw new IllegalArgumentException(
                    "wekaResult must contain exactly one image plane; stackSize="
                            + wekaResult.getStackSize());
        }
        if (wekaResult.getWidth() != expectedWidth || wekaResult.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(
                    "wekaResult dimensions mismatch: got "
                            + wekaResult.getWidth() + "x" + wekaResult.getHeight()
                            + ", expected " + expectedWidth + "x" + expectedHeight);
        }

        final ImageProcessor processor = wekaResult.getProcessor();
        if (processor == null) {
            throw new IllegalArgumentException("wekaResult processor must not be null");
        }

        final ByteProcessor output = new ByteProcessor(expectedWidth, expectedHeight);
        final byte[] outPixels = (byte[]) output.getPixels();
        for (int y = 0; y < expectedHeight; y++) {
            for (int x = 0; x < expectedWidth; x++) {
                final float raw = processor.getf(x, y);
                if (!Float.isFinite(raw)) {
                    throw new IllegalArgumentException(
                            "non-finite Weka label at (" + x + "," + y + "): " + raw);
                }
                if (raw != Math.rint(raw)) {
                    throw new IllegalArgumentException(
                            "non-integral Weka label at (" + x + "," + y + "): " + raw);
                }
                final int value = Math.round(raw);
                if (value < 0 || value > 3) {
                    throw new IllegalArgumentException(
                            "Weka label out of range 0..3 at (" + x + "," + y + "): " + value);
                }
                outPixels[y * expectedWidth + x] = (byte) value;
            }
        }

        final ImagePlus result = new ImagePlus(outputTitle, output);
        final Calibration sourceCalibration = wekaResult.getCalibration();
        if (sourceCalibration != null) {
            result.setCalibration(sourceCalibration.copy());
        }
        return result;
    }

    public long[] countClassPixels(final ImagePlus directLabelImage) {
        if (directLabelImage == null) {
            throw new IllegalArgumentException("directLabelImage must not be null");
        }
        if (directLabelImage.getStackSize() != 1) {
            throw new IllegalArgumentException(
                    "directLabelImage must contain exactly one image plane");
        }
        final ImageProcessor processor = directLabelImage.getProcessor();
        if (!(processor instanceof ByteProcessor)) {
            throw new IllegalArgumentException(
                    "directLabelImage must be an 8-bit ByteProcessor");
        }
        final int width = processor.getWidth();
        final int height = processor.getHeight();
        final byte[] pixels = (byte[]) processor.getPixels();
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("directLabelImage pixels are invalid");
        }
        final long[] counts = new long[4];
        for (int i = 0; i < pixels.length; i++) {
            final int value = pixels[i] & 0xff;
            if (value < 0 || value > 3) {
                throw new IllegalArgumentException(
                        "direct label out of range 0..3 at index " + i + ": " + value);
            }
            counts[value]++;
        }
        return counts;
    }
}
