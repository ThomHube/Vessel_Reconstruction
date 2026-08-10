package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Calibration;

import java.util.Locale;

/**
 * Establishes physical pixel calibration from scale-bar measurement, direct
 * entry, or validated micrometre metadata.
 */
public final class PhysicalCalibrationService {

    public PhysicalCalibration fromScaleBarLine(
            final ImagePlus sourceImage,
            final double scaleBarPhysicalLengthUm) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        final Roi roi = sourceImage.getRoi();
        if (roi == null) {
            throw new IllegalArgumentException(
                    "No ROI found. Draw a straight line along the scale bar.");
        }
        if (!(roi instanceof Line)) {
            throw new IllegalArgumentException(
                    "Scale-bar calibration requires a straight line ROI.");
        }
        final Line line = (Line) roi;
        final double dx = line.x2d - line.x1d;
        final double dy = line.y2d - line.y1d;
        final double lengthPx = Math.sqrt(dx * dx + dy * dy);
        return PhysicalCalibration.fromScaleBar(scaleBarPhysicalLengthUm, lengthPx);
    }

    public PhysicalCalibration fromEnteredPixelSize(
            final double pixelWidthUm,
            final Double pixelHeightUmOrBlank) {
        return PhysicalCalibration.fromEnteredPixelSize(pixelWidthUm, pixelHeightUmOrBlank);
    }

    public PhysicalCalibration fromValidMicrometreMetadata(final ImagePlus sourceImage) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        final Calibration calibration = sourceImage.getCalibration();
        if (calibration == null) {
            throw new IllegalArgumentException(
                    "Source image has no calibration metadata.");
        }
        final String unit = calibration.getUnit();
        if (!isMicrometreUnit(unit)) {
            throw new IllegalArgumentException(
                    "Source metadata unit is not micrometre-compatible: "
                            + String.valueOf(unit));
        }
        final double width = calibration.pixelWidth;
        final double height = calibration.pixelHeight;
        if (!(width > 0.0) || Double.isNaN(width) || Double.isInfinite(width)
                || !(height > 0.0) || Double.isNaN(height) || Double.isInfinite(height)) {
            throw new IllegalArgumentException(
                    "Source micrometre metadata pixel size is invalid: width="
                            + width + ", height=" + height);
        }
        return new PhysicalCalibration(
                width,
                height,
                CalibrationMode.USE_VALID_MICROMETRE_METADATA,
                null,
                null,
                unit == null ? "" : unit,
                Double.valueOf(width),
                Double.valueOf(height));
    }

    public static boolean isMicrometreUnit(final String unit) {
        if (unit == null) {
            return false;
        }
        final String normalized = unit.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        if ("µm".equals(normalized)
                || "um".equals(normalized)
                || "micron".equals(normalized)
                || "microns".equals(normalized)
                || "micrometer".equals(normalized)
                || "micrometers".equals(normalized)
                || "micrometre".equals(normalized)
                || "micrometres".equals(normalized)) {
            return true;
        }
        // Reject known non-micrometre units explicitly.
        if ("inch".equals(normalized)
                || "inches".equals(normalized)
                || "pixel".equals(normalized)
                || "pixels".equals(normalized)) {
            return false;
        }
        return false;
    }

    public static void applyToImage(final ImagePlus image, final PhysicalCalibration calibration) {
        if (image == null || calibration == null) {
            throw new IllegalArgumentException("image and calibration must not be null");
        }
        final Calibration ijCalibration = image.getCalibration() == null
                ? new Calibration()
                : image.getCalibration().copy();
        ijCalibration.pixelWidth = calibration.getPixelWidthUm();
        ijCalibration.pixelHeight = calibration.getPixelHeightUm();
        ijCalibration.setUnit(PhysicalCalibration.UNIT_MICROMETRE);
        image.setCalibration(ijCalibration);
    }
}
