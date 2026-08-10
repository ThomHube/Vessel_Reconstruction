package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFullProcessingStageResultTest {

    @TempDir
    Path tempDir;

    @Test
    void classificationSuccessRoundTrip() throws Exception {
        final Path primary = tempDir.resolve("masked_classified.tif").toAbsolutePath().normalize();
        final BatchFullProcessingStageResult original = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.CLASSIFICATION)
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .reasonCode(RCsvSupport.NA)
                .message("classification committed")
                .imageId("010101")
                .machineId("machine_01")
                .attemptNumber(Integer.valueOf(1))
                .processExitCode(Integer.valueOf(0))
                .outOfMemoryDetected(Boolean.FALSE)
                .stageCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .runId("class-run-1")
                .primaryOutputPath(primary)
                .sourceUnchanged(Boolean.TRUE)
                .processingTimeMs(Long.valueOf(1200L))
                .build();

        final Path path = tempDir.resolve("classification_result.properties");
        original.writeAtomic(path);
        final BatchFullProcessingStageResult loaded = BatchFullProcessingStageResult.read(path);
        assertEquals(original, loaded);
        assertEquals(RCsvSupport.NA, loaded.totalComponents());
    }

    @Test
    void reconstructionSuccessRoundTrip() throws Exception {
        final Path primary = tempDir.resolve("reconstructed.tif").toAbsolutePath().normalize();
        final BatchFullProcessingStageResult original = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.RECONSTRUCTION)
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .message("reconstruction committed")
                .imageId("010101")
                .machineId("machine_01")
                .attemptNumber(Integer.valueOf(2))
                .outOfMemoryDetected(Boolean.FALSE)
                .stageCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .runId("recon-run-1")
                .primaryOutputPath(primary)
                .totalComponents(Integer.valueOf(40))
                .eligibleComponents(Integer.valueOf(35))
                .reconstructedComponents(Integer.valueOf(12))
                .changedPixels(Integer.valueOf(9001))
                .sourceUnchanged(Boolean.TRUE)
                .processingTimeMs(Long.valueOf(5400L))
                .build();

        final Path path = tempDir.resolve("reconstruction_result.properties");
        original.writeAtomic(path);
        assertEquals(original, BatchFullProcessingStageResult.read(path));
    }

    @Test
    void zeroReconstructedComponentsCanSucceed() throws Exception {
        final BatchFullProcessingStageResult result = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.RECONSTRUCTION)
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .stageCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .reconstructedComponents(Integer.valueOf(0))
                .totalComponents(Integer.valueOf(10))
                .eligibleComponents(Integer.valueOf(0))
                .changedPixels(Integer.valueOf(0))
                .build();
        assertEquals(BatchFullProcessingStatus.FULL_SUCCESS, result.status());
        assertEquals("0", result.reconstructedComponents());
    }

    @Test
    void classificationAndReconstructionOomRepresentation() throws Exception {
        for (final BatchFullProcessingStage stage : BatchFullProcessingStage.values()) {
            final BatchFullProcessingStageResult result = BatchFullProcessingStageResult.builder()
                    .stage(stage)
                    .status(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY)
                    .outOfMemoryDetected(Boolean.TRUE)
                    .stageCompleted(Boolean.FALSE)
                    .requiredOutputsValidated(Boolean.FALSE)
                    .build();
            final Path path = tempDir.resolve(stage.name() + "_oom.properties");
            result.writeAtomic(path);
            final String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            assertTrue(text.contains("stage=" + stage.name()));
            assertTrue(text.contains("status=FAILED_OUT_OF_MEMORY"));
            assertTrue(text.contains("out_of_memory_detected=TRUE"));
        }
    }

    @Test
    void trueFalseAndNaSerialization() throws Exception {
        final BatchFullProcessingStageResult result = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.CLASSIFICATION)
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .outOfMemoryDetected(Boolean.FALSE)
                .stageCompleted(Boolean.TRUE)
                .requiredOutputsValidated(Boolean.TRUE)
                .sourceUnchanged(Boolean.FALSE)
                .build();
        final Path path = tempDir.resolve("bools.properties");
        result.writeAtomic(path);
        final String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertTrue(text.contains("out_of_memory_detected=FALSE"));
        assertTrue(text.contains("stage_completed=TRUE"));
        assertTrue(text.contains("primary_output_path=NA"));
        assertFalse(text.contains("=true"));
        assertFalse(text.contains("=false"));
        assertFalse(text.contains("=NaN"));
    }

    @Test
    void unicodeAndSpecialCharacterMessageRoundTrip() throws Exception {
        final String message = "path=C:\\tmp\\x : μ-lumen\nline2; quote\" and equals=ok";
        final BatchFullProcessingStageResult original = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.CLASSIFICATION)
                .status(BatchFullProcessingStatus.FAILED_UNEXPECTED)
                .message(message)
                .imageId("010101")
                .machineId("machine_01")
                .attemptNumber(Integer.valueOf(1))
                .build();
        final Path path = tempDir.resolve("special.properties");
        original.writeAtomic(path);
        assertEquals(message, BatchFullProcessingStageResult.read(path).message());
    }

    @Test
    void atomicWriteFailurePreservesEarlierResult() throws Exception {
        final Path dir = tempDir.resolve("atomic").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        final Path path = dir.resolve("stage_result.properties");

        final BatchFullProcessingStageResult first = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.CLASSIFICATION)
                .status(BatchFullProcessingStatus.FULL_SUCCESS)
                .message("first-valid-result")
                .imageId("010101")
                .machineId("machine_01")
                .attemptNumber(Integer.valueOf(1))
                .outOfMemoryDetected(Boolean.FALSE)
                .stageCompleted(Boolean.TRUE)
                .processingTimeMs(Long.valueOf(1L))
                .build();
        first.writeAtomic(path);
        final byte[] before = Files.readAllBytes(path);

        final BatchFullProcessingStageResult second = BatchFullProcessingStageResult.builder()
                .stage(BatchFullProcessingStage.RECONSTRUCTION)
                .status(BatchFullProcessingStatus.FAILED_OUT_OF_MEMORY)
                .message("should-not-replace")
                .imageId("010101")
                .machineId("machine_01")
                .attemptNumber(Integer.valueOf(1))
                .outOfMemoryDetected(Boolean.TRUE)
                .stageCompleted(Boolean.FALSE)
                .processingTimeMs(Long.valueOf(2L))
                .build();

        assertTrue(dir.toFile().setWritable(false));
        try {
            assertThrows(IOException.class, () -> second.writeAtomic(path));
            assertEquals(new String(before, StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } finally {
            assertTrue(dir.toFile().setWritable(true));
        }
    }
}
