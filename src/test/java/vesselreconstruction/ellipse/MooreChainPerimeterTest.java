package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static boolean[] rectangleMask(final int width, final int height) {
        final boolean[] mask = new boolean[width * height];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = true;
        }
        return mask;
    }
}
