package org.cytoscape.massql.app.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.MassqlOptions;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.app.run.MassqlRunner;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.json.JSONResult;

/**
 * The {@code massql run} command.
 *
 * <p>Builds the same request the dialog does and hands it to the same {@link MassqlRunner}, so a
 * scripted run and a clicked one cannot drift apart.
 */
public class MassqlCommandTask extends AbstractTask implements ObservableTask {

    @Tunable(
            description = "Peak list data file",
            longDescription =
                    "Path to the mass spectra file to query: an .mgf peak list, which carries MS2 "
                            + "fragmentation spectra only, or an .mzML or .mzXML file, which also "
                            + "carries the MS1 survey scans needed to report precursor intensity. "
                            + "The format is detected by reading the file, not from its extension.",
            exampleStringValue = "/data/gnps/yeast_peaks.mgf",
            required = true,
            context = "nogui")
    public File file;

    @Tunable(
            description = "MassQL query",
            longDescription =
                    "The query to run, in the scaninfo subset of MassQL: "
                            + "QUERY scaninfo(MS1DATA|MS2DATA) WHERE ... [FILTER ...]. Conditions "
                            + "may select on fragment and precursor m/z, neutral loss, retention "
                            + "time, scan range, charge and polarity.",
            exampleStringValue = "QUERY scaninfo(MS2DATA) WHERE MS2PROD=144.1019:TOLERANCEPPM=20",
            required = true,
            context = "nogui")
    public String query;

    @Tunable(
            description = "Query name",
            longDescription =
                    "Names the node-table columns this run writes: MASSQL::<name> for the full "
                            + "JSON result, and MASSQL::<name>_<attribute> for each derived number. "
                            + "Re-running with the same name overwrites those columns. May not "
                            + "contain ':'.",
            exampleStringValue = "hexose_loss",
            required = true,
            context = "nogui")
    public String name;

    @Tunable(
            description = "Scan number column",
            longDescription =
                    "The node-table column holding each node's scan number, which is what results "
                            + "are joined on. May be an Integer, Long or String column.",
            exampleStringValue = "scan",
            required = true,
            context = "nogui")
    public String scanColumn;

    @Tunable(
            description = "Network",
            longDescription =
                    "The network whose node table receives the columns. Defaults to the current "
                            + "network.",
            exampleStringValue = "current",
            context = "nogui")
    public CyNetwork network;

    @Tunable(
            description = "Write the full JSON result column",
            longDescription =
                    "Whether to write MASSQL::<name>, holding each matched node's complete result "
                            + "as JSON. Nodes that matched nothing are left empty.",
            exampleStringValue = "true",
            context = "nogui")
    public boolean resultColumn = true;

    @Tunable(
            description = "Attributes to write as numeric columns",
            longDescription =
                    "Comma-separated result attributes to write as Double columns, one each. "
                            + "Accepts precmz, rt, tic, base_peak_i, base_peak_mz, ms1_i, "
                            + "ms1_precmz and ms1_base_peak_i. A node that matched nothing, or a "
                            + "value the instrument did not record, is left empty rather than "
                            + "written as zero.",
            exampleStringValue = "base_peak_i,tic",
            context = "nogui")
    public String deriveColumns = "";

    @Tunable(
            description = "Precursor tolerance (ppm)",
            longDescription =
                    "The window used to find a precursor in its MS1 survey scan, in parts per "
                            + "million. Defaults to 20.0. Has no effect on an .mgf, which carries "
                            + "no MS1 scans.",
            exampleStringValue = "20.0",
            context = "nogui")
    public double precursorTolPpm = MassqlOptions.DEFAULT_PRECURSOR_TOL_PPM;

    private final CyServiceRegistrar registrar;
    private MassqlRunSummary summary;

    public MassqlCommandTask(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public void run(TaskMonitor monitor) {
        monitor.setTitle("Run MassQL");
        summary = new MassqlRunner(registrar).run(toRequest(), monitor, () -> cancelled);
    }

    /** Package-private so a test can build the request without running it. */
    MassqlRunRequest toRequest() {
        CyNetwork target = network != null ? network : currentNetwork();
        if (target == null) {
            throw new MassqlException(
                    "there is no current network to write to; pass network=<name or SUID>");
        }
        return new MassqlRunRequest(
                file == null ? null : file.toPath(),
                query,
                name,
                scanColumn,
                resultColumn,
                parseAttributes(deriveColumns),
                precursorTolPpm,
                target);
    }

    private CyNetwork currentNetwork() {
        CyApplicationManager applications = registrar.getService(CyApplicationManager.class);
        return applications == null ? null : applications.getCurrentNetwork();
    }

    /**
     * A name that is unknown, or that identifies a spectrum rather than measuring it, is rejected
     * by name -- a caller who asked for "scan" should be told why it is not on offer, not handed a
     * silently shorter list of columns.
     */
    static List<ResultAttribute> parseAttributes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<ResultAttribute> attributes = new ArrayList<>();
        for (String token : csv.split(",")) {
            String jsonName = token.trim();
            if (jsonName.isEmpty()) {
                continue;
            }
            ResultAttribute attribute = ResultAttribute.byJsonName(jsonName);
            if (attribute == null || !attribute.derivable()) {
                throw new MassqlException(
                        "'"
                                + jsonName
                                + "' is not an attribute a numeric column can be derived from."
                                + " Accepted: "
                                + derivableNames()
                                + ".");
            }
            attributes.add(attribute);
        }
        return attributes;
    }

    static String derivableNames() {
        return ResultAttribute.derivableAttributes().stream()
                .map(ResultAttribute::jsonName)
                .collect(Collectors.joining(", "));
    }

    @Override
    public List<Class<?>> getResultClasses() {
        return List.of(String.class, JSONResult.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R getResults(Class<? extends R> type) {
        if (summary == null) {
            return null;
        }
        if (JSONResult.class.equals(type)) {
            JSONResult result = () -> SummaryJson.of(summary);
            return (R) result;
        }
        if (String.class.equals(type)) {
            return (R) SummaryJson.of(summary);
        }
        return null;
    }
}
