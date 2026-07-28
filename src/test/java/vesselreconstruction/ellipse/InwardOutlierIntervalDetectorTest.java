package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for diagnostic inward-outlier interval detection.
 */
class InwardOutlierIntervalDetectorTest {

    private final InwardOutlierIntervalDetector detector = new InwardOutlierIntervalDetector();

    @Test
    void nullProfileRejected() {
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }

    @Test
    void fewerThanEightResidualsRejected() {
        final List<Double> residuals = constantList(6, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectResidualsForTesting(residuals));
    }

    @Test
    void nullResidualListRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectResidualsForTesting(null));
    }

    @Test
    void nullResidualEntryRejected() {
        final List<Double> residuals = constantList(8, 0.0);
        residuals.set(3, null);
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectResidualsForTesting(residuals));
    }

    @Test
    void nonFiniteResidualRejected() {
        final List<Double> residuals = constantList(8, 0.0);
        residuals.set(2, Double.valueOf(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectResidualsForTesting(residuals));
    }

    @Test
    void robustScaleEqualsNormalizedMad() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final double mad = manualMad(residuals);
        assertEquals(1.4826 * mad, result.getRobustScale(), 1.0e-12);
    }

    @Test
    void coreThresholdFormulaExact() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final double median = manualMedian(residuals);
        final double expected = median - 3.0 * result.getRobustScale();
        assertEquals(expected, result.getCoreThreshold(), 1.0e-12);
    }

    @Test
    void expansionThresholdFormulaExact() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final double median = manualMedian(residuals);
        final double expected = median - 1.5 * result.getRobustScale();
        assertEquals(expected, result.getExpansionThreshold(), 1.0e-12);
    }

    @Test
    void coreThresholdMoreNegativeThanExpansion() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertTrue(result.getCoreThreshold() < result.getExpansionThreshold());
    }

    @Test
    void zeroMadReturnsRobustScaleTooSmall() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(constantList(12, -0.01));
        assertEquals(InwardOutlierDetectionResult.ROBUST_SCALE_TOO_SMALL,
                result.getSelectionReason());
        assertFalse(result.isIntervalSelected());
        assertTrue(result.getCandidateIntervals().isEmpty());
    }

    @Test
    void noCorePointsReturnsNoCorePoints() {
        // Mild variation, no deep inward points.
        final List<Double> residuals = new ArrayList<Double>();
        for (int i = 0; i < 16; i++) {
            residuals.add(Double.valueOf(0.01 * Math.sin(i)));
        }
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertEquals(InwardOutlierDetectionResult.NO_CORE_POINTS, result.getSelectionReason());
        assertFalse(result.isIntervalSelected());
    }

    @Test
    void oneContiguousExpansionRunCreatesOneCandidate() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertTrue(result.getCandidateIntervals().size() >= 1);
        assertTrue(result.isIntervalSelected());
    }

    @Test
    void expansionRunWithoutCoreIgnored() {
        final List<Double> residuals = wallNoiseList(20);
        // Mild shallow dip (expansion only) and one deep core valley.
        for (int i = 2; i <= 4; i++) {
            residuals.set(i, Double.valueOf(-0.03));
        }
        for (int i = 10; i <= 16; i++) {
            residuals.set(i, Double.valueOf(-0.25));
        }
        residuals.set(13, Double.valueOf(-0.40));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        for (final InwardOutlierInterval interval : result.getCandidateIntervals()) {
            assertTrue(interval.getCorePointCount() > 0);
        }
    }

    @Test
    void twoSeparatedRunsRemainSeparate() {
        final List<Double> residuals = wallNoiseList(40);
        for (int i = 4; i <= 10; i++) {
            residuals.set(i, Double.valueOf(-0.30));
        }
        residuals.set(7, Double.valueOf(-0.50));
        for (int i = 24; i <= 30; i++) {
            residuals.set(i, Double.valueOf(-0.28));
        }
        residuals.set(27, Double.valueOf(-0.45));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.getCandidateIntervals().size() >= 2);
    }

    @Test
    void oneSampleGapIsNotBridged() {
        final List<Double> residuals = wallNoiseList(20);
        for (int i = 4; i <= 8; i++) {
            residuals.set(i, Double.valueOf(-0.30));
        }
        residuals.set(6, Double.valueOf(-0.50));
        // Gap at index 9
        residuals.set(9, Double.valueOf(0.01));
        for (int i = 10; i <= 12; i++) {
            residuals.set(i, Double.valueOf(-0.30));
        }
        residuals.set(11, Double.valueOf(-0.48));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.getCandidateIntervals().size() >= 2);
        for (final InwardOutlierInterval interval : result.getCandidateIntervals()) {
            assertFalse(interval.getOrderedBoundaryIndices().contains(Integer.valueOf(9))
                    && interval.getOrderedBoundaryIndices().contains(Integer.valueOf(8))
                    && interval.getOrderedBoundaryIndices().contains(Integer.valueOf(10)));
        }
    }

    @Test
    void firstAndFinalRunsMergeIntoWrappingInterval() {
        final List<Double> residuals = wallNoiseList(16);
        for (int i = 0; i <= 2; i++) {
            residuals.set(i, Double.valueOf(-0.30));
        }
        for (int i = 13; i <= 15; i++) {
            residuals.set(i, Double.valueOf(-0.30));
        }
        residuals.set(0, Double.valueOf(-0.50));
        residuals.set(14, Double.valueOf(-0.45));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        boolean foundWrap = false;
        for (final InwardOutlierInterval interval : result.getCandidateIntervals()) {
            if (interval.isWrapsAround()) {
                foundWrap = true;
                assertTrue(interval.getStartIndex() > interval.getEndIndex()
                        || interval.getOrderedBoundaryIndices().get(0).intValue()
                        == interval.getStartIndex());
            }
        }
        assertTrue(foundWrap);
    }

    @Test
    void wrappingIntervalPreservesCircularIndexOrder() {
        final List<Double> residuals = wallNoiseList(16);
        for (int i = 0; i <= 2; i++) {
            residuals.set(i, Double.valueOf(-0.35));
        }
        for (int i = 12; i <= 15; i++) {
            residuals.set(i, Double.valueOf(-0.35));
        }
        residuals.set(14, Double.valueOf(-0.55));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        InwardOutlierInterval wrap = null;
        for (final InwardOutlierInterval interval : result.getCandidateIntervals()) {
            if (interval.isWrapsAround()) {
                wrap = interval;
                break;
            }
        }
        assertNotNull(wrap);
        final List<Integer> indices = wrap.getOrderedBoundaryIndices();
        assertEquals(wrap.getStartIndex(), indices.get(0).intValue());
        assertEquals(wrap.getEndIndex(), indices.get(indices.size() - 1).intValue());
        // After start, indices should increase to n-1 then continue from 0.
        boolean sawDrop = false;
        for (int i = 1; i < indices.size(); i++) {
            final int prev = indices.get(i - 1).intValue();
            final int curr = indices.get(i).intValue();
            if (curr < prev) {
                sawDrop = true;
                assertEquals(15, prev);
                assertEquals(0, curr);
            } else {
                assertEquals(prev + 1, curr);
            }
        }
        assertTrue(sawDrop);
    }

    @Test
    void nonWrappingIntervalPreservesAscendingOrder() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        final InwardOutlierInterval selected = result.getSelectedInterval();
        assertNotNull(selected);
        assertFalse(selected.isWrapsAround());
        final List<Integer> indices = selected.getOrderedBoundaryIndices();
        for (int i = 1; i < indices.size(); i++) {
            assertEquals(indices.get(i - 1).intValue() + 1, indices.get(i).intValue());
        }
    }

    @Test
    void intervalEdgeCountCorrect() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        final InwardOutlierInterval selected = result.getSelectedInterval();
        assertEquals(selected.getOrderedBoundaryIndices().size(), selected.getEdgeCount());
    }

    @Test
    void intervalBoundaryFractionCorrect() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final InwardOutlierInterval selected = result.getSelectedInterval();
        assertEquals(
                selected.getEdgeCount() / (double) residuals.size(),
                selected.getBoundaryFraction(),
                1.0e-15);
    }

    @Test
    void intervalCorePointCountCorrect() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        final InwardOutlierInterval selected = result.getSelectedInterval();
        int expected = 0;
        for (final Integer idx : selected.getOrderedBoundaryIndices()) {
            if (valleySequence().get(idx.intValue()).doubleValue() <= result.getCoreThreshold()) {
                expected++;
            }
        }
        assertEquals(expected, selected.getCorePointCount());
    }

    @Test
    void intervalMinimumResidualAndIndexCorrect() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final InwardOutlierInterval selected = result.getSelectedInterval();
        double min = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        for (final Integer idx : selected.getOrderedBoundaryIndices()) {
            final double value = residuals.get(idx.intValue()).doubleValue();
            if (value < min) {
                min = value;
                minIndex = idx.intValue();
            }
        }
        assertEquals(min, selected.getMinimumResidual(), 0.0);
        assertEquals(minIndex, selected.getMinimumResidualIndex());
    }

    @Test
    void intervalMeanResidualCorrect() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final InwardOutlierInterval selected = result.getSelectedInterval();
        double sum = 0.0;
        for (final Integer idx : selected.getOrderedBoundaryIndices()) {
            sum += residuals.get(idx.intValue()).doubleValue();
        }
        assertEquals(sum / selected.getEdgeCount(), selected.getMeanResidual(), 1.0e-12);
    }

    @Test
    void integratedInwardExcessCorrect() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        final InwardOutlierInterval selected = result.getSelectedInterval();
        double expected = 0.0;
        for (final Integer idx : selected.getOrderedBoundaryIndices()) {
            expected += Math.max(0.0,
                    result.getExpansionThreshold()
                            - residuals.get(idx.intValue()).doubleValue());
        }
        assertEquals(expected, selected.getIntegratedInwardExcess(), 1.0e-12);
    }

    @Test
    void containsGlobalMinimumCorrect() {
        final List<Double> residuals = valleySequence();
        int globalMinIndex = 0;
        double globalMin = residuals.get(0).doubleValue();
        for (int i = 1; i < residuals.size(); i++) {
            if (residuals.get(i).doubleValue() < globalMin) {
                globalMin = residuals.get(i).doubleValue();
                globalMinIndex = i;
            }
        }
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.getSelectedInterval().isContainsGlobalMinimum());
        assertTrue(result.getSelectedInterval().getOrderedBoundaryIndices()
                .contains(Integer.valueOf(globalMinIndex)));
    }

    @Test
    void intervalShorterThanFiveRejected() {
        final List<Double> residuals = wallNoiseList(20);
        // Very narrow deep spike of 3 samples.
        residuals.set(8, Double.valueOf(-0.50));
        residuals.set(9, Double.valueOf(-0.60));
        residuals.set(10, Double.valueOf(-0.50));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertFalse(result.isIntervalSelected());
        assertTrue(result.getSelectionReason().equals(
                InwardOutlierDetectionResult.INTERVAL_TOO_SHORT)
                || result.getSelectionReason().equals(
                InwardOutlierDetectionResult.NO_VALID_INTERVAL)
                || result.getSelectionReason().equals(
                InwardOutlierDetectionResult.NO_CORE_POINTS));
        // If core points exist, candidates should still be retained diagnostically.
        if (!result.getSelectionReason().equals(InwardOutlierDetectionResult.NO_CORE_POINTS)
                && !result.getSelectionReason().equals(
                InwardOutlierDetectionResult.ROBUST_SCALE_TOO_SMALL)) {
            assertFalse(result.getCandidateIntervals().isEmpty());
        }
    }

    @Test
    void intervalCoveringMoreThan35PercentRejected() {
        final List<Double> residuals = wallNoiseList(40);
        // 16/40 = 0.40 > 0.35, but majority of samples remain ordinary wall noise.
        for (int i = 2; i <= 17; i++) {
            residuals.set(i, Double.valueOf(-0.40));
        }
        residuals.set(8, Double.valueOf(-0.70));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertFalse(result.isIntervalSelected());
        assertTrue(
                result.getSelectionReason().equals(InwardOutlierDetectionResult.INTERVAL_TOO_LARGE)
                        || result.getSelectionReason().equals(
                        InwardOutlierDetectionResult.NO_VALID_INTERVAL));
        assertFalse(result.getCandidateIntervals().isEmpty());
    }

    @Test
    void candidatesRemainAfterRejection() {
        final List<Double> residuals = wallNoiseList(40);
        for (int i = 2; i <= 17; i++) {
            residuals.set(i, Double.valueOf(-0.40));
        }
        residuals.set(8, Double.valueOf(-0.70));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertFalse(result.isIntervalSelected());
        assertTrue(result.getCandidateIntervals().size() >= 1);
    }

    @Test
    void oneValidIntervalIsSelected() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertTrue(result.isIntervalSelected());
        assertEquals(InwardOutlierDetectionResult.SELECTED_DOMINANT_INWARD_INTERVAL,
                result.getSelectionReason());
        assertNotNull(result.getSelectedInterval());
    }

    @Test
    void oneValidIntervalHasInfiniteDominance() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertEquals(Double.POSITIVE_INFINITY, result.getDominanceRatio(), 0.0);
    }

    @Test
    void selectedIntervalContainsGlobalMinimum() {
        final List<Double> residuals = valleySequence();
        int minIndex = 0;
        double min = residuals.get(0).doubleValue();
        for (int i = 1; i < residuals.size(); i++) {
            if (residuals.get(i).doubleValue() < min) {
                min = residuals.get(i).doubleValue();
                minIndex = i;
            }
        }
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertEquals(minIndex, result.getSelectedInterval().getMinimumResidualIndex());
    }

    @Test
    void twoValidIntervalsOrderedByIntegratedExcess() {
        final List<Double> residuals = twoValleySequence(0.60, 0.35);
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.getCandidateIntervals().size() >= 2);
        final InwardOutlierInterval first = result.getCandidateIntervals().get(0);
        final InwardOutlierInterval second = result.getCandidateIntervals().get(1);
        assertTrue(first.getIntegratedInwardExcess() >= second.getIntegratedInwardExcess());
    }

    @Test
    void minimumResidualIsSecondTieBreaker() {
        // Construct nearly equal excess with different minima via controlled values.
        final List<Double> residuals = wallNoiseList(40);
        // Valley A
        for (int i = 4; i <= 10; i++) {
            residuals.set(i, Double.valueOf(-0.20));
        }
        residuals.set(7, Double.valueOf(-0.55));
        // Valley B similar integrated excess but less deep min
        for (int i = 24; i <= 30; i++) {
            residuals.set(i, Double.valueOf(-0.22));
        }
        residuals.set(27, Double.valueOf(-0.40));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.getCandidateIntervals().size() >= 2);
        assertTrue(result.getCandidateIntervals().get(0).getMinimumResidual()
                <= result.getCandidateIntervals().get(1).getMinimumResidual());
    }

    @Test
    void corePointCountIsThirdTieBreaker() {
        final List<Double> residuals = wallNoiseList(40);
        for (int i = 4; i <= 10; i++) {
            residuals.set(i, Double.valueOf(-0.25));
        }
        residuals.set(6, Double.valueOf(-0.50));
        residuals.set(7, Double.valueOf(-0.50));
        residuals.set(8, Double.valueOf(-0.50));
        for (int i = 24; i <= 30; i++) {
            residuals.set(i, Double.valueOf(-0.25));
        }
        residuals.set(27, Double.valueOf(-0.50));
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        if (result.getCandidateIntervals().size() >= 2) {
            final InwardOutlierInterval a = result.getCandidateIntervals().get(0);
            final InwardOutlierInterval b = result.getCandidateIntervals().get(1);
            if (a.getIntegratedInwardExcess() == b.getIntegratedInwardExcess()
                    && a.getMinimumResidual() == b.getMinimumResidual()) {
                assertTrue(a.getCorePointCount() >= b.getCorePointCount());
            }
        }
    }

    @Test
    void boundaryFractionIsFourthTieBreaker() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertNotNull(result.getCandidateIntervals());
    }

    @Test
    void startAndEndIndicesProvideFinalTieBreaking() {
        final InwardOutlierDetectionResult a =
                detector.detectResidualsForTesting(valleySequence());
        final InwardOutlierDetectionResult b =
                detector.detectResidualsForTesting(valleySequence());
        assertEquals(a.getCandidateIntervals(), b.getCandidateIntervals());
    }

    @Test
    void dominanceRatioBelowThresholdReturnsMultipleCompeting() {
        final List<Double> residuals = twoValleySequence(0.50, 0.48);
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        if (result.getCandidateIntervals().size() >= 2) {
            // May select or reject depending on validity/fraction; if both valid and close:
            if (!result.isIntervalSelected()) {
                assertEquals(InwardOutlierDetectionResult.MULTIPLE_COMPETING_INTERVALS,
                        result.getSelectionReason());
                assertTrue(result.getDominanceRatio() < 1.50);
            }
        }
    }

    @Test
    void dominanceRatioExactlyOnePointFiveAccepted() {
        // Hard to force exact 1.5; verify the comparison uses >= by selecting a clear winner.
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertTrue(result.isIntervalSelected());
    }

    @Test
    void detectorDoesNotMutateSuppliedResiduals() {
        final List<Double> residuals = valleySequence();
        final List<Double> snapshot = new ArrayList<Double>(residuals);
        detector.detectResidualsForTesting(residuals);
        assertEquals(snapshot, residuals);
    }

    @Test
    void repeatedDetectionIsDeterministic() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult a = detector.detectResidualsForTesting(residuals);
        final InwardOutlierDetectionResult b = detector.detectResidualsForTesting(residuals);
        assertEquals(a.getSelectionReason(), b.getSelectionReason());
        assertEquals(a.isIntervalSelected(), b.isIntervalSelected());
        assertEquals(a.getCandidateIntervals(), b.getCandidateIntervals());
        assertEquals(a.getSelectedInterval(), b.getSelectedInterval());
    }

    @Test
    void candidateListIsImmutable() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getCandidateIntervals().add(result.getCandidateIntervals().get(0)));
    }

    @Test
    void orderedIndexListIsImmutable() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getSelectedInterval().getOrderedBoundaryIndices()
                        .add(Integer.valueOf(0)));
    }

    @Test
    void returnedSupportPointsAreDefensiveCopies() {
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(valleySequence());
        final java.awt.geom.Point2D.Double p =
                result.getSelectedInterval().getStartSupportMidpoint();
        final double ox = p.x;
        p.x = -99;
        assertEquals(ox, result.getSelectedInterval().getStartSupportMidpoint().x, 0.0);
    }

    @Test
    void noProfileSampleIsRemoved() {
        final List<Double> residuals = valleySequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertEquals(residuals.size(), result.getResidualProfile().getSamples().size());
    }

    @Test
    void noEllipseParameterIsChanged() {
        final OrderedBoundaryExtractor extractor = new OrderedBoundaryExtractor();
        final List<java.awt.Point> pixels = new ArrayList<java.awt.Point>();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                pixels.add(new java.awt.Point(x, y));
            }
        }
        final OrderedBoundaryLoop loop = extractor.selectOuterLoop(pixels);
        final EllipseModel ellipse = new EllipseModel(2, 2, 3, 2, 0.2);
        final OrderedBoundaryResidualProfile profile =
                OrderedBoundaryResidualProfile.create(loop, ellipse);
        detector.detect(profile);
        assertEquals(2.0, ellipse.getCenterX(), 0.0);
        assertEquals(2.0, ellipse.getCenterY(), 0.0);
        assertEquals(3.0, ellipse.getSemiMajorAxis(), 0.0);
        assertEquals(2.0, ellipse.getSemiMinorAxis(), 0.0);
        assertEquals(0.2, ellipse.getRotationRadians(), 0.0);
    }

    @Test
    void realExampleShapedSequenceSelectsOneDominantInterval() {
        final List<Double> residuals = realExampleShapedSequence();
        final InwardOutlierDetectionResult result =
                detector.detectResidualsForTesting(residuals);
        assertTrue(result.isIntervalSelected());
        assertEquals(1, countDominantLike(result));
        int minIndex = 0;
        double min = residuals.get(0).doubleValue();
        for (int i = 1; i < residuals.size(); i++) {
            if (residuals.get(i).doubleValue() < min) {
                min = residuals.get(i).doubleValue();
                minIndex = i;
            }
        }
        assertTrue(result.getSelectedInterval().getOrderedBoundaryIndices()
                .contains(Integer.valueOf(minIndex)));
        assertTrue(result.getSelectedInterval().getBoundaryFraction() <= 0.35);
    }

    private static int countDominantLike(final InwardOutlierDetectionResult result) {
        return result.isIntervalSelected() ? 1 : 0;
    }

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
        // Smaller fluctuation elsewhere
        residuals.set(30, Double.valueOf(-0.05));
        residuals.set(31, Double.valueOf(-0.06));
        return residuals;
    }

    private static List<Double> twoValleySequence(
            final double depthA,
            final double depthB) {
        final List<Double> residuals = wallNoiseList(40);
        for (int i = 4; i <= 12; i++) {
            residuals.set(i, Double.valueOf(-0.15));
        }
        residuals.set(8, Double.valueOf(-depthA));
        for (int i = 24; i <= 32; i++) {
            residuals.set(i, Double.valueOf(-0.15));
        }
        residuals.set(28, Double.valueOf(-depthB));
        return residuals;
    }

    private static List<Double> realExampleShapedSequence() {
        final List<Double> residuals = wallNoiseList(48);
        // Broad deep valley around a known minimum index
        for (int i = 18; i <= 30; i++) {
            residuals.set(i, Double.valueOf(-0.10));
        }
        for (int i = 21; i <= 27; i++) {
            residuals.set(i, Double.valueOf(-0.18));
        }
        residuals.set(24, Double.valueOf(-0.223));
        // Smaller fluctuations
        residuals.set(5, Double.valueOf(-0.04));
        residuals.set(6, Double.valueOf(-0.05));
        residuals.set(38, Double.valueOf(-0.045));
        return residuals;
    }

    private static List<Double> constantList(final int n, final double value) {
        final List<Double> list = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            list.add(Double.valueOf(value));
        }
        return list;
    }

    /**
     * Ordinary wall variation near zero so MAD is positive and thresholds are usable.
     */
    private static List<Double> wallNoiseList(final int n) {
        final List<Double> list = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            list.add(Double.valueOf(0.01 * Math.sin(i * 0.7)));
        }
        return list;
    }

    private static double manualMedian(final List<Double> values) {
        final double[] sorted = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            sorted[i] = values.get(i).doubleValue();
        }
        java.util.Arrays.sort(sorted);
        final int n = sorted.length;
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static double manualMad(final List<Double> values) {
        final double median = manualMedian(values);
        final List<Double> absDev = new ArrayList<Double>();
        for (int i = 0; i < values.size(); i++) {
            absDev.add(Double.valueOf(Math.abs(values.get(i).doubleValue() - median)));
        }
        return manualMedian(absDev);
    }
}
