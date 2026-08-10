package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearestLumenDistanceTest {

    @Test
    void computesKnownCentroidAndExactWallDistances() {
        final List<NearestLumenDistance.LumenGeometry> lumina = Arrays.asList(
                lumen("one", 0.0, 0.0, new Point(0, 0), new Point(0, 1)),
                lumen("two", 3.0, 4.0, new Point(3, 0), new Point(3, 1)));

        final NearestLumenDistance.Result[] results =
                NearestLumenDistance.computeAll(lumina, 2.0, 3.0);

        assertEquals("two", results[0].nearestLumenUid);
        assertEquals(5.0, results[0].nearestCentroidDistancePx, 1.0e-12);
        assertEquals(Math.sqrt(180.0), results[0].nearestCentroidDistanceUm, 1.0e-12);
        assertEquals(3.0, results[0].nearestWallDistancePx, 1.0e-12);
        assertEquals(6.0, results[0].nearestWallDistanceUm, 1.0e-12);
    }

    @Test
    void excludesSameComponentAndReturnsUnavailableForOneLumen() {
        final NearestLumenDistance.Result result = NearestLumenDistance.computeAll(
                Collections.singletonList(lumen("only", 1.0, 1.0, new Point(1, 1), new Point(2, 2))),
                1.0, 1.0)[0];

        assertNull(result.nearestLumenUid);
        assertTrue(Double.isNaN(result.nearestCentroidDistancePx));
        assertTrue(Double.isNaN(result.nearestWallDistancePx));
    }

    private static NearestLumenDistance.LumenGeometry lumen(
            final String uid, final double x, final double y, final Point... boundary) {
        return new NearestLumenDistance.LumenGeometry(uid, x, y, Arrays.asList(boundary));
    }
}
