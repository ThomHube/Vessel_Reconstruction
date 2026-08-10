package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable six-digit image ID {@code PPSSRR}.
 *
 * <p>PP = plot 01|02, SS = tree 01..20, RR = replicate 01|02.</p>
 */
public final class SixDigitSampleId implements Comparable<SixDigitSampleId> {

    private final String imageId;
    private final int plotNumber;
    private final int treeNumber;
    private final int replicateNumber;

    private SixDigitSampleId(
            final String imageId,
            final int plotNumber,
            final int treeNumber,
            final int replicateNumber) {
        this.imageId = imageId;
        this.plotNumber = plotNumber;
        this.treeNumber = treeNumber;
        this.replicateNumber = replicateNumber;
    }

    public static SixDigitSampleId parse(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("image ID must not be null");
        }
        final String trimmed = text.trim();
        if (trimmed.length() != 6) {
            throw new IllegalArgumentException(
                    "image ID must be exactly six digits: " + text);
        }
        for (int i = 0; i < 6; i++) {
            final char ch = trimmed.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException(
                        "image ID must contain digits only: " + text);
            }
        }
        final int plot = Integer.parseInt(trimmed.substring(0, 2));
        final int tree = Integer.parseInt(trimmed.substring(2, 4));
        final int replicate = Integer.parseInt(trimmed.substring(4, 6));
        if (plot != 1 && plot != 2) {
            throw new IllegalArgumentException(
                    "plot number must be 01 or 02: " + text);
        }
        if (tree < 1 || tree > 20) {
            throw new IllegalArgumentException(
                    "tree number must be 01 through 20: " + text);
        }
        if (replicate != 1 && replicate != 2) {
            throw new IllegalArgumentException(
                    "replicate number must be 01 or 02: " + text);
        }
        return new SixDigitSampleId(trimmed, plot, tree, replicate);
    }

    public static List<SixDigitSampleId> expectedCompleteSet() {
        final List<SixDigitSampleId> ids = new ArrayList<SixDigitSampleId>(80);
        for (int plot = 1; plot <= 2; plot++) {
            for (int tree = 1; tree <= 20; tree++) {
                for (int replicate = 1; replicate <= 2; replicate++) {
                    ids.add(parse(String.format(Locale.US, "%02d%02d%02d",
                            Integer.valueOf(plot),
                            Integer.valueOf(tree),
                            Integer.valueOf(replicate))));
                }
            }
        }
        return Collections.unmodifiableList(ids);
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

    public int replicateNumber() {
        return replicateNumber;
    }

    public String plotId() {
        return String.format(Locale.US, "%02d", Integer.valueOf(plotNumber));
    }

    public String treeId() {
        return String.format(Locale.US, "%02d", Integer.valueOf(treeNumber));
    }

    public String replicateId() {
        return String.format(Locale.US, "%02d", Integer.valueOf(replicateNumber));
    }

    @Override
    public int compareTo(final SixDigitSampleId other) {
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
        return Integer.compare(replicateNumber, other.replicateNumber);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SixDigitSampleId)) {
            return false;
        }
        return imageId.equals(((SixDigitSampleId) obj).imageId);
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
