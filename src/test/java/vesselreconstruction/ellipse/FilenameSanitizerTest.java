package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilenameSanitizerTest {

    @Test
    void sanitizesSpacesUnicodeAndPunctuationRuns() {
        assertEquals("sample_id", FilenameSanitizer.sanitizeForFilename("sample id"));
        assertEquals("caf", FilenameSanitizer.sanitizeForFilename("café"));
        assertEquals("sample_id", FilenameSanitizer.sanitizeForFilename("sample!!!,,, id"));
    }

    @Test
    void sanitizesPathSeparatorsWithoutPathEscape() {
        final String slash = FilenameSanitizer.sanitizeForFilename("sample/segment");
        final String backslash = FilenameSanitizer.sanitizeForFilename("sample\\segment");

        assertEquals("sample_segment", slash);
        assertEquals("sample_segment", backslash);
        assertFalse(slash.contains("/"));
        assertFalse(backslash.contains("\\"));
    }

    @Test
    void rejectsUnsafeOrBlankNamesAndProvidesUnnamedFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> FilenameSanitizer.sanitizeForFilename(".."));
        assertThrows(IllegalArgumentException.class,
                () -> FilenameSanitizer.sanitizeForFilename("   "));
        assertEquals("unnamed", FilenameSanitizer.sanitizeOrUnnamed(" "));
    }

    @Test
    void buildsCanonicalFilenamesAndZeroPaddedLumenUid() {
        final String sample = "0105A1";
        final String segment = "segment_02";

        assertEquals("0105A1__segment_02__original_rgb.tif",
                FilenameSanitizer.originalRgbFilename(sample, segment));
        assertEquals("0105A1__segment_02__approved_mask_ds8.tif",
                FilenameSanitizer.approvedMaskFilename(sample, segment, 8));
        assertEquals("0105A1__segment_02__masked_rgb.tif",
                FilenameSanitizer.maskedRgbFilename(sample, segment));
        assertEquals("0105A1__segment_02__masked_classified.tif",
                FilenameSanitizer.maskedClassifiedFilename(sample, segment));
        assertEquals("0105A1__segment_02__reconstructed_classified.tif",
                FilenameSanitizer.reconstructedClassifiedFilename(sample, segment));
        assertEquals("0105A1__segment_02__lumen_measurements.csv",
                FilenameSanitizer.lumenMeasurementsFilename(sample, segment));
        assertEquals("0105A1__segment_02__artifact_diagnostics.csv",
                FilenameSanitizer.artifactDiagnosticsFilename(sample, segment));
        assertEquals("0105A1__segment_02__L000007",
                FilenameSanitizer.lumenUid(sample, segment, 7));
    }
}
