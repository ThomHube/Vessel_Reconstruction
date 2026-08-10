package vesselreconstruction.ellipse;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Deterministic non-overwrite path allocation for temporary/non-canonical files.
 */
public final class OutputPathAllocator {

    private OutputPathAllocator() {
    }

    public static File allocateUnique(final File directory, final String filename) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        final File primary = new File(directory, filename);
        if (!primary.exists()) {
            return primary;
        }
        final String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());
        final int dot = filename.lastIndexOf('.');
        final String stem = dot > 0 ? filename.substring(0, dot) : filename;
        final String ext = dot > 0 ? filename.substring(dot) : "";
        File candidate = new File(directory, stem + "__" + stamp + ext);
        int suffix = 1;
        while (candidate.exists()) {
            candidate = new File(directory, stem + "__" + stamp + "_" + suffix + ext);
            suffix++;
        }
        return candidate;
    }

    public static String createRunId(
            final String sourceBase,
            final String segmentId,
            final String timestamp) {
        return FilenameSanitizer.runId(
                FilenameSanitizer.sanitizeOrUnnamed(sourceBase),
                FilenameSanitizer.sanitizeOrUnnamed(segmentId),
                "run",
                timestamp);
    }
}
