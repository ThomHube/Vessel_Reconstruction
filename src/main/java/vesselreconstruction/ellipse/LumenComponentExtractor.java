package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
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
        final long pixelCount = ((long) width) * ((long) height);
        if (pixelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "classifiedProcessor pixel count exceeds Integer.MAX_VALUE: "
                            + width + "x" + height);
        }
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }

        final int checksumBefore = computeChecksum(pixels);
        final int totalPixels = (int) pixelCount;
        final BitSet visited = new BitSet(totalPixels);
        final List<LumenComponentDescriptor> components =
                new ArrayList<LumenComponentDescriptor>();
        int nextId = 1;

        final IntRingQueue bfsQueue = new IntRingQueue();
        final IntAccumulator members = new IntAccumulator();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int seedIndex = y * width + x;
                if (visited.get(seedIndex)) {
                    continue;
                }
                if ((pixels[seedIndex] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }

                members.clear();
                bfsQueue.clear();
                bfsQueue.addLast(seedIndex);
                visited.set(seedIndex);

                while (!bfsQueue.isEmpty()) {
                    final int index = bfsQueue.removeFirst();
                    final int cx = index % width;
                    final int cy = index / width;
                    members.add(index);

                    for (int i = 0; i < DX.length; i++) {
                        final int nx = cx + DX[i];
                        final int ny = cy + DY[i];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                            continue;
                        }
                        final int nIndex = ny * width + nx;
                        if (visited.get(nIndex)) {
                            continue;
                        }
                        if ((pixels[nIndex] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                            continue;
                        }
                        visited.set(nIndex);
                        bfsQueue.addLast(nIndex);
                    }
                }

                final int[] packed = members.toArray();
                Arrays.sort(packed);
                components.add(LumenComponentDescriptor.fromPackedIndices(
                        nextId++,
                        packed,
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

    private static final class IntAccumulator {
        private static final int INITIAL_CAPACITY = 16;

        private int[] values = new int[INITIAL_CAPACITY];
        private int size;

        void clear() {
            size = 0;
        }

        void add(final int value) {
            if (size == values.length) {
                int grown = values.length + (values.length >> 1);
                if (grown <= values.length) {
                    grown = values.length + 1;
                }
                values = Arrays.copyOf(values, grown);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    /**
     * Primitive circular FIFO used as the BFS frontier. Grows geometrically
     * with the live backlog; never preallocates the full image or component.
     */
    static final class IntRingQueue {
        private static final int INITIAL_CAPACITY = 256;

        private int[] values;
        private int head;
        private int size;

        IntRingQueue() {
            this(INITIAL_CAPACITY);
        }

        IntRingQueue(final int initialCapacity) {
            if (initialCapacity < 1) {
                throw new IllegalArgumentException(
                        "initialCapacity must be positive: " + initialCapacity);
            }
            values = new int[initialCapacity];
        }

        void clear() {
            head = 0;
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int capacity() {
            return values.length;
        }

        void addLast(final int value) {
            if (size == values.length) {
                grow();
            }
            final int index = head + size;
            values[index < values.length ? index : index - values.length] = value;
            size++;
        }

        int removeFirst() {
            final int value = values[head];
            head++;
            if (head == values.length) {
                head = 0;
            }
            size--;
            return value;
        }

        private void grow() {
            int grown = values.length << 1;
            if (grown <= values.length) {
                grown = values.length + 1;
            }
            final int[] next = new int[grown];
            final int first = values.length - head;
            if (first >= size) {
                System.arraycopy(values, head, next, 0, size);
            } else {
                System.arraycopy(values, head, next, 0, first);
                System.arraycopy(values, 0, next, first, size - first);
            }
            values = next;
            head = 0;
        }
    }
}
