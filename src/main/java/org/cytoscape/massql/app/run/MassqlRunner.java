package org.cytoscape.massql.app.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.massql.ExecutionResult;
import org.cytoscape.massql.Massql;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.io.SpectraFile;
import org.cytoscape.massql.io.SpectraStream;
import org.cytoscape.massql.lang.ast.MassqlQuery;
import org.cytoscape.massql.result.ScanInfoResult;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskMonitor;

/**
 * Runs a query and writes the results to a node table. Holds no Swing or OSGi concern, so both the
 * dialog and the {@code massql run} command drive the identical code path.
 */
public final class MassqlRunner {

    private final CyServiceRegistrar registrar;

    public MassqlRunner(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    public MassqlRunSummary run(
            MassqlRunRequest request, TaskMonitor monitor, BooleanSupplier cancelled) {

        MassqlQuery query = Massql.parse(request.queryText());

        if (monitor != null) {
            monitor.setStatusMessage("Reading " + request.file().getFileName());
            monitor.setProgress(-1);
        }

        ExecutionResult execution;
        try (SpectraStream stream =
                new ProgressSpectraStream(SpectraFile.open(request.file()), monitor, cancelled)) {
            execution = Massql.executeWithDiagnostics(query, stream, request.options());
        }

        // Cancelling has to leave the table exactly as it was; a half-applied query is worse than
        // no query, because nothing in the table says which rows are stale.
        if (cancelled != null && cancelled.getAsBoolean()) {
            return MassqlRunSummary.ofCancellation();
        }

        List<ScanInfoResult> rows = execution.rows();
        Map<Long, ScanInfoResult> byScan = new HashMap<>();
        int duplicates = 0;
        for (ScanInfoResult row : rows) {
            if (row.scan() == null) {
                continue;
            }
            if (byScan.put(Long.valueOf(row.scan().longValue()), row) != null) {
                duplicates++;
            }
        }

        CyTable nodeTable = request.network().getDefaultNodeTable();
        Map<CyRow, ScanInfoResult> hits = new LinkedHashMap<>();
        int matched = 0;
        int unmatched = 0;
        for (CyNode node : request.network().getNodeList()) {
            CyRow nodeRow = request.network().getRow(node);
            Long scan = scanOf(nodeRow, request.scanColumn());
            ScanInfoResult hit = scan == null ? null : byScan.get(scan);
            hits.put(nodeRow, hit);
            if (hit == null) {
                unmatched++;
            } else {
                matched++;
            }
        }

        if (monitor != null) {
            monitor.setStatusMessage("Writing " + matched + " matched nodes");
        }

        // Anything this query name wrote last time that it will not write now describes a query
        // the user has replaced, so it goes before a single cell is written.
        NodeColumns.removeStaleColumns(
                nodeTable, request.queryName(), columnsThisRunWrites(request));

        String resultColumn = null;
        if (request.createResultColumn()) {
            resultColumn = NodeColumns.resultColumn(request.queryName());
            NodeColumns.ensure(nodeTable, resultColumn, String.class);
            writeColumn(
                    nodeTable,
                    resultColumn,
                    hits,
                    // An unmatched node gets "" rather than null. A null would make the equation
                    // engine abandon MASSQL_PARSE before calling it and mark the cell as an error;
                    // an empty string reaches the function, which answers with a blank cell.
                    hit -> hit == null ? "" : ResultJsonCodec.toJson(hit));
        }

        List<String> derived = new ArrayList<>();
        for (ResultAttribute attribute : request.deriveAttributes()) {
            String name = NodeColumns.derivedColumn(request.queryName(), attribute);
            NodeColumns.ensure(nodeTable, name, Double.class);
            writeColumn(nodeTable, name, hits, hit -> hit == null ? null : attribute.extract(hit));
            derived.add(name);
        }

        writeQueryMarker(nodeTable, request.queryName(), hits);

        return new MassqlRunSummary(
                rows.size(),
                duplicates,
                matched,
                unmatched,
                resultColumn,
                derived,
                execution.diagnostics(),
                false);
    }

    /** Every column name this request will write, result column and derived alike. */
    private static Set<String> columnsThisRunWrites(MassqlRunRequest request) {
        Set<String> names = new LinkedHashSet<>();
        if (request.createResultColumn()) {
            names.add(NodeColumns.resultColumn(request.queryName()));
        }
        for (ResultAttribute attribute : request.deriveAttributes()) {
            names.add(NodeColumns.derivedColumn(request.queryName(), attribute));
        }
        return names;
    }

    /**
     * Records this query's name against the nodes it matched, and takes it off the nodes it did
     * not, so the column always answers which queries a node matches now.
     *
     * <p>Written separately from {@link #writeColumn} because the value of a cell depends on the
     * cell: the new list is the old one plus or minus this name. Only rows that actually change are
     * touched -- a query matching thirty scans in a twenty-thousand node network should write
     * thirty rows.
     */
    private void writeQueryMarker(
            CyTable table, String queryName, Map<CyRow, ScanInfoResult> hits) {

        NodeColumns.ensureQueriesColumn(table);

        CyEventHelper events = registrar.getService(CyEventHelper.class);
        List<RowSetRecord> changed = new ArrayList<>();

        events.silenceEventSource(table);
        try {
            for (Map.Entry<CyRow, ScanInfoResult> entry : hits.entrySet()) {
                CyRow row = entry.getKey();
                List<String> current = row.getList(NodeColumns.QUERIES_COLUMN, String.class);
                List<String> updated = withQuery(current, queryName, entry.getValue() != null);
                if (updated == null) {
                    continue;
                }
                row.set(NodeColumns.QUERIES_COLUMN, updated);
                changed.add(new RowSetRecord(row, NodeColumns.QUERIES_COLUMN, updated, updated));
            }
        } finally {
            events.unsilenceEventSource(table);
        }
        events.fireEvent(new RowsSetEvent(table, changed));
    }

    /**
     * The list this row should hold, or null when it already holds it.
     *
     * @param current what the row holds now, null when the cell has never been written
     * @param matched whether this query matched the row
     */
    static List<String> withQuery(List<String> current, String queryName, boolean matched) {
        List<String> names = current == null ? List.of() : current;
        if (matched == names.contains(queryName)) {
            return null;
        }
        List<String> updated = new ArrayList<>(names);
        if (matched) {
            updated.add(queryName);
        } else {
            updated.remove(queryName);
        }
        return updated;
    }

    /**
     * The scan column may be typed Integer, Long or String depending on how the network was
     * imported, and the same number means the same scan in all three.
     */
    static Long scanOf(CyRow row, String column) {
        Object raw = row.getTable().getColumn(column) == null ? null : row.getRaw(column);
        if (raw == null) {
            raw = row.get(column, Object.class);
        }
        if (raw instanceof Number number) {
            return Long.valueOf(number.longValue());
        }
        if (raw instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException notAScanNumber) {
                return null;
            }
        }
        return null;
    }

    /**
     * Writes one column in a single burst. Left to itself Cytoscape emits a row-set event per cell
     * and repaints the table between them, so a large network would crawl; silencing the table and
     * firing one coalesced event afterwards keeps every listener correct without that cost.
     */
    private <T> void writeColumn(
            CyTable table,
            String column,
            Map<CyRow, ScanInfoResult> hits,
            java.util.function.Function<ScanInfoResult, T> valueOf) {

        CyEventHelper events = registrar.getService(CyEventHelper.class);
        List<RowSetRecord> changed = new ArrayList<>(hits.size());

        events.silenceEventSource(table);
        try {
            for (Map.Entry<CyRow, ScanInfoResult> entry : hits.entrySet()) {
                T value = valueOf.apply(entry.getValue());
                entry.getKey().set(column, value);
                changed.add(new RowSetRecord(entry.getKey(), column, value, value));
            }
        } finally {
            events.unsilenceEventSource(table);
        }
        events.fireEvent(new RowsSetEvent(table, changed));
    }
}
