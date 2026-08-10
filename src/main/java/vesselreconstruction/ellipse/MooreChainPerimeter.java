package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Final lumen perimeter (wall length) using an 8-connected Moore chain.
 *
 * <p>Horizontal/vertical steps contribute 1 px; diagonal steps contribute
 * {@code sqrt(2)}. The closing step is included. Boundary pixel count is not
 * used as perimeter.</p>
 */
public final class MooreChainPerimeter {

    private MooreChainPerimeter() {
    }

    public static final class Result {
        public final double lengthPx;
        public final double lengthUm;
        public final List<Point> boundaryPixels;

        public Result(
                final double lengthPx,
                final double lengthUm,
                final List<Point> boundaryPixels) {
            this.lengthPx = lengthPx;
            this.lengthUm = lengthUm;
            this.boundaryPixels = boundaryPixels;
        }
    }

    /**
     * Computes perimeter for a binary mask of a single component.
     *
     * @param mask true = lumen pixel; width/height define the image
     */
    public static Result measure(
            final boolean[] mask,
            final int width,
            final int height,
            final double pixelWidthUm,
            final double pixelHeightUm) {
        final List<Point> boundary = extractBoundaryPixels(mask, width, height);
        if (boundary.isEmpty()) {
            return new Result(Double.NaN, Double.NaN, boundary);
        }
        // Ordered chain via nearest-neighbor walk starting at top-left boundary pixel.
        final List<Point> ordered = orderBoundary(boundary);
        if (ordered.size() < 2) {
            return new Result(Double.NaN, Double.NaN, boundary);
        }
        double lengthPx = 0.0;
        double lengthUm = 0.0;
        for (int i = 0; i < ordered.size(); i++) {
            final Point a = ordered.get(i);
            final Point b = ordered.get((i + 1) % ordered.size());
            final int dx = b.x - a.x;
            final int dy = b.y - a.y;
            final int adx = Math.abs(dx);
            final int ady = Math.abs(dy);
            if (adx > 1 || ady > 1 || (adx == 0 && ady == 0)) {
                // Non-adjacent; fall back to Euclidean for robustness.
                final double stepPx = Math.hypot(dx, dy);
                lengthPx += stepPx;
                lengthUm += Math.hypot(dx * pixelWidthUm, dy * pixelHeightUm);
                continue;
            }
            if (adx == 1 && ady == 1) {
                lengthPx += Math.sqrt(2.0);
                lengthUm += Math.sqrt(
                        pixelWidthUm * pixelWidthUm + pixelHeightUm * pixelHeightUm);
            } else if (adx == 1) {
                lengthPx += 1.0;
                lengthUm += pixelWidthUm;
            } else {
                lengthPx += 1.0;
                lengthUm += pixelHeightUm;
            }
        }
        return new Result(lengthPx, lengthUm, ordered);
    }

    public static List<Point> extractBoundaryPixels(
            final boolean[] mask,
            final int width,
            final int height) {
        final List<Point> points = new ArrayList<Point>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y * width + x]) {
                    continue;
                }
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!border) {
                    for (int dy = -1; dy <= 1 && !border; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }
                            if (!mask[(y + dy) * width + (x + dx)]) {
                                border = true;
                                break;
                            }
                        }
                    }
                }
                if (border) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    private static List<Point> orderBoundary(final List<Point> boundary) {
        final boolean[] used = new boolean[boundary.size()];
        final List<Point> ordered = new ArrayList<Point>(boundary.size());
        int current = 0;
        for (int i = 1; i < boundary.size(); i++) {
            final Point p = boundary.get(i);
            final Point c = boundary.get(current);
            if (p.y < c.y || (p.y == c.y && p.x < c.x)) {
                current = i;
            }
        }
        while (ordered.size() < boundary.size()) {
            used[current] = true;
            ordered.add(boundary.get(current));
            int next = -1;
            double best = Double.POSITIVE_INFINITY;
            final Point c = boundary.get(current);
            for (int i = 0; i < boundary.size(); i++) {
                if (used[i]) {
                    continue;
                }
                final Point p = boundary.get(i);
                final int adx = Math.abs(p.x - c.x);
                final int ady = Math.abs(p.y - c.y);
                if (adx > 1 || ady > 1) {
                    continue;
                }
                final double d = adx + ady + (adx == 1 && ady == 1 ? 0.1 : 0.0);
                if (d < best) {
                    best = d;
                    next = i;
                }
            }
            if (next < 0) {
                // disconnected remainder; append by nearest any unused
                best = Double.POSITIVE_INFINITY;
                for (int i = 0; i < boundary.size(); i++) {
                    if (used[i]) {
                        continue;
                    }
                    final Point p = boundary.get(i);
                    final double d = Math.hypot(p.x - c.x, p.y - c.y);
                    if (d < best) {
                        best = d;
                        next = i;
                    }
                }
            }
            if (next < 0) {
                break;
            }
            current = next;
        }
        return ordered;
    }
}
