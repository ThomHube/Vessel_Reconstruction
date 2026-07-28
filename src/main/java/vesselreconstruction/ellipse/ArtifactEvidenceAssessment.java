package vesselreconstruction.ellipse;

import java.util.Objects;

/**
 * Immutable diagnostic assessment of whether a selected inward interval shows
 * strong geometric evidence consistent with one local artifact.
 */
public final class ArtifactEvidenceAssessment {

    public enum Decision {
        STRONG_ARTIFACT_EVIDENCE,
        INSUFFICIENT_RESIDUAL_EVIDENCE,
        NO_INTERVAL_ASSOCIATED_FILL,
        AMBIGUOUS_INTERVAL_ASSOCIATION,
        UNMATCHED_INTERVAL_EXTERIOR_SEEDS,
        OTHER_LUMEN_OVERLAP,
        UNKNOWN
    }

    private final Decision decision;
    private final String decisionReason;
    private final boolean strongArtifactEvidence;

    private final double minimumDepthSigma;
    private final double meanDepthSigma;
    private final double coreFraction;
    private final double normalizedIntegratedInwardExcess;
    private final double retainedRmsImprovementFraction;

    private final double intervalAssociatedFillFraction;
    private final double matchedExteriorSeedFraction;
    private final double intervalAssociatedCircularityChange;

    private final int intervalEdgeCount;
    private final int corePointCount;
    private final int intervalAssociatedComponentCount;
    private final int matchedExteriorSeedPixelCount;
    private final int unmatchedExteriorSeedPixelCount;
    private final int otherLumenInsideRefittedEllipseCount;

    private final boolean exactlyOneIntervalAssociatedComponent;
    private final boolean allExteriorSeedsMatched;
    private final boolean noOtherLumenOverlap;

    private final boolean minimumDepthPassed;
    private final boolean meanDepthPassed;
    private final boolean coreFractionPassed;
    private final boolean integratedExcessPassed;
    private final boolean retainedRmsImprovementPassed;
    private final boolean structuralAssociationPassed;

    private final double minimumDepthThreshold;
    private final double meanDepthThreshold;
    private final double coreFractionThreshold;
    private final double normalizedIntegratedExcessThreshold;
    private final double retainedRmsImprovementThreshold;

    public ArtifactEvidenceAssessment(
            final Decision decision,
            final String decisionReason,
            final double minimumDepthSigma,
            final double meanDepthSigma,
            final double coreFraction,
            final double normalizedIntegratedInwardExcess,
            final double retainedRmsImprovementFraction,
            final double intervalAssociatedFillFraction,
            final double matchedExteriorSeedFraction,
            final double intervalAssociatedCircularityChange,
            final int intervalEdgeCount,
            final int corePointCount,
            final int intervalAssociatedComponentCount,
            final int matchedExteriorSeedPixelCount,
            final int unmatchedExteriorSeedPixelCount,
            final int otherLumenInsideRefittedEllipseCount,
            final boolean exactlyOneIntervalAssociatedComponent,
            final boolean minimumDepthPassed,
            final boolean meanDepthPassed,
            final boolean coreFractionPassed,
            final boolean integratedExcessPassed,
            final boolean retainedRmsImprovementPassed,
            final boolean structuralAssociationPassed,
            final double minimumDepthThreshold,
            final double meanDepthThreshold,
            final double coreFractionThreshold,
            final double normalizedIntegratedExcessThreshold,
            final double retainedRmsImprovementThreshold) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (decisionReason == null || decisionReason.isEmpty()) {
            throw new IllegalArgumentException("decisionReason must not be null or empty");
        }
        requireFinite("minimumDepthSigma", minimumDepthSigma);
        requireFinite("meanDepthSigma", meanDepthSigma);
        requireFinite("coreFraction", coreFraction);
        requireFinite("normalizedIntegratedInwardExcess", normalizedIntegratedInwardExcess);
        requireFinite("retainedRmsImprovementFraction", retainedRmsImprovementFraction);
        requireFinite("intervalAssociatedFillFraction", intervalAssociatedFillFraction);
        requireFinite("matchedExteriorSeedFraction", matchedExteriorSeedFraction);
        requireFinite("intervalAssociatedCircularityChange", intervalAssociatedCircularityChange);
        requireFinite("minimumDepthThreshold", minimumDepthThreshold);
        requireFinite("meanDepthThreshold", meanDepthThreshold);
        requireFinite("coreFractionThreshold", coreFractionThreshold);
        requireFinite("normalizedIntegratedExcessThreshold",
                normalizedIntegratedExcessThreshold);
        requireFinite("retainedRmsImprovementThreshold", retainedRmsImprovementThreshold);

        requireFraction("coreFraction", coreFraction);
        requireFraction("intervalAssociatedFillFraction", intervalAssociatedFillFraction);
        requireFraction("matchedExteriorSeedFraction", matchedExteriorSeedFraction);

        if (intervalEdgeCount <= 0) {
            throw new IllegalArgumentException(
                    "intervalEdgeCount must be positive: " + intervalEdgeCount);
        }
        if (corePointCount < 0) {
            throw new IllegalArgumentException(
                    "corePointCount must be non-negative: " + corePointCount);
        }
        if (corePointCount > intervalEdgeCount) {
            throw new IllegalArgumentException(
                    "corePointCount must not exceed intervalEdgeCount: core="
                            + corePointCount + ", edges=" + intervalEdgeCount);
        }
        requireNonNegative("intervalAssociatedComponentCount", intervalAssociatedComponentCount);
        requireNonNegative("matchedExteriorSeedPixelCount", matchedExteriorSeedPixelCount);
        requireNonNegative("unmatchedExteriorSeedPixelCount", unmatchedExteriorSeedPixelCount);
        requireNonNegative("otherLumenInsideRefittedEllipseCount",
                otherLumenInsideRefittedEllipseCount);

        final boolean expectedAllSeedsMatched =
                unmatchedExteriorSeedPixelCount == 0 && matchedExteriorSeedPixelCount > 0;
        final boolean expectedNoOtherLumen = otherLumenInsideRefittedEllipseCount == 0;
        final boolean expectedStrong = decision == Decision.STRONG_ARTIFACT_EVIDENCE;

        final boolean expectedMinPass = minimumDepthSigma >= minimumDepthThreshold;
        final boolean expectedMeanPass = meanDepthSigma >= meanDepthThreshold;
        final boolean expectedCorePass = coreFraction >= coreFractionThreshold;
        final boolean expectedIntegratedPass =
                normalizedIntegratedInwardExcess >= normalizedIntegratedExcessThreshold;
        final boolean expectedRmsPass =
                retainedRmsImprovementFraction >= retainedRmsImprovementThreshold;

        if (minimumDepthPassed != expectedMinPass) {
            throw new IllegalArgumentException("minimumDepthPassed inconsistent with metric/threshold");
        }
        if (meanDepthPassed != expectedMeanPass) {
            throw new IllegalArgumentException("meanDepthPassed inconsistent with metric/threshold");
        }
        if (coreFractionPassed != expectedCorePass) {
            throw new IllegalArgumentException("coreFractionPassed inconsistent with metric/threshold");
        }
        if (integratedExcessPassed != expectedIntegratedPass) {
            throw new IllegalArgumentException(
                    "integratedExcessPassed inconsistent with metric/threshold");
        }
        if (retainedRmsImprovementPassed != expectedRmsPass) {
            throw new IllegalArgumentException(
                    "retainedRmsImprovementPassed inconsistent with metric/threshold");
        }

        final boolean expectedStructural =
                intervalAssociatedComponentCount == 1
                        && exactlyOneIntervalAssociatedComponent
                        && expectedAllSeedsMatched
                        && expectedNoOtherLumen;
        if (structuralAssociationPassed != expectedStructural) {
            throw new IllegalArgumentException(
                    "structuralAssociationPassed inconsistent with structural conditions");
        }

        if (expectedStrong) {
            if (!(expectedMinPass && expectedMeanPass && expectedCorePass
                    && expectedIntegratedPass && expectedRmsPass
                    && structuralAssociationPassed)) {
                throw new IllegalArgumentException(
                        "STRONG_ARTIFACT_EVIDENCE requires all residual and structural criteria");
            }
        }

        this.decision = decision;
        this.decisionReason = decisionReason;
        this.strongArtifactEvidence = expectedStrong;
        this.minimumDepthSigma = minimumDepthSigma;
        this.meanDepthSigma = meanDepthSigma;
        this.coreFraction = coreFraction;
        this.normalizedIntegratedInwardExcess = normalizedIntegratedInwardExcess;
        this.retainedRmsImprovementFraction = retainedRmsImprovementFraction;
        this.intervalAssociatedFillFraction = intervalAssociatedFillFraction;
        this.matchedExteriorSeedFraction = matchedExteriorSeedFraction;
        this.intervalAssociatedCircularityChange = intervalAssociatedCircularityChange;
        this.intervalEdgeCount = intervalEdgeCount;
        this.corePointCount = corePointCount;
        this.intervalAssociatedComponentCount = intervalAssociatedComponentCount;
        this.matchedExteriorSeedPixelCount = matchedExteriorSeedPixelCount;
        this.unmatchedExteriorSeedPixelCount = unmatchedExteriorSeedPixelCount;
        this.otherLumenInsideRefittedEllipseCount = otherLumenInsideRefittedEllipseCount;
        this.exactlyOneIntervalAssociatedComponent = exactlyOneIntervalAssociatedComponent;
        this.allExteriorSeedsMatched = expectedAllSeedsMatched;
        this.noOtherLumenOverlap = expectedNoOtherLumen;
        this.minimumDepthPassed = minimumDepthPassed;
        this.meanDepthPassed = meanDepthPassed;
        this.coreFractionPassed = coreFractionPassed;
        this.integratedExcessPassed = integratedExcessPassed;
        this.retainedRmsImprovementPassed = retainedRmsImprovementPassed;
        this.structuralAssociationPassed = structuralAssociationPassed;
        this.minimumDepthThreshold = minimumDepthThreshold;
        this.meanDepthThreshold = meanDepthThreshold;
        this.coreFractionThreshold = coreFractionThreshold;
        this.normalizedIntegratedExcessThreshold = normalizedIntegratedExcessThreshold;
        this.retainedRmsImprovementThreshold = retainedRmsImprovementThreshold;
    }

    private static void requireFinite(final String name, final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static void requireFraction(final String name, final double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1]: " + value);
        }
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    public Decision getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public boolean isStrongArtifactEvidence() {
        return strongArtifactEvidence;
    }

    public double getMinimumDepthSigma() {
        return minimumDepthSigma;
    }

    public double getMeanDepthSigma() {
        return meanDepthSigma;
    }

    public double getCoreFraction() {
        return coreFraction;
    }

    public double getNormalizedIntegratedInwardExcess() {
        return normalizedIntegratedInwardExcess;
    }

    public double getRetainedRmsImprovementFraction() {
        return retainedRmsImprovementFraction;
    }

    public double getIntervalAssociatedFillFraction() {
        return intervalAssociatedFillFraction;
    }

    public double getMatchedExteriorSeedFraction() {
        return matchedExteriorSeedFraction;
    }

    public double getIntervalAssociatedCircularityChange() {
        return intervalAssociatedCircularityChange;
    }

    public int getIntervalEdgeCount() {
        return intervalEdgeCount;
    }

    public int getCorePointCount() {
        return corePointCount;
    }

    public int getIntervalAssociatedComponentCount() {
        return intervalAssociatedComponentCount;
    }

    public int getMatchedExteriorSeedPixelCount() {
        return matchedExteriorSeedPixelCount;
    }

    public int getUnmatchedExteriorSeedPixelCount() {
        return unmatchedExteriorSeedPixelCount;
    }

    public int getOtherLumenInsideRefittedEllipseCount() {
        return otherLumenInsideRefittedEllipseCount;
    }

    public boolean isExactlyOneIntervalAssociatedComponent() {
        return exactlyOneIntervalAssociatedComponent;
    }

    public boolean isAllExteriorSeedsMatched() {
        return allExteriorSeedsMatched;
    }

    public boolean isNoOtherLumenOverlap() {
        return noOtherLumenOverlap;
    }

    public boolean isMinimumDepthPassed() {
        return minimumDepthPassed;
    }

    public boolean isMeanDepthPassed() {
        return meanDepthPassed;
    }

    public boolean isCoreFractionPassed() {
        return coreFractionPassed;
    }

    public boolean isIntegratedExcessPassed() {
        return integratedExcessPassed;
    }

    public boolean isRetainedRmsImprovementPassed() {
        return retainedRmsImprovementPassed;
    }

    public boolean isStructuralAssociationPassed() {
        return structuralAssociationPassed;
    }

    public double getMinimumDepthThreshold() {
        return minimumDepthThreshold;
    }

    public double getMeanDepthThreshold() {
        return meanDepthThreshold;
    }

    public double getCoreFractionThreshold() {
        return coreFractionThreshold;
    }

    public double getNormalizedIntegratedExcessThreshold() {
        return normalizedIntegratedExcessThreshold;
    }

    public double getRetainedRmsImprovementThreshold() {
        return retainedRmsImprovementThreshold;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ArtifactEvidenceAssessment)) {
            return false;
        }
        final ArtifactEvidenceAssessment other = (ArtifactEvidenceAssessment) obj;
        return decision == other.decision
                && strongArtifactEvidence == other.strongArtifactEvidence
                && Double.compare(minimumDepthSigma, other.minimumDepthSigma) == 0
                && Double.compare(meanDepthSigma, other.meanDepthSigma) == 0
                && Double.compare(coreFraction, other.coreFraction) == 0
                && Double.compare(normalizedIntegratedInwardExcess,
                other.normalizedIntegratedInwardExcess) == 0
                && Double.compare(retainedRmsImprovementFraction,
                other.retainedRmsImprovementFraction) == 0
                && Double.compare(intervalAssociatedFillFraction,
                other.intervalAssociatedFillFraction) == 0
                && Double.compare(matchedExteriorSeedFraction,
                other.matchedExteriorSeedFraction) == 0
                && Double.compare(intervalAssociatedCircularityChange,
                other.intervalAssociatedCircularityChange) == 0
                && intervalEdgeCount == other.intervalEdgeCount
                && corePointCount == other.corePointCount
                && intervalAssociatedComponentCount == other.intervalAssociatedComponentCount
                && matchedExteriorSeedPixelCount == other.matchedExteriorSeedPixelCount
                && unmatchedExteriorSeedPixelCount == other.unmatchedExteriorSeedPixelCount
                && otherLumenInsideRefittedEllipseCount == other.otherLumenInsideRefittedEllipseCount
                && exactlyOneIntervalAssociatedComponent
                == other.exactlyOneIntervalAssociatedComponent
                && allExteriorSeedsMatched == other.allExteriorSeedsMatched
                && noOtherLumenOverlap == other.noOtherLumenOverlap
                && minimumDepthPassed == other.minimumDepthPassed
                && meanDepthPassed == other.meanDepthPassed
                && coreFractionPassed == other.coreFractionPassed
                && integratedExcessPassed == other.integratedExcessPassed
                && retainedRmsImprovementPassed == other.retainedRmsImprovementPassed
                && structuralAssociationPassed == other.structuralAssociationPassed
                && Double.compare(minimumDepthThreshold, other.minimumDepthThreshold) == 0
                && Double.compare(meanDepthThreshold, other.meanDepthThreshold) == 0
                && Double.compare(coreFractionThreshold, other.coreFractionThreshold) == 0
                && Double.compare(normalizedIntegratedExcessThreshold,
                other.normalizedIntegratedExcessThreshold) == 0
                && Double.compare(retainedRmsImprovementThreshold,
                other.retainedRmsImprovementThreshold) == 0
                && Objects.equals(decisionReason, other.decisionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                decision,
                decisionReason,
                Boolean.valueOf(strongArtifactEvidence),
                Double.valueOf(minimumDepthSigma),
                Double.valueOf(meanDepthSigma),
                Double.valueOf(coreFraction),
                Double.valueOf(normalizedIntegratedInwardExcess),
                Double.valueOf(retainedRmsImprovementFraction),
                Double.valueOf(intervalAssociatedFillFraction),
                Double.valueOf(matchedExteriorSeedFraction),
                Double.valueOf(intervalAssociatedCircularityChange),
                Integer.valueOf(intervalEdgeCount),
                Integer.valueOf(corePointCount),
                Integer.valueOf(intervalAssociatedComponentCount),
                Integer.valueOf(matchedExteriorSeedPixelCount),
                Integer.valueOf(unmatchedExteriorSeedPixelCount),
                Integer.valueOf(otherLumenInsideRefittedEllipseCount),
                Boolean.valueOf(exactlyOneIntervalAssociatedComponent),
                Boolean.valueOf(allExteriorSeedsMatched),
                Boolean.valueOf(noOtherLumenOverlap),
                Boolean.valueOf(minimumDepthPassed),
                Boolean.valueOf(meanDepthPassed),
                Boolean.valueOf(coreFractionPassed),
                Boolean.valueOf(integratedExcessPassed),
                Boolean.valueOf(retainedRmsImprovementPassed),
                Boolean.valueOf(structuralAssociationPassed),
                Double.valueOf(minimumDepthThreshold),
                Double.valueOf(meanDepthThreshold),
                Double.valueOf(coreFractionThreshold),
                Double.valueOf(normalizedIntegratedExcessThreshold),
                Double.valueOf(retainedRmsImprovementThreshold));
    }

    @Override
    public String toString() {
        return "ArtifactEvidenceAssessment{"
                + "decision=" + decision
                + ", strongArtifactEvidence=" + strongArtifactEvidence
                + ", reason=" + decisionReason
                + ", minDepth=" + minimumDepthSigma
                + ", meanDepth=" + meanDepthSigma
                + ", coreFraction=" + coreFraction
                + ", normExcess=" + normalizedIntegratedInwardExcess
                + ", rmsImprovement=" + retainedRmsImprovementFraction
                + '}';
    }
}
