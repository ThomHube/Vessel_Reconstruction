package vesselreconstruction.ellipse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of one isolated benchmark configuration.
 */
public final class HardwareBenchmarkRunResult {

    public final String configurationId;
    public final String phase;
    public final int threadsPerTile;
    public final int fijiHeapMb;
    public final boolean success;
    public final boolean unsafe;
    public final String unsafeReason;
    public final int exitCode;
    public final long wallClockMs;
    public final double throughputTilesPerSecond;
    public final int tilesCompleted;
    public final double meanCpuPercent;
    public final double meanProcessCpuPercent;
    public final long peakRssBytes;
    public final long peakSystemUsedRamBytes;
    public final long minAvailableRamBytes;
    public final long peakSwapUsedBytes;
    public final long startSwapUsedBytes;
    public final long processTreeSwapStartBytes;
    public final long processTreeSwapPeakBytes;
    public final double peakLoadAverage;
    public final String errorMessage;
    public final int tileWidthPx;
    public final int tileHeightPx;
    public final String workloadType;
    public final long processedPixels;
    public final double throughputPixelsPerSecond;
    public final boolean outputValid;
    public final String classifierSha256;

    public HardwareBenchmarkRunResult(
            final String configurationId,
            final String phase,
            final int threadsPerTile,
            final int fijiHeapMb,
            final boolean success,
            final boolean unsafe,
            final String unsafeReason,
            final int exitCode,
            final long wallClockMs,
            final double throughputTilesPerSecond,
            final int tilesCompleted,
            final double meanCpuPercent,
            final double meanProcessCpuPercent,
            final long peakRssBytes,
            final long peakSystemUsedRamBytes,
            final long minAvailableRamBytes,
            final long peakSwapUsedBytes,
            final long startSwapUsedBytes,
            final double peakLoadAverage,
            final String errorMessage) {
        this.configurationId = configurationId;
        this.phase = phase;
        this.threadsPerTile = threadsPerTile;
        this.fijiHeapMb = fijiHeapMb;
        this.success = success;
        this.unsafe = unsafe;
        this.unsafeReason = unsafeReason == null ? RCsvSupport.NA : unsafeReason;
        this.exitCode = exitCode;
        this.wallClockMs = wallClockMs;
        this.throughputTilesPerSecond = throughputTilesPerSecond;
        this.tilesCompleted = tilesCompleted;
        this.meanCpuPercent = meanCpuPercent;
        this.meanProcessCpuPercent = meanProcessCpuPercent;
        this.peakRssBytes = peakRssBytes;
        this.peakSystemUsedRamBytes = peakSystemUsedRamBytes;
        this.minAvailableRamBytes = minAvailableRamBytes;
        this.peakSwapUsedBytes = peakSwapUsedBytes;
        this.startSwapUsedBytes = startSwapUsedBytes;
        this.processTreeSwapStartBytes = 0L;
        this.processTreeSwapPeakBytes = 0L;
        this.peakLoadAverage = peakLoadAverage;
        this.errorMessage = errorMessage == null || errorMessage.trim().isEmpty()
                ? RCsvSupport.NA : errorMessage.trim();
        this.tileWidthPx = HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX;
        this.tileHeightPx = HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX;
        this.workloadType = HardwareExecutionProfile.WORKLOAD_SYNTHETIC;
        this.processedPixels = (long) tilesCompleted
                * HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX
                * HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX;
        this.throughputPixelsPerSecond = 0.0d;
        this.outputValid = success && !unsafe;
        this.classifierSha256 = RCsvSupport.NA;
    }

    public HardwareBenchmarkRunResult(
            final String configurationId,
            final String phase,
            final int threadsPerTile,
            final int fijiHeapMb,
            final boolean success,
            final boolean unsafe,
            final String unsafeReason,
            final int exitCode,
            final long wallClockMs,
            final double throughputTilesPerSecond,
            final int tilesCompleted,
            final double meanCpuPercent,
            final double meanProcessCpuPercent,
            final long peakRssBytes,
            final long peakSystemUsedRamBytes,
            final long minAvailableRamBytes,
            final long peakSwapUsedBytes,
            final long startSwapUsedBytes,
            final double peakLoadAverage,
            final String errorMessage,
            final String workloadType,
            final long processedPixels,
            final double throughputPixelsPerSecond,
            final boolean outputValid,
            final String classifierSha256) {
        this(configurationId, phase, threadsPerTile, fijiHeapMb, success, unsafe, unsafeReason,
                exitCode, wallClockMs, throughputTilesPerSecond, tilesCompleted, meanCpuPercent,
                meanProcessCpuPercent, peakRssBytes, peakSystemUsedRamBytes, minAvailableRamBytes,
                peakSwapUsedBytes, startSwapUsedBytes, peakLoadAverage, errorMessage, workloadType,
                processedPixels, throughputPixelsPerSecond, outputValid, classifierSha256,
                0L, 0L);
    }

    public HardwareBenchmarkRunResult(
            final String configurationId,
            final String phase,
            final int threadsPerTile,
            final int fijiHeapMb,
            final boolean success,
            final boolean unsafe,
            final String unsafeReason,
            final int exitCode,
            final long wallClockMs,
            final double throughputTilesPerSecond,
            final int tilesCompleted,
            final double meanCpuPercent,
            final double meanProcessCpuPercent,
            final long peakRssBytes,
            final long peakSystemUsedRamBytes,
            final long minAvailableRamBytes,
            final long peakSwapUsedBytes,
            final long startSwapUsedBytes,
            final double peakLoadAverage,
            final String errorMessage,
            final String workloadType,
            final long processedPixels,
            final double throughputPixelsPerSecond,
            final boolean outputValid,
            final String classifierSha256,
            final long processTreeSwapStartBytes,
            final long processTreeSwapPeakBytes) {
        this.configurationId = configurationId;
        this.phase = phase;
        this.threadsPerTile = threadsPerTile;
        this.fijiHeapMb = fijiHeapMb;
        this.success = success;
        this.unsafe = unsafe;
        this.unsafeReason = unsafeReason == null ? RCsvSupport.NA : unsafeReason;
        this.exitCode = exitCode;
        this.wallClockMs = wallClockMs;
        this.throughputTilesPerSecond = throughputTilesPerSecond;
        this.tilesCompleted = tilesCompleted;
        this.meanCpuPercent = meanCpuPercent;
        this.meanProcessCpuPercent = meanProcessCpuPercent;
        this.peakRssBytes = peakRssBytes;
        this.peakSystemUsedRamBytes = peakSystemUsedRamBytes;
        this.minAvailableRamBytes = minAvailableRamBytes;
        this.peakSwapUsedBytes = peakSwapUsedBytes;
        this.startSwapUsedBytes = startSwapUsedBytes;
        this.processTreeSwapStartBytes = processTreeSwapStartBytes;
        this.processTreeSwapPeakBytes = processTreeSwapPeakBytes;
        this.peakLoadAverage = peakLoadAverage;
        this.errorMessage = errorMessage == null || errorMessage.trim().isEmpty()
                ? RCsvSupport.NA : errorMessage.trim();
        this.tileWidthPx = HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX;
        this.tileHeightPx = HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX;
        this.workloadType = workloadType == null || workloadType.trim().isEmpty()
                ? HardwareExecutionProfile.WORKLOAD_SYNTHETIC : workloadType.trim();
        this.processedPixels = processedPixels;
        this.throughputPixelsPerSecond = throughputPixelsPerSecond;
        this.outputValid = outputValid;
        this.classifierSha256 = classifierSha256 == null || classifierSha256.trim().isEmpty()
                ? RCsvSupport.NA : classifierSha256.trim();
    }

    public boolean safeSuccess() {
        if (HardwareExecutionProfile.WORKLOAD_REAL.equals(workloadType)) {
            return success && !unsafe && outputValid && wallClockMs > 0L
                    && rankingThroughput() > 0.0d;
        }
        return success && !unsafe && tilesCompleted > 0 && throughputTilesPerSecond > 0.0d;
    }

    public double rankingThroughput() {
        if (HardwareExecutionProfile.WORKLOAD_REAL.equals(workloadType)
                && throughputPixelsPerSecond > 0.0d) {
            return throughputPixelsPerSecond;
        }
        return throughputTilesPerSecond;
    }

    public double wallClockSeconds() {
        return wallClockMs / 1000.0d;
    }

    public long swapGrowthBytes() {
        return hostSwapGrowthBytes();
    }

    public long hostSwapGrowthBytes() {
        return Math.max(0L, peakSwapUsedBytes - startSwapUsedBytes);
    }

    public long processTreeSwapGrowthBytes() {
        return Math.max(0L, processTreeSwapPeakBytes - processTreeSwapStartBytes);
    }

    public Map<String, String> toCsvRow() {
        final Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("configuration_id", configurationId);
        row.put("phase", phase);
        row.put("threads_per_tile", Integer.toString(threadsPerTile));
        row.put("fiji_heap_mb", Integer.toString(fijiHeapMb));
        row.put("tile_width", Integer.toString(tileWidthPx));
        row.put("tile_height", Integer.toString(tileHeightPx));
        row.put("success", success ? RCsvSupport.TRUE : RCsvSupport.FALSE);
        row.put("unsafe", unsafe ? RCsvSupport.TRUE : RCsvSupport.FALSE);
        row.put("unsafe_reason", unsafeReason);
        row.put("exit_code", Integer.toString(exitCode));
        row.put("wall_clock_ms", Long.toString(wallClockMs));
        row.put("throughput_tiles_per_second", RCsvSupport.formatNumber(throughputTilesPerSecond));
        row.put("tiles_completed", Integer.toString(tilesCompleted));
        row.put("mean_cpu_percent", RCsvSupport.formatNumber(meanCpuPercent));
        row.put("mean_process_cpu_percent", RCsvSupport.formatNumber(meanProcessCpuPercent));
        row.put("peak_rss_bytes", Long.toString(peakRssBytes));
        row.put("peak_system_used_ram_bytes", Long.toString(peakSystemUsedRamBytes));
        row.put("min_available_ram_bytes", Long.toString(minAvailableRamBytes));
        row.put("peak_swap_used_bytes", Long.toString(peakSwapUsedBytes));
        row.put("swap_growth_bytes", Long.toString(swapGrowthBytes()));
        row.put("host_swap_start_bytes", Long.toString(startSwapUsedBytes));
        row.put("host_swap_peak_bytes", Long.toString(peakSwapUsedBytes));
        row.put("host_swap_growth_bytes", Long.toString(hostSwapGrowthBytes()));
        row.put("process_tree_swap_start_bytes", Long.toString(processTreeSwapStartBytes));
        row.put("process_tree_swap_peak_bytes", Long.toString(processTreeSwapPeakBytes));
        row.put("process_tree_swap_growth_bytes", Long.toString(processTreeSwapGrowthBytes()));
        row.put("peak_load_average", RCsvSupport.formatNumber(peakLoadAverage));
        row.put("workload_type", workloadType);
        row.put("processed_pixels", Long.toString(processedPixels));
        row.put("throughput_pixels_per_second", RCsvSupport.formatNumber(throughputPixelsPerSecond));
        row.put("output_valid", outputValid ? RCsvSupport.TRUE : RCsvSupport.FALSE);
        row.put("classifier_sha256", classifierSha256);
        row.put("error", errorMessage);
        return row;
    }
}
