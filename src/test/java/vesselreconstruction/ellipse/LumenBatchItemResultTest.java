package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenBatchItemResultTest {

    @Test
    void invalidComponentIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().componentId(0).build());
    }

    @Test
    void invalidAreaIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().sourceArea(0).build());
    }

    @Test
    void nullStatusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().status(null).build());
    }

    @Test
    void emptyReasonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().reason("").build());
    }

    @Test
    void candidateCountMatchesList() {
        final LumenBatchItemResult result = reconstructed(
                Arrays.asList(new Point(1, 2), new Point(0, 1)));
        assertEquals(2, result.getCandidatePixelCount());
        assertEquals(2, result.getCandidatePixels().size());
    }

    @Test
    void reconstructedRequiresNonEmptyCandidate() {
        assertThrows(IllegalArgumentException.class, () ->
                baseBuilder()
                        .status(LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT)
                        .reason("ready")
                        .candidates(Collections.<Point>emptyList())
                        .build());
    }

    @Test
    void nonReconstructedRequiresEmptyCandidateList() {
        assertThrows(IllegalArgumentException.class, () ->
                baseBuilder()
                        .status(LumenBatchItemResult.Status.NO_INTERVAL_SELECTED)
                        .reason("none")
                        .candidates(Collections.singletonList(new Point(1, 1)))
                        .build());
    }

    @Test
    void candidatePixelsSortedYThenX() {
        final LumenBatchItemResult result = reconstructed(
                Arrays.asList(new Point(3, 1), new Point(1, 0), new Point(2, 0)));
        final List<Point> pixels = result.getCandidatePixels();
        assertEquals(new Point(1, 0), pixels.get(0));
        assertEquals(new Point(2, 0), pixels.get(1));
        assertEquals(new Point(3, 1), pixels.get(2));
    }

    @Test
    void listsAreImmutable() {
        final LumenBatchItemResult result = reconstructed(
                Collections.singletonList(new Point(1, 1)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getCandidatePixels().add(new Point(2, 2)));
    }

    @Test
    void pointsAreDefensive() {
        final LumenBatchItemResult result = reconstructed(
                Collections.singletonList(new Point(1, 1)));
        result.getCandidatePixels().get(0).x = 9;
        assertEquals(1, result.getCandidatePixels().get(0).x);
    }

    @Test
    void rectangleIsDefensive() {
        final LumenBatchItemResult result = baseBuilder().build();
        result.getSourceBounds().x = 99;
        assertEquals(1, result.getSourceBounds().x);
    }

    @Test
    void nanIsPermittedForUnavailableDoubleMetrics() {
        final LumenBatchItemResult result = baseBuilder().build();
        assertTrue(Double.isNaN(result.getMinimumDepthSigma()));
        assertTrue(Double.isNaN(result.getOriginalCircularity()));
    }

    @Test
    void processingTimeCannotBeNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> baseBuilder().processingTimeNanoseconds(-1L).build());
    }

    @Test
    void equalityAndHashCodeAreDeterministic() {
        final LumenBatchItemResult a = reconstructed(
                Collections.singletonList(new Point(1, 1)));
        final LumenBatchItemResult b = reconstructed(
                Collections.singletonList(new Point(1, 1)));
        final LumenBatchItemResult c = reconstructed(
                Collections.singletonList(new Point(2, 2)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(a.isReconstructed());
        assertFalse(baseBuilder().build().isReconstructed());
    }

    private static LumenBatchItemResult reconstructed(final List<Point> candidates) {
        return baseBuilder()
                .status(LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT)
                .reason("ready")
                .candidates(candidates)
                .build();
    }

    private static Builder baseBuilder() {
        return new Builder();
    }

    private static final class Builder {
        private int componentId = 1;
        private int sourceArea = 10;
        private Rectangle sourceBounds = new Rectangle(1, 2, 3, 4);
        private boolean touchesImageBorder = false;
        private LumenBatchItemResult.Status status =
                LumenBatchItemResult.Status.BELOW_MINIMUM_AREA;
        private String reason = "below";
        private List<Point> candidates = Collections.emptyList();
        private long processingTimeNanoseconds = 1L;

        private Builder componentId(final int value) {
            this.componentId = value;
            return this;
        }

        private Builder sourceArea(final int value) {
            this.sourceArea = value;
            return this;
        }

        private Builder status(final LumenBatchItemResult.Status value) {
            this.status = value;
            return this;
        }

        private Builder reason(final String value) {
            this.reason = value;
            return this;
        }

        private Builder candidates(final List<Point> value) {
            this.candidates = value;
            return this;
        }

        private Builder processingTimeNanoseconds(final long value) {
            this.processingTimeNanoseconds = value;
            return this;
        }

        private LumenBatchItemResult build() {
            return new LumenBatchItemResult(
                    componentId,
                    sourceArea,
                    sourceBounds,
                    touchesImageBorder,
                    status,
                    reason,
                    candidates,
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
                    processingTimeNanoseconds);
        }
    }
}
