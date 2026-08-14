package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalCoreCoordinateAssemblerTest {

    private static final String SAMPLE = "0105A";
    private static final String SEG1 = "0105A1";
    private static final String SEG2 = "0105A2";
    private static final String SEG3 = "0105A3";
    private static final PhysicalCoreCoordinateAssembler ASSEMBLER =
            new PhysicalCoreCoordinateAssembler();

    @Test
    void singleSegmentStartIsZeroAndGlobalEqualsAxis(@TempDir final Path root) throws Exception {
        assertAssemblerDoesNotOpenImages();
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 100, 2.0)),
                Arrays.asList(lumen(SEG1, "L1", 30.0, "keep-area-1")));
        ASSEMBLER.assemble(fixture.root);

        assertEquals(RCsvSupport.formatNumber(0.0), startUm(fixture, SEG1));
        assertEquals(RCsvSupport.formatNumber(30.0), globalUm(fixture.aggregate(), "L1"));
        assertEquals(RCsvSupport.formatNumber(30.0), globalUm(fixture.perSegment(SEG1), "L1"));
        assertEquals(SAMPLE, sampleId(fixture, SEG1));
        assertEquals(SEG1, segmentId(fixture, SEG1));
        assertNoTiffFiles(fixture.root);
    }

    @Test
    void twoSegmentsAccumulateExactStarts(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 100, 2.0),
                segment(SEG2, 50, 2.0)),
                Arrays.asList(
                        lumen(SEG1, "L1", 10.0, "a1"),
                        lumen(SEG2, "L2", 30.0, "a2")));
        ASSEMBLER.assemble(fixture.root);

        assertEquals(RCsvSupport.formatNumber(0.0), startUm(fixture, SEG1));
        assertEquals(RCsvSupport.formatNumber(200.0), startUm(fixture, SEG2));
        assertEquals(RCsvSupport.formatNumber(230.0), globalUm(fixture.aggregate(), "L2"));
        assertEquals(RCsvSupport.formatNumber(230.0), globalUm(fixture.perSegment(SEG2), "L2"));
    }

    @Test
    void threeSegmentsNonuniformLengthsAccumulate(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 1.0),
                segment(SEG2, 25, 1.0),
                segment(SEG3, 7, 1.0)),
                Arrays.asList(
                        lumen(SEG1, "L1", 1.0, "a"),
                        lumen(SEG2, "L2", 2.0, "b"),
                        lumen(SEG3, "L3", 3.0, "c")));
        ASSEMBLER.assemble(fixture.root);

        assertEquals(RCsvSupport.formatNumber(0.0), startUm(fixture, SEG1));
        assertEquals(RCsvSupport.formatNumber(10.0), startUm(fixture, SEG2));
        assertEquals(RCsvSupport.formatNumber(35.0), startUm(fixture, SEG3));
        assertEquals(RCsvSupport.formatNumber(38.0), globalUm(fixture.aggregate(), "L3"));
    }

    @Test
    void nonuniformPixelCalibrationUsesPerSegmentScale(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 2.0),
                segment(SEG2, 10, 0.5),
                segment(SEG3, 4, 3.0)),
                Arrays.asList(
                        lumen(SEG1, "L1", 1.0, "a"),
                        lumen(SEG2, "L2", 2.0, "b"),
                        lumen(SEG3, "L3", 5.0, "c")));
        ASSEMBLER.assemble(fixture.root);

        assertEquals(RCsvSupport.formatNumber(0.0), startUm(fixture, SEG1));
        assertEquals(RCsvSupport.formatNumber(20.0), startUm(fixture, SEG2));
        assertEquals(RCsvSupport.formatNumber(25.0), startUm(fixture, SEG3));
        assertEquals(RCsvSupport.formatNumber(30.0), globalUm(fixture.aggregate(), "L3"));
        assertEquals(RCsvSupport.FALSE, continuation(fixture, SEG2));
        assertEquals(RCsvSupport.NA, previousSegment(fixture, SEG2));
        assertEquals(RCsvSupport.NA, gapToPrevious(fixture, SEG2));
        assertEquals(RCsvSupport.NA, overlapWithPrevious(fixture, SEG2));
    }

    @Test
    void physicalLengthUsesNTimesPixelSizeNotNMinusOne(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 2.0),
                segment(SEG2, 5, 2.0)),
                Arrays.asList(
                        lumen(SEG1, "L-last-center", 19.0, "inside-N-px"),
                        lumen(SEG2, "L2", 1.0, "s2")));
        ASSEMBLER.assemble(fixture.root);

        assertEquals(RCsvSupport.formatNumber(20.0), startUm(fixture, SEG2));
        assertFalse(RCsvSupport.formatNumber(18.0).equals(startUm(fixture, SEG2)),
                "length must be N * pixel_size, not (N - 1) * pixel_size");
        assertEquals(RCsvSupport.formatNumber(19.0),
                globalUm(fixture.aggregate(), "L-last-center"));
        assertEquals(RCsvSupport.formatNumber(21.0), globalUm(fixture.aggregate(), "L2"));
    }

    @Test
    void missingSegmentOrderFailsWithoutChangingTargets(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 1.0),
                segment(SEG3, 10, 1.0)),
                Arrays.asList(
                        lumen(SEG1, "L1", 1.0, "a"),
                        lumen(SEG3, "L3", 1.0, "c")));
        final Map<Path, byte[]> before = snapshotTargets(fixture);
        final IOException ex = assertThrows(IOException.class, () -> ASSEMBLER.assemble(fixture.root));
        assertTrue(ex.getMessage().contains("contiguous")
                || ex.getMessage().contains("segment_order"), ex.getMessage());
        assertUnchanged(before);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void coreNotStartingAtOneFailsWithoutChangingTargets(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG2, 10, 1.0),
                segment(SEG3, 10, 1.0)),
                Arrays.asList(
                        lumen(SEG2, "L2", 1.0, "b"),
                        lumen(SEG3, "L3", 1.0, "c")));
        final Map<Path, byte[]> before = snapshotTargets(fixture);
        final IOException ex = assertThrows(IOException.class, () -> ASSEMBLER.assemble(fixture.root));
        assertTrue(ex.getMessage().contains("must start at segment_order 1"), ex.getMessage());
        assertUnchanged(before);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void duplicateOrderOrIdentityConflictFailsWithoutPartialWrites(@TempDir final Path root)
            throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 1.0),
                segment(SEG1, 8, 1.0)),
                Arrays.asList(lumen(SEG1, "L1", 1.0, "a")));
        final Map<Path, byte[]> before = snapshotTargets(fixture);
        assertThrows(IOException.class, () -> ASSEMBLER.assemble(fixture.root));
        assertUnchanged(before);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void unspecifiedOrientationFailsWithoutChangingTargets(@TempDir final Path root)
            throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 10, 1.0).orientation(SegmentOrientation.UNSPECIFIED.name())),
                Arrays.asList(lumen(SEG1, "L1", 1.0, "a")));
        final Map<Path, byte[]> before = snapshotTargets(fixture);
        final IOException ex = assertThrows(IOException.class, () -> ASSEMBLER.assemble(fixture.root));
        assertTrue(ex.getMessage().contains("TOP_TO_BOTTOM"), ex.getMessage());
        assertUnchanged(before);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void secondAssemblyIsByteIdentical(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 100, 2.0),
                segment(SEG2, 50, 2.0)),
                Arrays.asList(
                        lumen(SEG1, "L1", 10.0, "a1"),
                        lumen(SEG2, "L2", 30.0, "a2")));
        ASSEMBLER.assemble(fixture.root);
        final Map<Path, byte[]> afterFirst = snapshotTargets(fixture);
        ASSEMBLER.assemble(fixture.root);
        assertUnchanged(afterFirst);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void conflictingPreexistingGlobalValueFailsClosed(@TempDir final Path root) throws Exception {
        final Fixture fixture = Fixture.create(root, Arrays.asList(
                segment(SEG1, 100, 2.0),
                segment(SEG2, 50, 2.0).globalStart("199")),
                Arrays.asList(
                        lumen(SEG1, "L1", 10.0, "a1"),
                        lumen(SEG2, "L2", 30.0, "a2")));
        final Map<Path, byte[]> before = snapshotTargets(fixture);
        final IOException ex = assertThrows(IOException.class, () -> ASSEMBLER.assemble(fixture.root));
        assertTrue(ex.getMessage().contains("Conflicting existing global_segment_start_um"),
                ex.getMessage());
        assertUnchanged(before);
        assertNoAssemblyTemps(fixture.root);
    }

    @Test
    void aggregateAndPerSegmentAgreeWithoutNonfiniteValues(@TempDir final Path root)
            throws Exception {
        final List<SegmentSpec> segments = Arrays.asList(
                segment(SEG2, 50, 2.0).notes("row-order-seg2"),
                segment(SEG1, 100, 2.0).notes("row-order-seg1"));
        final List<LumenSpec> lumens = Arrays.asList(
                lumen(SEG2, "L2b", 40.0, "area-2b"),
                lumen(SEG1, "L1a", 5.0, "area-1a"),
                lumen(SEG2, "L2a", 11.0, "area-2a"),
                lumen(SEG1, "L1b", 17.0, "area-1b"));
        final Fixture fixture = Fixture.create(root, segments, lumens);
        final List<String> aggregateOrderBefore = lumenUidOrder(fixture.aggregate());
        final List<String> segmentOrderBefore = segmentIdOrder(fixture);
        final String imageManifestBefore = new String(
                Files.readAllBytes(fixture.layout.imageManifest()), StandardCharsets.UTF_8);

        ASSEMBLER.assemble(fixture.root);

        assertEquals(segmentOrderBefore, segmentIdOrder(fixture));
        assertEquals(aggregateOrderBefore, lumenUidOrder(fixture.aggregate()));
        assertEquals(imageManifestBefore, new String(
                Files.readAllBytes(fixture.layout.imageManifest()), StandardCharsets.UTF_8));
        assertEquals(RCsvSupport.formatNumber(200.0), startUm(fixture, SEG2));
        assertEquals(RCsvSupport.formatNumber(0.0), startUm(fixture, SEG1));
        assertEquals("row-order-seg2", notes(fixture, SEG2));
        assertEquals("row-order-seg1", notes(fixture, SEG1));

        assertSameGlobal(fixture, "L1a", 5.0);
        assertSameGlobal(fixture, "L1b", 17.0);
        assertSameGlobal(fixture, "L2a", 211.0);
        assertSameGlobal(fixture, "L2b", 240.0);
        assertEquals("area-2b", area(fixture.aggregate(), "L2b"));
        assertEquals("area-2b", area(fixture.perSegment(SEG2), "L2b"));
        assertEquals(RCsvSupport.NA, cell(fixture.aggregate(), "L1a", "global_x_um"));
        assertEquals(RCsvSupport.NA, cell(fixture.aggregate(), "L1a", "global_y_um"));
        assertEquals(RCsvSupport.NA, originX(fixture, SEG1));
        assertEquals(RCsvSupport.NA, originY(fixture, SEG1));
        assertNoNonFiniteText(fixture);
        assertNoTiffFiles(fixture.root);
        assertNoAssemblyTemps(fixture.root);
    }

    private static void assertSameGlobal(
            final Fixture fixture,
            final String lumenUid,
            final double expected) throws IOException {
        final String formatted = RCsvSupport.formatNumber(expected);
        assertEquals(formatted, globalUm(fixture.aggregate(), lumenUid));
        final String segmentId = cell(fixture.aggregate(), lumenUid, "segment_id");
        assertEquals(formatted, globalUm(fixture.perSegment(segmentId), lumenUid));
    }

    private static void assertAssemblerDoesNotOpenImages() throws IOException {
        final Path source = Paths.get(
                "src/main/java/vesselreconstruction/ellipse/PhysicalCoreCoordinateAssembler.java");
        assertTrue(Files.isRegularFile(source), "assembler source must be present for structural check");
        final String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertFalse(text.contains("import ij."), text);
        assertFalse(text.contains("ImagePlus"), text);
        assertFalse(text.contains("Opener"), text);
        assertFalse(text.contains(".tif"), text);
    }

    private static void assertNoTiffFiles(final Path root) throws IOException {
        final List<Path> tiffs = new ArrayList<Path>();
        collectTiffs(root, tiffs);
        assertTrue(tiffs.isEmpty(), "assembler fixtures must not contain TIFF files: " + tiffs);
    }

    private static void collectTiffs(final Path path, final List<Path> tiffs) throws IOException {
        if (Files.isRegularFile(path)) {
            final String name = path.getFileName().toString().toLowerCase();
            if (name.endsWith(".tif") || name.endsWith(".tiff")) {
                tiffs.add(path);
            }
            return;
        }
        if (!Files.isDirectory(path)) {
            return;
        }
        final java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path);
        try {
            for (final Path child : stream) {
                collectTiffs(child, tiffs);
            }
        } finally {
            stream.close();
        }
    }

    private static void assertNoNonFiniteText(final Fixture fixture) throws IOException {
        final List<Path> targets = targetFiles(fixture);
        for (int i = 0; i < targets.size(); i++) {
            final String text = new String(Files.readAllBytes(targets.get(i)), StandardCharsets.UTF_8);
            assertFalse(text.contains("NaN"), "NaN in " + targets.get(i));
            assertFalse(text.contains("Infinity"), "Infinity in " + targets.get(i));
            assertFalse(text.contains("-Infinity"), "-Infinity in " + targets.get(i));
        }
    }

    private static void assertNoAssemblyTemps(final Path root) throws IOException {
        final List<Path> temps = new ArrayList<Path>();
        collectTemps(root, temps);
        assertTrue(temps.isEmpty(), "temporary assembly files must be cleaned up: " + temps);
    }

    private static void collectTemps(final Path path, final List<Path> temps) throws IOException {
        if (Files.isRegularFile(path)) {
            if (path.getFileName().toString().endsWith(".core-assembly.tmp")) {
                temps.add(path);
            }
            return;
        }
        if (!Files.isDirectory(path)) {
            return;
        }
        final java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path);
        try {
            for (final Path child : stream) {
                collectTemps(child, temps);
            }
        } finally {
            stream.close();
        }
    }

    private static Map<Path, byte[]> snapshotTargets(final Fixture fixture) throws IOException {
        final Map<Path, byte[]> map = new LinkedHashMap<Path, byte[]>();
        final List<Path> targets = targetFiles(fixture);
        for (int i = 0; i < targets.size(); i++) {
            final Path path = targets.get(i);
            map.put(path, Files.readAllBytes(path));
        }
        map.put(fixture.layout.imageManifest(), Files.readAllBytes(fixture.layout.imageManifest()));
        return map;
    }

    private static List<Path> targetFiles(final Fixture fixture) throws IOException {
        final List<Path> paths = new ArrayList<Path>();
        paths.add(fixture.layout.segmentManifest());
        paths.add(fixture.layout.dataLumenMeasurementsAll());
        final java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(
                fixture.layout.dataLumenMeasurementsPerSegment(), "*.csv");
        try {
            for (final Path child : stream) {
                paths.add(child);
            }
        } finally {
            stream.close();
        }
        Collections.sort(paths);
        return paths;
    }

    private static void assertUnchanged(final Map<Path, byte[]> before) throws IOException {
        for (final Map.Entry<Path, byte[]> entry : before.entrySet()) {
            assertTrue(Files.isRegularFile(entry.getKey()), "missing after failure: " + entry.getKey());
            assertTrue(java.util.Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())),
                    "CSV changed although assembly must fail closed: " + entry.getKey());
        }
    }

    private static SegmentSpec segment(
            final String segmentId,
            final int sourceHeightPx,
            final double pixelHeightUm) {
        return new SegmentSpec(segmentId, sourceHeightPx, pixelHeightUm);
    }

    private static LumenSpec lumen(
            final String segmentId,
            final String lumenUid,
            final double axisUm,
            final String areaUm2) {
        return new LumenSpec(segmentId, lumenUid, axisUm, areaUm2);
    }

    private static String startUm(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "global_segment_start_um");
    }

    private static String sampleId(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "sample_id");
    }

    private static String segmentId(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "segment_id");
    }

    private static String notes(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "notes");
    }

    private static String continuation(final Fixture fixture, final String segmentId)
            throws IOException {
        return segmentCell(fixture, segmentId, "continuation_confirmed");
    }

    private static String previousSegment(final Fixture fixture, final String segmentId)
            throws IOException {
        return segmentCell(fixture, segmentId, "previous_segment_id");
    }

    private static String gapToPrevious(final Fixture fixture, final String segmentId)
            throws IOException {
        return segmentCell(fixture, segmentId, "gap_to_previous_um");
    }

    private static String overlapWithPrevious(final Fixture fixture, final String segmentId)
            throws IOException {
        return segmentCell(fixture, segmentId, "overlap_with_previous_um");
    }

    private static String originX(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "global_origin_x_um");
    }

    private static String originY(final Fixture fixture, final String segmentId) throws IOException {
        return segmentCell(fixture, segmentId, "global_origin_y_um");
    }

    private static String segmentCell(
            final Fixture fixture,
            final String segmentId,
            final String column) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(fixture.layout.segmentManifest());
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(table.get(0));
        final int idIndex = idx.get("segment_id").intValue();
        final int colIndex = idx.get(column).intValue();
        for (int r = 1; r < table.size(); r++) {
            if (segmentId.equals(table.get(r).get(idIndex))) {
                return table.get(r).get(colIndex);
            }
        }
        throw new AssertionError("missing segment_id " + segmentId);
    }

    private static String globalUm(final Path csv, final String lumenUid) throws IOException {
        return cell(csv, lumenUid, "global_longitudinal_um");
    }

    private static String area(final Path csv, final String lumenUid) throws IOException {
        return cell(csv, lumenUid, "area_um2");
    }

    private static String cell(final Path csv, final String lumenUid, final String column)
            throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(csv);
        final Map<String, Integer> idx = ProductionCsvStore.headerIndex(table.get(0));
        final int uidIndex = idx.get("lumen_uid").intValue();
        final int colIndex = idx.get(column).intValue();
        for (int r = 1; r < table.size(); r++) {
            if (lumenUid.equals(table.get(r).get(uidIndex))) {
                return table.get(r).get(colIndex);
            }
        }
        throw new AssertionError("missing lumen_uid " + lumenUid + " in " + csv);
    }

    private static List<String> lumenUidOrder(final Path csv) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(csv);
        final int uidIndex = table.get(0).indexOf("lumen_uid");
        final List<String> order = new ArrayList<String>();
        for (int r = 1; r < table.size(); r++) {
            order.add(table.get(r).get(uidIndex));
        }
        return order;
    }

    private static List<String> segmentIdOrder(final Fixture fixture) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(fixture.layout.segmentManifest());
        final int idIndex = table.get(0).indexOf("segment_id");
        final List<String> order = new ArrayList<String>();
        for (int r = 1; r < table.size(); r++) {
            order.add(table.get(r).get(idIndex));
        }
        return order;
    }

    private static final class SegmentSpec {
        final String segmentId;
        final int sourceHeightPx;
        final double pixelHeightUm;
        String orientation = SegmentOrientation.TOP_TO_BOTTOM.name();
        String globalStart = RCsvSupport.NA;
        String notes = RCsvSupport.NA;

        SegmentSpec(final String segmentId, final int sourceHeightPx, final double pixelHeightUm) {
            this.segmentId = segmentId;
            this.sourceHeightPx = sourceHeightPx;
            this.pixelHeightUm = pixelHeightUm;
        }

        SegmentSpec orientation(final String value) {
            this.orientation = value;
            return this;
        }

        SegmentSpec globalStart(final String value) {
            this.globalStart = value;
            return this;
        }

        SegmentSpec notes(final String value) {
            this.notes = value;
            return this;
        }
    }

    private static final class LumenSpec {
        final String segmentId;
        final String lumenUid;
        final double axisUm;
        final String areaUm2;

        LumenSpec(
                final String segmentId,
                final String lumenUid,
                final double axisUm,
                final String areaUm2) {
            this.segmentId = segmentId;
            this.lumenUid = lumenUid;
            this.axisUm = axisUm;
            this.areaUm2 = areaUm2;
        }
    }

    private static final class Fixture {
        final Path root;
        final ProductionDatasetLayout layout;

        private Fixture(final Path root, final ProductionDatasetLayout layout) {
            this.root = root;
            this.layout = layout;
        }

        Path aggregate() {
            return layout.dataLumenMeasurementsAll();
        }

        Path perSegment(final String segmentId) {
            final CoreSegmentId id = CoreSegmentId.parse(segmentId);
            return layout.dataLumenMeasurementsPerSegment().resolve(
                    FilenameSanitizer.lumenMeasurementsFilename(id.sampleId(), id.segmentId()));
        }

        static Fixture create(
                final Path root,
                final List<SegmentSpec> segments,
                final List<LumenSpec> lumens) throws IOException {
            final ProductionDatasetLayout layout = new ProductionDatasetLayout(root);
            layout.ensureDirectoriesExist();
            final List<List<String>> segmentRows = new ArrayList<List<String>>();
            final List<List<String>> imageRows = new ArrayList<List<String>>();
            for (int i = 0; i < segments.size(); i++) {
                final SegmentSpec spec = segments.get(i);
                final CoreSegmentId id = CoreSegmentId.parse(spec.segmentId);
                segmentRows.add(segmentRow(spec, id));
                imageRows.add(imageRow(spec, id));
            }
            ProductionCsvStore.writeTableAtomically(
                    layout.segmentManifest(), ProductionSchemas.SEGMENT_MANIFEST_HEADER, segmentRows);
            ProductionCsvStore.writeTableAtomically(
                    layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER, imageRows);

            final Map<String, List<List<String>>> perSegmentRows =
                    new LinkedHashMap<String, List<List<String>>>();
            for (int i = 0; i < segments.size(); i++) {
                perSegmentRows.put(segments.get(i).segmentId, new ArrayList<List<String>>());
            }
            final List<List<String>> aggregateRows = new ArrayList<List<String>>();
            for (int i = 0; i < lumens.size(); i++) {
                final LumenSpec lumen = lumens.get(i);
                SegmentSpec matching = null;
                for (int s = 0; s < segments.size(); s++) {
                    if (segments.get(s).segmentId.equals(lumen.segmentId)) {
                        matching = segments.get(s);
                        break;
                    }
                }
                if (matching == null) {
                    throw new IllegalArgumentException("lumen without segment: " + lumen.segmentId);
                }
                final List<String> row = measurementRow(lumen, matching);
                aggregateRows.add(row);
                perSegmentRows.get(lumen.segmentId).add(new ArrayList<String>(row));
            }
            ProductionCsvStore.writeTableAtomically(
                    layout.dataLumenMeasurementsAll(),
                    ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                    aggregateRows);
            for (int i = 0; i < segments.size(); i++) {
                final SegmentSpec spec = segments.get(i);
                final CoreSegmentId id = CoreSegmentId.parse(spec.segmentId);
                final Path perSegment = layout.dataLumenMeasurementsPerSegment().resolve(
                        FilenameSanitizer.lumenMeasurementsFilename(id.sampleId(), id.segmentId()));
                ProductionCsvStore.writeTableAtomically(
                        perSegment,
                        ProductionSchemas.LUMEN_MEASUREMENTS_HEADER,
                        perSegmentRows.get(spec.segmentId));
            }
            return new Fixture(root, layout);
        }

        private static List<String> segmentRow(final SegmentSpec spec, final CoreSegmentId id) {
            final List<String> row = naRow(ProductionSchemas.SEGMENT_MANIFEST_HEADER);
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "schema_version",
                    Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION));
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "sample_id", id.sampleId());
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "segment_id", id.segmentId());
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "segment_order",
                    Integer.toString(id.segmentOrder()));
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "segment_orientation", spec.orientation);
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "continuation_confirmed",
                    RCsvSupport.FALSE);
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "global_segment_start_um",
                    spec.globalStart);
            set(row, ProductionSchemas.SEGMENT_MANIFEST_HEADER, "notes", spec.notes);
            return row;
        }

        private static List<String> imageRow(final SegmentSpec spec, final CoreSegmentId id) {
            final List<String> row = naRow(ProductionSchemas.IMAGE_MANIFEST_HEADER);
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "schema_version",
                    Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION));
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "sample_id", id.sampleId());
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "segment_id", id.segmentId());
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "segment_order",
                    Integer.toString(id.segmentOrder()));
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "segment_orientation", spec.orientation);
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "source_width_px", "20");
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "source_height_px",
                    Integer.toString(spec.sourceHeightPx));
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "pixel_width_um",
                    RCsvSupport.formatNumber(spec.pixelHeightUm));
            set(row, ProductionSchemas.IMAGE_MANIFEST_HEADER, "pixel_height_um",
                    RCsvSupport.formatNumber(spec.pixelHeightUm));
            return row;
        }

        private static List<String> measurementRow(final LumenSpec lumen, final SegmentSpec segment) {
            final CoreSegmentId id = CoreSegmentId.parse(lumen.segmentId);
            final List<String> row = naRow(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER);
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "schema_version",
                    Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION));
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "lumen_uid", lumen.lumenUid);
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "sample_id", id.sampleId());
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "segment_id", id.segmentId());
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "segment_order",
                    Integer.toString(id.segmentOrder()));
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "segment_orientation",
                    segment.orientation);
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "original_height_px",
                    Integer.toString(segment.sourceHeightPx));
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "pixel_height_um",
                    RCsvSupport.formatNumber(segment.pixelHeightUm));
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "segment_axis_position_um",
                    RCsvSupport.formatNumber(lumen.axisUm));
            set(row, ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "area_um2", lumen.areaUm2);
            return row;
        }

        private static List<String> naRow(final List<String> header) {
            final List<String> row = new ArrayList<String>(header.size());
            for (int i = 0; i < header.size(); i++) {
                row.add(RCsvSupport.NA);
            }
            return row;
        }

        private static void set(
                final List<String> row,
                final List<String> header,
                final String column,
                final String value) {
            row.set(header.indexOf(column), value);
        }
    }
}
