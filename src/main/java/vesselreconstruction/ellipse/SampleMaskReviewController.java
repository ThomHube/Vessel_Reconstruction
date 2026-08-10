package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;

/**
 * Interactive review of the downsampled sample mask using a non-blocking dialog
 * so the user can edit the binary mask image before classification begins.
 */
public final class SampleMaskReviewController {

    public SampleMaskReviewResult review(
            final SampleMaskGenerationResult generationResult,
            final boolean reviewEnabled) {
        if (generationResult == null) {
            throw new IllegalArgumentException("generationResult must not be null");
        }
        final DownsampledSampleMask automaticMask = generationResult.getMask();
        final ImagePlus rgbPreview = generationResult.getRgbPreview();
        final ImagePlus editableMask = generationResult.getEditableMaskImage();

        maybeAttachStaticOverlay(rgbPreview, automaticMask);
        rgbPreview.show();
        editableMask.show();

        if (!reviewEnabled) {
            IJ.log("Sample-mask review disabled; using automatic mask.");
            return new SampleMaskReviewResult(
                    automaticMask,
                    false,
                    editableMask,
                    rgbPreview);
        }

        final NonBlockingGenericDialog dialog =
                new NonBlockingGenericDialog("Approve Sample Analysis Mask");
        dialog.addMessage(
                "White pixels = included sample region.\n"
                        + "Black pixels = excluded exterior region.\n\n"
                        + "The mask is downsampled for memory efficiency.\n"
                        + "The original RGB TIFF remains completely unchanged.\n"
                        + "The scale bar should remain black/excluded in the mask.\n"
                        + "Pale lumina inside the wood section should remain white/included.\n\n"
                        + "Edit the binary mask image with normal ImageJ tools while this\n"
                        + "dialog remains open.\n\n"
                        + "OK accepts the edited mask and starts classification.\n"
                        + "Cancel aborts without classification.");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            IJ.log("Sample-mask review was cancelled. "
                    + "Tiled Weka classification was not run.");
            throw new SampleMaskReviewCancelledException(
                    "Sample-mask review was cancelled. "
                            + "Tiled Weka classification was not run.");
        }

        final DownsampledSampleMask approved = readApprovedMask(
                editableMask,
                automaticMask.getOriginalWidth(),
                automaticMask.getOriginalHeight(),
                automaticMask.getDownsampleFactor());
        IJ.log("Sample-mask reviewed and approved.");
        return new SampleMaskReviewResult(
                approved,
                true,
                editableMask,
                rgbPreview);
    }

    static DownsampledSampleMask readApprovedMask(
            final ImagePlus editableMask,
            final int originalWidth,
            final int originalHeight,
            final int downsampleFactor) {
        if (editableMask == null) {
            throw new IllegalArgumentException("editableMask must not be null");
        }
        final int expectedWidth = DownsampledSampleMask.maskDimension(
                originalWidth, downsampleFactor);
        final int expectedHeight = DownsampledSampleMask.maskDimension(
                originalHeight, downsampleFactor);
        if (editableMask.getWidth() != expectedWidth
                || editableMask.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(
                    "Approved sample mask dimensions changed incompatibly. Expected "
                            + expectedWidth + "x" + expectedHeight
                            + ", got " + editableMask.getWidth() + "x"
                            + editableMask.getHeight());
        }
        if (editableMask.getStackSize() != 1) {
            throw new IllegalArgumentException(
                    "Approved sample mask must remain a single image plane");
        }
        final ImageProcessor processor = editableMask.getProcessor();
        if (!(processor instanceof ByteProcessor)) {
            throw new IllegalArgumentException(
                    "Approved sample mask must remain an 8-bit binary image");
        }
        final byte[] sourcePixels = (byte[]) processor.getPixels();
        final byte[] normalized = new byte[sourcePixels.length];
        long included = 0L;
        for (int i = 0; i < sourcePixels.length; i++) {
            if ((sourcePixels[i] & 0xff) != 0) {
                normalized[i] = (byte) 255;
                included++;
            } else {
                normalized[i] = 0;
            }
        }
        if (included == 0L) {
            throw new IllegalArgumentException(
                    "Approved sample mask contains no included pixels. "
                            + "Classification was aborted.");
        }
        // Reflect normalized values back into the open image.
        System.arraycopy(normalized, 0, sourcePixels, 0, normalized.length);
        editableMask.updateAndDraw();
        return new DownsampledSampleMask(
                originalWidth,
                originalHeight,
                downsampleFactor,
                normalized,
                expectedWidth,
                expectedHeight);
    }

    private static void maybeAttachStaticOverlay(
            final ImagePlus rgbPreview,
            final DownsampledSampleMask mask) {
        try {
            final ByteProcessor maskProcessor = new ByteProcessor(
                    mask.getMaskWidth(), mask.getMaskHeight());
            System.arraycopy(
                    mask.copyMaskPixels(),
                    0,
                    maskProcessor.getPixels(),
                    0,
                    mask.getMaskWidth() * mask.getMaskHeight());
            maskProcessor.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            final Roi roi = ij.plugin.filter.ThresholdToSelection.run(new ImagePlus(
                    "mask-roi", maskProcessor));
            if (roi == null) {
                return;
            }
            roi.setStrokeColor(new Color(0, 180, 0));
            roi.setFillColor(new Color(0, 200, 0, 70));
            rgbPreview.setOverlay(new Overlay(roi));
        } catch (final Throwable ignored) {
            // Overlay is optional; classification depends only on the binary mask.
        }
    }
}
