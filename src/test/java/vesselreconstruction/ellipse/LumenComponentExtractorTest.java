package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenComponentExtractorTest {

    private final LumenComponentExtractor extractor = new LumenComponentExtractor();

    @Test
    void nullProcessorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null));
    }

    @Test
    void noLumenPixelsReturnsEmptyList() {
        final ByteProcessor processor = filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertTrue(components.isEmpty());
    }

    @Test
    void oneLumenPixelReturnsOneComponent() {
        final ByteProcessor processor = filled(5, 5, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        assertEquals(1, components.get(0).getComponentId());
        assertEquals(1, components.get(0).getArea());
        assertEquals(new Point(2, 2), components.get(0).getPixels().get(0));
    }

    @Test
    void cardinalNeighboursFormOneComponent() {
        final ByteProcessor processor = filled(6, 6, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(3, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        assertEquals(3, components.get(0).getArea());
    }

    @Test
    void diagonalNeighboursFormOneComponent() {
        final ByteProcessor processor = filled(6, 6, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(3, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        assertEquals(2, components.get(0).getArea());
    }

    @Test
    void separatedRegionsFormSeparateComponents() {
        final ByteProcessor processor = filled(8, 8, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(2, components.size());
        assertEquals(1, components.get(0).getComponentId());
        assertEquals(2, components.get(1).getComponentId());
    }

    @Test
    void componentIdsFollowRasterDiscoveryOrder() {
        final ByteProcessor processor = filled(8, 8, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(5, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.get(0).getComponentId());
        assertEquals(new Point(1, 1), components.get(0).getPixels().get(0));
        assertEquals(2, components.get(1).getComponentId());
        assertEquals(new Point(5, 5), components.get(1).getPixels().get(0));
    }

    @Test
    void componentPixelListsAreSortedYThenX() {
        final ByteProcessor processor = filled(6, 6, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(3, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<Point> pixels = extractor.extract(processor).get(0).getPixels();
        assertEquals(new Point(2, 2), pixels.get(0));
        assertEquals(new Point(3, 2), pixels.get(1));
        assertEquals(new Point(2, 3), pixels.get(2));
    }

    @Test
    void boundsAreExact() {
        final ByteProcessor processor = filled(10, 10, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(2, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(4, 5, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(3, 4, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = extractor.extract(processor).get(0);
        assertEquals(2, component.getBounds().x);
        assertEquals(3, component.getBounds().y);
        assertEquals(3, component.getBounds().width);
        assertEquals(3, component.getBounds().height);
    }

    @Test
    void borderDetectionIsExact() {
        final ByteProcessor processor = filled(5, 5, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(0, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(2, components.size());
        assertTrue(components.get(0).isTouchesImageBorder());
        assertFalse(components.get(1).isTouchesImageBorder());
    }

    @Test
    void rawValuesOtherThanLumenAreIgnored() {
        final ByteProcessor processor = filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.RAY_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.BACKGROUND_RESULT_VALUE);
        assertTrue(extractor.extract(processor).isEmpty());
    }

    @Test
    void duplicateComponentMembershipCannotOccur() {
        final ByteProcessor processor = filled(6, 6, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(3, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        assertEquals(3, components.get(0).getArea());
        assertEquals(3, components.get(0).getPixels().size());
    }

    @Test
    void sourcePixelsRemainUnchanged() {
        final ByteProcessor processor = filled(5, 5, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(2, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final byte[] before = copy((byte[]) processor.getPixels());
        extractor.extract(processor);
        assertTrue(same(before, (byte[]) processor.getPixels()));
    }

    @Test
    void repeatedExtractionIsDeterministic() {
        final ByteProcessor processor = filled(8, 8, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(1, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(6, 6, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> first = extractor.extract(processor);
        final List<LumenComponentDescriptor> second = extractor.extract(processor);
        assertEquals(first, second);
    }

    @Test
    void returnedListIsImmutable() {
        final ByteProcessor processor = filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertThrows(UnsupportedOperationException.class,
                () -> components.add(components.get(0)));
    }

    @Test
    void returnedPointsCannotMutateInternalState() {
        final ByteProcessor processor = filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor component = extractor.extract(processor).get(0);
        final List<Point> pixels = component.getPixels();
        pixels.get(0).x = 99;
        assertEquals(1, component.getPixels().get(0).x);
    }

    @Test
    void descriptorRetainsPackedPrimitiveMembershipNotPointCollections() {
        final ByteProcessor processor = filled(4, 4, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor descriptor = extractor.extract(processor).get(0);
        assertEquals(2, descriptor.getArea());

        boolean foundPackedIntArray = false;
        final Field[] fields = LumenComponentDescriptor.class.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            final Field field = fields[i];
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final Class<?> type = field.getType();
            assertFalse(List.class.isAssignableFrom(type),
                    "persistent membership must not be a List: " + field.getName());
            assertFalse(type.isArray() && Point.class.equals(type.getComponentType()),
                    "persistent membership must not be Point[]: " + field.getName());
            assertFalse(Set.class.isAssignableFrom(type),
                    "persistent membership must not be a Set: " + field.getName());
            assertFalse(Map.class.isAssignableFrom(type),
                    "persistent membership must not be a Map: " + field.getName());
            if (int[].class.equals(type)) {
                foundPackedIntArray = true;
            }
        }
        assertTrue(foundPackedIntArray,
                "LumenComponentDescriptor must retain packed membership as int[]");
    }

    @Test
    void getPixelsMaterializesIdenticalDefensiveCopy() {
        final ByteProcessor processor = filled(4, 3, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(2, 0, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(1, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(2, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(1, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        final LumenComponentDescriptor descriptor = extractor.extract(processor).get(0);
        final List<Point> first = descriptor.getPixels();
        assertEquals(4, first.size());
        assertEquals(new Point(2, 0), first.get(0));
        assertEquals(new Point(1, 1), first.get(1));
        assertEquals(new Point(2, 1), first.get(2));
        assertEquals(new Point(1, 2), first.get(3));

        first.get(1).x = 99;
        first.get(1).y = 99;
        first.clear();
        first.add(new Point(0, 0));

        final List<Point> second = descriptor.getPixels();
        assertEquals(4, second.size());
        assertEquals(new Point(2, 0), second.get(0));
        assertEquals(new Point(1, 1), second.get(1));
        assertEquals(new Point(2, 1), second.get(2));
        assertEquals(new Point(1, 2), second.get(3));
    }

    @Test
    void intRingQueuePreservesFifoOrderAcrossWrapAndGrowth() {
        final LumenComponentExtractor.IntRingQueue queue =
                new LumenComponentExtractor.IntRingQueue(4);
        queue.addLast(10);
        queue.addLast(20);
        queue.addLast(30);
        queue.addLast(40);
        assertEquals(10, queue.removeFirst());
        assertEquals(20, queue.removeFirst());

        queue.addLast(50);
        queue.addLast(60);
        assertEquals(4, queue.capacity());

        queue.addLast(70);
        assertTrue(queue.capacity() > 4);
        queue.addLast(80);
        queue.addLast(90);

        assertEquals(30, queue.removeFirst());
        assertEquals(40, queue.removeFirst());
        assertEquals(50, queue.removeFirst());
        assertEquals(60, queue.removeFirst());
        assertEquals(70, queue.removeFirst());
        assertEquals(80, queue.removeFirst());
        assertEquals(90, queue.removeFirst());
        assertTrue(queue.isEmpty());
    }

    @Test
    void extractionResultIsIdenticalWhenRingQueueMustGrow() {
        final int inset = 5;
        final int lumenWidth = 200;
        final int lumenHeight = 200;
        final int width = inset + lumenWidth + inset;
        final int height = inset + lumenHeight + inset;
        final ByteProcessor processor = filled(width, height, ClassifierLabels.WOOD_RESULT_VALUE);
        for (int y = inset; y < inset + lumenHeight; y++) {
            for (int x = inset; x < inset + lumenWidth; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
            }
        }

        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        final LumenComponentDescriptor component = components.get(0);
        assertEquals(1, component.getComponentId());
        assertEquals(lumenWidth * lumenHeight, component.getArea());
        assertEquals(inset, component.getBounds().x);
        assertEquals(inset, component.getBounds().y);
        assertEquals(lumenWidth, component.getBounds().width);
        assertEquals(lumenHeight, component.getBounds().height);
        assertFalse(component.isTouchesImageBorder());

        final List<Point> pixels = component.getPixels();
        assertEquals(lumenWidth * lumenHeight, pixels.size());
        assertEquals(new Point(inset, inset), pixels.get(0));
        assertEquals(new Point(inset + 1, inset), pixels.get(1));
        assertEquals(new Point(inset, inset + 1), pixels.get(lumenWidth));
        assertEquals(
                new Point(inset + lumenWidth - 1, inset + lumenHeight - 1),
                pixels.get(pixels.size() - 1));
    }

    @Test
    void denseMultiPathComponentDoesNotDuplicateMembers() {
        final int inset = 2;
        final int lumenWidth = 9;
        final int lumenHeight = 7;
        final int width = inset + lumenWidth + inset;
        final int height = inset + lumenHeight + inset;
        final ByteProcessor processor = filled(width, height, ClassifierLabels.WOOD_RESULT_VALUE);
        for (int y = inset; y < inset + lumenHeight; y++) {
            for (int x = inset; x < inset + lumenWidth; x++) {
                processor.set(x, y, ClassifierLabels.LUMEN_RESULT_VALUE);
            }
        }

        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(1, components.size());
        final LumenComponentDescriptor component = components.get(0);
        final int expectedArea = lumenWidth * lumenHeight;
        assertEquals(1, component.getComponentId());
        assertEquals(expectedArea, component.getArea());
        assertEquals(inset, component.getBounds().x);
        assertEquals(inset, component.getBounds().y);
        assertEquals(lumenWidth, component.getBounds().width);
        assertEquals(lumenHeight, component.getBounds().height);
        assertFalse(component.isTouchesImageBorder());

        final List<Point> pixels = component.getPixels();
        assertEquals(expectedArea, pixels.size());
        final Set<Point> unique = new HashSet<Point>();
        for (int i = 0; i < pixels.size(); i++) {
            final Point pixel = pixels.get(i);
            assertTrue(unique.add(new Point(pixel.x, pixel.y)),
                    "duplicate membership at " + pixel.x + "," + pixel.y);
            if (i > 0) {
                final Point previous = pixels.get(i - 1);
                assertTrue(previous.y < pixel.y
                                || (previous.y == pixel.y && previous.x < pixel.x),
                        "pixels must be sorted y then x");
            }
        }
        assertEquals(expectedArea, unique.size());
        assertEquals(new Point(inset, inset), pixels.get(0));
        assertEquals(
                new Point(inset + lumenWidth - 1, inset + lumenHeight - 1),
                pixels.get(pixels.size() - 1));
    }

    @Test
    void highLinearIndicesKeepRasterComponentIdsAndMembership() {
        final int width = 12;
        final int height = 10;
        final ByteProcessor processor = filled(width, height, ClassifierLabels.WOOD_RESULT_VALUE);
        processor.set(0, 1, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(3, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(4, 2, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(4, 3, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(10, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(11, 8, ClassifierLabels.LUMEN_RESULT_VALUE);
        processor.set(11, 9, ClassifierLabels.LUMEN_RESULT_VALUE);

        final List<LumenComponentDescriptor> components = extractor.extract(processor);
        assertEquals(3, components.size());

        final LumenComponentDescriptor first = components.get(0);
        assertEquals(1, first.getComponentId());
        assertEquals(1, first.getArea());
        assertEquals(0, first.getBounds().x);
        assertEquals(1, first.getBounds().y);
        assertEquals(1, first.getBounds().width);
        assertEquals(1, first.getBounds().height);
        assertTrue(first.isTouchesImageBorder());
        assertEquals(new Point(0, 1), first.getPixels().get(0));

        final LumenComponentDescriptor second = components.get(1);
        assertEquals(2, second.getComponentId());
        assertEquals(3, second.getArea());
        assertEquals(3, second.getBounds().x);
        assertEquals(2, second.getBounds().y);
        assertEquals(2, second.getBounds().width);
        assertEquals(2, second.getBounds().height);
        assertFalse(second.isTouchesImageBorder());
        assertEquals(new Point(3, 2), second.getPixels().get(0));
        assertEquals(new Point(4, 2), second.getPixels().get(1));
        assertEquals(new Point(4, 3), second.getPixels().get(2));

        final LumenComponentDescriptor third = components.get(2);
        assertEquals(3, third.getComponentId());
        assertEquals(3, third.getArea());
        assertEquals(10, third.getBounds().x);
        assertEquals(8, third.getBounds().y);
        assertEquals(2, third.getBounds().width);
        assertEquals(2, third.getBounds().height);
        assertTrue(third.isTouchesImageBorder());
        assertEquals(3, third.getPixels().size());
        assertEquals(new Point(10, 8), third.getPixels().get(0));
        assertEquals(new Point(11, 8), third.getPixels().get(1));
        assertEquals(new Point(11, 9), third.getPixels().get(2));
    }

    private static ByteProcessor filled(final int width, final int height, final int value) {
        final ByteProcessor processor = new ByteProcessor(width, height);
        final byte[] pixels = (byte[]) processor.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) value;
        }
        return processor;
    }

    private static byte[] copy(final byte[] source) {
        final byte[] out = new byte[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }

    private static boolean same(final byte[] a, final byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
