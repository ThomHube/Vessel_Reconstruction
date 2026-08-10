package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only pure-Java planner that builds a deterministic Phase-4 machine run
 * plan from the Phase-1 machine manifest, Phase-2 precheck results, and
 * canonical production dataset inspection.
 *
 * <p>Never launches Fiji, never deserializes a Weka model, and never writes
 * production or attempt artifacts.</p>
 */
public final class BatchMachineRunPlanner {

    private BatchMachineRunPlanner() {
    }

    /**
     * Immutable planning request.
     */
    public static final class Request {
        public final Path inputRoot;
        public final Path batchRoot;
        public final String machineId;
        public final Path configPath;
        public final Path classifierPath;
        public final int tileProfileIndex;

        public Request(
                final Path inputRoot,
                final Path batchRoot,
                final String machineId,
                final Path configPath,
                final Path classifierPath,
                final int tileProfileIndex) {
            if (inputRoot == null || batchRoot == null || configPath == null
                    || classifierPath == null) {
                throw new IllegalArgumentException("paths must not be null");
            }
            if (machineId == null || machineId.trim().isEmpty()) {
                throw new IllegalArgumentException("machineId must not be blank");
            }
            if (tileProfileIndex < 0) {
                throw new IllegalArgumentException(
                        "tileProfileIndex must be non-negative: " + tileProfileIndex);
            }
            this.inputRoot = inputRoot.toAbsolutePath().normalize();
            this.batchRoot = batchRoot.toAbsolutePath().normalize();
            this.machineId = machineId.trim();
            this.configPath = configPath.toAbsolutePath().normalize();
            this.classifierPath = classifierPath.toAbsolutePath().normalize();
            this.tileProfileIndex = tileProfileIndex;
        }
    }

    public static BatchMachineRunPlan plan(final Request request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        final BatchConfiguration config = BatchConfiguration.load(request.configPath);
        if (request.tileProfileIndex >= config.tileProfiles().size()) {
            throw new IllegalArgumentException(
                    "tileProfileIndex out of range [0,"
                            + (config.tileProfiles().size() - 1) + "]: "
                            + request.tileProfileIndex);
        }
        if (!Files.isRegularFile(request.classifierPath)) {
            throw new IllegalArgumentException(
                    "classifier file missing: " + request.classifierPath);
        }
        final String classifierSha = Sha256Files.sha256OfFile(request.classifierPath);

        final Path manifest = request.batchRoot
                .resolve(BatchManifestSchema.DIR_MACHINES)
                .resolve(BatchManifestSchema.machineManifestFilename(request.machineId));
        final List<BatchPrecheckSupervisor.ManifestRow> rows =
                BatchPrecheckSupervisor.readMachineManifest(
                        manifest, request.machineId, config.sha256());

        validateAssignment(rows, request.inputRoot);

        final List<BatchPrecheckSupervisor.ManifestRow> ordered =
                new ArrayList<BatchPrecheckSupervisor.ManifestRow>(rows);
        Collections.sort(ordered, new Comparator<BatchPrecheckSupervisor.ManifestRow>() {
            @Override
            public int compare(
                    final BatchPrecheckSupervisor.ManifestRow a,
                    final BatchPrecheckSupervisor.ManifestRow b) {
                return SixDigitSampleId.parse(a.imageId)
                        .compareTo(SixDigitSampleId.parse(b.imageId));
            }
        });

        final Path datasetRoot = request.batchRoot.resolve(
                BatchManifestSchema.datasetShardRelativePath(request.machineId))
                .toAbsolutePath().normalize();

        final List<BatchMachineImagePlan> plans = new ArrayList<BatchMachineImagePlan>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            final BatchPrecheckSupervisor.ManifestRow row = ordered.get(i);
            final Path sourceTiff = request.inputRoot.resolve(row.sourceRelativePath)
                    .toAbsolutePath().normalize();
            plans.add(planOneImage(
                    i + 1,
                    request.machineId,
                    row,
                    sourceTiff,
                    datasetRoot,
                    request.batchRoot,
                    config,
                    classifierSha,
                    request.tileProfileIndex));
        }
        return BatchMachineRunPlan.of(request.machineId, plans);
    }

    private static void validateAssignment(
            final List<BatchPrecheckSupervisor.ManifestRow> rows,
            final Path inputRoot) throws IOException {
        final Set<String> seenIds = new HashSet<String>();
        final Set<String> seenSources = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            final BatchPrecheckSupervisor.ManifestRow row = rows.get(i);
            final SixDigitSampleId id = SixDigitSampleId.parse(row.imageId);
            if (!seenIds.add(id.imageId())) {
                throw new IllegalArgumentException("duplicate image ID: " + id.imageId());
            }
            final String sourceKey = row.sourceRelativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!seenSources.add(sourceKey)) {
                throw new IllegalArgumentException(
                        "duplicate source TIFF assignment: " + row.sourceRelativePath);
            }
            final Path source = inputRoot.resolve(row.sourceRelativePath).toAbsolutePath().normalize();
            if (!source.startsWith(inputRoot.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException(
                        "source TIFF escapes input root: " + row.sourceRelativePath);
            }
            if (!Files.isRegularFile(source)) {
                throw new IllegalArgumentException("source TIFF missing: " + source);
            }
            final String actualSha = Sha256Files.sha256OfFile(source);
            if (!actualSha.equalsIgnoreCase(row.sourceSha256)) {
                throw new IllegalArgumentException(
                        "source SHA-256 mismatch for " + row.imageId
                                + ": expected " + row.sourceSha256 + ", found " + actualSha);
            }
        }
    }

    private static BatchMachineImagePlan planOneImage(
            final int sequenceNumber,
            final String machineId,
            final BatchPrecheckSupervisor.ManifestRow row,
            final Path sourceTiff,
            final Path datasetRoot,
            final Path batchRoot,
            final BatchConfiguration config,
            final String classifierSha,
            final int tileProfileIndex) {
        final BatchMachineImagePlan.Builder builder = BatchMachineImagePlan.builder()
                .sequenceNumber(sequenceNumber)
                .imageId(row.imageId)
                .machineId(machineId)
                .sourceTiff(sourceTiff)
                .sourceSha256(row.sourceSha256)
                .tileProfileIndex(Integer.valueOf(tileProfileIndex));

        final Path precheckResultPath = batchRoot
                .resolve(BatchPrecheckCsvSchema.machinePrecheckRoot(machineId))
                .resolve(BatchPrecheckCsvSchema.DIR_JOBS)
                .resolve(row.imageId)
                .resolve("result.properties");

        if (!Files.isRegularFile(precheckResultPath)) {
            return builder
                    .state(BatchMachineImageState.PRECHECK_MISSING)
                    .plannedAction(BatchMachinePlannedAction.RUN_PRECHECK)
                    .reasonCode("PRECHECK_MISSING")
                    .message("no authoritative precheck result")
                    .build();
        }

        final BatchPrecheckResult precheck;
        try {
            precheck = BatchPrecheckResult.load(precheckResultPath);
        } catch (final Exception ex) {
            return builder
                    .state(BatchMachineImageState.UNKNOWN_INVALID_STATE)
                    .plannedAction(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE)
                    .reasonCode("PRECHECK_UNREADABLE")
                    .message(ex.getMessage())
                    .build();
        }

        final BatchPrecheckStatus precheckStatus = precheck.status();
        if (precheckStatus.isReviewRequired()) {
            return builder
                    .state(BatchMachineImageState.PRECHECK_REVIEW_REQUIRED)
                    .plannedAction(BatchMachinePlannedAction.WAIT_FOR_REVIEW)
                    .reasonCode(precheckStatus.name())
                    .message(precheck.get(BatchPrecheckResult.KEY_MESSAGE))
                    .build();
        }
        if (precheckStatus.isFailure()) {
            return builder
                    .state(BatchMachineImageState.PRECHECK_FAILED)
                    .plannedAction(BatchMachinePlannedAction.RUN_PRECHECK)
                    .reasonCode(precheckStatus.name())
                    .message(precheck.get(BatchPrecheckResult.KEY_MESSAGE))
                    .build();
        }
        if (!precheckStatus.isSuccess()) {
            return builder
                    .state(BatchMachineImageState.UNKNOWN_INVALID_STATE)
                    .plannedAction(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE)
                    .reasonCode(precheckStatus.name())
                    .message("unsupported precheck status")
                    .build();
        }

        final String precheckSourceSha = precheck.get(BatchPrecheckResult.KEY_SOURCE_SHA256);
        if (precheckSourceSha == null
                || !precheckSourceSha.equalsIgnoreCase(row.sourceSha256)) {
            return builder
                    .state(BatchMachineImageState.UNKNOWN_INVALID_STATE)
                    .plannedAction(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE)
                    .reasonCode("PRECHECK_STALE_SOURCE")
                    .message("precheck source SHA-256 does not match machine manifest")
                    .build();
        }
        final String precheckConfigSha = precheck.get(BatchPrecheckResult.KEY_CONFIG_SHA256);
        if (precheckConfigSha == null
                || !precheckConfigSha.equalsIgnoreCase(config.sha256())) {
            return builder
                    .state(BatchMachineImageState.UNKNOWN_INVALID_STATE)
                    .plannedAction(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE)
                    .reasonCode("PRECHECK_STALE_CONFIGURATION")
                    .message("precheck configuration SHA-256 does not match current config")
                    .build();
        }

        final BatchProductionOutputValidator.FinalizedDatasetInspection inspection =
                BatchProductionOutputValidator.inspectFinalizedDataset(
                        datasetRoot,
                        row.imageId,
                        row.sourceSha256,
                        classifierSha,
                        config.reconstructionMinimumLumenAreaPx(),
                        config.refreshAggregateTables());

        switch (inspection.state) {
            case FINAL_DATASET_COMPLETE:
                return builder
                        .state(BatchMachineImageState.FULL_SUCCESS)
                        .plannedAction(BatchMachinePlannedAction.SKIP_COMPLETE)
                        .reasonCode(RCsvSupport.NA)
                        .message("final dataset already complete")
                        .classificationRunId(inspection.classificationRunId)
                        .reconstructionRunId(inspection.reconstructionRunId)
                        .build();
            case CLASSIFICATION_COMPLETE_RECONSTRUCTION_INCOMPLETE:
                return builder
                        .state(BatchMachineImageState.CLASSIFICATION_COMPLETE)
                        .plannedAction(BatchMachinePlannedAction.RESUME_RECONSTRUCTION)
                        .reasonCode(inspection.reasonCode)
                        .message("classification reusable; reconstruction incomplete")
                        .classificationRunId(inspection.classificationRunId)
                        .build();
            case CANONICAL_CONFLICT:
                return builder
                        .state(BatchMachineImageState.CANONICAL_CONFLICT)
                        .plannedAction(BatchMachinePlannedAction.STOP_FOR_CANONICAL_CONFLICT)
                        .reasonCode(inspection.reasonCode)
                        .message(inspection.failures.isEmpty()
                                ? "canonical conflict"
                                : inspection.failures.get(0))
                        .build();
            case CLASSIFICATION_INCOMPLETE:
                return builder
                        .state(BatchMachineImageState.READY_FOR_FULL_PROCESSING)
                        .plannedAction(BatchMachinePlannedAction.RUN_FULL_PROCESSING)
                        .reasonCode(inspection.reasonCode)
                        .message("precheck success; classification incomplete")
                        .build();
            default:
                return builder
                        .state(BatchMachineImageState.UNKNOWN_INVALID_STATE)
                        .plannedAction(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE)
                        .reasonCode("UNKNOWN_PRODUCTION_STATE")
                        .message(String.valueOf(inspection.state))
                        .build();
        }
    }
}
