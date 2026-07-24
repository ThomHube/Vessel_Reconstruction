package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Immutable free ellipse: center, two independent axes, and rotation.
 * Axes are stored canonically with {@code semiMajorAxis >= semiMinorAxis}
 * and rotation normalized to {@code [0, π)}.
 */
public final class EllipseModel {

    private static final double PI = Math.PI;

    private final double centerX;
    private final double centerY;
    private final double semiMajorAxis;
    private final double semiMinorAxis;
    private final double rotationRadians;

    /**
     * Creates a free ellipse. Axes are swapped and rotation adjusted by π/2
     * when needed so that the semi-major axis is stored as the larger axis.
     *
     * @param centerX ellipse center X
     * @param centerY ellipse center Y
     * @param semiMajorAxis first axis length (must be &gt; 0); may be swapped
     * @param semiMinorAxis second axis length (must be &gt; 0); may be swapped
     * @param rotationRadians orientation of the first supplied axis
     */
    public EllipseModel(
            final double centerX,
            final double centerY,
            final double semiMajorAxis,
            final double semiMinorAxis,
            final double rotationRadians) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(semiMajorAxis) || !Double.isFinite(semiMinorAxis)
                || !Double.isFinite(rotationRadians)) {
            throw new IllegalArgumentException(
                    "Ellipse parameters must be finite: center=(" + centerX + ", " + centerY
                            + "), axes=(" + semiMajorAxis + ", " + semiMinorAxis
                            + "), rotation=" + rotationRadians);
        }
        if (!(semiMajorAxis > 0.0) || !(semiMinorAxis > 0.0)) {
            throw new IllegalArgumentException(
                    "Ellipse axes must be greater than zero: semiMajorAxis="
                            + semiMajorAxis + ", semiMinorAxis=" + semiMinorAxis);
        }

        double a = semiMajorAxis;
        double b = semiMinorAxis;
        double theta = rotationRadians;
        if (b > a) {
            final double tmp = a;
            a = b;
            b = tmp;
            theta += PI / 2.0;
        }

        this.centerX = centerX;
        this.centerY = centerY;
        this.semiMajorAxis = a;
        this.semiMinorAxis = b;
        this.rotationRadians = normalizeRotation(theta);
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getSemiMajorAxis() {
        return semiMajorAxis;
    }

    public double getSemiMinorAxis() {
        return semiMinorAxis;
    }

    public double getRotationRadians() {
        return rotationRadians;
    }

    public double getAxisRatio() {
        return semiMajorAxis / semiMinorAxis;
    }

    public double getArea() {
        return PI * semiMajorAxis * semiMinorAxis;
    }

    /**
     * Normalized elliptic radius. Value 1 is on the ellipse, &lt;1 inside, &gt;1 outside.
     */
    public double normalizedRadius(final double x, final double y) {
        requireFinitePoint(x, y);
        final double dx = x - centerX;
        final double dy = y - centerY;
        final double cos = Math.cos(rotationRadians);
        final double sin = Math.sin(rotationRadians);
        final double localX = cos * dx + sin * dy;
        final double localY = -sin * dx + cos * dy;
        final double q = (localX * localX) / (semiMajorAxis * semiMajorAxis)
                + (localY * localY) / (semiMinorAxis * semiMinorAxis);
        return Math.sqrt(q);
    }

    /**
     * Signed normalized radial residual: {@code normalizedRadius - 1}.
     * Negative = inward; positive = outward.
     */
    public double normalizedRadialResidual(final double x, final double y) {
        return normalizedRadius(x, y) - 1.0;
    }

    /**
     * Parametric point on the ellipse at angle {@code parameterRadians}.
     */
    public Point2D.Double pointAt(final double parameterRadians) {
        if (!Double.isFinite(parameterRadians)) {
            throw new IllegalArgumentException(
                    "Ellipse parameter must be finite: " + parameterRadians);
        }
        final double localX = semiMajorAxis * Math.cos(parameterRadians);
        final double localY = semiMinorAxis * Math.sin(parameterRadians);
        final double cos = Math.cos(rotationRadians);
        final double sin = Math.sin(rotationRadians);
        final double x = centerX + cos * localX - sin * localY;
        final double y = centerY + sin * localX + cos * localY;
        return new Point2D.Double(x, y);
    }

    static double normalizeRotation(final double radians) {
        double theta = radians % PI;
        if (theta < 0.0) {
            theta += PI;
        }
        // Map π exactly into the half-open range [0, π).
        if (theta >= PI) {
            theta -= PI;
        }
        return theta;
    }

    private static void requireFinitePoint(final double x, final double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException(
                    "Point coordinates must be finite: (" + x + ", " + y + ")");
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EllipseModel)) {
            return false;
        }
        final EllipseModel other = (EllipseModel) obj;
        return Double.doubleToLongBits(centerX) == Double.doubleToLongBits(other.centerX)
                && Double.doubleToLongBits(centerY) == Double.doubleToLongBits(other.centerY)
                && Double.doubleToLongBits(semiMajorAxis)
                == Double.doubleToLongBits(other.semiMajorAxis)
                && Double.doubleToLongBits(semiMinorAxis)
                == Double.doubleToLongBits(other.semiMinorAxis)
                && Double.doubleToLongBits(rotationRadians)
                == Double.doubleToLongBits(other.rotationRadians);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerX, centerY, semiMajorAxis, semiMinorAxis, rotationRadians);
    }

    @Override
    public String toString() {
        return "EllipseModel{centerX=" + centerX
                + ", centerY=" + centerY
                + ", semiMajorAxis=" + semiMajorAxis
                + ", semiMinorAxis=" + semiMinorAxis
                + ", rotationRadians=" + rotationRadians
                + '}';
    }
}
