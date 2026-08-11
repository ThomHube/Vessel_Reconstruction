package vesselreconstruction.ellipse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class BatchManifestSchemaTest {

    @Test
    void schemaVersionIsTwo() {
        assertEquals(2, BatchManifestSchema.SCHEMA_VERSION);
    }

    @Test
    void headerHasCoreIdentityColumnsAndNoReplicateId() {
        assertEquals(
                java.util.Arrays.asList(
                        "schema_version",
                        "order_index",
                        "image_id",
                        "plot_id",
                        "tree_id",
                        "core_label",
                        "core_id",
                        "segment_order",
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
                        "configuration_sha256"),
                BatchManifestSchema.HEADER);
        assertFalse(BatchManifestSchema.HEADER.contains("replicate_id"));
    }
}
