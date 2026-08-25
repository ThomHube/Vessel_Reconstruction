package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareBenchmarkCliTest {

    @Test
    void parseArgsAcceptsSyntheticOnlyFlagWithoutValue() {
        final Map<String, String> values = HardwareBenchmarkCli.parseArgs(new String[] {
                "--synthetic-only",
                "--output-root",
                "hardware_benchmark"
        });
        assertTrue(HardwareBenchmarkCli.flag(values, "--synthetic-only"));
        assertEquals("hardware_benchmark", values.get("--output-root"));
        assertFalse(HardwareBenchmarkCli.flag(values, "--skip-synthetic"));
    }

    @Test
    void defaultModeRequiresFijiLauncher(@TempDir final Path dir) {
        final Map<String, String> values = HardwareBenchmarkCli.parseArgs(new String[] {
                "--output-root", dir.toString(),
                "--classes-dir", dir.toString()
        });
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> HardwareBenchmarkCli.execute(values));
        assertTrue(ex.getMessage().contains("fiji-launcher"));
    }

    @Test
    void defaultModeRequiresClassifierWhenFijiIsPresent(@TempDir final Path dir) throws Exception {
        final Path fiji = dir.resolve("ImageJ-linux64");
        Files.write(fiji, new byte[] {1});
        final Map<String, String> values = HardwareBenchmarkCli.parseArgs(new String[] {
                "--output-root", dir.toString(),
                "--classes-dir", dir.toString(),
                "--fiji-launcher", fiji.toString()
        });
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> HardwareBenchmarkCli.execute(values));
        assertTrue(ex.getMessage().contains("classifier"));
    }
}
