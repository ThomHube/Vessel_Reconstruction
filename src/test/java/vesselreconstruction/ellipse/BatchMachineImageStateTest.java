package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchMachineImageStateTest {

    @Test
    void parseRoundTripsAllValues() {
        for (final BatchMachineImageState state : BatchMachineImageState.values()) {
            assertEquals(state, BatchMachineImageState.parse(state.name()));
        }
    }

    @Test
    void plannedActionMappingIsStable() {
        assertEquals(BatchMachinePlannedAction.RUN_PRECHECK,
                BatchMachinePlannedAction.forState(BatchMachineImageState.PRECHECK_MISSING));
        assertEquals(BatchMachinePlannedAction.RUN_PRECHECK,
                BatchMachinePlannedAction.forState(BatchMachineImageState.PRECHECK_FAILED));
        assertEquals(BatchMachinePlannedAction.WAIT_FOR_REVIEW,
                BatchMachinePlannedAction.forState(BatchMachineImageState.PRECHECK_REVIEW_REQUIRED));
        assertEquals(BatchMachinePlannedAction.RUN_FULL_PROCESSING,
                BatchMachinePlannedAction.forState(BatchMachineImageState.READY_FOR_FULL_PROCESSING));
        assertEquals(BatchMachinePlannedAction.RESUME_RECONSTRUCTION,
                BatchMachinePlannedAction.forState(BatchMachineImageState.CLASSIFICATION_COMPLETE));
        assertEquals(BatchMachinePlannedAction.SKIP_COMPLETE,
                BatchMachinePlannedAction.forState(BatchMachineImageState.FULL_SUCCESS));
        assertEquals(BatchMachinePlannedAction.STOP_FOR_CANONICAL_CONFLICT,
                BatchMachinePlannedAction.forState(BatchMachineImageState.CANONICAL_CONFLICT));
        assertEquals(BatchMachinePlannedAction.STOP_FOR_INVALID_STATE,
                BatchMachinePlannedAction.forState(BatchMachineImageState.UNKNOWN_INVALID_STATE));
    }

    @Test
    void blankStateRejected() {
        assertThrows(IllegalArgumentException.class, () -> BatchMachineImageState.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> BatchMachinePlannedAction.parse(""));
    }
}
