package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable 8-connected proposed-fill component with interval-association
 * diagnostics.
 */
public final class ProposedFillComponent {

    private final int componentId;
    private final List<Point> pixels;
    private final int area;
    private final Rectangle bounds;
    private final int selectedLumenContactPixelCount;
    private final int selectedLumenContactEdgeCount;
    private final int intervalExteriorSeedPixelCount;
    private final int intervalExteriorEdgeHitCount;
    private final boolean intervalAssociated;
    private final int woodPixelCount;
    private final int rayPixelCount;
    private final int backgroundPixelCount;
    private final int otherClassPixelCount;

    public ProposedFillComponent(
            final int componentId,
            final List<Point> pixels,
            final int area,
            final Rectangle bounds,
            final int selectedLumenContactPixelCount,
            final int selectedLumenContactEdgeCount,
            final int intervalExteriorSeedPixelCount,
            final int intervalExteriorEdgeHitCount,
            final boolean intervalAssociated,
            final int woodPixelCount,
            final int rayPixelCount,
            final int backgroundPixelCount,
            final int otherClassPixelCount) {
        if (componentId <= 0) {
            throw new IllegalArgumentException("componentId must be positive: " + componentId);
        }
        if (pixels == null || pixels.isEmpty()) {
            throw new IllegalArgumentException("pixels must not be null or empty");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (area != pixels.size()) {
            throw new IllegalArgumentException(
                    "area must equal pixels.size(): " + area + " vs " + pixels.size());
        }
        requireNonNegative("selectedLumenContactPixelCount", selectedLumenContactPixelCount);
        requireNonNegative("selectedLumenContactEdgeCount", selectedLumenContactEdgeCount);
        requireNonNegative("intervalExteriorSeedPixelCount", intervalExteriorSeedPixelCount);
        requireNonNegative("intervalExteriorEdgeHitCount", intervalExteriorEdgeHitCount);
        requireNonNegative("woodPixelCount", woodPixelCount);
        requireNonNegative("rayPixelCount", rayPixelCount);
        requireNonNegative("backgroundPixelCount", backgroundPixelCount);
        requireNonNegative("otherClassPixelCount", otherClassPixelCount);
        if (woodPixelCount + rayPixelCount + backgroundPixelCount + otherClassPixelCount
                != area) {
            throw new IllegalArgumentException("Class counts must sum to area");
        }
        if (intervalAssociated != (intervalExteriorSeedPixelCount > 0)) {
            throw new IllegalArgumentException(
                    "intervalAssociated must equal intervalExteriorSeedPixelCount > 0");
        }

        final List<Point> copy = new ArrayList<Point>(pixels.size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < pixels.size(); i++) {
            final Point point = pixels.get(i);
            if (point == null) {
                throw new IllegalArgumentException("pixels must not contain null");
            }
            if (i > 0) {
                final Point prev = pixels.get(i - 1);
                if (point.y < prev.y || (point.y == prev.y && point.x < prev.x)) {
                    throw new IllegalArgumentException(
                            "pixels must be sorted by Y ascending, then X ascending");
                }
            }
            copy.add(new Point(point.x, point.y));
            if (point.x < minX) {
                minX = point.x;
            }
            if (point.y < minY) {
                minY = point.y;
            }
            if (point.x > maxX) {
                maxX = point.x;
            }
            if (point.y > maxY) {
                maxY = point.y;
            }
        }
        if (bounds.x != minX || bounds.y != minY
                || bounds.width != (maxX - minX + 1)
                || bounds.height != (maxY - minY + 1)) {
            throw new IllegalArgumentException(
                    "bounds must exactly contain the component coordinates");
        }

        this.componentId = componentId;
        this.pixels = Collections.unmodifiableList(copy);
        this.area = area;
        this.bounds = new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height);
        this.selectedLumenContactPixelCount = selectedLumenContactPixelCount;
        this.selectedLumenContactEdgeCount = selectedLumenContactEdgeCount;
        this.intervalExteriorSeedPixelCount = intervalExteriorSeedPixelCount;
        this.intervalExteriorEdgeHitCount = intervalExteriorEdgeHitCount;
        this.intervalAssociated = intervalAssociated;
        this.woodPixelCount = woodPixelCount;
        this.rayPixelCount = rayPixelCount;
        this.backgroundPixelCount = backgroundPixelCount;
        this.otherClassPixelCount = otherClassPixelCount;
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    public int getComponentId() {
        return componentId;
    }

    public List<Point> getPixels() {
        final List<Point> copy = new ArrayList<Point>(pixels.size());
        for (int i = 0; i < pixels.size(); i++) {
            final Point point = pixels.get(i);
            copy.add(new Point(point.x, point.y));
        }
        return Collections.unmodifiableList(copy);
    }

    public int getArea() {
        return area;
    }

    public Rectangle getBounds() {
        return new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public int getSelectedLumenContactPixelCount() {
        return selectedLumenContactPixelCount;
    }

    public int getSelectedLumenContactEdgeCount() {
        return selectedLumenContactEdgeCount;
    }

    public int getIntervalExteriorSeedPixelCount() {
        return intervalExteriorSeedPixelCount;
    }

    public int getIntervalExteriorEdgeHitCount() {
        return intervalExteriorEdgeHitCount;
    }

    public boolean isIntervalAssociated() {
        return intervalAssociated;
    }

    public int getWoodPixelCount() {
        return woodPixelCount;
    }

    public int getRayPixelCount() {
        return rayPixelCount;
    }

    public int getBackgroundPixelCount() {
        return backgroundPixelCount;
    }

    public int getOtherClassPixelCount() {
        return otherClassPixelCount;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProposedFillComponent)) {
            return false;
        }
        final ProposedFillComponent other = (ProposedFillComponent) obj;
        return componentId == other.componentId
                && area == other.area
                && selectedLumenContactPixelCount == other.selectedLumenContactPixelCount
                && selectedLumenContactEdgeCount == other.selectedLumenContactEdgeCount
                && intervalExteriorSeedPixelCount == other.intervalExteriorSeedPixelCount
                && intervalExteriorEdgeHitCount == other.intervalExteriorEdgeHitCount
                && intervalAssociated == other.intervalAssociated
                && woodPixelCount == other.woodPixelCount
                && rayPixelCount == other.rayPixelCount
                && backgroundPixelCount == other.backgroundPixelCount
                && otherClassPixelCount == other.otherClassPixelCount
                && Objects.equals(pixels, other.pixels)
                && Objects.equals(bounds, other.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(componentId),
                pixels,
                Integer.valueOf(area),
                bounds,
                Integer.valueOf(selectedLumenContactPixelCount),
                Integer.valueOf(selectedLumenContactEdgeCount),
                Integer.valueOf(intervalExteriorSeedPixelCount),
                Integer.valueOf(intervalExteriorEdgeHitCount),
                Boolean.valueOf(intervalAssociated),
                Integer.valueOf(woodPixelCount),
                Integer.valueOf(rayPixelCount),
                Integer.valueOf(backgroundPixelCount),
                Integer.valueOf(otherClassPixelCount));
    }

    @Override
    public String toString() {
        return "ProposedFillComponent{id=" + componentId
                + ", area=" + area
                + ", associated=" + intervalAssociated
                + ", seedPixels=" + intervalExteriorSeedPixelCount
                + ", edgeHits=" + intervalExteriorEdgeHitCount
                + '}';
    }
}
