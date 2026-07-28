package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered residual profile of an outer boundary loop against a fitted free ellipse.
 * Does not exclude points, detect arcs, or refit the ellipse.
 */
public final class OrderedBoundaryResidualProfile {

    public static final class Sample {
        private final int boundaryIndex;
        private final Point2D.Double supportMidpoint;
        private final int edgeStartX;
        private final int edgeStartY;
        private final int edgeEndX;
        private final int edgeEndY;
        private final DirectedPixelEdge.Direction edgeDirection;
        private final double normalizedRadius;
        private final double residual;

        public Sample(
                final int boundaryIndex,
                final Point2D.Double supportMidpoint,
                final int edgeStartX,
                final int edgeStartY,
                final int edgeEndX,
                final int edgeEndY,
                final DirectedPixelEdge.Direction edgeDirection,
                final double normalizedRadius,
                final double residual) {
            if (boundaryIndex < 0) {
                throw new IllegalArgumentException(
                        "boundaryIndex must be non-negative: " + boundaryIndex);
            }
            if (supportMidpoint == null) {
                throw new IllegalArgumentException("supportMidpoint must not be null");
            }
            if (edgeDirection == null) {
                throw new IllegalArgumentException("edgeDirection must not be null");
            }
            if (!Double.isFinite(supportMidpoint.x) || !Double.isFinite(supportMidpoint.y)) {
                throw new IllegalArgumentException("supportMidpoint must be finite");
            }
            if (!Double.isFinite(normalizedRadius) || !Double.isFinite(residual)) {
                throw new IllegalArgumentException("normalizedRadius and residual must be finite");
            }
            this.boundaryIndex = boundaryIndex;
            this.supportMidpoint = new Point2D.Double(supportMidpoint.x, supportMidpoint.y);
            this.edgeStartX = edgeStartX;
            this.edgeStartY = edgeStartY;
            this.edgeEndX = edgeEndX;
            this.edgeEndY = edgeEndY;
            this.edgeDirection = edgeDirection;
            this.normalizedRadius = normalizedRadius;
            this.residual = residual;
        }

        public int getBoundaryIndex() {
            return boundaryIndex;
        }

        public Point2D.Double getSupportMidpoint() {
            return new Point2D.Double(supportMidpoint.x, supportMidpoint.y);
        }

        public int getEdgeStartX() {
            return edgeStartX;
        }

        public int getEdgeStartY() {
            return edgeStartY;
        }

        public int getEdgeEndX() {
            return edgeEndX;
        }

        public int getEdgeEndY() {
            return edgeEndY;
        }

        public DirectedPixelEdge.Direction getEdgeDirection() {
            return edgeDirection;
        }

        public double getNormalizedRadius() {
            return normalizedRadius;
        }

        public double getResidual() {
            return residual;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Sample)) {
                return false;
            }
            final Sample other = (Sample) obj;
            return boundaryIndex == other.boundaryIndex
                    && edgeStartX == other.edgeStartX
                    && edgeStartY == other.edgeStartY
                    && edgeEndX == other.edgeEndX
                    && edgeEndY == other.edgeEndY
                    && edgeDirection == other.edgeDirection
                    && Double.doubleToLongBits(normalizedRadius)
                    == Double.doubleToLongBits(other.normalizedRadius)
                    && Double.doubleToLongBits(residual)
                    == Double.doubleToLongBits(other.residual)
                    && Double.doubleToLongBits(supportMidpoint.x)
                    == Double.doubleToLongBits(other.supportMidpoint.x)
                    && Double.doubleToLongBits(supportMidpoint.y)
                    == Double.doubleToLongBits(other.supportMidpoint.y);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(boundaryIndex),
                    Double.valueOf(supportMidpoint.x),
                    Double.valueOf(supportMidpoint.y),
                    Integer.valueOf(edgeStartX),
                    Integer.valueOf(edgeStartY),
                    Integer.valueOf(edgeEndX),
                    Integer.valueOf(edgeEndY),
                    edgeDirection,
                    Double.valueOf(normalizedRadius),
                    Double.valueOf(residual));
        }

        @Override
        public String toString() {
            return "Sample{index=" + boundaryIndex
                    + ", residual=" + residual
                    + ", midpoint=(" + supportMidpoint.x + "," + supportMidpoint.y + ")"
                    + '}';
        }
    }

    private final OrderedBoundaryLoop boundaryLoop;
    private final EllipseModel ellipse;
    private final List<Sample> samples;
    private final double medianResidual;
    private final double medianAbsoluteDeviation;
    private final double minimumResidual;
    private final int minimumResidualIndex;
    private final Point2D.Double minimumResidualPoint;
    private final double maximumResidual;
    private final int maximumResidualIndex;
    private final Point2D.Double maximumResidualPoint;
    private final int negativeResidualCount;
    private final int positiveResidualCount;
    private final int zeroResidualCount;

    private OrderedBoundaryResidualProfile(
            final OrderedBoundaryLoop boundaryLoop,
            final EllipseModel ellipse,
            final List<Sample> samples,
            final double medianResidual,
            final double medianAbsoluteDeviation,
            final double minimumResidual,
            final int minimumResidualIndex,
            final Point2D.Double minimumResidualPoint,
            final double maximumResidual,
            final int maximumResidualIndex,
            final Point2D.Double maximumResidualPoint,
            final int negativeResidualCount,
            final int positiveResidualCount,
            final int zeroResidualCount) {
        this.boundaryLoop = boundaryLoop;
        this.ellipse = ellipse;
        this.samples = Collections.unmodifiableList(samples);
        this.medianResidual = medianResidual;
        this.medianAbsoluteDeviation = medianAbsoluteDeviation;
        this.minimumResidual = minimumResidual;
        this.minimumResidualIndex = minimumResidualIndex;
        this.minimumResidualPoint = new Point2D.Double(
                minimumResidualPoint.x, minimumResidualPoint.y);
        this.maximumResidual = maximumResidual;
        this.maximumResidualIndex = maximumResidualIndex;
        this.maximumResidualPoint = new Point2D.Double(
                maximumResidualPoint.x, maximumResidualPoint.y);
        this.negativeResidualCount = negativeResidualCount;
        this.positiveResidualCount = positiveResidualCount;
        this.zeroResidualCount = zeroResidualCount;
    }

    public static OrderedBoundaryResidualProfile create(
            final OrderedBoundaryLoop boundaryLoop,
            final EllipseModel ellipse) {
        if (boundaryLoop == null) {
            throw new IllegalArgumentException("boundaryLoop must not be null");
        }
        if (ellipse == null) {
            throw new IllegalArgumentException("ellipse must not be null");
        }

        final List<DirectedPixelEdge> edges = boundaryLoop.getDirectedEdges();
        final List<Sample> samples = new ArrayList<Sample>(edges.size());
        final List<Double> residuals = new ArrayList<Double>(edges.size());

        double minResidual = Double.POSITIVE_INFINITY;
        double maxResidual = Double.NEGATIVE_INFINITY;
        int minIndex = -1;
        int maxIndex = -1;
        Point2D.Double minPoint = null;
        Point2D.Double maxPoint = null;
        int negative = 0;
        int positive = 0;
        int zero = 0;

        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge edge = edges.get(i);
            final Point2D.Double mid = edge.getMidpoint();
            final double radius = ellipse.normalizedRadius(mid.x, mid.y);
            final double residual = radius - 1.0;
            if (!Double.isFinite(radius) || !Double.isFinite(residual)) {
                throw new IllegalArgumentException(
                        "Non-finite residual at boundary index " + i);
            }

            samples.add(new Sample(
                    i,
                    mid,
                    edge.getStartX(),
                    edge.getStartY(),
                    edge.getEndX(),
                    edge.getEndY(),
                    edge.getDirection(),
                    radius,
                    residual));
            residuals.add(Double.valueOf(residual));

            if (residual < minResidual) {
                minResidual = residual;
                minIndex = i;
                minPoint = mid;
            }
            if (residual > maxResidual) {
                maxResidual = residual;
                maxIndex = i;
                maxPoint = mid;
            }
            if (residual < 0.0) {
                negative++;
            } else if (residual > 0.0) {
                positive++;
            } else {
                zero++;
            }
        }

        final double median = medianOf(residuals);
        final List<Double> absDev = new ArrayList<Double>(residuals.size());
        for (int i = 0; i < residuals.size(); i++) {
            absDev.add(Double.valueOf(Math.abs(residuals.get(i).doubleValue() - median)));
        }
        final double mad = medianOf(absDev);

        return new OrderedBoundaryResidualProfile(
                boundaryLoop,
                ellipse,
                samples,
                median,
                mad,
                minResidual,
                minIndex,
                minPoint,
                maxResidual,
                maxIndex,
                maxPoint,
                negative,
                positive,
                zero);
    }

    public OrderedBoundaryLoop getBoundaryLoop() {
        return boundaryLoop;
    }

    public EllipseModel getEllipse() {
        return ellipse;
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public double getMedianResidual() {
        return medianResidual;
    }

    public double getMedianAbsoluteDeviation() {
        return medianAbsoluteDeviation;
    }

    public double getMinimumResidual() {
        return minimumResidual;
    }

    public int getMinimumResidualIndex() {
        return minimumResidualIndex;
    }

    public Point2D.Double getMinimumResidualPoint() {
        return new Point2D.Double(minimumResidualPoint.x, minimumResidualPoint.y);
    }

    public double getMaximumResidual() {
        return maximumResidual;
    }

    public int getMaximumResidualIndex() {
        return maximumResidualIndex;
    }

    public Point2D.Double getMaximumResidualPoint() {
        return new Point2D.Double(maximumResidualPoint.x, maximumResidualPoint.y);
    }

    public int getNegativeResidualCount() {
        return negativeResidualCount;
    }

    public int getPositiveResidualCount() {
        return positiveResidualCount;
    }

    public int getZeroResidualCount() {
        return zeroResidualCount;
    }

    private static double medianOf(final List<Double> values) {
        final int n = values.size();
        final double[] sorted = new double[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = values.get(i).doubleValue();
        }
        Arrays.sort(sorted);
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OrderedBoundaryResidualProfile)) {
            return false;
        }
        final OrderedBoundaryResidualProfile other = (OrderedBoundaryResidualProfile) obj;
        return minimumResidualIndex == other.minimumResidualIndex
                && maximumResidualIndex == other.maximumResidualIndex
                && negativeResidualCount == other.negativeResidualCount
                && positiveResidualCount == other.positiveResidualCount
                && zeroResidualCount == other.zeroResidualCount
                && Double.doubleToLongBits(medianResidual)
                == Double.doubleToLongBits(other.medianResidual)
                && Double.doubleToLongBits(medianAbsoluteDeviation)
                == Double.doubleToLongBits(other.medianAbsoluteDeviation)
                && Double.doubleToLongBits(minimumResidual)
                == Double.doubleToLongBits(other.minimumResidual)
                && Double.doubleToLongBits(maximumResidual)
                == Double.doubleToLongBits(other.maximumResidual)
                && Objects.equals(boundaryLoop, other.boundaryLoop)
                && Objects.equals(ellipse, other.ellipse)
                && Objects.equals(samples, other.samples)
                && Double.doubleToLongBits(minimumResidualPoint.x)
                == Double.doubleToLongBits(other.minimumResidualPoint.x)
                && Double.doubleToLongBits(minimumResidualPoint.y)
                == Double.doubleToLongBits(other.minimumResidualPoint.y)
                && Double.doubleToLongBits(maximumResidualPoint.x)
                == Double.doubleToLongBits(other.maximumResidualPoint.x)
                && Double.doubleToLongBits(maximumResidualPoint.y)
                == Double.doubleToLongBits(other.maximumResidualPoint.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                boundaryLoop,
                ellipse,
                samples,
                Double.valueOf(medianResidual),
                Double.valueOf(medianAbsoluteDeviation),
                Double.valueOf(minimumResidual),
                Integer.valueOf(minimumResidualIndex),
                Double.valueOf(minimumResidualPoint.x),
                Double.valueOf(minimumResidualPoint.y),
                Double.valueOf(maximumResidual),
                Integer.valueOf(maximumResidualIndex),
                Double.valueOf(maximumResidualPoint.x),
                Double.valueOf(maximumResidualPoint.y),
                Integer.valueOf(negativeResidualCount),
                Integer.valueOf(positiveResidualCount),
                Integer.valueOf(zeroResidualCount));
    }

    @Override
    public String toString() {
        return "OrderedBoundaryResidualProfile{samples=" + samples.size()
                + ", medianResidual=" + medianResidual
                + ", MAD=" + medianAbsoluteDeviation
                + ", minResidual=" + minimumResidual
                + " @" + minimumResidualIndex
                + ", maxResidual=" + maximumResidual
                + " @" + maximumResidualIndex
                + '}';
    }
}
