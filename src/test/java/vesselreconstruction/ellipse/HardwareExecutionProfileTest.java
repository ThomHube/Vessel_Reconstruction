package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareExecutionProfileTest {

    @Test
    void defaultProductionConfigIsSingleMachineWithFixedTileGeometry() throws Exception {
        final Path configPath = Paths.get("runtime/batch/default_batch.properties");
        final BatchConfiguration loaded = BatchConfiguration.load(configPath);
        assertEquals(HardwareExecutionProfile.PRODUCTION_MACHINE_COUNT, loaded.machinesCount());
        final BatchTileProfile production = HardwareExecutionProfile.requireProductionTileProfile(
                loaded.tileProfiles());
        assertEquals(HardwareExecutionProfile.PRODUCTION_TILE_WIDTH_PX, production.widthPx());
        assertEquals(HardwareExecutionProfile.PRODUCTION_TILE_HEIGHT_PX, production.heightPx());
        assertEquals(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN, production.toToken());
        assertTrue(loaded.threadsPerTile() >= 1);
    }

    @Test
    void raisingInternalConcurrencyDoesNotChangeTileGeometry() {
        final Map<String, String> values = BatchTestConfigs.validMap(false, 1);
        final String originalProfiles = values.get(BatchConfiguration.KEY_CLASSIFICATION_TILE_PROFILES);
        new HardwareExecutionProfile(8).applyThreadsPerTile(values);

        final BatchConfiguration loaded = BatchConfiguration.fromMap(values);
        assertEquals(8, loaded.threadsPerTile());
        assertEquals(1, loaded.machinesCount());
        assertEquals(originalProfiles, values.get(BatchConfiguration.KEY_CLASSIFICATION_TILE_PROFILES));
        final BatchTileProfile production = HardwareExecutionProfile.requireProductionTileProfile(
                loaded.tileProfiles());
        assertEquals(1024, production.widthPx());
        assertEquals(1024, production.heightPx());
    }

    @Test
    void productionTileProfileIsIndependentOfMachineCountInLibraryConfigs() {
        final Map<String, String> values = BatchTestConfigs.validMap(false, 3);
        final BatchConfiguration loaded = BatchConfiguration.fromMap(values);
        final BatchTileProfile production = HardwareExecutionProfile.requireProductionTileProfile(
                loaded.tileProfiles());
        assertEquals(HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN, production.toToken());
        assertEquals(3, loaded.machinesCount());
    }

    @Test
    void rejectsNonPositiveThreads() {
        assertThrows(IllegalArgumentException.class, () -> new HardwareExecutionProfile(0));
        assertThrows(IllegalArgumentException.class, () -> new HardwareExecutionProfile(-1));
    }

    @Test
    void writeAndLoadRoundTripPreservesExecutionKnobs(@TempDir final Path dir) throws Exception {
        final Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put("hardware.hostname", "testhost");
        meta.put("benchmark.reason", "stable throughput");
        final HardwareExecutionProfile original = new HardwareExecutionProfile(4, 2048, meta);
        final Path file = dir.resolve("selected_profile.properties");
        original.writeProperties(file);

        final HardwareExecutionProfile loaded = HardwareExecutionProfile.load(file);
        assertEquals(4, loaded.threadsPerTile());
        assertEquals(2048, loaded.fijiHeapMb());
        assertEquals("1024", loaded.metadata().get(HardwareExecutionProfile.KEY_TILE_WIDTH));
        assertEquals("1024", loaded.metadata().get(HardwareExecutionProfile.KEY_TILE_HEIGHT));
        assertEquals("1", loaded.metadata().get("machines.count"));
        assertEquals(HardwareExecutionProfile.PRODUCTION_MACHINE_ID,
                loaded.metadata().get("machine_id"));
        assertEquals(1, loaded.fijiHeapArguments().size());
        assertEquals("--heap=2048m", loaded.fijiHeapArguments().get(0));
    }

    @Test
    void rejectsNonProductionTileGeometry() {
        final Map<String, String> values = new LinkedHashMap<String, String>();
        values.put(HardwareExecutionProfile.KEY_TILE_WIDTH, "512");
        values.put(HardwareExecutionProfile.KEY_TILE_HEIGHT, "512");
        values.put(HardwareExecutionProfile.KEY_THREADS, "2");
        assertThrows(IllegalArgumentException.class, () -> HardwareExecutionProfile.fromMap(values));
    }

    @Test
    void unspecifiedHeapDoesNotEmitFijiArgument() {
        assertTrue(new HardwareExecutionProfile(1).fijiHeapArguments().isEmpty());
    }

    @Test
    void loadValidatedForProductionAcceptsRealClassificationProfile(@TempDir final Path dir)
            throws Exception {
        final Path file = writeProfile(dir, HardwareExecutionProfile.STATUS_REAL_VALIDATED, 16, 3433);
        final HardwareExecutionProfile loaded =
                HardwareExecutionProfile.loadValidatedForProduction(file);
        assertEquals(16, loaded.threadsPerTile());
        assertEquals(3433, loaded.fijiHeapMb());
        assertEquals("--heap=3433m", loaded.fijiHeapArguments().get(0));
    }

    @Test
    void loadValidatedForProductionRejectsSyntheticOnly(@TempDir final Path dir) throws Exception {
        final Path file = writeProfile(dir, HardwareExecutionProfile.STATUS_SYNTHETIC_ONLY, 8, 2048);
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HardwareExecutionProfile.loadValidatedForProduction(file));
        assertTrue(ex.getMessage().contains(HardwareExecutionProfile.STATUS_REAL_VALIDATED));
    }

    @Test
    void loadValidatedForProductionRejectsMissingFile(@TempDir final Path dir) {
        final Path missing = dir.resolve("missing.properties");
        assertThrows(IllegalArgumentException.class,
                () -> HardwareExecutionProfile.loadValidatedForProduction(missing));
    }

    @Test
    void loadValidatedForProductionRejectsMissingHeap(@TempDir final Path dir) throws Exception {
        final Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_STATUS,
                HardwareExecutionProfile.STATUS_REAL_VALIDATED);
        final Path file = dir.resolve("selected_profile.properties");
        new HardwareExecutionProfile(4, 0, meta).writeProperties(file);
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HardwareExecutionProfile.loadValidatedForProduction(file));
        assertTrue(ex.getMessage().contains(HardwareExecutionProfile.KEY_FIJI_HEAP_MB));
    }

    @Test
    void loadValidatedForProductionRejectsInvalidThreads(@TempDir final Path dir) throws Exception {
        final Path file = writeProfile(dir, HardwareExecutionProfile.STATUS_REAL_VALIDATED, 4, 2048);
        final String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("classification.threads_per_tile=4", "classification.threads_per_tile=0");
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> HardwareExecutionProfile.loadValidatedForProduction(file));
    }

    @Test
    void resolveFromEnvironmentIgnoresBlankPath() {
        assertNull(HardwareExecutionProfile.resolveFromEnvironment(null));
        assertNull(HardwareExecutionProfile.resolveFromEnvironment(""));
        assertNull(HardwareExecutionProfile.resolveFromEnvironment("   "));
    }

    @Test
    void resolveFromEnvironmentLoadsValidatedProfile(@TempDir final Path dir) throws Exception {
        final Path file = writeProfile(dir, HardwareExecutionProfile.STATUS_REAL_VALIDATED, 8, 4096);
        final HardwareExecutionProfile loaded =
                HardwareExecutionProfile.resolveFromEnvironment(file.toString());
        assertEquals(8, loaded.threadsPerTile());
        assertEquals(4096, loaded.fijiHeapMb());
    }

    @Test
    void classificationThreadsPerTileUsesProfileWhenPresent() {
        final Map<String, String> values = BatchTestConfigs.validMap(false, 1);
        final BatchConfiguration config = BatchConfiguration.fromMap(values);
        assertEquals(config.threadsPerTile(),
                HardwareExecutionProfile.classificationThreadsPerTile(config, null));
        assertEquals(16, HardwareExecutionProfile.classificationThreadsPerTile(
                config, new HardwareExecutionProfile(16, 3433)));
    }

    @Test
    void applyThreadsToConfigFileDoesNotModifySourceOrTileProfiles(@TempDir final Path dir)
            throws Exception {
        final Path source = Paths.get("runtime/batch/default_batch.properties");
        final byte[] original = Files.readAllBytes(source);
        final Path dest = dir.resolve("production_batch.applied.properties");
        new HardwareExecutionProfile(16, 3433).applyThreadsToConfigFile(source, dest);

        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(source)));
        final BatchConfiguration loaded = BatchConfiguration.load(dest);
        assertEquals(16, loaded.threadsPerTile());
        final String originalProfiles = BatchConfiguration.load(source).tileProfiles().toString();
        assertEquals(originalProfiles, loaded.tileProfiles().toString());
        final String destText = new String(Files.readAllBytes(dest), StandardCharsets.UTF_8);
        assertTrue(destText.contains("classification.tile_profiles=2048x2048;1536x1536;1024x1024"));
        assertTrue(destText.contains("classification.threads_per_tile=16"));
        HardwareExecutionProfile.requireProductionTileProfile(loaded.tileProfiles());
    }

    @Test
    void applyThreadsToConfigFileRefusesToOverwriteSource(@TempDir final Path dir) throws Exception {
        final Path source = dir.resolve("production_batch.properties");
        Files.copy(Paths.get("runtime/batch/default_batch.properties"), source);
        assertThrows(IllegalArgumentException.class,
                () -> new HardwareExecutionProfile(8, 2048).applyThreadsToConfigFile(source, source));
    }

    private static Path writeProfile(
            final Path dir,
            final String status,
            final int threads,
            final int heapMb) throws Exception {
        final Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_STATUS, status);
        meta.put(HardwareExecutionProfile.KEY_BENCHMARK_WORKLOAD,
                HardwareExecutionProfile.STATUS_REAL_VALIDATED.equals(status)
                        ? HardwareExecutionProfile.WORKLOAD_REAL
                        : HardwareExecutionProfile.WORKLOAD_SYNTHETIC);
        final Path file = dir.resolve("selected_profile.properties");
        new HardwareExecutionProfile(threads, heapMb, meta).writeProperties(file);
        return file;
    }
}
