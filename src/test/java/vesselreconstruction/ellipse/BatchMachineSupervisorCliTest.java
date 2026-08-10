package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineSupervisorCliTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsUnknownOption() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BatchMachineSupervisorCli.parseArgs(new String[] {
                        "--input-root", "a",
                        "--batch-root", "b",
                        "--machine-id", "machine_01",
                        "--config", "c",
                        "--classifier", "d",
                        "--fiji-launcher", "e",
                        "--tile-profile-index", "0",
                        "--extra", "x"
                }));
        assertTrue(ex.getMessage().contains("Unknown option"));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineSupervisorCli.parseArgs(new String[] {
                        "--input-root"
                }));
    }

    @Test
    void exitCodesAreDistinct() {
        assertEquals(0, BatchMachineSupervisorCli.exitCodeFor(
                BatchMachineSupervisorStatus.MACHINE_SUCCESS));
        assertEquals(10, BatchMachineSupervisorCli.exitCodeFor(
                BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_BLOCKED_IMAGES));
        assertEquals(11, BatchMachineSupervisorCli.exitCodeFor(
                BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES));
        assertEquals(2, BatchMachineSupervisorCli.exitCodeFor(
                BatchMachineSupervisorStatus.MACHINE_FAILED_CONFIGURATION));
        assertEquals(6, BatchMachineSupervisorCli.exitCodeFor(
                BatchMachineSupervisorStatus.MACHINE_FAILED_UNEXPECTED));
    }

    @Test
    void runMachineScriptUsesTargetClassesRequiresSevenArgsAndPreservesQuotes()
            throws Exception {
        final Path script = projectRoot().resolve("runtime/batch/run_machine.sh");
        final String text = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        assertTrue(text.contains("target/classes"));
        assertTrue(text.contains("BatchMachineSupervisorCli"));
        assertTrue(text.contains("$# -ne 7"));
        assertTrue(text.contains("\"${INPUT_ROOT}\""));
        assertTrue(text.contains("\"${BATCH_ROOT}\""));
        assertTrue(text.contains("\"${FIJI_LAUNCHER}\""));

        final Path inspect = projectRoot().resolve("runtime/batch/inspect_machine_run.sh");
        final String inspectText = new String(Files.readAllBytes(inspect), StandardCharsets.UTF_8);
        assertTrue(inspectText.contains("BatchMachineRunPlanCli"));
        assertTrue(inspectText.contains("$# -ne 5"));
        assertFalse(inspectText.contains("BatchMachineSupervisorCli"));
    }

    @Test
    void parseArgsAcceptsTileProfileIndex() {
        final Path classes = tempDir.resolve("classes-override");
        try {
            Files.createDirectories(classes);
        } catch (final Exception ex) {
            throw new AssertionError(ex);
        }
        System.setProperty("vesselreconstruction.classes.dir", classes.toString());
        try {
            final BatchMachineSupervisorCli.ParsedArgs parsed =
                    BatchMachineSupervisorCli.parseArgs(new String[] {
                            "--input-root", tempDir.resolve("in").toString(),
                            "--batch-root", tempDir.resolve("batch").toString(),
                            "--machine-id", "machine_02",
                            "--config", tempDir.resolve("cfg").toString(),
                            "--classifier", tempDir.resolve("model").toString(),
                            "--fiji-launcher", tempDir.resolve("fiji").toString(),
                            "--tile-profile-index", "3"
                    });
            assertEquals(3, parsed.tileProfileIndex);
            assertEquals("machine_02", parsed.machineId);
            assertEquals(classes.toAbsolutePath().normalize(),
                    parsed.classesDirectory.toAbsolutePath().normalize());
        } finally {
            System.clearProperty("vesselreconstruction.classes.dir");
        }
    }

    private static Path projectRoot() {
        Path dir = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            if (Files.isRegularFile(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException("Unable to locate project root");
    }
}
