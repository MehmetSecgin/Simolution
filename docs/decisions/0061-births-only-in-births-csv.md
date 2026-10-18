# 0061: Births only in births.csv.gz

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** The event log wrote one `BIRTH` line per birth, and `births.csv.gz` writes one row per birth from the same birth list. On a 50,000-tick viable run half of the 5.2 million event lines were births.

**Decision.** The event log keeps `DEATH` and `EXTINCTION` lines only. Births are read from `births.csv.gz`.

**Why.** The two files carried the same tick, slot, lineage, generation and parent. Keeping one copy loses none of those fields and halves the log (gzipped, 55 MB to 26 MB).

**Traded away.** Two `BIRTH` fields have no other copy: the child's cell at birth, and the count of flipped bits (`births.csv.gz` has the `mutated` flag and both genome ids, so the bit count can be read from the catalog). A checkpoint seek still gives the exact cell.
