package vesselreconstruction.ellipse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic conservative extractor of closed directed pixel-edge boundary loops.
 */
public final class OrderedBoundaryExtractor {

    public List<OrderedBoundaryLoop> extractAllLoops(final List<Point> selectedLumenPixels) {
        final List<Point> pixels = validateAndCopy(selectedLumenPixels);
        final Set<Long> membership = toMembership(pixels);
        final List<DirectedPixelEdge> exposed = buildExposedEdges(pixels, membership);
        return traceLoops(exposed);
    }

    public OrderedBoundaryLoop selectOuterLoop(final List<Point> selectedLumenPixels) {
        final List<OrderedBoundaryLoop> loops = extractAllLoops(selectedLumenPixels);
        if (loops.isEmpty()) {
            throw new IllegalArgumentException("No ordered boundary loops were extracted");
        }
        OrderedBoundaryLoop best = loops.get(0);
        for (int i = 1; i < loops.size(); i++) {
            final OrderedBoundaryLoop candidate = loops.get(i);
            if (isPreferredOuter(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isPreferredOuter(
            final OrderedBoundaryLoop candidate,
            final OrderedBoundaryLoop incumbent) {
        final int areaCmp = Double.compare(candidate.getAbsoluteArea(), incumbent.getAbsoluteArea());
        if (areaCmp != 0) {
            return areaCmp > 0;
        }
        final int edgeCmp = Integer.compare(
                candidate.getDirectedEdges().size(),
                incumbent.getDirectedEdges().size());
        if (edgeCmp != 0) {
            return edgeCmp > 0;
        }
        if (candidate.getLatticeBounds().y != incumbent.getLatticeBounds().y) {
            return candidate.getLatticeBounds().y < incumbent.getLatticeBounds().y;
        }
        if (candidate.getLatticeBounds().x != incumbent.getLatticeBounds().x) {
            return candidate.getLatticeBounds().x < incumbent.getLatticeBounds().x;
        }
        return candidate.getLoopNumber() < incumbent.getLoopNumber();
    }

    private static List<Point> validateAndCopy(final List<Point> selectedLumenPixels) {
        if (selectedLumenPixels == null) {
            throw new IllegalArgumentException("selectedLumenPixels must not be null");
        }
        if (selectedLumenPixels.isEmpty()) {
            throw new IllegalArgumentException("selectedLumenPixels must not be empty");
        }
        final List<Point> copy = new ArrayList<Point>(selectedLumenPixels.size());
        final Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i < selectedLumenPixels.size(); i++) {
            final Point p = selectedLumenPixels.get(i);
            if (p == null) {
                throw new IllegalArgumentException(
                        "selectedLumenPixels must not contain null entries");
            }
            if (p.x < 0 || p.y < 0) {
                throw new IllegalArgumentException(
                        "Pixel coordinates must be non-negative: (" + p.x + ", " + p.y + ")");
            }
            final long key = pack(p.x, p.y);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate coordinate pair is not allowed: (" + p.x + ", " + p.y + ")");
            }
            copy.add(new Point(p.x, p.y));
        }
        return copy;
    }

    private static Set<Long> toMembership(final List<Point> pixels) {
        final Set<Long> keys = new HashSet<Long>(pixels.size() * 2);
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            keys.add(pack(p.x, p.y));
        }
        return keys;
    }

    private static List<DirectedPixelEdge> buildExposedEdges(
            final List<Point> pixels,
            final Set<Long> membership) {
        final List<DirectedPixelEdge> edges = new ArrayList<DirectedPixelEdge>();
        for (int i = 0; i < pixels.size(); i++) {
            final Point p = pixels.get(i);
            final int x = p.x;
            final int y = p.y;

            // NORTH exposed -> EAST-directed edge
            if (!membership.contains(pack(x, y - 1))) {
                edges.add(new DirectedPixelEdge(x, y, x + 1, y));
            }
            // EAST exposed -> SOUTH-directed edge
            if (!membership.contains(pack(x + 1, y))) {
                edges.add(new DirectedPixelEdge(x + 1, y, x + 1, y + 1));
            }
            // SOUTH exposed -> WEST-directed edge
            if (!membership.contains(pack(x, y + 1))) {
                edges.add(new DirectedPixelEdge(x + 1, y + 1, x, y + 1));
            }
            // WEST exposed -> NORTH-directed edge
            if (!membership.contains(pack(x - 1, y))) {
                edges.add(new DirectedPixelEdge(x, y + 1, x, y));
            }
        }

        Collections.sort(edges, new Comparator<DirectedPixelEdge>() {
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
        });
        return edges;
    }

    private static List<OrderedBoundaryLoop> traceLoops(final List<DirectedPixelEdge> exposed) {
        final int totalExposed = exposed.size();
        final Set<DirectedPixelEdge> unused = new HashSet<DirectedPixelEdge>(exposed);
        final Map<Long, List<DirectedPixelEdge>> outgoing =
                buildOutgoingLookup(exposed);

        final List<OrderedBoundaryLoop> loops = new ArrayList<OrderedBoundaryLoop>();
        int loopNumber = 1;
        int accounted = 0;

        while (!unused.isEmpty()) {
            DirectedPixelEdge start = null;
            for (int i = 0; i < exposed.size(); i++) {
                final DirectedPixelEdge candidate = exposed.get(i);
                if (unused.contains(candidate)) {
                    start = candidate;
                    break;
                }
            }
            if (start == null) {
                break;
            }

            final List<DirectedPixelEdge> loopEdges = new ArrayList<DirectedPixelEdge>();
            DirectedPixelEdge current = start;
            int steps = 0;
            while (true) {
                if (steps > totalExposed + 1) {
                    throw new IllegalArgumentException("BOUNDARY_TRAVERSAL_LIMIT_EXCEEDED");
                }
                if (!unused.remove(current)) {
                    throw new IllegalArgumentException("DIRECTED_EDGE_REUSED");
                }
                loopEdges.add(current);
                steps++;

                final int endX = current.getEndX();
                final int endY = current.getEndY();
                if (endX == start.getStartX() && endY == start.getStartY()) {
                    // Closed when returning to the starting vertex.
                    // For a valid simple loop, no unused outgoing edge should remain
                    // from this vertex that belongs to this incomplete path; closure
                    // is accepted here and validated by OrderedBoundaryLoop.
                    break;
                }

                final List<DirectedPixelEdge> options = unusedOutgoing(outgoing, unused, endX, endY);
                if (options.isEmpty()) {
                    throw new IllegalArgumentException("OPEN_BOUNDARY_LOOP");
                }
                if (options.size() > 1) {
                    throw new IllegalArgumentException("AMBIGUOUS_BOUNDARY_VERTEX");
                }
                current = options.get(0);
            }

            loops.add(new OrderedBoundaryLoop(loopNumber, loopEdges));
            accounted += loopEdges.size();
            loopNumber++;
        }

        if (accounted != totalExposed) {
            throw new IllegalArgumentException("EXPOSED_EDGE_ACCOUNTING_MISMATCH");
        }
        return Collections.unmodifiableList(loops);
    }

    private static Map<Long, List<DirectedPixelEdge>> buildOutgoingLookup(
            final List<DirectedPixelEdge> edges) {
        final Map<Long, List<DirectedPixelEdge>> map =
                new HashMap<Long, List<DirectedPixelEdge>>();
        for (int i = 0; i < edges.size(); i++) {
            final DirectedPixelEdge edge = edges.get(i);
            final long key = pack(edge.getStartX(), edge.getStartY());
            List<DirectedPixelEdge> list = map.get(key);
            if (list == null) {
                list = new ArrayList<DirectedPixelEdge>();
                map.put(key, list);
            }
            list.add(edge);
        }
        return map;
    }

    private static List<DirectedPixelEdge> unusedOutgoing(
            final Map<Long, List<DirectedPixelEdge>> outgoing,
            final Set<DirectedPixelEdge> unused,
            final int x,
            final int y) {
        final List<DirectedPixelEdge> all = outgoing.get(pack(x, y));
        final List<DirectedPixelEdge> options = new ArrayList<DirectedPixelEdge>();
        if (all == null) {
            return options;
        }
        for (int i = 0; i < all.size(); i++) {
            final DirectedPixelEdge edge = all.get(i);
            if (unused.contains(edge)) {
                options.add(edge);
            }
        }
        return options;
    }

    private static long pack(final int x, final int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
