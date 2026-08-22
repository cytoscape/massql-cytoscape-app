package org.cytoscape.massql.app.run;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.cytoscape.massql.result.ScanInfoResult;

/** Serializes one result row to the JSON string stored in the node table. */
public final class ResultJsonCodec {

    /**
     * {@code serializeNulls} is not optional: without it Gson drops every null field, so an MGF row
     * would emit eight keys instead of twelve and the column's shape would vary by input format.
     */
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ResultJsonCodec() {}

    public static String toJson(ScanInfoResult row) {
        return GSON.toJson(row);
    }
}
