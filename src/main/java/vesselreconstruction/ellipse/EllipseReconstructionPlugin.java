package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ImageJ entry point for the initial free-ellipse diagnostic prototype.
 * Fits all observed boundary support points, previews proposed fill, and never
 * modifies the classified input image.
 */
public final class EllipseReconstructionPlugin implements PlugIn {

    private static final Color COLOR_BACKGROUND = new Color(24, 24, 24);
    private static final Color COLOR_SELECTED_LUMEN = new Color(110, 110, 110);
    private static final Color COLOR_WOOD = new Color(0, 180, 0);
    private static final Color COLOR_RAY = new Color(255, 140, 0);
    private static final Color COLOR_BACKGROUND_FILL = new Color(220, 40, 40);
    private static final Color COLOR_OTHER_CLASS = new Color(220, 0, 220);
    private static final Color COLOR_OTHER_LUMEN = new Color(0, 40, 140);
    private static final Color COLOR_SUPPORT = Color.CYAN;
    private static final Color COLOR_ELLIPSE = Color.YELLOW;
    private static final Color COLOR_CENTER = Color.WHITE;
    private static final Color COLOR_MAJOR = Color.WHITE;
    private static final Color COLOR_MINOR = new Color(200, 200, 200);

    private static final int ELLIPSE_SAMPLES = 720;

    @Override
    public void run(final String arg) {
        IJ.log("Robust Ellipse Reconstruction Prototype started.");
        IJ.log("Mode: PROPOSED_FILL_COMPONENT_DIAGNOSTICS");
        IJ.log("Input format: TRAINABLE_WEKA_CREATE_RESULT");
        IJ.log("Whole-ellipse fill: DIAGNOSTIC");
        IJ.log("Interval-associated fill: DIAGNOSTIC");
        IJ.log("Component acceptance: NOT IMPLEMENTED");
        IJ.log("Source pixel modification: NOT APPLIED");
        IJ.log("Production reconstruction: NOT APPLIED");
        IJ.log("Diagonal boundary-contact policy:");
        IJ.log("Exact checkerboard contact = RIGHT_TURN_SAME_FACE");
        IJ.log("All other ambiguous vertices = REJECTED");
        IJ.log("Component membership = 8_CONNECTED");
        IJ.log("Boundary loops at point contacts = SEPARATE_SIMPLE_LOOPS");

        IJ.log("Weka class-number mapping:");
        IJ.log("Class " + ClassifierLabels.WOOD + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.WOOD));
        IJ.log("Class " + ClassifierLabels.RAY + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.RAY));
        IJ.log("Class " + ClassifierLabels.LUMEN + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.LUMEN));
        IJ.log("Class " + ClassifierLabels.BACKGROUND + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.BACKGROUND));

        IJ.log("Weka result-image encoding:");
        IJ.log("Pixel value " + ClassifierLabels.WOOD_RESULT_VALUE
                + " = Class " + ClassifierLabels.WOOD + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.WOOD));
        IJ.log("Pixel value " + ClassifierLabels.RAY_RESULT_VALUE
                + " = Class " + ClassifierLabels.RAY + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.RAY));
        IJ.log("Pixel value " + ClassifierLabels.LUMEN_RESULT_VALUE
                + " = Class " + ClassifierLabels.LUMEN + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.LUMEN));
        IJ.log("Pixel value " + ClassifierLabels.BACKGROUND_RESULT_VALUE
                + " = Class " + ClassifierLabels.BACKGROUND + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.BACKGROUND));

        final ImagePlus image = WindowManager.getCurrentImage();
        if (image == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "An 8-bit classified image must be opened and active.");
            return;
        }

        final ImageProcessor processor = image.getProcessor();
        if (processor == null || !(processor instanceof ByteProcessor)) {
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "The active image must be an 8-bit classified image.\n"
                            + "RGB, 16-bit and 32-bit images are not supported.");
            return;
        }

        final ByteProcessor classified = (ByteProcessor) processor;
        final byte[] sourcePixels = (byte[]) classified.getPixels();
        final byte[] originalPixels = copyBytes(sourcePixels);

        logResultPixelHistogram(classified);

        final InitialEllipseAnalysisResult result;
        try {
            result = new InitialEllipseAnalyzer().analyze(classified);
        } catch (IllegalArgumentException ex) {
            IJ.log("Initial ellipse analysis failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Initial ellipse analysis failed:\n" + ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            IJ.log("Initial ellipse analysis failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Initial ellipse analysis failed:\n" + String.valueOf(ex.getMessage()));
            return;
        }

        // Verify the classified image was not modified.
        final byte[] afterPixels = (byte[]) classified.getPixels();
        if (!sameBytes(originalPixels, afterPixels)) {
            IJ.log("ERROR: classified image pixels were modified unexpectedly.");
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Internal error: classified image was modified.");
            return;
        }

        logAnalysis(image.getTitle(), result);

        final OrderedBoundaryLoop outerLoop;
        final OrderedBoundaryResidualProfile residualProfile;
        try {
            outerLoop = new OrderedBoundaryExtractor()
                    .selectOuterLoop(result.getSelectedLumenPixels());
            residualProfile = OrderedBoundaryResidualProfile.create(
                    outerLoop,
                    result.getEllipseFitResult().getFittedEllipse());
        } catch (AmbiguousBoundaryVertexException ex) {
            handleAmbiguousBoundaryVertex(image, classified, result, ex);
            return;
        } catch (IllegalArgumentException ex) {
            IJ.log("Ordered boundary analysis failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Ordered boundary analysis failed:\n" + ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            IJ.log("Ordered boundary analysis failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Ordered boundary analysis failed:\n" + String.valueOf(ex.getMessage()));
            return;
        }

        // Re-verify source pixels after ordered-boundary diagnostics.
        if (!sameBytes(originalPixels, (byte[]) classified.getPixels())) {
            IJ.log("ERROR: classified image pixels were modified unexpectedly.");
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Internal error: classified image was modified.");
            return;
        }

        logOrderedBoundary(residualProfile);

        final InwardOutlierDetectionResult outlierDetection;
        try {
            outlierDetection = new InwardOutlierIntervalDetector().detect(residualProfile);
        } catch (IllegalArgumentException ex) {
            IJ.log("Inward-outlier interval detection failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Inward-outlier interval detection failed:\n" + ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            IJ.log("Inward-outlier interval detection failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Inward-outlier interval detection failed:\n"
                            + String.valueOf(ex.getMessage()));
            return;
        }

        if (!sameBytes(originalPixels, (byte[]) classified.getPixels())) {
            IJ.log("ERROR: classified image pixels were modified unexpectedly.");
            IJ.error(
                    "Robust Ellipse Reconstruction Prototype",
                    "Internal error: classified image was modified.");
            return;
        }

        logInwardOutlierDetection(outlierDetection);
        showVisualization(image.getTitle(), classified, result, residualProfile, outlierDetection);
        showResidualProfilePlot(image.getTitle(), residualProfile, outlierDetection);

        SingleIntervalEllipseRefitResult refitResult = null;
        if (outlierDetection.isIntervalSelected()) {
            try {
                refitResult = new SingleIntervalEllipseRefitter().refit(outlierDetection);
            } catch (IllegalArgumentException ex) {
                IJ.log("Single-interval ellipse refit failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Single-interval ellipse refit failed:\n" + ex.getMessage());
                return;
            } catch (RuntimeException ex) {
                IJ.log("Single-interval ellipse refit failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Single-interval ellipse refit failed:\n"
                                + String.valueOf(ex.getMessage()));
                return;
            }

            if (!sameBytes(originalPixels, (byte[]) classified.getPixels())) {
                IJ.log("ERROR: classified image pixels were modified unexpectedly.");
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Internal error: classified image was modified.");
                return;
            }

            logSingleIntervalRefit(refitResult);
            showSingleIntervalRefitVisualization(
                    image.getTitle(), result, residualProfile, refitResult);
            showBaselineVsRefitResidualPlot(
                    image.getTitle(), residualProfile, refitResult);

            final RefittedEllipseFillPreviewResult fillPreview;
            try {
                fillPreview = new RefittedEllipseFillPreviewAnalyzer()
                        .analyze(classified, result, refitResult);
            } catch (IllegalArgumentException ex) {
                IJ.log("Refitted ellipse fill preview failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Refitted ellipse fill preview failed:\n" + ex.getMessage());
                return;
            } catch (RuntimeException ex) {
                IJ.log("Refitted ellipse fill preview failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Refitted ellipse fill preview failed:\n"
                                + String.valueOf(ex.getMessage()));
                return;
            }

            if (!sameBytes(originalPixels, (byte[]) classified.getPixels())) {
                IJ.log("ERROR: classified image pixels were modified unexpectedly.");
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Internal error: classified image was modified.");
                return;
            }

            logFillPreview(fillPreview);
            showFillPreviewVisualization(
                    image.getTitle(), residualProfile, outlierDetection, fillPreview);
            showBaselineVsRefitFillDifference(
                    image.getTitle(), residualProfile, outlierDetection, fillPreview);

            final ProposedFillComponentAnalysisResult componentAnalysis;
            try {
                componentAnalysis = new ProposedFillComponentAnalyzer()
                        .analyze(classified, fillPreview, outlierDetection);
            } catch (IllegalArgumentException ex) {
                IJ.log("Proposed-fill component analysis failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Proposed-fill component analysis failed:\n" + ex.getMessage());
                return;
            } catch (RuntimeException ex) {
                IJ.log("Proposed-fill component analysis failed: " + ex.getMessage());
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Proposed-fill component analysis failed:\n"
                                + String.valueOf(ex.getMessage()));
                return;
            }

            if (!sameBytes(originalPixels, (byte[]) classified.getPixels())) {
                IJ.log("ERROR: classified image pixels were modified unexpectedly.");
                IJ.error(
                        "Robust Ellipse Reconstruction Prototype",
                        "Internal error: classified image was modified.");
                return;
            }

            logProposedFillComponents(componentAnalysis);
            showProposedFillComponentsVisualization(
                    image.getTitle(), residualProfile, outlierDetection, fillPreview,
                    componentAnalysis);
            showWholeVsAssociatedFillComparison(
                    image.getTitle(), residualProfile, outlierDetection, fillPreview,
                    componentAnalysis);
            showSummaryDialog(
                    result, residualProfile, outlierDetection, refitResult, fillPreview,
                    componentAnalysis);
            return;
        }

        showSummaryDialog(result, residualProfile, outlierDetection, null, null, null);
    }

    private static void logResultPixelHistogram(final ByteProcessor classified) {
        final int[] counts = InitialEllipseAnalyzer.countResultPixelHistogram(classified);
        IJ.log("Trainable Weka result pixel histogram:");
        IJ.log("resultValue,classNumber,className,pixelCount,recognized");
        for (int value = 0; value < counts.length; value++) {
            if (counts[value] <= 0) {
                continue;
            }
            if (value >= ClassifierLabels.WOOD_RESULT_VALUE
                    && value <= ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                final int classNumber = ClassifierLabels.classNumberOfResultValue(value);
                IJ.log(value
                        + "," + classNumber
                        + "," + ClassifierLabels.nameOf(classNumber)
                        + "," + counts[value]
                        + ",true");
            } else {
                IJ.log(value + ",-1,UNKNOWN," + counts[value] + ",false");
            }
        }
    }

    private static void logAnalysis(
            final String imageTitle,
            final InitialEllipseAnalysisResult result) {
        final EllipseFitResult fit = result.getEllipseFitResult();
        final EllipseModel ellipse = fit.getFittedEllipse();
        final Rectangle bounds = result.getSelectedComponentBounds();

        IJ.log("Initial ellipse analysis:");
        IJ.log("imageTitle,imageWidth,imageHeight,lumenComponentCount,selectedArea,"
                + "minX,minY,maxX,maxY,boundarySupportPoints,exposedEdgePerimeter,"
                + "centerX,centerY,semiMajorAxis,semiMinorAxis,axisRatio,"
                + "rotationRadians,rotationDegrees,ellipseArea,fitConverged,"
                + "convergenceReason,iterationCount,initialMeanSquaredResidual,"
                + "finalMeanSquaredResidual,rootMeanSquaredResidual,medianResidual,"
                + "medianAbsoluteDeviation,maximumInwardResidual,maximumOutwardResidual,"
                + "totalPixelCentersInsideEllipse,proposedFillPixels,proposedWoodPixels,"
                + "proposedRayPixels,proposedBackgroundPixels,proposedOtherClassPixels,"
                + "otherLumenPixelsInsideEllipse");

        final String row = sanitize(imageTitle)
                + "," + result.getImageWidth()
                + "," + result.getImageHeight()
                + "," + result.getLumenComponentCount()
                + "," + result.getSelectedComponentArea()
                + "," + bounds.x
                + "," + bounds.y
                + "," + (bounds.x + bounds.width - 1)
                + "," + (bounds.y + bounds.height - 1)
                + "," + result.getBoundarySupportPoints().size()
                + "," + result.getExposedEdgePerimeter()
                + "," + fmt(ellipse.getCenterX())
                + "," + fmt(ellipse.getCenterY())
                + "," + fmt(ellipse.getSemiMajorAxis())
                + "," + fmt(ellipse.getSemiMinorAxis())
                + "," + fmt(ellipse.getAxisRatio())
                + "," + fmt(ellipse.getRotationRadians())
                + "," + fmt(Math.toDegrees(ellipse.getRotationRadians()))
                + "," + fmt(ellipse.getArea())
                + "," + fit.isConverged()
                + "," + sanitize(fit.getConvergenceReason())
                + "," + fit.getIterationCount()
                + "," + fmt(fit.getInitialMeanSquaredResidual())
                + "," + fmt(fit.getFinalMeanSquaredResidual())
                + "," + fmt(fit.getRootMeanSquaredResidual())
                + "," + fmt(fit.getMedianResidual())
                + "," + fmt(fit.getMedianAbsoluteDeviation())
                + "," + fmt(fit.getMaximumInwardResidual())
                + "," + fmt(fit.getMaximumOutwardResidual())
                + "," + result.getTotalPixelCentersInsideEllipse()
                + "," + result.getProposedFillPixelCount()
                + "," + result.getProposedWoodPixelCount()
                + "," + result.getProposedRayPixelCount()
                + "," + result.getProposedBackgroundPixelCount()
                + "," + result.getProposedOtherClassPixelCount()
                + "," + result.getOtherLumenPixelsInsideEllipse();
        IJ.log(row);

        IJ.log("IMPORTANT:");
        IJ.log("This is the initial fit to all observed boundary support points.");
        IJ.log("The artifact may influence this ellipse.");
        IJ.log("No support points were excluded.");
        IJ.log("No pixels were changed.");
    }

    private static void logOrderedBoundary(final OrderedBoundaryResidualProfile profile) {
        final OrderedBoundaryLoop loop = profile.getBoundaryLoop();
        final Rectangle bounds = loop.getLatticeBounds();
        final Point2D.Double minPt = profile.getMinimumResidualPoint();
        final Point2D.Double maxPt = profile.getMaximumResidualPoint();

        IJ.log("Ordered outer-boundary summary:");
        IJ.log("loopNumber,edgeCount,signedArea,absoluteArea,distinctVertices,"
                + "minX,minY,maxX,maxY,medianResidual,medianAbsoluteDeviation,"
                + "minimumResidual,minimumResidualIndex,minimumResidualX,minimumResidualY,"
                + "maximumResidual,maximumResidualIndex,maximumResidualX,maximumResidualY,"
                + "negativeResidualCount,positiveResidualCount,zeroResidualCount");
        IJ.log(loop.getLoopNumber()
                + "," + loop.getDirectedEdges().size()
                + "," + fmt(loop.getSignedArea())
                + "," + fmt(loop.getAbsoluteArea())
                + "," + loop.getDistinctVertexCount()
                + "," + bounds.x
                + "," + bounds.y
                + "," + (bounds.x + bounds.width - 1)
                + "," + (bounds.y + bounds.height - 1)
                + "," + fmt(profile.getMedianResidual())
                + "," + fmt(profile.getMedianAbsoluteDeviation())
                + "," + fmt(profile.getMinimumResidual())
                + "," + profile.getMinimumResidualIndex()
                + "," + fmt(minPt.x)
                + "," + fmt(minPt.y)
                + "," + fmt(profile.getMaximumResidual())
                + "," + profile.getMaximumResidualIndex()
                + "," + fmt(maxPt.x)
                + "," + fmt(maxPt.y)
                + "," + profile.getNegativeResidualCount()
                + "," + profile.getPositiveResidualCount()
                + "," + profile.getZeroResidualCount());

        IJ.log("IMPORTANT:");
        IJ.log("Boundary residuals are now ordered around the selected outer loop.");
        IJ.log("No support points were excluded.");
        IJ.log("The ellipse was not refitted.");
        IJ.log("No pixels were changed.");
    }

    private static void logInwardOutlierDetection(final InwardOutlierDetectionResult detection) {
        IJ.log("Inward-outlier interval detection:");
        IJ.log("robustScale,coreThreshold,expansionThreshold,candidateCount,"
                + "intervalSelected,selectionReason,dominanceRatio");
        IJ.log(fmt(detection.getRobustScale())
                + "," + fmt(detection.getCoreThreshold())
                + "," + fmt(detection.getExpansionThreshold())
                + "," + detection.getCandidateIntervals().size()
                + "," + detection.isIntervalSelected()
                + "," + sanitize(detection.getSelectionReason())
                + "," + fmtDominance(detection.getDominanceRatio()));

        IJ.log("Inward-outlier interval candidates:");
        IJ.log("candidateNumber,startIndex,endIndex,wrapsAround,edgeCount,boundaryFraction,"
                + "corePointCount,minimumResidual,minimumResidualIndex,meanResidual,"
                + "integratedInwardExcess,containsGlobalMinimum,startX,startY,endX,endY,selected");
        final List<InwardOutlierInterval> candidates = detection.getCandidateIntervals();
        for (int i = 0; i < candidates.size(); i++) {
            final InwardOutlierInterval interval = candidates.get(i);
            final boolean selected = detection.isIntervalSelected()
                    && detection.getSelectedInterval().equals(interval);
            final Point2D.Double start = interval.getStartSupportMidpoint();
            final Point2D.Double end = interval.getEndSupportMidpoint();
            IJ.log((i + 1)
                    + "," + interval.getStartIndex()
                    + "," + interval.getEndIndex()
                    + "," + interval.isWrapsAround()
                    + "," + interval.getEdgeCount()
                    + "," + fmt(interval.getBoundaryFraction())
                    + "," + interval.getCorePointCount()
                    + "," + fmt(interval.getMinimumResidual())
                    + "," + interval.getMinimumResidualIndex()
                    + "," + fmt(interval.getMeanResidual())
                    + "," + fmt(interval.getIntegratedInwardExcess())
                    + "," + interval.isContainsGlobalMinimum()
                    + "," + fmt(start.x)
                    + "," + fmt(start.y)
                    + "," + fmt(end.x)
                    + "," + fmt(end.y)
                    + "," + selected);
        }

        if (!detection.isIntervalSelected()) {
            IJ.log("Selected inward-outlier interval:");
            IJ.log("NONE");
        } else {
            final InwardOutlierInterval selected = detection.getSelectedInterval();
            IJ.log("Selected inward-outlier interval:");
            IJ.log("startIndex,endIndex,wrapsAround,edgeCount,boundaryFraction,"
                    + "minimumResidual,minimumResidualIndex,integratedInwardExcess,dominanceRatio");
            IJ.log(selected.getStartIndex()
                    + "," + selected.getEndIndex()
                    + "," + selected.isWrapsAround()
                    + "," + selected.getEdgeCount()
                    + "," + fmt(selected.getBoundaryFraction())
                    + "," + fmt(selected.getMinimumResidual())
                    + "," + selected.getMinimumResidualIndex()
                    + "," + fmt(selected.getIntegratedInwardExcess())
                    + "," + fmtDominance(detection.getDominanceRatio()));
        }

        IJ.log("IMPORTANT:");
        IJ.log("The interval is diagnostic only.");
        IJ.log("No support points were excluded from the ellipse fit.");
        IJ.log("The ellipse was not refitted.");
        IJ.log("No pixels were changed.");
        IJ.log("Reconstruction was not applied.");
    }

    private static String fmtDominance(final double value) {
        if (Double.isInfinite(value)) {
            return "Infinity";
        }
        return fmt(value);
    }

    private static void showVisualization(
            final String originalTitle,
            final ByteProcessor classified,
            final InitialEllipseAnalysisResult result,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection) {
        final int width = result.getImageWidth();
        final int height = result.getImageHeight();
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(COLOR_BACKGROUND);
        rgb.fill();

        paintPoints(rgb, result.getSelectedLumenPixels(), COLOR_SELECTED_LUMEN);

        final byte[] pixels = (byte[]) classified.getPixels();
        final List<Point> proposed = result.getProposedFillPixels();
        for (int i = 0; i < proposed.size(); i++) {
            final Point p = proposed.get(i);
            final int resultValue = pixels[p.y * width + p.x] & 0xff;
            final Color color;
            if (resultValue == ClassifierLabels.WOOD_RESULT_VALUE) {
                color = COLOR_WOOD;
            } else if (resultValue == ClassifierLabels.RAY_RESULT_VALUE) {
                color = COLOR_RAY;
            } else if (resultValue == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                color = COLOR_BACKGROUND_FILL;
            } else {
                color = COLOR_OTHER_CLASS;
            }
            rgb.setColor(color);
            rgb.drawPixel(p.x, p.y);
        }

        paintOtherLumenInsideEllipse(rgb, classified, result);

        final EllipseModel ellipse = result.getEllipseFitResult().getFittedEllipse();
        drawSupportPoints(rgb, result.getBoundarySupportPoints());
        drawOrderedResidualBoundary(rgb, residualProfile);
        drawSelectedInwardInterval(rgb, residualProfile, outlierDetection);
        drawFittedEllipse(rgb, ellipse);
        drawAxes(rgb, ellipse);
        drawTextPanel(rgb, result, residualProfile);

        final ImagePlus debug = new ImagePlus(
                "Initial Free Ellipse Fit - " + originalTitle, rgb);
        debug.show();
    }

    private static void paintOtherLumenInsideEllipse(
            final ColorProcessor rgb,
            final ByteProcessor classified,
            final InitialEllipseAnalysisResult result) {
        final EllipseModel ellipse = result.getEllipseFitResult().getFittedEllipse();
        final int width = result.getImageWidth();
        final int height = result.getImageHeight();
        final byte[] pixels = (byte[]) classified.getPixels();
        final java.util.HashSet<Long> selected = new java.util.HashSet<Long>();
        final List<Point> selectedPixels = result.getSelectedLumenPixels();
        for (int i = 0; i < selectedPixels.size(); i++) {
            final Point p = selectedPixels.get(i);
            selected.add((((long) p.x) << 32) ^ (p.y & 0xffffffffL));
        }

        rgb.setColor(COLOR_OTHER_LUMEN);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] & 0xff) != ClassifierLabels.LUMEN_RESULT_VALUE) {
                    continue;
                }
                if (selected.contains((((long) x) << 32) ^ (y & 0xffffffffL))) {
                    continue;
                }
                if (ellipse.normalizedRadius(x + 0.5, y + 0.5) <= 1.0 + 1.0e-12) {
                    rgb.drawPixel(x, y);
                }
            }
        }
    }

    private static void paintPoints(
            final ColorProcessor rgb,
            final List<Point> points,
            final Color color) {
        rgb.setColor(color);
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            rgb.drawPixel(p.x, p.y);
        }
    }

    private static void drawSupportPoints(
            final ColorProcessor rgb,
            final List<Point2D.Double> supportPoints) {
        rgb.setColor(COLOR_SUPPORT);
        for (int i = 0; i < supportPoints.size(); i++) {
            final Point2D.Double p = supportPoints.get(i);
            final int x = (int) Math.round(p.x);
            final int y = (int) Math.round(p.y);
            if (x >= 0 && y >= 0 && x < rgb.getWidth() && y < rgb.getHeight()) {
                rgb.drawPixel(x, y);
            }
        }
    }

    private static void drawFittedEllipse(
            final ColorProcessor rgb,
            final EllipseModel ellipse) {
        rgb.setColor(COLOR_ELLIPSE);
        Point2D.Double previous = null;
        Point2D.Double first = null;
        for (int i = 0; i <= ELLIPSE_SAMPLES; i++) {
            final double t = 2.0 * Math.PI * i / ELLIPSE_SAMPLES;
            final Point2D.Double point = ellipse.pointAt(t);
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                drawClippedLine(rgb, previous.x, previous.y, point.x, point.y);
            }
            previous = point;
        }
        if (first != null && previous != null) {
            drawClippedLine(rgb, previous.x, previous.y, first.x, first.y);
        }
    }

    private static void drawAxes(
            final ColorProcessor rgb,
            final EllipseModel ellipse) {
        final double cx = ellipse.getCenterX();
        final double cy = ellipse.getCenterY();
        final double cos = Math.cos(ellipse.getRotationRadians());
        final double sin = Math.sin(ellipse.getRotationRadians());
        final double a = ellipse.getSemiMajorAxis();
        final double b = ellipse.getSemiMinorAxis();

        rgb.setColor(COLOR_MAJOR);
        drawClippedLine(
                rgb,
                cx - a * cos,
                cy - a * sin,
                cx + a * cos,
                cy + a * sin);

        rgb.setColor(COLOR_MINOR);
        drawClippedLine(
                rgb,
                cx + b * sin,
                cy - b * cos,
                cx - b * sin,
                cy + b * cos);

        rgb.setColor(COLOR_CENTER);
        final int ix = (int) Math.round(cx);
        final int iy = (int) Math.round(cy);
        drawClippedLine(rgb, ix - 4, iy, ix + 4, iy);
        drawClippedLine(rgb, ix, iy - 4, ix, iy + 4);
    }

    private static void drawTextPanel(
            final ColorProcessor rgb,
            final InitialEllipseAnalysisResult result,
            final OrderedBoundaryResidualProfile residualProfile) {
        final EllipseFitResult fit = result.getEllipseFitResult();
        final EllipseModel ellipse = fit.getFittedEllipse();
        final String text = "INITIAL FREE ELLIPSE FIT\n"
                + "ORDERED OUTER BOUNDARY\n"
                + "NO OUTLIER THRESHOLD\n"
                + "NO REFIT / NO RECONSTRUCTION\n"
                + "\n"
                + "center=(" + fmt(ellipse.getCenterX()) + ", "
                + fmt(ellipse.getCenterY()) + ")\n"
                + "axes=(" + fmt(ellipse.getSemiMajorAxis()) + ", "
                + fmt(ellipse.getSemiMinorAxis()) + ")\n"
                + "axis ratio=" + fmt(ellipse.getAxisRatio()) + "\n"
                + "RMS=" + fmt(fit.getRootMeanSquaredResidual()) + "\n"
                + "edges=" + residualProfile.getSamples().size() + "\n"
                + "min res=" + fmt(residualProfile.getMinimumResidual()) + "\n"
                + "max res=" + fmt(residualProfile.getMaximumResidual()) + "\n"
                + "fill=" + result.getProposedFillPixelCount();

        final int panelHeight = Math.max(40, (int) Math.floor(rgb.getHeight() * 0.35));
        final int panelWidth = Math.min(340, Math.max(180, rgb.getWidth() / 2));
        rgb.setColor(new Color(0, 0, 0));
        rgb.setRoi(4, 4, panelWidth, panelHeight);
        rgb.fill();
        rgb.setRoi((Roi) null);

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        final String[] lines = text.split("\n");
        int y = 16;
        final int maxY = 4 + panelHeight - 4;
        for (int i = 0; i < lines.length; i++) {
            if (y > maxY) {
                break;
            }
            rgb.drawString(lines[i], 10, y);
            y += 13;
        }
    }

    private static void drawOrderedResidualBoundary(
            final ColorProcessor rgb,
            final OrderedBoundaryResidualProfile profile) {
        final double minRes = profile.getMinimumResidual();
        final double maxRes = profile.getMaximumResidual();
        final List<OrderedBoundaryResidualProfile.Sample> samples = profile.getSamples();

        for (int i = 0; i < samples.size(); i++) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(i);
            final double residual = sample.getResidual();
            final Color color;
            if (residual == 0.0) {
                color = Color.WHITE;
            } else if (residual < 0.0) {
                final double t = minRes < 0.0
                        ? clamp01(residual / minRes)
                        : 0.0;
                color = interpolate(Color.CYAN, Color.RED, t);
            } else {
                final double t = maxRes > 0.0
                        ? clamp01(residual / maxRes)
                        : 0.0;
                color = interpolate(Color.CYAN, Color.BLUE, t);
            }
            rgb.setColor(color);
            drawClippedLine(
                    rgb,
                    sample.getEdgeStartX(),
                    sample.getEdgeStartY(),
                    sample.getEdgeEndX(),
                    sample.getEdgeEndY());
        }

        final Point2D.Double minPt = profile.getMinimumResidualPoint();
        final Point2D.Double maxPt = profile.getMaximumResidualPoint();
        drawFilledSquare(rgb, minPt.x, minPt.y, 3, Color.WHITE);
        drawOutlinedSquare(rgb, maxPt.x, maxPt.y, 4, Color.WHITE);
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        rgb.drawString("MIN INWARD",
                clamp((int) Math.round(minPt.x) + 6, 0, rgb.getWidth() - 1),
                clamp((int) Math.round(minPt.y) - 4, 0, rgb.getHeight() - 1));
        rgb.drawString("MAX OUTWARD",
                clamp((int) Math.round(maxPt.x) + 6, 0, rgb.getWidth() - 1),
                clamp((int) Math.round(maxPt.y) + 14, 0, rgb.getHeight() - 1));
    }

    private static void drawSelectedInwardInterval(
            final ColorProcessor rgb,
            final OrderedBoundaryResidualProfile profile,
            final InwardOutlierDetectionResult detection) {
        if (!detection.isIntervalSelected()) {
            return;
        }
        final InwardOutlierInterval interval = detection.getSelectedInterval();
        final List<OrderedBoundaryResidualProfile.Sample> samples = profile.getSamples();
        final Color magenta = new Color(255, 0, 255);
        rgb.setColor(magenta);
        for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
            final int index = indexObj.intValue();
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(index);
            // Thicker overlay: draw the unit edge and a parallel offset.
            drawClippedLine(
                    rgb,
                    sample.getEdgeStartX(),
                    sample.getEdgeStartY(),
                    sample.getEdgeEndX(),
                    sample.getEdgeEndY());
            drawClippedLine(
                    rgb,
                    sample.getEdgeStartX() + 1,
                    sample.getEdgeStartY(),
                    sample.getEdgeEndX() + 1,
                    sample.getEdgeEndY());
            drawClippedLine(
                    rgb,
                    sample.getEdgeStartX(),
                    sample.getEdgeStartY() + 1,
                    sample.getEdgeEndX(),
                    sample.getEdgeEndY() + 1);
        }

        final Point2D.Double start = interval.getStartSupportMidpoint();
        final Point2D.Double end = interval.getEndSupportMidpoint();
        drawFilledSquare(rgb, start.x, start.y, 3, Color.WHITE);
        drawOutlinedSquare(rgb, end.x, end.y, 4, Color.WHITE);
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        rgb.drawString("INWARD INTERVAL START",
                clamp((int) Math.round(start.x) + 6, 0, rgb.getWidth() - 1),
                clamp((int) Math.round(start.y) - 4, 0, rgb.getHeight() - 1));
        rgb.drawString("INWARD INTERVAL END",
                clamp((int) Math.round(end.x) + 6, 0, rgb.getWidth() - 1),
                clamp((int) Math.round(end.y) + 14, 0, rgb.getHeight() - 1));
    }

    private static void showResidualProfilePlot(
            final String sourceTitle,
            final OrderedBoundaryResidualProfile profile,
            final InwardOutlierDetectionResult detection) {
        final int edgeCount = profile.getSamples().size();
        final int width = Math.max(600, edgeCount);
        final int height = 320;
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        double minRes = profile.getMinimumResidual();
        double maxRes = profile.getMaximumResidual();
        minRes = Math.min(minRes, detection.getCoreThreshold());
        minRes = Math.min(minRes, detection.getExpansionThreshold());
        minRes = Math.min(minRes, profile.getMedianResidual() - profile.getMedianAbsoluteDeviation());
        maxRes = Math.max(maxRes, profile.getMedianResidual() + profile.getMedianAbsoluteDeviation());
        if (minRes == maxRes) {
            minRes -= 0.01;
            maxRes += 0.01;
        }
        final double pad = 0.05 * (maxRes - minRes);
        minRes -= pad;
        maxRes += pad;

        final int left = 50;
        final int right = width - 20;
        final int top = 50;
        final int bottom = height - 40;

        if (detection.isIntervalSelected()) {
            final InwardOutlierInterval interval = detection.getSelectedInterval();
            rgb.setColor(new Color(80, 0, 80));
            for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
                final int i = indexObj.intValue();
                final int x = left + (int) Math.round(
                        (right - left) * (i / (double) Math.max(1, edgeCount - 1)));
                rgb.drawLine(x, top, x, bottom);
            }
        }

        // Zero line
        rgb.setColor(Color.WHITE);
        final int zeroY = residualToY(0.0, minRes, maxRes, top, bottom);
        rgb.drawLine(left, zeroY, right, zeroY);

        // Median and MAD lines
        rgb.setColor(Color.YELLOW);
        final int medY = residualToY(profile.getMedianResidual(), minRes, maxRes, top, bottom);
        rgb.drawLine(left, medY, right, medY);
        rgb.setColor(new Color(255, 165, 0));
        final int madLo = residualToY(
                profile.getMedianResidual() - profile.getMedianAbsoluteDeviation(),
                minRes, maxRes, top, bottom);
        final int madHi = residualToY(
                profile.getMedianResidual() + profile.getMedianAbsoluteDeviation(),
                minRes, maxRes, top, bottom);
        rgb.drawLine(left, madLo, right, madLo);
        rgb.drawLine(left, madHi, right, madHi);

        // Core and expansion thresholds
        rgb.setColor(Color.MAGENTA);
        final int coreY = residualToY(detection.getCoreThreshold(), minRes, maxRes, top, bottom);
        rgb.drawLine(left, coreY, right, coreY);
        rgb.setColor(new Color(255, 140, 0));
        final int expY = residualToY(
                detection.getExpansionThreshold(), minRes, maxRes, top, bottom);
        rgb.drawLine(left, expY, right, expY);

        final List<OrderedBoundaryResidualProfile.Sample> samples = profile.getSamples();
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < samples.size(); i++) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(i);
            final int x = left + (int) Math.round(
                    (right - left) * (i / (double) Math.max(1, edgeCount - 1)));
            final int y = residualToY(sample.getResidual(), minRes, maxRes, top, bottom);
            if (sample.getResidual() < 0.0) {
                rgb.setColor(Color.RED);
            } else if (sample.getResidual() > 0.0) {
                rgb.setColor(Color.BLUE);
            } else {
                rgb.setColor(Color.WHITE);
            }
            rgb.drawPixel(x, y);
            if (prevX >= 0) {
                rgb.setColor(Color.CYAN);
                rgb.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }

        final int minX = left + (int) Math.round(
                (right - left) * (profile.getMinimumResidualIndex()
                        / (double) Math.max(1, edgeCount - 1)));
        final int minY = residualToY(profile.getMinimumResidual(), minRes, maxRes, top, bottom);
        final int maxX = left + (int) Math.round(
                (right - left) * (profile.getMaximumResidualIndex()
                        / (double) Math.max(1, edgeCount - 1)));
        final int maxY = residualToY(profile.getMaximumResidual(), minRes, maxRes, top, bottom);
        drawFilledSquare(rgb, minX, minY, 3, Color.WHITE);
        drawFilledSquare(rgb, maxX, maxY, 3, Color.LIGHT_GRAY);

        if (detection.isIntervalSelected()) {
            final InwardOutlierInterval interval = detection.getSelectedInterval();
            final int startX = left + (int) Math.round(
                    (right - left) * (interval.getStartIndex()
                            / (double) Math.max(1, edgeCount - 1)));
            final int endX = left + (int) Math.round(
                    (right - left) * (interval.getEndIndex()
                            / (double) Math.max(1, edgeCount - 1)));
            rgb.setColor(Color.WHITE);
            rgb.drawLine(startX, top, startX, bottom);
            rgb.drawLine(endX, top, endX, bottom);
            rgb.drawString("SELECTED INWARD INTERVAL", startX + 4, top + 14);
        } else {
            rgb.setColor(Color.WHITE);
            rgb.drawString("NO INTERVAL SELECTED", 10, 58);
            rgb.drawString(detection.getSelectionReason(), 10, 72);
        }

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rgb.drawString("ORDERED OUTER BOUNDARY RESIDUAL PROFILE", 10, 16);
        rgb.drawString("CORE/EXPANSION THRESHOLDS SHOWN", 10, 30);
        rgb.drawString("NO POINTS EXCLUDED / NO REFIT / NO RECONSTRUCTION", 10, 44);
        rgb.drawString("min=" + fmt(profile.getMinimumResidual())
                + " @" + profile.getMinimumResidualIndex()
                + "  max=" + fmt(profile.getMaximumResidual())
                + " @" + profile.getMaximumResidualIndex(), 10, height - 24);
        rgb.drawString("median=" + fmt(profile.getMedianResidual())
                + "  MAD=" + fmt(profile.getMedianAbsoluteDeviation())
                + "  edges=" + edgeCount, 10, height - 10);

        new ImagePlus("Ordered Boundary Residual Profile - " + sourceTitle, rgb).show();
    }

    private static int residualToY(
            final double residual,
            final double minRes,
            final double maxRes,
            final int top,
            final int bottom) {
        final double t = (residual - minRes) / (maxRes - minRes);
        return bottom - (int) Math.round(t * (bottom - top));
    }

    private static void drawFilledSquare(
            final ColorProcessor rgb,
            final double cx,
            final double cy,
            final int radius,
            final Color color) {
        rgb.setColor(color);
        final int x = (int) Math.round(cx);
        final int y = (int) Math.round(cy);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                final int px = x + dx;
                final int py = y + dy;
                if (px >= 0 && py >= 0 && px < rgb.getWidth() && py < rgb.getHeight()) {
                    rgb.drawPixel(px, py);
                }
            }
        }
    }

    private static void drawOutlinedSquare(
            final ColorProcessor rgb,
            final double cx,
            final double cy,
            final int radius,
            final Color color) {
        rgb.setColor(color);
        final int x0 = (int) Math.round(cx) - radius;
        final int y0 = (int) Math.round(cy) - radius;
        final int x1 = (int) Math.round(cx) + radius;
        final int y1 = (int) Math.round(cy) + radius;
        drawClippedLine(rgb, x0, y0, x1, y0);
        drawClippedLine(rgb, x1, y0, x1, y1);
        drawClippedLine(rgb, x1, y1, x0, y1);
        drawClippedLine(rgb, x0, y1, x0, y0);
    }

    private static Color interpolate(final Color a, final Color b, final double tRaw) {
        final double t = clamp01(tRaw);
        final int r = (int) Math.round(a.getRed() + t * (b.getRed() - a.getRed()));
        final int g = (int) Math.round(a.getGreen() + t * (b.getGreen() - a.getGreen()));
        final int bl = (int) Math.round(a.getBlue() + t * (b.getBlue() - a.getBlue()));
        return new Color(r, g, bl);
    }

    private static double clamp01(final double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static void drawClippedLine(
            final ColorProcessor rgb,
            final double x0,
            final double y0,
            final double x1,
            final double y1) {
        final int width = rgb.getWidth();
        final int height = rgb.getHeight();
        final int ix0 = clamp((int) Math.round(x0), 0, width - 1);
        final int iy0 = clamp((int) Math.round(y0), 0, height - 1);
        final int ix1 = clamp((int) Math.round(x1), 0, width - 1);
        final int iy1 = clamp((int) Math.round(y1), 0, height - 1);
        rgb.drawLine(ix0, iy0, ix1, iy1);
    }

    private static int clamp(final int value, final int min, final int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static void logSingleIntervalRefit(final SingleIntervalEllipseRefitResult refit) {
        final EllipseModel baseline = refit.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refitted = refit.getIntervalExcludedFit().getFittedEllipse();
        IJ.log("Single inward-interval ellipse refit:");
        IJ.log("totalOuterSupportPoints,retainedSupportPoints,excludedSupportPoints,"
                + "retainedSupportFraction,excludedSupportFraction,"
                + "baselineCenterX,baselineCenterY,baselineSemiMajorAxis,baselineSemiMinorAxis,"
                + "baselineAxisRatio,baselineRotationDegrees,baselineEllipseArea,"
                + "refittedCenterX,refittedCenterY,refittedSemiMajorAxis,refittedSemiMinorAxis,"
                + "refittedAxisRatio,refittedRotationDegrees,refittedEllipseArea,"
                + "centerShift,semiMajorAxisAbsoluteChange,semiMajorAxisRelativeChange,"
                + "semiMinorAxisAbsoluteChange,semiMinorAxisRelativeChange,axisRatioAbsoluteChange,"
                + "rotationChangeDegrees,ellipseAreaAbsoluteChange,ellipseAreaRelativeChange,"
                + "baselineAllBoundaryRms,refittedAllBoundaryRms,"
                + "baselineRetainedRms,refittedRetainedRms,"
                + "baselineExcludedRms,refittedExcludedRms,"
                + "baselineExcludedMeanResidual,refittedExcludedMeanResidual,"
                + "baselineExcludedMinimumResidual,refittedExcludedMinimumResidual,"
                + "baselineConverged,baselineConvergenceReason,"
                + "refittedConverged,refittedConvergenceReason");
        IJ.log(refit.getTotalSupportPointCount()
                + "," + refit.getRetainedSupportPointCount()
                + "," + refit.getExcludedSupportPointCount()
                + "," + fmt(refit.getRetainedSupportFraction())
                + "," + fmt(refit.getExcludedSupportFraction())
                + "," + fmt(baseline.getCenterX())
                + "," + fmt(baseline.getCenterY())
                + "," + fmt(baseline.getSemiMajorAxis())
                + "," + fmt(baseline.getSemiMinorAxis())
                + "," + fmt(baseline.getAxisRatio())
                + "," + fmt(Math.toDegrees(baseline.getRotationRadians()))
                + "," + fmt(baseline.getArea())
                + "," + fmt(refitted.getCenterX())
                + "," + fmt(refitted.getCenterY())
                + "," + fmt(refitted.getSemiMajorAxis())
                + "," + fmt(refitted.getSemiMinorAxis())
                + "," + fmt(refitted.getAxisRatio())
                + "," + fmt(Math.toDegrees(refitted.getRotationRadians()))
                + "," + fmt(refitted.getArea())
                + "," + fmt(refit.getCenterShift())
                + "," + fmt(refit.getSemiMajorAxisAbsoluteChange())
                + "," + fmt(refit.getSemiMajorAxisRelativeChange())
                + "," + fmt(refit.getSemiMinorAxisAbsoluteChange())
                + "," + fmt(refit.getSemiMinorAxisRelativeChange())
                + "," + fmt(refit.getAxisRatioAbsoluteChange())
                + "," + fmt(refit.getRotationChangeDegrees())
                + "," + fmt(refit.getEllipseAreaAbsoluteChange())
                + "," + fmt(refit.getEllipseAreaRelativeChange())
                + "," + fmt(refit.getBaselineAllBoundaryRmsResidual())
                + "," + fmt(refit.getRefittedAllBoundaryRmsResidual())
                + "," + fmt(refit.getBaselineRetainedRmsResidual())
                + "," + fmt(refit.getRefittedRetainedRmsResidual())
                + "," + fmt(refit.getBaselineExcludedRmsResidual())
                + "," + fmt(refit.getRefittedExcludedRmsResidual())
                + "," + fmt(refit.getBaselineExcludedMeanResidual())
                + "," + fmt(refit.getRefittedExcludedMeanResidual())
                + "," + fmt(refit.getBaselineExcludedMinimumResidual())
                + "," + fmt(refit.getRefittedExcludedMinimumResidual())
                + "," + refit.getOuterBoundaryBaselineFit().isConverged()
                + "," + sanitize(refit.getOuterBoundaryBaselineFit().getConvergenceReason())
                + "," + refit.getIntervalExcludedFit().isConverged()
                + "," + sanitize(refit.getIntervalExcludedFit().getConvergenceReason()));

        IJ.log("IMPORTANT:");
        IJ.log("The baseline fit uses all selected outer-boundary midpoints.");
        IJ.log("The diagnostic refit excludes exactly the selected inward interval.");
        IJ.log("No second interval was detected.");
        IJ.log("No iterative exclusion was performed.");
        IJ.log("The refitted ellipse has not been accepted.");
        IJ.log("No pixels were changed.");
        IJ.log("Reconstruction was not applied.");
    }

    private static void showSingleIntervalRefitVisualization(
            final String originalTitle,
            final InitialEllipseAnalysisResult analysis,
            final OrderedBoundaryResidualProfile residualProfile,
            final SingleIntervalEllipseRefitResult refit) {
        final int width = analysis.getImageWidth();
        final int height = analysis.getImageHeight();
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(new Color(20, 20, 20));
        rgb.fill();

        paintPoints(rgb, analysis.getSelectedLumenPixels(), new Color(110, 110, 110));

        final InwardOutlierInterval interval = refit.getExcludedInterval();
        final boolean[] excluded = new boolean[residualProfile.getSamples().size()];
        for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
            excluded[indexObj.intValue()] = true;
        }

        final List<OrderedBoundaryResidualProfile.Sample> samples = residualProfile.getSamples();
        for (int i = 0; i < samples.size(); i++) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(i);
            rgb.setColor(excluded[i] ? Color.MAGENTA : Color.CYAN);
            drawClippedLine(
                    rgb,
                    sample.getEdgeStartX(),
                    sample.getEdgeStartY(),
                    sample.getEdgeEndX(),
                    sample.getEdgeEndY());
        }

        final EllipseModel baseline = refit.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refitted = refit.getIntervalExcludedFit().getFittedEllipse();
        drawFittedEllipse(rgb, baseline, Color.YELLOW);
        drawFittedEllipse(rgb, refitted, Color.WHITE);
        drawAxesColored(rgb, baseline, new Color(180, 180, 0), new Color(180, 180, 0), Color.YELLOW);
        drawAxesColored(rgb, refitted, Color.LIGHT_GRAY, Color.LIGHT_GRAY, Color.WHITE);

        final Point2D.Double start = interval.getStartSupportMidpoint();
        final Point2D.Double end = interval.getEndSupportMidpoint();
        drawFilledSquare(rgb, start.x, start.y, 3, Color.WHITE);
        drawOutlinedSquare(rgb, end.x, end.y, 4, Color.WHITE);
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        rgb.drawString("EXCLUDED INTERVAL START",
                clamp((int) Math.round(start.x) + 6, 0, width - 1),
                clamp((int) Math.round(start.y) - 4, 0, height - 1));
        rgb.drawString("EXCLUDED INTERVAL END",
                clamp((int) Math.round(end.x) + 6, 0, width - 1),
                clamp((int) Math.round(end.y) + 14, 0, height - 1));

        drawRefitTextPanel(rgb, refit);

        new ImagePlus("Single Inward-Interval Ellipse Refit - " + originalTitle, rgb).show();
    }

    private static void drawFittedEllipse(
            final ColorProcessor rgb,
            final EllipseModel ellipse,
            final Color color) {
        rgb.setColor(color);
        Point2D.Double previous = null;
        Point2D.Double first = null;
        for (int i = 0; i < ELLIPSE_SAMPLES; i++) {
            final double t = 2.0 * Math.PI * i / ELLIPSE_SAMPLES;
            final Point2D.Double point = ellipse.pointAt(t);
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                drawClippedLine(rgb, previous.x, previous.y, point.x, point.y);
            }
            previous = point;
        }
        if (previous != null && first != null) {
            drawClippedLine(rgb, previous.x, previous.y, first.x, first.y);
        }
    }

    private static void drawAxesColored(
            final ColorProcessor rgb,
            final EllipseModel ellipse,
            final Color majorColor,
            final Color minorColor,
            final Color centerColor) {
        final double cos = Math.cos(ellipse.getRotationRadians());
        final double sin = Math.sin(ellipse.getRotationRadians());
        final double cx = ellipse.getCenterX();
        final double cy = ellipse.getCenterY();
        final double a = ellipse.getSemiMajorAxis();
        final double b = ellipse.getSemiMinorAxis();
        rgb.setColor(majorColor);
        drawClippedLine(rgb, cx - a * cos, cy - a * sin, cx + a * cos, cy + a * sin);
        rgb.setColor(minorColor);
        drawClippedLine(rgb, cx + b * sin, cy - b * cos, cx - b * sin, cy + b * cos);
        final int ix = (int) Math.round(cx);
        final int iy = (int) Math.round(cy);
        rgb.setColor(centerColor);
        drawClippedLine(rgb, ix - 4, iy, ix + 4, iy);
        drawClippedLine(rgb, ix, iy - 4, ix, iy + 4);
    }

    private static void drawRefitTextPanel(
            final ColorProcessor rgb,
            final SingleIntervalEllipseRefitResult refit) {
        final EllipseModel baseline = refit.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refitted = refit.getIntervalExcludedFit().getFittedEllipse();
        final int maxPanelHeight = (int) Math.floor(rgb.getHeight() * 0.40);
        final String[] lines = new String[] {
                "SINGLE INWARD-INTERVAL DIAGNOSTIC REFIT",
                "BASELINE = ALL OUTER-BOUNDARY POINTS",
                "REFIT = SELECTED INTERVAL EXCLUDED",
                "NO ITERATIVE EXCLUSION",
                "ELLIPSE NOT ACCEPTED",
                "RECONSTRUCTION NOT APPLIED",
                "retained=" + refit.getRetainedSupportPointCount()
                        + "/" + refit.getTotalSupportPointCount(),
                "center shift=" + fmt(refit.getCenterShift()),
                "baseline axes=(" + fmt(baseline.getSemiMajorAxis())
                        + "," + fmt(baseline.getSemiMinorAxis()) + ")",
                "refitted axes=(" + fmt(refitted.getSemiMajorAxis())
                        + "," + fmt(refitted.getSemiMinorAxis()) + ")",
                "baseline ratio=" + fmt(baseline.getAxisRatio()),
                "refitted ratio=" + fmt(refitted.getAxisRatio()),
                "rotation change=" + fmt(refit.getRotationChangeDegrees()) + " degrees",
                "baseline retained RMS=" + fmt(refit.getBaselineRetainedRmsResidual()),
                "refitted retained RMS=" + fmt(refit.getRefittedRetainedRmsResidual()),
                "excluded mean residual before="
                        + fmt(refit.getBaselineExcludedMeanResidual()),
                "excluded mean residual after="
                        + fmt(refit.getRefittedExcludedMeanResidual())
        };

        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        final int lineHeight = 13;
        int used = 8 + lines.length * lineHeight;
        if (used > maxPanelHeight) {
            used = maxPanelHeight;
        }
        rgb.setColor(new Color(0, 0, 0));
        rgb.setRoi(4, 4, Math.min(420, rgb.getWidth() - 8), used);
        rgb.fill();
        rgb.setRoi(0, 0, rgb.getWidth(), rgb.getHeight());
        rgb.setColor(Color.WHITE);
        int y = 16;
        for (int i = 0; i < lines.length; i++) {
            if (y > maxPanelHeight) {
                break;
            }
            rgb.drawString(lines[i], 8, y);
            y += lineHeight;
        }
    }

    private static void showBaselineVsRefitResidualPlot(
            final String sourceTitle,
            final OrderedBoundaryResidualProfile profile,
            final SingleIntervalEllipseRefitResult refit) {
        final int edgeCount = profile.getSamples().size();
        final int width = Math.max(600, edgeCount);
        final int height = 360;
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        final EllipseModel baseline = refit.getOuterBoundaryBaselineFit().getFittedEllipse();
        final EllipseModel refitted = refit.getIntervalExcludedFit().getFittedEllipse();
        final List<OrderedBoundaryResidualProfile.Sample> samples = profile.getSamples();
        final double[] baselineResiduals = new double[edgeCount];
        final double[] refitResiduals = new double[edgeCount];
        double minRes = Double.POSITIVE_INFINITY;
        double maxRes = Double.NEGATIVE_INFINITY;
        int refitMinIndex = 0;
        for (int i = 0; i < edgeCount; i++) {
            final Point2D.Double mid = samples.get(i).getSupportMidpoint();
            baselineResiduals[i] = baseline.normalizedRadialResidual(mid.x, mid.y);
            refitResiduals[i] = refitted.normalizedRadialResidual(mid.x, mid.y);
            minRes = Math.min(minRes, Math.min(baselineResiduals[i], refitResiduals[i]));
            maxRes = Math.max(maxRes, Math.max(baselineResiduals[i], refitResiduals[i]));
            if (refitResiduals[i] < refitResiduals[refitMinIndex]) {
                refitMinIndex = i;
            }
        }
        if (minRes == maxRes) {
            minRes -= 0.01;
            maxRes += 0.01;
        }
        final double pad = 0.05 * (maxRes - minRes);
        minRes -= pad;
        maxRes += pad;

        final int left = 50;
        final int right = width - 20;
        final int top = 70;
        final int bottom = height - 50;

        final InwardOutlierInterval interval = refit.getExcludedInterval();
        rgb.setColor(new Color(80, 0, 80));
        for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
            final int i = indexObj.intValue();
            final int x = left + (int) Math.round(
                    (right - left) * (i / (double) Math.max(1, edgeCount - 1)));
            rgb.drawLine(x, top, x, bottom);
        }

        rgb.setColor(Color.GRAY);
        final int zeroY = residualToY(0.0, minRes, maxRes, top, bottom);
        rgb.drawLine(left, zeroY, right, zeroY);

        int prevBX = -1;
        int prevBY = -1;
        int prevRX = -1;
        int prevRY = -1;
        for (int i = 0; i < edgeCount; i++) {
            final int x = left + (int) Math.round(
                    (right - left) * (i / (double) Math.max(1, edgeCount - 1)));
            final int by = residualToY(baselineResiduals[i], minRes, maxRes, top, bottom);
            final int ry = residualToY(refitResiduals[i], minRes, maxRes, top, bottom);
            if (prevBX >= 0) {
                rgb.setColor(Color.YELLOW);
                rgb.drawLine(prevBX, prevBY, x, by);
                rgb.setColor(Color.WHITE);
                rgb.drawLine(prevRX, prevRY, x, ry);
            }
            prevBX = x;
            prevBY = by;
            prevRX = x;
            prevRY = ry;
        }

        final int startX = left + (int) Math.round(
                (right - left) * (interval.getStartIndex()
                        / (double) Math.max(1, edgeCount - 1)));
        final int endX = left + (int) Math.round(
                (right - left) * (interval.getEndIndex()
                        / (double) Math.max(1, edgeCount - 1)));
        rgb.setColor(Color.MAGENTA);
        rgb.drawLine(startX, top, startX, bottom);
        rgb.drawLine(endX, top, endX, bottom);

        final int minX = left + (int) Math.round(
                (right - left) * (refitMinIndex / (double) Math.max(1, edgeCount - 1)));
        final int minY = residualToY(refitResiduals[refitMinIndex], minRes, maxRes, top, bottom);
        drawFilledSquare(rgb, minX, minY, 3, Color.WHITE);

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        rgb.drawString("BASELINE VS SINGLE-INTERVAL REFIT", 10, 14);
        rgb.drawString("YELLOW = ALL OUTER-BOUNDARY FIT", 10, 28);
        rgb.drawString("WHITE = INTERVAL-EXCLUDED FIT", 10, 42);
        rgb.drawString("MAGENTA = EXCLUDED SUPPORT INTERVAL", 10, 56);
        rgb.drawString("NO SECOND EXCLUSION / NO ACCEPTANCE / NO RECONSTRUCTION",
                10, height - 34);
        rgb.drawString("retained=" + fmt(refit.getRetainedSupportFraction())
                + "  baseRetRMS=" + fmt(refit.getBaselineRetainedRmsResidual())
                + "  refitRetRMS=" + fmt(refit.getRefittedRetainedRmsResidual()),
                10, height - 20);
        rgb.drawString("exMeanB=" + fmt(refit.getBaselineExcludedMeanResidual())
                + "  exMeanR=" + fmt(refit.getRefittedExcludedMeanResidual())
                + "  shift=" + fmt(refit.getCenterShift())
                + "  dA=" + fmt(refit.getSemiMajorAxisAbsoluteChange())
                + "  dB=" + fmt(refit.getSemiMinorAxisAbsoluteChange())
                + "  dRot=" + fmt(refit.getRotationChangeDegrees()),
                10, height - 6);

        new ImagePlus("Baseline vs Refit Residuals - " + sourceTitle, rgb).show();
    }

    private static void logFillPreview(final RefittedEllipseFillPreviewResult preview) {
        IJ.log("Refitted ellipse fill preview:");
        IJ.log("selectedObservedLumenArea,baselinePixelCentersInsideEllipse,"
                + "refittedPixelCentersInsideEllipse,baselineProposedFillCount,"
                + "refittedProposedFillCount,sharedProposedFillCount,refitOnlyProposedFillCount,"
                + "baselineOnlyProposedFillCount,refittedWoodFillCount,refittedRayFillCount,"
                + "refittedBackgroundFillCount,refittedOtherClassFillCount,"
                + "selectedLumenOutsideRefittedEllipseCount,otherLumenInsideRefittedEllipseCount,"
                + "wouldOverlapAnotherLumenComponent,originalArea,originalPerimeter,"
                + "originalCircularity,previewArea,previewPerimeter,previewCircularity,"
                + "circularityChange,addedAreaFraction,ellipseAreaToObservedLumenAreaRatio");
        IJ.log(preview.getSelectedObservedLumenArea()
                + "," + preview.getBaselinePixelCentersInsideEllipse()
                + "," + preview.getRefittedPixelCentersInsideEllipse()
                + "," + preview.getBaselineProposedFillCount()
                + "," + preview.getRefittedProposedFillCount()
                + "," + preview.getSharedProposedFillCount()
                + "," + preview.getRefitOnlyProposedFillCount()
                + "," + preview.getBaselineOnlyProposedFillCount()
                + "," + preview.getRefittedWoodFillCount()
                + "," + preview.getRefittedRayFillCount()
                + "," + preview.getRefittedBackgroundFillCount()
                + "," + preview.getRefittedOtherClassFillCount()
                + "," + preview.getSelectedLumenOutsideRefittedEllipseCount()
                + "," + preview.getOtherLumenInsideRefittedEllipseCount()
                + "," + preview.isWouldOverlapAnotherLumenComponent()
                + "," + preview.getOriginalArea()
                + "," + preview.getOriginalPerimeter()
                + "," + fmt(preview.getOriginalCircularity())
                + "," + preview.getPreviewArea()
                + "," + preview.getPreviewPerimeter()
                + "," + fmt(preview.getPreviewCircularity())
                + "," + fmt(preview.getCircularityChange())
                + "," + fmt(preview.getAddedAreaFraction())
                + "," + fmt(preview.getEllipseAreaToObservedLumenAreaRatio()));

        IJ.log("IMPORTANT:");
        IJ.log("The preview uses the frozen single-interval refitted ellipse.");
        IJ.log("Every non-Lumen pixel center inside that ellipse is shown as proposed fill.");
        IJ.log("Observed Lumen pixels outside the ellipse are preserved.");
        IJ.log("Other existing Lumen components are not merged into the selected preview.");
        IJ.log("Circularity is diagnostic only.");
        IJ.log("The source image was not modified.");
        IJ.log("The refitted ellipse has not been accepted.");
        IJ.log("Production reconstruction was not applied.");
    }

    private static void showFillPreviewVisualization(
            final String originalTitle,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection,
            final RefittedEllipseFillPreviewResult preview) {
        final int width = preview.getImageWidth();
        final int height = preview.getImageHeight();
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(new Color(20, 20, 20));
        rgb.fill();

        paintPoints(rgb, preview.getSelectedObservedLumenPixels(), new Color(110, 110, 110));
        paintPoints(rgb, preview.getRefittedWoodFillPixels(), new Color(0, 180, 0));
        paintPoints(rgb, preview.getRefittedRayFillPixels(), new Color(255, 140, 0));
        paintPoints(rgb, preview.getRefittedBackgroundFillPixels(), new Color(220, 40, 40));
        paintPoints(rgb, preview.getRefittedOtherClassFillPixels(), new Color(220, 0, 220));
        paintPoints(rgb, preview.getSelectedLumenPixelsOutsideRefittedEllipse(),
                new Color(0, 40, 140));
        paintPoints(rgb, preview.getOtherLumenPixelsInsideRefittedEllipse(),
                new Color(40, 120, 255));

        if (outlierDetection.isIntervalSelected()) {
            final InwardOutlierInterval interval = outlierDetection.getSelectedInterval();
            final List<OrderedBoundaryResidualProfile.Sample> samples =
                    residualProfile.getSamples();
            rgb.setColor(Color.MAGENTA);
            for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
                final OrderedBoundaryResidualProfile.Sample sample =
                        samples.get(indexObj.intValue());
                drawClippedLine(
                        rgb,
                        sample.getEdgeStartX(),
                        sample.getEdgeStartY(),
                        sample.getEdgeEndX(),
                        sample.getEdgeEndY());
            }
        }

        drawFittedEllipse(rgb, preview.getBaselineEllipse(), Color.YELLOW);
        drawFittedEllipse(rgb, preview.getRefittedEllipse(), Color.WHITE);
        drawAxesColored(
                rgb,
                preview.getRefittedEllipse(),
                Color.LIGHT_GRAY,
                Color.LIGHT_GRAY,
                Color.WHITE);
        drawPreviewCardinalBoundary(rgb, preview.getPreviewSelectedLumenPixels(), Color.CYAN);
        drawFillPreviewTextPanel(rgb, preview);

        new ImagePlus("Refitted Ellipse Fill Preview - " + originalTitle, rgb).show();
    }

    private static void showBaselineVsRefitFillDifference(
            final String originalTitle,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection,
            final RefittedEllipseFillPreviewResult preview) {
        final int width = preview.getImageWidth();
        final int height = preview.getImageHeight();
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        paintPoints(rgb, preview.getSelectedObservedLumenPixels(), new Color(60, 60, 60));
        paintPoints(rgb, preview.getSharedProposedFillPixels(), new Color(0, 180, 0));
        paintPoints(rgb, preview.getRefitOnlyProposedFillPixels(), Color.WHITE);
        paintPoints(rgb, preview.getBaselineOnlyProposedFillPixels(), Color.YELLOW);

        if (outlierDetection.isIntervalSelected()) {
            final InwardOutlierInterval interval = outlierDetection.getSelectedInterval();
            final List<OrderedBoundaryResidualProfile.Sample> samples =
                    residualProfile.getSamples();
            rgb.setColor(Color.MAGENTA);
            for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
                final OrderedBoundaryResidualProfile.Sample sample =
                        samples.get(indexObj.intValue());
                drawClippedLine(
                        rgb,
                        sample.getEdgeStartX(),
                        sample.getEdgeStartY(),
                        sample.getEdgeEndX(),
                        sample.getEdgeEndY());
            }
        }

        drawFittedEllipse(rgb, preview.getBaselineEllipse(), new Color(180, 180, 0));
        drawFittedEllipse(rgb, preview.getRefittedEllipse(), Color.LIGHT_GRAY);

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        rgb.drawString("BASELINE VS REFIT FILL DIFFERENCE", 8, 16);
        rgb.drawString("GREEN = SHARED PROPOSED FILL", 8, 30);
        rgb.drawString("WHITE = REFIT-ONLY PROPOSED FILL", 8, 44);
        rgb.drawString("YELLOW = BASELINE-ONLY PROPOSED FILL", 8, 58);
        rgb.drawString("MAGENTA = SELECTED INWARD INTERVAL", 8, 72);
        rgb.drawString("NO PIXELS CHANGED", 8, 86);
        rgb.drawString("NO ACCEPTANCE", 8, 100);
        rgb.drawString("NO PRODUCTION RECONSTRUCTION", 8, 114);
        rgb.drawString("shared=" + preview.getSharedProposedFillCount()
                + "  refit-only=" + preview.getRefitOnlyProposedFillCount()
                + "  baseline-only=" + preview.getBaselineOnlyProposedFillCount(), 8, 130);

        new ImagePlus("Baseline vs Refit Fill Difference - " + originalTitle, rgb).show();
    }

    private static void drawPreviewCardinalBoundary(
            final ColorProcessor rgb,
            final List<Point> members,
            final Color color) {
        final Set<Long> keys = new HashSet<Long>(members.size() * 2);
        for (int i = 0; i < members.size(); i++) {
            final Point p = members.get(i);
            keys.add(Long.valueOf((((long) p.x) << 32) ^ (p.y & 0xffffffffL)));
        }
        final int width = rgb.getWidth();
        final int height = rgb.getHeight();
        final int[] dx = {0, 1, 0, -1};
        final int[] dy = {-1, 0, 1, 0};
        rgb.setColor(color);
        for (int i = 0; i < members.size(); i++) {
            final Point p = members.get(i);
            for (int d = 0; d < dx.length; d++) {
                final int nx = p.x + dx[d];
                final int ny = p.y + dy[d];
                final long nkey = (((long) nx) << 32) ^ (ny & 0xffffffffL);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !keys.contains(Long.valueOf(nkey))) {
                    if (dx[d] == 0 && dy[d] == -1) {
                        drawClippedLine(rgb, p.x, p.y, p.x + 1, p.y);
                    } else if (dx[d] == 1 && dy[d] == 0) {
                        drawClippedLine(rgb, p.x + 1, p.y, p.x + 1, p.y + 1);
                    } else if (dx[d] == 0 && dy[d] == 1) {
                        drawClippedLine(rgb, p.x, p.y + 1, p.x + 1, p.y + 1);
                    } else {
                        drawClippedLine(rgb, p.x, p.y, p.x, p.y + 1);
                    }
                }
            }
        }
    }

    private static void drawFillPreviewTextPanel(
            final ColorProcessor rgb,
            final RefittedEllipseFillPreviewResult preview) {
        final int maxPanelHeight = (int) Math.floor(rgb.getHeight() * 0.45);
        final String[] lines = new String[] {
                "REFITTED ELLIPSE FILL PREVIEW",
                "GREEN = PROPOSED WOOD TO LUMEN",
                "ORANGE = PROPOSED RAY TO LUMEN",
                "RED = PROPOSED BACKGROUND TO LUMEN",
                "MAGENTA = PROPOSED UNKNOWN TO LUMEN",
                "DARK BLUE = OBSERVED LUMEN OUTSIDE ELLIPSE",
                "BRIGHT BLUE = OTHER LUMEN INSIDE ELLIPSE",
                "WHITE = FROZEN REFITTED ELLIPSE",
                "CYAN = PREVIEW LUMEN BOUNDARY",
                "SOURCE IMAGE NOT MODIFIED",
                "ELLIPSE NOT ACCEPTED",
                "PRODUCTION RECONSTRUCTION NOT APPLIED",
                "observed area=" + preview.getSelectedObservedLumenArea(),
                "proposed fill=" + preview.getRefittedProposedFillCount(),
                "Wood fill=" + preview.getRefittedWoodFillCount(),
                "Ray fill=" + preview.getRefittedRayFillCount(),
                "Background fill=" + preview.getRefittedBackgroundFillCount(),
                "other fill=" + preview.getRefittedOtherClassFillCount(),
                "observed Lumen outside ellipse="
                        + preview.getSelectedLumenOutsideRefittedEllipseCount(),
                "other Lumen inside ellipse="
                        + preview.getOtherLumenInsideRefittedEllipseCount(),
                "original circularity=" + fmt(preview.getOriginalCircularity()),
                "preview circularity=" + fmt(preview.getPreviewCircularity()),
                "circularity change=" + fmt(preview.getCircularityChange()),
                "added area fraction=" + fmt(preview.getAddedAreaFraction())
        };
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        final int lineHeight = 13;
        int used = 8 + lines.length * lineHeight;
        if (used > maxPanelHeight) {
            used = maxPanelHeight;
        }
        rgb.setColor(Color.BLACK);
        rgb.setRoi(4, 4, Math.min(460, rgb.getWidth() - 8), used);
        rgb.fill();
        rgb.setRoi(0, 0, rgb.getWidth(), rgb.getHeight());
        rgb.setColor(Color.WHITE);
        int y = 16;
        for (int i = 0; i < lines.length; i++) {
            if (y > maxPanelHeight) {
                break;
            }
            rgb.drawString(lines[i], 8, y);
            y += lineHeight;
        }
    }

    private static void logProposedFillComponents(
            final ProposedFillComponentAnalysisResult analysis) {
        final ProposedFillComponent primary = analysis.getPrimaryIntervalAssociatedComponent();
        final int primaryId = primary == null ? -1 : primary.getComponentId();
        final int primaryArea = primary == null ? 0 : primary.getArea();
        IJ.log("Proposed-fill component summary:");
        IJ.log("totalComponents,intervalAssociatedComponents,nonAssociatedComponents,"
                + "totalProposedFillPixels,intervalAssociatedFillPixels,nonAssociatedFillPixels,"
                + "intervalAssociatedFillFraction,primaryComponentId,primaryComponentArea,"
                + "primaryComponentFillFraction,primaryWithinAssociatedFraction,"
                + "intervalExteriorEdges,uniqueIntervalExteriorSeedPixels,"
                + "matchedIntervalExteriorSeedPixels,unmatchedIntervalExteriorSeedPixels,"
                + "exactlyOneIntervalAssociatedComponent,allProposedFillIsIntervalAssociated,"
                + "wholeEllipsePreviewArea,wholeEllipsePreviewPerimeter,"
                + "wholeEllipsePreviewCircularity,wholeEllipseCircularityChange,"
                + "intervalAssociatedPreviewArea,intervalAssociatedPreviewPerimeter,"
                + "intervalAssociatedPreviewCircularity,intervalAssociatedCircularityChange");
        final RefittedEllipseFillPreviewResult whole = analysis.getWholeEllipsePreview();
        IJ.log(analysis.getTotalComponentCount()
                + "," + analysis.getIntervalAssociatedComponentCount()
                + "," + analysis.getNonAssociatedComponentCount()
                + "," + analysis.getTotalProposedFillPixelCount()
                + "," + analysis.getIntervalAssociatedFillPixelCount()
                + "," + analysis.getNonAssociatedFillPixelCount()
                + "," + fmt(analysis.getIntervalAssociatedFillFraction())
                + "," + primaryId
                + "," + primaryArea
                + "," + fmt(analysis.getPrimaryComponentFillFraction())
                + "," + fmt(analysis.getPrimaryWithinAssociatedFraction())
                + "," + analysis.getIntervalExteriorEdgeCount()
                + "," + analysis.getUniqueIntervalExteriorSeedPixelCount()
                + "," + analysis.getMatchedIntervalExteriorSeedPixelCount()
                + "," + analysis.getUnmatchedIntervalExteriorSeedPixelCount()
                + "," + analysis.isExactlyOneIntervalAssociatedComponent()
                + "," + analysis.isAllProposedFillIsIntervalAssociated()
                + "," + whole.getPreviewArea()
                + "," + whole.getPreviewPerimeter()
                + "," + fmt(analysis.getWholeEllipseCircularity())
                + "," + fmt(analysis.getWholeEllipseCircularityChange())
                + "," + analysis.getIntervalAssociatedPreviewArea()
                + "," + analysis.getIntervalAssociatedPreviewPerimeter()
                + "," + fmt(analysis.getIntervalAssociatedPreviewCircularity())
                + "," + fmt(analysis.getIntervalAssociatedCircularityChange()));

        IJ.log("Proposed-fill components:");
        IJ.log("componentId,area,minX,minY,maxX,maxY,selectedLumenContactPixels,"
                + "selectedLumenContactEdges,intervalExteriorSeedPixels,"
                + "intervalExteriorEdgeHits,intervalAssociated,woodPixels,rayPixels,"
                + "backgroundPixels,otherClassPixels,primary");
        final List<ProposedFillComponent> components = analysis.getAllComponents();
        for (int i = 0; i < components.size(); i++) {
            final ProposedFillComponent c = components.get(i);
            final Rectangle b = c.getBounds();
            final boolean isPrimary = primary != null && primary.getComponentId() == c.getComponentId();
            IJ.log(c.getComponentId()
                    + "," + c.getArea()
                    + "," + b.x
                    + "," + b.y
                    + "," + (b.x + b.width - 1)
                    + "," + (b.y + b.height - 1)
                    + "," + c.getSelectedLumenContactPixelCount()
                    + "," + c.getSelectedLumenContactEdgeCount()
                    + "," + c.getIntervalExteriorSeedPixelCount()
                    + "," + c.getIntervalExteriorEdgeHitCount()
                    + "," + c.isIntervalAssociated()
                    + "," + c.getWoodPixelCount()
                    + "," + c.getRayPixelCount()
                    + "," + c.getBackgroundPixelCount()
                    + "," + c.getOtherClassPixelCount()
                    + "," + isPrimary);
        }

        IJ.log("IMPORTANT:");
        IJ.log("Proposed fill was split using 8-connectivity.");
        IJ.log("Interval association requires an exact match with an interval exterior seed.");
        IJ.log("Components were not bridged or merged.");
        IJ.log("The whole-ellipse preview remains unchanged.");
        IJ.log("The interval-associated preview is diagnostic only.");
        IJ.log("No component has been accepted or rejected.");
        IJ.log("The source image was not modified.");
        IJ.log("Production reconstruction was not applied.");
    }

    private static void showProposedFillComponentsVisualization(
            final String originalTitle,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection,
            final RefittedEllipseFillPreviewResult fillPreview,
            final ProposedFillComponentAnalysisResult analysis) {
        final int srcW = fillPreview.getImageWidth();
        final int srcH = fillPreview.getImageHeight();
        final int panelW = 340;
        final int width = srcW + panelW;
        final int height = Math.max(srcH, 420);
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(new Color(20, 20, 20));
        rgb.fill();

        paintPoints(rgb, fillPreview.getSelectedObservedLumenPixels(), new Color(110, 110, 110));
        paintPoints(rgb, analysis.getNonAssociatedFillPixels(), new Color(255, 140, 0));

        final ProposedFillComponent primary = analysis.getPrimaryIntervalAssociatedComponent();
        final List<ProposedFillComponent> associated = analysis.getIntervalAssociatedComponents();
        for (int i = 0; i < associated.size(); i++) {
            final ProposedFillComponent c = associated.get(i);
            if (primary != null && c.getComponentId() == primary.getComponentId()) {
                continue;
            }
            paintPoints(rgb, c.getPixels(), new Color(0, 160, 0));
        }
        if (primary != null) {
            paintPoints(rgb, primary.getPixels(), new Color(40, 255, 40));
            final Rectangle b = primary.getBounds();
            rgb.setColor(Color.WHITE);
            rgb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            final int labelX = clamp(b.x + b.width + 4, 0, srcW - 1);
            final int labelY = clamp(b.y + 12, 12, srcH - 1);
            rgb.drawString("PRIMARY INTERVAL-ASSOCIATED FILL", labelX, labelY);
        }

        final Set<Long> fillKeys = toPointKeySet(fillPreview.getRefittedProposedFillPixels());
        final List<Point> seeds = analysis.getIntervalExteriorSeedPixels();
        for (int i = 0; i < seeds.size(); i++) {
            final Point seed = seeds.get(i);
            final long key = packPoint(seed.x, seed.y);
            if (fillKeys.contains(Long.valueOf(key))) {
                drawFilledSquare(rgb, seed.x + 0.5, seed.y + 0.5, 2, Color.WHITE);
            } else {
                drawFilledSquare(rgb, seed.x + 0.5, seed.y + 0.5, 2, Color.RED);
            }
        }

        if (outlierDetection.isIntervalSelected()) {
            drawIntervalEdges(rgb, residualProfile, outlierDetection.getSelectedInterval(),
                    Color.MAGENTA);
        }
        drawFittedEllipse(rgb, fillPreview.getRefittedEllipse(), Color.WHITE);
        drawPreviewCardinalBoundary(rgb, fillPreview.getSelectedObservedLumenPixels(), Color.CYAN);

        // Right text panel
        rgb.setColor(new Color(10, 10, 10));
        rgb.setRoi(srcW, 0, panelW, height);
        rgb.fill();
        rgb.setRoi(0, 0, width, height);
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        final String[] lines = new String[] {
                "PROPOSED FILL COMPONENT DIAGNOSTICS",
                "",
                "orange = non-associated fill component",
                "green = interval-associated fill component",
                "bright green = primary associated component",
                "white pixels = matched interval exterior seeds",
                "red pixels = unmatched interval exterior seeds",
                "magenta = selected inward interval",
                "white outline = frozen refitted ellipse",
                "cyan = observed lumen boundary",
                "",
                "components=" + analysis.getTotalComponentCount(),
                "associated components=" + analysis.getIntervalAssociatedComponentCount(),
                "non-associated components=" + analysis.getNonAssociatedComponentCount(),
                "total proposed fill=" + analysis.getTotalProposedFillPixelCount(),
                "associated fill=" + analysis.getIntervalAssociatedFillPixelCount(),
                "non-associated fill=" + analysis.getNonAssociatedFillPixelCount(),
                "associated fraction=" + fmt(analysis.getIntervalAssociatedFillFraction()),
                "primary component=" + (primary == null ? "NONE" : String.valueOf(primary.getComponentId())),
                "primary area=" + (primary == null ? 0 : primary.getArea()),
                "primary / total=" + fmt(analysis.getPrimaryComponentFillFraction()),
                "primary / associated=" + fmt(analysis.getPrimaryWithinAssociatedFraction()),
                "matched seeds=" + analysis.getMatchedIntervalExteriorSeedPixelCount(),
                "unmatched seeds=" + analysis.getUnmatchedIntervalExteriorSeedPixelCount(),
                "",
                "NO COMPONENT ACCEPTANCE",
                "SOURCE IMAGE NOT MODIFIED",
                "NO PRODUCTION RECONSTRUCTION"
        };
        int y = 18;
        for (int i = 0; i < lines.length; i++) {
            rgb.drawString(lines[i], srcW + 8, y);
            y += 14;
        }

        new ImagePlus("Proposed Fill Components - " + originalTitle, rgb).show();
    }

    private static void showWholeVsAssociatedFillComparison(
            final String originalTitle,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection,
            final RefittedEllipseFillPreviewResult fillPreview,
            final ProposedFillComponentAnalysisResult analysis) {
        final int srcW = fillPreview.getImageWidth();
        final int srcH = fillPreview.getImageHeight();
        final int strip = 70;
        final int width = srcW * 2;
        final int height = srcH + strip;
        final ColorProcessor rgb = new ColorProcessor(width, height);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        // Left: whole-ellipse fill
        paintPointsOffset(rgb, fillPreview.getSelectedObservedLumenPixels(),
                new Color(110, 110, 110), 0, strip);
        paintPointsOffset(rgb, fillPreview.getRefittedProposedFillPixels(),
                new Color(0, 180, 0), 0, strip);
        drawPreviewCardinalBoundaryOffset(rgb, fillPreview.getPreviewSelectedLumenPixels(),
                Color.CYAN, 0, strip);
        drawFittedEllipseOffset(rgb, fillPreview.getRefittedEllipse(), Color.WHITE, 0, strip);

        // Right: interval-associated only
        paintPointsOffset(rgb, fillPreview.getSelectedObservedLumenPixels(),
                new Color(110, 110, 110), srcW, strip);
        paintPointsOffset(rgb, analysis.getIntervalAssociatedFillPixels(),
                new Color(40, 255, 40), srcW, strip);
        // Non-associated as dark orange dots (not in preview)
        final List<Point> nonAssoc = analysis.getNonAssociatedFillPixels();
        for (int i = 0; i < nonAssoc.size(); i++) {
            final Point p = nonAssoc.get(i);
            rgb.setColor(new Color(180, 90, 0));
            rgb.drawPixel(p.x + srcW, p.y + strip);
        }
        drawPreviewCardinalBoundaryOffset(rgb,
                analysis.getIntervalAssociatedPreviewLumenPixels(), Color.CYAN, srcW, strip);
        drawFittedEllipseOffset(rgb, fillPreview.getRefittedEllipse(), Color.WHITE, srcW, strip);

        if (outlierDetection.isIntervalSelected()) {
            drawIntervalEdgesOffset(rgb, residualProfile, outlierDetection.getSelectedInterval(),
                    Color.MAGENTA, 0, strip);
            drawIntervalEdgesOffset(rgb, residualProfile, outlierDetection.getSelectedInterval(),
                    Color.MAGENTA, srcW, strip);
        }

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        rgb.drawString("WHOLE ELLIPSE FILL", 12, 24);
        rgb.drawString("INTERVAL-ASSOCIATED FILL ONLY", srcW + 12, 24);
        rgb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        rgb.drawString("whole fill pixels=" + analysis.getTotalProposedFillPixelCount()
                + "  associated fill pixels=" + analysis.getIntervalAssociatedFillPixelCount()
                + "  non-associated fill pixels=" + analysis.getNonAssociatedFillPixelCount(),
                12, 44);
        rgb.drawString("whole circularity=" + fmt(analysis.getWholeEllipseCircularity())
                + "  associated circularity="
                + fmt(analysis.getIntervalAssociatedPreviewCircularity())
                + "  original circularity="
                + fmt(fillPreview.getOriginalCircularity()),
                12, 58);
        rgb.drawString("whole circularity change="
                + fmt(analysis.getWholeEllipseCircularityChange())
                + "  associated circularity change="
                + fmt(analysis.getIntervalAssociatedCircularityChange()),
                12, 72);

        new ImagePlus("Whole Ellipse vs Interval-Associated Fill - " + originalTitle, rgb).show();
    }

    private static void drawIntervalEdges(
            final ColorProcessor rgb,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierInterval interval,
            final Color color) {
        final List<OrderedBoundaryResidualProfile.Sample> samples = residualProfile.getSamples();
        rgb.setColor(color);
        for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(indexObj.intValue());
            drawClippedLine(rgb, sample.getEdgeStartX(), sample.getEdgeStartY(),
                    sample.getEdgeEndX(), sample.getEdgeEndY());
        }
    }

    private static void drawIntervalEdgesOffset(
            final ColorProcessor rgb,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierInterval interval,
            final Color color,
            final int ox,
            final int oy) {
        final List<OrderedBoundaryResidualProfile.Sample> samples = residualProfile.getSamples();
        rgb.setColor(color);
        for (final Integer indexObj : interval.getOrderedBoundaryIndices()) {
            final OrderedBoundaryResidualProfile.Sample sample = samples.get(indexObj.intValue());
            drawClippedLine(rgb,
                    sample.getEdgeStartX() + ox, sample.getEdgeStartY() + oy,
                    sample.getEdgeEndX() + ox, sample.getEdgeEndY() + oy);
        }
    }

    private static void paintPointsOffset(
            final ColorProcessor rgb,
            final List<Point> points,
            final Color color,
            final int ox,
            final int oy) {
        rgb.setColor(color);
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            rgb.drawPixel(p.x + ox, p.y + oy);
        }
    }

    private static void drawPreviewCardinalBoundaryOffset(
            final ColorProcessor rgb,
            final List<Point> members,
            final Color color,
            final int ox,
            final int oy) {
        final Set<Long> keys = toPointKeySet(members);
        final int[] dx = {0, 1, 0, -1};
        final int[] dy = {-1, 0, 1, 0};
        rgb.setColor(color);
        for (int i = 0; i < members.size(); i++) {
            final Point p = members.get(i);
            for (int d = 0; d < dx.length; d++) {
                final int nx = p.x + dx[d];
                final int ny = p.y + dy[d];
                if (!keys.contains(Long.valueOf(packPoint(nx, ny)))) {
                    if (dx[d] == 0 && dy[d] == -1) {
                        drawClippedLine(rgb, p.x + ox, p.y + oy, p.x + 1 + ox, p.y + oy);
                    } else if (dx[d] == 1 && dy[d] == 0) {
                        drawClippedLine(rgb, p.x + 1 + ox, p.y + oy, p.x + 1 + ox, p.y + 1 + oy);
                    } else if (dx[d] == 0 && dy[d] == 1) {
                        drawClippedLine(rgb, p.x + ox, p.y + 1 + oy, p.x + 1 + ox, p.y + 1 + oy);
                    } else {
                        drawClippedLine(rgb, p.x + ox, p.y + oy, p.x + ox, p.y + 1 + oy);
                    }
                }
            }
        }
    }

    private static void drawFittedEllipseOffset(
            final ColorProcessor rgb,
            final EllipseModel ellipse,
            final Color color,
            final int ox,
            final int oy) {
        rgb.setColor(color);
        Point2D.Double previous = null;
        Point2D.Double first = null;
        for (int i = 0; i < ELLIPSE_SAMPLES; i++) {
            final double t = 2.0 * Math.PI * i / ELLIPSE_SAMPLES;
            final Point2D.Double point = ellipse.pointAt(t);
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                drawClippedLine(rgb, previous.x + ox, previous.y + oy, point.x + ox, point.y + oy);
            }
            previous = point;
        }
        if (previous != null && first != null) {
            drawClippedLine(rgb, previous.x + ox, previous.y + oy, first.x + ox, first.y + oy);
        }
    }

    private static Set<Long> toPointKeySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            keys.add(Long.valueOf(packPoint(p.x, p.y)));
        }
        return keys;
    }

    private static long packPoint(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static void showSummaryDialog(
            final InitialEllipseAnalysisResult result,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection,
            final SingleIntervalEllipseRefitResult refitResult,
            final RefittedEllipseFillPreviewResult fillPreview,
            final ProposedFillComponentAnalysisResult componentAnalysis) {
        final StringBuilder message = new StringBuilder();
        if (componentAnalysis != null) {
            final ProposedFillComponent primary =
                    componentAnalysis.getPrimaryIntervalAssociatedComponent();
            final RefittedEllipseFillPreviewResult whole =
                    componentAnalysis.getWholeEllipsePreview();
            message.append("Proposed-fill component diagnostics completed.\n\n");
            message.append("Total proposed-fill components: ")
                    .append(componentAnalysis.getTotalComponentCount()).append("\n");
            message.append("Interval-associated components: ")
                    .append(componentAnalysis.getIntervalAssociatedComponentCount()).append("\n");
            message.append("Non-associated components: ")
                    .append(componentAnalysis.getNonAssociatedComponentCount()).append("\n\n");
            message.append("Total proposed-fill pixels: ")
                    .append(componentAnalysis.getTotalProposedFillPixelCount()).append("\n");
            message.append("Interval-associated fill pixels: ")
                    .append(componentAnalysis.getIntervalAssociatedFillPixelCount()).append("\n");
            message.append("Non-associated fill pixels: ")
                    .append(componentAnalysis.getNonAssociatedFillPixelCount()).append("\n");
            message.append("Interval-associated fill fraction: ")
                    .append(fmt(componentAnalysis.getIntervalAssociatedFillFraction()))
                    .append("\n\n");
            message.append("Primary associated component ID: ")
                    .append(primary == null ? "NONE" : String.valueOf(primary.getComponentId()))
                    .append("\n");
            message.append("Primary component area: ")
                    .append(primary == null ? 0 : primary.getArea()).append("\n");
            message.append("Primary / total fill fraction: ")
                    .append(fmt(componentAnalysis.getPrimaryComponentFillFraction())).append("\n");
            message.append("Primary / associated fill fraction: ")
                    .append(fmt(componentAnalysis.getPrimaryWithinAssociatedFraction()))
                    .append("\n\n");
            message.append("Interval exterior edges: ")
                    .append(componentAnalysis.getIntervalExteriorEdgeCount()).append("\n");
            message.append("Unique exterior seed pixels: ")
                    .append(componentAnalysis.getUniqueIntervalExteriorSeedPixelCount())
                    .append("\n");
            message.append("Matched exterior seed pixels: ")
                    .append(componentAnalysis.getMatchedIntervalExteriorSeedPixelCount())
                    .append("\n");
            message.append("Unmatched exterior seed pixels: ")
                    .append(componentAnalysis.getUnmatchedIntervalExteriorSeedPixelCount())
                    .append("\n\n");
            message.append("Whole-ellipse preview:\n");
            message.append("area=").append(whole.getPreviewArea()).append("\n");
            message.append("perimeter=").append(whole.getPreviewPerimeter()).append("\n");
            message.append("circularity=")
                    .append(fmt(componentAnalysis.getWholeEllipseCircularity())).append("\n");
            message.append("circularity change=")
                    .append(fmt(componentAnalysis.getWholeEllipseCircularityChange()))
                    .append("\n\n");
            message.append("Interval-associated preview:\n");
            message.append("area=")
                    .append(componentAnalysis.getIntervalAssociatedPreviewArea()).append("\n");
            message.append("perimeter=")
                    .append(componentAnalysis.getIntervalAssociatedPreviewPerimeter())
                    .append("\n");
            message.append("circularity=")
                    .append(fmt(componentAnalysis.getIntervalAssociatedPreviewCircularity()))
                    .append("\n");
            message.append("circularity change=")
                    .append(fmt(componentAnalysis.getIntervalAssociatedCircularityChange()))
                    .append("\n\n");
            message.append("No component was accepted or rejected.\n");
            message.append("The source image was not modified.\n");
            message.append("Production reconstruction was not applied.");
        } else if (fillPreview != null) {
            message.append("Refitted ellipse fill preview completed.\n");
            message.append("Observed lumen area: ")
                    .append(fillPreview.getSelectedObservedLumenArea()).append("\n");
            message.append("The source image was not modified.\n");
            message.append("Production reconstruction was not applied.");
        } else {
            message.append("Initial free ellipse fit completed.\n");
            message.append("Selected lumen area: ")
                    .append(result.getSelectedComponentArea()).append("\n");
            message.append("Interval selected: ")
                    .append(outlierDetection.isIntervalSelected()).append("\n");
            message.append("Ordered outer-boundary edges: ")
                    .append(residualProfile.getSamples().size()).append("\n");
            if (refitResult != null) {
                message.append("Refit completed but fill preview was not generated.\n");
            }
            message.append("The source image was not modified.\n");
            message.append("Production reconstruction was not applied.");
        }
        IJ.showMessage("Robust Ellipse Reconstruction Prototype", message.toString());
    }

    private static String fmt(final double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String sanitize(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean sameBytes(final byte[] a, final byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] copyBytes(final byte[] source) {
        final byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static void handleAmbiguousBoundaryVertex(
            final ImagePlus image,
            final ImageProcessor classified,
            final InitialEllipseAnalysisResult result,
            final AmbiguousBoundaryVertexException ex) {
        final AmbiguousBoundaryVertexDiagnostic diagnostic = ex.getDiagnostic();
        IJ.log("Ordered boundary analysis failed:");
        IJ.log("AMBIGUOUS_BOUNDARY_VERTEX");
        logAmbiguousBoundaryVertexDiagnostic(diagnostic);
        showAmbiguousBoundaryVertexVisualization(
                image.getTitle(),
                classified.getWidth(),
                classified.getHeight(),
                result.getSelectedLumenPixels(),
                diagnostic);

        try {
            final ImageJBoundaryReferenceResult reference =
                    new ImageJBoundaryReferenceAnalyzer().analyze(
                            classified.getWidth(),
                            classified.getHeight(),
                            result.getSelectedLumenPixels(),
                            diagnostic);
            logImageJBoundaryReferenceAudit(reference);
            showImageJBoundaryReferenceVisualization(
                    image.getTitle(),
                    classified.getWidth(),
                    classified.getHeight(),
                    result.getSelectedLumenPixels(),
                    diagnostic,
                    reference);
        } catch (RuntimeException referenceEx) {
            IJ.log("ImageJ boundary reference audit failed: "
                    + String.valueOf(referenceEx.getMessage()));
            if (referenceEx.getClass() != null) {
                IJ.log("Reference audit exception type: " + referenceEx.getClass().getName());
            }
        }

        final StringBuilder dialog = new StringBuilder();
        dialog.append("Ordered boundary analysis stopped at an ambiguous lattice vertex.\n\n");
        dialog.append("Vertex: (").append(diagnostic.getVertexX()).append(", ")
                .append(diagnostic.getVertexY()).append(")\n");
        dialog.append("Loop: ").append(diagnostic.getLoopNumber()).append("\n");
        dialog.append("Traversal step: ").append(diagnostic.getTraversalStep()).append("\n");
        dialog.append("Incoming direction: ").append(diagnostic.getIncomingDirection())
                .append("\n");
        dialog.append("Unused outgoing candidates: ")
                .append(diagnostic.getUnusedOutgoingEdges().size()).append("\n");
        dialog.append("Occupancy pattern: ").append(diagnostic.getLocalOccupancyPattern())
                .append("\n");
        dialog.append("Turn relations: ")
                .append(joinStrings(diagnostic.getCandidateTurnRelations())).append("\n\n");
        dialog.append("No successor was selected.\n");
        dialog.append("No pixels were changed.");
        IJ.error("Robust Ellipse Reconstruction Prototype", dialog.toString());
    }

    private static void logAmbiguousBoundaryVertexDiagnostic(
            final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        IJ.log("Ambiguous boundary vertex diagnostic:");
        IJ.log("loopNumber,traversalStep,totalExposedEdges,usedEdgesBeforeFailure,"
                + "vertexX,vertexY,incomingStartX,incomingStartY,incomingEndX,incomingEndY,"
                + "incomingDirection,incidentEdgeCount,outgoingEdgeCount,"
                + "unusedOutgoingEdgeCount,usedOutgoingEdgeCount,"
                + "northWestSelected,northEastSelected,southWestSelected,southEastSelected,"
                + "localOccupancyPattern,unusedOutgoingDirections,candidateTurnRelations,"
                + "failureInvariant");
        final DirectedPixelEdge incoming = diagnostic.getIncomingEdge();
        IJ.log(String.format(Locale.US,
                "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s",
                Integer.valueOf(diagnostic.getLoopNumber()),
                Integer.valueOf(diagnostic.getTraversalStep()),
                Integer.valueOf(diagnostic.getTotalExposedEdgeCount()),
                Integer.valueOf(diagnostic.getUsedEdgeCountBeforeFailure()),
                Integer.valueOf(diagnostic.getVertexX()),
                Integer.valueOf(diagnostic.getVertexY()),
                Integer.valueOf(incoming.getStartX()),
                Integer.valueOf(incoming.getStartY()),
                Integer.valueOf(incoming.getEndX()),
                Integer.valueOf(incoming.getEndY()),
                diagnostic.getIncomingDirection(),
                Integer.valueOf(diagnostic.getAllIncidentEdges().size()),
                Integer.valueOf(diagnostic.getOutgoingEdges().size()),
                Integer.valueOf(diagnostic.getUnusedOutgoingEdges().size()),
                Integer.valueOf(diagnostic.getUsedOutgoingEdges().size()),
                Boolean.toString(diagnostic.isNorthWestSelected()),
                Boolean.toString(diagnostic.isNorthEastSelected()),
                Boolean.toString(diagnostic.isSouthWestSelected()),
                Boolean.toString(diagnostic.isSouthEastSelected()),
                diagnostic.getLocalOccupancyPattern(),
                sanitize(joinStrings(diagnostic.getUnusedOutgoingDirections())),
                sanitize(joinStrings(diagnostic.getCandidateTurnRelations())),
                diagnostic.getFailureInvariant()));

        final Set<DirectedPixelEdge> usedOutgoingSet =
                new HashSet<DirectedPixelEdge>(diagnostic.getUsedOutgoingEdges());

        IJ.log("Incident exposed directed edges:");
        IJ.log("edgeNumber,startX,startY,endX,endY,direction,used,outgoingFromAmbiguousVertex");
        final List<DirectedPixelEdge> incident = diagnostic.getAllIncidentEdges();
        for (int i = 0; i < incident.size(); i++) {
            final DirectedPixelEdge edge = incident.get(i);
            final boolean outgoing =
                    edge.getStartX() == diagnostic.getVertexX()
                            && edge.getStartY() == diagnostic.getVertexY();
            final boolean usedFlag;
            if (outgoing) {
                usedFlag = usedOutgoingSet.contains(edge);
            } else {
                usedFlag = edge.equals(diagnostic.getIncomingEdge());
            }
            IJ.log(String.format(Locale.US,
                    "%d,%d,%d,%d,%d,%s,%s,%s",
                    Integer.valueOf(i + 1),
                    Integer.valueOf(edge.getStartX()),
                    Integer.valueOf(edge.getStartY()),
                    Integer.valueOf(edge.getEndX()),
                    Integer.valueOf(edge.getEndY()),
                    edge.getDirection().name(),
                    Boolean.toString(usedFlag),
                    Boolean.toString(outgoing)));
        }

        IJ.log("Unused outgoing candidates in evaluation order:");
        IJ.log("candidateNumber,startX,startY,endX,endY,direction,turnRelation");
        final List<DirectedPixelEdge> unused = diagnostic.getUnusedOutgoingEdges();
        final List<String> turns = diagnostic.getCandidateTurnRelations();
        for (int i = 0; i < unused.size(); i++) {
            final DirectedPixelEdge edge = unused.get(i);
            IJ.log(String.format(Locale.US,
                    "%d,%d,%d,%d,%d,%s,%s",
                    Integer.valueOf(i + 1),
                    Integer.valueOf(edge.getStartX()),
                    Integer.valueOf(edge.getStartY()),
                    Integer.valueOf(edge.getEndX()),
                    Integer.valueOf(edge.getEndY()),
                    edge.getDirection().name(),
                    turns.get(i)));
        }

        IJ.log("IMPORTANT:");
        IJ.log("No successor was selected.");
        IJ.log("The ordered boundary was not normalized or repaired.");
        IJ.log("The ellipse was not refitted.");
        IJ.log("No interval was detected.");
        IJ.log("No proposed-fill component was accepted.");
        IJ.log("The source image was not modified.");
        IJ.log("Production reconstruction was not applied.");
    }

    private static String joinStrings(final List<String> values) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static void showAmbiguousBoundaryVertexVisualization(
            final String sourceTitle,
            final int imageWidth,
            final int imageHeight,
            final List<Point> selectedLumenPixels,
            final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        final int canvasWidth = 520;
        final int canvasHeight = 420;
        final ColorProcessor rgb = new ColorProcessor(canvasWidth, canvasHeight);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        final Set<Long> selected = new HashSet<Long>();
        for (int i = 0; i < selectedLumenPixels.size(); i++) {
            final Point p = selectedLumenPixels.get(i);
            selected.add((((long) p.x) << 32) ^ (p.y & 0xffffffffL));
        }

        final int vertexX = diagnostic.getVertexX();
        final int vertexY = diagnostic.getVertexY();
        // Local region: four surrounding cells plus one-cell margin.
        final int cellMinX = vertexX - 2;
        final int cellMaxX = vertexX + 1;
        final int cellMinY = vertexY - 2;
        final int cellMaxY = vertexY + 1;
        final int cellCountX = cellMaxX - cellMinX + 1;
        final int cellCountY = cellMaxY - cellMinY + 1;

        final int latticeLeft = 12;
        final int latticeTop = 12;
        final int latticeMaxWidth = 300;
        final int latticeMaxHeight = 300;
        final int scale = Math.max(
                36,
                Math.min(latticeMaxWidth / cellCountX, latticeMaxHeight / cellCountY));
        final int latticeWidth = cellCountX * scale;
        final int latticeHeight = cellCountY * scale;

        final Color selectedCell = new Color(120, 120, 120);
        final Color nonSelectedCell = new Color(40, 40, 40);
        final Color outsideCell = Color.BLACK;
        final Color exposedEdge = Color.CYAN;
        final Color incomingColor = Color.YELLOW;
        final Color unusedOutgoingColor = Color.MAGENTA;
        final Color usedOutgoingColor = new Color(200, 100, 0);

        for (int cy = cellMinY; cy <= cellMaxY; cy++) {
            for (int cx = cellMinX; cx <= cellMaxX; cx++) {
                final int sx = latticeLeft + (cx - cellMinX) * scale;
                final int sy = latticeTop + (cy - cellMinY) * scale;
                final boolean inBounds =
                        cx >= 0 && cy >= 0 && cx < imageWidth && cy < imageHeight;
                final boolean isSelected = inBounds
                        && selected.contains((((long) cx) << 32) ^ (cy & 0xffffffffL));
                if (!inBounds) {
                    rgb.setColor(outsideCell);
                } else if (isSelected) {
                    rgb.setColor(selectedCell);
                } else {
                    rgb.setColor(nonSelectedCell);
                }
                rgb.fillRect(sx, sy, scale, scale);
                rgb.setColor(new Color(70, 70, 70));
                rgb.drawRect(sx, sy, scale, scale);
            }
        }

        // Cell labels for the four surrounding cells.
        labelSurroundingCell(rgb, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                diagnostic.getNorthWestX(), diagnostic.getNorthWestY(),
                "NW", cellStateLabel(
                        diagnostic.getNorthWestX(), diagnostic.getNorthWestY(),
                        imageWidth, imageHeight, selected));
        labelSurroundingCell(rgb, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                diagnostic.getNorthEastX(), diagnostic.getNorthEastY(),
                "NE", cellStateLabel(
                        diagnostic.getNorthEastX(), diagnostic.getNorthEastY(),
                        imageWidth, imageHeight, selected));
        labelSurroundingCell(rgb, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                diagnostic.getSouthWestX(), diagnostic.getSouthWestY(),
                "SW", cellStateLabel(
                        diagnostic.getSouthWestX(), diagnostic.getSouthWestY(),
                        imageWidth, imageHeight, selected));
        labelSurroundingCell(rgb, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                diagnostic.getSouthEastX(), diagnostic.getSouthEastY(),
                "SE", cellStateLabel(
                        diagnostic.getSouthEastX(), diagnostic.getSouthEastY(),
                        imageWidth, imageHeight, selected));

        // Draw all incident exposed edges in local region (cyan), then overlays.
        final List<DirectedPixelEdge> incident = diagnostic.getAllIncidentEdges();
        for (int i = 0; i < incident.size(); i++) {
            final DirectedPixelEdge edge = incident.get(i);
            if (!edgeTouchesLocalRegion(edge, cellMinX, cellMinY, cellMaxX + 1, cellMaxY + 1)) {
                continue;
            }
            drawLatticeEdge(rgb, edge, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                    exposedEdge, 1, true);
        }
        for (int i = 0; i < diagnostic.getUsedOutgoingEdges().size(); i++) {
            final DirectedPixelEdge edge = diagnostic.getUsedOutgoingEdges().get(i);
            drawLatticeEdge(rgb, edge, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                    usedOutgoingColor, 2, true);
        }
        drawLatticeEdge(rgb, diagnostic.getIncomingEdge(),
                latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                incomingColor, 3, true);
        for (int i = 0; i < diagnostic.getUnusedOutgoingEdges().size(); i++) {
            final DirectedPixelEdge edge = diagnostic.getUnusedOutgoingEdges().get(i);
            drawLatticeEdge(rgb, edge, latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                    unusedOutgoingColor, 3, true);
        }

        // Ambiguous lattice vertex marker.
        final int vxScreen = latticeLeft + (vertexX - cellMinX) * scale;
        final int vyScreen = latticeTop + (vertexY - cellMinY) * scale;
        final int half = Math.max(4, scale / 6);
        rgb.setColor(Color.WHITE);
        rgb.fillRect(vxScreen - half, vyScreen - half, half * 2, half * 2);

        // Text panel on the right.
        final int textX = latticeLeft + latticeWidth + 16;
        int textY = 28;
        rgb.setFont(new Font("Monospaced", Font.BOLD, 12));
        rgb.setColor(Color.WHITE);
        rgb.drawString("AMBIGUOUS BOUNDARY VERTEX DIAGNOSTIC", textX, textY);
        textY += 20;
        rgb.setFont(new Font("Monospaced", Font.PLAIN, 11));
        final String[] lines = new String[] {
                "vertex=(" + vertexX + "," + vertexY + ")",
                "loop=" + diagnostic.getLoopNumber(),
                "step=" + diagnostic.getTraversalStep(),
                "occupancy=" + diagnostic.getLocalOccupancyPattern(),
                "incoming=" + diagnostic.getIncomingDirection(),
                "unused candidates=" + diagnostic.getUnusedOutgoingEdges().size(),
                candidateLine(diagnostic, 0),
                candidateLine(diagnostic, 1),
                "used edges=" + diagnostic.getUsedEdgeCountBeforeFailure()
                        + "/" + diagnostic.getTotalExposedEdgeCount(),
                "",
                "NO SUCCESSOR SELECTED",
                "NO BOUNDARY REPAIR",
                "NO PIXELS CHANGED"
        };
        for (int i = 0; i < lines.length; i++) {
            rgb.drawString(lines[i], textX, textY);
            textY += 16;
        }

        new ImagePlus("Ambiguous Boundary Vertex - " + sourceTitle, rgb).show();
    }

    private static String candidateLine(
            final AmbiguousBoundaryVertexDiagnostic diagnostic,
            final int index) {
        if (index >= diagnostic.getUnusedOutgoingEdges().size()) {
            return "candidate " + (index + 1) + "=n/a";
        }
        return "candidate " + (index + 1) + "="
                + diagnostic.getUnusedOutgoingDirections().get(index)
                + ", " + diagnostic.getCandidateTurnRelations().get(index);
    }

    private static String cellStateLabel(
            final int x,
            final int y,
            final int imageWidth,
            final int imageHeight,
            final Set<Long> selected) {
        if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
            return "OUTSIDE IMAGE";
        }
        if (selected.contains((((long) x) << 32) ^ (y & 0xffffffffL))) {
            return "SELECTED";
        }
        return "NOT SELECTED";
    }

    private static void labelSurroundingCell(
            final ColorProcessor rgb,
            final int latticeLeft,
            final int latticeTop,
            final int scale,
            final int cellMinX,
            final int cellMinY,
            final int cellX,
            final int cellY,
            final String cornerLabel,
            final String stateLabel) {
        final int sx = latticeLeft + (cellX - cellMinX) * scale;
        final int sy = latticeTop + (cellY - cellMinY) * scale;
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font("Monospaced", Font.BOLD, Math.max(10, scale / 5)));
        rgb.drawString(cornerLabel, sx + 4, sy + Math.max(12, scale / 4));
        rgb.setFont(new Font("Monospaced", Font.PLAIN, Math.max(8, scale / 7)));
        rgb.drawString(stateLabel, sx + 4, sy + scale - 6);
    }

    private static boolean edgeTouchesLocalRegion(
            final DirectedPixelEdge edge,
            final int minX,
            final int minY,
            final int maxXExclusive,
            final int maxYExclusive) {
        return pointInInclusive(edge.getStartX(), edge.getStartY(), minX, minY,
                maxXExclusive, maxYExclusive)
                || pointInInclusive(edge.getEndX(), edge.getEndY(), minX, minY,
                maxXExclusive, maxYExclusive);
    }

    private static boolean pointInInclusive(
            final int x,
            final int y,
            final int minX,
            final int minY,
            final int maxXExclusive,
            final int maxYExclusive) {
        return x >= minX && y >= minY && x <= maxXExclusive && y <= maxYExclusive;
    }

    private static void drawLatticeEdge(
            final ColorProcessor rgb,
            final DirectedPixelEdge edge,
            final int latticeLeft,
            final int latticeTop,
            final int scale,
            final int cellMinX,
            final int cellMinY,
            final Color color,
            final int thickness,
            final boolean arrow) {
        final int x0 = latticeLeft + (edge.getStartX() - cellMinX) * scale;
        final int y0 = latticeTop + (edge.getStartY() - cellMinY) * scale;
        final int x1 = latticeLeft + (edge.getEndX() - cellMinX) * scale;
        final int y1 = latticeTop + (edge.getEndY() - cellMinY) * scale;
        rgb.setColor(color);
        for (int t = -thickness / 2; t <= thickness / 2; t++) {
            if (edge.getStartX() == edge.getEndX()) {
                rgb.drawLine(x0 + t, y0, x1 + t, y1);
            } else {
                rgb.drawLine(x0, y0 + t, x1, y1 + t);
            }
        }
        if (arrow) {
            final int mx = (x0 + x1) / 2;
            final int my = (y0 + y1) / 2;
            final int dx = Integer.signum(x1 - x0);
            final int dy = Integer.signum(y1 - y0);
            final int ax = 5;
            if (dx != 0) {
                rgb.drawLine(mx, my, mx - dx * ax, my - ax);
                rgb.drawLine(mx, my, mx - dx * ax, my + ax);
            } else if (dy != 0) {
                rgb.drawLine(mx, my, mx - ax, my - dy * ax);
                rgb.drawLine(mx, my, mx + ax, my - dy * ax);
            }
        }
    }

    private static void logImageJBoundaryReferenceAudit(
            final ImageJBoundaryReferenceResult reference) {
        IJ.log("ImageJ boundary reference audit:");
        IJ.log("method,outlineNumber,vertexCount,unitEdgeCount,closed,signedArea,absoluteArea,"
                + "distinctVertices,repeatedNonClosingVertices,expectedEdgeMatches,"
                + "missingExpectedEdges,extraReferenceEdges,expectedCoverageFraction,"
                + "containsAmbiguousVertex,ambiguousVertexOccurrences,incomingEdgeFound,"
                + "incomingEdgeOccurrences,successorDirection,successorTurnRelation");
        logReferenceOutlines(reference.getThresholdToSelectionOutlines());
        logReferenceOutlines(reference.getWandFourConnectedOutlines());
        logReferenceOutlines(reference.getWandEightConnectedOutlines());

        IJ.log("ImageJ boundary reference method summary:");
        IJ.log("method,outlineCount,unionExpectedEdgeMatches,unionMissingExpectedEdges,"
                + "unionExtraReferenceEdges,unionCoverageFraction");
        logReferenceMethodSummary(
                ImageJBoundaryReferenceResult.ReferenceMethod.THRESHOLD_TO_SELECTION,
                reference.getThresholdToSelectionOutlines(),
                reference.getExpectedExposedEdgeCount());
        logReferenceMethodSummary(
                ImageJBoundaryReferenceResult.ReferenceMethod.WAND_FOUR_CONNECTED,
                reference.getWandFourConnectedOutlines(),
                reference.getExpectedExposedEdgeCount());
        logReferenceMethodSummary(
                ImageJBoundaryReferenceResult.ReferenceMethod.WAND_EIGHT_CONNECTED,
                reference.getWandEightConnectedOutlines(),
                reference.getExpectedExposedEdgeCount());

        IJ.log("ImageJ boundary reference conclusion:");
        IJ.log(reference.getOverallDiagnosticConclusion());
        IJ.log("IMPORTANT:");
        IJ.log("ImageJ reference contours were used for diagnosis only.");
        IJ.log("OrderedBoundaryExtractor was not changed.");
        IJ.log("No production successor was selected.");
        IJ.log("No boundary was repaired or normalized for production use.");
        IJ.log("Component membership remains 8-connected.");
        IJ.log("Ellipse fitting was not changed.");
        IJ.log("Residual thresholds were not changed.");
        IJ.log("No pixels were changed.");
        IJ.log("Production reconstruction was not applied.");
    }

    private static void logReferenceOutlines(
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> outlines) {
        for (int i = 0; i < outlines.size(); i++) {
            final ImageJBoundaryReferenceResult.ReferenceOutline o = outlines.get(i);
            final String successor = o.getSuccessorDirectionAtAmbiguity() == null
                    ? ""
                    : o.getSuccessorDirectionAtAmbiguity().name();
            IJ.log(String.format(Locale.US,
                    "%s,%d,%d,%d,%s,%.6f,%.6f,%d,%d,%d,%d,%d,%.6f,%s,%d,%s,%d,%s,%s",
                    o.getMethod().name(),
                    Integer.valueOf(o.getOutlineNumber()),
                    Integer.valueOf(o.getLatticeVertices().size()),
                    Integer.valueOf(o.getExpandedUnitEdges().size()),
                    Boolean.toString(o.isClosed()),
                    Double.valueOf(o.getSignedArea()),
                    Double.valueOf(o.getAbsoluteArea()),
                    Integer.valueOf(o.getDistinctVertexCount()),
                    Integer.valueOf(o.getRepeatedNonClosingVertexCount()),
                    Integer.valueOf(o.getExpectedExposedEdgeMatchCount()),
                    Integer.valueOf(o.getMissingExpectedExposedEdgeCount()),
                    Integer.valueOf(o.getExtraReferenceEdgeCount()),
                    Double.valueOf(o.getExpectedExposedEdgeCoverageFraction()),
                    Boolean.toString(o.isContainsAmbiguousVertex()),
                    Integer.valueOf(o.getAmbiguousVertexOccurrenceCount()),
                    Boolean.toString(o.isMatchingIncomingEdgeFound()),
                    Integer.valueOf(o.getMatchingIncomingEdgeOccurrenceCount()),
                    successor,
                    o.getSuccessorTurnRelationAtAmbiguity().name()));
        }
    }

    private static void logReferenceMethodSummary(
            final ImageJBoundaryReferenceResult.ReferenceMethod method,
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> outlines,
            final int expectedEdgeCount) {
        final Set<String> unionKeys = new HashSet<String>();
        for (int i = 0; i < outlines.size(); i++) {
            final List<DirectedPixelEdge> edges = outlines.get(i).getExpandedUnitEdges();
            for (int j = 0; j < edges.size(); j++) {
                unionKeys.add(ImageJBoundaryReferenceAnalyzer.undirectedKey(edges.get(j)));
            }
        }
        final int reportedMatches;
        final int reportedMissing;
        final int reportedExtra;
        if (outlines.size() == 1) {
            reportedMatches = outlines.get(0).getExpectedExposedEdgeMatchCount();
            reportedMissing = outlines.get(0).getMissingExpectedExposedEdgeCount();
            reportedExtra = outlines.get(0).getExtraReferenceEdgeCount();
        } else if (outlines.isEmpty()) {
            reportedMatches = 0;
            reportedMissing = expectedEdgeCount;
            reportedExtra = 0;
        } else {
            reportedMatches = Math.min(unionKeys.size(), expectedEdgeCount);
            reportedMissing = Math.max(0, expectedEdgeCount - reportedMatches);
            reportedExtra = Math.max(0, unionKeys.size() - reportedMatches);
        }
        final double coverage = expectedEdgeCount == 0
                ? 0.0
                : ((double) reportedMatches) / ((double) expectedEdgeCount);
        IJ.log(String.format(Locale.US,
                "%s,%d,%d,%d,%d,%.6f",
                method.name(),
                Integer.valueOf(outlines.size()),
                Integer.valueOf(reportedMatches),
                Integer.valueOf(reportedMissing),
                Integer.valueOf(reportedExtra),
                Double.valueOf(coverage)));
    }

    private static void showImageJBoundaryReferenceVisualization(
            final String sourceTitle,
            final int imageWidth,
            final int imageHeight,
            final List<Point> selectedLumenPixels,
            final AmbiguousBoundaryVertexDiagnostic diagnostic,
            final ImageJBoundaryReferenceResult reference) {
        final int canvasWidth = 980;
        final int canvasHeight = 520;
        final ColorProcessor rgb = new ColorProcessor(canvasWidth, canvasHeight);
        rgb.setColor(Color.BLACK);
        rgb.fill();

        final Set<Long> selected = new HashSet<Long>();
        for (int i = 0; i < selectedLumenPixels.size(); i++) {
            final Point p = selectedLumenPixels.get(i);
            selected.add((((long) p.x) << 32) ^ (p.y & 0xffffffffL));
        }

        final int panelWidth = 300;
        final int panelHeight = 300;
        final int panelTop = 24;
        paintReferencePanel(rgb, 12, panelTop, panelWidth, panelHeight,
                "THRESHOLD TO SELECTION", Color.CYAN,
                imageWidth, imageHeight, selected, diagnostic,
                reference.getThresholdToSelectionOutlines());
        paintReferencePanel(rgb, 324, panelTop, panelWidth, panelHeight,
                "WAND FOUR CONNECTED", new Color(255, 140, 0),
                imageWidth, imageHeight, selected, diagnostic,
                reference.getWandFourConnectedOutlines());
        paintReferencePanel(rgb, 636, panelTop, panelWidth, panelHeight,
                "WAND EIGHT CONNECTED", new Color(0, 200, 0),
                imageWidth, imageHeight, selected, diagnostic,
                reference.getWandEightConnectedOutlines());

        final int textX = 12;
        int textY = panelTop + panelHeight + 28;
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font("Monospaced", Font.BOLD, 13));
        rgb.drawString("REFERENCE AUDIT ONLY", textX, textY);
        textY += 18;
        rgb.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rgb.drawString("NO PRODUCTION SUCCESSOR SELECTED", textX, textY);
        textY += 16;
        rgb.drawString("ORDERED BOUNDARY EXTRACTOR UNCHANGED", textX, textY);
        textY += 16;
        rgb.drawString("NO PIXELS CHANGED", textX, textY);
        textY += 18;
        rgb.drawString("conclusion=" + reference.getOverallDiagnosticConclusion(), textX, textY);

        new ImagePlus("ImageJ Boundary Reference Audit - " + sourceTitle, rgb).show();
    }

    private static void paintReferencePanel(
            final ColorProcessor rgb,
            final int left,
            final int top,
            final int panelWidth,
            final int panelHeight,
            final String title,
            final Color contourColor,
            final int imageWidth,
            final int imageHeight,
            final Set<Long> selected,
            final AmbiguousBoundaryVertexDiagnostic diagnostic,
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> outlines) {
        rgb.setColor(new Color(20, 20, 20));
        rgb.fillRect(left, top, panelWidth, panelHeight);
        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font("Monospaced", Font.BOLD, 11));
        rgb.drawString(title, left + 6, top + 14);

        final int vertexX = diagnostic.getVertexX();
        final int vertexY = diagnostic.getVertexY();
        final int cellMinX = vertexX - 2;
        final int cellMaxX = vertexX + 1;
        final int cellMinY = vertexY - 2;
        final int cellMaxY = vertexY + 1;
        final int cellCountX = cellMaxX - cellMinX + 1;
        final int cellCountY = cellMaxY - cellMinY + 1;
        final int latticeLeft = left + 10;
        final int latticeTop = top + 24;
        final int scale = Math.max(28, Math.min(
                (panelWidth - 20) / cellCountX,
                (panelHeight - 110) / cellCountY));

        final Color selectedCell = new Color(120, 120, 120);
        final Color nonSelectedCell = new Color(40, 40, 40);
        for (int cy = cellMinY; cy <= cellMaxY; cy++) {
            for (int cx = cellMinX; cx <= cellMaxX; cx++) {
                final int sx = latticeLeft + (cx - cellMinX) * scale;
                final int sy = latticeTop + (cy - cellMinY) * scale;
                final boolean inBounds =
                        cx >= 0 && cy >= 0 && cx < imageWidth && cy < imageHeight;
                final boolean isSelected = inBounds
                        && selected.contains((((long) cx) << 32) ^ (cy & 0xffffffffL));
                if (!inBounds) {
                    rgb.setColor(Color.BLACK);
                } else if (isSelected) {
                    rgb.setColor(selectedCell);
                } else {
                    rgb.setColor(nonSelectedCell);
                }
                rgb.fillRect(sx, sy, scale, scale);
                rgb.setColor(new Color(70, 70, 70));
                rgb.drawRect(sx, sy, scale, scale);
            }
        }

        // Custom unused candidates (magenta) and incoming (yellow).
        for (int i = 0; i < diagnostic.getUnusedOutgoingEdges().size(); i++) {
            drawLatticeEdge(rgb, diagnostic.getUnusedOutgoingEdges().get(i),
                    latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                    Color.MAGENTA, 2, true);
        }
        drawLatticeEdge(rgb, diagnostic.getIncomingEdge(),
                latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                Color.YELLOW, 3, true);

        DirectedPixelEdge successor = null;
        ImageJBoundaryReferenceResult.ReferenceTurnRelation turn =
                ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND;
        int unitEdges = 0;
        double coverage = 0.0;
        int repeated = 0;
        if (!outlines.isEmpty()) {
            final ImageJBoundaryReferenceResult.ReferenceOutline primary = outlines.get(0);
            unitEdges = primary.getExpandedUnitEdges().size();
            coverage = primary.getExpectedExposedEdgeCoverageFraction();
            repeated = primary.getRepeatedNonClosingVertexCount();
            turn = primary.getSuccessorTurnRelationAtAmbiguity();
            final List<DirectedPixelEdge> edges = primary.getExpandedUnitEdges();
            for (int i = 0; i < edges.size(); i++) {
                drawLatticeEdge(rgb, edges.get(i),
                        latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                        contourColor, 1, false);
            }
            if (primary.isMatchingIncomingEdgeFound()
                    && primary.getMatchingIncomingEdgeOccurrenceCount() == 1
                    && primary.getSuccessorDirectionAtAmbiguity() != null) {
                for (int i = 0; i < edges.size(); i++) {
                    if (edges.get(i).equals(diagnostic.getIncomingEdge())) {
                        successor = edges.get((i + 1) % edges.size());
                        break;
                    }
                }
            }
            if (successor != null) {
                drawLatticeEdge(rgb, successor,
                        latticeLeft, latticeTop, scale, cellMinX, cellMinY,
                        contourColor, 3, true);
            }
        }

        final int vxScreen = latticeLeft + (vertexX - cellMinX) * scale;
        final int vyScreen = latticeTop + (vertexY - cellMinY) * scale;
        final int half = Math.max(3, scale / 7);
        rgb.setColor(Color.WHITE);
        rgb.fillRect(vxScreen - half, vyScreen - half, half * 2, half * 2);

        rgb.setColor(Color.WHITE);
        rgb.setFont(new Font("Monospaced", Font.PLAIN, 10));
        int ty = latticeTop + cellCountY * scale + 14;
        rgb.drawString("outlines=" + outlines.size(), left + 6, ty);
        ty += 12;
        rgb.drawString("unitEdges=" + unitEdges, left + 6, ty);
        ty += 12;
        rgb.drawString(String.format(Locale.US, "coverage=%.3f", Double.valueOf(coverage)),
                left + 6, ty);
        ty += 12;
        rgb.drawString("repeated=" + repeated, left + 6, ty);
        ty += 12;
        final String successorText = successor == null
                ? "n/a"
                : successor.getDirection().name();
        rgb.drawString("successor=" + successorText, left + 6, ty);
        ty += 12;
        rgb.drawString("turn=" + turn.name(), left + 6, ty);
        ty += 12;
        if (outlines.isEmpty()
                || turn == ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND) {
            rgb.setColor(Color.YELLOW);
            rgb.drawString("INCOMING EDGE NOT FOUND", left + 6, ty);
        } else if (turn
                == ImageJBoundaryReferenceResult.ReferenceTurnRelation.MULTIPLE_MATCHES) {
            rgb.setColor(Color.YELLOW);
            rgb.drawString("MULTIPLE INCOMING MATCHES", left + 6, ty);
        }
    }
}
