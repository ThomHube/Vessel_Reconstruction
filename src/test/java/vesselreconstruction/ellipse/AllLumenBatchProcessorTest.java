package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllLumenBatchProcessorTest {

    @Test
    void nullProcessorIsRejected() {
        final AllLumenBatchProcessor processor = new AllLumenBatchProcessor(
                new LumenComponentExtractor(), new RecordingPipeline());
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(null, AllLumenBatchConfiguration.silentDefaults()));
    }

    @Test
    void nullConfigurationIsRejected() {
        final AllLumenBatchProcessor processor = new AllLumenBatchProcessor(
                new LumenComponentExtractor(), new RecordingPipeline());
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE), null));
    }

    @Test
    void everyExtractedComponentProducesOneItemResult() {
        final ByteProcessor image = twoSeparatedLumens();
        final RecordingPipeline pipeline = new RecordingPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchConfiguration config = config(1, false);
        final AllLumenBatchResult result = processor.process(image, config);
        assertEquals(2, result.getTotalComponentCount());
        assertEquals(2, result.getItemResults().size());
    }

    @Test
    void componentsBelowMinimumAreaDoNotCallPipeline() {
        final ByteProcessor image = twoSeparatedLumens();
        final RecordingPipeline pipeline = new RecordingPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(100, false));
        assertEquals(0, pipeline.calls.get());
        assertEquals(2, result.getBelowMinimumAreaCount());
    }

    @Test
    void skippedBorderComponentsDoNotCallPipeline() {
        final ByteProcessor image = filled(6, 6, ClassifierLabels.WOOD_RESULT_VALUE);
        image.set(0, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(0, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(3, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(3, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        final RecordingPipeline pipeline = new RecordingPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(1, true));
        assertEquals(1, pipeline.calls.get());
        assertEquals(1, result.getBorderSkippedCount());
    }

    @Test
    void eligibleComponentsCallPipelineOnceEach() {
        final ByteProcessor image = twoSeparatedLumens();
        final RecordingPipeline pipeline = new RecordingPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        processor.process(image, config(1, false));
        assertEquals(2, pipeline.calls.get());
    }

    @Test
    void processingOrderFollowsComponentId() {
        final ByteProcessor image = twoSeparatedLumens();
        final RecordingPipeline pipeline = new RecordingPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        processor.process(image, config(1, false));
        assertEquals(Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)),
                pipeline.processedIds);
    }

    @Test
    void oneComponentFailureDoesNotStopLaterComponents() {
        final ByteProcessor image = twoSeparatedLumens();
        final FailingThenReadyPipeline pipeline = new FailingThenReadyPipeline();
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertEquals(2, result.getItemResults().size());
        assertEquals(LumenBatchItemResult.Status.PROCESSING_ERROR,
                result.getItemResults().get(0).getStatus());
        assertEquals(LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                result.getItemResults().get(1).getStatus());
    }

    @Test
    void acceptedCandidatesFromDifferentComponentsAreCombined() {
        final ByteProcessor image = woodWithTwoCandidatePairs();
        final MapPipeline pipeline = new MapPipeline();
        pipeline.put(1, Collections.singletonList(new Point(1, 1)));
        pipeline.put(2, Collections.singletonList(new Point(4, 4)));
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertEquals(2, result.getCombinedChangedPixelCount());
        assertEquals(new Point(1, 1), result.getCombinedChangedPixels().get(0));
        assertEquals(new Point(4, 4), result.getCombinedChangedPixels().get(1));
    }

    @Test
    void duplicateAcceptedCoordinatesCreateConflicts() {
        final ByteProcessor image = woodWithTwoCandidatePairs();
        final MapPipeline pipeline = new MapPipeline();
        pipeline.put(1, Arrays.asList(new Point(2, 2), new Point(1, 1)));
        pipeline.put(2, Arrays.asList(new Point(2, 2), new Point(4, 4)));
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertEquals(2, result.getCandidateConflictComponentCount());
        assertEquals(1, result.getConflictingPixelCount());
        assertEquals(0, result.getCombinedChangedPixelCount());
        assertEquals(LumenBatchItemResult.Status.CANDIDATE_CONFLICT,
                result.getItemResults().get(0).getStatus());
        assertEquals(LumenBatchItemResult.Status.CANDIDATE_CONFLICT,
                result.getItemResults().get(1).getStatus());
    }

    @Test
    void nonConflictingComponentsStillReconstruct() {
        final ByteProcessor image = threeLumens();
        final MapPipeline pipeline = new MapPipeline();
        pipeline.put(1, Arrays.asList(new Point(1, 1), new Point(2, 2)));
        pipeline.put(2, Arrays.asList(new Point(2, 2), new Point(5, 1)));
        pipeline.put(3, Collections.singletonList(new Point(8, 8)));
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertEquals(2, result.getCandidateConflictComponentCount());
        assertEquals(1, result.getReconstructedComponentCount());
        assertEquals(1, result.getCombinedChangedPixelCount());
        assertEquals(new Point(8, 8), result.getCombinedChangedPixels().get(0));
    }

    @Test
    void sourceChecksumRemainsUnchangedAndCopyIsSeparate() {
        final ByteProcessor image = woodWithTwoCandidatePairs();
        final MapPipeline pipeline = new MapPipeline();
        pipeline.put(1, Collections.singletonList(new Point(1, 1)));
        pipeline.put(2, Collections.singletonList(new Point(4, 4)));
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final byte[] before = copy((byte[]) image.getPixels());
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertTrue(result.isSourceUnchanged());
        assertTrue(same(before, (byte[]) image.getPixels()));

        final ByteProcessor copy = processor.createCombinedDiagnosticCopy(image, result);
        assertNotSame(image, copy);
        assertEquals(ClassifierLabels.LUMEN_RESULT_VALUE, copy.get(1, 1) & 0xff);
        assertEquals(ClassifierLabels.LUMEN_RESULT_VALUE, copy.get(4, 4) & 0xff);
        assertEquals(ClassifierLabels.WOOD_RESULT_VALUE, image.get(1, 1) & 0xff);
        assertEquals(ClassifierLabels.RAY_RESULT_VALUE, copy.get(0, 0) & 0xff);

        final ByteProcessor mask = processor.createCombinedChangeMask(result);
        assertEquals(255, mask.get(1, 1) & 0xff);
        assertEquals(255, mask.get(4, 4) & 0xff);
        assertEquals(0, mask.get(0, 0) & 0xff);
        int foreground = 0;
        final byte[] maskPixels = (byte[]) mask.getPixels();
        for (int i = 0; i < maskPixels.length; i++) {
            if ((maskPixels[i] & 0xff) == 255) {
                foreground++;
            }
        }
        assertEquals(2, foreground);
        assertTrue(same(before, (byte[]) image.getPixels()));
    }

    @Test
    void summaryCountsMatchStatusesAndRepeatedProcessingIsDeterministic() {
        final ByteProcessor image = twoSeparatedLumens();
        final MapPipeline pipeline = new MapPipeline();
        pipeline.put(1, Collections.singletonList(new Point(1, 1)));
        pipeline.put(2, Collections.<Point>emptyList());
        pipeline.statusForEmpty = LumenBatchItemResult.Status.NO_INTERVAL_SELECTED;
        final AllLumenBatchProcessor processor =
                new AllLumenBatchProcessor(new LumenComponentExtractor(), pipeline);
        final AllLumenBatchConfiguration config = config(1, false);
        final AllLumenBatchResult first = processor.process(image, config);
        final AllLumenBatchResult second = processor.process(image, config);
        assertEquals(1, first.getReconstructedComponentCount());
        assertEquals(1, first.getNoIntervalCount());
        assertEquals(first.getTotalComponentCount(), second.getTotalComponentCount());
        assertEquals(first.getCombinedChangedPixels(), second.getCombinedChangedPixels());
    }

    @Test
    void silentConfigurationContainsNoDisplayFlags() {
        final AllLumenBatchConfiguration silent = AllLumenBatchConfiguration.silentDefaults();
        assertEquals(AllLumenBatchConfiguration.DisplayPolicy.SILENT, silent.getDisplayPolicy());
        assertFalse(silent.isCreateCombinedDiagnosticCopy());
        assertFalse(silent.isCreateCombinedChangeMask());
        assertFalse(silent.isCreateDecisionOverview());
        assertFalse(silent.isCreateResultsTable());
    }

    @Test
    void processingDoesNotRequireImagePlus() {
        final ByteProcessor image = twoSeparatedLumens();
        final AllLumenBatchProcessor processor = new AllLumenBatchProcessor(
                new LumenComponentExtractor(), new RecordingPipeline());
        final AllLumenBatchResult result = processor.process(image, config(1, false));
        assertEquals(2, result.getTotalComponentCount());
    }

    private static AllLumenBatchConfiguration config(
            final int minimumArea,
            final boolean skipBorder) {
        return new AllLumenBatchConfiguration(
                minimumArea,
                skipBorder,
                AllLumenBatchConfiguration.DisplayPolicy.SILENT,
                false,
                false,
                false,
                false);
    }

    private static ByteProcessor twoSeparatedLumens() {
        final ByteProcessor image = filled(8, 8, ClassifierLabels.WOOD_RESULT_VALUE);
        image.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(1, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(6, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        return image;
    }

    private static ByteProcessor woodWithTwoCandidatePairs() {
        final ByteProcessor image = filled(8, 8, ClassifierLabels.RAY_RESULT_VALUE);
        // make wood candidates
        image.set(1, 1, ClassifierLabels.WOOD_RESULT_VALUE);
        image.set(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        image.set(2, 2, ClassifierLabels.WOOD_RESULT_VALUE);
        // two lumen components
        image.set(0, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(7, 7, ClassifierLabels.LUMEN_RESULT_VALUE);
        return image;
    }

    private static ByteProcessor threeLumens() {
        final ByteProcessor image = filled(12, 12, ClassifierLabels.WOOD_RESULT_VALUE);
        image.set(0, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(3, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        image.set(10, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        return image;
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) value;
        }
        return processor;
    }

    private static byte[] copy(final byte[] source) {
        final byte[] out = new byte[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }

    private static boolean same(final byte[] a, final byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static LumenBatchItemResult reconstructedItem(
            final LumenComponentDescriptor component,
            final List<Point> candidates) {
        return new LumenBatchItemResult(
                component.getComponentId(),
                component.getArea(),
                component.getBounds(),
                component.isTouchesImageBorder(),
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                "ready",
                candidates,
                true,
                "SELECTED",
                "STRONG_ARTIFACT_EVIDENCE",
                "READY_FOR_DIAGNOSTIC_COPY",
                10,
                5,
                3,
                5.0,
                4.0,
                0.5,
                2.0,
                0.3,
                1,
                4,
                0,
                0,
                0.5,
                0.6,
                0.1,
                0.2,
                1L);
    }

    private static LumenBatchItemResult statusItem(
            final LumenComponentDescriptor component,
            final LumenBatchItemResult.Status status,
            final String reason) {
        return LumenBatchItemResult.skipped(component, status, reason, 1L);
    }

    private static final class RecordingPipeline implements SingleLumenDiagnosticPipeline {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<Integer> processedIds = new ArrayList<Integer>();

        @Override
        public LumenBatchItemResult process(
                final ByteProcessor classifiedProcessor,
                final LumenComponentDescriptor component) {
            calls.incrementAndGet();
            processedIds.add(Integer.valueOf(component.getComponentId()));
            return statusItem(component,
                    LumenBatchItemResult.Status.NO_INTERVAL_SELECTED,
                    "none");
        }
    }

    private static final class FailingThenReadyPipeline implements SingleLumenDiagnosticPipeline {
        private int call = 0;

        @Override
        public LumenBatchItemResult process(
                final ByteProcessor classifiedProcessor,
                final LumenComponentDescriptor component) {
            call++;
            if (call == 1) {
                return statusItem(component,
                        LumenBatchItemResult.Status.PROCESSING_ERROR,
                        "boom");
            }
            return reconstructedItem(component,
                    Collections.singletonList(new Point(6, 6)));
        }
    }

    private static final class MapPipeline implements SingleLumenDiagnosticPipeline {
        private final Map<Integer, List<Point>> map = new HashMap<Integer, List<Point>>();
        private LumenBatchItemResult.Status statusForEmpty =
                LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE;

        private void put(final int id, final List<Point> candidates) {
            map.put(Integer.valueOf(id), candidates);
        }

        @Override
        public LumenBatchItemResult process(
                final ByteProcessor classifiedProcessor,
                final LumenComponentDescriptor component) {
            final List<Point> candidates = map.get(Integer.valueOf(component.getComponentId()));
            if (candidates == null || candidates.isEmpty()) {
                return statusItem(component, statusForEmpty, "empty");
            }
            return reconstructedItem(component, candidates);
        }
    }
}
