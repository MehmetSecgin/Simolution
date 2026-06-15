# Audit prompt — re-examine the whole run-inspection / data design (report-v13)

Paste everything below the line into a fresh agent (or read it yourself). It is
self-contained: assume the reader knows nothing about prior conversations.

---

## Mission

You are auditing the **run-inspection and data-storage design** of Simolution (an
artificial-life kernel). The design currently ships as **report-v13** (spec
`docs/specs/report-v13.md`, decision `docs/decisions/0032-*.md`). It works and is committed.

The owner's belief, which is your working hypothesis to **prove or disprove**:

> "We can make this *way easier* and *hold less data*. We are still being more complicated and
> storing more than the problem requires."

Your job is an **adversarial, first-principles re-derivation**. Do not defend the current
design. Do not assume any current decision is correct — every one is on trial, including the
*requirements themselves*. Bias hard toward **less data** and **fewer moving parts**. If after
honest analysis the current design is genuinely near-optimal, say so *with evidence* — but the
default posture is suspicion.

Two axes matter, weighted in this order:
1. **Simplicity** — fewer files, fewer formats, fewer code paths, fewer dependencies, less
   bespoke machinery. "Easier" is the owner's first word. A design that's 20% bigger but half
   the moving parts may win.
2. **Bytes stored** — total on-disk per run, and peak RAM to inspect.

## Inviolable constraints (a proposal that breaks any of these is invalid)

Read `AGENTS.md` (the "Invariants" and "Performance doctrine" sections) before proposing
anything. In short:
- **Kernel stays pure and data-oriented.** Flat arrays, no per-tick allocation, no history in
  the kernel, no OO organism graph, byte-identical output when inspection sinks are off.
  Determinism is absolute (fixed seed + config → identical tick history). Any storage/inspection
  machinery lives in the **harness/observability layer**, never the kernel.
- **No new heavyweight dependency** without explicit justification — the project deliberately
  avoids, e.g., a Java Parquet writer. Browser-side libraries (sql.js, duckdb-wasm) are *on the
  table* but their weight counts against "simpler."
- **Memory doctrine**: footprint linear in units, **constant in tick count**. No unbounded
  per-tick accumulation anywhere in a loop.

## The lever you must test first: determinism is compression — is it fully applied?

The entire redesign was justified by "the run is a deterministic function of (seed, config,
founder genomes), so don't persist what you can recompute." **The current design applies this
selectively and may have left the biggest win on the table.** Test these specifically:

1. **Genomes are a deterministic function of founder + mutation history.** The catalog today
   stores *full* genomes (every distinct genome, ~32 ints each). On a rich run that is **9–30 MB
   / tens of thousands of genomes**. But each genome = its parent's genome + one mutation
   (point/indel), and the births log *already records* `parent_genome_id`, `child_genome_id`,
   `mutated` for every birth. So a genome could be stored as a **diff against its parent** (one
   gene index + new value for a point mutation; a length change for an indel) and reconstructed
   on demand. Question: can the catalog be replaced by *founder genomes + a mutation-diff log*,
   shrinking it ~10–100×? What does that cost the viewer (reconstruct a genome by walking diffs
   to a founder — bounded by generation depth, ≤ ~85)? Is the births log already 90% of that
   diff log?

2. **A unit's genome-id changes only at birth.** The frame stream carries `genomeId` for every
   unit *every tick*. That column is almost entirely constant per slot between births. Could the
   frames carry genome-id only on change (or not at all — derive each slot's current genome from
   the birth/death event stream)? What survives in the per-tick frame if you remove it?

3. **The resource field is recomputable.** Under uniform inflow it is constant; under cyclic
   inflow it is an analytic function of `InflowConfig` + tick (a pulsing disk + diffusion). The
   frames RLE-encode it every frame anyway. Can it be dropped from frames entirely and recomputed
   by the viewer from the config? (Diffusion makes it path-dependent — check whether the viewer
   can cheaply recompute, or whether one stored snapshot + analytic delta suffices.)

If any of these hold, the "lean every-tick frame + full-genome catalog" design is storing a lot
of derivable data — exactly the anti-pattern the redesign claimed to kill.

## Challenge the requirements, not just the implementation

The current design takes these owner requirements as fixed. **Re-litigate each** — is it truly
needed, or did it inflate the design?

- **"Every unit, every tick (not sampled)."** Is full per-tick resolution actually used, or
  would sampled frames + on-demand exact reconstruction of a chosen window (via the deterministic
  replayer) serve every real inspection need at a fraction of the bytes? What concrete task
  *requires* tick-by-tick over the whole run rather than over a window?
- **"Which action fired, per unit per tick."** Used for anything but colour-by-action? Is it
  derivable (genome + state) rather than stored?
- **Live-watch.** How often is a run actually watched *as it ticks* vs inspected after? Live-watch
  is the single requirement that forces a streamed file to exist. If it's rare, dropping it
  unlocks replay-canonical (store almost nothing; materialize on demand).
- **"No JVM to view."** Worth how much? It's the reason for bespoke text formats + a static
  server instead of just using the JVM replayer. Quantify the convenience vs the machinery it
  forces.

## The system as it stands (inventory — verify all of this; don't trust it)

A `--out` run writes these (sizes measured on two real runs — **baseline**: uniform, 100 units,
1000 ticks, world 100, population crashed to ~5; **live**: cyclic `--resource-cycle`, 300 units,
3000 ticks, world 100, sustained ~1600 units):

| file | what | baseline | live (rich) |
|------|------|---------:|------------:|
| `.txt` | run report (human) | 1.9 KB | 1.9 KB |
| `.timeseries.csv` | per-tick aggregates | 49 KB | 54 KB |
| `.births.csv.gz` | every birth: `tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated` | 8.6 KB | 871 KB (117k rows) |
| `.catalog` | global write-once genome dict `g <id> <count> <genes…>` | 128 KB | **9.2 MB** |
| `.catalog.idx` | `<gid> <count> <off> <len>` per genome | 6 KB | 490 KB |
| `.frames` | per tick: header, RLE resource line, then `u <cell> <slot> <mass> <action> <genomeId>` per living unit; `end` trailer | 678 KB | **112 MB** |
| `.frames.idx` | `<tick> <off> <len>` per frame (+`end`) | 15 KB | 57 KB |
| `.obs/…` (opt-in `--observe`) | replay manifest + checkpoints + events/metrics — the only *canonical* (recoverable) artifact | — | — |

Code (harness, `src/main/java/com/simolution/sim/`): `MapFrameWriter` (frames + idx),
`GenomeCatalogWriter` (catalog + idx), `BirthLogWriter` (births), `RunReport` (.txt),
`MetricsWriter`/`TimeSeriesReport`, plus the report-v8 observability stack (`RunManifest`,
`CheckpointWriter`, `Replayer`, `EventLogWriter`). Viewer: `src/main/resources/live.html`
(client-side parser, two modes: **embed** = baked offline, **indexed** = served + HTTP Range).
Range server: `scripts/serve.py` (~25-line stdlib subclass — stdlib `http.server` ignores
Range). Baker: `tools/mapviz.py`. Lineage walk: in-viewer, loads `.births.csv.gz`, gunzips
client-side, shows a per-generation mutation timeline.

### Measured facts (verify; one was misread once already)

- **Population is small** (avg ~33–56) even on rich runs; **peak** swings high (~700–1020).
- **Births**: the `.timeseries.csv` `births` column is **cumulative** — its *diff* is the
  per-tick rate. Real shape: a short colonization **boom** (≲200 births/tick for tens of ticks)
  then a near-static plateau.
- On the **cyclic** run, births-total is large (**117k rows**) and **~49% of births have
  `parent_slot = -1` and `mutated = -1`** — the unattributed half of symmetric binary fission.
  Distinct genomes on that run: **~22–73k**. (NB: an earlier note claimed "births-total ~1–2k
  per run" — that was true for the *uniform* baseline, NOT the cyclic run. Re-measure both;
  the rich run is the one that matters.)
- Viewer with on-demand indexing: **7 MB browser heap** on the 117 MB run, ~1.5 MB transferred
  to scrub 38 frames. (Whole-load was ~1 GB — that's why indexing exists.)

Commands to reproduce / measure (JDK 25; `sdk env` first):
```
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100 --out runs/baseline.txt"
./gradlew run --args="--units 300 --ticks 3000 --seed 100 --world 100 --resource-cycle \
  --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --out runs/live.txt"
ls -la runs/live.*            # sizes
gzcat runs/live.births.csv.gz | head; gzcat runs/live.births.csv.gz | wc -l
```

## Decision ledger — render a verdict on each

For **every** item below output: `KEEP / SIMPLIFY / REPLACE / KILL`, the **evidence** (numbers
from the runs, not adjectives), a **concrete leaner alternative**, its **byte + RAM estimate**,
and its **complexity delta** (files/formats/deps/code-paths added or removed). Be specific
enough that someone could implement your proposal without re-deriving it.

1. **Every-tick full frames** vs sampled-overview + on-demand window (replay or stored).
2. **Frame row fields** — is `mass` + `action` + `genomeId` each justified per-tick? Quantize/drop?
3. **Genome catalog as full genomes** vs founders + mutation-diff reconstruction (the lever above).
4. **Two index sidecars + Range server** — needed, or avoidable with a different container?
5. **`.births.csv.gz` keeping every edge** — prune the 49% `-1` fission halves? What's lost?
6. **Resource field stored per frame** — drop + recompute from `InflowConfig`?
7. **`.timeseries.csv` vs `.obs/metrics.csv`** — duplicate? Keep which?
8. **`.txt` report** — keep (it's tiny); but its schema lineage is v4–v13. Consolidate?
9. **Two viewer modes** (embed baker + indexed served) — collapse to one?
10. **No-JVM bespoke text formats** vs a single columnar/SQLite file queried by sql.js/duckdb-wasm
    (one artifact, no `.idx`, no Range server, seeking handled by the lib). Weigh "simpler".
11. **Live-watch as a hard requirement** — if dropped, does replay-canonical become simplest?
12. **The whole "stream frames to disk" model** vs "store only manifest+checkpoints, materialize
    any view on demand." This was considered and rejected for live-watch + no-JVM — re-test that
    rejection against the real usage, now that on-demand indexing exists.

Also surface **anything not listed** that stores or complicates more than the problem needs.

## Strong candidate end-states to evaluate explicitly

Don't just critique piecemeal — cost out at least these whole-design alternatives and recommend one:

- **(A) Current** — lean frames + full-genome catalog + births CSV + 2 idx + Range server + 2 viewers.
- **(B) Diff-genome** — A, but catalog = founders + mutation diffs (genomes reconstructed by
  walking the births log). Measure catalog shrink and viewer cost.
- **(C) Single-file** — one SQLite (or DuckDB) file holding frames + genomes + births; viewer
  uses sql.js/duckdb-wasm; no bespoke text formats, no `.idx`, no Range server. Measure file
  size + front-end weight + which code/files disappear.
- **(D) Replay-canonical** — persist only `--observe` manifest + sparse checkpoints; materialize
  any frame window / genome / lineage on demand via the deterministic replayer; viewer fed by
  materialized windows. Measure stored bytes (near-zero) vs inspection latency + JVM dependency.

## Deliverable

1. A filled **decision ledger** (the 12 items + extras), each with verdict + evidence + proposal.
2. A **recommended end-state** (one of A–D or a hybrid you define), with a total-bytes and
   total-moving-parts comparison table against the current design, on **both** the baseline and
   the cyclic run.
3. A short **migration sketch** (what to build/delete) and what it would break (tests, specs).
4. An explicit statement of **what you would give up** for the simplicity/size win, so the owner
   can veto.

Verify every number against the actual runs before you rely on it. Where the current design is
genuinely justified, prove it with data and say so plainly. The win condition is the owner
reading your output and saying "yes — that's smaller and easier, and I understand the trade."
