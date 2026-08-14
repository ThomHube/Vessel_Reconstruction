package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-image Phase-3 full-processing orchestration, split into two
 * independently-launched stages that each run in a fresh Fiji JVM:
 *
 * <ul>
 * <li>{@link #processClassificationStage} binds the precheck, reproduces the mask
 * and scale bar, runs tiled Weka classification, and commits only the
 * classification-stage production outputs. It never touches All Lumina.</li>
 * <li>{@link #processReconstructionStage} opens <em>only</em> the already-saved
 * masked-classified TIFF from the dataset, verifies its checksum against
 * {@code image_manifest}, and runs All Lumina reconstruction. It never loads
 * a Weka classifier model.</li>
 * </ul>
 *
 * <p>This split exists because reconstruction (specifically
 * {@code FinalLumenGeometry.buildAll}) can exhaust heap after an otherwise
 * successful classification; running each stage in its own process means an
 * out-of-memory reconstruction failure can never destroy an
 * already-committed classification result.</p>
 */
public class BatchFullProcessingService {

    private static final String[] EXPECTED_CLASS_LABELS =
            {"Wood", "Ray", "Lumen", "Background"};

    /**
     * Production acquisition invariant: every final production TIFF is scanned
     * with the bark-nearest edge at the top of the image and the center/pith
     * nearest edge at the bottom. Positive {@code segment_axis_position_um}
     * therefore increases bark → center via {@link SegmentOrientation#TOP_TO_BOTTOM}.
     * This is not operator-selectable for the current production dataset.
     */
    private static final SegmentOrientation PRODUCTION_SEGMENT_ORIENTATION =
            SegmentOrientation.TOP_TO_BOTTOM;

    /**
     * Package-visible seams so unit tests can prove classification never
     * touches All Lumina and reconstruction never loads Weka.
     */
    interface WekaClassification {
        TiledWekaClassificationResult classify(
                ImagePlus sourceImage,
                File modelFile,
                TiledWekaClassificationConfiguration configuration,
                DownsampledSampleMask approvedMask,
                SampleMaskStatistics automaticMaskStatistics,
                boolean sampleMaskReviewed,
                AnalysisCropGeometry cropGeometry);
    }

    interface AllLumenReconstruction {
        AllLumenBatchResult process(
                ByteProcessor labels, AllLumenBatchConfiguration configuration);

        AllLuminaDatasetWriter.Result write(
                ImagePlus maskedClassified,
                ByteProcessor source,
                AllLumenBatchResult batchResult,
                int minimumArea,
                ProductionDatasetLayout layout,
                boolean refreshAggregates) throws IOException;
    }

    /** Optional package-visible listener for stage-entry isolation tests. */
    interface StageEntryListener {
        void onClassificationStageEntered();

        void onReconstructionStageEntered();
    }

    private static final StageEntryListener NOOP_LISTENER = new StageEntryListener() {
        @Override
        public void onClassificationStageEntered() {
        }

        @Override
        public void onReconstructionStageEntered() {
        }
    };

    private static final WekaClassification DEFAULT_WEKA = new WekaClassification() {
        @Override
        public TiledWekaClassificationResult classify(
                final ImagePlus sourceImage,
                final File modelFile,
                final TiledWekaClassificationConfiguration configuration,
                final DownsampledSampleMask approvedMask,
                final SampleMaskStatistics automaticMaskStatistics,
                final boolean sampleMaskReviewed,
                final AnalysisCropGeometry cropGeometry) {
            return new TiledWekaClassifier().classify(
                    sourceImage, modelFile, configuration, approvedMask,
                    automaticMaskStatistics, sampleMaskReviewed, cropGeometry);
        }
    };

    private static final AllLumenReconstruction DEFAULT_ALL_LUMEN = new AllLumenReconstruction() {
        @Override
        public AllLumenBatchResult process(
                final ByteProcessor labels, final AllLumenBatchConfiguration configuration) {
            return new AllLumenBatchProcessor().process(labels, configuration);
        }

        @Override
        public AllLuminaDatasetWriter.Result write(
                final ImagePlus maskedClassified,
                final ByteProcessor source,
                final AllLumenBatchResult batchResult,
                final int minimumArea,
                final ProductionDatasetLayout layout,
                final boolean refreshAggregates) throws IOException {
            return new AllLuminaDatasetWriter().write(
                    maskedClassified, source, batchResult, minimumArea, layout, refreshAggregates);
        }
    };

    private final WekaClassification wekaClassification;
    private final AllLumenReconstruction allLumenReconstruction;
    private final StageEntryListener stageEntryListener;

    public BatchFullProcessingService() {
        this(DEFAULT_WEKA, DEFAULT_ALL_LUMEN, NOOP_LISTENER);
    }

    BatchFullProcessingService(
            final WekaClassification wekaClassification,
            final AllLumenReconstruction allLumenReconstruction) {
        this(wekaClassification, allLumenReconstruction, NOOP_LISTENER);
    }

    BatchFullProcessingService(
            final WekaClassification wekaClassification,
            final AllLumenReconstruction allLumenReconstruction,
            final StageEntryListener stageEntryListener) {
        this.wekaClassification = wekaClassification == null ? DEFAULT_WEKA : wekaClassification;
        this.allLumenReconstruction =
                allLumenReconstruction == null ? DEFAULT_ALL_LUMEN : allLumenReconstruction;
        this.stageEntryListener = stageEntryListener == null ? NOOP_LISTENER : stageEntryListener;
    }

    /**
     * Stage-aware child entry: dispatches strictly from
     * {@link BatchFullProcessingJob#processingStage()}. Missing or unknown
     * stages fail before image processing. Never silently falls back to a
     * combined path.
     */
    public BatchFullProcessingStageResult process(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        if (job == null) {
            return stageFail(null, BatchFullProcessingStage.CLASSIFICATION, config,
                    new ProgressFlags(), BatchFullProcessingStatus.INVALID_JOB,
                    "JOB_NULL", "job must not be null", System.currentTimeMillis());
        }
        final BatchFullProcessingStage stage = job.processingStage();
        if (stage == null) {
            return stageFail(job, BatchFullProcessingStage.CLASSIFICATION, config,
                    new ProgressFlags(), BatchFullProcessingStatus.INVALID_JOB,
                    "MISSING_STAGE",
                    "processing_stage is required for the stage-aware child path",
                    System.currentTimeMillis());
        }
        if (stage == BatchFullProcessingStage.CLASSIFICATION) {
            return processClassificationStage(job, config);
        }
        if (stage == BatchFullProcessingStage.RECONSTRUCTION) {
            return processReconstructionStage(job, config);
        }
        return stageFail(job, stage, config, new ProgressFlags(),
                BatchFullProcessingStatus.INVALID_JOB, "UNKNOWN_STAGE",
                "unsupported processing_stage: " + stage, System.currentTimeMillis());
    }

    /**
     * Independent CLASSIFICATION child operation. Returns a standalone
     * {@link BatchFullProcessingStageResult}.
     */
    public BatchFullProcessingStageResult processClassificationStage(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        stageEntryListener.onClassificationStageEntered();
        return toStageResult(
                BatchFullProcessingStage.CLASSIFICATION,
                job,
                processClassification(job, config));
    }

    /**
     * Independent RECONSTRUCTION child operation. Returns a standalone
     * {@link BatchFullProcessingStageResult}. Never reads the classifier path.
     */
    public BatchFullProcessingStageResult processReconstructionStage(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        stageEntryListener.onReconstructionStageEntered();
        return toStageResult(
                BatchFullProcessingStage.RECONSTRUCTION,
                job,
                processReconstruction(job, config));
    }

    /**
     * Temporary legacy compatibility path that still returns the rich
     * {@link BatchFullProcessingResult} produced by one stage. Prefer
     * {@link #processClassificationStage} / {@link #processReconstructionStage}
     * for the stage-aware Fiji child. Do not call this from the new child
     * plugin path. Remove once mini-iteration 3C retires combined-result
     * expectations from callers that still consume {@code BatchFullProcessingResult}.
     */
    public BatchFullProcessingResult processLegacyCombinedResult(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        if (job != null && job.processingStage() == BatchFullProcessingStage.RECONSTRUCTION) {
            return processReconstruction(job, config);
        }
        return processClassification(job, config);
    }

    /**
     * Classification-only stage (legacy rich result). Prefer
     * {@link #processClassificationStage} for the child path. Never runs All
     * Lumina reconstruction.
     */
    public BatchFullProcessingResult processClassification(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        final long started = System.currentTimeMillis();
        final ProgressFlags flags = new ProgressFlags();
        ImagePlus source = null;
        SampleMaskGenerationResult maskResult = null;
        ImagePlus classifiedLabels = null;
        logHeap("CLASSIFICATION_START");
        try {
            if (job == null) {
                return fail(null, null, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "JOB_NULL", "job must not be null", started);
            }
            if (config == null) {
                return fail(job, null, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "CONFIG_NULL", "configuration must not be null", started);
            }
            if (job.processingStage() != BatchFullProcessingStage.CLASSIFICATION) {
                final String actual = job.processingStage() == null
                        ? "null" : job.processingStage().name();
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "WRONG_STAGE",
                        "job processing_stage is " + actual
                                + ", expected CLASSIFICATION", started);
            }
            if (!config.sha256().equalsIgnoreCase(job.configurationSha256())) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "CONFIG_SHA_MISMATCH", "configuration SHA-256 mismatch", started);
            }

            // 1. Bind precheck.
            final BindResult bind = bindPrecheck(job, config, flags, started);
            if (bind.failure != null) {
                return bind.failure;
            }
            flags.precheckVerified = Boolean.TRUE;

            // 2. Open TIFF; verify RGB; checksum before.
            try {
                final String actualSha = Sha256Files.sha256OfFile(job.sourcePath());
                if (!actualSha.equalsIgnoreCase(job.sourceSha256())) {
                    return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                            "STALE_SOURCE", "source SHA-256 mismatch before open", started);
                }
            } catch (final Exception ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "SOURCE_UNREADABLE", ex.getMessage(), started);
            }
            try {
                source = new Opener().openImage(job.sourcePath().toString());
            } catch (final Exception ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "OPEN_FAILED", ex.getMessage(), started);
            }
            if (source == null) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "OPEN_NULL", "Opener returned null", started);
            }
            if (!(source.getProcessor() instanceof ColorProcessor)
                    && source.getType() != ImagePlus.COLOR_RGB) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "NOT_RGB", "source must be RGB", started);
            }
            final int width = source.getWidth();
            final int height = source.getHeight();
            if (width <= 0 || height <= 0) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "NON_POSITIVE_DIMENSIONS", "source dimensions must be positive", started);
            }
            final int checksumBefore = TiledWekaClassifier.checksumSource(source);

            // 3a. Regenerate mask and compare exactly with precheck.
            try {
                maskResult = new AutomaticSampleMaskGenerator().generate(
                        source, config.maskDownsampleFactor(), config.maskExpansionMarginPx());
            } catch (final SampleMaskGenerationException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION,
                        "MASK_GENERATION_FAILED", ex.getMessage(), started);
            }
            final AnalysisCropGeometry crop = AnalysisCropGeometry.fromApprovedMask(
                    maskResult.getMask(), config.maskCropPaddingPx());
            final BatchMaskAcceptancePolicy.Decision maskDecision =
                    BatchMaskAcceptancePolicy.evaluate(maskResult, crop, width, height, config);
            if (!maskDecision.accepted) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION,
                        maskDecision.reasonCode, maskDecision.message, started);
            }
            final int maskChecksum = maskResult.getStatistics().getAutomaticMaskChecksum();
            final int precheckMaskChecksum = parseIntStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_MASK_CHECKSUM));
            if (maskChecksum != precheckMaskChecksum) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION,
                        "MASK_CHECKSUM_MISMATCH",
                        "mask checksum " + maskChecksum + " != precheck " + precheckMaskChecksum,
                        started);
            }
            final int precheckCropX = parseIntStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_CROP_X));
            final int precheckCropY = parseIntStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_CROP_Y));
            final int precheckCropW = parseIntStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_CROP_W));
            final int precheckCropH = parseIntStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_CROP_H));
            if (crop.getCropXPx() != precheckCropX
                    || crop.getCropYPx() != precheckCropY
                    || crop.getCropWidthPx() != precheckCropW
                    || crop.getCropHeightPx() != precheckCropH) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION,
                        "CROP_MISMATCH",
                        String.format(Locale.US,
                                "crop (%d,%d,%d,%d) != precheck (%d,%d,%d,%d)",
                                Integer.valueOf(crop.getCropXPx()),
                                Integer.valueOf(crop.getCropYPx()),
                                Integer.valueOf(crop.getCropWidthPx()),
                                Integer.valueOf(crop.getCropHeightPx()),
                                Integer.valueOf(precheckCropX),
                                Integer.valueOf(precheckCropY),
                                Integer.valueOf(precheckCropW),
                                Integer.valueOf(precheckCropH)),
                        started);
            }
            flags.maskReproduced = Boolean.TRUE;

            // 3b. Scale-bar detection and comparison with precheck.
            ScaleBarDetection detection;
            try {
                detection = new AutomaticScaleBarDetector().detect(
                        source,
                        maskResult.getMask(),
                        (ColorProcessor) maskResult.getRgbPreview().getProcessor());
            } catch (final RuntimeException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                        "SCALE_BAR_DETECTION_EXCEPTION", ex.getMessage(), started);
            }
            final BatchScaleBarAcceptancePolicy.Decision scaleDecision =
                    BatchScaleBarAcceptancePolicy.evaluate(detection, maskResult.getMask(), config);
            if (!scaleDecision.accepted) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                        scaleDecision.reasonCode, scaleDecision.message, started);
            }
            final String precheckMethod = bind.precheckResult.get(BatchPrecheckResult.KEY_SB_METHOD);
            if (!detection.getMethod().name().equals(precheckMethod)) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                        "SCALE_BAR_METHOD_MISMATCH",
                        "method " + detection.getMethod().name() + " != precheck " + precheckMethod,
                        started);
            }
            final double precheckCenter = parseDoubleStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_SB_CENTER));
            if (Math.abs(detection.getMeasuredLengthPx() - precheckCenter) > 0.01) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                        "SCALE_BAR_CENTER_DISTANCE_MISMATCH",
                        "center distance " + detection.getMeasuredLengthPx()
                                + " != precheck " + precheckCenter,
                        started);
            }
            final double precheckPixelSize = parseDoubleStrict(
                    bind.precheckResult.get(BatchPrecheckResult.KEY_PX_SIZE));
            final double relDev = precheckPixelSize == 0.0
                    ? Double.NaN
                    : Math.abs(scaleDecision.pixelSizeUm - precheckPixelSize) / precheckPixelSize;
            if (!(relDev <= 1e-6)) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                        "SCALE_BAR_PIXEL_SIZE_MISMATCH",
                        "pixel size " + scaleDecision.pixelSizeUm
                                + " != precheck " + precheckPixelSize,
                        started);
            }
            flags.scaleBarReproduced = Boolean.TRUE;

            // 4. Physical calibration from the auto-detected scale bar.
            final PhysicalCalibration calibration = PhysicalCalibration.fromAutoDetectedScaleBar(
                    config.scaleBarPhysicalLengthUm(), detection.getMeasuredLengthPx());

            // 5. Tiled Weka classification configuration.
            final String imageId = job.imageId().imageId();
            final CoreSegmentId coreSegmentId = job.imageId();
            final String sampleId = coreSegmentId.sampleId();
            final String segmentId = coreSegmentId.segmentId();
            final int segmentOrder = coreSegmentId.segmentOrder();
            final TiledWekaClassificationConfiguration twConfig =
                    new TiledWekaClassificationConfiguration(
                            job.tileProfile().widthPx(),
                            job.tileProfile().heightPx(),
                            config.threadsPerTile(),
                            config.runUntiledReferenceComparison(),
                            config.seamAuditHalfWidthPx(),
                            config.maskDownsampleFactor(),
                            config.maskExpansionMarginPx(),
                            false,
                            true,
                            config.maskCropPaddingPx(),
                            true,
                            true,
                            true,
                            sampleId,
                            segmentId,
                            segmentOrder,
                            PRODUCTION_SEGMENT_ORIENTATION,
                            null,
                            job.datasetRoot().toFile(),
                            TiledWekaClassificationDefaults.CALIBRATION_MODE,
                            config.scaleBarPhysicalLengthUm(),
                            null,
                            null);

            // 6. Production dataset layout.
            final ProductionDatasetLayout layout = new ProductionDatasetLayout(job.datasetRoot());
            try {
                layout.ensureDirectoriesExist();
            } catch (final IOException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_UNEXPECTED,
                        "DATASET_DIRECTORIES_FAILED", ex.getMessage(), started);
            }

            // 7. Save pre-classification artifacts, then classify, then finalize.
            final ClassificationDatasetWriter writer = new ClassificationDatasetWriter();
            final ClassificationDatasetWriter.PreClassificationArtifacts pre;
            try {
                pre = writer.savePreClassificationArtifacts(
                        source, job.sourcePath().toFile(), maskResult.getMask(), crop,
                        calibration, twConfig, detection, layout);
            } catch (final IOException ex) {
                return classificationFailure(job, config, flags, ex, started);
            }

            final TiledWekaClassificationResult classificationResult;
            try {
                classificationResult = wekaClassification.classify(
                        source, job.classifierPath().toFile(), twConfig, maskResult.getMask(),
                        maskResult.getStatistics(), false, crop);
            } catch (final OutOfMemoryError oom) {
                flags.outOfMemoryDetected = Boolean.TRUE;
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                        "CLASSIFICATION_OUT_OF_MEMORY", oom.getMessage(), started);
            } catch (final RuntimeException ex) {
                if (containsOutOfMemory(ex)) {
                    flags.outOfMemoryDetected = Boolean.TRUE;
                    return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                            "CLASSIFICATION_OUT_OF_MEMORY", ex.getMessage(), started);
                }
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_CLASSIFICATION,
                        "CLASSIFICATION_FAILED", ex.getMessage(), started);
            }

            warnIfClassLabelsUnexpected(classificationResult);

            classifiedLabels = classificationResult.getTiledDirectLabels();

            // 8. Re-verify the authoritative source TIFF after Weka and before
            // committing post-Weka classification success. Uses the same
            // job.sourceSha256() already verified before Weka.
            final String postWekaSourceSha;
            try {
                postWekaSourceSha = Sha256Files.sha256OfFile(job.sourcePath());
            } catch (final Exception ex) {
                flags.sourceUnchanged = Boolean.FALSE;
                flags.requiredOutputsValidated = Boolean.FALSE;
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "SOURCE_UNREADABLE", ex.getMessage(), started);
            }
            if (!postWekaSourceSha.equalsIgnoreCase(job.sourceSha256())) {
                flags.sourceUnchanged = Boolean.FALSE;
                flags.requiredOutputsValidated = Boolean.FALSE;
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_SOURCE,
                        "SOURCE_CHANGED", "source changed during classification", started);
            }
            flags.sourceUnchanged = Boolean.TRUE;

            // 9. Commit post-Weka classification success only after source re-hash.
            final ClassificationDatasetWriter.Result classResult;
            try {
                classResult = writer.finalizeClassificationStage(
                        pre, source, classifiedLabels, maskResult.getMask(), crop, calibration,
                        twConfig, classificationResult, job.classifierPath().toFile(),
                        detection, layout);
            } catch (final IOException ex) {
                return classificationFailure(job, config, flags, ex, started);
            }
            flags.classificationCompleted = Boolean.TRUE;

            // 10. Validate committed classification-only production outputs.
            final BatchProductionOutputValidator.ValidationResult validation =
                    BatchProductionOutputValidator.validateClassificationOnly(
                            job.datasetRoot(), imageId, job.sourceSha256());
            flags.requiredOutputsValidated = Boolean.valueOf(validation.ok);

            final int checksumAfter = TiledWekaClassifier.checksumSource(source);

            final BatchFullProcessingStatus finalStatus;
            final String finalReason;
            final String finalMessage;
            if (!validation.ok) {
                finalStatus = BatchFullProcessingStatus.FAILED_OUTPUT_VALIDATION;
                finalReason = "VALIDATION_FAILED";
                finalMessage = joinFailures(validation.failures);
            } else {
                finalStatus = BatchFullProcessingStatus.FULL_SUCCESS;
                finalReason = RCsvSupport.NA;
                finalMessage = "classification stage committed";
            }

            return baseBuilder(job, config, flags, finalStatus, finalReason, finalMessage, started)
                    .datasetRoot(job.datasetRoot().toString())
                    .originalRgbPath(relativize(job.datasetRoot(), classResult.originalRgb))
                    .approvedMaskPath(relativize(job.datasetRoot(), classResult.approvedMask))
                    .maskedRgbPath(relativize(job.datasetRoot(), classResult.maskedRgb))
                    .maskedClassifiedPath(relativize(job.datasetRoot(), classResult.maskedClassified))
                    .imageManifestPath(relativize(job.datasetRoot(), layout.imageManifest()))
                    .segmentManifestPath(relativize(job.datasetRoot(), layout.segmentManifest()))
                    .processingRunsPath(relativize(job.datasetRoot(), layout.processingRuns()))
                    .classificationRunId(classResult.runId)
                    .classificationCompleted(Boolean.TRUE)
                    .reconstructionCompleted(Boolean.FALSE)
                    .sourceChecksums(Integer.valueOf(checksumBefore), Integer.valueOf(checksumAfter))
                    .processingTimeMs(System.currentTimeMillis() - started)
                    .build();
        } catch (final OutOfMemoryError oom) {
            flags.outOfMemoryDetected = Boolean.TRUE;
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                    "CLASSIFICATION_OUT_OF_MEMORY", oom.getMessage(), started);
        } catch (final Exception ex) {
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_UNEXPECTED,
                    "UNEXPECTED", ex.getMessage(), started);
        } finally {
            logHeap("CLASSIFICATION_END");
            closeAllImages(source, maskResult, classifiedLabels, null);
        }
    }

    /**
     * Reconstruction-only stage: open only the previously saved
     * masked-classified TIFF, verify its checksum against
     * {@code image_manifest}, and run All Lumina reconstruction. Never loads
     * a Weka classifier model.
     */
    public BatchFullProcessingResult processReconstruction(
            final BatchFullProcessingJob job,
            final BatchConfiguration config) {
        final long started = System.currentTimeMillis();
        final ProgressFlags flags = new ProgressFlags();
        ImagePlus maskedClassifiedImage = null;
        logHeap("RECONSTRUCTION_START");
        try {
            if (job == null) {
                return fail(null, null, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "JOB_NULL", "job must not be null", started);
            }
            if (config == null) {
                return fail(job, null, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "CONFIG_NULL", "configuration must not be null", started);
            }
            if (job.processingStage() != BatchFullProcessingStage.RECONSTRUCTION) {
                final String actual = job.processingStage() == null
                        ? "null" : job.processingStage().name();
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "WRONG_STAGE",
                        "job processing_stage is " + actual
                                + ", expected RECONSTRUCTION", started);
            }
            if (!config.sha256().equalsIgnoreCase(job.configurationSha256())) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "CONFIG_SHA_MISMATCH", "configuration SHA-256 mismatch", started);
            }

            // Reconstruction always assumes a prior successful classification stage;
            // that assumption is checked below via the masked-classified SHA-256.
            flags.classificationCompleted = Boolean.TRUE;

            final String imageId = job.imageId().imageId();
            final ProductionDatasetLayout layout = new ProductionDatasetLayout(job.datasetRoot());
            if (!layout.isAccessibleDirectory()) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "DATASET_ROOT_MISSING", "dataset root missing: " + job.datasetRoot(), started);
            }
            final String safeSample;
            final String safeSegment;
            try {
                final CoreSegmentId parsedId = CoreSegmentId.parse(imageId);
                safeSample = FilenameSanitizer.sanitizeForFilename(parsedId.sampleId());
                safeSegment = FilenameSanitizer.sanitizeForFilename(parsedId.segmentId());
            } catch (final RuntimeException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.INVALID_JOB,
                        "IMAGE_ID_UNSAFE", ex.getMessage(), started);
            }

            final Path maskedClassifiedPath = layout.imagesMaskedClassified().resolve(
                    FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment));
            if (!Files.isRegularFile(maskedClassifiedPath)) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_MISSING",
                        "masked classified TIFF missing: " + maskedClassifiedPath, started);
            }

            final Map<String, String> manifestRow;
            try {
                manifestRow = readImageManifestRow(layout, imageId);
            } catch (final IOException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "IMAGE_MANIFEST_UNREADABLE", ex.getMessage(), started);
            }
            if (manifestRow == null) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "IMAGE_MANIFEST_ROW_MISSING",
                        "image_manifest has no row for " + imageId, started);
            }
            final String manifestSha = manifestRow.get("masked_classified_sha256");
            final String classificationRunId = manifestRow.get("classification_run_id");
            if (manifestSha == null || RCsvSupport.NA.equals(manifestSha.trim())) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_SHA_MISSING",
                        "image_manifest masked_classified_sha256 is missing", started);
            }
            final String actualSha;
            try {
                actualSha = Sha256Files.sha256OfFile(maskedClassifiedPath);
            } catch (final IOException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_UNREADABLE", ex.getMessage(), started);
            }
            if (!actualSha.equalsIgnoreCase(manifestSha.trim())) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_SHA_MISMATCH",
                        "masked classified SHA-256 " + actualSha
                                + " != image_manifest " + manifestSha, started);
            }

            logHeap("RECONSTRUCTION_BEFORE_OPEN");
            try {
                maskedClassifiedImage = new Opener().openImage(maskedClassifiedPath.toString());
            } catch (final Exception ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "REOPEN_MASKED_CLASSIFIED_FAILED", ex.getMessage(), started);
            }
            if (maskedClassifiedImage == null
                    || !(maskedClassifiedImage.getProcessor() instanceof ByteProcessor)) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_INVALID",
                        "masked classified TIFF missing or not a ByteProcessor", started);
            }

            final Object infoProperty = maskedClassifiedImage.getProperty("Info");
            final LumenImageMetadata.ParseResult parsedMetadata = LumenImageMetadata.parse(
                    infoProperty == null ? "" : String.valueOf(infoProperty));
            if (!parsedMetadata.isAvailable()) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_METADATA_MISSING",
                        parsedMetadata.getUnavailableReason(), started);
            }
            final LumenImageMetadata metadata = parsedMetadata.getMetadata().get();
            try {
                final String metaSampleId = metadata.getRequired(LumenImageMetadata.KEY_SAMPLE_ID);
                final String metaSegmentId = metadata.getRequired(LumenImageMetadata.KEY_SEGMENT_ID);
                final CoreSegmentId expectedId = CoreSegmentId.parse(imageId);
                if (!expectedId.sampleId().equals(metaSampleId)
                        || !expectedId.segmentId().equals(metaSegmentId)) {
                    return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                            "MASKED_CLASSIFIED_IDENTITY_MISMATCH",
                            "masked classified sample/segment ids are " + metaSampleId + "/"
                                    + metaSegmentId
                                    + ", expected " + expectedId.sampleId() + "/"
                                    + expectedId.segmentId(), started);
                }
                final int originalWidth = metadata.getIntRequired(
                        LumenImageMetadata.KEY_ORIGINAL_WIDTH_PX);
                final int originalHeight = metadata.getIntRequired(
                        LumenImageMetadata.KEY_ORIGINAL_HEIGHT_PX);
                if (originalWidth <= 0 || originalHeight <= 0) {
                    return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                            "MASKED_CLASSIFIED_DIMENSIONS_INVALID",
                            "original dimensions must be positive", started);
                }
                // Crop offsets are required integers (may be zero).
                metadata.getIntRequired(LumenImageMetadata.KEY_CROP_X_PX);
                metadata.getIntRequired(LumenImageMetadata.KEY_CROP_Y_PX);
                metadata.getIntRequired(LumenImageMetadata.KEY_CROP_WIDTH_PX);
                metadata.getIntRequired(LumenImageMetadata.KEY_CROP_HEIGHT_PX);
                final double pixelWidth = metadata.getDoubleRequired(
                        LumenImageMetadata.KEY_PIXEL_WIDTH_UM);
                final double pixelHeight = metadata.getDoubleRequired(
                        LumenImageMetadata.KEY_PIXEL_HEIGHT_UM);
                if (!(pixelWidth > 0.0) || !(pixelHeight > 0.0)) {
                    return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                            "MASKED_CLASSIFIED_CALIBRATION_INVALID",
                            "pixel size must be finite and positive", started);
                }
            } catch (final RuntimeException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_METADATA_INVALID", ex.getMessage(), started);
            }

            final AllLumenBatchConfiguration allLumenConfig = new AllLumenBatchConfiguration(
                    config.reconstructionMinimumLumenAreaPx(),
                    true,
                    AllLumenBatchConfiguration.DisplayPolicy.SILENT,
                    false,
                    false,
                    false,
                    false);

            logHeap("RECONSTRUCTION_BEFORE_ANALYSIS");
            System.gc();
            final AllLumenBatchResult allLumenResult;
            try {
                allLumenResult = allLumenReconstruction.process(
                        (ByteProcessor) maskedClassifiedImage.getProcessor(), allLumenConfig);
            } catch (final OutOfMemoryError oom) {
                flags.outOfMemoryDetected = Boolean.TRUE;
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                        "RECONSTRUCTION_OUT_OF_MEMORY", oom.getMessage(), started);
            } catch (final RuntimeException ex) {
                if (containsOutOfMemory(ex)) {
                    flags.outOfMemoryDetected = Boolean.TRUE;
                    return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                            "RECONSTRUCTION_OUT_OF_MEMORY", ex.getMessage(), started);
                }
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "RECONSTRUCTION_FAILED", ex.getMessage(), started);
            }

            logHeap("RECONSTRUCTION_BEFORE_WRITE");
            final AllLuminaDatasetWriter.Result reconResult;
            try {
                reconResult = allLumenReconstruction.write(
                        maskedClassifiedImage,
                        (ByteProcessor) maskedClassifiedImage.getProcessor(),
                        allLumenResult,
                        config.reconstructionMinimumLumenAreaPx(),
                        layout,
                        config.refreshAggregateTables());
            } catch (final OutOfMemoryError oom) {
                flags.outOfMemoryDetected = Boolean.TRUE;
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                        "RECONSTRUCTION_OUT_OF_MEMORY", oom.getMessage(), started);
            } catch (final IOException ex) {
                return reconstructionFailure(job, config, flags, ex, started);
            }
            flags.reconstructionCompleted = Boolean.TRUE;

            final String shaAfterWrite;
            try {
                shaAfterWrite = Sha256Files.sha256OfFile(maskedClassifiedPath);
            } catch (final IOException ex) {
                return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                        "MASKED_CLASSIFIED_UNREADABLE_AFTER", ex.getMessage(), started);
            }
            final boolean maskedUnchanged = shaAfterWrite.equalsIgnoreCase(actualSha);
            flags.sourceUnchanged = Boolean.valueOf(maskedUnchanged);

            // Validate the full committed production outputs (classification is
            // assumed already committed; this also re-confirms it).
            final BatchProductionOutputValidator.ValidationResult validation =
                    BatchProductionOutputValidator.validate(
                            job.datasetRoot(), imageId, job.sourceSha256());
            flags.requiredOutputsValidated = Boolean.valueOf(validation.ok);

            logHeap("RECONSTRUCTION_BEFORE_SAVE");

            final BatchFullProcessingStatus finalStatus;
            final String finalReason;
            final String finalMessage;
            if (!maskedUnchanged) {
                finalStatus = BatchFullProcessingStatus.FAILED_RECONSTRUCTION;
                finalReason = "MASKED_CLASSIFIED_CHANGED";
                finalMessage = "masked classified TIFF changed during reconstruction";
            } else if (!validation.ok) {
                finalStatus = BatchFullProcessingStatus.FAILED_OUTPUT_VALIDATION;
                finalReason = "VALIDATION_FAILED";
                finalMessage = joinFailures(validation.failures);
            } else {
                finalStatus = BatchFullProcessingStatus.FULL_SUCCESS;
                finalReason = RCsvSupport.NA;
                finalMessage = "reconstruction stage committed";
            }

            return baseBuilder(job, config, flags, finalStatus, finalReason, finalMessage, started)
                    .datasetRoot(job.datasetRoot().toString())
                    .originalRgbPath(relativizeManifestPath(job.datasetRoot(), manifestRow.get("source_rgb_path")))
                    .approvedMaskPath(relativizeManifestPath(job.datasetRoot(), manifestRow.get("approved_mask_path")))
                    .maskedRgbPath(relativizeManifestPath(job.datasetRoot(), manifestRow.get("masked_rgb_path")))
                    .maskedClassifiedPath(relativize(job.datasetRoot(), maskedClassifiedPath))
                    .reconstructedClassifiedPath(
                            relativize(job.datasetRoot(), reconResult.reconstructedClassified))
                    .lumenMeasurementsPath(relativize(job.datasetRoot(), reconResult.measurements))
                    .artifactDiagnosticsPath(relativize(job.datasetRoot(), reconResult.diagnostics))
                    .imageManifestPath(relativize(job.datasetRoot(), layout.imageManifest()))
                    .segmentManifestPath(relativize(job.datasetRoot(), layout.segmentManifest()))
                    .processingRunsPath(relativize(job.datasetRoot(), layout.processingRuns()))
                    .classificationRunId(classificationRunId)
                    .reconstructionRunId(reconResult.runId)
                    .classificationCompleted(Boolean.TRUE)
                    .reconstructionCompleted(Boolean.TRUE)
                    .components(
                            Integer.valueOf(allLumenResult.getTotalComponentCount()),
                            Integer.valueOf(allLumenResult.getEligibleComponentCount()),
                            Integer.valueOf(allLumenResult.getReconstructedComponentCount()),
                            Integer.valueOf(allLumenResult.getCombinedChangedPixelCount()))
                    .processingTimeMs(System.currentTimeMillis() - started)
                    .build();
        } catch (final OutOfMemoryError oom) {
            flags.outOfMemoryDetected = Boolean.TRUE;
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                    "RECONSTRUCTION_OUT_OF_MEMORY", oom.getMessage(), started);
        } catch (final Exception ex) {
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_UNEXPECTED,
                    "UNEXPECTED", ex.getMessage(), started);
        } finally {
            logHeap("RECONSTRUCTION_END");
            closeAllImages(null, null, null, maskedClassifiedImage);
        }
    }

    /**
     * Case-insensitive, depth-limited cause-chain search for an
     * {@link OutOfMemoryError}. Shared by the plugin and the CLI.
     */
    public static boolean containsOutOfMemory(final Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 16) {
            if (current instanceof OutOfMemoryError) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    /** Prints current heap usage for {@code stageLabel} to stdout. */
    public static void logHeap(final String stageLabel) {
        final Runtime rt = Runtime.getRuntime();
        final long max = rt.maxMemory();
        final long total = rt.totalMemory();
        final long free = rt.freeMemory();
        final long used = total - free;
        System.out.println(String.format(Locale.US,
                "HEAP[%s] max=%d total=%d free=%d used=%d",
                stageLabel,
                Long.valueOf(max), Long.valueOf(total), Long.valueOf(free), Long.valueOf(used)));
    }

    private static void warnIfClassLabelsUnexpected(final TiledWekaClassificationResult result) {
        final String[] labels = result.getModelClassLabels();
        boolean matches = labels != null && labels.length == EXPECTED_CLASS_LABELS.length;
        if (matches) {
            for (int i = 0; i < EXPECTED_CLASS_LABELS.length; i++) {
                if (!EXPECTED_CLASS_LABELS[i].equalsIgnoreCase(labels[i])) {
                    matches = false;
                    break;
                }
            }
        }
        if (!matches) {
            System.out.println("WARNING: loaded classifier model class labels "
                    + Arrays.toString(labels) + " differ from expected "
                    + Arrays.toString(EXPECTED_CLASS_LABELS)
                    + "; preserving index mapping 0=Wood,1=Ray,2=Lumen,3=Background.");
        }
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
        final CoreSegmentId expectedId = CoreSegmentId.parse(imageId);
        final String expectedSample = expectedId.sampleId();
        final String expectedSegment = expectedId.segmentId();
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (expectedSample.equals(row.get(sampleIdx))
                    && expectedSegment.equals(row.get(segmentIdx))) {
                final Map<String, String> map = new HashMap<String, String>();
                for (final Map.Entry<String, Integer> entry : idx.entrySet()) {
                    map.put(entry.getKey(), row.get(entry.getValue().intValue()));
                }
                return map;
            }
        }
        return null;
    }

    private static String relativizeManifestPath(final Path datasetRoot, final String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty() || RCsvSupport.NA.equals(rawPath.trim())) {
            return null;
        }
        return relativize(datasetRoot, Paths.get(rawPath.trim()));
    }

    private static final class ProgressFlags {
        Boolean precheckVerified;
        Boolean maskReproduced;
        Boolean scaleBarReproduced;
        Boolean classificationCompleted;
        Boolean reconstructionCompleted;
        Boolean requiredOutputsValidated;
        Boolean sourceUnchanged;
        Boolean outOfMemoryDetected;
    }

    private static final class BindResult {
        BatchFullProcessingResult failure;
        BatchPrecheckResult precheckResult;
    }

    private BindResult bindPrecheck(
            final BatchFullProcessingJob job,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final long serviceStarted) {
        final BindResult result = new BindResult();
        final long started = System.currentTimeMillis();
        if (!Files.isRegularFile(job.precheckResultPath())) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                    "PRECHECK_RESULT_MISSING",
                    "precheck result missing: " + job.precheckResultPath(), started);
            return result;
        }
        final BatchPrecheckResult precheckResult;
        try {
            precheckResult = BatchPrecheckResult.load(job.precheckResultPath());
        } catch (final Exception ex) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                    "PRECHECK_RESULT_INVALID", ex.getMessage(), started);
            return result;
        }
        if (!precheckResult.status().isSuccess()) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_SUCCESSFUL,
                    "PRECHECK_NOT_SUCCESSFUL",
                    "precheck status was " + precheckResult.status().name(), started);
            return result;
        }
        if (!job.sourceSha256().equalsIgnoreCase(
                precheckResult.get(BatchPrecheckResult.KEY_SOURCE_SHA256))) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_STALE_SOURCE,
                    "PRECHECK_SOURCE_SHA_MISMATCH",
                    "precheck result source SHA-256 does not match job", started);
            return result;
        }
        if (!job.configurationSha256().equalsIgnoreCase(
                precheckResult.get(BatchPrecheckResult.KEY_CONFIG_SHA256))) {
            result.failure = fail(job, config, flags,
                    BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION,
                    "PRECHECK_CONFIG_SHA_MISMATCH",
                    "precheck result configuration SHA-256 does not match job", started);
            return result;
        }

        if (!Files.isRegularFile(job.precheckCsvPath())) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                    "PRECHECK_CSV_MISSING",
                    "precheck results CSV missing: " + job.precheckCsvPath(), started);
            return result;
        }
        final List<String> csvRow;
        try {
            csvRow = findPrecheckCsvRow(job.precheckCsvPath(), job.imageId().imageId());
        } catch (final Exception ex) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                    "PRECHECK_CSV_UNREADABLE", ex.getMessage(), started);
            return result;
        }
        if (csvRow == null) {
            result.failure = fail(job, config, flags, BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                    "PRECHECK_CSV_ROW_MISSING",
                    "no precheck CSV row for image " + job.imageId().imageId(), started);
            return result;
        }
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(BatchPrecheckCsvSchema.HEADER);
        final String mismatch = firstCsvResultMismatch(csvRow, idx, precheckResult);
        if (mismatch != null) {
            result.failure = fail(job, config, flags,
                    BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION,
                    "PRECHECK_CSV_RESULT_MISMATCH", mismatch, started);
            return result;
        }

        result.precheckResult = precheckResult;
        return result;
    }

    private static List<String> findPrecheckCsvRow(
            final Path csvPath, final String imageId) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(csvPath);
        if (table.isEmpty() || !table.get(0).equals(BatchPrecheckCsvSchema.HEADER)) {
            throw new IOException("precheck CSV header mismatch: " + csvPath);
        }
        final int imageIdIndex = BatchPrecheckCsvSchema.HEADER.indexOf("image_id");
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (imageId.equals(row.get(imageIdIndex))) {
                return row;
            }
        }
        return null;
    }

    private static String firstCsvResultMismatch(
            final List<String> row,
            final Map<String, Integer> idx,
            final BatchPrecheckResult result) {
        if (!mismatchCheck(row, idx, "image_id", result.get(BatchPrecheckResult.KEY_IMAGE_ID))) {
            return "image_id disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "machine_id", result.get(BatchPrecheckResult.KEY_MACHINE_ID))) {
            return "machine_id disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheckIgnoreCase(row, idx, "source_sha256",
                result.get(BatchPrecheckResult.KEY_SOURCE_SHA256))) {
            return "source_sha256 disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheckIgnoreCase(row, idx, "configuration_sha256",
                result.get(BatchPrecheckResult.KEY_CONFIG_SHA256))) {
            return "configuration_sha256 disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "status", result.status().name())) {
            return "status disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "crop_x_px", result.get(BatchPrecheckResult.KEY_CROP_X))) {
            return "crop_x_px disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "crop_y_px", result.get(BatchPrecheckResult.KEY_CROP_Y))) {
            return "crop_y_px disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "crop_width_px", result.get(BatchPrecheckResult.KEY_CROP_W))) {
            return "crop_width_px disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "crop_height_px", result.get(BatchPrecheckResult.KEY_CROP_H))) {
            return "crop_height_px disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "mask_checksum", result.get(BatchPrecheckResult.KEY_MASK_CHECKSUM))) {
            return "mask_checksum disagreement between precheck CSV row and result.properties";
        }
        if (!mismatchCheck(row, idx, "scale_bar_detection_method",
                result.get(BatchPrecheckResult.KEY_SB_METHOD))) {
            return "scale_bar_detection_method disagreement between precheck CSV row and "
                    + "result.properties";
        }
        if (!mismatchCheck(row, idx, "pixel_size_um", result.get(BatchPrecheckResult.KEY_PX_SIZE))) {
            return "pixel_size_um disagreement between precheck CSV row and result.properties";
        }
        return null;
    }

    private static boolean mismatchCheck(
            final List<String> row, final Map<String, Integer> idx,
            final String column, final String expected) {
        final Integer i = idx.get(column);
        if (i == null) {
            return true;
        }
        final String actual = row.get(i.intValue());
        return actual == null ? expected == null : actual.equals(expected);
    }

    private static boolean mismatchCheckIgnoreCase(
            final List<String> row, final Map<String, Integer> idx,
            final String column, final String expected) {
        final Integer i = idx.get(column);
        if (i == null) {
            return true;
        }
        final String actual = row.get(i.intValue());
        return actual == null ? expected == null : actual.equalsIgnoreCase(expected);
    }

    private static int parseIntStrict(final String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("expected integer precheck value, found: " + raw, ex);
        }
    }

    private static double parseDoubleStrict(final String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("expected numeric precheck value, found: " + raw, ex);
        }
    }

    private static BatchFullProcessingResult classificationFailure(
            final BatchFullProcessingJob job,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final IOException ex,
            final long started) {
        final String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.toLowerCase(Locale.ROOT).contains("conflict")) {
            return fail(job, config, flags, BatchFullProcessingStatus.CANONICAL_CONFLICT,
                    "CANONICAL_CONFLICT", message, started);
        }
        if (containsOutOfMemory(ex)) {
            flags.outOfMemoryDetected = Boolean.TRUE;
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                    "CLASSIFICATION_OUT_OF_MEMORY", message, started);
        }
        return fail(job, config, flags, BatchFullProcessingStatus.FAILED_CLASSIFICATION_SAVE,
                "CLASSIFICATION_SAVE_FAILED", message, started);
    }

    private static BatchFullProcessingResult reconstructionFailure(
            final BatchFullProcessingJob job,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final IOException ex,
            final long started) {
        final String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.toLowerCase(Locale.ROOT).contains("conflict")) {
            return fail(job, config, flags, BatchFullProcessingStatus.CANONICAL_CONFLICT,
                    "CANONICAL_CONFLICT", message, started);
        }
        if (containsOutOfMemory(ex)) {
            flags.outOfMemoryDetected = Boolean.TRUE;
            return fail(job, config, flags, BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                    "RECONSTRUCTION_OUT_OF_MEMORY", message, started);
        }
        return fail(job, config, flags, BatchFullProcessingStatus.FAILED_RECONSTRUCTION_SAVE,
                "RECONSTRUCTION_SAVE_FAILED", message, started);
    }

    private static String joinFailures(final List<String> failures) {
        final StringBuilder sb = new StringBuilder("production output validation failed: ");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(failures.get(i));
        }
        return sb.toString();
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

    private static BatchFullProcessingResult.Builder baseBuilder(
            final BatchFullProcessingJob job,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final BatchFullProcessingStatus status,
            final String reason,
            final String message,
            final long started) {
        final BatchFullProcessingResult.Builder b = BatchFullProcessingResult.builder()
                .status(status)
                .reasonCode(reason)
                .message(message)
                .precheckVerified(flags.precheckVerified)
                .maskReproduced(flags.maskReproduced)
                .scaleBarReproduced(flags.scaleBarReproduced)
                .classificationCompleted(flags.classificationCompleted)
                .reconstructionCompleted(flags.reconstructionCompleted)
                .requiredOutputsValidated(flags.requiredOutputsValidated)
                .sourceUnchanged(flags.sourceUnchanged)
                .outOfMemoryDetected(flags.outOfMemoryDetected)
                .processingTimeMs(System.currentTimeMillis() - started);
        if (job != null) {
            b.imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .sourceFilename(job.sourcePath().getFileName().toString())
                    .sourceSha256(job.sourceSha256())
                    .classifierFilename(job.classifierPath().getFileName().toString())
                    .classifierSha256(job.classifierSha256())
                    .attemptNumber(Integer.valueOf(job.attemptNumber()))
                    .tileProfile(
                            Integer.valueOf(job.tileProfileIndex()),
                            Integer.valueOf(job.tileProfile().widthPx()),
                            Integer.valueOf(job.tileProfile().heightPx()))
                    .datasetRoot(job.datasetRoot().toString());
        }
        if (config != null) {
            b.configurationSha256(config.sha256());
        } else if (job != null) {
            b.configurationSha256(job.configurationSha256());
        }
        return b;
    }

    private static BatchFullProcessingStageResult toStageResult(
            final BatchFullProcessingStage stage,
            final BatchFullProcessingJob job,
            final BatchFullProcessingResult result) {
        final BatchFullProcessingStageResult.Builder builder = BatchFullProcessingStageResult.builder()
                .stage(stage)
                .status(result.status())
                .reasonCode(result.get(BatchFullProcessingResult.KEY_REASON))
                .message(result.get(BatchFullProcessingResult.KEY_MESSAGE))
                .outOfMemoryDetected(parseTrueFalse(result.get(
                        BatchFullProcessingResult.KEY_OUT_OF_MEMORY_DETECTED)))
                .requiredOutputsValidated(parseTrueFalse(result.get(
                        BatchFullProcessingResult.KEY_REQUIRED_OUTPUTS_VALIDATED)))
                .sourceUnchanged(parseTrueFalse(result.get(
                        BatchFullProcessingResult.KEY_SOURCE_UNCHANGED)))
                .processingTimeMs(parseLongOrNull(result.get(BatchFullProcessingResult.KEY_TIME_MS)));

        if (job != null) {
            builder.imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .attemptNumber(Integer.valueOf(job.attemptNumber()));
        } else {
            builder.imageId(result.get(BatchFullProcessingResult.KEY_IMAGE_ID))
                    .machineId(result.get(BatchFullProcessingResult.KEY_MACHINE_ID))
                    .attemptNumber(parseIntOrNull(result.get(
                            BatchFullProcessingResult.KEY_ATTEMPT_NUMBER)));
        }

        if (stage == BatchFullProcessingStage.CLASSIFICATION) {
            final boolean completed = RCsvSupport.TRUE.equals(
                    result.get(BatchFullProcessingResult.KEY_CLASSIFICATION_COMPLETED));
            builder.stageCompleted(Boolean.valueOf(completed))
                    .runId(result.get(BatchFullProcessingResult.KEY_CLASSIFICATION_RUN_ID))
                    .primaryOutputPath(absoluteUnderDataset(
                            job == null ? null : job.datasetRoot(),
                            result.get(BatchFullProcessingResult.KEY_MASKED_CLASSIFIED_PATH)));
            // Classification-stage component counts are always NA.
        } else {
            final boolean completed = RCsvSupport.TRUE.equals(
                    result.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_COMPLETED));
            builder.stageCompleted(Boolean.valueOf(completed))
                    .runId(result.get(BatchFullProcessingResult.KEY_RECONSTRUCTION_RUN_ID))
                    .primaryOutputPath(absoluteUnderDataset(
                            job == null ? null : job.datasetRoot(),
                            result.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_CLASSIFIED_PATH)))
                    .totalComponents(parseIntOrNull(
                            result.get(BatchFullProcessingResult.KEY_TOTAL_COMPONENTS)))
                    .eligibleComponents(parseIntOrNull(
                            result.get(BatchFullProcessingResult.KEY_ELIGIBLE_COMPONENTS)))
                    .reconstructedComponents(parseIntOrNull(
                            result.get(BatchFullProcessingResult.KEY_RECONSTRUCTED_COMPONENTS)))
                    .changedPixels(parseIntOrNull(
                            result.get(BatchFullProcessingResult.KEY_CHANGED_PIXELS)));
        }
        return builder.build();
    }

    private static BatchFullProcessingStageResult stageFail(
            final BatchFullProcessingJob job,
            final BatchFullProcessingStage stage,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final BatchFullProcessingStatus status,
            final String reason,
            final String message,
            final long started) {
        return toStageResult(stage, job, fail(job, config, flags, status, reason, message, started));
    }

    private static Boolean parseTrueFalse(final String raw) {
        if (RCsvSupport.TRUE.equals(raw)) {
            return Boolean.TRUE;
        }
        if (RCsvSupport.FALSE.equals(raw)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseIntOrNull(final String raw) {
        if (raw == null || raw.trim().isEmpty() || RCsvSupport.NA.equals(raw.trim())) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLongOrNull(final String raw) {
        if (raw == null || raw.trim().isEmpty() || RCsvSupport.NA.equals(raw.trim())) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(raw.trim()));
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static String absoluteUnderDataset(final Path datasetRoot, final String relativeOrAbsolute) {
        if (relativeOrAbsolute == null
                || relativeOrAbsolute.trim().isEmpty()
                || RCsvSupport.NA.equals(relativeOrAbsolute.trim())) {
            return RCsvSupport.NA;
        }
        final Path raw = Paths.get(relativeOrAbsolute.trim());
        if (raw.isAbsolute()) {
            return raw.normalize().toString();
        }
        if (datasetRoot == null) {
            return raw.normalize().toString();
        }
        return datasetRoot.resolve(raw).toAbsolutePath().normalize().toString();
    }

    private static BatchFullProcessingResult fail(
            final BatchFullProcessingJob job,
            final BatchConfiguration config,
            final ProgressFlags flags,
            final BatchFullProcessingStatus status,
            final String reason,
            final String message,
            final long started) {
        return baseBuilder(job, config, flags, status, reason, message, started).build();
    }

    private static void closeAllImages(
            final ImagePlus source,
            final SampleMaskGenerationResult maskResult,
            final ImagePlus classifiedLabels,
            final ImagePlus maskedClassifiedImage) {
        try {
            if (source != null) {
                source.changes = false;
                source.close();
            }
        } catch (final Exception ignored) {
            // continue cleanup
        }
        try {
            if (classifiedLabels != null) {
                classifiedLabels.changes = false;
                classifiedLabels.close();
            }
        } catch (final Exception ignored) {
            // continue cleanup
        }
        try {
            if (maskedClassifiedImage != null) {
                maskedClassifiedImage.changes = false;
                maskedClassifiedImage.close();
            }
        } catch (final Exception ignored) {
            // continue cleanup
        }
        if (maskResult != null) {
            try {
                if (maskResult.getRgbPreview() != null) {
                    maskResult.getRgbPreview().changes = false;
                    maskResult.getRgbPreview().close();
                }
            } catch (final Exception ignored) {
                // continue
            }
            try {
                if (maskResult.getEditableMaskImage() != null) {
                    maskResult.getEditableMaskImage().changes = false;
                    maskResult.getEditableMaskImage().close();
                }
            } catch (final Exception ignored) {
                // continue
            }
        }
        try {
            final int[] ids = WindowManager.getIDList();
            if (ids != null) {
                for (int i = 0; i < ids.length; i++) {
                    final ImagePlus imp = WindowManager.getImage(ids[i]);
                    if (imp != null) {
                        imp.changes = false;
                        imp.close();
                    }
                }
            }
        } catch (final Exception ignored) {
            // best effort
        }
    }
}
