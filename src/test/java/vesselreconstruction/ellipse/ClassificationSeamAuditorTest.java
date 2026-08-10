package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationSeamAuditorTest {

    private final ClassificationSeamAuditor auditor = new ClassificationSeamAuditor();

    @Test
    void nullTiledImageIsRejected() {
        final ImagePlus reference = labels(8, 8, 0);
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(null, reference, layout, 0));
    }

    @Test
    void nullReferenceIsRejected() {
        final ImagePlus tiled = labels(8, 8, 0);
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(tiled, null, layout, 0));
    }

    @Test
    void dimensionMismatchIsRejected() {
        final ImagePlus tiled = labels(8, 8, 0);
        final ImagePlus reference = labels(6, 8, 0);
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(tiled, reference, layout, 0));
    }

    @Test
    void nonByteImageIsRejected() {
        final ImagePlus tiled = labels(8, 8, 0);
        final ImagePlus reference = new ImagePlus("float", new FloatProcessor(8, 8));
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(tiled, reference, layout, 0));
    }

    @Test
    void invalidRawValueIsRejected() {
        final ImagePlus tiled = labels(8, 8, 0);
        final ByteProcessor bad = new ByteProcessor(8, 8);
        ((byte[]) bad.getPixels())[0] = 4;
        final ImagePlus reference = new ImagePlus("bad", bad);
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(tiled, reference, layout, 0));
    }

    @Test
    void negativeSeamWidthIsRejected() {
        final ImagePlus image = labels(8, 8, 0);
        final CoreTileLayout layout = CoreTileLayout.create(8, 8, 4, 4, 0);
        assertThrows(IllegalArgumentException.class,
                () -> auditor.compare(image, labels(8, 8, 0), layout, -1));
    }

    @Test
    void identicalImagesProduceExactMatch() {
        final ImagePlus image = labels(12, 12, 2);
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        final ClassificationSeamAudit audit =
                auditor.compare(image, labels(12, 12, 2), layout, 1);
        assertTrue(audit.isExactMatch());
        assertEquals(0L, audit.getDifferingPixelCount());
    }

    @Test
    void oneDifferingPixelIsCounted() {
        final ImagePlus tiled = labels(12, 12, 0);
        final ImagePlus reference = labels(12, 12, 0);
        setPixel(tiled, 0, 0, 1);
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        final ClassificationSeamAudit audit = auditor.compare(tiled, reference, layout, 0);
        assertEquals(1L, audit.getDifferingPixelCount());
        assertFalse(audit.isExactMatch());
    }

    @Test
    void confusionMatrixOrientationIsCorrect() {
        final ImagePlus tiled = labels(4, 4, 2);
        final ImagePlus reference = labels(4, 4, 1);
        final CoreTileLayout layout = CoreTileLayout.create(4, 4, 4, 4, 0);
        final ClassificationSeamAudit audit = auditor.compare(tiled, reference, layout, 0);
        final long[][] confusion = audit.getConfusionMatrix();
        assertEquals(16L, confusion[1][2]);
        assertEquals(0L, confusion[2][1]);
    }

    @Test
    void classCountsAreCorrect() {
        final ImagePlus tiled = labelsFrom(new byte[] {0, 1, 2, 3}, 2, 2);
        final ImagePlus reference = labelsFrom(new byte[] {3, 2, 1, 0}, 2, 2);
        final CoreTileLayout layout = CoreTileLayout.create(2, 2, 2, 2, 0);
        final ClassificationSeamAudit audit = auditor.compare(tiled, reference, layout, 0);
        assertArrayEquals(new long[] {1L, 1L, 1L, 1L}, audit.getTiledClassCounts());
        assertArrayEquals(new long[] {1L, 1L, 1L, 1L}, audit.getReferenceClassCounts());
    }

    @Test
    void seamPositionsUseCoreTileBoundaries() {
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        assertArrayEquals(new int[] {4, 8}, layout.getVerticalSeams());
        assertArrayEquals(new int[] {4, 8}, layout.getHorizontalSeams());
        assertTrue(ClassificationSeamAuditor.isInSeamBand(
                4, 0, layout.getVerticalSeams(), layout.getHorizontalSeams(), 0));
        assertFalse(ClassificationSeamAuditor.isInSeamBand(
                3, 0, layout.getVerticalSeams(), layout.getHorizontalSeams(), 0));
    }

    @Test
    void largeImageSeamsMatchExpectedCoordinates() {
        final CoreTileLayout layout = CoreTileLayout.create(9395, 35935, 4096, 4096, 0);
        assertArrayEquals(new int[] {4096, 8192}, layout.getVerticalSeams());
        assertArrayEquals(new int[] {
                4096, 8192, 12288, 16384, 20480, 24576, 28672, 32768
        }, layout.getHorizontalSeams());
    }

    @Test
    void seamDifferenceAndNonSeamDifferenceAreSeparated() {
        final ImagePlus tiled = labels(12, 12, 0);
        final ImagePlus reference = labels(12, 12, 0);
        setPixel(tiled, 4, 1, 1); // vertical seam
        setPixel(tiled, 0, 0, 2); // outside seam
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        final ClassificationSeamAudit audit = auditor.compare(tiled, reference, layout, 0);
        assertEquals(1L, audit.getSeamBandDifferingPixelCount());
        assertEquals(1L, audit.getNonSeamDifferingPixelCount());
    }

    @Test
    void crossedSeamPixelIsCountedOnce() {
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        final ClassificationSeamAudit audit =
                auditor.compare(labels(12, 12, 0), labels(12, 12, 0), layout, 0);
        // seams at 4 and 8 in both axes:
        // vertical: 2*12=24, horizontal: 2*12=24, intersections: 4 => 44
        assertEquals(44L, audit.getSeamBandPixelCount());
        assertEquals(144L - 44L, audit.getNonSeamPixelCount());
    }

    @Test
    void differenceAndSeamAuditMaskEncodingsAreCorrect() {
        final ImagePlus tiled = labels(12, 12, 0);
        final ImagePlus reference = labels(12, 12, 0);
        setPixel(tiled, 0, 0, 3); // different outside
        setPixel(tiled, 4, 3, 1); // different inside
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 4, 0);
        final ClassificationSeamAudit audit = auditor.compare(tiled, reference, layout, 0);

        assertEquals(255, getMaskValue(audit.getDifferenceMask(), 0, 0));
        assertEquals(255, getMaskValue(audit.getDifferenceMask(), 4, 3));
        assertEquals(0, getMaskValue(audit.getDifferenceMask(), 1, 1));

        assertEquals(192, getMaskValue(audit.getSeamAuditMask(), 0, 0));
        assertEquals(255, getMaskValue(audit.getSeamAuditMask(), 4, 3));
        assertEquals(64, getMaskValue(audit.getSeamAuditMask(), 4, 0));
        assertEquals(0, getMaskValue(audit.getSeamAuditMask(), 1, 1));
    }

    @Test
    void inputImagesRemainUnchangedAndComparisonIsDeterministic() {
        final byte[] tiledPixels = new byte[] {0, 1, 2, 3};
        final byte[] referencePixels = new byte[] {0, 1, 2, 3};
        final ImagePlus tiled = labelsFrom(tiledPixels.clone(), 2, 2);
        final ImagePlus reference = labelsFrom(referencePixels.clone(), 2, 2);
        final CoreTileLayout layout = CoreTileLayout.create(2, 2, 2, 1, 0);
        final ClassificationSeamAudit first = auditor.compare(tiled, reference, layout, 1);
        final ClassificationSeamAudit second = auditor.compare(tiled, reference, layout, 1);
        assertEquals(first, second);
        assertArrayEquals(new byte[] {0, 1, 2, 3}, (byte[]) tiled.getProcessor().getPixels());
        assertArrayEquals(new byte[] {0, 1, 2, 3}, (byte[]) reference.getProcessor().getPixels());
    }

    @Test
    void auditStoresCoreTilePixelSizes() {
        final CoreTileLayout layout = CoreTileLayout.create(12, 12, 4, 5, 0);
        final ClassificationSeamAudit audit =
                auditor.compare(labels(12, 12, 0), labels(12, 12, 0), layout, 2);
        assertEquals(4, audit.getCoreTileWidthPixels());
        assertEquals(5, audit.getCoreTileHeightPixels());
        assertEquals(layout.getTilesX(), audit.getCalculatedTilesX());
        assertEquals(layout.getTilesY(), audit.getCalculatedTilesY());
    }

    private static ImagePlus labels(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        Arrays.fill((byte[]) processor.getPixels(), (byte) value);
        return new ImagePlus("labels", processor);
    }

    private static ImagePlus labelsFrom(final byte[] pixels, final int width, final int height) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        System.arraycopy(pixels, 0, processor.getPixels(), 0, pixels.length);
        return new ImagePlus("labels", processor);
    }

    private static void setPixel(final ImagePlus image, final int x, final int y, final int value) {
        final byte[] pixels = (byte[]) image.getProcessor().getPixels();
        pixels[y * image.getWidth() + x] = (byte) value;
    }

    private static int getMaskValue(final ImagePlus mask, final int x, final int y) {
        final byte[] pixels = (byte[]) mask.getProcessor().getPixels();
        return pixels[y * mask.getWidth() + x] & 0xff;
    }
}
