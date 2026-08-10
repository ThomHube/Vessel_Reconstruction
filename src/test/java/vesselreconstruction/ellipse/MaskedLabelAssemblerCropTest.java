package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskedLabelAssemblerCropTest {

    private static final int CROP_OFFSET_X = 10;
    private static final int CROP_OFFSET_Y = 5;
    private static final int LAYOUT_WIDTH = 20;
    private static final int LAYOUT_HEIGHT = 10;
    private static final int CLASS_LABEL = 2;
    private static final int BACKGROUND_LABEL =
            TiledWekaClassificationDefaults.OUTSIDE_MASK_BACKGROUND_CLASS;

    private final MaskedLabelAssembler assembler = new MaskedLabelAssembler();

    @Test
    void cropOffsetAppliesMaskInOriginalCoordinates() {
        final CoreTileLayout layout = CoreTileLayout.create(
                LAYOUT_WIDTH, LAYOUT_HEIGHT, LAYOUT_WIDTH, LAYOUT_HEIGHT, 0);
        final DownsampledSampleMask mask = partialMask();
        final MaskedLabelAssembler.AssemblyResult result = assembler.assemble(
                layout,
                mask,
                new MaskedLabelAssembler.TileClassifier() {
                    @Override
                    public ImagePlus classifyTile(final CoreTileGeometry tile) {
                        final ByteProcessor processor = new ByteProcessor(
                                tile.getCoreWidth(), tile.getCoreHeight());
                        final byte[] pixels = (byte[]) processor.getPixels();
                        for (int i = 0; i < pixels.length; i++) {
                            pixels[i] = (byte) CLASS_LABEL;
                        }
                        return new ImagePlus("tile", processor);
                    }
                },
                CROP_OFFSET_X,
                CROP_OFFSET_Y);

        final ImagePlus labels = result.getLabels();
        assertEquals(LAYOUT_WIDTH, labels.getWidth());
        assertEquals(LAYOUT_HEIGHT, labels.getHeight());
        assertEquals(1, result.getClassifiedTiles());
        assertEquals(0, result.getSkippedTiles());
        assertTrue(result.getOutsideMaskForcedBackgroundPixels() > 0L);

        assertEquals(CLASS_LABEL, labels.getProcessor().get(0, 0) & 0xff);
        assertEquals(CLASS_LABEL, labels.getProcessor().get(9, 4) & 0xff);
        assertEquals(BACKGROUND_LABEL, labels.getProcessor().get(15, 0) & 0xff);
        assertEquals(BACKGROUND_LABEL, labels.getProcessor().get(19, 9) & 0xff);
    }

    private static DownsampledSampleMask partialMask() {
        final int originalWidth = CROP_OFFSET_X + LAYOUT_WIDTH;
        final int originalHeight = CROP_OFFSET_Y + LAYOUT_HEIGHT;
        final byte[] maskPixels = new byte[originalWidth * originalHeight];
        for (int y = CROP_OFFSET_Y; y < CROP_OFFSET_Y + 5; y++) {
            for (int x = CROP_OFFSET_X; x < CROP_OFFSET_X + 10; x++) {
                maskPixels[y * originalWidth + x] = (byte) 255;
            }
        }
        return new DownsampledSampleMask(
                originalWidth,
                originalHeight,
                1,
                maskPixels,
                originalWidth,
                originalHeight);
    }
}
