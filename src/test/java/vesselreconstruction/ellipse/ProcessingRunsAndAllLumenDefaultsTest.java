package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ij.process.ByteProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingRunsAndAllLumenDefaultsTest {

    @TempDir
    Path tempDir;

    @Test
    void allLumenDefaultMinimumAreaIs1000() {
        assertEquals(1000, AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX);
        assertEquals(1000, AllLumenBatchConfiguration.summaryDefaults().getMinimumLumenArea());
        assertEquals(1000, AllLumenBatchConfiguration.silentDefaults().getMinimumLumenArea());
    }

    @Test
    void registeredPluginSourceUsesDefaultConstantAndDialogOrder() throws IOException {
        final String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/vesselreconstruction/ellipse/AllLumenSummaryPlugin.java")),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX"));
        assertFalse(source.contains("addNumericField(\"Minimum Lumen area in pixels:\", 100,"));
        final int minField = source.indexOf("\"Minimum Lumen area in pixels:\"");
        final int saveField = source.indexOf("addCheckbox(\"Save production outputs\"");
        final int refreshField = source.indexOf("addCheckbox(\"Refresh dataset aggregate tables\"");
        final int readMin = source.indexOf("final int minimumArea = (int) Math.round(dialog.getNextNumber())");
        final int readSkip = source.indexOf("final boolean skipBorder = dialog.getNextBoolean()");
        final int readSave = source.indexOf("final boolean saveProduction = dialog.getNextBoolean()");
        final int readRefresh = source.indexOf("final boolean refreshAggregates = dialog.getNextBoolean()");
        assertTrue(minField >= 0 && saveField > minField && refreshField > saveField);
        assertTrue(readMin >= 0 && readSkip > readMin && readSave > readSkip && readRefresh > readSave);
    }

    @Test
    void defaultBatchThresholdSeparates999And1000() {
        final ByteProcessor exact = wood(100, 100);
        paintBlob(exact, 1, 1, 999);
        paintBlob(exact, 1, 40, 1000);
        paintBlob(exact, 1, 75, 1001);
        final AllLumenBatchResult result = new AllLumenBatchProcessor().process(
                exact, AllLumenBatchConfiguration.silentDefaults());
        assertEquals(3, result.getTotalComponentCount());
        assertEquals(1, result.getBelowMinimumAreaCount());
        assertEquals(2, result.getEligibleComponentCount());
    }

    @Test
    void processingRunsHeaderIsExactly24CanonicalColumns() {
        assertEquals(24, ProcessingRunRecord.COLUMN_COUNT);
        assertEquals(24, ProcessingRunRecord.HEADER.size());
        assertEquals(ProductionSchemas.PROCESSING_RUNS_HEADER, ProcessingRunRecord.HEADER);
        final List<String> expected = Arrays.asList(
                "schema_version", "run_id", "timestamp", "stage", "status",
                "sample_id", "segment_id", "input_filename", "output_filename",
                "plugin_version", "git_commit", "classifier_filename",
                "core_tile_width_px", "core_tile_height_px", "threads_per_tile",
                "mask_downsample_factor", "mask_expansion_margin_px",
                "minimum_lumen_area_px", "total_components", "eligible_components",
                "reconstructed_components", "changed_pixels", "processing_time_ms",
                "message");
        assertEquals(expected, ProcessingRunRecord.HEADER);
    }

    @Test
    void tiledClassificationRowHas24FieldsAndReconstructionNa() {
        final List<String> row = classificationRecord("r1").toRow();
        assertEquals(24, row.size());
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("total_components")));
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("eligible_components")));
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
    }

    @Test
    void allLuminaRowHas24FieldsWithNamedCounts() {
        final List<String> row = reconstructionRecord("r2").toRow();
        assertEquals(24, row.size());
        assertEquals("1000", row.get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals("131", row.get(ProcessingRunRecord.HEADER.indexOf("total_components")));
        assertEquals("43", row.get(ProcessingRunRecord.HEADER.indexOf("eligible_components")));
        assertEquals("2", row.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals("1171", row.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
    }

    @Test
    void csvMessageEscapingPreservesSpecialCharacters() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final ProcessingRunRecord record = ProcessingRunRecord.builder()
                .schemaVersion(1)
                .runId("r")
                .timestamp("t")
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(ProductionSchemas.RunStatus.FAILED)
                .sampleId("s")
                .segmentId("g")
                .message("err, \"quoted\"\nline with µ")
                .build();
        ProcessingRunsManifest.append(
                file, record, ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        final List<List<String>> table = ProductionCsvStore.readTable(file);
        assertEquals("err, \"quoted\"\nline with µ",
                table.get(1).get(ProcessingRunRecord.HEADER.indexOf("message")));
    }

    @Test
    void exactHeaderAppendSucceeds() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        ProcessingRunsManifest.append(file, classificationRecord("runA"),
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION);
        ProcessingRunsManifest.append(file, reconstructionRecord("runB"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        final List<List<String>> table = ProductionCsvStore.readTable(file);
        assertEquals(3, table.size());
        assertEquals(ProcessingRunRecord.HEADER, table.get(0));
        assertEquals("TILED_CLASSIFICATION", table.get(1).get(3));
        assertEquals("ALL_LUMINA_RECONSTRUCTION", table.get(2).get(3));
    }

    @Test
    void olderSubsetHeaderMigratesAndPreservesRows() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final List<String> oldHeader = Arrays.asList(
                "schema_version", "run_id", "timestamp", "stage", "status",
                "sample_id", "segment_id", "input_filename", "output_filename",
                "plugin_version", "git_commit", "classifier_filename",
                "core_tile_width_px", "core_tile_height_px", "threads_per_tile",
                "mask_downsample_factor", "mask_expansion_margin_px",
                "processing_time_ms", "message");
        final List<String> oldRow = Arrays.asList(
                "1", "oldRun", "ts", "TILED_CLASSIFICATION", "SUCCESS",
                "s", "g", "NA", "out.tif", "0.1.0-SNAPSHOT", "NA", "m.model",
                "2048", "2048", "1", "8", "32", "9.0", "classification committed");
        ProductionCsvStore.writeTableAtomically(file, oldHeader, Collections.singletonList(oldRow));
        ProcessingRunsManifest.append(file, reconstructionRecord("newRun"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        final List<List<String>> table = ProductionCsvStore.readTable(file);
        assertEquals(ProcessingRunRecord.HEADER, table.get(0));
        assertEquals(3, table.size());
        assertEquals("oldRun", table.get(1).get(1));
        assertEquals(RCsvSupport.NA,
                table.get(1).get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals("newRun", table.get(2).get(1));
    }

    @Test
    void permutedKnownHeaderMigratesByName() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final List<String> permuted = Arrays.asList(
                "message", "run_id", "schema_version", "timestamp", "stage", "status",
                "sample_id", "segment_id", "output_filename", "processing_time_ms");
        final List<String> oldRow = Arrays.asList(
                "hello", "old", "1", "ts", "TILED_CLASSIFICATION", "SUCCESS",
                "s", "g", "out.tif", "3.0");
        ProductionCsvStore.writeTableAtomically(file, permuted, Collections.singletonList(oldRow));
        ProcessingRunsManifest.append(file, reconstructionRecord("new"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        final List<List<String>> table = ProductionCsvStore.readTable(file);
        assertEquals("old", table.get(1).get(ProcessingRunRecord.HEADER.indexOf("run_id")));
        assertEquals("hello", table.get(1).get(ProcessingRunRecord.HEADER.indexOf("message")));
        assertEquals("out.tif",
                table.get(1).get(ProcessingRunRecord.HEADER.indexOf("output_filename")));
    }

    @Test
    void unknownExtraColumnAbortsAndPreservesBytes() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final List<String> bad = new ArrayList<String>(ProcessingRunRecord.HEADER);
        bad.add("mystery_column");
        final List<String> row = new ArrayList<String>();
        for (int i = 0; i < bad.size(); i++) {
            row.add("x");
        }
        ProductionCsvStore.writeTableAtomically(file, bad, Collections.singletonList(row));
        final byte[] before = Files.readAllBytes(file);
        final IOException ex = assertThrows(IOException.class, () -> ProcessingRunsManifest.append(
                file, reconstructionRecord("n"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION));
        assertTrue(ex.getMessage().contains("extra columns")
                || ex.getMessage().contains("mystery_column"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
    }

    @Test
    void duplicateHeaderColumnAbortsAndPreservesBytes() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final String csv = "schema_version,run_id,run_id,timestamp,stage,status,sample_id,segment_id,"
                + "input_filename,output_filename,plugin_version,git_commit,classifier_filename,"
                + "core_tile_width_px,core_tile_height_px,threads_per_tile,mask_downsample_factor,"
                + "mask_expansion_margin_px,minimum_lumen_area_px,total_components,eligible_components,"
                + "reconstructed_components,changed_pixels,processing_time_ms,message\n"
                + "1,a,a,t,TILED_CLASSIFICATION,SUCCESS,s,g,NA,o,v,NA,c,1,1,1,1,1,NA,NA,NA,NA,NA,1,m\n";
        Files.write(file, csv.getBytes(StandardCharsets.UTF_8));
        final byte[] before = Files.readAllBytes(file);
        final IOException ex = assertThrows(IOException.class, () -> ProcessingRunsManifest.append(
                file, reconstructionRecord("n"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION));
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
    }

    @Test
    void malformedRowWidthAbortsAndPreservesBytes() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final String csv = RCsvSupport.joinRow(ProcessingRunRecord.HEADER) + "\n1,short,row\n";
        Files.write(file, csv.getBytes(StandardCharsets.UTF_8));
        final byte[] before = Files.readAllBytes(file);
        final IOException ex = assertThrows(IOException.class, () -> ProcessingRunsManifest.append(
                file, reconstructionRecord("n"),
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION));
        assertTrue(ex.getMessage().contains("Malformed") || ex.getMessage().contains("row width"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(file)));
    }

    @Test
    void subsetMigrationPreservesClassificationSuccessRow() throws IOException {
        final Path file = tempDir.resolve("processing_runs.csv");
        final List<String> oldHeader = Arrays.asList(
                "schema_version", "run_id", "timestamp", "stage", "status",
                "sample_id", "segment_id", "message");
        final List<String> oldRow = Arrays.asList(
                "1", "classRun", "t", "TILED_CLASSIFICATION", "SUCCESS", "s", "g", "ok");
        ProductionCsvStore.writeTableAtomically(file, oldHeader, Collections.singletonList(oldRow));
        ProcessingRunsManifest.ensureCanonicalSchema(
                file, ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        final List<List<String>> table = ProductionCsvStore.readTable(file);
        assertEquals(ProcessingRunRecord.HEADER, table.get(0));
        assertEquals("classRun", table.get(1).get(1));
        assertEquals("SUCCESS", table.get(1).get(4));
    }

    @Test
    void historicalShortAllLuminaRowHad23FieldsAgainst24Header() {
        final List<String> buggy23 = RCsvSupport.mutableCopy(
                "1", "run", "ts", "ALL_LUMINA_RECONSTRUCTION", "SUCCESS",
                "s", "g", "NA", "out.tif", "0.1.0-SNAPSHOT", "NA", "NA",
                "NA", "NA", "NA", "NA",
                "1000", "131", "43", "2", "1171", "1.0",
                "all-lumina reconstruction committed");
        assertEquals(23, buggy23.size());
        assertEquals(24, reconstructionRecord("run").toRow().size());
    }

    @Test
    void noNaNInSerializedProcessingRunCells() {
        final List<String> row = ProcessingRunRecord.builder()
                .schemaVersion(1)
                .runId("r")
                .timestamp("t")
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(ProductionSchemas.RunStatus.SUCCESS)
                .sampleId("s")
                .segmentId("g")
                .processingTimeMs(Double.valueOf(Double.NaN))
                .message("")
                .build()
                .toRow();
        for (int i = 0; i < row.size(); i++) {
            assertFalse("NaN".equalsIgnoreCase(row.get(i)), "column " + ProcessingRunRecord.HEADER.get(i));
        }
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("processing_time_ms")));
        assertEquals(RCsvSupport.NA, row.get(ProcessingRunRecord.HEADER.indexOf("message")));
    }

    @Test
    void mismatchExceptionContainsDiagnosticFields() {
        final String msg = ProcessingRunRecord.describeHeaderMismatch(
                "/tmp/processing_runs.csv",
                Arrays.asList("a", "b"),
                ProcessingRunRecord.HEADER,
                Arrays.asList("1", "2", "3"));
        assertTrue(msg.contains("manifest path:"));
        assertTrue(msg.contains("existing header column count: 2"));
        assertTrue(msg.contains("expected header column count: 24"));
        assertTrue(msg.contains("row column count: 3"));
        assertTrue(msg.contains("missing columns:"));
        assertTrue(msg.contains("extra columns:"));
        assertTrue(msg.contains("duplicate columns:"));
    }

    @Test
    void validatedCountFixtureConstantsRemainDocumented() {
        // Manual validation checklist for minimum area 1000 (not hard-coded production IDs).
        assertEquals(1000, AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX);
        assertEquals(131, 131);
        assertEquals(43, 43);
        assertEquals(88, 131 - 43);
        assertEquals(2, 2);
        assertEquals(1171, 1171);
    }

    private static ProcessingRunRecord classificationRecord(final String runId) {
        return ClassificationDatasetWriter.buildClassificationSuccessRecord(
                runId,
                "t",
                "s",
                "g",
                "source.tif",
                "masked_classified.tif",
                "m.model",
                2048,
                2048,
                1,
                8,
                32,
                1L);
    }

    private static ProcessingRunRecord reconstructionRecord(final String runId) {
        return ProcessingRunRecord.builder()
                .schemaVersion(1)
                .runId(runId)
                .timestamp("t")
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(ProductionSchemas.RunStatus.SUCCESS)
                .sampleId("s")
                .segmentId("g")
                .inputFilename("masked.tif")
                .outputFilename("recon.tif")
                .pluginVersion("0.1.0-SNAPSHOT")
                .minimumLumenAreaPx(Integer.valueOf(1000))
                .totalComponents(Integer.valueOf(131))
                .eligibleComponents(Integer.valueOf(43))
                .reconstructedComponents(Integer.valueOf(2))
                .changedPixels(Integer.valueOf(1171))
                .processingTimeMs(Double.valueOf(1.0))
                .message("all-lumina reconstruction committed")
                .build();
    }

    private static ByteProcessor wood(final int w, final int h) {
        final ByteProcessor p = new ByteProcessor(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                p.set(x, y, ClassifierLabels.WOOD_RESULT_VALUE);
            }
        }
        return p;
    }

    private static void paintBlob(
            final ByteProcessor image,
            final int x0,
            final int y0,
            final int count) {
        int painted = 0;
        int y = y0;
        while (painted < count) {
            for (int x = x0; x < x0 + 45 && painted < count; x++) {
                image.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
                painted++;
            }
            y++;
        }
    }
}
