package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.util.Collections;
import java.util.List;

/**
 * Production headless single-Lumen diagnostic pipeline.
 *
 * <p>Orchestrates the verified computational classes only. Creates no UI,
 * no diagnostic image copies, and does not mutate the source processor.</p>
 */
public final class DefaultSingleLumenDiagnosticPipeline
        implements SingleLumenDiagnosticPipeline {

    private final InitialEllipseAnalyzer initialAnalyzer;
    private final OrderedBoundaryExtractor boundaryExtractor;
    private final InwardOutlierIntervalDetector intervalDetector;
    private final SingleIntervalEllipseRefitter refitter;
    private final RefittedEllipseFillPreviewAnalyzer fillPreviewAnalyzer;
    private final ProposedFillComponentAnalyzer componentAnalyzer;
    private final ArtifactEvidenceAssessor evidenceAssessor;
    private final DiagnosticReconstructionBuilder reconstructionBuilder;

    public DefaultSingleLumenDiagnosticPipeline() {
        this.initialAnalyzer = new InitialEllipseAnalyzer();
        this.boundaryExtractor = new OrderedBoundaryExtractor();
        this.intervalDetector = new InwardOutlierIntervalDetector();
        this.refitter = new SingleIntervalEllipseRefitter();
        this.fillPreviewAnalyzer = new RefittedEllipseFillPreviewAnalyzer();
        this.componentAnalyzer = new ProposedFillComponentAnalyzer();
        this.evidenceAssessor = new ArtifactEvidenceAssessor();
        this.reconstructionBuilder = new DiagnosticReconstructionBuilder();
    }

    @Override
    public LumenBatchItemResult process(
            final ByteProcessor classifiedProcessor,
            final LumenComponentDescriptor component) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }

        final long startNanos = System.nanoTime();
        try {
            return processInternal(classifiedProcessor, component, startNanos);
        } catch (AmbiguousBoundaryVertexException ex) {
            return failure(
                    component,
                    LumenBatchItemResult.Status.UNSUPPORTED_BOUNDARY,
                    ex.getMessage() == null ? "AMBIGUOUS_BOUNDARY_VERTEX" : ex.getMessage(),
                    startNanos);
        } catch (IllegalArgumentException ex) {
            final String message = ex.getMessage() == null ? "IllegalArgumentException" : ex.getMessage();
            if (message.startsWith("AMBIGUOUS_BOUNDARY_VERTEX")
                    || message.startsWith("BOUNDARY_")
                    || message.startsWith("DIRECTED_EDGE")
                    || message.startsWith("OPEN_BOUNDARY")
                    || message.startsWith("EXPOSED_EDGE")
                    || message.contains("ordered boundary")
                    || message.contains("INSUFFICIENT_BOUNDARY_SUPPORT")) {
                return failure(
                        component,
                        LumenBatchItemResult.Status.UNSUPPORTED_BOUNDARY,
                        message,
                        startNanos);
            }
            if (message.startsWith("NO_LUMEN")
                    || message.contains("selected pixel")
                    || message.contains("selectedLumenPixels")
                    || message.contains("8-connected")) {
                return failure(
                        component,
                        LumenBatchItemResult.Status.INITIAL_ANALYSIS_FAILED,
                        message,
                        startNanos);
            }
            return failure(
                    component,
                    LumenBatchItemResult.Status.PROCESSING_ERROR,
                    message,
                    startNanos);
        } catch (RuntimeException ex) {
            return failure(
                    component,
                    LumenBatchItemResult.Status.PROCESSING_ERROR,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    startNanos);
        }
    }

    private LumenBatchItemResult processInternal(
            final ByteProcessor classifiedProcessor,
            final LumenComponentDescriptor component,
            final long startNanos) {
        final InitialEllipseAnalysisResult initial =
                initialAnalyzer.analyzeSelectedComponent(
                        classifiedProcessor, component.getPixels());

        final OrderedBoundaryLoop outerLoop =
                boundaryExtractor.selectOuterLoop(initial.getSelectedLumenPixels());
        final OrderedBoundaryResidualProfile residualProfile =
                OrderedBoundaryResidualProfile.create(
                        outerLoop,
                        initial.getEllipseFitResult().getFittedEllipse());
        final int boundaryEdgeCount = outerLoop.getDirectedEdges().size();

        final InwardOutlierDetectionResult detection =
                intervalDetector.detect(residualProfile);
        if (!detection.isIntervalSelected()) {
            return buildResult(
                    component,
                    LumenBatchItemResult.Status.NO_INTERVAL_SELECTED,
                    detection.getSelectionReason(),
                    Collections.<Point>emptyList(),
                    false,
                    detection.getSelectionReason(),
                    "",
                    "",
                    boundaryEdgeCount,
                    -1,
                    -1,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    -1,
                    -1,
                    -1,
                    -1,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    startNanos);
        }

        final InwardOutlierInterval selected = detection.getSelectedInterval();
        final SingleIntervalEllipseRefitResult refit = refitter.refit(detection);
        final RefittedEllipseFillPreviewResult fillPreview =
                fillPreviewAnalyzer.analyze(classifiedProcessor, initial, refit);
        final ProposedFillComponentAnalysisResult componentResult =
                componentAnalyzer.analyze(classifiedProcessor, fillPreview, detection);
        final ArtifactEvidenceAssessment evidence =
                evidenceAssessor.assess(detection, refit, componentResult);

        if (!evidence.isStrongArtifactEvidence()) {
            final LumenBatchItemResult.Status mapped = mapEvidenceDecision(evidence.getDecision());
            return buildResult(
                    component,
                    mapped,
                    evidence.getDecisionReason(),
                    Collections.<Point>emptyList(),
                    true,
                    detection.getSelectionReason(),
                    evidence.getDecision().name(),
                    "",
                    boundaryEdgeCount,
                    selected.getEdgeCount(),
                    selected.getCorePointCount(),
                    evidence.getMinimumDepthSigma(),
                    evidence.getMeanDepthSigma(),
                    evidence.getCoreFraction(),
                    evidence.getNormalizedIntegratedInwardExcess(),
                    evidence.getRetainedRmsImprovementFraction(),
                    evidence.getIntervalAssociatedComponentCount(),
                    evidence.getMatchedExteriorSeedPixelCount(),
                    evidence.getUnmatchedExteriorSeedPixelCount(),
                    evidence.getOtherLumenInsideRefittedEllipseCount(),
                    fillPreview.getOriginalCircularity(),
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    startNanos);
        }

        final DiagnosticReconstructionCandidate candidate =
                reconstructionBuilder.evaluate(
                        classifiedProcessor, evidence, componentResult);
        final LumenBatchItemResult.Status mapped =
                mapReconstructionDecision(candidate.getDecision());
        final List<Point> candidatePixels =
                mapped == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT
                        ? candidate.getPixelsToChange()
                        : Collections.<Point>emptyList();
        final String reason = mapped == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT
                ? "Ready for diagnostic reconstructed copy"
                : candidate.getDecisionReason();

        return buildResult(
                component,
                mapped,
                reason,
                candidatePixels,
                true,
                detection.getSelectionReason(),
                evidence.getDecision().name(),
                candidate.getDecision().name(),
                boundaryEdgeCount,
                selected.getEdgeCount(),
                selected.getCorePointCount(),
                evidence.getMinimumDepthSigma(),
                evidence.getMeanDepthSigma(),
                evidence.getCoreFraction(),
                evidence.getNormalizedIntegratedInwardExcess(),
                evidence.getRetainedRmsImprovementFraction(),
                evidence.getIntervalAssociatedComponentCount(),
                evidence.getMatchedExteriorSeedPixelCount(),
                evidence.getUnmatchedExteriorSeedPixelCount(),
                evidence.getOtherLumenInsideRefittedEllipseCount(),
                fillPreview.getOriginalCircularity(),
                mapped == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT
                        ? candidate.getDiagnosticReconstructedCircularity()
                        : Double.NaN,
                mapped == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT
                        ? candidate.getDiagnosticCircularityChange()
                        : Double.NaN,
                mapped == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT
                        ? candidate.getAddedAreaFraction()
                        : Double.NaN,
                startNanos);
    }

    static LumenBatchItemResult.Status mapEvidenceDecision(
            final ArtifactEvidenceAssessment.Decision decision) {
        if (decision == ArtifactEvidenceAssessment.Decision.NO_INTERVAL_ASSOCIATED_FILL) {
            return LumenBatchItemResult.Status.NO_INTERVAL_ASSOCIATED_FILL;
        }
        if (decision == ArtifactEvidenceAssessment.Decision.AMBIGUOUS_INTERVAL_ASSOCIATION) {
            return LumenBatchItemResult.Status.AMBIGUOUS_INTERVAL_ASSOCIATION;
        }
        if (decision == ArtifactEvidenceAssessment.Decision.UNMATCHED_INTERVAL_EXTERIOR_SEEDS) {
            return LumenBatchItemResult.Status.UNMATCHED_INTERVAL_EXTERIOR_SEEDS;
        }
        if (decision == ArtifactEvidenceAssessment.Decision.OTHER_LUMEN_OVERLAP) {
            return LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP;
        }
        return LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE;
    }

    static LumenBatchItemResult.Status mapReconstructionDecision(
            final DiagnosticReconstructionCandidate.Decision decision) {
        if (decision == DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY) {
            return LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT;
        }
        if (decision == DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS) {
            return LumenBatchItemResult.Status.NON_WOOD_CANDIDATE_PIXELS;
        }
        if (decision == DiagnosticReconstructionCandidate.Decision.SOURCE_PIXEL_VALUE_MISMATCH) {
            return LumenBatchItemResult.Status.SOURCE_PIXEL_VALUE_MISMATCH;
        }
        if (decision == DiagnosticReconstructionCandidate.Decision.OTHER_LUMEN_OVERLAP) {
            return LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP;
        }
        if (decision == DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION) {
            return LumenBatchItemResult.Status.INVALID_STRUCTURAL_ASSOCIATION;
        }
        return LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE;
    }

    private static LumenBatchItemResult failure(
            final LumenComponentDescriptor component,
            final LumenBatchItemResult.Status status,
            final String reason,
            final long startNanos) {
        return buildResult(
                component,
                status,
                reason,
                Collections.<Point>emptyList(),
                false,
                "",
                "",
                "",
                -1,
                -1,
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                -1,
                -1,
                -1,
                -1,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                startNanos);
    }

    private static LumenBatchItemResult buildResult(
            final LumenComponentDescriptor component,
            final LumenBatchItemResult.Status status,
            final String reason,
            final List<Point> candidatePixels,
            final boolean intervalSelected,
            final String intervalSelectionReason,
            final String artifactEvidenceDecision,
            final String reconstructionCandidateDecision,
            final int boundaryEdgeCount,
            final int intervalEdgeCount,
            final int corePointCount,
            final double minimumDepthSigma,
            final double meanDepthSigma,
            final double coreFraction,
            final double normalizedIntegratedInwardExcess,
            final double retainedRmsImprovementFraction,
            final int intervalAssociatedComponentCount,
            final int matchedExteriorSeedPixelCount,
            final int unmatchedExteriorSeedPixelCount,
            final int otherLumenInsideRefittedEllipseCount,
            final double originalCircularity,
            final double diagnosticReconstructedCircularity,
            final double diagnosticCircularityChange,
            final double addedAreaFraction,
            final long startNanos) {
        return new LumenBatchItemResult(
                component.getComponentId(),
                component.getArea(),
                component.getBounds(),
                component.isTouchesImageBorder(),
                status,
                reason,
                candidatePixels,
                intervalSelected,
                intervalSelectionReason,
                artifactEvidenceDecision,
                reconstructionCandidateDecision,
                boundaryEdgeCount,
                intervalEdgeCount,
                corePointCount,
                minimumDepthSigma,
                meanDepthSigma,
                coreFraction,
                normalizedIntegratedInwardExcess,
                retainedRmsImprovementFraction,
                intervalAssociatedComponentCount,
                matchedExteriorSeedPixelCount,
                unmatchedExteriorSeedPixelCount,
                otherLumenInsideRefittedEllipseCount,
                originalCircularity,
                diagnosticReconstructedCircularity,
                diagnosticCircularityChange,
                addedAreaFraction,
                System.nanoTime() - startNanos);
    }
}
