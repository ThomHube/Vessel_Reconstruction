package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for a source-safe fill preview of the frozen
 * single-interval refitted ellipse.
 */
public final class RefittedEllipseFillPreviewResult {

    private final int imageWidth;
    private final int imageHeight;
    private final EllipseModel baselineEllipse;
    private final EllipseModel refittedEllipse;
    private final List<Point> selectedObservedLumenPixels;
    private final List<Point> baselineProposedFillPixels;
    private final List<Point> refittedProposedFillPixels;
    private final List<Point> sharedProposedFillPixels;
    private final List<Point> refitOnlyProposedFillPixels;
    private final List<Point> baselineOnlyProposedFillPixels;
    private final List<Point> refittedWoodFillPixels;
    private final List<Point> refittedRayFillPixels;
    private final List<Point> refittedBackgroundFillPixels;
    private final List<Point> refittedOtherClassFillPixels;
    private final List<Point> selectedLumenPixelsOutsideRefittedEllipse;
    private final List<Point> otherLumenPixelsInsideRefittedEllipse;
    private final List<Point> previewSelectedLumenPixels;
    private final int selectedObservedLumenArea;
    private final int baselinePixelCentersInsideEllipse;
    private final int refittedPixelCentersInsideEllipse;
    private final int baselineProposedFillCount;
    private final int refittedProposedFillCount;
    private final int sharedProposedFillCount;
    private final int refitOnlyProposedFillCount;
    private final int baselineOnlyProposedFillCount;
    private final int refittedWoodFillCount;
    private final int refittedRayFillCount;
    private final int refittedBackgroundFillCount;
    private final int refittedOtherClassFillCount;
    private final int selectedLumenOutsideRefittedEllipseCount;
    private final int otherLumenInsideRefittedEllipseCount;
    private final int originalArea;
    private final int originalPerimeter;
    private final double originalCircularity;
    private final int previewArea;
    private final int previewPerimeter;
    private final double previewCircularity;
    private final double circularityChange;
    private final double addedAreaFraction;
    private final double ellipseAreaToObservedLumenAreaRatio;
    private final boolean wouldOverlapAnotherLumenComponent;

    public RefittedEllipseFillPreviewResult(
            final int imageWidth,
            final int imageHeight,
            final EllipseModel baselineEllipse,
            final EllipseModel refittedEllipse,
            final List<Point> selectedObservedLumenPixels,
            final List<Point> baselineProposedFillPixels,
            final List<Point> refittedProposedFillPixels,
            final List<Point> sharedProposedFillPixels,
            final List<Point> refitOnlyProposedFillPixels,
            final List<Point> baselineOnlyProposedFillPixels,
            final List<Point> refittedWoodFillPixels,
            final List<Point> refittedRayFillPixels,
            final List<Point> refittedBackgroundFillPixels,
            final List<Point> refittedOtherClassFillPixels,
            final List<Point> selectedLumenPixelsOutsideRefittedEllipse,
            final List<Point> otherLumenPixelsInsideRefittedEllipse,
            final List<Point> previewSelectedLumenPixels,
            final int selectedObservedLumenArea,
            final int baselinePixelCentersInsideEllipse,
            final int refittedPixelCentersInsideEllipse,
            final int baselineProposedFillCount,
            final int refittedProposedFillCount,
            final int sharedProposedFillCount,
            final int refitOnlyProposedFillCount,
            final int baselineOnlyProposedFillCount,
            final int refittedWoodFillCount,
            final int refittedRayFillCount,
            final int refittedBackgroundFillCount,
            final int refittedOtherClassFillCount,
            final int selectedLumenOutsideRefittedEllipseCount,
            final int otherLumenInsideRefittedEllipseCount,
            final int originalArea,
            final int originalPerimeter,
            final double originalCircularity,
            final int previewArea,
            final int previewPerimeter,
            final double previewCircularity,
            final double circularityChange,
            final double addedAreaFraction,
            final double ellipseAreaToObservedLumenAreaRatio,
            final boolean wouldOverlapAnotherLumenComponent) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (baselineEllipse == null || refittedEllipse == null) {
            throw new IllegalArgumentException("Ellipses must not be null");
        }
        if (selectedObservedLumenPixels == null || selectedObservedLumenPixels.isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedObservedLumenPixels must not be null or empty");
        }

        this.selectedObservedLumenPixels = copyPoints(selectedObservedLumenPixels);
        this.baselineProposedFillPixels = copyPoints(baselineProposedFillPixels);
        this.refittedProposedFillPixels = copyPoints(refittedProposedFillPixels);
        this.sharedProposedFillPixels = copyPoints(sharedProposedFillPixels);
        this.refitOnlyProposedFillPixels = copyPoints(refitOnlyProposedFillPixels);
        this.baselineOnlyProposedFillPixels = copyPoints(baselineOnlyProposedFillPixels);
        this.refittedWoodFillPixels = copyPoints(refittedWoodFillPixels);
        this.refittedRayFillPixels = copyPoints(refittedRayFillPixels);
        this.refittedBackgroundFillPixels = copyPoints(refittedBackgroundFillPixels);
        this.refittedOtherClassFillPixels = copyPoints(refittedOtherClassFillPixels);
        this.selectedLumenPixelsOutsideRefittedEllipse =
                copyPoints(selectedLumenPixelsOutsideRefittedEllipse);
        this.otherLumenPixelsInsideRefittedEllipse =
                copyPoints(otherLumenPixelsInsideRefittedEllipse);
        this.previewSelectedLumenPixels = copyPoints(previewSelectedLumenPixels);

        requireNonNegative("selectedObservedLumenArea", selectedObservedLumenArea);
        requireNonNegative("baselinePixelCentersInsideEllipse", baselinePixelCentersInsideEllipse);
        requireNonNegative("refittedPixelCentersInsideEllipse", refittedPixelCentersInsideEllipse);
        requireNonNegative("baselineProposedFillCount", baselineProposedFillCount);
        requireNonNegative("refittedProposedFillCount", refittedProposedFillCount);
        requireNonNegative("sharedProposedFillCount", sharedProposedFillCount);
        requireNonNegative("refitOnlyProposedFillCount", refitOnlyProposedFillCount);
        requireNonNegative("baselineOnlyProposedFillCount", baselineOnlyProposedFillCount);
        requireNonNegative("refittedWoodFillCount", refittedWoodFillCount);
        requireNonNegative("refittedRayFillCount", refittedRayFillCount);
        requireNonNegative("refittedBackgroundFillCount", refittedBackgroundFillCount);
        requireNonNegative("refittedOtherClassFillCount", refittedOtherClassFillCount);
        requireNonNegative(
                "selectedLumenOutsideRefittedEllipseCount",
                selectedLumenOutsideRefittedEllipseCount);
        requireNonNegative(
                "otherLumenInsideRefittedEllipseCount", otherLumenInsideRefittedEllipseCount);
        requireNonNegative("originalArea", originalArea);
        requireNonNegative("previewArea", previewArea);

        if (originalPerimeter <= 0 || previewPerimeter <= 0) {
            throw new IllegalArgumentException(
                    "Areas and perimeters must be positive: originalPerimeter="
                            + originalPerimeter + ", previewPerimeter=" + previewPerimeter);
        }
        if (originalArea <= 0 || previewArea <= 0) {
            throw new IllegalArgumentException(
                    "Areas must be positive: originalArea=" + originalArea
                            + ", previewArea=" + previewArea);
        }

        requireCount("selectedObservedLumenArea", selectedObservedLumenArea,
                this.selectedObservedLumenPixels.size());
        requireCount("baselineProposedFillCount", baselineProposedFillCount,
                this.baselineProposedFillPixels.size());
        requireCount("refittedProposedFillCount", refittedProposedFillCount,
                this.refittedProposedFillPixels.size());
        requireCount("sharedProposedFillCount", sharedProposedFillCount,
                this.sharedProposedFillPixels.size());
        requireCount("refitOnlyProposedFillCount", refitOnlyProposedFillCount,
                this.refitOnlyProposedFillPixels.size());
        requireCount("baselineOnlyProposedFillCount", baselineOnlyProposedFillCount,
                this.baselineOnlyProposedFillPixels.size());
        requireCount("refittedWoodFillCount", refittedWoodFillCount,
                this.refittedWoodFillPixels.size());
        requireCount("refittedRayFillCount", refittedRayFillCount,
                this.refittedRayFillPixels.size());
        requireCount("refittedBackgroundFillCount", refittedBackgroundFillCount,
                this.refittedBackgroundFillPixels.size());
        requireCount("refittedOtherClassFillCount", refittedOtherClassFillCount,
                this.refittedOtherClassFillPixels.size());
        requireCount(
                "selectedLumenOutsideRefittedEllipseCount",
                selectedLumenOutsideRefittedEllipseCount,
                this.selectedLumenPixelsOutsideRefittedEllipse.size());
        requireCount(
                "otherLumenInsideRefittedEllipseCount",
                otherLumenInsideRefittedEllipseCount,
                this.otherLumenPixelsInsideRefittedEllipse.size());
        requireCount("previewArea", previewArea, this.previewSelectedLumenPixels.size());

        if (originalArea != selectedObservedLumenArea) {
            throw new IllegalArgumentException(
                    "originalArea must equal selectedObservedLumenArea");
        }
        if (previewArea != originalArea + refittedProposedFillCount) {
            throw new IllegalArgumentException(
                    "previewArea must equal originalArea + refittedProposedFillCount");
        }
        if (refittedWoodFillCount + refittedRayFillCount + refittedBackgroundFillCount
                + refittedOtherClassFillCount != refittedProposedFillCount) {
            throw new IllegalArgumentException(
                    "Refitted fill class counts must sum to refittedProposedFillCount");
        }
        if (sharedProposedFillCount + refitOnlyProposedFillCount != refittedProposedFillCount) {
            throw new IllegalArgumentException(
                    "shared + refit-only must equal refitted proposed fill count");
        }
        if (sharedProposedFillCount + baselineOnlyProposedFillCount != baselineProposedFillCount) {
            throw new IllegalArgumentException(
                    "shared + baseline-only must equal baseline proposed fill count");
        }
        if (wouldOverlapAnotherLumenComponent != (otherLumenInsideRefittedEllipseCount > 0)) {
            throw new IllegalArgumentException(
                    "wouldOverlapAnotherLumenComponent must equal "
                            + "otherLumenInsideRefittedEllipseCount > 0");
        }

        requireFinite("originalCircularity", originalCircularity);
        requireFinite("previewCircularity", previewCircularity);
        requireFinite("circularityChange", circularityChange);
        requireFinite("addedAreaFraction", addedAreaFraction);
        requireFinite("ellipseAreaToObservedLumenAreaRatio", ellipseAreaToObservedLumenAreaRatio);
        if (addedAreaFraction < 0.0) {
            throw new IllegalArgumentException(
                    "addedAreaFraction must be non-negative: " + addedAreaFraction);
        }
        if (!(ellipseAreaToObservedLumenAreaRatio > 0.0)) {
            throw new IllegalArgumentException(
                    "ellipseAreaToObservedLumenAreaRatio must be positive: "
                            + ellipseAreaToObservedLumenAreaRatio);
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.baselineEllipse = baselineEllipse;
        this.refittedEllipse = refittedEllipse;
        this.selectedObservedLumenArea = selectedObservedLumenArea;
        this.baselinePixelCentersInsideEllipse = baselinePixelCentersInsideEllipse;
        this.refittedPixelCentersInsideEllipse = refittedPixelCentersInsideEllipse;
        this.baselineProposedFillCount = baselineProposedFillCount;
        this.refittedProposedFillCount = refittedProposedFillCount;
        this.sharedProposedFillCount = sharedProposedFillCount;
        this.refitOnlyProposedFillCount = refitOnlyProposedFillCount;
        this.baselineOnlyProposedFillCount = baselineOnlyProposedFillCount;
        this.refittedWoodFillCount = refittedWoodFillCount;
        this.refittedRayFillCount = refittedRayFillCount;
        this.refittedBackgroundFillCount = refittedBackgroundFillCount;
        this.refittedOtherClassFillCount = refittedOtherClassFillCount;
        this.selectedLumenOutsideRefittedEllipseCount = selectedLumenOutsideRefittedEllipseCount;
        this.otherLumenInsideRefittedEllipseCount = otherLumenInsideRefittedEllipseCount;
        this.originalArea = originalArea;
        this.originalPerimeter = originalPerimeter;
        this.originalCircularity = originalCircularity;
        this.previewArea = previewArea;
        this.previewPerimeter = previewPerimeter;
        this.previewCircularity = previewCircularity;
        this.circularityChange = circularityChange;
        this.addedAreaFraction = addedAreaFraction;
        this.ellipseAreaToObservedLumenAreaRatio = ellipseAreaToObservedLumenAreaRatio;
        this.wouldOverlapAnotherLumenComponent = wouldOverlapAnotherLumenComponent;
    }

    private static List<Point> copyPoints(final List<Point> source) {
        if (source == null) {
            throw new IllegalArgumentException("Point list must not be null");
        }
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            if (point == null) {
                throw new IllegalArgumentException("Point list must not contain null entries");
            }
            copy.add(new Point(point.x, point.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Point> defensivePoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            copy.add(new Point(point.x, point.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static void requireCount(final String name, final int count, final int size) {
        if (count != size) {
            throw new IllegalArgumentException(
                    name + " must equal corresponding list size: " + count + " vs " + size);
        }
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public EllipseModel getBaselineEllipse() {
        return baselineEllipse;
    }

    public EllipseModel getRefittedEllipse() {
        return refittedEllipse;
    }

    public List<Point> getSelectedObservedLumenPixels() {
        return defensivePoints(selectedObservedLumenPixels);
    }

    public List<Point> getBaselineProposedFillPixels() {
        return defensivePoints(baselineProposedFillPixels);
    }

    public List<Point> getRefittedProposedFillPixels() {
        return defensivePoints(refittedProposedFillPixels);
    }

    public List<Point> getSharedProposedFillPixels() {
        return defensivePoints(sharedProposedFillPixels);
    }

    public List<Point> getRefitOnlyProposedFillPixels() {
        return defensivePoints(refitOnlyProposedFillPixels);
    }

    public List<Point> getBaselineOnlyProposedFillPixels() {
        return defensivePoints(baselineOnlyProposedFillPixels);
    }

    public List<Point> getRefittedWoodFillPixels() {
        return defensivePoints(refittedWoodFillPixels);
    }

    public List<Point> getRefittedRayFillPixels() {
        return defensivePoints(refittedRayFillPixels);
    }

    public List<Point> getRefittedBackgroundFillPixels() {
        return defensivePoints(refittedBackgroundFillPixels);
    }

    public List<Point> getRefittedOtherClassFillPixels() {
        return defensivePoints(refittedOtherClassFillPixels);
    }

    public List<Point> getSelectedLumenPixelsOutsideRefittedEllipse() {
        return defensivePoints(selectedLumenPixelsOutsideRefittedEllipse);
    }

    public List<Point> getOtherLumenPixelsInsideRefittedEllipse() {
        return defensivePoints(otherLumenPixelsInsideRefittedEllipse);
    }

    public List<Point> getPreviewSelectedLumenPixels() {
        return defensivePoints(previewSelectedLumenPixels);
    }

    public int getSelectedObservedLumenArea() {
        return selectedObservedLumenArea;
    }

    public int getBaselinePixelCentersInsideEllipse() {
        return baselinePixelCentersInsideEllipse;
    }

    public int getRefittedPixelCentersInsideEllipse() {
        return refittedPixelCentersInsideEllipse;
    }

    public int getBaselineProposedFillCount() {
        return baselineProposedFillCount;
    }

    public int getRefittedProposedFillCount() {
        return refittedProposedFillCount;
    }

    public int getSharedProposedFillCount() {
        return sharedProposedFillCount;
    }

    public int getRefitOnlyProposedFillCount() {
        return refitOnlyProposedFillCount;
    }

    public int getBaselineOnlyProposedFillCount() {
        return baselineOnlyProposedFillCount;
    }

    public int getRefittedWoodFillCount() {
        return refittedWoodFillCount;
    }

    public int getRefittedRayFillCount() {
        return refittedRayFillCount;
    }

    public int getRefittedBackgroundFillCount() {
        return refittedBackgroundFillCount;
    }

    public int getRefittedOtherClassFillCount() {
        return refittedOtherClassFillCount;
    }

    public int getSelectedLumenOutsideRefittedEllipseCount() {
        return selectedLumenOutsideRefittedEllipseCount;
    }

    public int getOtherLumenInsideRefittedEllipseCount() {
        return otherLumenInsideRefittedEllipseCount;
    }

    public int getOriginalArea() {
        return originalArea;
    }

    public int getOriginalPerimeter() {
        return originalPerimeter;
    }

    public double getOriginalCircularity() {
        return originalCircularity;
    }

    public int getPreviewArea() {
        return previewArea;
    }

    public int getPreviewPerimeter() {
        return previewPerimeter;
    }

    public double getPreviewCircularity() {
        return previewCircularity;
    }

    public double getCircularityChange() {
        return circularityChange;
    }

    public double getAddedAreaFraction() {
        return addedAreaFraction;
    }

    public double getEllipseAreaToObservedLumenAreaRatio() {
        return ellipseAreaToObservedLumenAreaRatio;
    }

    public boolean isWouldOverlapAnotherLumenComponent() {
        return wouldOverlapAnotherLumenComponent;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RefittedEllipseFillPreviewResult)) {
            return false;
        }
        final RefittedEllipseFillPreviewResult other = (RefittedEllipseFillPreviewResult) obj;
        return imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && selectedObservedLumenArea == other.selectedObservedLumenArea
                && baselinePixelCentersInsideEllipse == other.baselinePixelCentersInsideEllipse
                && refittedPixelCentersInsideEllipse == other.refittedPixelCentersInsideEllipse
                && baselineProposedFillCount == other.baselineProposedFillCount
                && refittedProposedFillCount == other.refittedProposedFillCount
                && sharedProposedFillCount == other.sharedProposedFillCount
                && refitOnlyProposedFillCount == other.refitOnlyProposedFillCount
                && baselineOnlyProposedFillCount == other.baselineOnlyProposedFillCount
                && refittedWoodFillCount == other.refittedWoodFillCount
                && refittedRayFillCount == other.refittedRayFillCount
                && refittedBackgroundFillCount == other.refittedBackgroundFillCount
                && refittedOtherClassFillCount == other.refittedOtherClassFillCount
                && selectedLumenOutsideRefittedEllipseCount
                == other.selectedLumenOutsideRefittedEllipseCount
                && otherLumenInsideRefittedEllipseCount == other.otherLumenInsideRefittedEllipseCount
                && originalArea == other.originalArea
                && originalPerimeter == other.originalPerimeter
                && previewArea == other.previewArea
                && previewPerimeter == other.previewPerimeter
                && wouldOverlapAnotherLumenComponent == other.wouldOverlapAnotherLumenComponent
                && Objects.equals(baselineEllipse, other.baselineEllipse)
                && Objects.equals(refittedEllipse, other.refittedEllipse)
                && Objects.equals(selectedObservedLumenPixels, other.selectedObservedLumenPixels)
                && Objects.equals(baselineProposedFillPixels, other.baselineProposedFillPixels)
                && Objects.equals(refittedProposedFillPixels, other.refittedProposedFillPixels)
                && Objects.equals(sharedProposedFillPixels, other.sharedProposedFillPixels)
                && Objects.equals(refitOnlyProposedFillPixels, other.refitOnlyProposedFillPixels)
                && Objects.equals(baselineOnlyProposedFillPixels, other.baselineOnlyProposedFillPixels)
                && Objects.equals(refittedWoodFillPixels, other.refittedWoodFillPixels)
                && Objects.equals(refittedRayFillPixels, other.refittedRayFillPixels)
                && Objects.equals(refittedBackgroundFillPixels, other.refittedBackgroundFillPixels)
                && Objects.equals(refittedOtherClassFillPixels, other.refittedOtherClassFillPixels)
                && Objects.equals(
                        selectedLumenPixelsOutsideRefittedEllipse,
                        other.selectedLumenPixelsOutsideRefittedEllipse)
                && Objects.equals(
                        otherLumenPixelsInsideRefittedEllipse,
                        other.otherLumenPixelsInsideRefittedEllipse)
                && Objects.equals(previewSelectedLumenPixels, other.previewSelectedLumenPixels)
                && doubleEquals(originalCircularity, other.originalCircularity)
                && doubleEquals(previewCircularity, other.previewCircularity)
                && doubleEquals(circularityChange, other.circularityChange)
                && doubleEquals(addedAreaFraction, other.addedAreaFraction)
                && doubleEquals(
                        ellipseAreaToObservedLumenAreaRatio,
                        other.ellipseAreaToObservedLumenAreaRatio);
    }

    private static boolean doubleEquals(final double a, final double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                baselineEllipse,
                refittedEllipse,
                selectedObservedLumenPixels,
                baselineProposedFillPixels,
                refittedProposedFillPixels,
                sharedProposedFillPixels,
                refitOnlyProposedFillPixels,
                baselineOnlyProposedFillPixels,
                refittedWoodFillPixels,
                refittedRayFillPixels,
                refittedBackgroundFillPixels,
                refittedOtherClassFillPixels,
                selectedLumenPixelsOutsideRefittedEllipse,
                otherLumenPixelsInsideRefittedEllipse,
                previewSelectedLumenPixels,
                Integer.valueOf(selectedObservedLumenArea),
                Integer.valueOf(baselinePixelCentersInsideEllipse),
                Integer.valueOf(refittedPixelCentersInsideEllipse),
                Integer.valueOf(baselineProposedFillCount),
                Integer.valueOf(refittedProposedFillCount),
                Integer.valueOf(sharedProposedFillCount),
                Integer.valueOf(refitOnlyProposedFillCount),
                Integer.valueOf(baselineOnlyProposedFillCount),
                Integer.valueOf(refittedWoodFillCount),
                Integer.valueOf(refittedRayFillCount),
                Integer.valueOf(refittedBackgroundFillCount),
                Integer.valueOf(refittedOtherClassFillCount),
                Integer.valueOf(selectedLumenOutsideRefittedEllipseCount),
                Integer.valueOf(otherLumenInsideRefittedEllipseCount),
                Integer.valueOf(originalArea),
                Integer.valueOf(originalPerimeter),
                Double.valueOf(originalCircularity),
                Integer.valueOf(previewArea),
                Integer.valueOf(previewPerimeter),
                Double.valueOf(previewCircularity),
                Double.valueOf(circularityChange),
                Double.valueOf(addedAreaFraction),
                Double.valueOf(ellipseAreaToObservedLumenAreaRatio),
                Boolean.valueOf(wouldOverlapAnotherLumenComponent));
    }

    @Override
    public String toString() {
        return "RefittedEllipseFillPreviewResult{observedArea=" + selectedObservedLumenArea
                + ", refittedFill=" + refittedProposedFillCount
                + ", previewArea=" + previewArea
                + ", circularityChange=" + circularityChange
                + '}';
    }
}
