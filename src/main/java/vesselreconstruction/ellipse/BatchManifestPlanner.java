package vesselreconstruction.ellipse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java Phase-1 batch planner: discovery, assignment, manifests.
 */
public final class BatchManifestPlanner {

    public static final class PlanRequest {
        public final Path inputRoot;
        public final Path batchRoot;
        public final Path configPath;

        public PlanRequest(final Path inputRoot, final Path batchRoot, final Path configPath) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.configPath = configPath;
        }
    }

    public static final class PlanResult {
        public final Path masterManifest;
        public final Path assignmentSummary;
        public final Path planReport;
        public final List<Path> machineManifests;
        public final int validTiffCount;
        public final int machineCount;
        public final String firstImageId;
        public final String lastImageId;
        public final String configurationSha256;

        public PlanResult(
                final Path masterManifest,
                final Path assignmentSummary,
                final Path planReport,
                final List<Path> machineManifests,
                final int validTiffCount,
                final int machineCount,
                final String firstImageId,
                final String lastImageId,
                final String configurationSha256) {
            this.masterManifest = masterManifest;
            this.assignmentSummary = assignmentSummary;
            this.planReport = planReport;
            this.machineManifests = Collections.unmodifiableList(
                    new ArrayList<Path>(machineManifests));
            this.validTiffCount = validTiffCount;
            this.machineCount = machineCount;
            this.firstImageId = firstImageId;
            this.lastImageId = lastImageId;
            this.configurationSha256 = configurationSha256;
        }
    }

    public static final class PlanningException extends Exception {
        public final int exitCode;

        public PlanningException(final int exitCode, final String message) {
            super(message);
            this.exitCode = exitCode;
        }

        public PlanningException(final int exitCode, final String message, final Throwable cause) {
            super(message, cause);
            this.exitCode = exitCode;
        }
    }

    private BatchManifestPlanner() {
    }

    public static PlanResult plan(final PlanRequest request) throws PlanningException {
        if (request == null) {
            throw new PlanningException(2, "request must not be null");
        }
        if (request.inputRoot == null || request.batchRoot == null || request.configPath == null) {
            throw new PlanningException(2, "input-root, batch-root, and config are required");
        }

        final BatchConfiguration config;
        try {
            config = BatchConfiguration.load(request.configPath);
        } catch (final IllegalArgumentException ex) {
            throw new PlanningException(3, ex.getMessage(), ex);
        } catch (final IOException ex) {
            throw new PlanningException(3, "Failed to load configuration: " + ex.getMessage(), ex);
        }

        final BatchTiffDiscovery.Result discovery;
        try {
            discovery = BatchTiffDiscovery.discover(
                    request.inputRoot,
                    config.recursive(),
                    config.requireCompleteExpectedSet());
        } catch (final IllegalArgumentException ex) {
            throw new PlanningException(4, ex.getMessage(), ex);
        } catch (final IOException ex) {
            throw new PlanningException(4, "Discovery failed: " + ex.getMessage(), ex);
        }

        if (discovery.hasBlockingErrors()) {
            throw new PlanningException(4, buildValidationFailure(discovery, config));
        }
        if (config.requireCompleteExpectedSet() && !discovery.missingExpectedIds().isEmpty()) {
            throw new PlanningException(4, buildValidationFailure(discovery, config));
        }

        final List<BatchTiffFile> tiffs = discovery.validTiffs();
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(tiffs, config.machinesCount());

        final Path batchRoot = request.batchRoot.toAbsolutePath().normalize();
        final Path machinesDir = batchRoot.resolve(BatchManifestSchema.DIR_MACHINES);
        final Path masterPath = batchRoot.resolve(BatchManifestSchema.FILE_MASTER_MANIFEST);
        final Path summaryPath = batchRoot.resolve(BatchManifestSchema.FILE_ASSIGNMENT_SUMMARY);
        final Path reportPath = batchRoot.resolve(BatchManifestSchema.FILE_PLAN_REPORT);

        final String created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .format(new Date());

        final List<List<String>> masterRows = new ArrayList<List<String>>();
        final List<List<List<String>>> perMachineRows =
                new ArrayList<List<List<String>>>(slices.size());
        for (int m = 0; m < slices.size(); m++) {
            perMachineRows.add(new ArrayList<List<String>>());
        }

        int globalOrder = 1;
        for (int m = 0; m < slices.size(); m++) {
            final BatchMachineAssignment.MachineSlice slice = slices.get(m);
            final List<BatchTiffFile> files = slice.files();
            for (int i = 0; i < files.size(); i++) {
                final BatchTiffFile file = files.get(i);
                final List<String> row = buildRow(
                        globalOrder,
                        file,
                        slice.machineId(),
                        i + 1,
                        config.sha256());
                masterRows.add(row);
                perMachineRows.get(m).add(row);
                globalOrder++;
            }
        }

        final String summaryText = buildAssignmentSummary(
                slices, tiffs.size(), config, created);
        final String reportText = buildPlanReport(
                request, config, discovery, "SUCCESS", created);

        // Preserve previous plan bytes for rollback.
        final byte[] previousMaster = readIfExists(masterPath);
        final byte[] previousSummary = readIfExists(summaryPath);
        final byte[] previousReport = readIfExists(reportPath);
        final List<byte[]> previousMachine = new ArrayList<byte[]>();
        final List<Path> machinePaths = new ArrayList<Path>();
        for (int m = 0; m < slices.size(); m++) {
            final Path mp = machinesDir.resolve(
                    BatchManifestSchema.machineManifestFilename(slices.get(m).machineId()));
            machinePaths.add(mp);
            previousMachine.add(readIfExists(mp));
        }

        final Path stagingRoot = batchRoot.resolve(".batch_plan_staging");
        try {
            deleteRecursively(stagingRoot);
            final Path stagingMachines = stagingRoot.resolve(BatchManifestSchema.DIR_MACHINES);
            Files.createDirectories(stagingMachines);

            final Path stagingMaster = stagingRoot.resolve(BatchManifestSchema.FILE_MASTER_MANIFEST);
            final Path stagingSummary = stagingRoot.resolve(BatchManifestSchema.FILE_ASSIGNMENT_SUMMARY);
            final Path stagingReport = stagingRoot.resolve(BatchManifestSchema.FILE_PLAN_REPORT);
            final List<Path> stagingMachinePaths = new ArrayList<Path>();

            writeCsvValidated(stagingMaster, BatchManifestSchema.HEADER, masterRows);
            for (int m = 0; m < slices.size(); m++) {
                final Path sm = stagingMachines.resolve(
                        BatchManifestSchema.machineManifestFilename(slices.get(m).machineId()));
                stagingMachinePaths.add(sm);
                writeCsvValidated(sm, BatchManifestSchema.HEADER, perMachineRows.get(m));
            }
            writeTextAtomically(stagingSummary, summaryText);
            writeTextAtomically(stagingReport, reportText);

            // Commit only after every staged file validates.
            Files.createDirectories(machinesDir);
            replaceFile(stagingMaster, masterPath);
            for (int m = 0; m < machinePaths.size(); m++) {
                replaceFile(stagingMachinePaths.get(m), machinePaths.get(m));
            }
            replaceFile(stagingSummary, summaryPath);
            replaceFile(stagingReport, reportPath);
        } catch (final IOException ex) {
            restorePrevious(masterPath, previousMaster);
            restorePrevious(summaryPath, previousSummary);
            restorePrevious(reportPath, previousReport);
            for (int m = 0; m < machinePaths.size(); m++) {
                restorePrevious(machinePaths.get(m), previousMachine.get(m));
            }
            throw new PlanningException(5, "Output writing failed: " + ex.getMessage(), ex);
        } finally {
            try {
                deleteRecursively(stagingRoot);
            } catch (final IOException ignored) {
                // best effort cleanup
            }
        }

        final String first = tiffs.isEmpty() ? RCsvSupport.NA : tiffs.get(0).id().imageId();
        final String last = tiffs.isEmpty()
                ? RCsvSupport.NA
                : tiffs.get(tiffs.size() - 1).id().imageId();
        return new PlanResult(
                masterPath,
                summaryPath,
                reportPath,
                machinePaths,
                tiffs.size(),
                config.machinesCount(),
                first,
                last,
                config.sha256());
    }

    private static List<String> buildRow(
            final int orderIndex,
            final BatchTiffFile file,
            final String machineId,
            final int assignmentIndex,
            final String configurationSha256) {
        final String relative = file.relativePath().toString().replace('\\', '/');
        if (relative.startsWith("/") || relative.contains(":")) {
            throw new IllegalStateException(
                    "Manifest must not contain absolute source paths: " + relative);
        }
        return RCsvSupport.mutableCopy(
                Integer.toString(BatchManifestSchema.SCHEMA_VERSION),
                Integer.toString(orderIndex),
                file.id().imageId(),
                file.id().plotId(),
                file.id().treeId(),
                file.id().replicateId(),
                file.sourceFilename(),
                relative,
                file.sha256(),
                Long.toString(file.sizeBytes()),
                machineId,
                Integer.toString(assignmentIndex),
                BatchManifestSchema.STATUS_PENDING,
                "0",
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA,
                RCsvSupport.NA,
                BatchManifestSchema.datasetShardRelativePath(machineId),
                configurationSha256);
    }

    private static void replaceFile(final Path source, final Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        if (Files.isDirectory(target)) {
            deleteRecursively(target);
        }
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException atomicFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeCsvValidated(
            final Path target,
            final List<String> header,
            final List<List<String>> rows) throws IOException {
        ProductionCsvStore.writeTableAtomically(target, header, rows);
        final List<List<String>> parsed = ProductionCsvStore.readTable(target);
        if (parsed.isEmpty() || !parsed.get(0).equals(header)) {
            throw new IOException("CSV header revalidation failed for " + target);
        }
        if (parsed.size() - 1 != rows.size()) {
            throw new IOException("CSV row-count revalidation failed for " + target);
        }
        for (int r = 0; r < rows.size(); r++) {
            if (!parsed.get(r + 1).equals(rows.get(r))) {
                throw new IOException("CSV content revalidation failed for " + target
                        + " at row " + (r + 1));
            }
        }
    }

    private static void writeTextAtomically(final Path target, final String text)
            throws IOException {
        Files.createDirectories(target.getParent());
        final Path temp = Files.createTempFile(
                target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
                writer.write(text);
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] readIfExists(final Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (final IOException ignored) {
            // treat as absent
        }
        return null;
    }

    private static void restorePrevious(final Path path, final byte[] previous) {
        try {
            if (Files.isDirectory(path)) {
                deleteRecursively(path);
            }
            if (previous == null) {
                Files.deleteIfExists(path);
            } else {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, previous);
            }
        } catch (final IOException ignored) {
            // best effort
        }
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (final Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static String buildValidationFailure(
            final BatchTiffDiscovery.Result discovery,
            final BatchConfiguration config) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Discovery/expected-set validation failed.\n");
        sb.append("valid TIFF count: ").append(discovery.validTiffs().size()).append('\n');
        sb.append("expected TIFF count: ").append(80).append('\n');
        sb.append("require complete set: ").append(config.requireCompleteExpectedSet()).append('\n');
        sb.append("malformed TIFF-like files: ").append(discovery.malformedTiffLikeFiles()).append('\n');
        sb.append("duplicate IDs: ").append(discovery.duplicateIds()).append('\n');
        sb.append("missing IDs: ");
        for (int i = 0; i < discovery.missingExpectedIds().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(discovery.missingExpectedIds().get(i).imageId());
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String buildAssignmentSummary(
            final List<BatchMachineAssignment.MachineSlice> slices,
            final int totalTiffs,
            final BatchConfiguration config,
            final String created) {
        final StringBuilder sb = new StringBuilder();
        sb.append("machine_assignment_summary\n");
        sb.append("total_tiff_count=").append(totalTiffs).append('\n');
        sb.append("configuration_sha256=").append(config.sha256()).append('\n');
        sb.append("require_complete_expected_set=")
                .append(config.requireCompleteExpectedSet()).append('\n');
        sb.append("input_recursive=").append(config.recursive()).append('\n');
        sb.append("creation_timestamp=").append(created).append('\n');
        sb.append('\n');
        for (int i = 0; i < slices.size(); i++) {
            final BatchMachineAssignment.MachineSlice slice = slices.get(i);
            sb.append("machine_id=").append(slice.machineId()).append('\n');
            sb.append("assigned_count=").append(slice.assignedCount()).append('\n');
            sb.append("first_image_id=").append(slice.firstImageIdOrNa()).append('\n');
            sb.append("last_image_id=").append(slice.lastImageIdOrNa()).append('\n');
            sb.append("total_source_bytes=").append(slice.totalSourceBytes()).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String buildPlanReport(
            final PlanRequest request,
            final BatchConfiguration config,
            final BatchTiffDiscovery.Result discovery,
            final String status,
            final String created) {
        final StringBuilder sb = new StringBuilder();
        sb.append("batch_plan_report\n");
        sb.append("creation_timestamp=").append(created).append('\n');
        sb.append("input_root=").append(request.inputRoot.toAbsolutePath().normalize()).append('\n');
        sb.append("batch_root=").append(request.batchRoot.toAbsolutePath().normalize()).append('\n');
        sb.append("configuration_path=")
                .append(request.configPath.toAbsolutePath().normalize()).append('\n');
        sb.append("configuration_sha256=").append(config.sha256()).append('\n');
        sb.append("valid_tiff_count=").append(discovery.validTiffs().size()).append('\n');
        sb.append("expected_tiff_count=80\n");
        sb.append("missing_ids=");
        for (int i = 0; i < discovery.missingExpectedIds().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(discovery.missingExpectedIds().get(i).imageId());
        }
        sb.append('\n');
        sb.append("duplicate_ids=").append(join(discovery.duplicateIds())).append('\n');
        sb.append("malformed_tiff_like_files=")
                .append(join(discovery.malformedTiffLikeFiles())).append('\n');
        sb.append("non_tiff_files=").append(join(discovery.nonTiffFiles())).append('\n');
        sb.append("machine_count=").append(config.machinesCount()).append('\n');
        sb.append("planning_status=").append(status).append('\n');
        return sb.toString();
    }

    private static String join(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
