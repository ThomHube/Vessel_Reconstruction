package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiledWekaClassificationConfigurationTest {

    @Test
    void validConfigurationIsAccepted() {
        final TiledWekaClassificationConfiguration configuration =
                new TiledWekaClassificationConfiguration(
                        2048, 2048, 1, false, 16, 8, 32, true);
        assertEquals(2048, configuration.getCoreTileWidthPixels());
        assertEquals(2048, configuration.getCoreTileHeightPixels());
        assertEquals(1, configuration.getThreadsPerTile());
        assertFalse(configuration.isRunUntiledReference());
        assertEquals(16, configuration.getSeamAuditHalfWidthPixels());
        assertEquals(8, configuration.getSampleMaskDownsampleFactor());
        assertEquals(32, configuration.getSampleMaskExpansionMarginOriginalPixels());
        assertTrue(configuration.isReviewAndEditSampleMask());
    }

    @Test
    void invalidCoreAndMaskSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        0, 2048, 1, false, 16, 8, 32, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        2048, 0, 1, false, 16, 8, 32, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        2048, 2048, 0, false, 16, 8, 32, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        2048, 2048, 1, false, -1, 8, 32, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        2048, 2048, 1, false, 16, 0, 32, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TiledWekaClassificationConfiguration(
                        2048, 2048, 1, false, 16, 8, -1, true));
    }

    @Test
    void createLayoutDerivesProofOfConceptAndLargeGrids() {
        final TiledWekaClassificationConfiguration configuration =
                new TiledWekaClassificationConfiguration(
                        2048, 2048, 1, false, 16, 8, 32, true);
        final CoreTileLayout small = configuration.createLayout(6672, 1650, 16);
        assertEquals(4, small.getTilesX());
        assertEquals(1, small.getTilesY());
        assertEquals(4L, small.getTotalTiles());

        final CoreTileLayout large = configuration.createLayout(9395, 35935, 16);
        assertEquals(5, large.getTilesX());
        assertEquals(18, large.getTilesY());
        assertEquals(90L, large.getTotalTiles());
    }
}
