package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareInventoryDetectorTest {

    @Test
    void parsesMultiSocketCpuinfoWithoutHostConstants() {
        final String cpuInfo = ""
                + "processor\t: 0\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 0\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 1\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 0\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 2\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 0\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 3\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 0\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 4\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 1\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 5\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 1\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 6\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 1\n"
                + "cpu cores\t: 4\n"
                + "\n"
                + "processor\t: 7\n"
                + "model name\t: Test CPU Model\n"
                + "physical id\t: 1\n"
                + "cpu cores\t: 4\n";
        final HardwareInventoryDetector.CpuFacts cpu = HardwareInventoryDetector.parseCpu(cpuInfo);
        assertEquals("Test CPU Model", cpu.model);
        assertEquals(8, cpu.physicalCores);
        assertEquals(8, cpu.logicalCpus);
        assertEquals(1, cpu.threadsPerCore);
    }

    @Test
    void parsesMeminfoKilobytes() {
        final HardwareInventoryDetector.MemFacts mem = HardwareInventoryDetector.parseMem(
                "MemTotal:       16777216 kB\nSwapTotal:        2097152 kB\n");
        assertEquals(16777216L * 1024L, mem.ramBytes);
        assertEquals(2097152L * 1024L, mem.swapBytes);
    }

    @Test
    void detectReadsInjectedProcRoot(@TempDir final Path proc) throws Exception {
        Files.write(proc.resolve("cpuinfo"), (
                "processor\t: 0\nmodel name\t: Injected CPU\nphysical id\t: 0\ncpu cores\t: 2\n"
                        + "processor\t: 1\nmodel name\t: Injected CPU\nphysical id\t: 0\ncpu cores\t: 2\n"
                        + "processor\t: 2\nmodel name\t: Injected CPU\nphysical id\t: 0\ncpu cores\t: 2\n"
                        + "processor\t: 3\nmodel name\t: Injected CPU\nphysical id\t: 0\ncpu cores\t: 2\n"
        ).getBytes(StandardCharsets.UTF_8));
        Files.write(proc.resolve("meminfo"),
                "MemTotal:        8388608 kB\nSwapTotal:             0 kB\n"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(proc.resolve("version"),
                "Linux version test-kernel".getBytes(StandardCharsets.UTF_8));

        final HardwareInventory inventory = HardwareInventoryDetector.detect(proc, null);
        assertEquals("Injected CPU", inventory.cpuModel());
        assertEquals(2, inventory.physicalCores());
        assertEquals(4, inventory.logicalCpus());
        assertEquals(8192, inventory.ramMegabytes());
        assertTrue(inventory.javaVersion() != null && !inventory.javaVersion().isEmpty());
    }
}
