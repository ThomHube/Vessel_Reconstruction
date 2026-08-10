package vesselreconstruction.ellipse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Canonical Phase-1 batch manifest CSV schema.
 */
public final class BatchManifestSchema {

    public static final int SCHEMA_VERSION = 1;
    public static final String STATUS_PENDING = "PENDING";

    public static final List<String> HEADER = Collections.unmodifiableList(Arrays.asList(
            "schema_version",
            "order_index",
            "image_id",
            "plot_id",
            "tree_id",
            "replicate_id",
            "source_filename",
            "source_relative_path",
            "source_sha256",
            "source_size_bytes",
            "assigned_machine",
            "assignment_index",
            "status",
            "attempt_count",
            "last_start_timestamp",
            "last_end_timestamp",
            "last_exit_code",
            "last_message",
            "tile_profile_index",
            "dataset_shard_relative_path",
            "configuration_sha256"));

    public static final String FILE_MASTER_MANIFEST = "master_batch_manifest.csv";
    public static final String FILE_ASSIGNMENT_SUMMARY = "machine_assignment_summary.txt";
    public static final String FILE_PLAN_REPORT = "batch_plan_report.txt";
    public static final String DIR_MACHINES = "machines";

    private BatchManifestSchema() {
    }

    public static String datasetShardRelativePath(final String machineId) {
        return "output/lumen_dataset_" + machineId;
    }

    public static String machineManifestFilename(final String machineId) {
        return machineId + "_manifest.csv";
    }
}
