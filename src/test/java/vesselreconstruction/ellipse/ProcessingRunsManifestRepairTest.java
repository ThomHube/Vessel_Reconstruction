package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingRunsManifestRepairTest {

    @TempDir
    Path tempDir;

    @Test
    void classificationRecordUsesSourceFilenameAndReconstructionNa() {
        final ProcessingRunRecord record = ClassificationDatasetWriter.buildClassificationSuccessRecord(
                "run1",
                "ts",
                "scalebar1-1",
                "scalebar1-1",
                "scalebar1-1.tif",
                "scalebar1-1__scalebar1-1__masked_classified.tif",
                "Classifier9.model",
                2048,
                2048,
                1,
                8,
                32,
                381003L);
        final List<String> row = record.toRow();
        assertEquals(24, row.size());
        assertEquals("scalebar1-1.tif",
                row.get(ProcessingRunRecord.HEADER.indexOf("input_filename")));
        assertEquals(RCsvSupport.NA,
                row.get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals(RCsvSupport.NA,
                row.get(ProcessingRunRecord.HEADER.indexOf("total_components")));
        assertEquals(RCsvSupport.NA,
                row.get(ProcessingRunRecord.HEADER.indexOf("eligible_components")));
        assertEquals(RCsvSupport.NA,
                row.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals(RCsvSupport.NA,
                row.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
        // Classification tile / outside-mask counts must not appear in recon columns.
        assertFalse("2".equals(row.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components"))));
        assertFalse("1502912".equals(row.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels"))));
    }

    @Test
    void classificationBuilderRejectsPathAsSourceFilename() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassificationDatasetWriter.buildClassificationSuccessRecord(
                        "r", "t", "s", "g", "/tmp/scalebar1-1.tif", "out.tif", "m.model",
                        2048, 2048, 1, 8, 32, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> ClassificationDatasetWriter.buildClassificationSuccessRecord(
                        "r", "t", "s", "g", "..\\x.tif", "out.tif", "m.model",
                        2048, 2048, 1, 8, 32, 1L));
    }

    @Test
    void allLuminaValidatedFixtureUnchanged() {
        final ProcessingRunRecord record = ProcessingRunRecord.builder()
                .schemaVersion(1)
                .runId("recon")
                .timestamp("t")
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(ProductionSchemas.RunStatus.SUCCESS)
                .sampleId("scalebar1-1")
                .segmentId("scalebar1-1")
                .inputFilename("scalebar1-1__scalebar1-1__masked_classified.tif")
                .outputFilename("scalebar1-1__scalebar1-1__reconstructed_classified.tif")
                .pluginVersion("0.1.0-SNAPSHOT")
                .minimumLumenAreaPx(Integer.valueOf(1000))
                .totalComponents(Integer.valueOf(131))
                .eligibleComponents(Integer.valueOf(43))
                .reconstructedComponents(Integer.valueOf(2))
                .changedPixels(Integer.valueOf(1171))
                .processingTimeMs(Double.valueOf(1.0))
                .message("all-lumina reconstruction committed")
                .build();
        final List<String> row = record.toRow();
        assertEquals("1000", row.get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals("131", row.get(ProcessingRunRecord.HEADER.indexOf("total_components")));
        assertEquals("43", row.get(ProcessingRunRecord.HEADER.indexOf("eligible_components")));
        assertEquals("2", row.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals("1171", row.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
    }

    @Test
    void repairsCurrentMisplacedClassificationValues() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        writeLegacyBrokenManifest(manifest);

        final ProcessingRunsManifestRepair.Result result = ProcessingRunsManifestRepair.repair(
                new ProcessingRunsManifestRepair.Request(
                        root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif"));
        assertTrue(result.fileChanged);
        assertEquals("NA", result.oldInputFilename);
        assertEquals("scalebar1-1.tif", result.newInputFilename);
        assertEquals("2", result.oldReconstructedComponents);
        assertEquals("1502912", result.oldChangedPixels);

        final List<List<String>> table = ProductionCsvStore.readTable(manifest);
        final List<String> classRow = table.get(1);
        assertEquals("scalebar1-1.tif",
                classRow.get(ProcessingRunRecord.HEADER.indexOf("input_filename")));
        assertEquals(RCsvSupport.NA,
                classRow.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals(RCsvSupport.NA,
                classRow.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
        final List<String> reconRow = table.get(2);
        assertEquals("1000",
                reconRow.get(ProcessingRunRecord.HEADER.indexOf("minimum_lumen_area_px")));
        assertEquals("131",
                reconRow.get(ProcessingRunRecord.HEADER.indexOf("total_components")));
        assertEquals("43",
                reconRow.get(ProcessingRunRecord.HEADER.indexOf("eligible_components")));
        assertEquals("2",
                reconRow.get(ProcessingRunRecord.HEADER.indexOf("reconstructed_components")));
        assertEquals("1171",
                reconRow.get(ProcessingRunRecord.HEADER.indexOf("changed_pixels")));
    }

    @Test
    void allLuminaRowSemanticallyPreservedAfterRepair() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        writeLegacyBrokenManifest(manifest);
        final List<String> beforeRecon = ProductionCsvStore.readTable(manifest).get(2);
        ProcessingRunsManifestRepair.repair(new ProcessingRunsManifestRepair.Request(
                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif"));
        final List<String> afterRecon = ProductionCsvStore.readTable(manifest).get(2);
        assertEquals(beforeRecon, afterRecon);
    }

    @Test
    void secondRepairIsIdempotent() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        writeLegacyBrokenManifest(manifest);
        ProcessingRunsManifestRepair.repair(new ProcessingRunsManifestRepair.Request(
                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif"));
        final byte[] afterFirst = Files.readAllBytes(manifest);
        final ProcessingRunsManifestRepair.Result second = ProcessingRunsManifestRepair.repair(
                new ProcessingRunsManifestRepair.Request(
                        root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif"));
        assertFalse(second.fileChanged);
        assertTrue(Arrays.equals(afterFirst, Files.readAllBytes(manifest)));
    }

    @Test
    void zeroMatchingRowsAbortsAndPreservesBytes() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        writeLegacyBrokenManifest(manifest);
        final byte[] before = Files.readAllBytes(manifest);
        final Exception ex = assertThrows(Exception.class,
                () -> ProcessingRunsManifestRepair.repair(
                        new ProcessingRunsManifestRepair.Request(
                                root, "other", "other", "scalebar1-1.tif")));
        assertTrue(ex.getMessage().contains("exactly one"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(manifest)));
    }

    @Test
    void multipleMatchingRowsAbortsAndPreservesBytes() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        final List<List<String>> body = new ArrayList<List<String>>();
        body.add(brokenClassificationRow("runA"));
        body.add(brokenClassificationRow("runB"));
        body.add(validReconstructionRow());
        ProductionCsvStore.writeTableAtomically(manifest, ProcessingRunRecord.HEADER, body);
        final byte[] before = Files.readAllBytes(manifest);
        final Exception ex = assertThrows(Exception.class,
                () -> ProcessingRunsManifestRepair.repair(
                        new ProcessingRunsManifestRepair.Request(
                                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif")));
        assertTrue(ex.getMessage().contains("exactly one"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(manifest)));
    }

    @Test
    void invalidHeaderAbortsAndPreservesBytes() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        final String bad = "a,b,c\n1,2,3\n";
        Files.write(manifest, bad.getBytes(StandardCharsets.UTF_8));
        final byte[] before = Files.readAllBytes(manifest);
        assertThrows(Exception.class,
                () -> ProcessingRunsManifestRepair.repair(
                        new ProcessingRunsManifestRepair.Request(
                                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif")));
        assertTrue(Arrays.equals(before, Files.readAllBytes(manifest)));
    }

    @Test
    void malformedRowWidthAbortsAndPreservesBytes() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        final String csv = RCsvSupport.joinRow(ProcessingRunRecord.HEADER) + "\n1,short\n";
        Files.write(manifest, csv.getBytes(StandardCharsets.UTF_8));
        final byte[] before = Files.readAllBytes(manifest);
        assertThrows(Exception.class,
                () -> ProcessingRunsManifestRepair.repair(
                        new ProcessingRunsManifestRepair.Request(
                                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif")));
        assertTrue(Arrays.equals(before, Files.readAllBytes(manifest)));
    }

    @Test
    void quotedMessageSurvivesRepair() throws Exception {
        final Path root = createDatasetRoot();
        final Path manifest = root.resolve("data/manifests/processing_runs.csv");
        final List<String> classRow = brokenClassificationRow("runC");
        classRow.set(ProcessingRunRecord.HEADER.indexOf("message"),
                "done, \"ok\"\nline with µ");
        final List<List<String>> body = new ArrayList<List<String>>();
        body.add(classRow);
        body.add(validReconstructionRow());
        ProductionCsvStore.writeTableAtomically(manifest, ProcessingRunRecord.HEADER, body);
        ProcessingRunsManifestRepair.repair(new ProcessingRunsManifestRepair.Request(
                root, "scalebar1-1", "scalebar1-1", "scalebar1-1.tif"));
        final List<String> repaired = ProductionCsvStore.readTable(manifest).get(1);
        assertEquals("done, \"ok\"\nline with µ",
                repaired.get(ProcessingRunRecord.HEADER.indexOf("message")));
    }

    @Test
    void cliRejectsUnknownAndMissingArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepairCli.parseArgs(new String[] {
                        "--dataset-root", "x", "--sample-id", "s", "--segment-id", "g",
                        "--source-filename", "a.tif", "--extra", "1"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepairCli.parseArgs(new String[] {
                        "--dataset-root", "x", "--sample-id", "s", "--segment-id", "g"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepairCli.parseArgs(new String[0]));
    }

    @Test
    void cliAndRepairRejectUnsafeSourceFilename() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepair.requireSafeFilename("../x.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepair.requireSafeFilename("/tmp/x.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingRunsManifestRepair.requireSafeFilename("a\\b.tif"));
    }

    @Test
    void cliParsesSupportedArguments() {
        final ProcessingRunsManifestRepair.Request request =
                ProcessingRunsManifestRepairCli.parseArgs(new String[] {
                        "--dataset-root", "/tmp/ds",
                        "--sample-id", "scalebar1-1",
                        "--segment-id", "scalebar1-1",
                        "--source-filename", "scalebar1-1.tif"
                });
        assertEquals("scalebar1-1", request.sampleId);
        assertEquals("scalebar1-1.tif", request.sourceFilename);
    }

    @Test
    void pluginsConfigDoesNotRegisterRepairCli() throws Exception {
        final String text = new String(Files.readAllBytes(
                java.nio.file.Paths.get("src/main/resources/plugins.config")),
                StandardCharsets.UTF_8);
        assertFalse(text.contains("ProcessingRunsManifestRepairCli"));
    }

    private Path createDatasetRoot() throws Exception {
        final Path root = tempDir.resolve("lumen_dataset");
        new ProductionDatasetLayout(root).ensureDirectoriesExist();
        return root;
    }

    private void writeLegacyBrokenManifest(final Path manifest) throws Exception {
        final List<List<String>> body = new ArrayList<List<String>>();
        body.add(brokenClassificationRow(
                "scalebar1-1__scalebar1-1__TILED_CLASSIFICATION__20260802_100831_494"));
        body.add(validReconstructionRow());
        ProductionCsvStore.writeTableAtomically(manifest, ProcessingRunRecord.HEADER, body);
    }

    private static List<String> brokenClassificationRow(final String runId) {
        final List<String> row = ClassificationDatasetWriter.buildClassificationSuccessRecord(
                runId,
                "20260802_100831_494",
                "scalebar1-1",
                "scalebar1-1",
                "placeholder.tif",
                "scalebar1-1__scalebar1-1__masked_classified.tif",
                "Classifier9.model",
                2048,
                2048,
                1,
                8,
                32,
                381003L).toRow();
        // Simulate the historical bug: NA input and misplaced classification counts.
        row.set(ProcessingRunRecord.HEADER.indexOf("input_filename"), RCsvSupport.NA);
        row.set(ProcessingRunRecord.HEADER.indexOf("reconstructed_components"), "2");
        row.set(ProcessingRunRecord.HEADER.indexOf("changed_pixels"), "1502912");
        return row;
    }

    private static List<String> validReconstructionRow() {
        return ProcessingRunRecord.builder()
                .schemaVersion(1)
                .runId("scalebar1-1__scalebar1-1__ALL_LUMINA_RECONSTRUCTION__20260802_101500_000")
                .timestamp("20260802_101500_000")
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(ProductionSchemas.RunStatus.SUCCESS)
                .sampleId("scalebar1-1")
                .segmentId("scalebar1-1")
                .inputFilename("scalebar1-1__scalebar1-1__masked_classified.tif")
                .outputFilename("scalebar1-1__scalebar1-1__reconstructed_classified.tif")
                .pluginVersion("0.1.0-SNAPSHOT")
                .minimumLumenAreaPx(Integer.valueOf(1000))
                .totalComponents(Integer.valueOf(131))
                .eligibleComponents(Integer.valueOf(43))
                .reconstructedComponents(Integer.valueOf(2))
                .changedPixels(Integer.valueOf(1171))
                .processingTimeMs(Double.valueOf(12.0))
                .message("all-lumina reconstruction committed")
                .build()
                .toRow();
    }
}
