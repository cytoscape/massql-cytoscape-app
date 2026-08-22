package org.cytoscape.massql.app.run;

import org.cytoscape.massql.MassqlException;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;

/** Naming and creation of the node-table columns a run writes. */
public final class NodeColumns {

    /**
     * Cytoscape reads everything before the first {@code ::} as a namespace, so every column this
     * app writes groups under one heading in the Node Table and the Style column pickers.
     */
    public static final String NAMESPACE = "MASSQL";

    private NodeColumns() {}

    public static String resultColumn(String queryName) {
        return NAMESPACE + "::" + queryName;
    }

    public static String derivedColumn(String queryName, ResultAttribute attribute) {
        return resultColumn(queryName) + "_" + attribute.jsonName();
    }

    /**
     * Returns a mutable column of {@code type} at {@code name}, creating it if absent.
     *
     * <p>An existing column of the right type is reused rather than replaced: dropping it would
     * take every equation and visual mapping bound to it with it, which is a steep price for a
     * re-run of the same query. Only a type clash forces a replacement.
     */
    public static CyColumn ensure(CyTable table, String name, Class<?> type) {
        CyColumn existing = table.getColumn(name);
        if (existing == null) {
            table.createColumn(name, type, false);
            return table.getColumn(name);
        }
        if (existing.getType() == type) {
            return existing;
        }
        if (existing.isImmutable()) {
            throw new MassqlException(
                    "column '"
                            + name
                            + "' already exists as "
                            + existing.getType().getSimpleName()
                            + " and cannot be replaced, because it is immutable. Use a different"
                            + " query name.");
        }
        table.deleteColumn(name);
        table.createColumn(name, type, false);
        return table.getColumn(name);
    }
}
