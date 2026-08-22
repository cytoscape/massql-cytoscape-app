package org.cytoscape.massql.app.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.cytoscape.massql.app.MassqlRunSummary;

/**
 * The wire form of a run's outcome, as returned by {@code massql run}.
 *
 * <p>Callers script against these field names, so they are the record's own component names and
 * nothing reshapes them on the way out.
 */
public final class SummaryJson {

    /**
     * {@code resultColumn} is null when the JSON column was not requested, and callers test for
     * that, so nulls are written rather than dropped.
     */
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private SummaryJson() {}

    public static String of(MassqlRunSummary summary) {
        return GSON.toJson(summary);
    }
}
