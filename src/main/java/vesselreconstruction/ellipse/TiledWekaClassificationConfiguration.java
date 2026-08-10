package vesselreconstruction.ellipse;

import java.io.File;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable configuration for tiled Weka classification and dataset preparation.
 */
public final class TiledWekaClassificationConfiguration {

    private final int coreTileWidthPixels;
    private final int coreTileHeightPixels;
    private final int threadsPerTile;
    private final boolean runUntiledReference;
    private final int seamAuditHalfWidthPixels;
    private final int sampleMaskDownsampleFactor;
    private final int sampleMaskExpansionMarginOriginalPixels;
    private final boolean reviewAndEditSampleMask;

    private final boolean cropOutputToApprovedMaskExtent;
    private final int additionalCropPaddingOriginalPixels;
    private final boolean saveCroppedClassifiedTiffAutomatically;
    private final boolean saveApprovedDownsampledMaskAutomatically;
    private final boolean saveCroppedRgbCopy;

    private final String sampleId;
    private final String segmentId;
    private final int segmentOrder;
    private final SegmentOrientation segmentOrientation;
    private final Double optionalGlobalSegmentStartUm;
    private final File outputDirectory;

    private final CalibrationMode calibrationMode;
    private final double scaleBarPhysicalLengthUm;
    private final Double enteredPixelWidthUm;
    private final Double enteredPixelHeightUm;

    public TiledWekaClassificationConfiguration(
            final int coreTileWidthPixels,
            final int coreTileHeightPixels,
            final int threadsPerTile,
            final boolean runUntiledReference,
            final int seamAuditHalfWidthPixels,
            final int sampleMaskDownsampleFactor,
            final int sampleMaskExpansionMarginOriginalPixels,
            final boolean reviewAndEditSampleMask) {
        this(
                coreTileWidthPixels,
                coreTileHeightPixels,
                threadsPerTile,
                runUntiledReference,
                seamAuditHalfWidthPixels,
                sampleMaskDownsampleFactor,
                sampleMaskExpansionMarginOriginalPixels,
                reviewAndEditSampleMask,
                TiledWekaClassificationDefaults.CROP_OUTPUT_TO_APPROVED_MASK_EXTENT,
                TiledWekaClassificationDefaults.ADDITIONAL_CROP_PADDING_ORIGINAL_PIXELS,
                TiledWekaClassificationDefaults.SAVE_CROPPED_CLASSIFIED_TIFF_AUTOMATICALLY,
                TiledWekaClassificationDefaults.SAVE_APPROVED_DOWNSAMPLED_MASK_AUTOMATICALLY,
                TiledWekaClassificationDefaults.SAVE_CROPPED_RGB_COPY,
                "sample",
                "segment",
                TiledWekaClassificationDefaults.SEGMENT_ORDER,
                TiledWekaClassificationDefaults.SEGMENT_ORIENTATION,
                null,
                new File(TiledWekaClassificationDefaults.OUTPUT_DIRECTORY_NAME),
                TiledWekaClassificationDefaults.CALIBRATION_MODE,
                TiledWekaClassificationDefaults.SCALE_BAR_PHYSICAL_LENGTH_UM,
                null,
                null);
    }

    public TiledWekaClassificationConfiguration(
            final int coreTileWidthPixels,
            final int coreTileHeightPixels,
            final int threadsPerTile,
            final boolean runUntiledReference,
            final int seamAuditHalfWidthPixels,
            final int sampleMaskDownsampleFactor,
            final int sampleMaskExpansionMarginOriginalPixels,
            final boolean reviewAndEditSampleMask,
            final boolean cropOutputToApprovedMaskExtent,
            final int additionalCropPaddingOriginalPixels,
            final boolean saveCroppedClassifiedTiffAutomatically,
            final boolean saveApprovedDownsampledMaskAutomatically,
            final boolean saveCroppedRgbCopy,
            final String sampleId,
            final String segmentId,
            final int segmentOrder,
            final SegmentOrientation segmentOrientation,
            final Double optionalGlobalSegmentStartUm,
            final File outputDirectory,
            final CalibrationMode calibrationMode,
            final double scaleBarPhysicalLengthUm,
            final Double enteredPixelWidthUm,
            final Double enteredPixelHeightUm) {
        if (coreTileWidthPixels <= 0 || coreTileHeightPixels <= 0) {
            throw new IllegalArgumentException(
                    "Core tile width and height must both be greater than zero.");
        }
        if (threadsPerTile < 1) {
            throw new IllegalArgumentException(
                    "threadsPerTile must be >= 1: " + threadsPerTile);
        }
        if (seamAuditHalfWidthPixels < 0) {
            throw new IllegalArgumentException(
                    "seamAuditHalfWidthPixels must be >= 0: " + seamAuditHalfWidthPixels);
        }
        if (sampleMaskDownsampleFactor <= 0) {
            throw new IllegalArgumentException(
                    "sampleMaskDownsampleFactor must be > 0: " + sampleMaskDownsampleFactor);
        }
        if (sampleMaskExpansionMarginOriginalPixels < 0) {
            throw new IllegalArgumentException(
                    "sampleMaskExpansionMarginOriginalPixels must be >= 0: "
                            + sampleMaskExpansionMarginOriginalPixels);
        }
        if (additionalCropPaddingOriginalPixels < 0) {
            throw new IllegalArgumentException(
                    "additionalCropPaddingOriginalPixels must be >= 0: "
                            + additionalCropPaddingOriginalPixels);
        }
        if (sampleId == null || sampleId.trim().isEmpty()) {
            throw new IllegalArgumentException("Sample ID must not be blank");
        }
        if (segmentId == null || segmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Segment ID must not be blank");
        }
        if (segmentOrder < 1) {
            throw new IllegalArgumentException("Segment order must be >= 1");
        }
        if (segmentOrientation == null) {
            throw new IllegalArgumentException("segmentOrientation must not be null");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Output directory must not be null");
        }
        if (calibrationMode == null) {
            throw new IllegalArgumentException("calibrationMode must not be null");
        }
        if (optionalGlobalSegmentStartUm != null
                && (Double.isNaN(optionalGlobalSegmentStartUm.doubleValue())
                || Double.isInfinite(optionalGlobalSegmentStartUm.doubleValue()))) {
            throw new IllegalArgumentException(
                    "Optional global segment start must be a finite number when provided");
        }
        if (calibrationMode == CalibrationMode.ENTER_PIXEL_SIZE) {
            if (enteredPixelWidthUm == null
                    || !(enteredPixelWidthUm.doubleValue() > 0.0)
                    || Double.isNaN(enteredPixelWidthUm.doubleValue())
                    || Double.isInfinite(enteredPixelWidthUm.doubleValue())) {
                throw new IllegalArgumentException(
                        "Entered pixel width must be finite and > 0");
            }
            if (enteredPixelHeightUm != null
                    && (!(enteredPixelHeightUm.doubleValue() > 0.0)
                    || Double.isNaN(enteredPixelHeightUm.doubleValue())
                    || Double.isInfinite(enteredPixelHeightUm.doubleValue()))) {
                throw new IllegalArgumentException(
                        "Entered pixel height must be finite and > 0 when provided");
            }
        }
        if (!(scaleBarPhysicalLengthUm > 0.0)
                || Double.isNaN(scaleBarPhysicalLengthUm)
                || Double.isInfinite(scaleBarPhysicalLengthUm)) {
            throw new IllegalArgumentException(
                    "Scale bar physical length must be finite and > 0");
        }

        this.coreTileWidthPixels = coreTileWidthPixels;
        this.coreTileHeightPixels = coreTileHeightPixels;
        this.threadsPerTile = threadsPerTile;
        this.runUntiledReference = runUntiledReference;
        this.seamAuditHalfWidthPixels = seamAuditHalfWidthPixels;
        this.sampleMaskDownsampleFactor = sampleMaskDownsampleFactor;
        this.sampleMaskExpansionMarginOriginalPixels = sampleMaskExpansionMarginOriginalPixels;
        this.reviewAndEditSampleMask = reviewAndEditSampleMask;
        this.cropOutputToApprovedMaskExtent = cropOutputToApprovedMaskExtent;
        this.additionalCropPaddingOriginalPixels = additionalCropPaddingOriginalPixels;
        this.saveCroppedClassifiedTiffAutomatically = saveCroppedClassifiedTiffAutomatically;
        this.saveApprovedDownsampledMaskAutomatically = saveApprovedDownsampledMaskAutomatically;
        this.saveCroppedRgbCopy = saveCroppedRgbCopy;
        this.sampleId = sampleId.trim();
        this.segmentId = segmentId.trim();
        this.segmentOrder = segmentOrder;
        this.segmentOrientation = segmentOrientation;
        this.optionalGlobalSegmentStartUm = optionalGlobalSegmentStartUm;
        this.outputDirectory = outputDirectory;
        this.calibrationMode = calibrationMode;
        this.scaleBarPhysicalLengthUm = scaleBarPhysicalLengthUm;
        this.enteredPixelWidthUm = enteredPixelWidthUm;
        this.enteredPixelHeightUm = enteredPixelHeightUm;
    }

    public int getCoreTileWidthPixels() {
        return coreTileWidthPixels;
    }

    public int getCoreTileHeightPixels() {
        return coreTileHeightPixels;
    }

    public int getThreadsPerTile() {
        return threadsPerTile;
    }

    public boolean isRunUntiledReference() {
        return runUntiledReference;
    }

    public int getSeamAuditHalfWidthPixels() {
        return seamAuditHalfWidthPixels;
    }

    public int getSampleMaskDownsampleFactor() {
        return sampleMaskDownsampleFactor;
    }

    public int getSampleMaskExpansionMarginOriginalPixels() {
        return sampleMaskExpansionMarginOriginalPixels;
    }

    public boolean isReviewAndEditSampleMask() {
        return reviewAndEditSampleMask;
    }

    public boolean isCropOutputToApprovedMaskExtent() {
        return cropOutputToApprovedMaskExtent;
    }

    public int getAdditionalCropPaddingOriginalPixels() {
        return additionalCropPaddingOriginalPixels;
    }

    public boolean isSaveCroppedClassifiedTiffAutomatically() {
        return saveCroppedClassifiedTiffAutomatically;
    }

    public boolean isSaveApprovedDownsampledMaskAutomatically() {
        return saveApprovedDownsampledMaskAutomatically;
    }

    public boolean isSaveCroppedRgbCopy() {
        return saveCroppedRgbCopy;
    }

    public String getSampleId() {
        return sampleId;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public int getSegmentOrder() {
        return segmentOrder;
    }

    public SegmentOrientation getSegmentOrientation() {
        return segmentOrientation;
    }

    public Optional<Double> getOptionalGlobalSegmentStartUm() {
        return Optional.ofNullable(optionalGlobalSegmentStartUm);
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public CalibrationMode getCalibrationMode() {
        return calibrationMode;
    }

    public double getScaleBarPhysicalLengthUm() {
        return scaleBarPhysicalLengthUm;
    }

    public Optional<Double> getEnteredPixelWidthUm() {
        return Optional.ofNullable(enteredPixelWidthUm);
    }

    public Optional<Double> getEnteredPixelHeightUm() {
        return Optional.ofNullable(enteredPixelHeightUm);
    }

    public CoreTileLayout createLayout(
            final int imageWidth,
            final int imageHeight,
            final int classificationHaloPixels) {
        return CoreTileLayout.create(
                imageWidth,
                imageHeight,
                coreTileWidthPixels,
                coreTileHeightPixels,
                classificationHaloPixels);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TiledWekaClassificationConfiguration)) {
            return false;
        }
        final TiledWekaClassificationConfiguration other =
                (TiledWekaClassificationConfiguration) obj;
        return coreTileWidthPixels == other.coreTileWidthPixels
                && coreTileHeightPixels == other.coreTileHeightPixels
                && threadsPerTile == other.threadsPerTile
                && runUntiledReference == other.runUntiledReference
                && seamAuditHalfWidthPixels == other.seamAuditHalfWidthPixels
                && sampleMaskDownsampleFactor == other.sampleMaskDownsampleFactor
                && sampleMaskExpansionMarginOriginalPixels
                == other.sampleMaskExpansionMarginOriginalPixels
                && reviewAndEditSampleMask == other.reviewAndEditSampleMask
                && cropOutputToApprovedMaskExtent == other.cropOutputToApprovedMaskExtent
                && additionalCropPaddingOriginalPixels
                == other.additionalCropPaddingOriginalPixels
                && saveCroppedClassifiedTiffAutomatically
                == other.saveCroppedClassifiedTiffAutomatically
                && saveApprovedDownsampledMaskAutomatically
                == other.saveApprovedDownsampledMaskAutomatically
                && saveCroppedRgbCopy == other.saveCroppedRgbCopy
                && sampleId.equals(other.sampleId)
                && segmentId.equals(other.segmentId)
                && segmentOrder == other.segmentOrder
                && segmentOrientation == other.segmentOrientation
                && Objects.equals(optionalGlobalSegmentStartUm, other.optionalGlobalSegmentStartUm)
                && outputDirectory.equals(other.outputDirectory)
                && calibrationMode == other.calibrationMode
                && Double.compare(scaleBarPhysicalLengthUm, other.scaleBarPhysicalLengthUm) == 0
                && Objects.equals(enteredPixelWidthUm, other.enteredPixelWidthUm)
                && Objects.equals(enteredPixelHeightUm, other.enteredPixelHeightUm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(coreTileWidthPixels),
                Integer.valueOf(coreTileHeightPixels),
                Integer.valueOf(threadsPerTile),
                Boolean.valueOf(runUntiledReference),
                Integer.valueOf(seamAuditHalfWidthPixels),
                Integer.valueOf(sampleMaskDownsampleFactor),
                Integer.valueOf(sampleMaskExpansionMarginOriginalPixels),
                Boolean.valueOf(reviewAndEditSampleMask),
                Boolean.valueOf(cropOutputToApprovedMaskExtent),
                Integer.valueOf(additionalCropPaddingOriginalPixels),
                Boolean.valueOf(saveCroppedClassifiedTiffAutomatically),
                Boolean.valueOf(saveApprovedDownsampledMaskAutomatically),
                Boolean.valueOf(saveCroppedRgbCopy),
                sampleId,
                segmentId,
                Integer.valueOf(segmentOrder),
                segmentOrientation,
                optionalGlobalSegmentStartUm,
                outputDirectory,
                calibrationMode,
                Double.valueOf(scaleBarPhysicalLengthUm),
                enteredPixelWidthUm,
                enteredPixelHeightUm);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "TiledWekaClassificationConfiguration{core=%dx%d,threads=%d,crop=%s,"
                        + "sample=%s,segment=%s,calibration=%s}",
                Integer.valueOf(coreTileWidthPixels),
                Integer.valueOf(coreTileHeightPixels),
                Integer.valueOf(threadsPerTile),
                Boolean.valueOf(cropOutputToApprovedMaskExtent),
                sampleId,
                segmentId,
                calibrationMode.name());
    }
}
