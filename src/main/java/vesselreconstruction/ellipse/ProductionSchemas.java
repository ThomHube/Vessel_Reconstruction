package vesselreconstruction.ellipse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable production CSV schemas (schema_version = 1).
 */
public final class ProductionSchemas {

    public static final int DATA_SCHEMA_VERSION = 1;

    public static final List<String> LUMEN_MEASUREMENTS_HEADER = Collections.unmodifiableList(
            Arrays.asList(
                    "schema_version",
                    "run_id",
                    "lumen_uid",
                    "sample_id",
                    "segment_id",
                    "segment_order",
                    "segment_orientation",
                    "lumen_id_segment",
                    "source_rgb_filename",
                    "approved_mask_filename",
                    "masked_rgb_filename",
                    "masked_classified_filename",
                    "reconstructed_classified_filename",
                    "crop_x_px",
                    "crop_y_px",
                    "crop_width_px",
                    "crop_height_px",
                    "original_width_px",
                    "original_height_px",
                    "pixel_width_um",
                    "pixel_height_um",
                    "centroid_x_crop_px",
                    "centroid_y_crop_px",
                    "centroid_x_original_px",
                    "centroid_y_original_px",
                    "centroid_x_original_um",
                    "centroid_y_original_um",
                    "segment_axis_position_um",
                    "global_longitudinal_um",
                    "global_x_um",
                    "global_y_um",
                    "area_px",
                    "area_um2",
                    "ellipse_major_axis_px",
                    "ellipse_minor_axis_px",
                    "ellipse_major_axis_um",
                    "ellipse_minor_axis_um",
                    "ellipse_axis_ratio_minor_major",
                    "ellipse_fit_rms_normalized",
                    "wall_length_px",
                    "wall_length_um",
                    "nearest_lumen_uid",
                    "nearest_centroid_distance_px",
                    "nearest_centroid_distance_um",
                    "nearest_wall_distance_px",
                    "nearest_wall_distance_um",
                    "nearest_distance_scope",
                    "artifact_detected",
                    "artifact_reconstructed",
                    "artifact_changed_pixels",
                    "artifact_decision",
                    "touches_analysis_border",
                    "measurement_valid",
                    "measurement_exclusion_reason"));

    public static final List<String> IMAGE_MANIFEST_HEADER = Collections.unmodifiableList(
            Arrays.asList(
                    "schema_version",
                    "sample_id",
                    "segment_id",
                    "segment_order",
                    "segment_orientation",
                    "dataset_root",
                    "source_rgb_filename",
                    "source_rgb_path",
                    "source_rgb_sha256",
                    "source_width_px",
                    "source_height_px",
                    "approved_mask_filename",
                    "approved_mask_path",
                    "approved_mask_sha256",
                    "mask_downsample_factor",
                    "mask_width_px",
                    "mask_height_px",
                    "mask_checksum",
                    "masked_rgb_filename",
                    "masked_rgb_path",
                    "masked_rgb_sha256",
                    "masked_classified_filename",
                    "masked_classified_path",
                    "masked_classified_sha256",
                    "reconstructed_classified_filename",
                    "reconstructed_classified_path",
                    "reconstructed_classified_sha256",
                    "crop_x_px",
                    "crop_y_px",
                    "crop_width_px",
                    "crop_height_px",
                    "pixel_width_um",
                    "pixel_height_um",
                    "physical_unit",
                    "calibration_method",
                    "scale_bar_value_um",
                    "scale_bar_length_px",
                    "classifier_filename",
                    "classifier_path",
                    "classifier_sha256",
                    "classification_run_id",
                    "reconstruction_run_id",
                    "classification_timestamp",
                    "reconstruction_timestamp"));

    public static final List<String> SEGMENT_MANIFEST_HEADER = Collections.unmodifiableList(
            Arrays.asList(
                    "schema_version",
                    "sample_id",
                    "segment_id",
                    "segment_order",
                    "segment_orientation",
                    "continuation_confirmed",
                    "previous_segment_id",
                    "gap_to_previous_um",
                    "overlap_with_previous_um",
                    "global_segment_start_um",
                    "global_origin_x_um",
                    "global_origin_y_um",
                    "notes"));

    public static final List<String> PROCESSING_RUNS_HEADER = Collections.unmodifiableList(
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
                    "minimum_lumen_area_px",
                    "total_components",
                    "eligible_components",
                    "reconstructed_components",
                    "changed_pixels",
                    "processing_time_ms",
                    "message"));

    public static final List<String> DATA_DICTIONARY_HEADER = Collections.unmodifiableList(
            Arrays.asList(
                    "table_name",
                    "column_name",
                    "data_type",
                    "unit",
                    "allowed_values",
                    "description"));

    /**
     * Canonical artifact-diagnostics CSV header. Kept in this pure-Java schema
     * class so parent CLI finalized-dataset inspection does not load
     * ImageJ-dependent writers.
     */
    public static final List<String> ARTIFACT_DIAGNOSTICS_HEADER = Collections.unmodifiableList(
            Arrays.asList(
                    "schema_version",
                    "run_id",
                    "sample_id",
                    "segment_id",
                    "segment_order",
                    "lumen_uid",
                    "lumen_id_segment",
                    "status",
                    "reason",
                    "interval_selected",
                    "interval_selection_reason",
                    "artifact_evidence_decision",
                    "reconstruction_candidate_decision",
                    "boundary_edges",
                    "interval_edges",
                    "core_points",
                    "minimum_depth_sigma",
                    "mean_depth_sigma",
                    "core_fraction",
                    "normalized_integrated_excess",
                    "retained_rms_improvement",
                    "associated_components",
                    "matched_seeds",
                    "unmatched_seeds",
                    "other_lumen_inside_ellipse",
                    "changed_pixels",
                    "original_circularity",
                    "reconstructed_circularity",
                    "circularity_change",
                    "added_area_fraction",
                    "processing_time_ms"));

    public enum ProcessingStage {
        TILED_CLASSIFICATION,
        ALL_LUMINA_RECONSTRUCTION
    }

    public enum RunStatus {
        SUCCESS,
        CANCELLED,
        FAILED
    }

    private ProductionSchemas() {
    }

    public static void requireExactHeader(final List<String> actual, final List<String> expected, final String path) {
        if (actual == null || actual.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "Incompatible CSV header for " + path
                            + ": expected " + expected.size()
                            + " columns, found " + (actual == null ? 0 : actual.size()));
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                throw new IllegalArgumentException(
                        "Incompatible CSV header for " + path
                                + " at column " + i
                                + ": expected '" + expected.get(i)
                                + "' found '" + actual.get(i) + "'");
            }
        }
    }
}
