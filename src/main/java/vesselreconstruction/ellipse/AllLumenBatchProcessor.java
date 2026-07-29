package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic headless all-lumen diagnostic batch processor.
 *
 * <p>Analyzes every Lumen component against the unchanged original classified
 * image, resolves candidate conflicts, and optionally builds one combined
 * diagnostic copy and one combined change mask.</p>
 */
public final class AllLumenBatchProcessor {

    private final LumenComponentExtractor componentExtractor;
    private final SingleLumenDiagnosticPipeline singleLumenPipeline;

    public AllLumenBatchProcessor() {
        this(new LumenComponentExtractor(), new DefaultSingleLumenDiagnosticPipeline());
    }

    AllLumenBatchProcessor(
            final LumenComponentExtractor componentExtractor,
            final SingleLumenDiagnosticPipeline singleLumenPipeline) {
        if (componentExtractor == null) {
            throw new IllegalArgumentException("componentExtractor must not be null");
        }
        if (singleLumenPipeline == null) {
            throw new IllegalArgumentException("singleLumenPipeline must not be null");
        }
        this.componentExtractor = componentExtractor;
        this.singleLumenPipeline = singleLumenPipeline;
    }

    public AllLumenBatchResult process(
            final ByteProcessor classifiedProcessor,
            final AllLumenBatchConfiguration configuration) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }

        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }

        final long batchStart = System.nanoTime();
        final int checksumBefore = computeChecksum(pixels);

        final List<LumenComponentDescriptor> components =
                componentExtractor.extract(classifiedProcessor);
        final List<LumenBatchItemResult> provisional =
                new ArrayList<LumenBatchItemResult>(components.size());

        // PHASE 1: analyze every component against the original image.
        for (int i = 0; i < components.size(); i++) {
            final LumenComponentDescriptor component = components.get(i);
            final long itemStart = System.nanoTime();

            if (component.getArea() < configuration.getMinimumLumenArea()) {
                provisional.add(LumenBatchItemResult.skipped(
                        component,
                        LumenBatchItemResult.Status.BELOW_MINIMUM_AREA,
                        "Component area below minimumLumenArea="
                                + configuration.getMinimumLumenArea(),
                        System.nanoTime() - itemStart));
                continue;
            }
            if (configuration.isSkipBorderTouchingComponents()
                    && component.isTouchesImageBorder()) {
                provisional.add(LumenBatchItemResult.skipped(
                        component,
                        LumenBatchItemResult.Status.BORDER_TOUCHING_SKIPPED,
                        "Component touches the image border",
                        System.nanoTime() - itemStart));
                continue;
            }

            provisional.add(singleLumenPipeline.process(classifiedProcessor, component));
        }

        // PHASE 2: resolve candidate conflicts and build combined changed pixels.
        final ConflictResolution resolved = resolveConflicts(provisional);
        final int checksumAfter = computeChecksum(pixels);
        if (checksumBefore != checksumAfter) {
            throw new IllegalStateException(
                    "classifiedProcessor was mutated during all-lumen batch processing");
        }

        return new AllLumenBatchResult(
                width,
                height,
                checksumBefore,
                checksumAfter,
                resolved.finalItems,
                resolved.combinedChangedPixels,
                resolved.conflictingPixelCount,
                System.nanoTime() - batchStart);
    }

    public ByteProcessor createCombinedDiagnosticCopy(
            final ByteProcessor classifiedProcessor,
            final AllLumenBatchResult result) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width != result.getImageWidth() || height != result.getImageHeight()) {
            throw new IllegalArgumentException(
                    "processor dimensions must match batch result: processor="
                            + width + "x" + height + ", result="
                            + result.getImageWidth() + "x" + result.getImageHeight());
        }

        final byte[] sourcePixels = (byte[]) classifiedProcessor.getPixels();
        if (sourcePixels == null || sourcePixels.length != width * height) {
            throw new IllegalArgumentException("classifiedProcessor pixels are invalid");
        }
        final int checksumBefore = computeChecksum(sourcePixels);
        if (checksumBefore != result.getSourceChecksumBefore()) {
            throw new IllegalArgumentException(
                    "source checksum does not match batch result checksum");
        }

        final ByteProcessor copy = new ByteProcessor(width, height);
        final ColorModel colorModel = classifiedProcessor.getColorModel();
        if (colorModel != null) {
            copy.setColorModel(colorModel);
        }
        final byte[] outPixels = (byte[]) copy.getPixels();
        System.arraycopy(sourcePixels, 0, outPixels, 0, sourcePixels.length);

        final List<Point> changed = result.getCombinedChangedPixels();
        for (int i = 0; i < changed.size(); i++) {
            final Point p = changed.get(i);
            final int index = p.y * width + p.x;
            final int value = sourcePixels[index] & 0xff;
            if (value != ClassifierLabels.WOOD_RESULT_VALUE) {
                throw new IllegalStateException(
                        "combined candidate source value must be Wood (0) at ("
                                + p.x + "," + p.y + "), was " + value);
            }
            outPixels[index] = (byte) ClassifierLabels.LUMEN_RESULT_VALUE;
        }

        int changedCount = 0;
        for (int i = 0; i < outPixels.length; i++) {
            if (outPixels[i] != sourcePixels[i]) {
                changedCount++;
                if ((outPixels[i] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    throw new IllegalStateException(
                            "changed output pixel is not Lumen value 2 at index " + i);
                }
            }
        }
        if (changedCount != result.getCombinedChangedPixelCount()) {
            throw new IllegalStateException(
                    "changed pixel count mismatch: expected "
                            + result.getCombinedChangedPixelCount()
                            + ", actual " + changedCount);
        }

        final int checksumAfter = computeChecksum(sourcePixels);
        if (checksumAfter != checksumBefore) {
            throw new IllegalStateException("source processor was mutated during copy creation");
        }
        return copy;
    }

    public ByteProcessor createCombinedChangeMask(final AllLumenBatchResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        final int width = result.getImageWidth();
        final int height = result.getImageHeight();
        final ByteProcessor mask = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) mask.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0;
        }
        final List<Point> changed = result.getCombinedChangedPixels();
        for (int i = 0; i < changed.size(); i++) {
            final Point p = changed.get(i);
            pixels[p.y * width + p.x] = (byte) 255;
        }
        int foreground = 0;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0xff) == 255) {
                foreground++;
            }
        }
        if (foreground != result.getCombinedChangedPixelCount()) {
            throw new IllegalStateException(
                    "change-mask foreground count mismatch: expected "
                            + result.getCombinedChangedPixelCount()
                            + ", actual " + foreground);
        }
        return mask;
    }

    private static ConflictResolution resolveConflicts(
            final List<LumenBatchItemResult> provisional) {
        // Map packed coordinate -> list of provisional reconstructed item indices.
        final Map<Long, List<Integer>> owners = new HashMap<Long, List<Integer>>();
        for (int i = 0; i < provisional.size(); i++) {
            final LumenBatchItemResult item = provisional.get(i);
            if (item.getStatus() != LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT) {
                continue;
            }
            final List<Point> candidates = item.getCandidatePixels();
            for (int j = 0; j < candidates.size(); j++) {
                final Point p = candidates.get(j);
                final Long key = Long.valueOf(pack(p.x, p.y));
                List<Integer> list = owners.get(key);
                if (list == null) {
                    list = new ArrayList<Integer>(2);
                    owners.put(key, list);
                }
                list.add(Integer.valueOf(i));
            }
        }

        final Set<Integer> conflictedItemIndices = new HashSet<Integer>();
        final Set<Long> conflictingCoords = new HashSet<Long>();
        final Iterator<Map.Entry<Long, List<Integer>>> it = owners.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Long, List<Integer>> entry = it.next();
            final List<Integer> list = entry.getValue();
            if (list.size() > 1) {
                conflictingCoords.add(entry.getKey());
                for (int k = 0; k < list.size(); k++) {
                    conflictedItemIndices.add(list.get(k));
                }
            }
        }

        final List<LumenBatchItemResult> finalItems =
                new ArrayList<LumenBatchItemResult>(provisional.size());
        final List<Point> combined = new ArrayList<Point>();
        final Set<Long> combinedKeys = new HashSet<Long>();

        for (int i = 0; i < provisional.size(); i++) {
            final LumenBatchItemResult item = provisional.get(i);
            if (conflictedItemIndices.contains(Integer.valueOf(i))) {
                final int conflictCountForItem = countConflictsForItem(item, conflictingCoords);
                finalItems.add(LumenBatchItemResult.withStatus(
                        item,
                        LumenBatchItemResult.Status.CANDIDATE_CONFLICT,
                        "Candidate conflict: " + conflictCountForItem
                                + " conflicting coordinates",
                        Collections.<Point>emptyList()));
                continue;
            }
            finalItems.add(item);
            if (item.getStatus() == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT) {
                final List<Point> candidates = item.getCandidatePixels();
                for (int j = 0; j < candidates.size(); j++) {
                    final Point p = candidates.get(j);
                    final Long key = Long.valueOf(pack(p.x, p.y));
                    if (combinedKeys.add(key)) {
                        combined.add(new Point(p.x, p.y));
                    }
                }
            }
        }

        Collections.sort(combined, POINT_ORDER);
        final ConflictResolution resolution = new ConflictResolution();
        resolution.finalItems = finalItems;
        resolution.combinedChangedPixels = combined;
        resolution.conflictingPixelCount = conflictingCoords.size();
        return resolution;
    }

    private static int countConflictsForItem(
            final LumenBatchItemResult item,
            final Set<Long> conflictingCoords) {
        int count = 0;
        final List<Point> candidates = item.getCandidatePixels();
        for (int i = 0; i < candidates.size(); i++) {
            final Point p = candidates.get(i);
            if (conflictingCoords.contains(Long.valueOf(pack(p.x, p.y)))) {
                count++;
            }
        }
        return count;
    }

    static int computeChecksum(final byte[] pixels) {
        int result = 1;
        for (int i = 0; i < pixels.length; i++) {
            result = 31 * result + (pixels[i] & 0xff);
        }
        return result;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
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

    private static final class ConflictResolution {
        private List<LumenBatchItemResult> finalItems;
        private List<Point> combinedChangedPixels;
        private int conflictingPixelCount;
    }
}
