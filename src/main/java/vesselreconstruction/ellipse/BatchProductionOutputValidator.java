package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Post-hoc validation of Phase-3 production outputs for a single image.
 *
 * <p>Identity model: {@code sample_id == core_id}, {@code segment_id == image_id},
 * with {@code segment_order} from {@link CoreSegmentId}.</p>
 */
public final class BatchProductionOutputValidator {

    /**
     * Outcome of inspecting the canonical production dataset before launching
     * any Fiji child.
     */
    public enum FinalizedDatasetState {
        CLASSIFICATION_INCOMPLETE,
        CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE,
        FINAL_DATASET_COMPLETE,
        CANONICAL_CONFLICT
    }

    /**
     * Immutable inspection of whether classification and/or reconstruction
     * can be reused from the canonical dataset.
     */
    public static final class FinalizedDatasetInspection {
        public final FinalizedDatasetState state;
        public final String reasonCode;
        public final List<String> failures;
        public final String classificationRunId;
        public final String reconstructionRunId;
        public final Integer totalComponents;
        public final Integer eligibleComponents;
        public final Integer reconstructedComponents;
        public final Integer changedPixels;
        public final String originalRgbPath;
        public final String approvedMaskPath;
        public final String maskedRgbPath;
        public final String maskedClassifiedPath;
        public final String reconstructedClassifiedPath;
        public final String lumenMeasurementsPath;
        public final String artifactDiagnosticsPath;
        public final String imageManifestPath;
        public final String segmentManifestPath;
        public final String processingRunsPath;

        private FinalizedDatasetInspection(
                final FinalizedDatasetState state,
                final String reasonCode,
                final List<String> failures,
                final String classificationRunId,
                final String reconstructionRunId,
                final Integer totalComponents,
                final Integer eligibleComponents,
                final Integer reconstructedComponents,
                final Integer changedPixels,
                final String originalRgbPath,
                final String approvedMaskPath,
                final String maskedRgbPath,
                final String maskedClassifiedPath,
                final String reconstructedClassifiedPath,
                final String lumenMeasurementsPath,
                final String artifactDiagnosticsPath,
                final String imageManifestPath,
                final String segmentManifestPath,
                final String processingRunsPath) {
            this.state = state;
            this.reasonCode = reasonCode;
            this.failures = Collections.unmodifiableList(new ArrayList<String>(failures));
            this.classificationRunId = classificationRunId;
            this.reconstructionRunId = reconstructionRunId;
            this.totalComponents = totalComponents;
            this.eligibleComponents = eligibleComponents;
            this.reconstructedComponents = reconstructedComponents;
            this.changedPixels = changedPixels;
            this.originalRgbPath = originalRgbPath;
            this.approvedMaskPath = approvedMaskPath;
            this.maskedRgbPath = maskedRgbPath;
            this.maskedClassifiedPath = maskedClassifiedPath;
            this.reconstructedClassifiedPath = reconstructedClassifiedPath;
            this.lumenMeasurementsPath = lumenMeasurementsPath;
            this.artifactDiagnosticsPath = artifactDiagnosticsPath;
            this.imageManifestPath = imageManifestPath;
            this.segmentManifestPath = segmentManifestPath;
            this.processingRunsPath = processingRunsPath;
        }

        public boolean isFinalDatasetComplete() {
            return state == FinalizedDatasetState.FINAL_DATASET_COMPLETE;
        }

        public boolean isCanonicalConflict() {
            return state == FinalizedDatasetState.CANONICAL_CONFLICT;
        }
    }

    public static final class ValidationResult {
        public final boolean ok;
        public final List<String> failures;

        private ValidationResult(final boolean ok, final List<String> failures) {
            this.ok = ok;
            this.failures = failures;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, Collections.<String>emptyList());
        }

        public static ValidationResult of(final List<String> failures) {
            return new ValidationResult(failures.isEmpty(), failures);
        }
    }

    private BatchProductionOutputValidator() {
    }

    public static ValidationResult validate(
            final Path datasetRoot,
            final String imageId,
            final String sourceSha256) {
        return validate(datasetRoot, imageId, sourceSha256, true);
    }

    /**
     * Validates only the classification-stage production outputs: original
     * RGB, approved mask, masked RGB, masked classified, manifests, and a
     * {@code TILED_CLASSIFICATION} {@code SUCCESS} processing-run row.
     * {@code reconstructed_classified}, lumen measurements, and artifact
     * diagnostics are not required yet.
     */
    public static ValidationResult validateClassificationOnly(
            final Path datasetRoot,
            final String imageId,
            final String sourceSha256) {
        return validate(datasetRoot, imageId, sourceSha256, false);
    }

    /**
     * Inspects the canonical production dataset to decide whether Phase-3
     * can skip classification, reconstruction, both, or must abort for a
     * canonical conflict. Read-only; never mutates the dataset.
     *
     * <p>Completion is determined from production files and manifests, not
     * from prior attempt-level {@code result.properties}.</p>
     */
    public static FinalizedDatasetInspection inspectFinalizedDataset(
            final Path datasetRoot,
            final String imageId,
            final String sourceSha256,
            final String classifierSha256,
            final int minimumLumenAreaPx,
            final boolean requireAggregateTables) {
        final List<String> failures = new ArrayList<String>();
        if (datasetRoot == null) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "INVALID_ARGUMENTS", Collections.singletonList("datasetRoot must not be null"));
        }
        if (imageId == null || imageId.trim().isEmpty()) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "INVALID_ARGUMENTS", Collections.singletonList("imageId must not be blank"));
        }
        if (minimumLumenAreaPx <= 0) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "INVALID_ARGUMENTS",
                    Collections.singletonList("minimumLumenAreaPx must be positive"));
        }

        final ProductionDatasetLayout layout;
        try {
            layout = new ProductionDatasetLayout(datasetRoot);
        } catch (final RuntimeException ex) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "INVALID_DATASET_ROOT",
                    Collections.singletonList("invalid dataset root: " + ex.getMessage()));
        }
        if (!layout.isAccessibleDirectory()) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "DATASET_ROOT_MISSING",
                    Collections.singletonList(
                            "dataset root is not an accessible directory: " + datasetRoot));
        }

        final String trimmedId = imageId.trim();
        final String safeSample;
        final String safeSegment;
        try {
            safeSample = sanitizeSample(trimmedId);
            safeSegment = sanitizeSegment(trimmedId);
        } catch (final RuntimeException ex) {
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    "IMAGE_ID_UNSAFE",
                    Collections.singletonList(
                            "imageId cannot be sanitized for filenames: " + ex.getMessage()));
        }

        final Path originalRgb = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(safeSample, safeSegment));
        if (Files.isRegularFile(originalRgb) && sourceSha256 != null && !sourceSha256.trim().isEmpty()) {
            try {
                final String actual = Sha256Files.sha256OfFile(originalRgb);
                if (!actual.equalsIgnoreCase(sourceSha256.trim())) {
                    return incomplete(FinalizedDatasetState.CANONICAL_CONFLICT,
                            "CANONICAL_CONFLICT",
                            Collections.singletonList(
                                    "original_rgb SHA-256 mismatch: expected "
                                            + sourceSha256 + ", found " + actual));
                }
            } catch (final IOException ex) {
                failures.add("failed hashing original_rgb: " + ex.getMessage());
            }
        }

        final List<String> classificationFailures = new ArrayList<String>();
        checkImages(layout, safeSample, safeSegment, sourceSha256, false, classificationFailures);
        checkImageManifest(layout, trimmedId, false, classificationFailures);
        checkSegmentManifest(layout, trimmedId, classificationFailures);
        checkProcessingRuns(layout, trimmedId, false, classificationFailures);
        checkClassificationManifestDetails(
                layout, trimmedId, safeSample, safeSegment, classifierSha256, classificationFailures);
        checkPathsUnderRoot(layout, safeSample, safeSegment, false, classificationFailures);

        if (!classificationFailures.isEmpty()) {
            failures.addAll(classificationFailures);
            return incomplete(FinalizedDatasetState.CLASSIFICATION_INCOMPLETE,
                    primaryReason(classificationFailures), failures);
        }

        final List<String> reconstructionFailures = new ArrayList<String>();
        checkImages(layout, safeSample, safeSegment, sourceSha256, true, reconstructionFailures);
        // Drop original/mask/masked checks already known-good; keep only recon-specific
        // by re-running the reconstruction-focused helpers instead of full checkImages.
        reconstructionFailures.clear();
        final Path reconstructed = layout.imagesReconstructedClassified().resolve(
                FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment));
        checkFileExists(reconstructed, "reconstructed_classified", reconstructionFailures);

        checkImageManifest(layout, trimmedId, true, reconstructionFailures);
        checkProcessingRuns(layout, trimmedId, true, reconstructionFailures);
        checkLumenMeasurements(layout, safeSample, safeSegment, reconstructionFailures);
        checkDiagnostics(layout, safeSample, safeSegment, reconstructionFailures);
        if (requireAggregateTables) {
            checkAggregateTables(layout, reconstructionFailures);
        }
        final ReconstructionRowDetails reconDetails = checkReconstructionRunDetails(
                layout, trimmedId, safeSample, safeSegment, minimumLumenAreaPx, reconstructionFailures);
        checkPathsUnderRoot(layout, safeSample, safeSegment, true, reconstructionFailures);

        if (!reconstructionFailures.isEmpty()) {
            return incomplete(
                    FinalizedDatasetState.CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE,
                    primaryReason(reconstructionFailures), reconstructionFailures);
        }

        final Map<String, String> imageRow = readImageManifestRowMap(layout, trimmedId);
        final String classificationRunId = imageRow == null ? null : imageRow.get("classification_run_id");
        final String reconstructionRunId = imageRow == null ? null : imageRow.get("reconstruction_run_id");
        final Path approvedMask = findApprovedMask(layout, safeSample, safeSegment);

        return new FinalizedDatasetInspection(
                FinalizedDatasetState.FINAL_DATASET_COMPLETE,
                RCsvSupport.NA,
                Collections.<String>emptyList(),
                classificationRunId,
                reconstructionRunId != null ? reconstructionRunId
                        : (reconDetails == null ? null : reconDetails.runId),
                reconDetails == null ? null : reconDetails.totalComponents,
                reconDetails == null ? null : reconDetails.eligibleComponents,
                reconDetails == null ? null : reconDetails.reconstructedComponents,
                reconDetails == null ? null : reconDetails.changedPixels,
                relativize(layout.getDatasetRoot(), originalRgb),
                approvedMask == null ? null : relativize(layout.getDatasetRoot(), approvedMask),
                relativize(layout.getDatasetRoot(), layout.imagesMaskedRgb().resolve(
                        FilenameSanitizer.maskedRgbFilename(safeSample, safeSegment))),
                relativize(layout.getDatasetRoot(), layout.imagesMaskedClassified().resolve(
                        FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment))),
                relativize(layout.getDatasetRoot(), reconstructed),
                relativize(layout.getDatasetRoot(), layout.dataLumenMeasurementsPerSegment().resolve(
                        FilenameSanitizer.lumenMeasurementsFilename(safeSample, safeSegment))),
                relativize(layout.getDatasetRoot(), layout.dataDiagnosticsPerSegment().resolve(
                        FilenameSanitizer.artifactDiagnosticsFilename(safeSample, safeSegment))),
                relativize(layout.getDatasetRoot(), layout.imageManifest()),
                relativize(layout.getDatasetRoot(), layout.segmentManifest()),
                relativize(layout.getDatasetRoot(), layout.processingRuns()));
    }

    private static FinalizedDatasetInspection incomplete(
            final FinalizedDatasetState state,
            final String reasonCode,
            final List<String> failures) {
        return new FinalizedDatasetInspection(
                state, reasonCode, failures,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static String primaryReason(final List<String> failures) {
        if (failures == null || failures.isEmpty()) {
            return "VALIDATION_FAILED";
        }
        final String first = failures.get(0);
        if (first.contains("SHA-256 mismatch")) {
            return "CANONICAL_CONFLICT";
        }
        if (first.contains("minimum_lumen_area_px")) {
            return "MINIMUM_LUMEN_AREA_MISMATCH";
        }
        if (first.contains("duplicate lumen_uid")) {
            return "DUPLICATE_LUMEN_UID";
        }
        if (first.contains("non-finite")) {
            return "NAN_IN_SCIENTIFIC_CSV";
        }
        if (first.contains("reconstructed_classified file missing")) {
            return "RECONSTRUCTED_CLASSIFIED_MISSING";
        }
        if (first.contains("lumen_measurements CSV missing")) {
            return "LUMEN_MEASUREMENTS_MISSING";
        }
        if (first.contains("artifact_diagnostics CSV missing")) {
            return "ARTIFACT_DIAGNOSTICS_MISSING";
        }
        if (first.contains("masked_classified file missing")) {
            return "MASKED_CLASSIFIED_MISSING";
        }
        if (first.contains("output_filename")) {
            return "RECONSTRUCTION_OUTPUT_FILENAME_MISMATCH";
        }
        return "VALIDATION_FAILED";
    }

    private static ValidationResult validate(
            final Path datasetRoot,
            final String imageId,
            final String sourceSha256,
            final boolean requireReconstruction) {
        final List<String> failures = new ArrayList<String>();
        if (datasetRoot == null) {
            failures.add("datasetRoot must not be null");
            return ValidationResult.of(failures);
        }
        if (imageId == null || imageId.trim().isEmpty()) {
            failures.add("imageId must not be blank");
            return ValidationResult.of(failures);
        }
        final ProductionDatasetLayout layout;
        try {
            layout = new ProductionDatasetLayout(datasetRoot);
        } catch (final RuntimeException ex) {
            failures.add("invalid dataset root: " + ex.getMessage());
            return ValidationResult.of(failures);
        }
        if (!layout.isAccessibleDirectory()) {
            failures.add("dataset root is not an accessible directory: " + datasetRoot);
            return ValidationResult.of(failures);
        }
        final String safeSample;
        final String safeSegment;
        try {
            safeSample = sanitizeSample(imageId.trim());
            safeSegment = sanitizeSegment(imageId.trim());
        } catch (final RuntimeException ex) {
            failures.add("imageId cannot be sanitized for filenames: " + ex.getMessage());
            return ValidationResult.of(failures);
        }

        checkImages(layout, safeSample, safeSegment, sourceSha256, requireReconstruction, failures);
        checkImageManifest(layout, imageId.trim(), requireReconstruction, failures);
        checkSegmentManifest(layout, imageId.trim(), failures);
        checkProcessingRuns(layout, imageId.trim(), requireReconstruction, failures);
        if (requireReconstruction) {
            checkLumenMeasurements(layout, safeSample, safeSegment, failures);
            checkDiagnostics(layout, safeSample, safeSegment, failures);
        }

        return ValidationResult.of(failures);
    }

    private static void checkImages(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment,
            final String sourceSha256,
            final boolean requireReconstruction,
            final List<String> failures) {
        final Path originalRgb = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(safeSample, safeSegment));
        if (!checkFileExists(originalRgb, "original_rgb", failures)) {
            // nothing further to compare
        } else if (sourceSha256 != null && !sourceSha256.trim().isEmpty()) {
            try {
                final String actual = Sha256Files.sha256OfFile(originalRgb);
                if (!actual.equalsIgnoreCase(sourceSha256.trim())) {
                    failures.add("original_rgb SHA-256 mismatch: expected "
                            + sourceSha256 + ", found " + actual);
                }
            } catch (final IOException ex) {
                failures.add("failed hashing original_rgb: " + ex.getMessage());
            }
        }

        if (!approvedMaskExists(layout, safeSample, safeSegment)) {
            failures.add("approved mask missing under " + layout.imagesApprovedMasks());
        }

        final Path maskedRgb = layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename(safeSample, safeSegment));
        checkFileExists(maskedRgb, "masked_rgb", failures);

        final Path maskedClassified = layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment));
        checkFileExists(maskedClassified, "masked_classified", failures);

        if (requireReconstruction) {
            final Path reconstructed = layout.imagesReconstructedClassified().resolve(
                    FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment));
            checkFileExists(reconstructed, "reconstructed_classified", failures);
        }
    }

    private static void checkClassificationManifestDetails(
            final ProductionDatasetLayout layout,
            final String imageId,
            final String safeSample,
            final String safeSegment,
            final String classifierSha256,
            final List<String> failures) {
        final Map<String, String> row = readImageManifestRowMap(layout, imageId);
        if (row == null) {
            return;
        }
        checkNonNegativeInt(row, "crop_x_px", "image_manifest", failures);
        checkNonNegativeInt(row, "crop_y_px", "image_manifest", failures);
        checkPositiveInt(row, "crop_width_px", "image_manifest", failures);
        checkPositiveInt(row, "crop_height_px", "image_manifest", failures);

        final String maskedClassifiedFilename = row.get("masked_classified_filename");
        final String expectedMaskedClassified =
                FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment);
        if (maskedClassifiedFilename == null
                || RCsvSupport.NA.equals(maskedClassifiedFilename.trim())
                || !expectedMaskedClassified.equals(maskedClassifiedFilename.trim())) {
            failures.add("image_manifest masked_classified_filename mismatch: expected "
                    + expectedMaskedClassified + ", found " + maskedClassifiedFilename);
        }

        final String maskedClassifiedSha = row.get("masked_classified_sha256");
        if (maskedClassifiedSha == null || RCsvSupport.NA.equals(maskedClassifiedSha.trim())) {
            failures.add("image_manifest masked_classified_sha256 is missing");
        } else {
            final Path maskedClassified = layout.imagesMaskedClassified().resolve(expectedMaskedClassified);
            if (Files.isRegularFile(maskedClassified)) {
                try {
                    final String actual = Sha256Files.sha256OfFile(maskedClassified);
                    if (!actual.equalsIgnoreCase(maskedClassifiedSha.trim())) {
                        failures.add("masked_classified SHA-256 mismatch: expected "
                                + maskedClassifiedSha + ", found " + actual);
                    }
                } catch (final IOException ex) {
                    failures.add("failed hashing masked_classified: " + ex.getMessage());
                }
            }
        }

        final String manifestClassifierSha = row.get("classifier_sha256");
        if (classifierSha256 != null && !classifierSha256.trim().isEmpty()
                && manifestClassifierSha != null
                && !RCsvSupport.NA.equals(manifestClassifierSha.trim())
                && !manifestClassifierSha.trim().equalsIgnoreCase(classifierSha256.trim())) {
            failures.add("classifier SHA-256 mismatch: expected "
                    + classifierSha256 + ", found " + manifestClassifierSha);
        }

        // Classification output filename on the SUCCESS processing-run row.
        final List<String> classRun = findSuccessfulProcessingRun(
                layout, imageId, ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION);
        if (classRun != null) {
            final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                    ProductionSchemas.PROCESSING_RUNS_HEADER);
            final String outputFilename = classRun.get(idx.get("output_filename").intValue());
            if (outputFilename == null || !expectedMaskedClassified.equals(outputFilename.trim())) {
                failures.add("classification output_filename mismatch: expected "
                        + expectedMaskedClassified + ", found " + outputFilename);
            }
            final String sampleId = classRun.get(idx.get("sample_id").intValue());
            final String segmentId = classRun.get(idx.get("segment_id").intValue());
            final CoreSegmentId expected = CoreSegmentId.parse(imageId);
            if (!expected.sampleId().equals(sampleId) || !expected.segmentId().equals(segmentId)) {
                failures.add("classification processing-run sample/segment mismatch");
            }
        }
    }

    private static final class ReconstructionRowDetails {
        final String runId;
        final Integer totalComponents;
        final Integer eligibleComponents;
        final Integer reconstructedComponents;
        final Integer changedPixels;

        private ReconstructionRowDetails(
                final String runId,
                final Integer totalComponents,
                final Integer eligibleComponents,
                final Integer reconstructedComponents,
                final Integer changedPixels) {
            this.runId = runId;
            this.totalComponents = totalComponents;
            this.eligibleComponents = eligibleComponents;
            this.reconstructedComponents = reconstructedComponents;
            this.changedPixels = changedPixels;
        }
    }

    private static ReconstructionRowDetails checkReconstructionRunDetails(
            final ProductionDatasetLayout layout,
            final String imageId,
            final String safeSample,
            final String safeSegment,
            final int minimumLumenAreaPx,
            final List<String> failures) {
        final List<String> row = findSuccessfulProcessingRun(
                layout, imageId, ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
        if (row == null) {
            return null;
        }
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.PROCESSING_RUNS_HEADER);
        final String expectedInput = FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment);
        final String expectedOutput = FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment);
        final String inputFilename = row.get(idx.get("input_filename").intValue());
        final String outputFilename = row.get(idx.get("output_filename").intValue());
        if (inputFilename == null || !expectedInput.equals(inputFilename.trim())) {
            failures.add("reconstruction input_filename mismatch: expected "
                    + expectedInput + ", found " + inputFilename);
        }
        if (outputFilename == null || !expectedOutput.equals(outputFilename.trim())) {
            failures.add("reconstruction output_filename mismatch: expected "
                    + expectedOutput + ", found " + outputFilename);
        }
        final String minAreaRaw = row.get(idx.get("minimum_lumen_area_px").intValue());
        Integer minArea = parseNonNegativeInt(minAreaRaw);
        if (minArea == null) {
            failures.add("reconstruction minimum_lumen_area_px is not a nonnegative integer: "
                    + minAreaRaw);
        } else if (minArea.intValue() != minimumLumenAreaPx) {
            failures.add("reconstruction minimum_lumen_area_px mismatch: expected "
                    + minimumLumenAreaPx + ", found " + minAreaRaw);
        }

        final Integer total = requireNonNegativeInt(
                row, idx, "total_components", failures);
        final Integer eligible = requireNonNegativeInt(
                row, idx, "eligible_components", failures);
        final Integer reconstructed = requireNonNegativeInt(
                row, idx, "reconstructed_components", failures);
        final Integer changed = requireNonNegativeInt(
                row, idx, "changed_pixels", failures);

        final String runId = row.get(idx.get("run_id").intValue());
        return new ReconstructionRowDetails(
                runId == null || RCsvSupport.NA.equals(runId.trim()) ? null : runId.trim(),
                total, eligible, reconstructed, changed);
    }

    private static void checkAggregateTables(
            final ProductionDatasetLayout layout, final List<String> failures) {
        final Path measurementsAll = layout.dataLumenMeasurementsAll();
        if (!Files.isRegularFile(measurementsAll)) {
            failures.add("aggregate lumen_measurements_all CSV missing: " + measurementsAll);
        } else {
            final List<List<String>> table =
                    readTableSafely(measurementsAll, failures, "lumen_measurements_all");
            if (table != null) {
                if (table.isEmpty()
                        || !table.get(0).equals(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER)) {
                    failures.add("lumen_measurements_all header mismatch: " + measurementsAll);
                } else {
                    checkNoNaNCells(table, "lumen_measurements_all", failures);
                }
            }
        }

        final Path diagnosticsAll = layout.dataDiagnosticsAll();
        if (!Files.isRegularFile(diagnosticsAll)) {
            failures.add("aggregate artifact_diagnostics_all CSV missing: " + diagnosticsAll);
        } else {
            final List<List<String>> table =
                    readTableSafely(diagnosticsAll, failures, "artifact_diagnostics_all");
            if (table != null) {
                if (table.isEmpty()
                        || !table.get(0).equals(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER)) {
                    failures.add("artifact_diagnostics_all header mismatch: " + diagnosticsAll);
                } else {
                    checkNoNaNCells(table, "artifact_diagnostics_all", failures);
                }
            }
        }
    }

    private static void checkPathsUnderRoot(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment,
            final boolean requireReconstruction,
            final List<String> failures) {
        final Path root = layout.getDatasetRoot();
        requireUnderRoot(root, layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(safeSample, safeSegment)), failures);
        requireUnderRoot(root, layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename(safeSample, safeSegment)), failures);
        requireUnderRoot(root, layout.imagesMaskedClassified().resolve(
                FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment)), failures);
        if (requireReconstruction) {
            requireUnderRoot(root, layout.imagesReconstructedClassified().resolve(
                    FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment)), failures);
            requireUnderRoot(root, layout.dataLumenMeasurementsPerSegment().resolve(
                    FilenameSanitizer.lumenMeasurementsFilename(safeSample, safeSegment)), failures);
            requireUnderRoot(root, layout.dataDiagnosticsPerSegment().resolve(
                    FilenameSanitizer.artifactDiagnosticsFilename(safeSample, safeSegment)), failures);
        }
    }

    private static void requireUnderRoot(
            final Path root, final Path path, final List<String> failures) {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            failures.add("canonical path escapes dataset root: " + path);
        }
    }

    private static boolean approvedMaskExists(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment) {
        return findApprovedMask(layout, safeSample, safeSegment) != null;
    }

    private static Path findApprovedMask(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment) {
        final Path dir = layout.imagesApprovedMasks();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        final String prefix = safeSample + "__" + safeSegment + "__approved_mask_ds";
        try {
            final java.io.File[] children = dir.toFile().listFiles();
            if (children == null) {
                return null;
            }
            for (int i = 0; i < children.length; i++) {
                final String name = children[i].getName();
                if (name.startsWith(prefix) && name.endsWith(".tif") && children[i].isFile()) {
                    return children[i].toPath().toAbsolutePath().normalize();
                }
            }
        } catch (final RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static boolean checkFileExists(
            final Path path,
            final String label,
            final List<String> failures) {
        if (!Files.isRegularFile(path)) {
            failures.add(label + " file missing: " + path);
            return false;
        }
        return true;
    }

    private static void checkImageManifest(
            final ProductionDatasetLayout layout,
            final String imageId,
            final boolean requireReconstruction,
            final List<String> failures) {
        final Path path = layout.imageManifest();
        final List<List<String>> table = readTableSafely(path, failures, "image_manifest");
        if (table == null) {
            return;
        }
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.IMAGE_MANIFEST_HEADER)) {
            failures.add("image_manifest header mismatch: " + path);
            return;
        }
        checkNoNaNCells(table, "image_manifest", failures);
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.IMAGE_MANIFEST_HEADER);
        final CoreSegmentId expected = CoreSegmentId.parse(imageId);
        List<String> row = findRow(table, idx, expected.sampleId(), expected.segmentId());
        if (row == null) {
            failures.add("image_manifest has no row for sample_id/segment_id="
                    + expected.sampleId() + "/" + expected.segmentId());
            return;
        }
        checkFinitePositive(row, idx, "pixel_width_um", "image_manifest", failures);
        checkFinitePositive(row, idx, "pixel_height_um", "image_manifest", failures);
        final String classificationRunId = row.get(idx.get("classification_run_id").intValue());
        if (classificationRunId == null || RCsvSupport.NA.equals(classificationRunId.trim())) {
            failures.add("image_manifest classification_run_id is missing");
        }
        if (requireReconstruction) {
            final String reconstructedFilename =
                    row.get(idx.get("reconstructed_classified_filename").intValue());
            if (reconstructedFilename == null || reconstructedFilename.trim().isEmpty()
                    || RCsvSupport.NA.equals(reconstructedFilename.trim())) {
                failures.add("image_manifest reconstructed_classified_filename is missing");
            }
            final String reconstructionRunId = row.get(idx.get("reconstruction_run_id").intValue());
            if (reconstructionRunId == null || RCsvSupport.NA.equals(reconstructionRunId.trim())) {
                failures.add("image_manifest reconstruction_run_id is missing");
            }
        }
    }

    private static void checkSegmentManifest(
            final ProductionDatasetLayout layout,
            final String imageId,
            final List<String> failures) {
        final Path path = layout.segmentManifest();
        final List<List<String>> table = readTableSafely(path, failures, "segment_manifest");
        if (table == null) {
            return;
        }
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.SEGMENT_MANIFEST_HEADER)) {
            failures.add("segment_manifest header mismatch: " + path);
            return;
        }
        checkNoNaNCells(table, "segment_manifest", failures);
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        final CoreSegmentId expected = CoreSegmentId.parse(imageId);
        if (findRow(table, idx, expected.sampleId(), expected.segmentId()) == null) {
            failures.add("segment_manifest has no row for sample_id/segment_id="
                    + expected.sampleId() + "/" + expected.segmentId());
        }
    }

    private static void checkProcessingRuns(
            final ProductionDatasetLayout layout,
            final String imageId,
            final boolean requireReconstruction,
            final List<String> failures) {
        final Path path = layout.processingRuns();
        final List<List<String>> table = readTableSafely(path, failures, "processing_runs");
        if (table == null) {
            return;
        }
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.PROCESSING_RUNS_HEADER)) {
            failures.add("processing_runs header mismatch: " + path);
            return;
        }
        checkNoNaNCells(table, "processing_runs", failures);
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.PROCESSING_RUNS_HEADER);
        final CoreSegmentId expected = CoreSegmentId.parse(imageId);
        boolean classificationSuccess = false;
        boolean reconstructionSuccess = false;
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (!expected.sampleId().equals(row.get(idx.get("sample_id").intValue()))
                    || !expected.segmentId().equals(row.get(idx.get("segment_id").intValue()))) {
                continue;
            }
            final String stage = row.get(idx.get("stage").intValue());
            final String status = row.get(idx.get("status").intValue());
            if (ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name().equals(stage)
                    && ProductionSchemas.RunStatus.SUCCESS.name().equals(status)) {
                classificationSuccess = true;
            }
            if (ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name().equals(stage)
                    && ProductionSchemas.RunStatus.SUCCESS.name().equals(status)) {
                reconstructionSuccess = true;
            }
        }
        if (!classificationSuccess) {
            failures.add("processing_runs missing a TILED_CLASSIFICATION SUCCESS row for "
                    + imageId);
        }
        if (requireReconstruction && !reconstructionSuccess) {
            failures.add("processing_runs missing an ALL_LUMINA_RECONSTRUCTION SUCCESS row for "
                    + imageId);
        }
    }

    private static void checkLumenMeasurements(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment,
            final List<String> failures) {
        final Path path = layout.dataLumenMeasurementsPerSegment().resolve(
                FilenameSanitizer.lumenMeasurementsFilename(safeSample, safeSegment));
        if (!Files.isRegularFile(path)) {
            failures.add("lumen_measurements CSV missing: " + path);
            return;
        }
        final List<List<String>> table = readTableSafely(path, failures, "lumen_measurements");
        if (table == null) {
            return;
        }
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER)) {
            failures.add("lumen_measurements header mismatch: " + path);
            return;
        }
        checkNoNaNCells(table, "lumen_measurements", failures);
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
        final int uidIndex = idx.get("lumen_uid").intValue();
        final Set<String> seen = new HashSet<String>();
        for (int r = 1; r < table.size(); r++) {
            final String uid = table.get(r).get(uidIndex);
            if (!seen.add(uid)) {
                failures.add("duplicate lumen_uid in lumen_measurements: " + uid);
            }
        }
    }

    private static void checkDiagnostics(
            final ProductionDatasetLayout layout,
            final String safeSample,
            final String safeSegment,
            final List<String> failures) {
        final Path path = layout.dataDiagnosticsPerSegment().resolve(
                FilenameSanitizer.artifactDiagnosticsFilename(safeSample, safeSegment));
        if (!Files.isRegularFile(path)) {
            failures.add("artifact_diagnostics CSV missing: " + path);
            return;
        }
        final List<List<String>> table = readTableSafely(path, failures, "artifact_diagnostics");
        if (table == null) {
            return;
        }
        if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER)) {
            failures.add("artifact_diagnostics header mismatch: " + path);
            return;
        }
        checkNoNaNCells(table, "artifact_diagnostics", failures);
    }

    private static List<String> findSuccessfulProcessingRun(
            final ProductionDatasetLayout layout,
            final String imageId,
            final ProductionSchemas.ProcessingStage stage) {
        if (!Files.isRegularFile(layout.processingRuns())) {
            return null;
        }
        try {
            final List<List<String>> table = ProductionCsvStore.readTable(layout.processingRuns());
            if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.PROCESSING_RUNS_HEADER)) {
                return null;
            }
            final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                    ProductionSchemas.PROCESSING_RUNS_HEADER);
            final CoreSegmentId expected = CoreSegmentId.parse(imageId);
            List<String> latest = null;
            for (int r = 1; r < table.size(); r++) {
                final List<String> row = table.get(r);
                if (!expected.sampleId().equals(row.get(idx.get("sample_id").intValue()))
                        || !expected.segmentId().equals(row.get(idx.get("segment_id").intValue()))) {
                    continue;
                }
                if (stage.name().equals(row.get(idx.get("stage").intValue()))
                        && ProductionSchemas.RunStatus.SUCCESS.name().equals(
                                row.get(idx.get("status").intValue()))) {
                    latest = row;
                }
            }
            return latest;
        } catch (final IOException ex) {
            return null;
        }
    }

    private static Map<String, String> readImageManifestRowMap(
            final ProductionDatasetLayout layout, final String imageId) {
        if (!Files.isRegularFile(layout.imageManifest())) {
            return null;
        }
        try {
            final List<List<String>> table = ProductionCsvStore.readTable(layout.imageManifest());
            if (table.isEmpty() || !table.get(0).equals(ProductionSchemas.IMAGE_MANIFEST_HEADER)) {
                return null;
            }
            final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                    ProductionSchemas.IMAGE_MANIFEST_HEADER);
            final CoreSegmentId expected = CoreSegmentId.parse(imageId);
            final List<String> row = findRow(table, idx, expected.sampleId(), expected.segmentId());
            if (row == null) {
                return null;
            }
            final Map<String, String> map = new java.util.HashMap<String, String>();
            for (final Map.Entry<String, Integer> entry : idx.entrySet()) {
                map.put(entry.getKey(), row.get(entry.getValue().intValue()));
            }
            return map;
        } catch (final IOException ex) {
            return null;
        }
    }


    private static String sanitizeSample(final String imageId) {
        return FilenameSanitizer.sanitizeForFilename(CoreSegmentId.parse(imageId.trim()).sampleId());
    }

    private static String sanitizeSegment(final String imageId) {
        return FilenameSanitizer.sanitizeForFilename(CoreSegmentId.parse(imageId.trim()).segmentId());
    }

    private static List<String> findRow(
            final List<List<String>> table,
            final Map<String, Integer> idx,
            final String sampleId,
            final String segmentId) {
        final int sampleIndex = idx.get("sample_id").intValue();
        final int segmentIndex = idx.get("segment_id").intValue();
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (sampleId.equals(row.get(sampleIndex)) && segmentId.equals(row.get(segmentIndex))) {
                return row;
            }
        }
        return null;
    }

    private static void checkFinitePositive(
            final List<String> row,
            final Map<String, Integer> idx,
            final String column,
            final String tableName,
            final List<String> failures) {
        final Integer index = idx.get(column);
        if (index == null) {
            failures.add(tableName + " is missing expected column " + column);
            return;
        }
        final String raw = row.get(index.intValue());
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (final NumberFormatException ex) {
            failures.add(tableName + "." + column + " is not numeric: " + raw);
            return;
        }
        if (!Double.isFinite(value) || !(value > 0.0)) {
            failures.add(tableName + "." + column + " must be finite and > 0, found: " + raw);
        }
    }

    private static void checkPositiveInt(
            final Map<String, String> row,
            final String column,
            final String tableName,
            final List<String> failures) {
        final Integer value = parseNonNegativeInt(row.get(column));
        if (value == null || value.intValue() <= 0) {
            failures.add(tableName + "." + column + " must be a positive integer, found: "
                    + row.get(column));
        }
    }

    private static void checkNonNegativeInt(
            final Map<String, String> row,
            final String column,
            final String tableName,
            final List<String> failures) {
        final Integer value = parseNonNegativeInt(row.get(column));
        if (value == null) {
            failures.add(tableName + "." + column + " must be a nonnegative integer, found: "
                    + row.get(column));
        }
    }

    private static Integer requireNonNegativeInt(
            final List<String> row,
            final Map<String, Integer> idx,
            final String column,
            final List<String> failures) {
        final String raw = row.get(idx.get(column).intValue());
        final Integer value = parseNonNegativeInt(raw);
        if (value == null) {
            failures.add("reconstruction " + column + " is not a nonnegative integer: " + raw);
        }
        return value;
    }

    private static Integer parseNonNegativeInt(final String raw) {
        if (raw == null || raw.trim().isEmpty() || RCsvSupport.NA.equals(raw.trim())) {
            return null;
        }
        try {
            final int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                return null;
            }
            return Integer.valueOf(value);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static void checkNoNaNCells(
            final List<List<String>> table,
            final String tableName,
            final List<String> failures) {
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            for (int c = 0; c < row.size(); c++) {
                final String cell = row.get(c);
                if (cell != null && isNaNLike(cell)) {
                    failures.add(tableName + " row " + r + " column " + c
                            + " contains a non-finite value: " + cell);
                }
            }
        }
    }

    private static boolean isNaNLike(final String value) {
        final String trimmed = value.trim();
        return "NaN".equalsIgnoreCase(trimmed)
                || "Infinity".equalsIgnoreCase(trimmed)
                || "-Infinity".equalsIgnoreCase(trimmed);
    }

    private static List<List<String>> readTableSafely(
            final Path path,
            final List<String> failures,
            final String label) {
        if (!Files.isRegularFile(path)) {
            failures.add(label + " CSV missing: " + path);
            return null;
        }
        try {
            return ProductionCsvStore.readTable(path);
        } catch (final IOException ex) {
            failures.add("failed reading " + label + ": " + ex.getMessage());
            return null;
        }
    }

    private static String relativize(final Path root, final Path path) {
        if (root == null || path == null) {
            return null;
        }
        try {
            return root.toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (final IllegalArgumentException ex) {
            return path.toString().replace('\\', '/');
        }
    }
}
