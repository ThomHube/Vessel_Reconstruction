package vesselreconstruction.ellipse;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Wand;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic diagnostic comparison of ImageJ core boundary implementations
 * against the project's expected exposed directed pixel-edge geometry.
 *
 * <p>This class never modifies production boundary traversal and never selects a
 * production successor.</p>
 */
public final class ImageJBoundaryReferenceAnalyzer {

    private static final double LATTICE_TOLERANCE = 1.0e-9;

    public ImageJBoundaryReferenceResult analyze(
            final int imageWidth,
            final int imageHeight,
            final List<Point> selectedLumenPixels,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic) {
        validateInputs(imageWidth, imageHeight, selectedLumenPixels, ambiguityDiagnostic);

        final List<Point> selected = copySelected(selectedLumenPixels);
        final Set<Long> membership = toMembership(selected);
        final List<DirectedPixelEdge> expectedEdges = buildExpectedExposedEdges(selected, membership);
        final Set<String> expectedUndirected = toUndirectedKeys(expectedEdges);

        final ByteProcessor mask = createMask(imageWidth, imageHeight, selected);
        final byte[] snapshot = copyBytes((byte[]) mask.getPixels());

        List<ImageJBoundaryReferenceResult.ReferenceOutline> ttsOutlines;
        List<ImageJBoundaryReferenceResult.ReferenceOutline> wand4Outlines;
        List<ImageJBoundaryReferenceResult.ReferenceOutline> wand8Outlines;
        String conversionFailure = null;
        try {
            ttsOutlines = analyzeThresholdToSelection(
                    duplicateMask(mask),
                    expectedEdges,
                    expectedUndirected,
                    ambiguityDiagnostic);
            wand4Outlines = analyzeWand(
                    duplicateMask(mask),
                    selected,
                    Wand.FOUR_CONNECTED,
                    ImageJBoundaryReferenceResult.ReferenceMethod.WAND_FOUR_CONNECTED,
                    expectedEdges,
                    expectedUndirected,
                    ambiguityDiagnostic);
            wand8Outlines = analyzeWand(
                    duplicateMask(mask),
                    selected,
                    Wand.EIGHT_CONNECTED,
                    ImageJBoundaryReferenceResult.ReferenceMethod.WAND_EIGHT_CONNECTED,
                    expectedEdges,
                    expectedUndirected,
                    ambiguityDiagnostic);
        } catch (RuntimeException ex) {
            conversionFailure = ImageJBoundaryReferenceResult.REFERENCE_CONVERSION_FAILED;
            ttsOutlines = Collections.emptyList();
            wand4Outlines = Collections.emptyList();
            wand8Outlines = Collections.emptyList();
        }

        if (!sameBytes(snapshot, (byte[]) mask.getPixels())) {
            throw new IllegalStateException(
                    "Private diagnostic mask was modified by a reference implementation");
        }

        final String conclusion;
        if (conversionFailure != null) {
            conclusion = conversionFailure;
        } else {
            conclusion = decideConclusion(
                    ttsOutlines, wand4Outlines, wand8Outlines, expectedUndirected.size());
        }

        return new ImageJBoundaryReferenceResult(
                imageWidth,
                imageHeight,
                selected.size(),
                expectedEdges.size(),
                expectedEdges,
                ambiguityDiagnostic,
                ttsOutlines,
                wand4Outlines,
                wand8Outlines,
                true,
                conclusion);
    }

    // ------------------------------------------------------------------
    // Input validation / mask
    // ------------------------------------------------------------------

    private static void validateInputs(
            final int imageWidth,
            final int imageHeight,
            final List<Point> selectedLumenPixels,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        if (selectedLumenPixels == null) {
            throw new IllegalArgumentException("selectedLumenPixels must not be null");
        }
        if (selectedLumenPixels.isEmpty()) {
            throw new IllegalArgumentException("selectedLumenPixels must not be empty");
        }
        if (ambiguityDiagnostic == null) {
            throw new IllegalArgumentException("ambiguityDiagnostic must not be null");
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        final Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i < selectedLumenPixels.size(); i++) {
            final Point p = selectedLumenPixels.get(i);
            if (p == null) {
                throw new IllegalArgumentException(
                        "selectedLumenPixels must not contain null entries");
            }
            if (p.x < 0 || p.y < 0 || p.x >= imageWidth || p.y >= imageHeight) {
                throw new IllegalArgumentException(
                        "selected coordinate out of bounds: (" + p.x + ", " + p.y + ")");
            }
            final long key = pack(p.x, p.y);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate coordinate pair is not allowed: (" + p.x + ", " + p.y + ")");
            }
            if (p.x < minX) {
                minX = p.x;
            }
            if (p.y < minY) {
                minY = p.y;
            }
            if (p.x > maxX) {
                maxX = p.x;
            }
            if (p.y > maxY) {
                maxY = p.y;
            }
        }

        final int vx = ambiguityDiagnostic.getVertexX();
        final int vy = ambiguityDiagnostic.getVertexY();
        // Lattice vertex must lie on or immediately adjacent to selected pixel bounds.
        if (vx < minX || vx > maxX + 1 || vy < minY || vy > maxY + 1) {
            throw new IllegalArgumentException(
                    "ambiguous vertex is not on or immediately adjacent to selected bounds: ("
                            + vx + "," + vy + ")");
        }
    }

    private static List<Point> copySelected(final List<Point> selectedLumenPixels) {
        final List<Point> copy = new ArrayList<Point>(selectedLumenPixels.size());
        for (int i = 0; i < selectedLumenPixels.size(); i++) {
            final Point p = selectedLumenPixels.get(i);
            copy.add(new Point(p.x, p.y));
        }
        return copy;
    }

    private static ByteProcessor createMask(
            final int width,
            final int height,
            final List<Point> selected) {
        final ByteProcessor mask = new ByteProcessor(width, height);
        mask.setValue(0);
        mask.fill();
        for (int i = 0; i < selected.size(); i++) {
            final Point p = selected.get(i);
            mask.set(p.x, p.y, 255);
        }
        return mask;
    }

    private static ByteProcessor duplicateMask(final ByteProcessor mask) {
        return (ByteProcessor) mask.duplicate();
    }

    // ------------------------------------------------------------------
    // Expected exposed edges
    // ------------------------------------------------------------------

    static List<DirectedPixelEdge> buildExpectedExposedEdges(
            final List<Point> pixels,
            final Set<Long> membership) {
        final List<DirectedPixelEdge> edges = new ArrayList<DirectedPixelEdge>();
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            final int x = p.x;
            final int y = p.y;
            if (!membership.contains(pack(x, y - 1))) {
                edges.add(new DirectedPixelEdge(x, y, x + 1, y));
            }
            if (!membership.contains(pack(x + 1, y))) {
                edges.add(new DirectedPixelEdge(x + 1, y, x + 1, y + 1));
            }
            if (!membership.contains(pack(x, y + 1))) {
                edges.add(new DirectedPixelEdge(x + 1, y + 1, x, y + 1));
            }
            if (!membership.contains(pack(x - 1, y))) {
                edges.add(new DirectedPixelEdge(x, y + 1, x, y));
            }
        }
        Collections.sort(edges, EDGE_ORDER);
        return edges;
    }

    static String undirectedKey(final DirectedPixelEdge edge) {
        final int ax = edge.getStartX();
        final int ay = edge.getStartY();
        final int bx = edge.getEndX();
        final int by = edge.getEndY();
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        if (ay < by || (ay == by && ax <= bx)) {
            x0 = ax;
            y0 = ay;
            x1 = bx;
            y1 = by;
        } else {
            x0 = bx;
            y0 = by;
            x1 = ax;
            y1 = ay;
        }
        return y0 + "," + x0 + "," + y1 + "," + x1;
    }

    private static Set<String> toUndirectedKeys(final List<DirectedPixelEdge> edges) {
        final Set<String> keys = new HashSet<String>(edges.size() * 2);
        for (int i = 0; i < edges.size(); i++) {
            keys.add(undirectedKey(edges.get(i)));
        }
        return keys;
    }

    private static final Comparator<DirectedPixelEdge> EDGE_ORDER =
            new Comparator<DirectedPixelEdge>() {
                @Override
                public int compare(final DirectedPixelEdge a, final DirectedPixelEdge b) {
                    if (a.getStartY() != b.getStartY()) {
                        return Integer.compare(a.getStartY(), b.getStartY());
                    }
                    if (a.getStartX() != b.getStartX()) {
                        return Integer.compare(a.getStartX(), b.getStartX());
                    }
                    if (a.getEndY() != b.getEndY()) {
                        return Integer.compare(a.getEndY(), b.getEndY());
                    }
                    return Integer.compare(a.getEndX(), b.getEndX());
                }
            };

    // ------------------------------------------------------------------
    // ThresholdToSelection
    // ------------------------------------------------------------------

    private static List<ImageJBoundaryReferenceResult.ReferenceOutline> analyzeThresholdToSelection(
            final ByteProcessor mask,
            final List<DirectedPixelEdge> expectedEdges,
            final Set<String> expectedUndirected,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic) {
        mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        final ThresholdToSelection converter = new ThresholdToSelection();
        converter.showStatus(false);
        final Roi roi = converter.convert(mask);
        if (roi == null) {
            return Collections.emptyList();
        }

        final List<Roi> rois = new ArrayList<Roi>();
        if (roi instanceof ShapeRoi) {
            final Roi[] sub = ((ShapeRoi) roi).getRois();
            if (sub != null) {
                for (int i = 0; i < sub.length; i++) {
                    if (sub[i] != null) {
                        rois.add(sub[i]);
                    }
                }
            }
        } else {
            rois.add(roi);
        }

        final List<ImageJBoundaryReferenceResult.ReferenceOutline> outlines =
                new ArrayList<ImageJBoundaryReferenceResult.ReferenceOutline>();
        for (int i = 0; i < rois.size(); i++) {
            final PathConversion conversion = convertRoiToPath(rois.get(i));
            if (conversion == null || conversion.vertices.isEmpty()) {
                continue;
            }
            outlines.add(buildOutline(
                    ImageJBoundaryReferenceResult.ReferenceMethod.THRESHOLD_TO_SELECTION,
                    i + 1,
                    conversion,
                    expectedEdges,
                    expectedUndirected,
                    ambiguityDiagnostic));
        }

        sortOutlines(outlines);
        return renumber(outlines);
    }

    // ------------------------------------------------------------------
    // Wand
    // ------------------------------------------------------------------

    private static List<ImageJBoundaryReferenceResult.ReferenceOutline> analyzeWand(
            final ByteProcessor mask,
            final List<Point> selected,
            final int mode,
            final ImageJBoundaryReferenceResult.ReferenceMethod method,
            final List<DirectedPixelEdge> expectedEdges,
            final Set<String> expectedUndirected,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic) {
        final Point seed = chooseSeed(selected);
        final boolean previousAllPoints = Wand.allPoints();
        try {
            Wand.setAllPoints(true);
            final Wand wand = new Wand(mask);
            wand.autoOutline(seed.x, seed.y, 255.0, 255.0, mode);
            if (wand.npoints <= 0) {
                return Collections.emptyList();
            }
            final List<Point> vertices = new ArrayList<Point>(wand.npoints);
            for (int i = 0; i < wand.npoints; i++) {
                vertices.add(new Point(wand.xpoints[i], wand.ypoints[i]));
            }
            final PathConversion conversion = expandClosedLatticePath(vertices, true);
            if (conversion.vertices.isEmpty()) {
                return Collections.emptyList();
            }
            final ImageJBoundaryReferenceResult.ReferenceOutline outline = buildOutline(
                    method,
                    1,
                    conversion,
                    expectedEdges,
                    expectedUndirected,
                    ambiguityDiagnostic);
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> list =
                    new ArrayList<ImageJBoundaryReferenceResult.ReferenceOutline>(1);
            list.add(outline);
            return Collections.unmodifiableList(list);
        } finally {
            Wand.setAllPoints(previousAllPoints);
        }
    }

    private static Point chooseSeed(final List<Point> selected) {
        Point best = selected.get(0);
        for (int i = 1; i < selected.size(); i++) {
            final Point p = selected.get(i);
            if (p.y < best.y || (p.y == best.y && p.x < best.x)) {
                best = p;
            }
        }
        return new Point(best.x, best.y);
    }

    // ------------------------------------------------------------------
    // ROI / path conversion
    // ------------------------------------------------------------------

    static final class PathConversion {
        final List<Point> vertices;
        final List<DirectedPixelEdge> unitEdges;
        final boolean closed;
        final boolean hadUnsupportedSegment;
        final int repeatedNonClosingVertexCount;

        private PathConversion(
                final List<Point> vertices,
                final List<DirectedPixelEdge> unitEdges,
                final boolean closed,
                final boolean hadUnsupportedSegment,
                final int repeatedNonClosingVertexCount) {
            this.vertices = vertices;
            this.unitEdges = unitEdges;
            this.closed = closed;
            this.hadUnsupportedSegment = hadUnsupportedSegment;
            this.repeatedNonClosingVertexCount = repeatedNonClosingVertexCount;
        }
    }

    /**
     * Package-visible expansion hook for unit tests. Does not alter production
     * boundary extraction.
     */
    static PathConversion expandForTest(
            final List<Point> rawVertices,
            final boolean treatAsClosed) {
        return expandClosedLatticePath(rawVertices, treatAsClosed);
    }

    /**
     * Package-visible Wand all-points restore probe for unit tests.
     */
    static void wandAllPointsRestoreProbeThatThrows() {
        final boolean previous = Wand.allPoints();
        try {
            Wand.setAllPoints(true);
            throw new IllegalStateException("forced wand probe failure");
        } finally {
            Wand.setAllPoints(previous);
        }
    }

    private static PathConversion convertRoiToPath(final Roi roi) {
        final FloatCoordSequence coords = extractFloatCoordinates(roi);
        if (coords.count < 2) {
            return null;
        }
        final List<Point> lattice = new ArrayList<Point>(coords.count);
        for (int i = 0; i < coords.count; i++) {
            final double x = coords.x[i];
            final double y = coords.y[i];
            if (Double.isNaN(x) || Double.isNaN(y)
                    || Double.isInfinite(x) || Double.isInfinite(y)) {
                throw new IllegalArgumentException("ROI coordinates must be finite");
            }
            final long rx = Math.round(x);
            final long ry = Math.round(y);
            if (Math.abs(x - rx) > LATTICE_TOLERANCE
                    || Math.abs(y - ry) > LATTICE_TOLERANCE) {
                throw new IllegalArgumentException(
                        "ROI coordinate is not on integer lattice within tolerance: ("
                                + x + "," + y + ")");
            }
            lattice.add(new Point((int) rx, (int) ry));
        }
        return expandClosedLatticePath(lattice, true);
    }

    private static final class FloatCoordSequence {
        private final float[] x;
        private final float[] y;
        private final int count;

        private FloatCoordSequence(final float[] x, final float[] y, final int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }

    private static FloatCoordSequence extractFloatCoordinates(final Roi roi) {
        // Prefer float polygon when available; fall back to integer polygon.
        try {
            final ij.process.FloatPolygon fp = roi.getFloatPolygon();
            if (fp != null && fp.npoints > 0) {
                return new FloatCoordSequence(fp.xpoints, fp.ypoints, fp.npoints);
            }
        } catch (RuntimeException ignored) {
            // Fall through to integer polygon.
        }
        final Polygon poly = roi.getPolygon();
        if (poly == null || poly.npoints <= 0) {
            return new FloatCoordSequence(new float[0], new float[0], 0);
        }
        final float[] xs = new float[poly.npoints];
        final float[] ys = new float[poly.npoints];
        for (int i = 0; i < poly.npoints; i++) {
            xs[i] = poly.xpoints[i];
            ys[i] = poly.ypoints[i];
        }
        return new FloatCoordSequence(xs, ys, poly.npoints);
    }

    /**
     * Expands a closed lattice vertex path into unit edges.
     * Does not append the first vertex merely to show closure in the vertex list.
     */
    static PathConversion expandClosedLatticePath(
            final List<Point> rawVertices,
            final boolean treatAsClosed) {
        if (rawVertices == null || rawVertices.isEmpty()) {
            return new PathConversion(
                    Collections.<Point>emptyList(),
                    Collections.<DirectedPixelEdge>emptyList(),
                    false,
                    false,
                    0);
        }

        // Drop exact trailing duplicate of first vertex if present (closure marker).
        final List<Point> cleaned = new ArrayList<Point>();
        for (int i = 0; i < rawVertices.size(); i++) {
            final Point p = rawVertices.get(i);
            if (cleaned.isEmpty()) {
                cleaned.add(new Point(p.x, p.y));
                continue;
            }
            final Point prev = cleaned.get(cleaned.size() - 1);
            if (prev.x == p.x && prev.y == p.y) {
                // Exact consecutive duplicate: ignore for edge expansion.
                continue;
            }
            cleaned.add(new Point(p.x, p.y));
        }
        if (cleaned.size() >= 2) {
            final Point first = cleaned.get(0);
            final Point last = cleaned.get(cleaned.size() - 1);
            if (first.x == last.x && first.y == last.y) {
                cleaned.remove(cleaned.size() - 1);
            }
        }

        final int repeated = countRepeatedNonClosing(cleaned);
        final List<DirectedPixelEdge> edges = new ArrayList<DirectedPixelEdge>();
        boolean unsupported = false;
        final int n = cleaned.size();
        if (n < 2) {
            return new PathConversion(cleaned, edges, false, false, repeated);
        }
        final int segmentCount = treatAsClosed ? n : n - 1;
        for (int i = 0; i < segmentCount; i++) {
            final Point a = cleaned.get(i);
            final Point b = cleaned.get((i + 1) % n);
            final int dx = b.x - a.x;
            final int dy = b.y - a.y;
            if (dx == 0 && dy == 0) {
                continue;
            }
            if (dx != 0 && dy != 0) {
                unsupported = true;
                continue;
            }
            final int steps = Math.abs(dx) + Math.abs(dy);
            final int sx = Integer.signum(dx);
            final int sy = Integer.signum(dy);
            int cx = a.x;
            int cy = a.y;
            for (int s = 0; s < steps; s++) {
                final int nx = cx + sx;
                final int ny = cy + sy;
                edges.add(new DirectedPixelEdge(cx, cy, nx, ny));
                cx = nx;
                cy = ny;
            }
        }
        final boolean closed = treatAsClosed && !unsupported && edges.size() >= 3;
        return new PathConversion(cleaned, edges, closed, unsupported, repeated);
    }

    private static int countRepeatedNonClosing(final List<Point> vertices) {
        final Map<Long, Integer> counts = new HashMap<Long, Integer>();
        for (int i = 0; i < vertices.size(); i++) {
            final Point p = vertices.get(i);
            final long key = pack(p.x, p.y);
            final Integer prev = counts.get(key);
            counts.put(key, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
        }
        int repeated = 0;
        for (final Integer c : counts.values()) {
            if (c.intValue() > 1) {
                repeated += c.intValue() - 1;
            }
        }
        return repeated;
    }

    // ------------------------------------------------------------------
    // Orientation / outline construction
    // ------------------------------------------------------------------

    private static ImageJBoundaryReferenceResult.ReferenceOutline buildOutline(
            final ImageJBoundaryReferenceResult.ReferenceMethod method,
            final int provisionalNumber,
            final PathConversion conversion,
            final List<DirectedPixelEdge> expectedEdges,
            final Set<String> expectedUndirected,
            final AmbiguousBoundaryVertexDiagnostic ambiguityDiagnostic) {
        List<DirectedPixelEdge> oriented = new ArrayList<DirectedPixelEdge>(conversion.unitEdges);
        List<Point> orientedVertices = new ArrayList<Point>(conversion.vertices);

        if (!oriented.isEmpty()) {
            final List<DirectedPixelEdge> reversed = reverseEdges(oriented);
            final int forwardMatches = countDirectedMatches(oriented, expectedEdges);
            final int reverseMatches = countDirectedMatches(reversed, expectedEdges);
            boolean useReverse = reverseMatches > forwardMatches;
            if (reverseMatches == forwardMatches) {
                final double forwardArea = signedAreaFromEdges(oriented);
                final double reverseArea = signedAreaFromEdges(reversed);
                // Prefer selected-on-right / positive signed area in Y-down image coords.
                if (reverseArea > forwardArea) {
                    useReverse = true;
                } else if (Double.compare(reverseArea, forwardArea) == 0) {
                    useReverse = compareEdgeLists(reversed, oriented) < 0;
                }
            }
            if (useReverse) {
                oriented = reversed;
                orientedVertices = reverseVertices(orientedVertices);
            }
            final int rotation = indexOfSmallestEdge(oriented);
            oriented = rotateEdges(oriented, rotation);
            orientedVertices = verticesFromEdges(oriented);
        }

        final EdgeAccounting accounting = accountEdges(oriented, expectedUndirected);
        final double signed = signedAreaFromEdges(oriented);
        final double abs = Math.abs(signed);
        final Rectangle bounds = boundsOf(orientedVertices);
        final int distinct = countDistinct(orientedVertices);

        final AmbiguityAudit audit = auditAmbiguity(
                oriented, orientedVertices, ambiguityDiagnostic);

        final boolean closed = conversion.closed && !conversion.hadUnsupportedSegment;
        final double coverage = expectedUndirected.isEmpty()
                ? 0.0
                : ((double) accounting.matchCount) / ((double) expectedUndirected.size());

        return new ImageJBoundaryReferenceResult.ReferenceOutline(
                method,
                provisionalNumber,
                orientedVertices,
                oriented,
                closed,
                signed,
                abs,
                conversion.repeatedNonClosingVertexCount,
                distinct,
                bounds,
                accounting.matchCount,
                accounting.missingCount,
                accounting.extraCount,
                coverage,
                audit.containsAmbiguousVertex,
                audit.ambiguousVertexOccurrenceCount,
                audit.matchingIncomingEdgeFound,
                audit.matchingIncomingEdgeOccurrenceCount,
                audit.successorDirection,
                audit.successorTurnRelation);
    }

    private static int countDirectedMatches(
            final List<DirectedPixelEdge> candidate,
            final List<DirectedPixelEdge> expected) {
        final Set<DirectedPixelEdge> expectedSet = new HashSet<DirectedPixelEdge>(expected);
        int matches = 0;
        final Set<DirectedPixelEdge> seen = new HashSet<DirectedPixelEdge>();
        for (int i = 0; i < candidate.size(); i++) {
            final DirectedPixelEdge edge = candidate.get(i);
            if (seen.add(edge) && expectedSet.contains(edge)) {
                matches++;
            }
        }
        return matches;
    }

    private static List<DirectedPixelEdge> reverseEdges(final List<DirectedPixelEdge> edges) {
        final List<DirectedPixelEdge> reversed = new ArrayList<DirectedPixelEdge>(edges.size());
        for (int i = edges.size() - 1; i >= 0; i--) {
            final DirectedPixelEdge e = edges.get(i);
            reversed.add(new DirectedPixelEdge(
                    e.getEndX(), e.getEndY(), e.getStartX(), e.getStartY()));
        }
        return reversed;
    }

    private static List<Point> reverseVertices(final List<Point> vertices) {
        final List<Point> reversed = new ArrayList<Point>(vertices.size());
        for (int i = vertices.size() - 1; i >= 0; i--) {
            final Point p = vertices.get(i);
            reversed.add(new Point(p.x, p.y));
        }
        return reversed;
    }

    private static int indexOfSmallestEdge(final List<DirectedPixelEdge> edges) {
        int best = 0;
        for (int i = 1; i < edges.size(); i++) {
            if (EDGE_ORDER.compare(edges.get(i), edges.get(best)) < 0) {
                best = i;
            }
        }
        return best;
    }

    private static List<DirectedPixelEdge> rotateEdges(
            final List<DirectedPixelEdge> edges,
            final int startIndex) {
        final List<DirectedPixelEdge> rotated = new ArrayList<DirectedPixelEdge>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            rotated.add(edges.get((startIndex + i) % edges.size()));
        }
        return rotated;
    }

    private static List<Point> verticesFromEdges(final List<DirectedPixelEdge> edges) {
        final List<Point> vertices = new ArrayList<Point>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge e = edges.get(i);
            vertices.add(new Point(e.getStartX(), e.getStartY()));
        }
        return vertices;
    }

    private static int compareEdgeLists(
            final List<DirectedPixelEdge> a,
            final List<DirectedPixelEdge> b) {
        final int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            final int cmp = EDGE_ORDER.compare(a.get(i), b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static double signedAreaFromEdges(final List<DirectedPixelEdge> edges) {
        if (edges.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge e = edges.get(i);
            sum += ((double) e.getStartX()) * e.getEndY()
                    - ((double) e.getEndX()) * e.getStartY();
        }
        return sum * 0.5;
    }

    private static final class EdgeAccounting {
        private final int matchCount;
        private final int missingCount;
        private final int extraCount;

        private EdgeAccounting(
                final int matchCount,
                final int missingCount,
                final int extraCount) {
            this.matchCount = matchCount;
            this.missingCount = missingCount;
            this.extraCount = extraCount;
        }
    }

    private static EdgeAccounting accountEdges(
            final List<DirectedPixelEdge> reference,
            final Set<String> expectedUndirected) {
        final Set<String> refUnique = new HashSet<String>();
        for (int i = 0; i < reference.size(); i++) {
            refUnique.add(undirectedKey(reference.get(i)));
        }
        int matches = 0;
        for (final String key : refUnique) {
            if (expectedUndirected.contains(key)) {
                matches++;
            }
        }
        final int missing = expectedUndirected.size() - matches;
        int extra = 0;
        for (final String key : refUnique) {
            if (!expectedUndirected.contains(key)) {
                extra++;
            }
        }
        return new EdgeAccounting(matches, missing, extra);
    }

    private static Rectangle boundsOf(final List<Point> vertices) {
        if (vertices.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }
        int minX = vertices.get(0).x;
        int minY = vertices.get(0).y;
        int maxX = minX;
        int maxY = minY;
        for (int i = 1; i < vertices.size(); i++) {
            final Point p = vertices.get(i);
            if (p.x < minX) {
                minX = p.x;
            }
            if (p.y < minY) {
                minY = p.y;
            }
            if (p.x > maxX) {
                maxX = p.x;
            }
            if (p.y > maxY) {
                maxY = p.y;
            }
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private static int countDistinct(final List<Point> vertices) {
        final Set<Long> keys = new HashSet<Long>();
        for (int i = 0; i < vertices.size(); i++) {
            final Point p = vertices.get(i);
            keys.add(pack(p.x, p.y));
        }
        return keys.size();
    }

    // ------------------------------------------------------------------
    // Ambiguity successor audit
    // ------------------------------------------------------------------

    private static final class AmbiguityAudit {
        private final boolean containsAmbiguousVertex;
        private final int ambiguousVertexOccurrenceCount;
        private final boolean matchingIncomingEdgeFound;
        private final int matchingIncomingEdgeOccurrenceCount;
        private final DirectedPixelEdge.Direction successorDirection;
        private final ImageJBoundaryReferenceResult.ReferenceTurnRelation successorTurnRelation;

        private AmbiguityAudit(
                final boolean containsAmbiguousVertex,
                final int ambiguousVertexOccurrenceCount,
                final boolean matchingIncomingEdgeFound,
                final int matchingIncomingEdgeOccurrenceCount,
                final DirectedPixelEdge.Direction successorDirection,
                final ImageJBoundaryReferenceResult.ReferenceTurnRelation successorTurnRelation) {
            this.containsAmbiguousVertex = containsAmbiguousVertex;
            this.ambiguousVertexOccurrenceCount = ambiguousVertexOccurrenceCount;
            this.matchingIncomingEdgeFound = matchingIncomingEdgeFound;
            this.matchingIncomingEdgeOccurrenceCount = matchingIncomingEdgeOccurrenceCount;
            this.successorDirection = successorDirection;
            this.successorTurnRelation = successorTurnRelation;
        }
    }

    private static AmbiguityAudit auditAmbiguity(
            final List<DirectedPixelEdge> edges,
            final List<Point> vertices,
            final AmbiguousBoundaryVertexDiagnostic diagnostic) {
        final int vx = diagnostic.getVertexX();
        final int vy = diagnostic.getVertexY();
        int vertexOccurrences = 0;
        for (int i = 0; i < vertices.size(); i++) {
            final Point p = vertices.get(i);
            if (p.x == vx && p.y == vy) {
                vertexOccurrences++;
            }
        }

        final DirectedPixelEdge incoming = diagnostic.getIncomingEdge();
        final List<Integer> matchIndexes = new ArrayList<Integer>();
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i).equals(incoming)) {
                matchIndexes.add(Integer.valueOf(i));
            }
        }

        if (matchIndexes.isEmpty()) {
            return new AmbiguityAudit(
                    vertexOccurrences > 0,
                    vertexOccurrences,
                    false,
                    0,
                    null,
                    ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND);
        }
        if (matchIndexes.size() > 1) {
            return new AmbiguityAudit(
                    vertexOccurrences > 0,
                    vertexOccurrences,
                    true,
                    matchIndexes.size(),
                    null,
                    ImageJBoundaryReferenceResult.ReferenceTurnRelation.MULTIPLE_MATCHES);
        }

        final int idx = matchIndexes.get(0).intValue();
        final DirectedPixelEdge successor = edges.get((idx + 1) % edges.size());
        if (successor.getStartX() != vx || successor.getStartY() != vy) {
            return new AmbiguityAudit(
                    vertexOccurrences > 0,
                    vertexOccurrences,
                    true,
                    1,
                    null,
                    ImageJBoundaryReferenceResult.ReferenceTurnRelation.NOT_FOUND);
        }
        final ImageJBoundaryReferenceResult.ReferenceTurnRelation turn =
                classifyTurn(incoming.getDirection(), successor.getDirection());
        return new AmbiguityAudit(
                vertexOccurrences > 0,
                vertexOccurrences,
                true,
                1,
                successor.getDirection(),
                turn);
    }

    static ImageJBoundaryReferenceResult.ReferenceTurnRelation classifyTurn(
            final DirectedPixelEdge.Direction incoming,
            final DirectedPixelEdge.Direction outgoing) {
        final int delta = (outgoing.ordinal() - incoming.ordinal() + 4) % 4;
        if (delta == 0) {
            return ImageJBoundaryReferenceResult.ReferenceTurnRelation.STRAIGHT;
        }
        if (delta == 1) {
            return ImageJBoundaryReferenceResult.ReferenceTurnRelation.RIGHT_TURN;
        }
        if (delta == 2) {
            return ImageJBoundaryReferenceResult.ReferenceTurnRelation.REVERSE;
        }
        return ImageJBoundaryReferenceResult.ReferenceTurnRelation.LEFT_TURN;
    }

    // ------------------------------------------------------------------
    // Sorting / conclusion
    // ------------------------------------------------------------------

    private static void sortOutlines(
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> outlines) {
        Collections.sort(outlines, new Comparator<ImageJBoundaryReferenceResult.ReferenceOutline>() {
            @Override
            public int compare(
                    final ImageJBoundaryReferenceResult.ReferenceOutline a,
                    final ImageJBoundaryReferenceResult.ReferenceOutline b) {
                final int areaCmp = Double.compare(b.getAbsoluteArea(), a.getAbsoluteArea());
                if (areaCmp != 0) {
                    return areaCmp;
                }
                final int edgeCmp = Integer.compare(
                        b.getExpandedUnitEdges().size(),
                        a.getExpandedUnitEdges().size());
                if (edgeCmp != 0) {
                    return edgeCmp;
                }
                if (a.getLatticeBounds().y != b.getLatticeBounds().y) {
                    return Integer.compare(a.getLatticeBounds().y, b.getLatticeBounds().y);
                }
                if (a.getLatticeBounds().x != b.getLatticeBounds().x) {
                    return Integer.compare(a.getLatticeBounds().x, b.getLatticeBounds().x);
                }
                return Integer.compare(a.getOutlineNumber(), b.getOutlineNumber());
            }
        });
    }

    private static List<ImageJBoundaryReferenceResult.ReferenceOutline> renumber(
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> sorted) {
        final List<ImageJBoundaryReferenceResult.ReferenceOutline> out =
                new ArrayList<ImageJBoundaryReferenceResult.ReferenceOutline>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            final ImageJBoundaryReferenceResult.ReferenceOutline src = sorted.get(i);
            out.add(new ImageJBoundaryReferenceResult.ReferenceOutline(
                    src.getMethod(),
                    i + 1,
                    src.getLatticeVertices(),
                    src.getExpandedUnitEdges(),
                    src.isClosed(),
                    src.getSignedArea(),
                    src.getAbsoluteArea(),
                    src.getRepeatedNonClosingVertexCount(),
                    src.getDistinctVertexCount(),
                    src.getLatticeBounds(),
                    src.getExpectedExposedEdgeMatchCount(),
                    src.getMissingExpectedExposedEdgeCount(),
                    src.getExtraReferenceEdgeCount(),
                    src.getExpectedExposedEdgeCoverageFraction(),
                    src.isContainsAmbiguousVertex(),
                    src.getAmbiguousVertexOccurrenceCount(),
                    src.isMatchingIncomingEdgeFound(),
                    src.getMatchingIncomingEdgeOccurrenceCount(),
                    src.getSuccessorDirectionAtAmbiguity(),
                    src.getSuccessorTurnRelationAtAmbiguity()));
        }
        return Collections.unmodifiableList(out);
    }

    private static String decideConclusion(
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> tts,
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> wand4,
            final List<ImageJBoundaryReferenceResult.ReferenceOutline> wand8,
            final int expectedUndirectedCount) {
        final List<ImageJBoundaryReferenceResult.ReferenceOutline> all =
                new ArrayList<ImageJBoundaryReferenceResult.ReferenceOutline>();
        all.addAll(tts);
        all.addAll(wand4);
        all.addAll(wand8);

        boolean anyIncomplete = false;
        for (int i = 0; i < all.size(); i++) {
            final ImageJBoundaryReferenceResult.ReferenceOutline o = all.get(i);
            if (!o.isClosed()
                    || o.getMissingExpectedExposedEdgeCount() > 0
                    || o.getExtraReferenceEdgeCount() > 0) {
                // Incomplete only when unit-edge accounting fails or unsupported.
                if (!o.isClosed()
                        || o.getExtraReferenceEdgeCount() > 0
                        || (o.getExpectedExposedEdgeMatchCount() == 0
                        && expectedUndirectedCount > 0)) {
                    anyIncomplete = true;
                }
            }
        }

        final List<ImageJBoundaryReferenceResult.ReferenceTurnRelation> foundTurns =
                new ArrayList<ImageJBoundaryReferenceResult.ReferenceTurnRelation>();
        for (int i = 0; i < all.size(); i++) {
            final ImageJBoundaryReferenceResult.ReferenceOutline o = all.get(i);
            final ImageJBoundaryReferenceResult.ReferenceTurnRelation turn =
                    o.getSuccessorTurnRelationAtAmbiguity();
            if (turn == ImageJBoundaryReferenceResult.ReferenceTurnRelation.STRAIGHT
                    || turn == ImageJBoundaryReferenceResult.ReferenceTurnRelation.RIGHT_TURN
                    || turn == ImageJBoundaryReferenceResult.ReferenceTurnRelation.LEFT_TURN
                    || turn == ImageJBoundaryReferenceResult.ReferenceTurnRelation.REVERSE) {
                foundTurns.add(turn);
            }
        }

        if (foundTurns.isEmpty()) {
            return ImageJBoundaryReferenceResult.AMBIGUOUS_INCOMING_EDGE_NOT_FOUND;
        }

        boolean disagree = false;
        final ImageJBoundaryReferenceResult.ReferenceTurnRelation first = foundTurns.get(0);
        for (int i = 1; i < foundTurns.size(); i++) {
            if (foundTurns.get(i) != first) {
                disagree = true;
                break;
            }
        }

        // Also treat different topology (multiple outlines vs one, coverage gaps) as diagonal
        // disagreement when methods find the edge but differ.
        if (!disagree && tts.size() > 1 && (wand4.size() == 1 || wand8.size() == 1)) {
            // Multiple TTS subpaths vs single wand outline can indicate diagonal topology split.
            disagree = true;
        }

        if (disagree) {
            return ImageJBoundaryReferenceResult.REFERENCE_METHODS_DISAGREE_AT_DIAGONAL_CONTACT;
        }

        if (anyIncomplete) {
            return ImageJBoundaryReferenceResult.REFERENCE_CONTOUR_INCOMPLETE;
        }

        // All methods that found the edge agree on turn; require they are otherwise complete.
        boolean allComplete = true;
        for (int i = 0; i < all.size(); i++) {
            final ImageJBoundaryReferenceResult.ReferenceOutline o = all.get(i);
            if (o.getSuccessorTurnRelationAtAmbiguity() == first) {
                if (!o.isClosed()
                        || o.getMissingExpectedExposedEdgeCount() != 0
                        || o.getExtraReferenceEdgeCount() != 0) {
                    allComplete = false;
                    break;
                }
            }
        }
        if (allComplete && foundTurns.size() >= 1) {
            // Require every method that produced an outline and found the edge to agree —
            // already checked. Prefer AGREE only when at least one method found it and no
            // disagreement.
            return ImageJBoundaryReferenceResult.REFERENCE_METHODS_AGREE;
        }
        if (!allComplete) {
            return ImageJBoundaryReferenceResult.REFERENCE_CONTOUR_INCOMPLETE;
        }
        return ImageJBoundaryReferenceResult.UNKNOWN;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<Long> toMembership(final List<Point> pixels) {
        final Set<Long> keys = new HashSet<Long>(pixels.size() * 2);
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            keys.add(pack(p.x, p.y));
        }
        return keys;
    }

    static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static byte[] copyBytes(final byte[] source) {
        final byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static boolean sameBytes(final byte[] a, final byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
