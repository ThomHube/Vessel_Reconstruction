package vesselreconstruction.ellipse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * Production ProcessBuilder launcher for one BatchFullProcessingCli subprocess.
 * Tees combined process output to the terminal and a per-image controller log.
 */
public final class BatchMachineDefaultImageProcessLauncher
        implements BatchMachineImageProcessLauncher {

    @Override
    public LaunchResult launch(final Request request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        final List<String> command = request.buildCommand();
        Files.createDirectories(request.controllerLogPath().getParent());

        final ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final StringBuilder captured = new StringBuilder();
        final Thread gobbler = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        captured.append(line).append('\n');
                    }
                } catch (final IOException ignored) {
                    // process ended
                }
            }
        }, "machine-supervisor-image-tee");
        gobbler.setDaemon(true);
        gobbler.start();

        final int exitCode = process.waitFor();
        gobbler.join(5000L);
        Sha256Files.writeBytesAtomically(
                request.controllerLogPath(),
                captured.toString().getBytes(StandardCharsets.UTF_8));
        return new LaunchResult(exitCode, command);
    }
}
