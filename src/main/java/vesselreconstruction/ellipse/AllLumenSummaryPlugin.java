package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * ImageJ entry point for all-lumen diagnostic batch processing and production
 * reconstruction saving.
 *
 * <p>SUMMARY_ONLY may show combined outputs only. SILENT creates no windows.</p>
 */
public final class AllLumenSummaryPlugin implements PlugIn {

    private static final String PREFS_DATASET_ROOT =
            "vesselreconstruction.ellipse.allLumina.datasetRoot";

    private static final Color COLOR_WOOD = new Color(0, 100, 0);
    private static final Color COLOR_RAY = new Color(140, 80, 0);
    private static final Color COLOR_LUMEN = new Color(70, 70, 70);
    private static final Color COLOR_BACKGROUND = new Color(120, 20, 20);
    private static final Color COLOR_OTHER = new Color(120, 0, 120);

    @Override
    public void run(final String arg) {
        IJ.log("Robust Ellipse Reconstruction - All Lumina started.");
        IJ.log("Mode: ALL_LUMEN_DIAGNOSTIC_BATCH");
        IJ.log("Per-lumen windows: DISABLED");
        IJ.log("Source image modification: DISABLED");
        IJ.log("Directory processing: NOT IMPLEMENTED");

        final ImagePlus image = WindowManager.getCurrentImage();
        if (image == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction - All Lumina",
                    "An 8-bit classified image must be opened and active.");
            return;
        }
        final ImageProcessor processor = image.getProcessor();
        if (processor == null || !(processor instanceof ByteProcessor)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - All Lumina",
                    "The active image must be an 8-bit classified image.\n"
                            + "RGB, 16-bit and 32-bit images are not supported.");
            return;
        }

        final LumenImageMetadata.ParseResult parsedMetadata = LumenImageMetadata.parse(
                image.getProperty("Info") == null ? null : String.valueOf(image.getProperty("Info")));
        final Optional<String> metaDatasetRoot = readDatasetRootFromInfo(image);
        final String sampleIdDisplay = parsedMetadata.isAvailable()
                ? parsedMetadata.getMetadata().get().getRequired(LumenImageMetadata.KEY_SAMPLE_ID)
                : "(unknown)";
        final String segmentIdDisplay = parsedMetadata.isAvailable()
                ? parsedMetadata.getMetadata().get().getRequired(LumenImageMetadata.KEY_SEGMENT_ID)
                : "(unknown)";

        final GenericDialog dialog = new GenericDialog(
                "Robust Ellipse Reconstruction - All Lumina");
        dialog.addChoice(
                "Run mode:",
                new String[] {
                        AllLumenBatchConfiguration.DisplayPolicy.SUMMARY_ONLY.name(),
                        AllLumenBatchConfiguration.DisplayPolicy.SILENT.name()
                },
                AllLumenBatchConfiguration.DisplayPolicy.SUMMARY_ONLY.name());
        dialog.addNumericField(
                "Minimum Lumen area in pixels:",
                AllLumenBatchConfiguration.DEFAULT_MINIMUM_LUMEN_AREA_PX,
                0);
        dialog.addCheckbox("Skip border-touching components", true);
        dialog.addMessage("--- Production saving ---");
        dialog.addCheckbox("Save production outputs", true);
        dialog.addCheckbox("Refresh dataset aggregate tables", true);
        dialog.addMessage("Sample ID: " + sampleIdDisplay);
        dialog.addMessage("Segment ID: " + segmentIdDisplay);
        dialog.addMessage("Dataset root (from metadata): "
                + (metaDatasetRoot.isPresent() ? metaDatasetRoot.get() : "(absent)"));
        if (parsedMetadata.isAvailable()) {
            final LumenImageMetadata meta = parsedMetadata.getMetadata().get();
            final String safeSample = FilenameSanitizer.sanitizeOrUnnamed(
                    meta.getRequired(LumenImageMetadata.KEY_SAMPLE_ID));
            final String safeSegment = FilenameSanitizer.sanitizeOrUnnamed(
                    meta.getRequired(LumenImageMetadata.KEY_SEGMENT_ID));
            dialog.addMessage("Masked-classified input: " + safeTitle(image));
            dialog.addMessage("Reconstructed-classified output: "
                    + FilenameSanitizer.reconstructedClassifiedFilename(safeSample, safeSegment));
        } else {
            dialog.addMessage("Masked-classified input: " + safeTitle(image));
            dialog.addMessage("Reconstructed-classified output: (requires metadata)");
        }
        dialog.addMessage(
                "The source masked-classified image is never modified.\n"
                        + "Legacy images without dataset metadata can still run diagnostically.");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }

        final String modeName = dialog.getNextChoice();
        final int minimumArea = (int) Math.round(dialog.getNextNumber());
        final boolean skipBorder = dialog.getNextBoolean();
        final boolean saveProduction = dialog.getNextBoolean();
        final boolean refreshAggregates = dialog.getNextBoolean();
        if (minimumArea < 1) {
            IJ.error(
                    "Robust Ellipse Reconstruction - All Lumina",
                    "Minimum Lumen area must be >= 1.");
            return;
        }

        final AllLumenBatchConfiguration.DisplayPolicy policy =
                AllLumenBatchConfiguration.DisplayPolicy.valueOf(modeName);
        final AllLumenBatchConfiguration configuration;
        if (policy == AllLumenBatchConfiguration.DisplayPolicy.SILENT) {
            configuration = new AllLumenBatchConfiguration(
                    minimumArea, skipBorder, policy, false, false, false, false);
        } else {
            configuration = new AllLumenBatchConfiguration(
                    minimumArea, skipBorder, policy, true, true, true, true);
        }

        final ByteProcessor classified = (ByteProcessor) processor;
        final AllLumenBatchProcessor batchProcessor = new AllLumenBatchProcessor();
        final AllLumenBatchResult result;
        try {
            result = batchProcessor.process(classified, configuration);
        } catch (RuntimeException ex) {
            IJ.log("All-lumen batch failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction - All Lumina",
                    "All-lumen batch failed:\n" + String.valueOf(ex.getMessage()));
            return;
        }

        logBatchSummary(image.getTitle(), result, configuration, saveProduction);

        if (saveProduction) {
            try {
                saveProductionOutputs(
                        image, classified, result, minimumArea,
                        metaDatasetRoot, parsedMetadata, refreshAggregates);
            } catch (final Exception ex) {
                IJ.log("ERROR: Production save failed; SUCCESS not written.");
                IJ.log(String.valueOf(ex.getMessage()));
                IJ.error(
                        "Robust Ellipse Reconstruction - All Lumina",
                        "Production save failed:\n" + String.valueOf(ex.getMessage())
                                + "\nDiagnostic results remain available.");
            }
        } else {
            IJ.log("Production saving disabled; diagnostic-only behavior.");
        }

        if (policy == AllLumenBatchConfiguration.DisplayPolicy.SILENT) {
            return;
        }

        if (configuration.isCreateResultsTable()) {
            showResultsTable(image, result);
        }
        if (configuration.isCreateDecisionOverview()) {
            showDecisionOverview(image.getTitle(), classified, result);
        }
        if (result.getCombinedChangedPixelCount() > 0) {
            if (configuration.isCreateCombinedDiagnosticCopy()) {
                final ByteProcessor copy =
                        batchProcessor.createCombinedDiagnosticCopy(classified, result);
                new ImagePlus(
                        "Diagnostic Reconstructed All Lumina - " + image.getTitle(),
                        copy).show();
            }
            if (configuration.isCreateCombinedChangeMask()) {
                final ByteProcessor mask = batchProcessor.createCombinedChangeMask(result);
                new ImagePlus(
                        "Diagnostic Reconstruction Change Mask All Lumina - "
                                + image.getTitle(),
                        mask).show();
            }
        }
    }

    private static void saveProductionOutputs(
            final ImagePlus image,
            final ByteProcessor classified,
            final AllLumenBatchResult result,
            final int minimumArea,
            final Optional<String> metaDatasetRoot,
            final LumenImageMetadata.ParseResult parsedMetadata,
            final boolean refreshAggregates) throws IOException {
        if (result.getProcessingErrorCount() > 0) {
            throw new IOException(
                    "Processing errors occurred; refusing production SUCCESS.");
        }
        if (!parsedMetadata.isAvailable()) {
            throw new IOException(
                    "Production saving requires Lumen coordinate metadata on the "
                            + "masked-classified TIFF. Run tiled classification first, "
                            + "or supply a production-tagged image.");
        }
        final ProductionDatasetLayout layout = resolveDatasetRoot(metaDatasetRoot);
        layout.ensureDirectoriesExist();
        final AllLuminaDatasetWriter.Result saved = new AllLuminaDatasetWriter().write(
                image,
                classified,
                result,
                minimumArea,
                layout,
                parsedMetadata.getMetadata().get(),
                refreshAggregates);
        IJ.log("Production reconstruction saved.");
        IJ.log("Reconstructed classified: " + saved.reconstructedClassified);
        IJ.log("Per-segment measurements: " + saved.measurements);
        IJ.log("Per-segment diagnostics: " + saved.diagnostics);
        IJ.log("Run ID: " + saved.runId);
    }

    private static ProductionDatasetLayout resolveDatasetRoot(
            final Optional<String> metaDatasetRoot) {
        if (metaDatasetRoot.isPresent()) {
            final Path path = Paths.get(metaDatasetRoot.get().trim());
            if (Files.isDirectory(path)) {
                Prefs.set(PREFS_DATASET_ROOT, path.toAbsolutePath().toString());
                return new ProductionDatasetLayout(path);
            }
            IJ.log("Metadata dataset root is absent or invalid: " + metaDatasetRoot.get());
        }
        final String prefs = Prefs.get(PREFS_DATASET_ROOT, "");
        final DirectoryChooser chooser = new DirectoryChooser("Choose dataset root directory");
        if (prefs != null && !prefs.trim().isEmpty()) {
            // DirectoryChooser has no setDirectory in all IJ versions; message only.
            IJ.log("Previous dataset root preference: " + prefs);
        }
        final String chosen = chooser.getDirectory();
        if (chosen == null || chosen.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset root selection cancelled.");
        }
        final File root = new File(chosen.trim());
        Prefs.set(PREFS_DATASET_ROOT, root.getAbsolutePath());
        return new ProductionDatasetLayout(root);
    }

    private static Optional<String> readDatasetRootFromInfo(final ImagePlus image) {
        final Object info = image.getProperty("Info");
        if (info == null) {
            return Optional.empty();
        }
        final String text = String.valueOf(info);
        final String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.startsWith("lumen.dataset_root=")) {
                final String value = line.substring("lumen.dataset_root=".length()).trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private static void logBatchSummary(
            final String sourceTitle,
            final AllLumenBatchResult result,
            final AllLumenBatchConfiguration configuration,
            final boolean saveProduction) {
        IJ.log("All-lumen diagnostic batch summary:");
        IJ.log("sourceTitle,width,height,totalComponents,eligibleComponents,"
                + "reconstructedComponents,combinedChangedPixels,belowMinimumArea,"
                + "borderSkipped,noInterval,insufficientEvidence,overlapRejected,"
                + "unsupported,candidateConflictComponents,processingErrors,"
                + "sourceChecksumBefore,sourceChecksumAfter,sourceUnchanged,"
                + "totalProcessingTimeMs,displayPolicy");
        IJ.log(String.format(Locale.US,
                "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%.3f,%s",
                sanitize(sourceTitle),
                Integer.valueOf(result.getImageWidth()),
                Integer.valueOf(result.getImageHeight()),
                Integer.valueOf(result.getTotalComponentCount()),
                Integer.valueOf(result.getEligibleComponentCount()),
                Integer.valueOf(result.getReconstructedComponentCount()),
                Integer.valueOf(result.getCombinedChangedPixelCount()),
                Integer.valueOf(result.getBelowMinimumAreaCount()),
                Integer.valueOf(result.getBorderSkippedCount()),
                Integer.valueOf(result.getNoIntervalCount()),
                Integer.valueOf(result.getInsufficientEvidenceCount()),
                Integer.valueOf(result.getOverlapRejectedCount()),
                Integer.valueOf(result.getUnsupportedCount()),
                Integer.valueOf(result.getCandidateConflictComponentCount()),
                Integer.valueOf(result.getProcessingErrorCount()),
                Integer.valueOf(result.getSourceChecksumBefore()),
                Integer.valueOf(result.getSourceChecksumAfter()),
                Boolean.toString(result.isSourceUnchanged()),
                Double.valueOf(result.getTotalProcessingTimeNanoseconds() / 1.0e6),
                configuration.getDisplayPolicy().name()));
        IJ.log("IMPORTANT:");
        IJ.log("All Lumen components were analyzed against the unchanged original classified");
        IJ.log("image.");
        IJ.log("Only non-conflicting strong single-artifact candidates were combined.");
        IJ.log("No per-Lumen diagnostic windows were created.");
        IJ.log("The source image was not modified.");
        if (!saveProduction) {
            IJ.log("Outputs were not saved automatically.");
        }
    }

    private static void showResultsTable(
            final ImagePlus classifiedImage,
            final AllLumenBatchResult result) {
        final ResultsTable table = new ResultsTable();
        final List<LumenBatchItemResult> items = result.getItemResults();
        final LumenResultCoordinateEnricher enricher = new LumenResultCoordinateEnricher();
        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            final Rectangle bounds = item.getSourceBounds();
            final LumenResultCoordinateEnricher.EnrichedValues coords =
                    enricher.enrich(classifiedImage, item);
            table.incrementCounter();
            table.addValue("Component_ID", item.getComponentId());
            table.addValue("Area", item.getSourceArea());
            table.addValue("Min_X", bounds.x);
            table.addValue("Min_Y", bounds.y);
            table.addValue("Max_X", bounds.x + bounds.width - 1);
            table.addValue("Max_Y", bounds.y + bounds.height - 1);
            table.addValue("Touches_Border", item.isTouchesImageBorder() ? 1.0 : 0.0);
            table.addValue("Status", item.getStatus().name());
            table.addValue("Reason", item.getReason());
            table.addValue("Interval_Selected", item.isIntervalSelected() ? 1.0 : 0.0);
            table.addValue("Interval_Selection_Reason", item.getIntervalSelectionReason());
            table.addValue("Evidence_Decision", item.getArtifactEvidenceDecision());
            table.addValue("Reconstruction_Candidate_Decision",
                    item.getReconstructionCandidateDecision());
            addIntOrBlank(table, "Boundary_Edges", item.getBoundaryEdgeCount());
            addIntOrBlank(table, "Interval_Edges", item.getIntervalEdgeCount());
            addIntOrBlank(table, "Core_Points", item.getCorePointCount());
            addDoubleOrBlank(table, "Minimum_Depth_Sigma", item.getMinimumDepthSigma());
            addDoubleOrBlank(table, "Mean_Depth_Sigma", item.getMeanDepthSigma());
            addDoubleOrBlank(table, "Core_Fraction", item.getCoreFraction());
            addDoubleOrBlank(table, "Normalized_Integrated_Excess",
                    item.getNormalizedIntegratedInwardExcess());
            addDoubleOrBlank(table, "Retained_RMS_Improvement",
                    item.getRetainedRmsImprovementFraction());
            addIntOrBlank(table, "Associated_Components",
                    item.getIntervalAssociatedComponentCount());
            addIntOrBlank(table, "Matched_Seeds", item.getMatchedExteriorSeedPixelCount());
            addIntOrBlank(table, "Unmatched_Seeds", item.getUnmatchedExteriorSeedPixelCount());
            addIntOrBlank(table, "Other_Lumen_Inside_Ellipse",
                    item.getOtherLumenInsideRefittedEllipseCount());
            table.addValue("Changed_Pixels", item.getCandidatePixelCount());
            addDoubleOrBlank(table, "Original_Circularity", item.getOriginalCircularity());
            addDoubleOrBlank(table, "Reconstructed_Circularity",
                    item.getDiagnosticReconstructedCircularity());
            addDoubleOrBlank(table, "Circularity_Change",
                    item.getDiagnosticCircularityChange());
            addDoubleOrBlank(table, "Added_Area_Fraction", item.getAddedAreaFraction());
            table.addValue("Processing_Time_ms",
                    item.getProcessingTimeNanoseconds() / 1.0e6);

            table.addValue("sample_id", coords.sampleId);
            table.addValue("segment_id", coords.segmentId);
            table.addValue("segment_order", coords.segmentOrder);
            table.addValue("segment_orientation", coords.segmentOrientation);
            table.addValue("crop_x_offset_px", coords.cropXOffsetPx);
            table.addValue("crop_y_offset_px", coords.cropYOffsetPx);
            table.addValue("original_width_px", coords.originalWidthPx);
            table.addValue("original_height_px", coords.originalHeightPx);
            table.addValue("pixel_width_um", coords.pixelWidthUm);
            table.addValue("pixel_height_um", coords.pixelHeightUm);
            table.addValue("centroid_x_crop_px", coords.centroidXCropPx);
            table.addValue("centroid_y_crop_px", coords.centroidYCropPx);
            table.addValue("centroid_x_original_px", coords.centroidXOriginalPx);
            table.addValue("centroid_y_original_px", coords.centroidYOriginalPx);
            table.addValue("centroid_x_original_um", coords.centroidXOriginalUm);
            table.addValue("centroid_y_original_um", coords.centroidYOriginalUm);
            table.addValue("segment_axis_position_um", coords.segmentAxisPositionUm);
            table.addValue("global_axis_position_um", coords.globalAxisPositionUm);
            table.addValue("area_um2", coords.areaUm2);
            table.addValue("bounding_box_width_um", coords.boundingBoxWidthUm);
            table.addValue("bounding_box_height_um", coords.boundingBoxHeightUm);
        }
        table.show("All Lumen Reconstruction Results");
    }

    private static void showDecisionOverview(
            final String sourceTitle,
            final ByteProcessor classified,
            final AllLumenBatchResult result) {
        final int srcW = classified.getWidth();
        final int srcH = classified.getHeight();
        final int legendW = 220;
        final ColorProcessor rgb = new ColorProcessor(srcW + legendW, srcH);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                final int value = classified.get(x, y) & 0xff;
                final Color base;
                if (value == ClassifierLabels.WOOD_RESULT_VALUE) {
                    base = COLOR_WOOD;
                } else if (value == ClassifierLabels.RAY_RESULT_VALUE) {
                    base = COLOR_RAY;
                } else if (value == ClassifierLabels.LUMEN_RESULT_VALUE) {
                    base = COLOR_LUMEN;
                } else if (value == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                    base = COLOR_BACKGROUND;
                } else {
                    base = COLOR_OTHER;
                }
                rgb.setColor(darken(base));
                rgb.drawPixel(x, y);
            }
        }

        final List<LumenBatchItemResult> items = result.getItemResults();
        rgb.setFont(new Font("Monospaced", Font.BOLD, 10));
        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            final Color color = colorForStatus(item.getStatus());
            final Rectangle bounds = item.getSourceBounds();
            rgb.setColor(color);
            rgb.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            final int cx = bounds.x + bounds.width / 2;
            final int cy = bounds.y + bounds.height / 2;
            rgb.drawString(Integer.toString(item.getComponentId()), cx, cy);
        }

        int ly = 18;
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font("Monospaced", Font.BOLD, 11));
        rgb.drawString("DECISION LEGEND", srcW + 10, ly);
        ly += 16;
        rgb.setFont(new Font("Monospaced", Font.PLAIN, 10));
        ly = legendLine(rgb, srcW + 10, ly, Color.GREEN, "RECONSTRUCTED");
        ly = legendLine(rgb, srcW + 10, ly, Color.CYAN, "NO_INTERVAL");
        ly = legendLine(rgb, srcW + 10, ly, Color.YELLOW, "INSUFFICIENT");
        ly = legendLine(rgb, srcW + 10, ly, new Color(255, 140, 0), "OTHER_LUMEN");
        ly = legendLine(rgb, srcW + 10, ly, new Color(60, 60, 60), "BELOW_MIN");
        ly = legendLine(rgb, srcW + 10, ly, Color.MAGENTA, "BORDER");
        ly = legendLine(rgb, srcW + 10, ly, Color.WHITE, "CONFLICT");
        ly = legendLine(rgb, srcW + 10, ly, Color.RED, "UNSUPPORTED/ERROR");
        legendLine(rgb, srcW + 10, ly, new Color(255, 180, 120), "OTHER REJECT");

        new ImagePlus("All Lumen Decisions - " + sourceTitle, rgb).show();
    }

    private static int legendLine(
            final ColorProcessor rgb,
            final int x,
            final int y,
            final Color color,
            final String text) {
        rgb.setColor(color);
        rgb.fillRect(x, y - 9, 10, 10);
        rgb.setColor(Color.WHITE);
        rgb.drawString(text, x + 14, y);
        return y + 14;
    }

    private static Color colorForStatus(final LumenBatchItemResult.Status status) {
        switch (status) {
            case RECONSTRUCTED_SINGLE_ARTIFACT:
                return Color.GREEN;
            case NO_INTERVAL_SELECTED:
                return Color.CYAN;
            case INSUFFICIENT_ARTIFACT_EVIDENCE:
                return Color.YELLOW;
            case OTHER_LUMEN_OVERLAP:
                return new Color(255, 140, 0);
            case BELOW_MINIMUM_AREA:
                return new Color(60, 60, 60);
            case BORDER_TOUCHING_SKIPPED:
                return Color.MAGENTA;
            case CANDIDATE_CONFLICT:
                return Color.WHITE;
            case UNSUPPORTED_BOUNDARY:
            case PROCESSING_ERROR:
            case INITIAL_ANALYSIS_FAILED:
                return Color.RED;
            default:
                return new Color(255, 180, 120);
        }
    }

    private static Color darken(final Color color) {
        return new Color(
                Math.max(0, color.getRed() / 2),
                Math.max(0, color.getGreen() / 2),
                Math.max(0, color.getBlue() / 2));
    }

    private static void addIntOrBlank(final ResultsTable table, final String column, final int value) {
        if (value < 0) {
            table.addValue(column, Double.NaN);
        } else {
            table.addValue(column, value);
        }
    }

    private static void addDoubleOrBlank(
            final ResultsTable table,
            final String column,
            final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            table.addValue(column, Double.NaN);
        } else {
            table.addValue(column, value);
        }
    }

    private static String sanitize(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', '_');
    }

    private static String safeTitle(final ImagePlus image) {
        return image.getTitle() == null ? "Untitled" : image.getTitle();
    }
}
