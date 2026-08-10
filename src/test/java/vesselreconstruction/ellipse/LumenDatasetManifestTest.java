package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenDatasetManifestTest {

    private final LumenDatasetManifest manifest = new LumenDatasetManifest();

    @Test
    void headerAndRowColumnCountsMatch() {
        final LumenDatasetManifest.Row row = sampleRow();
        final List<String> columns = manifest.toColumns(row);
        assertEquals(LumenDatasetManifest.HEADER.size(), columns.size());
        assertEquals(LumenDatasetManifest.HEADER.size(),
                CsvSupport.parseRow(manifest.serializeHeader()).size());
        assertEquals(LumenDatasetManifest.HEADER.size(),
                CsvSupport.parseRow(manifest.serializeRow(row)).size());
    }

    @Test
    void commasAndQuotesAreEscaped() {
        final LumenDatasetManifest.Row row = sampleRow();
        row.sourcePath = "/data/a,b\"quoted\".tif";
        row.classifierPath = "C:\\models\\weka, v1.model";
        final String serialized = manifest.serializeRow(row);
        assertTrue(serialized.contains("\"/data/a,b\"\"quoted\"\".tif\""));
        assertTrue(serialized.contains("\"C:\\models\\weka, v1.model\""));
        final List<String> parsed = CsvSupport.parseRow(serialized);
        assertEquals("/data/a,b\"quoted\".tif", parsed.get(9));
        assertEquals("C:\\models\\weka, v1.model", parsed.get(14));
    }

    @Test
    void blankOptionalFieldsStayBlank() {
        final LumenDatasetManifest.Row row = sampleRow();
        row.globalSegmentStartUm = null;
        row.scaleBarValueUm = null;
        row.scaleBarLengthPx = null;
        row.sourceChecksum = null;
        row.outputChecksum = null;
        final List<String> columns = manifest.toColumns(row);
        assertEquals("", columns.get(7));
        assertEquals("", columns.get(25));
        assertEquals("", columns.get(26));
        assertEquals("", columns.get(12));
        assertEquals("", columns.get(58));
    }

    @Test
    void incompatibleHeaderIsRejectedOnAppend(@TempDir final File tempDir) throws Exception {
        final File master = new File(tempDir, LumenDatasetManifest.MASTER_FILENAME);
        Files.write(master.toPath(),
                Arrays.asList("wrong,header", "a,b"),
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> manifest.appendToMaster(master, sampleRow()));
    }

    @Test
    void writePerRunAtomicallyCreatesManifest(@TempDir final File tempDir) throws Exception {
        final File target = new File(tempDir, "run_manifest.csv");
        manifest.writePerRunAtomically(target, sampleRow());
        assertTrue(target.exists());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(target.toPath()), StandardCharsets.UTF_8))) {
            final String header = reader.readLine();
            final String rowLine = reader.readLine();
            manifest.validateHeader(CsvSupport.parseRow(header), target.getAbsolutePath());
            final List<String> parsed = CsvSupport.parseRow(rowLine);
            assertEquals(LumenDatasetManifest.HEADER.size(), parsed.size());
            assertEquals("sample-a", parsed.get(3));
            assertEquals("segment-1", parsed.get(4));
        }
    }

    @Test
    void appendToMasterPreservesHeaderAndAddsRow(@TempDir final File tempDir) throws Exception {
        final File master = new File(tempDir, LumenDatasetManifest.MASTER_FILENAME);
        manifest.appendToMaster(master, sampleRow());
        manifest.appendToMaster(master, sampleRow());

        final List<String> lines = Files.readAllLines(master.toPath(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        manifest.validateHeader(CsvSupport.parseRow(lines.get(0)), master.getAbsolutePath());
    }

    private static LumenDatasetManifest.Row sampleRow() {
        final LumenDatasetManifest.Row row = new LumenDatasetManifest.Row();
        row.runId = "run-001";
        row.createdTimestamp = "2026-08-01T12:00:00Z";
        row.sampleId = "sample-a";
        row.segmentId = "segment-1";
        row.segmentOrder = 1;
        row.segmentOrientation = SegmentOrientation.UNSPECIFIED.name();
        row.sourceFilename = "source.tif";
        row.sourcePath = "/data/source.tif";
        row.sourceWidthPx = 6672;
        row.sourceHeightPx = 1650;
        row.cropEnabled = true;
        row.cropXPx = 2744;
        row.cropYPx = 0;
        row.cropWidthPx = 3928;
        row.cropHeightPx = 1650;
        row.pixelWidthUm = 1.0964912280701755;
        row.pixelHeightUm = 1.0964912280701755;
        row.calibrationMethod = CalibrationMode.MEASURE_SCALE_BAR_LINE.name();
        row.scaleBarValueUm = Double.valueOf(500.0);
        row.scaleBarLengthPx = Double.valueOf(456.0);
        row.maskDownsampleFactor = 8;
        row.maskWidthPx = 834;
        row.maskHeightPx = 207;
        row.maskChecksum = 12345;
        row.maskIncludedPixels = 1000L;
        row.maskIncludedFraction = 0.5;
        row.coreTileWidthPx = 2048;
        row.coreTileHeightPx = 2048;
        row.classificationHaloPx = 0;
        row.calculatedTilesX = 2;
        row.calculatedTilesY = 1;
        row.totalTiles = 2L;
        row.classifiedTiles = 2;
        row.skippedTiles = 0;
        row.threadsPerTile = 1;
        row.seamAuditHalfWidthPx = 16;
        row.outputWidthPx = 3928;
        row.outputHeightPx = 1650;
        row.status = LumenDatasetManifest.Status.SUCCESS;
        return row;
    }
}
