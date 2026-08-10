package vesselreconstruction.ellipse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Authoritative production dataset folder layout shared by tiled classification
 * and All Lumina.
 */
public final class ProductionDatasetLayout {

    public static final String DIR_IMAGES = "images";
    public static final String DIR_ORIGINAL_RGB = "original_rgb";
    public static final String DIR_APPROVED_MASKS = "approved_masks";
    public static final String DIR_MASKED_RGB = "masked_rgb";
    public static final String DIR_MASKED_CLASSIFIED = "masked_classified";
    public static final String DIR_RECONSTRUCTED_CLASSIFIED = "reconstructed_classified";
    public static final String DIR_DATA = "data";
    public static final String DIR_LUMEN_MEASUREMENTS = "lumen_measurements";
    public static final String DIR_PER_SEGMENT = "per_segment";
    public static final String DIR_MANIFESTS = "manifests";
    public static final String DIR_DIAGNOSTICS = "diagnostics";
    public static final String DIR_LOGS = "logs";

    public static final String FILE_LUMEN_MEASUREMENTS_ALL = "lumen_measurements_all.csv";
    public static final String FILE_ARTIFACT_DIAGNOSTICS_ALL = "artifact_diagnostics_all.csv";
    public static final String FILE_IMAGE_MANIFEST = "image_manifest.csv";
    public static final String FILE_SEGMENT_MANIFEST = "segment_manifest.csv";
    public static final String FILE_PROCESSING_RUNS = "processing_runs.csv";
    public static final String FILE_DATA_DICTIONARY = "data_dictionary.csv";

    public static final List<String> REQUIRED_RELATIVE_DIRS = Collections.unmodifiableList(
            Arrays.asList(
                    DIR_IMAGES + "/" + DIR_ORIGINAL_RGB,
                    DIR_IMAGES + "/" + DIR_APPROVED_MASKS,
                    DIR_IMAGES + "/" + DIR_MASKED_RGB,
                    DIR_IMAGES + "/" + DIR_MASKED_CLASSIFIED,
                    DIR_IMAGES + "/" + DIR_RECONSTRUCTED_CLASSIFIED,
                    DIR_DATA + "/" + DIR_LUMEN_MEASUREMENTS + "/" + DIR_PER_SEGMENT,
                    DIR_DATA + "/" + DIR_MANIFESTS,
                    DIR_DATA + "/" + DIR_DIAGNOSTICS + "/" + DIR_PER_SEGMENT,
                    DIR_LOGS));

    private final Path datasetRoot;

    public ProductionDatasetLayout(final File datasetRoot) {
        this(datasetRoot == null ? null : datasetRoot.toPath());
    }

    public ProductionDatasetLayout(final Path datasetRoot) {
        if (datasetRoot == null) {
            throw new IllegalArgumentException("datasetRoot must not be null");
        }
        final Path normalized = datasetRoot.toAbsolutePath().normalize();
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(
                    "Dataset root must be a directory, not a file: " + normalized);
        }
        this.datasetRoot = normalized;
    }

    public static ProductionDatasetLayout fromPathString(final String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("dataset root path must not be blank");
        }
        return new ProductionDatasetLayout(Paths.get(path.trim()));
    }

    public Path getDatasetRoot() {
        return datasetRoot;
    }

    public File getDatasetRootFile() {
        return datasetRoot.toFile();
    }

    public Path imagesOriginalRgb() {
        return datasetRoot.resolve(DIR_IMAGES).resolve(DIR_ORIGINAL_RGB);
    }

    public Path imagesApprovedMasks() {
        return datasetRoot.resolve(DIR_IMAGES).resolve(DIR_APPROVED_MASKS);
    }

    public Path imagesMaskedRgb() {
        return datasetRoot.resolve(DIR_IMAGES).resolve(DIR_MASKED_RGB);
    }

    public Path imagesMaskedClassified() {
        return datasetRoot.resolve(DIR_IMAGES).resolve(DIR_MASKED_CLASSIFIED);
    }

    public Path imagesReconstructedClassified() {
        return datasetRoot.resolve(DIR_IMAGES).resolve(DIR_RECONSTRUCTED_CLASSIFIED);
    }

    public Path dataLumenMeasurementsPerSegment() {
        return datasetRoot.resolve(DIR_DATA).resolve(DIR_LUMEN_MEASUREMENTS).resolve(DIR_PER_SEGMENT);
    }

    public Path dataLumenMeasurementsAll() {
        return datasetRoot.resolve(DIR_DATA).resolve(DIR_LUMEN_MEASUREMENTS)
                .resolve(FILE_LUMEN_MEASUREMENTS_ALL);
    }

    public Path dataDiagnosticsPerSegment() {
        return datasetRoot.resolve(DIR_DATA).resolve(DIR_DIAGNOSTICS).resolve(DIR_PER_SEGMENT);
    }

    public Path dataDiagnosticsAll() {
        return datasetRoot.resolve(DIR_DATA).resolve(DIR_DIAGNOSTICS)
                .resolve(FILE_ARTIFACT_DIAGNOSTICS_ALL);
    }

    public Path dataManifests() {
        return datasetRoot.resolve(DIR_DATA).resolve(DIR_MANIFESTS);
    }

    public Path imageManifest() {
        return dataManifests().resolve(FILE_IMAGE_MANIFEST);
    }

    public Path segmentManifest() {
        return dataManifests().resolve(FILE_SEGMENT_MANIFEST);
    }

    public Path processingRuns() {
        return dataManifests().resolve(FILE_PROCESSING_RUNS);
    }

    public Path dataDictionary() {
        return datasetRoot.resolve(DIR_DATA).resolve(FILE_DATA_DICTIONARY);
    }

    public Path logs() {
        return datasetRoot.resolve(DIR_LOGS);
    }

    public Path resolveUnderRoot(final String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        final Path resolved = datasetRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(datasetRoot)) {
            throw new IllegalArgumentException(
                    "Path escapes dataset root: " + relativePath);
        }
        return resolved;
    }

    public void ensureDirectoriesExist() throws IOException {
        for (final String relative : REQUIRED_RELATIVE_DIRS) {
            final Path dir = resolveUnderRoot(relative);
            if (Files.exists(dir) && !Files.isDirectory(dir)) {
                throw new IOException(
                        "Expected directory but found a file: " + dir);
            }
            Files.createDirectories(dir);
        }
    }

    public boolean isAccessibleDirectory() {
        return Files.isDirectory(datasetRoot);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProductionDatasetLayout)) {
            return false;
        }
        return datasetRoot.equals(((ProductionDatasetLayout) obj).datasetRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetRoot);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "ProductionDatasetLayout{%s}", datasetRoot);
    }
}
