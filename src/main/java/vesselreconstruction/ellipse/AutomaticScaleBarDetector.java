package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Two-resolution automatic detector for a red graphical scale bar.
 *
 * <p>Measurement is the distance between terminal-tick centerlines, not the
 * outer component bounding-box width and not the red text.</p>
 */
public final class AutomaticScaleBarDetector {

    private static final int MIN_RED_DOMINANCE_GUARD = 18;
    private static final int MIN_RED_CHANNEL = 60;
    private static final int SEARCH_MARGIN_ORIGINAL_PX = 24;
    private static final double MAX_SHAFT_ANGLE_DEGREES = 12.0;
    private static final double MIN_ASPECT_RATIO = 4.0;

    public ScaleBarDetection detect(
            final ImagePlus sourceImage,
            final DownsampledSampleMask approvedMask,
            final ColorProcessor downsampledRgbPreview) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        if (approvedMask == null) {
            throw new IllegalArgumentException("approvedMask must not be null");
        }
        if (sourceImage.getWidth() != approvedMask.getOriginalWidth()
                || sourceImage.getHeight() != approvedMask.getOriginalHeight()) {
            throw new IllegalArgumentException(
                    "approved mask original dimensions must match the source image");
        }

        // Max-red pooling preserves thin bars that area-average previews wash out.
        final ColorProcessor lowRgb = downsampleRgbMaxRed(
                sourceImage.getProcessor(), approvedMask.getDownsampleFactor());
        if (downsampledRgbPreview != null
                && (downsampledRgbPreview.getWidth() != approvedMask.getMaskWidth()
                || downsampledRgbPreview.getHeight() != approvedMask.getMaskHeight())) {
            throw new IllegalArgumentException(
                    "downsampled RGB preview dimensions must match the approved mask");
        }

        final List<ScaleBarDetectionCandidate> candidates = findLowResolutionCandidates(
                lowRgb, approvedMask);
        if (candidates.isEmpty()) {
            return ScaleBarDetection.failed(
                    "No red exterior candidate components found",
                    candidates);
        }

        ScaleBarDetectionCandidate best = null;
        for (final ScaleBarDetectionCandidate candidate : candidates) {
            if (candidate.isAccepted()
                    && (best == null || candidate.getGeometryScore() > best.getGeometryScore())) {
                best = candidate;
            }
        }
        if (best == null) {
            return ScaleBarDetection.failed(
                    "No geometrically plausible red scale-bar candidate outside the sample mask",
                    candidates);
        }

        final int factor = approvedMask.getDownsampleFactor();
        final Rectangle lowOriginalBounds = new Rectangle(
                best.getMaskMinX() * factor,
                best.getMaskMinY() * factor,
                Math.min(sourceImage.getWidth(), (best.getMaskMaxX() + 1) * factor)
                        - best.getMaskMinX() * factor,
                Math.min(sourceImage.getHeight(), (best.getMaskMaxY() + 1) * factor)
                        - best.getMaskMinY() * factor);
        final Rectangle search = expandAndClip(
                lowOriginalBounds,
                SEARCH_MARGIN_ORIGINAL_PX,
                sourceImage.getWidth(),
                sourceImage.getHeight());

        return refineAtFullResolution(
                sourceImage,
                approvedMask,
                best,
                lowOriginalBounds,
                search,
                candidates);
    }

    List<ScaleBarDetectionCandidate> findLowResolutionCandidates(
            final ColorProcessor lowRgb,
            final DownsampledSampleMask approvedMask) {
        final int width = lowRgb.getWidth();
        final int height = lowRgb.getHeight();
        final byte[] maskPixels = approvedMask.copyMaskPixels();
        final int[] dominance = new int[width * height];
        int maxDom = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = lowRgb.get(x, y);
                final int r = (rgb >> 16) & 0xff;
                final int g = (rgb >> 8) & 0xff;
                final int b = rgb & 0xff;
                final int index = y * width + x;
                // Prefer exterior mask cells; zero inside the approved sample.
                final boolean exterior = (maskPixels[index] & 0xff) == 0;
                if (!exterior) {
                    dominance[index] = 0;
                    continue;
                }
                final int redDom = r - Math.max(g, b);
                final int satProxy = r - (g + b) / 2;
                if (r < MIN_RED_CHANNEL || redDom < MIN_RED_DOMINANCE_GUARD || satProxy < 10) {
                    dominance[index] = 0;
                } else {
                    dominance[index] = Math.max(0, redDom);
                    if (dominance[index] > maxDom) {
                        maxDom = dominance[index];
                    }
                }
            }
        }
        if (maxDom <= 0) {
            return Collections.emptyList();
        }

        final int threshold = Math.max(MIN_RED_DOMINANCE_GUARD, otsuThreshold(dominance));
        final boolean[] binary = new boolean[width * height];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = dominance[i] >= threshold;
        }
        closeHorizontal(binary, width, height, 1);

        final boolean[] visited = new boolean[width * height];
        final List<ScaleBarDetectionCandidate> candidates = new ArrayList<ScaleBarDetectionCandidate>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int seed = y * width + x;
                if (!binary[seed] || visited[seed]) {
                    continue;
                }
                candidates.add(scoreComponent(
                        binary, visited, maskPixels, width, height, x, y));
            }
        }
        Collections.sort(candidates, new Comparator<ScaleBarDetectionCandidate>() {
            @Override
            public int compare(
                    final ScaleBarDetectionCandidate a,
                    final ScaleBarDetectionCandidate b) {
                return Double.compare(b.getGeometryScore(), a.getGeometryScore());
            }
        });
        return candidates;
    }

    private ScaleBarDetectionCandidate scoreComponent(
            final boolean[] binary,
            final boolean[] visited,
            final byte[] maskPixels,
            final int width,
            final int height,
            final int seedX,
            final int seedY) {
        final ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        final int seed = seedY * width + seedX;
        queue.add(Integer.valueOf(seed));
        visited[seed] = true;
        int minX = seedX;
        int maxX = seedX;
        int minY = seedY;
        int maxY = seedY;
        int count = 0;
        long sumX = 0L;
        long sumY = 0L;
        long sumXX = 0L;
        long sumYY = 0L;
        long sumXY = 0L;
        int exteriorCount = 0;
        while (!queue.isEmpty()) {
            final int index = queue.removeFirst().intValue();
            final int x = index % width;
            final int y = index / width;
            count++;
            sumX += x;
            sumY += y;
            sumXX += ((long) x) * x;
            sumYY += ((long) y) * y;
            sumXY += ((long) x) * y;
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            if ((maskPixels[y * width + x] & 0xff) == 0) {
                exteriorCount++;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    final int nx = x + dx;
                    final int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    final int nIndex = ny * width + nx;
                    if (!binary[nIndex] || visited[nIndex]) {
                        continue;
                    }
                    visited[nIndex] = true;
                    queue.add(Integer.valueOf(nIndex));
                }
            }
        }

        final int bboxW = maxX - minX + 1;
        final int bboxH = maxY - minY + 1;
        final double aspect = bboxH <= 0 ? 0.0 : ((double) bboxW) / bboxH;
        final double meanX = sumX / (double) count;
        final double meanY = sumY / (double) count;
        final double covXX = sumXX / (double) count - meanX * meanX;
        final double covYY = sumYY / (double) count - meanY * meanY;
        final double covXY = sumXY / (double) count - meanX * meanY;
        final double angleRad = 0.5 * Math.atan2(2.0 * covXY, covXX - covYY);
        final double angleDeg = Math.toDegrees(angleRad);
        final double absAngle = Math.abs(angleDeg);
        final double horizontalAngle = Math.min(absAngle, 180.0 - absAngle);
        final double exteriorFraction = count == 0 ? 0.0 : exteriorCount / (double) count;

        String reason = "accepted";
        boolean accepted = true;
        if (bboxW < 8 || count < 12) {
            accepted = false;
            reason = "too small / short stroke";
        } else if (aspect < MIN_ASPECT_RATIO) {
            accepted = false;
            reason = "aspect ratio too low (compact blob or text-like)";
        } else if (bboxH > bboxW) {
            accepted = false;
            reason = "vertical line / upright component";
        } else if (horizontalAngle > MAX_SHAFT_ANGLE_DEGREES) {
            accepted = false;
            reason = "not sufficiently horizontal";
        } else if (exteriorFraction < 0.85) {
            accepted = false;
            reason = "insufficient exterior-mask fraction";
        } else if (bboxW < Math.max(10, width / 40)) {
            accepted = false;
            reason = "horizontal extent implausible for scale bar";
        }

        // Geometry score prefers long, thin, horizontal, exterior bars — not raw red area.
        final double score = accepted
                ? (bboxW * Math.min(aspect, 40.0)
                * (1.0 - horizontalAngle / 90.0)
                * exteriorFraction
                * Math.log(1.0 + count))
                : 0.0;
        return new ScaleBarDetectionCandidate(
                minX, minY, maxX, maxY, count, score, aspect, horizontalAngle,
                exteriorFraction, accepted, reason);
    }

    private ScaleBarDetection refineAtFullResolution(
            final ImagePlus sourceImage,
            final DownsampledSampleMask approvedMask,
            final ScaleBarDetectionCandidate bestLowRes,
            final Rectangle candidateBoundsOriginal,
            final Rectangle search,
            final List<ScaleBarDetectionCandidate> candidates) {
        final ImageProcessor source = sourceImage.getProcessor();
        final int sw = search.width;
        final int sh = search.height;
        final boolean[] red = new boolean[sw * sh];
        final int[] dominance = new int[sw * sh];
        int maxDom = 0;
        for (int y = 0; y < sh; y++) {
            final int sy = search.y + y;
            for (int x = 0; x < sw; x++) {
                final int sx = search.x + x;
                final int rgb = source.get(sx, sy);
                final int r = (rgb >> 16) & 0xff;
                final int g = (rgb >> 8) & 0xff;
                final int b = rgb & 0xff;
                final int index = y * sw + x;
                final boolean exterior = !approvedMask.containsOriginalPixel(sx, sy);
                final int redDom = r - Math.max(g, b);
                final int satProxy = r - (g + b) / 2;
                if (!exterior || r < MIN_RED_CHANNEL || redDom < MIN_RED_DOMINANCE_GUARD
                        || satProxy < 8) {
                    dominance[index] = 0;
                } else {
                    dominance[index] = redDom;
                    if (redDom > maxDom) {
                        maxDom = redDom;
                    }
                }
            }
        }
        if (maxDom <= 0) {
            return ScaleBarDetection.failed(
                    "Full-resolution refinement found no exterior red pixels in search region",
                    candidates);
        }
        final int threshold = Math.max(MIN_RED_DOMINANCE_GUARD, otsuThreshold(dominance));
        for (int i = 0; i < red.length; i++) {
            red[i] = dominance[i] >= threshold;
        }
        closeHorizontal(red, sw, sh, 2);

        final ComponentStats component = largestBarLikeComponent(red, sw, sh);
        if (component == null) {
            return ScaleBarDetection.failed(
                    "Full-resolution refinement could not isolate a bar-like component",
                    candidates);
        }

        final Rectangle componentBounds = new Rectangle(
                search.x + component.minX,
                search.y + component.minY,
                component.maxX - component.minX + 1,
                component.maxY - component.minY + 1);
        final double outerWidth = component.maxX - component.minX + 1.0;

        final ShaftModel shaft = fitShaft(red, sw, sh, component);
        if (shaft == null) {
            return ScaleBarDetection.failed(
                    "Could not determine a continuous horizontal shaft",
                    candidates);
        }

        final TickCenters ticks = locateTerminalTicks(red, sw, sh, component, shaft);
        if (ticks == null) {
            // Explicit shaft-only fallback, never confused with tick measurement.
            final double length = Math.hypot(
                    shaft.rightX - shaft.leftX, shaft.rightY - shaft.leftY);
            if (!(length > 0.0) || Double.isNaN(length) || Double.isInfinite(length)) {
                return ScaleBarDetection.failed(
                        "Missing terminal ticks and shaft-endpoint fallback length invalid",
                        candidates);
            }
            final double score = bestLowRes.getGeometryScore() * 0.35;
            return ScaleBarDetection.measured(
                    ScaleBarDetectionConfidence.LOW,
                    score,
                    ScaleBarDetectionMethod.SHAFT_ENDPOINTS,
                    search.x + shaft.leftX,
                    search.y + shaft.leftY,
                    search.x + shaft.rightX,
                    search.y + shaft.rightY,
                    length,
                    outerWidth,
                    shaft.angleDegrees,
                    shaft.continuity,
                    candidateBoundsOriginal,
                    search,
                    componentBounds,
                    candidates);
        }

        final double dx = ticks.rightX - ticks.leftX;
        final double dy = ticks.rightY - ticks.leftY;
        // Distance along fitted shaft axis when slightly rotated.
        final double axisX = Math.cos(Math.toRadians(shaft.angleDegrees));
        final double axisY = Math.sin(Math.toRadians(shaft.angleDegrees));
        final double alongAxis = Math.abs(dx * axisX + dy * axisY);
        final double euclidean = Math.hypot(dx, dy);
        final double measured = Math.abs(shaft.angleDegrees) < 1.0 ? Math.abs(dx) : alongAxis;
        final double length = measured > 0.0 ? measured : euclidean;

        if (!(length > 0.0) || Double.isNaN(length) || Double.isInfinite(length)
                || ticks.rightX <= ticks.leftX) {
            return ScaleBarDetection.failed(
                    "Terminal-tick centers produced an invalid length",
                    candidates);
        }

        final ScaleBarDetectionConfidence confidence = classifyConfidence(
                shaft, ticks, length, outerWidth, bestLowRes);
        final double score = bestLowRes.getGeometryScore()
                * (0.5 + 0.5 * shaft.continuity)
                * (ticks.leftEvidence + ticks.rightEvidence) / 2.0;

        return ScaleBarDetection.measured(
                confidence,
                score,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                search.x + ticks.leftX,
                search.y + ticks.leftY,
                search.x + ticks.rightX,
                search.y + ticks.rightY,
                length,
                outerWidth,
                shaft.angleDegrees,
                shaft.continuity,
                candidateBoundsOriginal,
                search,
                componentBounds,
                candidates);
    }

    private static ScaleBarDetectionConfidence classifyConfidence(
            final ShaftModel shaft,
            final TickCenters ticks,
            final double length,
            final double outerWidth,
            final ScaleBarDetectionCandidate lowRes) {
        if (shaft.continuity < 0.55
                || ticks.leftEvidence < 0.35
                || ticks.rightEvidence < 0.35
                || Math.abs(shaft.angleDegrees) > MAX_SHAFT_ANGLE_DEGREES) {
            return ScaleBarDetectionConfidence.LOW;
        }
        // Outer width should exceed center distance when ticks have thickness.
        if (outerWidth + 0.5 < length) {
            return ScaleBarDetectionConfidence.LOW;
        }
        if (shaft.continuity >= 0.85
                && ticks.leftEvidence >= 0.7
                && ticks.rightEvidence >= 0.7
                && lowRes.getAspectRatio() >= 8.0
                && Math.abs(shaft.angleDegrees) <= 5.0) {
            return ScaleBarDetectionConfidence.HIGH;
        }
        return ScaleBarDetectionConfidence.MEDIUM;
    }

    private static ComponentStats largestBarLikeComponent(
            final boolean[] red,
            final int width,
            final int height) {
        final boolean[] visited = new boolean[red.length];
        ComponentStats best = null;
        double bestScore = -1.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int seed = y * width + x;
                if (!red[seed] || visited[seed]) {
                    continue;
                }
                final ComponentStats stats = flood(red, visited, width, height, x, y);
                final int w = stats.maxX - stats.minX + 1;
                final int h = stats.maxY - stats.minY + 1;
                if (w < 8 || h < 1) {
                    continue;
                }
                final double aspect = h <= 0 ? 0.0 : w / (double) h;
                final double score = w * Math.min(aspect, 50.0) * Math.log(1.0 + stats.count);
                if (aspect >= 3.0 && score > bestScore) {
                    bestScore = score;
                    best = stats;
                }
            }
        }
        return best;
    }

    private static ComponentStats flood(
            final boolean[] red,
            final boolean[] visited,
            final int width,
            final int height,
            final int seedX,
            final int seedY) {
        final ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        final int seed = seedY * width + seedX;
        queue.add(Integer.valueOf(seed));
        visited[seed] = true;
        int minX = seedX;
        int maxX = seedX;
        int minY = seedY;
        int maxY = seedY;
        int count = 0;
        while (!queue.isEmpty()) {
            final int index = queue.removeFirst().intValue();
            final int x = index % width;
            final int y = index / width;
            count++;
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    final int nx = x + dx;
                    final int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    final int nIndex = ny * width + nx;
                    if (!red[nIndex] || visited[nIndex]) {
                        continue;
                    }
                    visited[nIndex] = true;
                    queue.add(Integer.valueOf(nIndex));
                }
            }
        }
        return new ComponentStats(minX, minY, maxX, maxY, count);
    }

    private static ShaftModel fitShaft(
            final boolean[] red,
            final int width,
            final int height,
            final ComponentStats component) {
        final int[] rowCounts = new int[height];
        int bestRow = -1;
        int bestCount = 0;
        for (int y = component.minY; y <= component.maxY; y++) {
            int count = 0;
            for (int x = component.minX; x <= component.maxX; x++) {
                if (red[y * width + x]) {
                    count++;
                }
            }
            rowCounts[y] = count;
            if (count > bestCount) {
                bestCount = count;
                bestRow = y;
            }
        }
        if (bestRow < 0 || bestCount < 8) {
            return null;
        }
        // Expand shaft band around densest rows.
        int y0 = bestRow;
        int y1 = bestRow;
        final int rowThreshold = Math.max(3, bestCount / 3);
        while (y0 > component.minY && rowCounts[y0 - 1] >= rowThreshold) {
            y0--;
        }
        while (y1 < component.maxY && rowCounts[y1 + 1] >= rowThreshold) {
            y1++;
        }

        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int occupiedColumns = 0;
        for (int x = component.minX; x <= component.maxX; x++) {
            boolean occupied = false;
            for (int y = y0; y <= y1; y++) {
                if (red[y * width + x]) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) {
                occupiedColumns++;
                if (x < left) {
                    left = x;
                }
                if (x > right) {
                    right = x;
                }
            }
        }
        if (left == Integer.MAX_VALUE || right <= left) {
            return null;
        }
        final double continuity = occupiedColumns / (double) (right - left + 1);
        final double midY = 0.5 * (y0 + y1);
        // Small angle from first/last shaft centroids in end windows.
        final double leftY = columnMeanY(red, width, left, Math.min(right, left + 5), y0, y1);
        final double rightY = columnMeanY(red, width, Math.max(left, right - 5), right, y0, y1);
        final double angle = Math.toDegrees(Math.atan2(rightY - leftY, right - left));
        return new ShaftModel(left, midY, right, midY, angle, continuity, y0, y1);
    }

    private static TickCenters locateTerminalTicks(
            final boolean[] red,
            final int width,
            final int height,
            final ComponentStats component,
            final ShaftModel shaft) {
        final int shaftLeft = (int) Math.round(shaft.leftX);
        final int shaftRight = (int) Math.round(shaft.rightX);
        final int span = shaftRight - shaftLeft + 1;
        final int window = Math.max(4, Math.min(30, span / 12));
        final int verticalPad = Math.max(3, (shaft.y1 - shaft.y0 + 1) * 3);

        final TickSide left = measureTick(
                red, width, height, component,
                shaftLeft, Math.min(shaftRight, shaftLeft + window),
                Math.max(component.minY, shaft.y0 - verticalPad),
                Math.min(component.maxY, shaft.y1 + verticalPad),
                shaft.y0, shaft.y1, true);
        final TickSide right = measureTick(
                red, width, height, component,
                Math.max(shaftLeft, shaftRight - window), shaftRight,
                Math.max(component.minY, shaft.y0 - verticalPad),
                Math.min(component.maxY, shaft.y1 + verticalPad),
                shaft.y0, shaft.y1, false);
        if (left == null || right == null) {
            return null;
        }
        if (right.centerX <= left.centerX) {
            return null;
        }
        return new TickCenters(
                left.centerX, left.centerY, left.evidence,
                right.centerX, right.centerY, right.evidence);
    }

    private static TickSide measureTick(
            final boolean[] red,
            final int width,
            final int height,
            final ComponentStats component,
            final int x0,
            final int x1,
            final int y0,
            final int y1,
            final int shaftY0,
            final int shaftY1,
            final boolean leftSide) {
        final int[] colCounts = new int[x1 - x0 + 1];
        final int[] colVert = new int[x1 - x0 + 1];
        boolean any = false;
        for (int x = x0; x <= x1; x++) {
            int total = 0;
            int aboveOrBelow = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int y = y0; y <= y1; y++) {
                if (y < 0 || y >= height) {
                    continue;
                }
                if (red[y * width + x]) {
                    total++;
                    any = true;
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (y < shaftY0 || y > shaftY1) {
                        aboveOrBelow++;
                    }
                }
            }
            colCounts[x - x0] = total;
            colVert[x - x0] = maxY >= minY ? (maxY - minY + 1) : 0;
            if (aboveOrBelow > 0) {
                colVert[x - x0] = Math.max(colVert[x - x0], aboveOrBelow + (shaftY1 - shaftY0 + 1));
            }
        }
        if (!any) {
            return null;
        }
        // Prefer columns with greater vertical extent than the shaft alone.
        final int shaftHeight = shaftY1 - shaftY0 + 1;
        double weightSum = 0.0;
        double weightedX = 0.0;
        double weightedY = 0.0;
        double evidenceMass = 0.0;
        for (int x = x0; x <= x1; x++) {
            final int vert = colVert[x - x0];
            final int total = colCounts[x - x0];
            if (total <= 0) {
                continue;
            }
            final double tickWeight = vert > shaftHeight ? (vert - shaftHeight + 1.0) : 0.25;
            final double w = total * tickWeight;
            weightSum += w;
            weightedX += w * x;
            // Y centroid for this column.
            double ySum = 0.0;
            int yCount = 0;
            for (int y = y0; y <= y1; y++) {
                if (y >= 0 && y < height && red[y * width + x]) {
                    ySum += y;
                    yCount++;
                }
            }
            if (yCount > 0) {
                weightedY += w * (ySum / yCount);
            }
            if (vert > shaftHeight) {
                evidenceMass += 1.0;
            }
        }
        if (!(weightSum > 0.0)) {
            return null;
        }
        final double evidence = Math.min(1.0, evidenceMass / Math.max(1.0, (x1 - x0 + 1) * 0.35));
        // Require some vertical terminal structure beyond a pure shaft end.
        if (evidence < 0.2) {
            return null;
        }
        return new TickSide(weightedX / weightSum, weightedY / weightSum, evidence);
    }

    private static double columnMeanY(
            final boolean[] red,
            final int width,
            final int x0,
            final int x1,
            final int y0,
            final int y1) {
        long sum = 0L;
        int count = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (red[y * width + x]) {
                    sum += y;
                    count++;
                }
            }
        }
        return count == 0 ? 0.5 * (y0 + y1) : sum / (double) count;
    }

    /**
     * Downsamples by choosing, in each mask cell, the source pixel with the
     * strongest red dominance. This keeps thin red scale bars visible at low
     * resolution without allocating a full-size binary mask.
     */
    static ColorProcessor downsampleRgbMaxRed(
            final ImageProcessor source,
            final int factor) {
        final int width = source.getWidth();
        final int height = source.getHeight();
        final int maskWidth = DownsampledSampleMask.maskDimension(width, factor);
        final int maskHeight = DownsampledSampleMask.maskDimension(height, factor);
        final ColorProcessor downsampled = new ColorProcessor(maskWidth, maskHeight);
        for (int my = 0; my < maskHeight; my++) {
            final int y0 = my * factor;
            final int y1 = Math.min(height, y0 + factor);
            for (int mx = 0; mx < maskWidth; mx++) {
                final int x0 = mx * factor;
                final int x1 = Math.min(width, x0 + factor);
                int bestRgb = 0;
                int bestScore = Integer.MIN_VALUE;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        final int rgb = source.get(x, y);
                        final int r = (rgb >> 16) & 0xff;
                        final int g = (rgb >> 8) & 0xff;
                        final int b = rgb & 0xff;
                        final int score = (r - Math.max(g, b)) * 2 + r;
                        if (score > bestScore) {
                            bestScore = score;
                            bestRgb = rgb;
                        }
                    }
                }
                downsampled.set(mx, my, bestRgb);
            }
        }
        return downsampled;
    }

    static Rectangle expandAndClip(
            final Rectangle bounds,
            final int margin,
            final int imageWidth,
            final int imageHeight) {
        final int x0 = Math.max(0, bounds.x - margin);
        final int y0 = Math.max(0, bounds.y - margin);
        final int x1 = Math.min(imageWidth, bounds.x + bounds.width + margin);
        final int y1 = Math.min(imageHeight, bounds.y + bounds.height + margin);
        return new Rectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    static int otsuThreshold(final int[] values) {
        final int[] hist = new int[256];
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            if (v > 0) {
                hist[Math.min(255, v)]++;
                count++;
            }
        }
        if (count == 0) {
            return MIN_RED_DOMINANCE_GUARD;
        }
        long sum = 0L;
        for (int i = 0; i < 256; i++) {
            sum += (long) i * hist[i];
        }
        long sumB = 0L;
        int wB = 0;
        double maxVar = -1.0;
        int threshold = MIN_RED_DOMINANCE_GUARD;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) {
                continue;
            }
            final int wF = count - wB;
            if (wF == 0) {
                break;
            }
            sumB += (long) t * hist[t];
            final double mB = sumB / (double) wB;
            final double mF = (sum - sumB) / (double) wF;
            final double between = ((double) wB) * wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }
        return Math.max(MIN_RED_DOMINANCE_GUARD, threshold);
    }

    private static void closeHorizontal(
            final boolean[] binary,
            final int width,
            final int height,
            final int radius) {
        if (radius <= 0) {
            return;
        }
        final boolean[] dilated = Arrays.copyOf(binary, binary.length);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!binary[y * width + x]) {
                    continue;
                }
                for (int dx = -radius; dx <= radius; dx++) {
                    final int nx = x + dx;
                    if (nx >= 0 && nx < width) {
                        dilated[y * width + nx] = true;
                    }
                }
            }
        }
        final boolean[] eroded = new boolean[binary.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean all = true;
                for (int dx = -radius; dx <= radius; dx++) {
                    final int nx = x + dx;
                    if (nx < 0 || nx >= width || !dilated[y * width + nx]) {
                        all = false;
                        break;
                    }
                }
                eroded[y * width + x] = all;
            }
        }
        System.arraycopy(eroded, 0, binary, 0, binary.length);
    }

    private static final class ComponentStats {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;
        final int count;

        ComponentStats(
                final int minX,
                final int minY,
                final int maxX,
                final int maxY,
                final int count) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.count = count;
        }
    }

    private static final class ShaftModel {
        final double leftX;
        final double leftY;
        final double rightX;
        final double rightY;
        final double angleDegrees;
        final double continuity;
        final int y0;
        final int y1;

        ShaftModel(
                final double leftX,
                final double leftY,
                final double rightX,
                final double rightY,
                final double angleDegrees,
                final double continuity,
                final int y0,
                final int y1) {
            this.leftX = leftX;
            this.leftY = leftY;
            this.rightX = rightX;
            this.rightY = rightY;
            this.angleDegrees = angleDegrees;
            this.continuity = continuity;
            this.y0 = y0;
            this.y1 = y1;
        }
    }

    private static final class TickSide {
        final double centerX;
        final double centerY;
        final double evidence;

        TickSide(final double centerX, final double centerY, final double evidence) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.evidence = evidence;
        }
    }

    private static final class TickCenters {
        final double leftX;
        final double leftY;
        final double leftEvidence;
        final double rightX;
        final double rightY;
        final double rightEvidence;

        TickCenters(
                final double leftX,
                final double leftY,
                final double leftEvidence,
                final double rightX,
                final double rightY,
                final double rightEvidence) {
            this.leftX = leftX;
            this.leftY = leftY;
            this.leftEvidence = leftEvidence;
            this.rightX = rightX;
            this.rightY = rightY;
            this.rightEvidence = rightEvidence;
        }
    }

    static String describeCandidates(final List<ScaleBarDetectionCandidate> candidates) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(String.format(Locale.US, "  candidate[%d]: %s",
                    Integer.valueOf(i), candidates.get(i)));
        }
        return sb.toString();
    }
}
