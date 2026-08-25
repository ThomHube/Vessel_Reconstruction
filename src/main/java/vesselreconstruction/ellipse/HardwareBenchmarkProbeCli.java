package vesselreconstruction.ellipse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Child-JVM entry for one isolated probe configuration.
 */
public final class HardwareBenchmarkProbeCli {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_FAILURE = 3;
    public static final int EXIT_OOM = 26;

    private HardwareBenchmarkProbeCli() {
    }

    public static void main(final String[] args) {
        try {
            System.exit(run(args));
        } catch (final OutOfMemoryError oom) {
            System.err.println("OutOfMemoryError: "
                    + (oom.getMessage() == null ? "Java heap space" : oom.getMessage()));
            System.exit(EXIT_OOM);
        }
    }

    static int run(final String[] args) {
        final Map<String, String> values;
        try {
            values = parseArgs(args);
        } catch (final IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            return EXIT_USAGE;
        }
        final Path inputDir = Paths.get(values.get("--input-dir"));
        final Path output = Paths.get(values.get("--output"));
        final int threads = Integer.parseInt(values.get("--threads"));
        final int passes = Integer.parseInt(values.get("--passes"));
        try {
            final HardwareBenchmarkProbe.Result result =
                    HardwareBenchmarkProbe.run(inputDir, threads, passes);
            HardwareBenchmarkProbe.writeResultProperties(output, result);
            return result.success ? EXIT_SUCCESS : EXIT_FAILURE;
        } catch (final OutOfMemoryError oom) {
            System.err.println("OutOfMemoryError: Java heap space");
            return EXIT_OOM;
        } catch (final Exception ex) {
            System.err.println("ERROR: " + ex.getMessage());
            try {
                final String text = "status=FAILED\nmessage=" + String.valueOf(ex.getMessage()) + "\n";
                Files.write(output, text.getBytes(StandardCharsets.UTF_8));
            } catch (final Exception ignored) {
                // best effort
            }
            return EXIT_FAILURE;
        }
    }

    static Map<String, String> parseArgs(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage("Missing arguments"));
        }
        final Map<String, String> values = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(usage("Missing value for " + args[i]));
            }
            values.put(args[i], args[i + 1]);
        }
        if (!values.containsKey("--input-dir") || !values.containsKey("--output")
                || !values.containsKey("--threads") || !values.containsKey("--passes")) {
            throw new IllegalArgumentException(usage("Required: --input-dir --output --threads --passes"));
        }
        return values;
    }

    private static String usage(final String message) {
        return message + "\nUsage: HardwareBenchmarkProbeCli --input-dir <dir> --output <file> "
                + "--threads <n> --passes <n>";
    }
}
