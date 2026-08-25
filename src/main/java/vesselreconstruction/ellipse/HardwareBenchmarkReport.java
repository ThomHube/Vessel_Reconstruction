package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Writes the hardware_benchmark report tree.
 */
public final class HardwareBenchmarkReport {

    private HardwareBenchmarkReport() {
    }

    public static void write(
            final Path outputRoot,
            final HardwareInventory inventory,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkRunResult selected,
            final String sourceCommit,
            final String reason,
            final String safetyMargin) throws IOException {
        write(outputRoot, inventory, results, selected, sourceCommit, reason, safetyMargin, null);
    }

    public static void write(
            final Path outputRoot,
            final HardwareInventory inventory,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkRunResult selected,
            final String sourceCommit,
            final String reason,
            final String safetyMargin,
            final HardwareBenchmarkWorkload.Prepared workload) throws IOException {
        Files.createDirectories(outputRoot);
        Files.write(outputRoot.resolve("hardware.json"),
                inventory.toJson().getBytes(StandardCharsets.UTF_8));
        writeConfigurationsCsv(outputRoot.resolve("benchmark_configurations.csv"), results);
        writeMeasurementsCsv(outputRoot.resolve("benchmark_measurements.csv"), results);
        writeReadme(outputRoot.resolve("README.txt"), inventory, results, selected, sourceCommit,
                reason, safetyMargin);
        if (selected != null) {
            selectedProfile(inventory, selected, sourceCommit, reason, safetyMargin, workload)
                    .writeProperties(outputRoot.resolve("selected_profile.properties"));
        }
        if (selected != null) {
            Files.write(outputRoot.resolve("SUCCESS"),
                    "BENCHMARK SUCCESS\n".getBytes(StandardCharsets.UTF_8));
        }
        writeStatus(outputRoot, results, selected);
    }

    static void writeStatus(
            final Path outputRoot,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkRunResult selected) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("HARDWARE BENCHMARK\n");
        sb.append("BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n");
        sb.append("configurations ").append(results.size()).append('\n');
        if (!results.isEmpty()) {
            sb.append("phase: ").append(results.get(results.size() - 1).workloadType).append('\n');
        }
        if (selected != null) {
            sb.append("selected/best configuration ").append(selected.configurationId).append('\n');
            sb.append("threads_per_tile=").append(selected.threadsPerTile).append('\n');
            sb.append("fiji.heap_mb=").append(selected.fijiHeapMb).append('\n');
            sb.append("throughput=").append(RCsvSupport.formatNumber(selected.rankingThroughput()));
            if (HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)) {
                sb.append(" pixels/s\n");
            } else {
                sb.append(" tiles/s\n");
            }
            sb.append("result=SUCCESS\n");
        } else {
            sb.append("selected/best configuration none\n");
            sb.append("result=FAILED\n");
        }
        Files.write(outputRoot.resolve("STATUS.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static HardwareExecutionProfile selectedProfile(
            final HardwareInventory inventory,
            final HardwareBenchmarkRunResult selected,
            final String sourceCommit,
            final String reason,
            final String safetyMargin) {
        return selectedProfile(inventory, selected, sourceCommit, reason, safetyMargin, null);
    }

    public static HardwareExecutionProfile selectedProfile(
            final HardwareInventory inventory,
            final HardwareBenchmarkRunResult selected,
            final String sourceCommit,
            final String reason,
            final String safetyMargin,
            final HardwareBenchmarkWorkload.Prepared workload) {
        final Map<String, String> meta = inventory.toPropertyMap();
        meta.put("benchmark.timestamp_utc", utcNow());
        meta.put("benchmark.timestamp", utcNow());
        meta.put("benchmark.version", Integer.toString(HardwareExecutionProfile.BENCHMARK_VERSION));
        meta.put("benchmark_version", Integer.toString(HardwareExecutionProfile.BENCHMARK_VERSION));
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_TYPE, selected.workloadType);
        meta.put("benchmark_type", selected.workloadType);
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_WORKLOAD, selected.workloadType);
        meta.put("benchmark_workload", selected.workloadType);
        final String status = HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)
                ? HardwareExecutionProfile.STATUS_REAL_VALIDATED
                : HardwareExecutionProfile.STATUS_SYNTHETIC_ONLY;
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_STATUS, status);
        meta.put("benchmark_status", status);
        meta.put("benchmark.result", "SUCCESS");
        meta.put("source.commit", sourceCommit == null || sourceCommit.trim().isEmpty()
                ? RCsvSupport.NA : sourceCommit.trim());
        meta.put("source_commit", meta.get("source.commit"));
        meta.put("cpu_model", inventory.cpuModel());
        meta.put("physical_cores", Integer.toString(inventory.physicalCores()));
        meta.put("logical_cpus", Integer.toString(inventory.logicalCpus()));
        meta.put("ram_mib", Integer.toString(inventory.ramMegabytes()));
        meta.put("java_version", inventory.javaVersion());
        meta.put("fiji_version", inventory.fijiVersion());
        meta.put("wall_clock_seconds", RCsvSupport.formatNumber(selected.wallClockSeconds()));
        meta.put("throughput", RCsvSupport.formatNumber(selected.rankingThroughput()));
        meta.put("benchmark.throughput_tiles_per_second",
                RCsvSupport.formatNumber(selected.throughputTilesPerSecond));
        meta.put("benchmark.reason", reason);
        meta.put("selection_reason", reason);
        meta.put("benchmark.safety_margin", safetyMargin);
        meta.put("safety_margin", safetyMargin);
        meta.put("workload", selected.workloadType);
        meta.put("workload_identity", selected.workloadType);
        meta.put("workload_sha256", RCsvSupport.NA);
        meta.put("classifier_sha256", selected.classifierSha256);
        meta.put("disclaimer", "BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.");
        if (workload != null) {
            meta.putAll(HardwareBenchmarkWorkload.identityMap(workload));
        }
        if (!HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)) {
            meta.put("production_ready", RCsvSupport.FALSE);
        } else {
            meta.put("production_ready", RCsvSupport.TRUE);
        }
        return new HardwareExecutionProfile(selected.threadsPerTile, selected.fijiHeapMb, meta);
    }

    public static String formatSummary(
            final HardwareInventory inventory,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkRunResult selected,
            final String reason,
            final String safetyMargin,
            final Path profilePath) {
        int successful = 0;
        int rejected = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).safeSuccess()) {
                successful++;
            } else {
                rejected++;
            }
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("LUMEN AUTOMATED HARDWARE BENCHMARK\n");
        sb.append("============================================================\n\n");
        sb.append("BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n\n");
        sb.append("Hardware:\n");
        sb.append("  CPU: ").append(inventory.cpuModel()).append('\n');
        sb.append("  physical cores: ").append(inventory.physicalCores()).append('\n');
        sb.append("  logical CPUs: ").append(inventory.logicalCpus()).append('\n');
        sb.append("  RAM: ").append(inventory.ramMegabytes()).append(" MiB\n");
        sb.append("  Java: ").append(inventory.javaVersion()).append("\n\n");
        sb.append("Fixed scientific geometry:\n");
        sb.append("  tile: ").append(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN)
                .append("\n\n");
        if (selected != null) {
            sb.append("Workload: ").append(selected.workloadType).append('\n');
            sb.append("Status: ").append(
                    HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)
                            ? HardwareExecutionProfile.STATUS_REAL_VALIDATED
                            : HardwareExecutionProfile.STATUS_SYNTHETIC_ONLY)
                    .append("\n\n");
        }
        sb.append("Configurations tested: ").append(results.size()).append('\n');
        sb.append("Successful: ").append(successful).append('\n');
        sb.append("Rejected: ").append(rejected).append("\n\n");
        if (selected == null) {
            sb.append("Best stable configuration:\n  none\n\n");
            sb.append("Reason:\n  ").append(reason).append("\n\n");
            sb.append("BENCHMARK FAILED\n");
            return sb.toString();
        }
        sb.append("Best stable configuration:\n");
        sb.append("  threads_per_tile=").append(selected.threadsPerTile).append('\n');
        sb.append("  fiji.heap_mb=").append(selected.fijiHeapMb).append('\n');
        sb.append("  throughput=").append(RCsvSupport.formatNumber(selected.rankingThroughput()));
        if (HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)) {
            sb.append(" pixels/s\n");
            sb.append("  wall_clock_seconds=").append(RCsvSupport.formatNumber(selected.wallClockSeconds()))
                    .append("\n\n");
        } else {
            sb.append(" tiles/s\n\n");
        }
        sb.append("Reason:\n  ").append(reason).append("\n\n");
        sb.append("Safety margin:\n  ").append(safetyMargin).append("\n\n");
        sb.append("Profile:\n  ").append(profilePath.toAbsolutePath().normalize()).append("\n\n");
        if (HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)) {
            sb.append("BENCHMARK SUCCESS\n");
        } else {
            sb.append("SYNTHETIC_ONLY — NOT PRODUCTION-READY\n");
        }
        return sb.toString();
    }

    private static void writeConfigurationsCsv(
            final Path path,
            final List<HardwareBenchmarkRunResult> results) throws IOException {
        final List<String> header = java.util.Arrays.asList(
                "configuration_id", "phase", "threads_per_tile", "fiji_heap_mb",
                "tile_width", "tile_height");
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < results.size(); i++) {
            final HardwareBenchmarkRunResult result = results.get(i);
            rows.add(java.util.Arrays.asList(
                    result.configurationId,
                    result.phase,
                    Integer.toString(result.threadsPerTile),
                    Integer.toString(result.fijiHeapMb),
                    Integer.toString(result.tileWidthPx),
                    Integer.toString(result.tileHeightPx)));
        }
        ProductionCsvStore.writeTableAtomically(path, header, rows);
    }

    private static void writeMeasurementsCsv(
            final Path path,
            final List<HardwareBenchmarkRunResult> results) throws IOException {
        final List<String> header = new ArrayList<String>();
        if (!results.isEmpty()) {
            header.addAll(results.get(0).toCsvRow().keySet());
        } else {
            header.add("configuration_id");
        }
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < results.size(); i++) {
            final Map<String, String> map = results.get(i).toCsvRow();
            final List<String> row = new ArrayList<String>();
            for (int h = 0; h < header.size(); h++) {
                final String value = map.get(header.get(h));
                row.add(value == null ? RCsvSupport.NA : value);
            }
            rows.add(row);
        }
        ProductionCsvStore.writeTableAtomically(path, header, rows);
    }

    private static void writeReadme(
            final Path path,
            final HardwareInventory inventory,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkRunResult selected,
            final String sourceCommit,
            final String reason,
            final String safetyMargin) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("LUMEN AUTOMATED HARDWARE BENCHMARK\n");
        sb.append("BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n\n");
        sb.append("source.commit=").append(sourceCommit).append('\n');
        sb.append("benchmark.version=").append(HardwareExecutionProfile.BENCHMARK_VERSION)
                .append('\n');
        sb.append("tile=").append(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN)
                .append('\n');
        sb.append("cpu=").append(inventory.cpuModel()).append('\n');
        sb.append("reason=").append(reason).append('\n');
        sb.append("safety_margin=").append(safetyMargin).append('\n');
        if (selected != null) {
            sb.append("selected.threads_per_tile=").append(selected.threadsPerTile).append('\n');
            sb.append("selected.fiji.heap_mb=").append(selected.fijiHeapMb).append('\n');
        }
        sb.append("configurations=").append(results.size()).append('\n');
        Sha256Files.writeBytesAtomically(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String utcNow() {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
