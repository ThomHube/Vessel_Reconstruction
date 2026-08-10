package vesselreconstruction.ellipse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unregistered maintenance CLI for repairing TILED_CLASSIFICATION rows in
 * {@code processing_runs.csv}.
 *
 * <p>Not a Fiji/SciJava plugin. Invoke with:</p>
 *
 * <pre>
 * java -cp target/classes vesselreconstruction.ellipse.ProcessingRunsManifestRepairCli \
 *   --dataset-root /path/to/lumen_dataset \
 *   --sample-id scalebar1-1 \
 *   --segment-id scalebar1-1 \
 *   --source-filename scalebar1-1.tif
 * </pre>
 *
 * <p>Does not rerun Weka, All Lumina, or any image processing.</p>
 */
public final class ProcessingRunsManifestRepairCli {

    private ProcessingRunsManifestRepairCli() {
    }

    public static void main(final String[] args) {
        try {
            final ProcessingRunsManifestRepair.Request request = parseArgs(args);
            final ProcessingRunsManifestRepair.Result result =
                    ProcessingRunsManifestRepair.repair(request);
            System.out.println(result.toReport());
        } catch (final Exception ex) {
            System.err.println("ERROR: " + ex.getMessage());
            System.exit(1);
        }
    }

    static ProcessingRunsManifestRepair.Request parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        int i = 0;
        while (i < args.length) {
            final String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException(usage("Unexpected argument: " + option));
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(usage("Missing value for " + option));
            }
            final String value = args[i + 1];
            if (values.containsKey(option)) {
                throw new IllegalArgumentException(usage("Duplicate option: " + option));
            }
            if (!isSupportedOption(option)) {
                throw new IllegalArgumentException(usage("Unknown option: " + option));
            }
            values.put(option, value);
            i += 2;
        }
        requirePresent(values, "--dataset-root");
        requirePresent(values, "--sample-id");
        requirePresent(values, "--segment-id");
        requirePresent(values, "--source-filename");
        if (values.size() != 4) {
            throw new IllegalArgumentException(usage("Unexpected options: " + values.keySet()));
        }
        final Path datasetRoot = Paths.get(values.get("--dataset-root"));
        return new ProcessingRunsManifestRepair.Request(
                datasetRoot,
                values.get("--sample-id"),
                values.get("--segment-id"),
                values.get("--source-filename"));
    }

    private static boolean isSupportedOption(final String option) {
        return "--dataset-root".equals(option)
                || "--sample-id".equals(option)
                || "--segment-id".equals(option)
                || "--source-filename".equals(option);
    }

    private static void requirePresent(final Map<String, String> values, final String option) {
        if (!values.containsKey(option) || values.get(option) == null
                || values.get(option).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + option));
        }
    }

    private static String usage(final String reason) {
        return reason + "\n\nUsage:\n"
                + "java -cp target/classes "
                + "vesselreconstruction.ellipse.ProcessingRunsManifestRepairCli \\\n"
                + "  --dataset-root <path> \\\n"
                + "  --sample-id <value> \\\n"
                + "  --segment-id <value> \\\n"
                + "  --source-filename <filename>\n";
    }
}
