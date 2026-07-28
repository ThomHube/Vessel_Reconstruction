package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a source-safe diagnostic fill preview for the frozen single-interval
 * refitted ellipse. Does not fit ellipses, detect intervals, accept results, or
 * modify classified pixels.
 */
public final class RefittedEllipseFillPreviewAnalyzer {

    private static final double INSIDE_TOLERANCE = 1.0e-12;
    private static final double CIRCULARITY_OVERSHOOT = 1.0e-9;

    private static final int[] CARDINAL_DX = {0, 1, 0, -1};
    private static final int[] CARDINAL_DY = {-1, 0, 1, 0};

    public RefittedEllipseFillPreviewResult analyze(
            final ByteProcessor classifiedProcessor,
            final InitialEllipseAnalysisResult initialAnalysis,
            final SingleIntervalEllipseRefitResult refitResult) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (initialAnalysis == null) {
            throw new IllegalArgumentException("initialAnalysis must not be null");
        }
        if (refitResult == null) {
            throw new IllegalArgumentException("refitResult must not be null");
        }

        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width != initialAnalysis.getImageWidth()
                || height != initialAnalysis.getImageHeight()) {
            throw new IllegalArgumentException(
                    "Image dimensions must match initialAnalysis: processor="
                            + width + "x" + height + ", analysis="
                            + initialAnalysis.getImageWidth() + "x"
                            + initialAnalysis.getImageHeight());
        }

        final EllipseModel baselineEllipse =
                refitResult.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refittedEllipse =
                refitResult.getIntervalExcludedFit().getFittedEllipse();
        if (baselineEllipse == null || refittedEllipse == null) {
            throw new IllegalArgumentException(
                    "refitResult must contain valid baseline and refitted ellipses");
        }

        return analyzeCore(
                classifiedProcessor,
                initialAnalysis.getSelectedLumenPixels(),
                baselineEllipse,
                refittedEllipse);
    }

    /**
     * Package-visible test hook using the same private fill and metric logic.
     */
    RefittedEllipseFillPreviewResult analyzeForTesting(
            final ByteProcessor classifiedProcessor,
            final List<Point> selectedObservedLumenPixels,
            final EllipseModel baselineEllipse,
            final EllipseModel refittedEllipse) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (selectedObservedLumenPixels == null) {
            throw new IllegalArgumentException(
                    "selectedObservedLumenPixels must not be null");
        }
        if (baselineEllipse == null || refittedEllipse == null) {
            throw new IllegalArgumentException("Ellipses must not be null");
        }
        return analyzeCore(
                classifiedProcessor,
                selectedObservedLumenPixels,
                baselineEllipse,
                refittedEllipse);
    }

    private static RefittedEllipseFillPreviewResult analyzeCore(
            final ByteProcessor classifiedProcessor,
            final List<Point> selectedObservedLumenPixels,
            final EllipseModel baselineEllipse,
            final EllipseModel refittedEllipse) {
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null) {
            throw new IllegalArgumentException("classifiedProcessor pixels must not be null");
        }
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (pixels.length != width * height) {
            throw new IllegalArgumentException(
                    "Pixel array length does not match image dimensions");
        }

        if (selectedObservedLumenPixels.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one selected observed lumen pixel is required");
        }

        final List<Point> selected = copyAndValidateSelected(
                selectedObservedLumenPixels, pixels, width, height);
        final Set<Long> selectedKeys = toKeySet(selected);

        final FillScan baselineScan = scanEllipse(
                pixels, width, height, selectedKeys, baselineEllipse);
        final FillScan refittedScan = scanEllipse(
                pixels, width, height, selectedKeys, refittedEllipse);

        final Set<Long> baselineKeys = toKeySet(baselineScan.proposedFill);
        final Set<Long> refittedKeys = toKeySet(refittedScan.proposedFill);

        final List<Point> shared = intersectionPoints(baselineKeys, refittedKeys);
        final List<Point> refitOnly = differencePoints(refittedKeys, baselineKeys);
        final List<Point> baselineOnly = differencePoints(baselineKeys, refittedKeys);

        if (shared.size() + refitOnly.size() != refittedScan.proposedFill.size()) {
            throw new IllegalStateException(
                    "shared + refit-only must equal refitted proposed fill");
        }
        if (shared.size() + baselineOnly.size() != baselineScan.proposedFill.size()) {
            throw new IllegalStateException(
                    "shared + baseline-only must equal baseline proposed fill");
        }

        final ClassBuckets buckets = classifyRefittedFill(
                pixels, width, refittedScan.proposedFill);

        final List<Point> selectedOutside = new ArrayList<Point>();
        for (int i = 0; i < selected.size(); i++) {
            final Point point = selected.get(i);
            if (!isInsideOrOn(refittedEllipse, point.x, point.y)) {
                selectedOutside.add(new Point(point.x, point.y));
            }
        }
        sortPointsInPlace(selectedOutside);

        final List<Point> otherLumenInside = sortPoints(refittedScan.otherLumenInside);
        final boolean overlap = !otherLumenInside.isEmpty();

        final List<Point> preview = unionPoints(selectedKeys, refittedKeys);
        if (preview.size() != selected.size() + refittedScan.proposedFill.size()) {
            throw new IllegalStateException(
                    "preview must equal selected union refitted fill");
        }

        final int originalArea = selected.size();
        final int originalPerimeter = exposedCardinalPerimeter(selectedKeys, width, height);
        final double originalCircularity = circularity(originalArea, originalPerimeter);

        final Set<Long> previewKeys = toKeySet(preview);
        final int previewArea = preview.size();
        final int previewPerimeter = exposedCardinalPerimeter(previewKeys, width, height);
        final double previewCircularity = circularity(previewArea, previewPerimeter);
        final double circularityChange = previewCircularity - originalCircularity;
        final double addedAreaFraction =
                refittedScan.proposedFill.size() / (double) originalArea;
        final double ellipseAreaRatio =
                refittedEllipse.getArea() / (double) originalArea;

        return new RefittedEllipseFillPreviewResult(
                width,
                height,
                baselineEllipse,
                refittedEllipse,
                selected,
                baselineScan.proposedFill,
                refittedScan.proposedFill,
                shared,
                refitOnly,
                baselineOnly,
                buckets.wood,
                buckets.ray,
                buckets.background,
                buckets.other,
                selectedOutside,
                otherLumenInside,
                preview,
                originalArea,
                baselineScan.totalInside,
                refittedScan.totalInside,
                baselineScan.proposedFill.size(),
                refittedScan.proposedFill.size(),
                shared.size(),
                refitOnly.size(),
                baselineOnly.size(),
                buckets.wood.size(),
                buckets.ray.size(),
                buckets.background.size(),
                buckets.other.size(),
                selectedOutside.size(),
                otherLumenInside.size(),
                originalArea,
                originalPerimeter,
                originalCircularity,
                previewArea,
                previewPerimeter,
                previewCircularity,
                circularityChange,
                addedAreaFraction,
                ellipseAreaRatio,
                overlap);
    }

    private static List<Point> copyAndValidateSelected(
            final List<Point> selectedObservedLumenPixels,
            final byte[] pixels,
            final int width,
            final int height) {
        final List<Point> selected = new ArrayList<Point>(selectedObservedLumenPixels.size());
        final Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i < selectedObservedLumenPixels.size(); i++) {
            final Point point = selectedObservedLumenPixels.get(i);
            if (point == null) {
                throw new IllegalArgumentException(
                        "selectedObservedLumenPixels must not contain null entries");
            }
            if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) {
                throw new IllegalArgumentException(
                        "Selected observed lumen pixel out of bounds: ("
                                + point.x + ", " + point.y + ")");
            }
            final int resultValue = unsigned(pixels[point.y * width + point.x]);
            if (resultValue != ClassifierLabels.LUMEN_RESULT_VALUE) {
                throw new IllegalArgumentException(
                        "Selected observed lumen pixels must have raw result value "
                                + ClassifierLabels.LUMEN_RESULT_VALUE
                                + "; found " + resultValue + " at (" + point.x + ", "
                                + point.y + ")");
            }
            final long key = pack(point.x, point.y);
            if (!seen.add(Long.valueOf(key))) {
                throw new IllegalArgumentException(
                        "selectedObservedLumenPixels must not contain duplicates");
            }
            selected.add(new Point(point.x, point.y));
        }
        sortPointsInPlace(selected);
        return selected;
    }

    private static FillScan scanEllipse(
            final byte[] pixels,
            final int width,
            final int height,
            final Set<Long> selectedKeys,
            final EllipseModel ellipse) {
        final List<Point> proposed = new ArrayList<Point>();
        final List<Point> otherLumen = new ArrayList<Point>();
        int totalInside = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isInsideOrOn(ellipse, x, y)) {
                    continue;
                }
                totalInside++;
                final long key = pack(x, y);
                if (selectedKeys.contains(Long.valueOf(key))) {
                    continue;
                }
                final int resultValue = unsigned(pixels[y * width + x]);
                if (resultValue == ClassifierLabels.LUMEN_RESULT_VALUE) {
                    otherLumen.add(new Point(x, y));
                    continue;
                }
                proposed.add(new Point(x, y));
            }
        }

        final FillScan scan = new FillScan();
        scan.proposedFill = sortPoints(proposed);
        scan.otherLumenInside = otherLumen;
        scan.totalInside = totalInside;
        return scan;
    }

    static boolean isInsideOrOn(final EllipseModel ellipse, final int x, final int y) {
        final double centerX = x + 0.5;
        final double centerY = y + 0.5;
        return ellipse.normalizedRadius(centerX, centerY) <= 1.0 + INSIDE_TOLERANCE;
    }

    private static ClassBuckets classifyRefittedFill(
            final byte[] pixels,
            final int width,
            final List<Point> refittedFill) {
        final List<Point> wood = new ArrayList<Point>();
        final List<Point> ray = new ArrayList<Point>();
        final List<Point> background = new ArrayList<Point>();
        final List<Point> other = new ArrayList<Point>();
        for (int i = 0; i < refittedFill.size(); i++) {
            final Point point = refittedFill.get(i);
            final int resultValue = unsigned(pixels[point.y * width + point.x]);
            if (resultValue == ClassifierLabels.LUMEN_RESULT_VALUE) {
                throw new IllegalStateException(
                        "Raw Lumen value must never appear in a proposed fill list");
            }
            if (resultValue == ClassifierLabels.WOOD_RESULT_VALUE) {
                wood.add(new Point(point.x, point.y));
            } else if (resultValue == ClassifierLabels.RAY_RESULT_VALUE) {
                ray.add(new Point(point.x, point.y));
            } else if (resultValue == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                background.add(new Point(point.x, point.y));
            } else {
                other.add(new Point(point.x, point.y));
            }
        }
        final ClassBuckets buckets = new ClassBuckets();
        buckets.wood = sortPoints(wood);
        buckets.ray = sortPoints(ray);
        buckets.background = sortPoints(background);
        buckets.other = sortPoints(other);
        return buckets;
    }

    private static int exposedCardinalPerimeter(
            final Set<Long> memberKeys,
            final int width,
            final int height) {
        int perimeter = 0;
        for (final Long keyObj : memberKeys) {
            final long key = keyObj.longValue();
            final int x = (int) (key >> 32);
            final int y = (int) key;
            for (int i = 0; i < CARDINAL_DX.length; i++) {
                final int nx = x + CARDINAL_DX[i];
                final int ny = y + CARDINAL_DY[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !memberKeys.contains(Long.valueOf(pack(nx, ny)))) {
                    perimeter++;
                }
            }
        }
        return perimeter;
    }

    private static double circularity(final int area, final int perimeter) {
        final double value = 4.0 * Math.PI * area / (perimeter * (double) perimeter);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite circularity");
        }
        if (value > 1.0 && value < 1.0 + CIRCULARITY_OVERSHOOT) {
            return 1.0;
        }
        return value;
    }

    private static List<Point> intersectionPoints(
            final Set<Long> a,
            final Set<Long> b) {
        final List<Point> points = new ArrayList<Point>();
        for (final Long keyObj : a) {
            if (b.contains(keyObj)) {
                points.add(unpack(keyObj.longValue()));
            }
        }
        return sortPoints(points);
    }

    private static List<Point> differencePoints(
            final Set<Long> a,
            final Set<Long> b) {
        final List<Point> points = new ArrayList<Point>();
        for (final Long keyObj : a) {
            if (!b.contains(keyObj)) {
                points.add(unpack(keyObj.longValue()));
            }
        }
        return sortPoints(points);
    }

    private static List<Point> unionPoints(
            final Set<Long> a,
            final Set<Long> b) {
        final Set<Long> union = new HashSet<Long>(a);
        union.addAll(b);
        final List<Point> points = new ArrayList<Point>(union.size());
        for (final Long keyObj : union) {
            points.add(unpack(keyObj.longValue()));
        }
        return sortPoints(points);
    }

    private static List<Point> sortPoints(final List<Point> points) {
        final List<Point> sorted = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            sorted.add(new Point(point.x, point.y));
        }
        sortPointsInPlace(sorted);
        return sorted;
    }

    private static void sortPointsInPlace(final List<Point> points) {
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(final Point a, final Point b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
    }

    private static Set<Long> toKeySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            keys.add(Long.valueOf(pack(point.x, point.y)));
        }
        return keys;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Point unpack(final long key) {
        return new Point((int) (key >> 32), (int) key);
    }

    private static int unsigned(final byte value) {
        return value & 0xff;
    }

    private static final class FillScan {
        private List<Point> proposedFill;
        private List<Point> otherLumenInside;
        private int totalInside;
    }

    private static final class ClassBuckets {
        private List<Point> wood;
        private List<Point> ray;
        private List<Point> background;
        private List<Point> other;
    }
}
