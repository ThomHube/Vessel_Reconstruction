package vesselreconstruction.ellipse;

import ij.plugin.PlugIn;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unregistered headless Fiji entry point for one-image Phase-2 precheck.
 *
 * <p>Not listed in {@code plugins.config}. Invoked via:</p>
 *
 * <pre>
 * ImageJ-linux64 --headless --no-splash --allow-multiple --full-classpath \
 *   --main-class vesselreconstruction.ellipse.BatchPrecheckImagePlugin \
 *   /path/to/job.properties
 * </pre>
 *
 * <p>Does not construct an {@code ij.ImageJ} frame (that throws
 * {@code HeadlessException} under {@code --headless}).</p>
 */
public final class BatchPrecheckImagePlugin implements PlugIn {

    @Override
    public void run(final String arg) {
        final int code = processJobPath(arg);
        if (code != 0) {
            System.err.println("BatchPrecheckImagePlugin failed with code " + code);
        }
    }

    public static void main(final String[] args) {
        final String jobPath;
        if (args != null && args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            jobPath = args[0].trim();
        } else {
            System.err.println("ERROR: missing job.properties path argument");
            System.exit(2);
            return;
        }
        final int code = processJobPath(jobPath);
        System.exit(code);
    }

    static int processJobPath(final String jobPathText) {
        try {
            final Path jobPath = Paths.get(jobPathText).toAbsolutePath().normalize();
            final BatchPrecheckJob job = BatchPrecheckJob.load(jobPath);
            final BatchConfiguration config = BatchConfiguration.load(job.configurationPath());
            final BatchPrecheckResult result = new BatchPrecheckProcessor().process(job, config);
            result.write(job.resultPath());
            System.out.println("PRECHECK_STATUS=" + result.status().name());
            System.out.println("PRECHECK_REASON=" + result.get(BatchPrecheckResult.KEY_REASON));
            System.out.println("PRECHECK_RESULT=" + job.resultPath());
            return 0;
        } catch (final Exception ex) {
            System.err.println("ERROR: BatchPrecheckImagePlugin failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return 1;
        }
    }
}
