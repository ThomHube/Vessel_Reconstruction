package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SixDigitSampleIdTest {

    @Test
    void parse010101() {
        final SixDigitSampleId id = SixDigitSampleId.parse("010101");
        assertEquals("010101", id.imageId());
        assertEquals(1, id.plotNumber());
        assertEquals(1, id.treeNumber());
        assertEquals(1, id.replicateNumber());
        assertEquals("01", id.plotId());
        assertEquals("01", id.treeId());
        assertEquals("01", id.replicateId());
    }

    @Test
    void parse022002() {
        final SixDigitSampleId id = SixDigitSampleId.parse("022002");
        assertEquals("022002", id.imageId());
        assertEquals(2, id.plotNumber());
        assertEquals(20, id.treeNumber());
        assertEquals(2, id.replicateNumber());
        assertEquals("02", id.plotId());
        assertEquals("20", id.treeId());
        assertEquals("02", id.replicateId());
    }

    @Test
    void rejectPlot00() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("000101"));
    }

    @Test
    void rejectPlot03() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("030101"));
    }

    @Test
    void rejectTree00() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("010001"));
    }

    @Test
    void rejectTree21() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("012101"));
    }

    @Test
    void rejectReplicate00() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("010100"));
    }

    @Test
    void rejectReplicate03() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("010103"));
    }

    @Test
    void rejectFiveDigits() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("01010"));
    }

    @Test
    void rejectSevenDigits() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("0101011"));
    }

    @Test
    void rejectNondigits() {
        assertThrows(IllegalArgumentException.class, () -> SixDigitSampleId.parse("01010a"));
    }

    @Test
    void expectedSetContainsExactly80Ids() {
        assertEquals(80, SixDigitSampleId.expectedCompleteSet().size());
    }

    @Test
    void expectedSetBeginsWith010101() {
        assertEquals("010101", SixDigitSampleId.expectedCompleteSet().get(0).imageId());
    }

    @Test
    void expectedSetEndsWith022002() {
        final List<SixDigitSampleId> expected = SixDigitSampleId.expectedCompleteSet();
        assertEquals("022002", expected.get(expected.size() - 1).imageId());
    }

    @Test
    void expectedSetHasNoDuplicates() {
        final List<SixDigitSampleId> expected = SixDigitSampleId.expectedCompleteSet();
        final Set<String> seen = new HashSet<String>();
        for (final SixDigitSampleId id : expected) {
            assertTrue(seen.add(id.imageId()), "duplicate " + id);
        }
        assertEquals(80, seen.size());
    }

    @Test
    void canonicalNumericalSorting() {
        final List<SixDigitSampleId> shuffled = new ArrayList<SixDigitSampleId>();
        shuffled.add(SixDigitSampleId.parse("020101"));
        shuffled.add(SixDigitSampleId.parse("010102"));
        shuffled.add(SixDigitSampleId.parse("010201"));
        shuffled.add(SixDigitSampleId.parse("010101"));
        Collections.sort(shuffled);
        assertEquals("010101", shuffled.get(0).imageId());
        assertEquals("010102", shuffled.get(1).imageId());
        assertEquals("010201", shuffled.get(2).imageId());
        assertEquals("020101", shuffled.get(3).imageId());
    }

    @Test
    void expectedSetIsUnmodifiable() {
        try {
            SixDigitSampleId.expectedCompleteSet().add(SixDigitSampleId.parse("010101"));
            fail("expected set must be unmodifiable");
        } catch (final UnsupportedOperationException expected) {
            // ok
        }
    }
}
