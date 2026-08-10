package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMaskAcceptancePolicyTest {

    private static final int SOURCE_W = 800;
    private static final int SOURCE_H = 600;
    private static final int FACTOR = 8;

    @Test
    void acceptsValidSyntheticMask() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final Fixture fixture = validFixture(0.40, 0L);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, fixture.crop, SOURCE_W, SOURCE_H, config);
        assertTrue(decision.accepted);
        assertEquals(RCsvSupport.NA, decision.reasonCode);
        assertTrue(decision.majorSpanFraction >= config.maskMinimumMajorSpanFraction());
    }

    @Test
    void rejectsIncludedTooSmall() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final Fixture fixture = statsOverrideFixture(0.01, 80, 60, 0L);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, fixture.crop, SOURCE_W, SOURCE_H, config);
        assertFalse(decision.accepted);
        assertEquals("MASK_INCLUDED_FRACTION_TOO_SMALL", decision.reasonCode);
    }

    @Test
    void rejectsIncludedTooLarge() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final Fixture fixture = statsOverrideFixture(0.90, 80, 60, 0L);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, fixture.crop, SOURCE_W, SOURCE_H, config);
        assertFalse(decision.accepted);
        assertEquals("MASK_INCLUDED_FRACTION_TOO_LARGE", decision.reasonCode);
    }

    @Test
    void rejectsMajorSpanTooSmall() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final Fixture fixture = statsOverrideFixture(0.40, 20, 10, 0L);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, fixture.crop, SOURCE_W, SOURCE_H, config);
        assertFalse(decision.accepted);
        assertEquals("MASK_MAJOR_SPAN_TOO_SMALL", decision.reasonCode);
    }

    @Test
    void rejectsCompetingComponentTooLarge() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        // secondLargest / selected = 500 / 1000 = 0.5 > 0.30
        final Fixture fixture = statsOverrideFixture(0.40, 80, 60, 500L);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, fixture.crop, SOURCE_W, SOURCE_H, config);
        assertFalse(decision.accepted);
        assertEquals("MASK_COMPETING_COMPONENT_TOO_LARGE", decision.reasonCode);
    }

    @Test
    void rejectsInvalidCrop() {
        final BatchConfiguration config = BatchConfiguration.fromMap(BatchTestConfigs.validMap());
        final Fixture fixture = validFixture(0.40, 0L);
        final AnalysisCropGeometry escaping = AnalysisCropGeometry.fullSource(900, 700);
        final BatchMaskAcceptancePolicy.Decision decision = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, escaping, SOURCE_W, SOURCE_H, config);
        assertFalse(decision.accepted);
        assertEquals("MASK_CROP_INVALID", decision.reasonCode);

        final BatchMaskAcceptancePolicy.Decision nullCrop = BatchMaskAcceptancePolicy.evaluate(
                fixture.generation, null, SOURCE_W, SOURCE_H, config);
        assertFalse(nullCrop.accepted);
        assertEquals("MASK_CROP_INVALID", nullCrop.reasonCode);
    }

    private static Fixture validFixture(final double includedFraction, final long secondLargest) {
        return statsOverrideFixture(includedFraction, 80, 60, secondLargest);
    }

    private static Fixture statsOverrideFixture(
            final double includedFraction,
            final int widthSpan,
            final int heightSpan,
            final long secondLargest) {
        final int maskW = DownsampledSampleMask.maskDimension(SOURCE_W, FACTOR);
        final int maskH = DownsampledSampleMask.maskDimension(SOURCE_H, FACTOR);
        final byte[] pixels = new byte[maskW * maskH];
        for (int y = 10; y < 70; y++) {
            for (int x = 10; x < 90; x++) {
                pixels[y * maskW + x] = (byte) 255;
            }
        }
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                SOURCE_W, SOURCE_H, FACTOR, pixels, maskW, maskH);
        final long selectedArea = 1000L;
        final SampleMaskStatistics stats = new SampleMaskStatistics(
                200, 200, 200,
                40,
                2,
                selectedArea,
                new Rectangle(10, 10, widthSpan, heightSpan),
                widthSpan,
                heightSpan,
                secondLargest,
                (long) (includedFraction * maskW * maskH),
                includedFraction,
                12345,
                4);
        final ImagePlus rgb = new ImagePlus("rgb", new ColorProcessor(maskW, maskH));
        final ImagePlus editable = new ImagePlus("editable", new ByteProcessor(maskW, maskH));
        final SampleMaskGenerationResult generation = new SampleMaskGenerationResult(
                mask, stats, rgb, editable);
        final AnalysisCropGeometry crop = AnalysisCropGeometry.fromApprovedMask(mask, 0);
        return new Fixture(generation, crop);
    }

    private static final class Fixture {
        final SampleMaskGenerationResult generation;
        final AnalysisCropGeometry crop;

        Fixture(final SampleMaskGenerationResult generation, final AnalysisCropGeometry crop) {
            this.generation = generation;
            this.crop = crop;
        }
    }
}
