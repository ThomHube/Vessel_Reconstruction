package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves derived classification outputs, metadata, and authoritative manifests.
 */
public final class DatasetOutputWriter {

    public static final class SavedOutputs {
        public final File classifiedFile;
        public final File approvedMaskFile;
        public final File rgbCropFile;
        public final File perRunManifestFile;
        public final File masterManifestFile;
        public final String runId;
        public final String createdTimestamp;
        public final int outputChecksum;

        public SavedOutputs(
                final File classifiedFile,
                final File approvedMaskFile,
                final File rgbCropFile,
                final File perRunManifestFile,
                final File masterManifestFile,
                final String runId,
                final String createdTimestamp,
                final int outputChecksum) {
            this.classifiedFile = classifiedFile;
            this.approvedMaskFile = approvedMaskFile;
            this.rgbCropFile = rgbCropFile;
            this.perRunManifestFile = perRunManifestFile;
            this.masterManifestFile = masterManifestFile;
            this.runId = runId;
            this.createdTimestamp = createdTimestamp;
            this.outputChecksum = outputChecksum;
        }
    }

    public SavedOutputs saveSuccessfulRun(
            final ImagePlus sourceImage,
            final ImagePlus classifiedLabels,
            final DownsampledSampleMask approvedMask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final TiledWekaClassificationResult classificationResult,
            final File modelFile) throws IOException {
        return saveSuccessfulRun(
                sourceImage,
                classifiedLabels,
                approvedMask,
                crop,
                calibration,
                configuration,
                classificationResult,
                modelFile,
                null);
    }

    public SavedOutputs saveSuccessfulRun(
            final ImagePlus sourceImage,
            final ImagePlus classifiedLabels,
            final DownsampledSampleMask approvedMask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final TiledWekaClassificationResult classificationResult,
            final File modelFile,
            final ScaleBarDetection scaleBarDetection) throws IOException {
        if (!configuration.isSaveCroppedClassifiedTiffAutomatically()) {
            throw new IllegalStateException(
                    "Required classified TIFF save is disabled; cannot mark SUCCESS");
        }
        if (!configuration.isSaveApprovedDownsampledMaskAutomatically()) {
            throw new IllegalStateException(
                    "Required approved mask save is disabled; cannot mark SUCCESS");
        }

        final File outputDir = configuration.getOutputDirectory();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException(
                    "Output directory does not exist and could not be created: "
                            + outputDir.getAbsolutePath());
        }
        if (!outputDir.isDirectory()) {
            throw new IOException(
                    "Output directory is not a directory: " + outputDir.getAbsolutePath());
        }

        final String sourceBase = FilenameSanitizer.stripExtension(safeTitle(sourceImage));
        final String safeSegment = FilenameSanitizer.sanitizeForFilename(
                configuration.getSegmentId());
        final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());
        final String runId = OutputPathAllocator.createRunId(sourceBase, safeSegment, timestamp);

        final String classifiedSuffix = configuration.isCropOutputToApprovedMaskExtent()
                ? "classified_crop.tif"
                : "classified_full.tif";
        final File classifiedFile = OutputPathAllocator.allocateUnique(
                outputDir,
                FilenameSanitizer.sanitizeForFilename(sourceBase)
                        + "__" + safeSegment + "__" + classifiedSuffix);
        final File maskFile = OutputPathAllocator.allocateUnique(
                outputDir,
                FilenameSanitizer.sanitizeForFilename(sourceBase)
                        + "__" + safeSegment + "__approved_mask_ds"
                        + approvedMask.getDownsampleFactor() + ".tif");
        File rgbFile = null;
        if (configuration.isSaveCroppedRgbCopy()) {
            rgbFile = OutputPathAllocator.allocateUnique(
                    outputDir,
                    FilenameSanitizer.sanitizeForFilename(sourceBase)
                            + "__" + safeSegment + "__rgb_crop.tif");
        }
        final File perRunManifest = OutputPathAllocator.allocateUnique(
                outputDir,
                FilenameSanitizer.sanitizeForFilename(sourceBase)
                        + "__" + safeSegment + "__manifest.csv");
        final File masterManifest = new File(outputDir, LumenDatasetManifest.MASTER_FILENAME);

        final int outputChecksum = checksumProcessor(classifiedLabels.getProcessor());
        final String sourcePath = resolveSourcePath(sourceImage);
        final String sourceFilename = sourcePath == null
                ? safeTitle(sourceImage)
                : new File(sourcePath).getName();

        final LumenImageMetadata metadata = LumenImageMetadata.create(
                sourceFilename,
                sourcePath == null ? "" : sourcePath,
                configuration.getSampleId(),
                configuration.getSegmentId(),
                configuration.getSegmentOrder(),
                configuration.getSegmentOrientation(),
                crop,
                calibration,
                configuration.getOptionalGlobalSegmentStartUm().orElse(null),
                approvedMask.getDownsampleFactor(),
                Integer.valueOf(approvedMask.getChecksum()),
                modelFile.getAbsolutePath(),
                modelFile.getName(),
                configuration.getCoreTileWidthPixels(),
                configuration.getCoreTileHeightPixels(),
                classificationResult.getLayout().getClassificationHaloPixels(),
                Integer.valueOf(classificationResult.getSourceChecksumBefore()),
                Integer.valueOf(outputChecksum),
                timestamp,
                scaleBarDetection);

        prepareClassifiedImage(classifiedLabels, calibration, metadata, crop, configuration);
        saveTiff("classified TIFF", classifiedLabels, classifiedFile);

        final ImagePlus maskImage = createMaskImage(approvedMask, calibration, metadata);
        try {
            saveTiff("approved mask", maskImage, maskFile);
        } finally {
            maskImage.close();
        }

        if (rgbFile != null) {
            final ImagePlus rgbCrop = createRgbCrop(sourceImage, crop, calibration, metadata);
            try {
                saveTiff("RGB crop", rgbCrop, rgbFile);
            } finally {
                rgbCrop.close();
            }
        }

        final LumenDatasetManifest.Row row = buildRow(
                runId,
                timestamp,
                sourceImage,
                sourceFilename,
                sourcePath,
                approvedMask,
                crop,
                calibration,
                configuration,
                classificationResult,
                modelFile,
                classifiedFile,
                maskFile,
                rgbFile,
                outputChecksum);
        row.status = LumenDatasetManifest.Status.SUCCESS;

        final LumenDatasetManifest manifest = new LumenDatasetManifest();
        try {
            manifest.writePerRunAtomically(perRunManifest, row);
        } catch (final IOException ex) {
            throw new IOException(
                    "Manifest writing failed for per-run file "
                            + perRunManifest.getAbsolutePath()
                            + ": " + ex.getMessage(),
                    ex);
        }
        try {
            manifest.appendToMaster(masterManifest, row);
        } catch (final IllegalArgumentException ex) {
            throw new IOException(
                    "Manifest header validation failed for "
                            + masterManifest.getAbsolutePath()
                            + ": " + ex.getMessage(),
                    ex);
        } catch (final IOException ex) {
            throw new IOException(
                    "Manifest writing failed for master file "
                            + masterManifest.getAbsolutePath()
                            + ": " + ex.getMessage(),
                    ex);
        }

        IJ.log("Saved classified TIFF: " + classifiedFile.getAbsolutePath());
        IJ.log("Saved approved mask: " + maskFile.getAbsolutePath());
        if (rgbFile != null) {
            IJ.log("Saved RGB crop: " + rgbFile.getAbsolutePath());
        }
        IJ.log("Saved per-run manifest: " + perRunManifest.getAbsolutePath());
        IJ.log("Updated master manifest: " + masterManifest.getAbsolutePath());
        IJ.log("Coordinate schema version: " + LumenImageMetadata.SCHEMA_VERSION);
        IJ.log("Crop-to-original transformation metadata was written");

        return new SavedOutputs(
                classifiedFile,
                maskFile,
                rgbFile,
                perRunManifest,
                masterManifest,
                runId,
                timestamp,
                outputChecksum);
    }

    private static void prepareClassifiedImage(
            final ImagePlus classified,
            final PhysicalCalibration calibration,
            final LumenImageMetadata metadata,
            final AnalysisCropGeometry crop,
            final TiledWekaClassificationConfiguration configuration) {
        PhysicalCalibrationService.applyToImage(classified, calibration);
        classified.setProperty("Info", mergeInfo(classified, metadata.serialize()));
        final String title = configuration.isCropOutputToApprovedMaskExtent()
                ? "Classified crop " + crop.getCropWidthPx() + "x" + crop.getCropHeightPx()
                : "Classified full-size";
        classified.setTitle(title);
    }

    private static ImagePlus createMaskImage(
            final DownsampledSampleMask mask,
            final PhysicalCalibration calibration,
            final LumenImageMetadata metadata) {
        final ByteProcessor processor = new ByteProcessor(
                mask.getMaskWidth(), mask.getMaskHeight());
        final byte[] pixels = mask.copyMaskPixels();
        System.arraycopy(pixels, 0, processor.getPixels(), 0, pixels.length);
        final ImagePlus image = new ImagePlus("Approved downsampled sample mask", processor);
        final ij.measure.Calibration ijCal = image.getCalibration() == null
                ? new ij.measure.Calibration()
                : image.getCalibration().copy();
        ijCal.pixelWidth = calibration.getPixelWidthUm() * mask.getDownsampleFactor();
        ijCal.pixelHeight = calibration.getPixelHeightUm() * mask.getDownsampleFactor();
        ijCal.setUnit(PhysicalCalibration.UNIT_MICROMETRE);
        image.setCalibration(ijCal);
        final String info = metadata.serialize()
                + "\nlumen.mask_is_downsampled=true"
                + "\nlumen.mask_note=Downsampled approved sample mask; "
                + "mask pixel size equals source µm/px times downsample factor";
        image.setProperty("Info", info);
        return image;
    }

    private static ImagePlus createRgbCrop(
            final ImagePlus source,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final LumenImageMetadata metadata) {
        final ImageProcessor sourceProcessor = source.getProcessor();
        sourceProcessor.setRoi(
                crop.getCropXPx(),
                crop.getCropYPx(),
                crop.getCropWidthPx(),
                crop.getCropHeightPx());
        final ImageProcessor cropped = sourceProcessor.crop();
        sourceProcessor.resetRoi();
        final ImagePlus rgb = new ImagePlus("RGB crop", cropped);
        PhysicalCalibrationService.applyToImage(rgb, calibration);
        rgb.setProperty("Info", metadata.serialize());
        return rgb;
    }

    private static void saveTiff(
            final String outputType,
            final ImagePlus image,
            final File path) throws IOException {
        try {
            final boolean ok = new FileSaver(image).saveAsTiff(path.getAbsolutePath());
            if (!ok) {
                throw new IOException("FileSaver.saveAsTiff returned false");
            }
        } catch (final Exception ex) {
            throw new IOException(
                    "Saving failed for output type=" + outputType
                            + ", path=" + path.getAbsolutePath()
                            + ", exception=" + ex.getClass().getName()
                            + ", message=" + (ex.getMessage() == null ? "" : ex.getMessage()),
                    ex);
        }
    }

    private static LumenDatasetManifest.Row buildRow(
            final String runId,
            final String timestamp,
            final ImagePlus sourceImage,
            final String sourceFilename,
            final String sourcePath,
            final DownsampledSampleMask mask,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final TiledWekaClassificationConfiguration configuration,
            final TiledWekaClassificationResult result,
            final File modelFile,
            final File classifiedFile,
            final File maskFile,
            final File rgbFile,
            final int outputChecksum) {
        final long[] counts = result.getTiledClassCounts();
        final LumenDatasetManifest.Row row = new LumenDatasetManifest.Row();
        row.runId = runId;
        row.createdTimestamp = timestamp;
        row.sampleId = configuration.getSampleId();
        row.segmentId = configuration.getSegmentId();
        row.segmentOrder = configuration.getSegmentOrder();
        row.segmentOrientation = configuration.getSegmentOrientation().name();
        row.globalSegmentStartUm = configuration.getOptionalGlobalSegmentStartUm().orElse(null);
        row.sourceFilename = sourceFilename;
        row.sourcePath = sourcePath == null ? "" : sourcePath;
        row.sourceWidthPx = sourceImage.getWidth();
        row.sourceHeightPx = sourceImage.getHeight();
        row.sourceChecksum = Integer.valueOf(result.getSourceChecksumBefore());
        row.classifierFilename = modelFile.getName();
        row.classifierPath = modelFile.getAbsolutePath();
        row.cropEnabled = configuration.isCropOutputToApprovedMaskExtent();
        row.cropXPx = crop.getCropXPx();
        row.cropYPx = crop.getCropYPx();
        row.cropWidthPx = crop.getCropWidthPx();
        row.cropHeightPx = crop.getCropHeightPx();
        row.cropPaddingPx = crop.getCropPaddingPx();
        row.pixelWidthUm = calibration.getPixelWidthUm();
        row.pixelHeightUm = calibration.getPixelHeightUm();
        row.physicalUnit = calibration.getUnit();
        row.calibrationMethod = calibration.getCalibrationMethod().name();
        row.scaleBarValueUm = calibration.getScaleBarPhysicalLengthUm();
        row.scaleBarLengthPx = calibration.getScaleBarMeasuredLengthPx();
        row.sourceMetadataUnit = calibration.getSourceMetadataUnit();
        row.maskDownsampleFactor = mask.getDownsampleFactor();
        row.maskWidthPx = mask.getMaskWidth();
        row.maskHeightPx = mask.getMaskHeight();
        row.maskChecksum = mask.getChecksum();
        row.maskIncludedPixels = mask.getIncludedMaskPixels();
        row.maskIncludedFraction = mask.getIncludedMaskFraction();
        row.coreTileWidthPx = configuration.getCoreTileWidthPixels();
        row.coreTileHeightPx = configuration.getCoreTileHeightPixels();
        row.classificationHaloPx = result.getLayout().getClassificationHaloPixels();
        row.calculatedTilesX = result.getLayout().getTilesX();
        row.calculatedTilesY = result.getLayout().getTilesY();
        row.totalTiles = result.getLayout().getTotalTiles();
        row.classifiedTiles = result.getClassifiedTiles();
        row.skippedTiles = result.getSkippedTiles();
        row.threadsPerTile = configuration.getThreadsPerTile();
        row.seamAuditHalfWidthPx = configuration.getSeamAuditHalfWidthPixels();
        row.runUntiledReferenceComparison = configuration.isRunUntiledReference();
        row.classifiedOutputFilename = classifiedFile.getName();
        row.classifiedOutputPath = classifiedFile.getAbsolutePath();
        row.approvedMaskFilename = maskFile.getName();
        row.approvedMaskPath = maskFile.getAbsolutePath();
        row.rgbCropFilename = rgbFile == null ? "" : rgbFile.getName();
        row.rgbCropPath = rgbFile == null ? "" : rgbFile.getAbsolutePath();
        row.outputWidthPx = crop.getCropWidthPx();
        row.outputHeightPx = crop.getCropHeightPx();
        row.woodPixels = counts[0];
        row.rayPixels = counts[1];
        row.lumenPixels = counts[2];
        row.backgroundPixels = counts[3];
        row.outsideMaskForcedBackgroundPixels = result.getOutsideMaskForcedBackgroundPixels();
        row.outputChecksum = Integer.valueOf(outputChecksum);
        row.sourceUnchanged = result.isSourceUnchanged();
        row.classificationTimeMs = result.getTiledClassificationTimeMs();
        return row;
    }

    private static String mergeInfo(final ImagePlus image, final String lumenBlock) {
        final Object existing = image.getProperty("Info");
        if (existing == null || String.valueOf(existing).trim().isEmpty()) {
            return lumenBlock;
        }
        return String.valueOf(existing) + "\n" + lumenBlock;
    }

    private static String resolveSourcePath(final ImagePlus image) {
        final FileInfo info = image.getOriginalFileInfo();
        if (info == null || info.directory == null || info.fileName == null) {
            return null;
        }
        return new File(info.directory, info.fileName).getAbsolutePath();
    }

    private static String safeTitle(final ImagePlus image) {
        return image.getTitle() == null ? "untitled" : image.getTitle();
    }

    private static int checksumProcessor(final ImageProcessor processor) {
        final int width = processor.getWidth();
        final int height = processor.getHeight();
        int hash = 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hash = 31 * hash + processor.get(x, y);
            }
        }
        return hash;
    }
}
