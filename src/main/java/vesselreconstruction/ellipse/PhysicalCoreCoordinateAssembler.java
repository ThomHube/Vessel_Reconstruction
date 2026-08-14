package vesselreconstruction.ellipse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Deterministically enriches an already-complete local lumen dataset with
 * one-dimensional bark-to-center physical-core longitudinal coordinates.
 *
 * <p>Physical length of a production {@code TOP_TO_BOTTOM} segment is
 * {@code source_height_px * pixel_height_um} using the full original source
 * TIFF height from {@code image_manifest.csv}. Consecutive contiguous
 * segments of one core concatenate with no gap and no overlap. No TIFF is
 * opened and no two-dimensional registration is performed.</p>
 */
public final class PhysicalCoreCoordinateAssembler {

    private static final String COL_SAMPLE_ID = "sample_id";
    private static final String COL_SEGMENT_ID = "segment_id";
    private static final String COL_SEGMENT_ORDER = "segment_order";
    private static final String COL_SEGMENT_ORIENTATION = "segment_orientation";
    private static final String COL_SOURCE_HEIGHT_PX = "source_height_px";
    private static final String COL_PIXEL_HEIGHT_UM = "pixel_height_um";
    private static final String COL_GLOBAL_SEGMENT_START_UM = "global_segment_start_um";
    private static final String COL_GLOBAL_LONGITUDINAL_UM = "global_longitudinal_um";
    private static final String COL_SEGMENT_AXIS_POSITION_UM = "segment_axis_position_um";
    private static final String COL_ORIGINAL_HEIGHT_PX = "original_height_px";
    private static final String COL_LUMEN_UID = "lumen_uid";
    private static final String TEMP_SUFFIX = ".core-assembly.tmp";

    /**
     * Assembles cumulative physical-core coordinates into the canonical CSVs
     * under {@code datasetRoot}.
     *
     * @param datasetRoot existing {@code lumen_dataset_*} root
     * @throws IOException if the dataset is missing, scientifically invalid,
     *                     or cannot be replaced safely
     */
    public void assemble(final Path datasetRoot) throws IOException {
        if (datasetRoot == null) {
            throw new IOException("datasetRoot must not be null");
        }
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(datasetRoot);
        if (!layout.isAccessibleDirectory()) {
            throw new IOException("Dataset root is not an accessible directory: "
                    + layout.getDatasetRoot());
        }

        final Path segmentManifestPath = layout.segmentManifest();
        final Path imageManifestPath = layout.imageManifest();
        final Path aggregatePath = layout.dataLumenMeasurementsAll();
        requireRegularFile(segmentManifestPath, "segment_manifest.csv");
        requireRegularFile(imageManifestPath, "image_manifest.csv");
        requireRegularFile(aggregatePath, "lumen_measurements_all.csv");

        final List<List<String>> segmentTable = ProductionCsvStore.readTable(segmentManifestPath);
        final List<List<String>> imageTable = ProductionCsvStore.readTable(imageManifestPath);
        final List<List<String>> aggregateTable = ProductionCsvStore.readTable(aggregatePath);
        requireNonEmptyTable(segmentTable, segmentManifestPath);
        requireNonEmptyTable(imageTable, imageManifestPath);
        requireNonEmptyTable(aggregateTable, aggregatePath);
        ProductionSchemas.requireExactHeader(
                segmentTable.get(0), ProductionSchemas.SEGMENT_MANIFEST_HEADER,
                segmentManifestPath.toString());
        ProductionSchemas.requireExactHeader(
                imageTable.get(0), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                imageManifestPath.toString());
        ProductionSchemas.requireExactHeader(
                aggregateTable.get(0), ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                aggregatePath.toString());

        if (segmentTable.size() < 2) {
            throw fail("segment_manifest.csv contains no segment rows");
        }

        final Map<String, ImageGeometry> imageBySegmentId = parseImageManifest(imageTable);
        final List<AssembledSegment> assembled = assembleSegments(segmentTable, imageBySegmentId);
        final Map<String, AssembledSegment> bySegmentId = indexBySegmentId(assembled);

        final List<Path> perSegmentPaths = new ArrayList<Path>();
        final List<List<List<String>>> perSegmentTables = new ArrayList<List<List<String>>>();
        for (int i = 0; i < assembled.size(); i++) {
            final AssembledSegment segment = assembled.get(i);
            final Path perSegmentPath = layout.dataLumenMeasurementsPerSegment().resolve(
                    FilenameSanitizer.lumenMeasurementsFilename(
                            FilenameSanitizer.sanitizeForFilename(segment.sampleId),
                            FilenameSanitizer.sanitizeForFilename(segment.segmentId)));
            requireRegularFile(perSegmentPath,
                    "per-segment lumen measurements for " + segment.segmentId);
            final List<List<String>> table = ProductionCsvStore.readTable(perSegmentPath);
            requireNonEmptyTable(table, perSegmentPath);
            ProductionSchemas.requireExactHeader(
                    table.get(0), ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                    perSegmentPath.toString());
            perSegmentPaths.add(perSegmentPath);
            perSegmentTables.add(table);
        }

        final List<List<String>> enrichedSegmentRows = enrichSegmentManifest(segmentTable, bySegmentId);
        final List<List<String>> enrichedAggregateRows = enrichMeasurements(
                aggregateTable, bySegmentId, aggregatePath);
        final List<List<List<String>>> enrichedPerSegmentRows = new ArrayList<List<List<String>>>();
        for (int i = 0; i < perSegmentTables.size(); i++) {
            enrichedPerSegmentRows.add(enrichMeasurements(
                    perSegmentTables.get(i), bySegmentId, perSegmentPaths.get(i)));
        }
        assertAggregateAgreesWithPerSegment(
                enrichedAggregateRows, enrichedPerSegmentRows, assembled);

        final List<PendingWrite> pending = new ArrayList<PendingWrite>();
        pending.add(new PendingWrite(segmentManifestPath,
                ProductionSchemas.SEGMENT_MANIFEST_HEADER, enrichedSegmentRows));
        pending.add(new PendingWrite(aggregatePath,
                ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, enrichedAggregateRows));
        for (int i = 0; i < perSegmentPaths.size(); i++) {
            pending.add(new PendingWrite(perSegmentPaths.get(i),
                    ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, enrichedPerSegmentRows.get(i)));
        }
        commitAll(pending);
    }

    private static List<AssembledSegment> assembleSegments(
            final List<List<String>> segmentTable,
            final Map<String, ImageGeometry> imageBySegmentId) throws IOException {
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.SEGMENT_MANIFEST_HEADER);
        final List<ParsedSegmentRow> parsed = new ArrayList<ParsedSegmentRow>();
        final Set<String> seenSegmentIds = new HashSet<String>();
        for (int r = 1; r < segmentTable.size(); r++) {
            parsed.add(parseSegmentRow(segmentTable.get(r), idx, r, seenSegmentIds));
        }

        final Map<String, List<ParsedSegmentRow>> byCore =
                new LinkedHashMap<String, List<ParsedSegmentRow>>();
        for (int i = 0; i < parsed.size(); i++) {
            final ParsedSegmentRow row = parsed.get(i);
            List<ParsedSegmentRow> coreRows = byCore.get(row.sampleId);
            if (coreRows == null) {
                coreRows = new ArrayList<ParsedSegmentRow>();
                byCore.put(row.sampleId, coreRows);
            }
            coreRows.add(row);
        }

        final List<AssembledSegment> assembled = new ArrayList<AssembledSegment>();
        for (final Map.Entry<String, List<ParsedSegmentRow>> entry : byCore.entrySet()) {
            assembled.addAll(assembleOneCore(entry.getKey(), entry.getValue(), imageBySegmentId));
        }
        return assembled;
    }

    private static List<AssembledSegment> assembleOneCore(
            final String sampleId,
            final List<ParsedSegmentRow> coreRows,
            final Map<String, ImageGeometry> imageBySegmentId) throws IOException {
        final Set<Integer> seenOrders = new HashSet<Integer>();
        for (int i = 0; i < coreRows.size(); i++) {
            final ParsedSegmentRow row = coreRows.get(i);
            if (!seenOrders.add(Integer.valueOf(row.segmentOrder))) {
                throw fail("Duplicate segment_order " + row.segmentOrder
                        + " within sample_id " + sampleId);
            }
        }
        final List<ParsedSegmentRow> ordered = new ArrayList<ParsedSegmentRow>(coreRows);
        Collections.sort(ordered, new Comparator<ParsedSegmentRow>() {
            @Override
            public int compare(final ParsedSegmentRow a, final ParsedSegmentRow b) {
                return Integer.compare(a.segmentOrder, b.segmentOrder);
            }
        });
        requireContiguousFromOne(sampleId, ordered);

        double cumulativeStartUm = 0.0;
        final List<AssembledSegment> assembled = new ArrayList<AssembledSegment>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            final ParsedSegmentRow row = ordered.get(i);
            final ImageGeometry geometry = imageBySegmentId.get(row.segmentId);
            if (geometry == null) {
                throw fail("image_manifest.csv has no row for segment_id " + row.segmentId);
            }
            if (!sampleId.equals(geometry.sampleId) || row.segmentOrder != geometry.segmentOrder) {
                throw fail("image_manifest identity does not match segment_manifest for "
                        + row.segmentId);
            }
            if (geometry.orientation != SegmentOrientation.TOP_TO_BOTTOM) {
                throw fail("image_manifest segment_orientation must be TOP_TO_BOTTOM for "
                        + row.segmentId + ", found " + geometry.orientation.name());
            }
            final double lengthUm = geometry.sourceHeightPx * geometry.pixelHeightUm;
            if (!Double.isFinite(lengthUm) || !(lengthUm > 0.0)) {
                throw fail("Physical segment length is not a positive finite value for "
                        + row.segmentId);
            }
            if (!Double.isFinite(cumulativeStartUm)) {
                throw fail("Cumulative global_segment_start_um is not finite for "
                        + row.segmentId);
            }
            assembled.add(new AssembledSegment(
                    row.sampleId,
                    row.segmentId,
                    row.segmentOrder,
                    geometry.sourceHeightPx,
                    geometry.pixelHeightUm,
                    lengthUm,
                    cumulativeStartUm));
            cumulativeStartUm += lengthUm;
        }
        return assembled;
    }

    private static void requireContiguousFromOne(
            final String sampleId,
            final List<ParsedSegmentRow> ordered) throws IOException {
        if (ordered.isEmpty()) {
            throw fail("Physical core " + sampleId + " has no segments");
        }
        if (ordered.get(0).segmentOrder != 1) {
            throw fail("Physical core " + sampleId
                    + " must start at segment_order 1, found "
                    + ordered.get(0).segmentOrder);
        }
        for (int i = 0; i < ordered.size(); i++) {
            final int expected = i + 1;
            final int actual = ordered.get(i).segmentOrder;
            if (actual != expected) {
                throw fail("Physical core " + sampleId
                        + " is not a contiguous 1..n sequence; expected segment_order "
                        + expected + " but found " + actual);
            }
        }
        if (ordered.size() > 5) {
            throw fail("Physical core " + sampleId + " has more than 5 segments");
        }
    }

    private static ParsedSegmentRow parseSegmentRow(
            final List<String> row,
            final Map<String, Integer> idx,
            final int rowNumber,
            final Set<String> seenSegmentIds) throws IOException {
        requireColumnCount(row, idx.size(), "segment_manifest.csv row " + rowNumber);
        final String sampleId = cell(row, idx, COL_SAMPLE_ID);
        final String segmentId = cell(row, idx, COL_SEGMENT_ID);
        final CoreSegmentId identity;
        try {
            identity = CoreSegmentId.parse(segmentId);
        } catch (final IllegalArgumentException ex) {
            throw fail("segment_id is not a valid PPTTCS identity at segment_manifest.csv row "
                    + rowNumber + ": " + segmentId + " (" + ex.getMessage() + ")");
        }
        if (!identity.sampleId().equals(sampleId)) {
            throw fail("sample_id " + sampleId + " does not equal segment_id without the trailing "
                    + "segment digit (" + identity.sampleId() + ") at segment_manifest.csv row "
                    + rowNumber);
        }
        final int segmentOrder = parsePositiveInt(
                cell(row, idx, COL_SEGMENT_ORDER),
                "segment_order at segment_manifest.csv row " + rowNumber);
        if (segmentOrder < 1 || segmentOrder > 5) {
            throw fail("segment_order must be in 1..5 at segment_manifest.csv row "
                    + rowNumber + ", found " + segmentOrder);
        }
        if (segmentOrder != identity.segmentOrder()) {
            throw fail("segment_order " + segmentOrder
                    + " does not equal the trailing segment digit of " + segmentId
                    + " at segment_manifest.csv row " + rowNumber);
        }
        requireTopToBottom(cell(row, idx, COL_SEGMENT_ORIENTATION),
                "segment_manifest.csv row " + rowNumber + " segment_id " + segmentId);
        if (!seenSegmentIds.add(segmentId)) {
            throw fail("Duplicate segment_id " + segmentId + " in segment_manifest.csv");
        }
        return new ParsedSegmentRow(sampleId, segmentId, segmentOrder);
    }

    private static Map<String, ImageGeometry> parseImageManifest(
            final List<List<String>> imageTable) throws IOException {
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.IMAGE_MANIFEST_HEADER);
        final Map<String, ImageGeometry> bySegmentId = new LinkedHashMap<String, ImageGeometry>();
        for (int r = 1; r < imageTable.size(); r++) {
            final List<String> row = imageTable.get(r);
            requireColumnCount(row, idx.size(), "image_manifest.csv row " + r);
            final String sampleId = cell(row, idx, COL_SAMPLE_ID);
            final String segmentId = cell(row, idx, COL_SEGMENT_ID);
            final CoreSegmentId identity;
            try {
                identity = CoreSegmentId.parse(segmentId);
            } catch (final IllegalArgumentException ex) {
                throw fail("image_manifest.csv segment_id is not a valid PPTTCS identity at row "
                        + r + ": " + segmentId);
            }
            if (!identity.sampleId().equals(sampleId)) {
                throw fail("image_manifest.csv sample_id/segment_id mismatch at row " + r);
            }
            final int segmentOrder = parsePositiveInt(
                    cell(row, idx, COL_SEGMENT_ORDER),
                    "image_manifest.csv segment_order at row " + r);
            if (segmentOrder != identity.segmentOrder()) {
                throw fail("image_manifest.csv segment_order does not match trailing digit at row "
                        + r);
            }
            final SegmentOrientation orientation = parseOrientationOrFail(
                    cell(row, idx, COL_SEGMENT_ORIENTATION),
                    "image_manifest.csv row " + r);
            final int sourceHeightPx = parsePositiveInt(
                    cell(row, idx, COL_SOURCE_HEIGHT_PX),
                    "source_height_px at image_manifest.csv row " + r);
            final double pixelHeightUm = parsePositiveFiniteDouble(
                    cell(row, idx, COL_PIXEL_HEIGHT_UM),
                    "pixel_height_um at image_manifest.csv row " + r);
            if (bySegmentId.containsKey(segmentId)) {
                throw fail("Duplicate segment_id " + segmentId + " in image_manifest.csv");
            }
            bySegmentId.put(segmentId, new ImageGeometry(
                    sampleId, segmentId, segmentOrder, orientation, sourceHeightPx, pixelHeightUm));
        }
        return bySegmentId;
    }

    private static Map<String, AssembledSegment> indexBySegmentId(
            final List<AssembledSegment> assembled) throws IOException {
        final Map<String, AssembledSegment> map = new HashMap<String, AssembledSegment>();
        for (int i = 0; i < assembled.size(); i++) {
            final AssembledSegment segment = assembled.get(i);
            if (map.put(segment.segmentId, segment) != null) {
                throw fail("Duplicate assembled segment_id " + segment.segmentId);
            }
        }
        return map;
    }

    private static List<List<String>> enrichSegmentManifest(
            final List<List<String>> table,
            final Map<String, AssembledSegment> bySegmentId) throws IOException {
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(table.get(0));
        final int startIndex = requiredIndex(idx, COL_GLOBAL_SEGMENT_START_UM);
        final int segmentIdIndex = requiredIndex(idx, COL_SEGMENT_ID);
        final List<List<String>> body = new ArrayList<List<String>>(table.size() - 1);
        for (int r = 1; r < table.size(); r++) {
            final List<String> copy = new ArrayList<String>(table.get(r));
            final String segmentId = copy.get(segmentIdIndex);
            final AssembledSegment segment = bySegmentId.get(segmentId);
            if (segment == null) {
                throw fail("No assembled start for segment_id " + segmentId);
            }
            copy.set(startIndex, resolveExistingNumeric(
                    copy.get(startIndex),
                    segment.globalSegmentStartUm,
                    COL_GLOBAL_SEGMENT_START_UM,
                    "segment_id " + segmentId));
            body.add(copy);
        }
        return body;
    }

    private static List<List<String>> enrichMeasurements(
            final List<List<String>> table,
            final Map<String, AssembledSegment> bySegmentId,
            final Path source) throws IOException {
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(table.get(0));
        final int sampleIndex = requiredIndex(idx, COL_SAMPLE_ID);
        final int segmentIdIndex = requiredIndex(idx, COL_SEGMENT_ID);
        final int orderIndex = requiredIndex(idx, COL_SEGMENT_ORDER);
        final int orientationIndex = requiredIndex(idx, COL_SEGMENT_ORIENTATION);
        final int axisIndex = requiredIndex(idx, COL_SEGMENT_AXIS_POSITION_UM);
        final int globalIndex = requiredIndex(idx, COL_GLOBAL_LONGITUDINAL_UM);
        final int originalHeightIndex = requiredIndex(idx, COL_ORIGINAL_HEIGHT_PX);
        final int pixelHeightIndex = requiredIndex(idx, COL_PIXEL_HEIGHT_UM);
        final int lumenUidIndex = requiredIndex(idx, COL_LUMEN_UID);
        final List<List<String>> body = new ArrayList<List<String>>(Math.max(0, table.size() - 1));
        for (int r = 1; r < table.size(); r++) {
            final List<String> copy = new ArrayList<String>(table.get(r));
            requireColumnCount(copy, table.get(0).size(), source + " row " + r);
            final String segmentId = copy.get(segmentIdIndex);
            final String lumenUid = copy.get(lumenUidIndex);
            final AssembledSegment segment = bySegmentId.get(segmentId);
            if (segment == null) {
                throw fail("Measurement row in " + source
                        + " refers to unknown segment_id " + segmentId
                        + " (lumen_uid=" + lumenUid + ")");
            }
            if (!segment.sampleId.equals(copy.get(sampleIndex))) {
                throw fail("Measurement sample_id does not match assembled segment for "
                        + lumenUid);
            }
            final int order = parsePositiveInt(copy.get(orderIndex),
                    "segment_order for lumen_uid " + lumenUid);
            if (order != segment.segmentOrder) {
                throw fail("Measurement segment_order does not match assembled segment for "
                        + lumenUid);
            }
            requireTopToBottom(copy.get(orientationIndex),
                    "measurement lumen_uid " + lumenUid);
            final String originalHeightRaw = copy.get(originalHeightIndex);
            if (!isMissing(originalHeightRaw)) {
                final int originalHeightPx = parsePositiveInt(
                        originalHeightRaw, "original_height_px for lumen_uid " + lumenUid);
                if (originalHeightPx != segment.sourceHeightPx) {
                    throw fail("original_height_px " + originalHeightPx
                            + " does not match image_manifest source_height_px "
                            + segment.sourceHeightPx + " for lumen_uid " + lumenUid);
                }
            }
            final String pixelHeightRaw = copy.get(pixelHeightIndex);
            if (!isMissing(pixelHeightRaw)) {
                final double pixelHeightUm = parsePositiveFiniteDouble(
                        pixelHeightRaw, "pixel_height_um for lumen_uid " + lumenUid);
                if (Double.compare(pixelHeightUm, segment.pixelHeightUm) != 0) {
                    throw fail("pixel_height_um on measurement row does not match image_manifest "
                            + "for lumen_uid " + lumenUid);
                }
            }
            final double axisUm = parseFiniteDouble(
                    copy.get(axisIndex),
                    "segment_axis_position_um for lumen_uid " + lumenUid);
            if (axisUm < 0.0 || axisUm >= segment.lengthUm) {
                throw fail("segment_axis_position_um is outside [0, source_height_px"
                        + " * pixel_height_um) for lumen_uid " + lumenUid
                        + ": axis=" + RCsvSupport.formatNumber(axisUm)
                        + " length=" + RCsvSupport.formatNumber(segment.lengthUm));
            }
            final double globalUm = segment.globalSegmentStartUm + axisUm;
            if (!Double.isFinite(globalUm)) {
                throw fail("global_longitudinal_um is not finite for lumen_uid " + lumenUid);
            }
            copy.set(globalIndex, resolveExistingNumeric(
                    copy.get(globalIndex),
                    globalUm,
                    COL_GLOBAL_LONGITUDINAL_UM,
                    "lumen_uid " + lumenUid));
            body.add(copy);
        }
        return body;
    }

    private static void assertAggregateAgreesWithPerSegment(
            final List<List<String>> aggregateRows,
            final List<List<List<String>>> perSegmentRows,
            final List<AssembledSegment> assembled) throws IOException {
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(
                ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
        final int uidIndex = requiredIndex(idx, COL_LUMEN_UID);
        final int globalIndex = requiredIndex(idx, COL_GLOBAL_LONGITUDINAL_UM);
        final Map<String, String> aggregateGlobals = new HashMap<String, String>();
        for (int i = 0; i < aggregateRows.size(); i++) {
            final List<String> row = aggregateRows.get(i);
            final String uid = row.get(uidIndex);
            if (aggregateGlobals.put(uid, row.get(globalIndex)) != null) {
                throw fail("Duplicate lumen_uid in aggregate measurements: " + uid);
            }
        }
        final Set<String> perSegmentUids = new HashSet<String>();
        for (int t = 0; t < perSegmentRows.size(); t++) {
            final List<List<String>> table = perSegmentRows.get(t);
            for (int i = 0; i < table.size(); i++) {
                final List<String> row = table.get(i);
                final String uid = row.get(uidIndex);
                if (!perSegmentUids.add(uid)) {
                    throw fail("Duplicate lumen_uid in per-segment measurements: " + uid);
                }
                final String aggregateValue = aggregateGlobals.get(uid);
                if (aggregateValue == null) {
                    throw fail("Per-segment lumen_uid " + uid
                            + " is missing from lumen_measurements_all.csv");
                }
                if (!aggregateValue.equals(row.get(globalIndex))) {
                    throw fail("Aggregate and per-segment global_longitudinal_um disagree for "
                            + uid);
                }
            }
        }
        if (aggregateGlobals.size() != perSegmentUids.size()) {
            throw fail("Aggregate and per-segment measurement tables do not contain the same "
                    + "lumen rows");
        }
        if (assembled.isEmpty()) {
            throw fail("No assembled segments");
        }
    }

    private static String resolveExistingNumeric(
            final String current,
            final double expected,
            final String field,
            final String rowId) throws IOException {
        if (!Double.isFinite(expected)) {
            throw fail("Refusing to write non-finite " + field + " for " + rowId);
        }
        final String formatted = RCsvSupport.formatNumber(expected);
        if (formatted.indexOf("NaN") >= 0
                || formatted.toLowerCase(Locale.US).indexOf("inf") >= 0) {
            throw fail("Formatted " + field + " is non-finite for " + rowId + ": " + formatted);
        }
        if (isMissing(current)) {
            return formatted;
        }
        final double existing = parseFiniteDouble(current, field + " for " + rowId);
        if (Double.compare(existing, expected) == 0) {
            return current;
        }
        throw fail("Conflicting existing " + field + " for " + rowId
                + ": existing=" + current
                + " expected=" + formatted);
    }

    private static void commitAll(final List<PendingWrite> pending) throws IOException {
        final List<Path> temps = new ArrayList<Path>();
        try {
            for (int i = 0; i < pending.size(); i++) {
                final PendingWrite write = pending.get(i);
                Files.createDirectories(write.target.getParent());
                final Path temp = Files.createTempFile(
                        write.target.getParent(),
                        write.target.getFileName().toString() + ".",
                        TEMP_SUFFIX);
                temps.add(temp);
                writeCompleteCsv(temp, write.header, write.rows);
            }
            for (int i = 0; i < pending.size(); i++) {
                replaceAtomically(temps.get(i), pending.get(i).target);
            }
        } catch (final IOException ex) {
            deleteAll(temps);
            throw ex;
        } catch (final RuntimeException ex) {
            deleteAll(temps);
            throw ex;
        } finally {
            deleteAll(temps);
        }
    }

    private static void writeCompleteCsv(
            final Path temp,
            final List<String> header,
            final List<List<String>> rows) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
            writer.write(RCsvSupport.joinRow(header));
            writer.newLine();
            for (int i = 0; i < rows.size(); i++) {
                final List<String> row = rows.get(i);
                if (row.size() != header.size()) {
                    throw new IOException("Row/header column mismatch while writing " + temp);
                }
                rejectNonFiniteText(row);
                writer.write(RCsvSupport.joinRow(row));
                writer.newLine();
            }
        }
    }

    private static void rejectNonFiniteText(final List<String> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            final String value = row.get(i);
            if (value == null) {
                continue;
            }
            if (value.indexOf("NaN") >= 0
                    || "Infinity".equals(value)
                    || "-Infinity".equals(value)
                    || "Inf".equals(value)
                    || "-Inf".equals(value)) {
                throw fail("Refusing to write non-finite CSV value: " + value);
            }
        }
    }

    private static void replaceAtomically(final Path temp, final Path target) throws IOException {
        try {
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteAll(final List<Path> temps) {
        for (int i = 0; i < temps.size(); i++) {
            try {
                Files.deleteIfExists(temps.get(i));
            } catch (final IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void requireRegularFile(final Path path, final String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw fail("Required CSV is missing: " + label + " (" + path + ")");
        }
    }

    private static void requireNonEmptyTable(final List<List<String>> table, final Path path)
            throws IOException {
        if (table == null || table.isEmpty()) {
            throw fail("CSV is empty: " + path);
        }
    }

    private static void requireColumnCount(
            final List<String> row,
            final int expected,
            final String where) throws IOException {
        if (row == null || row.size() != expected) {
            throw fail("Column count mismatch at " + where);
        }
    }

    private static int requiredIndex(final Map<String, Integer> idx, final String column)
            throws IOException {
        final Integer value = idx.get(column);
        if (value == null) {
            throw fail("Missing required column " + column);
        }
        return value.intValue();
    }

    private static String cell(
            final List<String> row,
            final Map<String, Integer> idx,
            final String column) throws IOException {
        return row.get(requiredIndex(idx, column));
    }

    private static void requireTopToBottom(final String raw, final String where) throws IOException {
        if (!SegmentOrientation.TOP_TO_BOTTOM.name().equals(raw)) {
            throw fail("segment_orientation must be TOP_TO_BOTTOM at " + where
                    + ", found " + raw);
        }
    }

    private static SegmentOrientation parseOrientationOrFail(
            final String raw,
            final String where) throws IOException {
        if (raw == null || raw.isEmpty() || RCsvSupport.NA.equals(raw)) {
            throw fail("segment_orientation is missing at " + where);
        }
        try {
            return SegmentOrientation.valueOf(raw);
        } catch (final IllegalArgumentException ex) {
            throw fail("Malformed segment_orientation at " + where + ": " + raw);
        }
    }

    private static boolean isMissing(final String raw) {
        return raw == null || raw.isEmpty() || RCsvSupport.NA.equals(raw);
    }

    private static int parsePositiveInt(final String raw, final String field) throws IOException {
        if (isMissing(raw)) {
            throw fail(field + " is NA");
        }
        final int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (final NumberFormatException ex) {
            throw fail(field + " is not an integer: " + raw);
        }
        if (value <= 0) {
            throw fail(field + " must be > 0, found " + raw);
        }
        return value;
    }

    private static double parsePositiveFiniteDouble(
            final String raw,
            final String field) throws IOException {
        final double value = parseFiniteDouble(raw, field);
        if (!(value > 0.0)) {
            throw fail(field + " must be > 0 and finite, found " + raw);
        }
        return value;
    }

    private static double parseFiniteDouble(final String raw, final String field) throws IOException {
        if (isMissing(raw)) {
            throw fail(field + " is NA");
        }
        final double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (final NumberFormatException ex) {
            throw fail(field + " is not numeric: " + raw);
        }
        if (!Double.isFinite(value)) {
            throw fail(field + " is not finite: " + raw);
        }
        return value;
    }

    private static IOException fail(final String message) {
        return new IOException(message);
    }

    private static final class ParsedSegmentRow {
        final String sampleId;
        final String segmentId;
        final int segmentOrder;

        ParsedSegmentRow(final String sampleId, final String segmentId, final int segmentOrder) {
            this.sampleId = sampleId;
            this.segmentId = segmentId;
            this.segmentOrder = segmentOrder;
        }
    }

    private static final class ImageGeometry {
        final String sampleId;
        final String segmentId;
        final int segmentOrder;
        final SegmentOrientation orientation;
        final int sourceHeightPx;
        final double pixelHeightUm;

        ImageGeometry(
                final String sampleId,
                final String segmentId,
                final int segmentOrder,
                final SegmentOrientation orientation,
                final int sourceHeightPx,
                final double pixelHeightUm) {
            this.sampleId = sampleId;
            this.segmentId = segmentId;
            this.segmentOrder = segmentOrder;
            this.orientation = orientation;
            this.sourceHeightPx = sourceHeightPx;
            this.pixelHeightUm = pixelHeightUm;
        }
    }

    private static final class AssembledSegment {
        final String sampleId;
        final String segmentId;
        final int segmentOrder;
        final int sourceHeightPx;
        final double pixelHeightUm;
        final double lengthUm;
        final double globalSegmentStartUm;

        AssembledSegment(
                final String sampleId,
                final String segmentId,
                final int segmentOrder,
                final int sourceHeightPx,
                final double pixelHeightUm,
                final double lengthUm,
                final double globalSegmentStartUm) {
            this.sampleId = sampleId;
            this.segmentId = segmentId;
            this.segmentOrder = segmentOrder;
            this.sourceHeightPx = sourceHeightPx;
            this.pixelHeightUm = pixelHeightUm;
            this.lengthUm = lengthUm;
            this.globalSegmentStartUm = globalSegmentStartUm;
        }
    }

    private static final class PendingWrite {
        final Path target;
        final List<String> header;
        final List<List<String>> rows;

        PendingWrite(
                final Path target,
                final List<String> header,
                final List<List<String>> rows) {
            this.target = target;
            this.header = header;
            this.rows = rows;
        }
    }
}
