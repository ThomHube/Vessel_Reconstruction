package vesselreconstruction.ellipse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoreSegmentIdTest {

    @Test
    void parses0101A1() {
        final CoreSegmentId id = CoreSegmentId.parse("0101A1");
        assertEquals("0101A1", id.imageId());
        assertEquals(1, id.plotNumber());
        assertEquals(1, id.treeNumber());
        assertEquals("01", id.plotId());
        assertEquals("01", id.treeId());
        assertEquals('A', id.coreLabel());
        assertEquals("0101A", id.coreId());
        assertEquals("0101A", id.sampleId());
        assertEquals("0101A1", id.segmentId());
        assertEquals(1, id.segmentOrder());
    }

    @Test
    void parses0101A5And0101B3() {
        final CoreSegmentId a5 = CoreSegmentId.parse("0101A5");
        assertEquals(5, a5.segmentOrder());
        assertEquals("0101A", a5.coreId());
        final CoreSegmentId b3 = CoreSegmentId.parse("0101B3");
        assertEquals('B', b3.coreLabel());
        assertEquals("0101B", b3.coreId());
        assertEquals(3, b3.segmentOrder());
    }

    @Test
    void canonicalOrdering() {
        final List<CoreSegmentId> ids = Arrays.asList(
                CoreSegmentId.parse("0201A1"),
                CoreSegmentId.parse("0102A1"),
                CoreSegmentId.parse("0101B1"),
                CoreSegmentId.parse("0101A2"),
                CoreSegmentId.parse("0101A1"));
        Collections.sort(ids);
        assertEquals(Arrays.asList(
                CoreSegmentId.parse("0101A1"),
                CoreSegmentId.parse("0101A2"),
                CoreSegmentId.parse("0101B1"),
                CoreSegmentId.parse("0102A1"),
                CoreSegmentId.parse("0201A1")), ids);
    }

    @Test
    void equalsAndHashCode() {
        final CoreSegmentId a = CoreSegmentId.parse("0101A2");
        final CoreSegmentId b = CoreSegmentId.parse("0101A2");
        final CoreSegmentId c = CoreSegmentId.parse("0101A3");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertEquals("0101A2", a.toString());
    }

    @Test
    void rejectsObsoleteNumericSixDigit() {
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("010101"));
    }

    @Test
    void rejectsLowercaseCoreLabel() {
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101a1"));
    }

    @Test
    void rejectsSurroundingWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse(" 0101A1"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101A1 "));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse(" 0101A1 "));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("\t0101A1"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101A1\n"));
    }

    @Test
    void rejectsInvalidCoreAndSegment() {
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101C1"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101A0"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101A6"));
    }

    @Test
    void rejectsExtraCharactersAndZeroPlotTree() {
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0101A01"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0001A1"));
        assertThrows(IllegalArgumentException.class, () -> CoreSegmentId.parse("0100A1"));
    }

    @Test
    void aBeforeBWithinSameTree() {
        assertTrue(CoreSegmentId.parse("0101A5").compareTo(CoreSegmentId.parse("0101B1")) < 0);
    }
}
