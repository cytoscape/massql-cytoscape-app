package org.cytoscape.massql.app;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.MassqlOptions;
import org.cytoscape.massql.app.run.NodeColumns;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyNetwork;

/**
 * One request to run a query and write its results to a network's node table.
 *
 * @param file the peak list -- .mgf, .mzML or .mzXML
 * @param queryText the MassQL query, unvalidated here; {@code Massql.parse} is the authority
 * @param queryName names the columns this run writes
 * @param scanColumn the node-table column holding each node's scan number
 * @param createResultColumn whether to write the full JSON result column
 * @param deriveAttributes the attributes to write as numeric columns; may be empty
 * @param precursorTolPpm the MS1 precursor lookup window
 * @param network the network whose node table receives the columns
 */
public record MassqlRunRequest(
        Path file,
        String queryText,
        String queryName,
        String scanColumn,
        boolean createResultColumn,
        List<ResultAttribute> deriveAttributes,
        double precursorTolPpm,
        CyNetwork network) {

    public MassqlRunRequest {
        require(file != null, "no peak list file was chosen");
        require(queryText != null && !queryText.isBlank(), "the query is empty");
        require(network != null, "there is no network to write to");
        require(scanColumn != null && !scanColumn.isBlank(), "no scan column was chosen");
        require(queryName != null && !queryName.isBlank(), "the query name is empty");
        require(
                !queryName.contains(":"),
                "the query name may not contain ':' -- it separates a column's namespace from its"
                        + " name");
        require(
                !NodeColumns.isReservedQueryName(queryName),
                "'"
                        + queryName
                        + "' is reserved: "
                        + NodeColumns.QUERIES_COLUMN
                        + " lists the queries each node matches. Choose another name.");
        require(
                precursorTolPpm > 0 && Double.isFinite(precursorTolPpm),
                "the precursor tolerance must be a positive number of ppm");

        deriveAttributes = deriveAttributes == null ? List.of() : dedupe(deriveAttributes);

        // A run that writes neither the JSON column nor any numeric column does nothing at all;
        // failing here beats leaving the user to notice an unchanged table.
        require(
                createResultColumn || !deriveAttributes.isEmpty(),
                "select the full result column, at least one attribute, or both -- otherwise the run"
                        + " writes nothing");

        for (ResultAttribute a : deriveAttributes) {
            require(
                    a.derivable(),
                    "'"
                            + a.jsonName()
                            + "' identifies a spectrum rather than measuring it, so no"
                            + " numeric column is derived from it");
        }
    }

    public MassqlOptions options() {
        return MassqlOptions.defaults().withPrecursorTolPpm(precursorTolPpm);
    }

    private static List<ResultAttribute> dedupe(List<ResultAttribute> attrs) {
        Set<ResultAttribute> seen = new LinkedHashSet<>();
        for (ResultAttribute a : attrs) {
            if (a != null) {
                seen.add(a);
            }
        }
        return List.copyOf(seen);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new MassqlException(message);
        }
    }
}
