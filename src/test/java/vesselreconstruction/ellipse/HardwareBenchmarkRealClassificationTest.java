package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkRealClassificationTest {

    private static final String IMAGE_ID = HardwareBenchmarkWorkload.IMAGE_ID;
    private static final String SAMPLE_ID = "9999A";
    private static final String CLASSIFIER_SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void missingResultIsProcessExitedButOutputInvalid(@TempDir final Path dir) throws Exception {
        final Path dataset = dir.resolve("dataset");
        new ProductionDatasetLayout(dataset).ensureDirectoriesExist();
        final HardwareBenchmarkClassificationSupport.OutputCheck check =
                HardwareBenchmarkClassificationSupport.validateOutput(
                        dataset, dir.resolve("missing.properties"), "deadbeef", 0);
        assertFalse(check.valid);
        assertTrue(check.reason.contains("PROCESS_EXITED_BUT_OUTPUT_INVALID"));
    }

    @Test
    void nonZeroExitIsNotSuccessfulClassification(@TempDir final Path dir) {
        final HardwareBenchmarkClassificationSupport.OutputCheck check =
                HardwareBenchmarkClassificationSupport.validateOutput(
                        dir.resolve("dataset"), dir.resolve("result.properties"), "deadbeef", 1);
        assertFalse(check.valid);
        assertTrue(check.reason.contains("non-zero Fiji exit"));
    }

    @Test
    void structurallyValidClassificationOutputIsSuccessful(@TempDir final Path dir)
            throws Exception {
        final Path dataset = dir.resolve("dataset");
        final byte[] source = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        final String sourceSha = seedClassificationDataset(dataset, source);
        final Path result = dir.resolve("result.properties");
        writeStageResult(result, BatchFullProcessingStatus.FULL_SUCCESS);
        final HardwareBenchmarkClassificationSupport.OutputCheck check =
                HardwareBenchmarkClassificationSupport.validateOutput(dataset, result, sourceSha, 0);
        assertTrue(check.valid, check.reason);
        assertEquals("SUCCESSFUL_CLASSIFICATION", check.reason);
    }

    @Test
    void executeClassificationTimesOutAndKillsOnlyOwnedChild(@TempDir final Path dir)
            throws Exception {
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request(dir, new SleepFactory()),
                candidate("slow_real"),
                800L,
                preparedRun(dir, Arrays.asList("sleep", "30"), "input-sha"));
        assertTrue(result.unsafe);
        assertEquals("timeout", result.unsafeReason);
        assertFalse(result.safeSuccess());
        assertEquals(HardwareExecutionProfile.WORKLOAD_REAL, result.workloadType);
    }

    @Test
    void executeClassificationRejectsExitZeroWithInvalidOutput(@TempDir final Path dir)
            throws Exception {
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request(dir, new ExitFactory(0)),
                candidate("invalid_out"),
                15_000L,
                preparedRun(dir, Arrays.asList("true"), "input-sha"));
        assertTrue(result.unsafe);
        assertTrue(result.unsafeReason.contains("PROCESS_EXITED_BUT_OUTPUT_INVALID"));
        assertFalse(result.safeSuccess());
        assertFalse(result.outputValid);
    }

    @Test
    void executeClassificationAcceptsValidOutputAndPropagatesClassifierHash(
            @TempDir final Path dir) throws Exception {
        final Path original = dir.resolve("original").resolve("9999A1.tif");
        Files.createDirectories(original.getParent());
        final byte[] source = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(original, source);
        final String originalSha = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkClassificationSupport.PreparedRun prepared =
                preparedRun(dir, Arrays.asList("true"), originalSha);
        Files.write(prepared.inputTiff, source);
        final HardwareBenchmarkRunner.Request request = request(
                dir,
                new ValidOutputFactory(prepared, source),
                original,
                originalSha);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request, candidate("ok_real"), 15_000L, prepared);
        assertTrue(result.safeSuccess(), result.unsafeReason);
        assertTrue(result.outputValid);
        assertEquals(CLASSIFIER_SHA, result.classifierSha256);
        assertEquals(HardwareExecutionProfile.WORKLOAD_REAL, result.workloadType);
        assertTrue(result.throughputPixelsPerSecond > 0.0d);
        assertEquals(originalSha, Sha256Files.sha256OfFile(original));
    }

    @Test
    void successfulClassificationWithUnrelatedHostSwapGrowthIsSafe(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        final long start = 510L * HardwareBenchmarkProcFixtures.MIB;
        final long peak = 617L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, start);
        final Path original = dir.resolve("original").resolve("9999A1.tif");
        Files.createDirectories(original.getParent());
        final byte[] source = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(original, source);
        final String originalSha = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkClassificationSupport.PreparedRun prepared =
                preparedRun(dir, Arrays.asList("true"), originalSha);
        Files.write(prepared.inputTiff, source);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request(dir, new ValidOutputSwapFactory(prepared, source, proc, peak, 0L),
                        original, originalSha, proc),
                candidate("ok_host_swap"),
                15_000L,
                prepared);
        assertTrue(result.safeSuccess(), result.unsafeReason);
        assertTrue(result.outputValid);
        assertTrue(result.hostSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
        assertEquals(0L, result.processTreeSwapGrowthBytes());
        assertEquals(originalSha, Sha256Files.sha256OfFile(original));
    }

    @Test
    void ownedFijiProcessTreeSwapGrowthRejectsSuccessfulClassification(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        final long host = 510L * HardwareBenchmarkProcFixtures.MIB;
        final long owned = 128L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, host);
        final Path original = dir.resolve("original").resolve("9999A1.tif");
        Files.createDirectories(original.getParent());
        final byte[] source = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(original, source);
        final String originalSha = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkClassificationSupport.PreparedRun prepared =
                preparedRun(dir, Arrays.asList("true"), originalSha);
        Files.write(prepared.inputTiff, source);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request(dir, new ValidOutputSwapFactory(prepared, source, proc, host, owned),
                        original, originalSha, proc),
                candidate("fiji_swap"),
                15_000L,
                prepared);
        assertTrue(result.outputValid);
        assertTrue(result.unsafe);
        assertEquals("swap growth", result.unsafeReason);
        assertFalse(result.safeSuccess());
        assertTrue(result.processTreeSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
        assertEquals(0L, result.hostSwapGrowthBytes());
    }

    @Test
    void mutatedClassificationInputIsUnsafe(@TempDir final Path dir) throws Exception {
        final Path original = dir.resolve("original").resolve("9999A1.tif");
        Files.createDirectories(original.getParent());
        final byte[] source = "tiff-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(original, source);
        final String originalSha = Sha256Files.sha256OfFile(original);
        final HardwareBenchmarkClassificationSupport.PreparedRun prepared =
                preparedRun(dir, Arrays.asList("true"), originalSha);
        Files.write(prepared.inputTiff, source);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.executeClassification(
                request(dir, new MutatingFactory(prepared.inputTiff), original, originalSha),
                candidate("mut_real"),
                15_000L,
                prepared);
        assertTrue(result.unsafe);
        assertEquals("input checksum changed", result.unsafeReason);
        assertEquals(originalSha, Sha256Files.sha256OfFile(original));
    }

    private static HardwareBenchmarkRunner.Request request(
            final Path dir,
            final HardwareBenchmarkRunner.ProbeProcessFactory factory) throws Exception {
        final Path original = dir.resolve("original.tif");
        Files.write(original, "orig".getBytes(StandardCharsets.UTF_8));
        return request(dir, factory, original, Sha256Files.sha256OfFile(original),
                dir.resolve("missing-proc"));
    }

    private static HardwareBenchmarkRunner.Request request(
            final Path dir,
            final HardwareBenchmarkRunner.ProbeProcessFactory factory,
            final Path original,
            final String originalSha) {
        return request(dir, factory, original, originalSha, dir.resolve("missing-proc"));
    }

    private static HardwareBenchmarkRunner.Request request(
            final Path dir,
            final HardwareBenchmarkRunner.ProbeProcessFactory factory,
            final Path original,
            final String originalSha,
            final Path procRoot) {
        final HardwareBenchmarkWorkload.Prepared workload = new HardwareBenchmarkWorkload.Prepared(
                original,
                originalSha,
                64,
                64,
                original,
                originalSha,
                64,
                64,
                false);
        return new HardwareBenchmarkRunner.Request(
                dir.resolve("benchmark"),
                Paths.get("java"),
                dir,
                procRoot,
                HardwareBenchmarkFixtures.inventory(2, 2, 2L << 30),
                factory,
                new StringBuilder(),
                null,
                dir.resolve("fiji"),
                dir.resolve("classifier.model"),
                dir.resolve("config.properties"),
                workload);
    }

    private static HardwareBenchmarkPlanner.Candidate candidate(final String id) {
        return new HardwareBenchmarkPlanner.Candidate(
                id, HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 512);
    }

    private static HardwareBenchmarkClassificationSupport.PreparedRun preparedRun(
            final Path dir,
            final List<String> command,
            final String inputSha) throws IOException {
        final Path runDir = dir.resolve("benchmark").resolve("runs").resolve("real")
                .resolve("prepared");
        Files.createDirectories(runDir);
        final Path inputDir = runDir.resolve("input");
        Files.createDirectories(inputDir);
        final Path inputTiff = inputDir.resolve(HardwareBenchmarkWorkload.SOURCE_FILENAME);
        if (!Files.isRegularFile(inputTiff)) {
            Files.write(inputTiff, "input".getBytes(StandardCharsets.UTF_8));
        }
        final Path logs = runDir.resolve("logs");
        Files.createDirectories(logs);
        final Path dataset = runDir.resolve("output").resolve("dataset");
        new ProductionDatasetLayout(dataset).ensureDirectoriesExist();
        return new HardwareBenchmarkClassificationSupport.PreparedRun(
                runDir,
                inputDir,
                inputTiff,
                runDir.resolve("production_batch.properties"),
                runDir.resolve("batch"),
                dataset,
                runDir.resolve("classification").resolve("job.properties"),
                runDir.resolve("classification").resolve("result.properties"),
                logs.resolve("classification_stdout.log"),
                logs.resolve("classification_stderr.log"),
                command,
                "config-sha",
                CLASSIFIER_SHA,
                inputSha,
                new BatchTileProfile(
                        HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX,
                        HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX));
    }

    private static String seedClassificationDataset(final Path datasetRoot, final byte[] sourceBytes)
            throws Exception {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        layout.ensureDirectoriesExist();
        final Path originalRgb = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(originalRgb, sourceBytes);
        final String sha = Sha256Files.sha256OfFile(originalRgb);
        Files.write(layout.imagesApprovedMasks().resolve(
                FilenameSanitizer.approvedMaskFilename(SAMPLE_ID, IMAGE_ID, 8)),
                "mask".getBytes(StandardCharsets.UTF_8));
        Files.write(layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename(SAMPLE_ID, IMAGE_ID)),
                "masked-rgb".getBytes(StandardCharsets.UTF_8));
        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        Files.write(maskedClassified, "masked-classified".getBytes(StandardCharsets.UTF_8));

        final List<String> imageRow = naRow(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("sample_id"), SAMPLE_ID);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("segment_id"), IMAGE_ID);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("source_rgb_sha256"), sha);
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_width_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("pixel_height_um"), "1.5");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("classification_run_id"),
                "class-run-1");
        imageRow.set(ProductionSchemas.IMAGE_MANIFEST_HEADER.indexOf("masked_classified_filename"),
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                Arrays.asList(imageRow));

        final List<String> segmentRow = naRow(ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("sample_id"), SAMPLE_ID);
        segmentRow.set(ProductionSchemas.SEGMENT_MANIFEST_HEADER.indexOf("segment_id"), IMAGE_ID);
        ProductionCsvStore.writeTableAtomically(
                layout.segmentManifest(), ProductionSchemas.SEGMENT_MANIFEST_HEADER,
                Arrays.asList(segmentRow));

        final List<String> classRun = naRow(ProductionSchemas.PROCESSING_RUNS_HEADER);
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("stage"),
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("status"),
                ProductionSchemas.RunStatus.SUCCESS.name());
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("sample_id"), SAMPLE_ID);
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("segment_id"), IMAGE_ID);
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("run_id"), "class-run-1");
        classRun.set(ProductionSchemas.PROCESSING_RUNS_HEADER.indexOf("output_filename"),
                FilenameSanitizer.maskedClassifiedFilename(SAMPLE_ID, IMAGE_ID));
        ProductionCsvStore.writeTableAtomically(
                layout.processingRuns(), ProductionSchemas.PROCESSING_RUNS_HEADER,
                Arrays.asList(classRun));
        return sha;
    }

    private static List<String> naRow(final List<String> header) {
        return new ArrayList<String>(Collections.nCopies(header.size(), RCsvSupport.NA));
    }

    private static void writeStageResult(
            final Path path,
            final BatchFullProcessingStatus status) throws IOException {
        BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.CLASSIFICATION)
                .status(status)
                .reasonCode(status.isSuccess() ? RCsvSupport.NA : status.name())
                .message(status.isSuccess() ? "SUCCESSFUL_CLASSIFICATION" : status.name())
                .imageId(IMAGE_ID)
                .machineId(HardwareExecutionProfile.PRODUCTION_MACHINE_ID)
                .attemptNumber(Integer.valueOf(1))
                .processExitCode(Integer.valueOf(status.isSuccess() ? 0 : 1))
                .outOfMemoryDetected(Boolean.FALSE)
                .stageCompleted(Boolean.valueOf(status.isSuccess()))
                .requiredOutputsValidated(Boolean.valueOf(status.isSuccess()))
                .runId("class-run-1")
                .sourceUnchanged(Boolean.TRUE)
                .processingTimeMs(1L)
                .build()
                .writeAtomic(path);
    }

    private static final class ExitFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        private final int code;

        private ExitFactory(final int code) {
            this.code = code;
        }

        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            return new ProcessBuilder("bash", "-c", "exit " + code).start();
        }
    }

    private static final class SleepFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            return new ProcessBuilder("sleep", "30").start();
        }
    }

    private static final class MutatingFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        private final Path inputTiff;

        private MutatingFactory(final Path inputTiff) {
            this.inputTiff = inputTiff;
        }

        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            Files.write(inputTiff, new byte[] {9}, StandardOpenOption.APPEND);
            return new ProcessBuilder("true").start();
        }
    }

    private static final class ValidOutputFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        private final HardwareBenchmarkClassificationSupport.PreparedRun prepared;
        private final byte[] source;

        private ValidOutputFactory(
                final HardwareBenchmarkClassificationSupport.PreparedRun prepared,
                final byte[] source) {
            this.prepared = prepared;
            this.source = source;
        }

        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            try {
                seedClassificationDataset(prepared.datasetRoot, source);
                writeStageResult(prepared.resultPath, BatchFullProcessingStatus.FULL_SUCCESS);
            } catch (final Exception ex) {
                throw new IOException(ex);
            }
            return new ProcessBuilder("true").start();
        }
    }

    private static final class ValidOutputSwapFactory
            implements HardwareBenchmarkRunner.ProbeProcessFactory {
        private final HardwareBenchmarkClassificationSupport.PreparedRun prepared;
        private final byte[] source;
        private final Path procRoot;
        private final long hostSwapAfterStartBytes;
        private final long ownedVmSwapBytes;

        private ValidOutputSwapFactory(
                final HardwareBenchmarkClassificationSupport.PreparedRun prepared,
                final byte[] source,
                final Path procRoot,
                final long hostSwapAfterStartBytes,
                final long ownedVmSwapBytes) {
            this.prepared = prepared;
            this.source = source;
            this.procRoot = procRoot;
            this.hostSwapAfterStartBytes = hostSwapAfterStartBytes;
            this.ownedVmSwapBytes = ownedVmSwapBytes;
        }

        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            try {
                seedClassificationDataset(prepared.datasetRoot, source);
                writeStageResult(prepared.resultPath, BatchFullProcessingStatus.FULL_SUCCESS);
            } catch (final Exception ex) {
                throw new IOException(ex);
            }
            final Process process = new ProcessBuilder("true").start();
            final int pid = HardwareBenchmarkMonitor.readPid(process);
            HardwareBenchmarkProcFixtures.writeMeminfo(procRoot, hostSwapAfterStartBytes);
            HardwareBenchmarkProcFixtures.writePid(procRoot, pid, ownedVmSwapBytes, "");
            HardwareBenchmarkProcFixtures.writePid(
                    procRoot, 9999, 512L * HardwareBenchmarkProcFixtures.MIB, "");
            return process;
        }
    }
}
