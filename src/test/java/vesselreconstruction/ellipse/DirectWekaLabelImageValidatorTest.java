package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectWekaLabelImageValidatorTest {

    private final DirectWekaLabelImageValidator validator = new DirectWekaLabelImageValidator();

    @Test
    void nullImageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(null, 2, 2, "out"));
    }

    @Test
    void invalidExpectedWidthIsRejected() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 0, 2, "out"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, -1, 2, "out"));
    }

    @Test
    void invalidExpectedHeightIsRejected() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 0, "out"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, -3, "out"));
    }

    @Test
    void blankTitleIsRejected() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 2, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 2, ""));
    }

    @Test
    void dimensionMismatchIsRejected() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 3, 2, "out"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 3, "out"));
    }

    @Test
    void multiPlaneInputIsRejected() {
        final ij.ImageStack stack = new ij.ImageStack(2, 2);
        stack.addSlice(new ByteProcessor(2, 2));
        stack.addSlice(new ByteProcessor(2, 2));
        final ImagePlus image = new ImagePlus("stack", stack);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 2, "out"));
    }

    @Test
    void byteLabelsZeroThroughThreeAreAccepted() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "validated");
        assertEquals("validated", out.getTitle());
        assertTrue(out.getProcessor() instanceof ByteProcessor);
        assertArrayEquals(new byte[] {0, 1, 2, 3}, (byte[]) out.getProcessor().getPixels());
    }

    @Test
    void absentClassesAreAccepted() {
        final ImagePlus image = byteLabels(new byte[] {0, 0, 2, 2}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "absent");
        assertArrayEquals(new byte[] {0, 0, 2, 2}, (byte[]) out.getProcessor().getPixels());
        assertArrayEquals(new long[] {2L, 0L, 2L, 0L}, validator.countClassPixels(out));
    }

    @Test
    void negativeFloatValueIsRejected() {
        final FloatProcessor processor = new FloatProcessor(2, 2);
        processor.setf(0, 0, -1f);
        final ImagePlus image = new ImagePlus("neg", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 2, 2, "out"));
    }

    @Test
    void valueFourIsRejected() {
        final FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, 0, 4f);
        final ImagePlus image = new ImagePlus("four", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 1, 1, "out"));
    }

    @Test
    void fractionalValueIsRejected() {
        final FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, 0, 1.5f);
        final ImagePlus image = new ImagePlus("frac", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 1, 1, "out"));
    }

    @Test
    void nanIsRejected() {
        final FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, 0, Float.NaN);
        final ImagePlus image = new ImagePlus("nan", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 1, 1, "out"));
    }

    @Test
    void positiveInfinityIsRejected() {
        final FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, 0, Float.POSITIVE_INFINITY);
        final ImagePlus image = new ImagePlus("pinf", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 1, 1, "out"));
    }

    @Test
    void negativeInfinityIsRejected() {
        final FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, 0, Float.NEGATIVE_INFINITY);
        final ImagePlus image = new ImagePlus("ninf", processor);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndCopy(image, 1, 1, "out"));
    }

    @Test
    void outputIsByteProcessor() {
        final ImagePlus image = floatLabels(new float[] {0f, 1f, 2f, 3f}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "byte");
        assertTrue(out.getProcessor() instanceof ByteProcessor);
    }

    @Test
    void outputPixelsAreExactlyPreserved() {
        final ImagePlus image = floatLabels(new float[] {3f, 2f, 1f, 0f}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "exact");
        assertArrayEquals(new byte[] {3, 2, 1, 0}, (byte[]) out.getProcessor().getPixels());
    }

    @Test
    void noLabelShiftOccurs() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "noshift");
        final byte[] pixels = (byte[]) out.getProcessor().getPixels();
        assertEquals(0, pixels[0] & 0xff);
        assertEquals(1, pixels[1] & 0xff);
        assertEquals(2, pixels[2] & 0xff);
        assertEquals(3, pixels[3] & 0xff);
    }

    @Test
    void sourceResultPixelsRemainUnchanged() {
        final byte[] sourcePixels = new byte[] {0, 1, 2, 3};
        final ImagePlus image = byteLabels(sourcePixels, 2, 2);
        validator.validateAndCopy(image, 2, 2, "copy");
        assertArrayEquals(new byte[] {0, 1, 2, 3}, (byte[]) image.getProcessor().getPixels());
    }

    @Test
    void outputIsIndependent() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "indep");
        final byte[] outPixels = (byte[]) out.getProcessor().getPixels();
        outPixels[0] = 3;
        assertEquals(0, ((byte[]) image.getProcessor().getPixels())[0] & 0xff);
        assertNotSame(image.getProcessor().getPixels(), out.getProcessor().getPixels());
    }

    @Test
    void calibrationIsPreservedByValue() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        final Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.25;
        calibration.pixelHeight = 2.5;
        calibration.setUnit("mm");
        image.setCalibration(calibration);

        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "cal");
        assertEquals(1.25, out.getCalibration().pixelWidth, 1e-12);
        assertEquals(2.5, out.getCalibration().pixelHeight, 1e-12);
        assertEquals("mm", out.getCalibration().getUnit());
    }

    @Test
    void calibrationObjectIsNotShared() {
        final ImagePlus image = byteLabels(new byte[] {0, 1, 2, 3}, 2, 2);
        final Calibration calibration = new Calibration();
        calibration.pixelWidth = 3.0;
        image.setCalibration(calibration);

        final ImagePlus out = validator.validateAndCopy(image, 2, 2, "cal2");
        assertNotSame(image.getCalibration(), out.getCalibration());
        out.getCalibration().pixelWidth = 9.0;
        assertEquals(3.0, image.getCalibration().pixelWidth, 1e-12);
    }

    @Test
    void classCountsAreCorrect() {
        final ImagePlus image = byteLabels(new byte[] {
                0, 0, 1,
                2, 2, 2,
                3, 3, 1
        }, 3, 3);
        final ImagePlus out = validator.validateAndCopy(image, 3, 3, "counts");
        assertArrayEquals(new long[] {2L, 2L, 3L, 2L}, validator.countClassPixels(out));
    }

    @Test
    void invalidDirectLabelImageIsRejectedByCountClassPixels() {
        assertThrows(IllegalArgumentException.class, () -> validator.countClassPixels(null));

        final ShortProcessor shortProcessor = new ShortProcessor(1, 1);
        shortProcessor.set(0, 0, 1);
        assertThrows(IllegalArgumentException.class,
                () -> validator.countClassPixels(new ImagePlus("short", shortProcessor)));

        final ByteProcessor invalid = new ByteProcessor(1, 1);
        ((byte[]) invalid.getPixels())[0] = 4;
        assertThrows(IllegalArgumentException.class,
                () -> validator.countClassPixels(new ImagePlus("bad", invalid)));
    }

    private static ImagePlus byteLabels(final byte[] pixels, final int width, final int height) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        System.arraycopy(pixels, 0, processor.getPixels(), 0, pixels.length);
        return new ImagePlus("labels", processor);
    }

    private static ImagePlus floatLabels(final float[] pixels, final int width, final int height) {
        final FloatProcessor processor = new FloatProcessor(width, height, pixels.clone());
        return new ImagePlus("float-labels", processor);
    }
}
