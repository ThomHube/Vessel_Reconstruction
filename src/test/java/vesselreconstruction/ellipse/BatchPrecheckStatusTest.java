package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPrecheckStatusTest {

    @Test
    void allEnumValuesPresent() {
        assertEquals(13, BatchPrecheckStatus.values().length);
        assertEquals(BatchPrecheckStatus.PRECHECK_SUCCESS, BatchPrecheckStatus.valueOf("PRECHECK_SUCCESS"));
        assertEquals(BatchPrecheckStatus.REVIEW_REQUIRED_MASK, BatchPrecheckStatus.valueOf("REVIEW_REQUIRED_MASK"));
        assertEquals(BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR,
                BatchPrecheckStatus.valueOf("REVIEW_REQUIRED_SCALE_BAR"));
        assertEquals(BatchPrecheckStatus.INVALID_FILENAME, BatchPrecheckStatus.valueOf("INVALID_FILENAME"));
        assertEquals(BatchPrecheckStatus.INVALID_SOURCE, BatchPrecheckStatus.valueOf("INVALID_SOURCE"));
        assertEquals(BatchPrecheckStatus.STALE_SOURCE, BatchPrecheckStatus.valueOf("STALE_SOURCE"));
        assertEquals(BatchPrecheckStatus.INVALID_CONFIGURATION,
                BatchPrecheckStatus.valueOf("INVALID_CONFIGURATION"));
        assertEquals(BatchPrecheckStatus.FAILED_OPEN_IMAGE, BatchPrecheckStatus.valueOf("FAILED_OPEN_IMAGE"));
        assertEquals(BatchPrecheckStatus.FAILED_MASK_GENERATION,
                BatchPrecheckStatus.valueOf("FAILED_MASK_GENERATION"));
        assertEquals(BatchPrecheckStatus.FAILED_SCALE_BAR_DETECTION,
                BatchPrecheckStatus.valueOf("FAILED_SCALE_BAR_DETECTION"));
        assertEquals(BatchPrecheckStatus.FAILED_PREVIEW_SAVE, BatchPrecheckStatus.valueOf("FAILED_PREVIEW_SAVE"));
        assertEquals(BatchPrecheckStatus.FAILED_TIMEOUT, BatchPrecheckStatus.valueOf("FAILED_TIMEOUT"));
        assertEquals(BatchPrecheckStatus.FAILED_UNEXPECTED, BatchPrecheckStatus.valueOf("FAILED_UNEXPECTED"));
    }

    @Test
    void parseAcceptsExactNamesAndRejectsBlankOrUnknown() {
        assertEquals(BatchPrecheckStatus.PRECHECK_SUCCESS, BatchPrecheckStatus.parse("PRECHECK_SUCCESS"));
        assertEquals(BatchPrecheckStatus.FAILED_TIMEOUT, BatchPrecheckStatus.parse("  FAILED_TIMEOUT  "));
        assertThrows(IllegalArgumentException.class, () -> BatchPrecheckStatus.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BatchPrecheckStatus.parse(""));
        assertThrows(IllegalArgumentException.class, () -> BatchPrecheckStatus.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> BatchPrecheckStatus.parse("NOT_A_STATUS"));
    }

    @Test
    void successReviewAndFailureClassification() {
        assertTrue(BatchPrecheckStatus.PRECHECK_SUCCESS.isSuccess());
        assertFalse(BatchPrecheckStatus.PRECHECK_SUCCESS.isReviewRequired());
        assertFalse(BatchPrecheckStatus.PRECHECK_SUCCESS.isFailure());

        assertFalse(BatchPrecheckStatus.REVIEW_REQUIRED_MASK.isSuccess());
        assertTrue(BatchPrecheckStatus.REVIEW_REQUIRED_MASK.isReviewRequired());
        assertFalse(BatchPrecheckStatus.REVIEW_REQUIRED_MASK.isFailure());

        assertFalse(BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR.isSuccess());
        assertTrue(BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR.isReviewRequired());
        assertFalse(BatchPrecheckStatus.REVIEW_REQUIRED_SCALE_BAR.isFailure());

        for (final BatchPrecheckStatus status : BatchPrecheckStatus.values()) {
            if (status.isSuccess() || status.isReviewRequired()) {
                assertFalse(status.isFailure(), status.name());
            } else {
                assertTrue(status.isFailure(), status.name());
                assertFalse(status.isSuccess(), status.name());
                assertFalse(status.isReviewRequired(), status.name());
            }
        }
    }
}
