package vesselreconstruction.ellipse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotent data dictionary generator from shared schemas.
 */
public final class DataDictionaryWriter {

    private DataDictionaryWriter() {
    }

    public static void writeIdempotent(final ProductionDatasetLayout layout) throws IOException {
        layout.ensureDirectoriesExist();
        final Set<String> seen = new LinkedHashSet<String>();
        final List<List<String>> rows = new ArrayList<List<String>>();
        addSchema(rows, seen, "lumen_measurements", ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
        addSchema(rows, seen, "artifact_diagnostics", ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER);
        addSchema(rows, seen, "image_manifest", ProductionSchemas.IMAGE_MANIFEST_HEADER);
        addSchema(rows, seen, "segment_manifest", ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        addSchema(rows, seen, "processing_runs", ProductionSchemas.PROCESSING_RUNS_HEADER);

        add(rows, seen, "lumen_measurements", "ellipse_axis_ratio_minor_major", "double",
                "dimensionless", "0 < value <= 1",
                "Final fitted ellipse minor axis divided by major axis");
        add(rows, seen, "lumen_measurements", "wall_length_um", "double", "µm", "positive",
                "Final outer boundary perimeter using an 8-connected Moore chain (MOORE_CHAIN_8_CONNECTED)");
        add(rows, seen, "lumen_measurements", "nearest_wall_distance_um", "double", "µm",
                "nonnegative or NA",
                "Minimum physical distance between final boundary pixel centers within the same segment");
        add(rows, seen, "lumen_measurements", "artifact_reconstructed", "logical", "dimensionless",
                "TRUE|FALSE",
                "Whether accepted Wood-to-Lumen changes were applied to the reconstructed output");

        ProductionCsvStore.writeTableAtomically(
                layout.dataDictionary(),
                ProductionSchemas.DATA_DICTIONARY_HEADER,
                rows);
    }

    private static void addSchema(
            final List<List<String>> rows,
            final Set<String> seen,
            final String table,
            final List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            add(rows, seen, table, columns.get(i), "string", "NA", "NA",
                    "See production schema for " + table + "." + columns.get(i));
        }
    }

    private static void add(
            final List<List<String>> rows,
            final Set<String> seen,
            final String table,
            final String column,
            final String type,
            final String unit,
            final String allowed,
            final String description) {
        final String key = table + "\t" + column;
        if (!seen.add(key)) {
            return;
        }
        rows.add(RCsvSupport.mutableCopy(table, column, type, unit, allowed, description));
    }
}
