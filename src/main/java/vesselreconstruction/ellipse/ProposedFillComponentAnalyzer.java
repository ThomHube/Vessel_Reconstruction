package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic 8-connected proposed-fill component diagnostics with exact
 * interval-exterior seed association. Does not accept components, modify
 * classified pixels, refit ellipses, or change the selected interval.
 */
public final class ProposedFillComponentAnalyzer {

    private static final double CIRCULARITY_OVERSHOOT = 1.0e-9;

    /** 8-connectivity neighbour offsets in exact discovery order. */
    private static final int[] OFFSET_DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] OFFSET_DY = {-1, -1, -1, 0, 0, 1, 1, 1};

    /** Cardinal contact order: NORTH, EAST, SOUTH, WEST. */
    private static final int[] CARDINAL_DX = {0, 1, 0, -1};
    private static final int[] CARDINAL_DY = {-1, 0, 1, 0};

    public ProposedFillComponentAnalysisResult analyze(
            final ByteProcessor classifiedProcessor,
            final RefittedEllipseFillPreviewResult wholeEllipsePreview,
            final InwardOutlierDetectionResult inwardOutlierDetection) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (wholeEllipsePreview == null) {
            throw new IllegalArgumentException("wholeEllipsePreview must not be null");
        }
        if (inwardOutlierDetection == null) {
            throw new IllegalArgumentException("inwardOutlierDetection must not be null");
        }
        if (!inwardOutlierDetection.isIntervalSelected()
                || inwardOutlierDetection.getSelectedInterval() == null) {
            throw new IllegalArgumentException(
                    "inwardOutlierDetection must have intervalSelected=true");
        }

        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (width != wholeEllipsePreview.getImageWidth()
                || height != wholeEllipsePreview.getImageHeight()) {
            throw new IllegalArgumentException(
                    "Image dimensions must match wholeEllipsePreview: processor="
                            + width + "x" + height + ", preview="
                            + wholeEllipsePreview.getImageWidth() + "x"
                            + wholeEllipsePreview.getImageHeight());
        }

        final OrderedBoundaryLoop boundaryLoop =
                inwardOutlierDetection.getResidualProfile().getBoundaryLoop();
        if (boundaryLoop == null) {
            throw new IllegalArgumentException("boundaryLoop must not be null");
        }
        validateSelectedInterval(
                inwardOutlierDetection.getSelectedInterval(), boundaryLoop);

        return analyzeCore(
                classifiedProcessor,
                wholeEllipsePreview,
                inwardOutlierDetection,
                wholeEllipsePreview.getSelectedObservedLumenPixels(),
                wholeEllipsePreview.getRefittedProposedFillPixels(),
                boundaryLoop,
                inwardOutlierDetection.getSelectedInterval());
    }

    /**
     * Package-visible test hook using the same private component, seed,
     * association, ordering, perimeter, and circularity logic.
     */
    ProposedFillComponentAnalysisResult analyzeForTesting(
            final ByteProcessor classifiedProcessor,
            final List<Point> selectedObservedLumenPixels,
            final List<Point> proposedFillPixels,
            final OrderedBoundaryLoop boundaryLoop,
            final List<Integer> selectedIntervalIndices,
            final double originalCircularity,
            final double wholeEllipseCircularity,
            final double wholeEllipseCircularityChange) {
        if (classifiedProcessor == null) {
            throw new IllegalArgumentException("classifiedProcessor must not be null");
        }
        if (selectedObservedLumenPixels == null) {
            throw new IllegalArgumentException(
                    "selectedObservedLumenPixels must not be null");
        }
        if (proposedFillPixels == null) {
            throw new IllegalArgumentException("proposedFillPixels must not be null");
        }
        if (boundaryLoop == null) {
            throw new IllegalArgumentException("boundaryLoop must not be null");
        }
        if (selectedIntervalIndices == null || selectedIntervalIndices.isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedIntervalIndices must not be null or empty");
        }
        if (!Double.isFinite(originalCircularity)
                || !Double.isFinite(wholeEllipseCircularity)
                || !Double.isFinite(wholeEllipseCircularityChange)) {
            throw new IllegalArgumentException("Circularity values must be finite");
        }

        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        final List<Point> selected = copyPoints(selectedObservedLumenPixels);
        final List<Point> fill = copyPoints(proposedFillPixels);
        sortPointsInPlace(selected);
        sortPointsInPlace(fill);

        final EllipseModel ellipse = syntheticEllipse(width, height);
        final OrderedBoundaryResidualProfile residualProfile =
                OrderedBoundaryResidualProfile.create(boundaryLoop, ellipse);
        final InwardOutlierInterval selectedInterval = buildSyntheticInterval(
                residualProfile, boundaryLoop, selectedIntervalIndices);
        final List<InwardOutlierInterval> candidates =
                new ArrayList<InwardOutlierInterval>(1);
        candidates.add(selectedInterval);
        final InwardOutlierDetectionResult detection = new InwardOutlierDetectionResult(
                residualProfile,
                1.0,
                0.0,
                0.0,
                candidates,
                selectedInterval,
                true,
                InwardOutlierDetectionResult.SELECTED_DOMINANT_INWARD_INTERVAL,
                1.0);

        final RefittedEllipseFillPreviewResult wholePreview = buildSyntheticWholePreview(
                width,
                height,
                selected,
                fill,
                ellipse,
                originalCircularity,
                wholeEllipseCircularity,
                wholeEllipseCircularityChange);

        return analyzeCore(
                classifiedProcessor,
                wholePreview,
                detection,
                selected,
                fill,
                boundaryLoop,
                selectedInterval);
    }

    private static ProposedFillComponentAnalysisResult analyzeCore(
            final ByteProcessor classifiedProcessor,
            final RefittedEllipseFillPreviewResult wholeEllipsePreview,
            final InwardOutlierDetectionResult inwardOutlierDetection,
            final List<Point> selectedObservedLumenPixels,
            final List<Point> proposedFillPixels,
            final OrderedBoundaryLoop boundaryLoop,
            final InwardOutlierInterval selectedInterval) {
        final byte[] pixels = (byte[]) classifiedProcessor.getPixels();
        if (pixels == null) {
            throw new IllegalArgumentException("classifiedProcessor pixels must not be null");
        }
        final int width = classifiedProcessor.getWidth();
        final int height = classifiedProcessor.getHeight();
        if (pixels.length != width * height) {
            throw new IllegalArgumentException(
                    "Pixel array length does not match image dimensions");
        }

        final List<Point> selected = copyPoints(selectedObservedLumenPixels);
        final List<Point> fill = copyPoints(proposedFillPixels);
        sortPointsInPlace(selected);
        sortPointsInPlace(fill);

        final Set<Long> selectedKeys = toKeySet(selected);
        final Set<Long> fillKeys = toKeySet(fill);
        if (fillKeys.size() != fill.size()) {
            throw new IllegalArgumentException(
                    "proposedFillPixels must not contain duplicate coordinates");
        }

        final List<ProposedFillComponent> allComponents = extractComponents(
                pixels, width, height, fill, fillKeys, selectedKeys);

        final SeedDiagnostics seeds = deriveIntervalExteriorSeeds(
                width, height, boundaryLoop, selectedInterval, fillKeys);

        final Map<Long, Integer> pixelToComponentId = new HashMap<Long, Integer>();
        for (int i = 0; i < allComponents.size(); i++) {
            final ProposedFillComponent component = allComponents.get(i);
            final List<Point> componentPixels = component.getPixels();
            for (int j = 0; j < componentPixels.size(); j++) {
                final Point point = componentPixels.get(j);
                pixelToComponentId.put(
                        Long.valueOf(pack(point.x, point.y)),
                        Integer.valueOf(component.getComponentId()));
            }
        }

        final int[] seedPixelCounts = new int[allComponents.size() + 1];
        final int[] edgeHitCounts = new int[allComponents.size() + 1];
        final Set<Long>[] matchedSeedsPerComponent = new Set[allComponents.size() + 1];
        for (int i = 1; i <= allComponents.size(); i++) {
            matchedSeedsPerComponent[i] = new HashSet<Long>();
        }

        for (int e = 0; e < seeds.edgeExteriorKeys.size(); e++) {
            final Long exteriorKey = seeds.edgeExteriorKeys.get(e);
            if (exteriorKey == null) {
                continue;
            }
            final Integer componentId = pixelToComponentId.get(exteriorKey);
            if (componentId != null) {
                edgeHitCounts[componentId.intValue()]++;
                matchedSeedsPerComponent[componentId.intValue()].add(exteriorKey);
            }
        }
        for (int i = 1; i <= allComponents.size(); i++) {
            seedPixelCounts[i] = matchedSeedsPerComponent[i].size();
        }

        final List<ProposedFillComponent> rebuilt = new ArrayList<ProposedFillComponent>(
                allComponents.size());
        for (int i = 0; i < allComponents.size(); i++) {
            final ProposedFillComponent base = allComponents.get(i);
            final int id = base.getComponentId();
            final int seedCount = seedPixelCounts[id];
            rebuilt.add(new ProposedFillComponent(
                    id,
                    base.getPixels(),
                    base.getArea(),
                    base.getBounds(),
                    base.getSelectedLumenContactPixelCount(),
                    base.getSelectedLumenContactEdgeCount(),
                    seedCount,
                    edgeHitCounts[id],
                    seedCount > 0,
                    base.getWoodPixelCount(),
                    base.getRayPixelCount(),
                    base.getBackgroundPixelCount(),
                    base.getOtherClassPixelCount()));
        }

        final List<ProposedFillComponent> associated =
                new ArrayList<ProposedFillComponent>();
        final List<ProposedFillComponent> nonAssociated =
                new ArrayList<ProposedFillComponent>();
        for (int i = 0; i < rebuilt.size(); i++) {
            final ProposedFillComponent component = rebuilt.get(i);
            if (component.isIntervalAssociated()) {
                associated.add(component);
            } else {
                nonAssociated.add(component);
            }
        }
        Collections.sort(associated, ASSOCIATED_ORDER);

        final ProposedFillComponent primary =
                associated.isEmpty() ? null : associated.get(0);

        final List<Point> associatedFill = unionComponentPixels(associated);
        final List<Point> nonAssociatedFill = unionComponentPixels(nonAssociated);
        final Set<Long> associatedFillKeys = toKeySet(associatedFill);
        final List<Point> previewLumen = unionPoints(selectedKeys, associatedFillKeys);

        final int totalFill = fill.size();
        final int associatedCount = associatedFill.size();
        final int nonAssociatedCount = nonAssociatedFill.size();
        if (associatedCount + nonAssociatedCount != totalFill) {
            throw new IllegalStateException(
                    "associated plus non-associated fill must equal total fill");
        }

        final double associatedFraction;
        final double primaryTotalFraction;
        final double primaryAssociatedFraction;
        if (totalFill == 0) {
            associatedFraction = 0.0;
            primaryTotalFraction = 0.0;
            primaryAssociatedFraction = 0.0;
        } else {
            associatedFraction = associatedCount / (double) totalFill;
            if (primary == null) {
                primaryTotalFraction = 0.0;
                primaryAssociatedFraction = 0.0;
            } else {
                primaryTotalFraction = primary.getArea() / (double) totalFill;
                primaryAssociatedFraction =
                        primary.getArea() / (double) associatedCount;
            }
        }

        final Set<Long> previewKeys = toKeySet(previewLumen);
        final int previewArea = previewLumen.size();
        final int previewPerimeter =
                exposedCardinalPerimeter(previewKeys, width, height);
        final double previewCircularity = circularity(previewArea, previewPerimeter);
        final double associatedCircularityChange =
                previewCircularity - wholeEllipsePreview.getOriginalCircularity();

        return new ProposedFillComponentAnalysisResult(
                wholeEllipsePreview,
                inwardOutlierDetection,
                seeds.uniqueSeedPixels,
                seeds.intervalExteriorEdgeCount,
                seeds.uniqueSeedPixels.size(),
                seeds.matchedUniqueSeedCount,
                seeds.uniqueSeedPixels.size() - seeds.matchedUniqueSeedCount,
                rebuilt,
                associated,
                nonAssociated,
                primary,
                rebuilt.size(),
                associated.size(),
                nonAssociated.size(),
                totalFill,
                associatedCount,
                nonAssociatedCount,
                associatedFraction,
                primaryTotalFraction,
                primaryAssociatedFraction,
                associatedFill,
                nonAssociatedFill,
                previewLumen,
                previewArea,
                previewPerimeter,
                previewCircularity,
                associatedCircularityChange,
                wholeEllipsePreview.getPreviewCircularity(),
                wholeEllipsePreview.getCircularityChange(),
                associated.size() == 1,
                nonAssociatedCount == 0);
    }

    private static final Comparator<ProposedFillComponent> ASSOCIATED_ORDER =
            new Comparator<ProposedFillComponent>() {
                @Override
                public int compare(
                        final ProposedFillComponent a,
                        final ProposedFillComponent b) {
                    if (a.getIntervalExteriorEdgeHitCount()
                            != b.getIntervalExteriorEdgeHitCount()) {
                        return Integer.compare(
                                b.getIntervalExteriorEdgeHitCount(),
                                a.getIntervalExteriorEdgeHitCount());
                    }
                    if (a.getIntervalExteriorSeedPixelCount()
                            != b.getIntervalExteriorSeedPixelCount()) {
                        return Integer.compare(
                                b.getIntervalExteriorSeedPixelCount(),
                                a.getIntervalExteriorSeedPixelCount());
                    }
                    if (a.getArea() != b.getArea()) {
                        return Integer.compare(b.getArea(), a.getArea());
                    }
                    if (a.getSelectedLumenContactEdgeCount()
                            != b.getSelectedLumenContactEdgeCount()) {
                        return Integer.compare(
                                b.getSelectedLumenContactEdgeCount(),
                                a.getSelectedLumenContactEdgeCount());
                    }
                    final Rectangle ab = a.getBounds();
                    final Rectangle bb = b.getBounds();
                    if (ab.y != bb.y) {
                        return Integer.compare(ab.y, bb.y);
                    }
                    if (ab.x != bb.x) {
                        return Integer.compare(ab.x, bb.x);
                    }
                    return Integer.compare(a.getComponentId(), b.getComponentId());
                }
            };

    private static List<ProposedFillComponent> extractComponents(
            final byte[] pixels,
            final int width,
            final int height,
            final List<Point> fillSorted,
            final Set<Long> fillKeys,
            final Set<Long> selectedKeys) {
        final Set<Long> visited = new HashSet<Long>();
        final List<ProposedFillComponent> components =
                new ArrayList<ProposedFillComponent>();
        int nextId = 1;

        for (int i = 0; i < fillSorted.size(); i++) {
            final Point seed = fillSorted.get(i);
            final long seedKey = pack(seed.x, seed.y);
            if (visited.contains(Long.valueOf(seedKey))) {
                continue;
            }

            final List<Point> memberPixels = new ArrayList<Point>();
            final ArrayDeque<Point> queue = new ArrayDeque<Point>();
            visited.add(Long.valueOf(seedKey));
            queue.add(new Point(seed.x, seed.y));

            while (!queue.isEmpty()) {
                final Point current = queue.removeFirst();
                memberPixels.add(new Point(current.x, current.y));
                for (int n = 0; n < OFFSET_DX.length; n++) {
                    final int nx = current.x + OFFSET_DX[n];
                    final int ny = current.y + OFFSET_DY[n];
                    final long nKey = pack(nx, ny);
                    if (!fillKeys.contains(Long.valueOf(nKey))) {
                        continue;
                    }
                    if (visited.add(Long.valueOf(nKey))) {
                        queue.addLast(new Point(nx, ny));
                    }
                }
            }

            sortPointsInPlace(memberPixels);
            components.add(buildComponent(
                    nextId++,
                    memberPixels,
                    pixels,
                    width,
                    height,
                    selectedKeys));
        }

        if (visited.size() != fillSorted.size()) {
            throw new IllegalStateException(
                    "Every proposed-fill pixel must belong to exactly one component");
        }
        return components;
    }

    private static ProposedFillComponent buildComponent(
            final int componentId,
            final List<Point> memberPixels,
            final byte[] pixels,
            final int width,
            final int height,
            final Set<Long> selectedKeys) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int contactPixels = 0;
        int contactEdges = 0;
        int wood = 0;
        int ray = 0;
        int background = 0;
        int other = 0;

        for (int i = 0; i < memberPixels.size(); i++) {
            final Point point = memberPixels.get(i);
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

            boolean touchesLumen = false;
            for (int c = 0; c < CARDINAL_DX.length; c++) {
                final int nx = point.x + CARDINAL_DX[c];
                final int ny = point.y + CARDINAL_DY[c];
                if (selectedKeys.contains(Long.valueOf(pack(nx, ny)))) {
                    contactEdges++;
                    touchesLumen = true;
                }
            }
            if (touchesLumen) {
                contactPixels++;
            }

            if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) {
                throw new IllegalArgumentException(
                        "Proposed-fill pixel out of bounds: (" + point.x + ", " + point.y + ")");
            }
            final int resultValue = unsigned(pixels[point.y * width + point.x]);
            if (resultValue == ClassifierLabels.LUMEN_RESULT_VALUE) {
                throw new IllegalStateException("PROPOSED_FILL_CONTAINS_LUMEN");
            }
            if (resultValue == ClassifierLabels.WOOD_RESULT_VALUE) {
                wood++;
            } else if (resultValue == ClassifierLabels.RAY_RESULT_VALUE) {
                ray++;
            } else if (resultValue == ClassifierLabels.BACKGROUND_RESULT_VALUE) {
                background++;
            } else {
                other++;
            }
        }

        final Rectangle bounds = new Rectangle(
                minX, minY, maxX - minX + 1, maxY - minY + 1);
        return new ProposedFillComponent(
                componentId,
                memberPixels,
                memberPixels.size(),
                bounds,
                contactPixels,
                contactEdges,
                0,
                0,
                false,
                wood,
                ray,
                background,
                other);
    }

    private static SeedDiagnostics deriveIntervalExteriorSeeds(
            final int width,
            final int height,
            final OrderedBoundaryLoop boundaryLoop,
            final InwardOutlierInterval selectedInterval,
            final Set<Long> fillKeys) {
        final List<DirectedPixelEdge> edges = boundaryLoop.getDirectedEdges();
        final List<Integer> indices = selectedInterval.getOrderedBoundaryIndices();
        final Set<Long> uniqueInBounds = new HashSet<Long>();
        final List<Long> edgeExteriorKeys = new ArrayList<Long>(indices.size());

        for (int i = 0; i < indices.size(); i++) {
            final int index = indices.get(i).intValue();
            final DirectedPixelEdge edge = edges.get(index);
            final Point exterior = exteriorSeedFor(edge);
            final boolean inBounds = exterior.x >= 0 && exterior.y >= 0
                    && exterior.x < width && exterior.y < height;
            if (inBounds) {
                final long key = pack(exterior.x, exterior.y);
                uniqueInBounds.add(Long.valueOf(key));
                edgeExteriorKeys.add(Long.valueOf(key));
            } else {
                edgeExteriorKeys.add(null);
            }
        }

        int matchedUnique = 0;
        final List<Point> uniquePixels = new ArrayList<Point>(uniqueInBounds.size());
        for (final Long keyObj : uniqueInBounds) {
            uniquePixels.add(unpack(keyObj.longValue()));
            if (fillKeys.contains(keyObj)) {
                matchedUnique++;
            }
        }
        sortPointsInPlace(uniquePixels);

        final SeedDiagnostics diagnostics = new SeedDiagnostics();
        diagnostics.intervalExteriorEdgeCount = indices.size();
        diagnostics.uniqueSeedPixels = uniquePixels;
        diagnostics.matchedUniqueSeedCount = matchedUnique;
        diagnostics.edgeExteriorKeys = edgeExteriorKeys;
        return diagnostics;
    }

    static Point exteriorSeedFor(final DirectedPixelEdge edge) {
        final int sx = edge.getStartX();
        final int sy = edge.getStartY();
        switch (edge.getDirection()) {
            case EAST:
                return new Point(sx, sy - 1);
            case SOUTH:
                return new Point(sx, sy);
            case WEST:
                return new Point(sx - 1, sy);
            case NORTH:
                return new Point(sx - 1, sy - 1);
            default:
                throw new IllegalStateException(
                        "Unknown edge direction: " + edge.getDirection());
        }
    }

    private static void validateSelectedInterval(
            final InwardOutlierInterval selectedInterval,
            final OrderedBoundaryLoop boundaryLoop) {
        final int edgeCount = boundaryLoop.getDirectedEdges().size();
        final List<Integer> indices = selectedInterval.getOrderedBoundaryIndices();
        if (indices.isEmpty()) {
            throw new IllegalArgumentException(
                    "selected interval must contain at least one boundary index");
        }
        for (int i = 0; i < indices.size(); i++) {
            final Integer value = indices.get(i);
            if (value == null || value.intValue() < 0 || value.intValue() >= edgeCount) {
                throw new IllegalArgumentException(
                        "selected interval index out of range: " + value);
            }
        }
    }

    private static InwardOutlierInterval buildSyntheticInterval(
            final OrderedBoundaryResidualProfile residualProfile,
            final OrderedBoundaryLoop boundaryLoop,
            final List<Integer> selectedIntervalIndices) {
        final List<DirectedPixelEdge> edges = boundaryLoop.getDirectedEdges();
        final int n = edges.size();
        final List<Integer> indices = new ArrayList<Integer>(selectedIntervalIndices.size());
        final Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < selectedIntervalIndices.size(); i++) {
            final Integer value = selectedIntervalIndices.get(i);
            if (value == null || value.intValue() < 0 || value.intValue() >= n) {
                throw new IllegalArgumentException(
                        "selectedIntervalIndices contains invalid index: " + value);
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        "selectedIntervalIndices must not contain duplicates");
            }
            indices.add(Integer.valueOf(value.intValue()));
        }

        boolean wrapsAround = false;
        for (int i = 0; i < indices.size() - 1; i++) {
            final int current = indices.get(i).intValue();
            final int next = indices.get(i + 1).intValue();
            final int expected = (current + 1) % n;
            if (next != expected) {
                throw new IllegalArgumentException(
                        "selectedIntervalIndices must form a contiguous circular run");
            }
            if (current == n - 1 && next == 0) {
                wrapsAround = true;
            }
        }

        final int startIndex = indices.get(0).intValue();
        final int endIndex = indices.get(indices.size() - 1).intValue();
        final int edgeCount = indices.size();
        final List<OrderedBoundaryResidualProfile.Sample> samples =
                residualProfile.getSamples();

        double sum = 0.0;
        double minResidual = Double.POSITIVE_INFINITY;
        int minIndex = startIndex;
        for (int i = 0; i < indices.size(); i++) {
            final int idx = indices.get(i).intValue();
            final double residual = samples.get(idx).getResidual();
            sum += residual;
            if (residual < minResidual) {
                minResidual = residual;
                minIndex = idx;
            }
        }

        final Point2D.Double startMid = edges.get(startIndex).getMidpoint();
        final Point2D.Double endMid = edges.get(endIndex).getMidpoint();
        final boolean containsGlobal =
                minIndex == residualProfile.getMinimumResidualIndex();

        return new InwardOutlierInterval(
                startIndex,
                endIndex,
                wrapsAround,
                edgeCount,
                edgeCount / (double) n,
                edgeCount,
                minResidual,
                minIndex,
                sum / edgeCount,
                1.0,
                containsGlobal,
                startMid,
                endMid,
                indices);
    }

    private static RefittedEllipseFillPreviewResult buildSyntheticWholePreview(
            final int width,
            final int height,
            final List<Point> selected,
            final List<Point> fill,
            final EllipseModel ellipse,
            final double originalCircularity,
            final double wholeEllipseCircularity,
            final double wholeEllipseCircularityChange) {
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedObservedLumenPixels must not be empty");
        }

        final Set<Long> selectedKeys = toKeySet(selected);
        final Set<Long> fillKeys = toKeySet(fill);
        final List<Point> preview = unionPoints(selectedKeys, fillKeys);
        final List<Point> empty = Collections.emptyList();

        final int originalPerimeter =
                exposedCardinalPerimeter(selectedKeys, width, height);
        final int previewPerimeter =
                exposedCardinalPerimeter(toKeySet(preview), width, height);
        if (originalPerimeter <= 0 || previewPerimeter <= 0) {
            throw new IllegalArgumentException(
                    "Synthetic preview requires positive original and preview perimeters");
        }

        final int originalArea = selected.size();
        final int fillCount = fill.size();
        final double addedAreaFraction = fillCount / (double) originalArea;
        final double ellipseAreaRatio = ellipse.getArea() / (double) originalArea;

        return new RefittedEllipseFillPreviewResult(
                width,
                height,
                ellipse,
                ellipse,
                selected,
                fill,
                fill,
                fill,
                empty,
                empty,
                fill,
                empty,
                empty,
                empty,
                empty,
                empty,
                preview,
                originalArea,
                originalArea + fillCount,
                originalArea + fillCount,
                fillCount,
                fillCount,
                fillCount,
                0,
                0,
                fillCount,
                0,
                0,
                0,
                0,
                0,
                originalArea,
                originalPerimeter,
                originalCircularity,
                originalArea + fillCount,
                previewPerimeter,
                wholeEllipseCircularity,
                wholeEllipseCircularityChange,
                addedAreaFraction,
                ellipseAreaRatio,
                false);
    }

    private static EllipseModel syntheticEllipse(final int width, final int height) {
        final double cx = Math.max(1.0, width / 2.0);
        final double cy = Math.max(1.0, height / 2.0);
        final double major = Math.max(2.0, Math.max(width, height));
        final double minor = Math.max(1.0, major / 2.0);
        return new EllipseModel(cx, cy, major, minor, 0.0);
    }

    private static List<Point> unionComponentPixels(
            final List<ProposedFillComponent> components) {
        final List<Point> points = new ArrayList<Point>();
        for (int i = 0; i < components.size(); i++) {
            final List<Point> componentPixels = components.get(i).getPixels();
            for (int j = 0; j < componentPixels.size(); j++) {
                final Point point = componentPixels.get(j);
                points.add(new Point(point.x, point.y));
            }
        }
        sortPointsInPlace(points);
        return points;
    }

    private static int exposedCardinalPerimeter(
            final Set<Long> memberKeys,
            final int width,
            final int height) {
        int perimeter = 0;
        for (final Long keyObj : memberKeys) {
            final long key = keyObj.longValue();
            final int x = (int) (key >> 32);
            final int y = (int) key;
            for (int i = 0; i < CARDINAL_DX.length; i++) {
                final int nx = x + CARDINAL_DX[i];
                final int ny = y + CARDINAL_DY[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height
                        || !memberKeys.contains(Long.valueOf(pack(nx, ny)))) {
                    perimeter++;
                }
            }
        }
        return perimeter;
    }

    private static double circularity(final int area, final int perimeter) {
        if (perimeter <= 0) {
            throw new IllegalStateException("Perimeter must be positive for circularity");
        }
        final double value = 4.0 * Math.PI * area / (perimeter * (double) perimeter);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite circularity");
        }
        if (value > 1.0 && value < 1.0 + CIRCULARITY_OVERSHOOT) {
            return 1.0;
        }
        return value;
    }

    private static List<Point> unionPoints(
            final Set<Long> a,
            final Set<Long> b) {
        final Set<Long> union = new HashSet<Long>(a);
        union.addAll(b);
        final List<Point> points = new ArrayList<Point>(union.size());
        for (final Long keyObj : union) {
            points.add(unpack(keyObj.longValue()));
        }
        return sortPoints(points);
    }

    private static List<Point> copyPoints(final List<Point> source) {
        final List<Point> copy = new ArrayList<Point>(source.size());
        for (int i = 0; i < source.size(); i++) {
            final Point point = source.get(i);
            if (point == null) {
                throw new IllegalArgumentException("Point list must not contain null entries");
            }
            copy.add(new Point(point.x, point.y));
        }
        return copy;
    }

    private static List<Point> sortPoints(final List<Point> points) {
        final List<Point> sorted = new ArrayList<Point>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            sorted.add(new Point(point.x, point.y));
        }
        sortPointsInPlace(sorted);
        return sorted;
    }

    private static void sortPointsInPlace(final List<Point> points) {
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(final Point a, final Point b) {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return Integer.compare(a.x, b.x);
            }
        });
    }

    private static Set<Long> toKeySet(final List<Point> points) {
        final Set<Long> keys = new HashSet<Long>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            final Point point = points.get(i);
            keys.add(Long.valueOf(pack(point.x, point.y)));
        }
        return keys;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Point unpack(final long key) {
        return new Point((int) (key >> 32), (int) key);
    }

    private static int unsigned(final byte value) {
        return value & 0xff;
    }

    private static final class SeedDiagnostics {
        private int intervalExteriorEdgeCount;
        private List<Point> uniqueSeedPixels;
        private int matchedUniqueSeedCount;
        private List<Long> edgeExteriorKeys;
    }
}
