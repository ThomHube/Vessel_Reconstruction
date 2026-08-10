package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineAssignmentTest {

    @Test
    void eightyFilesThreeMachinesGives272726() {
        final List<BatchTiffFile> files = fakeFiles(80);
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 3);
        assertEquals(3, slices.size());
        assertEquals(27, slices.get(0).assignedCount());
        assertEquals(27, slices.get(1).assignedCount());
        assertEquals(26, slices.get(2).assignedCount());
        assertEquals("machine_01", slices.get(0).machineId());
        assertEquals("machine_02", slices.get(1).machineId());
        assertEquals("machine_03", slices.get(2).machineId());
    }

    @Test
    void contiguousAssignmentsAndGlobalOrder() {
        final List<BatchTiffFile> files = fakeFiles(5);
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 2);
        assertEquals(3, slices.get(0).assignedCount());
        assertEquals(2, slices.get(1).assignedCount());
        assertEquals("010101", slices.get(0).files().get(0).id().imageId());
        assertEquals("010201", slices.get(0).files().get(2).id().imageId());
        assertEquals("010202", slices.get(1).files().get(0).id().imageId());
        assertEquals("010301", slices.get(1).files().get(1).id().imageId());
        // Contiguous: last of first + 1 equals first of second in global list order.
        assertEquals(files.get(3).id(), slices.get(1).files().get(0).id());
    }

    @Test
    void moreMachinesThanFilesAllowsEmpty() {
        final List<BatchTiffFile> files = fakeFiles(3);
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 5);
        assertEquals(5, slices.size());
        assertEquals(1, slices.get(0).assignedCount());
        assertEquals(1, slices.get(1).assignedCount());
        assertEquals(1, slices.get(2).assignedCount());
        assertEquals(0, slices.get(3).assignedCount());
        assertEquals(0, slices.get(4).assignedCount());
        assertEquals(RCsvSupport.NA, slices.get(3).firstImageIdOrNa());
        assertEquals(RCsvSupport.NA, slices.get(4).lastImageIdOrNa());
    }

    @Test
    void threeFilesOneAndTwoMachines() {
        final List<BatchTiffFile> files = fakeFiles(3);
        assertEquals(3, BatchMachineAssignment.assign(files, 1).get(0).assignedCount());
        final List<BatchMachineAssignment.MachineSlice> two =
                BatchMachineAssignment.assign(files, 2);
        assertEquals(2, two.get(0).assignedCount());
        assertEquals(1, two.get(1).assignedCount());
    }

    @Test
    void sizesDifferByAtMostOne() {
        for (int n = 0; n <= 20; n++) {
            for (int m = 1; m <= 7; m++) {
                final List<BatchMachineAssignment.MachineSlice> slices =
                        BatchMachineAssignment.assign(fakeFiles(n), m);
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int sum = 0;
                for (final BatchMachineAssignment.MachineSlice slice : slices) {
                    min = Math.min(min, slice.assignedCount());
                    max = Math.max(max, slice.assignedCount());
                    sum += slice.assignedCount();
                }
                assertEquals(n, sum);
                assertTrue(max - min <= 1, "n=" + n + " m=" + m);
            }
        }
    }

    private static List<BatchTiffFile> fakeFiles(final int count) {
        final List<SixDigitSampleId> expected = SixDigitSampleId.expectedCompleteSet();
        final List<BatchTiffFile> files = new ArrayList<BatchTiffFile>(count);
        for (int i = 0; i < count; i++) {
            final SixDigitSampleId id = expected.get(i);
            final String name = id.imageId() + ".tif";
            files.add(new BatchTiffFile(
                    id,
                    Paths.get("/abs", name),
                    Paths.get(name),
                    name,
                    ".tif",
                    10L + i,
                    String.format("%064d", Integer.valueOf(i))));
        }
        return files;
    }
}
