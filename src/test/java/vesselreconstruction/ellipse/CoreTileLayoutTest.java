package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTileLayoutTest {

    @Test
    void largeFailureImageGeometryIsCorrect() {
        final CoreTileLayout layout = CoreTileLayout.create(9395, 35935, 4096, 4096, 16);
        assertEquals(3, layout.getTilesX());
        assertEquals(9, layout.getTilesY());
        assertEquals(27L, layout.getTotalTiles());
        assertEquals(1203, 9395 - 2 * 4096);
        assertEquals(3167, 35935 - 8 * 4096);

        final CoreTileGeometry bottomRight = layout.getTiles().get(layout.getTiles().size() - 1);
        assertEquals(2, bottomRight.getTileXIndex());
        assertEquals(8, bottomRight.getTileYIndex());
        assertEquals(8192, bottomRight.getCoreX());
        assertEquals(32768, bottomRight.getCoreY());
        assertEquals(1203, bottomRight.getCoreWidth());
        assertEquals(3167, bottomRight.getCoreHeight());
        layout.verifyExactCoverage();
    }

    @Test
    void exactMultipleUsesUniformCoreTiles() {
        final CoreTileLayout layout = CoreTileLayout.create(8192, 12288, 4096, 4096, 0);
        assertEquals(2, layout.getTilesX());
        assertEquals(3, layout.getTilesY());
        assertEquals(6L, layout.getTotalTiles());
        for (final CoreTileGeometry tile : layout.getTiles()) {
            assertEquals(4096, tile.getCoreWidth());
            assertEquals(4096, tile.getCoreHeight());
        }
        layout.verifyExactCoverage();
    }

    @Test
    void imageSmallerThanOneTileProducesSingleCore() {
        final CoreTileLayout layout = CoreTileLayout.create(1000, 800, 4096, 4096, 32);
        assertEquals(1, layout.getTilesX());
        assertEquals(1, layout.getTilesY());
        assertEquals(1L, layout.getTotalTiles());
        final CoreTileGeometry only = layout.getTiles().get(0);
        assertEquals(0, only.getCoreX());
        assertEquals(0, only.getCoreY());
        assertEquals(1000, only.getCoreWidth());
        assertEquals(800, only.getCoreHeight());
        assertEquals(0, layout.getVerticalSeams().length);
        assertEquals(0, layout.getHorizontalSeams().length);
        layout.verifyExactCoverage();
    }

    @Test
    void oneExactAndOnePartialDimension() {
        final CoreTileLayout layout = CoreTileLayout.create(8192, 9000, 4096, 4096, 0);
        assertEquals(2, layout.getTilesX());
        assertEquals(3, layout.getTilesY());
        assertEquals(808, 9000 - 2 * 4096);
        for (final CoreTileGeometry tile : layout.getTiles()) {
            if (tile.getTileYIndex() == 2) {
                assertEquals(808, tile.getCoreHeight());
            } else {
                assertEquals(4096, tile.getCoreHeight());
            }
            assertEquals(4096, tile.getCoreWidth());
        }
        layout.verifyExactCoverage();
    }

    @Test
    void invalidCoreDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CoreTileLayout.create(100, 100, 0, 4096, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CoreTileLayout.create(100, 100, 4096, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CoreTileLayout.create(100, 100, -1, 4096, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CoreTileLayout.create(100, 100, 4096, -5, 0));
    }

    @Test
    void fullCoverageHoldsForSeveralDimensions() {
        final int[][] cases = new int[][] {
                {9395, 35935, 4096, 4096},
                {8192, 12288, 4096, 4096},
                {1000, 800, 4096, 4096},
                {8192, 9000, 4096, 4096},
                {17, 23, 5, 7},
                {1, 1, 1, 1},
                {5000, 100, 1024, 64}
        };
        for (int i = 0; i < cases.length; i++) {
            final CoreTileLayout layout = CoreTileLayout.create(
                    cases[i][0], cases[i][1], cases[i][2], cases[i][3], 8);
            layout.verifyExactCoverage();
        }
    }

    @Test
    void haloClippingIsCorrectForCornerAndEdgeTiles() {
        final CoreTileLayout layout = CoreTileLayout.create(9395, 35935, 4096, 4096, 48);
        final List<CoreTileGeometry> tiles = layout.getTiles();

        final CoreTileGeometry topLeft = tiles.get(0);
        assertEquals(0, topLeft.getCropX());
        assertEquals(0, topLeft.getCropY());
        assertEquals(0, topLeft.getSourceCoreX());
        assertEquals(0, topLeft.getSourceCoreY());
        assertTrue(topLeft.getCropWidth() <= 4096 + 48);
        assertTrue(topLeft.getSourceCoreX() + topLeft.getCoreWidth() <= topLeft.getCropWidth());
        assertTrue(topLeft.getSourceCoreY() + topLeft.getCoreHeight() <= topLeft.getCropHeight());

        CoreTileGeometry interior = null;
        for (final CoreTileGeometry tile : tiles) {
            if (tile.getTileXIndex() == 1 && tile.getTileYIndex() == 1) {
                interior = tile;
                break;
            }
        }
        assertTrue(interior != null);
        assertEquals(4096 - 48, interior.getCropX());
        assertEquals(4096 - 48, interior.getCropY());
        assertEquals(48, interior.getSourceCoreX());
        assertEquals(48, interior.getSourceCoreY());
        assertEquals(4096 + 96, interior.getCropWidth());
        assertEquals(4096 + 96, interior.getCropHeight());

        CoreTileGeometry rightEdge = null;
        for (final CoreTileGeometry tile : tiles) {
            if (tile.getTileXIndex() == 2 && tile.getTileYIndex() == 0) {
                rightEdge = tile;
                break;
            }
        }
        assertTrue(rightEdge != null);
        assertEquals(9395, rightEdge.getCropX() + rightEdge.getCropWidth());
        assertEquals(rightEdge.getCoreX() - rightEdge.getCropX(), rightEdge.getSourceCoreX());
        assertTrue(rightEdge.getSourceCoreX() + rightEdge.getCoreWidth()
                <= rightEdge.getCropWidth());

        CoreTileGeometry bottomEdge = null;
        for (final CoreTileGeometry tile : tiles) {
            if (tile.getTileXIndex() == 0 && tile.getTileYIndex() == 8) {
                bottomEdge = tile;
                break;
            }
        }
        assertTrue(bottomEdge != null);
        assertEquals(35935, bottomEdge.getCropY() + bottomEdge.getCropHeight());
        assertEquals(bottomEdge.getCoreY() - bottomEdge.getCropY(), bottomEdge.getSourceCoreY());

        final CoreTileGeometry bottomRight = tiles.get(tiles.size() - 1);
        assertEquals(9395, bottomRight.getCropX() + bottomRight.getCropWidth());
        assertEquals(35935, bottomRight.getCropY() + bottomRight.getCropHeight());
        assertTrue(bottomRight.getSourceCoreX() + bottomRight.getCoreWidth()
                <= bottomRight.getCropWidth());
        assertTrue(bottomRight.getSourceCoreY() + bottomRight.getCoreHeight()
                <= bottomRight.getCropHeight());
    }

    @Test
    void seamPositionsMatchFixedCoreBoundaries() {
        final CoreTileLayout layout = CoreTileLayout.create(9395, 35935, 4096, 4096, 0);
        assertArrayEquals(new int[] {4096, 8192}, layout.getVerticalSeams());
        assertArrayEquals(new int[] {
                4096, 8192, 12288, 16384, 20480, 24576, 28672, 32768
        }, layout.getHorizontalSeams());
        assertTrue(Arrays.binarySearch(layout.getVerticalSeams(), 0) < 0);
        assertTrue(Arrays.binarySearch(layout.getVerticalSeams(), 9395) < 0);
        assertTrue(Arrays.binarySearch(layout.getHorizontalSeams(), 0) < 0);
        assertTrue(Arrays.binarySearch(layout.getHorizontalSeams(), 35935) < 0);
    }

    @Test
    void maximumExpandedCropUsesTheoreticalInteriorMaximum() {
        final CoreTileLayout layout = CoreTileLayout.create(10000, 10000, 4096, 4096, 48);
        assertEquals(4096 + 2 * 48, layout.getMaximumExpandedCropWidth());
        assertEquals(4096 + 2 * 48, layout.getMaximumExpandedCropHeight());
    }

    @Test
    void ceilDivIsIntegerSafe() {
        assertEquals(3, CoreTileLayout.ceilDiv(9395, 4096));
        assertEquals(9, CoreTileLayout.ceilDiv(35935, 4096));
        assertEquals(2, CoreTileLayout.ceilDiv(8192, 4096));
        assertEquals(1, CoreTileLayout.ceilDiv(1000, 4096));
    }
}
