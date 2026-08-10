package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchManifestPlannerCliTest {

    @TempDir
    Path tempDir;

    @Test
    void unknownOptionReturnsExit2() {
        final int code = BatchManifestPlannerCli.run(new String[]{
                "--input-root", "/tmp",
                "--batch-root", "/tmp",
                "--config", "/tmp/x.properties",
                "--unknown", "1"
        });
        assertEquals(BatchManifestPlannerCli.EXIT_INVALID_ARGS, code);
    }

    @Test
    void missingArgumentReturnsExit2() {
        assertEquals(BatchManifestPlannerCli.EXIT_INVALID_ARGS,
                BatchManifestPlannerCli.run(new String[]{"--input-root"}));
        assertEquals(BatchManifestPlannerCli.EXIT_INVALID_ARGS,
                BatchManifestPlannerCli.run(new String[]{
                        "--input-root", "/tmp",
                        "--batch-root", "/tmp"
                }));
        assertEquals(BatchManifestPlannerCli.EXIT_INVALID_ARGS,
                BatchManifestPlannerCli.run(new String[]{
                        "--input-root", "/tmp",
                        "--batch-root", "/tmp",
                        "--config"
                }));
    }

    @Test
    void successfulSubsetPlan() throws Exception {
        final Path input = tempDir.resolve("cli-input");
        final Path batch = tempDir.resolve("cli-batch");
        Files.createDirectories(input);
        Files.write(input.resolve("010101.tif"), "a".getBytes(StandardCharsets.UTF_8));
        Files.write(input.resolve("010102.tif"), "b".getBytes(StandardCharsets.UTF_8));
        final Path config = tempDir.resolve("cli.properties");
        Files.write(config, BatchTestConfigs.validPropertiesText(false, 1, false)
                .getBytes(StandardCharsets.UTF_8));

        final int code = BatchManifestPlannerCli.run(new String[]{
                "--input-root", input.toString(),
                "--batch-root", batch.toString(),
                "--config", config.toString()
        });
        assertEquals(BatchManifestPlannerCli.EXIT_SUCCESS, code);
        assertTrue(Files.isRegularFile(batch.resolve("master_batch_manifest.csv")));
        assertTrue(Files.isRegularFile(batch.resolve("machines/machine_01_manifest.csv")));
        assertTrue(Files.isRegularFile(batch.resolve("machine_assignment_summary.txt")));
        assertTrue(Files.isRegularFile(batch.resolve("batch_plan_report.txt")));
    }

    @Test
    void shellScriptsExistAndAreExecutable() throws Exception {
        final Path plan = projectRoot().resolve("runtime/batch/plan_batch.sh");
        final Path inspect = projectRoot().resolve("runtime/batch/inspect_batch_plan.sh");
        assertTrue(Files.isRegularFile(plan), "missing " + plan);
        assertTrue(Files.isRegularFile(inspect), "missing " + inspect);
        assertTrue(Files.isExecutable(plan), "not executable: " + plan);
        assertTrue(Files.isExecutable(inspect), "not executable: " + inspect);

        // Also verify POSIX execute bit when available.
        try {
            final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(plan);
            assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE)
                    || perms.contains(PosixFilePermission.GROUP_EXECUTE)
                    || perms.contains(PosixFilePermission.OTHERS_EXECUTE));
            assertEquals(EnumSet.copyOf(perms), perms);
        } catch (final UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        }
    }

    @Test
    void defaultBatchPropertiesExists() {
        final Path props = projectRoot().resolve("runtime/batch/default_batch.properties");
        assertTrue(Files.isRegularFile(props));
    }

    private static Path projectRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("runtime/batch"))) {
            return cwd;
        }
        // surefire may run from module root already; walk up a little
        Path cur = cwd;
        for (int i = 0; i < 4; i++) {
            if (Files.isDirectory(cur.resolve("runtime/batch"))) {
                return cur;
            }
            cur = cur.getParent();
            if (cur == null) {
                break;
            }
        }
        return cwd;
    }
}
