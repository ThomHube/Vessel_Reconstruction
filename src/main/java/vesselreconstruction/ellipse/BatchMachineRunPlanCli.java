package vesselreconstruction.ellipse;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java dry-run CLI that prints a Phase-4 machine run plan without
 * launching Fiji or writing production artifacts.
 *
 * <pre>
 * java -cp target/classes vesselreconstruction.ellipse.BatchMachineRunPlanCli \
 *   --input-root &lt;path&gt; \
 *   --batch-root &lt;path&gt; \
 *   --machine-id &lt;machine_XX&gt; \
 *   --config &lt;path&gt; \
 *   --classifier &lt;path&gt;
 * </pre>
 */
public final class BatchMachineRunPlanCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_INVALID_ARGS = 2;
    public static final int EXIT_INVALID_CONFIG = 3;
    public static final int EXIT_VALIDATION = 4;
    public static final int EXIT_UNEXPECTED = 6;

    private BatchMachineRunPlanCli() {
    }

    public static void main(final String[] args) {
        System.exit(run(args));
    }

    static int run(final String[] args) {
        final BatchMachineRunPlanner.Request request;
        try {
            request = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_INVALID_ARGS;
        }
        try {
            final BatchMachineRunPlan plan = BatchMachineRunPlanner.plan(request);
            printPlan(plan);
            return EXIT_SUCCESS;
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_VALIDATION;
        } catch (final Exception ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_UNEXPECTED;
        }
    }

    static BatchMachineRunPlanner.Request parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException(usage("Unexpected argument: " + option));
            }
            if (!isSupported(option)) {
                throw new IllegalArgumentException(usage("Unknown option: " + option));
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException(usage("Missing value for " + option));
            }
            if (values.containsKey(option)) {
                throw new IllegalArgumentException(usage("Duplicate option: " + option));
            }
            values.put(option, args[++i]);
        }
        require(values, "--input-root");
        require(values, "--batch-root");
        require(values, "--machine-id");
        require(values, "--config");
        require(values, "--classifier");
        if (values.size() != 5) {
            throw new IllegalArgumentException(usage("Unexpected options"));
        }
        return new BatchMachineRunPlanner.Request(
                Paths.get(values.get("--input-root")),
                Paths.get(values.get("--batch-root")),
                values.get("--machine-id"),
                Paths.get(values.get("--config")),
                Paths.get(values.get("--classifier")),
                0);
    }

    private static boolean isSupported(final String option) {
        return "--input-root".equals(option)
                || "--batch-root".equals(option)
                || "--machine-id".equals(option)
                || "--config".equals(option)
                || "--classifier".equals(option);
    }

    private static void require(final Map<String, String> values, final String key) {
        if (!values.containsKey(key) || values.get(key) == null || values.get(key).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + key));
        }
    }

    private static String usage(final String prefix) {
        return prefix + "\n\nUsage:\n"
                + "java -cp target/classes vesselreconstruction.ellipse.BatchMachineRunPlanCli \\\n"
                + "  --input-root <path> \\\n"
                + "  --batch-root <path> \\\n"
                + "  --machine-id <machine_XX> \\\n"
                + "  --config <path> \\\n"
                + "  --classifier <path>\n";
    }

    static void printPlan(final BatchMachineRunPlan plan) {
        System.out.println("MACHINE RUN PLAN");
        System.out.println("machine: " + plan.machineId());
        System.out.println("assigned: " + plan.assignedCount());
        System.out.println("completed: " + plan.completedCount());
        System.out.println("progress: " + plan.formatProgressPercent());
        System.out.println();
        System.out.println(String.format(Locale.ROOT,
                "%-4s %-8s %-26s %-24s",
                "SEQ", "IMAGE_ID", "STATE", "ACTION"));
        final List<BatchMachineImagePlan> images = plan.images();
        for (int i = 0; i < images.size(); i++) {
            final BatchMachineImagePlan image = images.get(i);
            System.out.println(String.format(Locale.ROOT,
                    "%03d  %-8s %-26s %-24s",
                    Integer.valueOf(image.sequenceNumber()),
                    image.imageId(),
                    image.state().name(),
                    image.plannedAction().name()));
        }
        System.out.println();
        System.out.println("SUMMARY");
        System.out.println("FULL_SUCCESS: " + plan.fullSuccessCount());
        System.out.println("CLASSIFICATION_COMPLETE: " + plan.classificationCompleteCount());
        System.out.println("READY_FOR_FULL_PROCESSING: " + plan.readyForFullProcessingCount());
        System.out.println("PRECHECK_MISSING: " + plan.precheckMissingCount());
        System.out.println("PRECHECK_FAILED: " + plan.precheckFailedCount());
        System.out.println("REVIEW_REQUIRED: " + plan.reviewRequiredCount());
        System.out.println("CANONICAL_CONFLICT: " + plan.canonicalConflictCount());
        System.out.println("INVALID: " + plan.invalidStateCount());
        System.out.println();
        System.out.println("planned processing remaining: " + plan.plannedProcessingRemaining());
    }
}
