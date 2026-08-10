package vesselreconstruction.ellipse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Authoritative CSV dataset manifest writer/appender.
 */
public final class LumenDatasetManifest {

    public static final int SCHEMA_VERSION = 1;
    public static final String MASTER_FILENAME = "lumen_dataset_manifest.csv";

    public static final List<String> HEADER = Collections.unmodifiableList(CsvSupport.immutableCopy(
            "schema_version",
            "run_id",
            "created_timestamp",
            "sample_id",
            "segment_id",
            "segment_order",
            "segment_orientation",
            "global_segment_start_um",
            "source_filename",
            "source_path",
            "source_width_px",
            "source_height_px",
            "source_checksum",
            "classifier_filename",
            "classifier_path",
            "crop_enabled",
            "crop_x_px",
            "crop_y_px",
            "crop_width_px",
            "crop_height_px",
            "crop_padding_px",
            "pixel_width_um",
            "pixel_height_um",
            "physical_unit",
            "calibration_method",
            "scale_bar_value_um",
            "scale_bar_length_px",
            "source_metadata_unit",
            "mask_downsample_factor",
            "mask_width_px",
            "mask_height_px",
            "mask_checksum",
            "mask_included_pixels",
            "mask_included_fraction",
            "core_tile_width_px",
            "core_tile_height_px",
            "classification_halo_px",
            "calculated_tiles_x",
            "calculated_tiles_y",
            "total_tiles",
            "classified_tiles",
            "skipped_tiles",
            "threads_per_tile",
            "seam_audit_half_width_px",
            "run_untiled_reference_comparison",
            "classified_output_filename",
            "classified_output_path",
            "approved_mask_filename",
            "approved_mask_path",
            "rgb_crop_filename",
            "rgb_crop_path",
            "output_width_px",
            "output_height_px",
            "wood_pixels",
            "ray_pixels",
            "lumen_pixels",
            "background_pixels",
            "outside_mask_forced_background_pixels",
            "output_checksum",
            "source_unchanged",
            "classification_time_ms",
            "status"));

    public enum Status {
        SUCCESS,
        CANCELLED,
        FAILED
    }

    public static final class Row {
        public int schemaVersion = SCHEMA_VERSION;
        public String runId = "";
        public String createdTimestamp = "";
        public String sampleId = "";
        public String segmentId = "";
        public int segmentOrder = 1;
        public String segmentOrientation = SegmentOrientation.UNSPECIFIED.name();
        public Double globalSegmentStartUm;
        public String sourceFilename = "";
        public String sourcePath = "";
        public int sourceWidthPx;
        public int sourceHeightPx;
        public Integer sourceChecksum;
        public String classifierFilename = "";
        public String classifierPath = "";
        public boolean cropEnabled;
        public int cropXPx;
        public int cropYPx;
        public int cropWidthPx;
        public int cropHeightPx;
        public int cropPaddingPx;
        public double pixelWidthUm;
        public double pixelHeightUm;
        public String physicalUnit = PhysicalCalibration.UNIT_MICROMETRE;
        public String calibrationMethod = "";
        public Double scaleBarValueUm;
        public Double scaleBarLengthPx;
        public String sourceMetadataUnit = "";
        public int maskDownsampleFactor;
        public int maskWidthPx;
        public int maskHeightPx;
        public int maskChecksum;
        public long maskIncludedPixels;
        public double maskIncludedFraction;
        public int coreTileWidthPx;
        public int coreTileHeightPx;
        public int classificationHaloPx;
        public int calculatedTilesX;
        public int calculatedTilesY;
        public long totalTiles;
        public int classifiedTiles;
        public int skippedTiles;
        public int threadsPerTile;
        public int seamAuditHalfWidthPx;
        public boolean runUntiledReferenceComparison;
        public String classifiedOutputFilename = "";
        public String classifiedOutputPath = "";
        public String approvedMaskFilename = "";
        public String approvedMaskPath = "";
        public String rgbCropFilename = "";
        public String rgbCropPath = "";
        public int outputWidthPx;
        public int outputHeightPx;
        public long woodPixels;
        public long rayPixels;
        public long lumenPixels;
        public long backgroundPixels;
        public long outsideMaskForcedBackgroundPixels;
        public Integer outputChecksum;
        public boolean sourceUnchanged;
        public long classificationTimeMs;
        public Status status = Status.SUCCESS;
    }

    public List<String> toColumns(final Row row) {
        if (row == null) {
            throw new IllegalArgumentException("row must not be null");
        }
        final List<String> cols = new ArrayList<String>(HEADER.size());
        cols.add(Integer.toString(row.schemaVersion));
        cols.add(nullSafe(row.runId));
        cols.add(nullSafe(row.createdTimestamp));
        cols.add(nullSafe(row.sampleId));
        cols.add(nullSafe(row.segmentId));
        cols.add(Integer.toString(row.segmentOrder));
        cols.add(nullSafe(row.segmentOrientation));
        cols.add(CsvSupport.formatDoubleOrBlank(row.globalSegmentStartUm));
        cols.add(nullSafe(row.sourceFilename));
        cols.add(nullSafe(row.sourcePath));
        cols.add(Integer.toString(row.sourceWidthPx));
        cols.add(Integer.toString(row.sourceHeightPx));
        cols.add(row.sourceChecksum == null ? "" : Integer.toString(row.sourceChecksum.intValue()));
        cols.add(nullSafe(row.classifierFilename));
        cols.add(nullSafe(row.classifierPath));
        cols.add(Boolean.toString(row.cropEnabled));
        cols.add(Integer.toString(row.cropXPx));
        cols.add(Integer.toString(row.cropYPx));
        cols.add(Integer.toString(row.cropWidthPx));
        cols.add(Integer.toString(row.cropHeightPx));
        cols.add(Integer.toString(row.cropPaddingPx));
        cols.add(CsvSupport.formatDouble(row.pixelWidthUm));
        cols.add(CsvSupport.formatDouble(row.pixelHeightUm));
        cols.add(nullSafe(row.physicalUnit));
        cols.add(nullSafe(row.calibrationMethod));
        cols.add(CsvSupport.formatDoubleOrBlank(row.scaleBarValueUm));
        cols.add(CsvSupport.formatDoubleOrBlank(row.scaleBarLengthPx));
        cols.add(nullSafe(row.sourceMetadataUnit));
        cols.add(Integer.toString(row.maskDownsampleFactor));
        cols.add(Integer.toString(row.maskWidthPx));
        cols.add(Integer.toString(row.maskHeightPx));
        cols.add(Integer.toString(row.maskChecksum));
        cols.add(Long.toString(row.maskIncludedPixels));
        cols.add(CsvSupport.formatDouble(row.maskIncludedFraction));
        cols.add(Integer.toString(row.coreTileWidthPx));
        cols.add(Integer.toString(row.coreTileHeightPx));
        cols.add(Integer.toString(row.classificationHaloPx));
        cols.add(Integer.toString(row.calculatedTilesX));
        cols.add(Integer.toString(row.calculatedTilesY));
        cols.add(Long.toString(row.totalTiles));
        cols.add(Integer.toString(row.classifiedTiles));
        cols.add(Integer.toString(row.skippedTiles));
        cols.add(Integer.toString(row.threadsPerTile));
        cols.add(Integer.toString(row.seamAuditHalfWidthPx));
        cols.add(Boolean.toString(row.runUntiledReferenceComparison));
        cols.add(nullSafe(row.classifiedOutputFilename));
        cols.add(nullSafe(row.classifiedOutputPath));
        cols.add(nullSafe(row.approvedMaskFilename));
        cols.add(nullSafe(row.approvedMaskPath));
        cols.add(nullSafe(row.rgbCropFilename));
        cols.add(nullSafe(row.rgbCropPath));
        cols.add(Integer.toString(row.outputWidthPx));
        cols.add(Integer.toString(row.outputHeightPx));
        cols.add(Long.toString(row.woodPixels));
        cols.add(Long.toString(row.rayPixels));
        cols.add(Long.toString(row.lumenPixels));
        cols.add(Long.toString(row.backgroundPixels));
        cols.add(Long.toString(row.outsideMaskForcedBackgroundPixels));
        cols.add(row.outputChecksum == null ? "" : Integer.toString(row.outputChecksum.intValue()));
        cols.add(Boolean.toString(row.sourceUnchanged));
        cols.add(Long.toString(row.classificationTimeMs));
        cols.add(row.status == null ? "" : row.status.name());
        if (cols.size() != HEADER.size()) {
            throw new IllegalStateException(
                    "Manifest column count mismatch: expected " + HEADER.size()
                            + ", got " + cols.size());
        }
        return cols;
    }

    public String serializeRow(final Row row) {
        return CsvSupport.joinRow(toColumns(row));
    }

    public String serializeHeader() {
        return CsvSupport.joinRow(HEADER);
    }

    public void validateHeader(final List<String> existingHeader, final String manifestPath) {
        if (existingHeader == null) {
            throw new IllegalArgumentException(
                    "Manifest header validation failed for " + manifestPath
                            + ": header is null");
        }
        if (existingHeader.size() != HEADER.size()) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Manifest header validation failed for %s: expected %d columns, found %d",
                    manifestPath,
                    Integer.valueOf(HEADER.size()),
                    Integer.valueOf(existingHeader.size())));
        }
        for (int i = 0; i < HEADER.size(); i++) {
            if (!HEADER.get(i).equals(existingHeader.get(i))) {
                throw new IllegalArgumentException(String.format(Locale.US,
                        "Manifest header validation failed for %s: column %d expected '%s' found '%s'",
                        manifestPath,
                        Integer.valueOf(i),
                        HEADER.get(i),
                        existingHeader.get(i)));
            }
        }
    }

    public void writePerRunAtomically(final File targetFile, final Row row) throws IOException {
        if (targetFile == null) {
            throw new IllegalArgumentException("targetFile must not be null");
        }
        final File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }
        final File temp = File.createTempFile(
                targetFile.getName() + ".",
                ".tmp",
                parent == null ? null : parent);
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8))) {
                writer.write(serializeHeader());
                writer.newLine();
                writer.write(serializeRow(row));
                writer.newLine();
            }
            try {
                Files.move(temp.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException ex) {
            if (temp.exists() && !temp.delete()) {
                // best effort
            }
            throw new IOException(
                    "Manifest serialization/writing failed for " + targetFile.getAbsolutePath()
                            + ": " + ex.getMessage(),
                    ex);
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    public void appendToMaster(final File masterFile, final Row row) throws IOException {
        if (masterFile == null) {
            throw new IllegalArgumentException("masterFile must not be null");
        }
        final File parent = masterFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }
        if (masterFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(masterFile.toPath()), StandardCharsets.UTF_8))) {
                final String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new IllegalArgumentException(
                            "Manifest header validation failed for "
                                    + masterFile.getAbsolutePath()
                                    + ": file is empty");
                }
                validateHeader(CsvSupport.parseRow(headerLine), masterFile.getAbsolutePath());
            } catch (final IllegalArgumentException ex) {
                throw ex;
            } catch (final IOException ex) {
                throw new IOException(
                        "Manifest header validation failed while reading "
                                + masterFile.getAbsolutePath() + ": " + ex.getMessage(),
                        ex);
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(masterFile.toPath(),
                            java.nio.file.StandardOpenOption.APPEND),
                    StandardCharsets.UTF_8))) {
                writer.write(serializeRow(row));
                writer.newLine();
            } catch (final IOException ex) {
                throw new IOException(
                        "Manifest writing failed for " + masterFile.getAbsolutePath()
                                + ": " + ex.getMessage(),
                        ex);
            }
        } else {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(masterFile.toPath()), StandardCharsets.UTF_8))) {
                writer.write(serializeHeader());
                writer.newLine();
                writer.write(serializeRow(row));
                writer.newLine();
            } catch (final IOException ex) {
                throw new IOException(
                        "Manifest writing failed for " + masterFile.getAbsolutePath()
                                + ": " + ex.getMessage(),
                        ex);
            }
        }
    }

    private static String nullSafe(final String value) {
        return value == null ? "" : value;
    }
}
