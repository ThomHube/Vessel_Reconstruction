package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MooreChainPerimeterTest {

    @Test
    void measuresAxisAlignedRectangleIncludingClosingStep() {
        final MooreChainPerimeter.Result result =
                MooreChainPerimeter.measure(rectangleMask(4, 3), 4, 3, 1.0, 1.0);

        assertEquals(10.0, result.lengthPx, 1.0e-12);
        assertEquals(10.0, result.lengthUm, 1.0e-12);
    }

    @Test
    void measuresDiagonalStepsAsSquareRootOfTwo() {
        final boolean[] diagonal = new boolean[4];
        diagonal[0] = true;
        diagonal[3] = true;

        final MooreChainPerimeter.Result result =
                MooreChainPerimeter.measure(diagonal, 2, 2, 1.0, 1.0);

        assertEquals(2.0 * Math.sqrt(2.0), result.lengthPx, 1.0e-12);
    }

    @Test
    void scalesAxisAlignedStepsWithAnisotropicPixels() {
        final MooreChainPerimeter.Result result =
                MooreChainPerimeter.measure(rectangleMask(4, 3), 4, 3, 2.0, 3.0);

        assertEquals(24.0, result.lengthUm, 1.0e-12);
    }

    @Test
    void internalHoleDoesNotChangeOuterPerimeter() {
        final boolean[] solid = rectangleMask(5, 5);
        final boolean[] withHole = rectangleMask(5, 5);
        withHole[2 * 5 + 2] = false;

        final MooreChainPerimeter.Result solidResult =
                MooreChainPerimeter.measure(solid, 5, 5, 1.0, 1.0);
        final MooreChainPerimeter.Result holeResult =
                MooreChainPerimeter.measure(withHole, 5, 5, 1.0, 1.0);

        assertEquals(16.0, solidResult.lengthPx, 1.0e-12);
        assertEquals(16.0, holeResult.lengthPx, 1.0e-12);
        assertEquals(solidResult.lengthPx, holeResult.lengthPx, 1.0e-12);
    }

    @Test
    void internalHoleBoundaryPixelsAreExcludedFromOuterContour() {
        final boolean[] withHole = rectangleMask(5, 5);
        withHole[2 * 5 + 2] = false;

        final List<Point> boundary =
                MooreChainPerimeter.extractBoundaryPixels(withHole, 5, 5);
        assertEquals(16, boundary.size());

        final Set<Point> set = new HashSet<Point>(boundary);
        assertFalse(set.contains(new Point(1, 1)));
        assertFalse(set.contains(new Point(2, 1)));
        assertFalse(set.contains(new Point(3, 1)));
        assertFalse(set.contains(new Point(1, 2)));
        assertFalse(set.contains(new Point(3, 2)));
        assertFalse(set.contains(new Point(1, 3)));
        assertFalse(set.contains(new Point(2, 3)));
        assertFalse(set.contains(new Point(3, 3)));

        // Outer frame only.
        for (final Point p : boundary) {
            assertTrue(p.x == 0 || p.x == 4 || p.y == 0 || p.y == 4,
                    "unexpected non-outer pixel: " + p.x + "," + p.y);
        }
    }

    @Test
    void concaveLShapeOuterContourHasNoInternalLoopAndMeasuresWithoutJump() {
        // L shape:
        // ###
        // #
        // #
        final boolean[] mask = new boolean[3 * 3];
        mask[0 * 3 + 0] = true;
        mask[0 * 3 + 1] = true;
        mask[0 * 3 + 2] = true;
        mask[1 * 3 + 0] = true;
        mask[2 * 3 + 0] = true;

        final List<Point> boundary =
                MooreChainPerimeter.extractBoundaryPixels(mask, 3, 3);
        assertFalse(boundary.isEmpty());

        // Interior corner relative to a filled 3x3 is (1,1)/(2,1)/(1,2)/(2,2);
        // with this L they are background and must not appear as an internal loop.
        final Set<Point> set = new HashSet<Point>(boundary);
        assertFalse(set.contains(new Point(1, 1)));
        assertFalse(set.contains(new Point(2, 1)));
        assertFalse(set.contains(new Point(1, 2)));
        assertFalse(set.contains(new Point(2, 2)));

        final MooreChainPerimeter.Result result =
                MooreChainPerimeter.measure(mask, 3, 3, 1.0, 1.0);
        assertTrue(Double.isFinite(result.lengthPx));
        assertTrue(result.lengthPx > 0.0);
    }

    private static boolean[] rectangleMask(final int width, final int height) {
        final boolean[] mask = new boolean[width * height];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = true;
        }
        return mask;
    }
}
