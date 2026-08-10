package vesselreconstruction.ellipse;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable geometry for one core classification tile, including the expanded
 * crop that would be sent to Weka when a classification halo is applied.
 *
 * <p>The official Trainable Weka ROI classifier applies its own halo based on
 * {@code maximumSigma} and returns only the core region. This geometry object
 * records both the core rectangle and the clipped expanded crop used for
 * logging and OutOfMemoryError diagnostics.</p>
 */
public final class CoreTileGeometry {

    private final int tileIndex;
    private final int tileXIndex;
    private final int tileYIndex;
    private final int coreX;
    private final int coreY;
    private final int coreWidth;
    private final int coreHeight;
    private final int cropX;
    private final int cropY;
    private final int cropWidth;
    private final int cropHeight;
    private final int sourceCoreX;
    private final int sourceCoreY;

    public CoreTileGeometry(
            final int tileIndex,
            final int tileXIndex,
            final int tileYIndex,
            final int coreX,
            final int coreY,
            final int coreWidth,
            final int coreHeight,
            final int cropX,
            final int cropY,
            final int cropWidth,
            final int cropHeight,
            final int sourceCoreX,
            final int sourceCoreY) {
        if (tileIndex < 1) {
            throw new IllegalArgumentException("tileIndex must be >= 1: " + tileIndex);
        }
        if (tileXIndex < 0 || tileYIndex < 0) {
            throw new IllegalArgumentException("tile indices must be >= 0");
        }
        if (coreX < 0 || coreY < 0 || coreWidth <= 0 || coreHeight <= 0) {
            throw new IllegalArgumentException("invalid core rectangle");
        }
        if (cropX < 0 || cropY < 0 || cropWidth <= 0 || cropHeight <= 0) {
            throw new IllegalArgumentException("invalid crop rectangle");
        }
        if (sourceCoreX < 0 || sourceCoreY < 0) {
            throw new IllegalArgumentException("sourceCore offsets must be >= 0");
        }
        if (sourceCoreX + coreWidth > cropWidth || sourceCoreY + coreHeight > cropHeight) {
            throw new IllegalArgumentException(
                    "core does not fit inside expanded crop");
        }
        this.tileIndex = tileIndex;
        this.tileXIndex = tileXIndex;
        this.tileYIndex = tileYIndex;
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreWidth = coreWidth;
        this.coreHeight = coreHeight;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.sourceCoreX = sourceCoreX;
        this.sourceCoreY = sourceCoreY;
    }

    public int getTileIndex() {
        return tileIndex;
    }

    public int getTileXIndex() {
        return tileXIndex;
    }

    public int getTileYIndex() {
        return tileYIndex;
    }

    public int getCoreX() {
        return coreX;
    }

    public int getCoreY() {
        return coreY;
    }

    public int getCoreWidth() {
        return coreWidth;
    }

    public int getCoreHeight() {
        return coreHeight;
    }

    public int getCropX() {
        return cropX;
    }

    public int getCropY() {
        return cropY;
    }

    public int getCropWidth() {
        return cropWidth;
    }

    public int getCropHeight() {
        return cropHeight;
    }

    public int getSourceCoreX() {
        return sourceCoreX;
    }

    public int getSourceCoreY() {
        return sourceCoreY;
    }

    public int[] getCoreOrigin() {
        return new int[] { coreX, coreY };
    }

    public int[] getCoreDimensions() {
        return new int[] { coreWidth, coreHeight };
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CoreTileGeometry)) {
            return false;
        }
        final CoreTileGeometry other = (CoreTileGeometry) obj;
        return tileIndex == other.tileIndex
                && tileXIndex == other.tileXIndex
                && tileYIndex == other.tileYIndex
                && coreX == other.coreX
                && coreY == other.coreY
                && coreWidth == other.coreWidth
                && coreHeight == other.coreHeight
                && cropX == other.cropX
                && cropY == other.cropY
                && cropWidth == other.cropWidth
                && cropHeight == other.cropHeight
                && sourceCoreX == other.sourceCoreX
                && sourceCoreY == other.sourceCoreY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(tileIndex),
                Integer.valueOf(tileXIndex),
                Integer.valueOf(tileYIndex),
                Integer.valueOf(coreX),
                Integer.valueOf(coreY),
                Integer.valueOf(coreWidth),
                Integer.valueOf(coreHeight),
                Integer.valueOf(cropX),
                Integer.valueOf(cropY),
                Integer.valueOf(cropWidth),
                Integer.valueOf(cropHeight),
                Integer.valueOf(sourceCoreX),
                Integer.valueOf(sourceCoreY));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "Tile %d [%d,%d] core=(%d,%d,%d,%d), crop=(%d,%d,%d,%d)",
                Integer.valueOf(tileIndex),
                Integer.valueOf(tileXIndex),
                Integer.valueOf(tileYIndex),
                Integer.valueOf(coreX),
                Integer.valueOf(coreY),
                Integer.valueOf(coreWidth),
                Integer.valueOf(coreHeight),
                Integer.valueOf(cropX),
                Integer.valueOf(cropY),
                Integer.valueOf(cropWidth),
                Integer.valueOf(cropHeight));
    }
}
