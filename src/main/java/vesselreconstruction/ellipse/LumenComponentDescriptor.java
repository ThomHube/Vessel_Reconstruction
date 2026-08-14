package vesselreconstruction.ellipse;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable descriptor of one 8-connected Class 3 Lumen component.
 *
 * <p>Persistent membership is a packed {@code int[]} of linear indices
 * {@code y * imageWidth + x}, sorted ascending. {@link #getPixels()}
 * materializes a defensive {@link Point} list on demand.</p>
 */
public final class LumenComponentDescriptor {

    private final int componentId;
    private final int[] pixelIndices;
    private final int area;
    private final Rectangle bounds;
    private final boolean touchesImageBorder;
    private final int imageWidth;
    private final int imageHeight;

    public LumenComponentDescriptor(
            final int componentId,
            final List<Point> pixels,
            final int imageWidth,
            final int imageHeight) {
        this(componentId, packPoints(pixels, imageWidth, imageHeight), imageWidth, imageHeight);
    }

    static LumenComponentDescriptor fromPackedIndices(
            final int componentId,
            final int[] pixelIndices,
            final int imageWidth,
            final int imageHeight) {
        return new LumenComponentDescriptor(componentId, pixelIndices, imageWidth, imageHeight);
    }

    private LumenComponentDescriptor(
            final int componentId,
            final int[] packedIndices,
            final int imageWidth,
            final int imageHeight) {
        if (componentId <= 0) {
            throw new IllegalArgumentException("componentId must be positive: " + componentId);
        }
        validateDimensions(imageWidth, imageHeight);
        if (packedIndices == null || packedIndices.length == 0) {
            throw new IllegalArgumentException("pixels must not be null or empty");
        }

        final int pixelCount = imageWidth * imageHeight;
        final int[] indices = Arrays.copyOf(packedIndices, packedIndices.length);
        Arrays.sort(indices);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean touchesBorder = false;
        for (int i = 0; i < indices.length; i++) {
            final int index = indices[i];
            if (index < 0 || index >= pixelCount) {
                throw new IllegalArgumentException(
                        "pixel out of bounds: index " + index + " for "
                                + imageWidth + "x" + imageHeight);
            }
            if (i > 0 && index == indices[i - 1]) {
                final int x = index % imageWidth;
                final int y = index / imageWidth;
                throw new IllegalArgumentException(
                        "duplicate pixel coordinate: (" + x + "," + y + ")");
            }
            final int x = index % imageWidth;
            final int y = index / imageWidth;
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (x == 0 || y == 0 || x == imageWidth - 1 || y == imageHeight - 1) {
                touchesBorder = true;
            }
        }

        this.componentId = componentId;
        this.pixelIndices = indices;
        this.area = indices.length;
        this.bounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        this.touchesImageBorder = touchesBorder;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public int getComponentId() {
        return componentId;
    }

    public List<Point> getPixels() {
        final List<Point> copy = new ArrayList<Point>(pixelIndices.length);
        for (int i = 0; i < pixelIndices.length; i++) {
            final int index = pixelIndices[i];
            copy.add(new Point(index % imageWidth, index / imageWidth));
        }
        return copy;
    }

    public int getArea() {
        return area;
    }

    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    public boolean isTouchesImageBorder() {
        return touchesImageBorder;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LumenComponentDescriptor)) {
            return false;
        }
        final LumenComponentDescriptor other = (LumenComponentDescriptor) obj;
        return componentId == other.componentId
                && area == other.area
                && touchesImageBorder == other.touchesImageBorder
                && imageWidth == other.imageWidth
                && imageHeight == other.imageHeight
                && bounds.equals(other.bounds)
                && Arrays.equals(pixelIndices, other.pixelIndices);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                Integer.valueOf(componentId),
                Integer.valueOf(area),
                Boolean.valueOf(touchesImageBorder),
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight),
                bounds);
        for (int i = 0; i < pixelIndices.length; i++) {
            final int index = pixelIndices[i];
            result = 31 * result + (index % imageWidth);
            result = 31 * result + (index / imageWidth);
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "LumenComponentDescriptor{id=%d,area=%d,bounds=%s,touchesBorder=%s,%dx%d}",
                Integer.valueOf(componentId),
                Integer.valueOf(area),
                bounds,
                Boolean.valueOf(touchesImageBorder),
                Integer.valueOf(imageWidth),
                Integer.valueOf(imageHeight));
    }

    private static int[] packPoints(
            final List<Point> pixels,
            final int imageWidth,
            final int imageHeight) {
        validateDimensions(imageWidth, imageHeight);
        if (pixels == null || pixels.isEmpty()) {
            throw new IllegalArgumentException("pixels must not be null or empty");
        }
        final int[] indices = new int[pixels.size()];
        for (int i = 0; i < pixels.size(); i++) {
            final Point raw = pixels.get(i);
            if (raw == null) {
                throw new IllegalArgumentException("pixels must not contain null entries");
            }
            final int x = raw.x;
            final int y = raw.y;
            if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
                throw new IllegalArgumentException(
                        "pixel out of bounds: (" + x + "," + y + ") for "
                                + imageWidth + "x" + imageHeight);
            }
            indices[i] = y * imageWidth + x;
        }
        return indices;
    }

    private static void validateDimensions(final int imageWidth, final int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + imageWidth + "x" + imageHeight);
        }
        final long pixelCount = ((long) imageWidth) * ((long) imageHeight);
        if (pixelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "image pixel count exceeds Integer.MAX_VALUE: "
                            + imageWidth + "x" + imageHeight);
        }
    }
}
