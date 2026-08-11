package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BatchMachineRunPlanTest {

    @TempDir
    Path tempDir;

    @Test
    void zeroAssignedImagesNeverGivesNaN() {
        final BatchMachineRunPlan plan = BatchMachineRunPlan.of("machine_01",
                Collections.<BatchMachineImagePlan>emptyList());
        assertEquals(0, plan.assignedCount());
        assertEquals(0, plan.completedCount());
        assertEquals(0.0, plan.progressFraction(), 0.0);
        assertEquals(0.0, plan.progressPercent(), 0.0);
        assertFalse(Double.isNaN(plan.progressFraction()));
        assertFalse(Double.isNaN(plan.progressPercent()));
        assertEquals("0.0%", plan.formatProgressPercent());
    }

    @Test
    void progressUsesOnlyFullSuccess() {
        final BatchMachineRunPlan plan = BatchMachineRunPlan.of("machine_01", Arrays.asList(
                image(1, "0101A1", BatchMachineImageState.FULL_SUCCESS),
                image(2, "0101A2", BatchMachineImageState.CLASSIFICATION_COMPLETE),
                image(3, "0102A1", BatchMachineImageState.READY_FOR_FULL_PROCESSING),
                image(4, "0102A2", BatchMachineImageState.PRECHECK_MISSING),
                image(5, "0103A1", BatchMachineImageState.PRECHECK_REVIEW_REQUIRED),
                image(6, "0103A2", BatchMachineImageState.PRECHECK_FAILED),
                image(7, "0201A1", BatchMachineImageState.CANONICAL_CONFLICT),
                image(8, "0201A2", BatchMachineImageState.UNKNOWN_INVALID_STATE)));

        assertEquals(8, plan.assignedCount());
        assertEquals(1, plan.completedCount());
        assertEquals(1, plan.fullSuccessCount());
        assertEquals(1, plan.classificationCompleteCount());
        assertEquals(1, plan.readyForFullProcessingCount());
        assertEquals(1, plan.precheckMissingCount());
        assertEquals(1, plan.reviewRequiredCount());
        assertEquals(1, plan.precheckFailedCount());
        assertEquals(1, plan.canonicalConflictCount());
        assertEquals(1, plan.invalidStateCount());
        assertEquals(0.125, plan.progressFraction(), 1.0e-12);
        assertEquals(12.5, plan.progressPercent(), 1.0e-12);
        assertEquals("12.5%", plan.formatProgressPercent());
        assertEquals(4, plan.plannedProcessingRemaining());
    }

    private BatchMachineImagePlan image(
            final int seq, final String imageId, final BatchMachineImageState state) {
        return BatchMachineImagePlan.builder()
                .sequenceNumber(seq)
                .imageId(imageId)
                .machineId("machine_01")
                .sourceTiff(tempDir.resolve(imageId + ".tif"))
                .sourceSha256("abc")
                .state(state)
                .build();
    }
}
