package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Final lumen perimeter (wall length) using an 8-connected Moore chain of the
 * OUTER contour only.
 *
 * <p>Horizontal/vertical steps contribute 1 px; diagonal steps contribute
 * {@code sqrt(2)}. The closing step is included. Internal holes do not
 * contribute to the outer wall. Boundary pixel count is not used as
 * perimeter.</p>
 */
public final class MooreChainPerimeter {

    /** Clockwise Moore directions: N, NE, E, SE, S, SW, W, NW. */
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};

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
     * Computes perimeter for a binary mask of a single component using a
     * Moore-neighbor trace of the outer contour.
     *
     * @param mask true = lumen pixel; width/height define the local mask
     */
    public static Result measure(
            final boolean[] mask,
            final int width,
            final int height,
            final double pixelWidthUm,
            final double pixelHeightUm) {
        validateMask(mask, width, height);
        final Trace trace = traceOuterContour(mask, width, height);
        if (trace == null || trace.size() < 2) {
            final List<Point> unique = uniqueBoundaryPixels(trace, width);
            return new Result(Double.NaN, Double.NaN, unique);
        }

        double lengthPx = 0.0;
        double lengthUm = 0.0;
        for (int i = 0; i < trace.size(); i++) {
            final int ax = trace.xs[i];
            final int ay = trace.ys[i];
            final int bx = trace.xs[(i + 1) % trace.size()];
            final int by = trace.ys[(i + 1) % trace.size()];
            final int dx = bx - ax;
            final int dy = by - ay;
            final int adx = Math.abs(dx);
            final int ady = Math.abs(dy);
            if (adx > 1 || ady > 1 || (adx == 0 && ady == 0)) {
                throw new IllegalStateException(
                        "Moore contour step is not an adjacent HV/diagonal move at ("
                                + ax + "," + ay + ") -> (" + bx + "," + by + ")");
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
        return new Result(lengthPx, lengthUm, uniqueBoundaryPixels(trace, width));
    }

    /**
     * Returns unique OUTER-contour boundary pixels in first-occurrence Moore
     * order. Internal-hole boundaries are excluded.
     */
    public static List<Point> extractBoundaryPixels(
            final boolean[] mask,
            final int width,
            final int height) {
        validateMask(mask, width, height);
        final Trace trace = traceOuterContour(mask, width, height);
        return uniqueBoundaryPixels(trace, width);
    }

    private static void validateMask(
            final boolean[] mask, final int width, final int height) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "mask dimensions must be positive: " + width + "x" + height);
        }
        final long expected = (long) width * (long) height;
        if (expected != mask.length) {
            throw new IllegalArgumentException(
                    "mask length " + mask.length + " != width*height " + expected);
        }
    }

    /**
     * Traces the outer contour with Jacob-style directed-entry closure.
     *
     * <p>Closure: after the first transition {@code start -> firstNext}, the
     * contour closes when a later step would enter {@code start} and continuing
     * from that start state would again produce {@code start -> firstNext}.
     * The duplicated closing {@code start}/{@code firstNext} pair is not
     * appended.</p>
     *
     * @return ordered raw Moore vertices (may revisit pixels), or {@code null}
     *         when the mask has no foreground
     */
    private static Trace traceOuterContour(
            final boolean[] mask, final int width, final int height) {
        int startX = -1;
        int startY = -1;
        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    startX = x;
                    startY = y;
                    break outer;
                }
            }
        }
        if (startX < 0) {
            return null;
        }

        // Isolated single pixel: no foreground Moore neighbor.
        final int[] first = nextMoore(mask, width, height, startX, startY, startX - 1, startY);
        if (first == null) {
            final Trace single = new Trace(1);
            single.add(startX, startY);
            return single;
        }

        final int firstNextX = first[0];
        final int firstNextY = first[1];
        final int firstBackX = first[2];
        final int firstBackY = first[3];

        final int maxSteps = Math.max(8, mask.length * 8);
        final Trace raw = new Trace(Math.min(maxSteps, mask.length + 8));
        raw.add(startX, startY);
        raw.add(firstNextX, firstNextY);

        int currentX = firstNextX;
        int currentY = firstNextY;
        int backX = firstBackX;
        int backY = firstBackY;

        for (int step = 0; step < maxSteps; step++) {
            final int[] next = nextMoore(mask, width, height, currentX, currentY, backX, backY);
            if (next == null) {
                throw new IllegalStateException(
                        "Moore outer contour lost the path at (" + currentX + "," + currentY + ")");
            }
            final int nx = next[0];
            final int ny = next[1];
            final int nbx = next[2];
            final int nby = next[3];

            if (nx == startX && ny == startY) {
                // Directed-entry closure (Jacob): entering start would again
                // depart start -> firstNext under the resulting backtrack.
                final int[] fromStart = nextMoore(
                        mask, width, height, startX, startY, nbx, nby);
                if (fromStart != null
                        && fromStart[0] == firstNextX
                        && fromStart[1] == firstNextY) {
                    return raw;
                }
            }

            raw.add(nx, ny);
            currentX = nx;
            currentY = ny;
            backX = nbx;
            backY = nby;
        }
        throw new IllegalStateException(
                "Moore outer contour failed to close within " + maxSteps + " steps");
    }

    /**
     * One Moore step from {@code (cx,cy)} with backtrack {@code (bx,by)}.
     *
     * @return {@code {nextX, nextY, newBackX, newBackY}} or {@code null}
     */
    private static int[] nextMoore(
            final boolean[] mask,
            final int width,
            final int height,
            final int cx,
            final int cy,
            final int bx,
            final int by) {
        final int backDir = directionIndex(bx - cx, by - cy);
        if (backDir < 0) {
            throw new IllegalStateException(
                    "Backtrack is not a Moore neighbor of current: ("
                            + cx + "," + cy + ") back=(" + bx + "," + by + ")");
        }
        for (int i = 1; i <= 8; i++) {
            final int dir = (backDir + i) % 8;
            final int nx = cx + DX[dir];
            final int ny = cy + DY[dir];
            if (isForeground(mask, width, height, nx, ny)) {
                final int prevDir = (dir + 7) % 8;
                final int nbx = cx + DX[prevDir];
                final int nby = cy + DY[prevDir];
                return new int[] {nx, ny, nbx, nby};
            }
        }
        return null;
    }

    private static int directionIndex(final int dx, final int dy) {
        for (int i = 0; i < 8; i++) {
            if (DX[i] == dx && DY[i] == dy) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isForeground(
            final boolean[] mask,
            final int width,
            final int height,
            final int x,
            final int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return mask[y * width + x];
    }

    private static List<Point> uniqueBoundaryPixels(final Trace trace, final int width) {
        final List<Point> unique = new ArrayList<Point>();
        if (trace == null || trace.size() == 0) {
            return unique;
        }
        final BitSet seen = new BitSet();
        for (int i = 0; i < trace.size(); i++) {
            final int x = trace.xs[i];
            final int y = trace.ys[i];
            final int index = y * width + x;
            if (seen.get(index)) {
                continue;
            }
            seen.set(index);
            unique.add(new Point(x, y));
        }
        return unique;
    }

    /** Compact ordered contour vertices without per-step Point allocation. */
    private static final class Trace {
        private int[] xs;
        private int[] ys;
        private int size;

        Trace(final int capacity) {
            this.xs = new int[Math.max(1, capacity)];
            this.ys = new int[Math.max(1, capacity)];
            this.size = 0;
        }

        void add(final int x, final int y) {
            if (size == xs.length) {
                final int n = xs.length * 2;
                final int[] nxs = new int[n];
                final int[] nys = new int[n];
                System.arraycopy(xs, 0, nxs, 0, size);
                System.arraycopy(ys, 0, nys, 0, size);
                xs = nxs;
                ys = nys;
            }
            xs[size] = x;
            ys[size] = y;
            size++;
        }

        int size() {
            return size;
        }
    }
}
