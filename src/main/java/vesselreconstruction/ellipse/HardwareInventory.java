package vesselreconstruction.ellipse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of the current analysis host. Values are measured, not hard-coded
 * for a particular personal computer.
 */
public final class HardwareInventory {

    private final String hostname;
    private final String cpuModel;
    private final int physicalCores;
    private final int logicalCpus;
    private final int threadsPerCore;
    private final long ramBytes;
    private final long swapBytes;
    private final String osName;
    private final String kernel;
    private final String javaVersion;
    private final String fijiVersion;

    public HardwareInventory(
            final String hostname,
            final String cpuModel,
            final int physicalCores,
            final int logicalCpus,
            final int threadsPerCore,
            final long ramBytes,
            final long swapBytes,
            final String osName,
            final String kernel,
            final String javaVersion,
            final String fijiVersion) {
        if (physicalCores < 1) {
            throw new IllegalArgumentException("physicalCores must be >= 1");
        }
        if (logicalCpus < 1) {
            throw new IllegalArgumentException("logicalCpus must be >= 1");
        }
        if (threadsPerCore < 1) {
            throw new IllegalArgumentException("threadsPerCore must be >= 1");
        }
        if (ramBytes < 1L) {
            throw new IllegalArgumentException("ramBytes must be >= 1");
        }
        this.hostname = nullToNa(hostname);
        this.cpuModel = nullToNa(cpuModel);
        this.physicalCores = physicalCores;
        this.logicalCpus = logicalCpus;
        this.threadsPerCore = threadsPerCore;
        this.ramBytes = ramBytes;
        this.swapBytes = swapBytes < 0L ? 0L : swapBytes;
        this.osName = nullToNa(osName);
        this.kernel = nullToNa(kernel);
        this.javaVersion = nullToNa(javaVersion);
        this.fijiVersion = nullToNa(fijiVersion);
    }

    public String hostname() {
        return hostname;
    }

    public String cpuModel() {
        return cpuModel;
    }

    public int physicalCores() {
        return physicalCores;
    }

    public int logicalCpus() {
        return logicalCpus;
    }

    public int threadsPerCore() {
        return threadsPerCore;
    }

    public long ramBytes() {
        return ramBytes;
    }

    public int ramMegabytes() {
        return (int) Math.max(1L, ramBytes / (1024L * 1024L));
    }

    public long swapBytes() {
        return swapBytes;
    }

    public String osName() {
        return osName;
    }

    public String kernel() {
        return kernel;
    }

    public String javaVersion() {
        return javaVersion;
    }

    public String fijiVersion() {
        return fijiVersion;
    }

    public Map<String, String> toPropertyMap() {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("hardware.hostname", hostname);
        map.put("hardware.cpu_model", cpuModel);
        map.put("hardware.physical_cores", Integer.toString(physicalCores));
        map.put("hardware.logical_cpus", Integer.toString(logicalCpus));
        map.put("hardware.threads_per_core", Integer.toString(threadsPerCore));
        map.put("hardware.ram_bytes", Long.toString(ramBytes));
        map.put("hardware.swap_bytes", Long.toString(swapBytes));
        map.put("hardware.os", osName);
        map.put("hardware.kernel", kernel);
        map.put("java.version", javaVersion);
        map.put("fiji.version", fijiVersion);
        return map;
    }

    public String toJson() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJson(sb, "hostname", hostname, true);
        appendJson(sb, "cpu_model", cpuModel, true);
        sb.append("  \"physical_cores\": ").append(physicalCores).append(",\n");
        sb.append("  \"logical_cpus\": ").append(logicalCpus).append(",\n");
        sb.append("  \"threads_per_core\": ").append(threadsPerCore).append(",\n");
        sb.append("  \"ram_bytes\": ").append(ramBytes).append(",\n");
        sb.append("  \"swap_bytes\": ").append(swapBytes).append(",\n");
        appendJson(sb, "os", osName, true);
        appendJson(sb, "kernel", kernel, true);
        appendJson(sb, "java_version", javaVersion, true);
        appendJson(sb, "fiji_version", fijiVersion, false);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendJson(
            final StringBuilder sb,
            final String key,
            final String value,
            final boolean comma) {
        sb.append("  \"").append(key).append("\": \"").append(jsonEscape(value)).append('"');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    static String jsonEscape(final String raw) {
        if (raw == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            final char ch = raw.charAt(i);
            if (ch == '\\' || ch == '"') {
                sb.append('\\').append(ch);
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch == '\r') {
                sb.append("\\r");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String nullToNa(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return RCsvSupport.NA;
        }
        return value.trim();
    }
}
