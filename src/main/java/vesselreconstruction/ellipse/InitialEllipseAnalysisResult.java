package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for the initial free-ellipse analysis of one classified image.
 */
public final class InitialEllipseAnalysisResult {

    private final int imageWidth;
    private final int imageHeight;
    private final int lumenComponentCount;
    private final int selectedComponentArea;
    private final Rectangle selectedComponentBounds;
    private final List<Point> selectedLumenPixels;
    private final List<Point2D.Double> boundarySupportPoints;
    private final int exposedEdgePerimeter;
    private final EllipseFitResult ellipseFitResult;
    private final List<Point> proposedFillPixels;
    private final int proposedFillPixelCount;
    private final int proposedWoodPixelCount;
    private final int proposedRayPixelCount;
    private final int proposedBackgroundPixelCount;
    private final int proposedOtherClassPixelCount;
    private final int otherLumenPixelsInsideEllipse;
    private final int totalPixelCentersInsideEllipse;

    public InitialEllipseAnalysisResult(
            final int imageWidth,
            final int imageHeight,
            final int lumenComponentCount,
            final int selectedComponentArea,
            final Rectangle selectedComponentBounds,
            final List<Point> selectedLumenPixels,
            final List<Point2D.Double> boundarySupportPoints,
            final int exposedEdgePerimeter,
            final EllipseFitResult ellipseFitResult,
            final List<Point> proposedFillPixels,
            final int proposedFillPixelCount,
            final int proposedWoodPixelCount,
            final int proposedRayPixelCount,
            final int proposedBackgroundPixelCount,
            final int proposedOtherClassPixelCount,
            final int otherLumenPixelsInsideEllipse,
            final int totalPixelCentersInsideEllipse) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (lumenComponentCount <= 0) {
            throw new IllegalArgumentException(
                    "lumenComponentCount must be positive: " + lumenComponentCount);
        }
        if (selectedComponentArea <= 0) {
            throw new IllegalArgumentException(
                    "selectedComponentArea must be positive: " + selectedComponentArea);
        }
        if (selectedComponentBounds == null) {
            throw new IllegalArgumentException("selectedComponentBounds must not be null");
        }
        if (ellipseFitResult == null) {
            throw new IllegalArgumentException("ellipseFitResult must not be null");
        }
        requireNonNegative("exposedEdgePerimeter", exposedEdgePerimeter);
        requireNonNegative("proposedFillPixelCount", proposedFillPixelCount);
        requireNonNegative("proposedWoodPixelCount", proposedWoodPixelCount);
        requireNonNegative("proposedRayPixelCount", proposedRayPixelCount);
        requireNonNegative("proposedBackgroundPixelCount", proposedBackgroundPixelCount);
        requireNonNegative("proposedOtherClassPixelCount", proposedOtherClassPixelCount);
        requireNonNegative("otherLumenPixelsInsideEllipse", otherLumenPixelsInsideEllipse);
        requireNonNegative("totalPixelCentersInsideEllipse", totalPixelCentersInsideEllipse);

        if (selectedLumenPixels == null) {
            throw new IllegalArgumentException("selectedLumenPixels must not be null");
        }
        if (boundarySupportPoints == null) {
            throw new IllegalArgumentException("boundarySupportPoints must not be null");
        }
        if (proposedFillPixels == null) {
            throw new IllegalArgumentException("proposedFillPixels must not be null");
        }
        if (selectedComponentArea != selectedLumenPixels.size()) {
            throw new IllegalArgumentException(
                    "selectedComponentArea must equal selectedLumenPixels.size(): "
                            + selectedComponentArea + " vs " + selectedLumenPixels.size());
        }
        if (exposedEdgePerimeter != boundarySupportPoints.size()) {
            throw new IllegalArgumentException(
                    "exposedEdgePerimeter must equal boundarySupportPoints.size(): "
                            + exposedEdgePerimeter + " vs " + boundarySupportPoints.size());
        }
        if (proposedFillPixelCount != proposedFillPixels.size()) {
            throw new IllegalArgumentException(
                    "proposedFillPixelCount must equal proposedFillPixels.size(): "
                            + proposedFillPixelCount + " vs " + proposedFillPixels.size());
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.lumenComponentCount = lumenComponentCount;
        this.selectedComponentArea = selectedComponentArea;
        this.selectedComponentBounds = new Rectangle(selectedComponentBounds);
        this.selectedLumenPixels = copyPoints(selectedLumenPixels);
        this.boundarySupportPoints = copySupportPoints(boundarySupportPoints);
        this.exposedEdgePerimeter = exposedEdgePerimeter;
        this.ellipseFitResult = ellipseFitResult;
        this.proposedFillPixels = copyPoints(proposedFillPixels);
        this.proposedFillPixelCount = proposedFillPixelCount;
        this.proposedWoodPixelCount = proposedWoodPixelCount;
        this.proposedRayPixelCount = proposedRayPixelCount;
        this.proposedBackgroundPixelCount = proposedBackgroundPixelCount;
        this.proposedOtherClassPixelCount = proposedOtherClassPixelCount;
        this.otherLumenPixelsInsideEllipse = otherLumenPixelsInsideEllipse;
        this.totalPixelCentersInsideEllipse = totalPixelCentersInsideEllipse;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getLumenComponentCount() {
        return lumenComponentCount;
    }

    public int getSelectedComponentArea() {
        return selectedComponentArea;
    }

    public Rectangle getSelectedComponentBounds() {
        return new Rectangle(selectedComponentBounds);
    }

    public List<Point> getSelectedLumenPixels() {
        return copyPoints(selectedLumenPixels);
    }

    public List<Point2D.Double> getBoundarySupportPoints() {
        return copySupportPoints(boundarySupportPoints);
    }

    public int getExposedEdgePerimeter() {
        return exposedEdgePerimeter;
    }

    public EllipseFitResult getEllipseFitResult() {
        return ellipseFitResult;
    }

    public List<Point> getProposedFillPixels() {
        return copyPoints(proposedFillPixels);
    }

    public int getProposedFillPixelCount() {
        return proposedFillPixelCount;
    }

    public int getProposedWoodPixelCount() {
        return proposedWoodPixelCount;
    }

    public int getProposedRayPixelCount() {
        return proposedRayPixelCount;
    }

    public int getProposedBackgroundPixelCount() {
        return proposedBackgroundPixelCount;
    }

    public int getProposedOtherClassPixelCount() {
        return proposedOtherClassPixelCount;
    }

    public int getOtherLumenPixelsInsideEllipse() {
        return otherLumenPixelsInsideEllipse;
    }

    public int getTotalPixelCentersInsideEllipse() {
        return totalPixelCentersInsideEllipse;
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static List<Point> copyPoints(final List<Point> points) {
        final List<Point> copy = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point p = points.get(i);
            if (p == null) {
                throw new IllegalArgumentException("Point list must not contain null entries");
            }
            copy.add(new Point(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Point2D.Double> copySupportPoints(final List<Point2D.Double> points) {
        final List<Point2D.Double> copy = new ArrayList<Point2D.Double>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point2D.Double p = points.get(i);
            if (p == null) {
                throw new IllegalArgumentException(
                        "Support-point list must not contain null entries");
            }
            if (!Double.isFinite(p.x) || !Double.isFinite(p.y)) {
                throw new IllegalArgumentException(
                        "Support-point coordinates must be finite: (" + p.x + ", " + p.y + ")");
            }
            copy.add(new Point2D.Double(p.x, p.y));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InitialEllipseAnalysisResult)) {
            return false;
        }
        final InitialEllipseAnalysisResult other = (InitialEllipseAnalysisResult) obj;
        return imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && lumenComponentCount == other.lumenComponentCount
                && selectedComponentArea == other.selectedComponentArea
                && exposedEdgePerimeter == other.exposedEdgePerimeter
                && proposedFillPixelCount == other.proposedFillPixelCount
                && proposedWoodPixelCount == other.proposedWoodPixelCount
                && proposedRayPixelCount == other.proposedRayPixelCount
                && proposedBackgroundPixelCount == other.proposedBackgroundPixelCount
                && proposedOtherClassPixelCount == other.proposedOtherClassPixelCount
                && otherLumenPixelsInsideEllipse == other.otherLumenPixelsInsideEllipse
                && totalPixelCentersInsideEllipse == other.totalPixelCentersInsideEllipse
                && Objects.equals(selectedComponentBounds, other.selectedComponentBounds)
                && Objects.equals(selectedLumenPixels, other.selectedLumenPixels)
                && Objects.equals(boundarySupportPoints, other.boundarySupportPoints)
                && Objects.equals(ellipseFitResult, other.ellipseFitResult)
                && Objects.equals(proposedFillPixels, other.proposedFillPixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                Integer.valueOf(lumenComponentCount),
                Integer.valueOf(selectedComponentArea),
                selectedComponentBounds,
                selectedLumenPixels,
                boundarySupportPoints,
                Integer.valueOf(exposedEdgePerimeter),
                ellipseFitResult,
                proposedFillPixels,
                Integer.valueOf(proposedFillPixelCount),
                Integer.valueOf(proposedWoodPixelCount),
                Integer.valueOf(proposedRayPixelCount),
                Integer.valueOf(proposedBackgroundPixelCount),
                Integer.valueOf(proposedOtherClassPixelCount),
                Integer.valueOf(otherLumenPixelsInsideEllipse),
                Integer.valueOf(totalPixelCentersInsideEllipse));
    }

    @Override
    public String toString() {
        return "InitialEllipseAnalysisResult{imageWidth=" + imageWidth
                + ", imageHeight=" + imageHeight
                + ", lumenComponentCount=" + lumenComponentCount
                + ", selectedComponentArea=" + selectedComponentArea
                + ", selectedComponentBounds=" + selectedComponentBounds
                + ", exposedEdgePerimeter=" + exposedEdgePerimeter
                + ", proposedFillPixelCount=" + proposedFillPixelCount
                + ", proposedWoodPixelCount=" + proposedWoodPixelCount
                + ", proposedRayPixelCount=" + proposedRayPixelCount
                + ", proposedBackgroundPixelCount=" + proposedBackgroundPixelCount
                + ", proposedOtherClassPixelCount=" + proposedOtherClassPixelCount
                + ", otherLumenPixelsInsideEllipse=" + otherLumenPixelsInsideEllipse
                + ", totalPixelCentersInsideEllipse=" + totalPixelCentersInsideEllipse
                + ", ellipseFitResult=" + ellipseFitResult
                + '}';
    }
}
