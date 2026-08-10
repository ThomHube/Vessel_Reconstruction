package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.AutoThresholder;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates a low-resolution automatic sample-analysis mask from an RGB source.
 *
 * <p>Stages:</p>
 * <ol>
 *   <li>area-average downsample of the complete field of view</li>
 *   <li>robust pale-background RGB estimation from left/right side borders</li>
 *   <li>colour-distance image and Otsu threshold</li>
 *   <li>lightweight morphology</li>
 *   <li>8-connected principal-component selection</li>
 *   <li>internal hole filling</li>
 *   <li>expansion by the configured original-pixel margin</li>
 * </ol>
 *
 * <p>Component-selection rule (deterministic): among 8-connected foreground
 * components, choose the component maximizing
 * {@code score = area * heightSpan * (heightSpan + 1)}. This favours the long
 * continuous wood sample over short wide annotation/scale-bar bands. A component
 * is credible only when
 * {@code heightSpan >= 0.15 * maskHeight} or
 * {@code area >= 0.005 * maskPixels}. If no credible component exists, generation
 * fails rather than returning an all-white mask.</p>
 */
public final class AutomaticSampleMaskGenerator {

    private static final double MIN_HEIGHT_SPAN_FRACTION = 0.15;
    private static final double MIN_AREA_FRACTION = 0.005;
    private static final int MIN_COMPONENT_AREA_PIXELS = 16;
    private static final int BORDER_FRACTION_DENOMINATOR = 20;
    private static final int VERTICAL_EXCLUDE_FRACTION_DENOMINATOR = 10;

    public SampleMaskGenerationResult generate(
            final ImagePlus sourceImage,
            final int downsampleFactor,
            final int expansionMarginOriginalPixels) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        if (downsampleFactor <= 0) {
            throw new IllegalArgumentException(
                    "downsampleFactor must be > 0: " + downsampleFactor);
        }
        if (expansionMarginOriginalPixels < 0) {
            throw new IllegalArgumentException(
                    "expansionMarginOriginalPixels must be >= 0: "
                            + expansionMarginOriginalPixels);
        }
        if (sourceImage.getType() != ImagePlus.COLOR_RGB
                && !(sourceImage.getProcessor() instanceof ColorProcessor)) {
            throw new IllegalArgumentException(
                    "Automatic sample-mask generation requires an RGB source image");
        }

        final String sourceTitle = sourceImage.getTitle() == null
                ? "Untitled" : sourceImage.getTitle();
        final int originalWidth = sourceImage.getWidth();
        final int originalHeight = sourceImage.getHeight();
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("source dimensions must be positive");
        }

        try {
            final ColorProcessor downsampled = downsampleRgbAreaAverage(
                    sourceImage.getProcessor(), downsampleFactor);
            final int maskWidth = downsampled.getWidth();
            final int maskHeight = downsampled.getHeight();

            final int[] backgroundRgb = estimateBackgroundRgb(downsampled);
            final ByteProcessor distance = colourDistanceImage(downsampled, backgroundRgb);
            final int threshold = otsuThreshold(distance);
            ByteProcessor binary = thresholdDistance(distance, threshold);

            binary = openBinary(binary, 1);
            binary = closeBinary(binary, 2);

            final ComponentSelection selection = selectPrincipalComponent(binary);
            if (selection == null) {
                throw new SampleMaskGenerationException(
                        "Automatic sample-mask generation found no credible sample component. "
                                + "Review the source image or adjust the mask manually.",
                        sourceTitle,
                        maskWidth,
                        maskHeight,
                        "component-selection",
                        null);
            }

            binary = keepOnlyComponent(binary, selection);
            fillInternalHoles(binary);

            // Convert original-pixel expansion to mask pixels with ceiling division
            // so the requested physical margin is never underestimated.
            final int radius = expansionMarginOriginalPixels == 0
                    ? 0
                    : CoreTileLayout.ceilDiv(expansionMarginOriginalPixels, downsampleFactor);
            if (radius > 0) {
                binary = dilateBinary(binary, radius);
            }

            final DownsampledSampleMask mask = new DownsampledSampleMask(
                    originalWidth,
                    originalHeight,
                    downsampleFactor,
                    (byte[]) binary.getPixels(),
                    maskWidth,
                    maskHeight);
            if (mask.isEmpty()) {
                throw new SampleMaskGenerationException(
                        "Automatic sample-mask generation produced an empty mask.",
                        sourceTitle,
                        maskWidth,
                        maskHeight,
                        "post-expansion",
                        null);
            }

            final SampleMaskStatistics statistics = new SampleMaskStatistics(
                    backgroundRgb[0],
                    backgroundRgb[1],
                    backgroundRgb[2],
                    threshold,
                    selection.candidateCount,
                    selection.area,
                    selection.bounds,
                    selection.bounds.width,
                    selection.bounds.height,
                    selection.secondLargestCredibleArea,
                    mask.getIncludedMaskPixels(),
                    mask.getIncludedMaskFraction(),
                    mask.getChecksum(),
                    radius);

            final ImagePlus previewRgb = new ImagePlus(
                    "Sample Mask RGB Preview - " + sourceTitle, downsampled);
            final ImagePlus maskImage = toMaskImage(mask, sourceTitle);
            return new SampleMaskGenerationResult(mask, statistics, previewRgb, maskImage);
        } catch (final SampleMaskGenerationException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            final int mw = DownsampledSampleMask.maskDimension(originalWidth, downsampleFactor);
            final int mh = DownsampledSampleMask.maskDimension(originalHeight, downsampleFactor);
            throw new SampleMaskGenerationException(
                    "Automatic sample-mask generation failed: "
                            + describe(ex),
                    sourceTitle,
                    mw,
                    mh,
                    "generation",
                    ex);
        }
    }

    static ColorProcessor downsampleRgbAreaAverage(
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
                long sumR = 0L;
                long sumG = 0L;
                long sumB = 0L;
                long count = 0L;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        final int rgb = source.get(x, y);
                        sumR += (rgb >> 16) & 0xff;
                        sumG += (rgb >> 8) & 0xff;
                        sumB += rgb & 0xff;
                        count++;
                    }
                }
                if (count == 0L) {
                    downsampled.set(mx, my, 0);
                } else {
                    final int r = (int) ((sumR + count / 2L) / count);
                    final int g = (int) ((sumG + count / 2L) / count);
                    final int b = (int) ((sumB + count / 2L) / count);
                    downsampled.set(mx, my, (r << 16) | (g << 8) | b);
                }
            }
        }
        return downsampled;
    }

    static int[] estimateBackgroundRgb(final ColorProcessor image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int borderWidth = Math.max(1, width / BORDER_FRACTION_DENOMINATOR);
        final int excludeTop = Math.max(0, height / VERTICAL_EXCLUDE_FRACTION_DENOMINATOR);
        final int excludeBottom = Math.max(0, height / VERTICAL_EXCLUDE_FRACTION_DENOMINATOR);
        final int y0 = Math.min(height - 1, excludeTop);
        final int y1 = Math.max(y0 + 1, height - excludeBottom);

        final List<Integer> reds = new ArrayList<Integer>();
        final List<Integer> greens = new ArrayList<Integer>();
        final List<Integer> blues = new ArrayList<Integer>();
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < borderWidth; x++) {
                addRgb(image.get(x, y), reds, greens, blues);
            }
            for (int x = Math.max(borderWidth, width - borderWidth); x < width; x++) {
                addRgb(image.get(x, y), reds, greens, blues);
            }
        }
        if (reds.isEmpty()) {
            // Fallback: whole left/right columns.
            for (int y = 0; y < height; y++) {
                addRgb(image.get(0, y), reds, greens, blues);
                addRgb(image.get(width - 1, y), reds, greens, blues);
            }
        }
        return new int[] {
                median(reds),
                median(greens),
                median(blues)
        };
    }

    static ByteProcessor colourDistanceImage(
            final ColorProcessor image,
            final int[] backgroundRgb) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final ByteProcessor distance = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) distance.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = image.get(x, y);
                final int dr = ((rgb >> 16) & 0xff) - backgroundRgb[0];
                final int dg = ((rgb >> 8) & 0xff) - backgroundRgb[1];
                final int db = (rgb & 0xff) - backgroundRgb[2];
                final int dist = (int) Math.round(Math.sqrt(
                        (double) dr * dr + (double) dg * dg + (double) db * db));
                pixels[y * width + x] = (byte) Math.min(255, dist);
            }
        }
        return distance;
    }

    static int otsuThreshold(final ByteProcessor distance) {
        final int[] histogram = new int[256];
        final byte[] pixels = (byte[]) distance.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            histogram[pixels[i] & 0xff]++;
        }
        final AutoThresholder thresholder = new AutoThresholder();
        return thresholder.getThreshold(AutoThresholder.Method.Otsu, histogram);
    }

    static ByteProcessor thresholdDistance(
            final ByteProcessor distance,
            final int threshold) {
        final ByteProcessor binary = new ByteProcessor(
                distance.getWidth(), distance.getHeight());
        final byte[] src = (byte[]) distance.getPixels();
        final byte[] dst = (byte[]) binary.getPixels();
        for (int i = 0; i < src.length; i++) {
            dst[i] = (byte) ((src[i] & 0xff) > threshold ? 255 : 0);
        }
        return binary;
    }

    static ByteProcessor openBinary(final ByteProcessor binary, final int iterations) {
        ByteProcessor result = (ByteProcessor) binary.duplicate();
        for (int i = 0; i < iterations; i++) {
            result = erodeBinary(result, 1);
            result = dilateBinary(result, 1);
        }
        return result;
    }

    static ByteProcessor closeBinary(final ByteProcessor binary, final int iterations) {
        ByteProcessor result = (ByteProcessor) binary.duplicate();
        for (int i = 0; i < iterations; i++) {
            result = dilateBinary(result, 1);
            result = erodeBinary(result, 1);
        }
        return result;
    }

    static ByteProcessor dilateBinary(final ByteProcessor binary, final int iterations) {
        ByteProcessor result = (ByteProcessor) binary.duplicate();
        result.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        for (int i = 0; i < iterations; i++) {
            result.dilate();
        }
        return normalizeBinary(result);
    }

    static ByteProcessor erodeBinary(final ByteProcessor binary, final int iterations) {
        ByteProcessor result = (ByteProcessor) binary.duplicate();
        result.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        for (int i = 0; i < iterations; i++) {
            result.erode();
        }
        return normalizeBinary(result);
    }

    static void fillInternalHoles(final ByteProcessor binary) {
        final int width = binary.getWidth();
        final int height = binary.getHeight();
        final ByteProcessor flooded = new ByteProcessor(width, height);
        final byte[] src = (byte[]) binary.getPixels();
        final byte[] work = (byte[]) flooded.getPixels();
        for (int i = 0; i < src.length; i++) {
            // Invert: background becomes foreground for flood fill from borders.
            work[i] = (byte) ((src[i] & 0xff) == 0 ? 255 : 0);
        }
        flooded.setColor(Color.black);
        final FloodFiller filler = new FloodFiller(flooded);
        for (int x = 0; x < width; x++) {
            if ((work[x] & 0xff) == 255) {
                filler.fill8(x, 0);
            }
            if ((work[(height - 1) * width + x] & 0xff) == 255) {
                filler.fill8(x, height - 1);
            }
        }
        for (int y = 0; y < height; y++) {
            if ((work[y * width] & 0xff) == 255) {
                filler.fill8(0, y);
            }
            if ((work[y * width + width - 1] & 0xff) == 255) {
                filler.fill8(width - 1, y);
            }
        }
        // Remaining white pixels in work are internal holes in the original mask.
        for (int i = 0; i < src.length; i++) {
            if ((work[i] & 0xff) == 255) {
                src[i] = (byte) 255;
            }
        }
    }

    static ComponentSelection selectPrincipalComponent(final ByteProcessor binary) {
        final int width = binary.getWidth();
        final int height = binary.getHeight();
        final byte[] pixels = (byte[]) binary.getPixels();
        final int total = width * height;
        final boolean[] visited = new boolean[total];
        ComponentSelection best = null;
        long secondLargestCredibleArea = 0L;
        int candidateCount = 0;

        final int[] queue = new int[total];
        for (int start = 0; start < total; start++) {
            if (visited[start] || (pixels[start] & 0xff) == 0) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            long area = 0L;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            while (head < tail) {
                final int index = queue[head++];
                final int x = index % width;
                final int y = index / width;
                area++;
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
                        if (visited[nIndex] || (pixels[nIndex] & 0xff) == 0) {
                            continue;
                        }
                        visited[nIndex] = true;
                        queue[tail++] = nIndex;
                    }
                }
            }

            if (area < MIN_COMPONENT_AREA_PIXELS) {
                continue;
            }
            candidateCount++;
            final int bboxWidth = maxX - minX + 1;
            final int bboxHeight = maxY - minY + 1;
            final boolean credible =
                    ((double) bboxHeight) / ((double) height) >= MIN_HEIGHT_SPAN_FRACTION
                            || ((double) area) / ((double) total) >= MIN_AREA_FRACTION;
            if (!credible) {
                continue;
            }
            final long score = area * ((long) bboxHeight) * ((long) bboxHeight + 1L);
            final ComponentSelection current = new ComponentSelection(
                    area,
                    new Rectangle(minX, minY, bboxWidth, bboxHeight),
                    score,
                    start,
                    0,
                    0L);
            if (best == null
                    || current.score > best.score
                    || (current.score == best.score && current.area > best.area)
                    || (current.score == best.score
                    && current.area == best.area
                    && current.bounds.y < best.bounds.y)
                    || (current.score == best.score
                    && current.area == best.area
                    && current.bounds.y == best.bounds.y
                    && current.bounds.x < best.bounds.x)) {
                if (best != null && best.area > secondLargestCredibleArea) {
                    secondLargestCredibleArea = best.area;
                }
                best = current;
            } else if (current.area > secondLargestCredibleArea) {
                secondLargestCredibleArea = current.area;
            }
        }
        if (best == null) {
            return null;
        }
        return new ComponentSelection(
                best.area,
                best.bounds,
                best.score,
                best.seedIndex,
                candidateCount,
                secondLargestCredibleArea);
    }

    static ByteProcessor keepOnlyComponent(
            final ByteProcessor binary,
            final ComponentSelection selection) {
        final int width = binary.getWidth();
        final int height = binary.getHeight();
        final byte[] src = (byte[]) binary.getPixels();
        final ByteProcessor kept = new ByteProcessor(width, height);
        final byte[] dst = (byte[]) kept.getPixels();
        final boolean[] visited = new boolean[width * height];
        final int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        queue[tail++] = selection.seedIndex;
        visited[selection.seedIndex] = true;
        while (head < tail) {
            final int index = queue[head++];
            dst[index] = (byte) 255;
            final int x = index % width;
            final int y = index / width;
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
                    if (visited[nIndex] || (src[nIndex] & 0xff) == 0) {
                        continue;
                    }
                    visited[nIndex] = true;
                    queue[tail++] = nIndex;
                }
            }
        }
        return kept;
    }

    static ImagePlus toMaskImage(final DownsampledSampleMask mask, final String sourceTitle) {
        final ByteProcessor processor = new ByteProcessor(
                mask.getMaskWidth(), mask.getMaskHeight());
        System.arraycopy(
                mask.copyMaskPixels(),
                0,
                processor.getPixels(),
                0,
                mask.getMaskWidth() * mask.getMaskHeight());
        return new ImagePlus("Approved Sample Mask - " + sourceTitle, processor);
    }

    private static ByteProcessor normalizeBinary(final ByteProcessor processor) {
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) ((pixels[i] & 0xff) == 0 ? 0 : 255);
        }
        return processor;
    }

    private static void addRgb(
            final int rgb,
            final List<Integer> reds,
            final List<Integer> greens,
            final List<Integer> blues) {
        reds.add(Integer.valueOf((rgb >> 16) & 0xff));
        greens.add(Integer.valueOf((rgb >> 8) & 0xff));
        blues.add(Integer.valueOf(rgb & 0xff));
    }

    private static int median(final List<Integer> values) {
        final int[] array = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i).intValue();
        }
        Arrays.sort(array);
        return array[array.length / 2];
    }

    private static String describe(final Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        final String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }

    static final class ComponentSelection {
        final long area;
        final Rectangle bounds;
        final long score;
        final int seedIndex;
        final int candidateCount;
        final long secondLargestCredibleArea;

        ComponentSelection(
                final long area,
                final Rectangle bounds,
                final long score,
                final int seedIndex,
                final int candidateCount,
                final long secondLargestCredibleArea) {
            this.area = area;
            this.bounds = bounds;
            this.score = score;
            this.seedIndex = seedIndex;
            this.candidateCount = candidateCount;
            this.secondLargestCredibleArea = secondLargestCredibleArea;
        }
    }
}
