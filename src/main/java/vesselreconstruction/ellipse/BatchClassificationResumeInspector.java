package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Inspects an existing production dataset to decide whether a Phase-3
 * classification stage can be skipped and reused for a later reconstruction
 * attempt, instead of re-running tiled Weka classification.
 *
 * <p>Read-only; never mutates the dataset. A dataset is resumable for an
 * image only when every one of the following holds:</p>
 *
 * <ul>
 * <li>the masked-classified TIFF exists on disk;</li>
 * <li>{@code image_manifest} has a row for the image with a complete,
 * non-{@code NA} {@code masked_classified_sha256} that matches the file's
 * actual SHA-256;</li>
 * <li>{@code image_manifest}'s {@code source_rgb_sha256} matches the job's
 * source SHA-256;</li>
 * <li>{@code image_manifest}'s {@code classifier_sha256} matches the job's
 * classifier SHA-256, when the manifest records one;</li>
 * <li>{@code processing_runs} has a {@code TILED_CLASSIFICATION}
 * {@code SUCCESS} row for the image.</li>
 * </ul>
 *
 * <p>{@code image_manifest} and {@code processing_runs} do not currently
 * record a configuration SHA-256, so configuration compatibility cannot be
 * strictly re-verified here; this is treated as compatible by design
 * (documented limitation).</p>
 */
public final class BatchClassificationResumeInspector {

    private BatchClassificationResumeInspector() {
    }

    public static final class Inspection {
        public final boolean resumable;
        public final String reasonCode;
        public final String classificationRunId;

        private Inspection(
                final boolean resumable, final String reasonCode, final String classificationRunId) {
            this.resumable = resumable;
            this.reasonCode = reasonCode;
            this.classificationRunId = classificationRunId;
        }

        private static Inspection notResumable(final String reasonCode) {
            return new Inspection(false, reasonCode, null);
        }

        private static Inspection resumable(final String classificationRunId) {
            return new Inspection(true, RCsvSupport.NA, classificationRunId);
        }
    }

    public static Inspection inspect(
            final Path datasetRoot,
            final String imageId,
            final String sourceSha256,
            final String classifierSha256) {
        if (datasetRoot == null || imageId == null || imageId.trim().isEmpty()) {
            return Inspection.notResumable("INVALID_ARGUMENTS");
        }
        final ProductionDatasetLayout layout;
        try {
            layout = new ProductionDatasetLayout(datasetRoot);
        } catch (final RuntimeException ex) {
            return Inspection.notResumable("INVALID_DATASET_ROOT");
        }
        if (!layout.isAccessibleDirectory()) {
            return Inspection.notResumable("DATASET_ROOT_MISSING");
        }
        final String safe;
        try {
            safe = FilenameSanitizer.sanitizeForFilename(imageId.trim());
        } catch (final RuntimeException ex) {
            return Inspection.notResumable("IMAGE_ID_UNSAFE");
        }

        final Path maskedClassifiedPath = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(safe, safe));
        if (!Files.isRegularFile(maskedClassifiedPath)) {
            return Inspection.notResumable("MASKED_CLASSIFIED_MISSING");
        }

        final Map<String, String> row;
        try {
            row = readImageManifestRow(layout, imageId.trim());
        } catch (final IOException ex) {
            return Inspection.notResumable("IMAGE_MANIFEST_UNREADABLE");
        }
        if (row == null) {
            return Inspection.notResumable("IMAGE_MANIFEST_ROW_MISSING");
        }

        final String manifestSourceSha = row.get("source_rgb_sha256");
        if (sourceSha256 != null && !sourceSha256.trim().isEmpty()
                && (manifestSourceSha == null
                        || !manifestSourceSha.trim().equalsIgnoreCase(sourceSha256.trim()))) {
            return Inspection.notResumable("SOURCE_SHA_MISMATCH");
        }

        final String manifestMaskedClassifiedSha = row.get("masked_classified_sha256");
        if (manifestMaskedClassifiedSha == null
                || RCsvSupport.NA.equals(manifestMaskedClassifiedSha.trim())) {
            return Inspection.notResumable("MASKED_CLASSIFIED_SHA_MISSING");
        }
        final String actualSha;
        try {
            actualSha = Sha256Files.sha256OfFile(maskedClassifiedPath);
        } catch (final IOException ex) {
            return Inspection.notResumable("MASKED_CLASSIFIED_UNREADABLE");
        }
        if (!actualSha.equalsIgnoreCase(manifestMaskedClassifiedSha.trim())) {
            return Inspection.notResumable("MASKED_CLASSIFIED_SHA_MISMATCH");
        }

        final String manifestClassifierSha = row.get("classifier_sha256");
        if (classifierSha256 != null && !classifierSha256.trim().isEmpty()
                && manifestClassifierSha != null
                && !RCsvSupport.NA.equals(manifestClassifierSha.trim())
                && !manifestClassifierSha.trim().equalsIgnoreCase(classifierSha256.trim())) {
            return Inspection.notResumable("CLASSIFIER_SHA_MISMATCH");
        }

        final String classificationRunId = row.get("classification_run_id");
        if (classificationRunId == null || RCsvSupport.NA.equals(classificationRunId.trim())) {
            return Inspection.notResumable("CLASSIFICATION_RUN_ID_MISSING");
        }

        if (!processingRunsHasClassificationSuccess(layout, imageId.trim())) {
            return Inspection.notResumable("PROCESSING_RUN_SUCCESS_MISSING");
        }

        return Inspection.resumable(classificationRunId.trim());
    }

    private static Map<String, String> readImageManifestRow(
            final ProductionDatasetLayout layout, final String imageId) throws IOException {
        if (!Files.isRegularFile(layout.imageManifest())) {
            return null;
        }
        final List<List<String>> table = ProductionCsvStore.readTable(layout.imageManifest());
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.IMAGE_MANIFEST_HEADER)) {
            throw new IOException("image_manifest header mismatch: " + layout.imageManifest());
        }
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.IMAGE_MANIFEST_HEADER);
        final int sampleIdx = idx.get("sample_id").intValue();
        final int segmentIdx = idx.get("segment_id").intValue();
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (imageId.equals(row.get(sampleIdx)) && imageId.equals(row.get(segmentIdx))) {
                final Map<String, String> map = new java.util.HashMap<String, String>();
                for (final Map.Entry<String, Integer> entry : idx.entrySet()) {
                    map.put(entry.getKey(), row.get(entry.getValue().intValue()));
                }
                return map;
            }
        }
        return null;
    }

    private static boolean processingRunsHasClassificationSuccess(
            final ProductionDatasetLayout layout, final String imageId) {
        if (!Files.isRegularFile(layout.processingRuns())) {
            return false;
        }
        try {
            final List<List<String>> table = ProductionCsvStore.readTable(layout.processingRuns());
            if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.PROCESSING_RUNS_HEADER)) {
                return false;
            }
            final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                    ProductionSchemas.PROCESSING_RUNS_HEADER);
            final int sampleIdx = idx.get("sample_id").intValue();
            final int segmentIdx = idx.get("segment_id").intValue();
            final int stageIdx = idx.get("stage").intValue();
            final int statusIdx = idx.get("status").intValue();
            for (int r = 1; r < table.size(); r++) {
                final List<String> row = table.get(r);
                if (!imageId.equals(row.get(sampleIdx)) || !imageId.equals(row.get(segmentIdx))) {
                    continue;
                }
                if (ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name().equals(row.get(stageIdx))
                        && ProductionSchemas.RunStatus.SUCCESS.name().equals(row.get(statusIdx))) {
                    return true;
                }
            }
            return false;
        } catch (final IOException ex) {
            return false;
        }
    }
}
