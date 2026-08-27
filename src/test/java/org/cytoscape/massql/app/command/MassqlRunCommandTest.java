package org.cytoscape.massql.app.command;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.app.run.ResultAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command's published surface: what callers read before they ever run it. */
class MassqlRunCommandTest {

    private static Set<String> summaryFields() {
        return Stream.of(MassqlRunSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    /**
     * The example is what a caller writes their parsing against, so it has to describe the result
     * model exactly -- every field, no extras, no drift when the record changes.
     */
    @Test
    void theExampleMatchesTheResultModelFieldForField() {
        JsonObject example =
                JsonParser.parseString(MassqlRunCommand.EXAMPLE_JSON).getAsJsonObject();

        assertEquals(
                summaryFields(),
                example.keySet(),
                "COMMAND_EXAMPLE_JSON has drifted from MassqlRunSummary");
    }

    @Test
    void theExampleIsShapedLikeARealResult() {
        String real =
                SummaryJson.of(
                        new MassqlRunSummary(
                                57,
                                0,
                                42,
                                9,
                                "MASSQL::hexose_loss",
                                List.of("MASSQL::hexose_loss_base_peak_i"),
                                List.of(),
                                false));

        assertEquals(
                JsonParser.parseString(MassqlRunCommand.EXAMPLE_JSON),
                JsonParser.parseString(real),
                "the example should be a value the command could actually return");
    }

    @Test
    void aNullResultColumnIsWrittenRatherThanDropped() {
        String json =
                SummaryJson.of(new MassqlRunSummary(1, 0, 1, 0, null, List.of(), List.of(), false));

        // Callers test resultColumn for null to learn whether the JSON column was written.
        assertTrue(json.contains("\"resultColumn\":null"), json);
    }

    @Test
    void theDescriptionSaysWhatKindOfToolThisIs() {
        String longDescription = MassqlRunCommand.LONG_DESCRIPTION;

        for (String expected :
                List.of("MassQL", "mass spectrometry", "scaninfo", ".mgf", ".mzML", "MS1", "MS2")) {
            assertTrue(
                    longDescription.contains(expected),
                    "the long description should mention " + expected);
        }
        assertTrue(
                longDescription.split("\\. ").length >= 3,
                "a scripting caller needs more than a one-line label");
    }

    @Test
    void everyDerivableAttributeIsNamedInTheDocumentation() {
        for (ResultAttribute attribute : ResultAttribute.derivableAttributes()) {
            assertTrue(
                    MassqlCommandTask.derivableNames().contains(attribute.jsonName()),
                    attribute.jsonName() + " is accepted but undocumented");
        }
    }

    @Test
    void parsesACommaSeparatedAttributeList() {
        assertEquals(
                List.of(ResultAttribute.BASE_PEAK_I, ResultAttribute.TIC),
                MassqlCommandTask.parseAttributes(" base_peak_i , tic "));
    }

    @Test
    void anEmptyAttributeListMeansNoNumericColumns() {
        assertEquals(List.of(), MassqlCommandTask.parseAttributes(""));
        assertEquals(List.of(), MassqlCommandTask.parseAttributes(null));
    }

    @Test
    void anUnknownAttributeIsRejectedByName() {
        MassqlException e =
                assertThrows(
                        MassqlException.class,
                        () -> MassqlCommandTask.parseAttributes("base_peak_intensity"));

        assertTrue(e.getMessage().contains("base_peak_intensity"), e.getMessage());
        assertTrue(e.getMessage().contains("base_peak_i"), "and the accepted names listed");
    }

    @Test
    void anIdentifierFieldIsRejectedWithTheSameClarity() {
        MassqlException e =
                assertThrows(
                        MassqlException.class, () -> MassqlCommandTask.parseAttributes("scan"));

        assertTrue(e.getMessage().contains("scan"), e.getMessage());
    }

    @Test
    void theDocumentedAttributesExcludeTheMarkerColumn() {
        assertTrue(
                !MassqlCommandTask.derivableNames().contains("QUERIES"),
                "MASSQL::QUERIES is a marker column, not a derivable attribute");
    }

    @Test
    void theCommandIsAddressableAsMassqlRun() {
        assertEquals("massql", MassqlRunCommand.NAMESPACE);
        assertEquals("run", MassqlRunCommand.NAME);
    }
}
