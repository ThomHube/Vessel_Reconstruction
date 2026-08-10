package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable physical calibration for derived Lumen outputs.
 */
public final class PhysicalCalibration {

    public static final String UNIT_MICROMETRE = "µm";

    private final double pixelWidthUm;
    private final double pixelHeightUm;
    private final String unit;
    private final CalibrationMode calibrationMethod;
    private final Double scaleBarPhysicalLengthUm;
    private final Double scaleBarMeasuredLengthPx;
    private final String sourceMetadataUnit;
    private final Double sourceMetadataPixelWidth;
    private final Double sourceMetadataPixelHeight;

    public PhysicalCalibration(
            final double pixelWidthUm,
            final double pixelHeightUm,
            final CalibrationMode calibrationMethod,
            final Double scaleBarPhysicalLengthUm,
            final Double scaleBarMeasuredLengthPx,
            final String sourceMetadataUnit,
            final Double sourceMetadataPixelWidth,
            final Double sourceMetadataPixelHeight) {
        if (!(pixelWidthUm > 0.0) || Double.isNaN(pixelWidthUm) || Double.isInfinite(pixelWidthUm)) {
            throw new IllegalArgumentException("pixelWidthUm must be finite and > 0");
        }
        if (!(pixelHeightUm > 0.0) || Double.isNaN(pixelHeightUm) || Double.isInfinite(pixelHeightUm)) {
            throw new IllegalArgumentException("pixelHeightUm must be finite and > 0");
        }
        if (calibrationMethod == null) {
            throw new IllegalArgumentException("calibrationMethod must not be null");
        }
        this.pixelWidthUm = pixelWidthUm;
        this.pixelHeightUm = pixelHeightUm;
        this.unit = UNIT_MICROMETRE;
        this.calibrationMethod = calibrationMethod;
        this.scaleBarPhysicalLengthUm = scaleBarPhysicalLengthUm;
        this.scaleBarMeasuredLengthPx = scaleBarMeasuredLengthPx;
        this.sourceMetadataUnit = sourceMetadataUnit == null ? "" : sourceMetadataUnit;
        this.sourceMetadataPixelWidth = sourceMetadataPixelWidth;
        this.sourceMetadataPixelHeight = sourceMetadataPixelHeight;
    }

    public static PhysicalCalibration fromScaleBar(
            final double physicalLengthUm,
            final double measuredLengthPx) {
        return fromScaleBar(
                physicalLengthUm,
                measuredLengthPx,
                CalibrationMode.MEASURE_SCALE_BAR_LINE);
    }

    public static PhysicalCalibration fromAutoDetectedScaleBar(
            final double physicalLengthUm,
            final double measuredCenterToCenterLengthPx) {
        return fromScaleBar(
                physicalLengthUm,
                measuredCenterToCenterLengthPx,
                CalibrationMode.AUTO_DETECT_RED_SCALE_BAR);
    }

    public static PhysicalCalibration fromScaleBar(
            final double physicalLengthUm,
            final double measuredLengthPx,
            final CalibrationMode method) {
        if (!(physicalLengthUm > 0.0) || Double.isNaN(physicalLengthUm)
                || Double.isInfinite(physicalLengthUm)) {
            throw new IllegalArgumentException(
                    "Scale bar physical length must be finite and > 0");
        }
        if (!(measuredLengthPx > 0.0) || Double.isNaN(measuredLengthPx)
                || Double.isInfinite(measuredLengthPx)) {
            throw new IllegalArgumentException(
                    "Measured scale-bar length must be finite and > 0");
        }
        if (method != CalibrationMode.MEASURE_SCALE_BAR_LINE
                && method != CalibrationMode.AUTO_DETECT_RED_SCALE_BAR) {
            throw new IllegalArgumentException(
                    "Scale-bar factory requires a scale-bar calibration method");
        }
        final double pixelSize = physicalLengthUm / measuredLengthPx;
        return new PhysicalCalibration(
                pixelSize,
                pixelSize,
                method,
                Double.valueOf(physicalLengthUm),
                Double.valueOf(measuredLengthPx),
                "",
                null,
                null);
    }

    public static PhysicalCalibration fromEnteredPixelSize(
            final double pixelWidthUm,
            final Double pixelHeightUmOrNull) {
        final double height = pixelHeightUmOrNull == null
                ? pixelWidthUm : pixelHeightUmOrNull.doubleValue();
        return new PhysicalCalibration(
                pixelWidthUm,
                height,
                CalibrationMode.ENTER_PIXEL_SIZE,
                null,
                null,
                "",
                null,
                null);
    }

    public double getPixelWidthUm() {
        return pixelWidthUm;
    }

    public double getPixelHeightUm() {
        return pixelHeightUm;
    }

    public String getUnit() {
        return unit;
    }

    public CalibrationMode getCalibrationMethod() {
        return calibrationMethod;
    }

    public Double getScaleBarPhysicalLengthUm() {
        return scaleBarPhysicalLengthUm;
    }

    public Double getScaleBarMeasuredLengthPx() {
        return scaleBarMeasuredLengthPx;
    }

    public String getSourceMetadataUnit() {
        return sourceMetadataUnit;
    }

    public Double getSourceMetadataPixelWidth() {
        return sourceMetadataPixelWidth;
    }

    public Double getSourceMetadataPixelHeight() {
        return sourceMetadataPixelHeight;
    }

    public boolean isIsotropic(final double relativeTolerance) {
        final double mean = 0.5 * (pixelWidthUm + pixelHeightUm);
        if (mean <= 0.0) {
            return false;
        }
        return Math.abs(pixelWidthUm - pixelHeightUm) / mean <= relativeTolerance;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PhysicalCalibration)) {
            return false;
        }
        final PhysicalCalibration other = (PhysicalCalibration) obj;
        return Double.compare(pixelWidthUm, other.pixelWidthUm) == 0
                && Double.compare(pixelHeightUm, other.pixelHeightUm) == 0
                && unit.equals(other.unit)
                && calibrationMethod == other.calibrationMethod
                && Objects.equals(scaleBarPhysicalLengthUm, other.scaleBarPhysicalLengthUm)
                && Objects.equals(scaleBarMeasuredLengthPx, other.scaleBarMeasuredLengthPx)
                && sourceMetadataUnit.equals(other.sourceMetadataUnit)
                && Objects.equals(sourceMetadataPixelWidth, other.sourceMetadataPixelWidth)
                && Objects.equals(sourceMetadataPixelHeight, other.sourceMetadataPixelHeight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Double.valueOf(pixelWidthUm),
                Double.valueOf(pixelHeightUm),
                unit,
                calibrationMethod,
                scaleBarPhysicalLengthUm,
                scaleBarMeasuredLengthPx,
                sourceMetadataUnit,
                sourceMetadataPixelWidth,
                sourceMetadataPixelHeight);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "PhysicalCalibration{pxW=%.12f,pxH=%.12f,unit=%s,method=%s}",
                Double.valueOf(pixelWidthUm),
                Double.valueOf(pixelHeightUm),
                unit,
                calibrationMethod.name());
    }
}
