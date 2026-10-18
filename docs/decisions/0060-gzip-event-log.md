# 0060: Gzip the event log

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** Every `--out` run writes the event log since `--observe` went away (ADR 0055). Uncompressed it reached 529 MB on a 50,000-tick run of `examples/viable.json` with 1,000 founders, two thirds of the run directory.

**Decision.** The writer streams it through gzip as `events.jsonl.gz`, the same way `births.csv.gz` and `catalog.gz` are written. The lines inside are unchanged.

**Why.** Nothing in the Observatory reads the file, but DuckDB reads a gzipped JSON file directly, so queries keep working with only the file name changed.

**Rejected.** Dropping the file in favour of `births.csv.gz` and `metrics.csv`: births are covered, but each death (its tick, cell and lifespan) and which lineage went extinct when exist nowhere else, and `metrics.csv` only counts them.
