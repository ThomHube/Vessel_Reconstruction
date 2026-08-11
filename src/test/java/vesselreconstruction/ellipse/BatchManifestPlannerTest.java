package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchManifestPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void exactMasterHeaderAndInitialRowFields() throws Exception {
        final Path input = tempDir.resolve("input");
        final Path batch = tempDir.resolve("batch");
        Files.createDirectories(input);
        writeTiff(input.resolve("0101A1.tif"), "one");
        writeTiff(input.resolve("0101A2.tif"), "two");
        final Path config = writeSubsetConfig(1);

        final BatchManifestPlanner.PlanResult result = BatchManifestPlanner.plan(
                new BatchManifestPlanner.PlanRequest(input, batch, config));

        final List<List<String>> table = ProductionCsvStore.readTable(result.masterManifest);
        assertEquals(BatchManifestSchema.HEADER, table.get(0));
        assertEquals(3, table.size());
        final List<String> row = table.get(1);
        assertEquals("2", row.get(0)); // schema_version
        assertEquals(Integer.toString(BatchManifestSchema.SCHEMA_VERSION), row.get(0));
        assertEquals("1", row.get(1)); // order_index
        assertEquals("0101A1", row.get(2)); // image_id
        assertEquals("01", row.get(3)); // plot_id
        assertEquals("01", row.get(4)); // tree_id
        assertEquals("A", row.get(5)); // core_label
        assertEquals("0101A", row.get(6)); // core_id
        assertEquals("1", row.get(7)); // segment_order
        assertEquals("0101A1.tif", row.get(8));
        assertEquals("0101A1.tif", row.get(9));
        assertFalse(row.get(9).startsWith("/"));
        assertEquals("machine_01", row.get(12));
        assertEquals("1", row.get(13));
        assertEquals(BatchManifestSchema.STATUS_PENDING, row.get(14));
        assertEquals("0", row.get(15));
        assertEquals(RCsvSupport.NA, row.get(16));
        assertEquals(RCsvSupport.NA, row.get(17));
        assertEquals(RCsvSupport.NA, row.get(18));
        assertEquals(RCsvSupport.NA, row.get(19));
        assertEquals(RCsvSupport.NA, row.get(20));
        assertEquals("output/lumen_dataset_machine_01", row.get(21));
        assertEquals(result.configurationSha256, row.get(22));
    }

    @Test
    void emptyMachineManifestAndMoreMachinesThanFiles() throws Exception {
        final Path input = tempDir.resolve("input2");
        final Path batch = tempDir.resolve("batch2");
        Files.createDirectories(input);
        writeTiff(input.resolve("0101A1.tif"), "a");
        writeTiff(input.resolve("0101A2.tif"), "b");
        writeTiff(input.resolve("0102A1.tif"), "c");
        final Path config = writeSubsetConfig(5);

        BatchManifestPlanner.plan(new BatchManifestPlanner.PlanRequest(input, batch, config));

        final Path empty = batch.resolve("machines/machine_05_manifest.csv");
        assertTrue(Files.isRegularFile(empty));
        final List<List<String>> table = ProductionCsvStore.readTable(empty);
        assertEquals(1, table.size());
        assertEquals(BatchManifestSchema.HEADER, table.get(0));
    }

    @Test
    void csvEscapingForCommaAndUnicodePaths() throws Exception {
        final Path input = tempDir.resolve("input3");
        final Path nested = input.resolve("dir,with,comma");
        Files.createDirectories(nested);
        writeTiff(nested.resolve("0101A1.tif"), "üñîçøde");
        final Path batch = tempDir.resolve("batch3");
        final Path config = writeSubsetConfig(1);

        final BatchManifestPlanner.PlanResult result = BatchManifestPlanner.plan(
                new BatchManifestPlanner.PlanRequest(input, batch, config));
        final String raw = new String(Files.readAllBytes(result.masterManifest), StandardCharsets.UTF_8);
        assertTrue(raw.contains("\"dir,with,comma/0101A1.tif\"")
                || raw.contains("dir,with,comma/0101A1.tif"));
        final List<List<String>> table = ProductionCsvStore.readTable(result.masterManifest);
        assertEquals("dir,with,comma/0101A1.tif", table.get(1).get(9).replace('\\', '/'));
    }

    @Test
    void atomicFailurePreservesPreviousPlan() throws Exception {
        final Path input = tempDir.resolve("input4");
        final Path batch = tempDir.resolve("batch4");
        Files.createDirectories(input);
        writeTiff(input.resolve("0101A1.tif"), "first");
        writeTiff(input.resolve("0101A2.tif"), "second");
        final Path config = writeSubsetConfig(1);

        BatchManifestPlanner.plan(new BatchManifestPlanner.PlanRequest(input, batch, config));
        final Path master = batch.resolve(BatchManifestSchema.FILE_MASTER_MANIFEST);
        final Path summary = batch.resolve(BatchManifestSchema.FILE_ASSIGNMENT_SUMMARY);
        final Path report = batch.resolve(BatchManifestSchema.FILE_PLAN_REPORT);
        final Path machinesDir = batch.resolve(BatchManifestSchema.DIR_MACHINES);
        final Path machineManifest = machinesDir.resolve("machine_01_manifest.csv");
        final byte[] previousMaster = Files.readAllBytes(master);
        final byte[] previousSummary = Files.readAllBytes(summary);
        final byte[] previousReport = Files.readAllBytes(report);
        final byte[] previousMachine = Files.readAllBytes(machineManifest);
        assertTrue(previousMaster.length > 0);

        // Block writing machine manifests without touching the committed master/summary/report.
        final Path machinesBackup = batch.resolve("machines.bak");
        Files.move(machinesDir, machinesBackup);
        Files.write(machinesDir, "blocker".getBytes(StandardCharsets.UTF_8));
        writeTiff(input.resolve("0102A1.tif"), "third");

        try {
            final BatchManifestPlanner.PlanningException ex = assertThrows(
                    BatchManifestPlanner.PlanningException.class,
                    () -> BatchManifestPlanner.plan(
                            new BatchManifestPlanner.PlanRequest(input, batch, config)));
            assertEquals(5, ex.exitCode);
            assertEquals(new String(previousMaster, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(master), StandardCharsets.UTF_8));
            assertEquals(new String(previousSummary, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(summary), StandardCharsets.UTF_8));
            assertEquals(new String(previousReport, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(report), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(machinesDir);
            if (Files.exists(machinesBackup)) {
                Files.move(machinesBackup, machinesDir);
            }
        }
        assertEquals(new String(previousMachine, StandardCharsets.UTF_8),
                new String(Files.readAllBytes(machineManifest), StandardCharsets.UTF_8));
    }

    @Test
    void strictCompleteSetRejectedByPlanner() throws Exception {
        final Path input = tempDir.resolve("input5");
        final Path batch = tempDir.resolve("batch5");
        Files.createDirectories(input);
        writeTiff(input.resolve("0101A1.tif"), "only");
        final Path config = tempDir.resolve("strict.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText(true, 1, false)
                .getBytes(StandardCharsets.UTF_8));

        final BatchManifestPlanner.PlanningException ex = assertThrows(
                BatchManifestPlanner.PlanningException.class,
                () -> BatchManifestPlanner.plan(
                        new BatchManifestPlanner.PlanRequest(input, batch, config)));
            assertEquals(3, ex.exitCode);
        assertFalse(Files.exists(batch.resolve(BatchManifestSchema.FILE_MASTER_MANIFEST)));
    }

    @Test
    void globalOrderPreservedAcrossMachines() throws Exception {
        final Path input = tempDir.resolve("input6");
        final Path batch = tempDir.resolve("batch6");
        Files.createDirectories(input);
        writeTiff(input.resolve("0102A1.tif"), "c");
        writeTiff(input.resolve("0101A1.tif"), "a");
        writeTiff(input.resolve("0101A2.tif"), "b");
        final Path config = writeSubsetConfig(2);

        BatchManifestPlanner.plan(new BatchManifestPlanner.PlanRequest(input, batch, config));
        final List<List<String>> master = ProductionCsvStore.readTable(
                batch.resolve(BatchManifestSchema.FILE_MASTER_MANIFEST));
        assertEquals("0101A1", master.get(1).get(2));
        assertEquals("0101A2", master.get(2).get(2));
        assertEquals("0102A1", master.get(3).get(2));
        assertEquals("1", master.get(1).get(1));
        assertEquals("2", master.get(2).get(1));
        assertEquals("3", master.get(3).get(1));
        // Same-core 0101A1/A2 stay together; 0102A1 can go to the other machine.
        assertEquals(master.get(1).get(12), master.get(2).get(12));
        assertEquals("machine_01", master.get(1).get(12));
        assertEquals("machine_02", master.get(3).get(12));
    }

    private Path writeSubsetConfig(final int machines) throws Exception {
        final Path config = tempDir.resolve("subset-" + machines + "-" + System.nanoTime() + ".properties");
        Files.write(config, BatchTestConfigs.validPropertiesText(false, machines, true)
                .getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private static void writeTiff(final Path path, final String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
