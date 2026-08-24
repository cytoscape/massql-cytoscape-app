# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

