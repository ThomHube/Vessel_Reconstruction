package vesselreconstruction.ellipse;

import ij.IJ;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Stage-local logger that mirrors messages to IJ.log and a run log file.
 */
public final class ProductionStageLogger implements Closeable {

    private final Path logFile;
    private final BufferedWriter writer;
    private boolean closed;

    public ProductionStageLogger(final Path logFile) throws IOException {
        this.logFile = logFile;
        Files.createDirectories(logFile.getParent());
        this.writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(logFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE),
                StandardCharsets.UTF_8));
        this.closed = false;
    }

    public Path getLogFile() {
        return logFile;
    }

    public void log(final String message) {
        final String text = message == null ? "" : message;
        IJ.log(text);
        if (!closed) {
            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
            } catch (final IOException ex) {
                IJ.log("WARNING: failed writing run log: " + ex.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            writer.close();
        }
    }
}
