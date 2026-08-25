package vesselreconstruction.ellipse;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Linux {@code /proc} facts. Callers inject text in tests; production
 * uses the live filesystem. No host-specific constants.
 */
public final class HardwareInventoryDetector {

    public static HardwareInventory detect() {
        return detect(Paths.get("/proc"), null);
    }

    public static HardwareInventory detect(final Path procRoot, final Path fijiLauncher) {
        final Path cpuInfo = procRoot.resolve("cpuinfo");
        final Path memInfo = procRoot.resolve("meminfo");
        String cpuText = readQuiet(cpuInfo);
        String memText = readQuiet(memInfo);
        if (cpuText.isEmpty()) {
            cpuText = "processor\t: 0\nmodel name\t: unknown\ncpu cores\t: 1\nphysical id\t: 0\n";
        }
        if (memText.isEmpty()) {
            memText = "MemTotal:        1048576 kB\nSwapTotal:             0 kB\n";
        }
        return parse(cpuText, memText, detectHostname(), detectOs(), detectKernel(procRoot),
                System.getProperty("java.version", RCsvSupport.NA), detectFijiVersion(fijiLauncher));
    }

    static HardwareInventory parse(
            final String cpuInfo,
            final String memInfo,
            final String hostname,
            final String osName,
            final String kernel,
            final String javaVersion,
            final String fijiVersion) {
        final CpuFacts cpu = parseCpu(cpuInfo);
        final MemFacts mem = parseMem(memInfo);
        return new HardwareInventory(
                hostname,
                cpu.model,
                cpu.physicalCores,
                cpu.logicalCpus,
                cpu.threadsPerCore,
                mem.ramBytes,
                mem.swapBytes,
                osName,
                kernel,
                javaVersion,
                fijiVersion);
    }

    static CpuFacts parseCpu(final String cpuInfo) {
        int logical = 0;
        String model = "unknown";
        final Map<String, Integer> coresBySocket = new LinkedHashMap<String, Integer>();
        String currentPhysical = "0";
        int lastCpuCores = 1;
        final String[] lines = cpuInfo.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.startsWith("processor")) {
                logical++;
            } else if (line.toLowerCase(Locale.ROOT).startsWith("model name")) {
                model = valueAfterColon(line, model);
            } else if (line.toLowerCase(Locale.ROOT).startsWith("physical id")) {
                currentPhysical = valueAfterColon(line, currentPhysical);
            } else if (line.toLowerCase(Locale.ROOT).startsWith("cpu cores")) {
                lastCpuCores = parsePositive(valueAfterColon(line, "1"), 1);
                coresBySocket.put(currentPhysical, Integer.valueOf(lastCpuCores));
            }
        }
        if (logical < 1) {
            logical = 1;
        }
        int physical = 0;
        for (final Integer cores : coresBySocket.values()) {
            physical += cores.intValue();
        }
        if (physical < 1) {
            physical = logical;
        }
        final int threadsPerCore = Math.max(1, (logical + physical - 1) / physical);
        return new CpuFacts(model, physical, logical, threadsPerCore);
    }

    static MemFacts parseMem(final String memInfo) {
        long ramKb = 0L;
        long swapKb = 0L;
        final String[] lines = memInfo.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.startsWith("MemTotal:")) {
                ramKb = parseKb(line);
            } else if (line.startsWith("SwapTotal:")) {
                swapKb = parseKb(line);
            }
        }
        if (ramKb < 1L) {
            ramKb = 1024L * 1024L;
        }
        return new MemFacts(ramKb * 1024L, swapKb * 1024L);
    }

    static long parseKb(final String line) {
        final String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return 0L;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (final NumberFormatException ex) {
            return 0L;
        }
    }

    static String detectHostname() {
        try {
            final String name = InetAddress.getLocalHost().getHostName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        } catch (final Exception ignored) {
            // fall through
        }
        return readQuiet(Paths.get("/etc/hostname"));
    }

    static String detectOs() {
        return System.getProperty("os.name", RCsvSupport.NA)
                + " " + System.getProperty("os.version", "").trim();
    }

    static String detectKernel(final Path procRoot) {
        final String version = readQuiet(procRoot.resolve("version"));
        if (!version.isEmpty()) {
            return version.trim();
        }
        return System.getProperty("os.version", RCsvSupport.NA);
    }

    static String detectFijiVersion(final Path fijiLauncher) {
        if (fijiLauncher == null) {
            return RCsvSupport.NA;
        }
        final Path app = fijiLauncher.toAbsolutePath().normalize().getParent();
        if (app == null || !Files.isDirectory(app)) {
            return RCsvSupport.NA;
        }
        final Path jars = app.resolve("jars");
        if (Files.isDirectory(jars)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(jars, "ij-*.jar")) {
                for (final Path jar : stream) {
                    final String name = jar.getFileName().toString();
                    if (name.startsWith("ij-") && name.endsWith(".jar")) {
                        return name.substring(3, name.length() - 4);
                    }
                }
            } catch (final IOException ignored) {
                // fall through
            }
        }
        if (Files.isRegularFile(app.resolve("ImageJ-linux64"))
                || Files.isRegularFile(fijiLauncher)) {
            return "Fiji.app";
        }
        return RCsvSupport.NA;
    }

    private static String valueAfterColon(final String line, final String fallback) {
        final int colon = line.indexOf(':');
        if (colon < 0 || colon + 1 >= line.length()) {
            return fallback;
        }
        final String value = line.substring(colon + 1).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static int parsePositive(final String raw, final int fallback) {
        try {
            final int value = Integer.parseInt(raw.trim());
            return value < 1 ? fallback : value;
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static String readQuiet(final Path path) {
        try {
            if (path != null && Files.isRegularFile(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        } catch (final IOException ignored) {
            // missing /proc files are handled by callers
        }
        return "";
    }

    static final class CpuFacts {
        final String model;
        final int physicalCores;
        final int logicalCpus;
        final int threadsPerCore;

        CpuFacts(final String model, final int physicalCores, final int logicalCpus,
                final int threadsPerCore) {
            this.model = model;
            this.physicalCores = physicalCores;
            this.logicalCpus = logicalCpus;
            this.threadsPerCore = threadsPerCore;
        }
    }

    static final class MemFacts {
        final long ramBytes;
        final long swapBytes;

        MemFacts(final long ramBytes, final long swapBytes) {
            this.ramBytes = ramBytes;
            this.swapBytes = swapBytes;
        }
    }

    private HardwareInventoryDetector() {
    }
}
