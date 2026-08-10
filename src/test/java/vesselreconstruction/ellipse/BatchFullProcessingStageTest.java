package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingStageTest {

    @Test
    void parseClassification() {
        assertEquals(BatchFullProcessingStage.CLASSIFICATION,
                BatchFullProcessingStage.parse("CLASSIFICATION"));
        assertEquals(BatchFullProcessingStage.CLASSIFICATION,
                BatchFullProcessingStage.parse("  CLASSIFICATION  "));
    }

    @Test
    void parseReconstruction() {
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION,
                BatchFullProcessingStage.parse("RECONSTRUCTION"));
        assertEquals(BatchFullProcessingStage.RECONSTRUCTION,
                BatchFullProcessingStage.parse("\tRECONSTRUCTION\n"));
    }

    @Test
    void rejectNull() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingStage.parse(null));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void rejectBlank() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> BatchFullProcessingStage.parse("   "));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void rejectLowercase() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("classification"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("reconstruction"));
    }

    @Test
    void rejectUnknownAndAliases() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("CLASSIFY"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("RECON"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("FULL"));
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BatchFullProcessingStage.parse("UNKNOWN_STAGE"));
        assertTrue(ex.getMessage().contains("Unknown processing_stage"));
    }
}
