package vesselreconstruction.ellipse;

import ij.process.ByteProcessor;

/**
 * Headless single-Lumen diagnostic pipeline used by all-lumen batch processing.
 */
public interface SingleLumenDiagnosticPipeline {

    LumenBatchItemResult process(
            ByteProcessor classifiedProcessor,
            LumenComponentDescriptor component);
}
