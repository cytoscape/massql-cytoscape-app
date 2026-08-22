# MassQL Cytoscape App

Run a [MassQL](https://mwang87.github.io/MassQueryLanguage_Documentation/) query against a mass
spectra file and apply the results to the node table of a network in Cytoscape 3 desktop, so they
can drive formulas, filters, and visual styles.

Implements [cytoscape/cytoscape#26](https://github.com/cytoscape/cytoscape/issues/26), on top of the
[massql-java](https://github.com/cytoscape/massql-java) SDK.

## Requirements

- Cytoscape 3.10 or later
- Java 17

## Building

The `Makefile` is the only supported entry point — never invoke `./gradlew` directly.

```
make build             # the OSGi bundle jar
make test              # unit tier
make integration-test  # everything: both tiers, the assembled-bundle smoke test, and lint
make install-app       # drop the bundle into a running Cytoscape, which hot-installs it
```

`make help` lists every target.

## Status

Early development. The build skeleton and packaging contract are in place; the query dialog,
node-table columns, `MASSQL_PARSE` equation function, and `massql run` command are not yet
implemented.
