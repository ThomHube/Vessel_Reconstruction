package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable Phase-3 core tile size candidate, expressed as {@code WIDTHxHEIGHT}.
 */
public final class BatchTileProfile {

    public static final int MINIMUM_DIMENSION_PX = 256;

    private final int widthPx;
    private final int heightPx;

    public BatchTileProfile(final int widthPx, final int heightPx) {
        if (widthPx < MINIMUM_DIMENSION_PX || heightPx < MINIMUM_DIMENSION_PX) {
            throw new IllegalArgumentException(
                    "tile profile width and height must both be >= " + MINIMUM_DIMENSION_PX
                            + ": " + widthPx + "x" + heightPx);
        }
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public static BatchTileProfile parseToken(final String token) {
        if (token == null) {
            throw new IllegalArgumentException("tile profile token must not be null");
        }
        final String trimmed = token.trim();
        final int sep = indexOfX(trimmed);
        if (sep <= 0 || sep >= trimmed.length() - 1) {
            throw new IllegalArgumentException(
                    "tile profile token must be WIDTHxHEIGHT: " + token);
        }
        final String widthText = trimmed.substring(0, sep);
        final String heightText = trimmed.substring(sep + 1);
        final int width;
        final int height;
        try {
            width = Integer.parseInt(widthText);
            height = Integer.parseInt(heightText);
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "tile profile token must be WIDTHxHEIGHT with integer dimensions: " + token, ex);
        }
        return new BatchTileProfile(width, height);
    }

    private static int indexOfX(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch == 'x' || ch == 'X') {
                return i;
            }
        }
        return -1;
    }

    public int widthPx() {
        return widthPx;
    }

    public int heightPx() {
        return heightPx;
    }

    public String toToken() {
        return widthPx + "x" + heightPx;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BatchTileProfile)) {
            return false;
        }
        final BatchTileProfile other = (BatchTileProfile) obj;
        return widthPx == other.widthPx && heightPx == other.heightPx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(widthPx), Integer.valueOf(heightPx));
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "BatchTileProfile{%s}", toToken());
    }
}
