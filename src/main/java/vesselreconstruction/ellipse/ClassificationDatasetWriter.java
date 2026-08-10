package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Saves tiled-classification production outputs into {@link ProductionDatasetLayout}.
 */
public final class ClassificationDatasetWriter {

    public static final class PreClassificationArtifacts {
        public final String runId;
        public final String timestamp;
        public final String safeSampleId;
        public final String safeSegmentId;
        /** Original source TIFF filename only (not the canonical dataset copy name). */
        public final String originalSourceFilename;
        public final Path originalRgb;
        public final Path approvedMask;
        public final Path maskedRgb;
        public final String originalRgbSha256;
        public final String approvedMaskSha256;
        public final String maskedRgbSha256;
        public final Path runLog;
        public final ProductionStageLogger logger;

        public PreClassificationArtifacts(
                final String runId,
                final String timestamp,
                final String safeSampleId,
                final String safeSegmentId,
                final String originalSourceFilename,
                final Path originalRgb,
                final Path approvedMask,
                final Path maskedRgb,
                final String originalRgbSha256,
                final String approvedMaskSha256,
                final String maskedRgbSha256,
                final Path runLog,
                final ProductionStageLogger logger) {
            this.runId = runId;
            this.timestamp = timestamp;
            this.safeSampleId = safeSampleId;
            this.safeSegmentId = safeSegmentId;
            this.originalSourceFilename = originalSourceFilename;
            this.originalRgb = originalRgb;
            this.approvedMask = approvedMask;
            this.maskedRgb = maskedRgb;
            this.originalRgbSha256 = originalRgbSha256;
            this.approvedMaskSha256 = approvedMaskSha256;
            this.maskedRgbSha256 = maskedRgbSha256;
            this.runLog = runLog;
            this.logger = logger;
        }
    }

    public static final class Result {
        public final String runId;
        public final String timestamp;
        public final Path originalRgb;
        public final Path approvedMask;
        public final Path maskedRgb;
        public final Path maskedClassified;
        public final String originalRgbSha256;
        public final String approvedMaskSha256;
        public final String maskedRgbSha256;
        public final String maskedClassifiedSha256;
        public final Path runLog;

        public Result(
                final String runId,
                final String timestamp,
                final Path originalRgb,
                final Path approvedMask,
                final Path maskedRgb,
                final Path maskedClassified,
                final String originalRgbSha256,
                final String approvedMaskSha256,
                final String maskedRgbSha256,
                final String maskedClassifiedSha256,
                final Path runLog) {
            this.runId = runId;
            this.timestamp = timestamp;
            this.originalRgb = originalRgb;
            this.approvedMask = approvedMask;
            this.maskedRgb = maskedRgb;
            this.maskedClassified = maskedClassified;
            this.originalRgbSha256 = originalRgbSha256;
            this.approvedMaskSha256 = approvedMaskSha256;
            this.maskedRgbSha256 = maskedRgbSha256;
            this.maskedClassifiedSha256 = maskedClassifiedSha256;
            this.runLog = runLog;
        }
    }

    /**
     * Saves original RGB, approved mask, and masked RGB before Weka starts.
     * Closes and releases the temporary masked RGB ImagePlus before returning.
     */
    public PreClassificationArtifacts savePreClassificationArtifacts(
            final ImagePlus sourceImage,
            final File originalSourceFile,
            final DownsampledSampleMask approvedMask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final ScaleBarDetection scaleBarDetection,
            final ProductionDatasetLayout layout) throws IOException {
        if (originalSourceFile == null || !originalSourceFile.isFile()) {
            throw new IOException("Original source TIFF path is required for production save.");
        }
        layout.ensureDirectoriesExist();
        final String safeSample = FilenameSanitizer.sanitizeForFilename(configuration.getSampleId());
        final String safeSegment = FilenameSanitizer.sanitizeForFilename(configuration.getSegmentId());
        final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());
        final String runId = FilenameSanitizer.runId(
                safeSample, safeSegment,
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name(),
                timestamp);

        final Path logPath = layout.logs().resolve(runId + ".log");
        final ProductionStageLogger logger = new ProductionStageLogger(logPath);
        logger.log("TILED_CLASSIFICATION pre-Weka production save started");
        logger.log("Dataset root: " + layout.getDatasetRoot());
        logger.log("Run ID: " + runId);

        final long maskedRgbBytes = ((long) crop.getCropWidthPx())
                * crop.getCropHeightPx() * 4L;
        final long free = Runtime.getRuntime().freeMemory()
                + (Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory());
        logger.log(String.format(Locale.US,
                "Masked RGB estimate: %d bytes; approx free heap: %d",
                Long.valueOf(maskedRgbBytes), Long.valueOf(free)));
        if (free > 0L && maskedRgbBytes > free) {
            try {
                logger.close();
            } catch (final IOException ignored) {
                // ignore
            }
            throw new IOException(
                    "Insufficient heap for required masked RGB crop (~"
                            + maskedRgbBytes + " bytes). Aborting before Weka classification. "
                            + "Increase Fiji heap or reduce crop size.");
        }

        final Path originalPath = layout.imagesOriginalRgb().resolve(
                FilenameSanitizer.originalRgbFilename(safeSample, safeSegment));
        final String originalSha = Sha256Files.commitCanonicalCopy(
                originalSourceFile.toPath(), originalPath);
        logger.log("Original RGB: " + originalPath + " sha256=" + originalSha);

        final ImagePlus maskImage = createMaskImage(approvedMask, calibration, configuration, layout, runId);
        final Path maskPath = layout.imagesApprovedMasks().resolve(
                FilenameSanitizer.approvedMaskFilename(
                        safeSample, safeSegment, approvedMask.getDownsampleFactor()));
        final String maskSha = commitImagePlusAsTiff(maskImage, maskPath);
        maskImage.close();
        logger.log("Approved mask: " + maskPath + " sha256=" + maskSha);

        final ImagePlus maskedRgb = createMaskedRgb(sourceImage, approvedMask, crop, calibration);
        applyCommonMetadata(maskedRgb, configuration, crop, calibration, layout, runId,
                originalPath.getFileName().toString(),
                maskPath.getFileName().toString(),
                FilenameSanitizer.maskedRgbFilename(safeSample, safeSegment),
                FilenameSanitizer.maskedClassifiedFilename(safeSample, safeSegment),
                null,
                scaleBarDetection,
                null);
        final Path maskedRgbPath = layout.imagesMaskedRgb().resolve(
                FilenameSanitizer.maskedRgbFilename(safeSample, safeSegment));
        final String maskedRgbSha = commitImagePlusAsTiff(maskedRgb, maskedRgbPath);
        maskedRgb.close();
        System.gc();
        logger.log("Masked RGB: " + maskedRgbPath + " sha256=" + maskedRgbSha);
        logger.log("Pre-Weka artifacts committed; starting Weka classification next");
        final String originalSourceFilename = originalSourceFile.getName();
        logger.log("Original source filename for processing_runs: " + originalSourceFilename);

        return new PreClassificationArtifacts(
                runId, timestamp, safeSample, safeSegment, originalSourceFilename,
                originalPath, maskPath, maskedRgbPath,
                originalSha, maskSha, maskedRgbSha, logPath, logger);
    }

    /**
     * Saves masked classified TIFF and commits manifests after successful Weka classification.
     */
    public Result finalizeClassificationStage(
            final PreClassificationArtifacts pre,
            final ImagePlus sourceImage,
            final ImagePlus classifiedLabels,
            final DownsampledSampleMask approvedMask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final TiledWekaClassificationResult classificationResult,
            final File modelFile,
            final ScaleBarDetection scaleBarDetection,
            final ProductionDatasetLayout layout) throws IOException {
        final ProductionStageLogger logger = pre.logger;
        try {
            applyCommonMetadata(classifiedLabels, configuration, crop, calibration, layout, pre.runId,
                    pre.originalRgb.getFileName().toString(),
                    pre.approvedMask.getFileName().toString(),
                    pre.maskedRgb.getFileName().toString(),
                    FilenameSanitizer.maskedClassifiedFilename(pre.safeSampleId, pre.safeSegmentId),
                    null,
                    scaleBarDetection,
                    null);
            PhysicalCalibrationService.applyToImage(classifiedLabels, calibration);
            final Path classifiedPath = layout.imagesMaskedClassified().resolve(
                    FilenameSanitizer.maskedClassifiedFilename(pre.safeSampleId, pre.safeSegmentId));
            final String classifiedSha = commitImagePlusAsTiff(classifiedLabels, classifiedPath);
            logger.log("Masked classified: " + classifiedPath + " sha256=" + classifiedSha);

            final String classifierSha = Sha256Files.sha256OfFile(modelFile.toPath());
            writeImageManifestClassification(
                    layout, configuration, crop, calibration, approvedMask,
                    pre.originalRgb, pre.originalRgbSha256, pre.approvedMask, pre.approvedMaskSha256,
                    pre.maskedRgb, pre.maskedRgbSha256, classifiedPath, classifiedSha,
                    modelFile, classifierSha, pre.runId, pre.timestamp, sourceImage);
            writeSegmentManifest(layout, configuration);
            writeProcessingRunSuccess(
                    layout, pre.runId, pre.timestamp, configuration, modelFile,
                    pre.originalSourceFilename,
                    classifiedPath.getFileName().toString(),
                    classificationResult, approvedMask);
            DataDictionaryWriter.writeIdempotent(layout);

            logger.log("TILED_CLASSIFICATION SUCCESS");
            return new Result(
                    pre.runId, pre.timestamp, pre.originalRgb, pre.approvedMask, pre.maskedRgb,
                    classifiedPath,
                    pre.originalRgbSha256, pre.approvedMaskSha256, pre.maskedRgbSha256,
                    classifiedSha, pre.runLog);
        } finally {
            try {
                logger.close();
            } catch (final IOException ignored) {
                // ignore
            }
        }
    }

    static ImagePlus createMaskedRgb(
            final ImagePlus source,
            final DownsampledSampleMask mask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration) {
        final ColorProcessor out = new ColorProcessor(crop.getCropWidthPx(), crop.getCropHeightPx());
        final ImageProcessor src = source.getProcessor();
        for (int y = 0; y < crop.getCropHeightPx(); y++) {
            final int oy = crop.getCropYPx() + y;
            for (int x = 0; x < crop.getCropWidthPx(); x++) {
                final int ox = crop.getCropXPx() + x;
                if (mask.containsOriginalPixel(ox, oy)) {
                    out.set(x, y, src.get(ox, oy));
                } else {
                    out.set(x, y, 0);
                }
            }
        }
        final ImagePlus image = new ImagePlus("masked_rgb", out);
        PhysicalCalibrationService.applyToImage(image, calibration);
        return image;
    }

    private static ImagePlus createMaskImage(
            final DownsampledSampleMask mask,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final ProductionDatasetLayout layout,
            final String runId) {
        final ByteProcessor processor = new ByteProcessor(mask.getMaskWidth(), mask.getMaskHeight());
        final byte[] pixels = mask.copyMaskPixels();
        System.arraycopy(pixels, 0, processor.getPixels(), 0, pixels.length);
        final ImagePlus image = new ImagePlus("approved_mask", processor);
        final ij.measure.Calibration ijCal = new ij.measure.Calibration();
        ijCal.pixelWidth = calibration.getPixelWidthUm() * mask.getDownsampleFactor();
        ijCal.pixelHeight = calibration.getPixelHeightUm() * mask.getDownsampleFactor();
        ijCal.setUnit(PhysicalCalibration.UNIT_MICROMETRE);
        image.setCalibration(ijCal);
        return image;
    }

    static void applyCommonMetadata(
            final ImagePlus image,
            final TiledWekaClassificationConfiguration configuration,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final ProductionDatasetLayout layout,
            final String runId,
            final String originalFilename,
            final String maskFilename,
            final String maskedRgbFilename,
            final String maskedClassifiedFilename,
            final String reconstructedFilename,
            final ScaleBarDetection detection,
            final String reconstructedExtras) {
        final LumenImageMetadata metadata = LumenImageMetadata.create(
                originalFilename,
                layout.getDatasetRoot().toString(),
                configuration.getSampleId(),
                configuration.getSegmentId(),
                configuration.getSegmentOrder(),
                configuration.getSegmentOrientation(),
                crop,
                calibration,
                configuration.getOptionalGlobalSegmentStartUm().orElse(null),
                configuration.getSampleMaskDownsampleFactor(),
                null,
                "",
                "",
                configuration.getCoreTileWidthPixels(),
                configuration.getCoreTileHeightPixels(),
                0,
                null,
                null,
                runId,
                detection);
        final StringBuilder info = new StringBuilder(metadata.serialize());
        info.append('\n').append("lumen.dataset_root=").append(layout.getDatasetRoot());
        info.append('\n').append("lumen.data_schema_version=")
                .append(ProductionSchemas.DATA_SCHEMA_VERSION);
        info.append('\n').append("lumen.processing_run_id=").append(runId);
        info.append('\n').append("lumen.source_rgb_filename=").append(nullToEmpty(originalFilename));
        info.append('\n').append("lumen.approved_mask_filename=").append(nullToEmpty(maskFilename));
        info.append('\n').append("lumen.masked_rgb_filename=").append(nullToEmpty(maskedRgbFilename));
        info.append('\n').append("lumen.masked_classified_filename=")
                .append(nullToEmpty(maskedClassifiedFilename));
        info.append('\n').append("lumen.reconstructed_classified_filename=")
                .append(nullToEmpty(reconstructedFilename));
        info.append('\n').append("lumen.image_manifest_path=")
                .append(layout.imageManifest());
        info.append('\n').append("lumen.segment_manifest_path=")
                .append(layout.segmentManifest());
        if (reconstructedExtras != null && !reconstructedExtras.isEmpty()) {
            info.append('\n').append(reconstructedExtras);
        }
        final Object existing = image.getProperty("Info");
        if (existing != null && !String.valueOf(existing).trim().isEmpty()) {
            image.setProperty("Info", String.valueOf(existing) + "\n" + info);
        } else {
            image.setProperty("Info", info.toString());
        }
    }

    static String commitImagePlusAsTiff(final ImagePlus image, final Path target)
            throws IOException {
        return commitImagePlusAsTiff(image, target, false);
    }

    /**
     * Canonical TIFF commit. When {@code allowReplaceIfPixelsMatch} is true and the
     * existing file differs only in metadata, the file may be atomically replaced.
     * Differing pixel content always conflicts.
     */
    static String commitImagePlusAsTiff(
            final ImagePlus image,
            final Path target,
            final boolean allowReplaceIfPixelsMatch) throws IOException {
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(
                target.getParent(), target.getFileName().toString() + ".", ".tmp.tif");
        try {
            final boolean ok = new FileSaver(image).saveAsTiff(temp.toAbsolutePath().toString());
            if (!ok) {
                throw new IOException("FileSaver.saveAsTiff returned false for " + target);
            }
            final String sha = Sha256Files.sha256OfFile(temp);
            if (Files.exists(target)) {
                final String existing = Sha256Files.sha256OfFile(target);
                if (existing.equals(sha)) {
                    Files.deleteIfExists(temp);
                    return existing;
                }
                if (allowReplaceIfPixelsMatch && pixelsMatchTiff(temp, target)) {
                    // Scientific pixels identical; refresh metadata atomically.
                } else {
                    final String message =
                            "Canonical TIFF conflict (different SHA-256). Refusing to overwrite:\n"
                                    + target
                                    + "\nexisting=" + existing
                                    + "\nintended=" + sha;
                    Files.deleteIfExists(temp);
                    throw new IOException(message);
                }
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return sha;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean pixelsMatchTiff(final Path a, final Path b) {
        final ImagePlus ia = IJ.openImage(a.toAbsolutePath().toString());
        final ImagePlus ib = IJ.openImage(b.toAbsolutePath().toString());
        if (ia == null || ib == null) {
            if (ia != null) {
                ia.close();
            }
            if (ib != null) {
                ib.close();
            }
            return false;
        }
        try {
            if (ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight()) {
                return false;
            }
            if (!(ia.getProcessor() instanceof ByteProcessor)
                    || !(ib.getProcessor() instanceof ByteProcessor)) {
                return false;
            }
            final byte[] pa = (byte[]) ia.getProcessor().getPixels();
            final byte[] pb = (byte[]) ib.getProcessor().getPixels();
            if (pa.length != pb.length) {
                return false;
            }
            for (int i = 0; i < pa.length; i++) {
                if (pa[i] != pb[i]) {
                    return false;
                }
            }
            return true;
        } finally {
            ia.close();
            ib.close();
        }
    }

    private static void writeImageManifestClassification(
            final ProductionDatasetLayout layout,
            final TiledWekaClassificationConfiguration configuration,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final DownsampledSampleMask mask,
            final Path originalPath,
            final String originalSha,
            final Path maskPath,
            final String maskSha,
            final Path maskedRgbPath,
            final String maskedRgbSha,
            final Path classifiedPath,
            final String classifiedSha,
            final File modelFile,
            final String classifierSha,
            final String runId,
            final String timestamp,
            final ImagePlus sourceImage) throws IOException {
        final List<String> row = RCsvSupport.mutableCopy(
                Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION),
                configuration.getSampleId(),
                configuration.getSegmentId(),
                Integer.toString(configuration.getSegmentOrder()),
                configuration.getSegmentOrientation().name(),
                layout.getDatasetRoot().toString(),
                originalPath.getFileName().toString(),
                originalPath.toString(),
                originalSha,
                Integer.toString(sourceImage.getWidth()),
                Integer.toString(sourceImage.getHeight()),
                maskPath.getFileName().toString(),
                maskPath.toString(),
                maskSha,
                Integer.toString(mask.getDownsampleFactor()),
                Integer.toString(mask.getMaskWidth()),
                Integer.toString(mask.getMaskHeight()),
                Integer.toString(mask.getChecksum()),
                maskedRgbPath.getFileName().toString(),
                maskedRgbPath.toString(),
                maskedRgbSha,
                classifiedPath.getFileName().toString(),
                classifiedPath.toString(),
                classifiedSha,
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA,
                Integer.toString(crop.getCropXPx()),
                Integer.toString(crop.getCropYPx()),
                Integer.toString(crop.getCropWidthPx()),
                Integer.toString(crop.getCropHeightPx()),
                RCsvSupport.formatNumber(calibration.getPixelWidthUm()),
                RCsvSupport.formatNumber(calibration.getPixelHeightUm()),
                PhysicalCalibration.UNIT_MICROMETRE,
                calibration.getCalibrationMethod().name(),
                RCsvSupport.numberOrNa(calibration.getScaleBarPhysicalLengthUm()),
                RCsvSupport.numberOrNa(calibration.getScaleBarMeasuredLengthPx()),
                modelFile.getName(),
                modelFile.getAbsolutePath(),
                classifierSha,
                runId,
                RCsvSupport.NA,
                timestamp,
                RCsvSupport.NA);
        ProductionCsvStore.upsertByKeys(
                layout.imageManifest(),
                ProductionSchemas.IMAGE_MANIFEST_HEADER,
                row,
                "sample_id", "segment_id");
    }

    private static void writeSegmentManifest(
            final ProductionDatasetLayout layout,
            final TiledWekaClassificationConfiguration configuration) throws IOException {
        final List<String> row = RCsvSupport.mutableCopy(
                Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION),
                configuration.getSampleId(),
                configuration.getSegmentId(),
                Integer.toString(configuration.getSegmentOrder()),
                configuration.getSegmentOrientation().name(),
                RCsvSupport.FALSE,
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.numberOrNa(
                        configuration.getOptionalGlobalSegmentStartUm().orElse(null)),
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA);
        ProductionCsvStore.upsertByKeys(
                layout.segmentManifest(),
                ProductionSchemas.SEGMENT_MANIFEST_HEADER,
                row,
                "sample_id", "segment_id");
    }

    private static void writeProcessingRunSuccess(
            final ProductionDatasetLayout layout,
            final String runId,
            final String timestamp,
            final TiledWekaClassificationConfiguration configuration,
            final File modelFile,
            final String originalSourceFilename,
            final String outputFilename,
            final TiledWekaClassificationResult classificationResult,
            final DownsampledSampleMask mask) throws IOException {
        // Reconstruction-only columns stay NA. Classification tile/outside-mask counts
        // belong in stage logs and image metadata, never in reconstructed_components
        // or changed_pixels.
        final ProcessingRunRecord record = buildClassificationSuccessRecord(
                runId,
                timestamp,
                configuration.getSampleId(),
                configuration.getSegmentId(),
                originalSourceFilename,
                outputFilename,
                modelFile.getName(),
                configuration.getCoreTileWidthPixels(),
                configuration.getCoreTileHeightPixels(),
                configuration.getThreadsPerTile(),
                mask.getDownsampleFactor(),
                configuration.getSampleMaskExpansionMarginOriginalPixels(),
                classificationResult.getTiledClassificationTimeMs());
        ProcessingRunsManifest.append(
                layout.processingRuns(),
                record,
                ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION);
    }

    /**
     * Builds a TILED_CLASSIFICATION SUCCESS row with reconstruction-only fields as NA.
     */
    static ProcessingRunRecord buildClassificationSuccessRecord(
            final String runId,
            final String timestamp,
            final String sampleId,
            final String segmentId,
            final String originalSourceFilename,
            final String outputFilename,
            final String classifierFilename,
            final int coreTileWidthPx,
            final int coreTileHeightPx,
            final int threadsPerTile,
            final int maskDownsampleFactor,
            final int maskExpansionMarginPx,
            final long processingTimeMs) {
        if (originalSourceFilename == null || originalSourceFilename.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "originalSourceFilename must not be blank for TILED_CLASSIFICATION");
        }
        if (originalSourceFilename.contains("/") || originalSourceFilename.contains("\\")
                || originalSourceFilename.contains("..")) {
            throw new IllegalArgumentException(
                    "originalSourceFilename must be a filename only: " + originalSourceFilename);
        }
        return ProcessingRunRecord.builder()
                .schemaVersion(ProductionSchemas.DATA_SCHEMA_VERSION)
                .runId(runId)
                .timestamp(timestamp)
                .stage(ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION)
                .status(ProductionSchemas.RunStatus.SUCCESS)
                .sampleId(sampleId)
                .segmentId(segmentId)
                .inputFilename(originalSourceFilename.trim())
                .outputFilename(outputFilename)
                .pluginVersion("0.1.0-SNAPSHOT")
                .gitCommit(RCsvSupport.NA)
                .classifierFilename(classifierFilename)
                .coreTileWidthPx(Integer.valueOf(coreTileWidthPx))
                .coreTileHeightPx(Integer.valueOf(coreTileHeightPx))
                .threadsPerTile(Integer.valueOf(threadsPerTile))
                .maskDownsampleFactor(Integer.valueOf(maskDownsampleFactor))
                .maskExpansionMarginPx(Integer.valueOf(maskExpansionMarginPx))
                .minimumLumenAreaPx(null)
                .totalComponents(null)
                .eligibleComponents(null)
                .reconstructedComponents(null)
                .changedPixels(null)
                .processingTimeMs(Double.valueOf(processingTimeMs))
                .message("classification production outputs committed")
                .build();
    }

    public static File resolveOriginalSourceFile(final ImagePlus source) {
        final FileInfo info = source.getOriginalFileInfo();
        if (info != null && info.directory != null && info.fileName != null) {
            final File file = new File(info.directory, info.fileName);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
}
