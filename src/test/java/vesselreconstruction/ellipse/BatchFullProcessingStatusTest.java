package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingStatusTest {

    @Test
    void allEnumValuesPresent() {
        assertEquals(19, BatchFullProcessingStatus.values().length);
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS,
                BatchFullProcessingStatus.valueOf("FULL_SUCCESS"));
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_FOUND,
                BatchFullProcessingStatus.valueOf("PRECHECK_NOT_FOUND"));
        assertEquals(BatchFullProcessingStatus.PRECHECK_NOT_SUCCESSFUL,
                BatchFullProcessingStatus.valueOf("PRECHECK_NOT_SUCCESSFUL"));
        assertEquals(BatchFullProcessingStatus.PRECHECK_STALE_SOURCE,
                BatchFullProcessingStatus.valueOf("PRECHECK_STALE_SOURCE"));
        assertEquals(BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION,
                BatchFullProcessingStatus.valueOf("PRECHECK_STALE_CONFIGURATION"));
        assertEquals(BatchFullProcessingStatus.INVALID_JOB,
                BatchFullProcessingStatus.valueOf("INVALID_JOB"));
        assertEquals(BatchFullProcessingStatus.INVALID_SOURCE,
                BatchFullProcessingStatus.valueOf("INVALID_SOURCE"));
        assertEquals(BatchFullProcessingStatus.INVALID_CLASSIFIER,
                BatchFullProcessingStatus.valueOf("INVALID_CLASSIFIER"));
        assertEquals(BatchFullProcessingStatus.FAILED_MASK_REPRODUCTION,
                BatchFullProcessingStatus.valueOf("FAILED_MASK_REPRODUCTION"));
        assertEquals(BatchFullProcessingStatus.FAILED_SCALE_BAR_REPRODUCTION,
                BatchFullProcessingStatus.valueOf("FAILED_SCALE_BAR_REPRODUCTION"));
        assertEquals(BatchFullProcessingStatus.FAILED_CLASSIFICATION,
                BatchFullProcessingStatus.valueOf("FAILED_CLASSIFICATION"));
        assertEquals(BatchFullProcessingStatus.FAILED_CLASSIFICATION_SAVE,
                BatchFullProcessingStatus.valueOf("FAILED_CLASSIFICATION_SAVE"));
        assertEquals(BatchFullProcessingStatus.FAILED_RECONSTRUCTION,
                BatchFullProcessingStatus.valueOf("FAILED_RECONSTRUCTION"));
        assertEquals(BatchFullProcessingStatus.FAILED_RECONSTRUCTION_SAVE,
                BatchFullProcessingStatus.valueOf("FAILED_RECONSTRUCTION_SAVE"));
        assertEquals(BatchFullProcessingStatus.FAILED_OUTPUT_VALIDATION,
                BatchFullProcessingStatus.valueOf("FAILED_OUTPUT_VALIDATION"));
        assertEquals(BatchFullProcessingStatus.CANONICAL_CONFLICT,
                BatchFullProcessingStatus.valueOf("CANONICAL_CONFLICT"));
        assertEquals(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY,
                BatchFullProcessingStatus.valueOf("FAILED_OUT_OF_MEMORY"));
        assertEquals(BatchFullProcessingStatus.FAILED_TIMEOUT,
                BatchFullProcessingStatus.valueOf("FAILED_TIMEOUT"));
        assertEquals(BatchFullProcessingStatus.FAILED_UNEXPECTED,
                BatchFullProcessingStatus.valueOf("FAILED_UNEXPECTED"));
    }

    @Test
    void parseAcceptsExactNamesAndRejectsBlankOrUnknown() {
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS,
                BatchFullProcessingStatus.parse("FULL_SUCCESS"));
        assertEquals(BatchFullProcessingStatus.FAILED_TIMEOUT,
                BatchFullProcessingStatus.parse("  FAILED_TIMEOUT  "));
        assertThrows(IllegalArgumentException.class, () -> BatchFullProcessingStatus.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BatchFullProcessingStatus.parse(""));
        assertThrows(IllegalArgumentException.class, () -> BatchFullProcessingStatus.parse("   "));
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStatus.parse("NOT_A_STATUS"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStatus.parse("FULL_PROCESSING_SUCCESS"));
    }

    @Test
    void successAndFailureClassification() {
        assertTrue(BatchFullProcessingStatus.FULL_SUCCESS.isSuccess());
        assertFalse(BatchFullProcessingStatus.FULL_SUCCESS.isFailure());

        for (final BatchFullProcessingStatus status : BatchFullProcessingStatus.values()) {
            if (status == BatchFullProcessingStatus.FULL_SUCCESS) {
                assertFalse(status.isFailure(), status.name());
            } else {
                assertTrue(status.isFailure(), status.name());
                assertFalse(status.isSuccess(), status.name());
            }
        }
    }

    @Test
    void precheckProblemClassification() {
        assertTrue(BatchFullProcessingStatus.PRECHECK_NOT_FOUND.isPrecheckProblem());
        assertTrue(BatchFullProcessingStatus.PRECHECK_NOT_SUCCESSFUL.isPrecheckProblem());
        assertTrue(BatchFullProcessingStatus.PRECHECK_STALE_SOURCE.isPrecheckProblem());
        assertTrue(BatchFullProcessingStatus.PRECHECK_STALE_CONFIGURATION.isPrecheckProblem());
        assertFalse(BatchFullProcessingStatus.FULL_SUCCESS.isPrecheckProblem());
        assertFalse(BatchFullProcessingStatus.FAILED_CLASSIFICATION.isPrecheckProblem());
    }
}
