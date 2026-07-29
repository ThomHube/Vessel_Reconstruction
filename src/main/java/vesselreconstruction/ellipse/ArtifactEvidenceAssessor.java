package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative diagnostic assessor of whether a selected inward interval shows
 * strong geometric artifact evidence.
 *
 * <p>Does not modify intervals, refit ellipses, accept components, or alter
 * source pixels.</p>
 */
public final class ArtifactEvidenceAssessor {

    static final double MINIMUM_DEPTH_SIGMA_THRESHOLD = 4.0;
    static final double MEAN_DEPTH_SIGMA_THRESHOLD = 3.0;
    static final double CORE_FRACTION_THRESHOLD = 0.15;
    static final double NORMALIZED_INTEGRATED_EXCESS_THRESHOLD = 1.50;
    static final double RETAINED_RMS_IMPROVEMENT_THRESHOLD = 0.20;

    private static final double TINY_NEGATIVE_EPSILON = -1.0e-12;

    public ArtifactEvidenceAssessment assess(
            final InwardOutlierDetectionResult detectionResult,
            final SingleIntervalEllipseRefitResult refitResult,
            final ProposedFillComponentAnalysisResult componentResult) {
        if (detectionResult == null) {
            throw new IllegalArgumentException("detectionResult must not be null");
        }
        if (refitResult == null) {
            throw new IllegalArgumentException("refitResult must not be null");
        }
        if (componentResult == null) {
            throw new IllegalArgumentException("componentResult must not be null");
        }
        if (!detectionResult.isIntervalSelected()
                || detectionResult.getSelectedInterval() == null) {
            throw new IllegalArgumentException(
                    "detectionResult must have intervalSelected=true with a selected interval");
        }

        final InwardOutlierInterval selected = detectionResult.getSelectedInterval();
        final List<InwardOutlierInterval> candidates = detectionResult.getCandidateIntervals();
        boolean found = false;
        for (int i = 0; i < candidates.size(); i++) {
            if (selected.equals(candidates.get(i))) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "selected interval must belong to the candidate list");
        }

        final double robustScale = detectionResult.getRobustScale();
        if (Double.isNaN(robustScale) || Double.isInfinite(robustScale) || robustScale <= 0.0) {
            throw new IllegalArgumentException(
                    "robustScale must be finite and strictly greater than zero: " + robustScale);
        }
        if (selected.getEdgeCount() <= 0) {
            throw new IllegalArgumentException(
                    "selected interval edge count must be positive: " + selected.getEdgeCount());
        }
        if (!selected.equals(refitResult.getExcludedInterval())) {
            throw new IllegalArgumentException(
                    "refitResult excluded interval must equal the detection selected interval");
        }
        if (!detectionResult.equals(componentResult.getInwardOutlierDetection())) {
            throw new IllegalArgumentException(
                    "componentResult inward-outlier detection must correspond to detectionResult");
        }

        final double medianResidual =
                detectionResult.getResidualProfile().getMedianResidual();
        final double minimumResidual = selected.getMinimumResidual();
        final double meanResidual = selected.getMeanResidual();
        requireFinite("medianResidual", medianResidual);
        requireFinite("minimumResidual", minimumResidual);
        requireFinite("meanResidual", meanResidual);
        requireFinite("integratedInwardExcess", selected.getIntegratedInwardExcess());

        double minimumDepthSigma =
                (medianResidual - minimumResidual) / robustScale;
        double meanDepthSigma =
                (medianResidual - meanResidual) / robustScale;
        minimumDepthSigma = normalizeTinyNegative(minimumDepthSigma, "minimumDepthSigma");
        meanDepthSigma = normalizeTinyNegative(meanDepthSigma, "meanDepthSigma");

        final double coreFraction =
                ((double) selected.getCorePointCount()) / ((double) selected.getEdgeCount());
        double normalizedIntegratedInwardExcess =
                selected.getIntegratedInwardExcess()
                        / (((double) selected.getEdgeCount()) * robustScale);
        normalizedIntegratedInwardExcess = normalizeTinyNegative(
                normalizedIntegratedInwardExcess, "normalizedIntegratedInwardExcess");

        final double baselineRetainedRms = refitResult.getBaselineRetainedRmsResidual();
        final double refittedRetainedRms = refitResult.getRefittedRetainedRmsResidual();
        requireFinite("baselineRetainedRms", baselineRetainedRms);
        requireFinite("refittedRetainedRms", refittedRetainedRms);
        if (!(baselineRetainedRms > 0.0)) {
            throw new IllegalArgumentException(
                    "baselineRetainedRms must be > 0.0: " + baselineRetainedRms);
        }
        final double retainedRmsImprovementFraction =
                (baselineRetainedRms - refittedRetainedRms) / baselineRetainedRms;
        requireFinite("retainedRmsImprovementFraction", retainedRmsImprovementFraction);

        final int associatedCount = componentResult.getIntervalAssociatedComponentCount();
        final int matchedSeeds = componentResult.getMatchedIntervalExteriorSeedPixelCount();
        final int unmatchedSeeds = componentResult.getUnmatchedIntervalExteriorSeedPixelCount();
        final int otherLumen = componentResult.getWholeEllipsePreview()
                .getOtherLumenInsideRefittedEllipseCount();
        final double fillFraction = componentResult.getIntervalAssociatedFillFraction();
        final double circularityChange =
                componentResult.getIntervalAssociatedCircularityChange();
        requireFinite("intervalAssociatedFillFraction", fillFraction);
        requireFinite("intervalAssociatedCircularityChange", circularityChange);

        final boolean exactlyOne =
                associatedCount == 1
                        && componentResult.getPrimaryIntervalAssociatedComponent() != null
                        && componentResult.isExactlyOneIntervalAssociatedComponent();

        return buildAssessment(
                minimumDepthSigma,
                meanDepthSigma,
                coreFraction,
                normalizedIntegratedInwardExcess,
                retainedRmsImprovementFraction,
                selected.getEdgeCount(),
                selected.getCorePointCount(),
                associatedCount,
                matchedSeeds,
                unmatchedSeeds,
                otherLumen,
                fillFraction,
                circularityChange,
                exactlyOne);
    }

    /**
     * Package-private metric hook for deterministic regression tests.
     * Uses the same criterion, structural, decision, and result construction
     * path as {@link #assess}.
     */
    ArtifactEvidenceAssessment assessMetricsForTesting(
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
        final boolean exactlyOne = intervalAssociatedComponentCount == 1;
        return buildAssessment(
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
                intervalAssociatedCircularityChange,
                exactlyOne);
    }

    private static ArtifactEvidenceAssessment buildAssessment(
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
            final double intervalAssociatedCircularityChange,
            final boolean exactlyOneIntervalAssociatedComponent) {
        requireFinite("minimumDepthSigma", minimumDepthSigma);
        requireFinite("meanDepthSigma", meanDepthSigma);
        requireFinite("coreFraction", coreFraction);
        requireFinite("normalizedIntegratedInwardExcess", normalizedIntegratedInwardExcess);
        requireFinite("retainedRmsImprovementFraction", retainedRmsImprovementFraction);
        requireFinite("intervalAssociatedFillFraction", intervalAssociatedFillFraction);
        requireFinite("intervalAssociatedCircularityChange",
                intervalAssociatedCircularityChange);

        final double matchedExteriorSeedFraction = matchedExteriorSeedFraction(
                matchedExteriorSeedPixelCount, unmatchedExteriorSeedPixelCount);

        final boolean minimumDepthPassed =
                minimumDepthSigma >= MINIMUM_DEPTH_SIGMA_THRESHOLD;
        final boolean meanDepthPassed =
                meanDepthSigma >= MEAN_DEPTH_SIGMA_THRESHOLD;
        final boolean coreFractionPassed =
                coreFraction >= CORE_FRACTION_THRESHOLD;
        final boolean integratedExcessPassed =
                normalizedIntegratedInwardExcess >= NORMALIZED_INTEGRATED_EXCESS_THRESHOLD;
        final boolean retainedRmsImprovementPassed =
                retainedRmsImprovementFraction >= RETAINED_RMS_IMPROVEMENT_THRESHOLD;

        final boolean allExteriorSeedsMatched =
                unmatchedExteriorSeedPixelCount == 0 && matchedExteriorSeedPixelCount > 0;
        final boolean noOtherLumenOverlap = otherLumenInsideRefittedEllipseCount == 0;
        final boolean structuralAssociationPassed =
                intervalAssociatedComponentCount == 1
                        && exactlyOneIntervalAssociatedComponent
                        && allExteriorSeedsMatched
                        && noOtherLumenOverlap;

        final DecisionOutcome outcome = decide(
                intervalAssociatedComponentCount,
                matchedExteriorSeedPixelCount,
                unmatchedExteriorSeedPixelCount,
                otherLumenInsideRefittedEllipseCount,
                minimumDepthPassed,
                meanDepthPassed,
                coreFractionPassed,
                integratedExcessPassed,
                retainedRmsImprovementPassed,
                structuralAssociationPassed);

        return new ArtifactEvidenceAssessment(
                outcome.decision,
                outcome.reason,
                minimumDepthSigma,
                meanDepthSigma,
                coreFraction,
                normalizedIntegratedInwardExcess,
                retainedRmsImprovementFraction,
                intervalAssociatedFillFraction,
                matchedExteriorSeedFraction,
                intervalAssociatedCircularityChange,
                intervalEdgeCount,
                corePointCount,
                intervalAssociatedComponentCount,
                matchedExteriorSeedPixelCount,
                unmatchedExteriorSeedPixelCount,
                otherLumenInsideRefittedEllipseCount,
                exactlyOneIntervalAssociatedComponent,
                minimumDepthPassed,
                meanDepthPassed,
                coreFractionPassed,
                integratedExcessPassed,
                retainedRmsImprovementPassed,
                structuralAssociationPassed,
                MINIMUM_DEPTH_SIGMA_THRESHOLD,
                MEAN_DEPTH_SIGMA_THRESHOLD,
                CORE_FRACTION_THRESHOLD,
                NORMALIZED_INTEGRATED_EXCESS_THRESHOLD,
                RETAINED_RMS_IMPROVEMENT_THRESHOLD);
    }

    private static double matchedExteriorSeedFraction(
            final int matched,
            final int unmatched) {
        final int total = matched + unmatched;
        if (total == 0) {
            return 0.0;
        }
        return ((double) matched) / ((double) total);
    }

    private static final class DecisionOutcome {
        private final ArtifactEvidenceAssessment.Decision decision;
        private final String reason;

        private DecisionOutcome(
                final ArtifactEvidenceAssessment.Decision decision,
                final String reason) {
            this.decision = decision;
            this.reason = reason;
        }
    }

    private static DecisionOutcome decide(
            final int intervalAssociatedComponentCount,
            final int matchedExteriorSeedPixelCount,
            final int unmatchedExteriorSeedPixelCount,
            final int otherLumenInsideRefittedEllipseCount,
            final boolean minimumDepthPassed,
            final boolean meanDepthPassed,
            final boolean coreFractionPassed,
            final boolean integratedExcessPassed,
            final boolean retainedRmsImprovementPassed,
            final boolean structuralAssociationPassed) {
        if (intervalAssociatedComponentCount == 0) {
            return new DecisionOutcome(
                    ArtifactEvidenceAssessment.Decision.NO_INTERVAL_ASSOCIATED_FILL,
                    "No interval-associated proposed-fill component was found");
        }
        if (intervalAssociatedComponentCount != 1) {
            return new DecisionOutcome(
                    ArtifactEvidenceAssessment.Decision.AMBIGUOUS_INTERVAL_ASSOCIATION,
                    "Interval association is ambiguous: associatedComponents="
                            + intervalAssociatedComponentCount);
        }
        if (matchedExteriorSeedPixelCount == 0 || unmatchedExteriorSeedPixelCount > 0) {
            return new DecisionOutcome(
                    ArtifactEvidenceAssessment.Decision.UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
                    "Interval exterior seeds are incomplete: matched="
                            + matchedExteriorSeedPixelCount
                            + ", unmatched=" + unmatchedExteriorSeedPixelCount);
        }
        if (otherLumenInsideRefittedEllipseCount > 0) {
            return new DecisionOutcome(
                    ArtifactEvidenceAssessment.Decision.OTHER_LUMEN_OVERLAP,
                    "Other Lumen pixels overlap the refitted ellipse: count="
                            + otherLumenInsideRefittedEllipseCount);
        }

        final boolean allResidual =
                minimumDepthPassed
                        && meanDepthPassed
                        && coreFractionPassed
                        && integratedExcessPassed
                        && retainedRmsImprovementPassed;
        if (allResidual && structuralAssociationPassed) {
            return new DecisionOutcome(
                    ArtifactEvidenceAssessment.Decision.STRONG_ARTIFACT_EVIDENCE,
                    "All residual and structural artifact-evidence criteria passed");
        }

        return new DecisionOutcome(
                ArtifactEvidenceAssessment.Decision.INSUFFICIENT_RESIDUAL_EVIDENCE,
                "Insufficient residual evidence: " + failedResidualCriteria(
                        minimumDepthPassed,
                        meanDepthPassed,
                        coreFractionPassed,
                        integratedExcessPassed,
                        retainedRmsImprovementPassed));
    }

    private static String failedResidualCriteria(
            final boolean minimumDepthPassed,
            final boolean meanDepthPassed,
            final boolean coreFractionPassed,
            final boolean integratedExcessPassed,
            final boolean retainedRmsImprovementPassed) {
        final List<String> failed = new ArrayList<String>(5);
        if (!minimumDepthPassed) {
            failed.add("MINIMUM_DEPTH_SIGMA");
        }
        if (!meanDepthPassed) {
            failed.add("MEAN_DEPTH_SIGMA");
        }
        if (!coreFractionPassed) {
            failed.add("CORE_FRACTION");
        }
        if (!integratedExcessPassed) {
            failed.add("NORMALIZED_INTEGRATED_EXCESS");
        }
        if (!retainedRmsImprovementPassed) {
            failed.add("RETAINED_RMS_IMPROVEMENT");
        }
        if (failed.isEmpty()) {
            // Structural failure reached residual branch unexpectedly.
            return "STRUCTURAL_ASSOCIATION";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failed.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(failed.get(i));
        }
        return sb.toString();
    }

    private static double normalizeTinyNegative(final double value, final String name) {
        requireFinite(name, value);
        if (value < TINY_NEGATIVE_EPSILON) {
            throw new IllegalArgumentException(
                    name + " is significantly negative and inconsistent with an inward interval: "
                            + value);
        }
        if (value < 0.0) {
            return 0.0;
        }
        return value;
    }

    private static void requireFinite(final String name, final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
