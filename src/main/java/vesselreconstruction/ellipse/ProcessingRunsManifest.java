package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Append-only processing_runs.csv with canonical schema validation and migration.
 */
public final class ProcessingRunsManifest {

    /**
     * Known older headers that can be migrated by column name to the canonical schema.
     * Orderings may differ; values are mapped by name.
     */
    private static final List<List<String>> KNOWN_MIGRATABLE_HEADERS =
            Collections.unmodifiableList(Arrays.asList(
                    // Pre-reconstruction subset (classification-only era).
                    Arrays.asList(
                            "schema_version",
                            "run_id",
                            "timestamp",
                            "stage",
                            "status",
                            "sample_id",
                            "segment_id",
                            "input_filename",
                            "output_filename",
                            "plugin_version",
                            "git_commit",
                            "classifier_filename",
                            "core_tile_width_px",
                            "core_tile_height_px",
                            "threads_per_tile",
                            "mask_downsample_factor",
                            "mask_expansion_margin_px",
                            "processing_time_ms",
                            "message"),
                    // Same subset with reconstruction count fields but missing some tile fields.
                    Arrays.asList(
                            "schema_version",
                            "run_id",
                            "timestamp",
                            "stage",
                            "status",
                            "sample_id",
                            "segment_id",
                            "input_filename",
                            "output_filename",
                            "plugin_version",
                            "git_commit",
                            "classifier_filename",
                            "minimum_lumen_area_px",
                            "total_components",
                            "eligible_components",
                            "reconstructed_components",
                            "changed_pixels",
                            "processing_time_ms",
                            "message")));

    private ProcessingRunsManifest() {
    }

    /**
     * Ensures the file uses the canonical header (migrating when allowed), then appends.
     */
    public static void append(
            final Path target,
            final ProcessingRunRecord record,
            final ProductionSchemas.ProcessingStage stage) throws IOException {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        final List<String> row = record.toRow();
        ensureCanonicalSchema(target, stage);
        appendExact(target, row, stage);
    }

    public static void ensureCanonicalSchema(
            final Path target,
            final ProductionSchemas.ProcessingStage stage) throws IOException {
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            return;
        }
        final byte[] originalBytes = Files.readAllBytes(target);
        final List<List<String>> table;
        try {
            table = ProductionCsvStore.readTable(target);
        } catch (final RuntimeException ex) {
            throw new IOException(
                    "Failed to parse processing_runs.csv; original file preserved.\n"
                            + "path: " + target + "\n" + ex.getMessage(), ex);
        }
        if (table.isEmpty()) {
            throw new IOException("processing_runs.csv is empty: " + target);
        }
        final List<String> existingHeader = table.get(0);
        if (existingHeader.equals(ProcessingRunRecord.HEADER)) {
            validateExistingRows(table, existingHeader, target);
            return;
        }
        if (!isKnownMigratable(existingHeader)) {
            throw new IOException(ProcessingRunRecord.describeHeaderMismatch(
                    target.toString(),
                    existingHeader,
                    ProcessingRunRecord.HEADER,
                    Collections.<String>emptyList())
                    + "\nstage attempting the write: "
                    + (stage == null ? "UNKNOWN" : stage.name())
                    + "\nMigration aborted; original file preserved.");
        }
        try {
            validateExistingRows(table, existingHeader, target);
            final List<List<String>> migratedBody = new ArrayList<List<String>>();
            final Map<String, Integer> oldIndex = ProductionCsvStore.headerIndex(existingHeader);
            for (int r = 1; r < table.size(); r++) {
                final List<String> oldRow = table.get(r);
                final ProcessingRunRecord.Builder builder = ProcessingRunRecord.builder();
                for (int c = 0; c < ProcessingRunRecord.HEADER.size(); c++) {
                    final String column = ProcessingRunRecord.HEADER.get(c);
                    final Integer idx = oldIndex.get(column);
                    if (idx == null) {
                        builder.put(column, RCsvSupport.NA);
                    } else {
                        builder.put(column, oldRow.get(idx.intValue()));
                    }
                }
                migratedBody.add(builder.build().toRow());
            }
            ProductionCsvStore.writeTableAtomically(
                    target, ProcessingRunRecord.HEADER, migratedBody);
            // Validate after write.
            final List<List<String>> rewritten = ProductionCsvStore.readTable(target);
            if (rewritten.isEmpty()
                    || !rewritten.get(0).equals(ProcessingRunRecord.HEADER)) {
                Files.write(target, originalBytes);
                throw new IOException(
                        "Migrated processing_runs.csv failed validation; original restored: "
                                + target);
            }
            validateExistingRows(rewritten, ProcessingRunRecord.HEADER, target);
        } catch (final IOException ex) {
            Files.write(target, originalBytes);
            throw ex;
        } catch (final RuntimeException ex) {
            Files.write(target, originalBytes);
            throw new IOException(
                    "processing_runs.csv migration failed; original preserved.\n"
                            + "path: " + target + "\n" + ex.getMessage(), ex);
        }
    }

    private static void appendExact(
            final Path target,
            final List<String> row,
            final ProductionSchemas.ProcessingStage stage) throws IOException {
        if (row.size() != ProcessingRunRecord.COLUMN_COUNT) {
            throw new IOException(ProcessingRunRecord.describeHeaderMismatch(
                    target.toString(),
                    Files.exists(target) && !ProductionCsvStore.readTable(target).isEmpty()
                            ? ProductionCsvStore.readTable(target).get(0)
                            : Collections.<String>emptyList(),
                    ProcessingRunRecord.HEADER,
                    row)
                    + "\nstage attempting the write: "
                    + (stage == null ? "UNKNOWN" : stage.name()));
        }
        if (!Files.exists(target)) {
            ProductionCsvStore.writeTableAtomically(
                    target, ProcessingRunRecord.HEADER, Collections.singletonList(row));
            return;
        }
        final List<List<String>> existing = ProductionCsvStore.readTable(target);
        if (existing.isEmpty()) {
            throw new IOException("processing_runs.csv is empty: " + target);
        }
        final List<String> header = existing.get(0);
        if (!header.equals(ProcessingRunRecord.HEADER) || row.size() != header.size()) {
            throw new IOException(ProcessingRunRecord.describeHeaderMismatch(
                    target.toString(), header, ProcessingRunRecord.HEADER, row)
                    + "\nstage attempting the write: "
                    + (stage == null ? "UNKNOWN" : stage.name()));
        }
        validateExistingRows(existing, header, target);
        ProductionCsvStore.appendRow(target, ProcessingRunRecord.HEADER, row);
    }

    private static void validateExistingRows(
            final List<List<String>> table,
            final List<String> header,
            final Path path) throws IOException {
        final List<String> duplicates = ProcessingRunRecord.findDuplicates(header);
        if (!duplicates.isEmpty()) {
            throw new IOException(
                    "processing_runs.csv has duplicate header columns: " + duplicates
                            + "\npath: " + path
                            + "\nMigration/append aborted; original file preserved.");
        }
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (row.size() != header.size()) {
                throw new IOException(
                        "Malformed processing_runs.csv row width at data row " + r
                                + ": expected " + header.size()
                                + " columns, found " + row.size()
                                + "\npath: " + path
                                + "\nMigration/append aborted; original file preserved.");
            }
        }
    }

    private static boolean isKnownMigratable(final List<String> existingHeader) {
        if (existingHeader == null || existingHeader.isEmpty()) {
            return false;
        }
        if (!ProcessingRunRecord.findDuplicates(existingHeader).isEmpty()) {
            return false;
        }
        // Exact known list, or any permutation/subset of canonical columns with no extras.
        for (int i = 0; i < KNOWN_MIGRATABLE_HEADERS.size(); i++) {
            if (sameMultiset(existingHeader, KNOWN_MIGRATABLE_HEADERS.get(i))) {
                return true;
            }
        }
        final Set<String> canonical = new HashSet<String>(ProcessingRunRecord.HEADER);
        final Set<String> existing = new LinkedHashSet<String>(existingHeader);
        if (existing.size() != existingHeader.size()) {
            return false;
        }
        for (final String col : existing) {
            if (!canonical.contains(col)) {
                return false;
            }
        }
        // Non-empty proper subset or permutation of canonical columns.
        return !existing.isEmpty() && existing.size() <= canonical.size();
    }

    private static boolean sameMultiset(final List<String> a, final List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        final List<String> aa = new ArrayList<String>(a);
        final List<String> bb = new ArrayList<String>(b);
        Collections.sort(aa);
        Collections.sort(bb);
        return aa.equals(bb);
    }

    public static String summarizeForLog(final Path path) {
        try {
            if (!Files.exists(path)) {
                return "processing_runs.csv absent";
            }
            final List<List<String>> table = ProductionCsvStore.readTable(path);
            if (table.isEmpty()) {
                return "processing_runs.csv empty";
            }
            return String.format(Locale.US,
                    "processing_runs.csv rows=%d headerCols=%d",
                    Integer.valueOf(Math.max(0, table.size() - 1)),
                    Integer.valueOf(table.get(0).size()));
        } catch (final Exception ex) {
            return "processing_runs.csv unreadable: " + ex.getMessage();
        }
    }
}
