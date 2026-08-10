package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchPrecheckCsvSchemaTest {

    @Test
    void headerHasExactThirtyEightColumnsInOrder() {
        final List<String> expected = Arrays.asList(
                "schema_version",
                "order_index",
                "image_id",
                "machine_id",
                "source_filename",
                "source_relative_path",
                "source_sha256",
                "configuration_sha256",
                "status",
                "reason_code",
                "message",
                "image_width_px",
                "image_height_px",
                "mask_threshold",
                "mask_candidate_count",
                "mask_included_fraction",
                "mask_major_span_fraction",
                "mask_selected_component_area",
                "mask_secondary_component_area_fraction",
                "mask_checksum",
                "crop_x_px",
                "crop_y_px",
                "crop_width_px",
                "crop_height_px",
                "scale_bar_detection_method",
                "scale_bar_confidence",
                "scale_bar_score",
                "scale_bar_center_distance_px",
                "scale_bar_outer_width_px",
                "scale_bar_shaft_continuity",
                "pixel_size_um",
                "pixel_size_relative_deviation",
                "source_checksum_before",
                "source_checksum_after",
                "source_unchanged",
                "mask_preview_path",
                "scale_bar_preview_path",
                "processing_time_ms");
        assertEquals(38, expected.size());
        assertEquals(expected, BatchPrecheckCsvSchema.HEADER);
        assertEquals(38, BatchPrecheckCsvSchema.HEADER.size());
    }
}
