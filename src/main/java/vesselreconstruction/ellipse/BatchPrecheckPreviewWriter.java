package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Diagnostic PNG preview writer for Phase-2 precheck. Never modifies source TIFFs.
 */
public final class BatchPrecheckPreviewWriter {

    private BatchPrecheckPreviewWriter() {
    }

    public static void writeMaskPreview(
            final Path target,
            final ImagePlus downsampledRgbPreview,
            final DownsampledSampleMask mask,
            final AnalysisCropGeometry crop,
            final String imageId,
            final boolean accepted,
            final String reasonCode) throws IOException {
        if (downsampledRgbPreview == null || mask == null) {
            throw new IllegalArgumentException("mask preview requires RGB preview and mask");
        }
        final ColorProcessor base = (ColorProcessor) downsampledRgbPreview.getProcessor().duplicate();
        final byte[] maskPixels = mask.copyMaskPixels();
        final int w = mask.getMaskWidth();
        final int h = mask.getMaskHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((maskPixels[y * w + x] & 0xff) != 0) {
                    final int rgb = base.get(x, y);
                    final int r = Math.min(255, (((rgb >> 16) & 0xff) * 2 + 40) / 3);
                    final int g = Math.min(255, (((rgb >> 8) & 0xff) * 2 + 180) / 3);
                    final int b = Math.min(255, ((rgb & 0xff) * 2) / 3);
                    base.set(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }
        final BufferedImage image = base.getBufferedImage();
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (crop != null && crop.getCropWidthPx() > 0 && crop.getCropHeightPx() > 0) {
                final int factor = mask.getDownsampleFactor();
                final int cx = crop.getCropXPx() / factor;
                final int cy = crop.getCropYPx() / factor;
                final int cw = Math.max(1, (crop.getCropXPx() + crop.getCropWidthPx() + factor - 1)
                        / factor - cx);
                final int ch = Math.max(1, (crop.getCropYPx() + crop.getCropHeightPx() + factor - 1)
                        / factor - cy);
                g.setColor(Color.YELLOW);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(cx, cy, cw - 1, ch - 1);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, w / 40)));
            g.setColor(accepted ? new Color(0, 160, 0) : Color.RED);
            final String status = accepted ? "ACCEPTED" : ("REJECTED " + nullToNa(reasonCode));
            g.drawString(imageId + " mask " + status, 8, Math.max(16, h / 20));
        } finally {
            g.dispose();
        }
        writePng(target, image);
    }

    public static void writeScaleBarPreview(
            final Path target,
            final ImagePlus sourceRgb,
            final ScaleBarDetection detection,
            final String imageId,
            final boolean accepted,
            final String reasonCode,
            final double pixelSizeUm) throws IOException {
        if (sourceRgb == null) {
            throw new IllegalArgumentException("scale-bar preview requires source RGB");
        }
        final ImageProcessor proc = sourceRgb.getProcessor();
        final Rectangle bounds = detection != null && detection.getFullResolutionComponentBounds() != null
                ? detection.getFullResolutionComponentBounds()
                : (detection != null ? detection.getCandidateBoundsOriginal() : null);
        final int pad = 40;
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            x0 = Math.max(0, bounds.x - pad);
            y0 = Math.max(0, bounds.y - pad);
            x1 = Math.min(proc.getWidth(), bounds.x + bounds.width + pad);
            y1 = Math.min(proc.getHeight(), bounds.y + bounds.height + pad);
        } else {
            x0 = 0;
            y0 = 0;
            x1 = Math.min(proc.getWidth(), 400);
            y1 = Math.min(proc.getHeight(), 200);
        }
        final int cw = Math.max(1, x1 - x0);
        final int ch = Math.max(1, y1 - y0);
        proc.setRoi(x0, y0, cw, ch);
        final ImageProcessor crop = proc.crop();
        proc.resetRoi();
        final BufferedImage image = crop.getBufferedImage();
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (detection != null && detection.isSuccessful()) {
                final double lx = detection.getLeftTickCenterX() - x0;
                final double ly = detection.getLeftTickCenterY() - y0;
                final double rx = detection.getRightTickCenterX() - x0;
                final double ry = detection.getRightTickCenterY() - y0;
                g.setColor(Color.CYAN);
                g.setStroke(new BasicStroke(3f));
                g.drawLine((int) Math.round(lx), (int) Math.round(ly),
                        (int) Math.round(rx), (int) Math.round(ry));
                g.fillOval((int) Math.round(lx) - 4, (int) Math.round(ly) - 4, 8, 8);
                g.fillOval((int) Math.round(rx) - 4, (int) Math.round(ry) - 4, 8, 8);
                if (bounds != null) {
                    g.setColor(Color.BLUE);
                    g.setStroke(new BasicStroke(2f));
                    g.drawRect(bounds.x - x0, bounds.y - y0, bounds.width - 1, bounds.height - 1);
                }
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(accepted ? new Color(0, 160, 0) : Color.RED);
            final String conf = detection == null ? RCsvSupport.NA : detection.getConfidence().name();
            final String measured = detection == null || !Double.isFinite(detection.getMeasuredLengthPx())
                    ? RCsvSupport.NA
                    : String.format(Locale.US, "%.3f px",
                    Double.valueOf(detection.getMeasuredLengthPx()));
            final String px = Double.isFinite(pixelSizeUm)
                    ? String.format(Locale.US, "%.6f um/px", Double.valueOf(pixelSizeUm))
                    : RCsvSupport.NA;
            int lineY = 18;
            g.drawString(imageId + " scale-bar " + (accepted ? "ACCEPTED" : "REJECTED"), 8, lineY);
            lineY += 18;
            g.drawString("confidence=" + conf + " measured=" + measured, 8, lineY);
            lineY += 18;
            g.drawString("pixelSize=" + px, 8, lineY);
            if (!accepted) {
                lineY += 18;
                g.drawString("reason=" + nullToNa(reasonCode), 8, lineY);
            }
        } finally {
            g.dispose();
        }
        writePng(target, image);
    }

    private static void writePng(final Path target, final BufferedImage image) throws IOException {
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            if (!ImageIO.write(image, "png", temp.toFile())) {
                throw new IOException("ImageIO failed to write PNG: " + target);
            }
            try {
                Files.move(temp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String nullToNa(final String value) {
        return value == null || value.trim().isEmpty() ? RCsvSupport.NA : value.trim();
    }
}
