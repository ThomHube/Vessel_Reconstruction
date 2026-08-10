package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchTiffDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptTifAndTiffAndUppercase() throws Exception {
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(tempDir.resolve("010102.tiff"), "b");
        writeBytes(tempDir.resolve("010201.TIF"), "c");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(3, result.validTiffs().size());
        assertEquals("010101", result.validTiffs().get(0).id().imageId());
        assertEquals("010102", result.validTiffs().get(1).id().imageId());
        assertEquals("010201", result.validTiffs().get(2).id().imageId());
        assertTrue(result.malformedTiffLikeFiles().isEmpty());
    }

    @Test
    void rejectSuffixes() throws Exception {
        writeBytes(tempDir.resolve("010101-copy.tif"), "x");
        writeBytes(tempDir.resolve("010101_1.tif"), "y");
        writeBytes(tempDir.resolve("sample010101.tif"), "z");
        writeBytes(tempDir.resolve("010101.tif.tmp"), "w");
        writeBytes(tempDir.resolve("010101.jpg"), "j");
        writeBytes(tempDir.resolve("01010.tif"), "s");
        writeBytes(tempDir.resolve("0101011.tif"), "l");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertTrue(result.validTiffs().isEmpty());
        // .tif.tmp and .jpg are non-TIFF by extension; the five *.tif names are malformed TIFF-like.
        assertEquals(5, result.malformedTiffLikeFiles().size());
        assertEquals(2, result.nonTiffFiles().size());
        assertTrue(result.hasBlockingErrors());
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("010101.tif.tmp"));
    }

    @Test
    void rejectDuplicateIds() throws Exception {
        final Path nested = tempDir.resolve("sub");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(nested.resolve("010101.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, true, false);
        assertTrue(result.validTiffs().isEmpty());
        assertFalse(result.duplicateIds().isEmpty());
        assertTrue(result.hasBlockingErrors());
    }

    @Test
    void rejectTifTiffDuplicateVariants() throws Exception {
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(tempDir.resolve("010101.tiff"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertTrue(result.validTiffs().isEmpty());
        assertEquals(1, result.duplicateIds().size());
        assertTrue(result.hasBlockingErrors());
    }

    @Test
    void topLevelDiscoveryIgnoresNested() throws Exception {
        final Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(nested.resolve("010102.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(1, result.validTiffs().size());
        assertEquals("010101", result.validTiffs().get(0).id().imageId());
    }

    @Test
    void recursiveDiscoveryFindsNested() throws Exception {
        final Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(nested.resolve("010102.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, true, false);
        assertEquals(2, result.validTiffs().size());
        assertEquals("nested/010102.tif",
                result.validTiffs().get(1).relativePath().toString().replace('\\', '/'));
    }

    @Test
    void relativeSourcePathsAndSha256() throws Exception {
        final Path file = tempDir.resolve("010101.tif");
        writeBytes(file, "hello-sha");
        final String expectedSha = Sha256Files.sha256OfBytes("hello-sha".getBytes(StandardCharsets.UTF_8));
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(1, result.validTiffs().size());
        final BatchTiffFile tiff = result.validTiffs().get(0);
        assertEquals("010101.tif", tiff.relativePath().toString().replace('\\', '/'));
        assertFalse(tiff.relativePath().isAbsolute());
        assertEquals(expectedSha, tiff.sha256());
        assertEquals(Files.size(file), tiff.sizeBytes());
    }

    @Test
    void missingIdReportingAndStrictFullSet() throws Exception {
        writeBytes(tempDir.resolve("010101.tif"), "a");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, true);
        assertEquals(1, result.validTiffs().size());
        assertEquals(79, result.missingExpectedIds().size());
        assertEquals("010102", result.missingExpectedIds().get(0).imageId());
        assertEquals("022002",
                result.missingExpectedIds().get(result.missingExpectedIds().size() - 1).imageId());
    }

    @Test
    void subsetAcceptedWhenCompletenessDisabled() throws Exception {
        writeBytes(tempDir.resolve("010101.tif"), "a");
        writeBytes(tempDir.resolve("020102.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(2, result.validTiffs().size());
        assertFalse(result.hasBlockingErrors());
        assertEquals("010101", result.validTiffs().get(0).id().imageId());
        assertEquals("020102", result.validTiffs().get(1).id().imageId());
    }

    @Test
    void parseFilenameAcceptsAndRejects() {
        assertEquals("010101", BatchTiffFile.parseFilename("010101.tif").id.imageId());
        assertEquals(".tiff", BatchTiffFile.parseFilename("010102.TIFF").extension);
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("010101-copy.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("010101.jpg"));
    }

    private static void writeBytes(final Path path, final String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
