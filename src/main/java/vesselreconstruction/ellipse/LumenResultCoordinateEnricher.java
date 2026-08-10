package vesselreconstruction.ellipse;

import ij.IJ;
import ij.ImagePlus;

import java.awt.Rectangle;
import java.util.Optional;

/**
 * Adds crop/original/physical/global coordinate columns and calibrated
 * dimensional measurements to All Lumina results without changing reconstruction.
 */
public final class LumenResultCoordinateEnricher {

    public static final class EnrichedValues {
        public String sampleId = "";
        public String segmentId = "";
        public String segmentOrder = "";
        public String segmentOrientation = "";
        public String cropXOffsetPx = "";
        public String cropYOffsetPx = "";
        public String originalWidthPx = "";
        public String originalHeightPx = "";
        public String pixelWidthUm = "";
        public String pixelHeightUm = "";
        public String centroidXCropPx = "";
        public String centroidYCropPx = "";
        public String centroidXOriginalPx = "";
        public String centroidYOriginalPx = "";
        public String centroidXOriginalUm = "";
        public String centroidYOriginalUm = "";
        public String segmentAxisPositionUm = "";
        public String globalAxisPositionUm = "";
        public String areaUm2 = "";
        public String boundingBoxWidthUm = "";
        public String boundingBoxHeightUm = "";
        public boolean contextAvailable;
    }

    public EnrichedValues enrich(
            final ImagePlus classifiedImage,
            final LumenBatchItemResult item) {
        final EnrichedValues values = new EnrichedValues();
        final Rectangle bounds = item.getSourceBounds();
        // Preserve existing centroid convention used by the overview overlay.
        final double centroidXCropPx = bounds.x + bounds.width / 2.0;
        final double centroidYCropPx = bounds.y + bounds.height / 2.0;
        values.centroidXCropPx = format(centroidXCropPx);
        values.centroidYCropPx = format(centroidYCropPx);

        final Object info = classifiedImage == null ? null : classifiedImage.getProperty("Info");
        final LumenImageMetadata.ParseResult parsed = LumenImageMetadata.parse(
                info == null ? null : String.valueOf(info));
        if (!parsed.isAvailable()) {
            values.contextAvailable = false;
            IJ.log("All Lumina: calibrated original/global coordinates unavailable: "
                    + parsed.getUnavailableReason());
            return values;
        }

        final LumenCoordinateContext context = parsed.getMetadata().get().toCoordinateContext();
        values.contextAvailable = true;
        values.sampleId = context.getSampleId();
        values.segmentId = context.getSegmentId();
        values.segmentOrder = Integer.toString(context.getSegmentOrder());
        values.segmentOrientation = context.getSegmentOrientation().name();
        values.cropXOffsetPx = Integer.toString(context.getCropX());
        values.cropYOffsetPx = Integer.toString(context.getCropY());
        values.originalWidthPx = Integer.toString(context.getOriginalWidthPx());
        values.originalHeightPx = Integer.toString(context.getOriginalHeightPx());
        values.pixelWidthUm = format(context.getPixelWidthUm());
        values.pixelHeightUm = format(context.getPixelHeightUm());

        final double originalX = context.cropXToOriginal(centroidXCropPx);
        final double originalY = context.cropYToOriginal(centroidYCropPx);
        values.centroidXOriginalPx = format(originalX);
        values.centroidYOriginalPx = format(originalY);
        values.centroidXOriginalUm = format(context.originalXToPhysicalUm(originalX));
        values.centroidYOriginalUm = format(context.originalYToPhysicalUm(originalY));

        final Optional<Double> axis = context.segmentAxisPositionUm(originalX, originalY);
        values.segmentAxisPositionUm = axis.isPresent() ? format(axis.get().doubleValue()) : "";
        final Optional<Double> global = context.globalAxisPositionUm(originalX, originalY);
        values.globalAxisPositionUm = global.isPresent() ? format(global.get().doubleValue()) : "";

        values.areaUm2 = format(context.areaPxToUm2(item.getSourceArea()));
        values.boundingBoxWidthUm = format(bounds.width * context.getPixelWidthUm());
        values.boundingBoxHeightUm = format(bounds.height * context.getPixelHeightUm());
        return values;
    }

    private static String format(final double value) {
        return LumenImageMetadata.formatDouble(value);
    }
}
