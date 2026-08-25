package vesselreconstruction.ellipse;

final class HardwareBenchmarkFixtures {

    private HardwareBenchmarkFixtures() {
    }

    static HardwareInventory inventory(
            final int physicalCores,
            final int logicalCpus,
            final long ramBytes) {
        final int threadsPerCore = Math.max(1, (logicalCpus + physicalCores - 1) / physicalCores);
        return new HardwareInventory(
                "testhost",
                "Test CPU",
                physicalCores,
                logicalCpus,
                threadsPerCore,
                ramBytes,
                0L,
                "Linux",
                "test-kernel",
                "1.8.0_test",
                "NA");
    }

    static HardwareInventory eightCoreSixteenGib() {
        return inventory(8, 16, 16L * 1024L * 1024L * 1024L);
    }

    static HardwareBenchmarkRunResult ok(
            final String id,
            final String phase,
            final int threads,
            final int heapMb,
            final double throughput,
            final double cpuPercent,
            final long rssBytes,
            final long swapUsedBytes) {
        return new HardwareBenchmarkRunResult(
                id,
                phase,
                threads,
                heapMb,
                true,
                false,
                RCsvSupport.NA,
                0,
                1000L,
                throughput,
                8,
                cpuPercent,
                cpuPercent,
                rssBytes,
                rssBytes,
                4L * 1024L * 1024L * 1024L,
                swapUsedBytes,
                swapUsedBytes,
                1.0d,
                RCsvSupport.NA);
    }

    static HardwareBenchmarkRunResult unsafe(
            final String id,
            final String phase,
            final int threads,
            final int heapMb,
            final String reason,
            final double throughput) {
        return new HardwareBenchmarkRunResult(
                id,
                phase,
                threads,
                heapMb,
                false,
                true,
                reason,
                1,
                1000L,
                throughput,
                0,
                99.0d,
                99.0d,
                8L * 1024L * 1024L * 1024L,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L * 1024L,
                256L * 1024L * 1024L,
                0L,
                8.0d,
                reason);
    }

    static HardwareBenchmarkRunResult okReal(
            final String id,
            final String phase,
            final int threads,
            final int heapMb,
            final double pixelsPerSecond,
            final double cpuPercent,
            final long rssBytes,
            final long swapUsedBytes,
            final long processedPixels) {
        final int tiles = (int) Math.max(1L, processedPixels
                / (HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX
                * HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX));
        return new HardwareBenchmarkRunResult(
                id,
                phase,
                threads,
                heapMb,
                true,
                false,
                RCsvSupport.NA,
                0,
                1000L,
                tiles / 1.0d,
                tiles,
                cpuPercent,
                cpuPercent,
                rssBytes,
                rssBytes,
                4L * 1024L * 1024L * 1024L,
                swapUsedBytes,
                swapUsedBytes,
                1.0d,
                RCsvSupport.NA,
                HardwareExecutionProfile.WORKLOAD_REAL,
                processedPixels,
                pixelsPerSecond,
                true,
                "classifier-sha-test");
    }
}
