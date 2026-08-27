package org.cytoscape.massql.app.run;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Names every query a node currently matches, so the queries a node took part in can be read
     * from the node itself rather than by scanning every other MASSQL column.
     */
    public static final String QUERIES_COLUMN = NAMESPACE + "::QUERIES";

    private static final String RESERVED_QUERY_NAME = "QUERIES";

    private NodeColumns() {}

    /** Whether a query of this name would write over {@link #QUERIES_COLUMN}. */
    public static boolean isReservedQueryName(String queryName) {
        return RESERVED_QUERY_NAME.equalsIgnoreCase(queryName == null ? null : queryName.trim());
    }

    /**
     * Returns the marker column, creating it if absent.
     *
     * <p>Created with no default value on purpose: a column's default list is one instance shared
     * by every unwritten row, so a mutable default is corruptible by any caller and an immutable
     * one refuses the appends this column exists for. An unwritten cell therefore reads back null,
     * which callers treat as no names.
     */
    public static CyColumn ensureQueriesColumn(CyTable table) {
        CyColumn existing = table.getColumn(QUERIES_COLUMN);
        if (existing == null) {
            table.createListColumn(QUERIES_COLUMN, String.class, false);
            return table.getColumn(QUERIES_COLUMN);
        }
        if (existing.getType() != List.class || existing.getListElementType() != String.class) {
            throw new MassqlException(
                    "column '"
                            + QUERIES_COLUMN
                            + "' already exists as "
                            + existing.getType().getSimpleName()
                            + " and is not the list of query names this app maintains. Rename or"
                            + " remove it.");
        }
        return existing;
    }

    public static String resultColumn(String queryName) {
        return NAMESPACE + "::" + queryName;
    }

    public static String derivedColumn(String queryName, ResultAttribute attribute) {
        return resultColumn(queryName) + "_" + attribute.jsonName();
    }

    /**
     * Removes columns from an earlier run of {@code queryName} that this run will not write, so a
     * re-run leaves nothing behind describing the previous query.
     *
     * <p>Columns the run does write are left in place for {@link #ensure} to reuse: their every row
     * is rewritten, so they carry nothing stale, and keeping the column keeps whatever visual
     * mapping or equation the user has bound to it.
     *
     * <p>Ownership is matched exactly -- the result column itself, or one of its {@code _attribute}
     * columns -- so re-running "a" leaves the columns of a query named "ab" alone.
     */
    public static void removeStaleColumns(CyTable table, String queryName, Set<String> keeping) {
        String owned = resultColumn(queryName);
        String prefix = owned + "_";
        Set<String> kept = keeping.stream().map(NodeColumns::key).collect(Collectors.toSet());

        for (CyColumn column : List.copyOf(table.getColumns())) {
            String name = column.getName();
            String key = key(name);
            // The marker column belongs to no single query, and a reserved name keeps any query
            // from claiming it -- but a column left by an earlier version is guarded here too.
            boolean ours =
                    !key.equals(key(QUERIES_COLUMN))
                            && (key.equals(key(owned)) || key.startsWith(key(prefix)));
            if (ours && !kept.contains(key) && !column.isImmutable()) {
                table.deleteColumn(name);
            }
        }
    }

    /** Cytoscape matches column names without regard to case, so ownership has to as well. */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
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
