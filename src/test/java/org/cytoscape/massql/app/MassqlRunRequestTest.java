package org.cytoscape.massql.app;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyNetwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MassqlRunRequestTest {

    private static final String QUERY = "QUERY scaninfo(MS2DATA) WHERE MS2PROD=200.5";

    private static MassqlRunRequest of(
            String name, boolean resultColumn, List<ResultAttribute> attrs) {
        return new MassqlRunRequest(
                Path.of("peaks.mgf"),
                QUERY,
                name,
                "scan",
                resultColumn,
                attrs,
                20.0,
                mock(CyNetwork.class));
    }

    @Test
    void acceptsAQueryNameContainingUnderscores() {
        assertEquals("hexose_loss", of("hexose_loss", true, List.of()).queryName());
    }

    @Test
    void rejectsAQueryNameCarryingTheNamespaceSeparator() {
        MassqlException e =
                assertThrows(MassqlException.class, () -> of("MASSQL::x", true, List.of()));
        assertTrue(e.getMessage().contains("':'"), e.getMessage());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"QUERIES", "queries", "Queries"})
    void rejectsTheReservedQueryName(String queryName) {
        MassqlException e =
                assertThrows(MassqlException.class, () -> of(queryName, true, List.of()));

        assertTrue(e.getMessage().contains("reserved"), e.getMessage());
        assertTrue(e.getMessage().contains("MASSQL::QUERIES"), e.getMessage());
    }

    @Test
    void rejectsARunThatWouldWriteNothing() {
        MassqlException e = assertThrows(MassqlException.class, () -> of("q", false, List.of()));
        assertTrue(e.getMessage().contains("writes nothing"), e.getMessage());
    }

    @Test
    void derivedColumnsAloneAreEnough() {
        MassqlRunRequest request = of("q", false, List.of(ResultAttribute.BASE_PEAK_I));
        assertEquals(List.of(ResultAttribute.BASE_PEAK_I), request.deriveAttributes());
    }

    @Test
    void rejectsDerivingFromAnIdentifierField() {
        MassqlException e =
                assertThrows(
                        MassqlException.class, () -> of("q", true, List.of(ResultAttribute.SCAN)));
        assertTrue(e.getMessage().contains("scan"), e.getMessage());
    }

    @Test
    void repeatingAnAttributeDoesNotWriteItsColumnTwice() {
        MassqlRunRequest request = of("q", true, List.of(ResultAttribute.TIC, ResultAttribute.TIC));
        assertEquals(1, request.deriveAttributes().size());
    }

    @Test
    void rejectsANonPositiveTolerance() {
        assertThrows(
                MassqlException.class,
                () ->
                        new MassqlRunRequest(
                                Path.of("peaks.mgf"),
                                QUERY,
                                "q",
                                "scan",
                                true,
                                List.of(),
                                0.0,
                                mock(CyNetwork.class)));
    }

    @Test
    void rejectsABlankQuery() {
        assertThrows(
                MassqlException.class,
                () ->
                        new MassqlRunRequest(
                                Path.of("peaks.mgf"),
                                "  ",
                                "q",
                                "scan",
                                true,
                                List.of(),
                                20.0,
                                mock(CyNetwork.class)));
    }
}
