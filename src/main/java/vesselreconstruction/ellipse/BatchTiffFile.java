package vesselreconstruction.ellipse;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable discovered TIFF assigned to a six-digit sample ID.
 */
public final class BatchTiffFile {

    private final SixDigitSampleId id;
    private final Path absolutePath;
    private final Path relativePath;
    private final String sourceFilename;
    private final String extension;
    private final long sizeBytes;
    private final String sha256;

    public BatchTiffFile(
            final SixDigitSampleId id,
            final Path absolutePath,
            final Path relativePath,
            final String sourceFilename,
            final String extension,
            final long sizeBytes,
            final String sha256) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (absolutePath == null) {
            throw new IllegalArgumentException("absolutePath must not be null");
        }
        if (relativePath == null) {
            throw new IllegalArgumentException("relativePath must not be null");
        }
        if (sourceFilename == null || sourceFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceFilename must not be blank");
        }
        if (extension == null || extension.trim().isEmpty()) {
            throw new IllegalArgumentException("extension must not be blank");
        }
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        if (sha256 == null || sha256.trim().isEmpty()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
        this.id = id;
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
        this.sourceFilename = sourceFilename;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    /**
     * Parses a TIFF filename (name only) into a six-digit ID and extension.
     *
     * @return array of [SixDigitSampleId as string imageId, extension including leading dot]
     */
    public static ParsedFilename parseFilename(final String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        final String name = filename.trim();
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException(
                    "filename must not contain path separators: " + filename);
        }
        final int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException(
                    "TIFF filename must have a base name and extension: " + filename);
        }
        final String base = name.substring(0, dot);
        final String ext = name.substring(dot);
        final String extLower = ext.toLowerCase(Locale.ROOT);
        if (!".tif".equals(extLower) && !".tiff".equals(extLower)) {
            throw new IllegalArgumentException(
                    "unsupported extension (expected .tif/.tiff): " + filename);
        }
        final SixDigitSampleId id = SixDigitSampleId.parse(base);
        if (!base.equals(id.imageId())) {
            throw new IllegalArgumentException(
                    "TIFF base name must equal the six-digit ID exactly: " + filename);
        }
        return new ParsedFilename(id, extLower);
    }

    public SixDigitSampleId id() {
        return id;
    }

    public Path absolutePath() {
        return absolutePath;
    }

    public Path relativePath() {
        return relativePath;
    }

    public String sourceFilename() {
        return sourceFilename;
    }

    public String extension() {
        return extension;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchTiffFile)) {
            return false;
        }
        final BatchTiffFile other = (BatchTiffFile) obj;
        return id.equals(other.id)
                && absolutePath.equals(other.absolutePath)
                && relativePath.equals(other.relativePath)
                && sourceFilename.equals(other.sourceFilename)
                && extension.equals(other.extension)
                && sizeBytes == other.sizeBytes
                && sha256.equals(other.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, absolutePath, relativePath, sourceFilename, extension,
                Long.valueOf(sizeBytes), sha256);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "BatchTiffFile{%s,%s}", id, sourceFilename);
    }

    public static final class ParsedFilename {
        public final SixDigitSampleId id;
        public final String extension;

        public ParsedFilename(final SixDigitSampleId id, final String extension) {
            this.id = id;
            this.extension = extension;
        }
    }
}
