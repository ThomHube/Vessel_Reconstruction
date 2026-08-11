package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.Opener;
import ij.process.ColorProcessor;

import java.nio.file.Path;

/**
 * One-image Phase-2 precheck processor. ImageJ-dependent; no dialogs; no Weka.
 */
public final class BatchPrecheckProcessor {

    public BatchPrecheckResult process(final BatchPrecheckJob job, final BatchConfiguration config) {
        final long started = System.currentTimeMillis();
        ImagePlus source = null;
        SampleMaskGenerationResult maskResult = null;
        try {
            if (job == null) {
                return fail(null, config, BatchPrecheckStatus.INVALID_CONFIGURATION,
                        "JOB_NULL", "job must not be null", started);
            }
            if (config == null) {
                return fail(job, null, BatchPrecheckStatus.INVALID_CONFIGURATION,
                        "CONFIG_NULL", "configuration must not be null", started);
            }
            if (!config.sha256().equalsIgnoreCase(job.configurationSha256())) {
                return fail(job, config, BatchPrecheckStatus.INVALID_CONFIGURATION,
                        "CONFIG_SHA_MISMATCH", "configuration SHA-256 mismatch", started);
            }

            try {
                CoreSegmentId.parse(
                        job.sourcePath().getFileName().toString().replaceAll("(?i)\\.tiff?$", ""));
            } catch (final RuntimeException ex) {
                return fail(job, config, BatchPrecheckStatus.INVALID_FILENAME,
                        "INVALID_FILENAME", ex.getMessage(), started);
            }

            try {
                final String actualSha = Sha256Files.sha256OfFile(job.sourcePath());
                if (!actualSha.equalsIgnoreCase(job.sourceSha256())) {
                    return fail(job, config, BatchPrecheckStatus.STALE_SOURCE,
                            "STALE_SOURCE", "source SHA-256 mismatch before open", started);
                }
            } catch (final Exception ex) {
                return fail(job, config, BatchPrecheckStatus.INVALID_SOURCE,
                        "SOURCE_UNREADABLE", ex.getMessage(), started);
            }

            try {
                source = new Opener().openImage(job.sourcePath().toString());
            } catch (final Exception ex) {
                return fail(job, config, BatchPrecheckStatus.FAILED_OPEN_IMAGE,
                        "OPEN_FAILED", ex.getMessage(), started);
            }
            if (source == null) {
                return fail(job, config, BatchPrecheckStatus.FAILED_OPEN_IMAGE,
                        "OPEN_NULL", "Opener returned null", started);
            }
            if (!(source.getProcessor() instanceof ColorProcessor)
                    && source.getType() != ImagePlus.COLOR_RGB) {
                return fail(job, config, BatchPrecheckStatus.INVALID_SOURCE,
                        "NOT_RGB", "source must be RGB", started);
            }
            final int width = source.getWidth();
            final int height = source.getHeight();
            if (width <= 0 || height <= 0) {
                return fail(job, config, BatchPrecheckStatus.INVALID_SOURCE,
                        "NON_POSITIVE_DIMENSIONS", "source dimensions must be positive", started);
            }

            final int checksumBefore = TiledWekaClassifier.checksumSource(source);

            try {
                maskResult = new AutomaticSampleMaskGenerator().generate(
                        source,
                        config.maskDownsampleFactor(),
                        config.maskExpansionMarginPx());
            } catch (final SampleMaskGenerationException ex) {
                return base(job, config, BatchPrecheckStatus.FAILED_MASK_GENERATION,
                        "MASK_GENERATION_FAILED", ex.getMessage(), width, height,
                        checksumBefore, null, started)
                        .build();
            }

            final AnalysisCropGeometry crop = AnalysisCropGeometry.fromApprovedMask(
                    maskResult.getMask(), config.maskCropPaddingPx());
            final BatchMaskAcceptancePolicy.Decision maskDecision =
                    BatchMaskAcceptancePolicy.evaluate(
                            maskResult, crop, width, height, config);
            if (!maskDecision.accepted) {
                savePreviewsSafe(job, maskResult, source, null, maskDecision, null, false);
                return filledMask(job, config, BatchPrecheckStatus.REVIEW_REQUIRED_MASK,
                        maskDecision, maskResult, crop, width, height, checksumBefore, null, started)
                        .build();
            }

            ScaleBarDetection detection;
            try {
                detection = new AutomaticScaleBarDetector().detect(
                        source,
                        maskResult.getMask(),
                        (ColorProcessor) maskResult.getRgbPreview().getProcessor());
            } catch (final RuntimeException ex) {
                return filledMask(job, config, BatchPrecheckStatus.FAILED_SCALE_BAR_DETECTION,
                        BatchMaskAcceptancePolicy.Decision.accept(
                                maskDecision.majorSpanFraction,
                                maskDecision.secondaryComponentAreaFraction),
                        maskResult, crop, width, height, checksumBefore, null, started)
                        .reasonCode("SCALE_BAR_DETECTION_EXCEPTION")
                        .message(ex.getMessage())
                        .build();
            }

            final BatchScaleBarAcceptancePolicy.Decision scaleDecision =
                    BatchScaleBarAcceptancePolicy.evaluate(
                            detection, maskResult.getMask(), config);

            final int checksumAfter = TiledWekaClassifier.checksumSource(source);
            final boolean unchanged = checksumBefore == checksumAfter;
            if (!unchanged) {
                return filledMask(job, config, BatchPrecheckStatus.INVALID_SOURCE,
                        maskDecision, maskResult, crop, width, height,
                        checksumBefore, Integer.valueOf(checksumAfter), started)
                        .reasonCode("SOURCE_CHANGED")
                        .message("source RGB pixels changed during precheck")
                        .sourceChecksums(Integer.valueOf(checksumBefore),
                                Integer.valueOf(checksumAfter), Boolean.FALSE)
                        .scaleBar(
                                detection.getMethod().name(),
                                detection.getConfidence().name(),
                                detection.getScore(),
                                detection.getMeasuredLengthPx(),
                                detection.getOuterComponentWidthPx(),
                                detection.getShaftContinuity(),
                                scaleDecision.pixelSizeUm,
                                scaleDecision.relativeDeviation)
                        .build();
            }

            try {
                BatchPrecheckPreviewWriter.writeMaskPreview(
                        job.maskPreviewPath(),
                        maskResult.getRgbPreview(),
                        maskResult.getMask(),
                        crop,
                        job.imageId().imageId(),
                        true,
                        RCsvSupport.NA);
                BatchPrecheckPreviewWriter.writeScaleBarPreview(
                        job.scaleBarPreviewPath(),
                        source,
                        detection,
                        job.imageId().imageId(),
                        scaleDecision.accepted,
                        scaleDecision.reasonCode,
                        scaleDecision.pixelSizeUm);
            } catch (final Exception ex) {
                return filledMask(job, config, BatchPrecheckStatus.FAILED_PREVIEW_SAVE,
                        maskDecision, maskResult, crop, width, height,
                        checksumBefore, Integer.valueOf(checksumAfter), started)
                        .reasonCode("PREVIEW_SAVE_FAILED")
                        .message(ex.getMessage())
                        .sourceChecksums(Integer.valueOf(checksumBefore),
                                Integer.valueOf(checksumAfter), Boolean.TRUE)
                        .scaleBar(
                                detection.getMethod().name(),
                                detection.getConfidence().name(),
                                detection.getScore(),
                                detection.getMeasuredLengthPx(),
                                detection.getOuterComponentWidthPx(),
                                detection.getShaftContinuity(),
                                scaleDecision.pixelSizeUm,
                                scaleDecision.relativeDeviation)
                        .build();
            }

            final BatchPrecheckStatus status = scaleDecision.accepted
                    ? BatchPrecheckStatus.PRECHECK_SUCCESS
                    : BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR;
            return filledMask(job, config, status, maskDecision, maskResult, crop,
                    width, height, checksumBefore, Integer.valueOf(checksumAfter), started)
                    .reasonCode(scaleDecision.accepted ? RCsvSupport.NA : scaleDecision.reasonCode)
                    .message(scaleDecision.message)
                    .sourceChecksums(Integer.valueOf(checksumBefore),
                            Integer.valueOf(checksumAfter), Boolean.TRUE)
                    .scaleBar(
                            detection.getMethod().name(),
                            detection.getConfidence().name(),
                            detection.getScore(),
                            detection.getMeasuredLengthPx(),
                            detection.getOuterComponentWidthPx(),
                            detection.getShaftContinuity(),
                            scaleDecision.pixelSizeUm,
                            scaleDecision.relativeDeviation)
                    .previews(
                            relativeToBatch(job.batchRoot(), job.maskPreviewPath()),
                            relativeToBatch(job.batchRoot(), job.scaleBarPreviewPath()))
                    .build();
        } catch (final Exception ex) {
            return fail(job, config, BatchPrecheckStatus.FAILED_UNEXPECTED,
                    "UNEXPECTED", ex.getMessage(), started);
        } finally {
            closeAllImages(source, maskResult);
        }
    }

    private static void savePreviewsSafe(
            final BatchPrecheckJob job,
            final SampleMaskGenerationResult maskResult,
            final ImagePlus source,
            final ScaleBarDetection detection,
            final BatchMaskAcceptancePolicy.Decision maskDecision,
            final BatchScaleBarAcceptancePolicy.Decision scaleDecision,
            final boolean maskAccepted) {
        try {
            if (maskResult != null) {
                final AnalysisCropGeometry crop = AnalysisCropGeometry.fromApprovedMask(
                        maskResult.getMask(), 0);
                BatchPrecheckPreviewWriter.writeMaskPreview(
                        job.maskPreviewPath(),
                        maskResult.getRgbPreview(),
                        maskResult.getMask(),
                        crop,
                        job.imageId().imageId(),
                        maskAccepted,
                        maskDecision == null ? RCsvSupport.NA : maskDecision.reasonCode);
            }
            if (source != null) {
                BatchPrecheckPreviewWriter.writeScaleBarPreview(
                        job.scaleBarPreviewPath(),
                        source,
                        detection,
                        job.imageId().imageId(),
                        scaleDecision != null && scaleDecision.accepted,
                        scaleDecision == null ? RCsvSupport.NA : scaleDecision.reasonCode,
                        scaleDecision == null ? Double.NaN : scaleDecision.pixelSizeUm);
            }
        } catch (final Exception ignored) {
            // preview failure handled by caller paths that require previews
        }
    }

    private static BatchPrecheckResult.Builder filledMask(
            final BatchPrecheckJob job,
            final BatchConfiguration config,
            final BatchPrecheckStatus status,
            final BatchMaskAcceptancePolicy.Decision maskDecision,
            final SampleMaskGenerationResult maskResult,
            final AnalysisCropGeometry crop,
            final int width,
            final int height,
            final int checksumBefore,
            final Integer checksumAfter,
            final long started) {
        final SampleMaskStatistics stats = maskResult.getStatistics();
        return base(job, config, status,
                maskDecision.reasonCode,
                maskDecision.message,
                width,
                height,
                checksumBefore,
                checksumAfter,
                started)
                .maskStats(
                        Integer.valueOf(stats.getAutomaticThreshold()),
                        Integer.valueOf(stats.getCandidateComponentCount()),
                        stats.getAutomaticIncludedFraction(),
                        maskDecision.majorSpanFraction,
                        Long.valueOf(stats.getSelectedComponentAreaMaskPixels()),
                        maskDecision.secondaryComponentAreaFraction,
                        Integer.valueOf(stats.getAutomaticMaskChecksum()))
                .crop(
                        Integer.valueOf(crop.getCropXPx()),
                        Integer.valueOf(crop.getCropYPx()),
                        Integer.valueOf(crop.getCropWidthPx()),
                        Integer.valueOf(crop.getCropHeightPx()))
                .previews(
                        relativeToBatch(job.batchRoot(), job.maskPreviewPath()),
                        relativeToBatch(job.batchRoot(), job.scaleBarPreviewPath()));
    }

    private static BatchPrecheckResult.Builder base(
            final BatchPrecheckJob job,
            final BatchConfiguration config,
            final BatchPrecheckStatus status,
            final String reason,
            final String message,
            final int width,
            final int height,
            final int checksumBefore,
            final Integer checksumAfter,
            final long started) {
        final BatchPrecheckResult.Builder b = BatchPrecheckResult.builder()
                .status(status)
                .reasonCode(reason)
                .message(message)
                .processingTimeMs(System.currentTimeMillis() - started);
        if (job != null) {
            b.imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .sourceFilename(job.sourcePath().getFileName().toString())
                    .sourceRelativePath(job.sourceRelativePath())
                    .sourceSha256(job.sourceSha256())
                    .previews(
                            relativeToBatch(job.batchRoot(), job.maskPreviewPath()),
                            relativeToBatch(job.batchRoot(), job.scaleBarPreviewPath()));
        }
        if (config != null) {
            b.configurationSha256(config.sha256());
        } else if (job != null) {
            b.configurationSha256(job.configurationSha256());
        }
        if (width > 0 && height > 0) {
            b.imageSize(width, height);
        }
        if (checksumAfter == null) {
            b.sourceChecksums(Integer.valueOf(checksumBefore), null, null);
        } else {
            b.sourceChecksums(Integer.valueOf(checksumBefore), checksumAfter,
                    Boolean.valueOf(checksumBefore == checksumAfter.intValue()));
        }
        return b;
    }

    private static BatchPrecheckResult fail(
            final BatchPrecheckJob job,
            final BatchConfiguration config,
            final BatchPrecheckStatus status,
            final String reason,
            final String message,
            final long started) {
        return base(job, config, status, reason, message, -1, -1, 0, null, started).build();
    }

    static String relativeToBatch(final Path batchRoot, final Path absolute) {
        if (batchRoot == null || absolute == null) {
            return RCsvSupport.NA;
        }
        try {
            return batchRoot.toAbsolutePath().normalize()
                    .relativize(absolute.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (final RuntimeException ex) {
            return absolute.toString().replace('\\', '/');
        }
    }

    private static void closeAllImages(
            final ImagePlus source,
            final SampleMaskGenerationResult maskResult) {
        try {
            if (source != null) {
                source.changes = false;
                source.close();
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
