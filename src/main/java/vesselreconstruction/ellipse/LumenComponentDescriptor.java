package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable descriptor of one 8-connected Class 3 Lumen component.
 */
public final class LumenComponentDescriptor {

    private final int componentId;
    private final List<Point> pixels;
    private final int area;
    private final Rectangle bounds;
    private final boolean touchesImageBorder;
    private final int imageWidth;
    private final int imageHeight;

    public LumenComponentDescriptor(
            final int componentId,
            final List<Point> pixels,
            final int imageWidth,
            final int imageHeight) {
        if (componentId <= 0) {
            throw new IllegalArgumentException("componentId must be positive: " + componentId);
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (pixels == null || pixels.isEmpty()) {
            throw new IllegalArgumentException("pixels must not be null or empty");
        }

        final List<Point> sorted = new ArrayList<Point>(pixels.size());
        final Set<Long> seen = new HashSet<Long>(pixels.size() * 2);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean touchesBorder = false;

        for (int i = 0; i < pixels.size(); i++) {
            final Point raw = pixels.get(i);
            if (raw == null) {
                throw new IllegalArgumentException("pixels must not contain null entries");
            }
            final int x = raw.x;
            final int y = raw.y;
            if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
                throw new IllegalArgumentException(
                        "pixel out of bounds: (" + x + "," + y + ") for "
                                + imageWidth + "x" + imageHeight);
            }
            final Long key = Long.valueOf(pack(x, y));
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "duplicate pixel coordinate: (" + x + "," + y + ")");
            }
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (x == 0 || y == 0 || x == imageWidth - 1 || y == imageHeight - 1) {
                touchesBorder = true;
            }
            sorted.add(new Point(x, y));
        }

        Collections.sort(sorted, POINT_ORDER);
        final Rectangle exactBounds = new Rectangle(
                minX, minY, maxX - minX + 1, maxY - minY + 1);

        this.componentId = componentId;
        this.pixels = Collections.unmodifiableList(sorted);
        this.area = sorted.size();
        this.bounds = exactBounds;
        this.touchesImageBorder = touchesBorder;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public int getComponentId() {
        return componentId;
    }

    public List<Point> getPixels() {
        return defensivePoints(pixels);
    }

    public int getArea() {
        return area;
    }

    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    public boolean isTouchesImageBorder() {
        return touchesImageBorder;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LumenComponentDescriptor)) {
            return false;
        }
        final LumenComponentDescriptor other = (LumenComponentDescriptor) obj;
        return componentId == other.componentId
                && area == other.area
                && touchesImageBorder == other.touchesImageBorder
                && imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && bounds.equals(other.bounds)
                && pixelsEqual(pixels, other.pixels);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                Integer.valueOf(componentId),
                Integer.valueOf(area),
                Boolean.valueOf(touchesImageBorder),
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                bounds);
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            result = 31 * result + p.x;
            result = 31 * result + p.y;
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "LumenComponentDescriptor{id=%d,area=%d,bounds=%s,touchesBorder=%s,%dx%d}",
                Integer.valueOf(componentId),
                Integer.valueOf(area),
                bounds,
                Boolean.valueOf(touchesImageBorder),
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight));
    }

    private static final Comparator<Point> POINT_ORDER = new Comparator<Point>() {
        @Override
        public int compare(final Point a, final Point b) {
            if (a.y != b.y) {
                return Integer.compare(a.y, b.y);
            }
            return Integer.compare(a.x, b.x);
        }
    };

    private static List<Point> defensivePoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point p = source.get(i);
            copy.add(new Point(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static boolean pixelsEqual(final List<Point> a, final List<Point> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            final Point pa = a.get(i);
            final Point pb = b.get(i);
            if (pa.x != pb.x || pa.y != pb.y) {
                return false;
            }
        }
        return true;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
