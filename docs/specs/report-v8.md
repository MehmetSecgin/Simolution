# report-v8 — run observability: event/metric logs, checkpoints, deterministic replay, query layer

Delta over [report-v7](report-v7.md): adds an **observability layer** so a finished
(or running) simulation can be explored after the fact — *"go to tick T and see
everything"* and *"query what already happened"*, the way you'd scrub a Grafana
time range. report-v7's text report, CSV sidecars, and `map.txt` are unchanged and
remain the human-readable default; report-v8 is **additive and optional**.

This spec is design-binding for the observability module; it does **not** change
any kernel law (contract-v3 stands). It is the design doc requested before
implementation — schemas and APIs here are the contract the code must meet.

## Design pillars (locked)

1. **Determinism is the time machine.** `seed + founder genomes + config →
   byte-identical history` (contract-v3 §1, invariants). So "show me tick T" does
   not require storing T ticks of state: store a **manifest** (the replay key) and
   **replay** to T, optionally accelerated by periodic **checkpoints**. Replay
   yields *full* fidelity — per-node outputs, delay memory, live circuits — which
   no sampled log can hold.
2. **Logging is an optional, decoupled sink — the kernel gets no observation hook.**
   The kernel stays pure: it logs nothing and exposes no event callback (laws never
   observe themselves). The sim layer owns a `RunLog` sink and derives **everything**
   from state it can already see: births/deaths/extinctions from snapshot deltas
   (the observer tracks these today), and the exact mutated bit indices at a birth
   by **diffing the child genome against the parent's** — both slots' genes are
   present at the birth tick and a move never rewrites genes (ADR 0020), so the flip
   set is recoverable with no kernel cooperation. **With no sink attached the run is
   byte-identical** and allocates nothing extra. Reader/query tooling is a separate
   concern the JVM never imports.
3. **History lives on disk, never in RAM** (memory doctrine). Every artifact is
   streamed and flushed incrementally like `MapFrameWriter` — footprint stays
   O(units), constant in tick count. Nothing accumulates a trajectory in memory.
4. **JVM writes plain text; the reader owns Parquet + query** (ADR 0023). The sim
   emits typed CSV/JSONL only — zero new JVM dependency. DuckDB (in the query
   layer) reads those directly and, if wanted, materializes Parquet in one
   statement. Heavy deps live only in tooling, never in the build that must run.

## Tiered capture — what is logged vs what is replayed

| Concern | Mechanism | Artifact |
|---|---|---|
| "Everything at tick T" — full per-unit + per-node + lattice | **replay** to T (+ checkpoints) | `manifest.json`, `ckpt/<tick>.ckpt` |
| Births / deaths / extinctions / abiogenesis | **event log, exact, every occurrence** | `events.jsonl` |
| Population / energy / audit over time | **metric log, every tick** (O(1)/tick, small) | `metrics.csv` |
| Lattice (resource + lineage) | **spatial sample**, every K ticks (exists) | `map.txt` |
| Per-unit detail between events | **replay on demand** (not streamed per tick) | — |

Events are rare relative to ticks, so they are logged exactly. Per-unit/per-node
state is *recovered by replay* rather than streamed, keeping logs small.

## Artifacts (all under `<base>.obs/` when `--observe` is set)

### `manifest.json` — the replay key
```json
{
  "kernel": "v3",
  "seed": 99,
  "worldWidth": 130,
  "founderCount": 200,
  "founderCells": [/* the scatter, ADR 0022 — explicit so replay is exact */],
  "founderGenomes": [[/* slot 0 genes */], [/* slot 1 genes */], ...],
  "genomeSeed": 99, "genesPerUnit": 8, "maxGenes": 32,
  "ticks": 40000,
  "configHash": "<hash of all KernelConfig constants>",
  "checkpointEvery": 2000,
  "mapSampleEvery": 333
}
```
`seed` drives the runtime RNG (`Noise.sample(seed, slot, tick)`); `genomeSeed`
drives founder generation (`GenomeFactory`). `configHash` guards against replaying
under changed constants. **Founder genomes are stored verbatim** in
`founderGenomes` (small: `founderCount × genes` ints) — *not* regenerated from
`genomeSeed` at replay time. Reason: `configHash` covers KernelConfig constants but
**not** the `GenomeFactory` algorithm, so a factory code change would silently
produce different founders with no mismatch caught. Same logic as `founderCells`:
the founding state is data, not law. `genomeSeed` is kept only as provenance. A run
whose `configHash` ≠ the current build refuses silent replay.

### `events.jsonl` — exact, one JSON object per line, append-only
```
{"t":1,"ev":"ABIOGENESIS","slot":..,"lineage":..,"cell":..}
{"t":N,"ev":"BIRTH","slot":child,"lineage":..,"gen":g,"cell":c,"parentSlot":p,"parentLineage":pl,"mutations":m}
{"t":N,"ev":"DEATH","slot":..,"lineage":..,"gen":g,"cell":c,"lifespan":L}
{"t":N,"ev":"EXTINCTION","lineage":..,"lastTick":N,"peakMembers":..,"maxGeneration":..}
```
`BIRTH.mutations` = count of bit flips applied at that birth (per ADR 0021's split),
derived in the sim by diffing child vs parent genes; the exact flip indices are an
opt-in field from the same diff (pillar 2), no kernel hook. DEATH fires when a slot
crosses `energy ≤ 0` (contract §9, derived). Births/deaths are observable as
snapshot deltas today; `EXTINCTION` is a lineage-tally edge the observer already
tracks.

### `metrics.csv` — per-tick aggregates (every tick; O(1) per tick)
```
tick,population,lineages_alive,births_cum,deaths_cum,energy_total,field_total,
     sink,intake_cum,inflow_cum,audit_error,max_generation
```
Supersedes today's `timeseries.csv` (renamed and moved under `.obs/`; not emitted
alongside — one canonical metric stream, not two). Every-tick is affordable: a
fixed-width row, streamed and flushed, no in-RAM growth.

### `map.txt` — spatial sample (unchanged from report-v7)
Resource field + per-cell lineage, sampled every K ticks.

### `ckpt/<tick>.ckpt` — full-state checkpoints (binary, every C ticks)
Restore point for O(1)-ish replay jumps. Contents = the complete restorable kernel
state at that tick: `tick`, and per-slot `genes`/`geneCount`, `energy`, `damage`,
`lineageId`, `generation`, `position`; plus `resourceField`, `outputsPrev`,
`delayMemory`, `energySink`, `cumulativeInflow`, `birthsTotal`, `maxGeneration`.
(`cellOccupant` is rederived from `position`.) **No RNG state is stored**: the
runtime RNG is the stateless counter-based `Noise.sample(seed, slot, tick)`, a pure
function of the seed and the (slot, tick) being computed — there is no internal
generator state to capture, and `tick` is already in the checkpoint. This is also
why restore + tick-forward reproduces the canonical history exactly. Binary,
little-endian, length-framed; not meant to be human-read — it is the replay
accelerator. Disk cost ≈ `(#checkpoints) × per-slot-state`; tune `C` to trade disk
for jump latency.

## Kernel surface needed (new APIs; no law change)

Replay and checkpointing need **one** addition to the runtime, pure mechanics:

1. **State save/restore.** `Kernel.saveState(out)` / a constructor or
   `Kernel.restore(manifest, ckpt)` that rehydrates the flat arrays. No behavior
   change — restoring a checkpoint at tick T and ticking forward must produce the
   same history as ticking from 0 (determinism test asserts this). This also
   requires per-slot genes to be readable for the dump, which is the same access
   the sim uses to diff mutations and to render live circuits.

No event hook is added: births, deaths, extinctions, and the exact mutated bit
indices are all sim-derivable (pillar 2), so the kernel reports nothing. This keeps
the runtime fully unobserving and the off-path byte-identical. Save/restore is
gated so the existing `--out`/report path and all tests are unaffected when
observability is off.

## Replay API (sim layer)

```
Replayer r = Replayer.open("<base>.obs");      // reads manifest, indexes checkpoints
KernelSnapshot s = r.seekTo(T);                 // nearest ckpt ≤ T, tick remainder, snapshot
CompiledConnection[] circuit = r.circuitOf(slot); // live evolved wiring at T
```
`seekTo(T)` loads the greatest checkpoint ≤ T and ticks forward `T − ckptTick`
times. With `checkpointEvery = C`, worst-case replay cost is `C` ticks per jump.
Exactness is guaranteed by determinism + `configHash` match.

## Query layer (separate tooling — DuckDB, optional Grafana)

No server, no JVM dep. DuckDB reads the artifacts in place:
```sql
-- lineages that outlived tick 5000 but went extinct, by longevity
SELECT lineage, lastTick, peakMembers
FROM read_json_auto('run.obs/events.jsonl')
WHERE ev = 'EXTINCTION' AND lastTick > 5000
ORDER BY lastTick DESC;

-- population & audit drift over time
SELECT tick, population, lineages_alive, audit_error
FROM read_csv_auto('run.obs/metrics.csv');

-- one-line Parquet materialization, if/when wanted
COPY (SELECT * FROM read_json_auto('run.obs/events.jsonl'))
  TO 'run.obs/events.parquet' (FORMAT parquet);
```
**Grafana is an optional front-end**, not the substrate: point a DuckDB/Parquet
datasource at `<base>.obs/` later for dashboards/alerting with zero migration. It
is explicitly *not* required and *not* the storage of record.

## Time-travel viewer (`tools/`, stdlib + DuckDB)

Extends the report-v7 viewers with a **snapshot page**: given a run and a tick T,
the JVM replays to T and dumps a one-shot state page — the lattice (as `map.txt`
frames), a per-unit table (energy, damage, generation, cell), and the selected
unit's live circuit (as `visualize.py` already renders founder/evolved circuits).
This is the "open tick T, see everything" surface. Aggregate panels (population,
energy, lineage counts) render from `metrics.csv`/`events.jsonl` via DuckDB.

## CLI

```
--observe                 enable the observability layer (writes <base>.obs/; requires --out)
--checkpoint-every C      full-state checkpoint cadence (default 2000; 0 disables → pure replay)
```
Off by default: tests, baseline, and the standard report path are untouched and
allocate nothing new. `--observe` requires `--out` (the `.obs/` dir sits beside it).

## Memory & determinism guarantees (must hold)

- Sink absent → run is byte-identical and allocates nothing extra (asserted in a
  test: digest with/without `--observe` is equal).
- Every artifact streamed + flushed incrementally; no per-tick history in RAM
  (footprint O(units), constant in tick count).
- Replay from a checkpoint reproduces the canonical history exactly (asserted:
  `seekTo(T)` digest == ticking from 0 to T).
- `configHash` mismatch refuses replay rather than producing a wrong "past".

## Out of scope (deferred)

Live push to Grafana during a run; cross-run comparison dashboards; per-tick
per-unit streaming (replay covers it); compression/retention policy for very long
runs' checkpoints.
