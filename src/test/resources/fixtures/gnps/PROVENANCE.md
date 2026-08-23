# GNPS scan-join fixtures

These two files are a matched pair from the dataset attached to
[cytoscape/cytoscape#26](https://github.com/cytoscape/cytoscape/issues/26). They exist to prove one
thing the app cannot check for a user at run time: that the scan numbers in a peak list and the
scan numbers in the network built from it actually refer to the same spectra.

They must be regenerated together or not at all — trimming one without the other silently breaks
the join the tests assert.

## `plusrize_slice.mgf`

400 spectra taken from `Plusrize_mzmine_spectra.mgf` (the mzmine export attached to the issue) in
the file's own order, with `SCANS=` values untouched. Roughly half its transitions descend, which
is ordinary for an MGF and which massql-java rejected before 0.0.3.

Selected as: every spectrum matched by the `trihydroxy_bile_acid` query below, plus filler sampled
across the file to reach 400.

## `gnps_node_table.csv`

600 nodes from the GNPS network the same dataset produced —
[NDEx a79c8a9a-3a98-11f1-94e8-005056ae3c32](https://www.ndexbio.org/viewer/networks/a79c8a9a-3a98-11f1-94e8-005056ae3c32),
*PlusRise_Carnitines_ibrary_only* by Helena Mannochio Russo — with their real `name` and `mz`
values. 400 of them are the spectra in the slice above; the other 200 are real nodes outside it, so
that unmatched nodes are genuine rather than invented.

**The scan number is the node's `name`, a String column. The network has no column called `scan`.**
That is why the dialog refuses to guess a scan column and makes the user choose one.

The full network and the full peak list join perfectly: 21,942 nodes, 21,942 spectra, every one
paired, none left over on either side.

## The query

```
QUERY scaninfo(MS2DATA)
  WHERE MS2PROD=337.25:TOLERANCEMZ=0.01:INTENSITYPERCENT=5
    AND MS2PROD=319.24:TOLERANCEMZ=0.01:INTENSITYPERCENT=5
```

One of the two the stakeholder supplied on the issue, for trihydroxy bile acids. It matches 31 of
the 400 spectra.
