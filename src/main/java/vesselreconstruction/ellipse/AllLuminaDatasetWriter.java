package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.process.ByteProcessor;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Commits accepted all-lumen reconstruction and its reproducible measurements.
 */
public final class AllLuminaDatasetWriter {

    public static final class Result {
        public final String runId;
        public final String timestamp;
        public final Path reconstructedClassified;
        public final Path measurements;
        public final Path diagnostics;

        private Result(final String runId, final String timestamp, final Path reconstructedClassified,
                final Path measurements, final Path diagnostics) {
            this.runId = runId;
            this.timestamp = timestamp;
            this.reconstructedClassified = reconstructedClassified;
            this.measurements = measurements;
            this.diagnostics = diagnostics;
        }
    }

    public Result write(final ImagePlus maskedClassified, final ByteProcessor source,
            final AllLumenBatchResult batchResult, final int minimumArea,
            final ProductionDatasetLayout layout, final LumenImageMetadata context,
            final boolean refreshAggregates) throws IOException {
        if (maskedClassified == null || source == null || batchResult == null || layout == null) {
            throw new IllegalArgumentException("image, source, batchResult, and layout must not be null");
        }
        if (minimumArea <= 0) {
            throw new IllegalArgumentException("minimumArea must be positive");
        }
        final LumenImageMetadata metadata = context == null ? metadataFromImage(maskedClassified) : context;
        final LumenCoordinateContext coordinates = metadata.toCoordinateContext();
        if (source.getWidth() != coordinates.getCropWidth()
                || source.getHeight() != coordinates.getCropHeight()) {
            throw new IllegalStateException("Source dimensions do not match metadata crop dimensions");
        }
        layout.ensureDirectoriesExist();
        final String safeSample = FilenameSanitizer.sanitizeForFilename(coordinates.getSampleId());
        final String safeSegment = FilenameSanitizer.sanitizeForFilename(coordinates.getSegmentId());
        final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        final String runId = FilenameSanitizer.runId(safeSample, safeSegment,
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION.name(), timestamp);
        final ProductionStageLogger logger = new ProductionStageLogger(layout.logs().resolve(runId + ".log"));
        final List<String> committed = new ArrayList<String>();
        try {
            logger.log("ALL_LUMINA_RECONSTRUCTION production save started");
            if (batchResult.getProcessingErrorCount() > 0) {
                throw new IOException("Processing errors occurred; refusing production SUCCESS.");
            }
            final String sourceShaBefore = Sha256Files.sha256OfBytes((byte[]) source.getPixels());
            final FileInfo info = maskedClassified.getOriginalFileInfo();
            final File sourceFile = info == null || info.directory == null || info.fileName == null
                    ? null : new File(info.directory, info.fileName);
            final String sourceFileShaBefore = sourceFile != null && sourceFile.isFile()
                    ? Sha256Files.sha256OfFile(sourceFile.toPath()) : null;
            final String inputFilename = sourceFile != null
                    ? sourceFile.getName()
                    : RCsvSupport.textOrNa(maskedClassified.getTitle());

            final ByteProcessor reconstructed = FinalLumenGeometry.buildReconstructedProcessor(source, batchResult);
            final String reconstructedPixelsSha = Sha256Files.sha256OfBytes((byte[]) reconstructed.getPixels());
            final List<FinalLumenGeometry.ComponentGeometry> geometry =
                    FinalLumenGeometry.buildAll(source, batchResult, minimumArea);
            final ImagePlus output = new ImagePlus("reconstructed_classified", reconstructed);
            output.setCalibration(maskedClassified.getCalibration());
            final String outputFilename = FilenameSanitizer.reconstructedClassifiedFilename(
                    safeSample, safeSegment);
            final String sourceFileShaForMeta = sourceFileShaBefore != null
                    ? sourceFileShaBefore : sourceShaBefore;
            appendOutputMetadata(output, maskedClassified, layout, runId, outputFilename,
                    sourceFileShaForMeta, reconstructedPixelsSha, batchResult);
            final Path outputPath = layout.imagesReconstructedClassified().resolve(outputFilename);
            final String outputSha = ClassificationDatasetWriter.commitImagePlusAsTiff(
                    output, outputPath, true);
            output.close();
            committed.add("reconstructed_classified=" + outputPath);

            if (!sourceShaBefore.equals(Sha256Files.sha256OfBytes((byte[]) source.getPixels()))) {
                throw new IllegalStateException("Source masked classified processor changed during reconstruction");
            }
            if (sourceFileShaBefore != null
                    && !sourceFileShaBefore.equals(Sha256Files.sha256OfFile(sourceFile.toPath()))) {
                throw new IllegalStateException("Source masked classified file changed during reconstruction");
            }

            final List<List<String>> measurementRows = measurementRows(geometry, coordinates,
                    safeSample, safeSegment, runId, minimumArea, outputFilename);
            final List<List<String>> diagnosticRows = diagnosticRows(batchResult, coordinates,
                    safeSample, safeSegment, runId);
            final Path measurementPath = layout.dataLumenMeasurementsPerSegment().resolve(
                    FilenameSanitizer.lumenMeasurementsFilename(safeSample, safeSegment));
            final Path diagnosticPath = layout.dataDiagnosticsPerSegment().resolve(
                    FilenameSanitizer.artifactDiagnosticsFilename(safeSample, safeSegment));
            ProductionCsvStore.writeTableAtomically(measurementPath,
                    ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, measurementRows);
            committed.add("lumen_measurements=" + measurementPath);
            ProductionCsvStore.writeTableAtomically(diagnosticPath,
                    ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER, diagnosticRows);
            committed.add("artifact_diagnostics=" + diagnosticPath);
            updateManifest(layout, coordinates, outputPath, outputSha, runId, timestamp);
            committed.add("image_manifest=" + layout.imageManifest());
            if (refreshAggregates) {
                ProductionCsvStore.refreshAggregateFromPerSegment(
                        layout.dataLumenMeasurementsPerSegment(), layout.dataLumenMeasurementsAll(),
                        ProductionSchemas.LUMEN_MEASUREMENTS_HEADER, "lumen_uid",
                        ProductionCsvStore.lumenRowComparator(ProductionSchemas.LUMEN_MEASUREMENTS_HEADER));
                committed.add("lumen_measurements_all=" + layout.dataLumenMeasurementsAll());
                ProductionCsvStore.refreshAggregateFromPerSegment(layout.dataDiagnosticsPerSegment(),
                        layout.dataDiagnosticsAll(), ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER, "lumen_uid",
                        ProductionCsvStore.lumenRowComparator(ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER));
                committed.add("artifact_diagnostics_all=" + layout.dataDiagnosticsAll());
            }
            DataDictionaryWriter.writeIdempotent(layout);
            ProcessingRunsManifest.ensureCanonicalSchema(
                    layout.processingRuns(),
                    ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
            writeSuccess(layout, runId, timestamp, coordinates, metadata, inputFilename,
                    outputFilename, minimumArea, batchResult);
            committed.add("processing_runs=" + layout.processingRuns());
            logger.log("ALL_LUMINA_RECONSTRUCTION SUCCESS");
            return new Result(runId, timestamp, outputPath, measurementPath, diagnosticPath);
        } catch (final Exception ex) {
            logger.log("ALL_LUMINA_RECONSTRUCTION FAILED: " + ex.getMessage());
            if (!committed.isEmpty()) {
                logger.log("Already committed before failure: " + committed);
            }
            try {
                writeFailure(layout, runId, timestamp, coordinates, metadata, minimumArea,
                        batchResult, ex);
            } catch (final Exception failureWriteEx) {
                logger.log("WARNING: could not write FAILED processing-run row: "
                        + failureWriteEx.getMessage());
            }
            if (ex instanceof IOException) {
                throw (IOException) ex;
            }
            throw new IOException(ex.getMessage(), ex);
        } finally {
            logger.close();
        }
    }

    public Result write(final ImagePlus maskedClassified, final ByteProcessor source,
            final AllLumenBatchResult batchResult, final int minimumArea,
            final ProductionDatasetLayout layout, final boolean refreshAggregates) throws IOException {
        return write(maskedClassified, source, batchResult, minimumArea, layout, null, refreshAggregates);
    }

    static List<String> diagnosticsHeader() {
        return ProductionSchemas.ARTIFACT_DIAGNOSTICS_HEADER;
    }

    private static LumenImageMetadata metadataFromImage(final ImagePlus image) {
        final LumenImageMetadata.ParseResult parsed =
                LumenImageMetadata.parse(String.valueOf(image.getProperty("Info")));
        if (!parsed.isAvailable()) {
            throw new IllegalStateException("Missing image metadata: " + parsed.getUnavailableReason());
        }
        return parsed.getMetadata().get();
    }

    private static void appendOutputMetadata(final ImagePlus output, final ImagePlus input,
            final ProductionDatasetLayout layout, final String runId, final String outputFilename,
            final String sourceSha, final String reconstructedPixelsSha,
            final AllLumenBatchResult result) {
        final String existing = input.getProperty("Info") == null ? ""
                : String.valueOf(input.getProperty("Info"));
        final String extra = "lumen.dataset_root=" + layout.getDatasetRoot()
                + "\nlumen.data_schema_version=" + ProductionSchemas.DATA_SCHEMA_VERSION
                + "\nlumen.processing_run_id=" + runId
                + "\nlumen.reconstructed_classified_filename=" + outputFilename
                + "\nlumen.image_manifest_path=" + layout.imageManifest()
                + "\nlumen.segment_manifest_path=" + layout.segmentManifest()
                + "\nlumen.reconstruction_applied=true"
                + "\nlumen.reconstructed_components=" + result.getReconstructedComponentCount()
                + "\nlumen.reconstructed_changed_pixels=" + result.getCombinedChangedPixelCount()
                + "\nlumen.reconstruction_source_sha256=" + sourceSha
                + "\nlumen.reconstructed_output_sha256=" + reconstructedPixelsSha
                + "\nlumen.reconstruction_run_id=" + runId
                + "\nlumen.reconstruction_timestamp="
                + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        output.setProperty("Info", existing.isEmpty() ? extra : existing + "\n" + extra);
    }

    private static List<List<String>> measurementRows(
            final List<FinalLumenGeometry.ComponentGeometry> geometry,
            final LumenCoordinateContext context, final String safeSample, final String safeSegment,
            final String runId, final int minimumArea, final String outputFilename) {
        final Map<Integer, NearestLumenDistance.Result> nearest = nearestValid(geometry, context,
                safeSample, safeSegment, minimumArea);
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            if (g.areaPx < minimumArea) {
                continue;
            }
            final String uid = FilenameSanitizer.lumenUid(safeSample, safeSegment, g.lumenIdSegment);
            final Fit fit = fit(g, context);
            final MooreChainPerimeter.Result wall = MooreChainPerimeter.measure(
                    g.mask, g.maskWidth, g.maskHeight,
                    context.getPixelWidthUm(), context.getPixelHeightUm());
            final boolean wallOk = !Double.isNaN(wall.lengthPx);
            final boolean fitOk = !Double.isNaN(fit.majorPx);
            String exclusion = RCsvSupport.NA;
            boolean valid = true;
            if (g.touchesBorder) {
                valid = false;
                exclusion = "TOUCHES_ANALYSIS_BORDER";
            } else if (!wallOk) {
                valid = false;
                exclusion = "INVALID_FINAL_GEOMETRY";
            } else if (!fitOk) {
                valid = false;
                exclusion = "ELLIPSE_MEASUREMENT_FAILED";
            }
            final NearestLumenDistance.Result n = nearest.get(Integer.valueOf(g.lumenIdSegment));
            final double cxOriginal = context.cropXToOriginal(g.centroidX);
            final double cyOriginal = context.cropYToOriginal(g.centroidY);
            final LumenBatchItemResult item = g.sourceItem;
            final boolean artifactDetected = item.isReconstructed()
                    || item.getStatus() == LumenBatchItemResult.Status.RECONSTRUCTED_SINGLE_ARTIFACT;
            rows.add(RCsvSupport.mutableCopy(
                    Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION), runId, uid,
                    context.getSampleId(), context.getSegmentId(),
                    Integer.toString(context.getSegmentOrder()), context.getSegmentOrientation().name(),
                    Integer.toString(g.lumenIdSegment), RCsvSupport.NA, RCsvSupport.NA,
                    RCsvSupport.NA, RCsvSupport.NA, outputFilename,
                    Integer.toString(context.getCropX()), Integer.toString(context.getCropY()),
                    Integer.toString(context.getCropWidth()), Integer.toString(context.getCropHeight()),
                    Integer.toString(context.getOriginalWidthPx()), Integer.toString(context.getOriginalHeightPx()),
                    RCsvSupport.numberOrNa(context.getPixelWidthUm()),
                    RCsvSupport.numberOrNa(context.getPixelHeightUm()),
                    RCsvSupport.numberOrNa(g.centroidX), RCsvSupport.numberOrNa(g.centroidY),
                    RCsvSupport.numberOrNa(cxOriginal), RCsvSupport.numberOrNa(cyOriginal),
                    RCsvSupport.numberOrNa(context.originalXToPhysicalUm(cxOriginal)),
                    RCsvSupport.numberOrNa(context.originalYToPhysicalUm(cyOriginal)),
                    RCsvSupport.numberOrNa(context.segmentAxisPositionUm(cxOriginal, cyOriginal).orElse(null)),
                    RCsvSupport.numberOrNa(context.globalAxisPositionUm(cxOriginal, cyOriginal).orElse(null)),
                    RCsvSupport.NA, RCsvSupport.NA, Integer.toString(g.areaPx),
                    RCsvSupport.numberOrNa(context.areaPxToUm2(g.areaPx)),
                    RCsvSupport.numberOrNa(fit.majorPx), RCsvSupport.numberOrNa(fit.minorPx),
                    RCsvSupport.numberOrNa(fit.majorUm), RCsvSupport.numberOrNa(fit.minorUm),
                    RCsvSupport.numberOrNa(fit.ratio), RCsvSupport.numberOrNa(fit.rms),
                    RCsvSupport.numberOrNa(wall.lengthPx), RCsvSupport.numberOrNa(wall.lengthUm),
                    n == null ? RCsvSupport.NA : RCsvSupport.textOrNa(n.nearestLumenUid),
                    n == null ? RCsvSupport.NA : RCsvSupport.numberOrNa(n.nearestCentroidDistancePx),
                    n == null ? RCsvSupport.NA : RCsvSupport.numberOrNa(n.nearestCentroidDistanceUm),
                    n == null ? RCsvSupport.NA : RCsvSupport.numberOrNa(n.nearestWallDistancePx),
                    n == null ? RCsvSupport.NA : RCsvSupport.numberOrNa(n.nearestWallDistanceUm),
                    n == null ? RCsvSupport.NA : RCsvSupport.textOrNa(n.nearestDistanceScope),
                    RCsvSupport.logical(artifactDetected), RCsvSupport.logical(item.isReconstructed()),
                    Integer.toString(g.changedPixels), item.getStatus().name(),
                    RCsvSupport.logical(g.touchesBorder), RCsvSupport.logical(valid),
                    exclusion));
        }
        return rows;
    }

    private static Map<Integer, NearestLumenDistance.Result> nearestValid(
            final List<FinalLumenGeometry.ComponentGeometry> geometry, final LumenCoordinateContext context,
            final String sample, final String segment, final int minimumArea) {
        final List<NearestLumenDistance.LumenGeometry> valid =
                new ArrayList<NearestLumenDistance.LumenGeometry>();
        final List<Integer> ids = new ArrayList<Integer>();
        for (int i = 0; i < geometry.size(); i++) {
            final FinalLumenGeometry.ComponentGeometry g = geometry.get(i);
            if (g.areaPx >= minimumArea && !g.touchesBorder) {
                final MooreChainPerimeter.Result wall = MooreChainPerimeter.measure(
                        g.mask, g.maskWidth, g.maskHeight,
                        context.getPixelWidthUm(), context.getPixelHeightUm());
                valid.add(new NearestLumenDistance.LumenGeometry(
                        FilenameSanitizer.lumenUid(sample, segment, g.lumenIdSegment),
                        g.centroidX, g.centroidY,
                        translateBoundaryToGlobal(g, wall.boundaryPixels)));
                ids.add(Integer.valueOf(g.lumenIdSegment));
            }
        }
        final NearestLumenDistance.Result[] results = NearestLumenDistance.computeAll(valid,
                context.getPixelWidthUm(), context.getPixelHeightUm());
        final Map<Integer, NearestLumenDistance.Result> map =
                new HashMap<Integer, NearestLumenDistance.Result>();
        for (int i = 0; i < results.length; i++) {
            map.put(ids.get(i), results[i]);
        }
        return map;
    }

    /** Translates Moore-chain local-mask boundary pixels into global crop-local coordinates. */
    private static List<Point> translateBoundaryToGlobal(
            final FinalLumenGeometry.ComponentGeometry geometry,
            final List<Point> localBoundary) {
        final List<Point> global = new ArrayList<Point>(localBoundary.size());
        for (int i = 0; i < localBoundary.size(); i++) {
            global.add(geometry.localToGlobal(localBoundary.get(i)));
        }
        return global;
    }

    private static Fit fit(final FinalLumenGeometry.ComponentGeometry g,
            final LumenCoordinateContext context) {
        final List<Point> boundaryLocal =
                MooreChainPerimeter.extractBoundaryPixels(g.mask, g.maskWidth, g.maskHeight);
        if (boundaryLocal.size() < 6) {
            return Fit.unavailable();
        }
        final List<Point2D.Double> points = new ArrayList<Point2D.Double>(boundaryLocal.size());
        for (int i = 0; i < boundaryLocal.size(); i++) {
            final Point local = boundaryLocal.get(i);
            final Point global = g.localToGlobal(local);
            points.add(new Point2D.Double(global.x + 0.5, global.y + 0.5));
        }
        try {
            final EllipseFitResult result = new FreeEllipseFitter().fit(points);
            final EllipseModel ellipse = result.getFittedEllipse();
            final double major = ellipse.getSemiMajorAxis() * 2.0;
            final double minor = ellipse.getSemiMinorAxis() * 2.0;
            return new Fit(major, minor, major * context.getPixelWidthUm(),
                    minor * context.getPixelHeightUm(), minor / major,
                    result.getRootMeanSquaredResidual());
        } catch (final RuntimeException ex) {
            return Fit.unavailable();
        }
    }

    private static List<List<String>> diagnosticRows(final AllLumenBatchResult result,
            final LumenCoordinateContext context, final String sample, final String segment,
            final String runId) {
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < result.getItemResults().size(); i++) {
            final LumenBatchItemResult item = result.getItemResults().get(i);
            rows.add(RCsvSupport.mutableCopy(Integer.toString(ProductionSchemas.DATA_SCHEMA_VERSION), runId,
                    context.getSampleId(), context.getSegmentId(), Integer.toString(context.getSegmentOrder()),
                    FilenameSanitizer.lumenUid(sample, segment, item.getComponentId()),
                    Integer.toString(item.getComponentId()), item.getStatus().name(), item.getReason(),
                    RCsvSupport.logical(item.isIntervalSelected()), item.getIntervalSelectionReason(),
                    item.getArtifactEvidenceDecision(), item.getReconstructionCandidateDecision(),
                    intNa(item.getBoundaryEdgeCount()), intNa(item.getIntervalEdgeCount()),
                    intNa(item.getCorePointCount()), RCsvSupport.numberOrNa(item.getMinimumDepthSigma()),
                    RCsvSupport.numberOrNa(item.getMeanDepthSigma()), RCsvSupport.numberOrNa(item.getCoreFraction()),
                    RCsvSupport.numberOrNa(item.getNormalizedIntegratedInwardExcess()),
                    RCsvSupport.numberOrNa(item.getRetainedRmsImprovementFraction()),
                    intNa(item.getIntervalAssociatedComponentCount()), intNa(item.getMatchedExteriorSeedPixelCount()),
                    intNa(item.getUnmatchedExteriorSeedPixelCount()), intNa(item.getOtherLumenInsideRefittedEllipseCount()),
                    Integer.toString(item.getCandidatePixelCount()), RCsvSupport.numberOrNa(item.getOriginalCircularity()),
                    RCsvSupport.numberOrNa(item.getDiagnosticReconstructedCircularity()),
                    RCsvSupport.numberOrNa(item.getDiagnosticCircularityChange()),
                    RCsvSupport.numberOrNa(item.getAddedAreaFraction()),
                    RCsvSupport.numberOrNa(item.getProcessingTimeNanoseconds() / 1.0e6)));
        }
        return rows;
    }

    private static String intNa(final int value) {
        return value < 0 ? RCsvSupport.NA : Integer.toString(value);
    }

    private static void updateManifest(final ProductionDatasetLayout layout,
            final LumenCoordinateContext context, final Path outputPath, final String outputSha,
            final String runId, final String timestamp) throws IOException {
        final List<List<String>> table = ProductionCsvStore.readTable(layout.imageManifest());
        ProductionSchemas.requireExactHeader(table.get(0), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                layout.imageManifest().toString());
        final Map<String, Integer> index = ProductionCsvStore.headerIndex(ProductionSchemas.IMAGE_MANIFEST_HEADER);
        for (int r = 1; r < table.size(); r++) {
            final List<String> row = new ArrayList<String>(table.get(r));
            if (!context.getSampleId().equals(row.get(index.get("sample_id").intValue()))
                    || !context.getSegmentId().equals(row.get(index.get("segment_id").intValue()))) {
                continue;
            }
            row.set(index.get("reconstructed_classified_filename").intValue(),
                    outputPath.getFileName().toString());
            row.set(index.get("reconstructed_classified_path").intValue(), outputPath.toString());
            row.set(index.get("reconstructed_classified_sha256").intValue(), outputSha);
            row.set(index.get("reconstruction_run_id").intValue(), runId);
            row.set(index.get("reconstruction_timestamp").intValue(), timestamp);
            ProductionCsvStore.upsertByKeys(layout.imageManifest(), ProductionSchemas.IMAGE_MANIFEST_HEADER,
                    row, "sample_id", "segment_id");
            return;
        }
        throw new IOException("No image_manifest row for " + context.getSampleId()
                + "/" + context.getSegmentId());
    }

    private static void writeSuccess(
            final ProductionDatasetLayout layout,
            final String runId,
            final String timestamp,
            final LumenCoordinateContext context,
            final LumenImageMetadata metadata,
            final String inputFilename,
            final String outputFilename,
            final int minimumArea,
            final AllLumenBatchResult result) throws IOException {
        final ProcessingRunRecord record = buildReconstructionRecord(
                runId, timestamp, context, metadata, inputFilename, outputFilename,
                ProductionSchemas.RunStatus.SUCCESS, minimumArea, result,
                "all-lumina reconstruction committed");
        ProcessingRunsManifest.append(
                layout.processingRuns(),
                record,
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
    }

    private static void writeFailure(
            final ProductionDatasetLayout layout,
            final String runId,
            final String timestamp,
            final LumenCoordinateContext context,
            final LumenImageMetadata metadata,
            final int minimumArea,
            final AllLumenBatchResult result,
            final Exception cause) throws IOException {
        final String message = cause == null || cause.getMessage() == null
                ? "production save failed"
                : cause.getMessage();
        final ProcessingRunRecord record = buildReconstructionRecord(
                runId, timestamp, context, metadata, RCsvSupport.NA, RCsvSupport.NA,
                ProductionSchemas.RunStatus.FAILED, minimumArea, result, message);
        ProcessingRunsManifest.append(
                layout.processingRuns(),
                record,
                ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION);
    }

    private static ProcessingRunRecord buildReconstructionRecord(
            final String runId,
            final String timestamp,
            final LumenCoordinateContext context,
            final LumenImageMetadata metadata,
            final String inputFilename,
            final String outputFilename,
            final ProductionSchemas.RunStatus status,
            final int minimumArea,
            final AllLumenBatchResult result,
            final String message) {
        return ProcessingRunRecord.builder()
                .schemaVersion(ProductionSchemas.DATA_SCHEMA_VERSION)
                .runId(runId)
                .timestamp(timestamp)
                .stage(ProductionSchemas.ProcessingStage.ALL_LUMINA_RECONSTRUCTION)
                .status(status)
                .sampleId(context.getSampleId())
                .segmentId(context.getSegmentId())
                .inputFilename(inputFilename)
                .outputFilename(outputFilename)
                .pluginVersion("0.1.0-SNAPSHOT")
                .gitCommit(RCsvSupport.NA)
                .classifierFilename(optionalMetadata(metadata, LumenImageMetadata.KEY_CLASSIFIER_FILENAME))
                .coreTileWidthPx(optionalIntMetadata(metadata, LumenImageMetadata.KEY_CORE_TILE_WIDTH_PX))
                .coreTileHeightPx(optionalIntMetadata(metadata, LumenImageMetadata.KEY_CORE_TILE_HEIGHT_PX))
                .threadsPerTile(null)
                .maskDownsampleFactor(optionalIntMetadata(
                        metadata, LumenImageMetadata.KEY_MASK_DOWNSAMPLE_FACTOR))
                .maskExpansionMarginPx(null)
                .minimumLumenAreaPx(Integer.valueOf(minimumArea))
                .totalComponents(Integer.valueOf(result.getTotalComponentCount()))
                .eligibleComponents(Integer.valueOf(result.getEligibleComponentCount()))
                .reconstructedComponents(Integer.valueOf(result.getReconstructedComponentCount()))
                .changedPixels(Integer.valueOf(result.getCombinedChangedPixelCount()))
                .processingTimeMs(Double.valueOf(result.getTotalProcessingTimeNanoseconds() / 1.0e6))
                .message(message)
                .build();
    }

    private static String optionalMetadata(final LumenImageMetadata metadata, final String key) {
        if (metadata == null) {
            return RCsvSupport.NA;
        }
        final java.util.Optional<String> value = metadata.getOptional(key);
        if (!value.isPresent() || value.get().trim().isEmpty()) {
            return RCsvSupport.NA;
        }
        return value.get();
    }

    private static Integer optionalIntMetadata(final LumenImageMetadata metadata, final String key) {
        if (metadata == null) {
            return null;
        }
        final java.util.Optional<String> value = metadata.getOptional(key);
        if (!value.isPresent() || value.get().trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(value.get().trim()));
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static final class Fit {
        private final double majorPx, minorPx, majorUm, minorUm, ratio, rms;
        private Fit(double majorPx, double minorPx, double majorUm, double minorUm, double ratio, double rms) {
            this.majorPx = majorPx; this.minorPx = minorPx; this.majorUm = majorUm;
            this.minorUm = minorUm; this.ratio = ratio; this.rms = rms;
        }
        private static Fit unavailable() {
            return new Fit(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }
}
