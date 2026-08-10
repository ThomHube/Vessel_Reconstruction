package vesselreconstruction.ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Balanced contiguous machine assignment for ordered TIFF lists.
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
     * Assigns ordered TIFFs to contiguous machine ranges.
     * Remainder is distributed to the first machines (sizes differ by at most one).
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
        final int n = orderedTiffs.size();
        final int base = n / machineCount;
        final int remainder = n % machineCount;
        final List<MachineSlice> slices = new ArrayList<MachineSlice>(machineCount);
        int cursor = 0;
        for (int m = 1; m <= machineCount; m++) {
            final int size = base + (m <= remainder ? 1 : 0);
            final List<BatchTiffFile> files = new ArrayList<BatchTiffFile>(size);
            final int startOrder = cursor + 1;
            for (int i = 0; i < size; i++) {
                files.add(orderedTiffs.get(cursor + i));
            }
            cursor += size;
            slices.add(new MachineSlice(machineId(m, machineCount), m, files, startOrder));
        }
        if (cursor != n) {
            throw new IllegalStateException("Assignment cursor mismatch");
        }
        return Collections.unmodifiableList(slices);
    }
}
