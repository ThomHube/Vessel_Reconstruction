package vesselreconstruction.ellipse;

/**
 * Safe filename sanitization and canonical production filename construction.
 *
 * <p>Human-entered {@code sample_id}/{@code segment_id} values are preserved in
 * metadata. Filenames use separately sanitized tokens.</p>
 */
public final class FilenameSanitizer {

    private FilenameSanitizer() {
    }

    /**
     * Sanitizes an identifier for use inside production filenames.
     *
     * <p>Allows letters, digits, underscore, and hyphen. Other character runs
     * become a single underscore. Leading/trailing underscores are removed.
     * Path separators and {@code ..} are rejected via empty/unnamed result.</p>
     */
    public static String sanitizeForFilename(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            // Still sanitize, but path-like inputs are neutralized.
        }
        final StringBuilder sb = new StringBuilder(trimmed.length());
        boolean lastWasUnderscore = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char ch = trimmed.charAt(i);
            final boolean allowed = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '_';
            if (allowed) {
                if (ch == '_' || ch == '-') {
                    if (ch == '_' && lastWasUnderscore) {
                        continue;
                    }
                    if (ch == '_' && sb.length() == 0) {
                        continue;
                    }
                }
                sb.append(ch);
                lastWasUnderscore = ch == '_';
            } else {
                if (!lastWasUnderscore && sb.length() > 0) {
                    sb.append('_');
                    lastWasUnderscore = true;
                }
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        String result = sb.toString();
        while (result.contains("__")) {
            result = result.replace("__", "_");
        }
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)
                || result.contains("/") || result.contains("\\") || result.contains("..")) {
            throw new IllegalArgumentException(
                    "Sanitized filename is empty or unsafe for value: " + value);
        }
        return result;
    }

    /** Best-effort sanitization that returns {@code unnamed} instead of throwing. */
    public static String sanitizeOrUnnamed(final String value) {
        try {
            return sanitizeForFilename(value == null || value.trim().isEmpty()
                    ? "unnamed" : value);
        } catch (final IllegalArgumentException ex) {
            return "unnamed";
        }
    }

    public static String stripExtension(final String filename) {
        if (filename == null || filename.isEmpty()) {
            return "untitled";
        }
        final int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        final String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        final int dot = base.lastIndexOf('.');
        if (dot <= 0) {
            return base;
        }
        return base.substring(0, dot);
    }

    public static String originalRgbFilename(final String safeSampleId, final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__original_rgb.tif";
    }

    public static String approvedMaskFilename(
            final String safeSampleId,
            final String safeSegmentId,
            final int downsampleFactor) {
        return pair(safeSampleId, safeSegmentId) + "__approved_mask_ds" + downsampleFactor + ".tif";
    }

    public static String maskedRgbFilename(final String safeSampleId, final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__masked_rgb.tif";
    }

    public static String maskedClassifiedFilename(
            final String safeSampleId,
            final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__masked_classified.tif";
    }

    public static String reconstructedClassifiedFilename(
            final String safeSampleId,
            final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__reconstructed_classified.tif";
    }

    public static String lumenMeasurementsFilename(
            final String safeSampleId,
            final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__lumen_measurements.csv";
    }

    public static String artifactDiagnosticsFilename(
            final String safeSampleId,
            final String safeSegmentId) {
        return pair(safeSampleId, safeSegmentId) + "__artifact_diagnostics.csv";
    }

    public static String lumenUid(
            final String safeSampleId,
            final String safeSegmentId,
            final int lumenIdSegment) {
        if (lumenIdSegment <= 0) {
            throw new IllegalArgumentException("lumenIdSegment must be positive");
        }
        return pair(safeSampleId, safeSegmentId)
                + "__L"
                + String.format(java.util.Locale.US, "%06d", Integer.valueOf(lumenIdSegment));
    }

    public static String runId(
            final String safeSampleId,
            final String safeSegmentId,
            final String stage,
            final String timestamp) {
        return pair(safeSampleId, safeSegmentId)
                + "__"
                + sanitizeOrUnnamed(stage)
                + "__"
                + sanitizeOrUnnamed(timestamp);
    }

    private static String pair(final String safeSampleId, final String safeSegmentId) {
        if (safeSampleId == null || safeSampleId.isEmpty()
                || safeSegmentId == null || safeSegmentId.isEmpty()) {
            throw new IllegalArgumentException("safe sample/segment IDs must not be blank");
        }
        if (safeSampleId.contains("/") || safeSampleId.contains("\\")
                || safeSegmentId.contains("/") || safeSegmentId.contains("\\")
                || safeSampleId.contains("..") || safeSegmentId.contains("..")) {
            throw new IllegalArgumentException("safe IDs must not contain path elements");
        }
        return safeSampleId + "__" + safeSegmentId;
    }
}
