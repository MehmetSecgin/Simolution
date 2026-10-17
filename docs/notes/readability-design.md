# Readability refactor (after engine v6)

## Intent

The owner wants to open any file and understand what it does without comments. Today the size sits in about ten long methods, three wide positional types (`EngineConfig` 27 args, `DynamicsSummary` 18, `RunConfig` 14) and one orchestration blob (`Main.main`, 150 lines). This refactor makes the code read like the domain: methods are sentences of named calls, and repeated arithmetic has one name.

It is a pure refactor. No behaviour, output format, CLI flag, JSON key or checkpoint byte changes.

## Rules

1. A method reads top to bottom as a sequence of calls named after the domain (`payBuildCost`, `splitBody`, `inheritGenome`). Aim for 25 lines or fewer; a longer method needs a reason a reviewer accepts.
2. No positional constructor takes more than about 5 arguments of one type. Group them into small records named after the domain.
3. Repeated arithmetic or logic gets one name, in one place.
4. Comment rule (replaces the old "Javadoc is welcome" rule in AGENTS.md): a comment stays only for an invariant or a numeric trick that a name cannot carry. Examples that stay: the MUL in-degree rule, the overflow rearrangement in the saturating drive, the operation-counter determinism of mutation, the Noise hash and range mapping, the parallel barrier handshake, the signed-zero argument behind junk pruning. Everything else becomes a name, or moves to `docs/` (specs, ADRs). History ("report-v13 retired X", "is now", "was") never lives in code.
5. Tests may carry only `// arrange`, `// act`, `// assert` markers. Contract references move to `@DisplayName`.

## Constraints

- Every step keeps `runs/baseline.txt` byte-identical (`state-digest: 78644e65e8086e45`) and every other output byte-identical too: the sidecars beside `--out` (layout, catalog, births, frames, frames index, time series), the `--observe` directory (run.json, events, metrics, checkpoints), replay snapshots, demo and trace console output, and error messages.
- Zero allocation per tick, no streams, boxing or lambdas in per-tick paths. Small private helpers are fine.
- StrictMath for transcendental functions in kernel code.
- No back-compat shims: when a type changes, every call site changes, tests included.
- JSON config format unchanged: same keys, same order, so config hashes, run ids and checkpoint-embedded configs stay identical.
- Every 32-bit int stays a legal gene.
- Two energy-audit formulas (MetricsWriter signed, DynamicsSummary absolute) add in different orders. They are not merged; each keeps its order.
- Observer order in the tick loop stays exactly: dynamics observer, per-tick digest print, time series, birth log, map frames, event log, metrics, checkpoints, trace. Birth log and map frames both assign catalog ids, so their order fixes the catalog bytes.

## Scope

Detailed findings with file and line references: `docs/notes/readability-audit-harness.md`, `docs/notes/readability-audit-config-genome-tests.md`, `docs/notes/readability-audit-kernel.md`.

| Stage | Area | Now or later |
|---|---|---|
| 1 | Main and run setup | now |
| 2 | CLI parsing and the inflow mini-language | now |
| 3 | EngineConfig grouped into domain records, JSON kept flat | now |
| 4 | Genome bit layout, SlotCompiler, inflow sources | now |
| 5 | Writers, reports, analysis, run directory | now |
| 6 | Kernel phases, WorldState, KernelSnapshot | after the parallel-claims branch merges |
| 7 | Test helpers and test comments | now |
| 8 | Package layout (`sim` into cli, run, observe, output, report, replay) | after stage 6 |

Stages 6 and 8 get their own plan later. Stage 6 waits because the parallel-claims work rewrites `ReproductionPhase`, `MovementPhase` and `Kernel.snapshot`.

## Decided

- `LatestFrame` and `RunDirectory.createBranch` stay: the web app will use them, even though nothing in production calls them yet.
- `EngineConfig` is grouped into `World`, `Founder`, `Metabolism`, `Harvest`, `Motility`, `Growth`, `Sensing`, `Reproduction`, `Mutation`. JSON stays flat through a private wire record inside `ConfigJson`. `v5Defaults` becomes `defaults`.
- `GeneDecoder.MAX_WEIGHT` is not a bound (raw -32768 decodes to -4.00012). It becomes `NOMINAL_MAX_WEIGHT`, and the test bound is corrected. No behaviour change.
- Missing CLI flag values fail with "`<flag>` needs a value" instead of an `ArrayIndexOutOfBoundsException`. This is the only intended change to a user-visible message.

## Left to the builder

Private helper names beyond those the plan fixes, the internal shape of parsers and writers, and the exact split points inside a method, as long as the rules and constraints hold.
