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
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(tempDir.resolve("0101A2.tiff"), "b");
        writeBytes(tempDir.resolve("0102A1.TIF"), "c");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(3, result.validTiffs().size());
        assertEquals("0101A1", result.validTiffs().get(0).id().imageId());
        assertEquals("0101A2", result.validTiffs().get(1).id().imageId());
        assertEquals("0102A1", result.validTiffs().get(2).id().imageId());
        assertTrue(result.malformedTiffLikeFiles().isEmpty());
    }

    @Test
    void rejectSuffixesAndObsoleteNumericIds() throws Exception {
        writeBytes(tempDir.resolve("0101A1-copy.tif"), "x");
        writeBytes(tempDir.resolve("0101A1_1.tif"), "y");
        writeBytes(tempDir.resolve("sample0101A1.tif"), "z");
        writeBytes(tempDir.resolve("0101A1.tif.tmp"), "w");
        writeBytes(tempDir.resolve("0101A1.jpg"), "j");
        writeBytes(tempDir.resolve("0101A.tif"), "s");
        writeBytes(tempDir.resolve("0101A11.tif"), "l");
        writeBytes(tempDir.resolve("010101.tif"), "obsolete");
        writeBytes(tempDir.resolve("0101a1.tif"), "lower");
        writeBytes(tempDir.resolve("0101C1.tif"), "badcore");
        writeBytes(tempDir.resolve("0101A0.tif"), "seg0");
        writeBytes(tempDir.resolve("0101A6.tif"), "seg6");
        writeBytes(tempDir.resolve("0001A1.tif"), "plot00");
        writeBytes(tempDir.resolve("0100A1.tif"), "tree00");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertTrue(result.validTiffs().isEmpty());
        // .tif.tmp and .jpg are non-TIFF by extension; remaining *.tif names are malformed.
        assertEquals(12, result.malformedTiffLikeFiles().size());
        assertEquals(2, result.nonTiffFiles().size());
        assertTrue(result.hasBlockingErrors());
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("0101A1.tif.tmp"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("010101.tif"));
    }

    @Test
    void rejectDuplicateIds() throws Exception {
        final Path nested = tempDir.resolve("sub");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(nested.resolve("0101A1.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, true, false);
        assertTrue(result.validTiffs().isEmpty());
        assertFalse(result.duplicateIds().isEmpty());
        assertTrue(result.hasBlockingErrors());
    }

    @Test
    void rejectTifTiffDuplicateVariants() throws Exception {
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(tempDir.resolve("0101A1.tiff"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertTrue(result.validTiffs().isEmpty());
        assertEquals(1, result.duplicateIds().size());
        assertTrue(result.hasBlockingErrors());
    }

    @Test
    void topLevelDiscoveryIgnoresNested() throws Exception {
        final Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(nested.resolve("0101A2.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(1, result.validTiffs().size());
        assertEquals("0101A1", result.validTiffs().get(0).id().imageId());
    }

    @Test
    void recursiveDiscoveryFindsNested() throws Exception {
        final Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(nested.resolve("0101A2.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, true, false);
        assertEquals(2, result.validTiffs().size());
        assertEquals("nested/0101A2.tif",
                result.validTiffs().get(1).relativePath().toString().replace('\\', '/'));
    }

    @Test
    void relativeSourcePathsAndSha256() throws Exception {
        final Path file = tempDir.resolve("0101A1.tif");
        writeBytes(file, "hello-sha");
        final String expectedSha = Sha256Files.sha256OfBytes("hello-sha".getBytes(StandardCharsets.UTF_8));
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(1, result.validTiffs().size());
        final BatchTiffFile tiff = result.validTiffs().get(0);
        assertEquals("0101A1.tif", tiff.relativePath().toString().replace('\\', '/'));
        assertFalse(tiff.relativePath().isAbsolute());
        assertEquals(expectedSha, tiff.sha256());
        assertEquals(Files.size(file), tiff.sizeBytes());
    }

    @Test
    void missingExpectedIdsAlwaysEmptyEvenWhenStrictFlagPassed() throws Exception {
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, true);
        assertEquals(1, result.validTiffs().size());
        assertTrue(result.missingExpectedIds().isEmpty());
        assertFalse(result.hasBlockingErrors());
    }

    @Test
    void subsetAcceptedWhenCompletenessDisabled() throws Exception {
        writeBytes(tempDir.resolve("0101A1.tif"), "a");
        writeBytes(tempDir.resolve("0201A1.tif"), "b");
        final BatchTiffDiscovery.Result result = BatchTiffDiscovery.discover(tempDir, false, false);
        assertEquals(2, result.validTiffs().size());
        assertFalse(result.hasBlockingErrors());
        assertEquals("0101A1", result.validTiffs().get(0).id().imageId());
        assertEquals("0201A1", result.validTiffs().get(1).id().imageId());
    }

    @Test
    void parseFilenameAcceptsAndRejects() {
        assertEquals("0101A1", BatchTiffFile.parseFilename("0101A1.tif").id.imageId());
        assertEquals(".tiff", BatchTiffFile.parseFilename("0101A2.TIFF").extension);
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("0101A1-copy.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("0101A1.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("010101.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename(" 0101A1.tif"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("0101A1.tif "));
        assertThrows(IllegalArgumentException.class,
                () -> BatchTiffFile.parseFilename("0101a1.tif"));
    }

    private static void writeBytes(final Path path, final String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
