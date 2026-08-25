package vesselreconstruction.ellipse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Linux {@code /proc} samples for one benchmark configuration. Java 8 compatible.
 */
public final class HardwareBenchmarkMonitor {

    public static final class Sample {
        public final long timestampMs;
        public final double systemCpuPercent;
        public final double processCpuPercent;
        public final long rssBytes;
        public final long memTotalBytes;
        public final long memAvailableBytes;
        public final long swapUsedBytes;
        public final long processTreeSwapBytes;
        public final double loadAverage;

        public Sample(
                final long timestampMs,
                final double systemCpuPercent,
                final double processCpuPercent,
                final long rssBytes,
                final long memTotalBytes,
                final long memAvailableBytes,
                final long swapUsedBytes,
                final double loadAverage) {
            this(timestampMs, systemCpuPercent, processCpuPercent, rssBytes, memTotalBytes,
                    memAvailableBytes, swapUsedBytes, 0L, loadAverage);
        }

        public Sample(
                final long timestampMs,
                final double systemCpuPercent,
                final double processCpuPercent,
                final long rssBytes,
                final long memTotalBytes,
                final long memAvailableBytes,
                final long swapUsedBytes,
                final long processTreeSwapBytes,
                final double loadAverage) {
            this.timestampMs = timestampMs;
            this.systemCpuPercent = systemCpuPercent;
            this.processCpuPercent = processCpuPercent;
            this.rssBytes = rssBytes;
            this.memTotalBytes = memTotalBytes;
            this.memAvailableBytes = memAvailableBytes;
            this.swapUsedBytes = swapUsedBytes;
            this.processTreeSwapBytes = processTreeSwapBytes;
            this.loadAverage = loadAverage;
        }
    }

    public static final class Accumulator {
        public long samples;
        public double cpuSum;
        public double processCpuSum;
        public long peakRss;
        public long peakUsedRam;
        public long minAvailableRam = Long.MAX_VALUE;
        public long peakSwap;
        public long startSwap = -1L;
        public long processTreePeakSwap;
        public long processTreeStartSwap = -1L;
        public double peakLoad;
        public CpuTick previousSystem;
        public CpuTick previousProcess;
        public final long clockTicks;

        public Accumulator() {
            this.clockTicks = detectClockTicks();
        }

        public void add(final Sample sample) {
            samples++;
            cpuSum += sample.systemCpuPercent;
            processCpuSum += sample.processCpuPercent;
            if (sample.rssBytes > peakRss) {
                peakRss = sample.rssBytes;
            }
            final long used = Math.max(0L, sample.memTotalBytes - sample.memAvailableBytes);
            if (used > peakUsedRam) {
                peakUsedRam = used;
            }
            if (sample.memAvailableBytes < minAvailableRam) {
                minAvailableRam = sample.memAvailableBytes;
            }
            if (sample.swapUsedBytes > peakSwap) {
                peakSwap = sample.swapUsedBytes;
            }
            if (startSwap < 0L) {
                startSwap = sample.swapUsedBytes;
            }
            if (sample.processTreeSwapBytes > processTreePeakSwap) {
                processTreePeakSwap = sample.processTreeSwapBytes;
            }
            if (processTreeStartSwap < 0L) {
                processTreeStartSwap = sample.processTreeSwapBytes;
            }
            if (sample.loadAverage > peakLoad) {
                peakLoad = sample.loadAverage;
            }
        }

        public long hostSwapGrowthBytes() {
            return Math.max(0L, peakSwap - Math.max(0L, startSwap));
        }

        public long processTreeSwapGrowthBytes() {
            return Math.max(0L, processTreePeakSwap - Math.max(0L, processTreeStartSwap));
        }

        public double meanCpu() {
            return samples == 0L ? 0.0d : cpuSum / samples;
        }

        public double meanProcessCpu() {
            return samples == 0L ? 0.0d : processCpuSum / samples;
        }
    }

    static final class CpuTick {
        final long idle;
        final long total;
        final long process;

        CpuTick(final long idle, final long total, final long process) {
            this.idle = idle;
            this.total = total;
            this.process = process;
        }
    }

    private HardwareBenchmarkMonitor() {
    }

    public static void captureHostSwapBeforeStart(final Path procRoot, final Accumulator acc) {
        final MemSample mem = readMem(procRoot);
        acc.startSwap = mem.swapUsed;
        if (mem.swapUsed > acc.peakSwap) {
            acc.peakSwap = mem.swapUsed;
        }
        acc.processTreeStartSwap = 0L;
    }

    public static Sample sample(final Path procRoot, final int pid, final Accumulator acc) {
        final MemSample mem = readMem(procRoot);
        final long treeSwap = pid > 0
                ? HardwareBenchmarkProcessTree.vmSwapBytes(procRoot, pid)
                : 0L;
        final CpuTick now = readCpu(procRoot, pid);
        double sysPct = 0.0d;
        double procPct = 0.0d;
        if (acc.previousSystem != null && now.total > acc.previousSystem.total) {
            final long dTotal = now.total - acc.previousSystem.total;
            final long dIdle = now.idle - acc.previousSystem.idle;
            sysPct = 100.0d * (dTotal - dIdle) / dTotal;
            if (sysPct < 0.0d) {
                sysPct = 0.0d;
            }
            if (sysPct > 100.0d) {
                sysPct = 100.0d;
            }
            final long dProc = now.process - acc.previousSystem.process;
            procPct = 100.0d * dProc / dTotal;
            if (procPct < 0.0d) {
                procPct = 0.0d;
            }
        }
        acc.previousSystem = now;
        final Sample sample = new Sample(
                System.currentTimeMillis(),
                sysPct,
                procPct,
                readRss(procRoot, pid),
                mem.total,
                mem.available,
                mem.swapUsed,
                treeSwap,
                readLoad(procRoot));
        acc.add(sample);
        return sample;
    }

    static MemSample readMem(final Path procRoot) {
        final String text = readQuiet(procRoot.resolve("meminfo"));
        long totalKb = 0L;
        long availKb = 0L;
        long swapTotalKb = 0L;
        long swapFreeKb = 0L;
        final String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.startsWith("MemTotal:")) {
                totalKb = HardwareInventoryDetector.parseKb(line);
            } else if (line.startsWith("MemAvailable:")) {
                availKb = HardwareInventoryDetector.parseKb(line);
            } else if (line.startsWith("SwapTotal:")) {
                swapTotalKb = HardwareInventoryDetector.parseKb(line);
            } else if (line.startsWith("SwapFree:")) {
                swapFreeKb = HardwareInventoryDetector.parseKb(line);
            }
        }
        return new MemSample(
                totalKb * 1024L,
                availKb * 1024L,
                Math.max(0L, swapTotalKb - swapFreeKb) * 1024L);
    }

    static CpuTick readCpu(final Path procRoot, final int pid) {
        long idle = 0L;
        long total = 0L;
        final String stat = readQuiet(procRoot.resolve("stat"));
        final int nl = stat.indexOf('\n');
        final String cpu = nl < 0 ? stat : stat.substring(0, nl);
        final String[] parts = cpu.trim().split("\\s+");
        if (parts.length >= 5) {
            long sum = 0L;
            for (int i = 1; i < parts.length; i++) {
                try {
                    sum += Long.parseLong(parts[i]);
                } catch (final NumberFormatException ignored) {
                    // skip
                }
            }
            try {
                idle = Long.parseLong(parts[4]);
            } catch (final NumberFormatException ignored) {
                idle = 0L;
            }
            total = sum;
        }
        long process = 0L;
        if (pid > 0) {
            final String pstat = readQuiet(procRoot.resolve(Integer.toString(pid)).resolve("stat"));
            final int rparen = pstat.lastIndexOf(')');
            if (rparen >= 0) {
                final String[] pparts = pstat.substring(rparen + 1).trim().split("\\s+");
                if (pparts.length >= 15) {
                    try {
                        process = Long.parseLong(pparts[11]) + Long.parseLong(pparts[12]);
                    } catch (final NumberFormatException ignored) {
                        process = 0L;
                    }
                }
            }
        }
        return new CpuTick(idle, total, process);
    }

    static long readRss(final Path procRoot, final int pid) {
        return readStatusKb(procRoot, pid, "VmRSS:") * 1024L;
    }

    static long readVmSwap(final Path procRoot, final int pid) {
        return readStatusKb(procRoot, pid, "VmSwap:") * 1024L;
    }

    static long readStatusKb(final Path procRoot, final int pid, final String key) {
        if (pid <= 0) {
            return 0L;
        }
        final String status = readQuiet(procRoot.resolve(Integer.toString(pid)).resolve("status"));
        final String[] lines = status.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(key)) {
                return HardwareInventoryDetector.parseKb(lines[i]);
            }
        }
        return 0L;
    }

    static double readLoad(final Path procRoot) {
        final String text = readQuiet(procRoot.resolve("loadavg")).trim();
        if (text.isEmpty()) {
            return 0.0d;
        }
        final int sp = text.indexOf(' ');
        final String first = sp < 0 ? text : text.substring(0, sp);
        try {
            return Double.parseDouble(first);
        } catch (final NumberFormatException ex) {
            return 0.0d;
        }
    }

    static int readPid(final Process process) {
        if (process == null) {
            return 0;
        }
        try {
            final java.lang.reflect.Method method = Process.class.getMethod("pid");
            final Object value = method.invoke(process);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (final Exception ignored) {
            // Java 8: Process.pid() does not exist
        }
        try {
            final java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            final Object raw = field.get(process);
            if (raw instanceof Number) {
                return ((Number) raw).intValue();
            }
        } catch (final Exception ignored) {
            // ProcessHandle unavailable
        }
        return 0;
    }

    static long detectClockTicks() {
        try {
            final Process process = new ProcessBuilder("getconf", "CLK_TCK").start();
            final byte[] bytes = readAll(process);
            process.waitFor();
            final String text = new String(bytes, StandardCharsets.UTF_8).trim();
            return Long.parseLong(text);
        } catch (final Exception ex) {
            return 100L;
        }
    }

    private static byte[] readAll(final Process process) {
        try {
            return readStream(process);
        } catch (final Exception ex) {
            return new byte[0];
        }
    }

    private static byte[] readStream(final Process process) throws Exception {
        final java.io.InputStream in = process.getInputStream();
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) {
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    private static String readQuiet(final Path path) {
        try {
            if (path != null && Files.isRegularFile(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        } catch (final Exception ignored) {
            // missing pid after exit
        }
        return "";
    }

    static final class MemSample {
        final long total;
        final long available;
        final long swapUsed;

        MemSample(final long total, final long available, final long swapUsed) {
            this.total = total;
            this.available = available;
            this.swapUsed = swapUsed;
        }
    }

    static Path defaultProcRoot() {
        return Paths.get("/proc");
    }

    public static String formatSampleLine(final String configurationId, final Sample sample) {
        return String.format(Locale.US,
                "HARDWARE BENCHMARK %s cpu=%.1f%% proc_cpu=%.1f%% rss_mb=%d avail_ram_mb=%d swap_mb=%d proc_swap_mb=%d load=%.2f",
                configurationId,
                Double.valueOf(sample.systemCpuPercent),
                Double.valueOf(sample.processCpuPercent),
                Long.valueOf(sample.rssBytes / (1024L * 1024L)),
                Long.valueOf(sample.memAvailableBytes / (1024L * 1024L)),
                Long.valueOf(sample.swapUsedBytes / (1024L * 1024L)),
                Long.valueOf(sample.processTreeSwapBytes / (1024L * 1024L)),
                Double.valueOf(sample.loadAverage));
    }
}
