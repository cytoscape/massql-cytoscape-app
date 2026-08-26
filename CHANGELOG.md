# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.2] - 2026-08-25

### Fixed

- Apply now enables as soon as every field in the Run MassQL dialog holds a value. The scan-column
  combo selects an entry on opening, and the dialog had been keeping its own record of that choice,
  so a fully filled form could sit behind a disabled button.

### Changed

- The dialog judges what was entered when Apply is pressed, reporting one problem at a time and
  putting the cursor in the field it came from — a query that fails to parse lands the caret at the
  position reported.
- The `ms1_i`, `ms1_precmz` and `ms1_base_peak_i` checkboxes are offered for the formats that
  measure them, mzML and mzXML.

## [0.0.1] - 2026-08-24

### Added

- Inaugural release of the **MassQL Cytoscape app**. Runs a MassQL `scaninfo` query against an
  `.mgf`, `.mzML` or `.mzXML` peak list and applies the matching scans to a network's node table,
  joined on scan number.
  - Launches from **Apps ▸ Run MassQL…** and from the Network panel's right-click menu.
  - Writes each matched node's full result as JSON in `MASSQL::<query name>`, and any of eight
    measured attributes as `Double` columns ready to bind to a visual style.
  - Adds `MASSQL_PARSE(massql_result, attribute)` to the Formula Builder, for deriving values from
    the JSON column.
  - Publishes `massql run` to the Automation panel.
  - Built on [massql-java](https://github.com/cytoscape/massql-java) 0.0.3.

