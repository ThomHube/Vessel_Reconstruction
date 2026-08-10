package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskedLabelAssemblerTest {

    private final MaskedLabelAssembler assembler = new MaskedLabelAssembler();

    @Test
    void emptyTilesAreSkippedAndPartialTilesAreMasked() {
        final CoreTileLayout layout = CoreTileLayout.create(16, 16, 8, 8, 0);
        final byte[] maskPixels = new byte[] {
                (byte) 255, 0,
                0, 0
        };
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                16, 16, 8, maskPixels, 2, 2);

        final int[] classifiedCalls = new int[1];
        final MaskedLabelAssembler.AssemblyResult result = assembler.assemble(
                layout,
                mask,
                new MaskedLabelAssembler.TileClassifier() {
                    @Override
                    public ImagePlus classifyTile(final CoreTileGeometry tile) {
                        classifiedCalls[0]++;
                        final ByteProcessor processor = new ByteProcessor(
                                tile.getCoreWidth(), tile.getCoreHeight());
                        final byte[] pixels = (byte[]) processor.getPixels();
                        for (int i = 0; i < pixels.length; i++) {
                            pixels[i] = (byte) (i % 3); // 0,1,2
                        }
                        return new ImagePlus("fake", processor);
                    }
                });

        assertEquals(1, result.getClassifiedTiles());
        assertEquals(3, result.getSkippedTiles());
        assertEquals(1, classifiedCalls[0]);

        final ImagePlus labels = result.getLabels();
        assertEquals(16, labels.getWidth());
        assertEquals(16, labels.getHeight());
        // Inside top-left mask block: retain fake labels.
        assertTrue((labels.getProcessor().get(0, 0) & 0xff) <= 2);
        // Outside mask: forced background 3.
        assertEquals(3, labels.getProcessor().get(15, 15) & 0xff);
        assertEquals(3, labels.getProcessor().get(8, 0) & 0xff);
        assertTrue(result.getOutsideMaskForcedBackgroundPixels() > 0L);
    }

    @Test
    void applyMaskToLabelsForcesOutsidePixelsToBackground() {
        final ByteProcessor labels = new ByteProcessor(8, 8);
        final byte[] pixels = (byte[]) labels.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 2;
        }
        final byte[] maskPixels = new byte[] {
                (byte) 255, 0,
                0, 0
        };
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                8, 8, 4, maskPixels, 2, 2);
        final long forced = MaskedLabelAssembler.applyMaskToLabels(
                new ImagePlus("labels", labels), mask);
        assertTrue(forced > 0L);
        assertEquals(2, labels.get(0, 0) & 0xff);
        assertEquals(3, labels.get(7, 7) & 0xff);
    }

    @Test
    void emptyEditedMaskIsRejectedByReviewReader() {
        final ByteProcessor empty = new ByteProcessor(2, 2);
        assertThrows(IllegalArgumentException.class,
                () -> SampleMaskReviewController.readApprovedMask(
                        new ImagePlus("empty", empty), 8, 8, 4));
    }

    @Test
    void untiledComparisonUsesSameMask() {
        final ByteProcessor tiled = new ByteProcessor(8, 8);
        final ByteProcessor untiled = new ByteProcessor(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                tiled.set(x, y, 1);
                untiled.set(x, y, 2);
            }
        }
        final byte[] maskPixels = new byte[] {
                (byte) 255, 0,
                (byte) 255, 0
        };
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                8, 8, 4, maskPixels, 2, 2);
        MaskedLabelAssembler.applyMaskToLabels(new ImagePlus("t", tiled), mask);
        MaskedLabelAssembler.applyMaskToLabels(new ImagePlus("u", untiled), mask);

        // Outside-mask differences become zero (both background).
        assertEquals(tiled.get(6, 1) & 0xff, untiled.get(6, 1) & 0xff);
        assertEquals(3, tiled.get(6, 1) & 0xff);
        // Inside-mask difference remains detectable.
        assertEquals(1, tiled.get(1, 5) & 0xff);
        assertEquals(2, untiled.get(1, 5) & 0xff);
    }
}
