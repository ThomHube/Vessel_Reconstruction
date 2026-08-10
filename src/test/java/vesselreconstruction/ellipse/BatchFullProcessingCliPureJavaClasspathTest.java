package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves finalized-dataset inspection and the parent CLI work without ImageJ
 * on the runtime classpath ({@code java -cp target/classes}).
 */
class BatchFullProcessingCliPureJavaClasspathTest {

    private static final String IMAGE_ID = "010101";
    private static final String MACHINE_ID = "machine_01";

    @TempDir
    Path tempDir;

    @Test
    void inspectFinalizedDatasetDoesNotRequireImageProcessor() throws Exception {
        final Path classesDir = Paths.get("target/classes").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(classesDir), "target/classes must exist after compile");

        final URLClassLoader loader = new URLClassLoader(
                new URL[] {classesDir.toUri().toURL()},
                /* parent */ null);
        try {
            assertThrows(ClassNotFoundException.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    Class.forName("ij.process.ImageProcessor", false, loader);
                }
            });
            // Initializing the ImageJ-dependent writer must fail without Fiji jars.
            assertThrows(Throwable.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    Class.forName(
                            "vesselreconstruction.ellipse.AllLuminaDatasetWriter", true, loader);
                }
            });

            final Path datasetRoot = tempDir.resolve("dataset-isolated");
            final byte[] sourceBytes = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
            final String sourceSha = BatchProductionOutputValidatorTest.seedCompleteDataset(
                    datasetRoot, sourceBytes);

            final Class<?> validatorClass = Class.forName(
                    "vesselreconstruction.ellipse.BatchProductionOutputValidator", true, loader);
            final Method inspect = validatorClass.getMethod(
                    "inspectFinalizedDataset",
                    Path.class, String.class, String.class, String.class, int.class, boolean.class);
            final Object inspection = inspect.invoke(
                    null, datasetRoot, IMAGE_ID, sourceSha, "classifier-sha",
                    Integer.valueOf(1000), Boolean.TRUE);

            final Object state = inspection.getClass().getField("state").get(inspection);
            assertEquals("FINAL_DATASET_COMPLETE", state.toString());

            // ImageJ must still be absent after inspection.
            assertThrows(ClassNotFoundException.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    Class.forName("ij.process.ImageProcessor", false, loader);
                }
            });
            assertThrows(Throwable.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    Class.forName(
                            "vesselreconstruction.ellipse.AllLuminaDatasetWriter", true, loader);
                }
            });
        } finally {
            loader.close();
        }
    }

    @Test
    void completedDatasetCliSubprocessUsesProductionClassesOnly() throws Exception {
        final Path classesDir = Paths.get("target/classes").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(classesDir), "target/classes must exist after compile");

        final Path inputRoot = tempDir.resolve("input");
        final Path batchRoot = tempDir.resolve("batch");
        Files.createDirectories(inputRoot);
        Files.createDirectories(batchRoot);

        final Path sourcePath = inputRoot.resolve(IMAGE_ID + ".tif");
        Files.write(sourcePath, "tiff-bytes".getBytes(StandardCharsets.UTF_8));
        final String sourceSha = Sha256Files.sha256OfFile(sourcePath);

        final Path classifierPath = inputRoot.resolve("classifier.model");
        Files.write(classifierPath, "model-bytes".getBytes(StandardCharsets.UTF_8));

        final Path configPath = batchRoot.resolve("batch.properties");
        Files.write(configPath, BatchTestConfigs.validPropertiesText().getBytes(StandardCharsets.UTF_8));
        final BatchConfiguration config = BatchConfiguration.load(configPath);

        final Path manifestPath = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(MACHINE_ID));
        Files.createDirectories(manifestPath.getParent());
        final List<String> row = Arrays.asList(
                "1", "1", IMAGE_ID, "01", "01", "01",
                IMAGE_ID + ".tif", IMAGE_ID + ".tif", sourceSha, "10",
                MACHINE_ID, "0", "PENDING", "0", "NA", "NA", "NA", "NA",
                "0", BatchManifestSchema.datasetShardRelativePath(MACHINE_ID), config.sha256());
        ProductionCsvStore.writeTableAtomically(
                manifestPath, BatchManifestSchema.HEADER,
                java.util.Collections.singletonList(row));

        final Path datasetRoot = batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(MACHINE_ID));
        BatchProductionOutputValidatorTest.seedCompleteDataset(
                datasetRoot, Files.readAllBytes(sourcePath));

        final Path fijiLauncher = tempDir.resolve("fake-fiji");
        Files.write(fijiLauncher, "#!/bin/sh\necho should-not-run\nexit 1\n".getBytes(StandardCharsets.UTF_8));
        fijiLauncher.toFile().setExecutable(true);

        final String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final List<String> command = new ArrayList<String>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classesDir.toString());
        command.add("vesselreconstruction.ellipse.BatchFullProcessingCli");
        command.add("--input-root");
        command.add(inputRoot.toString());
        command.add("--batch-root");
        command.add(batchRoot.toString());
        command.add("--machine-id");
        command.add(MACHINE_ID);
        command.add("--image-id");
        command.add(IMAGE_ID);
        command.add("--config");
        command.add(configPath.toString());
        command.add("--classifier");
        command.add(classifierPath.toString());
        command.add("--fiji-launcher");
        command.add(fijiLauncher.toString());
        command.add("--tile-profile-index");
        command.add("0");

        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final InputStream in = process.getInputStream();
        try {
            final byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                output.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        final boolean finished = process.waitFor(60L, TimeUnit.SECONDS);
        assertTrue(finished, "CLI subprocess timed out");
        final String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "stdout/stderr:\n" + text);
        assertTrue(text.contains(
                "FULL_DATASET_ALREADY_COMPLETE: reusing classification and reconstruction"),
                text);
        assertFalse(text.contains("NoClassDefFoundError"), text);
        assertFalse(text.contains("ij/process/ImageProcessor"), text);

        final Path attemptRoot = batchRoot.resolve("full").resolve(MACHINE_ID)
                .resolve(IMAGE_ID).resolve("attempt_1");
        final Path resultPath = attemptRoot.resolve("result.properties");
        assertTrue(Files.isRegularFile(resultPath));
        assertFalse(Files.exists(attemptRoot.resolve("classification")));
        assertFalse(Files.exists(attemptRoot.resolve("reconstruction")));

        final BatchFullProcessingResult combined = BatchFullProcessingResult.load(resultPath);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, combined.status());
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_REQUIRED_OUTPUTS_VALIDATED));
        assertEquals(RCsvSupport.TRUE,
                combined.get(BatchFullProcessingResult.KEY_SOURCE_UNCHANGED));
        assertEquals("class-run-1",
                combined.get(BatchFullProcessingResult.KEY_CLASSIFICATION_RUN_ID));
        assertEquals("recon-run-1",
                combined.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID));
        assertNotNull(combined.get(BatchFullProcessingResult.KEY_TOTAL_COMPONENTS));
    }
}
