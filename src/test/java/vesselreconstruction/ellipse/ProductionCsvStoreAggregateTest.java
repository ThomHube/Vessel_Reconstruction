package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionCsvStoreAggregateTest {

    private static final List<String> HEADER = Arrays.asList(
            "sample_id", "segment_order", "segment_id", "lumen_id_segment", "lumen_uid");

    @Test
    void rebuildSortsRowsByCanonicalLumenKeys(@TempDir final Path tempDir) throws Exception {
        final Path perSegment = Files.createDirectory(tempDir.resolve("per-segment"));
        final Path aggregate = tempDir.resolve("all.csv");
        write(perSegment.resolve("b.csv"),
                row("sample-b", "1", "segment_01", "2", "b-2"),
                row("sample-a", "2", "segment_01", "1", "a-2-1"));
        write(perSegment.resolve("a.csv"),
                row("sample-a", "1", "segment_02", "2", "a-1-2"),
                row("sample-a", "1", "segment_02", "1", "a-1-1"));

        refresh(perSegment, aggregate);

        final List<List<String>> table = ProductionCsvStore.readTable(aggregate);
        assertEquals(Arrays.asList("a-1-1", "a-1-2", "a-2-1", "b-2"),
                Arrays.asList(table.get(1).get(4), table.get(2).get(4),
                        table.get(3).get(4), table.get(4).get(4)));
    }

    @Test
    void rejectsDuplicateLumenUidAndRestoresPreviousAggregate(@TempDir final Path tempDir)
            throws Exception {
        final Path perSegment = Files.createDirectory(tempDir.resolve("per-segment"));
        final Path aggregate = tempDir.resolve("all.csv");
        final byte[] previous = "previous aggregate\n".getBytes("UTF-8");
        Files.write(aggregate, previous);
        write(perSegment.resolve("one.csv"), row("a", "1", "s", "1", "duplicate"));
        write(perSegment.resolve("two.csv"), row("a", "1", "s", "2", "duplicate"));

        assertThrows(IOException.class, () -> refresh(perSegment, aggregate));
        assertEquals("previous aggregate\n", new String(Files.readAllBytes(aggregate), "UTF-8"));
    }

    @Test
    void rejectsIncompatibleHeaderAndRestoresPreviousAggregate(@TempDir final Path tempDir)
            throws Exception {
        final Path perSegment = Files.createDirectory(tempDir.resolve("per-segment"));
        final Path aggregate = tempDir.resolve("all.csv");
        final byte[] previous = "previous aggregate\n".getBytes("UTF-8");
        Files.write(aggregate, previous);
        ProductionCsvStore.writeTableAtomically(perSegment.resolve("bad.csv"),
                Arrays.asList("wrong"), Arrays.<List<String>>asList(Arrays.asList("value")));

        assertThrows(IOException.class, () -> refresh(perSegment, aggregate));
        assertEquals("previous aggregate\n", new String(Files.readAllBytes(aggregate), "UTF-8"));
    }

    private static void refresh(final Path perSegment, final Path aggregate) throws IOException {
        ProductionCsvStore.refreshAggregateFromPerSegment(perSegment, aggregate, HEADER, "lumen_uid",
                ProductionCsvStore.lumenRowComparator(HEADER));
    }

    private static List<String> row(final String sample, final String order, final String segment,
            final String lumen, final String uid) {
        return Arrays.asList(sample, order, segment, lumen, uid);
    }

    @SafeVarargs
    private static void write(final Path target, final List<String>... rows) throws IOException {
        ProductionCsvStore.writeTableAtomically(target, HEADER, Arrays.asList(rows));
    }
}
