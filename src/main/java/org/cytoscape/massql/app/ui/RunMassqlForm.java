package org.cytoscape.massql.app.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.cytoscape.massql.MassqlOptions;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.run.NodeColumns;
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
     * Preselects a column named "scan" -- the GNPS convention -- and otherwise the first candidate,
     * so the form always holds whatever the dialog is showing.
     */
    private static String defaultScanColumn(CyNetwork network) {
        List<CyColumn> candidates = scanColumnCandidates(network);
        for (CyColumn column : candidates) {
            if ("scan".equalsIgnoreCase(column.getNameOnly())) {
                return column.getName();
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0).getName();
    }

    /**
     * Whether the chosen file carries MS1 survey scans. mzML and mzXML do; an MGF holds
     * fragmentation spectra alone.
     *
     * <p>Governs both the precursor tolerance, which is an MS1 lookup window, and the three
     * attributes measured in an MS1 scan.
     *
     * <p>Decided on the file name: the engine identifies a format by reading it, but exposes no way
     * to ask, and this only drives which controls are offered.
     */
    public boolean fileCarriesMs1() {
        return file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".mgf");
    }

    /** Whether {@code attribute} can be derived from the chosen file. */
    public boolean applies(ResultAttribute attribute) {
        return fileCarriesMs1() || !attribute.requiresMs1();
    }

    /** A field of the dialog, so a rejected value can be pointed at. */
    public enum Field {
        FILE,
        QUERY_NAME,
        QUERY_TEXT,
        COLUMNS
    }

    /** Something the user has to put right, and where. */
    public record Problem(Field field, String message) {}

    /**
     * Whether every required field has been given a value.
     *
     * <p>Drives Apply, so it asks only whether something is there -- checking that the file exists
     * or that the name is legal belongs to {@link #validate()}, after the user has said they are
     * finished.
     */
    public boolean isComplete() {
        return file != null
                && !queryName.isBlank()
                && !queryText.isBlank()
                && scanColumn != null
                && (createResultColumn || deriveAttributes.stream().anyMatch(this::applies));
    }

    /** What is wrong with the values entered, or null when they are usable. */
    public Problem validate() {
        if (file == null) {
            return new Problem(Field.FILE, "Choose a peak list file.");
        }
        if (!file.isFile()) {
            return new Problem(Field.FILE, "No file at " + file.getPath());
        }
        if (queryName.isBlank()) {
            return new Problem(Field.QUERY_NAME, "Name the query -- it names the columns written.");
        }
        if (queryName.contains(":")) {
            return new Problem(Field.QUERY_NAME, "The query name may not contain ':'.");
        }
        if (NodeColumns.isReservedQueryName(queryName)) {
            return new Problem(
                    Field.QUERY_NAME,
                    "'"
                            + queryName.trim()
                            + "' is reserved: "
                            + NodeColumns.QUERIES_COLUMN
                            + " lists the queries each node matches.");
        }
        if (queryText.isBlank()) {
            return new Problem(Field.QUERY_TEXT, "Enter a MassQL query.");
        }
        if (!createResultColumn && deriveAttributes.stream().noneMatch(this::applies)) {
            return new Problem(Field.COLUMNS, "Choose at least one column to add.");
        }
        return null;
    }

    /** The request these fields describe. Meaningful once {@link #validate()} passes. */
    public MassqlRunRequest toRequest() {
        return new MassqlRunRequest(
                file.toPath(),
                queryText,
                queryName.trim(),
                scanColumn,
                createResultColumn,
                deriveAttributes.stream().filter(this::applies).toList(),
                fileCarriesMs1() ? precursorTolPpm : MassqlOptions.DEFAULT_PRECURSOR_TOL_PPM,
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
