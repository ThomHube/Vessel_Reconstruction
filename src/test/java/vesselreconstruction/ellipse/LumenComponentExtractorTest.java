package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
