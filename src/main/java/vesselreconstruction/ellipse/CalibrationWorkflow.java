package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.process.ColorProcessor;

import java.util.Locale;

/**
 * Interactive calibration acquisition and confirmation.
 *
 * <p>Weka classification must not begin until {@link Outcome#ACCEPTED} is returned.</p>
 */
public final class CalibrationWorkflow {

    public enum Outcome {
        ACCEPTED,
        CANCELLED
    }

    public static final class Result {
        private final Outcome outcome;
        private final PhysicalCalibration calibration;
        private final ScaleBarDetection scaleBarDetection;

        private Result(
                final Outcome outcome,
                final PhysicalCalibration calibration,
                final ScaleBarDetection scaleBarDetection) {
            this.outcome = outcome;
            this.calibration = calibration;
            this.scaleBarDetection = scaleBarDetection;
        }

        public static Result accepted(final PhysicalCalibration calibration) {
            return accepted(calibration, null);
        }

        public static Result accepted(
                final PhysicalCalibration calibration,
                final ScaleBarDetection detection) {
            return new Result(Outcome.ACCEPTED, calibration, detection);
        }

        public static Result cancelled() {
            return new Result(Outcome.CANCELLED, null, null);
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public PhysicalCalibration getCalibration() {
            return calibration;
        }

        public ScaleBarDetection getScaleBarDetection() {
            return scaleBarDetection;
        }
    }

    private final PhysicalCalibrationService service = new PhysicalCalibrationService();
    private final AutomaticScaleBarDetector detector = new AutomaticScaleBarDetector();
    private final ScaleBarDetectionPreviewController previewController =
            new ScaleBarDetectionPreviewController();

    public Result acquire(
            final ImagePlus sourceImage,
            final TiledWekaClassificationConfiguration configuration) {
        return acquire(sourceImage, configuration, null, null);
    }

    public Result acquire(
            final ImagePlus sourceImage,
            final TiledWekaClassificationConfiguration configuration,
            final DownsampledSampleMask approvedMask,
            final ColorProcessor downsampledRgbPreview) {
        if (sourceImage == null || configuration == null) {
            throw new IllegalArgumentException("sourceImage and configuration required");
        }
        final CalibrationMode mode = configuration.getCalibrationMode();
        IJ.log("Calibration mode: " + mode.name());
        IJ.log("Source image: " + safeTitle(sourceImage));

        try {
            switch (mode) {
                case AUTO_DETECT_RED_SCALE_BAR:
                    if (approvedMask == null) {
                        throw new IllegalArgumentException(
                                "Approved sample mask is required for automatic scale-bar detection");
                    }
                    return autoDetectWithFallback(
                            sourceImage, configuration, approvedMask, downsampledRgbPreview);
                case MEASURE_SCALE_BAR_LINE:
                    return measureScaleBarWithRetry(sourceImage, configuration);
                case ENTER_PIXEL_SIZE:
                    return Result.accepted(service.fromEnteredPixelSize(
                            configuration.getEnteredPixelWidthUm().get().doubleValue(),
                            configuration.getEnteredPixelHeightUm().orElse(null)));
                case USE_VALID_MICROMETRE_METADATA:
                    return Result.accepted(service.fromValidMicrometreMetadata(sourceImage));
                default:
                    throw new IllegalStateException("Unsupported calibration mode: " + mode);
            }
        } catch (final IllegalArgumentException ex) {
            IJ.error(
                    "Calibration failed",
                    "Calibration mode: " + mode.name() + "\n"
                            + "Source image: " + safeTitle(sourceImage) + "\n"
                            + "Reason: " + ex.getMessage());
            return Result.cancelled();
        }
    }

    private Result autoDetectWithFallback(
            final ImagePlus sourceImage,
            final TiledWekaClassificationConfiguration configuration,
            final DownsampledSampleMask approvedMask,
            final ColorProcessor downsampledRgbPreview) {
        while (true) {
            IJ.log("Automatic red scale-bar detection started");
            IJ.log(String.format(Locale.US,
                    "Search image dimensions: %d x %d",
                    Integer.valueOf(sourceImage.getWidth()),
                    Integer.valueOf(sourceImage.getHeight())));
            IJ.log("Search restricted to exterior mask: true");
            IJ.log(String.format(Locale.US,
                    "Physical scale-bar value (entered, not OCR): %.6f µm",
                    Double.valueOf(configuration.getScaleBarPhysicalLengthUm())));

            final ScaleBarDetection detection = detector.detect(
                    sourceImage, approvedMask, downsampledRgbPreview);
            IJ.log("Low-resolution candidate count: " + detection.getCandidates().size());
            ScaleBarDetectionPreviewController.logDetection(detection);

            final ScaleBarDetectionPreviewController.Action action =
                    previewController.confirm(
                            sourceImage,
                            detection,
                            configuration.getScaleBarPhysicalLengthUm());

            switch (action) {
                case ACCEPT_AUTOMATIC:
                    if (!detection.isAcceptableForConfirmation()) {
                        IJ.error(
                                "Calibration failed",
                                "Automatic detection confidence is insufficient for acceptance.");
                        continue;
                    }
                    if (detection.getMethod() != ScaleBarDetectionMethod.TERMINAL_TICK_CENTERLINES
                            && detection.getMethod() != ScaleBarDetectionMethod.SHAFT_ENDPOINTS) {
                        IJ.error(
                                "Calibration failed",
                                "Automatic detection has no usable measurement method.");
                        continue;
                    }
                    // Prefer terminal-tick path; shaft-only is LOW and not acceptable above.
                    final PhysicalCalibration calibration =
                            PhysicalCalibration.fromAutoDetectedScaleBar(
                                    configuration.getScaleBarPhysicalLengthUm(),
                                    detection.getMeasuredLengthPx());
                    IJ.log(String.format(Locale.US,
                            "Accepted automatic calibration: method=%s, measuredCenterToCenterPx=%.17g, "
                                    + "physicalUm=%.17g, pixelSizeUm=%.17g",
                            detection.getMethod().name(),
                            Double.valueOf(detection.getMeasuredLengthPx()),
                            Double.valueOf(configuration.getScaleBarPhysicalLengthUm()),
                            Double.valueOf(calibration.getPixelWidthUm())));
                    return Result.accepted(calibration, detection);
                case RETRY_AUTOMATIC:
                    continue;
                case USE_MANUAL_LINE:
                    return measureScaleBarWithRetry(sourceImage, configuration);
                case ENTER_PIXEL_SIZE:
                    return promptEnteredPixelSize();
                case CANCEL:
                default:
                    IJ.log("Classification cancelled during automatic scale-bar calibration.");
                    return Result.cancelled();
            }
        }
    }

    private Result promptEnteredPixelSize() {
        final GenericDialog dialog = new GenericDialog("Enter Pixel Size");
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_ENTERED_PIXEL_WIDTH,
                Double.NaN,
                6);
        dialog.addNumericField(
                TiledWekaClassificationDefaults.LABEL_ENTERED_PIXEL_HEIGHT,
                Double.NaN,
                6);
        dialog.addMessage("Leave height blank/NaN to use isotropic width for both axes.");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return Result.cancelled();
        }
        final double width = dialog.getNextNumber();
        final double height = dialog.getNextNumber();
        if (Double.isNaN(width) || Double.isInfinite(width) || !(width > 0.0)) {
            IJ.error("Calibration failed", "Entered pixel width must be finite and > 0.");
            return Result.cancelled();
        }
        final Double heightOrNull = Double.isNaN(height) ? null : Double.valueOf(height);
        if (heightOrNull != null
                && (Double.isInfinite(heightOrNull.doubleValue())
                || !(heightOrNull.doubleValue() > 0.0))) {
            IJ.error("Calibration failed", "Entered pixel height must be finite and > 0.");
            return Result.cancelled();
        }
        return Result.accepted(service.fromEnteredPixelSize(width, heightOrNull));
    }

    private Result measureScaleBarWithRetry(
            final ImagePlus sourceImage,
            final TiledWekaClassificationConfiguration configuration) {
        sourceImage.show();
        WindowManagerFront.bringToFront(sourceImage);
        final Roi previousRoi = sourceImage.getRoi() == null
                ? null : (Roi) sourceImage.getRoi().clone();

        while (true) {
            final NonBlockingGenericDialog instruct = new NonBlockingGenericDialog(
                    "Measure Scale Bar");
            instruct.addMessage(
                    "Draw a straight line ROI exactly along the known scale bar.\n"
                            + "The line must span the physical bar length, not the text.\n"
                            + "Physical length: "
                            + configuration.getScaleBarPhysicalLengthUm() + " µm\n\n"
                            + "Click OK after the line is drawn.");
            instruct.showDialog();
            if (instruct.wasCanceled()) {
                restoreRoi(sourceImage, previousRoi);
                return Result.cancelled();
            }

            final PhysicalCalibration calibration;
            try {
                calibration = service.fromScaleBarLine(
                        sourceImage,
                        configuration.getScaleBarPhysicalLengthUm());
            } catch (final IllegalArgumentException ex) {
                IJ.error(
                        "Calibration failed",
                        "Calibration mode: MEASURE_SCALE_BAR_LINE\n"
                                + "Source image: " + safeTitle(sourceImage) + "\n"
                                + "Reason: " + ex.getMessage());
                continue;
            }

            final GenericDialog confirm = new GenericDialog("Confirm Scale-Bar Calibration");
            confirm.addMessage(String.format(Locale.US,
                    "Measured scale-bar length: %.6f px\n"
                            + "Physical scale-bar value: %.6f µm\n"
                            + "Calculated micrometres per pixel: %.12f µm/px\n\n"
                            + "Accept, Retry measurement, or Cancel classification.",
                    calibration.getScaleBarMeasuredLengthPx(),
                    calibration.getScaleBarPhysicalLengthUm(),
                    Double.valueOf(calibration.getPixelWidthUm())));
            confirm.enableYesNoCancel("Accept", "Retry measurement");
            confirm.showDialog();
            if (confirm.wasCanceled()) {
                restoreRoi(sourceImage, previousRoi);
                return Result.cancelled();
            }
            if (confirm.wasOKed()) {
                restoreRoi(sourceImage, previousRoi);
                IJ.log(String.format(Locale.US,
                        "Scale-bar calibration confirmed: measuredPx=%.17g, physicalUm=%.17g, "
                                + "pixelSizeUm=%.17g",
                        calibration.getScaleBarMeasuredLengthPx(),
                        calibration.getScaleBarPhysicalLengthUm(),
                        Double.valueOf(calibration.getPixelWidthUm())));
                return Result.accepted(calibration);
            }
        }
    }

    private static void restoreRoi(final ImagePlus image, final Roi previous) {
        if (previous == null) {
            image.deleteRoi();
        } else {
            image.setRoi(previous);
        }
    }

    private static String safeTitle(final ImagePlus image) {
        return image.getTitle() == null ? "Untitled" : image.getTitle();
    }

    /**
     * Tiny helper so calibration code can request fronting without depending on
     * WindowManager in unit tests that never construct ImageWindows.
     */
    static final class WindowManagerFront {
        private WindowManagerFront() {
        }

        static void bringToFront(final ImagePlus image) {
            try {
                if (image.getWindow() != null) {
                    image.getWindow().toFront();
                }
            } catch (final Exception ignored) {
                // headless-safe
            }
        }
    }
}
