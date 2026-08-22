package org.cytoscape.massql.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the packaging contract of the built bundle.
 *
 * <p>Cytoscape rejects the app outright if the OSGi headers are wrong, and diagnosing that from a
 * silent non-appearance in the App Manager is miserable. These checks fail at build time instead.
 */
class BundleManifestIT {

    private static Path bundleJar() throws IOException {
        Path libs = Path.of("build", "libs");
        assertTrue(Files.isDirectory(libs), "run `make build` first: " + libs.toAbsolutePath());
        try (Stream<Path> jars = Files.list(libs)) {
            return jars.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no jar in " + libs.toAbsolutePath()));
        }
    }

    @Test
    void carriesTheOsgiHeadersCytoscapeRequires() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            Attributes main = jar.getManifest().getMainAttributes();

            assertEquals("org.cytoscape.massql.app", main.getValue("Bundle-SymbolicName"));
            assertEquals("org.cytoscape.massql.app.CyActivator", main.getValue("Bundle-Activator"));
            assertNotNull(main.getValue("Bundle-Version"), "Bundle-Version is required for an app");
        }
    }

    /**
     * The SDK is embedded, so importing it would make the bundle depend on an exporter that does
     * not exist. It resolves today only because Import-Package is optional — an explicit assertion
     * is cheaper than discovering that by accident.
     */
    @Test
    void doesNotImportTheEmbeddedSdk() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            String imports = jar.getManifest().getMainAttributes().getValue("Import-Package");
            if (imports == null) {
                return;
            }
            assertTrue(
                    !imports.contains("org.cytoscape.massql"),
                    "Import-Package must not name the embedded SDK, but was: " + imports);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "org/cytoscape/massql/app/CyActivator.class",
                "org/cytoscape/massql/Massql.class",
                "com/google/gson/Gson.class",
                "org/antlr/v4/runtime/Parser.class",
                "javolution/xml/internal/stream/XMLStreamReaderImpl.class"
            })
    void embedsEverythingTheRuntimeDoesNotProvide(String entry) throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            assertNotNull(jar.getEntry(entry), entry + " is missing from the bundle");
        }
    }
}
