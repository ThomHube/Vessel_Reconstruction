package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSingleLumenDiagnosticPipelineTest {

    private final DefaultSingleLumenDiagnosticPipeline pipeline =
            new DefaultSingleLumenDiagnosticPipeline();
    private final LumenComponentExtractor extractor = new LumenComponentExtractor();

    @Test
    void nullProcessorIsRejected() {
        final LumenComponentDescriptor component = singlePixelComponent();
        assertThrows(IllegalArgumentException.class, () -> pipeline.process(null, component));
    }

    @Test
    void nullComponentIsRejected() {
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.WOOD_RESULT_VALUE);
        assertThrows(IllegalArgumentException.class, () -> pipeline.process(processor, null));
    }

    @Test
    void componentCoordinatesMustRemainRawValueTwo() {
        final ByteProcessor processor = filled(40, 40, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 20, 20, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = extractor.extract(processor).get(0);
        // Corrupt one lumen pixel after descriptor creation.
        final Point p = component.getPixels().get(0);
        processor.set(p.x, p.y, ClassifierLabels.WOOD_RESULT_VALUE);
        final LumenBatchItemResult result = pipeline.process(processor, component);
        assertEquals(LumenBatchItemResult.Status.INITIAL_ANALYSIS_FAILED, result.getStatus());
        assertTrue(result.getCandidatePixels().isEmpty());
    }

    @Test
    void smoothDiskMapsToNoIntervalSelectedAndPreservesReason() {
        final ByteProcessor processor = filled(80, 80, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 40, 40, 18, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = extractor.extract(processor).get(0);
        final byte[] before = copy((byte[]) processor.getPixels());
        final LumenBatchItemResult result = pipeline.process(processor, component);
        assertEquals(LumenBatchItemResult.Status.NO_INTERVAL_SELECTED, result.getStatus());
        assertFalse(result.getIntervalSelectionReason().isEmpty());
        assertEquals(result.getIntervalSelectionReason(), result.getReason());
        assertTrue(result.getCandidatePixels().isEmpty());
        assertFalse(result.isReconstructed());
        assertTrue(same(before, (byte[]) processor.getPixels()));
    }

    @Test
    void insufficientResidualEvidenceMapsCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.INSUFFICIENT_RESIDUAL_EVIDENCE));
        assertEquals(
                LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.UNKNOWN));
    }

    @Test
    void otherLumenOverlapMapsCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.OTHER_LUMEN_OVERLAP));
        assertEquals(
                LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.OTHER_LUMEN_OVERLAP));
    }

    @Test
    void noAssociatedFillMapsCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.NO_INTERVAL_ASSOCIATED_FILL,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.NO_INTERVAL_ASSOCIATED_FILL));
    }

    @Test
    void ambiguousAssociationMapsCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.AMBIGUOUS_INTERVAL_ASSOCIATION,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.AMBIGUOUS_INTERVAL_ASSOCIATION));
    }

    @Test
    void unmatchedSeedsMapCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
                DefaultSingleLumenDiagnosticPipeline.mapEvidenceDecision(
                        ArtifactEvidenceAssessment.Decision.UNMATCHED_INTERVAL_EXTERIOR_SEEDS));
    }

    @Test
    void nonWoodCandidateMapsCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.NON_WOOD_CANDIDATE_PIXELS,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS));
    }

    @Test
    void readyCandidateMapsToReconstructedSingleArtifact() {
        assertEquals(
                LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY));
    }

    @Test
    void invalidStructuralAndSourceMismatchMapCorrectly() {
        assertEquals(
                LumenBatchItemResult.Status.INVALID_STRUCTURAL_ASSOCIATION,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION));
        assertEquals(
                LumenBatchItemResult.Status.SOURCE_PIXEL_VALUE_MISMATCH,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.SOURCE_PIXEL_VALUE_MISMATCH));
        assertEquals(
                LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE,
                DefaultSingleLumenDiagnosticPipeline.mapReconstructionDecision(
                        DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE));
    }

    @Test
    void pipelineDoesNotCreateDiagnosticCopyOrRequireImagePlus() {
        final ByteProcessor processor = filled(60, 60, ClassifierLabels.WOOD_RESULT_VALUE);
        fillDisk(processor, 30, 30, 12, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = extractor.extract(processor).get(0);
        final byte[] before = copy((byte[]) processor.getPixels());
        final LumenBatchItemResult result = pipeline.process(processor, component);
        assertNotNull(result);
        assertTrue(same(before, (byte[]) processor.getPixels()));
        // Pipeline returns candidates only for reconstructed status; never mutates source.
        assertTrue(result.getCandidatePixels().isEmpty() || result.isReconstructed());
    }

    @Test
    void analyzeSelectedComponentMatchesAutoSelectForSingleComponent() {
        final ByteProcessor processor = filled(50, 50, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        fillDisk(processor, 25, 25, 10, ClassifierLabels.LUMEN_RESULT_VALUE);
        final InitialEllipseAnalyzer analyzer = new InitialEllipseAnalyzer();
        final InitialEllipseAnalysisResult auto = analyzer.analyze(processor);
        final InitialEllipseAnalysisResult selected = analyzer.analyzeSelectedComponent(
                processor, auto.getSelectedLumenPixels());
        assertEquals(auto.getSelectedComponentArea(), selected.getSelectedComponentArea());
        assertEquals(auto.getSelectedLumenPixels(), selected.getSelectedLumenPixels());
        assertEquals(auto.getBoundarySupportPoints().size(),
                selected.getBoundarySupportPoints().size());
        assertEquals(auto.getEllipseFitResult().getFittedEllipse().getCenterX(),
                selected.getEllipseFitResult().getFittedEllipse().getCenterX(), 1.0e-12);
    }

    private static LumenComponentDescriptor singlePixelComponent() {
        return new LumenComponentDescriptor(
                1,
                java.util.Collections.singletonList(new Point(1, 1)),
                4,
                4);
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) value;
        }
        return processor;
    }

    private static void fillDisk(
            final ByteProcessor processor,
            final int cx,
            final int cy,
            final int radius,
            final int value) {
        final int r2 = radius * radius;
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                final int dx = x - cx;
                final int dy = y - cy;
                if (dx * dx + dy * dy <= r2) {
                    processor.set(x, y, value);
                }
            }
        }
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
}
