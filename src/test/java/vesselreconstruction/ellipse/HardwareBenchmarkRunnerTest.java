package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkRunnerTest {

    @Test
    void timeoutIsDerivedFromBaselineWallClock() {
        assertEquals(HardwareBenchmarkRunner.MIN_TIMEOUT_MS, HardwareBenchmarkRunner.timeoutMs(0L));
        assertEquals(HardwareBenchmarkRunner.MIN_TIMEOUT_MS, HardwareBenchmarkRunner.timeoutMs(1000L));
        assertEquals(80_000L, HardwareBenchmarkRunner.timeoutMs(10_000L));
        assertEquals(HardwareBenchmarkRunner.MAX_TIMEOUT_MS,
                HardwareBenchmarkRunner.timeoutMs(60_000L));
    }

    @Test
    void realTimeoutIsDerivedFromRealBaselineNotSyntheticCap() {
        assertEquals(HardwareBenchmarkRunner.REAL_MIN_TIMEOUT_MS,
                HardwareBenchmarkRunner.timeoutMsReal(0L));
        assertEquals(HardwareBenchmarkRunner.REAL_MIN_TIMEOUT_MS,
                HardwareBenchmarkRunner.timeoutMsReal(5_000L));
        assertEquals(1_200_000L, HardwareBenchmarkRunner.timeoutMsReal(150_000L));
        assertEquals(HardwareBenchmarkRunner.REAL_MAX_TIMEOUT_MS,
                HardwareBenchmarkRunner.timeoutMsReal(900_000L));
        assertTrue(HardwareBenchmarkRunner.timeoutMsReal(100_000L)
                > HardwareBenchmarkRunner.MAX_TIMEOUT_MS);
    }

    @Test
    void checksumTreeDetectsMutation(@TempDir final Path dir) throws Exception {
        final Path input = dir.resolve("input");
        HardwareBenchmarkProbe.writeDeterministicInputs(input, 2);
        final Map<String, String> before = HardwareBenchmarkRunner.checksumTree(input);
        assertEquals(2, before.size());
        Files.write(input.resolve("tile_00.bin"), new byte[] {1, 2, 3}, StandardOpenOption.APPEND);
        final Map<String, String> after = HardwareBenchmarkRunner.checksumTree(input);
        assertFalse(before.equals(after));
    }

    @Test
    void successfulProbeLeavesInputsUnchanged(@TempDir final Path dir) throws Exception {
        final Path outputRoot = dir.resolve("benchmark");
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new SuccessFactory()),
                candidate("ok_1t_512m"),
                15_000L);
        assertTrue(result.safeSuccess());
        assertEquals(1024, result.tileWidthPx);
        assertEquals(1024, result.tileHeightPx);
        final Path input = outputRoot.resolve("runs").resolve("ok_1t_512m").resolve("input");
        assertTrue(Files.isDirectory(input));
        final Map<String, String> checksums = HardwareBenchmarkRunner.checksumTree(input);
        assertEquals(HardwareBenchmarkRunner.TILE_COUNT, checksums.size());
    }

    @Test
    void mutatedInputsAreUnsafe(@TempDir final Path dir) throws Exception {
        final Path outputRoot = dir.resolve("benchmark");
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new MutatingFactory()),
                candidate("mut_1t_512m"),
                15_000L);
        assertTrue(result.unsafe);
        assertEquals("input checksum changed", result.unsafeReason);
        assertFalse(result.safeSuccess());
    }

    @Test
    void nonZeroExitIsUnsafe(@TempDir final Path dir) throws Exception {
        final Path outputRoot = dir.resolve("benchmark");
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new ExitFactory(7)),
                candidate("fail_1t_512m"),
                15_000L);
        assertTrue(result.unsafe);
        assertTrue(result.unsafeReason.contains("non-zero exit"));
        assertEquals(7, result.exitCode);
    }

    @Test
    void timeoutDestroysOnlyTheOwnedChild(@TempDir final Path dir) throws Exception {
        final Path outputRoot = dir.resolve("benchmark");
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new SleepFactory()),
                candidate("slow_1t_512m"),
                800L);
        assertTrue(result.unsafe);
        assertEquals("timeout", result.unsafeReason);
        assertFalse(result.safeSuccess());
    }

    @Test
    void optionalScientificTiffsAreChecksummedAndNotModified(@TempDir final Path dir)
            throws Exception {
        final Path tiffs = dir.resolve("tiffs");
        Files.createDirectories(tiffs);
        final Path original = tiffs.resolve("9999A1.tif");
        Files.write(original, "FAKE-TIFF-NOT-PRODUCTION".getBytes(StandardCharsets.UTF_8));
        final String before = Sha256Files.sha256OfFile(original);
        final Path outputRoot = dir.resolve("benchmark");
        HardwareBenchmarkRunner.runOne(
                new HardwareBenchmarkRunner.Request(
                        outputRoot,
                        Paths.get("java"),
                        dir,
                        dir.resolve("missing-proc"),
                        HardwareBenchmarkFixtures.inventory(2, 2, 2L << 30),
                        new SuccessFactory(),
                        new StringBuilder(),
                        tiffs),
                candidate("ref_1t_512m"),
                15_000L);
        assertEquals(before, Sha256Files.sha256OfFile(original));
        final Path note = outputRoot.resolve("runs").resolve("ref_1t_512m")
                .resolve("reference_dataset_checksums.sha256");
        assertTrue(Files.isRegularFile(note));
        final String text = new String(Files.readAllBytes(note), StandardCharsets.UTF_8);
        assertTrue(text.contains(before));
        assertTrue(text.contains("9999A1.tif"));
    }

    @Test
    void isolatedRunDirectoryDoesNotReuseSiblingState(@TempDir final Path dir) throws Exception {
        final Path outputRoot = dir.resolve("benchmark");
        HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new SuccessFactory()),
                candidate("a_1t_512m"),
                15_000L);
        HardwareBenchmarkRunner.runOne(
                request(outputRoot, dir, new SuccessFactory()),
                candidate("b_1t_512m"),
                15_000L);
        assertTrue(Files.isDirectory(outputRoot.resolve("runs").resolve("a_1t_512m")));
        assertTrue(Files.isDirectory(outputRoot.resolve("runs").resolve("b_1t_512m")));
        assertFalse(Files.exists(outputRoot.resolve("runs").resolve("a_1t_512m")
                .resolve("b_1t_512m")));
    }

    @Test
    void preexistingHostSwapDoesNotMakeASuccessfulRunUnsafe(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        final long preexisting = 510L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, preexisting);
        HardwareBenchmarkProcFixtures.writePid(proc, 9999,
                512L * HardwareBenchmarkProcFixtures.MIB, "");
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(dir.resolve("benchmark"), dir, proc,
                        new SwapScenarioFactory(proc, preexisting, 0L)),
                candidate("pre_1t_512m"),
                15_000L);
        assertTrue(result.safeSuccess(), result.unsafeReason);
        assertEquals(preexisting, result.startSwapUsedBytes);
        assertEquals(0L, result.processTreeSwapGrowthBytes());
        assertEquals(0L, result.hostSwapGrowthBytes());
        assertEquals(preexisting, result.hostSwapGrowthBytes() + result.startSwapUsedBytes);
    }

    @Test
    void unrelatedHostSwapGrowthDoesNotRejectASuccessfulRun(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        final long start = 510L * HardwareBenchmarkProcFixtures.MIB;
        final long peak = 617L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, start);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(dir.resolve("benchmark"), dir, proc,
                        new SwapScenarioFactory(proc, peak, 0L)),
                candidate("host_1t_512m"),
                15_000L);
        assertTrue(result.safeSuccess(), result.unsafeReason);
        assertTrue(result.hostSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
        assertEquals(0L, result.processTreeSwapGrowthBytes());
        assertEquals(start, result.startSwapUsedBytes);
        assertEquals(peak, result.peakSwapUsedBytes);
        assertEquals(Long.toString(start), result.toCsvRow().get("host_swap_start_bytes"));
        assertEquals(Long.toString(peak), result.toCsvRow().get("host_swap_peak_bytes"));
        assertEquals("0", result.toCsvRow().get("process_tree_swap_growth_bytes"));
    }

    @Test
    void ownedProcessTreeSwapGrowthIsUnsafe(@TempDir final Path dir) throws Exception {
        final Path proc = dir.resolve("proc");
        final long host = 510L * HardwareBenchmarkProcFixtures.MIB;
        final long owned = 128L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, host);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(dir.resolve("benchmark"), dir, proc,
                        new SwapScenarioFactory(proc, host, owned)),
                candidate("tree_1t_512m"),
                15_000L);
        assertTrue(result.unsafe);
        assertEquals("swap growth", result.unsafeReason);
        assertFalse(result.safeSuccess());
        assertEquals(0L, result.hostSwapGrowthBytes());
        assertTrue(result.processTreeSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
        assertEquals(owned, result.processTreeSwapPeakBytes);
        assertEquals(0L, result.processTreeSwapStartBytes);
    }

    @Test
    void zeroOwnedProcessSwapGrowthIsSafeEvenWhenHostSwapGrew(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, 510L * HardwareBenchmarkProcFixtures.MIB);
        final HardwareBenchmarkRunResult result = HardwareBenchmarkRunner.runOne(
                request(dir.resolve("benchmark"), dir, proc,
                        new SwapScenarioFactory(proc, 617L * HardwareBenchmarkProcFixtures.MIB, 0L)),
                candidate("zero_1t_512m"),
                15_000L);
        assertTrue(result.safeSuccess(), result.unsafeReason);
        assertEquals(0L, result.processTreeSwapGrowthBytes());
        assertEquals(0L, result.processTreeSwapPeakBytes);
        assertTrue(result.hostSwapGrowthBytes() > 0L);
    }

    private static HardwareBenchmarkRunner.Request request(
            final Path outputRoot,
            final Path dir,
            final HardwareBenchmarkRunner.ProbeProcessFactory factory) {
        return request(outputRoot, dir, dir.resolve("missing-proc"), factory);
    }

    private static HardwareBenchmarkRunner.Request request(
            final Path outputRoot,
            final Path dir,
            final Path procRoot,
            final HardwareBenchmarkRunner.ProbeProcessFactory factory) {
        return new HardwareBenchmarkRunner.Request(
                outputRoot,
                Paths.get("java"),
                dir,
                procRoot,
                HardwareBenchmarkFixtures.inventory(2, 2, 2L << 30),
                factory,
                new StringBuilder(),
                null);
    }

    private static HardwareBenchmarkPlanner.Candidate candidate(final String id) {
        return new HardwareBenchmarkPlanner.Candidate(
                id, HardwareBenchmarkPlanner.PHASE_BASELINE, 1, 512);
    }

    private static final class SuccessFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            writeProbeResult(command, true, 8);
            return new ProcessBuilder("true").start();
        }
    }

    private static final class MutatingFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            writeProbeResult(command, true, 8);
            final Path input = Paths.get(arg(command, "--input-dir"));
            Files.write(input.resolve("tile_00.bin"), new byte[] {9}, StandardOpenOption.APPEND);
            return new ProcessBuilder("true").start();
        }
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

    private static final class SwapScenarioFactory implements HardwareBenchmarkRunner.ProbeProcessFactory {
        private final Path procRoot;
        private final long hostSwapAfterStartBytes;
        private final long ownedVmSwapBytes;

        private SwapScenarioFactory(
                final Path procRoot,
                final long hostSwapAfterStartBytes,
                final long ownedVmSwapBytes) {
            this.procRoot = procRoot;
            this.hostSwapAfterStartBytes = hostSwapAfterStartBytes;
            this.ownedVmSwapBytes = ownedVmSwapBytes;
        }

        @Override
        public Process start(final List<String> command, final Path workDir) throws IOException {
            writeProbeResult(command, true, 8);
            final Process process = new ProcessBuilder("true").start();
            final int pid = HardwareBenchmarkMonitor.readPid(process);
            HardwareBenchmarkProcFixtures.writeMeminfo(procRoot, hostSwapAfterStartBytes);
            HardwareBenchmarkProcFixtures.writePid(procRoot, pid, ownedVmSwapBytes, "");
            HardwareBenchmarkProcFixtures.writePid(
                    procRoot, 9999, 512L * HardwareBenchmarkProcFixtures.MIB, "");
            return process;
        }
    }

    private static void writeProbeResult(
            final List<String> command,
            final boolean success,
            final int tiles) throws IOException {
        final Path output = Paths.get(arg(command, "--output"));
        HardwareBenchmarkProbe.writeResultProperties(output, new HardwareBenchmarkProbe.Result(
                success, tiles, 100L, 80.0d, "SUCCESS"));
    }

    private static String arg(final List<String> command, final String key) {
        return command.get(command.indexOf(key) + 1);
    }
}
