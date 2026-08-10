package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;

import java.awt.Color;
import java.util.Locale;

/**
 * Non-destructive overlay preview and confirmation for automatic scale-bar detection.
 */
public final class ScaleBarDetectionPreviewController {

    public enum Action {
        ACCEPT_AUTOMATIC,
        RETRY_AUTOMATIC,
        USE_MANUAL_LINE,
        ENTER_PIXEL_SIZE,
        CANCEL
    }

    public Action confirm(
            final ImagePlus sourceImage,
            final ScaleBarDetection detection,
            final double physicalLengthUm) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        if (detection == null) {
            throw new IllegalArgumentException("detection must not be null");
        }

        sourceImage.show();
        CalibrationWorkflow.WindowManagerFront.bringToFront(sourceImage);

        final Overlay previousOverlay = sourceImage.getOverlay() == null
                ? null : sourceImage.getOverlay().duplicate();
        final Roi previousRoi = sourceImage.getRoi() == null
                ? null : (Roi) sourceImage.getRoi().clone();

        try {
            if (detection.isSuccessful()) {
                sourceImage.setOverlay(buildOverlay(detection));
            }

            final GenericDialog dialog = new GenericDialog("Automatic Scale-Bar Detection");
            if (!detection.isSuccessful()) {
                dialog.addMessage(
                        "Automatic red scale-bar detection FAILED.\n"
                                + "Reason: " + detection.getFailureReason() + "\n\n"
                                + "Weka classification will not start until calibration is accepted.\n"
                                + "Choose a fallback or cancel.");
                dialog.addChoice(
                        "Action:",
                        new String[] {
                                Action.USE_MANUAL_LINE.name(),
                                Action.ENTER_PIXEL_SIZE.name(),
                                Action.RETRY_AUTOMATIC.name(),
                                Action.CANCEL.name()
                        },
                        Action.USE_MANUAL_LINE.name());
            } else if (!detection.isAcceptableForConfirmation()) {
                dialog.addMessage(formatDetectionMessage(sourceImage, detection, physicalLengthUm)
                        + "\n\nConfidence is LOW. Automatic acceptance is not offered.\n"
                        + "Choose a fallback, retry, or cancel. Weka will not start yet.");
                dialog.addChoice(
                        "Action:",
                        new String[] {
                                Action.USE_MANUAL_LINE.name(),
                                Action.ENTER_PIXEL_SIZE.name(),
                                Action.RETRY_AUTOMATIC.name(),
                                Action.CANCEL.name()
                        },
                        Action.USE_MANUAL_LINE.name());
            } else {
                dialog.addMessage(formatDetectionMessage(sourceImage, detection, physicalLengthUm)
                        + "\n\nInspect the cyan overlay on the original RGB source.\n"
                        + "Accept if the terminal-center measurement looks correct.\n"
                        + "No line drawing is required.");
                dialog.addChoice(
                        "Action:",
                        new String[] {
                                Action.ACCEPT_AUTOMATIC.name(),
                                Action.RETRY_AUTOMATIC.name(),
                                Action.USE_MANUAL_LINE.name(),
                                Action.ENTER_PIXEL_SIZE.name(),
                                Action.CANCEL.name()
                        },
                        Action.ACCEPT_AUTOMATIC.name());
            }
            dialog.showDialog();
            if (dialog.wasCanceled()) {
                return Action.CANCEL;
            }
            return Action.valueOf(dialog.getNextChoice());
        } finally {
            sourceImage.setOverlay(previousOverlay);
            if (previousRoi == null) {
                sourceImage.deleteRoi();
            } else {
                sourceImage.setRoi(previousRoi);
            }
        }
    }

    static Overlay buildOverlay(final ScaleBarDetection detection) {
        final Overlay overlay = new Overlay();
        if (detection.getFullResolutionComponentBounds() != null) {
            final Roi box = new Roi(detection.getFullResolutionComponentBounds());
            box.setStrokeColor(Color.CYAN);
            box.setStrokeWidth(1.0);
            overlay.add(box);
        }
        if (detection.isSuccessful()) {
            final Line measure = new Line(
                    detection.getLeftTickCenterX(),
                    detection.getLeftTickCenterY(),
                    detection.getRightTickCenterX(),
                    detection.getRightTickCenterY());
            measure.setStrokeColor(Color.CYAN);
            measure.setStrokeWidth(2.0);
            overlay.add(measure);

            final PointRoi left = new PointRoi(
                    detection.getLeftTickCenterX(),
                    detection.getLeftTickCenterY());
            left.setStrokeColor(Color.BLUE);
            left.setPointType(PointRoi.CIRCLE);
            left.setSize(2);
            overlay.add(left);

            final PointRoi right = new PointRoi(
                    detection.getRightTickCenterX(),
                    detection.getRightTickCenterY());
            right.setStrokeColor(Color.BLUE);
            right.setPointType(PointRoi.CIRCLE);
            right.setSize(2);
            overlay.add(right);

            final Line shaft = new Line(
                    detection.getLeftTickCenterX(),
                    detection.getLeftTickCenterY() - 8,
                    detection.getRightTickCenterX(),
                    detection.getRightTickCenterY() - 8);
            shaft.setStrokeColor(new Color(0, 200, 255));
            shaft.setStrokeWidth(1.0);
            overlay.add(shaft);
        }
        return overlay;
    }

    private static String formatDetectionMessage(
            final ImagePlus sourceImage,
            final ScaleBarDetection detection,
            final double physicalLengthUm) {
        final double pixelSize = physicalLengthUm / detection.getMeasuredLengthPx();
        return String.format(Locale.US,
                "Source: %s\n"
                        + "Physical scale-bar length: %.6f µm\n"
                        + "Detected center-to-center length: %.6f px\n"
                        + "Component outer width (not used): %.6f px\n"
                        + "Calculated µm/pixel: %.12f\n"
                        + "Confidence: %s (score=%.4f)\n"
                        + "Detection method: %s\n"
                        + "WARNING: Red text was not used for the measurement.",
                sourceImage.getTitle() == null ? "Untitled" : sourceImage.getTitle(),
                Double.valueOf(physicalLengthUm),
                Double.valueOf(detection.getMeasuredLengthPx()),
                Double.valueOf(detection.getOuterComponentWidthPx()),
                Double.valueOf(pixelSize),
                detection.getConfidence().name(),
                Double.valueOf(detection.getScore()),
                detection.getMethod().name());
    }

    static void logDetection(final ScaleBarDetection detection) {
        IJ.log("Automatic red scale-bar detection result: " + detection);
        if (detection.getCandidates() != null && !detection.getCandidates().isEmpty()) {
            IJ.log("Low-resolution candidates:\n"
                    + AutomaticScaleBarDetector.describeCandidates(detection.getCandidates()));
        }
        if (detection.isSuccessful()) {
            IJ.log(String.format(Locale.US,
                    "Selected: method=%s, left=(%.4f,%.4f), right=(%.4f,%.4f), "
                            + "centerToCenter=%.6f, outerComponentWidth=%.6f, "
                            + "shaftAngle=%.3f, continuity=%.4f, confidence=%s, score=%.4f",
                    detection.getMethod().name(),
                    Double.valueOf(detection.getLeftTickCenterX()),
                    Double.valueOf(detection.getLeftTickCenterY()),
                    Double.valueOf(detection.getRightTickCenterX()),
                    Double.valueOf(detection.getRightTickCenterY()),
                    Double.valueOf(detection.getMeasuredLengthPx()),
                    Double.valueOf(detection.getOuterComponentWidthPx()),
                    Double.valueOf(detection.getShaftAngleDegrees()),
                    Double.valueOf(detection.getShaftContinuity()),
                    detection.getConfidence().name(),
                    Double.valueOf(detection.getScore())));
            IJ.log(String.format(Locale.US,
                    "Difference outerWidth - centerToCenter = %.6f px",
                    Double.valueOf(
                            detection.getOuterComponentWidthPx()
                                    - detection.getMeasuredLengthPx())));
        } else {
            IJ.log("Detection failed: " + detection.getFailureReason());
        }
    }
}
