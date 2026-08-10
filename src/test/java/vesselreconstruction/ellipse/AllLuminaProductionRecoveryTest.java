package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Idempotent All Lumina production recovery and scientific CSV NA rules.
 */
class AllLuminaProductionRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void scientificCsvSerializesUnavailableAsNaNotNaN() {
        assertEquals(RCsvSupport.NA, RCsvSupport.numberOrNa(Double.NaN));
        assertEquals(RCsvSupport.NA, RCsvSupport.numberOrNa(Double.POSITIVE_INFINITY));
        final String joined = RCsvSupport.joinRow(RCsvSupport.mutableCopy(
                RCsvSupport.numberOrNa(Double.NaN), "1.5", RCsvSupport.TRUE));
        assertFalseContainsNaNToken(joined);
        assertTrue(joined.startsWith("NA,"));
    }

    @Test
    void conflictingReconstructedTiffAbortsWithoutOverwrite() throws Exception {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(tempDir.resolve("ds"));
        layout.ensureDirectoriesExist();
        final String safeSample = "samp";
        final String safeSegment = "seg";
        final Path target = layout.imagesReconstructedClassified().resolve(
                FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment));
        Files.write(target, "existing-different-bytes".getBytes(StandardCharsets.UTF_8));
        final String existingSha = Sha256Files.sha256OfFile(target);

        final ImagePlus image = syntheticClassifiedWithMetadata();
        final ByteProcessor source = (ByteProcessor) image.getProcessor();
        final AllLumenBatchResult batch = new AllLumenBatchProcessor().process(
                source, AllLumenBatchConfiguration.silentDefaults());
        seedImageManifest(layout, "samp", "seg");

        final IOException ex = assertThrows(IOException.class, () -> new AllLuminaDatasetWriter().write(
                image, source, batch, 1000, layout,
                LumenImageMetadata.parse(String.valueOf(image.getProperty("Info"))).getMetadata().get(),
                true));
        assertTrue(ex.getMessage().contains("conflict") || ex.getMessage().contains("SHA-256")
                || ex.getMessage().toLowerCase().contains("refusing"));
        assertEquals(existingSha, Sha256Files.sha256OfFile(target));
    }

    @Test
    void failedRunRecoveryAppendsSingleSuccessAfterCanonicalRunsExist() throws Exception {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(tempDir.resolve("ds2"));
        layout.ensureDirectoriesExist();
        // Existing classification SUCCESS row (canonical).
        ProcessingRunsManifest.append(
                layout.processingRuns(),
                ProcessingRunRecord.builder()
                        .schemaVersion(1)
                        .runId("class1")
                        .timestamp("t0")
                        .stage(ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION)
                        .status(ProductionSchemas.RunStatus.SUCCESS)
                        .sampleId("samp")
                        .segmentId("seg")
                        .outputFilename("masked.tif")
                        .pluginVersion("0.1.0-SNAPSHOT")
                        .classifierFilename("m.model")
                        .coreTileWidthPx(Integer.valueOf(2048))
                        .coreTileHeightPx(Integer.valueOf(2048))
                        .threadsPerTile(Integer.valueOf(1))
                        .maskDownsampleFactor(Integer.valueOf(8))
                        .maskExpansionMarginPx(Integer.valueOf(32))
                        .processingTimeMs(Double.valueOf(1.0))
                        .message("classification production outputs committed")
                        .build(),
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION);

        final ImagePlus image = syntheticClassifiedWithMetadata();
        final ByteProcessor source = (ByteProcessor) image.getProcessor();
        final int checksumBefore = checksum(source);
        final AllLumenBatchResult batch = new AllLumenBatchProcessor().process(
                source, AllLumenBatchConfiguration.silentDefaults());
        seedImageManifest(layout, "samp", "seg");

        final AllLuminaDatasetWriter.Result first = new AllLuminaDatasetWriter().write(
                image, source, batch, AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX, layout,
                LumenImageMetadata.parse(String.valueOf(image.getProperty("Info"))).getMetadata().get(),
                true);
        final AllLuminaDatasetWriter.Result second = new AllLuminaDatasetWriter().write(
                image, source, batch, AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX, layout,
                LumenImageMetadata.parse(String.valueOf(image.getProperty("Info"))).getMetadata().get(),
                true);

        assertEquals(checksumBefore, checksum(source));
        final List<List<String>> runs = ProductionCsvStore.readTable(layout.processingRuns());
        int successReconstruction = 0;
        for (int i = 1; i < runs.size(); i++) {
            if ("ALL_LUMINA_RECONSTRUCTION".equals(runs.get(i).get(3))
                    && "SUCCESS".equals(runs.get(i).get(4))) {
                successReconstruction++;
            }
        }
        assertEquals(2, successReconstruction);
        assertTrue(!first.runId.equals(second.runId));

        final List<List<String>> measurements = ProductionCsvStore.readTable(
                layout.dataLumenMeasurementsAll());
        final java.util.HashSet<String> uids = new java.util.HashSet<String>();
        final int uidIdx = ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("lumen_uid");
        for (int i = 1; i < measurements.size(); i++) {
            assertTrue(uids.add(measurements.get(i).get(uidIdx)));
        }
    }

    private static void assertFalseContainsNaNToken(final String text) {
        assertTrue(!text.contains(",NaN") && !text.startsWith("NaN") && !text.contains("NaN,"));
    }

    private static int checksum(final ByteProcessor processor) {
        final byte[] pixels = (byte[]) processor.getPixels();
        int sum = 0;
        for (int i = 0; i < pixels.length; i++) {
            sum = 31 * sum + (pixels[i] & 0xff);
        }
        return sum;
    }

    private static void seedImageManifest(
            final ProductionDatasetLayout layout,
            final String sampleId,
            final String segmentId) throws IOException {
        final List<String> row = new ArrayList<String>();
        for (int i = 0; i < ProductionSchemas.IMAGE_MANIFEST_HEADER.size(); i++) {
            row.add(RCsvSupport.NA);
        }
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("schema_version"), "1");
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("sample_id"), sampleId);
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_id"), segmentId);
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_order"), "1");
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_orientation"),
                SegmentOrientation.UNSPECIFIED.name());
        row.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("dataset_root"),
                layout.getDatasetRoot().toString());
        ProductionCsvStore.writeTableAtomically(
                layout.imageManifest(),
                ProductionSchemas.IMAGE_MANIFEST_HEADER,
                Collections.singletonList(row));
    }

    private static ImagePlus syntheticClassifiedWithMetadata() {
        final ByteProcessor processor = new ByteProcessor(64, 48);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                processor.set(x, y, ClassifierLabels.WOOD_RESULT_VALUE);
            }
        }
        // One eligible lumen (>=1000) and one below.
        for (int i = 0; i < 1000; i++) {
            processor.set(1 + (i % 40), 1 + (i / 40), ClassifierLabels.LUMEN_RESULT_VALUE);
        }
        for (int i = 0; i < 50; i++) {
            processor.set(50 + (i % 10), 30 + (i / 10), ClassifierLabels.LUMEN_RESULT_VALUE);
        }
        final ImagePlus image = new ImagePlus("masked_classified", processor);
        final AnalysisCropGeometry crop = AnalysisCropGeometry.fullSource(64, 48);
        final PhysicalCalibration calibration = PhysicalCalibration.fromEnteredPixelSize(1.0, null);
        final LumenImageMetadata metadata = LumenImageMetadata.create(
                "source.tif",
                "/tmp/source.tif",
                "samp",
                "seg",
                1,
                SegmentOrientation.UNSPECIFIED,
                crop,
                calibration,
                null,
                8,
                Integer.valueOf(1),
                "/tmp/m.model",
                "m.model",
                2048,
                2048,
                32,
                Integer.valueOf(1),
                Integer.valueOf(1),
                "ts");
        image.setProperty("Info", metadata.serialize() + "\nlumen.dataset_root="
                + "placeholder");
        return image;
    }
}
