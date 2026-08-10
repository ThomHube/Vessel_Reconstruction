package vesselreconstruction.ellipse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-Java CLI for Phase-2 batch precheck supervision.
 */
public final class BatchPrecheckSupervisorCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_REVIEW = 10;
    public static final int EXIT_FAILED_IMAGES = 11;
    public static final int EXIT_INVALID_ARGS = 12;
    public static final int EXIT_INVALID_CONFIG = 13;
    public static final int EXIT_INVALID_MANIFEST = 14;
    public static final int EXIT_FIJI_LAUNCH = 15;
    public static final int EXIT_UNEXPECTED = 16;

    private BatchPrecheckSupervisorCli() {
    }

    public static void main(final String[] args) {
        System.exit(run(args));
    }

    static int run(final String[] args) {
        final BatchPrecheckSupervisor.Request request;
        try {
            request = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_INVALID_ARGS;
        }
        try {
            final BatchPrecheckSupervisor.Summary summary = BatchPrecheckSupervisor.run(request);
            System.out.println("Precheck completed");
            System.out.println("machine: " + request.machineId);
            System.out.println("total: " + summary.total);
            System.out.println("PRECHECK_SUCCESS: " + summary.success);
            System.out.println("review required: " + summary.reviewRequired);
            System.out.println("failed: " + summary.failed);
            System.out.println("results CSV: " + summary.resultsCsv);
            return summary.exitCode;
        } catch (final BatchPrecheckSupervisor.SupervisorException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return ex.exitCode;
        } catch (final Exception ex) {
            System.err.println("ERROR: unexpected supervisor failure: " + ex.getMessage());
            return EXIT_UNEXPECTED;
        }
    }

    static BatchPrecheckSupervisor.Request parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        int i = 0;
        while (i < args.length) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException(usage("Unexpected positional: " + option));
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(usage("Missing value for " + option));
            }
            if (values.containsKey(option)) {
                throw new IllegalArgumentException(usage("Duplicate option: " + option));
            }
            if (!isSupported(option)) {
                throw new IllegalArgumentException(usage("Unknown option: " + option));
            }
            values.put(option, args[i + 1]);
            i += 2;
        }
        require(values, "--input-root");
        require(values, "--batch-root");
        require(values, "--machine-id");
        require(values, "--config");
        require(values, "--fiji-launcher");
        if (values.size() != 5) {
            throw new IllegalArgumentException(usage("Unexpected options: " + values.keySet()));
        }
        return new BatchPrecheckSupervisor.Request(
                Paths.get(values.get("--input-root")),
                Paths.get(values.get("--batch-root")),
                values.get("--machine-id"),
                Paths.get(values.get("--config")),
                Paths.get(values.get("--fiji-launcher")),
                null);
    }

    private static boolean isSupported(final String option) {
        return "--input-root".equals(option)
                || "--batch-root".equals(option)
                || "--machine-id".equals(option)
                || "--config".equals(option)
                || "--fiji-launcher".equals(option);
    }

    private static void require(final Map<String, String> values, final String key) {
        if (!values.containsKey(key) || values.get(key).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + key));
        }
    }

    private static String usage(final String reason) {
        return reason + "\n\nUsage:\n"
                + "java -cp target/classes vesselreconstruction.ellipse.BatchPrecheckSupervisorCli \\\n"
                + "  --input-root <path> \\\n"
                + "  --batch-root <path> \\\n"
                + "  --machine-id <machine_XX> \\\n"
                + "  --config <path> \\\n"
                + "  --fiji-launcher <path>\n";
    }
}
