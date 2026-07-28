package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for source-safe refitted-ellipse fill preview analysis.
 */
class RefittedEllipseFillPreviewAnalyzerTest {

    private final RefittedEllipseFillPreviewAnalyzer analyzer =
            new RefittedEllipseFillPreviewAnalyzer();

    @Test
    void nullByteProcessorRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(null, null, null));
    }

    @Test
    void nullInitialAnalysisRejected() {
        final ByteProcessor processor = new ByteProcessor(8, 8);
        fillBackground(processor);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(processor, null, null));
    }

    @Test
    void nullRefitResultRejected() {
        final InitialEllipseAnalysisResult analysis = simpleAnalysis();
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(
                        analysisProcessor(analysis), analysis, null));
    }

    @Test
    void mismatchedImageDimensionsRejected() {
        final InitialEllipseAnalysisResult analysis = simpleAnalysis();
        final ByteProcessor other = new ByteProcessor(
                analysis.getImageWidth() + 1, analysis.getImageHeight());
        final SingleIntervalEllipseRefitResult refit = simpleRefitResult();
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(other, analysis, refit));
    }

    @Test
    void selectedPixelsMustBeInBounds() {
        final Fixture fixture = syntheticFixture();
        final List<Point> bad = copyPoints(fixture.selected);
        bad.add(new Point(fixture.processor.getWidth() + 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeForTesting(
                        fixture.processor, bad, fixture.baseline, fixture.refitted));
    }

    @Test
    void selectedPixelsMustBeRawValueTwo() {
        final Fixture fixture = syntheticFixture();
        final List<Point> bad = copyPoints(fixture.selected);
        // Convert one selected lumen pixel to wood in a copy of the processor.
        final ByteProcessor copy = copyProcessor(fixture.processor);
        final Point p = bad.get(0);
        copy.set(p.x, p.y, ClassifierLabels.WOOD_RESULT_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeForTesting(
                        copy, bad, fixture.baseline, fixture.refitted));
    }

    @Test
    void pixelCenterInclusionUsesHalfOffsets() {
        // Ellipse centered at (2.5, 2.5) with radius 1.0 includes pixel (2,2) center.
        final EllipseModel circle = new EllipseModel(2.5, 2.5, 1.0, 1.0, 0.0);
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(circle, 2, 2));
        assertFalse(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(circle, 4, 2));
    }

    @Test
    void inclusionUsesTolerance() {
        final EllipseModel ellipse = new EllipseModel(5.0, 5.0, 3.0, 2.0, 0.0);
        // Far outside
        assertFalse(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(ellipse, 0, 0));
        // Center inside
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(ellipse, 5, 5));
    }

    @Test
    void selectedObservedLumenNeverProposedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        for (final Point selected : fixture.selected) {
            assertFalse(contains(result.getBaselineProposedFillPixels(), selected));
            assertFalse(contains(result.getRefittedProposedFillPixels(), selected));
        }
    }

    @Test
    void otherRawValueTwoComponentNotProposedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getRefittedProposedFillPixels(), fixture.otherLumen));
        assertTrue(contains(result.getOtherLumenPixelsInsideRefittedEllipse(),
                fixture.otherLumen));
    }

    @Test
    void nonLumenInsideBaselineIsBaselineFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getBaselineProposedFillPixels(), fixture.sharedFill));
        assertTrue(contains(result.getBaselineProposedFillPixels(), fixture.baselineOnlyFill));
    }

    @Test
    void nonLumenOutsideBaselineNotBaselineFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getBaselineProposedFillPixels(), fixture.refitOnlyFill));
    }

    @Test
    void nonLumenInsideRefittedIsRefittedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefittedProposedFillPixels(), fixture.sharedFill));
        assertTrue(contains(result.getRefittedProposedFillPixels(), fixture.refitOnlyFill));
    }

    @Test
    void nonLumenOutsideRefittedNotRefittedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getRefittedProposedFillPixels(), fixture.baselineOnlyFill));
    }

    @Test
    void baselineAndRefittedFillIndependent() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(result.getBaselineProposedFillCount()
                != result.getRefittedProposedFillCount()
                || result.getBaselineOnlyProposedFillCount() > 0
                || result.getRefitOnlyProposedFillCount() > 0);
    }

    @Test
    void sharedIsExactIntersection() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getSharedProposedFillPixels(), fixture.sharedFill));
        assertFalse(contains(result.getSharedProposedFillPixels(), fixture.refitOnlyFill));
        assertFalse(contains(result.getSharedProposedFillPixels(), fixture.baselineOnlyFill));
    }

    @Test
    void refitOnlyExactDifference() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefitOnlyProposedFillPixels(), fixture.refitOnlyFill));
        assertFalse(contains(result.getRefitOnlyProposedFillPixels(), fixture.sharedFill));
    }

    @Test
    void baselineOnlyExactDifference() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getBaselineOnlyProposedFillPixels(),
                fixture.baselineOnlyFill));
        assertFalse(contains(result.getBaselineOnlyProposedFillPixels(), fixture.sharedFill));
    }

    @Test
    void sharedPlusRefitOnlyEqualsRefittedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getRefittedProposedFillCount(),
                result.getSharedProposedFillCount() + result.getRefitOnlyProposedFillCount());
    }

    @Test
    void sharedPlusBaselineOnlyEqualsBaselineFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getBaselineProposedFillCount(),
                result.getSharedProposedFillCount() + result.getBaselineOnlyProposedFillCount());
    }

    @Test
    void rawZeroCountedAsWood() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefittedWoodFillPixels(), fixture.woodFill));
    }

    @Test
    void rawOneCountedAsRay() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefittedRayFillPixels(), fixture.rayFill));
    }

    @Test
    void rawThreeCountedAsBackground() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefittedBackgroundFillPixels(), fixture.backgroundFill));
    }

    @Test
    void unknownNonLumenCountedAsOther() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getRefittedOtherClassFillPixels(), fixture.otherClassFill));
    }

    @Test
    void rawValueTwoNeverInFillClassLists() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getRefittedWoodFillPixels(), fixture.otherLumen));
        assertFalse(contains(result.getRefittedRayFillPixels(), fixture.otherLumen));
        assertFalse(contains(result.getRefittedBackgroundFillPixels(), fixture.otherLumen));
        assertFalse(contains(result.getRefittedOtherClassFillPixels(), fixture.otherLumen));
    }

    @Test
    void fillClassCountsSumToRefittedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getRefittedProposedFillCount(),
                result.getRefittedWoodFillCount()
                        + result.getRefittedRayFillCount()
                        + result.getRefittedBackgroundFillCount()
                        + result.getRefittedOtherClassFillCount());
    }

    @Test
    void selectedLumenOutsideRefittedDetected() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getSelectedLumenPixelsOutsideRefittedEllipse(),
                fixture.selectedOutside));
    }

    @Test
    void selectedOutsideRemainsInPreview() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getPreviewSelectedLumenPixels(), fixture.selectedOutside));
    }

    @Test
    void otherLumenInsideDetected() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(contains(result.getOtherLumenPixelsInsideRefittedEllipse(),
                fixture.otherLumen));
    }

    @Test
    void otherLumenNotMergedIntoPreview() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getPreviewSelectedLumenPixels(), fixture.otherLumen));
    }

    @Test
    void wouldOverlapFlagExact() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(result.isWouldOverlapAnotherLumenComponent());
        assertEquals(result.getOtherLumenInsideRefittedEllipseCount() > 0,
                result.isWouldOverlapAnotherLumenComponent());
    }

    @Test
    void previewEqualsSelectedUnionRefittedFill() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getSelectedObservedLumenArea() + result.getRefittedProposedFillCount(),
                result.getPreviewArea());
        for (final Point p : result.getSelectedObservedLumenPixels()) {
            assertTrue(contains(result.getPreviewSelectedLumenPixels(), p));
        }
        for (final Point p : result.getRefittedProposedFillPixels()) {
            assertTrue(contains(result.getPreviewSelectedLumenPixels(), p));
        }
    }

    @Test
    void previewDoesNotContainUnrelatedPixels() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertFalse(contains(result.getPreviewSelectedLumenPixels(), fixture.farAway));
    }

    @Test
    void originalAreaCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(fixture.selected.size(), result.getOriginalArea());
    }

    @Test
    void previewAreaCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getOriginalArea() + result.getRefittedProposedFillCount(),
                result.getPreviewArea());
    }

    @Test
    void onePixelPerimeterEqualsFour() {
        final ByteProcessor processor = new ByteProcessor(5, 5);
        fillBackground(processor);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<Point> selected = listOf(new Point(2, 2));
        final EllipseModel ellipse = new EllipseModel(2.5, 2.5, 0.1, 0.1, 0.0);
        final RefittedEllipseFillPreviewResult result = analyzer.analyzeForTesting(
                processor, selected, ellipse, ellipse);
        assertEquals(4, result.getOriginalPerimeter());
    }

    @Test
    void twoByTwoBlockPerimeterEqualsEight() {
        final ByteProcessor processor = new ByteProcessor(6, 6);
        fillBackground(processor);
        final List<Point> selected = new ArrayList<Point>();
        for (int y = 2; y <= 3; y++) {
            for (int x = 2; x <= 3; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                selected.add(new Point(x, y));
            }
        }
        final EllipseModel ellipse = new EllipseModel(2.5, 2.5, 0.1, 0.1, 0.0);
        final RefittedEllipseFillPreviewResult result = analyzer.analyzeForTesting(
                processor, selected, ellipse, ellipse);
        assertEquals(8, result.getOriginalPerimeter());
    }

    @Test
    void rectangularPerimeterCorrect() {
        final ByteProcessor processor = new ByteProcessor(10, 8);
        fillBackground(processor);
        final List<Point> selected = new ArrayList<Point>();
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 5; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                selected.add(new Point(x, y));
            }
        }
        // 3x4 rectangle: perimeter = 2*(3+4)=14
        final EllipseModel ellipse = new EllipseModel(2.5, 2.5, 0.1, 0.1, 0.0);
        final RefittedEllipseFillPreviewResult result = analyzer.analyzeForTesting(
                processor, selected, ellipse, ellipse);
        assertEquals(14, result.getOriginalPerimeter());
    }

    @Test
    void previewPerimeterUsesCardinalExposure() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertTrue(result.getPreviewPerimeter() > 0);
        assertEquals(
                expectedPerimeter(result.getPreviewSelectedLumenPixels(),
                        result.getImageWidth(), result.getImageHeight()),
                result.getPreviewPerimeter());
    }

    @Test
    void originalCircularityFormulaCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        final double expected = 4.0 * Math.PI * result.getOriginalArea()
                / (result.getOriginalPerimeter() * (double) result.getOriginalPerimeter());
        assertEquals(clampCircularity(expected), result.getOriginalCircularity(), 1.0e-12);
    }

    @Test
    void previewCircularityFormulaCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        final double expected = 4.0 * Math.PI * result.getPreviewArea()
                / (result.getPreviewPerimeter() * (double) result.getPreviewPerimeter());
        assertEquals(clampCircularity(expected), result.getPreviewCircularity(), 1.0e-12);
    }

    @Test
    void circularityChangeIsPreviewMinusOriginal() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(result.getPreviewCircularity() - result.getOriginalCircularity(),
                result.getCircularityChange(), 1.0e-12);
    }

    @Test
    void addedAreaFractionCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(
                result.getRefittedProposedFillCount()
                        / (double) result.getSelectedObservedLumenArea(),
                result.getAddedAreaFraction(),
                1.0e-12);
    }

    @Test
    void ellipseAreaRatioCorrect() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(
                fixture.refitted.getArea() / (double) result.getSelectedObservedLumenArea(),
                result.getEllipseAreaToObservedLumenAreaRatio(),
                1.0e-12);
    }

    @Test
    void allPixelListsOrderedByYThenX() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertOrdered(result.getSelectedObservedLumenPixels());
        assertOrdered(result.getBaselineProposedFillPixels());
        assertOrdered(result.getRefittedProposedFillPixels());
        assertOrdered(result.getSharedProposedFillPixels());
        assertOrdered(result.getRefitOnlyProposedFillPixels());
        assertOrdered(result.getBaselineOnlyProposedFillPixels());
        assertOrdered(result.getPreviewSelectedLumenPixels());
    }

    @Test
    void allPixelListsImmutable() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertThrows(UnsupportedOperationException.class,
                () -> result.getPreviewSelectedLumenPixels().add(new Point(0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getRefittedProposedFillPixels().add(new Point(0, 0)));
    }

    @Test
    void returnedPointsAreDefensiveCopies() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        final Point p = result.getPreviewSelectedLumenPixels().get(0);
        final int ox = p.x;
        p.x = -99;
        assertEquals(ox, result.getPreviewSelectedLumenPixels().get(0).x);
    }

    @Test
    void inputSelectedListUnchanged() {
        final Fixture fixture = syntheticFixture();
        final List<Point> snapshot = copyPoints(fixture.selected);
        analyze(fixture);
        assertEquals(snapshot, fixture.selected);
    }

    @Test
    void inputPointObjectsUnchanged() {
        final Fixture fixture = syntheticFixture();
        final Point p = fixture.selected.get(0);
        final int ox = p.x;
        final int oy = p.y;
        analyze(fixture);
        assertEquals(ox, p.x);
        assertEquals(oy, p.y);
    }

    @Test
    void sourcePixelsRemainUnchanged() {
        final Fixture fixture = syntheticFixture();
        final byte[] before = copyBytes((byte[]) fixture.processor.getPixels());
        analyze(fixture);
        assertTrue(sameBytes(before, (byte[]) fixture.processor.getPixels()));
    }

    @Test
    void repeatedAnalysisDeterministic() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult a = analyze(fixture);
        final RefittedEllipseFillPreviewResult b = analyze(fixture);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void resultEqualityAndHashCodeDeterministic() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult a = analyze(fixture);
        final RefittedEllipseFillPreviewResult b = analyze(fixture);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void noEllipseFittingInPreviewAnalyzer() {
        final Fixture fixture = syntheticFixture();
        final EllipseModel baselineBefore = fixture.baseline;
        final EllipseModel refittedBefore = fixture.refitted;
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(baselineBefore, result.getBaselineEllipse());
        assertEquals(refittedBefore, result.getRefittedEllipse());
    }

    @Test
    void noIntervalDetectionInPreviewAnalyzer() {
        // Preview analyzer has no detector dependency; verifying result uses supplied ellipses.
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        assertEquals(fixture.refitted, result.getRefittedEllipse());
    }

    @Test
    void noAcceptanceDecision() {
        final Fixture fixture = syntheticFixture();
        final RefittedEllipseFillPreviewResult result = analyze(fixture);
        // Diagnostic only: overlap flag does not reject.
        assertTrue(result.isWouldOverlapAnotherLumenComponent());
    }

    @Test
    void noSourcePixelModification() {
        final Fixture fixture = syntheticFixture();
        final byte[] before = copyBytes((byte[]) fixture.processor.getPixels());
        analyze(fixture);
        assertTrue(sameBytes(before, (byte[]) fixture.processor.getPixels()));
    }

    private RefittedEllipseFillPreviewResult analyze(final Fixture fixture) {
        return analyzer.analyzeForTesting(
                fixture.processor, fixture.selected, fixture.baseline, fixture.refitted);
    }

    /**
     * Synthetic 20x16 image with different baseline/refit ellipses and all fill categories.
     */
    private static Fixture syntheticFixture() {
        final int width = 20;
        final int height = 16;
        final ByteProcessor processor = new ByteProcessor(width, height);
        fillBackground(processor);

        // Selected observed lumen: 3x3 block at (6..8, 6..8) plus one outside pixel at (2,2)
        final List<Point> selected = new ArrayList<Point>();
        for (int y = 6; y <= 8; y++) {
            for (int x = 6; x <= 8; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                selected.add(new Point(x, y));
            }
        }
        final Point selectedOutside = new Point(2, 2);
        processor.set(selectedOutside.x, selectedOutside.y, ClassifierLabels.LUMEN_RESULT_VALUE);
        selected.add(selectedOutside);

        // Separate other lumen component inside refitted ellipse
        final Point otherLumen = new Point(12, 7);
        processor.set(otherLumen.x, otherLumen.y, ClassifierLabels.LUMEN_RESULT_VALUE);

        // Fill candidates by class
        final Point woodFill = new Point(10, 7); // shared-ish near both
        final Point rayFill = new Point(9, 6);
        final Point backgroundFill = new Point(9, 8);
        final Point otherClassFill = new Point(10, 6);
        final Point sharedFill = woodFill;
        final Point refitOnlyFill = new Point(13, 7);
        final Point baselineOnlyFill = new Point(4, 7);
        final Point farAway = new Point(18, 14);

        processor.set(woodFill.x, woodFill.y, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(rayFill.x, rayFill.y, ClassifierLabels.RAY_RESULT_VALUE);
        processor.set(backgroundFill.x, backgroundFill.y,
                ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(otherClassFill.x, otherClassFill.y, 7);
        processor.set(refitOnlyFill.x, refitOnlyFill.y, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(baselineOnlyFill.x, baselineOnlyFill.y, ClassifierLabels.RAY_RESULT_VALUE);
        // farAway remains background

        // Baseline ellipse covers left+center (includes baselineOnly, shared; not refitOnly)
        final EllipseModel baseline = new EllipseModel(7.0, 7.0, 4.2, 3.0, 0.0);
        // Refitted ellipse shifted right (includes shared, refitOnly, otherLumen; not baselineOnly)
        final EllipseModel refitted = new EllipseModel(10.0, 7.0, 4.2, 3.0, 0.0);

        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(baseline, sharedFill.x,
                sharedFill.y));
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(refitted, sharedFill.x,
                sharedFill.y));
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(baseline,
                baselineOnlyFill.x, baselineOnlyFill.y));
        assertFalse(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(refitted,
                baselineOnlyFill.x, baselineOnlyFill.y));
        assertFalse(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(baseline,
                refitOnlyFill.x, refitOnlyFill.y));
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(refitted,
                refitOnlyFill.x, refitOnlyFill.y));
        assertFalse(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(refitted,
                selectedOutside.x, selectedOutside.y));
        assertTrue(RefittedEllipseFillPreviewAnalyzer.isInsideOrOn(refitted,
                otherLumen.x, otherLumen.y));

        final Fixture fixture = new Fixture();
        fixture.processor = processor;
        fixture.selected = selected;
        fixture.baseline = baseline;
        fixture.refitted = refitted;
        fixture.selectedOutside = selectedOutside;
        fixture.otherLumen = otherLumen;
        fixture.woodFill = woodFill;
        fixture.rayFill = rayFill;
        fixture.backgroundFill = backgroundFill;
        fixture.otherClassFill = otherClassFill;
        fixture.sharedFill = sharedFill;
        fixture.refitOnlyFill = refitOnlyFill;
        fixture.baselineOnlyFill = baselineOnlyFill;
        fixture.farAway = farAway;
        return fixture;
    }

    private static void fillBackground(final ByteProcessor processor) {
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                processor.set(x, y, ClassifierLabels.BACKGROUND_RESULT_VALUE);
            }
        }
    }

    private static void assertOrdered(final List<Point> points) {
        for (int i = 1; i < points.size(); i++) {
            final Point a = points.get(i - 1);
            final Point b = points.get(i);
            assertTrue(a.y < b.y || (a.y == b.y && a.x <= b.x));
        }
    }

    private static boolean contains(final List<Point> points, final Point needle) {
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            if (p.x == needle.x && p.y == needle.y) {
                return true;
            }
        }
        return false;
    }

    private static List<Point> copyPoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            copy.add(new Point(source.get(i).x, source.get(i).y));
        }
        return copy;
    }

    private static List<Point> listOf(final Point point) {
        final List<Point> list = new ArrayList<Point>();
        list.add(point);
        return list;
    }

    private static ByteProcessor copyProcessor(final ByteProcessor source) {
        final ByteProcessor copy = new ByteProcessor(source.getWidth(), source.getHeight());
        final byte[] src = (byte[]) source.getPixels();
        final byte[] dst = (byte[]) copy.getPixels();
        System.arraycopy(src, 0, dst, 0, src.length);
        return copy;
    }

    private static byte[] copyBytes(final byte[] source) {
        final byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static boolean sameBytes(final byte[] a, final byte[] b) {
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

    private static double clampCircularity(final double value) {
        if (value > 1.0 && value < 1.0 + 1.0e-9) {
            return 1.0;
        }
        return value;
    }

    private static int expectedPerimeter(
            final List<Point> members,
            final int width,
            final int height) {
        final Set<Long> keys = new HashSet<Long>();
        for (int i = 0; i < members.size(); i++) {
            final Point p = members.get(i);
            keys.add(Long.valueOf((((long) p.x) << 32) ^ (p.y & 0xffffffffL)));
        }
        final int[] dx = {0, 1, 0, -1};
        final int[] dy = {-1, 0, 1, 0};
        int perimeter = 0;
        for (final Long keyObj : keys) {
            final long key = keyObj.longValue();
            final int x = (int) (key >> 32);
            final int y = (int) key;
            for (int i = 0; i < dx.length; i++) {
                final int nx = x + dx[i];
                final int ny = y + dy[i];
                final long nkey = (((long) nx) << 32) ^ (ny & 0xffffffffL);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !keys.contains(Long.valueOf(nkey))) {
                    perimeter++;
                }
            }
        }
        return perimeter;
    }

    private static InitialEllipseAnalysisResult simpleAnalysis() {
        final ByteProcessor processor = new ByteProcessor(12, 12);
        fillBackground(processor);
        for (int y = 3; y <= 8; y++) {
            for (int x = 3; x <= 8; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
            }
        }
        return new InitialEllipseAnalyzer().analyze(processor);
    }

    private static ByteProcessor analysisProcessor(final InitialEllipseAnalysisResult analysis) {
        final ByteProcessor processor = new ByteProcessor(
                analysis.getImageWidth(), analysis.getImageHeight());
        fillBackground(processor);
        final List<Point> selected = analysis.getSelectedLumenPixels();
        for (int i = 0; i < selected.size(); i++) {
            final Point p = selected.get(i);
            processor.set(p.x, p.y, ClassifierLabels.LUMEN_RESULT_VALUE);
        }
        return processor;
    }

    private static SingleIntervalEllipseRefitResult simpleRefitResult() {
        final List<java.awt.geom.Point2D.Double> points =
                new ArrayList<java.awt.geom.Point2D.Double>();
        final EllipseModel ellipse = new EllipseModel(6, 6, 4, 3, 0.2);
        for (int i = 0; i < 48; i++) {
            final double t = 2.0 * Math.PI * i / 48.0;
            points.add(ellipse.pointAt(t));
        }
        final List<Integer> excluded = new ArrayList<Integer>();
        for (int i = 10; i <= 18; i++) {
            excluded.add(Integer.valueOf(i));
        }
        return new SingleIntervalEllipseRefitter()
                .refitSupportPointsForTesting(points, excluded);
    }

    private static final class Fixture {
        private ByteProcessor processor;
        private List<Point> selected;
        private EllipseModel baseline;
        private EllipseModel refitted;
        private Point selectedOutside;
        private Point otherLumen;
        private Point woodFill;
        private Point rayFill;
        private Point backgroundFill;
        private Point otherClassFill;
        private Point sharedFill;
        private Point refitOnlyFill;
        private Point baselineOnlyFill;
        private Point farAway;
    }
}
