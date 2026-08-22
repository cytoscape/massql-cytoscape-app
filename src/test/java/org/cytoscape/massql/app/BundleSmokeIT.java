package org.cytoscape.massql.app;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.massql.Massql;
import org.cytoscape.massql.result.ScanInfoResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runs against the ASSEMBLED bundle jar, not the loose build output — see the {@code
 * bundleSmokeTest} task.
 *
 * <p>The jar strips {@code META-INF/versions/**} so Felix will resolve it, which silently removes
 * the multi-release entries gson and ANTLR ship. Whether either of them needs those at run time is
 * only answerable by executing a real query out of the packaged jar.
 */
class BundleSmokeIT {

    @Test
    void aRealQueryRunsFromInsideTheAssembledBundle() {
        Path mgf = TestFixtures.require("fixtures/micro/micro.mgf");

        List<ScanInfoResult> rows =
                Massql.run(
                        "QUERY scaninfo(MS2DATA) WHERE MS2PROD=200.5:TOLERANCEMZ=0.5", mgf, null);

        assertNotNull(rows);
        assertFalse(
                rows.isEmpty(), "the fixture must match, or this proves nothing about packaging");
    }
}
