package org.cytoscape.massql.app.run;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.massql.result.ScanInfoResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultAttributeTest {

    private static ScanInfoResult row(Double basePeakI, Double tic) {
        return new ScanInfoResult(
                576, 161.0209, null, 0.0, 1, tic, 2, basePeakI, 162.1122, null, null, null);
    }

    @Test
    void offersOnlyTheEightMeasuredFieldsForDerivedColumns() {
        List<String> names =
                ResultAttribute.derivableAttributes().stream()
                        .map(ResultAttribute::jsonName)
                        .toList();

        assertEquals(
                List.of(
                        "precmz",
                        "rt",
                        "tic",
                        "base_peak_i",
                        "base_peak_mz",
                        "ms1_i",
                        "ms1_precmz",
                        "ms1_base_peak_i"),
                names);
    }

    @Test
    void identifiersAndCategoriesAreNotDerivable() {
        for (String name : List.of("scan", "ms1scan", "charge", "mslevel")) {
            assertTrue(
                    !ResultAttribute.byJsonName(name).derivable(), name + " must not be offered");
        }
    }

    @Test
    void zeroIsAValueButAbsenceIsNot() {
        assertEquals(0.0, ResultAttribute.BASE_PEAK_I.extract(row(0.0, 1.0)));
        assertNull(ResultAttribute.BASE_PEAK_I.extract(row(null, 1.0)));
    }

    @Test
    void nonFiniteMeasurementsBecomeAbsent() {
        assertNull(ResultAttribute.TIC.extract(row(1.0, Double.NaN)));
        assertNull(ResultAttribute.TIC.extract(row(1.0, Double.POSITIVE_INFINITY)));
    }

    @Test
    void integerFieldsWidenForTheEquationEngine() {
        assertEquals(576.0, ResultAttribute.SCAN.extract(row(1.0, 1.0)));
    }

    @Test
    void anUnknownNameHasNoAttribute() {
        assertNull(ResultAttribute.byJsonName("base_peak"));
    }
}
