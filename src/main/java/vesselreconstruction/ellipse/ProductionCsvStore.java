package vesselreconstruction.ellipse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Atomic upsert and append helpers for production manifests and aggregate tables.
 */
public final class ProductionCsvStore {

    private ProductionCsvStore() {
    }

    public static void writeTableAtomically(
            final Path target,
            final List<String> header,
            final List<List<String>> rows) throws IOException {
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(
                target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
                writer.write(RCsvSupport.joinRow(header));
                writer.newLine();
                for (int i = 0; i < rows.size(); i++) {
                    final List<String> row = rows.get(i);
                    if (row.size() != header.size()) {
                        throw new IOException("Row/header column mismatch while writing " + target);
                    }
                    writer.write(RCsvSupport.joinRow(row));
                    writer.newLine();
                }
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void appendRow(
            final Path target,
            final List<String> header,
            final List<String> row) throws IOException {
        if (row.size() != header.size()) {
            throw new IOException(
                    ProcessingRunRecord.describeHeaderMismatch(
                            target.toString(),
                            Files.exists(target) && !readTable(target).isEmpty()
                                    ? readTable(target).get(0) : header,
                            header,
                            row));
        }
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            final List<List<String>> existing = readTable(target);
            if (existing.isEmpty()) {
                throw new IOException("Manifest is empty: " + target);
            }
            ProductionSchemas.requireExactHeader(existing.get(0), header, target.toString());
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(target, StandardOpenOption.APPEND),
                    StandardCharsets.UTF_8))) {
                writer.write(RCsvSupport.joinRow(row));
                writer.newLine();
            }
        } else {
            writeTableAtomically(target, header, Collections.singletonList(row));
        }
    }

    /**
     * Upserts a row keyed by the given column names (exact string match).
     */
    public static void upsertByKeys(
            final Path target,
            final List<String> header,
            final List<String> row,
            final String... keyColumns) throws IOException {
        if (row.size() != header.size()) {
            throw new IOException("Row/header mismatch for upsert: " + target);
        }
        final int[] keyIndexes = new int[keyColumns.length];
        for (int i = 0; i < keyColumns.length; i++) {
            keyIndexes[i] = header.indexOf(keyColumns[i]);
            if (keyIndexes[i] < 0) {
                throw new IllegalArgumentException("Unknown key column: " + keyColumns[i]);
            }
        }
        final List<List<String>> table = Files.exists(target)
                ? readTable(target) : new ArrayList<List<String>>();
        if (!table.isEmpty()) {
            ProductionSchemas.requireExactHeader(table.get(0), header, target.toString());
        } else {
            table.add(new ArrayList<String>(header));
        }
        boolean replaced = false;
        for (int r = 1; r < table.size(); r++) {
            final List<String> existing = table.get(r);
            boolean match = true;
            for (int k = 0; k < keyIndexes.length; k++) {
                if (!existing.get(keyIndexes[k]).equals(row.get(keyIndexes[k]))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                table.set(r, new ArrayList<String>(row));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            table.add(new ArrayList<String>(row));
        }
        final List<List<String>> body = new ArrayList<List<String>>();
        for (int i = 1; i < table.size(); i++) {
            body.add(table.get(i));
        }
        writeTableAtomically(target, header, body);
    }

    public static List<List<String>> readTable(final Path target) throws IOException {
        final byte[] bytes = Files.readAllBytes(target);
        final String content = new String(bytes, StandardCharsets.UTF_8);
        return CsvSupport.parseTable(content);
    }

    public static void refreshAggregateFromPerSegment(
            final Path perSegmentDir,
            final Path aggregateFile,
            final List<String> expectedHeader,
            final String uniqueIdColumn,
            final Comparator<List<String>> rowComparator) throws IOException {
        final Path backup = aggregateFile.resolveSibling(
                aggregateFile.getFileName().toString() + ".bak");
        byte[] previousBytes = null;
        if (Files.exists(aggregateFile)) {
            previousBytes = Files.readAllBytes(aggregateFile);
            Files.write(backup, previousBytes);
        }
        try {
            if (!Files.isDirectory(perSegmentDir)) {
                writeTableAtomically(aggregateFile, expectedHeader,
                        Collections.<List<String>>emptyList());
                return;
            }
            final List<Path> files = new ArrayList<Path>();
            for (final Path path : Files.newDirectoryStream(perSegmentDir, "*.csv")) {
                files.add(path);
            }
            Collections.sort(files);
            final int uidIndex = expectedHeader.indexOf(uniqueIdColumn);
            if (uidIndex < 0) {
                throw new IllegalArgumentException("uniqueIdColumn missing from header");
            }
            final Set<String> seen = new HashSet<String>();
            final List<List<String>> allRows = new ArrayList<List<String>>();
            for (final Path file : files) {
                final List<List<String>> table = readTable(file);
                if (table.isEmpty()) {
                    continue;
                }
                ProductionSchemas.requireExactHeader(
                        table.get(0), expectedHeader, file.toString());
                for (int r = 1; r < table.size(); r++) {
                    final List<String> row = table.get(r);
                    final String uid = row.get(uidIndex);
                    if (!seen.add(uid)) {
                        throw new IOException(
                                "Duplicate " + uniqueIdColumn + " while rebuilding aggregate: "
                                        + uid + " from " + file);
                    }
                    allRows.add(row);
                }
            }
            Collections.sort(allRows, rowComparator);
            writeTableAtomically(aggregateFile, expectedHeader, allRows);
            Files.deleteIfExists(backup);
        } catch (final Exception ex) {
            if (previousBytes != null) {
                Files.write(aggregateFile, previousBytes);
            }
            if (ex instanceof IOException) {
                throw (IOException) ex;
            }
            throw new IOException("Aggregate rebuild failed for " + aggregateFile
                    + ": " + ex.getMessage(), ex);
        } finally {
            // keep backup only on failure; deleted above on success
        }
    }

    public static Map<String, Integer> headerIndex(final List<String> header) {
        final Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < header.size(); i++) {
            map.put(header.get(i), Integer.valueOf(i));
        }
        return map;
    }

    public static Comparator<List<String>> lumenRowComparator(final List<String> header) {
        final Map<String, Integer> idx = headerIndex(header);
        return new Comparator<List<String>>() {
            @Override
            public int compare(final List<String> a, final List<String> b) {
                int c = a.get(idx.get("sample_id").intValue())
                        .compareTo(b.get(idx.get("sample_id").intValue()));
                if (c != 0) {
                    return c;
                }
                final int ao = Integer.parseInt(a.get(idx.get("segment_order").intValue()));
                final int bo = Integer.parseInt(b.get(idx.get("segment_order").intValue()));
                c = Integer.compare(ao, bo);
                if (c != 0) {
                    return c;
                }
                c = a.get(idx.get("segment_id").intValue())
                        .compareTo(b.get(idx.get("segment_id").intValue()));
                if (c != 0) {
                    return c;
                }
                final int al = Integer.parseInt(a.get(idx.get("lumen_id_segment").intValue()));
                final int bl = Integer.parseInt(b.get(idx.get("lumen_id_segment").intValue()));
                return Integer.compare(al, bl);
            }
        };
    }
}
