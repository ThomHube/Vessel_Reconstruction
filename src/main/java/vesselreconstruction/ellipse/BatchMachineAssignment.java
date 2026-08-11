package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Machine assignment that never splits segments of the same physical core.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Input TIFFs must already be sorted in {@link CoreSegmentId} canonical order.</li>
 *   <li>Group adjacent TIFFs that share the same {@code core_id} into indivisible
 *       core groups.</li>
 *   <li>Assign each ordered core group to the currently least-loaded machine by
 *       assigned image count; break ties by lower machine index.</li>
 *   <li>Each machine's file list is emitted in canonical order.</li>
 * </ol>
 *
 * <p>Core integrity takes priority over equal image counts. Assignments may therefore
 * be slightly uneven when groups contain 1–5 images.</p>
 */
public final class BatchMachineAssignment {

    public static final class MachineSlice {
        private final String machineId;
        private final int machineIndexOneBased;
        private final List<BatchTiffFile> files;
        private final int startOrderIndexOneBased;

        public MachineSlice(
                final String machineId,
                final int machineIndexOneBased,
                final List<BatchTiffFile> files,
                final int startOrderIndexOneBased) {
            this.machineId = machineId;
            this.machineIndexOneBased = machineIndexOneBased;
            this.files = Collections.unmodifiableList(new ArrayList<BatchTiffFile>(files));
            this.startOrderIndexOneBased = startOrderIndexOneBased;
        }

        public String machineId() {
            return machineId;
        }

        public int machineIndexOneBased() {
            return machineIndexOneBased;
        }

        public List<BatchTiffFile> files() {
            return files;
        }

        public int assignedCount() {
            return files.size();
        }

        /**
         * Historical contiguous-range start index. Under core-preserving assignment,
         * this is the 1-based index of the first file within this machine's own sorted
         * assignment (always 1 when the machine is non-empty, else 0).
         */
        public int startOrderIndexOneBased() {
            return startOrderIndexOneBased;
        }

        public String firstImageIdOrNa() {
            return files.isEmpty() ? RCsvSupport.NA : files.get(0).id().imageId();
        }

        public String lastImageIdOrNa() {
            return files.isEmpty()
                    ? RCsvSupport.NA
                    : files.get(files.size() - 1).id().imageId();
        }

        public long totalSourceBytes() {
            long sum = 0L;
            for (int i = 0; i < files.size(); i++) {
                sum += files.get(i).sizeBytes();
            }
            return sum;
        }
    }

    private BatchMachineAssignment() {
    }

    public static String machineId(final int machineIndexOneBased, final int machineCount) {
        if (machineIndexOneBased < 1 || machineIndexOneBased > machineCount) {
            throw new IllegalArgumentException("machine index out of range");
        }
        final int width = Math.max(2, Integer.toString(machineCount).length());
        return String.format(Locale.US, "machine_%0" + width + "d",
                Integer.valueOf(machineIndexOneBased));
    }

    /**
     * Assigns ordered TIFFs to machines without splitting any physical core group.
     */
    public static List<MachineSlice> assign(
            final List<BatchTiffFile> orderedTiffs,
            final int machineCount) {
        if (orderedTiffs == null) {
            throw new IllegalArgumentException("orderedTiffs must not be null");
        }
        if (machineCount < 1) {
            throw new IllegalArgumentException("machineCount must be >= 1");
        }
        for (int i = 1; i < orderedTiffs.size(); i++) {
            if (orderedTiffs.get(i - 1).id().compareTo(orderedTiffs.get(i).id()) > 0) {
                throw new IllegalArgumentException(
                        "orderedTiffs must be sorted in CoreSegmentId canonical order");
            }
        }

        final List<List<BatchTiffFile>> coreGroups = groupByCoreId(orderedTiffs);
        final List<List<BatchTiffFile>> machineBuckets =
                new ArrayList<List<BatchTiffFile>>(machineCount);
        final int[] loads = new int[machineCount];
        for (int m = 0; m < machineCount; m++) {
            machineBuckets.add(new ArrayList<BatchTiffFile>());
            loads[m] = 0;
        }

        for (int g = 0; g < coreGroups.size(); g++) {
            final List<BatchTiffFile> group = coreGroups.get(g);
            int best = 0;
            for (int m = 1; m < machineCount; m++) {
                if (loads[m] < loads[best]) {
                    best = m;
                }
            }
            machineBuckets.get(best).addAll(group);
            loads[best] += group.size();
        }

        final List<MachineSlice> slices = new ArrayList<MachineSlice>(machineCount);
        for (int m = 0; m < machineCount; m++) {
            final List<BatchTiffFile> files = machineBuckets.get(m);
            // Groups were assigned in global order; concatenating preserves canonical order.
            final int startOrder = files.isEmpty() ? 0 : 1;
            slices.add(new MachineSlice(
                    machineId(m + 1, machineCount),
                    m + 1,
                    files,
                    startOrder));
        }

        int total = 0;
        for (int m = 0; m < slices.size(); m++) {
            total += slices.get(m).assignedCount();
        }
        if (total != orderedTiffs.size()) {
            throw new IllegalStateException("Assignment lost or duplicated images");
        }
        return Collections.unmodifiableList(slices);
    }

    private static List<List<BatchTiffFile>> groupByCoreId(final List<BatchTiffFile> ordered) {
        final List<List<BatchTiffFile>> groups = new ArrayList<List<BatchTiffFile>>();
        List<BatchTiffFile> current = null;
        String currentCore = null;
        for (int i = 0; i < ordered.size(); i++) {
            final BatchTiffFile file = ordered.get(i);
            final String coreId = file.id().coreId();
            if (current == null || !coreId.equals(currentCore)) {
                current = new ArrayList<BatchTiffFile>();
                groups.add(current);
                currentCore = coreId;
            }
            current.add(file);
        }
        return groups;
    }
}
