package org.cytoscape.massql.app;

import java.util.List;

/**
 * What a run did, for the dialog to display and the command to return as JSON.
 *
 * @param resultRows rows the query matched in the peak list
 * @param duplicateScans result rows discarded because an earlier row claimed the same scan number
 * @param matchedNodes nodes whose scan number matched a result row
 * @param unmatchedNodes nodes that did not
 * @param resultColumn the JSON column written, or null if it was not requested
 * @param derivedColumns the numeric columns written, in the order requested
 * @param diagnostics whatever the engine wanted the caller to know
 * @param cancelled whether the user stopped the run, in which case nothing was written
 */
public record MassqlRunSummary(
        int resultRows,
        int duplicateScans,
        int matchedNodes,
        int unmatchedNodes,
        String resultColumn,
        List<String> derivedColumns,
        List<String> diagnostics,
        boolean cancelled) {

    public MassqlRunSummary {
        derivedColumns = derivedColumns == null ? List.of() : List.copyOf(derivedColumns);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** The result of a run the user stopped: nothing was written. */
    public static MassqlRunSummary ofCancellation() {
        return new MassqlRunSummary(0, 0, 0, 0, null, List.of(), List.of(), true);
    }
}
