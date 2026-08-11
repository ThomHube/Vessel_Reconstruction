package vesselreconstruction.ellipse;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMachineAssignmentTest {

    @Test
    void sameCoreSegmentsNeverSplitAcrossMachines() {
        final List<BatchTiffFile> files = fakeFiles(
                "0101A1", "0101A2", "0101A3", "0101B1", "0101B2", "0102A1");
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 2);
        assertCoreIntact(slices, "0101A");
        assertCoreIntact(slices, "0101B");
        assertCoreIntact(slices, "0102A");
    }

    @Test
    void differentCoresCanGoToDifferentMachines() {
        final List<BatchTiffFile> files = fakeFiles(
                "0101A1", "0101A2", "0101B1", "0102A1", "0201A1");
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 3);
        assertEquals(3, slices.size());
        assertTrue(slices.get(0).assignedCount() > 0);
        assertTrue(slices.get(1).assignedCount() > 0);
        // With five cores and three machines, at least two machines get work and
        // cores remain intact while loads stay as balanced as core sizes allow.
        assertCoreIntact(slices, "0101A");
        assertCoreIntact(slices, "0101B");
        assertCoreIntact(slices, "0102A");
        assertCoreIntact(slices, "0201A");
    }

    @Test
    void everyImageAssignedOnceDeterministically() {
        final List<BatchTiffFile> files = fakeFiles(
                "0101A1", "0101A2", "0101B1", "0102A1", "0201A1");
        final List<BatchMachineAssignment.MachineSlice> first =
                BatchMachineAssignment.assign(files, 2);
        final List<BatchMachineAssignment.MachineSlice> second =
                BatchMachineAssignment.assign(files, 2);
        assertEquals(imageIds(first), imageIds(second));
        final Set<String> seen = new HashSet<String>();
        int total = 0;
        for (final BatchMachineAssignment.MachineSlice slice : first) {
            for (final BatchTiffFile file : slice.files()) {
                assertTrue(seen.add(file.id().imageId()), "duplicate " + file.id());
                total++;
            }
        }
        assertEquals(files.size(), total);
    }

    @Test
    void oneMachineReceivesAllImages() {
        final List<BatchTiffFile> files = fakeFiles(
                "0101A1", "0101A2", "0101B1", "0102A1");
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 1);
        assertEquals(1, slices.size());
        assertEquals(4, slices.get(0).assignedCount());
        assertEquals("0101A1", slices.get(0).files().get(0).id().imageId());
        assertEquals("0102A1", slices.get(0).files().get(3).id().imageId());
    }

    @Test
    void moreMachinesThanCoresAllowsEmpty() {
        final List<BatchTiffFile> files = fakeFiles("0101A1", "0101B1", "0102A1");
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
        assertEquals("machine_01", slices.get(0).machineId());
        assertEquals("machine_05", slices.get(4).machineId());
    }

    @Test
    void fiveSegmentCoreRemainsIntact() {
        final List<BatchTiffFile> files = fakeFiles(
                "0101A1", "0101A2", "0101A3", "0101A4", "0101A5",
                "0101B1", "0102A1");
        final List<BatchMachineAssignment.MachineSlice> slices =
                BatchMachineAssignment.assign(files, 3);
        assertCoreIntact(slices, "0101A");
        final List<String> coreA = segmentsOnSameMachine(slices, "0101A");
        assertEquals(Arrays.asList("0101A1", "0101A2", "0101A3", "0101A4", "0101A5"), coreA);
    }

    private static void assertCoreIntact(
            final List<BatchMachineAssignment.MachineSlice> slices,
            final String coreId) {
        int machinesWithCore = 0;
        for (final BatchMachineAssignment.MachineSlice slice : slices) {
            boolean present = false;
            for (final BatchTiffFile file : slice.files()) {
                if (coreId.equals(file.id().coreId())) {
                    present = true;
                    break;
                }
            }
            if (present) {
                machinesWithCore++;
            }
        }
        assertEquals(1, machinesWithCore, "core " + coreId + " split across machines");
    }

    private static List<String> segmentsOnSameMachine(
            final List<BatchMachineAssignment.MachineSlice> slices,
            final String coreId) {
        for (final BatchMachineAssignment.MachineSlice slice : slices) {
            final List<String> ids = new ArrayList<String>();
            for (final BatchTiffFile file : slice.files()) {
                if (coreId.equals(file.id().coreId())) {
                    ids.add(file.id().imageId());
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        throw new AssertionError("core not found: " + coreId);
    }

    private static List<String> imageIds(final List<BatchMachineAssignment.MachineSlice> slices) {
        final List<String> ids = new ArrayList<String>();
        for (final BatchMachineAssignment.MachineSlice slice : slices) {
            for (final BatchTiffFile file : slice.files()) {
                ids.add(file.id().imageId());
            }
        }
        return ids;
    }

    private static List<BatchTiffFile> fakeFiles(final String... imageIds) {
        final List<BatchTiffFile> files = new ArrayList<BatchTiffFile>(imageIds.length);
        for (int i = 0; i < imageIds.length; i++) {
            final CoreSegmentId id = CoreSegmentId.parse(imageIds[i]);
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
