package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkWorkloadTest {

    @Test
    void discovers9999A1UnderNestedTestTree(@TempDir final Path root) throws Exception {
        final Path tiff = root.resolve("tiffs_9999A").resolve("9999A1").resolve("9999A1.tif");
        Files.createDirectories(tiff.getParent());
        Files.write(tiff, "not-a-real-tiff".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final Path found = HardwareBenchmarkWorkload.discover9999A1(root);
        assertNotNull(found);
        assertEquals(tiff.toAbsolutePath().normalize(), found.toAbsolutePath().normalize());
    }

    @Test
    void isolatedCopyRecordsChecksumsAndLeavesOriginalUnchanged(@TempDir final Path root)
            throws Exception {
        final Path original = root.resolve("source").resolve("9999A1.tif");
        writeRgbTiff(original, 64, 48);
        final String before = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkWorkload.Prepared prepared =
                HardwareBenchmarkWorkload.prepareIsolatedCopy(
                        original, root.resolve("workload"), false);
        assertEquals(before, Sha256Files.sha256OfFile(original));
        assertEquals(before, prepared.originalSha256);
        assertEquals(prepared.workloadSha256, Sha256Files.sha256OfFile(prepared.workloadTiff));
        assertEquals(64, prepared.originalWidthPx);
        assertEquals(48, prepared.originalHeightPx);
        assertEquals(64, prepared.workloadWidthPx);
        assertEquals(48, prepared.workloadHeightPx);
        assertFalse(prepared.cropped);
        assertTrue(prepared.identity.contains("9999A1"));
        final String readme = new String(
                Files.readAllBytes(root.resolve("workload").resolve("README.txt")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(readme.contains("NOT FOR SCIENTIFIC ANALYSIS"));
        assertTrue(readme.contains(before));
        assertTrue(readme.contains("tile=1024x1024"));
    }

    @Test
    void cropIsTakenFromACopyAndOriginalBytesStayIdentical(@TempDir final Path root)
            throws Exception {
        final Path original = root.resolve("source").resolve("9999A1.tif");
        writeRgbTiff(original, 2500, 2500);
        final String before = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkWorkload.Prepared prepared =
                HardwareBenchmarkWorkload.prepareIsolatedCopy(
                        original, root.resolve("workload"), true);
        assertEquals(before, Sha256Files.sha256OfFile(original));
        assertEquals(before, prepared.originalSha256);
        assertTrue(prepared.cropped);
        assertEquals(HardwareBenchmarkWorkload.TARGET_WIDTH_PX, prepared.workloadWidthPx);
        assertEquals(HardwareBenchmarkWorkload.TARGET_HEIGHT_PX, prepared.workloadHeightPx);
        assertFalse(prepared.workloadSha256.equalsIgnoreCase(before));
        assertEquals("9999A1_CROP_2304", prepared.identity);
        assertEquals(2500L * 2500L, (long) prepared.originalWidthPx * prepared.originalHeightPx);
    }

    @Test
    void scaleBarCompositeIsUsedOnlyWhenThe9999ABarWindowFits() {
        final ColorProcessor smallProc = new ColorProcessor(2500, 2500);
        final ImagePlus small = new ImagePlus("small", smallProc);
        try {
            assertFalse(HardwareBenchmarkWorkload.canComposeScaleBarWorkload(
                    small, HardwareBenchmarkWorkload.TARGET_WIDTH_PX,
                    HardwareBenchmarkWorkload.TARGET_HEIGHT_PX));
        } finally {
            small.changes = false;
            small.close();
        }
        final ColorProcessor largeProc = new ColorProcessor(4730, 5457);
        final ImagePlus large = new ImagePlus("large", largeProc);
        try {
            assertTrue(HardwareBenchmarkWorkload.canComposeScaleBarWorkload(
                    large, HardwareBenchmarkWorkload.TARGET_WIDTH_PX,
                    HardwareBenchmarkWorkload.TARGET_HEIGHT_PX));
        } finally {
            large.changes = false;
            large.close();
        }
    }

    static void writeRgbTiff(final Path dest, final int width, final int height) throws Exception {
        Files.createDirectories(dest.getParent());
        final ColorProcessor processor = new ColorProcessor(width, height);
        processor.setColor(Color.RED);
        processor.fill();
        final ImagePlus image = new ImagePlus("9999A1", processor);
        IJ.saveAsTiff(image, dest.toAbsolutePath().normalize().toString());
        image.changes = false;
        image.close();
    }
}
