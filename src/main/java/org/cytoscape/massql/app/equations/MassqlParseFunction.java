package org.cytoscape.massql.app.equations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.equations.AbstractFunction;
import org.cytoscape.equations.ArgDescriptor;
import org.cytoscape.equations.ArgType;
import org.cytoscape.equations.FunctionUtil;
import org.cytoscape.massql.app.run.ResultAttribute;

/**
 * {@code MASSQL_PARSE(massql_result, attribute)} — reads one numeric attribute out of the JSON
 * result object this app writes to a node.
 *
 * <p>The app's own numeric columns hold plain values, so nothing it writes depends on this. It
 * exists for the Formula Builder, where a user combines result attributes into something the
 * checkbox list does not offer — a ratio of precursor to base-peak intensity, say.
 *
 * <p>Absence and zero are different answers. A node the query did not match holds {@code ""} and a
 * field the instrument did not record is JSON {@code null}; both yield Java {@code null}, which
 * Cytoscape renders as an empty cell. A measured {@code 0.0} comes back as {@code 0.0}. Only a
 * genuine mistake — malformed JSON, or an attribute that does not exist — throws, which marks the
 * cell as an error rather than quietly reading as no-data.
 */
public class MassqlParseFunction extends AbstractFunction {

    /**
     * Cytoscape re-evaluates an equation on every read of its cell, so a formula over a large node
     * table re-parses the same handful of JSON strings continuously. Caching the parse keeps that
     * affordable; the bound stops a big table from pinning every row's JSON in memory.
     */
    private static final int CACHE_ENTRIES = 1024;

    private final Map<String, Map<String, Double>> parsed =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) {
                    return size() > CACHE_ENTRIES;
                }
            };

    public MassqlParseFunction() {
        super(
                new ArgDescriptor[] {
                    new ArgDescriptor(
                            ArgType.STRING,
                            "massql_result",
                            "A MassQL result column, referenced as ${MASSQL::query_name}."),
                    new ArgDescriptor(
                            ArgType.STRING,
                            "attribute",
                            "The result attribute to read, e.g. base_peak_i.")
                });
    }

    @Override
    public String getName() {
        return "MASSQL_PARSE";
    }

    @Override
    public String getCategoryName() {
        return "MassQL";
    }

    @Override
    public String getFunctionSummary() {
        return "Returns one numeric attribute of a MassQL result, or nothing if the node had no"
                + " result or the instrument did not record that value.";
    }

    @Override
    public Class<?> getReturnType() {
        return Double.class;
    }

    @Override
    public Object evaluateFunction(Object[] args) {
        if (args[1] == null) {
            throw new IllegalArgumentException(
                    "no attribute was named. Expected one of: " + names());
        }
        String attribute = FunctionUtil.getArgAsString(args[1]);

        // Checked before the cell is even looked at, so a mistyped attribute is reported on every
        // row rather than only on the rows that happened to match.
        if (ResultAttribute.byJsonName(attribute) == null) {
            throw new IllegalArgumentException(
                    "\""
                            + attribute
                            + "\" is not a MassQL result attribute. Expected one of: "
                            + names()
                            + ".");
        }

        // The engine rejects a null column reference before it reaches a function, so this only
        // guards a caller constructing the arguments directly.
        if (args[0] == null) {
            return null;
        }
        String json = FunctionUtil.getArgAsString(args[0]);
        if (json == null || json.isBlank()) {
            return null;
        }

        Map<String, Double> values = valuesOf(json);
        if (!values.containsKey(attribute)) {
            throw new IllegalArgumentException(
                    "this MassQL result carries no \"" + attribute + "\" attribute: " + json);
        }
        return values.get(attribute);
    }

    private Map<String, Double> valuesOf(String json) {
        synchronized (parsed) {
            Map<String, Double> hit = parsed.get(json);
            if (hit != null) {
                return hit;
            }
        }

        Map<String, Double> values = parse(json);

        synchronized (parsed) {
            parsed.put(json, values);
        }
        return values;
    }

    private static Map<String, Double> parse(String json) {
        JsonObject object;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(notAResult(json));
            }
            object = element.getAsJsonObject();
        } catch (RuntimeException notJson) {
            throw new IllegalArgumentException(notAResult(json), notJson);
        }

        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), numberOrNull(entry.getKey(), entry.getValue(), json));
        }
        // Not Map.copyOf: it rejects null values, and a null is exactly what distinguishes "the
        // instrument did not record this" from "this attribute does not exist".
        return Collections.unmodifiableMap(values);
    }

    private static Double numberOrNull(String key, JsonElement value, String json) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "the \"" + key + "\" attribute is not a number in: " + json);
        }
        double d = value.getAsDouble();
        return Double.isFinite(d) ? Double.valueOf(d) : null;
    }

    private static String notAResult(String json) {
        return "this cell does not hold a MassQL result object: " + json;
    }

    private static String names() {
        return Stream.of(ResultAttribute.values())
                .map(ResultAttribute::jsonName)
                .collect(Collectors.joining(", "));
    }
}
