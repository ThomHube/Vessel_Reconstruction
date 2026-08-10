package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineImageExecutionResultTest {

    @Test
    void skipCompleteDefaults() {
        final long start = 1000L;
        final long end = 1100L;
        final BatchMachineImageExecutionResult result = BatchMachineImageExecutionResult.builder()
                .sequenceNumber(1)
                .imageId("010101")
                .initialState(BatchMachineImageState.FULL_SUCCESS)
                .plannedAction(BatchMachinePlannedAction.SKIP_COMPLETE)
                .processingAttempted(false)
                .skippedComplete(true)
                .blocked(false)
                .processExitCode(-1)
                .finalStatus("FULL_SUCCESS")
                .reasonCode("SKIP_COMPLETE")
                .message("skipped")
                .timing(start, end)
                .build();
        assertFalse(result.processingAttempted());
        assertTrue(result.skippedComplete());
        assertFalse(result.blocked());
        assertEquals(-1, result.processExitCode());
        assertEquals(100L, result.elapsedMs());
    }

    @Test
    void rejectsInvalidTiming() {
        assertThrows(IllegalArgumentException.class, () -> BatchMachineImageExecutionResult.builder()
                .sequenceNumber(1)
                .imageId("010101")
                .initialState(BatchMachineImageState.READY_FOR_FULL_PROCESSING)
                .plannedAction(BatchMachinePlannedAction.RUN_FULL_PROCESSING)
                .timing(2000L, 1000L)
                .build());
    }
}
