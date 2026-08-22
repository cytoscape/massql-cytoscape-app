package org.cytoscape.massql.app.equations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.massql.app.run.ResultJsonCodec;
import org.cytoscape.massql.result.ScanInfoResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MassqlParseFunctionTest {

    private final MassqlParseFunction function = new MassqlParseFunction();

    /** An MGF row: no MS1 survey scan, so the four ms1 fields are genuinely absent. */
    private static String mgfResult() {
        return ResultJsonCodec.toJson(
                new ScanInfoResult(
                        576, 161.0209, null, 0.0, 1, 1299900.0, 2, 230000.0, 162.1122, null, null,
                        null));
    }

    private Object evaluate(String json, String attribute) {
        return function.evaluateFunction(new Object[] {json, attribute});
    }

    @Test
    void declaresItselfToTheFormulaBuilder() {
        assertEquals("MASSQL_PARSE", function.getName());
        assertEquals(Double.class, function.getReturnType());
        assertEquals("MassQL", function.getCategoryName());
        assertEquals(2, function.getArgumentDescriptors().size());
    }

    @Test
    void readsAMeasuredValue() {
        assertEquals(230000.0, evaluate(mgfResult(), "base_peak_i"));
    }

    @Test
    void aMeasuredZeroIsAValueNotAnAbsence() {
        String zeroed =
                ResultJsonCodec.toJson(
                        new ScanInfoResult(
                                7, 100.0, null, 0.0, 1, 0.0, 2, 0.0, 50.0, null, null, null));

        // The whole point of the design: a real 0.0 must not read the same as no data.
        assertEquals(0.0, evaluate(zeroed, "base_peak_i"));
        assertEquals(0.0, evaluate(zeroed, "rt"));
    }

    @Test
    void aFieldTheInstrumentDidNotRecordIsAbsent() {
        assertNull(evaluate(mgfResult(), "ms1_i"));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "   ", "\t"})
    void anUnmatchedNodeIsAbsent(String cell) {
        assertNull(evaluate(cell, "base_peak_i"));
    }

    @Test
    void aNullCellIsAbsent() {
        assertNull(evaluate(null, "base_peak_i"));
    }

    @Test
    void anIntegerAttributeComesBackAsANumber() {
        assertEquals(576.0, evaluate(mgfResult(), "scan"));
        assertEquals(2.0, evaluate(mgfResult(), "mslevel"));
    }

    @Test
    void everyAttributeInTheSchemaIsReadable() {
        for (ResultAttribute attribute : ResultAttribute.values()) {
            evaluate(mgfResult(), attribute.jsonName());
        }
    }

    /**
     * A typo is a mistake in the formula, not a missing measurement, so it has to be visible.
     * Checked before the cell is read, so it reports on unmatched rows too.
     */
    @Test
    void aMistypedAttributeIsAnErrorEvenOnAnEmptyCell() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class, () -> evaluate("", "base_peak_intensity"));

        assertTrue(e.getMessage().contains("base_peak_intensity"), e.getMessage());
        assertTrue(e.getMessage().contains("base_peak_i"), "the message should list valid names");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"not json at all", "{\"scan\":", "[1,2,3]", "\"just a string\""})
    void aCellThatIsNotAResultObjectIsAnError(String cell) {
        assertThrows(IllegalArgumentException.class, () -> evaluate(cell, "base_peak_i"));
    }

    @Test
    void aNonNumericAttributeIsAnError() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> evaluate("{\"base_peak_i\":\"loads\"}", "base_peak_i"));

        assertTrue(e.getMessage().contains("not a number"), e.getMessage());
    }

    @Test
    void anAttributeMissingFromAnOlderResultIsAnError() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> evaluate("{\"scan\":1}", "base_peak_i"));

        assertTrue(e.getMessage().contains("carries no"), e.getMessage());
    }

    @Test
    void repeatedReadsOfTheSameCellAgree() {
        String json = mgfResult();
        for (int i = 0; i < 2500; i++) {
            assertEquals(230000.0, evaluate(json, "base_peak_i"));
        }
    }

    /** Evicting the least-recently-used entry must change nothing a caller can observe. */
    @Test
    void aCacheOverflowDoesNotChangeAnyAnswer() {
        for (int scan = 0; scan < 2000; scan++) {
            String json =
                    ResultJsonCodec.toJson(
                            new ScanInfoResult(
                                    scan,
                                    1.0,
                                    null,
                                    0.0,
                                    1,
                                    1.0,
                                    2,
                                    (double) scan,
                                    1.0,
                                    null,
                                    null,
                                    null));
            assertEquals((double) scan, evaluate(json, "base_peak_i"));
        }
        assertEquals(230000.0, evaluate(mgfResult(), "base_peak_i"));
    }
}
