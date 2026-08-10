package vesselreconstruction.ellipse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Launches one BatchFullProcessingCli subprocess for a single assigned image.
 * Injectable for unit tests; production uses ProcessBuilder.
 */
public interface BatchMachineImageProcessLauncher {

    /**
     * Result of launching (or simulating) one single-image full-processing CLI.
     */
    final class LaunchResult {
        private final int exitCode;
        private final List<String> command;

        public LaunchResult(final int exitCode, final List<String> command) {
            this.exitCode = exitCode;
            this.command = Collections.unmodifiableList(new ArrayList<String>(
                    Objects.requireNonNull(command, "command")));
        }

        public int exitCode() {
            return exitCode;
        }

        public List<String> command() {
            return command;
        }
    }

    /**
     * Arguments for one BatchFullProcessingCli subprocess.
     */
    final class Request {
        private final Path javaExecutable;
        private final Path classesDirectory;
        private final Path inputRoot;
        private final Path batchRoot;
        private final String machineId;
        private final String imageId;
        private final Path configPath;
        private final Path classifierPath;
        private final Path fijiLauncher;
        private final int tileProfileIndex;
        private final Path controllerLogPath;

        public Request(
                final Path javaExecutable,
                final Path classesDirectory,
                final Path inputRoot,
                final Path batchRoot,
                final String machineId,
                final String imageId,
                final Path configPath,
                final Path classifierPath,
                final Path fijiLauncher,
                final int tileProfileIndex,
                final Path controllerLogPath) {
            this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable");
            this.classesDirectory = Objects.requireNonNull(classesDirectory, "classesDirectory");
            this.inputRoot = Objects.requireNonNull(inputRoot, "inputRoot");
            this.batchRoot = Objects.requireNonNull(batchRoot, "batchRoot");
            this.machineId = Objects.requireNonNull(machineId, "machineId");
            this.imageId = Objects.requireNonNull(imageId, "imageId");
            this.configPath = Objects.requireNonNull(configPath, "configPath");
            this.classifierPath = Objects.requireNonNull(classifierPath, "classifierPath");
            this.fijiLauncher = Objects.requireNonNull(fijiLauncher, "fijiLauncher");
            this.tileProfileIndex = tileProfileIndex;
            this.controllerLogPath = Objects.requireNonNull(controllerLogPath, "controllerLogPath");
        }

        public Path javaExecutable() {
            return javaExecutable;
        }

        public Path classesDirectory() {
            return classesDirectory;
        }

        public Path inputRoot() {
            return inputRoot;
        }

        public Path batchRoot() {
            return batchRoot;
        }

        public String machineId() {
            return machineId;
        }

        public String imageId() {
            return imageId;
        }

        public Path configPath() {
            return configPath;
        }

        public Path classifierPath() {
            return classifierPath;
        }

        public Path fijiLauncher() {
            return fijiLauncher;
        }

        public int tileProfileIndex() {
            return tileProfileIndex;
        }

        public Path controllerLogPath() {
            return controllerLogPath;
        }

        public List<String> buildCommand() {
            final List<String> command = new ArrayList<String>();
            command.add(javaExecutable.toAbsolutePath().normalize().toString());
            command.add("-cp");
            command.add(classesDirectory.toAbsolutePath().normalize().toString());
            command.add("vesselreconstruction.ellipse.BatchFullProcessingCli");
            command.add("--input-root");
            command.add(inputRoot.toAbsolutePath().normalize().toString());
            command.add("--batch-root");
            command.add(batchRoot.toAbsolutePath().normalize().toString());
            command.add("--machine-id");
            command.add(machineId);
            command.add("--image-id");
            command.add(imageId);
            command.add("--config");
            command.add(configPath.toAbsolutePath().normalize().toString());
            command.add("--classifier");
            command.add(classifierPath.toAbsolutePath().normalize().toString());
            command.add("--fiji-launcher");
            command.add(fijiLauncher.toAbsolutePath().normalize().toString());
            command.add("--tile-profile-index");
            command.add(Integer.toString(tileProfileIndex));
            return command;
        }
    }

    LaunchResult launch(Request request) throws IOException, InterruptedException;
}
