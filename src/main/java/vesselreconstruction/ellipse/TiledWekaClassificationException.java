package vesselreconstruction.ellipse;

/**
 * Runtime failure during sequential tiled Weka classification.
 */
public final class TiledWekaClassificationException extends RuntimeException {

    private final CoreTileGeometry failedTile;

    public TiledWekaClassificationException(
            final String message,
            final Throwable cause,
            final CoreTileGeometry failedTile) {
        super(message, cause);
        this.failedTile = failedTile;
    }

    public CoreTileGeometry getFailedTile() {
        return failedTile;
    }
}
