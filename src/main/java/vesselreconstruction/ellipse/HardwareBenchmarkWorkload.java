package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.Duplicator;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers the 9999A TEST-ONLY TIFF, copies it without modifying the
 * original, and optionally shrinks it to a 2304x2304 TEST workload that
 * still exercises 1024x1024 tiled classification and production precheck.
 *
 * <p>9999A1's red 500 µm bar sits below a giant "500 µm" label. At the
 * production mask downsample of 8 those glyphs merge with the shaft, so a
 * naive bottom-left crop (or the full TIFF) fails {@code SCALE_BAR_NOT_FOUND}.
 * The isolated copy therefore composites a sample window with a tight
 * shaft-and-tick strip from the same TEST TIFF. The original file is never
 * modified.</p>
 */
public final class HardwareBenchmarkWorkload {

    public static final String IMAGE_ID = "9999A1";
    public static final String SOURCE_FILENAME = "9999A1.tif";
    public static final int TARGET_WIDTH_PX = 2304;
    public static final int TARGET_HEIGHT_PX = 2304;

    /** Analysis-crop origin from the successful 9999A1 2026-08-14 TEST run. */
    static final int SAMPLE_SRC_X_PX = 456;
    static final int SAMPLE_SRC_Y_PX = 0;
    static final int SAMPLE_COPY_HEIGHT_PX = 2048;

    /**
     * Tight window around the 9999A1 shaft and terminal ticks, excluding the
     * giant "500 µm" label that merges with the bar at downsample 8.
     */
    static final int BAR_SRC_X_PX = 3000;
    static final int BAR_SRC_Y_PX = 4940;
    static final int BAR_SRC_WIDTH_PX = 700;
    static final int BAR_SRC_HEIGHT_PX = 120;
    static final int PALE_BACKGROUND_RGB = (230 << 16) | (230 << 8) | 225;
    public static final String DISCLAIMER =
            "BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.";

    public static final class Prepared {
        public final Path originalPath;
        public final String originalSha256;
        public final int originalWidthPx;
        public final int originalHeightPx;
        public final Path workloadTiff;
        public final String workloadSha256;
        public final int workloadWidthPx;
        public final int workloadHeightPx;
        public final boolean cropped;
        public final String identity;

        public Prepared(
                final Path originalPath,
                final String originalSha256,
                final int originalWidthPx,
                final int originalHeightPx,
                final Path workloadTiff,
                final String workloadSha256,
                final int workloadWidthPx,
                final int workloadHeightPx,
                final boolean cropped) {
            this.originalPath = originalPath;
            this.originalSha256 = originalSha256;
            this.originalWidthPx = originalWidthPx;
            this.originalHeightPx = originalHeightPx;
            this.workloadTiff = workloadTiff;
            this.workloadSha256 = workloadSha256;
            this.workloadWidthPx = workloadWidthPx;
            this.workloadHeightPx = workloadHeightPx;
            this.cropped = cropped;
            this.identity = IMAGE_ID + (cropped ? "_CROP_2304" : "_FULL");
        }

        public long processedPixels() {
            return Math.max(1L, (long) workloadWidthPx * (long) workloadHeightPx);
        }
    }

    private HardwareBenchmarkWorkload() {
    }

    public static Path discover9999A1(final Path inputRoot) throws IOException {
        if (inputRoot == null || !Files.isDirectory(inputRoot)) {
            return null;
        }
        final List<Path> matches = new ArrayList<Path>();
        collectNamed(inputRoot, SOURCE_FILENAME, matches);
        if (matches.isEmpty()) {
            collectNamed(inputRoot, "9999A1.tiff", matches);
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(null);
        return matches.get(0);
    }

    public static Prepared prepareIsolatedCopy(
            final Path original,
            final Path destDir,
            final boolean allowCrop) throws IOException {
        if (original == null || !Files.isRegularFile(original)) {
            throw new IllegalArgumentException("original 9999A1 TIFF missing: " + original);
        }
        Files.createDirectories(destDir);
        final String originalSha = Sha256Files.sha256OfFile(original);
        final ImageSize size = readSize(original);
        Path workload = destDir.resolve(SOURCE_FILENAME);
        Files.copy(original, workload, StandardCopyOption.REPLACE_EXISTING);
        boolean cropped = false;
        int width = size.width;
        int height = size.height;
        if (allowCrop && size.width > TARGET_WIDTH_PX && size.height > TARGET_HEIGHT_PX) {
            final Path croppedPath = destDir.resolve("9999A1_crop.tif");
            if (cropForBenchmark(original, croppedPath, TARGET_WIDTH_PX, TARGET_HEIGHT_PX)) {
                workload = croppedPath;
                cropped = true;
                width = TARGET_WIDTH_PX;
                height = TARGET_HEIGHT_PX;
            }
        }
        final String afterOriginal = Sha256Files.sha256OfFile(original);
        if (!originalSha.equalsIgnoreCase(afterOriginal)) {
            throw new IllegalStateException("original TIFF changed while preparing workload");
        }
        writeDisclaimer(destDir.resolve("README.txt"), original, originalSha, workload,
                Sha256Files.sha256OfFile(workload), width, height, size.width, size.height, cropped);
        return new Prepared(
                original.toAbsolutePath().normalize(),
                originalSha,
                size.width,
                size.height,
                workload.toAbsolutePath().normalize(),
                Sha256Files.sha256OfFile(workload),
                width,
                height,
                cropped);
    }

    static boolean cropForBenchmark(
            final Path source,
            final Path dest,
            final int width,
            final int height) {
        ImagePlus image = null;
        ImagePlus cropped = null;
        try {
            image = new Opener().openImage(source.toAbsolutePath().normalize().toString());
            if (image == null || image.getWidth() < width || image.getHeight() < height) {
                return false;
            }
            if (canComposeScaleBarWorkload(image, width, height)) {
                cropped = composeSampleAndScaleBar(image, width, height);
            } else {
                final int y = image.getHeight() - height;
                image.setRoi(0, y, width, height);
                cropped = new Duplicator().run(image);
            }
            if (cropped == null) {
                return false;
            }
            IJ.saveAsTiff(cropped, dest.toAbsolutePath().normalize().toString());
            return Files.isRegularFile(dest);
        } catch (final Exception ex) {
            return false;
        } finally {
            if (cropped != null) {
                cropped.changes = false;
                cropped.close();
            }
            if (image != null) {
                image.changes = false;
                image.close();
            }
        }
    }

    static boolean canComposeScaleBarWorkload(
            final ImagePlus image,
            final int width,
            final int height) {
        if (image == null) {
            return false;
        }
        if (SAMPLE_SRC_X_PX + width > image.getWidth()
                || SAMPLE_SRC_Y_PX + SAMPLE_COPY_HEIGHT_PX > image.getHeight()) {
            return false;
        }
        if (BAR_SRC_X_PX + BAR_SRC_WIDTH_PX > image.getWidth()
                || BAR_SRC_Y_PX + BAR_SRC_HEIGHT_PX > image.getHeight()) {
            return false;
        }
        return height >= SAMPLE_COPY_HEIGHT_PX + BAR_SRC_HEIGHT_PX + 32;
    }

    static ImagePlus composeSampleAndScaleBar(
            final ImagePlus source,
            final int width,
            final int height) {
        final ImagePlus sample = duplicateRoi(
                source, SAMPLE_SRC_X_PX, SAMPLE_SRC_Y_PX, width, SAMPLE_COPY_HEIGHT_PX);
        final ImagePlus bar = duplicateRoi(
                source, BAR_SRC_X_PX, BAR_SRC_Y_PX, BAR_SRC_WIDTH_PX, BAR_SRC_HEIGHT_PX);
        try {
            if (sample == null || bar == null) {
                return null;
            }
            final ColorProcessor canvas = new ColorProcessor(width, height);
            fillRgb(canvas, PALE_BACKGROUND_RGB);
            copyInto(sample.getProcessor(), canvas, 0, 0);
            copyInto(bar.getProcessor(), canvas, (width - BAR_SRC_WIDTH_PX) / 2,
                    height - BAR_SRC_HEIGHT_PX);
            return new ImagePlus(IMAGE_ID, canvas);
        } finally {
            closeQuietly(sample);
            closeQuietly(bar);
        }
    }

    static ImagePlus duplicateRoi(
            final ImagePlus source,
            final int x,
            final int y,
            final int width,
            final int height) {
        source.setRoi(x, y, width, height);
        return new Duplicator().run(source);
    }

    static void fillRgb(final ColorProcessor dest, final int rgb) {
        for (int y = 0; y < dest.getHeight(); y++) {
            for (int x = 0; x < dest.getWidth(); x++) {
                dest.set(x, y, rgb);
            }
        }
    }

    static void copyInto(
            final ImageProcessor src,
            final ColorProcessor dest,
            final int dx,
            final int dy) {
        for (int y = 0; y < src.getHeight(); y++) {
            final int dyOut = dy + y;
            if (dyOut < 0 || dyOut >= dest.getHeight()) {
                continue;
            }
            for (int x = 0; x < src.getWidth(); x++) {
                final int dxOut = dx + x;
                if (dxOut < 0 || dxOut >= dest.getWidth()) {
                    continue;
                }
                dest.set(dxOut, dyOut, src.get(x, y));
            }
        }
    }

    static void closeQuietly(final ImagePlus image) {
        if (image != null) {
            image.changes = false;
            image.close();
        }
    }

    static ImageSize readSize(final Path tiff) {
        ImagePlus image = null;
        try {
            image = new Opener().openImage(tiff.toAbsolutePath().normalize().toString());
            if (image == null) {
                return new ImageSize(0, 0);
            }
            return new ImageSize(image.getWidth(), image.getHeight());
        } catch (final Exception ex) {
            return new ImageSize(0, 0);
        } finally {
            if (image != null) {
                image.changes = false;
                image.close();
            }
        }
    }

    static void writeDisclaimer(
            final Path path,
            final Path original,
            final String originalSha,
            final Path workload,
            final String workloadSha,
            final int workloadW,
            final int workloadH,
            final int originalW,
            final int originalH,
            final boolean cropped) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("LUMEN HARDWARE BENCHMARK WORKLOAD\n");
        sb.append(DISCLAIMER).append("\n\n");
        sb.append("original_path=").append(original.toAbsolutePath().normalize()).append('\n');
        sb.append("original_sha256=").append(originalSha).append('\n');
        sb.append("original_width_px=").append(originalW).append('\n');
        sb.append("original_height_px=").append(originalH).append('\n');
        sb.append("workload_path=").append(workload.toAbsolutePath().normalize()).append('\n');
        sb.append("workload_sha256=").append(workloadSha).append('\n');
        sb.append("workload_width_px=").append(workloadW).append('\n');
        sb.append("workload_height_px=").append(workloadH).append('\n');
        sb.append("cropped=").append(cropped ? RCsvSupport.TRUE : RCsvSupport.FALSE).append('\n');
        sb.append("tile=").append(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN).append('\n');
        Sha256Files.writeBytesAtomically(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> identityMap(final Prepared prepared) {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("workload_identity", prepared.identity);
        map.put("workload_sha256", prepared.workloadSha256);
        map.put("workload_width_px", Integer.toString(prepared.workloadWidthPx));
        map.put("workload_height_px", Integer.toString(prepared.workloadHeightPx));
        map.put("source_sha256", prepared.originalSha256);
        map.put("source_width_px", Integer.toString(prepared.originalWidthPx));
        map.put("source_height_px", Integer.toString(prepared.originalHeightPx));
        map.put("disclaimer", DISCLAIMER);
        return map;
    }

    private static void collectNamed(
            final Path current,
            final String filename,
            final List<Path> matches) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    collectNamed(path, filename, matches);
                } else if (filename.equalsIgnoreCase(path.getFileName().toString())) {
                    matches.add(path);
                }
            }
        }
    }

    static final class ImageSize {
        final int width;
        final int height;

        ImageSize(final int width, final int height) {
            this.width = width;
            this.height = height;
        }
    }
}
