package org.cytoscape.massql.app;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Locates a committed fixture under {@code src/test/resources}. */
public final class TestFixtures {
    private TestFixtures() {}

    public static Path require(String relative) {
        URL url = TestFixtures.class.getClassLoader().getResource(relative);
        if (url == null) {
            throw new AssertionError(missing(relative));
        }
        Path p;
        try {
            p = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("fixture URL is not a usable file path: " + url, e);
        }
        if (!Files.exists(p)) {
            throw new AssertionError(missing(relative));
        }
        return p;
    }

    private static String missing(String relative) {
        return "fixture missing from src/test/resources: "
                + relative
                + "\nIt is committed to this repository. Restore it rather than making the test"
                + " conditional on its presence.";
    }
}
