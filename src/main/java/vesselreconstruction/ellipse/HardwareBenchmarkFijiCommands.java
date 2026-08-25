package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiji command construction for the hardware benchmark.
 *
 * <p>Reuses {@link BatchFullProcessingCli#buildFijiCommand} and
 * {@link BatchPrecheckSupervisor#buildFijiCommand}. Heap is the only extra
 * execution argument; tile geometry is never changed.</p>
 */
public final class HardwareBenchmarkFijiCommands {

    private HardwareBenchmarkFijiCommands() {
    }

    public static List<String> classificationCommand(
            final Path fijiLauncher,
            final Path jobPath,
            final int fijiHeapMb) {
        return classificationCommand(fijiLauncher, jobPath, fijiHeapMb, null);
    }

    public static List<String> classificationCommand(
            final Path fijiLauncher,
            final Path jobPath,
            final int fijiHeapMb,
            final Path pluginsDir) {
        return withLaunchOptions(
                BatchFullProcessingCli.buildFijiCommand(fijiLauncher, jobPath),
                fijiHeapMb, pluginsDir);
    }

    public static List<String> precheckCommand(
            final Path fijiLauncher,
            final Path jobPath,
            final int fijiHeapMb) {
        return precheckCommand(fijiLauncher, jobPath, fijiHeapMb, null);
    }

    public static List<String> precheckCommand(
            final Path fijiLauncher,
            final Path jobPath,
            final int fijiHeapMb,
            final Path pluginsDir) {
        return withLaunchOptions(
                BatchPrecheckSupervisor.buildFijiCommand(fijiLauncher, jobPath),
                fijiHeapMb, pluginsDir);
    }

    static List<String> withHeap(final List<String> base, final int fijiHeapMb) {
        return withLaunchOptions(base, fijiHeapMb, null);
    }

    static List<String> withLaunchOptions(
            final List<String> base,
            final int fijiHeapMb,
            final Path pluginsDir) {
        final List<String> command = new ArrayList<String>(base);
        if (command.isEmpty()) {
            return command;
        }
        int insertAt = 1;
        if (pluginsDir != null) {
            command.add(insertAt, "--plugins=" + pluginsDir.toAbsolutePath().normalize());
            insertAt++;
        }
        if (fijiHeapMb > 0) {
            command.addAll(insertAt,
                    new HardwareExecutionProfile(1, fijiHeapMb).fijiHeapArguments());
        }
        return command;
    }

    /**
     * Isolated Fiji plugins directory for the benchmark. Production
     * {@code Fiji.app/plugins} is not modified. Other Fiji plugin jars are
     * reused via symbolic links; only the Vessel Reconstruction JAR is
     * overlaid from this repository.
     */
    public static Path isolatePlugins(
            final Path fijiLauncher,
            final Path destDir,
            final Path overlayJar) throws IOException {
        if (fijiLauncher == null || overlayJar == null || !Files.isRegularFile(overlayJar)) {
            throw new IllegalArgumentException("fiji launcher and overlay plugin JAR are required");
        }
        final Path fijiApp = fijiLauncher.toAbsolutePath().normalize().getParent();
        final Path sourcePlugins = fijiApp.resolve("plugins");
        Files.createDirectories(destDir);
        if (Files.isDirectory(sourcePlugins)) {
            final DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePlugins, "*.jar");
            try {
                for (final Path jar : stream) {
                    final String name = jar.getFileName().toString();
                    if (name.startsWith("VesselReconstructionEllipse")) {
                        continue;
                    }
                    final Path link = destDir.resolve(name);
                    Files.deleteIfExists(link);
                    try {
                        Files.createSymbolicLink(link, jar.toAbsolutePath().normalize());
                    } catch (final IOException ignored) {
                        Files.copy(jar, link, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } finally {
                stream.close();
            }
        }
        Files.copy(overlayJar.toAbsolutePath().normalize(),
                destDir.resolve(overlayJar.getFileName().toString()),
                StandardCopyOption.REPLACE_EXISTING);
        return destDir.toAbsolutePath().normalize();
    }

    /**
     * Isolated Fiji.app tree so the benchmark can load this repository's
     * plugin JAR. Production {@code LumenRuntime/.../Fiji.app} is not modified.
     *
     * @return path to the isolated {@code ImageJ-linux64} launcher
     */
    public static Path isolateFijiApp(
            final Path realLauncher,
            final Path destApp,
            final Path overlayJar) throws IOException {
        if (realLauncher == null || !Files.isRegularFile(realLauncher)) {
            throw new IllegalArgumentException("real Fiji launcher is required");
        }
        final Path realApp = realLauncher.toAbsolutePath().normalize().getParent();
        Files.createDirectories(destApp);
        final String[] linkNames = {
                "jars", "java", "lib", "config", "images", "luts", "macros",
                "scripts", "licenses", "retro", "db.xml.gz", "fiji",
                "README.md", "WELCOME.md"
        };
        for (int i = 0; i < linkNames.length; i++) {
            final Path source = realApp.resolve(linkNames[i]);
            if (!Files.exists(source)) {
                continue;
            }
            final Path link = destApp.resolve(linkNames[i]);
            Files.deleteIfExists(link);
            try {
                Files.createSymbolicLink(link, source);
            } catch (final IOException ignored) {
                if (Files.isDirectory(source)) {
                    throw new IOException("cannot isolate Fiji directory: " + source);
                }
                Files.copy(source, link, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        isolatePlugins(realLauncher, destApp.resolve("plugins"), overlayJar);
        copyExecutable(realApp.resolve("ImageJ-linux64"), destApp.resolve("ImageJ-linux64"));
        copyExecutable(realApp.resolve("fiji-linux-x64"), destApp.resolve("fiji-linux-x64"));
        final Path isolatedLauncher = destApp.resolve("ImageJ-linux64");
        if (!Files.isRegularFile(isolatedLauncher)) {
            throw new IOException("failed to isolate Fiji launcher at " + isolatedLauncher);
        }
        isolatedLauncher.toFile().setExecutable(true);
        return isolatedLauncher.toAbsolutePath().normalize();
    }

    private static void copyExecutable(final Path source, final Path dest) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.deleteIfExists(dest);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        dest.toFile().setExecutable(true);
    }
}
