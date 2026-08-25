package vesselreconstruction.ellipse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-command automated hardware benchmark for the single-machine architecture.
 *
 * <p>Default: optional synthetic sanity, then {@code REAL_LUMEN_CLASSIFICATION}.
 * {@code --synthetic-only} keeps the probe and never marks the profile
 * production-ready.</p>
 *
 * <pre>
 * java -cp target/classes vesselreconstruction.ellipse.HardwareBenchmarkCli \
 *   --output-root hardware_benchmark
 * </pre>
 */
public final class HardwareBenchmarkCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_FAILURE = 3;

    private HardwareBenchmarkCli() {
    }

    public static void main(final String[] args) {
        System.exit(run(args));
    }

    static int run(final String[] args) {
        final Map<String, String> values;
        try {
            values = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_USAGE;
        }
        try {
            return execute(values);
        } catch (final Exception ex) {
            System.err.println("ERROR: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return EXIT_FAILURE;
        }
    }

    static int execute(final Map<String, String> values) throws Exception {
        final Path outputParent = Paths.get(valueOr(values, "--output-root", "hardware_benchmark"))
                .toAbsolutePath().normalize();
        final String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        final Path outputRoot = outputParent.resolve(stamp);
        Files.createDirectories(outputRoot);

        final Path classes = Paths.get(valueOr(values, "--classes-dir", "target/classes"))
                .toAbsolutePath().normalize();
        final Path java = resolveJavaExecutable(values);
        Path fiji = values.containsKey("--fiji-launcher")
                ? Paths.get(values.get("--fiji-launcher")).toAbsolutePath().normalize()
                : null;
        final Path inputRoot = values.containsKey("--input-root")
                ? Paths.get(values.get("--input-root")).toAbsolutePath().normalize()
                : null;
        final Path procRoot = values.containsKey("--proc-root")
                ? Paths.get(values.get("--proc-root"))
                : HardwareBenchmarkMonitor.defaultProcRoot();

        final HardwareInventory inventory = HardwareInventoryDetector.detect(procRoot, fiji);
        final String commit = valueOr(values, "--source-commit", detectCommit());
        final boolean syntheticOnly = flag(values, "--synthetic-only");
        final Path classifier = values.containsKey("--classifier")
                ? Paths.get(values.get("--classifier")).toAbsolutePath().normalize()
                : null;
        final Path configTemplate = Paths.get(valueOr(values, "--config",
                "runtime/batch/default_batch.properties")).toAbsolutePath().normalize();
        final StringBuilder log = new StringBuilder();
        HardwareBenchmarkRunner.log(log, "LUMEN AUTOMATED HARDWARE BENCHMARK");
        HardwareBenchmarkRunner.log(log, "BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.");
        HardwareBenchmarkRunner.log(log, "DEVELOPMENT MACHINE unless this host is labelled otherwise.");
        HardwareBenchmarkRunner.log(log, "tile=" + HardwareExecutionProfile.PRODUCTION_TILE_PROFILE_TOKEN);
        HardwareBenchmarkRunner.log(log, "cpu=" + inventory.cpuModel());
        HardwareBenchmarkRunner.log(log, "physical_cores=" + inventory.physicalCores());
        HardwareBenchmarkRunner.log(log, "logical_cpus=" + inventory.logicalCpus());
        HardwareBenchmarkRunner.log(log, "ram_mb=" + inventory.ramMegabytes());

        HardwareBenchmarkWorkload.Prepared workload = null;
        Path pluginsDir = null;
        if (!syntheticOnly) {
            if (fiji == null || !Files.isRegularFile(fiji)) {
                throw new IllegalStateException(
                        "REAL_LUMEN_CLASSIFICATION requires --fiji-launcher; use --synthetic-only for the probe");
            }
            if (classifier == null || !Files.isRegularFile(classifier)) {
                throw new IllegalStateException(
                        "REAL_LUMEN_CLASSIFICATION requires --classifier; use --synthetic-only for the probe");
            }
            if (inputRoot == null) {
                throw new IllegalStateException(
                        "REAL_LUMEN_CLASSIFICATION requires --input-root pointing at 9999A TEST TIFFs");
            }
            final Path pluginJar = Paths.get(valueOr(values, "--plugin-jar",
                    "target/VesselReconstructionEllipse-0.1.0-SNAPSHOT.jar"))
                    .toAbsolutePath().normalize();
            if (!Files.isRegularFile(pluginJar)) {
                throw new IllegalStateException(
                        "REAL_LUMEN_CLASSIFICATION requires plugin JAR at " + pluginJar
                                + " (mvn package; production Fiji.app/plugins is not modified)");
            }
            final Path isolatedFiji = HardwareBenchmarkFijiCommands.isolateFijiApp(
                    fiji, outputRoot.resolve("fiji_app"), pluginJar);
            pluginsDir = outputRoot.resolve("fiji_app").resolve("plugins").toAbsolutePath().normalize();
            HardwareBenchmarkRunner.log(log, "plugin_jar=" + pluginJar);
            HardwareBenchmarkRunner.log(log, "isolated_fiji=" + isolatedFiji);
            HardwareBenchmarkRunner.log(log, "fiji_plugins=" + pluginsDir);
            fiji = isolatedFiji;
            final Path original = HardwareBenchmarkWorkload.discover9999A1(inputRoot);
            if (original == null) {
                throw new IllegalStateException("9999A1.tif not found under " + inputRoot);
            }
            try {
                Class.forName("ij.io.Opener");
            } catch (final ClassNotFoundException ex) {
                throw new IllegalStateException(
                        "REAL_LUMEN_CLASSIFICATION requires ImageJ (ij.jar) on the classpath");
            }
            workload = HardwareBenchmarkWorkload.prepareIsolatedCopy(
                    original, outputRoot.resolve("workload"), true);
            HardwareBenchmarkRunner.log(log, "workload=" + workload.identity);
            HardwareBenchmarkRunner.log(log, "original_sha256=" + workload.originalSha256);
            HardwareBenchmarkRunner.log(log, "workload_sha256=" + workload.workloadSha256);
            HardwareBenchmarkRunner.log(log,
                    "original_px=" + workload.originalWidthPx + "x" + workload.originalHeightPx);
            HardwareBenchmarkRunner.log(log,
                    "workload_px=" + workload.workloadWidthPx + "x" + workload.workloadHeightPx);
        }

        final List<HardwareBenchmarkPlanner.Candidate> planned =
                new ArrayList<HardwareBenchmarkPlanner.Candidate>(
                        HardwareBenchmarkPlanner.plan(inventory));

        if (!syntheticOnly && workload != null && workload.cropped && !planned.isEmpty()) {
            HardwareBenchmarkRunner.log(log,
                    "probing cropped workload precheck before REAL_LUMEN_CLASSIFICATION");
            try {
                final BatchPrecheckSupervisor.Summary probe =
                        HardwareBenchmarkClassificationSupport.probePrecheck(
                                outputRoot.resolve("workload_crop_precheck"),
                                workload,
                                planned.get(0),
                                configTemplate,
                                fiji,
                                pluginsDir);
                HardwareBenchmarkRunner.log(log, "crop_precheck success=" + probe.success
                        + " review=" + probe.reviewRequired
                        + " failed=" + probe.failed);
                if (probe.success < 1) {
                    HardwareBenchmarkRunner.log(log,
                            "cropped 2304x2304 workload failed precheck; falling back to full 9999A1 TEST TIFF");
                    workload = HardwareBenchmarkWorkload.prepareIsolatedCopy(
                            workload.originalPath, outputRoot.resolve("workload"), false);
                    HardwareBenchmarkRunner.log(log, "workload=" + workload.identity);
                    HardwareBenchmarkRunner.log(log, "workload_sha256=" + workload.workloadSha256);
                    HardwareBenchmarkRunner.log(log,
                            "workload_px=" + workload.workloadWidthPx + "x"
                                    + workload.workloadHeightPx);
                }
            } catch (final Exception ex) {
                HardwareBenchmarkRunner.log(log,
                        "crop precheck probe failed (" + ex.getMessage()
                                + "); falling back to full 9999A1 TEST TIFF");
                workload = HardwareBenchmarkWorkload.prepareIsolatedCopy(
                        workload.originalPath, outputRoot.resolve("workload"), false);
            }
        }

        List<HardwareBenchmarkRunResult> syntheticResults = new ArrayList<HardwareBenchmarkRunResult>();
        if (syntheticOnly || !flag(values, "--skip-synthetic")) {
            HardwareBenchmarkRunner.log(log, "phase=SYNTHETIC_PROBE");
            final HardwareBenchmarkRunner.Request syntheticRequest =
                    new HardwareBenchmarkRunner.Request(
                            outputRoot, java, classes, procRoot, inventory,
                            new HardwareBenchmarkRunner.DefaultProbeProcessFactory(), log, inputRoot);
            final List<HardwareBenchmarkPlanner.Candidate> syntheticPlan = syntheticOnly
                    ? planned
                    : planned.subList(0, Math.min(1, planned.size()));
            syntheticResults = runSweep(outputRoot, syntheticRequest, syntheticPlan, inventory, log,
                    false);
        }

        List<HardwareBenchmarkRunResult> realResults = new ArrayList<HardwareBenchmarkRunResult>();
        HardwareBenchmarkRunResult selected;
        if (!syntheticOnly) {
            HardwareBenchmarkRunner.log(log, "phase=REAL_LUMEN_CLASSIFICATION");
            final HardwareBenchmarkRunner.Request realRequest = new HardwareBenchmarkRunner.Request(
                    outputRoot, java, classes, procRoot, inventory,
                    new HardwareBenchmarkRunner.DefaultProbeProcessFactory(), log, inputRoot,
                    fiji, classifier, configTemplate, workload, pluginsDir);
            realResults = runSweep(outputRoot, realRequest, planned, inventory, log, true);
            selected = HardwareBenchmarkRanker.select(realResults);
        } else {
            selected = HardwareBenchmarkRanker.select(syntheticResults);
        }

        final List<HardwareBenchmarkRunResult> reportResults = new ArrayList<HardwareBenchmarkRunResult>();
        reportResults.addAll(syntheticResults);
        reportResults.addAll(realResults);
        if (reportResults.isEmpty()) {
            throw new IllegalStateException("no benchmark configurations were executed");
        }
        if (selected == null && !syntheticOnly) {
            HardwareBenchmarkRunner.log(log, "real classification selected none");
        }

        final String reason = HardwareBenchmarkRanker.describeSelection(selected, inventory);
        final String safety = HardwareBenchmarkRanker.safetyMargin(selected, inventory);
        HardwareBenchmarkReport.write(
                outputRoot, inventory, reportResults, selected, commit, reason, safety, workload);
        Files.write(outputRoot.resolve("benchmark.log"),
                log.toString().getBytes(StandardCharsets.UTF_8));

        final Path profile = outputRoot.resolve("selected_profile.properties");
        final String summary = HardwareBenchmarkReport.formatSummary(
                inventory, reportResults, selected, reason, safety, profile);
        System.out.print(summary);
        Files.write(outputRoot.resolve("SUMMARY.txt"), summary.getBytes(StandardCharsets.UTF_8));
        if (selected == null) {
            return EXIT_FAILURE;
        }
        if (!syntheticOnly && !HardwareExecutionProfile.WORKLOAD_REAL.equals(selected.workloadType)) {
            return EXIT_FAILURE;
        }
        return EXIT_SUCCESS;
    }

    static List<HardwareBenchmarkRunResult> runSweep(
            final Path outputRoot,
            final HardwareBenchmarkRunner.Request request,
            final List<HardwareBenchmarkPlanner.Candidate> planned,
            final HardwareInventory inventory,
            final StringBuilder log,
            final boolean real) throws Exception {
        final List<HardwareBenchmarkRunResult> results = new ArrayList<HardwareBenchmarkRunResult>();
        HardwareBenchmarkRunResult lastSuccessfulThreads = null;
        long timeoutMs = real
                ? HardwareBenchmarkRunner.timeoutMsReal(0L)
                : HardwareBenchmarkRunner.timeoutMs(0L);

        for (int i = 0; i < planned.size(); i++) {
            final HardwareBenchmarkPlanner.Candidate candidate = planned.get(i);
            HardwareBenchmarkRunner.log(log, "phase="
                    + (real ? HardwareExecutionProfile.WORKLOAD_REAL
                            : HardwareExecutionProfile.WORKLOAD_SYNTHETIC)
                    + " configuration " + (results.size() + 1)
                    + " id=" + candidate.id);
            final HardwareBenchmarkRunResult result = real
                    ? HardwareBenchmarkRunner.runOneClassification(request, candidate, timeoutMs)
                    : HardwareBenchmarkRunner.runOne(request, candidate, timeoutMs);
            results.add(result);
            writeLiveStatus(outputRoot, planned.size(), results, candidate, result, real);
            if (HardwareBenchmarkPlanner.PHASE_BASELINE.equals(candidate.phase)) {
                timeoutMs = real
                        ? HardwareBenchmarkRunner.timeoutMsReal(result.wallClockMs)
                        : HardwareBenchmarkRunner.timeoutMs(result.wallClockMs);
                if (!result.safeSuccess()) {
                    HardwareBenchmarkRunner.log(log, "baseline unsafe: " + result.unsafeReason);
                    break;
                }
                lastSuccessfulThreads = result;
                continue;
            }
            if (!result.safeSuccess()) {
                HardwareBenchmarkRunner.log(log, "stop thread sweep: " + result.unsafeReason);
                break;
            }
            if (lastSuccessfulThreads != null
                    && HardwareBenchmarkRanker.shouldStopThreadSweep(lastSuccessfulThreads, result)) {
                HardwareBenchmarkRunner.log(log, "stop thread sweep: no meaningful throughput gain");
                lastSuccessfulThreads = result;
                break;
            }
            lastSuccessfulThreads = result;
        }

        HardwareBenchmarkRunResult currentBest = results.isEmpty()
                ? null : HardwareBenchmarkRanker.select(results);
        if (currentBest != null) {
            final HardwareBenchmarkPlanner.Candidate refine =
                    HardwareBenchmarkPlanner.refinement(inventory,
                            new HardwareBenchmarkPlanner.Candidate(
                                    currentBest.configurationId,
                                    currentBest.phase,
                                    currentBest.threadsPerTile,
                                    currentBest.fijiHeapMb));
            if (refine != null && !alreadyRan(results, refine)) {
                HardwareBenchmarkRunner.log(log, "heap refinement " + refine.id);
                results.add(real
                        ? HardwareBenchmarkRunner.runOneClassification(request, refine, timeoutMs)
                        : HardwareBenchmarkRunner.runOne(request, refine, timeoutMs));
                currentBest = HardwareBenchmarkRanker.select(results);
            }
        }
        if (currentBest != null) {
            final HardwareBenchmarkPlanner.Candidate confirm =
                    HardwareBenchmarkPlanner.confirmation(
                            new HardwareBenchmarkPlanner.Candidate(
                                    currentBest.configurationId,
                                    currentBest.phase,
                                    currentBest.threadsPerTile,
                                    currentBest.fijiHeapMb));
            HardwareBenchmarkRunner.log(log, "confirm " + confirm.id);
            final HardwareBenchmarkRunResult confirmed = real
                    ? HardwareBenchmarkRunner.runOneClassification(request, confirm, timeoutMs)
                    : HardwareBenchmarkRunner.runOne(request, confirm, timeoutMs);
            results.add(confirmed);
            if (!confirmed.safeSuccess()) {
                HardwareBenchmarkRunner.log(log, "confirmation unsafe; keeping prior safe candidate");
            }
        }
        return results;
    }

    static Map<String, String> parseArgs(final String[] args) {
        final Map<String, String> values = new LinkedHashMap<String, String>();
        if (args == null) {
            return values;
        }
        int i = 0;
        while (i < args.length) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected positional: " + option);
            }
            if (isBooleanFlag(option)) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    values.put(option, args[i + 1]);
                    i += 2;
                } else {
                    values.put(option, "true");
                    i += 1;
                }
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            values.put(option, args[i + 1]);
            i += 2;
        }
        return values;
    }

    static boolean isBooleanFlag(final String option) {
        return "--synthetic-only".equals(option) || "--skip-synthetic".equals(option);
    }

    static boolean flag(final Map<String, String> values, final String key) {
        if (values == null || !values.containsKey(key)) {
            return false;
        }
        final String raw = values.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        final String value = raw.trim();
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    static String detectCommit() {
        try {
            final Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[256];
            int n;
            while ((n = process.getInputStream().read(buf)) >= 0) {
                if (n > 0) {
                    out.write(buf, 0, n);
                }
            }
            final int code = process.waitFor();
            if (code == 0) {
                return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        } catch (final Exception ignored) {
            // optional
        }
        return RCsvSupport.NA;
    }

    static Path resolveJavaExecutable(final Map<String, String> values) {
        if (values.containsKey("--java-executable")) {
            final Path requested = Paths.get(values.get("--java-executable")).toAbsolutePath().normalize();
            if (Files.isRegularFile(requested)) {
                return requested;
            }
        }
        return BatchMachineSupervisor.resolveJavaExecutable();
    }

    private static boolean alreadyRan(
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkPlanner.Candidate candidate) {
        for (int i = 0; i < results.size(); i++) {
            final HardwareBenchmarkRunResult result = results.get(i);
            if (result.threadsPerTile == candidate.threadsPerTile
                    && result.fijiHeapMb == candidate.fijiHeapMb) {
                return true;
            }
        }
        return false;
    }

    private static void writeLiveStatus(
            final Path outputRoot,
            final int plannedCount,
            final List<HardwareBenchmarkRunResult> results,
            final HardwareBenchmarkPlanner.Candidate candidate,
            final HardwareBenchmarkRunResult result,
            final boolean real) {
        try {
            final StringBuilder sb = new StringBuilder();
            sb.append("HARDWARE BENCHMARK\n");
            sb.append("BENCHMARK / TEST ONLY. NOT FOR SCIENTIFIC ANALYSIS.\n");
            sb.append("phase: ").append(real
                    ? HardwareExecutionProfile.WORKLOAD_REAL
                    : HardwareExecutionProfile.WORKLOAD_SYNTHETIC).append('\n');
            sb.append("configuration ").append(results.size()).append(" / ").append(plannedCount)
                    .append('\n');
            sb.append("current workload ").append(candidate.id).append('\n');
            sb.append("CPU ").append(RCsvSupport.formatNumber(result.meanCpuPercent)).append("%\n");
            sb.append("RAM peak_rss_mb ")
                    .append(result.peakRssBytes / (1024L * 1024L)).append('\n');
            sb.append("swap growth_bytes ").append(result.swapGrowthBytes()).append('\n');
            sb.append("host_swap_growth_bytes ").append(result.hostSwapGrowthBytes()).append('\n');
            sb.append("process_tree_swap_growth_bytes ")
                    .append(result.processTreeSwapGrowthBytes()).append('\n');
            sb.append("elapsed_ms ").append(result.wallClockMs).append('\n');
            if (real) {
                sb.append("throughput ").append(RCsvSupport.formatNumber(
                        result.rankingThroughput())).append(" pixels/s\n");
            } else {
                sb.append("throughput ").append(RCsvSupport.formatNumber(
                        result.throughputTilesPerSecond)).append(" tiles/s\n");
            }
            final String outcome;
            if (result.safeSuccess()) {
                outcome = "SUCCESS";
            } else if (result.unsafe) {
                outcome = "UNSAFE";
            } else {
                outcome = "FAILED";
            }
            sb.append("result: ").append(outcome).append('\n');
            Files.write(outputRoot.resolve("STATUS.txt"),
                    sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException ignored) {
            // best effort live status
        }
    }

    private static String valueOr(final Map<String, String> values, final String key,
            final String fallback) {
        final String value = values.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
