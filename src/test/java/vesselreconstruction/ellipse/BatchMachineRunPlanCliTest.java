package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineRunPlanCliTest {

    private static final String MACHINE_ID = "machine_01";

    @TempDir
    Path tempDir;

    @Test
    void printsOrderedPlanSummaryAndProgress() throws Exception {
        final Path inputRoot = tempDir.resolve("input");
        final Path batchRoot = tempDir.resolve("batch");
        Files.createDirectories(inputRoot);
        Files.createDirectories(batchRoot);

        final Path classifier = inputRoot.resolve("classifier.model");
        Files.write(classifier, "model".getBytes(StandardCharsets.UTF_8));
        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final BatchConfiguration config = BatchConfiguration.load(configPath);

        for (final String imageId : Arrays.asList("0101A2", "0101A1")) {
            Files.write(inputRoot.resolve(imageId + ".tif"),
                    ("tiff-" + imageId).getBytes(StandardCharsets.UTF_8));
        }
        writeManifest(batchRoot, config, Arrays.asList("0101A2", "0101A1"), inputRoot);
        writePrecheck(batchRoot, config, inputRoot, "0101A1", BatchPrecheckStatus.PRECHECK_SUCCESS);
        // 0101A2 missing precheck

        final Path datasetRoot = batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(inputRoot.resolve("0101A1.tif")));

        final String[] args = new String[] {
                "--input-root", inputRoot.toString(),
                "--batch-root", batchRoot.toString(),
                "--machine-id", MACHINE_ID,
                "--config", configPath.toString(),
                "--classifier", classifier.toString()
        };

        final PrintStream originalOut = System.out;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, "UTF-8"));
        final int code;
        try {
            code = BatchMachineRunPlanCli.run(args);
        } finally {
            System.setOut(originalOut);
        }
        assertEquals(BatchMachineRunPlanCli.EXIT_SUCCESS, code);
        final String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(text.contains("MACHINE RUN PLAN"));
        assertTrue(text.contains("progress: 50.0%"));
        assertTrue(text.contains("SUMMARY"));
        assertTrue(text.contains("FULL_SUCCESS: 1"));
        assertTrue(text.contains("PRECHECK_MISSING: 1"));
        assertTrue(text.contains("planned processing remaining: 1"));
        final int idx101 = text.indexOf("0101A1");
        final int idx102 = text.indexOf("0101A2");
        assertTrue(idx101 >= 0 && idx102 >= 0 && idx101 < idx102, text);
        assertTrue(text.contains("SKIP_COMPLETE"));
        assertTrue(text.contains("RUN_PRECHECK"));
    }

    @Test
    void rejectsMissingAndUnknownArguments() {
        assertEquals(BatchMachineRunPlanCli.EXIT_INVALID_ARGS,
                BatchMachineRunPlanCli.run(new String[0]));
        assertEquals(BatchMachineRunPlanCli.EXIT_INVALID_ARGS,
                BatchMachineRunPlanCli.run(new String[] {"--unknown", "x"}));
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineRunPlanCli.parseArgs(new String[] {
                        "--input-root", "/a",
                        "--batch-root", "/b",
                        "--machine-id", MACHINE_ID,
                        "--config", "/c"
                        // missing classifier
                }));
    }

    @Test
    void runtimeScriptUsesPureJavaClasspathAndNoFiji() throws Exception {
        final Path script = PathsGet("runtime/batch/inspect_machine_run.sh");
        final String text = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        assertTrue(text.contains("java -cp \"${CLASSES_DIR}\""));
        assertTrue(text.contains("BatchMachineRunPlanCli"));
        assertFalse(text.toLowerCase().contains("fiji"));
        assertFalse(text.contains("ImageJ"));
    }

    private static Path PathsGet(final String relative) {
        return java.nio.file.Paths.get(relative).toAbsolutePath().normalize();
    }

    private void writeManifest(
            final Path batchRoot,
            final BatchConfiguration config,
            final List<String> imageIds,
            final Path inputRoot) throws Exception {
        final List<List<String>> rows = new java.util.ArrayList<List<String>>();
        for (int i = 0; i < imageIds.size(); i++) {
            final String imageId = imageIds.get(i);
            final String sha = Sha256Files.sha256OfFile(inputRoot.resolve(imageId + ".tif"));
            final CoreSegmentId id = CoreSegmentId.parse(imageId);
            rows.add(Arrays.asList(
                    Integer.toString(BatchManifestSchema.SCHEMA_VERSION),
                    Integer.toString(i + 1), imageId, id.plotId(), id.treeId(),
                    String.valueOf(id.coreLabel()), id.coreId(), Integer.toString(id.segmentOrder()),
                    imageId + ".tif", imageId + ".tif", sha, "10",
                    MACHINE_ID, "0", "PENDING", "0", "NA", "NA", "NA", "NA",
                    "0", BatchManifestSchema.datasetShardRelativePath(MACHINE_ID), config.sha256()));
        }
        final Path manifest = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(MACHINE_ID));
        Files.createDirectories(manifest.getParent());
        ProductionCsvStore.writeTableAtomically(manifest, BatchManifestSchema.HEADER, rows);
    }

    private void writePrecheck(
            final Path batchRoot,
            final BatchConfiguration config,
            final Path inputRoot,
            final String imageId,
            final BatchPrecheckStatus status) throws Exception {
        final Path path = batchRoot
                .resolve(BatchPrecheckCsvSchema.machinePrecheckRoot(MACHINE_ID))
                .resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(imageId)
                .resolve("result.properties");
        Files.createDirectories(path.getParent());
        BatchPrecheckResult.builder()
                .status(status)
                .message("ok")
                .reasonCode(RCsvSupport.NA)
                .imageId(imageId)
                .machineId(MACHINE_ID)
                .sourceFilename(imageId + ".tif")
                .sourceRelativePath(imageId + ".tif")
                .sourceSha256(Sha256Files.sha256OfFile(inputRoot.resolve(imageId + ".tif")))
                .configurationSha256(config.sha256())
                .imageSize(100, 80)
                .maskStats(Integer.valueOf(1), Integer.valueOf(1), 0.5, 0.5,
                        Long.valueOf(1L), 0.0, Integer.valueOf(1))
                .crop(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(10), Integer.valueOf(10))
                .scaleBar("TERMINAL_TICK_CENTERLINES", "HIGH", 0.9, 1.0, 1.0, 1.0, 1.0, 0.0)
                .sourceChecksums(Integer.valueOf(1), Integer.valueOf(1), Boolean.TRUE)
                .previews(RCsvSupport.NA, RCsvSupport.NA)
                .processingTimeMs(1L)
                .build()
                .write(path);
    }
}
