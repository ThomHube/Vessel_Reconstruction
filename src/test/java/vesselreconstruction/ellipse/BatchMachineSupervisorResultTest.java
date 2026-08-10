package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchMachineSupervisorResultTest {

    @Test
    void zeroAssignedProgressIsZeroNotNan() {
        assertEquals(0.0d, BatchMachineSupervisorResult.progressPercent(0, 0), 0.0d);
        final BatchMachineSupervisorResult result = BatchMachineSupervisorResult.builder()
                .machineId("machine_01")
                .timing(1L, 2L)
                .assignedCount(0)
                .initialCompletedCount(0)
                .finalCompletedCount(0)
                .skippedCompleteCount(0)
                .attemptedCount(0)
                .succeededThisRunCount(0)
                .blockedCount(0)
                .failedCount(0)
                .remainingCount(0)
                .initialProgressPercent(0.0d)
                .finalProgressPercent(0.0d)
                .status(BatchMachineSupervisorStatus.MACHINE_SUCCESS)
                .imageResults(Collections.<BatchMachineImageExecutionResult>emptyList())
                .build();
        assertEquals(0.0d, result.finalProgressPercent(), 0.0d);
    }

    @Test
    void progressPercentCorrect() {
        assertEquals(50.0d, BatchMachineSupervisorResult.progressPercent(1, 2), 0.0001d);
        assertEquals(100.0d, BatchMachineSupervisorResult.progressPercent(2, 2), 0.0001d);
    }

    @Test
    void rejectsNanProgress() {
        assertThrows(IllegalArgumentException.class, () -> BatchMachineSupervisorResult.builder()
                .machineId("machine_01")
                .timing(1L, 2L)
                .finalProgressPercent(Double.NaN)
                .status(BatchMachineSupervisorStatus.MACHINE_SUCCESS)
                .build());
    }
}
