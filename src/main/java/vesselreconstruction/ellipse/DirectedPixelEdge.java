package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Immutable directed unit lattice edge on the pixel grid.
 */
public final class DirectedPixelEdge {

    public enum Direction {
        EAST,
        SOUTH,
        WEST,
        NORTH
    }

    private final int startX;
    private final int startY;
    private final int endX;
    private final int endY;
    private final Direction direction;

    public DirectedPixelEdge(
            final int startX,
            final int startY,
            final int endX,
            final int endY) {
        final int dx = endX - startX;
        final int dy = endY - startY;
        if (dx == 0 && dy == 0) {
            throw new IllegalArgumentException(
                    "Directed pixel edge must not have zero length: ("
                            + startX + "," + startY + ")");
        }
        if (dx != 0 && dy != 0) {
            throw new IllegalArgumentException(
                    "Directed pixel edge must be axis-aligned, not diagonal: ("
                            + startX + "," + startY + ")->(" + endX + "," + endY + ")");
        }
        if (Math.abs(dx) + Math.abs(dy) != 1) {
            throw new IllegalArgumentException(
                    "Directed pixel edge must be exactly one lattice unit: ("
                            + startX + "," + startY + ")->(" + endX + "," + endY + ")");
        }

        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        if (dx == 1) {
            this.direction = Direction.EAST;
        } else if (dx == -1) {
            this.direction = Direction.WEST;
        } else if (dy == 1) {
            this.direction = Direction.SOUTH;
        } else {
            this.direction = Direction.NORTH;
        }
    }

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public int getEndX() {
        return endX;
    }

    public int getEndY() {
        return endY;
    }

    public Direction getDirection() {
        return direction;
    }

    public Point getStart() {
        return new Point(startX, startY);
    }

    public Point getEnd() {
        return new Point(endX, endY);
    }

    public Point2D.Double getMidpoint() {
        return new Point2D.Double(
                0.5 * (startX + endX),
                0.5 * (startY + endY));
    }

    public long canonicalCoordinateKey() {
        long key = startX;
        key = (key << 16) ^ (startY & 0xffffL);
        key = (key << 16) ^ (endX & 0xffffL);
        key = (key << 16) ^ (endY & 0xffffL);
        return key;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DirectedPixelEdge)) {
            return false;
        }
        final DirectedPixelEdge other = (DirectedPixelEdge) obj;
        return startX == other.startX
                && startY == other.startY
                && endX == other.endX
                && endY == other.endY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(startX),
                Integer.valueOf(startY),
                Integer.valueOf(endX),
                Integer.valueOf(endY));
    }

    @Override
    public String toString() {
        return "DirectedPixelEdge{(" + startX + "," + startY + ")->("
                + endX + "," + endY + "), " + direction + '}';
    }
}
