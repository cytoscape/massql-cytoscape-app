package org.cytoscape.massql.app.command;

/**
 * The published identity and documentation of {@code massql run}.
 *
 * <p>Held here rather than inline in the activator so a test can assert the example matches what
 * the command actually returns. A caller scripts against that example; one that has drifted from
 * the result model is worse than none at all.
 */
public final class MassqlRunCommand {

    public static final String NAMESPACE = "massql";
    public static final String NAME = "run";

    public static final String DESCRIPTION =
            "Run a MassQL query over mass spectra and apply the matching scans to a network's node"
                    + " table";

    public static final String LONG_DESCRIPTION =
            "Runs a MassQL-compliant query against a mass spectrometry peak list and writes the"
                    + " matching scans onto the nodes of a Cytoscape network. The query is the scaninfo"
                    + " subset of MassQL (QUERY scaninfo(MS1DATA|MS2DATA) WHERE ... [FILTER ...]), so"
                    + " it can select on fragment and precursor m/z, neutral loss, retention time, scan"
                    + " range, charge and polarity. Input may be an .mgf peak list, which carries MS2"
                    + " fragmentation spectra only, or an .mzML or .mzXML file, which also carries the"
                    + " MS1 survey scans needed to report precursor intensity. Each matching scan is"
                    + " joined to the node whose scan-number column holds that scan's number, and the"
                    + " results are written as a full JSON column, as individual numeric columns, or"
                    + " both. Compare matchedNodes against unmatchedNodes in the result to check that"
                    + " the peak list and the network agree on how scans are numbered.";

    /** A complete instance of the result model, not a fragment. */
    public static final String EXAMPLE_JSON =
            "{\"resultRows\":57,"
                    + "\"duplicateScans\":0,"
                    + "\"matchedNodes\":42,"
                    + "\"unmatchedNodes\":9,"
                    + "\"resultColumn\":\"MASSQL::hexose_loss\","
                    + "\"derivedColumns\":[\"MASSQL::hexose_loss_base_peak_i\"],"
                    + "\"diagnostics\":[],"
                    + "\"cancelled\":false}";

    private MassqlRunCommand() {}
}
