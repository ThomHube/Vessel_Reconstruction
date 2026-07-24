package vesselreconstruction.ellipse;

import ij.plugin.PlugIn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scaffold verification for the Robust Ellipse Reconstruction prototype.
 * Does not open ImageJ dialogs.
 */
class ProjectScaffoldTest {

    @Test
    void woodEqualsOne() {
        assertEquals(1, ClassifierLabels.WOOD);
    }

    @Test
    void rayEqualsTwo() {
        assertEquals(2, ClassifierLabels.RAY);
    }

    @Test
    void lumenEqualsThree() {
        assertEquals(3, ClassifierLabels.LUMEN);
    }

    @Test
    void backgroundEqualsFour() {
        assertEquals(4, ClassifierLabels.BACKGROUND);
    }

    @Test
    void nameOfWood() {
        assertEquals("Wood", ClassifierLabels.nameOf(1));
    }

    @Test
    void nameOfRay() {
        assertEquals("Ray", ClassifierLabels.nameOf(2));
    }

    @Test
    void nameOfLumen() {
        assertEquals("Lumen", ClassifierLabels.nameOf(3));
    }

    @Test
    void nameOfBackground() {
        assertEquals("Background", ClassifierLabels.nameOf(4));
    }

    @Test
    void nameOfZeroThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ClassifierLabels.nameOf(0));
        assertTrue(ex.getMessage().contains("0"));
    }

    @Test
    void nameOfFiveThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ClassifierLabels.nameOf(5));
        assertTrue(ex.getMessage().contains("5"));
    }

    @Test
    void exceptionMessageContainsUnsupportedValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ClassifierLabels.nameOf(99));
        assertTrue(ex.getMessage().contains("99"));
    }

    @Test
    void pluginImplementsPlugIn() {
        assertTrue(PlugIn.class.isAssignableFrom(EllipseReconstructionPlugin.class));
    }

    @Test
    void pluginCanBeInstantiated() {
        EllipseReconstructionPlugin plugin = new EllipseReconstructionPlugin();
        assertNotNull(plugin);
    }

    @Test
    void pluginsConfigExists() {
        Path config = projectRoot().resolve("src/main/resources/plugins.config");
        assertTrue(Files.isRegularFile(config), "plugins.config must exist under src/main/resources");
    }

    @Test
    void pluginsConfigContainsFullyQualifiedClassName() throws IOException {
        String content = readPluginsConfig();
        assertTrue(content.contains("vesselreconstruction.ellipse.EllipseReconstructionPlugin"));
    }

    @Test
    void pluginsConfigContainsMenuName() throws IOException {
        String content = readPluginsConfig();
        assertTrue(content.contains("Robust Ellipse Reconstruction Prototype"));
    }

    @Test
    void noLegacyIdentifiersInJavaSources() throws IOException {
        List<String> forbidden = forbiddenLegacyIdentifiers();
        Path root = projectRoot();
        List<String> hits = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> javaFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isIgnoredPath(root, p))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                for (String name : forbidden) {
                    if (source.contains(name)) {
                        hits.add(root.relativize(javaFile) + " contains " + name);
                    }
                }
            }
        }

        assertTrue(hits.isEmpty(), "Forbidden legacy identifiers found:\n" + String.join("\n", hits));
    }

    private static List<String> forbiddenLegacyIdentifiers() {
        // Construct from parts so this test source does not embed full forbidden names.
        List<String> names = new ArrayList<>();
        names.add("Artifact" + "Arc" + "Candidate");
        names.add("Artifact" + "Arc" + "Candidate" + "Finder");
        names.add("Artifact" + "Bridge" + "Triplet");
        names.add("Bridge" + "Candidate");
        names.add("Pixel" + "Edge" + "Boundary" + "Tracer");
        return names;
    }

    private static String readPluginsConfig() throws IOException {
        Path config = projectRoot().resolve("src/main/resources/plugins.config");
        return Files.readString(config, StandardCharsets.UTF_8);
    }

    private static boolean isIgnoredPath(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if ("target".equals(name) || ".git".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the Maven project root from this test class location without
     * depending on the process working directory.
     */
    private static Path projectRoot() {
        try {
            Path classFile = Paths.get(
                    ProjectScaffoldTest.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI());
            // test-classes -> target -> project root
            Path fromTarget = classFile.getParent().getParent();
            if (Files.isRegularFile(fromTarget.resolve("pom.xml"))) {
                return fromTarget.toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {
            // fall through to source-path heuristics
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("pom.xml"))) {
            return cwd;
        }

        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isRegularFile(userDir.resolve("pom.xml"))) {
            return userDir;
        }

        throw new IllegalStateException("Unable to resolve Maven project root containing pom.xml");
    }
}
