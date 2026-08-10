package vesselreconstruction.ellipse;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unregistered pure-Java CLI for Phase-1 batch planning.
 *
 * <pre>
 * java -cp target/classes vesselreconstruction.ellipse.BatchManifestPlannerCli \
 *   --input-root /path/to/tiffs \
 *   --batch-root /path/to/batch \
 *   --config runtime/batch/default_batch.properties
 * </pre>
 */
public final class BatchManifestPlannerCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_INVALID_ARGS = 2;
    public static final int EXIT_INVALID_CONFIG = 3;
    public static final int EXIT_VALIDATION = 4;
    public static final int EXIT_OUTPUT = 5;
    public static final int EXIT_UNEXPECTED = 6;

    private BatchManifestPlannerCli() {
    }

    public static void main(final String[] args) {
        final int code = run(args);
        if (code != EXIT_SUCCESS) {
            System.exit(code);
        }
    }

    static int run(final String[] args) {
        final BatchManifestPlanner.PlanRequest request;
        try {
            request = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_INVALID_ARGS;
        }
        try {
            final BatchManifestPlanner.PlanResult result = BatchManifestPlanner.plan(request);
            System.out.println("Batch plan SUCCESS");
            System.out.println("input root: " + request.inputRoot.toAbsolutePath().normalize());
            System.out.println("batch root: " + request.batchRoot.toAbsolutePath().normalize());
            System.out.println("valid TIFF count: " + result.validTiffCount);
            System.out.println("machine count: " + result.machineCount);
            System.out.println("first image ID: " + result.firstImageId);
            System.out.println("last image ID: " + result.lastImageId);
            System.out.println("configuration SHA-256: " + result.configurationSha256);
            System.out.println("master manifest path: " + result.masterManifest);
            return EXIT_SUCCESS;
        } catch (final BatchManifestPlanner.PlanningException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return ex.exitCode;
        } catch (final Exception ex) {
            System.err.println("ERROR: unexpected failure: " + ex.getMessage());
            return EXIT_UNEXPECTED;
        }
    }

    static BatchManifestPlanner.PlanRequest parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        int i = 0;
        while (i < args.length) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException(usage("Unexpected positional argument: " + option));
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(usage("Missing value for " + option));
            }
            final String value = args[i + 1];
            if (values.containsKey(option)) {
                throw new IllegalArgumentException(usage("Duplicate option: " + option));
            }
            if (!isSupported(option)) {
                throw new IllegalArgumentException(usage("Unknown option: " + option));
            }
            values.put(option, value);
            i += 2;
        }
        require(values, "--input-root");
        require(values, "--batch-root");
        require(values, "--config");
        if (values.size() != 3) {
            throw new IllegalArgumentException(usage("Unexpected options: " + values.keySet()));
        }
        return new BatchManifestPlanner.PlanRequest(
                Paths.get(values.get("--input-root")),
                Paths.get(values.get("--batch-root")),
                Paths.get(values.get("--config")));
    }

    private static boolean isSupported(final String option) {
        return "--input-root".equals(option)
                || "--batch-root".equals(option)
                || "--config".equals(option);
    }

    private static void require(final Map<String, String> values, final String key) {
        if (!values.containsKey(key) || values.get(key) == null || values.get(key).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + key));
        }
    }

    private static String usage(final String reason) {
        return reason + "\n\nUsage:\n"
                + "java -cp target/classes vesselreconstruction.ellipse.BatchManifestPlannerCli \\\n"
                + "  --input-root <path> \\\n"
                + "  --batch-root <path> \\\n"
                + "  --config <path>\n";
    }
}
