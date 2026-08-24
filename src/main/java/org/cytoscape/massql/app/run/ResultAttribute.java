package org.cytoscape.massql.app.run;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.cytoscape.massql.result.ScanInfoResult;

/**
 * The twelve fields of a {@code scaninfo} result row, in the order the SDK serializes them.
 *
 * <p>One source of truth for three consumers: the checkbox list in the dialog, the numeric columns
 * the run writes, and {@code MASSQL_PARSE}'s validation of an attribute name.
 */
public enum ResultAttribute {
    SCAN("scan", false, false, r -> widen(r.scan())),
    PRECMZ("precmz", true, false, ScanInfoResult::precmz),
    MS1SCAN("ms1scan", false, true, r -> widen(r.ms1scan())),
    RT("rt", true, false, ScanInfoResult::rt),
    CHARGE("charge", false, false, r -> widen(r.charge())),
    TIC("tic", true, false, ScanInfoResult::tic),
    MSLEVEL("mslevel", false, false, r -> widen(r.mslevel())),
    BASE_PEAK_I("base_peak_i", true, false, ScanInfoResult::basePeakI),
    BASE_PEAK_MZ("base_peak_mz", true, false, ScanInfoResult::basePeakMz),
    MS1_I("ms1_i", true, true, ScanInfoResult::ms1I),
    MS1_PRECMZ("ms1_precmz", true, true, ScanInfoResult::ms1Precmz),
    MS1_BASE_PEAK_I("ms1_base_peak_i", true, true, ScanInfoResult::ms1BasePeakI);

    private final String jsonName;
    private final boolean derivable;
    private final boolean requiresMs1;
    private final Function<ScanInfoResult, Double> extractor;

    ResultAttribute(
            String jsonName,
            boolean derivable,
            boolean requiresMs1,
            Function<ScanInfoResult, Double> extractor) {
        this.jsonName = jsonName;
        this.derivable = derivable;
        this.requiresMs1 = requiresMs1;
        this.extractor = extractor;
    }

    /** The key this field carries in the result JSON, and the name a user types. */
    public String jsonName() {
        return jsonName;
    }

    /**
     * Whether a numeric column may be derived from this field. {@code scan}, {@code ms1scan},
     * {@code charge} and {@code mslevel} identify or categorise a spectrum rather than measure it,
     * so binding a continuous mapping to one is meaningless; they stay reachable through the JSON
     * column.
     */
    public boolean derivable() {
        return derivable;
    }

    /**
     * Whether this value is measured in an MS1 survey scan, and so is reported for the formats that
     * carry one -- {@code .mzML} and {@code .mzXML}.
     */
    public boolean requiresMs1() {
        return requiresMs1;
    }

    /** The value for a matched row, or null where the field is absent or not finite. */
    public Double extract(ScanInfoResult row) {
        return clean(extractor.apply(row));
    }

    /** The attributes a numeric column may be derived from, in schema order. */
    public static List<ResultAttribute> derivableAttributes() {
        return Stream.of(values()).filter(ResultAttribute::derivable).toList();
    }

    /** The attribute with this JSON key, or null if no field carries it. */
    public static ResultAttribute byJsonName(String name) {
        for (ResultAttribute a : values()) {
            if (a.jsonName.equals(name)) {
                return a;
            }
        }
        return null;
    }

    private static Double widen(Integer v) {
        return v == null ? null : Double.valueOf(v.doubleValue());
    }

    /**
     * A blank cell has to mean "no data" and nothing else, so NaN and infinity are folded into null
     * rather than written as numbers a mapping would try to scale.
     */
    private static Double clean(Double v) {
        return v == null || v.isNaN() || v.isInfinite() ? null : v;
    }
}
