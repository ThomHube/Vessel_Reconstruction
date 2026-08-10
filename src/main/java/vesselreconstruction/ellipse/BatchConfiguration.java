package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Typed batch configuration for Phase-1 planning and Phase-2 precheck.
 */
public final class BatchConfiguration {

    public static final String KEY_SCHEMA_VERSION = "batch.schema_version";
    public static final String KEY_INPUT_RECURSIVE = "input.recursive";
    public static final String KEY_REQUIRE_COMPLETE =
            "input.require_complete_expected_set";
    public static final String KEY_MACHINES_COUNT = "machines.count";

    public static final String KEY_MASK_DOWNSAMPLE = "mask.downsample_factor";
    public static final String KEY_MASK_EXPANSION = "mask.expansion_margin_px";
    public static final String KEY_MASK_CROP_PADDING = "mask.crop_padding_px";
    public static final String KEY_MASK_MIN_INCLUDED = "mask.minimum_included_fraction";
    public static final String KEY_MASK_MAX_INCLUDED = "mask.maximum_included_fraction";
    public static final String KEY_MASK_MIN_MAJOR_SPAN = "mask.minimum_major_span_fraction";
    public static final String KEY_MASK_MAX_SECONDARY =
            "mask.maximum_secondary_component_area_fraction";

    public static final String KEY_SCALE_PHYSICAL = "scale_bar.physical_length_um";
    public static final String KEY_SCALE_MIN_CONFIDENCE = "scale_bar.minimum_confidence";
    public static final String KEY_SCALE_REQUIRE_CENTERLINES =
            "scale_bar.require_terminal_tick_centerlines";
    public static final String KEY_SCALE_MIN_CONTINUITY = "scale_bar.minimum_shaft_continuity";
    public static final String KEY_SCALE_EXPECTED_PX =
            "scale_bar.expected_pixel_size_um";
    public static final String KEY_SCALE_MAX_REL_DEV =
            "scale_bar.maximum_relative_pixel_size_deviation";

    public static final String KEY_PRECHECK_TIMEOUT = "precheck.timeout_minutes";
    public static final String KEY_PRECHECK_CONTINUE = "precheck.continue_after_failure";

    public static final String KEY_CLASSIFICATION_TILE_PROFILES = "classification.tile_profiles";
    public static final String KEY_CLASSIFICATION_THREADS_PER_TILE = "classification.threads_per_tile";
    public static final String KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH =
            "classification.seam_audit_half_width_px";
    public static final String KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE =
            "classification.run_untiled_reference_comparison";
    public static final String KEY_RECONSTRUCTION_MIN_LUMEN_AREA =
            "reconstruction.minimum_lumen_area_px";
    public static final String KEY_FULL_TIMEOUT = "full.timeout_minutes";
    public static final String KEY_FULL_REFRESH_AGGREGATE = "full.refresh_aggregate_tables";

    public static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    KEY_SCHEMA_VERSION,
                    KEY_INPUT_RECURSIVE,
                    KEY_REQUIRE_COMPLETE,
                    KEY_MACHINES_COUNT,
                    KEY_MASK_DOWNSAMPLE,
                    KEY_MASK_EXPANSION,
                    KEY_MASK_CROP_PADDING,
                    KEY_MASK_MIN_INCLUDED,
                    KEY_MASK_MAX_INCLUDED,
                    KEY_MASK_MIN_MAJOR_SPAN,
                    KEY_MASK_MAX_SECONDARY,
                    KEY_SCALE_PHYSICAL,
                    KEY_SCALE_MIN_CONFIDENCE,
                    KEY_SCALE_REQUIRE_CENTERLINES,
                    KEY_SCALE_MIN_CONTINUITY,
                    KEY_SCALE_EXPECTED_PX,
                    KEY_SCALE_MAX_REL_DEV,
                    KEY_PRECHECK_TIMEOUT,
                    KEY_PRECHECK_CONTINUE,
                    KEY_CLASSIFICATION_TILE_PROFILES,
                    KEY_CLASSIFICATION_THREADS_PER_TILE,
                    KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH,
                    KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE,
                    KEY_RECONSTRUCTION_MIN_LUMEN_AREA,
                    KEY_FULL_TIMEOUT,
                    KEY_FULL_REFRESH_AGGREGATE)));

    private final int schemaVersion;
    private final boolean recursive;
    private final boolean requireCompleteExpectedSet;
    private final int machinesCount;
    private final int maskDownsampleFactor;
    private final int maskExpansionMarginPx;
    private final int maskCropPaddingPx;
    private final double maskMinimumIncludedFraction;
    private final double maskMaximumIncludedFraction;
    private final double maskMinimumMajorSpanFraction;
    private final double maskMaximumSecondaryComponentAreaFraction;
    private final double scaleBarPhysicalLengthUm;
    private final ScaleBarDetectionConfidence scaleBarMinimumConfidence;
    private final boolean scaleBarRequireTerminalTickCenterlines;
    private final double scaleBarMinimumShaftContinuity;
    private final double scaleBarExpectedPixelSizeUm;
    private final double scaleBarMaximumRelativePixelSizeDeviation;
    private final int precheckTimeoutMinutes;
    private final boolean precheckContinueAfterFailure;
    private final List<BatchTileProfile> tileProfiles;
    private final int threadsPerTile;
    private final int seamAuditHalfWidthPx;
    private final boolean runUntiledReferenceComparison;
    private final int reconstructionMinimumLumenAreaPx;
    private final int fullTimeoutMinutes;
    private final boolean refreshAggregateTables;
    private final String canonicalText;
    private final String sha256;

    private BatchConfiguration(
            final int schemaVersion,
            final boolean recursive,
            final boolean requireCompleteExpectedSet,
            final int machinesCount,
            final int maskDownsampleFactor,
            final int maskExpansionMarginPx,
            final int maskCropPaddingPx,
            final double maskMinimumIncludedFraction,
            final double maskMaximumIncludedFraction,
            final double maskMinimumMajorSpanFraction,
            final double maskMaximumSecondaryComponentAreaFraction,
            final double scaleBarPhysicalLengthUm,
            final ScaleBarDetectionConfidence scaleBarMinimumConfidence,
            final boolean scaleBarRequireTerminalTickCenterlines,
            final double scaleBarMinimumShaftContinuity,
            final double scaleBarExpectedPixelSizeUm,
            final double scaleBarMaximumRelativePixelSizeDeviation,
            final int precheckTimeoutMinutes,
            final boolean precheckContinueAfterFailure,
            final List<BatchTileProfile> tileProfiles,
            final int threadsPerTile,
            final int seamAuditHalfWidthPx,
            final boolean runUntiledReferenceComparison,
            final int reconstructionMinimumLumenAreaPx,
            final int fullTimeoutMinutes,
            final boolean refreshAggregateTables,
            final String canonicalText,
            final String sha256) {
        this.schemaVersion = schemaVersion;
        this.recursive = recursive;
        this.requireCompleteExpectedSet = requireCompleteExpectedSet;
        this.machinesCount = machinesCount;
        this.maskDownsampleFactor = maskDownsampleFactor;
        this.maskExpansionMarginPx = maskExpansionMarginPx;
        this.maskCropPaddingPx = maskCropPaddingPx;
        this.maskMinimumIncludedFraction = maskMinimumIncludedFraction;
        this.maskMaximumIncludedFraction = maskMaximumIncludedFraction;
        this.maskMinimumMajorSpanFraction = maskMinimumMajorSpanFraction;
        this.maskMaximumSecondaryComponentAreaFraction =
                maskMaximumSecondaryComponentAreaFraction;
        this.scaleBarPhysicalLengthUm = scaleBarPhysicalLengthUm;
        this.scaleBarMinimumConfidence = scaleBarMinimumConfidence;
        this.scaleBarRequireTerminalTickCenterlines =
                scaleBarRequireTerminalTickCenterlines;
        this.scaleBarMinimumShaftContinuity = scaleBarMinimumShaftContinuity;
        this.scaleBarExpectedPixelSizeUm = scaleBarExpectedPixelSizeUm;
        this.scaleBarMaximumRelativePixelSizeDeviation =
                scaleBarMaximumRelativePixelSizeDeviation;
        this.precheckTimeoutMinutes = precheckTimeoutMinutes;
        this.precheckContinueAfterFailure = precheckContinueAfterFailure;
        this.tileProfiles = tileProfiles;
        this.threadsPerTile = threadsPerTile;
        this.seamAuditHalfWidthPx = seamAuditHalfWidthPx;
        this.runUntiledReferenceComparison = runUntiledReferenceComparison;
        this.reconstructionMinimumLumenAreaPx = reconstructionMinimumLumenAreaPx;
        this.fullTimeoutMinutes = fullTimeoutMinutes;
        this.refreshAggregateTables = refreshAggregateTables;
        this.canonicalText = canonicalText;
        this.sha256 = sha256;
    }

    public static BatchConfiguration load(final Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "configuration file must exist: " + path);
        }
        final List<String> rawLines = Files.readAllLines(path, StandardCharsets.UTF_8);
        final Map<String, String> parsed = parseStrictProperties(rawLines);
        return fromMap(parsed);
    }

    public static BatchConfiguration fromMap(final Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        for (final String key : values.keySet()) {
            if (!REQUIRED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown configuration key: " + key);
            }
        }
        for (final String required : REQUIRED_KEYS) {
            if (!values.containsKey(required)) {
                throw new IllegalArgumentException("Missing configuration key: " + required);
            }
            final String value = values.get(required);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Configuration value must not be blank: " + required);
            }
        }

        final int schemaVersion = parseInt(values.get(KEY_SCHEMA_VERSION), KEY_SCHEMA_VERSION);
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    KEY_SCHEMA_VERSION + " must be 1, found " + schemaVersion);
        }
        final boolean recursive = parseStrictBoolean(
                values.get(KEY_INPUT_RECURSIVE), KEY_INPUT_RECURSIVE);
        final boolean requireComplete = parseStrictBoolean(
                values.get(KEY_REQUIRE_COMPLETE), KEY_REQUIRE_COMPLETE);
        final int machinesCount = parseInt(values.get(KEY_MACHINES_COUNT), KEY_MACHINES_COUNT);
        if (machinesCount < 1) {
            throw new IllegalArgumentException(KEY_MACHINES_COUNT + " must be >= 1");
        }

        final int downsample = parseInt(values.get(KEY_MASK_DOWNSAMPLE), KEY_MASK_DOWNSAMPLE);
        if (downsample < 1) {
            throw new IllegalArgumentException(KEY_MASK_DOWNSAMPLE + " must be >= 1");
        }
        final int expansion = parseInt(values.get(KEY_MASK_EXPANSION), KEY_MASK_EXPANSION);
        if (expansion < 0) {
            throw new IllegalArgumentException(KEY_MASK_EXPANSION + " must be >= 0");
        }
        final int cropPadding = parseInt(values.get(KEY_MASK_CROP_PADDING), KEY_MASK_CROP_PADDING);
        if (cropPadding < 0) {
            throw new IllegalArgumentException(KEY_MASK_CROP_PADDING + " must be >= 0");
        }
        final double minIncluded = parseFiniteDouble(
                values.get(KEY_MASK_MIN_INCLUDED), KEY_MASK_MIN_INCLUDED);
        final double maxIncluded = parseFiniteDouble(
                values.get(KEY_MASK_MAX_INCLUDED), KEY_MASK_MAX_INCLUDED);
        requireUnitInterval(minIncluded, KEY_MASK_MIN_INCLUDED);
        requireUnitInterval(maxIncluded, KEY_MASK_MAX_INCLUDED);
        if (!(minIncluded < maxIncluded)) {
            throw new IllegalArgumentException(
                    KEY_MASK_MIN_INCLUDED + " must be < " + KEY_MASK_MAX_INCLUDED);
        }
        final double minMajor = parseFiniteDouble(
                values.get(KEY_MASK_MIN_MAJOR_SPAN), KEY_MASK_MIN_MAJOR_SPAN);
        requireUnitInterval(minMajor, KEY_MASK_MIN_MAJOR_SPAN);
        final double maxSecondary = parseFiniteDouble(
                values.get(KEY_MASK_MAX_SECONDARY), KEY_MASK_MAX_SECONDARY);
        requireUnitInterval(maxSecondary, KEY_MASK_MAX_SECONDARY);

        final double physical = parseFiniteDouble(
                values.get(KEY_SCALE_PHYSICAL), KEY_SCALE_PHYSICAL);
        if (!(physical > 0.0)) {
            throw new IllegalArgumentException(KEY_SCALE_PHYSICAL + " must be > 0");
        }
        final ScaleBarDetectionConfidence minConfidence = parseConfidence(
                values.get(KEY_SCALE_MIN_CONFIDENCE), KEY_SCALE_MIN_CONFIDENCE);
        final boolean requireCenterlines = parseStrictBoolean(
                values.get(KEY_SCALE_REQUIRE_CENTERLINES), KEY_SCALE_REQUIRE_CENTERLINES);
        final double minContinuity = parseFiniteDouble(
                values.get(KEY_SCALE_MIN_CONTINUITY), KEY_SCALE_MIN_CONTINUITY);
        requireUnitInterval(minContinuity, KEY_SCALE_MIN_CONTINUITY);
        final double expectedPx = parseFiniteDouble(
                values.get(KEY_SCALE_EXPECTED_PX), KEY_SCALE_EXPECTED_PX);
        if (!(expectedPx > 0.0)) {
            throw new IllegalArgumentException(KEY_SCALE_EXPECTED_PX + " must be > 0");
        }
        final double maxRelDev = parseFiniteDouble(
                values.get(KEY_SCALE_MAX_REL_DEV), KEY_SCALE_MAX_REL_DEV);
        if (Double.isNaN(maxRelDev) || maxRelDev < 0.0) {
            throw new IllegalArgumentException(KEY_SCALE_MAX_REL_DEV + " must be >= 0");
        }

        final int timeout = parseInt(values.get(KEY_PRECHECK_TIMEOUT), KEY_PRECHECK_TIMEOUT);
        if (timeout < 1) {
            throw new IllegalArgumentException(KEY_PRECHECK_TIMEOUT + " must be >= 1");
        }
        final boolean continueAfterFailure = parseStrictBoolean(
                values.get(KEY_PRECHECK_CONTINUE), KEY_PRECHECK_CONTINUE);

        final List<BatchTileProfile> tileProfiles = parseTileProfiles(
                values.get(KEY_CLASSIFICATION_TILE_PROFILES), KEY_CLASSIFICATION_TILE_PROFILES);
        final int threadsPerTile = parseInt(
                values.get(KEY_CLASSIFICATION_THREADS_PER_TILE), KEY_CLASSIFICATION_THREADS_PER_TILE);
        if (threadsPerTile < 1) {
            throw new IllegalArgumentException(KEY_CLASSIFICATION_THREADS_PER_TILE + " must be >= 1");
        }
        final int seamAuditHalfWidthPx = parseInt(
                values.get(KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH),
                KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH);
        if (seamAuditHalfWidthPx < 0) {
            throw new IllegalArgumentException(
                    KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH + " must be >= 0");
        }
        final boolean runUntiledReferenceComparison = parseStrictBoolean(
                values.get(KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE),
                KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE);
        final int reconstructionMinimumLumenAreaPx = parseInt(
                values.get(KEY_RECONSTRUCTION_MIN_LUMEN_AREA), KEY_RECONSTRUCTION_MIN_LUMEN_AREA);
        if (reconstructionMinimumLumenAreaPx < 1) {
            throw new IllegalArgumentException(KEY_RECONSTRUCTION_MIN_LUMEN_AREA + " must be >= 1");
        }
        final int fullTimeoutMinutes = parseInt(values.get(KEY_FULL_TIMEOUT), KEY_FULL_TIMEOUT);
        if (fullTimeoutMinutes < 1) {
            throw new IllegalArgumentException(KEY_FULL_TIMEOUT + " must be >= 1");
        }
        final boolean refreshAggregateTables = parseStrictBoolean(
                values.get(KEY_FULL_REFRESH_AGGREGATE), KEY_FULL_REFRESH_AGGREGATE);

        final Map<String, String> canonicalMap = new TreeMap<String, String>();
        canonicalMap.put(KEY_SCHEMA_VERSION, Integer.toString(schemaVersion));
        canonicalMap.put(KEY_INPUT_RECURSIVE, Boolean.toString(recursive));
        canonicalMap.put(KEY_REQUIRE_COMPLETE, Boolean.toString(requireComplete));
        canonicalMap.put(KEY_MACHINES_COUNT, Integer.toString(machinesCount));
        canonicalMap.put(KEY_MASK_DOWNSAMPLE, Integer.toString(downsample));
        canonicalMap.put(KEY_MASK_EXPANSION, Integer.toString(expansion));
        canonicalMap.put(KEY_MASK_CROP_PADDING, Integer.toString(cropPadding));
        canonicalMap.put(KEY_MASK_MIN_INCLUDED, formatDouble(minIncluded));
        canonicalMap.put(KEY_MASK_MAX_INCLUDED, formatDouble(maxIncluded));
        canonicalMap.put(KEY_MASK_MIN_MAJOR_SPAN, formatDouble(minMajor));
        canonicalMap.put(KEY_MASK_MAX_SECONDARY, formatDouble(maxSecondary));
        canonicalMap.put(KEY_SCALE_PHYSICAL, formatDouble(physical));
        canonicalMap.put(KEY_SCALE_MIN_CONFIDENCE, minConfidence.name());
        canonicalMap.put(KEY_SCALE_REQUIRE_CENTERLINES, Boolean.toString(requireCenterlines));
        canonicalMap.put(KEY_SCALE_MIN_CONTINUITY, formatDouble(minContinuity));
        canonicalMap.put(KEY_SCALE_EXPECTED_PX, formatDouble(expectedPx));
        canonicalMap.put(KEY_SCALE_MAX_REL_DEV, formatDouble(maxRelDev));
        canonicalMap.put(KEY_PRECHECK_TIMEOUT, Integer.toString(timeout));
        canonicalMap.put(KEY_PRECHECK_CONTINUE, Boolean.toString(continueAfterFailure));
        canonicalMap.put(KEY_CLASSIFICATION_TILE_PROFILES, joinTileProfiles(tileProfiles));
        canonicalMap.put(KEY_CLASSIFICATION_THREADS_PER_TILE, Integer.toString(threadsPerTile));
        canonicalMap.put(KEY_CLASSIFICATION_SEAM_AUDIT_HALF_WIDTH, Integer.toString(seamAuditHalfWidthPx));
        canonicalMap.put(KEY_CLASSIFICATION_RUN_UNTILED_REFERENCE,
                Boolean.toString(runUntiledReferenceComparison));
        canonicalMap.put(KEY_RECONSTRUCTION_MIN_LUMEN_AREA,
                Integer.toString(reconstructionMinimumLumenAreaPx));
        canonicalMap.put(KEY_FULL_TIMEOUT, Integer.toString(fullTimeoutMinutes));
        canonicalMap.put(KEY_FULL_REFRESH_AGGREGATE, Boolean.toString(refreshAggregateTables));

        final String canonical = buildCanonicalText(canonicalMap);
        final String sha = Sha256Files.toHex(
                Sha256Files.newDigest().digest(canonical.getBytes(StandardCharsets.UTF_8)));
        return new BatchConfiguration(
                schemaVersion,
                recursive,
                requireComplete,
                machinesCount,
                downsample,
                expansion,
                cropPadding,
                minIncluded,
                maxIncluded,
                minMajor,
                maxSecondary,
                physical,
                minConfidence,
                requireCenterlines,
                minContinuity,
                expectedPx,
                maxRelDev,
                timeout,
                continueAfterFailure,
                tileProfiles,
                threadsPerTile,
                seamAuditHalfWidthPx,
                runUntiledReferenceComparison,
                reconstructionMinimumLumenAreaPx,
                fullTimeoutMinutes,
                refreshAggregateTables,
                canonical,
                sha);
    }

    private static List<BatchTileProfile> parseTileProfiles(final String raw, final String key) {
        final String[] tokens = raw.trim().split(";", -1);
        final List<BatchTileProfile> profiles = new java.util.ArrayList<BatchTileProfile>();
        final LinkedHashSet<String> seenTokens = new LinkedHashSet<String>();
        for (int i = 0; i < tokens.length; i++) {
            final String token = tokens[i].trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException(key + " must not contain blank tile profile tokens");
            }
            final BatchTileProfile profile;
            try {
                profile = BatchTileProfile.parseToken(token);
            } catch (final IllegalArgumentException ex) {
                throw new IllegalArgumentException(key + " has an invalid tile profile token: "
                        + token + " (" + ex.getMessage() + ")", ex);
            }
            if (!seenTokens.add(profile.toToken())) {
                throw new IllegalArgumentException(
                        key + " contains a duplicate tile profile: " + profile.toToken());
            }
            profiles.add(profile);
        }
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException(key + " must contain at least one tile profile");
        }
        return Collections.unmodifiableList(profiles);
    }

    private static String joinTileProfiles(final List<BatchTileProfile> profiles) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(profiles.get(i).toToken());
        }
        return sb.toString();
    }

    static Map<String, String> parseStrictProperties(final List<String> lines) {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            final int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(
                        "Invalid properties line " + (i + 1) + ": " + line);
            }
            final String key = line.substring(0, eq).trim();
            final String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blank configuration key at line " + (i + 1));
            }
            if (map.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Duplicate configuration key: " + key);
            }
            map.put(key, value);
        }
        return map;
    }

    private static String buildCanonicalText(final Map<String, String> sorted) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static boolean parseStrictBoolean(final String raw, final String key) {
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new IllegalArgumentException(
                key + " must be exactly true or false, found: " + raw);
    }

    private static int parseInt(final String raw, final String key) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, ex);
        }
    }

    private static double parseFiniteDouble(final String raw, final String key) {
        try {
            final double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(key + " must be finite: " + raw);
            }
            return value;
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a number: " + raw, ex);
        }
    }

    private static void requireUnitInterval(final double value, final String key) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(key + " must be between 0 and 1");
        }
    }

    private static ScaleBarDetectionConfidence parseConfidence(
            final String raw, final String key) {
        try {
            return ScaleBarDetectionConfidence.valueOf(raw.trim());
        } catch (final RuntimeException ex) {
            throw new IllegalArgumentException(
                    key + " must be HIGH, MEDIUM, LOW, or FAILED, found: " + raw, ex);
        }
    }

    static String formatDouble(final double value) {
        return String.format(Locale.US, "%.16g", Double.valueOf(value));
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean recursive() {
        return recursive;
    }

    public boolean requireCompleteExpectedSet() {
        return requireCompleteExpectedSet;
    }

    public int machinesCount() {
        return machinesCount;
    }

    public int maskDownsampleFactor() {
        return maskDownsampleFactor;
    }

    public int maskExpansionMarginPx() {
        return maskExpansionMarginPx;
    }

    public int maskCropPaddingPx() {
        return maskCropPaddingPx;
    }

    public double maskMinimumIncludedFraction() {
        return maskMinimumIncludedFraction;
    }

    public double maskMaximumIncludedFraction() {
        return maskMaximumIncludedFraction;
    }

    public double maskMinimumMajorSpanFraction() {
        return maskMinimumMajorSpanFraction;
    }

    public double maskMaximumSecondaryComponentAreaFraction() {
        return maskMaximumSecondaryComponentAreaFraction;
    }

    public double scaleBarPhysicalLengthUm() {
        return scaleBarPhysicalLengthUm;
    }

    public ScaleBarDetectionConfidence scaleBarMinimumConfidence() {
        return scaleBarMinimumConfidence;
    }

    public boolean scaleBarRequireTerminalTickCenterlines() {
        return scaleBarRequireTerminalTickCenterlines;
    }

    public double scaleBarMinimumShaftContinuity() {
        return scaleBarMinimumShaftContinuity;
    }

    public double scaleBarExpectedPixelSizeUm() {
        return scaleBarExpectedPixelSizeUm;
    }

    public double scaleBarMaximumRelativePixelSizeDeviation() {
        return scaleBarMaximumRelativePixelSizeDeviation;
    }

    public int precheckTimeoutMinutes() {
        return precheckTimeoutMinutes;
    }

    public boolean precheckContinueAfterFailure() {
        return precheckContinueAfterFailure;
    }

    public List<BatchTileProfile> tileProfiles() {
        return tileProfiles;
    }

    public int threadsPerTile() {
        return threadsPerTile;
    }

    public int seamAuditHalfWidthPx() {
        return seamAuditHalfWidthPx;
    }

    public boolean runUntiledReferenceComparison() {
        return runUntiledReferenceComparison;
    }

    public int reconstructionMinimumLumenAreaPx() {
        return reconstructionMinimumLumenAreaPx;
    }

    public int fullTimeoutMinutes() {
        return fullTimeoutMinutes;
    }

    public boolean refreshAggregateTables() {
        return refreshAggregateTables;
    }

    public String canonicalText() {
        return canonicalText;
    }

    public String sha256() {
        return sha256;
    }
}
