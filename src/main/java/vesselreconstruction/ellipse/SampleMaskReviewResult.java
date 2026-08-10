package vesselreconstruction.ellipse;

import ij.ImagePlus;

/**
 * Approved sample mask after optional interactive review.
 */
public final class SampleMaskReviewResult {

    private final DownsampledSampleMask approvedMask;
    private final boolean reviewed;
    private final ImagePlus editableMaskImage;
    private final ImagePlus rgbPreview;

    public SampleMaskReviewResult(
            final DownsampledSampleMask approvedMask,
            final boolean reviewed,
            final ImagePlus editableMaskImage,
            final ImagePlus rgbPreview) {
        if (approvedMask == null) {
            throw new IllegalArgumentException("approvedMask must not be null");
        }
        this.approvedMask = approvedMask;
        this.reviewed = reviewed;
        this.editableMaskImage = editableMaskImage;
        this.rgbPreview = rgbPreview;
    }

    public DownsampledSampleMask getApprovedMask() {
        return approvedMask;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public ImagePlus getEditableMaskImage() {
        return editableMaskImage;
    }

    public ImagePlus getRgbPreview() {
        return rgbPreview;
    }
}
