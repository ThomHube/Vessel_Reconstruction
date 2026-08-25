package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkProbeTest {

    @Test
    void syntheticTilesAreExactly1024By1024() {
        assertEquals(1024, HardwareBenchmarkProbe.TILE_WIDTH);
        assertEquals(1024, HardwareBenchmarkProbe.TILE_HEIGHT);
        assertEquals(1024 * 1024 * 4, HardwareBenchmarkProbe.tileBytes(0).length);
        assertEquals(1, HardwareBenchmarkProbe.processTile(HardwareBenchmarkProbe.tileBytes(3), 1));
    }

    @Test
    void rejectsNonProductionTilePayload() {
        assertThrows(IllegalArgumentException.class,
                () -> HardwareBenchmarkProbe.processTile(new byte[512 * 512 * 4], 1));
    }

    @Test
    void deterministicInputsAreUnchangedByProcessing(@TempDir final Path dir) throws Exception {
        final Path input = dir.resolve("input");
        final List<Path> files = HardwareBenchmarkProbe.writeDeterministicInputs(input, 2);
        assertEquals(2, files.size());
        final String before = Sha256Files.sha256OfFile(files.get(0));
        final HardwareBenchmarkProbe.Result result = HardwareBenchmarkProbe.run(input, 1, 1);
        assertTrue(result.success);
        assertEquals(2, result.tilesCompleted);
        assertEquals(before, Sha256Files.sha256OfFile(files.get(0)));
        assertTrue(Files.isRegularFile(files.get(0)));
    }
}
