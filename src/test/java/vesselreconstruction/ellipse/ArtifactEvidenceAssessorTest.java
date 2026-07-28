package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic JUnit 5 tests for {@link ArtifactEvidenceAssessor} and
 * {@link ArtifactEvidenceAssessment}.
 *
 * <p>Criterion, decision, and metric-object tests use the package-private
 * {@code assessMetricsForTesting} hook (same approach used by other
 * analyzers/detectors in this package). Structural validation tests of the
 * public {@code assess()} method use real, correlated production objects
 * built via {@link InwardOutlierIntervalDetector#detectResidualsForTesting},
 * {@link SingleIntervalEllipseRefitter#refit}, and
 * {@link ProposedFillComponentAnalyzer#analyze} wherever practical.</p>
 */
class ArtifactEvidenceAssessorTest {

    private final ArtifactEvidenceAssessor assessor = new ArtifactEvidenceAssessor();
    private final InwardOutlierIntervalDetector detector = new InwardOutlierIntervalDetector();
    private final SingleIntervalEllipseRefitter refitter = new SingleIntervalEllipseRefitter();
    private final ProposedFillComponentAnalyzer analyzer = new ProposedFillComponentAnalyzer();

    // ------------------------------------------------------------------
    // Public assess() null / structural validation
    // ------------------------------------------------------------------

    @Test
    void nullDetectionResultRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final SingleIntervalEllipseRefitResult refit = refitter.refit(detection);
        final ProposedFillComponentAnalysisResult component = buildComponentResult(detection);
        assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(null, refit, component));
    }

    @Test
    void nullRefitResultRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final ProposedFillComponentAnalysisResult component = buildComponentResult(detection);
        assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(detection, null, component));
    }

    @Test
    void nullComponentResultRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final SingleIntervalEllipseRefitResult refit = refitter.refit(detection);
        assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(detection, refit, null));
    }

    @Test
    void detectionWithoutSelectedIntervalRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final SingleIntervalEllipseRefitResult fillerRefit = refitter.refit(detection);
        final ProposedFillComponentAnalysisResult fillerComponent =
                buildComponentResult(detection);

        final InwardOutlierDetectionResult noSelection = new InwardOutlierDetectionResult(
                detection.getResidualProfile(),
                detection.getRobustScale(),
                detection.getCoreThreshold(),
                detection.getExpansionThreshold(),
                Collections.<InwardOutlierInterval>emptyList(),
                null,
                false,
                InwardOutlierDetectionResult.NO_CORE_POINTS,
                0.0);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(noSelection, fillerRefit, fillerComponent));
        assertTrue(ex.getMessage().contains("intervalSelected=true"));
    }

    @Test
    void robustScaleZeroRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final SingleIntervalEllipseRefitResult fillerRefit = refitter.refit(detection);
        final ProposedFillComponentAnalysisResult fillerComponent =
                buildComponentResult(detection);

        final InwardOutlierDetectionResult zeroScale = new InwardOutlierDetectionResult(
                detection.getResidualProfile(),
                0.0,
                detection.getCoreThreshold(),
                detection.getExpansionThreshold(),
                detection.getCandidateIntervals(),
                detection.getSelectedInterval(),
                true,
                detection.getSelectionReason(),
                detection.getDominanceRatio());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(zeroScale, fillerRefit, fillerComponent));
        assertTrue(ex.getMessage().contains("robustScale"));
    }

    @Test
    void robustScaleNegativeRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final SingleIntervalEllipseRefitResult fillerRefit = refitter.refit(detection);
        final ProposedFillComponentAnalysisResult fillerComponent =
                buildComponentResult(detection);

        final InwardOutlierDetectionResult negativeScale = new InwardOutlierDetectionResult(
                detection.getResidualProfile(),
                -1.0,
                detection.getCoreThreshold(),
                detection.getExpansionThreshold(),
                detection.getCandidateIntervals(),
                detection.getSelectedInterval(),
                true,
                detection.getSelectionReason(),
                detection.getDominanceRatio());

        assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(negativeScale, fillerRefit, fillerComponent));
    }

    @Test
    void refitResultExcludedIntervalMismatchRejected() {
        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final ProposedFillComponentAnalysisResult fillerComponent =
                buildComponentResult(detection);

        final List<java.awt.geom.Point2D.Double> points =
                detection.getResidualProfile().getBoundaryLoop().getOrderedSupportMidpoints();
        final SingleIntervalEllipseRefitResult mismatchedRefit =
                refitter.refitSupportPointsForTesting(points, range(0, 5));
        assertNotEquals(detection.getSelectedInterval(), mismatchedRefit.getExcludedInterval());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(detection, mismatchedRefit, fillerComponent));
        assertTrue(ex.getMessage().contains("excluded interval"));
    }

    @Test
    void componentResultDetectionMismatchRejected() {
        final InwardOutlierDetectionResult detectionA =
                detector.detectResidualsForTesting(valleySequence());
        final InwardOutlierDetectionResult detectionB =
                detector.detectResidualsForTesting(secondValleySequence());
        assertNotEquals(detectionA, detectionB);

        final SingleIntervalEllipseRefitResult refitB = refitter.refit(detectionB);
        final ProposedFillComponentAnalysisResult componentA = buildComponentResult(detectionA);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assessor.assess(detectionB, refitB, componentA));
        assertTrue(ex.getMessage().contains("componentResult"));
    }

    // ------------------------------------------------------------------
    // assessMetricsForTesting(): decision-branch priority
    // ------------------------------------------------------------------

    @Test
    void zeroAssociatedComponentsYieldsNoIntervalAssociatedFillEvenWhenResidualCriteriaPass() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 0, 0, 0, 0, 0.0, 0.0);
        assertEquals(ArtifactEvidenceAssessment.Decision.NO_INTERVAL_ASSOCIATED_FILL,
                result.getDecision());
        assertEquals("No interval-associated proposed-fill component was found",
                result.getDecisionReason());
        assertFalse(result.isStrongArtifactEvidence());
    }

    @Test
    void multipleAssociatedComponentsYieldsAmbiguousAssociationEvenWhenResidualCriteriaPass() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 2, 6, 0, 0, 0.4, 0.1);
        assertEquals(ArtifactEvidenceAssessment.Decision.AMBIGUOUS_INTERVAL_ASSOCIATION,
                result.getDecision());
        assertEquals("Interval association is ambiguous: associatedComponents=2",
                result.getDecisionReason());
    }

    @Test
    void zeroMatchedSeedsYieldsUnmatchedIntervalExteriorSeeds() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 0, 0, 0, 0.4, 0.1);
        assertEquals(ArtifactEvidenceAssessment.Decision.UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
                result.getDecision());
        assertEquals("Interval exterior seeds are incomplete: matched=0, unmatched=0",
                result.getDecisionReason());
    }

    @Test
    void unmatchedSeedsPresentYieldsUnmatchedIntervalExteriorSeeds() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 2, 0, 0.4, 0.1);
        assertEquals(ArtifactEvidenceAssessment.Decision.UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
                result.getDecision());
        assertEquals("Interval exterior seeds are incomplete: matched=5, unmatched=2",
                result.getDecisionReason());
    }

    @Test
    void otherLumenOverlapYieldsOtherLumenOverlapDecision() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 0, 3, 0.4, 0.1);
        assertEquals(ArtifactEvidenceAssessment.Decision.OTHER_LUMEN_OVERLAP,
                result.getDecision());
        assertEquals("Other Lumen pixels overlap the refitted ellipse: count=3",
                result.getDecisionReason());
    }

    @Test
    void allCriteriaPassYieldsStrongArtifactEvidence() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
        assertTrue(result.isStructuralAssociationPassed());
        assertTrue(result.isStrongArtifactEvidence());
    }

    // ------------------------------------------------------------------
    // assessMetricsForTesting(): single residual-criterion failures
    // ------------------------------------------------------------------

    @Test
    void minimumDepthSigmaFailureAloneListedInReason() {
        final ArtifactEvidenceAssessment result =
                assess(4.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals("Insufficient residual evidence: MINIMUM_DEPTH_SIGMA",
                result.getDecisionReason());
        assertFalse(result.isMinimumDepthPassed());
        assertTrue(result.isMeanDepthPassed());
    }

    @Test
    void meanDepthSigmaFailureAloneListedInReason() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 2.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals("Insufficient residual evidence: MEAN_DEPTH_SIGMA",
                result.getDecisionReason());
        assertFalse(result.isMeanDepthPassed());
    }

    @Test
    void coreFractionFailureAloneListedInReason() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.10, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals("Insufficient residual evidence: CORE_FRACTION",
                result.getDecisionReason());
        assertFalse(result.isCoreFractionPassed());
    }

    @Test
    void normalizedIntegratedExcessFailureAloneListedInReason() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 1.2, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals("Insufficient residual evidence: NORMALIZED_INTEGRATED_EXCESS",
                result.getDecisionReason());
        assertFalse(result.isIntegratedExcessPassed());
    }

    @Test
    void retainedRmsImprovementFailureAloneListedInReason() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.10, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals("Insufficient residual evidence: RETAINED_RMS_IMPROVEMENT",
                result.getDecisionReason());
        assertFalse(result.isRetainedRmsImprovementPassed());
    }

    // ------------------------------------------------------------------
    // Exact regression examples
    // ------------------------------------------------------------------

    /** Positive example 2: all residual and structural criteria pass strongly. */
    @Test
    void positiveExample2ProducesStrongArtifactEvidence() {
        final ArtifactEvidenceAssessment result = assess(
                8.2, 5.1, 0.42, 2.35, 0.38,
                40, 17, 1, 12, 0, 0, 0.55, 0.12);
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
        assertTrue(result.isStrongArtifactEvidence());
        assertEquals("All residual and structural artifact-evidence criteria passed",
                result.getDecisionReason());
        assertEquals(8.2, result.getMinimumDepthSigma(), 0.0);
        assertEquals(5.1, result.getMeanDepthSigma(), 0.0);
        assertEquals(0.42, result.getCoreFraction(), 0.0);
        assertEquals(2.35, result.getNormalizedIntegratedInwardExcess(), 0.0);
        assertEquals(0.38, result.getRetainedRmsImprovementFraction(), 0.0);
        assertEquals(1, result.getIntervalAssociatedComponentCount());
        assertTrue(result.isExactlyOneIntervalAssociatedComponent());
        assertTrue(result.isAllExteriorSeedsMatched());
        assertTrue(result.isNoOtherLumenOverlap());
    }

    /** Clean example 7: all five residual criteria fail; structure otherwise passes. */
    @Test
    void cleanExample7ProducesInsufficientResidualEvidenceWithAllFiveCriteriaFailing() {
        final ArtifactEvidenceAssessment result = assess(
                1.0, 0.5, 0.05, 0.30, 0.05,
                40, 3, 1, 5, 0, 0, 0.10, 0.02);
        assertEquals(ArtifactEvidenceAssessment.Decision.INSUFFICIENT_RESIDUAL_EVIDENCE,
                result.getDecision());
        assertEquals(
                "Insufficient residual evidence: MINIMUM_DEPTH_SIGMA|MEAN_DEPTH_SIGMA|"
                        + "CORE_FRACTION|NORMALIZED_INTEGRATED_EXCESS|RETAINED_RMS_IMPROVEMENT",
                result.getDecisionReason());
        assertFalse(result.isMinimumDepthPassed());
        assertFalse(result.isMeanDepthPassed());
        assertFalse(result.isCoreFractionPassed());
        assertFalse(result.isIntegratedExcessPassed());
        assertFalse(result.isRetainedRmsImprovementPassed());
        assertFalse(result.isStrongArtifactEvidence());
        assertTrue(result.isStructuralAssociationPassed());
    }

    // ------------------------------------------------------------------
    // Threshold boundaries (>= is inclusive pass)
    // ------------------------------------------------------------------

    @Test
    void minimumDepthSigmaExactlyAtThresholdPasses() {
        final ArtifactEvidenceAssessment result = assess(
                ArtifactEvidenceAssessor.MINIMUM_DEPTH_SIGMA_THRESHOLD,
                5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(result.isMinimumDepthPassed());
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
    }

    @Test
    void minimumDepthSigmaJustBelowThresholdFails() {
        final double justBelow = ArtifactEvidenceAssessor.MINIMUM_DEPTH_SIGMA_THRESHOLD - 0.001;
        final ArtifactEvidenceAssessment result = assess(
                justBelow, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(result.isMinimumDepthPassed());
        assertEquals("Insufficient residual evidence: MINIMUM_DEPTH_SIGMA",
                result.getDecisionReason());
    }

    @Test
    void meanDepthSigmaExactlyAtThresholdPasses() {
        final ArtifactEvidenceAssessment result = assess(
                8.0, ArtifactEvidenceAssessor.MEAN_DEPTH_SIGMA_THRESHOLD,
                0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(result.isMeanDepthPassed());
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
    }

    @Test
    void meanDepthSigmaJustBelowThresholdFails() {
        final double justBelow = ArtifactEvidenceAssessor.MEAN_DEPTH_SIGMA_THRESHOLD - 0.001;
        final ArtifactEvidenceAssessment result = assess(
                8.0, justBelow, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(result.isMeanDepthPassed());
        assertEquals("Insufficient residual evidence: MEAN_DEPTH_SIGMA",
                result.getDecisionReason());
    }

    @Test
    void coreFractionExactlyAtThresholdPasses() {
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, ArtifactEvidenceAssessor.CORE_FRACTION_THRESHOLD,
                2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(result.isCoreFractionPassed());
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
    }

    @Test
    void coreFractionJustBelowThresholdFails() {
        final double justBelow = ArtifactEvidenceAssessor.CORE_FRACTION_THRESHOLD - 0.001;
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, justBelow, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(result.isCoreFractionPassed());
        assertEquals("Insufficient residual evidence: CORE_FRACTION",
                result.getDecisionReason());
    }

    @Test
    void normalizedIntegratedExcessExactlyAtThresholdPasses() {
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, 0.30,
                ArtifactEvidenceAssessor.NORMALIZED_INTEGRATED_EXCESS_THRESHOLD,
                0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(result.isIntegratedExcessPassed());
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
    }

    @Test
    void normalizedIntegratedExcessJustBelowThresholdFails() {
        final double justBelow =
                ArtifactEvidenceAssessor.NORMALIZED_INTEGRATED_EXCESS_THRESHOLD - 0.001;
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, 0.30, justBelow, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(result.isIntegratedExcessPassed());
        assertEquals("Insufficient residual evidence: NORMALIZED_INTEGRATED_EXCESS",
                result.getDecisionReason());
    }

    @Test
    void retainedRmsImprovementExactlyAtThresholdPasses() {
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, 0.30, 2.0,
                ArtifactEvidenceAssessor.RETAINED_RMS_IMPROVEMENT_THRESHOLD,
                40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(result.isRetainedRmsImprovementPassed());
        assertEquals(ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                result.getDecision());
    }

    @Test
    void retainedRmsImprovementJustBelowThresholdFails() {
        final double justBelow =
                ArtifactEvidenceAssessor.RETAINED_RMS_IMPROVEMENT_THRESHOLD - 0.001;
        final ArtifactEvidenceAssessment result = assess(
                8.0, 5.0, 0.30, 2.0, justBelow, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(result.isRetainedRmsImprovementPassed());
        assertEquals("Insufficient residual evidence: RETAINED_RMS_IMPROVEMENT",
                result.getDecisionReason());
    }

    // ------------------------------------------------------------------
    // Derived fractions / structural flags
    // ------------------------------------------------------------------

    @Test
    void matchedExteriorSeedFractionComputedCorrectly() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 3, 1, 0, 0.4, 0.1);
        assertEquals(0.75, result.getMatchedExteriorSeedFraction(), 1.0e-12);
    }

    @Test
    void matchedExteriorSeedFractionZeroWhenNoSeedsAtAll() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 0, 0, 0, 0.4, 0.1);
        assertEquals(0.0, result.getMatchedExteriorSeedFraction(), 0.0);
    }

    @Test
    void allExteriorSeedsMatchedTrueWhenMatchedPositiveAndUnmatchedZero() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 0, 0, 0.4, 0.1);
        assertTrue(result.isAllExteriorSeedsMatched());
    }

    @Test
    void allExteriorSeedsMatchedFalseWhenUnmatchedPositive() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 2, 0, 0.4, 0.1);
        assertFalse(result.isAllExteriorSeedsMatched());
    }

    @Test
    void noOtherLumenOverlapTrueWhenZero() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 0, 0, 0.4, 0.1);
        assertTrue(result.isNoOtherLumenOverlap());
    }

    @Test
    void noOtherLumenOverlapFalseWhenPositive() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 0, 2, 0.4, 0.1);
        assertFalse(result.isNoOtherLumenOverlap());
    }

    @Test
    void exactlyOneIntervalAssociatedComponentDerivedFromComponentCount() {
        final ArtifactEvidenceAssessment single =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 5, 0, 0, 0.4, 0.1);
        assertTrue(single.isExactlyOneIntervalAssociatedComponent());
        final ArtifactEvidenceAssessment multi =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 2, 5, 0, 0, 0.4, 0.1);
        assertFalse(multi.isExactlyOneIntervalAssociatedComponent());
    }

    @Test
    void isStrongArtifactEvidenceTrueOnlyForStrongDecision() {
        final ArtifactEvidenceAssessment strong =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertTrue(strong.isStrongArtifactEvidence());
        final ArtifactEvidenceAssessment weak =
                assess(1.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertFalse(weak.isStrongArtifactEvidence());
    }

    @Test
    void thresholdsAttachedToAssessmentMatchAssessorConstants() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals(ArtifactEvidenceAssessor.MINIMUM_DEPTH_SIGMA_THRESHOLD,
                result.getMinimumDepthThreshold(), 0.0);
        assertEquals(ArtifactEvidenceAssessor.MEAN_DEPTH_SIGMA_THRESHOLD,
                result.getMeanDepthThreshold(), 0.0);
        assertEquals(ArtifactEvidenceAssessor.CORE_FRACTION_THRESHOLD,
                result.getCoreFractionThreshold(), 0.0);
        assertEquals(ArtifactEvidenceAssessor.NORMALIZED_INTEGRATED_EXCESS_THRESHOLD,
                result.getNormalizedIntegratedExcessThreshold(), 0.0);
        assertEquals(ArtifactEvidenceAssessor.RETAINED_RMS_IMPROVEMENT_THRESHOLD,
                result.getRetainedRmsImprovementThreshold(), 0.0);
    }

    @Test
    void edgeCountAndCorePointCountForwardedToAssessment() {
        final ArtifactEvidenceAssessment result =
                assess(8.0, 5.0, 0.30, 2.0, 0.35, 40, 15, 1, 10, 0, 0, 0.5, 0.15);
        assertEquals(40, result.getIntervalEdgeCount());
        assertEquals(15, result.getCorePointCount());
    }

    // ------------------------------------------------------------------
    // ArtifactEvidenceAssessment direct construction validation
    // ------------------------------------------------------------------

    @Test
    void validAssessmentConstructsSuccessfully() {
        final ArtifactEvidenceAssessment assessment = build(validArgs());
        assertEquals(ArtifactEvidenceAssessment.Decision.INSUFFICIENT_RESIDUAL_EVIDENCE,
                assessment.getDecision());
        assertFalse(assessment.isStrongArtifactEvidence());
    }

    @Test
    void nullDecisionRejected() {
        final Args a = validArgs();
        a.decision = null;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void nullDecisionReasonRejected() {
        final Args a = validArgs();
        a.decisionReason = null;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void emptyDecisionReasonRejected() {
        final Args a = validArgs();
        a.decisionReason = "";
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void nonFiniteMinimumDepthSigmaRejected() {
        final Args a = validArgs();
        a.minimumDepthSigma = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void infiniteMetricRejected() {
        final Args a = validArgs();
        a.normalizedIntegratedInwardExcess = Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void coreFractionAboveOneRejected() {
        final Args a = validArgs();
        a.coreFraction = 1.5;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void coreFractionBelowZeroRejected() {
        final Args a = validArgs();
        a.coreFraction = -0.1;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void intervalAssociatedFillFractionOutOfRangeRejected() {
        final Args a = validArgs();
        a.intervalAssociatedFillFraction = 1.5;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void nonPositiveIntervalEdgeCountRejected() {
        final Args a = validArgs();
        a.intervalEdgeCount = 0;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void negativeCorePointCountRejected() {
        final Args a = validArgs();
        a.corePointCount = -1;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void corePointCountExceedingEdgeCountRejected() {
        final Args a = validArgs();
        a.corePointCount = a.intervalEdgeCount + 1;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void negativeComponentCountRejected() {
        final Args a = validArgs();
        a.intervalAssociatedComponentCount = -1;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void inconsistentMinimumDepthPassedFlagRejected() {
        final Args a = validArgs();
        a.minimumDepthPassed = true;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void inconsistentStructuralAssociationPassedFlagRejected() {
        final Args a = validArgs();
        a.structuralAssociationPassed = false;
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void strongDecisionRequiresAllCriteriaPassRejectedWhenNotAllPass() {
        final Args a = validArgs();
        a.decision = ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE;
        a.decisionReason = "All residual and structural artifact-evidence criteria passed";
        assertThrows(IllegalArgumentException.class, () -> build(a));
    }

    @Test
    void strongDecisionAcceptedWhenAllCriteriaActuallyPass() {
        final Args a = validArgs();
        a.decision = ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE;
        a.decisionReason = "All residual and structural artifact-evidence criteria passed";
        a.minimumDepthSigma = 8.0;
        a.meanDepthSigma = 5.0;
        a.coreFraction = 0.30;
        a.normalizedIntegratedInwardExcess = 2.0;
        a.retainedRmsImprovementFraction = 0.35;
        a.minimumDepthPassed = true;
        a.meanDepthPassed = true;
        a.coreFractionPassed = true;
        a.integratedExcessPassed = true;
        a.retainedRmsImprovementPassed = true;
        final ArtifactEvidenceAssessment assessment = build(a);
        assertTrue(assessment.isStrongArtifactEvidence());
    }

    @Test
    void equalsAndHashCodeConsistentForIdenticalArgs() {
        final ArtifactEvidenceAssessment x = build(validArgs());
        final ArtifactEvidenceAssessment y = build(validArgs());
        assertEquals(x, y);
        assertEquals(x.hashCode(), y.hashCode());
    }

    @Test
    void equalsFalseForDifferentDecision() {
        final Args a2 = validArgs();
        a2.decision = ArtifactEvidenceAssessment.Decision.NO_INTERVAL_ASSOCIATED_FILL;
        a2.decisionReason = "No interval-associated proposed-fill component was found";
        a2.intervalAssociatedComponentCount = 0;
        a2.matchedExteriorSeedPixelCount = 0;
        a2.exactlyOneIntervalAssociatedComponent = false;
        a2.structuralAssociationPassed = false;
        assertNotEquals(build(validArgs()), build(a2));
    }

    @Test
    void toStringContainsDecisionAndKeyMetrics() {
        final ArtifactEvidenceAssessment assessment = build(validArgs());
        final String text = assessment.toString();
        assertTrue(text.contains("INSUFFICIENT_RESIDUAL_EVIDENCE"));
        assertTrue(text.contains("minDepth"));
    }

    @Test
    void gettersReflectConstructorArguments() {
        final Args a = validArgs();
        final ArtifactEvidenceAssessment assessment = build(a);
        assertEquals(a.minimumDepthSigma, assessment.getMinimumDepthSigma(), 0.0);
        assertEquals(a.meanDepthSigma, assessment.getMeanDepthSigma(), 0.0);
        assertEquals(a.coreFraction, assessment.getCoreFraction(), 0.0);
        assertEquals(a.normalizedIntegratedInwardExcess,
                assessment.getNormalizedIntegratedInwardExcess(), 0.0);
        assertEquals(a.retainedRmsImprovementFraction,
                assessment.getRetainedRmsImprovementFraction(), 0.0);
        assertEquals(a.intervalAssociatedFillFraction,
                assessment.getIntervalAssociatedFillFraction(), 0.0);
        assertEquals(a.matchedExteriorSeedFraction,
                assessment.getMatchedExteriorSeedFraction(), 0.0);
        assertEquals(a.intervalAssociatedCircularityChange,
                assessment.getIntervalAssociatedCircularityChange(), 0.0);
        assertEquals(a.intervalEdgeCount, assessment.getIntervalEdgeCount());
        assertEquals(a.corePointCount, assessment.getCorePointCount());
        assertEquals(a.intervalAssociatedComponentCount,
                assessment.getIntervalAssociatedComponentCount());
        assertEquals(a.matchedExteriorSeedPixelCount,
                assessment.getMatchedExteriorSeedPixelCount());
        assertEquals(a.unmatchedExteriorSeedPixelCount,
                assessment.getUnmatchedExteriorSeedPixelCount());
        assertEquals(a.otherLumenInsideRefittedEllipseCount,
                assessment.getOtherLumenInsideRefittedEllipseCount());
    }

    // ------------------------------------------------------------------
    // Helpers: assessMetricsForTesting convenience wrapper
    // ------------------------------------------------------------------

    private ArtifactEvidenceAssessment assess(
            final double minimumDepthSigma,
            final double meanDepthSigma,
            final double coreFraction,
            final double normalizedIntegratedInwardExcess,
            final double retainedRmsImprovementFraction,
            final int intervalEdgeCount,
            final int corePointCount,
            final int intervalAssociatedComponentCount,
            final int matchedExteriorSeedPixelCount,
            final int unmatchedExteriorSeedPixelCount,
            final int otherLumenInsideRefittedEllipseCount,
            final double intervalAssociatedFillFraction,
            final double intervalAssociatedCircularityChange) {
        return assessor.assessMetricsForTesting(
                minimumDepthSigma,
                meanDepthSigma,
                coreFraction,
                normalizedIntegratedInwardExcess,
                retainedRmsImprovementFraction,
                intervalEdgeCount,
                corePointCount,
                intervalAssociatedComponentCount,
                matchedExteriorSeedPixelCount,
                unmatchedExteriorSeedPixelCount,
                otherLumenInsideRefittedEllipseCount,
                intervalAssociatedFillFraction,
                intervalAssociatedCircularityChange);
    }

    // ------------------------------------------------------------------
    // Helpers: ArtifactEvidenceAssessment direct construction
    // ------------------------------------------------------------------

    private static final class Args {
        private ArtifactEvidenceAssessment.Decision decision =
                ArtifactEvidenceAssessment.Decision.INSUFFICIENT_RESIDUAL_EVIDENCE;
        private String decisionReason = "Insufficient residual evidence: MINIMUM_DEPTH_SIGMA";
        private double minimumDepthSigma = 1.0;
        private double meanDepthSigma = 1.0;
        private double coreFraction = 0.05;
        private double normalizedIntegratedInwardExcess = 0.5;
        private double retainedRmsImprovementFraction = 0.05;
        private double intervalAssociatedFillFraction = 0.3;
        private double matchedExteriorSeedFraction = 0.8;
        private double intervalAssociatedCircularityChange = 0.1;
        private int intervalEdgeCount = 20;
        private int corePointCount = 5;
        private int intervalAssociatedComponentCount = 1;
        private int matchedExteriorSeedPixelCount = 4;
        private int unmatchedExteriorSeedPixelCount = 0;
        private int otherLumenInsideRefittedEllipseCount = 0;
        private boolean exactlyOneIntervalAssociatedComponent = true;
        private boolean minimumDepthPassed = false;
        private boolean meanDepthPassed = false;
        private boolean coreFractionPassed = false;
        private boolean integratedExcessPassed = false;
        private boolean retainedRmsImprovementPassed = false;
        private boolean structuralAssociationPassed = true;
        private double minimumDepthThreshold = 5.0;
        private double meanDepthThreshold = 3.0;
        private double coreFractionThreshold = 0.15;
        private double normalizedIntegratedExcessThreshold = 1.5;
        private double retainedRmsImprovementThreshold = 0.2;
    }

    private static Args validArgs() {
        return new Args();
    }

    private static ArtifactEvidenceAssessment build(final Args a) {
        return new ArtifactEvidenceAssessment(
                a.decision,
                a.decisionReason,
                a.minimumDepthSigma,
                a.meanDepthSigma,
                a.coreFraction,
                a.normalizedIntegratedInwardExcess,
                a.retainedRmsImprovementFraction,
                a.intervalAssociatedFillFraction,
                a.matchedExteriorSeedFraction,
                a.intervalAssociatedCircularityChange,
                a.intervalEdgeCount,
                a.corePointCount,
                a.intervalAssociatedComponentCount,
                a.matchedExteriorSeedPixelCount,
                a.unmatchedExteriorSeedPixelCount,
                a.otherLumenInsideRefittedEllipseCount,
                a.exactlyOneIntervalAssociatedComponent,
                a.minimumDepthPassed,
                a.meanDepthPassed,
                a.coreFractionPassed,
                a.integratedExcessPassed,
                a.retainedRmsImprovementPassed,
                a.structuralAssociationPassed,
                a.minimumDepthThreshold,
                a.meanDepthThreshold,
                a.coreFractionThreshold,
                a.normalizedIntegratedExcessThreshold,
                a.retainedRmsImprovementThreshold);
    }

    // ------------------------------------------------------------------
    // Helpers: real correlated production objects for assess() structural tests
    // ------------------------------------------------------------------

    private ProposedFillComponentAnalysisResult buildComponentResult(
            final InwardOutlierDetectionResult detection) {
        final List<Point> lumen = rect(0, 0, 3, 3);
        final ByteProcessor processor =
                filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final EllipseModel ellipse = new EllipseModel(1.0, 1.0, 2.0, 2.0, 0.0);
        final RefittedEllipseFillPreviewResult preview = buildWholePreview(
                20, 20, lumen, Collections.<Point>emptyList(), ellipse, 0.5, 0.5, 0.0);
        return analyzer.analyze(processor, preview, detection);
    }

    private static RefittedEllipseFillPreviewResult buildWholePreview(
            final int width,
            final int height,
            final List<Point> selected,
            final List<Point> fill,
            final EllipseModel ellipse,
            final double originalCircularity,
            final double wholeEllipseCircularity,
            final double wholeEllipseCircularityChange) {
        final Set<Long> selectedKeys = keySet(selected);
        final Set<Long> fillKeys = keySet(fill);
        final List<Point> preview = unionSorted(selectedKeys, fillKeys);
        final List<Point> empty = Collections.emptyList();
        final int originalPerimeter = exposedPerimeter(selectedKeys, width, height);
        final int previewPerimeter = exposedPerimeter(keySet(preview), width, height);
        final int originalArea = selected.size();
        final int fillCount = fill.size();
        final double addedAreaFraction = fillCount / (double) originalArea;
        final double ellipseAreaRatio = ellipse.getArea() / (double) originalArea;
        return new RefittedEllipseFillPreviewResult(
                width,
                height,
                ellipse,
                ellipse,
                selected,
                fill,
                fill,
                fill,
                empty,
                empty,
                fill,
                empty,
                empty,
                empty,
                empty,
                empty,
                preview,
                originalArea,
                originalArea + fillCount,
                originalArea + fillCount,
                fillCount,
                fillCount,
                fillCount,
                0,
                0,
                fillCount,
                0,
                0,
                0,
                0,
                0,
                originalArea,
                originalPerimeter,
                originalCircularity,
                originalArea + fillCount,
                previewPerimeter,
                wholeEllipseCircularity,
                wholeEllipseCircularityChange,
                addedAreaFraction,
                ellipseAreaRatio,
                false);
    }

    private static List<Integer> range(final int startInclusive, final int endInclusive) {
        final List<Integer> list = new ArrayList<Integer>();
        for (int i = startInclusive; i <= endInclusive; i++) {
            list.add(Integer.valueOf(i));
        }
        return list;
    }

    private static List<Point> rect(
            final int x0, final int y0, final int width, final int height) {
        final List<Point> list = new ArrayList<Point>(width * height);
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                list.add(new Point(x, y));
            }
        }
        return list;
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) value;
        }
        return processor;
    }

    private static Set<Long> keySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            keys.add(Long.valueOf(pack(point.x, point.y)));
        }
        return keys;
    }

    private static List<Point> unionSorted(final Set<Long> a, final Set<Long> b) {
        final Set<Long> union = new HashSet<Long>(a);
        union.addAll(b);
        final List<Point> points = new ArrayList<Point>(union.size());
        for (final Long keyObj : union) {
            points.add(unpack(keyObj.longValue()));
        }
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(final Point p1, final Point p2) {
                if (p1.y != p2.y) {
                    return Integer.compare(p1.y, p2.y);
                }
                return Integer.compare(p1.x, p2.x);
            }
        });
        return points;
    }

    private static int exposedPerimeter(
            final Set<Long> memberKeys, final int width, final int height) {
        final int[] dx = {0, 1, 0, -1};
        final int[] dy = {-1, 0, 1, 0};
        int perimeter = 0;
        for (final Long keyObj : memberKeys) {
            final long key = keyObj.longValue();
            final int x = (int) (key >> 32);
            final int y = (int) key;
            for (int i = 0; i < dx.length; i++) {
                final int nx = x + dx[i];
                final int ny = y + dy[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !memberKeys.contains(Long.valueOf(pack(nx, ny)))) {
                    perimeter++;
                }
            }
        }
        return perimeter;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Point unpack(final long key) {
        return new Point((int) (key >> 32), (int) key);
    }

    // ------------------------------------------------------------------
    // Helpers: synthetic residual sequences (mirrors InwardOutlierIntervalDetectorTest)
    // ------------------------------------------------------------------

    /**
     * Ordinary near-zero variation with one broad deep inward valley and smaller dips.
     */
    private static List<Double> valleySequence() {
        final List<Double> residuals = constantList(40, 0.0);
        for (int i = 0; i < residuals.size(); i++) {
            residuals.set(i, Double.valueOf(0.01 * Math.sin(i * 0.7)));
        }
        for (int i = 12; i <= 22; i++) {
            residuals.set(i, Double.valueOf(-0.08));
        }
        for (int i = 14; i <= 20; i++) {
            residuals.set(i, Double.valueOf(-0.18));
        }
        residuals.set(17, Double.valueOf(-0.35));
        residuals.set(18, Double.valueOf(-0.40));
        residuals.set(19, Double.valueOf(-0.33));
        residuals.set(30, Double.valueOf(-0.05));
        residuals.set(31, Double.valueOf(-0.06));
        return residuals;
    }

    /** A distinctly shaped and located valley, producing a different detection result. */
    private static List<Double> secondValleySequence() {
        final List<Double> residuals = constantList(48, 0.0);
        for (int i = 0; i < residuals.size(); i++) {
            residuals.set(i, Double.valueOf(0.01 * Math.cos(i * 0.5)));
        }
        for (int i = 5; i <= 15; i++) {
            residuals.set(i, Double.valueOf(-0.09));
        }
        for (int i = 7; i <= 13; i++) {
            residuals.set(i, Double.valueOf(-0.19));
        }
        residuals.set(9, Double.valueOf(-0.36));
        residuals.set(10, Double.valueOf(-0.41));
        residuals.set(11, Double.valueOf(-0.34));
        residuals.set(40, Double.valueOf(-0.05));
        return residuals;
    }

    private static List<Double> constantList(final int n, final double value) {
        final List<Double> list = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            list.add(Double.valueOf(value));
        }
        return list;
    }
}
