package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

/**
 * ImageJ entry point for all-lumen diagnostic batch processing.
 *
 * <p>SUMMARY_ONLY may show combined outputs only. SILENT creates no windows.</p>
 */
public final class AllLumenSummaryPlugin implements PlugIn {

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
        IJ.log("Automatic file saving: DISABLED");
        IJ.log("Directory processing: NOT IMPLEMENTED");
        IJ.log("Production reconstruction: NOT APPLIED");

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

        final GenericDialog dialog = new GenericDialog(
                "Robust Ellipse Reconstruction - All Lumina");
        dialog.addChoice(
                "Run mode:",
                new String[] {
                        AllLumenBatchConfiguration.DisplayPolicy.SUMMARY_ONLY.name(),
                        AllLumenBatchConfiguration.DisplayPolicy.SILENT.name()
                },
                AllLumenBatchConfiguration.DisplayPolicy.SUMMARY_ONLY.name());
        dialog.addNumericField("Minimum Lumen area:", 100, 0);
        dialog.addCheckbox("Skip border-touching components", true);
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }

        final String modeName = dialog.getNextChoice();
        final int minimumArea = (int) Math.round(dialog.getNextNumber());
        final boolean skipBorder = dialog.getNextBoolean();
        if (minimumArea <= 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - All Lumina",
                    "Minimum Lumen area must be positive.");
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

        logBatchSummary(image.getTitle(), result, configuration);

        if (policy == AllLumenBatchConfiguration.DisplayPolicy.SILENT) {
            return;
        }

        if (configuration.isCreateResultsTable()) {
            showResultsTable(result);
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

    private static void logBatchSummary(
            final String sourceTitle,
            final AllLumenBatchResult result,
            final AllLumenBatchConfiguration configuration) {
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
        IJ.log("Outputs were not saved automatically.");
        IJ.log("Production dataset processing is not yet enabled.");
    }

    private static void showResultsTable(final AllLumenBatchResult result) {
        final ResultsTable table = new ResultsTable();
        final List<LumenBatchItemResult> items = result.getItemResults();
        for (int i = 0; i < items.size(); i++) {
            final LumenBatchItemResult item = items.get(i);
            final Rectangle bounds = item.getSourceBounds();
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
        rgb.fillRect(x, y - 8, 10, 10);
        rgb.setColor(Color.WHITE);
        rgb.drawString(text, x + 14, y);
        return y + 14;
    }

    private static Color colorForStatus(final LumenBatchItemResult.Status status) {
        if (status == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT) {
            return Color.GREEN;
        }
        if (status == LumenBatchItemResult.Status.NO_INTERVAL_SELECTED) {
            return Color.CYAN;
        }
        if (status == LumenBatchItemResult.Status.INSUFFICIENT_ARTIFACT_EVIDENCE) {
            return Color.YELLOW;
        }
        if (status == LumenBatchItemResult.Status.OTHER_LUMEN_OVERLAP) {
            return new Color(255, 140, 0);
        }
        if (status == LumenBatchItemResult.Status.BELOW_MINIMUM_AREA) {
            return new Color(60, 60, 60);
        }
        if (status == LumenBatchItemResult.Status.BORDER_TOUCHING_SKIPPED) {
            return Color.MAGENTA;
        }
        if (status == LumenBatchItemResult.Status.CANDIDATE_CONFLICT) {
            return Color.WHITE;
        }
        if (status == LumenBatchItemResult.Status.UNSUPPORTED_BOUNDARY
                || status == LumenBatchItemResult.Status.INITIAL_ANALYSIS_FAILED
                || status == LumenBatchItemResult.Status.PROCESSING_ERROR) {
            return Color.RED;
        }
        return new Color(255, 180, 120);
    }

    private static Color darken(final Color color) {
        return new Color(
                Math.max(0, color.getRed() / 2),
                Math.max(0, color.getGreen() / 2),
                Math.max(0, color.getBlue() / 2));
    }

    private static void addIntOrBlank(
            final ResultsTable table,
            final String heading,
            final int value) {
        if (value < 0) {
            table.addValue(heading, Double.NaN);
        } else {
            table.addValue(heading, value);
        }
    }

    private static void addDoubleOrBlank(
            final ResultsTable table,
            final String heading,
            final double value) {
        table.addValue(heading, value);
    }

    private static String sanitize(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace(',', ';').replace('\n', ' ');
    }
}
