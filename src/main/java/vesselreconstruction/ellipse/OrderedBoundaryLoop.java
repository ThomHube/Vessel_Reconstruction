package vesselreconstruction.ellipse;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable closed ordered loop of directed pixel edges.
 */
public final class OrderedBoundaryLoop {

    private final int loopNumber;
    private final List<DirectedPixelEdge> directedEdges;
    private final List<Point2D.Double> orderedSupportMidpoints;
    private final double signedArea;
    private final double absoluteArea;
    private final int distinctVertexCount;
    private final Rectangle latticeBounds;

    public OrderedBoundaryLoop(
            final int loopNumber,
            final List<DirectedPixelEdge> directedEdges) {
        if (loopNumber <= 0) {
            throw new IllegalArgumentException("loopNumber must be positive: " + loopNumber);
        }
        if (directedEdges == null) {
            throw new IllegalArgumentException("directedEdges must not be null");
        }
        if (directedEdges.size() < 4) {
            throw new IllegalArgumentException(
                    "Ordered boundary loop requires at least 4 edges; got "
                            + directedEdges.size());
        }

        final List<DirectedPixelEdge> edgeCopy =
                new ArrayList<DirectedPixelEdge>(directedEdges.size());
        final List<Point2D.Double> midpoints =
                new ArrayList<Point2D.Double>(directedEdges.size());

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        final java.util.HashSet<Long> vertices = new java.util.HashSet<Long>();

        for (int i = 0; i < directedEdges.size(); i++) {
            final DirectedPixelEdge edge = directedEdges.get(i);
            if (edge == null) {
                throw new IllegalArgumentException("directedEdges must not contain null");
            }
            final DirectedPixelEdge next = directedEdges.get((i + 1) % directedEdges.size());
            if (edge.getEndX() != next.getStartX() || edge.getEndY() != next.getStartY()) {
                throw new IllegalArgumentException(
                        "Edge end must equal next edge start at index " + i);
            }
            edgeCopy.add(edge);
            final Point2D.Double mid = edge.getMidpoint();
            midpoints.add(new Point2D.Double(mid.x, mid.y));

            vertices.add(pack(edge.getStartX(), edge.getStartY()));
            if (edge.getStartX() < minX) {
                minX = edge.getStartX();
            }
            if (edge.getStartY() < minY) {
                minY = edge.getStartY();
            }
            if (edge.getStartX() > maxX) {
                maxX = edge.getStartX();
            }
            if (edge.getStartY() > maxY) {
                maxY = edge.getStartY();
            }
        }

        final DirectedPixelEdge first = edgeCopy.get(0);
        final DirectedPixelEdge last = edgeCopy.get(edgeCopy.size() - 1);
        if (last.getEndX() != first.getStartX() || last.getEndY() != first.getStartY()) {
            throw new IllegalArgumentException(
                    "Final edge end must equal first edge start");
        }

        double areaTwice = 0.0;
        for (int i = 0; i < edgeCopy.size(); i++) {
            final DirectedPixelEdge edge = edgeCopy.get(i);
            areaTwice += ((double) edge.getStartX()) * edge.getEndY()
                    - ((double) edge.getEndX()) * edge.getStartY();
        }
        final double signed = 0.5 * areaTwice;

        this.loopNumber = loopNumber;
        this.directedEdges = Collections.unmodifiableList(edgeCopy);
        this.orderedSupportMidpoints = Collections.unmodifiableList(midpoints);
        this.signedArea = signed;
        this.absoluteArea = Math.abs(signed);
        this.distinctVertexCount = vertices.size();
        this.latticeBounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public int getLoopNumber() {
        return loopNumber;
    }

    public List<DirectedPixelEdge> getDirectedEdges() {
        return directedEdges;
    }

    public List<Point2D.Double> getOrderedSupportMidpoints() {
        final List<Point2D.Double> copy =
                new ArrayList<Point2D.Double>(orderedSupportMidpoints.size());
        for (int i = 0; i < orderedSupportMidpoints.size(); i++) {
            final Point2D.Double p = orderedSupportMidpoints.get(i);
            copy.add(new Point2D.Double(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    public double getSignedArea() {
        return signedArea;
    }

    public double getAbsoluteArea() {
        return absoluteArea;
    }

    public int getDistinctVertexCount() {
        return distinctVertexCount;
    }

    public Rectangle getLatticeBounds() {
        return new Rectangle(latticeBounds);
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OrderedBoundaryLoop)) {
            return false;
        }
        final OrderedBoundaryLoop other = (OrderedBoundaryLoop) obj;
        return loopNumber == other.loopNumber
                && distinctVertexCount == other.distinctVertexCount
                && Double.doubleToLongBits(signedArea)
                == Double.doubleToLongBits(other.signedArea)
                && Double.doubleToLongBits(absoluteArea)
                == Double.doubleToLongBits(other.absoluteArea)
                && Objects.equals(directedEdges, other.directedEdges)
                && Objects.equals(orderedSupportMidpoints, other.orderedSupportMidpoints)
                && Objects.equals(latticeBounds, other.latticeBounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(loopNumber),
                directedEdges,
                orderedSupportMidpoints,
                Double.valueOf(signedArea),
                Double.valueOf(absoluteArea),
                Integer.valueOf(distinctVertexCount),
                latticeBounds);
    }

    @Override
    public String toString() {
        return "OrderedBoundaryLoop{loopNumber=" + loopNumber
                + ", edgeCount=" + directedEdges.size()
                + ", signedArea=" + signedArea
                + ", absoluteArea=" + absoluteArea
                + ", distinctVertexCount=" + distinctVertexCount
                + ", latticeBounds=" + latticeBounds
                + '}';
    }
}
