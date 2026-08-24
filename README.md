# MassQL Cytoscape App

Run a [MassQL](https://mwang87.github.io/MassQueryLanguage_Documentation/) query against a mass
spectra file and apply the matching scans to a network's node table in Cytoscape 3 desktop, where
they can drive formulas, filters and visual styles.

Implements [cytoscape/cytoscape#26](https://github.com/cytoscape/cytoscape/issues/26) on top of the
[massql-java](https://github.com/cytoscape/massql-java) SDK.

## Requirements

- Cytoscape 3.10 or later
- Java 17

## Installing

Two options:

### App Store
Install **MassQL** from the [Cytoscape App Store](https://apps.cytoscape.org/).

### Direct download
For a specific version, download the app jar from
[Releases](https://github.com/cytoscape/massql-cytoscape-app/releases), then in Cytoscape choose
**Apps ▸ App Store ▸ Install Apps From File**, select the jar, and restart if prompted.

---

## Running a query

Open a network, then choose **Apps ▸ Run MassQL…**, or right-click the network in the Network panel
and choose **Run MassQL…**.

### Peak list data file

Choose the file the query runs against. Three formats are read, and the difference between them
decides which results you can get:

| Format | Contains | Consequence |
|---|---|---|
| `.mgf` | MS2 fragmentation spectra only | `ms1scan`, `ms1_i`, `ms1_precmz` and `ms1_base_peak_i` are always empty |
| `.mzML`, `.mzXML` | MS1 survey scans as well as MS2 | precursor intensity is reported |

The format is detected by reading the file, not from its extension. The order spectra appear in the
file does not matter.

### Query name

Names the columns this run writes — `MASSQL::<name>` and `MASSQL::<name>_<attribute>`. Running
again with the same name overwrites those columns, which is how you refine a query. Use a different
name to keep both results side by side. The name may not contain `:`.

### Node column holding the scan number

Each query result row is matched
to the node table row whose column holds the query result scan number. 

**Where the peak list's scan numbers come from.** For an `.mgf`, each spectrum's number is its
`SCANS=` header. **When a file declares no `SCANS=`, the scan number becomes the spectrum's position in the
file instead** — first spectrum 1, second 2, and so on. 

**How to check it worked.** The run reports how many query results matched to network node table rows and how many did not. 

### MassQL query

The `scaninfo` subset of MassQL is supported:

```
QUERY scaninfo(MS1DATA|MS2DATA) WHERE <conditions> [FILTER <conditions>]
```

Conditions may select on `MS2PROD`, `MS2PREC`, `MS2NL`, `MS1MZ`, `RTMIN`, `RTMAX`, `SCANMIN`,
`SCANMAX`, `CHARGE` and `POLARITY`, qualified with `TOLERANCEMZ`, `TOLERANCEPPM`,
`INTENSITYPERCENT`, `INTENSITYTICPERCENT` or `INTENSITYVALUE`.

- **Retention times are in minutes.** `RTMIN`/`RTMAX` and the reported `rt` are minutes.
- **`=` means "at least" for intensity.** `INTENSITYPERCENT=5` matches peaks at 5% *or more*.
- **m/z windows exclude their edges.** A peak exactly on the boundary of `TOLERANCEMZ` does not
  match.
- **`POLARITY` cannot filter an `.mgf`.** MGF carries no polarity, so `POLARITY=Positive` matches
  every spectrum in one and `POLARITY=Negative` matches none, whatever the data actually is.
- **Precursor tolerance has no effect on an `.mgf`.** It governs the MS1 survey-scan lookup, and an
  MGF has no MS1 scans. The field is disabled when MGF chosen.
- **`ms1scan` is inferred from the file's ordering**, not read from its declared precursor
  reference. For straightforward DDA the two agree.

Other MassQL functions — `scansum`, `scannum`, `scanmz` and the rest — are not supported, and the
dialog will warn if attempted.

### Columns are added to the node table

Two independent choices; at least one is required.

**Full result (JSON)** writes `MASSQL::<name>`, holding each matched node's complete result:

```json
{"scan":576,"precmz":161.0209,"ms1scan":null,"rt":0.0,"charge":1,"tic":1299900.0,
 "mslevel":2,"base_peak_i":230000.0,"base_peak_mz":162.1122,"ms1_i":null,
 "ms1_precmz":null,"ms1_base_peak_i":null}
```

**Derived Numeric columns** generate a `Double` column in network node table prefixed by the query name per checked attribute — `MASSQL::<query_name>_tic` and so
on. This enables direct binding in continuous mapping styles. Eight measured attributes are offered: `precmz`, `rt`,
`tic`, `base_peak_i`, `base_peak_mz`, `ms1_i`, `ms1_precmz`, `ms1_base_peak_i`.

**An empty cell means no data.** A node the query did not match is left blank.

---

## Using the results

Numeric columns are ordinary `Double` columns: open **Style**, set a mapping on
`MASSQL::<name>_base_peak_i` and pick Continuous Mapping.

Can also derive values after the query is applied by using the Formula Builder in cytoscape on node table and createa new column and use the **`MASSQL_PARSE`** function published by this app:

```
MASSQL_PARSE(massql_json_result_column, attribute)
```

It reads the attribute out of the JSON column. Because the columns are namespaced, the
column reference needs brace syntax — `${MASSQL::query_name}`, not `$MASSQL::query_name`:

```
=MASSQL_PARSE(${MASSQL::bile_acid}, "base_peak_i")
```

Any of the twelve attributes may be read, and expressions combine as usual — a precursor-to-base-peak
ratio, for instance:

```
=MASSQL_PARSE(${MASSQL::bile_acid}, "ms1_i") / MASSQL_PARSE(${MASSQL::bile_acid}, "base_peak_i")
```

It returns nothing for a node with no result.

---

## Automation

The same run is available to scripting as `massql run`, in Cytoscape's Automation panel, CyREST and
py4cytoscape:

```
massql run file="/data/gnps/yeast_peaks.mgf" \
           query="QUERY scaninfo(MS2DATA) WHERE MS2PROD=144.1019:TOLERANCEPPM=20" \
           name="hexose_loss" \
           scanColumn="name" \
           deriveColumns="base_peak_i,tic"
```

`network` defaults to the current network. `resultColumn` defaults to true. It returns the outcome
as JSON:

```json
{"resultRows":57,"duplicateScans":0,"matchedNodes":42,"unmatchedNodes":9,
 "resultColumn":"MASSQL::hexose_loss",
 "derivedColumns":["MASSQL::hexose_loss_base_peak_i"],
 "diagnostics":[],"cancelled":false}
```

`matchedNodes` against `unmatchedNodes` is the pair to check that the peak list and the network
agree on how scans are numbered.

---

## Building

The `Makefile` is the only supported entry point — never invoke `./gradlew` directly.

```
make build             # the OSGi bundle jar
make test              # unit tier
make integration-test  # everything: both tiers, the assembled-bundle smoke test, and lint
make install-app       # install into a running Cytoscape
```

`make help` lists every target.
