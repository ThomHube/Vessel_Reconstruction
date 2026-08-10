package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputPathAllocatorTest {

    @Test
    void existingFileIsNotOverwritten(@TempDir final File tempDir) throws Exception {
        final String filename = "classified_segment.tif";
        final File existing = new File(tempDir, filename);
        Files.write(existing.toPath(), "existing".getBytes(StandardCharsets.UTF_8));

        final File allocated = OutputPathAllocator.allocateUnique(tempDir, filename);
        assertNotEquals(existing.getAbsolutePath(), allocated.getAbsolutePath());
        assertFalse(allocated.exists());
        assertTrue(allocated.getName().startsWith("classified_segment__"));
        assertTrue(allocated.getName().endsWith(".tif"));

        final String originalContents = new String(
                Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8);
        assertEquals("existing", originalContents);
    }

    @Test
    void unusedFilenameIsReturnedAsIs(@TempDir final File tempDir) {
        final String filename = "new_output.tif";
        final File allocated = OutputPathAllocator.allocateUnique(tempDir, filename);
        assertEquals(new File(tempDir, filename).getAbsolutePath(),
                allocated.getAbsolutePath());
    }

    @Test
    void createRunIdSanitizesComponents() {
        final String runId = OutputPathAllocator.createRunId(
                "source image", "segment/1", "2026-08-01 12:00");
        assertFalse(runId.contains(" "));
        assertFalse(runId.contains("/"));
        assertTrue(runId.contains("__"));
    }
}
