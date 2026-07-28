package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Deterministic initial free-ellipse analysis of one 8-bit classified image.
 * Fits all exposed pixel-edge midpoint support points without outlier exclusion
 * and without modifying the classified input.
 */
public final class InitialEllipseAnalyzer {

    private static final double INSIDE_TOLERANCE = 1.0e-12;

    private static final int[] DX = {
            -1, 0, 1,
            -1,    1,
            -1, 0, 1
    };
    private static final int[] DY = {
            -1, -1, -1,
             0,      0,
             1,  1,  1
    };

    private final FreeEllipseFitter fitter;

    public InitialEllipseAnalyzer() {
        this.fitter = new FreeEllipseFitter();
    }

    /**
     * Analyzes one classified ByteProcessor without mutating it.
     */
    public InitialEllipseAnalysisResult analyze(final ByteProcessor classifiedProcessor) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }

        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null) {
            throw new IllegalArgumentException("classifiedProcessor pixels must not be null");
        }

        final List<Component> components = extractLumenComponents(pixels, width, height);
        if (components.isEmpty()) {
            throw new IllegalArgumentException(
                    "NO_LUMEN_COMPONENT: no Class 3 Lumen pixels were found; "
                            + "a direct Trainable Weka result stores Class 3 as pixel value "
                            + ClassifierLabels.LUMEN_RESULT_VALUE);
        }

        final Component selected = selectComponent(components);
        final List<Point> selectedPixels = sortPoints(selected.pixels);
        final Set<Long> selectedKeys = toKeySet(selectedPixels);

        final List<Point2D.Double> supportPoints =
                extractSupportPoints(selectedPixels, selectedKeys, width, height);
        if (supportPoints.size() < 6 || distinctSupportCount(supportPoints) < 6) {
            throw new IllegalArgumentException(
                    "INSUFFICIENT_BOUNDARY_SUPPORT: need at least 6 distinct support points; got "
                            + supportPoints.size() + " points and "
                            + distinctSupportCount(supportPoints) + " distinct pairs");
        }

        final EllipseFitResult fitResult = fitter.fit(supportPoints);
        final EllipseModel fitted = fitResult.getFittedEllipse();

        final FillAnalysis fill = analyzeFill(
                pixels, width, height, selectedKeys, fitted);

        final Rectangle bounds = new Rectangle(
                selected.minX,
                selected.minY,
                selected.maxX - selected.minX + 1,
                selected.maxY - selected.minY + 1);

        return new InitialEllipseAnalysisResult(
                width,
                height,
                components.size(),
                selectedPixels.size(),
                bounds,
                selectedPixels,
                supportPoints,
                supportPoints.size(),
                fitResult,
                fill.proposedFillPixels,
                fill.proposedFillPixels.size(),
                fill.proposedWoodPixelCount,
                fill.proposedRayPixelCount,
                fill.proposedBackgroundPixelCount,
                fill.proposedOtherClassPixelCount,
                fill.otherLumenPixelsInsideEllipse,
                fill.totalPixelCentersInsideEllipse);
    }

    private static List<Component> extractLumenComponents(
            final byte[] pixels,
            final int width,
            final int height) {
        final boolean[] visited = new boolean[width * height];
        final List<Component> components = new ArrayList<Component>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = y * width + x;
                if (visited[index]) {
                    continue;
                }
                if (unsigned(pixels[index]) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }

                final Component component = floodFill(pixels, visited, width, height, x, y);
                components.add(component);
            }
        }
        return components;
    }

    private static Component floodFill(
            final byte[] pixels,
            final boolean[] visited,
            final int width,
            final int height,
            final int startX,
            final int startY) {
        final List<Point> members = new ArrayList<Point>();
        final Queue<Point> queue = new ArrayDeque<Point>();
        queue.add(new Point(startX, startY));
        visited[startY * width + startX] = true;

        int minX = startX;
        int minY = startY;
        int maxX = startX;
        int maxY = startY;

        while (!queue.isEmpty()) {
            final Point current = queue.remove();
            members.add(new Point(current.x, current.y));
            if (current.x < minX) {
                minX = current.x;
            }
            if (current.y < minY) {
                minY = current.y;
            }
            if (current.x > maxX) {
                maxX = current.x;
            }
            if (current.y > maxY) {
                maxY = current.y;
            }

            for (int i = 0; i < DX.length; i++) {
                final int nx = current.x + DX[i];
                final int ny = current.y + DY[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                final int nIndex = ny * width + nx;
                if (visited[nIndex]) {
                    continue;
                }
                if (unsigned(pixels[nIndex]) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }
                visited[nIndex] = true;
                queue.add(new Point(nx, ny));
            }
        }

        return new Component(members, minX, minY, maxX, maxY);
    }

    private static Component selectComponent(final List<Component> components) {
        Component best = components.get(0);
        for (int i = 1; i < components.size(); i++) {
            final Component candidate = components.get(i);
            if (isPreferred(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Selection order: greater area, then smaller minY, minX, maxY, maxX.
     */
    private static boolean isPreferred(final Component candidate, final Component incumbent) {
        final int areaCmp = Integer.compare(candidate.pixels.size(), incumbent.pixels.size());
        if (areaCmp != 0) {
            return areaCmp > 0;
        }
        if (candidate.minY != incumbent.minY) {
            return candidate.minY < incumbent.minY;
        }
        if (candidate.minX != incumbent.minX) {
            return candidate.minX < incumbent.minX;
        }
        if (candidate.maxY != incumbent.maxY) {
            return candidate.maxY < incumbent.maxY;
        }
        if (candidate.maxX != incumbent.maxX) {
            return candidate.maxX < incumbent.maxX;
        }
        return false;
    }

    private static List<Point> sortPoints(final List<Point> points) {
        final List<Point> sorted = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            sorted.add(new Point(p.x, p.y));
        }
        Collections.sort(sorted, new Comparator<Point>() {
            @Override
            public int compare(final Point a, final Point b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
        return sorted;
    }

    private static Set<Long> toKeySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            keys.add(pack(p.x, p.y));
        }
        return keys;
    }

    /**
     * Package-visible helper for deterministic exposed-edge midpoint extraction tests.
     */
    static List<Point2D.Double> supportPointsForSelectedPixels(
            final List<Point> selectedPixels,
            final int width,
            final int height) {
        return extractSupportPoints(selectedPixels, toKeySet(selectedPixels), width, height);
    }

    static List<Point2D.Double> extractSupportPoints(
            final List<Point> selectedPixels,
            final Set<Long> selectedKeys,
            final int width,
            final int height) {
        final List<Point2D.Double> support = new ArrayList<Point2D.Double>();
        for (int i = 0; i < selectedPixels.size(); i++) {
            final Point p = selectedPixels.get(i);
            final int x = p.x;
            final int y = p.y;

            // NORTH
            if (isExposed(x, y - 1, selectedKeys, width, height)) {
                support.add(new Point2D.Double(x + 0.5, y));
            }
            // EAST
            if (isExposed(x + 1, y, selectedKeys, width, height)) {
                support.add(new Point2D.Double(x + 1.0, y + 0.5));
            }
            // SOUTH
            if (isExposed(x, y + 1, selectedKeys, width, height)) {
                support.add(new Point2D.Double(x + 0.5, y + 1.0));
            }
            // WEST
            if (isExposed(x - 1, y, selectedKeys, width, height)) {
                support.add(new Point2D.Double(x, y + 0.5));
            }
        }
        return support;
    }

    private static boolean isExposed(
            final int nx,
            final int ny,
            final Set<Long> selectedKeys,
            final int width,
            final int height) {
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
            return true;
        }
        return !selectedKeys.contains(pack(nx, ny));
    }

    private static int distinctSupportCount(final List<Point2D.Double> points) {
        final Set<Long> keys = new HashSet<Long>();
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double p = points.get(i);
            keys.add((Double.doubleToLongBits(p.x) << 1) ^ Double.doubleToLongBits(p.y));
        }
        return keys.size();
    }

    private static FillAnalysis analyzeFill(
            final byte[] pixels,
            final int width,
            final int height,
            final Set<Long> selectedKeys,
            final EllipseModel fitted) {
        final List<Point> proposed = new ArrayList<Point>();
        int wood = 0;
        int ray = 0;
        int background = 0;
        int other = 0;
        int otherLumen = 0;
        int totalInside = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final double centerX = x + 0.5;
                final double centerY = y + 0.5;
                if (fitted.normalizedRadius(centerX, centerY) > 1.0 + INSIDE_TOLERANCE) {
                    continue;
                }
                totalInside++;

                final int resultValue = unsigned(pixels[y * width + x]);
                final boolean selected = selectedKeys.contains(pack(x, y));

                if (selected) {
                    continue;
                }
                if (resultValue == ClassifierLabels.LUMEN_RESULT_VALUE) {
                    otherLumen++;
                    continue;
                }

                proposed.add(new Point(x, y));
                if (resultValue == ClassifierLabels.WOOD_RESULT_VALUE) {
                    wood++;
                } else if (resultValue == ClassifierLabels.RAY_RESULT_VALUE) {
                    ray++;
                } else if (resultValue == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                    background++;
                } else {
                    other++;
                }
            }
        }

        final List<Point> ordered = sortPoints(proposed);
        final int sum = wood + ray + background + other;
        if (sum != ordered.size()) {
            throw new IllegalStateException(
                    "Proposed fill class counts do not sum to proposed fill size: "
                            + sum + " vs " + ordered.size());
        }

        final FillAnalysis fill = new FillAnalysis();
        fill.proposedFillPixels = ordered;
        fill.proposedWoodPixelCount = wood;
        fill.proposedRayPixelCount = ray;
        fill.proposedBackgroundPixelCount = background;
        fill.proposedOtherClassPixelCount = other;
        fill.otherLumenPixelsInsideEllipse = otherLumen;
        fill.totalPixelCentersInsideEllipse = totalInside;
        return fill;
    }

    private static int unsigned(final byte value) {
        return value & 0xff;
    }

    /**
     * Counts all 256 possible byte values without modifying the processor.
     * Returns a defensive copy of the counts array.
     */
    static int[] countResultPixelHistogram(final ByteProcessor classifiedProcessor) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null) {
            throw new IllegalArgumentException("classifiedProcessor pixels must not be null");
        }
        final int[] counts = new int[256];
        for (int i = 0; i < pixels.length; i++) {
            counts[unsigned(pixels[i])]++;
        }
        final int[] copy = new int[256];
        System.arraycopy(counts, 0, copy, 0, 256);
        return copy;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static final class Component {
        private final List<Point> pixels;
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private Component(
                final List<Point> pixels,
                final int minX,
                final int minY,
                final int maxX,
                final int maxY) {
            this.pixels = pixels;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class FillAnalysis {
        private List<Point> proposedFillPixels;
        private int proposedWoodPixelCount;
        private int proposedRayPixelCount;
        private int proposedBackgroundPixelCount;
        private int proposedOtherClassPixelCount;
        private int otherLumenPixelsInsideEllipse;
        private int totalPixelCentersInsideEllipse;
    }
}
