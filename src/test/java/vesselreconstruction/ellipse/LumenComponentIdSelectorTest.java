package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenComponentIdSelectorTest {

    private final LumenComponentIdSelector selector = new LumenComponentIdSelector();

    @Test
    void nullListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> selector.selectById(null, 1));
        assertThrows(IllegalArgumentException.class, () -> selector.describeAvailableRange(null));
    }

    @Test
    void emptyListIsRejected() {
        final List<LumenComponentDescriptor> empty =
                Collections.<LumenComponentDescriptor>emptyList();
        assertThrows(IllegalArgumentException.class, () -> selector.selectById(empty, 1));
        assertThrows(IllegalArgumentException.class, () -> selector.describeAvailableRange(empty));
    }

    @Test
    void requestedIdZeroIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectById(singleList(1), 0));
    }

    @Test
    void negativeRequestedIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectById(singleList(1), -3));
    }

    @Test
    void nullListElementIsRejected() {
        final List<LumenComponentDescriptor> list =
                new ArrayList<LumenComponentDescriptor>();
        list.add(null);
        assertThrows(IllegalArgumentException.class, () -> selector.selectById(list, 1));
    }

    @Test
    void descriptorWithInvalidIdIsRejectedWhereConstructible() {
        assertThrows(IllegalArgumentException.class, () ->
                new LumenComponentDescriptor(
                        0,
                        Collections.singletonList(new Point(1, 1)),
                        4,
                        4));
    }

    @Test
    void duplicateComponentIdsAreRejected() {
        final LumenComponentDescriptor a = descriptor(1, 1, 1);
        final LumenComponentDescriptor b = descriptor(1, 3, 3);
        final List<LumenComponentDescriptor> list = Arrays.asList(a, b);
        assertThrows(IllegalArgumentException.class, () -> selector.selectById(list, 1));
    }

    @Test
    void oneDescriptorCanBeSelected() {
        final LumenComponentDescriptor only = descriptor(7, 2, 2);
        assertSame(only, selector.selectById(Collections.singletonList(only), 7));
    }

    @Test
    void firstMiddleAndLastIdsCanBeSelected() {
        final LumenComponentDescriptor first = descriptor(1, 0, 0);
        final LumenComponentDescriptor middle = descriptor(2, 2, 2);
        final LumenComponentDescriptor last = descriptor(3, 4, 4);
        final List<LumenComponentDescriptor> list = Arrays.asList(first, middle, last);
        assertSame(first, selector.selectById(list, 1));
        assertSame(middle, selector.selectById(list, 2));
        assertSame(last, selector.selectById(list, 3));
    }

    @Test
    void missingIdIsRejectedWithRangeDetails() {
        final List<LumenComponentDescriptor> list = Arrays.asList(
                descriptor(2, 0, 0),
                descriptor(5, 3, 3),
                descriptor(9, 6, 6));
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> selector.selectById(list, 4));
        final String message = ex.getMessage();
        assertTrue(message.contains("4"));
        assertTrue(message.contains("2"));
        assertTrue(message.contains("9"));
        assertTrue(message.contains("3 total") || message.contains("(3 total)"));
    }

    @Test
    void inputListNeedNotBeSortedAndIsUnchanged() {
        final LumenComponentDescriptor a = descriptor(9, 0, 0);
        final LumenComponentDescriptor b = descriptor(2, 2, 2);
        final LumenComponentDescriptor c = descriptor(5, 4, 4);
        final List<LumenComponentDescriptor> list =
                new ArrayList<LumenComponentDescriptor>(Arrays.asList(a, b, c));
        final List<LumenComponentDescriptor> before =
                new ArrayList<LumenComponentDescriptor>(list);
        assertSame(b, selector.selectById(list, 2));
        assertEquals(before, list);
        assertEquals(9, list.get(0).getComponentId());
        assertEquals(1, list.get(0).getArea());
    }

    @Test
    void selectionReturnsExactRequestedIdAndDoesNotRenumber() {
        final LumenComponentDescriptor component = descriptor(105, 8, 8);
        final LumenComponentDescriptor selected =
                selector.selectById(Collections.singletonList(component), 105);
        assertSame(component, selected);
        assertEquals(105, selected.getComponentId());
    }

    @Test
    void repeatedSelectionIsDeterministic() {
        final List<LumenComponentDescriptor> list = Arrays.asList(
                descriptor(3, 1, 1),
                descriptor(8, 4, 4));
        final LumenComponentDescriptor first = selector.selectById(list, 8);
        final LumenComponentDescriptor second = selector.selectById(list, 8);
        assertSame(first, second);
        assertEquals(first, second);
    }

    @Test
    void describeAvailableRangeReturnsExactExpectedText() {
        final List<LumenComponentDescriptor> list = Arrays.asList(
                descriptor(4, 0, 0),
                descriptor(1, 2, 2),
                descriptor(9, 5, 5));
        assertEquals("Available component IDs: 1-9 (3 total)",
                selector.describeAvailableRange(list));
    }

    @Test
    void describeAvailableRangeUsesTrueMinimumAndMaximum() {
        final List<LumenComponentDescriptor> list = Arrays.asList(
                descriptor(20, 0, 0),
                descriptor(7, 2, 2),
                descriptor(11, 4, 4));
        assertEquals("Available component IDs: 7-20 (3 total)",
                selector.describeAvailableRange(list));
    }

    @Test
    void nonContiguousIdsAreSupportedAndGapsFail() {
        final List<LumenComponentDescriptor> list = Arrays.asList(
                descriptor(1, 0, 0),
                descriptor(3, 2, 2),
                descriptor(5, 4, 4));
        assertSame(list.get(1), selector.selectById(list, 3));
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> selector.selectById(list, 2));
        assertTrue(ex.getMessage().contains("2"));
        assertTrue(ex.getMessage().contains("1-5"));
    }

    @Test
    void selectionPerformsNoImageProcessingAndNeedsNoImagePlus() {
        final List<LumenComponentDescriptor> list = Collections.singletonList(
                descriptor(1, 1, 1));
        assertEquals(1, selector.selectById(list, 1).getComponentId());
        assertEquals("Available component IDs: 1-1 (1 total)",
                selector.describeAvailableRange(list));
    }

    private static List<LumenComponentDescriptor> singleList(final int id) {
        return Collections.singletonList(descriptor(id, 1, 1));
    }

    private static LumenComponentDescriptor descriptor(
            final int id,
            final int x,
            final int y) {
        return new LumenComponentDescriptor(
                id,
                Collections.singletonList(new Point(x, y)),
                16,
                16);
    }
}
