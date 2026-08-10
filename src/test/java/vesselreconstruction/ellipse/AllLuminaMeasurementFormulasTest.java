package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllLuminaMeasurementFormulasTest {

    @Test
    void areaUm2EqualsAreaPixelsTimesPixelDimensions() {
        final LumenCoordinateContext context = context(0.5, 2.0);

        assertEquals(30.0, context.areaPxToUm2(30), 1.0e-12);
    }

    @Test
    void ellipseAxisRatioIsMinorOverMajorWithinUnitInterval() throws Exception {
        final int width = 9;
        final int height = 7;
        final boolean[] mask = new boolean[width * height];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                mask[y * width + x] = true;
            }
        }
        final FinalLumenGeometry.ComponentGeometry geometry =
                new FinalLumenGeometry.ComponentGeometry(
                        1, mask, width, height,
                        0, 0, width, height,
                        35, 4.0, 3.0,
                        new Rectangle(1, 1, 7, 5), false, 0, false, null);
        final Method fit = AllLuminaDatasetWriter.class.getDeclaredMethod(
                "fit", FinalLumenGeometry.ComponentGeometry.class, LumenCoordinateContext.class);
        fit.setAccessible(true);
        final Object result = fit.invoke(null, geometry, context(1.0, 1.0));
        final Field ratio = result.getClass().getDeclaredField("ratio");
        ratio.setAccessible(true);
        final double value = ratio.getDouble(result);

        assertTrue(value > 0.0 && value <= 1.0, "ratio=" + value);
    }

    private static LumenCoordinateContext context(final double pixelWidthUm, final double pixelHeightUm) {
        return new LumenCoordinateContext(
                "0105A1", "segment_02", 1, SegmentOrientation.LEFT_TO_RIGHT,
                9, 7, 0, 0, 9, 7, pixelWidthUm, pixelHeightUm, null);
    }
}
