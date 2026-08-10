package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineSupervisorStatusTest {

    @Test
    void parsesAllValues() {
        for (final BatchMachineSupervisorStatus status : BatchMachineSupervisorStatus.values()) {
            assertEquals(status, BatchMachineSupervisorStatus.parse(status.name()));
        }
    }

    @Test
    void rejectsUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchMachineSupervisorStatus.parse("NOT_A_STATUS"));
    }

    @Test
    void successFlag() {
        assertTrue(BatchMachineSupervisorStatus.MACHINE_SUCCESS.isSuccess());
        assertFalse(BatchMachineSupervisorStatus.MACHINE_COMPLETED_WITH_FAILURES.isSuccess());
    }
}
