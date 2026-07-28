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
import java.util.List;
import java.util.Locale;

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
        IJ.log("Mode: INITIAL_FREE_ELLIPSE_FIT");
        IJ.log("Input format: TRAINABLE_WEKA_CREATE_RESULT");
        IJ.log("Outlier exclusion: NOT IMPLEMENTED");
        IJ.log("Reconstruction: NOT APPLIED");

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
        showSummaryDialog(result, residualProfile, outlierDetection);
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

    private static void showSummaryDialog(
            final InitialEllipseAnalysisResult result,
            final OrderedBoundaryResidualProfile residualProfile,
            final InwardOutlierDetectionResult outlierDetection) {
        final EllipseFitResult fit = result.getEllipseFitResult();
        final EllipseModel ellipse = fit.getFittedEllipse();
        final StringBuilder message = new StringBuilder();
        message.append("Initial free ellipse fit completed.\n\n");
        message.append("Input lumen encoding:\n");
        message.append("Class ").append(ClassifierLabels.LUMEN).append(" = ")
                .append(ClassifierLabels.nameOf(ClassifierLabels.LUMEN)).append("\n");
        message.append("Weka result pixel value = ")
                .append(ClassifierLabels.LUMEN_RESULT_VALUE).append("\n\n");
        message.append("Selected lumen area: ").append(result.getSelectedComponentArea()).append("\n");
        message.append("Boundary support points: ")
                .append(result.getBoundarySupportPoints().size()).append("\n");
        message.append("Ellipse center: (").append(fmt(ellipse.getCenterX())).append(", ")
                .append(fmt(ellipse.getCenterY())).append(")\n");
        message.append("Semi-major axis: ").append(fmt(ellipse.getSemiMajorAxis())).append("\n");
        message.append("Semi-minor axis: ").append(fmt(ellipse.getSemiMinorAxis())).append("\n");
        message.append("Axis ratio: ").append(fmt(ellipse.getAxisRatio())).append("\n");
        message.append("Rotation: ")
                .append(fmt(Math.toDegrees(ellipse.getRotationRadians()))).append(" degrees\n");
        message.append("RMS residual: ").append(fmt(fit.getRootMeanSquaredResidual())).append("\n");
        message.append("Proposed fill pixels: ")
                .append(result.getProposedFillPixelCount()).append("\n\n");
        message.append("Ordered outer-boundary edges: ")
                .append(residualProfile.getSamples().size()).append("\n");
        message.append("Minimum inward residual: ")
                .append(fmt(residualProfile.getMinimumResidual()))
                .append(" at index ").append(residualProfile.getMinimumResidualIndex()).append("\n");
        message.append("Maximum outward residual: ")
                .append(fmt(residualProfile.getMaximumResidual()))
                .append(" at index ").append(residualProfile.getMaximumResidualIndex()).append("\n");
        message.append("Median residual: ")
                .append(fmt(residualProfile.getMedianResidual())).append("\n");
        message.append("MAD: ")
                .append(fmt(residualProfile.getMedianAbsoluteDeviation())).append("\n\n");
        message.append("Robust residual scale: ")
                .append(fmt(outlierDetection.getRobustScale())).append("\n");
        message.append("Core threshold: ")
                .append(fmt(outlierDetection.getCoreThreshold())).append("\n");
        message.append("Expansion threshold: ")
                .append(fmt(outlierDetection.getExpansionThreshold())).append("\n");
        message.append("Candidate intervals: ")
                .append(outlierDetection.getCandidateIntervals().size()).append("\n");
        message.append("Interval selected: ")
                .append(outlierDetection.isIntervalSelected()).append("\n");
        message.append("Selection reason: ")
                .append(outlierDetection.getSelectionReason()).append("\n");
        if (outlierDetection.isIntervalSelected()) {
            final InwardOutlierInterval interval = outlierDetection.getSelectedInterval();
            message.append("\nInterval start index: ").append(interval.getStartIndex()).append("\n");
            message.append("Interval end index: ").append(interval.getEndIndex()).append("\n");
            message.append("Interval wraps around: ").append(interval.isWrapsAround()).append("\n");
            message.append("Interval edge count: ").append(interval.getEdgeCount()).append("\n");
            message.append("Interval boundary fraction: ")
                    .append(fmt(interval.getBoundaryFraction())).append("\n");
            message.append("Interval minimum residual: ")
                    .append(fmt(interval.getMinimumResidual())).append("\n");
            message.append("Interval dominance ratio: ")
                    .append(fmtDominance(outlierDetection.getDominanceRatio())).append("\n");
        }
        message.append("\nThe interval is diagnostic only.\n");
        message.append("No boundary points were excluded.\n");
        message.append("The ellipse was not refitted.\n");
        message.append("Reconstruction was not applied.");
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
}
