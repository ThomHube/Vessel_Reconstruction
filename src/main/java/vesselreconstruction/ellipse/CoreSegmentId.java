package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable production image identity {@code PPTTCS}.
 *
 * <p>PP = plot digits (not {@code 00}), TT = tree digits (not {@code 00}),
 * C = physical core label {@code A}|{@code B}, S = segment order {@code 1}–{@code 5}.</p>
 *
 * <p>Grammar: {@code ^[0-9]{4}[AB][1-5]$} with plot/tree ≠ 00.</p>
 */
public final class CoreSegmentId implements Comparable<CoreSegmentId> {

    private final String imageId;
    private final int plotNumber;
    private final int treeNumber;
    private final char coreLabel;
    private final int segmentOrder;

    private CoreSegmentId(
            final String imageId,
            final int plotNumber,
            final int treeNumber,
            final char coreLabel,
            final int segmentOrder) {
        this.imageId = imageId;
        this.plotNumber = plotNumber;
        this.treeNumber = treeNumber;
        this.coreLabel = coreLabel;
        this.segmentOrder = segmentOrder;
    }

    /**
     * Parses a production image ID.
     *
     * @param text raw ID text
     * @return parsed identity
     * @throws IllegalArgumentException if the text is not a valid {@code PPTTCS} ID
     */
    public static CoreSegmentId parse(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("image ID must not be null");
        }
        // Exact match only: no trim, no case folding, no silent rewrite.
        if (text.length() != 6) {
            throw new IllegalArgumentException(
                    "image ID must be exactly six characters (PPTTCS): " + text);
        }
        for (int i = 0; i < 4; i++) {
            final char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException(
                        "image ID plot/tree must be decimal digits: " + text);
            }
        }
        final char core = text.charAt(4);
        if (core != 'A' && core != 'B') {
            throw new IllegalArgumentException(
                    "image ID core label must be A or B: " + text);
        }
        final char segmentChar = text.charAt(5);
        if (segmentChar < '1' || segmentChar > '5') {
            throw new IllegalArgumentException(
                    "image ID segment order must be 1 through 5: " + text);
        }
        final int plot = Integer.parseInt(text.substring(0, 2));
        final int tree = Integer.parseInt(text.substring(2, 4));
        if (plot < 1) {
            throw new IllegalArgumentException(
                    "plot number must not be 00: " + text);
        }
        if (tree < 1) {
            throw new IllegalArgumentException(
                    "tree number must not be 00: " + text);
        }
        final int segmentOrder = segmentChar - '0';
        return new CoreSegmentId(text, plot, tree, core, segmentOrder);
    }

    public String imageId() {
        return imageId;
    }

    public int plotNumber() {
        return plotNumber;
    }

    public int treeNumber() {
        return treeNumber;
    }

    public String plotId() {
        return String.format(Locale.US, "%02d", Integer.valueOf(plotNumber));
    }

    public String treeId() {
        return String.format(Locale.US, "%02d", Integer.valueOf(treeNumber));
    }

    public char coreLabel() {
        return coreLabel;
    }

    /** Physical core identity: first four characters + A/B (e.g. {@code 0101A}). */
    public String coreId() {
        return imageId.substring(0, 5);
    }

    /** Sample identity equals physical core identity. */
    public String sampleId() {
        return coreId();
    }

    /** Segment identity equals the full image ID. */
    public String segmentId() {
        return imageId;
    }

    public int segmentOrder() {
        return segmentOrder;
    }

    @Override
    public int compareTo(final CoreSegmentId other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        int c = Integer.compare(plotNumber, other.plotNumber);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(treeNumber, other.treeNumber);
        if (c != 0) {
            return c;
        }
        c = Character.compare(coreLabel, other.coreLabel);
        if (c != 0) {
            return c;
        }
        return Integer.compare(segmentOrder, other.segmentOrder);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CoreSegmentId)) {
            return false;
        }
        return imageId.equals(((CoreSegmentId) obj).imageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageId);
    }

    @Override
    public String toString() {
        return imageId;
    }
}
