package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkProcessTreeTest {

    @Test
    void descendantsWalkOwnedChildTreeOnly(@TempDir final Path proc) throws Exception {
        writeChildren(proc, 100, "101");
        writeChildren(proc, 101, "102");
        writeChildren(proc, 102, "");
        writeChildren(proc, 999, "1000");
        final List<Integer> owned = HardwareBenchmarkProcessTree.descendants(proc, 100);
        assertEquals(3, owned.size());
        assertTrue(owned.contains(Integer.valueOf(100)));
        assertTrue(owned.contains(Integer.valueOf(101)));
        assertTrue(owned.contains(Integer.valueOf(102)));
        assertEquals("owned_pid=100 descendants=100,101,102",
                HardwareBenchmarkProcessTree.formatOwned(100, owned));
    }

    @Test
    void vmSwapBytesSumsOwnedTreeOnly(@TempDir final Path proc) throws Exception {
        HardwareBenchmarkProcFixtures.writePid(proc, 100, 10L * HardwareBenchmarkProcFixtures.MIB, "101");
        HardwareBenchmarkProcFixtures.writePid(proc, 101, 20L * HardwareBenchmarkProcFixtures.MIB, "102");
        HardwareBenchmarkProcFixtures.writePid(proc, 102, 5L * HardwareBenchmarkProcFixtures.MIB, "");
        HardwareBenchmarkProcFixtures.writePid(proc, 999, 512L * HardwareBenchmarkProcFixtures.MIB, "1000");
        HardwareBenchmarkProcFixtures.writePid(proc, 1000, 256L * HardwareBenchmarkProcFixtures.MIB, "");
        assertEquals(35L * HardwareBenchmarkProcFixtures.MIB,
                HardwareBenchmarkProcessTree.vmSwapBytes(proc, 100));
        assertEquals(0L, HardwareBenchmarkProcessTree.vmSwapBytes(proc, 0));
    }

    private static void writeChildren(final Path proc, final int pid, final String children)
            throws Exception {
        final Path task = proc.resolve(Integer.toString(pid)).resolve("task")
                .resolve(Integer.toString(pid));
        Files.createDirectories(task);
        Files.write(task.resolve("children"), children.getBytes(StandardCharsets.UTF_8));
    }
}
