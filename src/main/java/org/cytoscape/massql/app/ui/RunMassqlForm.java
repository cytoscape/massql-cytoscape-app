package org.cytoscape.massql.app.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.cytoscape.massql.MassqlOptions;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;

/**
 * The state behind the Run MassQL dialog, and the rules that decide whether it can be applied.
 *
 * <p>Separated from the dialog so the rules can be tested without a display: which columns may hold
 * a scan number, when Apply is allowed, and what request the fields add up to.
 */
public final class RunMassqlForm {

    /** Scan numbers arrive as whole numbers or as text; nothing else can be joined on. */
    private static final Set<Class<?>> SCAN_COLUMN_TYPES =
            Set.of(Integer.class, Long.class, String.class);

    private final CyNetwork network;

    private File file;
    private String queryName = "";
    private String queryText = "";
    private String scanColumn;
    private boolean createResultColumn = true;
    private double precursorTolPpm = MassqlOptions.DEFAULT_PRECURSOR_TOL_PPM;
    private final Set<ResultAttribute> deriveAttributes = new LinkedHashSet<>();

    public RunMassqlForm(CyNetwork network) {
        this.network = network;
        this.scanColumn = defaultScanColumn(network);
    }

    /**
     * Columns that could hold a scan number. The primary key is excluded: it is a SUID Cytoscape
     * assigns, unrelated to anything an instrument produced.
     */
    public static List<CyColumn> scanColumnCandidates(CyNetwork network) {
        CyTable table = network.getDefaultNodeTable();
        List<CyColumn> candidates = new ArrayList<>();
        for (CyColumn column : table.getColumns()) {
            if (!column.isPrimaryKey() && SCAN_COLUMN_TYPES.contains(column.getType())) {
                candidates.add(column);
            }
        }
        return candidates;
    }

    /**
     * Preselects a column named "scan" -- the GNPS convention -- and otherwise selects nothing.
     *
     * <p>Deliberately not "the first candidate": every node table carries {@code name} and {@code
     * shared name}, so a first-match default would silently land on one of those. That produces a
     * run which completes, writes a column, and matches nothing, with no indication that the wrong
     * key was joined on. Better to make the user say which column it is.
     */
    private static String defaultScanColumn(CyNetwork network) {
        for (CyColumn column : scanColumnCandidates(network)) {
            if ("scan".equalsIgnoreCase(column.getNameOnly())) {
                return column.getName();
            }
        }
        return null;
    }

    /**
     * Whether the precursor tolerance has any effect. It governs the MS1 survey-scan lookup, and an
     * MGF carries only fragmentation spectra, so for one the field is inert.
     *
     * <p>Decided on the file name: the engine identifies a format by reading it, but exposes no way
     * to ask, and this only drives whether a field is greyed out.
     */
    public boolean precursorToleranceApplies() {
        return file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".mgf");
    }

    /** Why Apply is disabled, or null when it is allowed. */
    public String whyNotReady() {
        if (file == null) {
            return "Choose a peak list file.";
        }
        if (!file.isFile()) {
            return "That peak list file does not exist.";
        }
        if (queryName.isBlank()) {
            return "Name the query -- it names the columns this run writes.";
        }
        if (queryName.contains(":")) {
            return "The query name may not contain ':'.";
        }
        if (queryText.isBlank()) {
            return "Enter a MassQL query.";
        }
        if (scanColumn == null) {
            return scanColumnCandidates(network).isEmpty()
                    ? "This network has no column that could hold a scan number."
                    : "Choose the node column that holds each node's scan number.";
        }
        if (!createResultColumn && deriveAttributes.isEmpty()) {
            return "Choose at least one column to add.";
        }
        return null;
    }

    public boolean isReady() {
        return whyNotReady() == null;
    }

    /** The request these fields describe. Only meaningful once {@link #isReady()}. */
    public MassqlRunRequest toRequest() {
        return new MassqlRunRequest(
                file.toPath(),
                queryText,
                queryName.trim(),
                scanColumn,
                createResultColumn,
                List.copyOf(deriveAttributes),
                precursorToleranceApplies()
                        ? precursorTolPpm
                        : MassqlOptions.DEFAULT_PRECURSOR_TOL_PPM,
                network);
    }

    public CyNetwork network() {
        return network;
    }

    public File file() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String queryName() {
        return queryName;
    }

    public void setQueryName(String queryName) {
        this.queryName = queryName == null ? "" : queryName;
    }

    public String queryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText == null ? "" : queryText;
    }

    public String scanColumn() {
        return scanColumn;
    }

    public void setScanColumn(String scanColumn) {
        this.scanColumn = scanColumn;
    }

    public boolean createResultColumn() {
        return createResultColumn;
    }

    public void setCreateResultColumn(boolean createResultColumn) {
        this.createResultColumn = createResultColumn;
    }

    public double precursorTolPpm() {
        return precursorTolPpm;
    }

    public void setPrecursorTolPpm(double precursorTolPpm) {
        this.precursorTolPpm = precursorTolPpm;
    }

    public Set<ResultAttribute> deriveAttributes() {
        return Set.copyOf(deriveAttributes);
    }

    public void setDerived(ResultAttribute attribute, boolean derive) {
        if (derive) {
            deriveAttributes.add(attribute);
        } else {
            deriveAttributes.remove(attribute);
        }
    }
}
