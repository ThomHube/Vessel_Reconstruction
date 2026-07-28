package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for 8-connected proposed-fill components and their
 * association with the selected inward-outlier interval.
 */
public final class ProposedFillComponentAnalysisResult {

    private final RefittedEllipseFillPreviewResult wholeEllipsePreview;
    private final InwardOutlierDetectionResult inwardOutlierDetection;

    private final List<Point> intervalExteriorSeedPixels;
    private final int intervalExteriorEdgeCount;
    private final int uniqueIntervalExteriorSeedPixelCount;
    private final int matchedIntervalExteriorSeedPixelCount;
    private final int unmatchedIntervalExteriorSeedPixelCount;

    private final List<ProposedFillComponent> allComponents;
    private final List<ProposedFillComponent> intervalAssociatedComponents;
    private final List<ProposedFillComponent> nonAssociatedComponents;

    private final ProposedFillComponent primaryIntervalAssociatedComponent;

    private final int totalComponentCount;
    private final int intervalAssociatedComponentCount;
    private final int nonAssociatedComponentCount;

    private final int totalProposedFillPixelCount;
    private final int intervalAssociatedFillPixelCount;
    private final int nonAssociatedFillPixelCount;

    private final double intervalAssociatedFillFraction;
    private final double primaryComponentFillFraction;
    private final double primaryWithinAssociatedFraction;

    private final List<Point> intervalAssociatedFillPixels;
    private final List<Point> nonAssociatedFillPixels;
    private final List<Point> intervalAssociatedPreviewLumenPixels;

    private final int intervalAssociatedPreviewArea;
    private final int intervalAssociatedPreviewPerimeter;
    private final double intervalAssociatedPreviewCircularity;
    private final double intervalAssociatedCircularityChange;

    private final double wholeEllipseCircularity;
    private final double wholeEllipseCircularityChange;

    private final boolean exactlyOneIntervalAssociatedComponent;
    private final boolean allProposedFillIsIntervalAssociated;

    public ProposedFillComponentAnalysisResult(
            final RefittedEllipseFillPreviewResult wholeEllipsePreview,
            final InwardOutlierDetectionResult inwardOutlierDetection,
            final List<Point> intervalExteriorSeedPixels,
            final int intervalExteriorEdgeCount,
            final int uniqueIntervalExteriorSeedPixelCount,
            final int matchedIntervalExteriorSeedPixelCount,
            final int unmatchedIntervalExteriorSeedPixelCount,
            final List<ProposedFillComponent> allComponents,
            final List<ProposedFillComponent> intervalAssociatedComponents,
            final List<ProposedFillComponent> nonAssociatedComponents,
            final ProposedFillComponent primaryIntervalAssociatedComponent,
            final int totalComponentCount,
            final int intervalAssociatedComponentCount,
            final int nonAssociatedComponentCount,
            final int totalProposedFillPixelCount,
            final int intervalAssociatedFillPixelCount,
            final int nonAssociatedFillPixelCount,
            final double intervalAssociatedFillFraction,
            final double primaryComponentFillFraction,
            final double primaryWithinAssociatedFraction,
            final List<Point> intervalAssociatedFillPixels,
            final List<Point> nonAssociatedFillPixels,
            final List<Point> intervalAssociatedPreviewLumenPixels,
            final int intervalAssociatedPreviewArea,
            final int intervalAssociatedPreviewPerimeter,
            final double intervalAssociatedPreviewCircularity,
            final double intervalAssociatedCircularityChange,
            final double wholeEllipseCircularity,
            final double wholeEllipseCircularityChange,
            final boolean exactlyOneIntervalAssociatedComponent,
            final boolean allProposedFillIsIntervalAssociated) {
        if (wholeEllipsePreview == null) {
            throw new IllegalArgumentException("wholeEllipsePreview must not be null");
        }
        if (inwardOutlierDetection == null) {
            throw new IllegalArgumentException("inwardOutlierDetection must not be null");
        }
        if (!inwardOutlierDetection.isIntervalSelected()
                || inwardOutlierDetection.getSelectedInterval() == null) {
            throw new IllegalArgumentException(
                    "inwardOutlierDetection must contain a selected interval");
        }
        if (allComponents == null) {
            throw new IllegalArgumentException("allComponents must not be null");
        }
        if (intervalAssociatedComponents == null) {
            throw new IllegalArgumentException(
                    "intervalAssociatedComponents must not be null");
        }
        if (nonAssociatedComponents == null) {
            throw new IllegalArgumentException("nonAssociatedComponents must not be null");
        }
        if (intervalExteriorSeedPixels == null) {
            throw new IllegalArgumentException(
                    "intervalExteriorSeedPixels must not be null");
        }
        if (intervalAssociatedFillPixels == null) {
            throw new IllegalArgumentException(
                    "intervalAssociatedFillPixels must not be null");
        }
        if (nonAssociatedFillPixels == null) {
            throw new IllegalArgumentException("nonAssociatedFillPixels must not be null");
        }
        if (intervalAssociatedPreviewLumenPixels == null) {
            throw new IllegalArgumentException(
                    "intervalAssociatedPreviewLumenPixels must not be null");
        }

        requireNonNegative("intervalExteriorEdgeCount", intervalExteriorEdgeCount);
        requireNonNegative(
                "uniqueIntervalExteriorSeedPixelCount", uniqueIntervalExteriorSeedPixelCount);
        requireNonNegative(
                "matchedIntervalExteriorSeedPixelCount", matchedIntervalExteriorSeedPixelCount);
        requireNonNegative(
                "unmatchedIntervalExteriorSeedPixelCount",
                unmatchedIntervalExteriorSeedPixelCount);
        requireNonNegative("totalComponentCount", totalComponentCount);
        requireNonNegative("intervalAssociatedComponentCount", intervalAssociatedComponentCount);
        requireNonNegative("nonAssociatedComponentCount", nonAssociatedComponentCount);
        requireNonNegative("totalProposedFillPixelCount", totalProposedFillPixelCount);
        requireNonNegative("intervalAssociatedFillPixelCount", intervalAssociatedFillPixelCount);
        requireNonNegative("nonAssociatedFillPixelCount", nonAssociatedFillPixelCount);
        requireNonNegative("intervalAssociatedPreviewArea", intervalAssociatedPreviewArea);
        requireNonNegative(
                "intervalAssociatedPreviewPerimeter", intervalAssociatedPreviewPerimeter);

        if (intervalExteriorEdgeCount
                != inwardOutlierDetection.getSelectedInterval().getEdgeCount()) {
            throw new IllegalArgumentException(
                    "intervalExteriorEdgeCount must equal selected interval edge count");
        }
        if (matchedIntervalExteriorSeedPixelCount + unmatchedIntervalExteriorSeedPixelCount
                != uniqueIntervalExteriorSeedPixelCount) {
            throw new IllegalArgumentException(
                    "matched plus unmatched seed pixels must equal unique in-bounds seeds");
        }
        if (uniqueIntervalExteriorSeedPixelCount != intervalExteriorSeedPixels.size()) {
            throw new IllegalArgumentException(
                    "uniqueIntervalExteriorSeedPixelCount must equal seed list size");
        }
        if (totalComponentCount != allComponents.size()) {
            throw new IllegalArgumentException(
                    "totalComponentCount must equal allComponents.size()");
        }
        if (intervalAssociatedComponentCount != intervalAssociatedComponents.size()) {
            throw new IllegalArgumentException(
                    "intervalAssociatedComponentCount must equal list size");
        }
        if (nonAssociatedComponentCount != nonAssociatedComponents.size()) {
            throw new IllegalArgumentException(
                    "nonAssociatedComponentCount must equal list size");
        }
        if (intervalAssociatedComponentCount + nonAssociatedComponentCount
                != totalComponentCount) {
            throw new IllegalArgumentException(
                    "associated plus non-associated components must equal all components");
        }
        if (intervalAssociatedFillPixelCount + nonAssociatedFillPixelCount
                != totalProposedFillPixelCount) {
            throw new IllegalArgumentException(
                    "associated plus non-associated fill must equal total fill");
        }
        if (totalProposedFillPixelCount
                != wholeEllipsePreview.getRefittedProposedFillCount()) {
            throw new IllegalArgumentException(
                    "totalProposedFillPixelCount must equal whole preview refitted fill count");
        }
        if (intervalAssociatedFillPixelCount != intervalAssociatedFillPixels.size()) {
            throw new IllegalArgumentException(
                    "intervalAssociatedFillPixelCount must equal list size");
        }
        if (nonAssociatedFillPixelCount != nonAssociatedFillPixels.size()) {
            throw new IllegalArgumentException(
                    "nonAssociatedFillPixelCount must equal list size");
        }
        if (intervalAssociatedPreviewArea
                != wholeEllipsePreview.getSelectedObservedLumenArea()
                + intervalAssociatedFillPixelCount) {
            throw new IllegalArgumentException(
                    "intervalAssociatedPreviewArea must equal observed lumen area"
                            + " + intervalAssociatedFillPixelCount");
        }
        if (intervalAssociatedPreviewArea != intervalAssociatedPreviewLumenPixels.size()) {
            throw new IllegalArgumentException(
                    "intervalAssociatedPreviewArea must equal preview lumen list size");
        }
        if (exactlyOneIntervalAssociatedComponent
                != (intervalAssociatedComponentCount == 1)) {
            throw new IllegalArgumentException(
                    "exactlyOneIntervalAssociatedComponent must equal"
                            + " intervalAssociatedComponentCount == 1");
        }
        if (allProposedFillIsIntervalAssociated != (nonAssociatedFillPixelCount == 0)) {
            throw new IllegalArgumentException(
                    "allProposedFillIsIntervalAssociated must equal"
                            + " nonAssociatedFillPixelCount == 0");
        }
        if (primaryIntervalAssociatedComponent == null) {
            if (intervalAssociatedComponentCount != 0) {
                throw new IllegalArgumentException(
                        "primaryIntervalAssociatedComponent may be null only when"
                                + " there is no interval-associated component");
            }
        } else {
            if (intervalAssociatedComponents.isEmpty()
                    || !primaryIntervalAssociatedComponent.equals(
                            intervalAssociatedComponents.get(0))) {
                throw new IllegalArgumentException(
                        "primaryIntervalAssociatedComponent must be the first"
                                + " interval-associated component");
            }
        }

        requireFinite("intervalAssociatedFillFraction", intervalAssociatedFillFraction);
        requireFinite("primaryComponentFillFraction", primaryComponentFillFraction);
        requireFinite("primaryWithinAssociatedFraction", primaryWithinAssociatedFraction);
        requireFinite(
                "intervalAssociatedPreviewCircularity", intervalAssociatedPreviewCircularity);
        requireFinite(
                "intervalAssociatedCircularityChange", intervalAssociatedCircularityChange);
        requireFinite("wholeEllipseCircularity", wholeEllipseCircularity);
        requireFinite("wholeEllipseCircularityChange", wholeEllipseCircularityChange);
        requireFraction("intervalAssociatedFillFraction", intervalAssociatedFillFraction);
        requireFraction("primaryComponentFillFraction", primaryComponentFillFraction);
        requireFraction("primaryWithinAssociatedFraction", primaryWithinAssociatedFraction);

        if (Double.doubleToLongBits(wholeEllipseCircularity)
                != Double.doubleToLongBits(wholeEllipsePreview.getPreviewCircularity())) {
            throw new IllegalArgumentException(
                    "wholeEllipseCircularity must exactly match wholeEllipsePreview");
        }
        if (Double.doubleToLongBits(wholeEllipseCircularityChange)
                != Double.doubleToLongBits(wholeEllipsePreview.getCircularityChange())) {
            throw new IllegalArgumentException(
                    "wholeEllipseCircularityChange must exactly match wholeEllipsePreview");
        }

        for (int i = 0; i < allComponents.size(); i++) {
            if (allComponents.get(i) == null) {
                throw new IllegalArgumentException("allComponents must not contain null");
            }
        }
        for (int i = 0; i < intervalAssociatedComponents.size(); i++) {
            final ProposedFillComponent component = intervalAssociatedComponents.get(i);
            if (component == null) {
                throw new IllegalArgumentException(
                        "intervalAssociatedComponents must not contain null");
            }
            if (!component.isIntervalAssociated()) {
                throw new IllegalArgumentException(
                        "intervalAssociatedComponents must all be interval-associated");
            }
        }
        for (int i = 0; i < nonAssociatedComponents.size(); i++) {
            final ProposedFillComponent component = nonAssociatedComponents.get(i);
            if (component == null) {
                throw new IllegalArgumentException(
                        "nonAssociatedComponents must not contain null");
            }
            if (component.isIntervalAssociated()) {
                throw new IllegalArgumentException(
                        "nonAssociatedComponents must not be interval-associated");
            }
        }

        this.wholeEllipsePreview = wholeEllipsePreview;
        this.inwardOutlierDetection = inwardOutlierDetection;
        this.intervalExteriorSeedPixels = copySortedPoints(intervalExteriorSeedPixels);
        this.intervalExteriorEdgeCount = intervalExteriorEdgeCount;
        this.uniqueIntervalExteriorSeedPixelCount = uniqueIntervalExteriorSeedPixelCount;
        this.matchedIntervalExteriorSeedPixelCount = matchedIntervalExteriorSeedPixelCount;
        this.unmatchedIntervalExteriorSeedPixelCount = unmatchedIntervalExteriorSeedPixelCount;
        this.allComponents = Collections.unmodifiableList(
                new ArrayList<ProposedFillComponent>(allComponents));
        this.intervalAssociatedComponents = Collections.unmodifiableList(
                new ArrayList<ProposedFillComponent>(intervalAssociatedComponents));
        this.nonAssociatedComponents = Collections.unmodifiableList(
                new ArrayList<ProposedFillComponent>(nonAssociatedComponents));
        this.primaryIntervalAssociatedComponent = primaryIntervalAssociatedComponent;
        this.totalComponentCount = totalComponentCount;
        this.intervalAssociatedComponentCount = intervalAssociatedComponentCount;
        this.nonAssociatedComponentCount = nonAssociatedComponentCount;
        this.totalProposedFillPixelCount = totalProposedFillPixelCount;
        this.intervalAssociatedFillPixelCount = intervalAssociatedFillPixelCount;
        this.nonAssociatedFillPixelCount = nonAssociatedFillPixelCount;
        this.intervalAssociatedFillFraction = intervalAssociatedFillFraction;
        this.primaryComponentFillFraction = primaryComponentFillFraction;
        this.primaryWithinAssociatedFraction = primaryWithinAssociatedFraction;
        this.intervalAssociatedFillPixels = copySortedPoints(intervalAssociatedFillPixels);
        this.nonAssociatedFillPixels = copySortedPoints(nonAssociatedFillPixels);
        this.intervalAssociatedPreviewLumenPixels =
                copySortedPoints(intervalAssociatedPreviewLumenPixels);
        this.intervalAssociatedPreviewArea = intervalAssociatedPreviewArea;
        this.intervalAssociatedPreviewPerimeter = intervalAssociatedPreviewPerimeter;
        this.intervalAssociatedPreviewCircularity = intervalAssociatedPreviewCircularity;
        this.intervalAssociatedCircularityChange = intervalAssociatedCircularityChange;
        this.wholeEllipseCircularity = wholeEllipseCircularity;
        this.wholeEllipseCircularityChange = wholeEllipseCircularityChange;
        this.exactlyOneIntervalAssociatedComponent = exactlyOneIntervalAssociatedComponent;
        this.allProposedFillIsIntervalAssociated = allProposedFillIsIntervalAssociated;
    }

    private static List<Point> copySortedPoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            if (point == null) {
                throw new IllegalArgumentException("Point list must not contain null entries");
            }
            if (i > 0) {
                final Point prev = source.get(i - 1);
                if (point.y < prev.y || (point.y == prev.y && point.x < prev.x)) {
                    throw new IllegalArgumentException(
                            "Point lists must be ordered by Y ascending, then X ascending");
                }
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

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static void requireFraction(final String name, final double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must lie in [0,1]: " + value);
        }
    }

    public RefittedEllipseFillPreviewResult getWholeEllipsePreview() {
        return wholeEllipsePreview;
    }

    public InwardOutlierDetectionResult getInwardOutlierDetection() {
        return inwardOutlierDetection;
    }

    public List<Point> getIntervalExteriorSeedPixels() {
        return defensivePoints(intervalExteriorSeedPixels);
    }

    public int getIntervalExteriorEdgeCount() {
        return intervalExteriorEdgeCount;
    }

    public int getUniqueIntervalExteriorSeedPixelCount() {
        return uniqueIntervalExteriorSeedPixelCount;
    }

    public int getMatchedIntervalExteriorSeedPixelCount() {
        return matchedIntervalExteriorSeedPixelCount;
    }

    public int getUnmatchedIntervalExteriorSeedPixelCount() {
        return unmatchedIntervalExteriorSeedPixelCount;
    }

    public List<ProposedFillComponent> getAllComponents() {
        return allComponents;
    }

    public List<ProposedFillComponent> getIntervalAssociatedComponents() {
        return intervalAssociatedComponents;
    }

    public List<ProposedFillComponent> getNonAssociatedComponents() {
        return nonAssociatedComponents;
    }

    public ProposedFillComponent getPrimaryIntervalAssociatedComponent() {
        return primaryIntervalAssociatedComponent;
    }

    public int getTotalComponentCount() {
        return totalComponentCount;
    }

    public int getIntervalAssociatedComponentCount() {
        return intervalAssociatedComponentCount;
    }

    public int getNonAssociatedComponentCount() {
        return nonAssociatedComponentCount;
    }

    public int getTotalProposedFillPixelCount() {
        return totalProposedFillPixelCount;
    }

    public int getIntervalAssociatedFillPixelCount() {
        return intervalAssociatedFillPixelCount;
    }

    public int getNonAssociatedFillPixelCount() {
        return nonAssociatedFillPixelCount;
    }

    public double getIntervalAssociatedFillFraction() {
        return intervalAssociatedFillFraction;
    }

    public double getPrimaryComponentFillFraction() {
        return primaryComponentFillFraction;
    }

    public double getPrimaryWithinAssociatedFraction() {
        return primaryWithinAssociatedFraction;
    }

    public List<Point> getIntervalAssociatedFillPixels() {
        return defensivePoints(intervalAssociatedFillPixels);
    }

    public List<Point> getNonAssociatedFillPixels() {
        return defensivePoints(nonAssociatedFillPixels);
    }

    public List<Point> getIntervalAssociatedPreviewLumenPixels() {
        return defensivePoints(intervalAssociatedPreviewLumenPixels);
    }

    public int getIntervalAssociatedPreviewArea() {
        return intervalAssociatedPreviewArea;
    }

    public int getIntervalAssociatedPreviewPerimeter() {
        return intervalAssociatedPreviewPerimeter;
    }

    public double getIntervalAssociatedPreviewCircularity() {
        return intervalAssociatedPreviewCircularity;
    }

    public double getIntervalAssociatedCircularityChange() {
        return intervalAssociatedCircularityChange;
    }

    public double getWholeEllipseCircularity() {
        return wholeEllipseCircularity;
    }

    public double getWholeEllipseCircularityChange() {
        return wholeEllipseCircularityChange;
    }

    public boolean isExactlyOneIntervalAssociatedComponent() {
        return exactlyOneIntervalAssociatedComponent;
    }

    public boolean isAllProposedFillIsIntervalAssociated() {
        return allProposedFillIsIntervalAssociated;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProposedFillComponentAnalysisResult)) {
            return false;
        }
        final ProposedFillComponentAnalysisResult other =
                (ProposedFillComponentAnalysisResult) obj;
        return intervalExteriorEdgeCount == other.intervalExteriorEdgeCount
                && uniqueIntervalExteriorSeedPixelCount
                == other.uniqueIntervalExteriorSeedPixelCount
                && matchedIntervalExteriorSeedPixelCount
                == other.matchedIntervalExteriorSeedPixelCount
                && unmatchedIntervalExteriorSeedPixelCount
                == other.unmatchedIntervalExteriorSeedPixelCount
                && totalComponentCount == other.totalComponentCount
                && intervalAssociatedComponentCount == other.intervalAssociatedComponentCount
                && nonAssociatedComponentCount == other.nonAssociatedComponentCount
                && totalProposedFillPixelCount == other.totalProposedFillPixelCount
                && intervalAssociatedFillPixelCount == other.intervalAssociatedFillPixelCount
                && nonAssociatedFillPixelCount == other.nonAssociatedFillPixelCount
                && intervalAssociatedPreviewArea == other.intervalAssociatedPreviewArea
                && intervalAssociatedPreviewPerimeter
                == other.intervalAssociatedPreviewPerimeter
                && exactlyOneIntervalAssociatedComponent
                == other.exactlyOneIntervalAssociatedComponent
                && allProposedFillIsIntervalAssociated
                == other.allProposedFillIsIntervalAssociated
                && doubleEquals(intervalAssociatedFillFraction,
                        other.intervalAssociatedFillFraction)
                && doubleEquals(primaryComponentFillFraction, other.primaryComponentFillFraction)
                && doubleEquals(primaryWithinAssociatedFraction,
                        other.primaryWithinAssociatedFraction)
                && doubleEquals(intervalAssociatedPreviewCircularity,
                        other.intervalAssociatedPreviewCircularity)
                && doubleEquals(intervalAssociatedCircularityChange,
                        other.intervalAssociatedCircularityChange)
                && doubleEquals(wholeEllipseCircularity, other.wholeEllipseCircularity)
                && doubleEquals(wholeEllipseCircularityChange,
                        other.wholeEllipseCircularityChange)
                && Objects.equals(wholeEllipsePreview, other.wholeEllipsePreview)
                && Objects.equals(inwardOutlierDetection, other.inwardOutlierDetection)
                && Objects.equals(intervalExteriorSeedPixels, other.intervalExteriorSeedPixels)
                && Objects.equals(allComponents, other.allComponents)
                && Objects.equals(
                        intervalAssociatedComponents, other.intervalAssociatedComponents)
                && Objects.equals(nonAssociatedComponents, other.nonAssociatedComponents)
                && Objects.equals(
                        primaryIntervalAssociatedComponent,
                        other.primaryIntervalAssociatedComponent)
                && Objects.equals(
                        intervalAssociatedFillPixels, other.intervalAssociatedFillPixels)
                && Objects.equals(nonAssociatedFillPixels, other.nonAssociatedFillPixels)
                && Objects.equals(
                        intervalAssociatedPreviewLumenPixels,
                        other.intervalAssociatedPreviewLumenPixels);
    }

    private static boolean doubleEquals(final double a, final double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                wholeEllipsePreview,
                inwardOutlierDetection,
                intervalExteriorSeedPixels,
                Integer.valueOf(intervalExteriorEdgeCount),
                Integer.valueOf(uniqueIntervalExteriorSeedPixelCount),
                Integer.valueOf(matchedIntervalExteriorSeedPixelCount),
                Integer.valueOf(unmatchedIntervalExteriorSeedPixelCount),
                allComponents,
                intervalAssociatedComponents,
                nonAssociatedComponents,
                primaryIntervalAssociatedComponent,
                Integer.valueOf(totalComponentCount),
                Integer.valueOf(intervalAssociatedComponentCount),
                Integer.valueOf(nonAssociatedComponentCount),
                Integer.valueOf(totalProposedFillPixelCount),
                Integer.valueOf(intervalAssociatedFillPixelCount),
                Integer.valueOf(nonAssociatedFillPixelCount),
                Double.valueOf(intervalAssociatedFillFraction),
                Double.valueOf(primaryComponentFillFraction),
                Double.valueOf(primaryWithinAssociatedFraction),
                intervalAssociatedFillPixels,
                nonAssociatedFillPixels,
                intervalAssociatedPreviewLumenPixels,
                Integer.valueOf(intervalAssociatedPreviewArea),
                Integer.valueOf(intervalAssociatedPreviewPerimeter),
                Double.valueOf(intervalAssociatedPreviewCircularity),
                Double.valueOf(intervalAssociatedCircularityChange),
                Double.valueOf(wholeEllipseCircularity),
                Double.valueOf(wholeEllipseCircularityChange),
                Boolean.valueOf(exactlyOneIntervalAssociatedComponent),
                Boolean.valueOf(allProposedFillIsIntervalAssociated));
    }

    @Override
    public String toString() {
        return "ProposedFillComponentAnalysisResult{components=" + totalComponentCount
                + ", associated=" + intervalAssociatedComponentCount
                + ", associatedFill=" + intervalAssociatedFillPixelCount
                + ", totalFill=" + totalProposedFillPixelCount
                + '}';
    }
}
