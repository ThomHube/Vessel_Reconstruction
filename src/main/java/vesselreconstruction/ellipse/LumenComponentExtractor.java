package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic extractor of every raw Class 3 Lumen component
 * ({@link ClassifierLabels#LUMEN_RESULT_VALUE}) using 8-connectivity.
 *
 * <p>Does not mutate the source processor, show windows, or filter by area.</p>
 */
public final class LumenComponentExtractor {

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

    public List<LumenComponentDescriptor> extract(final ByteProcessor classifiedProcessor) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "classifiedProcessor dimensions must be positive: " + width + "x" + height);
        }
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }

        final int checksumBefore = computeChecksum(pixels);
        final boolean[] visited = new boolean[width * height];
        final List<LumenComponentDescriptor> components =
                new ArrayList<LumenComponentDescriptor>();
        int nextId = 1;

        // Integer-array BFS queue: packed x/y indices.
        final int[] queue = new int[width * height];
        final List<Point> members = new ArrayList<Point>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int seedIndex = y * width + x;
                if (visited[seedIndex]) {
                    continue;
                }
                if ((pixels[seedIndex] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }

                members.clear();
                int head = 0;
                int tail = 0;
                queue[tail++] = seedIndex;
                visited[seedIndex] = true;

                while (head < tail) {
                    final int index = queue[head++];
                    final int cx = index % width;
                    final int cy = index / width;
                    members.add(new Point(cx, cy));

                    for (int i = 0; i < DX.length; i++) {
                        final int nx = cx + DX[i];
                        final int ny = cy + DY[i];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                            continue;
                        }
                        final int nIndex = ny * width + nx;
                        if (visited[nIndex]) {
                            continue;
                        }
                        if ((pixels[nIndex] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                            continue;
                        }
                        visited[nIndex] = true;
                        queue[tail++] = nIndex;
                    }
                }

                components.add(new LumenComponentDescriptor(
                        nextId++,
                        members,
                        width,
                        height));
            }
        }

        final int checksumAfter = computeChecksum(pixels);
        if (checksumBefore != checksumAfter) {
            throw new IllegalStateException(
                    "classifiedProcessor was mutated during lumen extraction");
        }

        return Collections.unmodifiableList(
                new ArrayList<LumenComponentDescriptor>(components));
    }

    static int computeChecksum(final byte[] pixels) {
        int result = 1;
        for (int i = 0; i < pixels.length; i++) {
            result = 31 * result + (pixels[i] & 0xff);
        }
        return result;
    }
}
