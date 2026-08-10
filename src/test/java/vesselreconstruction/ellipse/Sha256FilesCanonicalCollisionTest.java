package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sha256FilesCanonicalCollisionTest {

    @Test
    void commitsMissingFileAndReusesIdenticalContent(@TempDir final Path tempDir) throws Exception {
        final Path target = tempDir.resolve("canonical/output.bin");
        final byte[] content = "same bytes".getBytes(StandardCharsets.UTF_8);

        final String first = Sha256Files.commitCanonicalBytes(target, content);
        final String second = Sha256Files.commitCanonicalBytes(target, content);

        assertTrue(Files.isRegularFile(target));
        assertEquals(first, second);
        assertEquals("same bytes", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsDifferentCanonicalContentWithoutSuffix(@TempDir final Path tempDir) throws Exception {
        final Path target = tempDir.resolve("canonical/output.bin");
        Sha256Files.commitCanonicalBytes(target, "first".getBytes(StandardCharsets.UTF_8));

        assertThrows(java.io.IOException.class,
                () -> Sha256Files.commitCanonicalBytes(
                        target, "second".getBytes(StandardCharsets.UTF_8)));
        assertEquals("first", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        assertFalseSiblingSuffixExists(tempDir.resolve("canonical"), "output.bin");
    }

    private static void assertFalseSiblingSuffixExists(final Path directory, final String canonicalName)
            throws Exception {
        try (java.nio.file.DirectoryStream<Path> paths = Files.newDirectoryStream(directory)) {
            for (final Path path : paths) {
                assertEquals(canonicalName, path.getFileName().toString());
            }
        }
    }
}
