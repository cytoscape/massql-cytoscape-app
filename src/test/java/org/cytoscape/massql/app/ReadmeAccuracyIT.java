package org.cytoscape.massql.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.massql.app.command.MassqlRunCommand;
import org.cytoscape.massql.app.run.ResultAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the README's factual claims tied to the code.
 *
 * <p>Everything asserted here is something a researcher would act on — which attributes they can
 * ask for, and what shape the results come back in. Prose that has drifted from the behaviour is
 * worse than no prose, because it is trusted.
 */
class ReadmeAccuracyIT {

    private static String readme() throws IOException {
        Path path = Path.of("README.md");
        assertTrue(Files.isRegularFile(path), "README.md not found at " + path.toAbsolutePath());
        return Files.readString(path);
    }

    private static List<String> jsonBlocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL).matcher(markdown);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        return blocks;
    }

    @Test
    void theAttributesItOffersAreTheOnesTheAppDerives() throws IOException {
        Matcher m =
                Pattern.compile(
                                "Eight measured attributes are offered: (.+?)\\.\\s",
                                Pattern.DOTALL)
                        .matcher(readme());
        assertTrue(m.find(), "the README no longer lists the derivable attributes");

        List<String> documented = new ArrayList<>();
        Matcher names = Pattern.compile("`([a-z0-9_]+)`").matcher(m.group(1));
        while (names.find()) {
            documented.add(names.group(1));
        }

        assertEquals(
                ResultAttribute.derivableAttributes().stream()
                        .map(ResultAttribute::jsonName)
                        .toList(),
                documented);
    }

    @Test
    void theResultObjectItShowsCarriesTheRealSchema() throws IOException {
        JsonObject shown = JsonParser.parseString(jsonBlocks(readme()).get(0)).getAsJsonObject();

        assertEquals(
                java.util.stream.Stream.of(ResultAttribute.values())
                        .map(ResultAttribute::jsonName)
                        .toList(),
                List.copyOf(shown.keySet()),
                "the example result object no longer matches the 12-key schema");
    }

    @Test
    void theCommandOutputItShowsIsThePublishedExample() throws IOException {
        JsonObject shown = JsonParser.parseString(jsonBlocks(readme()).get(1)).getAsJsonObject();

        assertEquals(
                JsonParser.parseString(MassqlRunCommand.EXAMPLE_JSON).getAsJsonObject(),
                shown,
                "the README and COMMAND_EXAMPLE_JSON disagree about what a run returns");
    }
}
