package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * ImageJ 1.x entry point for tiled Weka classification and dataset preparation.
 *
 * <p>Menu: Robust Ellipse Reconstruction - Tiled Weka Validation</p>
 *
 * <p>Stages: (A) classification/dataset dialog, (B) sample-mask review,
 * (C) calibration, (D) cropped or full-size classification and saving.</p>
 */
public final class TiledWekaClassificationValidationPlugin implements PlugIn {

    private static final String PREFS_MODEL_DIR =
            "vesselreconstruction.ellipse.tiledWeka.modelDir";
    private static final String PREFS_OUTPUT_DIR =
            "vesselreconstruction.ellipse.tiledWeka.datasetRoot";

    private static final String RESULTS_TABLE_TITLE = "Tiled Weka Validation Results";
    private static final String CONFUSION_TABLE_TITLE = "Tiled Weka Confusion Matrix";

    @Override
    public void run(final String arg) {
        final ImagePlus source = WindowManager.getCurrentImage();
        if (source == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "An RGB image must be opened and active.\n"
                            + "Weka classification is not started automatically "
                            + "when no source image is active.");
            return;
        }
        if (!isSingleTwoDimensionalPlane(source)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "The active image must be a single two-dimensional plane.\n"
                            + "Stacks and time series are not supported.");
            return;
        }
        if (source.getWidth() <= 0 || source.getHeight() <= 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "The active image must have positive width and height.");
            return;
        }
        if (source.getType() != ImagePlus.COLOR_RGB
                && !(source.getProcessor() instanceof ColorProcessor)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "The active image must be an RGB microscopy source.");
            return;
        }

        final File modelFile = chooseModelFile();
        if (modelFile == null) {
            return;
        }

        final TiledWekaClassificationConfiguration configuration =
                showConfigurationDialog(source, modelFile);
        if (configuration == null) {
            return;
        }

        logStart(source, modelFile, configuration);

        final SampleMaskGenerationResult generationResult;
        try {
            IJ.showStatus("Generating sample mask");
            generationResult = new AutomaticSampleMaskGenerator().generate(
                    source,
                    configuration.getSampleMaskDownsampleFactor(),
                    configuration.getSampleMaskExpansionMarginOriginalPixels());
            logAutomaticMask(generationResult);
        } catch (final SampleMaskGenerationException ex) {
            IJ.showStatus("Finished");
            IJ.log("ERROR: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    ex.getMessage());
            return;
        } catch (final RuntimeException ex) {
            IJ.showStatus("Finished");
            final String message = "Automatic sample-mask generation failed:\n"
                    + TiledWekaClassifier.describeThrowable(ex);
            IJ.log("ERROR: " + message);
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    message);
            return;
        }

        final SampleMaskReviewResult reviewResult;
        try {
            IJ.showStatus("Reviewing sample mask");
            reviewResult = new SampleMaskReviewController().review(
                    generationResult,
                    configuration.isReviewAndEditSampleMask());
            logApprovedMask(reviewResult);
        } catch (final SampleMaskReviewCancelledException ex) {
            IJ.showStatus("Finished");
            IJ.log(ex.getMessage());
            return;
        } catch (final IllegalArgumentException ex) {
            IJ.showStatus("Finished");
            IJ.log("ERROR: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    ex.getMessage());
            return;
        }

        final ColorProcessor rgbPreviewProcessor =
                reviewResult.getRgbPreview() == null
                        || !(reviewResult.getRgbPreview().getProcessor() instanceof ColorProcessor)
                        ? null
                        : (ColorProcessor) reviewResult.getRgbPreview().getProcessor();
        final CalibrationWorkflow.Result calibrationResult =
                new CalibrationWorkflow().acquire(
                        source,
                        configuration,
                        reviewResult.getApprovedMask(),
                        rgbPreviewProcessor);
        if (calibrationResult.getOutcome() != CalibrationWorkflow.Outcome.ACCEPTED) {
            IJ.showStatus("Finished");
            IJ.log("Classification cancelled during calibration. Weka was not started.");
            return;
        }
        final PhysicalCalibration calibration = calibrationResult.getCalibration();
        final ScaleBarDetection scaleBarDetection = calibrationResult.getScaleBarDetection();
        logCalibration(configuration, calibration, scaleBarDetection);

        final AnalysisCropGeometry cropGeometry;
        try {
            cropGeometry = configuration.isCropOutputToApprovedMaskExtent()
                    ? AnalysisCropGeometry.fromApprovedMask(
                            reviewResult.getApprovedMask(),
                            configuration.getAdditionalCropPaddingOriginalPixels())
                    : AnalysisCropGeometry.fullSource(
                            source.getWidth(), source.getHeight());
        } catch (final IllegalArgumentException ex) {
            IJ.showStatus("Finished");
            final String message = "Crop calculation failed:\n"
                    + "source=" + source.getWidth() + "x" + source.getHeight()
                    + ", mask=" + reviewResult.getApprovedMask().getMaskWidth()
                    + "x" + reviewResult.getApprovedMask().getMaskHeight()
                    + ", factor=" + reviewResult.getApprovedMask().getDownsampleFactor()
                    + "\n" + ex.getMessage();
            IJ.log("ERROR: " + message);
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    message);
            return;
        }
        logCrop(configuration, cropGeometry);

        final boolean productionSaves =
                configuration.isSaveCroppedClassifiedTiffAutomatically()
                        && configuration.isSaveApprovedDownsampledMaskAutomatically()
                        && configuration.isSaveCroppedRgbCopy();

        File originalSourceFile = ClassificationDatasetWriter.resolveOriginalSourceFile(source);
        ClassificationDatasetWriter.PreClassificationArtifacts preArtifacts = null;
        ProductionDatasetLayout datasetLayout = null;
        if (productionSaves) {
            if (originalSourceFile == null) {
                originalSourceFile = askForOriginalSourceTiff(source);
                if (originalSourceFile == null) {
                    IJ.showStatus("Finished");
                    IJ.log("Production save cancelled: original source TIFF not provided.");
                    return;
                }
            }
            if (!dimensionsMatch(originalSourceFile, source)) {
                IJ.showStatus("Finished");
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        "Selected original TIFF dimensions do not match the active source image.");
                return;
            }
            try {
                datasetLayout = new ProductionDatasetLayout(configuration.getOutputDirectory());
                IJ.showStatus("Saving pre-classification dataset outputs");
                preArtifacts = new ClassificationDatasetWriter().savePreClassificationArtifacts(
                        source,
                        originalSourceFile,
                        reviewResult.getApprovedMask(),
                        cropGeometry,
                        calibration,
                        configuration,
                        scaleBarDetection,
                        datasetLayout);
            } catch (final IOException ex) {
                IJ.showStatus("Finished");
                final String message = "Required pre-classification save failed; "
                        + "Weka was not started.\n" + ex.getMessage();
                IJ.log("ERROR: " + message);
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        message);
                return;
            } catch (final IllegalArgumentException ex) {
                IJ.showStatus("Finished");
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        ex.getMessage());
                return;
            }
        } else {
            IJ.log("Automatic required production saving disabled; "
                    + "SUCCESS status will not be written.");
        }

        final TiledWekaClassifier classifier = new TiledWekaClassifier();
        final TiledWekaClassificationResult result;
        try {
            result = classifier.classify(
                    source,
                    modelFile,
                    configuration,
                    reviewResult.getApprovedMask(),
                    generationResult.getStatistics(),
                    reviewResult.isReviewed(),
                    cropGeometry);
        } catch (final OutOfMemoryError oom) {
            closePreLoggerQuietly(preArtifacts);
            IJ.showStatus("Finished");
            final String message = "Tiled Weka classification ran out of Java heap memory. "
                    + "Reduce the core tile width or height, reduce Weka concurrency if "
                    + "supported, or increase Fiji's Java heap.\n"
                    + TiledWekaClassifier.describeThrowable(oom);
            IJ.log("ERROR: " + message);
            logThrowable(oom);
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    message);
            System.gc();
            return;
        } catch (final TiledWekaClassificationException ex) {
            closePreLoggerQuietly(preArtifacts);
            IJ.showStatus("Finished");
            final String message = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                    ? TiledWekaClassifier.describeThrowable(ex)
                    : ex.getMessage();
            IJ.log("ERROR: " + message);
            logThrowable(ex);
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    message);
            System.gc();
            return;
        } catch (final RuntimeException ex) {
            closePreLoggerQuietly(preArtifacts);
            IJ.showStatus("Finished");
            final String message;
            if (TiledWekaClassifier.causeChainContainsOutOfMemory(ex)) {
                message = "Tiled Weka classification ran out of Java heap memory. "
                        + "Reduce the core tile width or height, reduce Weka concurrency if "
                        + "supported, or increase Fiji's Java heap.\n"
                        + TiledWekaClassifier.describeThrowable(ex);
            } else {
                message = "Tiled Weka classification failed:\n"
                        + TiledWekaClassifier.describeThrowable(ex);
            }
            IJ.log("ERROR: " + message);
            logThrowable(ex);
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    message);
            return;
        }

        ClassificationDatasetWriter.Result saved = null;
        ImagePlus displayClassified = result.getTiledDirectLabels();
        if (productionSaves && preArtifacts != null && datasetLayout != null) {
            try {
                IJ.showStatus("Saving classification dataset outputs");
                saved = new ClassificationDatasetWriter().finalizeClassificationStage(
                        preArtifacts,
                        source,
                        displayClassified,
                        reviewResult.getApprovedMask(),
                        cropGeometry,
                        calibration,
                        configuration,
                        result,
                        modelFile,
                        scaleBarDetection,
                        datasetLayout);
            } catch (final IOException ex) {
                IJ.showStatus("Finished");
                final String message = "Required classification save failed; run is not SUCCESS.\n"
                        + "Pre-classification files may already exist.\n"
                        + ex.getMessage();
                IJ.log("ERROR: " + message);
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        message);
                displayOutputs(result, reviewResult, displayClassified);
                populateResultsTables(result, cropGeometry, calibration, null);
                logSummary(result, cropGeometry, calibration, null);
                return;
            }
        } else {
            PhysicalCalibrationService.applyToImage(displayClassified, calibration);
        }

        IJ.showStatus("Finished");
        displayOutputs(result, reviewResult, displayClassified);
        populateResultsTables(result, cropGeometry, calibration, saved);
        logSummary(result, cropGeometry, calibration, saved);
    }

    private static void closePreLoggerQuietly(
            final ClassificationDatasetWriter.PreClassificationArtifacts pre) {
        if (pre == null || pre.logger == null) {
            return;
        }
        try {
            pre.logger.log("Classification failed after pre-Weka artifacts; SUCCESS not written.");
            pre.logger.close();
        } catch (final IOException ignored) {
            // ignore
        }
    }

    private static File askForOriginalSourceTiff(final ImagePlus source) {
        final OpenDialog dialog = new OpenDialog(
                "Locate original source TIFF for dataset copy", "");
        final String directory = dialog.getDirectory();
        final String fileName = dialog.getFileName();
        if (directory == null || fileName == null) {
            return null;
        }
        return new File(directory, fileName);
    }

    private static boolean dimensionsMatch(final File tiffFile, final ImagePlus source) {
        final ImagePlus opened = IJ.openImage(tiffFile.getAbsolutePath());
        if (opened == null) {
            return false;
        }
        try {
            return opened.getWidth() == source.getWidth()
                    && opened.getHeight() == source.getHeight();
        } finally {
            opened.close();
        }
    }

    private static boolean isSingleTwoDimensionalPlane(final ImagePlus image) {
        if (image.getStackSize() > 1) {
            return false;
        }
        if (image.getNSlices() > 1 || image.getNFrames() > 1) {
            return false;
        }
        if (image.getNChannels() > 1 && image.getType() != ImagePlus.COLOR_RGB) {
            return false;
        }
        return true;
    }

    private static File chooseModelFile() {
        final String previousDir = Prefs.get(PREFS_MODEL_DIR, OpenDialog.getDefaultDirectory());
        if (previousDir != null && !previousDir.isEmpty()) {
            OpenDialog.setDefaultDirectory(previousDir);
        }
        final OpenDialog dialog = new OpenDialog("Choose Trainable Weka Classifier", "");
        final String directory = dialog.getDirectory();
        final String fileName = dialog.getFileName();
        if (directory == null || fileName == null) {
            return null;
        }
        Prefs.set(PREFS_MODEL_DIR, directory);
        return new File(directory, fileName);
    }

    private static TiledWekaClassificationConfiguration showConfigurationDialog(
            final ImagePlus source,
            final File modelFile) {
        final String sourceBase = FilenameSanitizer.stripExtension(safeTitle(source));
        final File defaultOutputDir = defaultOutputDirectory(source);

        final GenericDialog dialog = new GenericDialog(
                "Tiled Weka Classification and Dataset Preparation");
        dialog.addMessage("Source: " + safeTitle(source));
        dialog.addMessage("Image size: " + source.getWidth() + " x " + source.getHeight());
        dialog.addMessage("Model: " + modelFile.getName());
        dialog.addMessage("--- Classification settings ---");
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_CORE_TILE_WIDTH,
                TiledWekaClassificationDefaults.CORE_TILE_WIDTH_PIXELS,
                0);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_CORE_TILE_HEIGHT,
                TiledWekaClassificationDefaults.CORE_TILE_HEIGHT_PIXELS,
                0);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_THREADS_PER_TILE,
                TiledWekaClassificationDefaults.THREADS_PER_TILE,
                0);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_SEAM_AUDIT_HALF_WIDTH,
                TiledWekaClassificationDefaults.SEAM_AUDIT_HALF_WIDTH_PIXELS,
                0);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_RUN_UNTILED_REFERENCE,
                TiledWekaClassificationDefaults.RUN_UNTILED_REFERENCE_COMPARISON);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_SAMPLE_MASK_DOWNSAMPLE_FACTOR,
                TiledWekaClassificationDefaults.SAMPLE_MASK_DOWNSAMPLE_FACTOR,
                0);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_SAMPLE_MASK_EXPANSION_MARGIN,
                TiledWekaClassificationDefaults.SAMPLE_MASK_EXPANSION_MARGIN_ORIGINAL_PIXELS,
                0);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_REVIEW_SAMPLE_MASK,
                TiledWekaClassificationDefaults.REVIEW_AND_EDIT_SAMPLE_MASK);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_CROP_OUTPUT,
                TiledWekaClassificationDefaults.CROP_OUTPUT_TO_APPROVED_MASK_EXTENT);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_CROP_PADDING,
                TiledWekaClassificationDefaults.ADDITIONAL_CROP_PADDING_ORIGINAL_PIXELS,
                0);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_SAVE_CLASSIFIED,
                TiledWekaClassificationDefaults.SAVE_CROPPED_CLASSIFIED_TIFF_AUTOMATICALLY);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_SAVE_MASK,
                TiledWekaClassificationDefaults.SAVE_APPROVED_DOWNSAMPLED_MASK_AUTOMATICALLY);
        dialog.addCheckbox(
                TiledWekaClassificationDefaults.LABEL_SAVE_RGB_CROP,
                TiledWekaClassificationDefaults.SAVE_CROPPED_RGB_COPY);

        dialog.addMessage("--- Dataset identification ---");
        dialog.addStringField(TiledWekaClassificationDefaults.LABEL_SAMPLE_ID, sourceBase, 32);
        dialog.addStringField(TiledWekaClassificationDefaults.LABEL_SEGMENT_ID, sourceBase, 32);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_SEGMENT_ORDER,
                TiledWekaClassificationDefaults.SEGMENT_ORDER,
                0);
        dialog.addChoice(
                TiledWekaClassificationDefaults.LABEL_SEGMENT_ORIENTATION,
                TiledWekaClassificationDefaults.ORIENTATION_CHOICES,
                TiledWekaClassificationDefaults.SEGMENT_ORIENTATION.name());
        dialog.addStringField(
                TiledWekaClassificationDefaults.LABEL_GLOBAL_SEGMENT_START,
                "",
                16);
        dialog.addStringField(
                TiledWekaClassificationDefaults.LABEL_OUTPUT_DIRECTORY,
                defaultOutputDir.getAbsolutePath(),
                48);
        dialog.addMessage(
                "Dataset root receives images/, data/, and logs/.\n"
                        + "Leave as-is, or browse after OK if blank.");

        dialog.addMessage("--- Calibration (applied after mask approval) ---");
        dialog.addChoice(
                TiledWekaClassificationDefaults.LABEL_CALIBRATION_MODE,
                TiledWekaClassificationDefaults.CALIBRATION_MODE_CHOICES,
                TiledWekaClassificationDefaults.CALIBRATION_MODE.name());
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_SCALE_BAR_LENGTH,
                TiledWekaClassificationDefaults.SCALE_BAR_PHYSICAL_LENGTH_UM,
                3);
        dialog.addStringField(
                TiledWekaClassificationDefaults.LABEL_ENTERED_PIXEL_WIDTH,
                "",
                16);
        dialog.addStringField(
                TiledWekaClassificationDefaults.LABEL_ENTERED_PIXEL_HEIGHT,
                "",
                16);
        dialog.addMessage(
                "Automatic mode detects the red graphical scale bar and measures\n"
                        + "terminal-tick centerlines. The entered physical value is used (no OCR).\n"
                        + "Cropped production mode is default. Do not trust 300-PPI/inch metadata.");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return null;
        }

        final double coreWidthValue = dialog.getNextNumber();
        final double coreHeightValue = dialog.getNextNumber();
        final double threadsValue = dialog.getNextNumber();
        final double seamValue = dialog.getNextNumber();
        final boolean runUntiled = dialog.getNextBoolean();
        final double downsampleValue = dialog.getNextNumber();
        final double expansionValue = dialog.getNextNumber();
        final boolean reviewMask = dialog.getNextBoolean();
        final boolean cropEnabled = dialog.getNextBoolean();
        final double cropPaddingValue = dialog.getNextNumber();
        final boolean saveClassified = dialog.getNextBoolean();
        final boolean saveMask = dialog.getNextBoolean();
        final boolean saveRgb = dialog.getNextBoolean();
        final String sampleId = dialog.getNextString();
        final String segmentId = dialog.getNextString();
        final double segmentOrderValue = dialog.getNextNumber();
        final String orientationChoice = dialog.getNextChoice();
        final String globalStartRaw = dialog.getNextString();
        String outputDirPath = dialog.getNextString();
        final String calibrationModeChoice = dialog.getNextChoice();
        final double scaleBarValue = dialog.getNextNumber();
        final String enteredWidthRaw = dialog.getNextString();
        final String enteredHeightRaw = dialog.getNextString();

        if (!isFiniteIntegral(coreWidthValue)
                || !isFiniteIntegral(coreHeightValue)
                || !isFiniteIntegral(threadsValue)
                || !isFiniteIntegral(seamValue)
                || !isFiniteIntegral(downsampleValue)
                || !isFiniteIntegral(expansionValue)
                || !isFiniteIntegral(cropPaddingValue)
                || !isFiniteIntegral(segmentOrderValue)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Integer numeric fields must be finite integers.");
            return null;
        }

        final int coreTileWidth = (int) coreWidthValue;
        final int coreTileHeight = (int) coreHeightValue;
        final int threadsPerTile = (int) threadsValue;
        final int seamHalfWidth = (int) seamValue;
        final int downsampleFactor = (int) downsampleValue;
        final int expansionMargin = (int) expansionValue;
        final int cropPadding = (int) cropPaddingValue;
        final int segmentOrder = (int) segmentOrderValue;

        if (sampleId == null || sampleId.trim().isEmpty()) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Sample ID must not be blank.");
            return null;
        }
        if (segmentId == null || segmentId.trim().isEmpty()) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Segment ID must not be blank.");
            return null;
        }
        if (segmentOrder < 1) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Segment order must be >= 1.");
            return null;
        }
        if (cropPadding < 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Crop padding must be >= 0.");
            return null;
        }
        if (coreTileWidth <= 0 || coreTileHeight <= 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Core tile width and height must both be greater than zero.");
            return null;
        }
        if (threadsPerTile < 1 || seamHalfWidth < 0
                || downsampleFactor <= 0 || expansionMargin < 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "One or more numeric classification settings are invalid.");
            return null;
        }
        if (Double.isNaN(scaleBarValue) || Double.isInfinite(scaleBarValue)
                || !(scaleBarValue > 0.0)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Scale bar physical length must be finite and > 0.");
            return null;
        }

        final Double globalStart = parseOptionalFiniteDouble(globalStartRaw);
        if (globalStartRaw != null && !globalStartRaw.trim().isEmpty() && globalStart == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Optional global segment start must be blank or a finite number.");
            return null;
        }

        final CalibrationMode calibrationMode;
        try {
            calibrationMode = CalibrationMode.valueOf(calibrationModeChoice);
        } catch (final RuntimeException ex) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Invalid calibration mode.");
            return null;
        }
        final SegmentOrientation orientation;
        try {
            orientation = SegmentOrientation.valueOf(orientationChoice);
        } catch (final RuntimeException ex) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Invalid segment orientation.");
            return null;
        }

        Double enteredWidth = parseOptionalFiniteDouble(enteredWidthRaw);
        Double enteredHeight = parseOptionalFiniteDouble(enteredHeightRaw);
        if (calibrationMode == CalibrationMode.ENTER_PIXEL_SIZE) {
            if (enteredWidth == null || !(enteredWidth.doubleValue() > 0.0)) {
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        "Entered pixel width must be finite and > 0.");
                return null;
            }
            if (enteredHeightRaw != null && !enteredHeightRaw.trim().isEmpty()
                    && (enteredHeight == null || !(enteredHeight.doubleValue() > 0.0))) {
                IJ.error(
                        "Robust Ellipse Reconstruction - Tiled Weka Validation",
                        "Entered pixel height must be blank, or finite and > 0.");
                return null;
            }
            if (enteredHeightRaw == null || enteredHeightRaw.trim().isEmpty()) {
                enteredHeight = null;
            }
        } else {
            enteredWidth = null;
            enteredHeight = null;
        }

        if (outputDirPath == null || outputDirPath.trim().isEmpty()) {
            final DirectoryChooser chooser = new DirectoryChooser("Choose dataset root directory");
            outputDirPath = chooser.getDirectory();
            if (outputDirPath == null) {
                return null;
            }
        }
        final File outputDirectory = new File(outputDirPath.trim());
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Dataset root does not exist and could not be created:\n"
                            + outputDirectory.getAbsolutePath());
            return null;
        }
        if (!outputDirectory.isDirectory()) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    "Dataset root is not a directory:\n"
                            + outputDirectory.getAbsolutePath());
            return null;
        }
        Prefs.set(PREFS_OUTPUT_DIR, outputDirectory.getAbsolutePath());

        try {
            return new TiledWekaClassificationConfiguration(
                    coreTileWidth,
                    coreTileHeight,
                    threadsPerTile,
                    runUntiled,
                    seamHalfWidth,
                    downsampleFactor,
                    expansionMargin,
                    reviewMask,
                    cropEnabled,
                    cropPadding,
                    saveClassified,
                    saveMask,
                    saveRgb,
                    sampleId.trim(),
                    segmentId.trim(),
                    segmentOrder,
                    orientation,
                    globalStart,
                    outputDirectory,
                    calibrationMode,
                    scaleBarValue,
                    enteredWidth,
                    enteredHeight);
        } catch (final IllegalArgumentException ex) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Tiled Weka Validation",
                    ex.getMessage());
            return null;
        }
    }

    private static File defaultOutputDirectory(final ImagePlus source) {
        final String prefs = Prefs.get(PREFS_OUTPUT_DIR, "");
        if (prefs != null && !prefs.trim().isEmpty()) {
            return new File(prefs.trim());
        }
        final FileInfo info = source.getOriginalFileInfo();
        if (info != null && info.directory != null && !info.directory.isEmpty()) {
            return new File(info.directory, TiledWekaClassificationDefaults.OUTPUT_DIRECTORY_NAME);
        }
        return new File(TiledWekaClassificationDefaults.OUTPUT_DIRECTORY_NAME);
    }

    private static Double parseOptionalFiniteDouble(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            final double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return Double.valueOf(value);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isFiniteIntegral(final double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value == Math.rint(value);
    }

    private static void logStart(
            final ImagePlus source,
            final File modelFile,
            final TiledWekaClassificationConfiguration configuration) {
        String modelPath;
        try {
            modelPath = modelFile.getCanonicalPath();
        } catch (final Exception ex) {
            modelPath = modelFile.getAbsolutePath();
        }
        final CoreTileLayout previewLayout = configuration.createLayout(
                source.getWidth(),
                source.getHeight(),
                0);
        final int expectedMaskWidth = DownsampledSampleMask.maskDimension(
                source.getWidth(), configuration.getSampleMaskDownsampleFactor());
        final int expectedMaskHeight = DownsampledSampleMask.maskDimension(
                source.getHeight(), configuration.getSampleMaskDownsampleFactor());
        IJ.log("Tiled Weka classification validation started.");
        IJ.log("Source image: " + safeTitle(source));
        IJ.log(String.format(Locale.US,
                "Source dimensions: %d x %d",
                Integer.valueOf(source.getWidth()),
                Integer.valueOf(source.getHeight())));
        IJ.log(String.format(Locale.US,
                "Core tile size: %d x %d",
                Integer.valueOf(configuration.getCoreTileWidthPixels()),
                Integer.valueOf(configuration.getCoreTileHeightPixels())));
        IJ.log(String.format(Locale.US,
                "Calculated tile grid: %d x %d",
                Integer.valueOf(previewLayout.getTilesX()),
                Integer.valueOf(previewLayout.getTilesY())));
        IJ.log(String.format(Locale.US,
                "Total tiles: %d",
                Long.valueOf(previewLayout.getTotalTiles())));
        IJ.log(String.format(Locale.US,
                "Sample mask downsample factor: %d",
                Integer.valueOf(configuration.getSampleMaskDownsampleFactor())));
        IJ.log(String.format(Locale.US,
                "Expected mask dimensions: %d x %d",
                Integer.valueOf(expectedMaskWidth),
                Integer.valueOf(expectedMaskHeight)));
        IJ.log(String.format(Locale.US,
                "Sample mask expansion margin in original pixels: %d",
                Integer.valueOf(configuration.getSampleMaskExpansionMarginOriginalPixels())));
        IJ.log("Mask review enabled: " + configuration.isReviewAndEditSampleMask());
        IJ.log("Run untiled reference comparison: "
                + configuration.isRunUntiledReference());
        IJ.log("Crop output to approved mask extent: "
                + configuration.isCropOutputToApprovedMaskExtent());
        IJ.log("Dataset Sample ID: " + configuration.getSampleId());
        IJ.log("Dataset Segment ID: " + configuration.getSegmentId());
        IJ.log("Dataset Segment order: " + configuration.getSegmentOrder());
        IJ.log("Dataset Segment orientation: "
                + configuration.getSegmentOrientation().name());
        IJ.log("Optional global segment start µm: "
                + (configuration.getOptionalGlobalSegmentStartUm().isPresent()
                ? configuration.getOptionalGlobalSegmentStartUm().get().toString()
                : "(blank)"));
        IJ.log("Output directory: "
                + configuration.getOutputDirectory().getAbsolutePath());
        IJ.log("Calibration mode: " + configuration.getCalibrationMode().name());
        IJ.log("Model: " + modelPath);
    }

    private static void logCalibration(
            final TiledWekaClassificationConfiguration configuration,
            final PhysicalCalibration calibration,
            final ScaleBarDetection detection) {
        IJ.log("Calibration confirmed.");
        IJ.log("Accepted calibration method: " + calibration.getCalibrationMethod().name());
        IJ.log("Selected dialog calibration mode: " + configuration.getCalibrationMode().name());
        if (calibration.getScaleBarMeasuredLengthPx() != null) {
            IJ.log(String.format(Locale.US,
                    "Measured scale-bar length px (center-to-center when automatic): %.17g",
                    calibration.getScaleBarMeasuredLengthPx()));
            IJ.log(String.format(Locale.US,
                    "Scale-bar physical length µm: %.17g",
                    calibration.getScaleBarPhysicalLengthUm()));
        }
        if (detection != null && detection.isSuccessful()) {
            IJ.log(String.format(Locale.US,
                    "Automatic detection outer component width px (not used as length): %.17g",
                    Double.valueOf(detection.getOuterComponentWidthPx())));
            IJ.log("Automatic detection confidence: " + detection.getConfidence().name());
        }
        IJ.log(String.format(Locale.US,
                "Pixel width µm: %.17g",
                Double.valueOf(calibration.getPixelWidthUm())));
        IJ.log(String.format(Locale.US,
                "Pixel height µm: %.17g",
                Double.valueOf(calibration.getPixelHeightUm())));
        IJ.log("Source metadata unit: "
                + (calibration.getSourceMetadataUnit().isEmpty()
                ? "(unavailable)" : calibration.getSourceMetadataUnit()));
    }

    private static void logCrop(
            final TiledWekaClassificationConfiguration configuration,
            final AnalysisCropGeometry crop) {
        IJ.log("Crop enabled: " + configuration.isCropOutputToApprovedMaskExtent());
        IJ.log(String.format(Locale.US,
                "Original dimensions: %d x %d",
                Integer.valueOf(crop.getOriginalWidthPx()),
                Integer.valueOf(crop.getOriginalHeightPx())));
        IJ.log(String.format(Locale.US,
                "Crop rectangle in original coordinates: x=%d,y=%d,w=%d,h=%d",
                Integer.valueOf(crop.getCropXPx()),
                Integer.valueOf(crop.getCropYPx()),
                Integer.valueOf(crop.getCropWidthPx()),
                Integer.valueOf(crop.getCropHeightPx())));
        IJ.log(String.format(Locale.US,
                "Crop output dimensions: %d x %d",
                Integer.valueOf(crop.getCropWidthPx()),
                Integer.valueOf(crop.getCropHeightPx())));
        IJ.log("Crop padding: " + crop.getCropPaddingPx());
    }

    private static void logAutomaticMask(final SampleMaskGenerationResult generationResult) {
        final SampleMaskStatistics statistics = generationResult.getStatistics();
        IJ.log("Automatic sample mask generated.");
        IJ.log(String.format(Locale.US,
                "Estimated background RGB: (%d,%d,%d)",
                Integer.valueOf(statistics.getBackgroundRed()),
                Integer.valueOf(statistics.getBackgroundGreen()),
                Integer.valueOf(statistics.getBackgroundBlue())));
        IJ.log(String.format(Locale.US,
                "Automatic threshold: %d",
                Integer.valueOf(statistics.getAutomaticThreshold())));
        IJ.log(String.format(Locale.US,
                "Candidate components: %d",
                Integer.valueOf(statistics.getCandidateComponentCount())));
        IJ.log(String.format(Locale.US,
                "Selected component area (mask px): %d",
                Long.valueOf(statistics.getSelectedComponentAreaMaskPixels())));
        IJ.log("Selected component bounding box (mask): "
                + statistics.getSelectedComponentBoundsMask());
        IJ.log(String.format(Locale.US,
                "Selected component span: %d x %d",
                Integer.valueOf(statistics.getSelectedComponentWidthSpan()),
                Integer.valueOf(statistics.getSelectedComponentHeightSpan())));
        IJ.log(String.format(Locale.US,
                "Automatic mask included pixels: %d (fraction=%.6f), checksum=%d",
                Long.valueOf(statistics.getAutomaticIncludedMaskPixels()),
                Double.valueOf(statistics.getAutomaticIncludedFraction()),
                Integer.valueOf(statistics.getAutomaticMaskChecksum())));
    }

    private static void logApprovedMask(final SampleMaskReviewResult reviewResult) {
        final DownsampledSampleMask mask = reviewResult.getApprovedMask();
        IJ.log("mask reviewed: " + reviewResult.isReviewed());
        IJ.log(String.format(Locale.US,
                "Final mask dimensions: %d x %d",
                Integer.valueOf(mask.getMaskWidth()),
                Integer.valueOf(mask.getMaskHeight())));
        IJ.log(String.format(Locale.US,
                "Final included mask pixels: %d (fraction=%.6f), checksum=%d",
                Long.valueOf(mask.getIncludedMaskPixels()),
                Double.valueOf(mask.getIncludedMaskFraction()),
                Integer.valueOf(mask.getChecksum())));
        IJ.log("Final included bounding box mapped to original coordinates: "
                + mask.getIncludedBoundsOriginal());
    }

    private static void displayOutputs(
            final TiledWekaClassificationResult result,
            final SampleMaskReviewResult reviewResult,
            final ImagePlus classifiedForDisplay) {
        final ImagePlus tiled = classifiedForDisplay == null
                ? result.getTiledDirectLabels()
                : classifiedForDisplay;
        tiled.show();

        if (reviewResult.getEditableMaskImage() != null) {
            reviewResult.getEditableMaskImage().show();
        }
        if (reviewResult.getRgbPreview() != null) {
            reviewResult.getRgbPreview().show();
        }

        if (!result.isUntiledReferenceSuccessful()) {
            return;
        }

        final ImagePlus untiled = result.getUntiledDirectLabels();
        if (untiled != null) {
            untiled.show();
        }

        final ClassificationSeamAudit audit = result.getSeamAudit();
        if (audit != null) {
            final ImagePlus difference = audit.getDifferenceMask();
            difference.setTitle(
                    "Tiled vs Untiled Difference Mask - " + result.getSourceTitle());
            difference.show();

            final ImagePlus seam = audit.getSeamAuditMask();
            seam.setTitle("Tiled Seam Audit - " + result.getSourceTitle());
            seam.show();
        }
    }

    private static void populateResultsTables(
            final TiledWekaClassificationResult result,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final ClassificationDatasetWriter.Result saved) {
        final ResultsTable table = new ResultsTable();
        final TiledWekaClassificationConfiguration configuration = result.getConfiguration();
        final CoreTileLayout layout = result.getLayout();
        final DownsampledSampleMask mask = result.getApprovedMask();
        final SampleMaskStatistics automatic = result.getAutomaticMaskStatistics();
        final long[] tiledCounts = result.getTiledClassCounts();
        final ClassificationSeamAudit audit = result.getSeamAudit();
        final boolean hasAudit = result.isUntiledReferenceSuccessful() && audit != null;
        final long[] referenceCounts = hasAudit
                ? audit.getReferenceClassCounts()
                : null;

        table.incrementCounter();
        table.addValue("Source_Title", result.getSourceTitle());
        table.addValue("sample_id", configuration.getSampleId());
        table.addValue("segment_id", configuration.getSegmentId());
        table.addValue("segment_order", configuration.getSegmentOrder());
        table.addValue("segment_orientation", configuration.getSegmentOrientation().name());
        table.addValue("crop_enabled", configuration.isCropOutputToApprovedMaskExtent() ? 1 : 0);
        table.addValue("crop_x_px", crop.getCropXPx());
        table.addValue("crop_y_px", crop.getCropYPx());
        table.addValue("crop_width_px", crop.getCropWidthPx());
        table.addValue("crop_height_px", crop.getCropHeightPx());
        table.addValue("pixel_width_um", calibration.getPixelWidthUm());
        table.addValue("pixel_height_um", calibration.getPixelHeightUm());
        table.addValue("calibration_method", calibration.getCalibrationMethod().name());
        table.addValue("Width", layout.getImageWidth());
        table.addValue("Height", layout.getImageHeight());
        if (saved != null) {
            table.addValue("run_id", saved.runId);
            table.addValue("dataset_root",
                    saved.maskedClassified.getParent().getParent().getParent().toString());
            table.addValue("original_rgb_path", saved.originalRgb.toString());
            table.addValue("approved_mask_path", saved.approvedMask.toString());
            table.addValue("masked_rgb_path", saved.maskedRgb.toString());
            table.addValue("masked_classified_path", saved.maskedClassified.toString());
            table.addValue("run_log_path", saved.runLog.toString());
        }
        table.addValue("Model_File", new File(result.getModelCanonicalPath()).getName());
        table.addValue("core_tile_width_px", configuration.getCoreTileWidthPixels());
        table.addValue("core_tile_height_px", configuration.getCoreTileHeightPixels());
        table.addValue("calculated_tiles_x", layout.getTilesX());
        table.addValue("calculated_tiles_y", layout.getTilesY());
        table.addValue("total_tiles", layout.getTotalTiles());
        table.addValue("threads_per_tile", configuration.getThreadsPerTile());
        table.addValue("seam_audit_half_width_px",
                configuration.getSeamAuditHalfWidthPixels());
        table.addValue("run_untiled_reference_comparison",
                result.isUntiledReferenceRequested() ? 1 : 0);
        table.addValue("sample_mask_enabled", 1);
        table.addValue("sample_mask_downsample_factor", mask.getDownsampleFactor());
        table.addValue("sample_mask_width", mask.getMaskWidth());
        table.addValue("sample_mask_height", mask.getMaskHeight());
        table.addValue("sample_mask_expansion_margin_px",
                configuration.getSampleMaskExpansionMarginOriginalPixels());
        table.addValue("sample_mask_reviewed", result.isSampleMaskReviewed() ? 1 : 0);
        table.addValue("sample_mask_threshold",
                automatic == null ? Double.NaN : automatic.getAutomaticThreshold());
        table.addValue("sample_mask_checksum", mask.getChecksum());
        table.addValue("sample_mask_included_pixels", mask.getIncludedMaskPixels());
        table.addValue("sample_mask_included_fraction", mask.getIncludedMaskFraction());
        table.addValue("classified_tiles", result.getClassifiedTiles());
        table.addValue("skipped_tiles", result.getSkippedTiles());
        table.addValue("outside_mask_forced_background_pixels",
                result.getOutsideMaskForcedBackgroundPixels());
        table.addValue("classification_halo_px", layout.getClassificationHaloPixels());
        table.addValue("Tiled_Time_ms", result.getTiledClassificationTimeMs());
        table.addValue("Untiled_Successful", result.isUntiledReferenceSuccessful() ? 1 : 0);
        if (result.isUntiledReferenceSuccessful()) {
            table.addValue("Untiled_Time_ms", result.getUntiledClassificationTimeMs());
        } else {
            table.addValue("Untiled_Time_ms", Double.NaN);
        }

        if (hasAudit) {
            table.addValue("Exact_Match", audit.isExactMatch() ? 1 : 0);
            table.addValue("Total_Different_Pixels", audit.getDifferingPixelCount());
            table.addValue("Different_Fraction", audit.getDifferingPixelFraction());
            table.addValue("Seam_Band_Pixels", audit.getSeamBandPixelCount());
            table.addValue("Seam_Different_Pixels", audit.getSeamBandDifferingPixelCount());
            table.addValue("Seam_Different_Fraction", audit.getSeamBandDifferingFraction());
            table.addValue("Non_Seam_Pixels", audit.getNonSeamPixelCount());
            table.addValue("Non_Seam_Different_Pixels", audit.getNonSeamDifferingPixelCount());
            table.addValue("Non_Seam_Different_Fraction", audit.getNonSeamDifferingFraction());
        } else {
            table.addValue("Exact_Match", Double.NaN);
            table.addValue("Total_Different_Pixels", Double.NaN);
            table.addValue("Different_Fraction", Double.NaN);
            table.addValue("Seam_Band_Pixels", Double.NaN);
            table.addValue("Seam_Different_Pixels", Double.NaN);
            table.addValue("Seam_Different_Fraction", Double.NaN);
            table.addValue("Non_Seam_Pixels", Double.NaN);
            table.addValue("Non_Seam_Different_Pixels", Double.NaN);
            table.addValue("Non_Seam_Different_Fraction", Double.NaN);
        }

        table.addValue("Tiled_Wood_Pixels", tiledCounts[0]);
        table.addValue("Tiled_Ray_Pixels", tiledCounts[1]);
        table.addValue("Tiled_Lumen_Pixels", tiledCounts[2]);
        table.addValue("Tiled_Background_Pixels", tiledCounts[3]);

        if (referenceCounts != null) {
            table.addValue("Reference_Wood_Pixels", referenceCounts[0]);
            table.addValue("Reference_Ray_Pixels", referenceCounts[1]);
            table.addValue("Reference_Lumen_Pixels", referenceCounts[2]);
            table.addValue("Reference_Background_Pixels", referenceCounts[3]);
        } else {
            table.addValue("Reference_Wood_Pixels", Double.NaN);
            table.addValue("Reference_Ray_Pixels", Double.NaN);
            table.addValue("Reference_Lumen_Pixels", Double.NaN);
            table.addValue("Reference_Background_Pixels", Double.NaN);
        }

        table.addValue("Source_Checksum_Before", result.getSourceChecksumBefore());
        table.addValue("Source_Checksum_After", result.getSourceChecksumAfter());
        table.addValue("Source_Unchanged", result.isSourceUnchanged() ? 1 : 0);
        table.addValue(
                "Untiled_Failure_Reason",
                result.isUntiledReferenceSuccessful()
                        ? ""
                        : result.getUntiledReferenceFailureReason());
        table.show(RESULTS_TABLE_TITLE);

        if (hasAudit) {
            populateConfusionTable(audit);
        }
    }

    private static void populateConfusionTable(final ClassificationSeamAudit audit) {
        final ResultsTable table = new ResultsTable();
        final long[][] confusion = audit.getConfusionMatrix();
        final String[] rowLabels = new String[] {
                "Wood", "Ray", "Lumen", "Background"
        };
        for (int row = 0; row < 4; row++) {
            long total = 0L;
            for (int col = 0; col < 4; col++) {
                total += confusion[row][col];
            }
            table.incrementCounter();
            table.addValue("Reference_Class", rowLabels[row]);
            table.addValue("Tiled_Wood", confusion[row][0]);
            table.addValue("Tiled_Ray", confusion[row][1]);
            table.addValue("Tiled_Lumen", confusion[row][2]);
            table.addValue("Tiled_Background", confusion[row][3]);
            table.addValue("Reference_Total", total);
        }
        table.show(CONFUSION_TABLE_TITLE);
    }

    private static void logSummary(
            final TiledWekaClassificationResult result,
            final AnalysisCropGeometry crop,
            final PhysicalCalibration calibration,
            final ClassificationDatasetWriter.Result saved) {
        final TiledWekaClassificationConfiguration configuration = result.getConfiguration();
        final CoreTileLayout layout = result.getLayout();
        final long[] tiledCounts = result.getTiledClassCounts();
        final DownsampledSampleMask mask = result.getApprovedMask();

        IJ.log("Tiled Weka classification result:");
        IJ.log(String.format(Locale.US,
                "%s,output=%dx%d,cropOffset=(%d,%d),%s,core_tile_width_px=%d,"
                        + "core_tile_height_px=%d,calculated_tiles_x=%d,calculated_tiles_y=%d,"
                        + "total_tiles=%d,classified_tiles=%d,skipped_tiles=%d,"
                        + "sample_mask_checksum=%d,outside_mask_forced_background_pixels=%d,"
                        + "threads=%d,tiledTimeMs=%d,wood=%d,ray=%d,lumen=%d,background=%d,"
                        + "checksumBefore=%d,checksumAfter=%d,sourceUnchanged=%s,"
                        + "pixelWidthUm=%.17g,pixelHeightUm=%.17g",
                result.getSourceTitle(),
                Integer.valueOf(layout.getImageWidth()),
                Integer.valueOf(layout.getImageHeight()),
                Integer.valueOf(crop.getCropXPx()),
                Integer.valueOf(crop.getCropYPx()),
                result.getModelCanonicalPath(),
                Integer.valueOf(configuration.getCoreTileWidthPixels()),
                Integer.valueOf(configuration.getCoreTileHeightPixels()),
                Integer.valueOf(layout.getTilesX()),
                Integer.valueOf(layout.getTilesY()),
                Long.valueOf(layout.getTotalTiles()),
                Integer.valueOf(result.getClassifiedTiles()),
                Integer.valueOf(result.getSkippedTiles()),
                Integer.valueOf(mask.getChecksum()),
                Long.valueOf(result.getOutsideMaskForcedBackgroundPixels()),
                Integer.valueOf(configuration.getThreadsPerTile()),
                Long.valueOf(result.getTiledClassificationTimeMs()),
                Long.valueOf(tiledCounts[0]),
                Long.valueOf(tiledCounts[1]),
                Long.valueOf(tiledCounts[2]),
                Long.valueOf(tiledCounts[3]),
                Integer.valueOf(result.getSourceChecksumBefore()),
                Integer.valueOf(result.getSourceChecksumAfter()),
                Boolean.valueOf(result.isSourceUnchanged()),
                Double.valueOf(calibration.getPixelWidthUm()),
                Double.valueOf(calibration.getPixelHeightUm())));

        if (result.isUntiledReferenceSuccessful() && result.getSeamAudit() != null) {
            final ClassificationSeamAudit audit = result.getSeamAudit();
            IJ.log("Tiled versus untiled audit (seam coordinates are crop-local):");
            IJ.log(String.format(Locale.US,
                    "%d,%d,%.10f,%d,%d,%.10f,%d,%d,%.10f,%s",
                    Long.valueOf(audit.getTotalPixelCount()),
                    Long.valueOf(audit.getDifferingPixelCount()),
                    Double.valueOf(audit.getDifferingPixelFraction()),
                    Long.valueOf(audit.getSeamBandPixelCount()),
                    Long.valueOf(audit.getSeamBandDifferingPixelCount()),
                    Double.valueOf(audit.getSeamBandDifferingFraction()),
                    Long.valueOf(audit.getNonSeamPixelCount()),
                    Long.valueOf(audit.getNonSeamDifferingPixelCount()),
                    Double.valueOf(audit.getNonSeamDifferingFraction()),
                    Boolean.valueOf(audit.isExactMatch())));
        } else if (result.isUntiledReferenceRequested()) {
            IJ.log("Untiled reference failed; tiled classification succeeded.");
            IJ.log("Failure reason: " + result.getUntiledReferenceFailureReason());
        }

        IJ.log("IMPORTANT:");
        if (configuration.isCropOutputToApprovedMaskExtent()) {
            IJ.log("Cropped production mode: classified coordinates begin at 0,0.");
            IJ.log("Original coordinates require crop offsets stored in metadata/manifest.");
        } else {
            IJ.log("Full-size validation mode: output dimensions equal source dimensions.");
        }
        IJ.log("Classification tiles are temporary memory-management regions only.");
        IJ.log("The sample mask is stored at downsampled resolution.");
        IJ.log("Pixels outside the approved mask are Background class 3.");
        IJ.log("The original RGB source image was not modified.");
        IJ.log("The scale bar remains only in the unchanged original RGB TIFF.");
        IJ.log("The external CSV manifest is authoritative for calibration/coordinates.");
        IJ.log("No images are physically stitched in this iteration.");
        IJ.log("The reconstruction algorithm was not run.");
        if (saved != null) {
            IJ.log("Required outputs saved; SUCCESS row written to manifests.");
        } else {
            IJ.log("Required automatic saving was not completed for SUCCESS status.");
        }
        IJ.log("Inspect the approved mask before trusting reconstruction results.");
    }

    private static void logThrowable(final Throwable throwable) {
        if (throwable == null) {
            return;
        }
        final StackTraceElement[] stack = throwable.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            IJ.log("    at " + stack[i].toString());
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            IJ.log("Caused by: " + TiledWekaClassifier.describeThrowable(throwable.getCause()));
            logThrowable(throwable.getCause());
        }
    }

    private static String safeTitle(final ImagePlus image) {
        return image.getTitle() == null ? "Untitled" : image.getTitle();
    }
}
