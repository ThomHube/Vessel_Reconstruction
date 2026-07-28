package vesselreconstruction.ellipse;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable contiguous inward-outlier interval on an ordered outer boundary.
 */
public final class InwardOutlierInterval {

    private final int startIndex;
    private final int endIndex;
    private final boolean wrapsAround;
    private final int edgeCount;
    private final double boundaryFraction;
    private final int corePointCount;
    private final double minimumResidual;
    private final int minimumResidualIndex;
    private final double meanResidual;
    private final double integratedInwardExcess;
    private final boolean containsGlobalMinimum;
    private final Point2D.Double startSupportMidpoint;
    private final Point2D.Double endSupportMidpoint;
    private final List<Integer> orderedBoundaryIndices;

    public InwardOutlierInterval(
            final int startIndex,
            final int endIndex,
            final boolean wrapsAround,
            final int edgeCount,
            final double boundaryFraction,
            final int corePointCount,
            final double minimumResidual,
            final int minimumResidualIndex,
            final double meanResidual,
            final double integratedInwardExcess,
            final boolean containsGlobalMinimum,
            final Point2D.Double startSupportMidpoint,
            final Point2D.Double endSupportMidpoint,
            final List<Integer> orderedBoundaryIndices) {
        if (startIndex < 0 || endIndex < 0 || minimumResidualIndex < 0) {
            throw new IllegalArgumentException(
                    "Indices must be non-negative: start=" + startIndex
                            + ", end=" + endIndex
                            + ", minIndex=" + minimumResidualIndex);
        }
        if (edgeCount <= 0) {
            throw new IllegalArgumentException("edgeCount must be positive: " + edgeCount);
        }
        if (!(boundaryFraction > 0.0) || boundaryFraction > 1.0) {
            throw new IllegalArgumentException(
                    "boundaryFraction must be in (0,1]: " + boundaryFraction);
        }
        if (corePointCount <= 0) {
            throw new IllegalArgumentException(
                    "corePointCount must be positive: " + corePointCount);
        }
        requireFinite("minimumResidual", minimumResidual);
        requireFinite("meanResidual", meanResidual);
        requireFinite("integratedInwardExcess", integratedInwardExcess);
        requireFinite("boundaryFraction", boundaryFraction);
        if (startSupportMidpoint == null || endSupportMidpoint == null) {
            throw new IllegalArgumentException("Support midpoints must not be null");
        }
        if (!Double.isFinite(startSupportMidpoint.x) || !Double.isFinite(startSupportMidpoint.y)
                || !Double.isFinite(endSupportMidpoint.x) || !Double.isFinite(endSupportMidpoint.y)) {
            throw new IllegalArgumentException("Support midpoints must be finite");
        }
        if (orderedBoundaryIndices == null) {
            throw new IllegalArgumentException("orderedBoundaryIndices must not be null");
        }
        if (orderedBoundaryIndices.size() != edgeCount) {
            throw new IllegalArgumentException(
                    "orderedBoundaryIndices size must equal edgeCount");
        }
        if (orderedBoundaryIndices.get(0).intValue() != startIndex) {
            throw new IllegalArgumentException("First ordered index must equal startIndex");
        }
        if (orderedBoundaryIndices.get(edgeCount - 1).intValue() != endIndex) {
            throw new IllegalArgumentException("Final ordered index must equal endIndex");
        }

        final List<Integer> indexCopy = new ArrayList<Integer>(edgeCount);
        final java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < orderedBoundaryIndices.size(); i++) {
            final Integer value = orderedBoundaryIndices.get(i);
            if (value == null || value.intValue() < 0) {
                throw new IllegalArgumentException(
                        "orderedBoundaryIndices must contain non-negative values");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        "orderedBoundaryIndices must not contain duplicates");
            }
            indexCopy.add(Integer.valueOf(value.intValue()));
        }

        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.wrapsAround = wrapsAround;
        this.edgeCount = edgeCount;
        this.boundaryFraction = boundaryFraction;
        this.corePointCount = corePointCount;
        this.minimumResidual = minimumResidual;
        this.minimumResidualIndex = minimumResidualIndex;
        this.meanResidual = meanResidual;
        this.integratedInwardExcess = integratedInwardExcess;
        this.containsGlobalMinimum = containsGlobalMinimum;
        this.startSupportMidpoint = new Point2D.Double(
                startSupportMidpoint.x, startSupportMidpoint.y);
        this.endSupportMidpoint = new Point2D.Double(
                endSupportMidpoint.x, endSupportMidpoint.y);
        this.orderedBoundaryIndices = Collections.unmodifiableList(indexCopy);
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public boolean isWrapsAround() {
        return wrapsAround;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public double getBoundaryFraction() {
        return boundaryFraction;
    }

    public int getCorePointCount() {
        return corePointCount;
    }

    public double getMinimumResidual() {
        return minimumResidual;
    }

    public int getMinimumResidualIndex() {
        return minimumResidualIndex;
    }

    public double getMeanResidual() {
        return meanResidual;
    }

    public double getIntegratedInwardExcess() {
        return integratedInwardExcess;
    }

    public boolean isContainsGlobalMinimum() {
        return containsGlobalMinimum;
    }

    public Point2D.Double getStartSupportMidpoint() {
        return new Point2D.Double(startSupportMidpoint.x, startSupportMidpoint.y);
    }

    public Point2D.Double getEndSupportMidpoint() {
        return new Point2D.Double(endSupportMidpoint.x, endSupportMidpoint.y);
    }

    public List<Integer> getOrderedBoundaryIndices() {
        return orderedBoundaryIndices;
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InwardOutlierInterval)) {
            return false;
        }
        final InwardOutlierInterval other = (InwardOutlierInterval) obj;
        return startIndex == other.startIndex
                && endIndex == other.endIndex
                && wrapsAround == other.wrapsAround
                && edgeCount == other.edgeCount
                && corePointCount == other.corePointCount
                && minimumResidualIndex == other.minimumResidualIndex
                && containsGlobalMinimum == other.containsGlobalMinimum
                && Double.doubleToLongBits(boundaryFraction)
                == Double.doubleToLongBits(other.boundaryFraction)
                && Double.doubleToLongBits(minimumResidual)
                == Double.doubleToLongBits(other.minimumResidual)
                && Double.doubleToLongBits(meanResidual)
                == Double.doubleToLongBits(other.meanResidual)
                && Double.doubleToLongBits(integratedInwardExcess)
                == Double.doubleToLongBits(other.integratedInwardExcess)
                && Double.doubleToLongBits(startSupportMidpoint.x)
                == Double.doubleToLongBits(other.startSupportMidpoint.x)
                && Double.doubleToLongBits(startSupportMidpoint.y)
                == Double.doubleToLongBits(other.startSupportMidpoint.y)
                && Double.doubleToLongBits(endSupportMidpoint.x)
                == Double.doubleToLongBits(other.endSupportMidpoint.x)
                && Double.doubleToLongBits(endSupportMidpoint.y)
                == Double.doubleToLongBits(other.endSupportMidpoint.y)
                && Objects.equals(orderedBoundaryIndices, other.orderedBoundaryIndices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(startIndex),
                Integer.valueOf(endIndex),
                Boolean.valueOf(wrapsAround),
                Integer.valueOf(edgeCount),
                Double.valueOf(boundaryFraction),
                Integer.valueOf(corePointCount),
                Double.valueOf(minimumResidual),
                Integer.valueOf(minimumResidualIndex),
                Double.valueOf(meanResidual),
                Double.valueOf(integratedInwardExcess),
                Boolean.valueOf(containsGlobalMinimum),
                Double.valueOf(startSupportMidpoint.x),
                Double.valueOf(startSupportMidpoint.y),
                Double.valueOf(endSupportMidpoint.x),
                Double.valueOf(endSupportMidpoint.y),
                orderedBoundaryIndices);
    }

    @Override
    public String toString() {
        return "InwardOutlierInterval{start=" + startIndex
                + ", end=" + endIndex
                + ", wraps=" + wrapsAround
                + ", edges=" + edgeCount
                + ", fraction=" + boundaryFraction
                + ", minResidual=" + minimumResidual
                + " @" + minimumResidualIndex
                + ", excess=" + integratedInwardExcess
                + '}';
    }
}
