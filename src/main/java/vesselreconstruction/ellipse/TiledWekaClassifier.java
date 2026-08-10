package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import trainableSegmentation.WekaSegmentation;
import trainableSegmentation.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Non-UI service that classifies a single 2D microscopy image using fixed-pixel
 * core tiles, an approved downsampled sample mask, and the official Trainable
 * Weka Segmentation ROI classifier.
 *
 * <p>Tiles whose approved mask contains no included original pixels are skipped.
 * Classified tiles still receive the normal halo-expanded RGB crop. Only core
 * pixels inside the mask retain Weka labels; all other output pixels are
 * Background class 3. Tiles are never processed in parallel.</p>
 */
public final class TiledWekaClassifier {

    private final DirectWekaLabelImageValidator validator;
    private final ClassificationSeamAuditor seamAuditor;
    private final MaskedLabelAssembler assembler;

    public TiledWekaClassifier() {
        this.validator = new DirectWekaLabelImageValidator();
        this.seamAuditor = new ClassificationSeamAuditor();
        this.assembler = new MaskedLabelAssembler();
    }

    public TiledWekaClassificationResult classify(
            final ImagePlus sourceImage,
            final File modelFile,
            final TiledWekaClassificationConfiguration configuration,
            final DownsampledSampleMask approvedMask,
            final SampleMaskStatistics automaticMaskStatistics,
            final boolean sampleMaskReviewed) {
        final AnalysisCropGeometry crop = configuration.isCropOutputToApprovedMaskExtent()
                ? AnalysisCropGeometry.fromApprovedMask(
                        approvedMask,
                        configuration.getAdditionalCropPaddingOriginalPixels())
                : AnalysisCropGeometry.fullSource(
                        sourceImage.getWidth(), sourceImage.getHeight());
        return classify(
                sourceImage,
                modelFile,
                configuration,
                approvedMask,
                automaticMaskStatistics,
                sampleMaskReviewed,
                crop);
    }

    public TiledWekaClassificationResult classify(
            final ImagePlus sourceImage,
            final File modelFile,
            final TiledWekaClassificationConfiguration configuration,
            final DownsampledSampleMask approvedMask,
            final SampleMaskStatistics automaticMaskStatistics,
            final boolean sampleMaskReviewed,
            final AnalysisCropGeometry cropGeometry) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        if (modelFile == null) {
            throw new IllegalArgumentException("modelFile must not be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        if (approvedMask == null) {
            throw new IllegalArgumentException("approvedMask must not be null");
        }
        if (cropGeometry == null) {
            throw new IllegalArgumentException("cropGeometry must not be null");
        }
        if (approvedMask.isEmpty()) {
            throw new IllegalArgumentException(
                    "Approved sample mask contains no included pixels. "
                            + "Classification was aborted.");
        }
        if (!modelFile.exists()) {
            throw new IllegalArgumentException("modelFile does not exist: " + modelFile);
        }
        if (!modelFile.isFile()) {
            throw new IllegalArgumentException("modelFile is not a regular file: " + modelFile);
        }
        if (!modelFile.canRead()) {
            throw new IllegalArgumentException("modelFile is not readable: " + modelFile);
        }

        validateSourceImage(sourceImage);
        if (approvedMask.getOriginalWidth() != sourceImage.getWidth()
                || approvedMask.getOriginalHeight() != sourceImage.getHeight()) {
            throw new IllegalArgumentException(
                    "approved mask original dimensions must match the source image");
        }
        if (cropGeometry.getOriginalWidthPx() != sourceImage.getWidth()
                || cropGeometry.getOriginalHeightPx() != sourceImage.getHeight()) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Crop geometry source mismatch: crop source=%dx%d, image=%dx%d, "
                            + "mask=%dx%d, factor=%d, rectangle=%s",
                    Integer.valueOf(cropGeometry.getOriginalWidthPx()),
                    Integer.valueOf(cropGeometry.getOriginalHeightPx()),
                    Integer.valueOf(sourceImage.getWidth()),
                    Integer.valueOf(sourceImage.getHeight()),
                    Integer.valueOf(approvedMask.getMaskWidth()),
                    Integer.valueOf(approvedMask.getMaskHeight()),
                    Integer.valueOf(approvedMask.getDownsampleFactor()),
                    cropGeometry.asRectangle()));
        }

        final String canonicalPath;
        try {
            canonicalPath = modelFile.getCanonicalPath();
        } catch (final IOException e) {
            throw new IllegalArgumentException("Unable to resolve canonical model path", e);
        }

        final String sourceTitle = sourceImage.getTitle() == null
                ? "Untitled" : sourceImage.getTitle();
        final int sourceWidth = sourceImage.getWidth();
        final int sourceHeight = sourceImage.getHeight();
        final int outputWidth = cropGeometry.getCropWidthPx();
        final int outputHeight = cropGeometry.getCropHeightPx();
        final int cropOffsetOriginalX = cropGeometry.getCropXPx();
        final int cropOffsetOriginalY = cropGeometry.getCropYPx();
        final Calibration sourceCalibration = sourceImage.getCalibration() == null
                ? null : sourceImage.getCalibration().copy();
        final Roi originalRoi = sourceImage.getRoi() == null
                ? null : (Roi) sourceImage.getRoi().clone();

        final int checksumBefore = checksumSource(sourceImage);

        ImagePlus tiledValidated = null;
        ImagePlus untiledValidated = null;
        ClassificationSeamAudit audit = null;
        String[] modelClassLabels = null;
        long tiledTimeMs = 0L;
        long untiledTimeMs = -1L;
        long[] tiledCounts = null;
        long[] untiledCounts = null;
        final boolean untiledRequested = configuration.isRunUntiledReference();
        boolean untiledSuccessful = false;
        String untiledFailureReason = "";
        CoreTileLayout layout = null;
        int classifiedTiles = 0;
        int skippedTiles = 0;
        long outsideMaskForcedBackgroundPixels = 0L;

        ImagePlus wekaUntiledRaw = null;
        WekaSegmentation tiledSegmentator = null;
        WekaSegmentation referenceSegmentator = null;
        ImagePlus assembledLabels = null;

        try {
            IJ.showStatus("Loading classifier");
            IJ.log("Loading classifier: " + canonicalPath);
            tiledSegmentator = new WekaSegmentation(false);
            if (!tiledSegmentator.loadClassifier(canonicalPath)) {
                throw new IllegalStateException(
                        "Failed to load Trainable Weka classifier: " + canonicalPath);
            }
            if (tiledSegmentator.getNumOfClasses() != 4) {
                throw new IllegalStateException(
                        "Classifier must have exactly 4 classes, found "
                                + tiledSegmentator.getNumOfClasses());
            }
            modelClassLabels = copyFourClassLabels(tiledSegmentator.getClassLabels());
            logModelClassLabels(modelClassLabels);

            final int classificationHalo = Math.max(
                    0, (int) tiledSegmentator.getMaximumSigma());
            // Tile geometry uses crop-local dimensions; Weka RGB reads use original offsets.
            final CoreTileLayout activeLayout = configuration.createLayout(
                    outputWidth, outputHeight, classificationHalo);
            activeLayout.verifyExactCoverage();
            layout = activeLayout;
            logGeometry(sourceTitle, activeLayout, configuration, approvedMask,
                    sampleMaskReviewed, cropGeometry);

            IJ.showStatus("Running tiled classification");
            final long tiledStart = System.currentTimeMillis();
            final WekaSegmentation segmentatorForTiles = tiledSegmentator;
            final long totalTiles = activeLayout.getTotalTiles();
            final int threadsPerTile = configuration.getThreadsPerTile();
            final MaskedLabelAssembler.AssemblyResult assembly = assembler.assemble(
                    activeLayout,
                    approvedMask,
                    new MaskedLabelAssembler.TileClassifier() {
                        @Override
                        public ImagePlus classifyTile(final CoreTileGeometry tile) {
                            return classifyOneTile(
                                    sourceImage,
                                    segmentatorForTiles,
                                    tile,
                                    totalTiles,
                                    threadsPerTile,
                                    cropOffsetOriginalX,
                                    cropOffsetOriginalY,
                                    sourceWidth,
                                    sourceHeight,
                                    classificationHalo);
                        }
                    },
                    cropOffsetOriginalX,
                    cropOffsetOriginalY);
            assembledLabels = assembly.getLabels();
            classifiedTiles = assembly.getClassifiedTiles();
            skippedTiles = assembly.getSkippedTiles();
            outsideMaskForcedBackgroundPixels =
                    assembly.getOutsideMaskForcedBackgroundPixels();
            tiledTimeMs = System.currentTimeMillis() - tiledStart;
            logTileSummary(activeLayout, classifiedTiles, skippedTiles);

            tiledValidated = validator.validateAndCopy(
                    assembledLabels,
                    outputWidth,
                    outputHeight,
                    "Tiled Weka Classified - " + sourceTitle);
            applyGoldenAngleLut(tiledValidated);
            if (sourceCalibration != null) {
                tiledValidated.setCalibration(sourceCalibration.copy());
            }
            tiledCounts = validator.countClassPixels(tiledValidated);

            assembledLabels.close();
            assembledLabels = null;
            tiledSegmentator = null;
            System.gc();

            if (untiledRequested) {
                IJ.showStatus("Running untiled reference");
                IJ.log("Running untiled reference classification");
                IJ.log(String.format(Locale.US,
                        "Untiled reference uses crop extent original=(%d,%d,%d,%d); "
                                + "seam audit coordinates are crop-local",
                        Integer.valueOf(cropOffsetOriginalX),
                        Integer.valueOf(cropOffsetOriginalY),
                        Integer.valueOf(outputWidth),
                        Integer.valueOf(outputHeight)));
                try {
                    referenceSegmentator = new WekaSegmentation(false);
                    if (!referenceSegmentator.loadClassifier(canonicalPath)) {
                        untiledFailureReason =
                                "Failed to load classifier for untiled reference: "
                                        + canonicalPath;
                        IJ.log("ERROR: " + untiledFailureReason);
                    } else {
                        final long untiledStart = System.currentTimeMillis();
                        if (cropOffsetOriginalX == 0
                                && cropOffsetOriginalY == 0
                                && outputWidth == sourceWidth
                                && outputHeight == sourceHeight) {
                            wekaUntiledRaw = referenceSegmentator.applyClassifier(
                                    sourceImage,
                                    configuration.getThreadsPerTile(),
                                    false);
                        } else {
                            // Same crop extent/origin as tiled output; no full-size allocation.
                            wekaUntiledRaw = referenceSegmentator.applyClassifier(
                                    sourceImage,
                                    new int[] { cropOffsetOriginalX, cropOffsetOriginalY },
                                    new int[] { outputWidth, outputHeight },
                                    configuration.getThreadsPerTile(),
                                    false);
                        }
                        untiledTimeMs = System.currentTimeMillis() - untiledStart;

                        if (wekaUntiledRaw == null) {
                            untiledFailureReason =
                                    "Untiled applyClassifier returned null";
                            IJ.log("ERROR: " + untiledFailureReason);
                        } else {
                            untiledValidated = validator.validateAndCopy(
                                    wekaUntiledRaw,
                                    outputWidth,
                                    outputHeight,
                                    "Untiled Weka Reference - " + sourceTitle);
                            MaskedLabelAssembler.applyMaskToLabels(
                                    untiledValidated,
                                    approvedMask,
                                    cropOffsetOriginalX,
                                    cropOffsetOriginalY);
                            applyGoldenAngleLut(untiledValidated);
                            if (sourceCalibration != null) {
                                untiledValidated.setCalibration(sourceCalibration.copy());
                            }
                            untiledCounts = validator.countClassPixels(untiledValidated);

                            wekaUntiledRaw.close();
                            wekaUntiledRaw = null;

                            IJ.showStatus("Comparing tiled and untiled results");
                            audit = seamAuditor.compare(
                                    tiledValidated,
                                    untiledValidated,
                                    layout,
                                    configuration.getSeamAuditHalfWidthPixels());
                            untiledSuccessful = true;
                        }
                    }
                } catch (final OutOfMemoryError oom) {
                    untiledFailureReason = "OutOfMemoryError during untiled reference: "
                            + describeThrowable(oom);
                    IJ.log("ERROR: " + untiledFailureReason);
                    logStackTrace(oom);
                    IJ.log("Keeping valid tiled classification result; "
                            + "untiled reference and seam audit were not created.");
                    untiledValidated = null;
                    audit = null;
                    untiledCounts = null;
                    untiledSuccessful = false;
                    if (untiledTimeMs < 0L) {
                        untiledTimeMs = -1L;
                    }
                    System.gc();
                } catch (final RuntimeException ex) {
                    untiledFailureReason = "Untiled reference failed: "
                            + describeThrowable(ex);
                    IJ.log("ERROR: " + untiledFailureReason);
                    logStackTrace(ex);
                    untiledValidated = null;
                    audit = null;
                    untiledCounts = null;
                    untiledSuccessful = false;
                } catch (final Exception ex) {
                    untiledFailureReason = "Untiled reference failed: "
                            + describeThrowable(ex);
                    IJ.log("ERROR: " + untiledFailureReason);
                    logStackTrace(ex);
                    untiledValidated = null;
                    audit = null;
                    untiledCounts = null;
                    untiledSuccessful = false;
                } finally {
                    referenceSegmentator = null;
                    if (wekaUntiledRaw != null) {
                        try {
                            wekaUntiledRaw.close();
                        } catch (final Exception ignored) {
                            // ignore close failures on temporary output
                        }
                        wekaUntiledRaw = null;
                    }
                    System.gc();
                }
            } else {
                IJ.log("Run untiled reference comparison: false");
            }
        } finally {
            restoreRoi(sourceImage, originalRoi);
            tiledSegmentator = null;
            referenceSegmentator = null;
            if (assembledLabels != null) {
                try {
                    assembledLabels.close();
                } catch (final Exception ignored) {
                    // ignore
                }
                assembledLabels = null;
            }
        }

        final int checksumAfter = checksumSource(sourceImage);
        logFinalCounts(
                layout,
                tiledCounts,
                classifiedTiles,
                skippedTiles,
                outsideMaskForcedBackgroundPixels,
                checksumBefore,
                checksumAfter);

        return new TiledWekaClassificationResult(
                tiledValidated,
                untiledValidated,
                audit,
                sourceTitle,
                canonicalPath,
                modelClassLabels,
                configuration,
                layout,
                approvedMask,
                automaticMaskStatistics,
                sampleMaskReviewed,
                classifiedTiles,
                skippedTiles,
                outsideMaskForcedBackgroundPixels,
                tiledTimeMs,
                untiledTimeMs,
                tiledCounts,
                untiledCounts,
                checksumBefore,
                checksumAfter,
                true,
                untiledRequested,
                untiledSuccessful,
                untiledFailureReason);
    }

    private static ImagePlus classifyOneTile(
            final ImagePlus sourceImage,
            final WekaSegmentation segmentator,
            final CoreTileGeometry tile,
            final long totalTiles,
            final int threadsPerTile,
            final int cropOffsetOriginalX,
            final int cropOffsetOriginalY,
            final int sourceWidth,
            final int sourceHeight,
            final int classificationHalo) {
        final int originalCoreX = cropOffsetOriginalX + tile.getCoreX();
        final int originalCoreY = cropOffsetOriginalY + tile.getCoreY();
        final int originalWekaCropX0 = Math.max(0, originalCoreX - classificationHalo);
        final int originalWekaCropY0 = Math.max(0, originalCoreY - classificationHalo);
        final int originalWekaCropX1 = Math.min(
                sourceWidth, originalCoreX + tile.getCoreWidth() + classificationHalo);
        final int originalWekaCropY1 = Math.min(
                sourceHeight, originalCoreY + tile.getCoreHeight() + classificationHalo);
        final int originalWekaCropWidth = originalWekaCropX1 - originalWekaCropX0;
        final int originalWekaCropHeight = originalWekaCropY1 - originalWekaCropY0;

        IJ.log(String.format(Locale.US,
                "Tile %d/%d [%d,%d] cropLocalCore=(%d,%d,%d,%d), "
                        + "originalCore=(%d,%d,%d,%d), originalWekaCrop=(%d,%d,%d,%d): classified",
                Integer.valueOf(tile.getTileIndex()),
                Long.valueOf(totalTiles),
                Integer.valueOf(tile.getTileXIndex()),
                Integer.valueOf(tile.getTileYIndex()),
                Integer.valueOf(tile.getCoreX()),
                Integer.valueOf(tile.getCoreY()),
                Integer.valueOf(tile.getCoreWidth()),
                Integer.valueOf(tile.getCoreHeight()),
                Integer.valueOf(originalCoreX),
                Integer.valueOf(originalCoreY),
                Integer.valueOf(tile.getCoreWidth()),
                Integer.valueOf(tile.getCoreHeight()),
                Integer.valueOf(originalWekaCropX0),
                Integer.valueOf(originalWekaCropY0),
                Integer.valueOf(originalWekaCropWidth),
                Integer.valueOf(originalWekaCropHeight)));
        IJ.showStatus(String.format(Locale.US,
                "Running tiled classification %d/%d",
                Integer.valueOf(tile.getTileIndex()),
                Long.valueOf(totalTiles)));
        try {
            final ImagePlus tileResult = segmentator.applyClassifier(
                    sourceImage,
                    new int[] { originalCoreX, originalCoreY },
                    tile.getCoreDimensions(),
                    threadsPerTile,
                    false);
            if (tileResult == null) {
                throw new TiledWekaClassificationException(
                        "Tiled applyClassifier returned null while processing "
                                + describeTile(tile, totalTiles, cropOffsetOriginalX,
                                cropOffsetOriginalY, originalWekaCropWidth,
                                originalWekaCropHeight),
                        null,
                        tile);
            }
            return tileResult;
        } catch (final OutOfMemoryError oom) {
            final String message = oomMessage(
                    tile,
                    totalTiles,
                    threadsPerTile,
                    originalWekaCropWidth,
                    originalWekaCropHeight);
            IJ.log("ERROR: " + message);
            logStackTrace(oom);
            System.gc();
            throw new TiledWekaClassificationException(message, oom, tile);
        } catch (final TiledWekaClassificationException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            final String message = "Tiled Weka classification failed while processing "
                    + describeTile(tile, totalTiles, cropOffsetOriginalX, cropOffsetOriginalY,
                    originalWekaCropWidth, originalWekaCropHeight)
                    + ": " + describeThrowable(ex);
            IJ.log("ERROR: " + message);
            logStackTrace(ex);
            throw new TiledWekaClassificationException(message, ex, tile);
        }
    }

    private static void logGeometry(
            final String sourceTitle,
            final CoreTileLayout layout,
            final TiledWekaClassificationConfiguration configuration,
            final DownsampledSampleMask mask,
            final boolean sampleMaskReviewed,
            final AnalysisCropGeometry cropGeometry) {
        IJ.log("Image: " + sourceTitle);
        IJ.log(String.format(Locale.US,
                "Original source dimensions: %d x %d",
                Integer.valueOf(cropGeometry.getOriginalWidthPx()),
                Integer.valueOf(cropGeometry.getOriginalHeightPx())));
        IJ.log("Crop enabled: " + configuration.isCropOutputToApprovedMaskExtent());
        IJ.log(String.format(Locale.US,
                "Crop rectangle (original coordinates): x=%d,y=%d,w=%d,h=%d,padding=%d",
                Integer.valueOf(cropGeometry.getCropXPx()),
                Integer.valueOf(cropGeometry.getCropYPx()),
                Integer.valueOf(cropGeometry.getCropWidthPx()),
                Integer.valueOf(cropGeometry.getCropHeightPx()),
                Integer.valueOf(cropGeometry.getCropPaddingPx())));
        IJ.log(String.format(Locale.US,
                "Crop output dimensions / crop-local tile grid image: %d x %d",
                Integer.valueOf(layout.getImageWidth()),
                Integer.valueOf(layout.getImageHeight())));
        IJ.log(String.format(Locale.US,
                "Core tile size: %d x %d",
                Integer.valueOf(layout.getCoreTileWidth()),
                Integer.valueOf(layout.getCoreTileHeight())));
        IJ.log(String.format(Locale.US,
                "Calculated crop-local tile grid: %d x %d",
                Integer.valueOf(layout.getTilesX()),
                Integer.valueOf(layout.getTilesY())));
        IJ.log(String.format(Locale.US,
                "Total tiles: %d",
                Long.valueOf(layout.getTotalTiles())));
        IJ.log(String.format(Locale.US,
                "Classification halo: %d pixels",
                Integer.valueOf(layout.getClassificationHaloPixels())));
        IJ.log(String.format(Locale.US,
                "Maximum crop-local expanded diagnostic crop: %d x %d",
                Integer.valueOf(layout.getMaximumExpandedCropWidth()),
                Integer.valueOf(layout.getMaximumExpandedCropHeight())));
        IJ.log(String.format(Locale.US,
                "Threads per tile: %d",
                Integer.valueOf(configuration.getThreadsPerTile())));
        IJ.log(String.format(Locale.US,
                "Seam audit half-width: %d (seam coordinates are crop-local)",
                Integer.valueOf(configuration.getSeamAuditHalfWidthPixels())));
        IJ.log("Run untiled reference comparison: "
                + configuration.isRunUntiledReference());
        IJ.log(String.format(Locale.US,
                "Sample mask downsample factor: %d",
                Integer.valueOf(mask.getDownsampleFactor())));
        IJ.log(String.format(Locale.US,
                "Sample mask dimensions: %d x %d",
                Integer.valueOf(mask.getMaskWidth()),
                Integer.valueOf(mask.getMaskHeight())));
        IJ.log(String.format(Locale.US,
                "Sample mask expansion margin (original px): %d",
                Integer.valueOf(configuration.getSampleMaskExpansionMarginOriginalPixels())));
        IJ.log("Sample mask reviewed: " + sampleMaskReviewed);
        IJ.log(String.format(Locale.US,
                "Final mask included pixels: %d (fraction=%.6f), checksum=%d",
                Long.valueOf(mask.getIncludedMaskPixels()),
                Double.valueOf(mask.getIncludedMaskFraction()),
                Integer.valueOf(mask.getChecksum())));
        IJ.log("Final included bounding box (original coordinates): "
                + mask.getIncludedBoundsOriginal());
    }

    private static void logTileSummary(
            final CoreTileLayout layout,
            final int classifiedTiles,
            final int skippedTiles) {
        final long total = layout.getTotalTiles();
        IJ.log(String.format(Locale.US,
                "Tile summary: total=%d, classified=%d, skipped=%d, "
                        + "classifiedFraction=%.6f, skippedFraction=%.6f",
                Long.valueOf(total),
                Integer.valueOf(classifiedTiles),
                Integer.valueOf(skippedTiles),
                Double.valueOf(total == 0L ? 0.0 : classifiedTiles / (double) total),
                Double.valueOf(total == 0L ? 0.0 : skippedTiles / (double) total)));
    }

    private static void logFinalCounts(
            final CoreTileLayout layout,
            final long[] tiledCounts,
            final int classifiedTiles,
            final int skippedTiles,
            final long outsideMaskForcedBackgroundPixels,
            final int checksumBefore,
            final int checksumAfter) {
        final long totalPixels = ((long) layout.getImageWidth()) * ((long) layout.getImageHeight());
        IJ.log("Tiled classification class counts:");
        IJ.log(String.format(Locale.US,
                "totalOutputPixels=%d, wood=%d, ray=%d, lumen=%d, background=%d, "
                        + "outsideMaskForcedBackgroundPixels=%d, classifiedTiles=%d, "
                        + "skippedTiles=%d, sourceChecksumBefore=%d, sourceChecksumAfter=%d, "
                        + "sourceUnchanged=%s, outputDimensions=%dx%d",
                Long.valueOf(totalPixels),
                Long.valueOf(tiledCounts[0]),
                Long.valueOf(tiledCounts[1]),
                Long.valueOf(tiledCounts[2]),
                Long.valueOf(tiledCounts[3]),
                Long.valueOf(outsideMaskForcedBackgroundPixels),
                Integer.valueOf(classifiedTiles),
                Integer.valueOf(skippedTiles),
                Integer.valueOf(checksumBefore),
                Integer.valueOf(checksumAfter),
                Boolean.valueOf(checksumBefore == checksumAfter),
                Integer.valueOf(layout.getImageWidth()),
                Integer.valueOf(layout.getImageHeight())));
    }

    private static String oomMessage(
            final CoreTileGeometry tile,
            final long totalTiles,
            final int threadsPerTile,
            final int originalWekaCropWidth,
            final int originalWekaCropHeight) {
        return String.format(Locale.US,
                "Tiled Weka classification ran out of Java heap memory while processing "
                        + "tile %d/%d [%d,%d]. cropLocalCore=%dx%d, "
                        + "originalSourceWekaCrop=%dx%d, threads=%d. "
                        + "Reduce the core tile width or height, reduce Weka concurrency if "
                        + "supported, or increase Fiji's Java heap.",
                Integer.valueOf(tile.getTileIndex()),
                Long.valueOf(totalTiles),
                Integer.valueOf(tile.getTileXIndex()),
                Integer.valueOf(tile.getTileYIndex()),
                Integer.valueOf(tile.getCoreWidth()),
                Integer.valueOf(tile.getCoreHeight()),
                Integer.valueOf(originalWekaCropWidth),
                Integer.valueOf(originalWekaCropHeight),
                Integer.valueOf(threadsPerTile));
    }

    private static String describeTile(
            final CoreTileGeometry tile,
            final long totalTiles,
            final int cropOffsetOriginalX,
            final int cropOffsetOriginalY,
            final int originalWekaCropWidth,
            final int originalWekaCropHeight) {
        return String.format(Locale.US,
                "tile %d/%d [%d,%d] cropLocalCore=(%d,%d,%d,%d) "
                        + "originalCore=(%d,%d,%d,%d) originalWekaCropDims=%dx%d",
                Integer.valueOf(tile.getTileIndex()),
                Long.valueOf(totalTiles),
                Integer.valueOf(tile.getTileXIndex()),
                Integer.valueOf(tile.getTileYIndex()),
                Integer.valueOf(tile.getCoreX()),
                Integer.valueOf(tile.getCoreY()),
                Integer.valueOf(tile.getCoreWidth()),
                Integer.valueOf(tile.getCoreHeight()),
                Integer.valueOf(cropOffsetOriginalX + tile.getCoreX()),
                Integer.valueOf(cropOffsetOriginalY + tile.getCoreY()),
                Integer.valueOf(tile.getCoreWidth()),
                Integer.valueOf(tile.getCoreHeight()),
                Integer.valueOf(originalWekaCropWidth),
                Integer.valueOf(originalWekaCropHeight));
    }

    static String describeThrowable(final Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        final StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                builder.append(" caused by ");
            }
            builder.append(current.getClass().getName());
            final String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                builder.append(": ").append(message);
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    static boolean causeChainContainsOutOfMemory(final Throwable throwable) {
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

    private static void logStackTrace(final Throwable throwable) {
        if (throwable == null) {
            return;
        }
        IJ.log(describeThrowable(throwable));
        final StackTraceElement[] stack = throwable.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            IJ.log("    at " + stack[i].toString());
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            IJ.log("Caused by:");
            logStackTrace(throwable.getCause());
        }
    }

    private static void restoreRoi(final ImagePlus image, final Roi originalRoi) {
        if (originalRoi == null) {
            image.deleteRoi();
        } else {
            image.setRoi(originalRoi);
        }
    }

    private static void validateSourceImage(final ImagePlus sourceImage) {
        if (sourceImage.getProcessor() == null) {
            throw new IllegalArgumentException("sourceImage has no processor");
        }
        if (sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
            throw new IllegalArgumentException("sourceImage must have positive dimensions");
        }
        if (sourceImage.getStackSize() > 1
                || sourceImage.getNSlices() > 1
                || sourceImage.getNFrames() > 1) {
            throw new IllegalArgumentException(
                    "sourceImage must be a single two-dimensional plane "
                            + "(stacks and time series are rejected)");
        }
        if (sourceImage.getNChannels() > 1 && !isRgbColor(sourceImage)) {
            throw new IllegalArgumentException(
                    "sourceImage must be a single two-dimensional plane "
                            + "(multi-channel hyperstacks are rejected)");
        }
        if (!isRgbColor(sourceImage)
                && !(sourceImage.getProcessor() instanceof ij.process.ColorProcessor)) {
            throw new IllegalArgumentException(
                    "Tiled Weka validation with sample masking requires an RGB source image");
        }
    }

    private static boolean isRgbColor(final ImagePlus image) {
        return image.getType() == ImagePlus.COLOR_RGB;
    }

    private static String[] copyFourClassLabels(final String[] labels) {
        if (labels == null || labels.length < 4) {
            throw new IllegalStateException(
                    "Classifier class labels missing or shorter than 4");
        }
        final String[] copy = new String[4];
        for (int i = 0; i < 4; i++) {
            copy[i] = labels[i] == null ? "" : labels[i];
        }
        return copy;
    }

    private static void logModelClassLabels(final String[] labels) {
        IJ.log(String.format(Locale.US,
                "Loaded classifier class labels (index order, not reinterpreted): "
                        + "0=%s (Wood), 1=%s (Ray), 2=%s (Lumen), 3=%s (Background)",
                labels[0], labels[1], labels[2], labels[3]));
    }

    private static void applyGoldenAngleLut(final ImagePlus image) {
        image.getProcessor().setLut(Utils.getGoldenAngleLUT());
    }

    static int checksumSource(final ImagePlus image) {
        final ImageProcessor processor = image.getProcessor();
        final Object pixels = processor.getPixels();
        if (pixels instanceof byte[]) {
            return checksumBytes((byte[]) pixels);
        }
        if (pixels instanceof short[]) {
            return checksumShorts((short[]) pixels);
        }
        if (pixels instanceof float[]) {
            return checksumFloats((float[]) pixels);
        }
        if (pixels instanceof int[]) {
            return checksumInts((int[]) pixels);
        }
        throw new IllegalArgumentException(
                "Unsupported pixel array type for checksum: "
                        + (pixels == null ? "null" : pixels.getClass().getName()));
    }

    private static int checksumBytes(final byte[] pixels) {
        int hash = 1;
        for (int i = 0; i < pixels.length; i++) {
            hash = 31 * hash + (pixels[i] & 0xff);
        }
        return hash;
    }

    private static int checksumShorts(final short[] pixels) {
        int hash = 1;
        for (int i = 0; i < pixels.length; i++) {
            hash = 31 * hash + (pixels[i] & 0xffff);
        }
        return hash;
    }

    private static int checksumFloats(final float[] pixels) {
        int hash = 1;
        for (int i = 0; i < pixels.length; i++) {
            hash = 31 * hash + Float.floatToIntBits(pixels[i]);
        }
        return hash;
    }

    private static int checksumInts(final int[] pixels) {
        int hash = 1;
        for (int i = 0; i < pixels.length; i++) {
            hash = 31 * hash + pixels[i];
        }
        return hash;
    }
}
