package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDatasetLayoutTest {

    @Test
    void ensureDirectoriesExistCreatesEveryRequiredDirectory(@TempDir final Path tempDir)
            throws Exception {
        final ProductionDatasetLayout layout =
                new ProductionDatasetLayout(tempDir.resolve("dataset"));

        layout.ensureDirectoriesExist();

        for (final String relative : ProductionDatasetLayout.REQUIRED_RELATIVE_DIRS) {
            assertTrue(Files.isDirectory(layout.resolveUnderRoot(relative)), relative);
        }
    }

    @Test
    void rejectsExistingFileAsDatasetRoot(@TempDir final Path tempDir) throws Exception {
        final Path file = Files.createFile(tempDir.resolve("not-a-directory"));

        assertThrows(IllegalArgumentException.class, () -> new ProductionDatasetLayout(file));
    }

    @Test
    void resolveUnderRootRejectsPathTraversal(@TempDir final Path tempDir) {
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(tempDir.resolve("dataset"));

        assertThrows(IllegalArgumentException.class, () -> layout.resolveUnderRoot("../escape.csv"));
    }
}
