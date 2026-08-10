package vesselreconstruction.ellipse;

import ij.ImagePlus;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable result of a single-image tiled Weka classification validation run.
 */
public final class TiledWekaClassificationResult {

    private final ImagePlus tiledDirectLabels;
    private final ImagePlus untiledDirectLabels;
    private final ClassificationSeamAudit seamAudit;
    private final String sourceTitle;
    private final String modelCanonicalPath;
    private final String[] modelClassLabels;
    private final TiledWekaClassificationConfiguration configuration;
    private final CoreTileLayout layout;
    private final DownsampledSampleMask approvedMask;
    private final SampleMaskStatistics automaticMaskStatistics;
    private final boolean sampleMaskReviewed;
    private final int classifiedTiles;
    private final int skippedTiles;
    private final long outsideMaskForcedBackgroundPixels;
    private final long tiledClassificationTimeMs;
    private final long untiledClassificationTimeMs;
    private final long[] tiledClassCounts;
    private final long[] untiledClassCounts;
    private final int sourceChecksumBefore;
    private final int sourceChecksumAfter;
    private final boolean sourceUnchanged;
    private final boolean tiledClassificationSuccessful;
    private final boolean untiledReferenceRequested;
    private final boolean untiledReferenceSuccessful;
    private final String untiledReferenceFailureReason;

    public TiledWekaClassificationResult(
            final ImagePlus tiledDirectLabels,
            final ImagePlus untiledDirectLabels,
            final ClassificationSeamAudit seamAudit,
            final String sourceTitle,
            final String modelCanonicalPath,
            final String[] modelClassLabels,
            final TiledWekaClassificationConfiguration configuration,
            final CoreTileLayout layout,
            final DownsampledSampleMask approvedMask,
            final SampleMaskStatistics automaticMaskStatistics,
            final boolean sampleMaskReviewed,
            final int classifiedTiles,
            final int skippedTiles,
            final long outsideMaskForcedBackgroundPixels,
            final long tiledClassificationTimeMs,
            final long untiledClassificationTimeMs,
            final long[] tiledClassCounts,
            final long[] untiledClassCounts,
            final int sourceChecksumBefore,
            final int sourceChecksumAfter,
            final boolean tiledClassificationSuccessful,
            final boolean untiledReferenceRequested,
            final boolean untiledReferenceSuccessful,
            final String untiledReferenceFailureReason) {
        if (!tiledClassificationSuccessful) {
            throw new IllegalArgumentException(
                    "tiledClassificationSuccessful must be true for a successful result");
        }
        if (tiledDirectLabels == null) {
            throw new IllegalArgumentException("tiledDirectLabels must not be null");
        }
        if (sourceTitle == null) {
            throw new IllegalArgumentException("sourceTitle must not be null");
        }
        if (modelCanonicalPath == null || modelCanonicalPath.isEmpty()) {
            throw new IllegalArgumentException("modelCanonicalPath must not be null or empty");
        }
        if (modelClassLabels == null || modelClassLabels.length != 4) {
            throw new IllegalArgumentException("modelClassLabels must have length 4");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        if (approvedMask == null) {
            throw new IllegalArgumentException("approvedMask must not be null");
        }
        if (tiledClassCounts == null || tiledClassCounts.length != 4) {
            throw new IllegalArgumentException("tiledClassCounts must have length 4");
        }
        if (untiledReferenceSuccessful) {
            if (untiledDirectLabels == null) {
                throw new IllegalArgumentException(
                        "untiledDirectLabels required when untiled reference succeeded");
            }
            if (seamAudit == null) {
                throw new IllegalArgumentException(
                        "seamAudit required when untiled reference succeeded");
            }
            if (untiledClassCounts == null || untiledClassCounts.length != 4) {
                throw new IllegalArgumentException(
                        "untiledClassCounts required when untiled reference succeeded");
            }
        } else if (seamAudit != null) {
            throw new IllegalArgumentException(
                    "seamAudit must be null when untiled reference did not succeed");
        }

        this.tiledDirectLabels = duplicateImage(tiledDirectLabels);
        this.untiledDirectLabels = untiledDirectLabels == null
                ? null : duplicateImage(untiledDirectLabels);
        this.seamAudit = seamAudit;
        this.sourceTitle = sourceTitle;
        this.modelCanonicalPath = modelCanonicalPath;
        this.modelClassLabels = Arrays.copyOf(modelClassLabels, 4);
        this.configuration = configuration;
        this.layout = layout;
        this.approvedMask = approvedMask;
        this.automaticMaskStatistics = automaticMaskStatistics;
        this.sampleMaskReviewed = sampleMaskReviewed;
        this.classifiedTiles = classifiedTiles;
        this.skippedTiles = skippedTiles;
        this.outsideMaskForcedBackgroundPixels = outsideMaskForcedBackgroundPixels;
        this.tiledClassificationTimeMs = tiledClassificationTimeMs;
        this.untiledClassificationTimeMs = untiledClassificationTimeMs;
        this.tiledClassCounts = Arrays.copyOf(tiledClassCounts, 4);
        this.untiledClassCounts = untiledClassCounts == null
                ? null : Arrays.copyOf(untiledClassCounts, 4);
        this.sourceChecksumBefore = sourceChecksumBefore;
        this.sourceChecksumAfter = sourceChecksumAfter;
        this.sourceUnchanged = sourceChecksumBefore == sourceChecksumAfter;
        this.tiledClassificationSuccessful = true;
        this.untiledReferenceRequested = untiledReferenceRequested;
        this.untiledReferenceSuccessful = untiledReferenceSuccessful;
        this.untiledReferenceFailureReason = untiledReferenceFailureReason == null
                ? "" : untiledReferenceFailureReason;
    }

    public ImagePlus getTiledDirectLabels() {
        return duplicateImage(tiledDirectLabels);
    }

    public ImagePlus getUntiledDirectLabels() {
        return untiledDirectLabels == null ? null : duplicateImage(untiledDirectLabels);
    }

    public ClassificationSeamAudit getSeamAudit() {
        return seamAudit;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getModelCanonicalPath() {
        return modelCanonicalPath;
    }

    public String[] getModelClassLabels() {
        return Arrays.copyOf(modelClassLabels, 4);
    }

    public TiledWekaClassificationConfiguration getConfiguration() {
        return configuration;
    }

    public CoreTileLayout getLayout() {
        return layout;
    }

    public DownsampledSampleMask getApprovedMask() {
        return approvedMask;
    }

    public SampleMaskStatistics getAutomaticMaskStatistics() {
        return automaticMaskStatistics;
    }

    public boolean isSampleMaskReviewed() {
        return sampleMaskReviewed;
    }

    public int getClassifiedTiles() {
        return classifiedTiles;
    }

    public int getSkippedTiles() {
        return skippedTiles;
    }

    public long getOutsideMaskForcedBackgroundPixels() {
        return outsideMaskForcedBackgroundPixels;
    }

    public long getTiledClassificationTimeMs() {
        return tiledClassificationTimeMs;
    }

    public long getUntiledClassificationTimeMs() {
        return untiledClassificationTimeMs;
    }

    public long[] getTiledClassCounts() {
        return Arrays.copyOf(tiledClassCounts, 4);
    }

    public long[] getUntiledClassCounts() {
        return untiledClassCounts == null ? null : Arrays.copyOf(untiledClassCounts, 4);
    }

    public int getSourceChecksumBefore() {
        return sourceChecksumBefore;
    }

    public int getSourceChecksumAfter() {
        return sourceChecksumAfter;
    }

    public boolean isSourceUnchanged() {
        return sourceUnchanged;
    }

    public boolean isTiledClassificationSuccessful() {
        return tiledClassificationSuccessful;
    }

    public boolean isUntiledReferenceRequested() {
        return untiledReferenceRequested;
    }

    public boolean isUntiledReferenceSuccessful() {
        return untiledReferenceSuccessful;
    }

    public String getUntiledReferenceFailureReason() {
        return untiledReferenceFailureReason;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TiledWekaClassificationResult)) {
            return false;
        }
        final TiledWekaClassificationResult other = (TiledWekaClassificationResult) obj;
        return sourceTitle.equals(other.sourceTitle)
                && modelCanonicalPath.equals(other.modelCanonicalPath)
                && Arrays.equals(modelClassLabels, other.modelClassLabels)
                && configuration.equals(other.configuration)
                && layout.equals(other.layout)
                && approvedMask.equals(other.approvedMask)
                && Objects.equals(automaticMaskStatistics, other.automaticMaskStatistics)
                && sampleMaskReviewed == other.sampleMaskReviewed
                && classifiedTiles == other.classifiedTiles
                && skippedTiles == other.skippedTiles
                && outsideMaskForcedBackgroundPixels == other.outsideMaskForcedBackgroundPixels
                && tiledClassificationTimeMs == other.tiledClassificationTimeMs
                && untiledClassificationTimeMs == other.untiledClassificationTimeMs
                && Arrays.equals(tiledClassCounts, other.tiledClassCounts)
                && Arrays.equals(untiledClassCounts, other.untiledClassCounts)
                && sourceChecksumBefore == other.sourceChecksumBefore
                && sourceChecksumAfter == other.sourceChecksumAfter
                && sourceUnchanged == other.sourceUnchanged
                && tiledClassificationSuccessful == other.tiledClassificationSuccessful
                && untiledReferenceRequested == other.untiledReferenceRequested
                && untiledReferenceSuccessful == other.untiledReferenceSuccessful
                && untiledReferenceFailureReason.equals(other.untiledReferenceFailureReason)
                && Objects.equals(seamAudit, other.seamAudit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceTitle,
                modelCanonicalPath,
                configuration,
                layout,
                approvedMask,
                automaticMaskStatistics,
                Boolean.valueOf(sampleMaskReviewed),
                Integer.valueOf(classifiedTiles),
                Integer.valueOf(skippedTiles),
                Long.valueOf(outsideMaskForcedBackgroundPixels),
                Long.valueOf(tiledClassificationTimeMs),
                Long.valueOf(untiledClassificationTimeMs),
                Integer.valueOf(sourceChecksumBefore),
                Integer.valueOf(sourceChecksumAfter),
                Boolean.valueOf(sourceUnchanged),
                Boolean.valueOf(tiledClassificationSuccessful),
                Boolean.valueOf(untiledReferenceRequested),
                Boolean.valueOf(untiledReferenceSuccessful),
                untiledReferenceFailureReason,
                seamAudit,
                Integer.valueOf(Arrays.hashCode(modelClassLabels)),
                Integer.valueOf(Arrays.hashCode(tiledClassCounts)),
                Integer.valueOf(Arrays.hashCode(untiledClassCounts)));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "TiledWekaClassificationResult{source=%s,core=%dx%d,grid=%dx%d,"
                        + "classified=%d,skipped=%d,untiled=%s}",
                sourceTitle,
                Integer.valueOf(configuration.getCoreTileWidthPixels()),
                Integer.valueOf(configuration.getCoreTileHeightPixels()),
                Integer.valueOf(layout.getTilesX()),
                Integer.valueOf(layout.getTilesY()),
                Integer.valueOf(classifiedTiles),
                Integer.valueOf(skippedTiles),
                Boolean.valueOf(untiledReferenceSuccessful));
    }

    private static ImagePlus duplicateImage(final ImagePlus source) {
        final ImagePlus copy = new ImagePlus(
                source.getTitle(),
                source.getProcessor().duplicate());
        if (source.getCalibration() != null) {
            copy.setCalibration(source.getCalibration().copy());
        }
        if (source.getProcessor().getLut() != null) {
            copy.getProcessor().setLut(source.getProcessor().getLut());
        }
        return copy;
    }
}
