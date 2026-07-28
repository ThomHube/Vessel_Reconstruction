package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic JUnit 5 tests for {@link DiagnosticReconstructionBuilder} and
 * {@link DiagnosticReconstructionCandidate}.
 *
 * <p>Coherent, geometrically consistent {@link ProposedFillComponentAnalysisResult}
 * instances are built via {@link ProposedFillComponentAnalyzer#analyzeForTesting}
 * on small synthetic images, following the same pattern used by
 * {@code ProposedFillComponentAnalyzerTest}. A small number of structural
 * edge cases that the real analyzer can never produce (an other-Lumen
 * overlap, a primary component that disagrees with its own reported
 * associated-fill pixel set, or zero matched exterior seeds despite a single
 * associated component) are built by hand using the same production
 * constructors, following the pattern used by
 * {@code ArtifactEvidenceAssessorTest}.</p>
 */
class DiagnosticReconstructionBuilderTest {

    private final DiagnosticReconstructionBuilder builder = new DiagnosticReconstructionBuilder();
    private final ProposedFillComponentAnalyzer analyzer = new ProposedFillComponentAnalyzer();
    private final OrderedBoundaryExtractor boundaryExtractor = new OrderedBoundaryExtractor();
    private final InwardOutlierIntervalDetector detector = new InwardOutlierIntervalDetector();
    private final ArtifactEvidenceAssessor assessor = new ArtifactEvidenceAssessor();

    private static final double ORIG_CIRC = 0.5;
    private static final double WHOLE_CIRC = 0.6;
    private static final double WHOLE_CHANGE = 0.1;

    // ------------------------------------------------------------------
    // 1-4: evaluate() argument / dimension validation
    // ------------------------------------------------------------------

    @Test
    void nullProcessorIsRejected() {
        final ReadyFixture ready = readyFixture();
        assertThrows(IllegalArgumentException.class,
                () -> builder.evaluate(null, ready.evidence, ready.componentResult));
    }

    @Test
    void nullEvidenceAssessmentIsRejected() {
        final ReadyFixture ready = readyFixture();
        assertThrows(IllegalArgumentException.class,
                () -> builder.evaluate(ready.base.processor, null, ready.componentResult));
    }

    @Test
    void nullComponentResultIsRejected() {
        final ReadyFixture ready = readyFixture();
        assertThrows(IllegalArgumentException.class,
                () -> builder.evaluate(ready.base.processor, ready.evidence, null));
    }

    @Test
    void mismatchedDimensionsAreRejected() {
        final ReadyFixture ready = readyFixture();
        final ByteProcessor other = filled(
                ready.base.processor.getWidth() + 1,
                ready.base.processor.getHeight(),
                ClassifierLabels.BACKGROUND_RESULT_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> builder.evaluate(other, ready.evidence, ready.componentResult));
    }

    // ------------------------------------------------------------------
    // 5-7: insufficient evidence short-circuit
    // ------------------------------------------------------------------

    @Test
    void insufficientEvidenceReturnsInsufficientArtifactEvidenceDecision() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, weakEvidence(), ready.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void insufficientEvidenceReturnsEmptyCandidatePixelList() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, weakEvidence(), ready.componentResult);
        assertTrue(candidate.getPixelsToChange().isEmpty());
        assertEquals(0, candidate.getPixelCount());
    }

    @Test
    void insufficientEvidenceCandidateCannotCreateReconstructedCopyOrMask() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, weakEvidence(), ready.componentResult);
        assertThrows(IllegalArgumentException.class,
                () -> builder.createDiagnosticCopy(ready.base.processor, candidate));
        assertThrows(IllegalArgumentException.class,
                () -> builder.createChangeMask(candidate));
    }

    // ------------------------------------------------------------------
    // 8-14: structural association requirements
    // ------------------------------------------------------------------

    @Test
    void exactlyOneAssociatedComponentIsRequired() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(8, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 3));
        assertEquals(2, result.getIntervalAssociatedComponentCount());
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void primaryComponentMustExist() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        // Interval selects far side; fill is not associated with it.
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(8, 11));
        assertNull(result.getPrimaryIntervalAssociatedComponent());
        assertEquals(0, result.getIntervalAssociatedComponentCount());
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
    }

    @Test
    void matchedSeedsMustBePositive() {
        final ManualScenario scenario = buildManualScenario(
                0, 0, 0,
                points(p(10, 10), p(10, 11)),
                points(p(10, 10), p(10, 11)));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(scenario.processor, strongEvidence(), scenario.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void unmatchedSeedsPreventReadiness() {
        final ManualScenario scenario = buildManualScenario(
                2, 1, 0,
                points(p(10, 10), p(10, 11)),
                points(p(10, 10), p(10, 11)));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(scenario.processor, strongEvidence(), scenario.componentResult);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
    }

    @Test
    void otherLumenOverlapReturnsOtherLumenOverlapDecision() {
        final ManualScenario scenario = buildManualScenario(
                2, 0, 3,
                points(p(10, 10), p(10, 11)),
                points(p(10, 10), p(10, 11)));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(scenario.processor, strongEvidence(), scenario.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.OTHER_LUMEN_OVERLAP,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void primaryComponentPixelsMustEqualAssociatedFillPixels() {
        final ManualScenario scenario = buildManualScenario(
                2, 0, 0,
                points(p(10, 10), p(10, 11)),
                points(p(10, 10), p(12, 10)));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(scenario.processor, strongEvidence(), scenario.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void candidateAreaMustEqualPrimaryComponentArea() {
        final ManualScenario scenario = buildManualScenario(
                2, 0, 0,
                points(p(10, 10), p(10, 11)),
                points(p(10, 10), p(10, 11), p(10, 12)));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(scenario.processor, strongEvidence(), scenario.componentResult);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.INVALID_STRUCTURAL_ASSOCIATION,
                candidate.getDecision());
    }

    // ------------------------------------------------------------------
    // 15-19: original raw-class validation
    // ------------------------------------------------------------------

    @Test
    void rawWoodCandidatePixelsAreAccepted() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                candidate.getDecision());
        assertTrue(candidate.isReadyForDiagnosticCopy());
    }

    @Test
    void rawRayCandidatePixelPreventsReadiness() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.RAY_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 1));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS,
                candidate.getDecision());
    }

    @Test
    void rawLumenCandidatePixelPreventsReadiness() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 1));
        // Simulate the source having changed to raw Lumen after analysis.
        base.processor.set(5, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.SOURCE_PIXEL_VALUE_MISMATCH,
                candidate.getDecision());
    }

    @Test
    void rawBackgroundCandidatePixelPreventsReadiness() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 1));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS,
                candidate.getDecision());
    }

    @Test
    void unknownCandidateValuePreventsReadiness() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, 7);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 1));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS,
                candidate.getDecision());
    }

    // ------------------------------------------------------------------
    // 20-24: DiagnosticReconstructionCandidate invariants
    // ------------------------------------------------------------------

    @Test
    void candidateCoordinatesMustBeInBounds() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason",
                true,
                points(p(-1, 0)),
                1, 1, 0, 0, 0, 0,
                10, 10,
                1, 1, true,
                5, 5, 8, 0.5, 0.0, 0.2, 1, 1));
    }

    @Test
    void candidatePixelsAreSortedYThenX() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(6, 4), p(5, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result =
                analyzeComponent(base, fill, range(0, 1));
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(base.processor, strongEvidence(), result);
        assertEquals(points(p(5, 4), p(6, 4)), candidate.getPixelsToChange());
    }

    @Test
    void candidatePixelListIsImmutable() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final List<Point> pixels = candidate.getPixelsToChange();
        assertThrows(UnsupportedOperationException.class,
                () -> pixels.add(new Point(0, 0)));
    }

    @Test
    void returnedCandidatePointsCannotMutateInternalState() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final Point point = candidate.getPixelsToChange().get(0);
        final int originalX = point.x;
        point.x = -777;
        assertEquals(originalX, candidate.getPixelsToChange().get(0).x);
    }

    @Test
    void originalClassCountsAreExact() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertEquals(candidate.getPixelCount(), candidate.getOriginalWoodPixelCount());
        assertEquals(0, candidate.getOriginalRayPixelCount());
        assertEquals(0, candidate.getOriginalLumenPixelCount());
        assertEquals(0, candidate.getOriginalBackgroundPixelCount());
        assertEquals(0, candidate.getOriginalOtherPixelCount());
    }

    // ------------------------------------------------------------------
    // 25-26: checksum determinism
    // ------------------------------------------------------------------

    @Test
    void sourceChecksumIsDeterministic() {
        final ReadyFixture ready = readyFixture();
        final int expected = referenceChecksum((byte[]) ready.base.processor.getPixels());
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertEquals(expected, candidate.getSourceChecksumBeforeEvaluation());
        assertEquals(expected, candidate.getSourceChecksumAfterEvaluation());
    }

    @Test
    void sourceChecksumIsUnchangedDuringEvaluation() {
        final ReadyFixture ready = readyFixture();
        final byte[] before = copyBytes((byte[]) ready.base.processor.getPixels());
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertTrue(candidate.isSourceUnchangedDuringEvaluation());
        assertTrue(sameBytes(before, (byte[]) ready.base.processor.getPixels()));
    }

    // ------------------------------------------------------------------
    // 27-31: reconstructed-shape diagnostics
    // ------------------------------------------------------------------

    @Test
    void selectedLumenUnionCandidateAreaIsCorrect() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertEquals(ready.base.lumen.size() + candidate.getPixelCount(),
                candidate.getDiagnosticReconstructedArea());
    }

    @Test
    void perimeterUsesCardinalExposedEdges() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final Set<Long> unionKeys = keySet(ready.base.lumen);
        unionKeys.addAll(keySet(candidate.getPixelsToChange()));
        final int expectedPerimeter = exposedPerimeter(unionKeys, 20, 20);
        assertEquals(expectedPerimeter, candidate.getDiagnosticReconstructedPerimeter());
    }

    @Test
    void circularityFormulaIsCorrect() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final double expected = 4.0 * Math.PI * candidate.getDiagnosticReconstructedArea()
                / ((double) candidate.getDiagnosticReconstructedPerimeter()
                        * candidate.getDiagnosticReconstructedPerimeter());
        assertEquals(expected, candidate.getDiagnosticReconstructedCircularity(), 1.0e-12);
        assertEquals(
                candidate.getDiagnosticReconstructedCircularity() - ORIG_CIRC,
                candidate.getDiagnosticCircularityChange(),
                1.0e-12);
    }

    @Test
    void circularityDoesNotAffectReadiness() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult lowCircularity = analyzer.analyzeForTesting(
                base.processor, base.lumen, fill, base.loop, range(0, 1), 0.01, WHOLE_CIRC,
                WHOLE_CHANGE);
        final ProposedFillComponentAnalysisResult highCircularity = analyzer.analyzeForTesting(
                base.processor, base.lumen, fill, base.loop, range(0, 1), 0.99, WHOLE_CIRC,
                WHOLE_CHANGE);
        final DiagnosticReconstructionCandidate a =
                builder.evaluate(base.processor, strongEvidence(), lowCircularity);
        final DiagnosticReconstructionCandidate b =
                builder.evaluate(base.processor, strongEvidence(), highCircularity);
        assertTrue(a.isReadyForDiagnosticCopy());
        assertTrue(b.isReadyForDiagnosticCopy());
        assertNotEquals(a.getDiagnosticCircularityChange(), b.getDiagnosticCircularityChange());
    }

    @Test
    void addedAreaFractionIsCorrect() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final double expected = candidate.getPixelCount()
                / (double) candidate.getOriginalSelectedLumenArea();
        assertEquals(expected, candidate.getAddedAreaFraction(), 1.0e-12);
    }

    // ------------------------------------------------------------------
    // 32-40: createDiagnosticCopy
    // ------------------------------------------------------------------

    @Test
    void createDiagnosticCopyReturnsADifferentProcessorInstance() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        assertNotSame(ready.base.processor, copy);
        assertNotSame(ready.base.processor.getPixels(), copy.getPixels());
    }

    @Test
    void createDiagnosticCopyPreservesDimensions() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        assertEquals(ready.base.processor.getWidth(), copy.getWidth());
        assertEquals(ready.base.processor.getHeight(), copy.getHeight());
    }

    @Test
    void createDiagnosticCopyPreservesAllNonCandidateValues() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        final Set<Long> changed = keySet(candidate.getPixelsToChange());
        final int width = ready.base.processor.getWidth();
        final int height = ready.base.processor.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!changed.contains(Long.valueOf(pack(x, y)))) {
                    assertEquals(ready.base.processor.get(x, y), copy.get(x, y));
                }
            }
        }
    }

    @Test
    void exactlyCandidateCountPixelsAreChanged() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        final byte[] sourceBytes = (byte[]) ready.base.processor.getPixels();
        final byte[] copyBytes = (byte[]) copy.getPixels();
        int changed = 0;
        for (int i = 0; i < sourceBytes.length; i++) {
            if (sourceBytes[i] != copyBytes[i]) {
                changed++;
            }
        }
        assertEquals(candidate.getPixelCount(), changed);
    }

    @Test
    void everyChangedOutputPixelBecomesRawLumenValueTwo() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        for (final Point point : candidate.getPixelsToChange()) {
            assertEquals(ClassifierLabels.LUMEN_RESULT_VALUE, copy.get(point.x, point.y));
        }
    }

    @Test
    void everyCandidateSourcePixelMustStillBeRawWoodValueZero() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final Point first = candidate.getPixelsToChange().get(0);
        ready.base.processor.set(first.x, first.y, ClassifierLabels.RAY_RESULT_VALUE);
        assertThrows(IllegalStateException.class,
                () -> builder.createDiagnosticCopy(ready.base.processor, candidate));
    }

    @Test
    void sourceProcessorRemainsByteForByteUnchangedAfterCopy() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final byte[] before = copyBytes((byte[]) ready.base.processor.getPixels());
        builder.createDiagnosticCopy(ready.base.processor, candidate);
        assertTrue(sameBytes(before, (byte[]) ready.base.processor.getPixels()));
    }

    @Test
    void repeatedCopyCreationIsDeterministic() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor copyA = builder.createDiagnosticCopy(ready.base.processor, candidate);
        final ByteProcessor copyB = builder.createDiagnosticCopy(ready.base.processor, candidate);
        assertTrue(sameBytes((byte[]) copyA.getPixels(), (byte[]) copyB.getPixels()));
    }

    @Test
    void changingReturnedCopyCannotAffectSource() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final byte[] before = copyBytes((byte[]) ready.base.processor.getPixels());
        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        copy.set(0, 0, 99);
        assertTrue(sameBytes(before, (byte[]) ready.base.processor.getPixels()));
    }

    // ------------------------------------------------------------------
    // 41-43: createChangeMask
    // ------------------------------------------------------------------

    @Test
    void changeMaskContains255OnlyAtCandidatePixels() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor mask = builder.createChangeMask(candidate);
        final Set<Long> changed = keySet(candidate.getPixelsToChange());
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                final boolean isCandidate = changed.contains(Long.valueOf(pack(x, y)));
                assertEquals(isCandidate ? 255 : 0, mask.get(x, y));
            }
        }
    }

    @Test
    void changeMaskForegroundCountEqualsCandidateCount() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor mask = builder.createChangeMask(candidate);
        int count = 0;
        final byte[] maskPixels = (byte[]) mask.getPixels();
        for (final byte value : maskPixels) {
            if ((value & 0xff) == 255) {
                count++;
            }
        }
        assertEquals(candidate.getPixelCount(), count);
    }

    @Test
    void changingReturnedMaskCannotAffectCandidateOrFutureMasks() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        final ByteProcessor maskA = builder.createChangeMask(candidate);
        maskA.set(0, 0, 42);
        final int pixelCountBefore = candidate.getPixelCount();
        final ByteProcessor maskB = builder.createChangeMask(candidate);
        assertEquals(pixelCountBefore, candidate.getPixelCount());
        assertEquals(0, maskB.get(0, 0));
    }

    // ------------------------------------------------------------------
    // 44-49: no side effects, no re-derivation of upstream diagnostics
    // ------------------------------------------------------------------

    @Test
    void noFileIsWrittenByBuilder() {
        for (final Method method : DiagnosticReconstructionBuilder.class.getDeclaredMethods()) {
            final String name = method.getName().toLowerCase();
            assertFalse(name.contains("save"));
            assertFalse(name.contains("write"));
            for (final Class<?> paramType : method.getParameterTypes()) {
                assertFalse(paramType.getName().contains("java.io.File"));
            }
        }
    }

    @Test
    void noImagePlusWindowIsRequiredByBuilder() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertNotNull(builder.createDiagnosticCopy(ready.base.processor, candidate));
        assertNotNull(builder.createChangeMask(candidate));
        for (final Method method : DiagnosticReconstructionBuilder.class.getDeclaredMethods()) {
            for (final Class<?> paramType : method.getParameterTypes()) {
                assertFalse(paramType.getName().contains("ImagePlus"));
            }
        }
    }

    @Test
    void noEllipseFittingOrIntervalDetectionOrComponentExtractionOccursInBuilder() {
        for (final Method method : DiagnosticReconstructionBuilder.class.getDeclaredMethods()) {
            final String name = method.getName().toLowerCase();
            assertFalse(name.contains("fit"));
            assertFalse(name.contains("detectinterval"));
            assertFalse(name.contains("extractcomponent"));
        }
    }

    @Test
    void noEvidenceThresholdIsRecalculatedInBuilder() {
        for (final Method method : DiagnosticReconstructionBuilder.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("threshold"));
        }
        for (final java.lang.reflect.Field field :
                DiagnosticReconstructionBuilder.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("threshold"));
        }
    }

    // ------------------------------------------------------------------
    // Exact regression examples
    // ------------------------------------------------------------------

    @Test
    void positiveExample2RegressionProducesReadyDecisionAndChangesOnlyPrimaryPixels() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, ready.evidence, ready.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                candidate.getDecision());
        assertTrue(candidate.isReadyForDiagnosticCopy());
        assertEquals(ready.fill.size(), candidate.getPixelCount());

        final ByteProcessor copy = builder.createDiagnosticCopy(ready.base.processor, candidate);
        for (final Point point : ready.fill) {
            assertEquals(ClassifierLabels.WOOD_RESULT_VALUE,
                    ready.base.processor.get(point.x, point.y));
            assertEquals(ClassifierLabels.LUMEN_RESULT_VALUE, copy.get(point.x, point.y));
        }
    }

    @Test
    void cleanExample7RegressionProducesInsufficientArtifactEvidenceAndNoReconstructedCopy() {
        final ReadyFixture ready = readyFixture();
        final DiagnosticReconstructionCandidate candidate =
                builder.evaluate(ready.base.processor, weakEvidence(), ready.componentResult);
        assertEquals(DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE,
                candidate.getDecision());
        assertFalse(candidate.isReadyForDiagnosticCopy());
        assertEquals(0, candidate.getPixelCount());
        assertThrows(IllegalArgumentException.class,
                () -> builder.createDiagnosticCopy(ready.base.processor, candidate));
        assertThrows(IllegalArgumentException.class,
                () -> builder.createChangeMask(candidate));
    }

    // ------------------------------------------------------------------
    // DiagnosticReconstructionCandidate direct construction validation
    // ------------------------------------------------------------------

    @Test
    void nullDecisionIsRejectedByCandidateConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                null, "reason", false, Collections.<Point>emptyList(),
                0, 0, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 0, 0, 0.0, 0.0, 0.0, 0, 0));
    }

    @Test
    void emptyDecisionReasonIsRejectedByCandidateConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE,
                "", false, Collections.<Point>emptyList(),
                0, 0, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 0, 0, 0.0, 0.0, 0.0, 0, 0));
    }

    @Test
    void nonPositiveSourceDimensionsAreRejectedByCandidateConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.INSUFFICIENT_ARTIFACT_EVIDENCE,
                "reason", false, Collections.<Point>emptyList(),
                0, 0, 0, 0, 0, 0, 0, 10, 1, 1, true, 5, 0, 0, 0.0, 0.0, 0.0, 0, 0));
    }

    @Test
    void readyDecisionRequiresPositivePixelCount() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, Collections.<Point>emptyList(),
                0, 0, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 0, 0, 0.0, 0.0, 0.0, 1, 0));
    }

    @Test
    void readyDecisionRequiresAllCandidatePixelsWood() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 0, 1, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, 0.2, 1, 1));
    }

    @Test
    void readyDecisionRequiresSourceUnchangedDuringEvaluation() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 2, false, 5, 1, 4, 0.5, 0.0, 0.2, 1, 1));
    }

    @Test
    void readyDecisionRequiresPrimaryComponentAreaEqualToPixelCount() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, 0.2, 1, 2));
    }

    @Test
    void nonReadyDecisionRequiresEmptyPixelList() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.NON_WOOD_CANDIDATE_PIXELS,
                "reason", false, points(p(1, 1)),
                1, 0, 1, 0, 0, 0, 10, 10, 1, 1, true, 5, 0, 0, 0.0, 0.0, 0.0, 0, 0));
    }

    @Test
    void readyFlagMustMatchDecision() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", false, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, 0.2, 1, 1));
    }

    @Test
    void classCountsMustSumToPixelCount() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 1, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, 0.2, 1, 1));
    }

    @Test
    void nonFiniteCircularityIsRejectedByCandidateConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, Double.NaN, 0.0, 0.2, 1, 1));
    }

    @Test
    void negativeAddedAreaFractionIsRejectedByCandidateConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, -0.1, 1, 1));
    }

    @Test
    void candidateEqualsAndHashCodeConsistentForIdenticalConstruction() {
        final DiagnosticReconstructionCandidate a = readyCandidate();
        final DiagnosticReconstructionCandidate b = readyCandidate();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void candidateToStringContainsDecisionAndPixelCount() {
        final DiagnosticReconstructionCandidate candidate = readyCandidate();
        final String text = candidate.toString();
        assertTrue(text.contains("READY_FOR_DIAGNOSTIC_COPY"));
        assertTrue(text.contains("pixelCount=1"));
    }

    private static DiagnosticReconstructionCandidate readyCandidate() {
        return new DiagnosticReconstructionCandidate(
                DiagnosticReconstructionCandidate.Decision.READY_FOR_DIAGNOSTIC_COPY,
                "reason", true, points(p(1, 1)),
                1, 1, 0, 0, 0, 0, 10, 10, 1, 1, true, 5, 1, 4, 0.5, 0.0, 0.2, 1, 1);
    }

    // ------------------------------------------------------------------
    // Helpers: evidence assessments
    // ------------------------------------------------------------------

    /** Mirrors the exact positive artifact example 2 metrics. */
    private ArtifactEvidenceAssessment strongEvidence() {
        return assessor.assessMetricsForTesting(
                8.2, 5.1, 0.42, 2.35, 0.38,
                40, 17, 1, 12, 0, 0, 0.55, 0.12);
    }

    /** Mirrors the exact clean example 7 metrics: all five residual criteria fail. */
    private ArtifactEvidenceAssessment weakEvidence() {
        return assessor.assessMetricsForTesting(
                1.0, 0.5, 0.05, 0.30, 0.05,
                40, 3, 1, 5, 0, 0, 0.10, 0.02);
    }

    // ------------------------------------------------------------------
    // Helpers: coherent fixtures via the real analyzer
    // ------------------------------------------------------------------

    private static final class ReadyFixture {
        private final BaseFixture base;
        private final List<Point> fill;
        private final ProposedFillComponentAnalysisResult componentResult;
        private final ArtifactEvidenceAssessment evidence;

        private ReadyFixture(
                final BaseFixture base,
                final List<Point> fill,
                final ProposedFillComponentAnalysisResult componentResult,
                final ArtifactEvidenceAssessment evidence) {
            this.base = base;
            this.fill = fill;
            this.componentResult = componentResult;
            this.evidence = evidence;
        }
    }

    private ReadyFixture readyFixture() {
        final BaseFixture base = baseLumenFixture();
        final List<Point> fill = points(p(5, 4), p(6, 4));
        paintFill(base.processor, fill, ClassifierLabels.WOOD_RESULT_VALUE);
        final ProposedFillComponentAnalysisResult result = analyzeComponent(base, fill, range(0, 1));
        return new ReadyFixture(base, fill, result, strongEvidence());
    }

    private ProposedFillComponentAnalysisResult analyzeComponent(
            final BaseFixture base, final List<Point> fill, final List<Integer> intervalIndices) {
        return analyzer.analyzeForTesting(
                base.processor, base.lumen, fill, base.loop, intervalIndices,
                ORIG_CIRC, WHOLE_CIRC, WHOLE_CHANGE);
    }

    private BaseFixture baseLumenFixture() {
        final List<Point> lumen = rect(5, 5, 4, 4);
        final ByteProcessor processor = filled(20, 20, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumen, ClassifierLabels.LUMEN_RESULT_VALUE);
        final OrderedBoundaryLoop loop = boundaryExtractor.selectOuterLoop(lumen);
        return new BaseFixture(processor, lumen, loop);
    }

    private static final class BaseFixture {
        private final ByteProcessor processor;
        private final List<Point> lumen;
        private final OrderedBoundaryLoop loop;

        private BaseFixture(
                final ByteProcessor processor, final List<Point> lumen,
                final OrderedBoundaryLoop loop) {
            this.processor = processor;
            this.lumen = lumen;
            this.loop = loop;
        }
    }

    // ------------------------------------------------------------------
    // Helpers: hand-built structural edge cases (impossible via the real
    // analyzer, which always keeps seeds/pixels/areas mutually consistent).
    // ------------------------------------------------------------------

    private static final class ManualScenario {
        private final ByteProcessor processor;
        private final ProposedFillComponentAnalysisResult componentResult;

        private ManualScenario(
                final ByteProcessor processor,
                final ProposedFillComponentAnalysisResult componentResult) {
            this.processor = processor;
            this.componentResult = componentResult;
        }
    }

    private ManualScenario buildManualScenario(
            final int matchedSeeds,
            final int unmatchedSeeds,
            final int otherLumenOverlapCount,
            final List<Point> primaryPixels,
            final List<Point> associatedFillPixelsOverride) {
        final int width = 20;
        final int height = 20;
        final List<Point> lumenPixels = rect(0, 0, 3, 3);
        final ByteProcessor processor = filled(width, height, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        paint(processor, lumenPixels, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<Point> sortedPrimaryPixels = sortedCopy(primaryPixels);
        paint(processor, sortedPrimaryPixels, ClassifierLabels.WOOD_RESULT_VALUE);

        final InwardOutlierDetectionResult detection =
                detector.detectResidualsForTesting(valleySequence());
        final int intervalEdgeCount = detection.getSelectedInterval().getEdgeCount();

        final List<Point> associatedFillPixels = sortedCopy(associatedFillPixelsOverride);
        final int associatedFillCount = associatedFillPixels.size();
        final double fillFraction = associatedFillCount == 0 ? 0.0 : 1.0;

        final EllipseModel ellipse = new EllipseModel(1.0, 1.0, 2.0, 2.0, 0.0);
        final List<Point> previewLumenPixels = unionSorted(lumenPixels, associatedFillPixels);
        final List<Point> otherLumenPixels = farPoints(otherLumenOverlapCount);
        final List<Point> exteriorSeedPlaceholders = farPoints(matchedSeeds + unmatchedSeeds, 200);

        final int originalPerimeter = exposedPerimeter(keySet(lumenPixels), width, height);
        final int previewPerimeter = exposedPerimeter(keySet(previewLumenPixels), width, height);
        final double originalCircularity = circularity(lumenPixels.size(), originalPerimeter);
        final double previewCircularity = circularity(previewLumenPixels.size(), previewPerimeter);
        final double circularityChange = previewCircularity - originalCircularity;
        final double addedAreaFraction = associatedFillCount / (double) lumenPixels.size();
        final double ellipseAreaRatio = ellipse.getArea() / (double) lumenPixels.size();

        final RefittedEllipseFillPreviewResult wholePreview = new RefittedEllipseFillPreviewResult(
                width, height, ellipse, ellipse,
                lumenPixels,
                associatedFillPixels, associatedFillPixels, associatedFillPixels,
                Collections.<Point>emptyList(), Collections.<Point>emptyList(),
                associatedFillPixels,
                Collections.<Point>emptyList(), Collections.<Point>emptyList(),
                Collections.<Point>emptyList(),
                Collections.<Point>emptyList(),
                otherLumenPixels,
                previewLumenPixels,
                lumenPixels.size(),
                previewLumenPixels.size(), previewLumenPixels.size(),
                associatedFillCount, associatedFillCount, associatedFillCount, 0, 0,
                associatedFillCount, 0, 0, 0,
                0, otherLumenPixels.size(),
                lumenPixels.size(), originalPerimeter, originalCircularity,
                previewLumenPixels.size(), previewPerimeter, previewCircularity, circularityChange,
                addedAreaFraction, ellipseAreaRatio, !otherLumenPixels.isEmpty());

        final Rectangle bounds = boundsOf(sortedPrimaryPixels);
        // The component's own seed count only needs to be positive so that
        // it legally qualifies as "interval-associated" for list membership;
        // the builder's readiness check inspects the aggregate matched /
        // unmatched exterior seed counts on the analysis result itself,
        // which are independently controlled by this scenario's parameters.
        final ProposedFillComponent primary = new ProposedFillComponent(
                1, sortedPrimaryPixels, sortedPrimaryPixels.size(), bounds,
                0, 0,
                1,
                1,
                true,
                sortedPrimaryPixels.size(), 0, 0, 0);
        final List<ProposedFillComponent> allComponents = Collections.singletonList(primary);
        final List<ProposedFillComponent> associatedComponents = Collections.singletonList(primary);

        final ProposedFillComponentAnalysisResult componentResult =
                new ProposedFillComponentAnalysisResult(
                        wholePreview,
                        detection,
                        exteriorSeedPlaceholders,
                        intervalEdgeCount,
                        matchedSeeds + unmatchedSeeds,
                        matchedSeeds,
                        unmatchedSeeds,
                        allComponents,
                        associatedComponents,
                        Collections.<ProposedFillComponent>emptyList(),
                        primary,
                        allComponents.size(),
                        1,
                        0,
                        associatedFillCount,
                        associatedFillCount,
                        0,
                        fillFraction,
                        fillFraction,
                        fillFraction,
                        associatedFillPixels,
                        Collections.<Point>emptyList(),
                        previewLumenPixels,
                        previewLumenPixels.size(),
                        previewPerimeter,
                        previewCircularity,
                        circularityChange,
                        wholePreview.getPreviewCircularity(),
                        wholePreview.getCircularityChange(),
                        true,
                        true);

        return new ManualScenario(processor, componentResult);
    }

    /** Mirrors {@code ArtifactEvidenceAssessorTest.valleySequence()}. */
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

    private static List<Double> constantList(final int n, final double value) {
        final List<Double> list = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            list.add(Double.valueOf(value));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Generic geometry / collection helpers
    // ------------------------------------------------------------------

    private static List<Integer> range(final int startInclusive, final int endInclusive) {
        final List<Integer> list = new ArrayList<Integer>(endInclusive - startInclusive + 1);
        for (int i = startInclusive; i <= endInclusive; i++) {
            list.add(Integer.valueOf(i));
        }
        return list;
    }

    private static Point p(final int x, final int y) {
        return new Point(x, y);
    }

    private static List<Point> points(final Point... pts) {
        final List<Point> list = new ArrayList<Point>(pts.length);
        for (int i = 0; i < pts.length; i++) {
            list.add(new Point(pts[i].x, pts[i].y));
        }
        return list;
    }

    private static List<Point> farPoints(final int count) {
        return farPoints(count, 100);
    }

    private static List<Point> farPoints(final int count, final int startX) {
        final List<Point> list = new ArrayList<Point>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Point(startX + i, 15));
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

    private static void paint(
            final ByteProcessor processor, final List<Point> pixels, final int value) {
        for (int i = 0; i < pixels.size(); i++) {
            final Point point = pixels.get(i);
            processor.set(point.x, point.y, value);
        }
    }

    private static void paintFill(
            final ByteProcessor processor, final List<Point> fill, final int value) {
        paint(processor, fill, value);
    }

    private static List<Point> sortedCopy(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            copy.add(new Point(point.x, point.y));
        }
        Collections.sort(copy, new Comparator<Point>() {
            @Override
            public int compare(final Point a, final Point b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
        return copy;
    }

    private static List<Point> unionSorted(final List<Point> a, final List<Point> b) {
        final Set<Long> union = keySet(a);
        union.addAll(keySet(b));
        final List<Point> points = new ArrayList<Point>(union.size());
        for (final Long keyObj : union) {
            points.add(unpack(keyObj.longValue()));
        }
        return sortedCopy(points);
    }

    private static Rectangle boundsOf(final List<Point> points) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Set<Long> keySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            keys.add(Long.valueOf(pack(point.x, point.y)));
        }
        return keys;
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

    private static double circularity(final int area, final int perimeter) {
        final double value = 4.0 * Math.PI * area / (perimeter * (double) perimeter);
        if (value > 1.0 && value < 1.0 + 1.0e-9) {
            return 1.0;
        }
        return value;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Point unpack(final long key) {
        return new Point((int) (key >> 32), (int) key);
    }

    private static int referenceChecksum(final byte[] pixels) {
        int result = 1;
        for (int i = 0; i < pixels.length; i++) {
            result = 31 * result + (pixels[i] & 0xff);
        }
        return result;
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
}
