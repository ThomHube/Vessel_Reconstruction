package vesselreconstruction.ellipse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CLI entry point for Phase-4B sequential multi-image machine supervision.
 *
 * <pre>
 * java -cp target/classes vesselreconstruction.ellipse.BatchMachineSupervisorCli \
 *   --input-root &lt;path&gt; \
 *   --batch-root &lt;path&gt; \
 *   --machine-id &lt;machine_XX&gt; \
 *   --config &lt;path&gt; \
 *   --classifier &lt;path&gt; \
 *   --fiji-launcher &lt;path&gt; \
 *   --tile-profile-index &lt;integer&gt;
 * </pre>
 */
public final class BatchMachineSupervisorCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_COMPLETED_WITH_BLOCKED = 10;
    public static final int EXIT_COMPLETED_WITH_FAILURES = 11;
    public static final int EXIT_FAILED_CONFIGURATION = 2;
    public static final int EXIT_FAILED_UNEXPECTED = 6;

    private BatchMachineSupervisorCli() {
    }

    public static void main(final String[] args) {
        System.exit(run(args));
    }

    static int run(final String[] args) {
        final ParsedArgs parsed;
        try {
            parsed = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_FAILED_CONFIGURATION;
        }

        final Path javaExecutable;
        try {
            javaExecutable = BatchMachineSupervisor.resolveJavaExecutable();
        } catch (final RuntimeException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_FAILED_CONFIGURATION;
        }

        final BatchMachineSupervisor.Request request;
        try {
            request = new BatchMachineSupervisor.Request(
                    parsed.inputRoot,
                    parsed.batchRoot,
                    parsed.machineId,
                    parsed.configPath,
                    parsed.classifierPath,
                    parsed.fijiLauncher,
                    parsed.tileProfileIndex,
                    parsed.classesDirectory,
                    javaExecutable,
                    new BatchMachineDefaultImageProcessLauncher(),
                    System.out);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_FAILED_CONFIGURATION;
        }

        final BatchMachineSupervisorResult result;
        try {
            result = BatchMachineSupervisor.run(request);
        } catch (final BatchMachineSupervisor.ConfigurationException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_FAILED_CONFIGURATION;
        } catch (final Exception ex) {
            System.err.println("ERROR: unexpected: " + ex.getMessage());
            return EXIT_FAILED_UNEXPECTED;
        }

        return exitCodeFor(result.status());
    }

    static int exitCodeFor(final BatchMachineSupervisorStatus status) {
        if (status == null) {
            return EXIT_FAILED_UNEXPECTED;
        }
        switch (status) {
            case MACHINE_SUCCESS:
                return EXIT_SUCCESS;
            case MACHINE_COMPLETED_WITH_BLOCKED_IMAGES:
                return EXIT_COMPLETED_WITH_BLOCKED;
            case MACHINE_COMPLETED_WITH_FAILURES:
                return EXIT_COMPLETED_WITH_FAILURES;
            case MACHINE_FAILED_CONFIGURATION:
                return EXIT_FAILED_CONFIGURATION;
            case MACHINE_FAILED_UNEXPECTED:
            default:
                return EXIT_FAILED_UNEXPECTED;
        }
    }

    static ParsedArgs parseArgs(final String[] args) {
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
        require(values, "--fiji-launcher");
        require(values, "--tile-profile-index");
        if (values.size() != 7) {
            throw new IllegalArgumentException(usage("Unexpected options"));
        }

        final int tileProfileIndex;
        try {
            tileProfileIndex = Integer.parseInt(values.get("--tile-profile-index").trim());
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException(usage(
                    "tile-profile-index must be an integer: "
                            + values.get("--tile-profile-index")));
        }
        if (tileProfileIndex < 0) {
            throw new IllegalArgumentException(usage(
                    "tile-profile-index must be non-negative: " + tileProfileIndex));
        }

        final Path classesDirectory = resolveClassesDirectory();
        if (!Files.isDirectory(classesDirectory)) {
            throw new IllegalArgumentException(
                    "classes directory missing: " + classesDirectory
                            + " (run mvn package first)");
        }

        return new ParsedArgs(
                Paths.get(values.get("--input-root")),
                Paths.get(values.get("--batch-root")),
                values.get("--machine-id").trim(),
                Paths.get(values.get("--config")),
                Paths.get(values.get("--classifier")),
                Paths.get(values.get("--fiji-launcher")),
                tileProfileIndex,
                classesDirectory);
    }

    static Path resolveClassesDirectory() {
        final String override = System.getProperty("vesselreconstruction.classes.dir");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        // Prefer CWD/target/classes (runtime scripts cd to project root via relative paths).
        final Path cwdClasses = Paths.get("target", "classes").toAbsolutePath().normalize();
        if (Files.isDirectory(cwdClasses)) {
            return cwdClasses;
        }
        return cwdClasses;
    }

    private static boolean isSupported(final String option) {
        return "--input-root".equals(option)
                || "--batch-root".equals(option)
                || "--machine-id".equals(option)
                || "--config".equals(option)
                || "--classifier".equals(option)
                || "--fiji-launcher".equals(option)
                || "--tile-profile-index".equals(option);
    }

    private static void require(final Map<String, String> values, final String key) {
        if (!values.containsKey(key) || values.get(key) == null || values.get(key).trim().isEmpty()) {
            throw new IllegalArgumentException(usage("Missing required option: " + key));
        }
    }

    private static String usage(final String prefix) {
        return prefix + "\n\nUsage:\n"
                + "java -cp target/classes vesselreconstruction.ellipse.BatchMachineSupervisorCli \\\n"
                + "  --input-root <path> \\\n"
                + "  --batch-root <path> \\\n"
                + "  --machine-id <machine_XX> \\\n"
                + "  --config <path> \\\n"
                + "  --classifier <path> \\\n"
                + "  --fiji-launcher <path> \\\n"
                + "  --tile-profile-index <integer>\n";
    }

    static final class ParsedArgs {
        final Path inputRoot;
        final Path batchRoot;
        final String machineId;
        final Path configPath;
        final Path classifierPath;
        final Path fijiLauncher;
        final int tileProfileIndex;
        final Path classesDirectory;

        ParsedArgs(
                final Path inputRoot,
                final Path batchRoot,
                final String machineId,
                final Path configPath,
                final Path classifierPath,
                final Path fijiLauncher,
                final int tileProfileIndex,
                final Path classesDirectory) {
            this.inputRoot = inputRoot;
            this.batchRoot = batchRoot;
            this.machineId = machineId;
            this.configPath = configPath;
            this.classifierPath = classifierPath;
            this.fijiLauncher = fijiLauncher;
            this.tileProfileIndex = tileProfileIndex;
            this.classesDirectory = classesDirectory;
        }
    }
}
