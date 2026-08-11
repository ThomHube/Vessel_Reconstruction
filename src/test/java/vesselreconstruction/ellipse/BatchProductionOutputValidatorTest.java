package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchProductionOutputValidatorTest {

    private static final String IMAGE_ID = "0101A1";
    private static final String SAMPLE_ID = "0101A";

    @TempDir
    Path tempDir;

    @Test
    void completeDatasetPasses() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset");
        final byte[] sourceBytes = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        final String sourceSha = seedCompleteDataset(datasetRoot, sourceBytes);

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, sourceSha);
        assertTrue(result.ok, "unexpected failures: " + result.failures);
        assertTrue(result.failures.isEmpty());
    }

    @Test
    void completeValidDatasetIsDetectedAsFinalDatasetComplete() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-final");
        final String sourceSha = seedCompleteDataset(
                datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID, sourceSha, "classifier-sha", 1000, true);
        assertEquals(BatchProductionOutputValidator.FinalizedDatasetState.FINAL_DATASET_COMPLETE,
                inspection.state);
        assertEquals("class-run-1", inspection.classificationRunId);
        assertEquals("recon-run-1", inspection.reconstructionRunId);
        assertEquals(Integer.valueOf(12), inspection.totalComponents);
        assertEquals(Integer.valueOf(10), inspection.eligibleComponents);
        assertEquals(Integer.valueOf(3), inspection.reconstructedComponents);
        assertEquals(Integer.valueOf(42), inspection.changedPixels);
        assertTrue(inspection.originalRgbPath.contains("original_rgb"));
        assertTrue(inspection.reconstructedClassifiedPath.contains("reconstructed_classified"));
        assertTrue(inspection.lumenMeasurementsPath.contains("lumen_measurements"));
    }

    @Test
    void missingDatasetRootFails() {
        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(
                        tempDir.resolve("does-not-exist"), IMAGE_ID, null);
        assertFalse(result.ok);
        assertFalse(result.failures.isEmpty());
    }

    @Test
    void blankImageIdFails() {
        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(tempDir, "  ", null);
        assertFalse(result.ok);
        assertTrue(result.failures.get(0).contains("imageId"));
    }

    @Test
    void mismatchedSourceShaFails() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-sha-mismatch");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(
                        datasetRoot, IMAGE_ID,
                        "0000000000000000000000000000000000000000000000000000000000000000");
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "SHA-256 mismatch"));
    }

    @Test
    void mismatchedSourceShaIsCanonicalConflictForFinalInspection() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-conflict");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        null, 1000, true);
        assertEquals(BatchProductionOutputValidator.FinalizedDatasetState.CANONICAL_CONFLICT,
                inspection.state);
    }

    @Test
    void missingImagesReportedIndividually() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-missing-images");
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        layout.ensureDirectoriesExist();
        writeManifestsAndCsvs(layout, "deadbeef", "masked-sha");

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, null);
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "original_rgb file missing"));
        assertTrue(anyFailureContains(result, "approved mask missing"));
        assertTrue(anyFailureContains(result, "masked_rgb file missing"));
        assertTrue(anyFailureContains(result, "masked_classified file missing"));
        assertTrue(anyFailureContains(result, "reconstructed_classified file missing"));
    }

    @Test
    void missingManifestRowsReported() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-missing-manifest-rows");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);

        ProductionCsvStore.writeTableAtomically(
                layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                new ArrayList<List<String>>());
        ProductionCsvStore.writeTableAtomically(
                layout.segmentManifest(), ProductionSchemas.SEGMENT_MANIFEST_HEADER,
                new ArrayList<List<String>>());

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, null);
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "image_manifest has no row"));
        assertTrue(anyFailureContains(result, "segment_manifest has no row"));
    }

    @Test
    void processingRunsMissingSuccessRowsReported() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-missing-runs");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);

        ProductionCsvStore.writeTableAtomically(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER,
                new ArrayList<List<String>>());

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, null);
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "TILED_CLASSIFICATION SUCCESS"));
        assertTrue(anyFailureContains(result, "ALL_LUMINA_RECONSTRUCTION SUCCESS"));
    }

    @Test
    void duplicateLumenUidReported() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-duplicate-lumen");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);

        final List<String> row = lumenRow("L000001");
        final List<List<String>> rows = Arrays.asList(row, lumenRow("L000001"));
        final Path lumenPath = layout.dataLumenMeasurementsPerSegment().resolve(
                FilenameSanitizer.lumenMeasurementsFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                lumenPath, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, rows);

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, null);
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "duplicate lumen_uid"));

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID, null, null, 1000, true);
        assertEquals(
                BatchProductionOutputValidator.FinalizedDatasetState
                        .CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE,
                inspection.state);
    }

    @Test
    void nanCellsInCsvReported() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-nan-cells");
        seedCompleteDataset(datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);

        final List<String> row = lumenRow("L000001");
        row.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("area_um2"), "NaN");
        final Path lumenPath = layout.dataLumenMeasurementsPerSegment().resolve(
                FilenameSanitizer.lumenMeasurementsFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                lumenPath, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                Arrays.asList(row));

        final BatchProductionOutputValidator.ValidationResult result =
                BatchProductionOutputValidator.validate(datasetRoot, IMAGE_ID, null);
        assertFalse(result.ok);
        assertTrue(anyFailureContains(result, "non-finite value"));
    }

    @Test
    void wrongMinimumLumenAreaPreventsFinalReuse() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-min-area");
        final String sourceSha = seedCompleteDataset(
                datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID, sourceSha, null, 999, true);
        assertEquals(
                BatchProductionOutputValidator.FinalizedDatasetState
                        .CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE,
                inspection.state);
        assertTrue(inspection.failures.get(0).contains("minimum_lumen_area_px"));
    }

    @Test
    void invalidReconstructionOutputFilenamePreventsReuse() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-bad-output");
        final String sourceSha = seedCompleteDataset(
                datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        final List<List<String>> table = ProductionCsvStore.readTable(layout.processingRuns());
        final int outputIdx = ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("output_filename");
        final int stageIdx = ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("stage");
        for (int r = 1; r < table.size(); r++) {
            if (ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name()
                    .equals(table.get(r).get(stageIdx))) {
                table.get(r).set(outputIdx, "wrong_name.tif");
            }
        }
        ProductionCsvStore.writeTableAtomically(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER,
                table.subList(1, table.size()));

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID, sourceSha, null, 1000, true);
        assertEquals(
                BatchProductionOutputValidator.FinalizedDatasetState
                        .CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE,
                inspection.state);
        assertTrue(anyInspectionFailureContains(inspection, "output_filename"));
    }

    @Test
    void missingMaskedClassifiedIsClassificationIncomplete() throws Exception {
        final Path datasetRoot = tempDir.resolve("dataset-no-masked");
        final String sourceSha = seedCompleteDataset(
                datasetRoot, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        Files.delete(layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID)));

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot, IMAGE_ID, sourceSha, null, 1000, true);
        assertEquals(BatchProductionOutputValidator.FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                inspection.state);
    }

    private static boolean anyFailureContains(
            final BatchProductionOutputValidator.ValidationResult result, final String needle) {
        for (final String failure : result.failures) {
            if (failure.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyInspectionFailureContains(
            final BatchProductionOutputValidator.FinalizedDatasetInspection inspection,
            final String needle) {
        for (final String failure : inspection.failures) {
            if (failure.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Builds a fully populated, passing production dataset for a single image. */
    static String seedCompleteDataset(final Path datasetRoot, final byte[] sourceBytes)
            throws Exception {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        layout.ensureDirectoriesExist();

        final Path originalRgb = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(originalRgb, sourceBytes);
        final String sha = Sha256Files.sha256OfFile(originalRgb);

        final Path approvedMask = layout.imagesApprovedMasks().resolve(
                FilenameSanitizer.approvedMaskFilename(SAMPLE_ID, IMAGE_ID, 8));
        Files.write(approvedMask, "mask".getBytes(StandardCharsets.UTF_8));

        final Path maskedRgb = layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(maskedRgb, "masked-rgb".getBytes(StandardCharsets.UTF_8));

        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(maskedClassified, "masked-classified".getBytes(StandardCharsets.UTF_8));
        final String maskedSha = Sha256Files.sha256OfFile(maskedClassified);

        final Path reconstructed = layout.imagesReconstructedClassified().resolve(
                FilenameSanitizer.reconstructedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(reconstructed, "reconstructed".getBytes(StandardCharsets.UTF_8));

        writeManifestsAndCsvs(layout, sha, maskedSha);
        return sha;
    }

    private static void writeManifestsAndCsvs(
            final ProductionDatasetLayout layout,
            final String sourceSha,
            final String maskedClassifiedSha) throws Exception {
        final List<String> imageRow = new ArrayList<String>();
        for (final String column : ProductionSchemas.IMAGE_MANIFEST_HEADER) {
            imageRow.add(defaultValueFor(column));
        }
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("sample_id"), SAMPLE_ID);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_id"), IMAGE_ID);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("source_rgb_sha256"), sourceSha);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("masked_classified_filename"),
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("masked_classified_sha256"),
                maskedClassifiedSha);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_x_px"), "0");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_y_px"), "0");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_width_px"), "100");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("crop_height_px"), "80");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_width_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_height_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("reconstructed_classified_filename"),
                FilenameSanitizer.reconstructedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("classification_run_id"), "class-run-1");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("reconstruction_run_id"), "recon-run-1");
        ProductionCsvStore.writeTableAtomically(
                layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                Arrays.asList(imageRow));

        final List<String> segmentRow = new ArrayList<String>();
        for (final String column : ProductionSchemas.SEGMENT_MANIFEST_HEADER) {
            segmentRow.add(defaultValueFor(column));
        }
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("sample_id"), SAMPLE_ID);
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("segment_id"), IMAGE_ID);
        ProductionCsvStore.writeTableAtomically(
                layout.segmentManifest(), ProductionSchemas.SEGMENT_MANIFEST_HEADER,
                Arrays.asList(segmentRow));

        final List<String> classificationRunRow = processingRunRow(
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION,
                ProductionSchemas.RunStatus.SUCCESS,
                FilenameSanitizer.originalRgbFilename(SAMPLE_ID, IMAGE_ID),
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID),
                RCsvSupport.NA, RCsvSupport.NA, RCsvSupport.NA, RCsvSupport.NA, RCsvSupport.NA);
        classificationRunRow.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("run_id"), "class-run-1");
        final List<String> reconstructionRunRow = processingRunRow(
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION,
                ProductionSchemas.RunStatus.SUCCESS,
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID),
                FilenameSanitizer.reconstructedClassifiedFilename(SAMPLE_ID, IMAGE_ID),
                "1000", "12", "10", "3", "42");
        reconstructionRunRow.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("run_id"), "recon-run-1");
        ProductionCsvStore.writeTableAtomically(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER,
                Arrays.asList(classificationRunRow, reconstructionRunRow));

        final Path lumenPath = layout.dataLumenMeasurementsPerSegment().resolve(
                FilenameSanitizer.lumenMeasurementsFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                lumenPath, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                Arrays.asList(lumenRow("L000001")));
        ProductionCsvStore.writeTableAtomically(
                layout.dataLumenMeasurementsAll(), ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                Arrays.asList(lumenRow("L000001")));

        final List<String> diagnosticsRow = diagnosticsRow();
        final Path diagnosticsPath = layout.dataDiagnosticsPerSegment().resolve(
                FilenameSanitizer.artifactDiagnosticsFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                diagnosticsPath, ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER,
                Arrays.asList(diagnosticsRow));
        ProductionCsvStore.writeTableAtomically(
                layout.dataDiagnosticsAll(), ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER,
                Arrays.asList(diagnosticsRow));
    }

    private static List<String> processingRunRow(
            final ProductionSchemas.ProcessingStage stage,
            final ProductionSchemas.RunStatus status,
            final String inputFilename,
            final String outputFilename,
            final String minimumArea,
            final String total,
            final String eligible,
            final String reconstructed,
            final String changed) {
        final List<String> row = new ArrayList<String>();
        for (final String column : ProductionSchemas.PROCESSING_RUNS_HEADER) {
            row.add(defaultValueFor(column));
        }
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("stage"), stage.name());
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("status"), status.name());
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("sample_id"), SAMPLE_ID);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("segment_id"), IMAGE_ID);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("input_filename"), inputFilename);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("output_filename"), outputFilename);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("minimum_lumen_area_px"), minimumArea);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("total_components"), total);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("eligible_components"), eligible);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("reconstructed_components"), reconstructed);
        row.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("changed_pixels"), changed);
        return row;
    }

    private static List<String> lumenRow(final String lumenUid) {
        final List<String> row = new ArrayList<String>();
        for (final String column : ProductionSchemas.LUMEN_MEASUREMENTS_HEADER) {
            row.add(defaultValueFor(column));
        }
        row.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("lumen_uid"), lumenUid);
        row.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("sample_id"), SAMPLE_ID);
        row.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("segment_id"), IMAGE_ID);
        row.set(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER.indexOf("area_um2"), "12.5");
        return row;
    }

    private static List<String> diagnosticsRow() {
        final List<String> header = ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER;
        final List<String> row = new ArrayList<String>(Collections.nCopies(header.size(), RCsvSupport.NA));
        row.set(header.indexOf("sample_id"), SAMPLE_ID);
        row.set(header.indexOf("segment_id"), IMAGE_ID);
        row.set(header.indexOf("lumen_uid"), "L000001");
        row.set(header.indexOf("status"), "NO_INTERVAL_SELECTED");
        return row;
    }

    private static String defaultValueFor(final String column) {
        return RCsvSupport.NA;
    }
}
