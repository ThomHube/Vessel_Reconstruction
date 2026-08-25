package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkFijiCommandsTest {

    @Test
    void classificationCommandReusesProductionFijiLauncherAndPlugin() {
        final Path fiji = Paths.get("/opt/Fiji.app/ImageJ-linux64");
        final Path job = Paths.get("/tmp/benchmark/job.properties");
        final List<String> command = HardwareBenchmarkFijiCommands.classificationCommand(
                fiji, job, 2048);
        assertEquals(fiji.toAbsolutePath().normalize().toString(), command.get(0));
        assertEquals("--heap=2048m", command.get(1));
        assertTrue(command.contains("--headless"));
        assertTrue(command.contains("--main-class"));
        assertTrue(command.contains("vesselreconstruction.ellipse.BatchFullProcessingImagePlugin"));
        assertTrue(command.contains(job.toAbsolutePath().normalize().toString()));
        final List<String> production = BatchFullProcessingCli.buildFijiCommand(fiji, job);
        assertEquals(production.size() + 1, command.size());
        assertEquals(production.get(0), command.get(0));
        assertEquals(production.subList(1, production.size()), command.subList(2, command.size()));
    }

    @Test
    void classificationCommandCanUseIsolatedPluginsDirectory() {
        final Path fiji = Paths.get("/opt/Fiji.app/ImageJ-linux64");
        final Path job = Paths.get("/tmp/benchmark/job.properties");
        final Path plugins = Paths.get("/tmp/benchmark/fiji_plugins");
        final List<String> command = HardwareBenchmarkFijiCommands.classificationCommand(
                fiji, job, 2048, plugins);
        assertEquals("--plugins=" + plugins.toAbsolutePath().normalize(), command.get(1));
        assertEquals("--heap=2048m", command.get(2));
        assertTrue(command.contains("vesselreconstruction.ellipse.BatchFullProcessingImagePlugin"));
    }

    @Test
    void writeConfigKeeps1024x1024ProductionTileProfile(@TempDir final Path dir) throws Exception {
        final Path template = Paths.get("runtime/batch/default_batch.properties");
        final Path dest = dir.resolve("production_batch.properties");
        HardwareBenchmarkClassificationSupport.writeConfigWithThreads(template, dest, 8);
        final BatchConfiguration loaded = BatchConfiguration.load(dest);
        assertEquals(8, loaded.threadsPerTile());
        final BatchTileProfile tile = HardwareExecutionProfile.requireProductionTileProfile(
                loaded.tileProfiles());
        assertEquals(1024, tile.widthPx());
        assertEquals(1024, tile.heightPx());
        final String text = new String(Files.readAllBytes(dest), StandardCharsets.UTF_8);
        assertTrue(text.contains("classification.tile_profiles=2048x2048;1536x1536;1024x1024"));
        assertTrue(text.contains("classification.threads_per_tile=8"));
    }
}
