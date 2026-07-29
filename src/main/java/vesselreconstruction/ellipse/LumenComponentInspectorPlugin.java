package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.plugin.PlugIn;

import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

/**
 * ImageJ entry point that opens detailed single-lumen diagnostics for one
 * user-selected component ID from the active classified image.
 */
public final class LumenComponentInspectorPlugin implements PlugIn {

    @Override
    public void run(final String arg) {
        final ImagePlus image = WindowManager.getCurrentImage();
        if (image == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "An 8-bit classified image must be opened and active.");
            return;
        }
        if (image.getNSlices() != 1 || image.getNFrames() != 1 || image.getNChannels() != 1) {
            // Allow ordinary single-plane ImagePlus; stacks are out of scope.
            if (image.getStackSize() != 1) {
                IJ.error(
                        "Robust Ellipse Reconstruction - Inspect Component ID",
                        "The active image must be a single-plane 8-bit classified image.");
                return;
            }
        }

        final ImageProcessor processor = image.getProcessor();
        if (processor == null || !(processor instanceof ByteProcessor)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "The active image must be an 8-bit classified image.\n"
                            + "RGB, 16-bit and 32-bit images are not supported.");
            return;
        }

        final ByteProcessor classified = (ByteProcessor) processor;
        final byte[] pixels = (byte[]) classified.getPixels();
        if (pixels == null) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "The active image has no pixel array.");
            return;
        }
        for (int i = 0; i < pixels.length; i++) {
            final int value = pixels[i] & 0xff;
            if (value != ClassifierLabels.WOOD_RESULT_VALUE
                    && value != ClassifierLabels.RAY_RESULT_VALUE
                    && value != ClassifierLabels.LUMEN_RESULT_VALUE
                    && value != ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                IJ.error(
                        "Robust Ellipse Reconstruction - Inspect Component ID",
                        "Unsupported raw result value " + value + " at index " + i + ".\n"
                                + "Expected only direct Trainable Weka result values 0, 1, 2, 3.");
                return;
            }
        }

        final List<LumenComponentDescriptor> components =
                new LumenComponentExtractor().extract(classified);
        if (components.isEmpty()) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "No Class 3 Lumen components (raw value 2) were found.");
            return;
        }

        final LumenComponentIdSelector selector = new LumenComponentIdSelector();
        final String availableRange = selector.describeAvailableRange(components);

        final GenericDialog dialog = new GenericDialog("Inspect Lumen Component");
        dialog.addMessage("Source: " + image.getTitle() + "\n"
                + "Image size: " + classified.getWidth() + " x " + classified.getHeight() + "\n"
                + availableRange + "\n\n"
                + "IDs apply only to this exact classified image.\n"
                + "Cropping or changing the image can change the IDs.");
        dialog.addNumericField("Component ID:", 1, 0);
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }

        final double requestedValue = dialog.getNextNumber();
        if (Double.isNaN(requestedValue) || Double.isInfinite(requestedValue)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "Component ID must be a finite number.");
            return;
        }
        if (requestedValue != Math.rint(requestedValue)) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "Component ID must be an integer; fractional values are not allowed.");
            return;
        }
        if (requestedValue > Integer.MAX_VALUE || requestedValue < Integer.MIN_VALUE) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "Component ID is outside the integer range.");
            return;
        }
        final int requestedId = (int) requestedValue;
        if (requestedId <= 0) {
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "Component ID must be positive.");
            return;
        }

        final LumenComponentDescriptor selected;
        try {
            selected = selector.selectById(components, requestedId);
        } catch (IllegalArgumentException ex) {
            IJ.log("Component ID selection failed: " + ex.getMessage());
            IJ.error(
                    "Robust Ellipse Reconstruction - Inspect Component ID",
                    "Component ID selection failed:\n" + ex.getMessage() + "\n"
                            + availableRange);
            return;
        }

        final Rectangle bounds = selected.getBounds();
        IJ.log("Lumen component inspector started.");
        IJ.log("sourceTitle,width,height,totalComponents,requestedComponentId,"
                + "selectedArea,minX,minY,maxX,maxY,touchesBorder");
        IJ.log(String.format(Locale.US,
                "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                sanitize(image.getTitle()),
                Integer.valueOf(classified.getWidth()),
                Integer.valueOf(classified.getHeight()),
                Integer.valueOf(components.size()),
                Integer.valueOf(requestedId),
                Integer.valueOf(selected.getArea()),
                Integer.valueOf(bounds.x),
                Integer.valueOf(bounds.y),
                Integer.valueOf(bounds.x + bounds.width - 1),
                Integer.valueOf(bounds.y + bounds.height - 1),
                Boolean.toString(selected.isTouchesImageBorder())));
        IJ.log("No batch filter is applied in inspector mode.");
        IJ.log("The selected component will be processed by the unchanged detailed");
        IJ.log("single-lumen diagnostic pipeline.");

        new EllipseReconstructionPlugin().runForSelectedComponent(image, selected);
    }

    private static String sanitize(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace(',', ';').replace('\n', ' ');
    }
}
