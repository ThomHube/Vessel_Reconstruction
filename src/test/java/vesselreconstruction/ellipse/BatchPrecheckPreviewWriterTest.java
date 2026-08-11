package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckPreviewWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesMaskAndScalePreviewsWithoutChangingSourcePixels() throws Exception {
        final int width = 64;
        final int height = 48;
        final ColorProcessor processor = new ColorProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                processor.set(x, y, ((x * 3) << 16) | ((y * 5) << 8) | ((x + y) & 0xff));
            }
        }
        final ImagePlus source = new ImagePlus("source-rgb", processor);
        final int checksumBefore = TiledWekaClassifier.checksumSource(source);

        final int factor = 8;
        final int maskW = DownsampledSampleMask.maskDimension(width, factor);
        final int maskH = DownsampledSampleMask.maskDimension(height, factor);
        final byte[] maskPixels = new byte[maskW * maskH];
        for (int i = 0; i < maskPixels.length / 2; i++) {
            maskPixels[i] = (byte) 255;
        }
        final DownsampledSampleMask mask = new DownsampledSampleMask(
                width, height, factor, maskPixels, maskW, maskH);
        final AnalysisCropGeometry crop = AnalysisCropGeometry.fromApprovedMask(mask, 0);

        final ColorProcessor previewProc = new ColorProcessor(maskW, maskH);
        previewProc.setColor(0x808080);
        previewProc.fill();
        final ImagePlus rgbPreview = new ImagePlus("preview", previewProc);

        final Path maskOut = tempDir.resolve("mask_preview.png");
        final Path scaleOut = tempDir.resolve("scale_preview.png");

        BatchPrecheckPreviewWriter.writeMaskPreview(
                maskOut, rgbPreview, mask, crop, "0101A1", true, RCsvSupport.NA);

        final double measured = 40.0;
        final ScaleBarDetection detection = ScaleBarDetection.measured(
                ScaleBarDetectionConfidence.MEDIUM,
                0.8,
                ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES,
                5.0, height - 5.0,
                5.0 + measured, height - 5.0,
                measured,
                8.0,
                0.0,
                0.99,
                new java.awt.Rectangle(0, height - 12, (int) measured + 10, 12),
                new java.awt.Rectangle(0, height - 12, (int) measured + 10, 12),
                new java.awt.Rectangle(0, height - 12, (int) measured + 10, 12),
                java.util.Collections.<ScaleBarDetectionCandidate>emptyList());

        BatchPrecheckPreviewWriter.writeScaleBarPreview(
                scaleOut, source, detection, "0101A1", true, RCsvSupport.NA, 1.095);

        assertTrue(Files.isRegularFile(maskOut));
        assertTrue(Files.isRegularFile(scaleOut));
        assertTrue(Files.size(maskOut) > 0L);
        assertTrue(Files.size(scaleOut) > 0L);

        final int checksumAfter = TiledWekaClassifier.checksumSource(source);
        assertEquals(checksumBefore, checksumAfter);
    }
}
