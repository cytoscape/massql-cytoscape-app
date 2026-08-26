package org.cytoscape.massql.app.run;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyTable;
import org.cytoscape.model.TableTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which columns a query name owns, and what survives a re-run. */
class NodeColumnsTest {

    private CyTable table;

    @BeforeEach
    void setUp() {
        table =
                new TableTestSupport()
                        .getTableFactory()
                        .createTable("nodes", "id", Long.class, true, true);
    }

    private void given(String... columns) {
        for (String column : columns) {
            table.createColumn(column, String.class, false);
        }
    }

    private List<String> remaining() {
        return table.getColumns().stream()
                .map(c -> c.getName())
                .filter(n -> n.startsWith("MASSQL::"))
                .sorted()
                .toList();
    }

    @Test
    void namesTheColumnsAQueryWrites() {
        assertEquals("MASSQL::q", NodeColumns.resultColumn("q"));
        assertEquals(
                "MASSQL::q_base_peak_i",
                NodeColumns.derivedColumn("q", ResultAttribute.BASE_PEAK_I));
    }

    @Test
    void keepsWhatTheRunWritesAndRemovesTheRest() {
        given("MASSQL::q", "MASSQL::q_tic", "MASSQL::q_base_peak_i");

        NodeColumns.removeStaleColumns(table, "q", Set.of("MASSQL::q", "MASSQL::q_tic"));

        assertEquals(List.of("MASSQL::q", "MASSQL::q_tic"), remaining());
    }

    /**
     * The trap: a prefix test alone would take "ab" to belong to "a". Ownership is the result
     * column itself or one of its {@code _attribute} columns, and nothing else.
     */
    @Test
    void aQueryDoesNotOwnAnotherWhoseNameMerelyStartsTheSame() {
        given("MASSQL::a", "MASSQL::a_tic", "MASSQL::ab", "MASSQL::ab_tic", "MASSQL::abc");

        NodeColumns.removeStaleColumns(table, "a", Set.of());

        assertEquals(List.of("MASSQL::ab", "MASSQL::ab_tic", "MASSQL::abc"), remaining());
    }

    @Test
    void leavesColumnsThisAppDidNotWrite() {
        given("MASSQL::q_tic", "scan", "mz", "shared name");

        NodeColumns.removeStaleColumns(table, "q", Set.of());

        assertEquals(List.of(), remaining());
        assertNotNull(table.getColumn("scan"));
        assertNotNull(table.getColumn("mz"));
        assertNotNull(table.getColumn("shared name"));
    }

    /** Cytoscape matches column names without regard to case, so ownership has to as well. */
    @Test
    void ownershipIgnoresCase() {
        given("MASSQL::Q_tic");

        NodeColumns.removeStaleColumns(table, "q", Set.of());

        assertEquals(List.of(), remaining());
    }

    @Test
    void aQueryNameContainingUnderscoresOwnsItsOwnColumns() {
        given("MASSQL::bile_acid", "MASSQL::bile_acid_tic", "MASSQL::bile");

        NodeColumns.removeStaleColumns(table, "bile_acid", Set.of("MASSQL::bile_acid"));

        assertEquals(List.of("MASSQL::bile", "MASSQL::bile_acid"), remaining());
    }

    @Test
    void aFirstRunHasNothingToRemove() {
        NodeColumns.removeStaleColumns(table, "q", Set.of("MASSQL::q"));

        assertNull(table.getColumn("MASSQL::q"));
    }
}
