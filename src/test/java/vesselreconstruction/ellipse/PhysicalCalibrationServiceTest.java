package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.gui.Line;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalCalibrationServiceTest {

    private static final double SCALE_BAR_PHYSICAL_UM = 500.0;
    private static final double SCALE_BAR_LENGTH_PX = 456.0;
    private static final double EXPECTED_PIXEL_SIZE =
            1.0964912280701755;

    private final PhysicalCalibrationService service = new PhysicalCalibrationService();

    @Test
    void scaleBarProducesExactPixelSize() {
        final PhysicalCalibration calibration = PhysicalCalibration.fromScaleBar(
                SCALE_BAR_PHYSICAL_UM, SCALE_BAR_LENGTH_PX);
        assertEquals(0, Double.compare(EXPECTED_PIXEL_SIZE, calibration.getPixelWidthUm()));
        assertEquals(0, Double.compare(EXPECTED_PIXEL_SIZE, calibration.getPixelHeightUm()));
        assertEquals(CalibrationMode.MEASURE_SCALE_BAR_LINE,
                calibration.getCalibrationMethod());
        assertEquals(Double.valueOf(SCALE_BAR_PHYSICAL_UM),
                calibration.getScaleBarPhysicalLengthUm());
        assertEquals(Double.valueOf(SCALE_BAR_LENGTH_PX),
                calibration.getScaleBarMeasuredLengthPx());
    }

    @Test
    void scaleBarLineRoiProducesExactPixelSize() {
        final ImagePlus image = new ImagePlus("scale", new ByteProcessor(1, 1));
        image.setRoi(new Line(0.0, 0.0, SCALE_BAR_LENGTH_PX, 0.0));
        final PhysicalCalibration calibration = service.fromScaleBarLine(
                image, SCALE_BAR_PHYSICAL_UM);
        assertEquals(0, Double.compare(EXPECTED_PIXEL_SIZE, calibration.getPixelWidthUm()));
        assertEquals(0, Double.compare(EXPECTED_PIXEL_SIZE, calibration.getPixelHeightUm()));
    }

    @Test
    void scaleBarRejectsZeroPhysicalLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(0.0, SCALE_BAR_LENGTH_PX));
    }

    @Test
    void scaleBarRejectsNegativePhysicalLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(-1.0, SCALE_BAR_LENGTH_PX));
    }

    @Test
    void scaleBarRejectsNonFinitePhysicalLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(Double.NaN, SCALE_BAR_LENGTH_PX));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(
                        Double.POSITIVE_INFINITY, SCALE_BAR_LENGTH_PX));
    }

    @Test
    void scaleBarRejectsZeroMeasuredLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(SCALE_BAR_PHYSICAL_UM, 0.0));
    }

    @Test
    void scaleBarRejectsNegativeMeasuredLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(SCALE_BAR_PHYSICAL_UM, -1.0));
    }

    @Test
    void scaleBarRejectsNonFiniteMeasuredLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(
                        SCALE_BAR_PHYSICAL_UM, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalCalibration.fromScaleBar(
                        SCALE_BAR_PHYSICAL_UM, Double.POSITIVE_INFINITY));
    }

    @Test
    void enteredIsotropicBlankHeightUsesWidth() {
        final PhysicalCalibration calibration = service.fromEnteredPixelSize(
                2.5, null);
        assertEquals(0, Double.compare(2.5, calibration.getPixelWidthUm()));
        assertEquals(0, Double.compare(2.5, calibration.getPixelHeightUm()));
        assertEquals(CalibrationMode.ENTER_PIXEL_SIZE,
                calibration.getCalibrationMethod());
    }

    @Test
    void enteredAnisotropicSizesArePreserved() {
        final PhysicalCalibration calibration = service.fromEnteredPixelSize(
                1.5, Double.valueOf(2.25));
        assertEquals(0, Double.compare(1.5, calibration.getPixelWidthUm()));
        assertEquals(0, Double.compare(2.25, calibration.getPixelHeightUm()));
    }

    @Test
    void enteredSizesRejectZeroWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(0.0, null));
    }

    @Test
    void enteredSizesRejectNegativeWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(-1.0, null));
    }

    @Test
    void enteredSizesRejectNonFiniteWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(Double.NaN, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(Double.POSITIVE_INFINITY, null));
    }

    @Test
    void enteredSizesRejectZeroHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(1.0, Double.valueOf(0.0)));
    }

    @Test
    void enteredSizesRejectNegativeHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(1.0, Double.valueOf(-1.0)));
    }

    @Test
    void enteredSizesRejectNonFiniteHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(1.0, Double.valueOf(Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> service.fromEnteredPixelSize(
                        1.0, Double.valueOf(Double.POSITIVE_INFINITY)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "µm", "um", "micron", "microns", "micrometer", "micrometers"
    })
    void isMicrometreUnitAcceptsKnownUnits(final String unit) {
        assertTrue(PhysicalCalibrationService.isMicrometreUnit(unit));
    }

    @ParameterizedTest
    @ValueSource(strings = {"inch", "inches", "pixel", "pixels", ""})
    void isMicrometreUnitRejectsKnownNonMicrometreUnits(final String unit) {
        assertFalse(PhysicalCalibrationService.isMicrometreUnit(unit));
    }

    @Test
    void isMicrometreUnitRejectsNull() {
        assertFalse(PhysicalCalibrationService.isMicrometreUnit(null));
    }

    @Test
    void fromValidMicrometreMetadataAcceptsPositiveCalibration() {
        final ImagePlus image = new ImagePlus("calibrated", new ByteProcessor(1, 1));
        final Calibration calibration = new Calibration();
        calibration.setUnit("µm");
        calibration.pixelWidth = 1.25;
        calibration.pixelHeight = 1.75;
        image.setCalibration(calibration);

        final PhysicalCalibration result = service.fromValidMicrometreMetadata(image);
        assertEquals(0, Double.compare(1.25, result.getPixelWidthUm()));
        assertEquals(0, Double.compare(1.75, result.getPixelHeightUm()));
        assertEquals(CalibrationMode.USE_VALID_MICROMETRE_METADATA,
                result.getCalibrationMethod());
        assertEquals("µm", result.getSourceMetadataUnit());
    }

    @Test
    void fromValidMicrometreMetadataRejectsInchUnit() {
        final ImagePlus image = calibratedImage("inch", 1.0, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> service.fromValidMicrometreMetadata(image));
    }

    @Test
    void fromValidMicrometreMetadataRejectsPixelUnit() {
        final ImagePlus image = calibratedImage("pixel", 1.0, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> service.fromValidMicrometreMetadata(image));
    }

    @Test
    void fromValidMicrometreMetadataRejectsZeroCalibration() {
        final ImagePlus image = calibratedImage("µm", 0.0, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> service.fromValidMicrometreMetadata(image));
        final ImagePlus zeroHeight = calibratedImage("µm", 1.0, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> service.fromValidMicrometreMetadata(zeroHeight));
    }

    private static ImagePlus calibratedImage(
            final String unit,
            final double width,
            final double height) {
        final ImagePlus image = new ImagePlus("calibrated", new ByteProcessor(1, 1));
        final Calibration calibration = new Calibration();
        calibration.setUnit(unit);
        calibration.pixelWidth = width;
        calibration.pixelHeight = height;
        image.setCalibration(calibration);
        return image;
    }
}
