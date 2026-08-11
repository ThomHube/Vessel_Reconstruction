package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckSupervisorTest {

    @TempDir
    Path tempDir;

    @Test
    void preserveManifestOrder() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("order"), true,
                Arrays.asList("0101A1", "0102A1", "0201A1"));
        final RecordingLauncher launcher = RecordingLauncher.successForAll();
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(0, summary.exitCode);
        assertEquals(3, summary.success);
        final List<List<String>> table = ProductionCsvStore.readTable(summary.resultsCsv);
        assertEquals("0101A1", table.get(1).get(2));
        assertEquals("0102A1", table.get(2).get(2));
        assertEquals("0201A1", table.get(3).get(2));
        assertEquals("1", table.get(1).get(1));
        assertEquals("2", table.get(2).get(1));
        assertEquals("3", table.get(3).get(1));
    }

    @Test
    void continueAfterReviewRequired() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("review"), true,
                Arrays.asList("0101A1", "0101A2"));
        final RecordingLauncher launcher = RecordingLauncher.withStatuses(
                BatchPrecheckStatus.REVIEW_REQUIRED_MASK,
                BatchPrecheckStatus.PRECHECK_SUCCESS);
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(2, launcher.starts.get());
        assertEquals(10, summary.exitCode);
        assertEquals(1, summary.reviewRequired);
        assertEquals(1, summary.success);
        assertEquals(2, ProductionCsvStore.readTable(summary.resultsCsv).size() - 1);
    }

    @Test
    void continueAfterFailureWhenContinueTrue() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("cont-true"), true,
                Arrays.asList("0101A1", "0101A2"));
        final RecordingLauncher launcher = RecordingLauncher.withStatuses(
                BatchPrecheckStatus.FAILED_UNEXPECTED,
                BatchPrecheckStatus.PRECHECK_SUCCESS);
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(2, launcher.starts.get());
        assertEquals(11, summary.exitCode);
        assertEquals(1, summary.failed);
        assertEquals(1, summary.success);
    }

    @Test
    void stopWhenContinueFalse() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("cont-false"), false,
                Arrays.asList("0101A1", "0101A2"));
        final RecordingLauncher launcher = RecordingLauncher.withStatuses(
                BatchPrecheckStatus.FAILED_UNEXPECTED,
                BatchPrecheckStatus.PRECHECK_SUCCESS);
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(1, launcher.starts.get());
        assertEquals(11, summary.exitCode);
        assertEquals(1, summary.failed);
        assertEquals(1, ProductionCsvStore.readTable(summary.resultsCsv).size() - 1);
    }

    @Test
    void rejectsSchemaVersionOneManifest() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("schema-v1"), true,
                Collections.singletonList("0101A1"));
        final Path manifest = h.batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename("machine_01"));
        final List<List<String>> table = ProductionCsvStore.readTable(manifest);
        final List<String> row = new ArrayList<String>(table.get(1));
        row.set(BatchManifestSchema.HEADER.indexOf("schema_version"), "1");
        ProductionCsvStore.writeTableAtomically(
                manifest, BatchManifestSchema.HEADER, Collections.singletonList(row));
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BatchPrecheckSupervisor.readMachineManifest(
                        manifest, "machine_01", h.config.sha256()));
        assertTrue(ex.getMessage().contains("schema_version"));
        assertTrue(ex.getMessage().contains("2"));
    }

    @Test
    void timeoutTermination() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("timeout"), true,
                Collections.singletonList("0101A1"));
        final RecordingLauncher launcher = new RecordingLauncher() {
            @Override
            Process createProcess(final List<String> command) {
                return new TimeoutProcess();
            }
        };
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(1, launcher.starts.get());
        assertEquals(11, summary.exitCode);
        assertEquals(1, summary.failed);
        final List<List<String>> table = ProductionCsvStore.readTable(summary.resultsCsv);
        assertEquals(BatchPrecheckStatus.FAILED_TIMEOUT.name(), table.get(1).get(8));
    }

    @Test
    void validPriorSuccessSkipped() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("skip"), true,
                Arrays.asList("0101A1", "0101A2"));
        final RecordingLauncher first = RecordingLauncher.successForAll();
        BatchPrecheckSupervisor.run(h.request(first));
        assertEquals(2, first.starts.get());

        final RecordingLauncher second = RecordingLauncher.successForAll();
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(second));
        assertEquals(0, second.starts.get());
        assertEquals(0, summary.exitCode);
        assertEquals(2, summary.success);
    }

    @Test
    void staleConfigurationRerun() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("stale-cfg"), true,
                Collections.singletonList("0101A1"));
        BatchPrecheckSupervisor.run(h.request(RecordingLauncher.successForAll()));

        final List<List<String>> prior = ProductionCsvStore.readTable(
                h.batchRoot.resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01")));
        final List<String> row = new ArrayList<String>(prior.get(1));
        row.set(7, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        ProductionCsvStore.writeTableAtomically(
                h.batchRoot.resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01")),
                BatchPrecheckCsvSchema.HEADER,
                Collections.singletonList(row));

        final RecordingLauncher launcher = RecordingLauncher.successForAll();
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(1, launcher.starts.get());
        assertEquals(0, summary.exitCode);
    }

    @Test
    void staleSourceRerun() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("stale-src"), true,
                Collections.singletonList("0101A1"));
        BatchPrecheckSupervisor.run(h.request(RecordingLauncher.successForAll()));

        final List<List<String>> prior = ProductionCsvStore.readTable(
                h.batchRoot.resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01")));
        final List<String> row = new ArrayList<String>(prior.get(1));
        row.set(6, "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        ProductionCsvStore.writeTableAtomically(
                h.batchRoot.resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01")),
                BatchPrecheckCsvSchema.HEADER,
                Collections.singletonList(row));

        final RecordingLauncher launcher = RecordingLauncher.successForAll();
        final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(h.request(launcher));
        assertEquals(1, launcher.starts.get());
        assertEquals(0, summary.exitCode);
    }

    @Test
    void noDuplicateRows() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("dedupe"), true,
                Arrays.asList("0101A1", "0101A2"));
        BatchPrecheckSupervisor.run(h.request(RecordingLauncher.successForAll()));
        BatchPrecheckSupervisor.run(h.request(RecordingLauncher.successForAll()));
        final List<List<String>> table = ProductionCsvStore.readTable(
                h.batchRoot.resolve(BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01")));
        assertEquals(3, table.size());
        final Set<String> ids = new HashSet<String>();
        ids.add(table.get(1).get(2));
        ids.add(table.get(2).get(2));
        assertEquals(2, ids.size());
    }

    @Test
    void atomicCsvUpdateFailurePreservesPrevious() throws Exception {
        final Harness h = Harness.create(tempDir.resolve("atomic"), true,
                Arrays.asList("0101A1", "0101A2"));
        final Path resultsCsv = h.batchRoot.resolve(
                BatchPrecheckCsvSchema.resultsCsvRelativePath("machine_01"));
        final Path machineRoot = resultsCsv.getParent();

        final AtomicInteger calls = new AtomicInteger();
        final BatchPrecheckSupervisor.ProcessLauncher launcher =
                new BatchPrecheckSupervisor.ProcessLauncher() {
                    @Override
                    public Process start(final List<String> command) throws java.io.IOException {
                        final int n = calls.incrementAndGet();
                        try {
                            RecordingLauncher.writeResultFromCommand(
                                    command, BatchPrecheckStatus.PRECHECK_SUCCESS);
                        } catch (final java.io.IOException ex) {
                            throw ex;
                        } catch (final Exception ex) {
                            throw new java.io.IOException(ex);
                        }
                        if (n == 2) {
                            final Set<PosixFilePermission> readOnly = EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_EXECUTE);
                            Files.setPosixFilePermissions(machineRoot, readOnly);
                        }
                        return new ImmediateExitProcess();
                    }
                };

        try {
            final BatchPrecheckSupervisor.SupervisorException ex = assertThrows(
                    BatchPrecheckSupervisor.SupervisorException.class,
                    () -> BatchPrecheckSupervisor.run(h.request(launcher)));
            assertEquals(16, ex.exitCode);
            assertTrue(Files.isRegularFile(resultsCsv));
            final List<List<String>> table = ProductionCsvStore.readTable(resultsCsv);
            assertEquals(2, table.size());
            assertEquals("0101A1", table.get(1).get(2));
            assertTrue(Files.readAllBytes(resultsCsv).length > 0);
        } finally {
            try {
                Files.setPosixFilePermissions(machineRoot, EnumSet.allOf(PosixFilePermission.class));
            } catch (final Exception ignored) {
                // best effort cleanup
            }
        }
    }

    @Test
    void cliArgumentValidation() {
        assertEquals(BatchPrecheckSupervisorCli.EXIT_INVALID_ARGS,
                BatchPrecheckSupervisorCli.run(new String[]{}));
        assertEquals(BatchPrecheckSupervisorCli.EXIT_INVALID_ARGS,
                BatchPrecheckSupervisorCli.run(new String[]{"--input-root"}));
        assertEquals(BatchPrecheckSupervisorCli.EXIT_INVALID_ARGS,
                BatchPrecheckSupervisorCli.run(new String[]{
                        "--input-root", "/tmp",
                        "--batch-root", "/tmp",
                        "--machine-id", "machine_01",
                        "--config", "/tmp/c.properties",
                        "--fiji-launcher", "/tmp/fiji",
                        "--unknown", "1"
                }));
        assertEquals(BatchPrecheckSupervisorCli.EXIT_INVALID_ARGS,
                BatchPrecheckSupervisorCli.run(new String[]{
                        "--input-root", "/tmp",
                        "--batch-root", "/tmp",
                        "--machine-id", "machine_01",
                        "--config", "/tmp/c.properties"
                }));
    }

    @Test
    void buildFijiCommandContainsRequiredFlags() {
        final Path fiji = tempDir.resolve("ImageJ-linux64");
        final Path job = tempDir.resolve("job.properties");
        final List<String> command = BatchPrecheckSupervisor.buildFijiCommand(fiji, job);
        assertEquals(fiji.toAbsolutePath().normalize().toString(), command.get(0));
        assertTrue(command.contains("--headless"));
        assertTrue(command.contains("--full-classpath"));
        assertTrue(command.contains("--main-class"));
        assertTrue(command.contains("vesselreconstruction.ellipse.BatchPrecheckImagePlugin"));
        assertEquals(job.toAbsolutePath().normalize().toString(), command.get(command.size() - 1));
    }

    private static final class Harness {
        final Path inputRoot;
        final Path batchRoot;
        final Path configPath;
        final Path fijiLauncher;
        final BatchConfiguration config;

        private Harness(
                final Path inputRoot,
                final Path batchRoot,
                final Path configPath,
                final Path fijiLauncher,
                final BatchConfiguration config) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.configPath = configPath;
            this.fijiLauncher = fijiLauncher;
            this.config = config;
        }

        static Harness create(
                final Path root,
                final boolean continueAfterFailure,
                final List<String> imageIds) throws Exception {
            final Path inputRoot = root.resolve("input");
            final Path batchRoot = root.resolve("batch");
            Files.createDirectories(inputRoot);
            Files.createDirectories(batchRoot.resolve(BatchManifestSchema.DIR_MACHINES));

            final Map<String, String> map = BatchTestConfigs.validMap();
            map.put(BatchConfiguration.KEY_PRECHECK_CONTINUE, Boolean.toString(continueAfterFailure));
            final BatchConfiguration config = BatchConfiguration.fromMap(map);
            final Path configPath = root.resolve("batch.properties");
            Files.write(configPath, config.canonicalText().getBytes(StandardCharsets.UTF_8));

            final List<List<String>> rows = new ArrayList<List<String>>();
            for (int i = 0; i < imageIds.size(); i++) {
                final String imageId = imageIds.get(i);
                final Path source = inputRoot.resolve(imageId + ".tif");
                Files.write(source, ("tiff-" + imageId).getBytes(StandardCharsets.UTF_8));
                final String sha = Sha256Files.sha256OfFile(source);
                final CoreSegmentId id = CoreSegmentId.parse(imageId);
                rows.add(Arrays.asList(
                        Integer.toString(BatchManifestSchema.SCHEMA_VERSION),
                        Integer.toString(i + 1),
                        imageId,
                        id.plotId(),
                        id.treeId(),
                        String.valueOf(id.coreLabel()),
                        id.coreId(),
                        Integer.toString(id.segmentOrder()),
                        imageId + ".tif",
                        imageId + ".tif",
                        sha,
                        Long.toString(Files.size(source)),
                        "machine_01",
                        Integer.toString(i + 1),
                        BatchManifestSchema.STATUS_PENDING,
                        "0",
                        RCsvSupport.NA,
                        RCsvSupport.NA,
                        RCsvSupport.NA,
                        RCsvSupport.NA,
                        RCsvSupport.NA,
                        BatchManifestSchema.datasetShardRelativePath("machine_01"),
                        config.sha256()));
            }
            ProductionCsvStore.writeTableAtomically(
                    batchRoot.resolve(BatchManifestSchema.DIR_MACHINES)
                            .resolve(BatchManifestSchema.machineManifestFilename("machine_01")),
                    BatchManifestSchema.HEADER,
                    rows);

            final Path fiji = root.resolve("fiji-stub");
            Files.write(fiji, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            return new Harness(inputRoot, batchRoot, configPath, fiji, config);
        }

        BatchPrecheckSupervisor.Request request(
                final BatchPrecheckSupervisor.ProcessLauncher launcher) {
            return new BatchPrecheckSupervisor.Request(
                    inputRoot, batchRoot, "machine_01", configPath, fijiLauncher, launcher);
        }
    }

    private abstract static class RecordingLauncher
            implements BatchPrecheckSupervisor.ProcessLauncher {
        final AtomicInteger starts = new AtomicInteger();

        static RecordingLauncher successForAll() {
            return new RecordingLauncher() {
                @Override
                Process createProcess(final List<String> command) throws Exception {
                    writeResultFromCommand(command, BatchPrecheckStatus.PRECHECK_SUCCESS);
                    return new ImmediateExitProcess();
                }
            };
        }

        static RecordingLauncher withStatuses(final BatchPrecheckStatus... statuses) {
            final List<BatchPrecheckStatus> list = Arrays.asList(statuses);
            final AtomicInteger index = new AtomicInteger();
            return new RecordingLauncher() {
                @Override
                Process createProcess(final List<String> command) throws Exception {
                    final int i = Math.min(index.getAndIncrement(), list.size() - 1);
                    writeResultFromCommand(command, list.get(i));
                    return new ImmediateExitProcess();
                }
            };
        }

        @Override
        public final Process start(final List<String> command) throws java.io.IOException {
            starts.incrementAndGet();
            try {
                return createProcess(command);
            } catch (final java.io.IOException ex) {
                throw ex;
            } catch (final Exception ex) {
                throw new java.io.IOException(ex);
            }
        }

        abstract Process createProcess(List<String> command) throws Exception;

        static void writeResultFromCommand(
                final List<String> command,
                final BatchPrecheckStatus status) throws Exception {
            final Path jobPath = java.nio.file.Paths.get(command.get(command.size() - 1));
            final BatchPrecheckJob job = BatchPrecheckJob.load(jobPath);
            BatchPrecheckResult.builder()
                    .status(status)
                    .message(status.name())
                    .reasonCode(status.isSuccess() ? RCsvSupport.NA : status.name())
                    .imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .sourceFilename(job.sourcePath().getFileName().toString())
                    .sourceRelativePath(job.sourceRelativePath())
                    .sourceSha256(job.sourceSha256())
                    .configurationSha256(job.configurationSha256())
                    .imageSize(10, 10)
                    .processingTimeMs(1L)
                    .build()
                    .write(job.resultPath());
        }
    }

    private static class ImmediateExitProcess extends Process {
        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(final long timeout, final TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    private static final class TimeoutProcess extends Process {
        private volatile boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            while (!destroyed) {
                Thread.sleep(10L);
            }
            return 1;
        }

        @Override
        public boolean waitFor(final long timeout, final TimeUnit unit) {
            if (!destroyed) {
                return false;
            }
            return true;
        }

        @Override
        public int exitValue() {
            if (!destroyed) {
                throw new IllegalThreadStateException("not exited");
            }
            return 1;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed;
        }
    }
}
