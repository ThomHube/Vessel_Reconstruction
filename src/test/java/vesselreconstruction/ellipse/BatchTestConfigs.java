package vesselreconstruction.ellipse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared Phase-1+2 configuration fixtures for tests.
 */
final class BatchTestConfigs {

    private BatchTestConfigs() {
    }

    static Map<String, String> validMap() {
        return validMap(false, 1);
    }

    static Map<String, String> validMap(final boolean requireComplete, final int machines) {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(BatchConfiguration.KEY_SCHEMA_VERSION, "1");
        map.put(BatchConfiguration.KEY_INPUT_RECURSIVE, "false");
        map.put(BatchConfiguration.KEY_REQUIRE_COMPLETE, Boolean.toString(requireComplete));
        map.put(BatchConfiguration.KEY_MACHINES_COUNT, Integer.toString(machines));
        map.put(BatchConfiguration.KEY_MASK_DOWNSAMPLE, "8");
        map.put(BatchConfiguration.KEY_MASK_EXPANSION, "32");
        map.put(BatchConfiguration.KEY_MASK_CROP_PADDING, "0");
        map.put(BatchConfiguration.KEY_MASK_MIN_INCLUDED, "0.02");
        map.put(BatchConfiguration.KEY_MASK_MAX_INCLUDED, "0.85");
        map.put(BatchConfiguration.KEY_MASK_MIN_MAJOR_SPAN, "0.50");
        map.put(BatchConfiguration.KEY_MASK_MAX_SECONDARY, "0.30");
        map.put(BatchConfiguration.KEY_SCALE_PHYSICAL, "500.0");
        map.put(BatchConfiguration.KEY_SCALE_MIN_CONFIDENCE, "MEDIUM");
        map.put(BatchConfiguration.KEY_SCALE_REQUIRE_CENTERLINES, "true");
        map.put(BatchConfiguration.KEY_SCALE_MIN_CONTINUITY, "0.95");
        map.put(BatchConfiguration.KEY_SCALE_EXPECTED_PX, "1.0954306217917742");
        map.put(BatchConfiguration.KEY_SCALE_MAX_REL_DEV, "0.01");
        map.put(BatchConfiguration.KEY_PRECHECK_TIMEOUT, "60");
        map.put(BatchConfiguration.KEY_PRECHECK_CONTINUE, "true");
        map.put(BatchConfiguration.KEY_CLASSIFICATION_TILE_PROFILES,
                "2048x2048;1536x1536;1024x1024");
        map.put(BatchConfiguration.KEY_CLASSIFICATION_THREADS_PER_TILE, "1");
        map.put(BatchConfiguration.KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH, "16");
        map.put(BatchConfiguration.KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE, "false");
        map.put(BatchConfiguration.KEY_RECONSTRUCTION_MIN_LUMEN_AREA, "1000");
        map.put(BatchConfiguration.KEY_FULL_TIMEOUT, "720");
        map.put(BatchConfiguration.KEY_FULL_REFRESH_AGGREGATE, "true");
        return map;
    }

    static String validPropertiesText() {
        return validPropertiesText(false, 1, false);
    }

    static String validPropertiesText(
            final boolean requireComplete,
            final int machines,
            final boolean recursive) {
        final StringBuilder sb = new StringBuilder();
        sb.append("batch.schema_version=1\n");
        sb.append("input.recursive=").append(recursive).append('\n');
        sb.append("input.require_complete_expected_set=").append(requireComplete).append('\n');
        sb.append("machines.count=").append(machines).append('\n');
        sb.append("mask.downsample_factor=8\n");
        sb.append("mask.expansion_margin_px=32\n");
        sb.append("mask.crop_padding_px=0\n");
        sb.append("mask.minimum_included_fraction=0.02\n");
        sb.append("mask.maximum_included_fraction=0.85\n");
        sb.append("mask.minimum_major_span_fraction=0.50\n");
        sb.append("mask.maximum_secondary_component_area_fraction=0.30\n");
        sb.append("scale_bar.physical_length_um=500.0\n");
        sb.append("scale_bar.minimum_confidence=MEDIUM\n");
        sb.append("scale_bar.require_terminal_tick_centerlines=true\n");
        sb.append("scale_bar.minimum_shaft_continuity=0.95\n");
        sb.append("scale_bar.expected_pixel_size_um=1.0954306217917742\n");
        sb.append("scale_bar.maximum_relative_pixel_size_deviation=0.01\n");
        sb.append("precheck.timeout_minutes=60\n");
        sb.append("precheck.continue_after_failure=true\n");
        sb.append("classification.tile_profiles=2048x2048;1536x1536;1024x1024\n");
        sb.append("classification.threads_per_tile=1\n");
        sb.append("classification.seam_audit_half_width_px=16\n");
        sb.append("classification.run_untiled_reference_comparison=false\n");
        sb.append("reconstruction.minimum_lumen_area_px=1000\n");
        sb.append("full.timeout_minutes=720\n");
        sb.append("full.refresh_aggregate_tables=true\n");
        return sb.toString();
    }
}
