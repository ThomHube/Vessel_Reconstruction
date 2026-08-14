package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact nearest-lumen search using a spatial grid over boundary pixel centers.
 *
 * <p>The nearest lumen is defined as the other lumen with the minimum
 * wall-to-wall Euclidean distance between boundary pixel centers. The
 * reported centroid distances are measured to that same wall-nearest lumen,
 * so every field in one {@link Result} refers to one consistent neighbor.
 * Same-component points are excluded.</p>
 */
public final class NearestLumenDistance {

    public static final class LumenGeometry {
        public final String lumenUid;
        public final double centroidX;
        public final double centroidY;
        public final List<Point> boundaryPixels;

        public LumenGeometry(
                final String lumenUid,
                final double centroidX,
                final double centroidY,
                final List<Point> boundaryPixels) {
            this.lumenUid = lumenUid;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
            this.boundaryPixels = boundaryPixels;
        }
    }

    public static final class Result {
        public final String nearestLumenUid;
        public final double nearestCentroidDistancePx;
        public final double nearestCentroidDistanceUm;
        public final double nearestWallDistancePx;
        public final double nearestWallDistanceUm;
        public final String nearestDistanceScope;

        public Result(
                final String nearestLumenUid,
                final double nearestCentroidDistancePx,
                final double nearestCentroidDistanceUm,
                final double nearestWallDistancePx,
                final double nearestWallDistanceUm,
                final String nearestDistanceScope) {
            this.nearestLumenUid = nearestLumenUid;
            this.nearestCentroidDistancePx = nearestCentroidDistancePx;
            this.nearestCentroidDistanceUm = nearestCentroidDistanceUm;
            this.nearestWallDistancePx = nearestWallDistancePx;
            this.nearestWallDistanceUm = nearestWallDistanceUm;
            this.nearestDistanceScope = nearestDistanceScope;
        }

        public static Result unavailable() {
            return new Result(null, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null);
        }
    }

    private NearestLumenDistance() {
    }

    public static Result[] computeAll(
            final List<LumenGeometry> lumina,
            final double pixelWidthUm,
            final double pixelHeightUm) {
        final Result[] results = new Result[lumina.size()];
        if (lumina.size() < 2) {
            for (int i = 0; i < results.length; i++) {
                results[i] = Result.unavailable();
            }
            return results;
        }

        // Build global spatial grid of boundary points labeled by lumen index.
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int totalPoints = 0;
        for (int i = 0; i < lumina.size(); i++) {
            for (final Point p : lumina.get(i).boundaryPixels) {
                if (p.x < minX) {
                    minX = p.x;
                }
                if (p.y < minY) {
                    minY = p.y;
                }
                if (p.x > maxX) {
                    maxX = p.x;
                }
                if (p.y > maxY) {
                    maxY = p.y;
                }
                totalPoints++;
            }
        }

        final int cell = Math.max(
                8,
                (int) Math.ceil(Math.sqrt(Math.max(1, totalPoints) / 64.0)));

        final Map<Long, List<int[]>> grid = new HashMap<Long, List<int[]>>();

        for (int i = 0; i < lumina.size(); i++) {
            for (final Point p : lumina.get(i).boundaryPixels) {
                final long key = cellKey(
                        (p.x - minX) / cell,
                        (p.y - minY) / cell);

                List<int[]> bucket = grid.get(Long.valueOf(key));

                if (bucket == null) {
                    bucket = new ArrayList<int[]>();
                    grid.put(Long.valueOf(key), bucket);
                }

                bucket.add(new int[] { p.x, p.y, i });
            }
        }

        for (int i = 0; i < lumina.size(); i++) {
            final LumenGeometry a = lumina.get(i);

            int bestWallLumenIndex = -1;
            double bestWallPx = Double.POSITIVE_INFINITY;
            double bestWallUm = Double.POSITIVE_INFINITY;

            // Exact wall distance via expanding ring search on the grid.
            // The nearest lumen is defined by this minimum wall-to-wall distance.
            for (final Point p : a.boundaryPixels) {
                final int cx = (p.x - minX) / cell;
                final int cy = (p.y - minY) / cell;

                int radius = 0;
                boolean foundCloser = false;
                double localBest = bestWallPx;

                while (true) {
                    boolean anyCell = false;

                    for (int gy = cy - radius; gy <= cy + radius; gy++) {
                        for (int gx = cx - radius; gx <= cx + radius; gx++) {
                            if (radius > 0
                                    && Math.abs(gx - cx) != radius
                                    && Math.abs(gy - cy) != radius) {
                                continue;
                            }

                            final List<int[]> bucket =
                                    grid.get(Long.valueOf(cellKey(gx, gy)));

                            if (bucket == null) {
                                continue;
                            }

                            anyCell = true;

                            for (int b = 0; b < bucket.size(); b++) {
                                final int[] q = bucket.get(b);

                                if (q[2] == i) {
                                    continue;
                                }

                                final double dPx =
                                        Math.hypot(p.x - q[0], p.y - q[1]);

                                if (dPx < localBest) {
                                    localBest = dPx;
                                    bestWallPx = dPx;
                                    bestWallUm = Math.hypot(
                                            (p.x - q[0]) * pixelWidthUm,
                                            (p.y - q[1]) * pixelHeightUm);
                                    bestWallLumenIndex = q[2];
                                    foundCloser = true;
                                }
                            }
                        }
                    }

                    // Termination: no closer point can exist beyond this cell radius.
                    final double minPossibleOutside = radius * cell;

                    if (foundCloser && minPossibleOutside >= bestWallPx) {
                        break;
                    }

                    if (!anyCell
                            && radius > 0
                            && minPossibleOutside >= bestWallPx) {
                        break;
                    }

                    radius++;

                    if (radius
                            > Math.max(maxX - minX, maxY - minY) / cell + 2) {
                        break;
                    }
                }
            }

            if (bestWallLumenIndex < 0) {
                results[i] = Result.unavailable();
                continue;
            }

            // Every result field must describe the same neighbor.
            // The neighbor is selected by minimum wall distance.
            final LumenGeometry nearest = lumina.get(bestWallLumenIndex);

            final double dCx = a.centroidX - nearest.centroidX;
            final double dCy = a.centroidY - nearest.centroidY;

            final double centroidPx = Math.hypot(dCx, dCy);

            final double centroidUm = Math.hypot(
                    dCx * pixelWidthUm,
                    dCy * pixelHeightUm);

            results[i] = new Result(
                    nearest.lumenUid,
                    centroidPx,
                    centroidUm,
                    bestWallPx,
                    bestWallUm,
                    "SEGMENT");
        }

        return results;
    }

    private static long cellKey(final int gx, final int gy) {
        return (((long) gx) << 32) ^ (gy & 0xffffffffL);
    }
}
