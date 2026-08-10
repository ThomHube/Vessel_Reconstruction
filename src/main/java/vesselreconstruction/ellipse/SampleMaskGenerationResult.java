package vesselreconstruction.ellipse;

import ij.ImagePlus;

/**
 * Result of automatic sample-mask generation, including review images.
 */
public final class SampleMaskGenerationResult {

    private final DownsampledSampleMask mask;
    private final SampleMaskStatistics statistics;
    private final ImagePlus rgbPreview;
    private final ImagePlus editableMaskImage;

    public SampleMaskGenerationResult(
            final DownsampledSampleMask mask,
            final SampleMaskStatistics statistics,
            final ImagePlus rgbPreview,
            final ImagePlus editableMaskImage) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (statistics == null) {
            throw new IllegalArgumentException("statistics must not be null");
        }
        if (rgbPreview == null) {
            throw new IllegalArgumentException("rgbPreview must not be null");
        }
        if (editableMaskImage == null) {
            throw new IllegalArgumentException("editableMaskImage must not be null");
        }
        this.mask = mask;
        this.statistics = statistics;
        this.rgbPreview = rgbPreview;
        this.editableMaskImage = editableMaskImage;
    }

    public DownsampledSampleMask getMask() {
        return mask;
    }

    public SampleMaskStatistics getStatistics() {
        return statistics;
    }

    public ImagePlus getRgbPreview() {
        return rgbPreview;
    }

    public ImagePlus getEditableMaskImage() {
        return editableMaskImage;
    }
}
