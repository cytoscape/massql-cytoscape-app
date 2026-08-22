package org.cytoscape.massql.app.run;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.cytoscape.massql.result.ScanInfoResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultJsonCodecTest {

    private static ScanInfoResult mgfRow() {
        // What an MGF yields: no MS1 survey scan, so five fields are genuinely absent.
        return new ScanInfoResult(
                576, 161.0209, null, 0.0, 1, 1299900.0, 2, 230000.0, 162.1122, null, null, null);
    }

    @Test
    void emitsAllTwelveKeysEvenWhenFieldsAreNull() {
        String json = ResultJsonCodec.toJson(mgfRow());

        for (ResultAttribute attribute : ResultAttribute.values()) {
            assertTrue(
                    json.contains("\"" + attribute.jsonName() + "\""),
                    attribute.jsonName()
                            + " is missing from "
                            + json
                            + " -- without serializeNulls the column's shape varies by input"
                            + " format");
        }
    }

    @Test
    void nullFieldsAreWrittenAsJsonNullNotDropped() {
        assertTrue(ResultJsonCodec.toJson(mgfRow()).contains("\"ms1_i\":null"));
    }

    @Test
    void keysAppearInSchemaOrder() {
        String json = ResultJsonCodec.toJson(mgfRow());

        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile("\"([a-z0-9_]+)\":").matcher(json);
        while (m.find()) {
            found.add(m.group(1));
        }

        assertEquals(ResultAttribute.values().length, found.size());
        for (int i = 0; i < found.size(); i++) {
            assertEquals(ResultAttribute.values()[i].jsonName(), found.get(i), "key " + i);
        }
    }

    @Test
    void aGenuineZeroSurvivesAsZero() {
        ScanInfoResult zeroed =
                new ScanInfoResult(7, 100.0, null, 0.0, 1, 0.0, 2, 0.0, 50.0, null, null, null);

        assertTrue(ResultJsonCodec.toJson(zeroed).contains("\"base_peak_i\":0.0"));
    }
}
