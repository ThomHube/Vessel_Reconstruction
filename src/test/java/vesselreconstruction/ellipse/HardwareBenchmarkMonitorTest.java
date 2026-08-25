package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkMonitorTest {

    @Test
    void readPidTracksOnlyTheOwnedChildProcess() throws Exception {
        final Process process = new ProcessBuilder("sleep", "5").start();
        try {
            final int pid = HardwareBenchmarkMonitor.readPid(process);
            assertTrue(pid > 0, "owned child pid must be tracked explicitly");
        } finally {
            HardwareBenchmarkRunner.destroyOwnedProcess(process);
        }
    }

    @Test
    void captureHostSwapBeforeStartRecordsPreexistingHostSwap(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        final long preexisting = 510L * HardwareBenchmarkProcFixtures.MIB;
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, preexisting);
        HardwareBenchmarkProcFixtures.writePid(proc, 42, 0L, "");
        final HardwareBenchmarkMonitor.Accumulator acc =
                new HardwareBenchmarkMonitor.Accumulator();
        HardwareBenchmarkMonitor.captureHostSwapBeforeStart(proc, acc);
        assertEquals(preexisting, acc.startSwap);
        assertEquals(preexisting, acc.peakSwap);
        assertEquals(0L, acc.processTreeStartSwap);
        assertEquals(0L, acc.hostSwapGrowthBytes());
    }

    @Test
    void hostSwapGrowthIsSeparateFromOwnedProcessTreeSwap(@TempDir final Path dir)
            throws Exception {
        final Path proc = dir.resolve("proc");
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, 510L * HardwareBenchmarkProcFixtures.MIB);
        HardwareBenchmarkProcFixtures.writePid(proc, 42, 0L, "43");
        HardwareBenchmarkProcFixtures.writePid(proc, 43, 0L, "");
        HardwareBenchmarkProcFixtures.writePid(proc, 999, 512L * HardwareBenchmarkProcFixtures.MIB, "");
        final HardwareBenchmarkMonitor.Accumulator acc =
                new HardwareBenchmarkMonitor.Accumulator();
        HardwareBenchmarkMonitor.captureHostSwapBeforeStart(proc, acc);
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, 617L * HardwareBenchmarkProcFixtures.MIB);
        HardwareBenchmarkMonitor.sample(proc, 42, acc);
        assertEquals(107L * HardwareBenchmarkProcFixtures.MIB, acc.hostSwapGrowthBytes());
        assertEquals(0L, acc.processTreeSwapGrowthBytes());
        assertTrue(acc.hostSwapGrowthBytes() >= HardwareBenchmarkRanker.MIN_SWAP_GROWTH_BYTES);
    }

    @Test
    void ownedProcessTreeVmSwapGrowthIsRecorded(@TempDir final Path dir) throws Exception {
        final Path proc = dir.resolve("proc");
        HardwareBenchmarkProcFixtures.writeMeminfo(proc, 510L * HardwareBenchmarkProcFixtures.MIB);
        HardwareBenchmarkProcFixtures.writePid(proc, 42, 0L, "43");
        HardwareBenchmarkProcFixtures.writePid(proc, 43, 0L, "");
        final HardwareBenchmarkMonitor.Accumulator acc =
                new HardwareBenchmarkMonitor.Accumulator();
        HardwareBenchmarkMonitor.captureHostSwapBeforeStart(proc, acc);
        HardwareBenchmarkProcFixtures.writePid(proc, 43, 128L * HardwareBenchmarkProcFixtures.MIB, "");
        HardwareBenchmarkMonitor.sample(proc, 42, acc);
        assertEquals(0L, acc.hostSwapGrowthBytes());
        assertEquals(128L * HardwareBenchmarkProcFixtures.MIB, acc.processTreeSwapGrowthBytes());
    }
}
