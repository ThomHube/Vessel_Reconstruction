package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java repair of mis-populated TILED_CLASSIFICATION rows in processing_runs.csv.
 *
 * <p>Does not use ImageJ APIs and does not rerun image processing.</p>
 */
public final class ProcessingRunsManifestRepair {

    public static final class Request {
        public final Path datasetRoot;
        public final String sampleId;
        public final String segmentId;
        public final String sourceFilename;

        public Request(
                final Path datasetRoot,
                final String sampleId,
                final String segmentId,
                final String sourceFilename) {
            this.datasetRoot = datasetRoot;
            this.sampleId = sampleId;
            this.segmentId = segmentId;
            this.sourceFilename = sourceFilename;
        }
    }

    public static final class Result {
        public final Path manifestPath;
        public final int headerColumnCount;
        public final int totalRowCount;
        public final String matchingRunId;
        public final String oldInputFilename;
        public final String newInputFilename;
        public final String oldMinimumLumenAreaPx;
        public final String oldTotalComponents;
        public final String oldEligibleComponents;
        public final String oldReconstructedComponents;
        public final String oldChangedPixels;
        public final boolean fileChanged;
        public final String finalValidation;

        public Result(
                final Path manifestPath,
                final int headerColumnCount,
                final int totalRowCount,
                final String matchingRunId,
                final String oldInputFilename,
                final String newInputFilename,
                final String oldMinimumLumenAreaPx,
                final String oldTotalComponents,
                final String oldEligibleComponents,
                final String oldReconstructedComponents,
                final String oldChangedPixels,
                final boolean fileChanged,
                final String finalValidation) {
            this.manifestPath = manifestPath;
            this.headerColumnCount = headerColumnCount;
            this.totalRowCount = totalRowCount;
            this.matchingRunId = matchingRunId;
            this.oldInputFilename = oldInputFilename;
            this.newInputFilename = newInputFilename;
            this.oldMinimumLumenAreaPx = oldMinimumLumenAreaPx;
            this.oldTotalComponents = oldTotalComponents;
            this.oldEligibleComponents = oldEligibleComponents;
            this.oldReconstructedComponents = oldReconstructedComponents;
            this.oldChangedPixels = oldChangedPixels;
            this.fileChanged = fileChanged;
            this.finalValidation = finalValidation;
        }

        public String toReport() {
            final StringBuilder sb = new StringBuilder();
            if (fileChanged) {
                sb.append("Manifest repaired successfully.\n");
            } else {
                sb.append("No changes were required.\n");
            }
            sb.append("manifest path: ").append(manifestPath).append('\n');
            sb.append("canonical header count: ").append(headerColumnCount).append('\n');
            sb.append("total row count: ").append(totalRowCount).append('\n');
            sb.append("TILED_CLASSIFICATION run:\n");
            sb.append(matchingRunId).append('\n');
            sb.append("input_filename: ").append(oldInputFilename)
                    .append(" -> ").append(newInputFilename).append('\n');
            sb.append("minimum_lumen_area_px: ").append(oldMinimumLumenAreaPx)
                    .append(" -> ").append(RCsvSupport.NA).append('\n');
            sb.append("total_components: ").append(oldTotalComponents)
                    .append(" -> ").append(RCsvSupport.NA).append('\n');
            sb.append("eligible_components: ").append(oldEligibleComponents)
                    .append(" -> ").append(RCsvSupport.NA).append('\n');
            sb.append("reconstructed_components: ").append(oldReconstructedComponents)
                    .append(" -> ").append(RCsvSupport.NA).append('\n');
            sb.append("changed_pixels: ").append(oldChangedPixels)
                    .append(" -> ").append(RCsvSupport.NA).append('\n');
            sb.append("ALL_LUMINA_RECONSTRUCTION rows changed: 0\n");
            sb.append("file changed: ").append(fileChanged).append('\n');
            sb.append("final validation: ").append(finalValidation);
            return sb.toString();
        }
    }

    private ProcessingRunsManifestRepair() {
    }

    public static void validateRequest(final Request request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.datasetRoot == null || !Files.isDirectory(request.datasetRoot)) {
            throw new IllegalArgumentException(
                    "dataset root must exist and be a directory: " + request.datasetRoot);
        }
        if (request.sampleId == null || request.sampleId.trim().isEmpty()) {
            throw new IllegalArgumentException("sample ID must not be blank");
        }
        if (request.segmentId == null || request.segmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("segment ID must not be blank");
        }
        requireSafeFilename(request.sourceFilename);
    }

    public static void requireSafeFilename(final String sourceFilename) {
        if (sourceFilename == null || sourceFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("source filename must not be blank");
        }
        final String name = sourceFilename.trim();
        if (name.contains("/") || name.contains("\\") || name.contains("..")
                || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException(
                    "source filename must be a filename only, not a path: " + sourceFilename);
        }
        if (!name.equals(sourceFilename) && (sourceFilename.contains("/")
                || sourceFilename.contains("\\"))) {
            throw new IllegalArgumentException(
                    "source filename must be a filename only, not a path: " + sourceFilename);
        }
    }

    public static Result repair(final Request request) throws IOException {
        validateRequest(request);
        final ProductionDatasetLayout layout = new ProductionDatasetLayout(request.datasetRoot);
        final Path manifest = layout.processingRuns();
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("processing_runs.csv does not exist: " + manifest);
        }
        final byte[] originalBytes = Files.readAllBytes(manifest);
        try {
            final List<List<String>> table = ProductionCsvStore.readTable(manifest);
            if (table.isEmpty()) {
                throw new IOException("processing_runs.csv is empty: " + manifest);
            }
            final List<String> header = table.get(0);
            if (!header.equals(ProcessingRunRecord.HEADER)) {
                throw new IOException(ProcessingRunRecord.describeHeaderMismatch(
                        manifest.toString(), header, ProcessingRunRecord.HEADER,
                        java.util.Collections.<String>emptyList()));
            }
            validateRows(table, manifest);

            final Map<String, Integer> idx = ProductionCsvStore.headerIndex(header);
            final int stageIdx = idx.get("stage").intValue();
            final int sampleIdx = idx.get("sample_id").intValue();
            final int segmentIdx = idx.get("segment_id").intValue();
            final int runIdIdx = idx.get("run_id").intValue();
            final int inputIdx = idx.get("input_filename").intValue();
            final int minAreaIdx = idx.get("minimum_lumen_area_px").intValue();
            final int totalIdx = idx.get("total_components").intValue();
            final int eligibleIdx = idx.get("eligible_components").intValue();
            final int reconstructedIdx = idx.get("reconstructed_components").intValue();
            final int changedIdx = idx.get("changed_pixels").intValue();

            final List<Integer> matches = new ArrayList<Integer>();
            final List<String> matchRunIds = new ArrayList<String>();
            for (int r = 1; r < table.size(); r++) {
                final List<String> row = table.get(r);
                if (ProductionSchemas.ProcessingStage.TILED_CLASSIFICATION.name()
                        .equals(row.get(stageIdx))
                        && request.sampleId.equals(row.get(sampleIdx))
                        && request.segmentId.equals(row.get(segmentIdx))) {
                    matches.add(Integer.valueOf(r));
                    matchRunIds.add(row.get(runIdIdx));
                }
            }
            if (matches.size() != 1) {
                throw new IOException(
                        "Expected exactly one matching TILED_CLASSIFICATION row for sample_id="
                                + request.sampleId + " segment_id=" + request.segmentId
                                + " but found " + matches.size()
                                + " with run IDs: " + matchRunIds
                                + "\nOriginal file preserved.");
            }

            final int rowIndex = matches.get(0).intValue();
            final List<String> oldRow = table.get(rowIndex);
            final String oldInput = oldRow.get(inputIdx);
            final String oldMin = oldRow.get(minAreaIdx);
            final String oldTotal = oldRow.get(totalIdx);
            final String oldEligible = oldRow.get(eligibleIdx);
            final String oldReconstructed = oldRow.get(reconstructedIdx);
            final String oldChanged = oldRow.get(changedIdx);
            final String sourceFilename = request.sourceFilename.trim();

            final boolean alreadyCorrect =
                    sourceFilename.equals(oldInput)
                            && RCsvSupport.NA.equals(oldMin)
                            && RCsvSupport.NA.equals(oldTotal)
                            && RCsvSupport.NA.equals(oldEligible)
                            && RCsvSupport.NA.equals(oldReconstructed)
                            && RCsvSupport.NA.equals(oldChanged);
            if (alreadyCorrect) {
                return new Result(
                        manifest,
                        header.size(),
                        table.size() - 1,
                        oldRow.get(runIdIdx),
                        oldInput,
                        sourceFilename,
                        oldMin,
                        oldTotal,
                        oldEligible,
                        oldReconstructed,
                        oldChanged,
                        false,
                        "OK (idempotent; no rewrite)");
            }

            final List<List<String>> body = new ArrayList<List<String>>();
            for (int r = 1; r < table.size(); r++) {
                if (r == rowIndex) {
                    final List<String> corrected = new ArrayList<String>(oldRow);
                    corrected.set(inputIdx, sourceFilename);
                    corrected.set(minAreaIdx, RCsvSupport.NA);
                    corrected.set(totalIdx, RCsvSupport.NA);
                    corrected.set(eligibleIdx, RCsvSupport.NA);
                    corrected.set(reconstructedIdx, RCsvSupport.NA);
                    corrected.set(changedIdx, RCsvSupport.NA);
                    if (corrected.size() != ProcessingRunRecord.COLUMN_COUNT) {
                        throw new IOException("Corrected row width invalid");
                    }
                    body.add(corrected);
                } else {
                    body.add(new ArrayList<String>(table.get(r)));
                }
            }

            // Validate corrected content before writing.
            final String serialized = RCsvSupport.tableToString(ProcessingRunRecord.HEADER, body);
            final List<List<String>> reparsed = CsvSupport.parseTable(serialized);
            if (reparsed.isEmpty() || !reparsed.get(0).equals(ProcessingRunRecord.HEADER)) {
                throw new IOException("Corrected CSV failed header revalidation");
            }
            validateRows(reparsed, manifest);
            assertAllLuminaUnchanged(table, reparsed, idx);

            ProductionCsvStore.writeTableAtomically(manifest, ProcessingRunRecord.HEADER, body);

            final List<List<String>> after = ProductionCsvStore.readTable(manifest);
            validateRows(after, manifest);
            if (!after.get(0).equals(ProcessingRunRecord.HEADER)) {
                Files.write(manifest, originalBytes);
                throw new IOException("Post-write validation failed; original restored");
            }

            return new Result(
                    manifest,
                    header.size(),
                    after.size() - 1,
                    oldRow.get(runIdIdx),
                    oldInput,
                    sourceFilename,
                    oldMin,
                    oldTotal,
                    oldEligible,
                    oldReconstructed,
                    oldChanged,
                    true,
                    "OK");
        } catch (final IOException ex) {
            Files.write(manifest, originalBytes);
            throw ex;
        } catch (final RuntimeException ex) {
            Files.write(manifest, originalBytes);
            throw new IOException(
                    "Repair failed; original processing_runs.csv preserved: " + ex.getMessage(),
                    ex);
        }
    }

    private static void validateRows(final List<List<String>> table, final Path path)
            throws IOException {
        final Set<String> runIds = new HashSet<String>();
        final int runIdIdx = ProcessingRunRecord.HEADER.indexOf("run_id");
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = table.get(r);
            if (row.size() != ProcessingRunRecord.COLUMN_COUNT) {
                throw new IOException(
                        "Malformed processing_runs.csv row width at data row " + r
                                + ": expected " + ProcessingRunRecord.COLUMN_COUNT
                                + " columns, found " + row.size()
                                + "\npath: " + path);
            }
            final String runId = row.get(runIdIdx);
            if (!runIds.add(runId)) {
                throw new IOException(
                        "Duplicate run_id in processing_runs.csv: " + runId
                                + "\npath: " + path);
            }
        }
    }

    private static void assertAllLuminaUnchanged(
            final List<List<String>> before,
            final List<List<String>> after,
            final Map<String, Integer> idx) throws IOException {
        final int stageIdx = idx.get("stage").intValue();
        final List<List<String>> beforeRecon = new ArrayList<List<String>>();
        final List<List<String>> afterRecon = new ArrayList<List<String>>();
        for (int r = 1; r < before.size(); r++) {
            if (ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name()
                    .equals(before.get(r).get(stageIdx))) {
                beforeRecon.add(before.get(r));
            }
        }
        for (int r = 1; r < after.size(); r++) {
            if (ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name()
                    .equals(after.get(r).get(stageIdx))) {
                afterRecon.add(after.get(r));
            }
        }
        if (beforeRecon.size() != afterRecon.size()) {
            throw new IOException("ALL_LUMINA_RECONSTRUCTION row count changed during repair");
        }
        for (int i = 0; i < beforeRecon.size(); i++) {
            if (!beforeRecon.get(i).equals(afterRecon.get(i))) {
                throw new IOException(
                        "ALL_LUMINA_RECONSTRUCTION row was altered during repair: run_id="
                                + beforeRecon.get(i).get(idx.get("run_id").intValue()));
            }
        }
    }
}
