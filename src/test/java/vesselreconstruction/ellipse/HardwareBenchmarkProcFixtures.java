package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class HardwareBenchmarkProcFixtures {

    static final long MIB = 1024L * 1024L;

    private HardwareBenchmarkProcFixtures() {
    }

    static void writeMeminfo(final Path proc, final long swapUsedBytes) throws IOException {
        Files.createDirectories(proc);
        final long swapUsedKb = swapUsedBytes / 1024L;
        final long swapTotalKb = 8L * 1024L * 1024L;
        final long swapFreeKb = Math.max(0L, swapTotalKb - swapUsedKb);
        final String text = "MemTotal:       14000000 kB\n"
                + "MemAvailable:    4000000 kB\n"
                + "SwapTotal:       " + swapTotalKb + " kB\n"
                + "SwapFree:        " + swapFreeKb + " kB\n";
        Files.write(proc.resolve("meminfo"), text.getBytes(StandardCharsets.UTF_8));
        Files.write(proc.resolve("loadavg"),
                "0.10 0.20 0.30 1/100 1\n".getBytes(StandardCharsets.UTF_8));
        Files.write(proc.resolve("stat"),
                "cpu  0 0 0 100 0 0 0 0 0 0\n".getBytes(StandardCharsets.UTF_8));
    }

    static void writePid(
            final Path proc,
            final int pid,
            final long vmSwapBytes,
            final String children) throws IOException {
        if (pid <= 0) {
            return;
        }
        final Path task = proc.resolve(Integer.toString(pid)).resolve("task")
                .resolve(Integer.toString(pid));
        Files.createDirectories(task);
        final String childText = children == null ? "" : children;
        Files.write(task.resolve("children"), childText.getBytes(StandardCharsets.UTF_8));
        final long kb = vmSwapBytes / 1024L;
        final String status = "Name:\tbench\nVmRSS:\t 10240 kB\nVmSwap:\t " + kb + " kB\n";
        Files.write(proc.resolve(Integer.toString(pid)).resolve("status"),
                status.getBytes(StandardCharsets.UTF_8));
    }
}
