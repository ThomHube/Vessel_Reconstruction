package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Tracks descendant PIDs of one owned benchmark process. Never uses
 * {@code pkill} or {@code killall}.
 */
public final class HardwareBenchmarkProcessTree {

    private HardwareBenchmarkProcessTree() {
    }

    public static List<Integer> descendants(final Path procRoot, final int rootPid) {
        final LinkedHashSet<Integer> seen = new LinkedHashSet<Integer>();
        collect(procRoot, rootPid, seen);
        return new ArrayList<Integer>(seen);
    }

    /**
     * Sums {@code VmSwap} for the owned root and its descendants only.
     */
    public static long vmSwapBytes(final Path procRoot, final int rootPid) {
        if (rootPid <= 0) {
            return 0L;
        }
        long sum = 0L;
        final List<Integer> pids = descendants(procRoot, rootPid);
        for (int i = 0; i < pids.size(); i++) {
            sum += HardwareBenchmarkMonitor.readVmSwap(procRoot, pids.get(i).intValue());
        }
        return sum;
    }

    public static void terminateOwnedTree(
            final Process process,
            final Path procRoot,
            final int rootPid) {
        final List<Integer> pids = descendants(procRoot, rootPid);
        HardwareBenchmarkRunner.destroyOwnedProcess(process);
        for (int i = 0; i < pids.size(); i++) {
            final int pid = pids.get(i).intValue();
            if (pid > 0 && pid != rootPid && isAlive(procRoot, pid)) {
                signal(pid, "-TERM");
            }
        }
        try {
            Thread.sleep(400L);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        for (int i = 0; i < pids.size(); i++) {
            final int pid = pids.get(i).intValue();
            if (pid > 0 && isAlive(procRoot, pid)) {
                signal(pid, "-KILL");
            }
        }
    }

    static void collect(final Path procRoot, final int pid, final LinkedHashSet<Integer> seen) {
        if (pid <= 0 || !seen.add(Integer.valueOf(pid))) {
            return;
        }
        final Path taskDir = procRoot.resolve(Integer.toString(pid)).resolve("task");
        if (!Files.isDirectory(taskDir)) {
            return;
        }
        try (DirectoryStream<Path> tasks = Files.newDirectoryStream(taskDir)) {
            for (final Path task : tasks) {
                final Path children = task.resolve("children");
                if (!Files.isRegularFile(children)) {
                    continue;
                }
                final String text = new String(Files.readAllBytes(children), StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    continue;
                }
                final String[] parts = text.split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    try {
                        collect(procRoot, Integer.parseInt(parts[i]), seen);
                    } catch (final NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        } catch (final IOException ignored) {
            // process already exited
        }
    }

    static boolean isAlive(final Path procRoot, final int pid) {
        return Files.isDirectory(procRoot.resolve(Integer.toString(pid)));
    }

    static void signal(final int pid, final String spec) {
        try {
            final Process kill = new ProcessBuilder("kill", spec, Integer.toString(pid))
                    .redirectErrorStream(true)
                    .start();
            kill.waitFor();
        } catch (final Exception ignored) {
            // best effort; never fall back to pkill
        }
    }

    static String describe(final List<Integer> pids) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(pids.get(i).intValue());
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    static String formatOwned(final int pid, final List<Integer> pids) {
        return String.format(Locale.US, "owned_pid=%d descendants=%s",
                Integer.valueOf(pid), describe(pids));
    }
}
