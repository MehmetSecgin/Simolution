# 0012 — Tuning discipline + emergent uptake capacity

## Context
The owner flagged a smell: several intake-era constants (`INTAKE_MAX`, `LEAK`,
`INFLOW`) had been chosen to produce a "nice" population — i.e. tuned for
outcomes. His worry: if adding sensors / nodes / genes / space forces us to
re-tune, we are hand-optimising the system instead of building one that optimises
itself. That is the core "no semantics in the kernel" invariant, applied to
constants rather than code.

## Decision

**1. The tuning discipline (contract-v1 §12).** Sort every constant into:
*scale anchors* (arbitrary units, set once), *world-harshness parameters* (define
the world's difficulty, set once to be livable), and *outcome targets* (survivor
count, lifespan, `E*`, population size — **never tuned**; they must emerge).
Rules that keep it honest: macro outcomes are emergent by construction; new
features must be **additive and self-paying** (expand the genome's strategy space,
pay the existing per-connection decay, change no existing constant); paradigm
shifts (intake, space) are rare, deliberate, one-ADR physics extensions, not a
tuning treadmill. We tune the world to be *alive-capable* once; never which life
appears or how much.

**2. Emergent uptake capacity (supersedes the flat `INTAKE_MAX` of ADR 0011).**
The intake ceiling is no longer a global grant. It is
`capacity = CAPACITY_PER_CONNECTION × transporterCount`, where `transporterCount`
is the number of connections feeding HARVEST from a meaningful source — the
genome's structural investment in eating machinery, counted once at construction
and paid for through ordinary per-connection decay. Demand is
`capacity · harvest/(HALF+harvest)`. So eating capacity is a heritable, evolvable
trait (the §6 acuity mechanism, now applied to the mouth), bounded by genome size
so it cannot diverge; the saturating signal term still prevents a runaway value
from buying extra intake or overflowing the shared denominator. The only globals
left are `CAPACITY_PER_CONNECTION` (1.5) and `HALF_SATURATION` (1.0) — a uniform
conversion/scale, not a per-unit talent. `HARVEST_INTAKE_MAX` removed.

## Why this is the principled fix
The flat `INTAKE_MAX` was an outcome knob: it set the same eating rate for every
unit, so "how good an eater you are" was granted by the kernel. Deriving the
ceiling from wiring investment moves that decision into the genome, where the
contract says all capture inequality must live (§1, §6). It also makes per-unit
`E*` emergent (it now depends on the unit's own transporter count), and it removes
the only constant that was genuinely tuned for an outcome.

## Evidence (constants left at INTAKE_MAX-era harshness; not re-tuned for counts)
Seed 42, 1000t: 34 survivors, max unit energy 2056 (units with more transporters
reach higher `E*` — capacity now differentiates), audit 1.2e-9. Seed 7, 20k:
monopolist unit 99 still implodes (dies tick 1671), max energy 2056 (no hoard),
16 survivors, deaths to tick 8283.

## Rejected
- Keep flat `INTAKE_MAX` (the outcome knob itself).
- Capacity from summed |weight| into HARVEST (conflates ceiling with signal gain;
  count is the cleaner "one gene = one transporter site").
- Count junk-source connections as transporters (they carry no signal; a built
  but unfed transporter should add no capacity).
- Re-tuning decay when genome size grows (that coupling — bigger genome costs more
  — is the physics working, per §12; compensating it away would be the smell).

## Deferred
Joint tuning of the surviving world-harshness parameters against the reproduction
milestone (inflow + per-cell `E*` set how many units the world feeds). Done once,
as harshness — never to hit a target population.
