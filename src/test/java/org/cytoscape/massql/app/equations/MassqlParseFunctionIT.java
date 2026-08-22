package org.cytoscape.massql.app.equations;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.equations.Equation;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.massql.app.run.MassqlRunner;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@code MASSQL_PARSE} the way a user meets it: compiled into an equation and stored in a
 * cell, then read back through a real {@link CyTable}.
 *
 * <p>This is the path the unit tests cannot cover, because it is Cytoscape -- not the function --
 * that decides whether a null answer becomes a blank cell or an error cell.
 */
class MassqlParseFunctionIT {

    private static final String QUERY =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5";

    /** The scan of the only spectrum the query matches; micro.mgf numbers by position. */
    private static final int MATCHING_SCAN = 2;

    private static final String RESULT_COLUMN = "MASSQL::q";
    private static final String DERIVED_COLUMN = "bpi";

    private final MassqlEquationTestSupport support = new MassqlEquationTestSupport();
    private CyNetwork network;
    private CyTable nodeTable;
    private CyNode matched;
    private CyNode missed;

    @BeforeEach
    void setUp() {
        network = support.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        nodeTable.createColumn("scan", Integer.class, false);

        matched = network.addNode();
        network.getRow(matched).set("scan", MATCHING_SCAN);
        missed = network.addNode();
        network.getRow(missed).set("scan", 999);

        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyEventHelper.class)).thenReturn(new DummyCyEventHelper());

        new MassqlRunner(registrar)
                .run(
                        new MassqlRunRequest(
                                TestFixtures.require("fixtures/micro/micro.mgf"),
                                QUERY,
                                "q",
                                "scan",
                                true,
                                ResultAttribute.derivableAttributes(),
                                20.0,
                                network),
                        null,
                        () -> false);

        nodeTable.createColumn(DERIVED_COLUMN, Double.class, false);
    }

    /** Compiles a formula and stores it in {@link #DERIVED_COLUMN} on every node. */
    private void applyFormula(String formula) {
        Map<String, Class<?>> columnTypes = new HashMap<>();
        for (var column : nodeTable.getColumns()) {
            columnTypes.put(column.getName(), column.getType());
        }
        // The engine rejects a formula that references the column it is being stored in.
        columnTypes.remove(DERIVED_COLUMN);

        assertTrue(
                support.compiler().compile(formula, columnTypes),
                () -> "did not compile: " + support.compiler().getLastErrorMsg());

        Equation equation = support.compiler().getEquation();
        network.getRow(matched).set(DERIVED_COLUMN, equation);
        network.getRow(missed).set(DERIVED_COLUMN, equation);
    }

    private Double valueOn(CyNode node) {
        return network.getRow(node).get(DERIVED_COLUMN, Double.class);
    }

    @Test
    void readsAnAttributeOutOfTheResultColumn() {
        applyFormula("=MASSQL_PARSE(${" + RESULT_COLUMN + "}, \"base_peak_i\")");

        assertEquals(1500.0, valueOn(matched));
    }

    /**
     * The reason unmatched nodes are written as {@code ""} rather than left null. A null cell makes
     * the engine abandon the equation before the function runs and record an error, which the table
     * browser paints as a broken cell; an empty string reaches the function and comes back blank.
     */
    @Test
    void anUnmatchedNodeIsBlankRatherThanBroken() {
        applyFormula("=MASSQL_PARSE(${" + RESULT_COLUMN + "}, \"base_peak_i\")");

        assertNull(valueOn(missed));
        assertTrue(
                isBlank(nodeTable.getLastInternalError()),
                "an unmatched node must not register an evaluation error, but got: "
                        + nodeTable.getLastInternalError());
    }

    @Test
    void aFieldTheInstrumentDidNotRecordIsAlsoBlank() {
        applyFormula("=MASSQL_PARSE(${" + RESULT_COLUMN + "}, \"ms1_i\")");

        assertNull(valueOn(matched), "micro.mgf carries no MS1 data");
        assertTrue(isBlank(nodeTable.getLastInternalError()));
    }

    @Test
    void attributesCombineIntoDerivedArithmetic() {
        applyFormula(
                "=MASSQL_PARSE(${"
                        + RESULT_COLUMN
                        + "}, \"base_peak_i\") / MASSQL_PARSE(${"
                        + RESULT_COLUMN
                        + "}, \"tic\")");

        assertNotNull(valueOn(matched));
        assertEquals(1500.0 / 2600.0, valueOn(matched), 1e-9);
    }

    /** A mistyped attribute is a broken formula, and Cytoscape reports it as one. */
    @Test
    void aMistypedAttributeRegistersAnEvaluationError() {
        applyFormula("=MASSQL_PARSE(${" + RESULT_COLUMN + "}, \"base_peak_intensity\")");

        assertNull(valueOn(matched));
        assertTrue(
                nodeTable.getLastInternalError() != null
                        && nodeTable.getLastInternalError().contains("base_peak_intensity"),
                "expected the typo to be reported, got: " + nodeTable.getLastInternalError());
    }

    /**
     * The app offers two routes to the same number: a numeric column it derives directly, or this
     * function over the JSON column. They read different code -- an accessor on the record versus a
     * parse of its serialized form -- so nothing but a test keeps them agreeing.
     */
    @Test
    void aDerivedColumnAgreesWithParsingTheJsonColumn() {
        for (ResultAttribute attribute : ResultAttribute.derivableAttributes()) {
            String derived = "MASSQL::q_" + attribute.jsonName();
            Double byColumn = network.getRow(matched).get(derived, Double.class);

            applyFormula(
                    "=MASSQL_PARSE(${" + RESULT_COLUMN + "}, \"" + attribute.jsonName() + "\")");
            Double byFunction = valueOn(matched);

            assertEquals(
                    byColumn,
                    byFunction,
                    () -> attribute.jsonName() + ": derived column and MASSQL_PARSE disagree");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
