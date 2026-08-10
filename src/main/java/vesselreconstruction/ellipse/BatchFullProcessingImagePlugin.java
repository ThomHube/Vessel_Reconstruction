package vesselreconstruction.ellipse;

import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unregistered headless Fiji entry point for one Phase-3 full-processing
 * stage (classification or reconstruction; see {@link BatchFullProcessingStage}).
 *
 * <p>Not listed in {@code plugins.config}. Invoked via:</p>
 *
 * <pre>
 * ImageJ-linux64 --headless --no-splash --allow-multiple --full-classpath \
 *   --main-class vesselreconstruction.ellipse.BatchFullProcessingImagePlugin \
 *   /path/to/job.properties
 * </pre>
 *
 * <p>Does not construct an {@code ij.ImageJ} frame (that throws
 * {@code HeadlessException} under {@code --headless}). Reads a stage-aware
 * job, executes exactly one {@link BatchFullProcessingService} stage, and
 * writes one {@link BatchFullProcessingStageResult} atomically.</p>
 */
public final class BatchFullProcessingImagePlugin implements PlugIn {

    interface ServiceFactory {
        BatchFullProcessingService create();
    }

    private static ServiceFactory serviceFactory = new ServiceFactory() {
        @Override
        public BatchFullProcessingService create() {
            return new BatchFullProcessingService();
        }
    };

    /** Package-visible test seam. */
    static void setServiceFactoryForTest(final ServiceFactory factory) {
        serviceFactory = factory == null
                ? new ServiceFactory() {
                    @Override
                    public BatchFullProcessingService create() {
                        return new BatchFullProcessingService();
                    }
                }
                : factory;
    }

    @Override
    public void run(final String arg) {
        final int code = processJobPath(arg);
        if (code != 0) {
            System.err.println("BatchFullProcessingImagePlugin failed with code " + code);
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
        return processJobPath(jobPathText, serviceFactory.create());
    }

    static int processJobPath(final String jobPathText, final BatchFullProcessingService service) {
        BatchFullProcessingJob job = null;
        try {
            final Path jobPath = Paths.get(jobPathText).toAbsolutePath().normalize();
            job = BatchFullProcessingJob.load(jobPath);
            if (job.processingStage() == null) {
                throw new IllegalArgumentException(
                        "processing_stage is required for the stage-aware child plugin");
            }
            final BatchConfiguration config = BatchConfiguration.load(job.configurationPath());
            final BatchFullProcessingStageResult result = service.process(job, config);
            result.writeAtomic(job.resultPath());
            System.out.println("FULL_STAGE=" + job.processingStage().name());
            System.out.println("FULL_STATUS=" + result.status().name());
            System.out.println("FULL_REASON=" + result.reasonCode());
            System.out.println("FULL_RESULT=" + job.resultPath());
            return 0;
        } catch (final OutOfMemoryError oom) {
            System.err.println("ERROR: BatchFullProcessingImagePlugin OutOfMemoryError: "
                    + (oom.getMessage() == null ? "Java heap space" : oom.getMessage()));
            printCauseChain(oom);
            writeMinimalOutOfMemoryResult(job, oom);
            return 1;
        } catch (final ThreadDeath threadDeath) {
            // Never swallow a deliberate thread termination.
            throw threadDeath;
        } catch (final Error fatal) {
            System.err.println("ERROR: BatchFullProcessingImagePlugin fatal error: "
                    + fatal.getMessage());
            printCauseChain(fatal);
            writeFailureResultBestEffort(job, fatal.getMessage(), "PLUGIN_FATAL_ERROR");
            return 1;
        } catch (final Exception ex) {
            System.err.println("ERROR: BatchFullProcessingImagePlugin failed: " + ex.getMessage());
            printCauseChain(ex);
            writeFailureResultBestEffort(job, ex.getMessage(), "PLUGIN_UNEXPECTED");
            return 1;
        } finally {
            closeAllOpenImagesBestEffort();
        }
    }

    private static void printCauseChain(final Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 16) {
            System.err.println("CAUSE[" + depth + "]: " + current.getClass().getName()
                    + ": " + current.getMessage());
            current = current.getCause();
            depth++;
        }
    }

    /**
     * Writes the smallest possible stage failure result under out-of-memory
     * conditions: no CSV reads, no dataset scans, only the identity fields
     * already held by the loaded job.
     */
    private static void writeMinimalOutOfMemoryResult(
            final BatchFullProcessingJob job, final Throwable cause) {
        if (job == null) {
            return;
        }
        try {
            final BatchFullProcessingStage stage = job.processingStage() == null
                    ? BatchFullProcessingStage.CLASSIFICATION
                    : job.processingStage();
            BatchFullProcessingStageResult.builder()
                    .stage(stage)
                    .status(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY)
                    .reasonCode(stage == BatchFullProcessingStage.RECONSTRUCTION
                            ? "RECONSTRUCTION_OUT_OF_MEMORY" : "CLASSIFICATION_OUT_OF_MEMORY")
                    .message(cause == null || cause.getMessage() == null
                            ? "out of memory" : cause.getMessage())
                    .imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .attemptNumber(Integer.valueOf(job.attemptNumber()))
                    .outOfMemoryDetected(Boolean.TRUE)
                    .stageCompleted(Boolean.FALSE)
                    .requiredOutputsValidated(Boolean.FALSE)
                    .build()
                    .writeAtomic(job.resultPath());
        } catch (final Throwable writeFailure) {
            try {
                System.err.println("ERROR: failed writing OOM result: " + writeFailure.getMessage());
            } catch (final Throwable ignored) {
                // truly out of options; give up silently
            }
        }
    }

    private static void writeFailureResultBestEffort(
            final BatchFullProcessingJob job, final String message, final String reasonCode) {
        if (job == null) {
            return;
        }
        try {
            final BatchFullProcessingStage stage = job.processingStage() == null
                    ? BatchFullProcessingStage.CLASSIFICATION
                    : job.processingStage();
            BatchFullProcessingStageResult.builder()
                    .stage(stage)
                    .status(BatchFullProcessingStatus.FAILED_UNEXPECTED)
                    .reasonCode(reasonCode)
                    .message(message)
                    .imageId(job.imageId().imageId())
                    .machineId(job.machineId())
                    .attemptNumber(Integer.valueOf(job.attemptNumber()))
                    .outOfMemoryDetected(Boolean.FALSE)
                    .stageCompleted(Boolean.FALSE)
                    .requiredOutputsValidated(Boolean.FALSE)
                    .build()
                    .writeAtomic(job.resultPath());
        } catch (final Exception writeEx) {
            System.err.println("ERROR: failed writing failure result: " + writeEx.getMessage());
        }
    }

    private static void closeAllOpenImagesBestEffort() {
        try {
            final int[] ids = WindowManager.getIDList();
            if (ids == null) {
                return;
            }
            for (int i = 0; i < ids.length; i++) {
                final ImagePlus imp = WindowManager.getImage(ids[i]);
                if (imp != null) {
                    imp.changes = false;
                    imp.close();
                }
            }
        } catch (final Throwable ignored) {
            // best effort
        }
    }
}
