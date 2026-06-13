#!/usr/bin/env python3
"""Self-contained HTML "journey" page for Simolution.

Hand-authored narrative visualization (not driven by a run CSV) in the same
dark-terminal style as tools/visualize.py. Facts and numbers live in the data
block below as the single source of truth; edit them there.

    python3 tools/journey.py            # -> docs/story/journey.html
    python3 tools/journey.py out.html   # -> out.html
"""

import html
import sys

# ---------------------------------------------------------------------------
# palette (mirrors tools/visualize.py / GitHub-dark)
# ---------------------------------------------------------------------------
C = {
    "bg": "#0d1117",
    "panel": "#161b22",
    "border": "#30363d",
    "grid": "#21262d",
    "text": "#e6edf3",
    "muted": "#7d8590",
    "dim": "#586069",
    "junk": "#484f58",
    "green": "#3fb950",
    "blue": "#58a6ff",
    "red": "#f85149",
    "amber": "#d29922",
    "purple": "#bc8cff",
    "sensor": "#7f77dd",
    "internal": "#378add",
    "action": "#1d9e75",
}

# per-milestone accent
ACCENT = {"v0": C["muted"], "v1": C["green"], "v2": C["purple"], "v3": C["amber"]}

# ---------------------------------------------------------------------------
# DATA — facts only; sources named in docs/story/journey.md
# ---------------------------------------------------------------------------
HEADER = {
    "title": "SIMOLUTION",
    "subtitle": "an artificial-life kernel — physics only, never goals or fitness",
    "span": "2026-01-04 skeleton · built 2026-06-11 → 06-13",
    "thesis": "A deterministic VM for evolving signal-graph units. The kernel "
    "defines energy, decay, signal propagation and space; it never defines "
    "meaning, goals, or fitness. Every lifelike strategy below emerged from "
    "random 32-bit genomes under those laws — none of it was coded.",
}

INVARIANTS = [
    ("genome = 32-bit ints", "one gene = one weighted connection; every int is a legal gene"),
    ("determinism", "same genome set + seed → byte-identical tick history"),
    ("energy conserved", "never created, only moved to a sink; audit error ~1e-9"),
    ("death is derived", "death = energy ≤ 0; there is no stored 'alive' flag"),
]

# gene bit layout: (label, bits, color)
GENE = [
    ("SrcType", 1, C["sensor"]),
    ("SrcID", 7, C["sensor"]),
    ("DstType", 1, C["action"]),
    ("DstID", 7, C["action"]),
    ("Weight", 16, C["amber"]),
]

# substrate growth — node sets per milestone.
# state: "base" | "new" | "changed"
SUBSTRATE = [
    {
        "tag": "v0.1", "accent": ACCENT["v0"],
        "sensors": [("CONST", "base"), ("RAND", "base")],
        "internals": [("ADD", "base"), ("MUL", "base"), ("CLAMP", "base"),
                      ("DELAY", "base"), ("THRESH", "base")],
        "actions": [("ACTION_Y", "base")],
    },
    {
        "tag": "v1", "accent": ACCENT["v1"],
        "sensors": [("CONST", "base"), ("RAND", "base"), ("RESOURCE", "new")],
        "internals": [("ADD", "base"), ("MUL", "base"), ("CLAMP", "base"),
                      ("DELAY", "base"), ("THRESH", "base")],
        "actions": [("ACTION_Y", "base"), ("HARVEST", "new")],
    },
    {
        "tag": "v2", "accent": ACCENT["v2"],
        "sensors": [("CONST", "base"), ("RAND", "base"), ("RESOURCE", "base"),
                    ("SELF_ENERGY", "new")],
        "internals": [("ADD", "base"), ("MUL", "base"), ("CLAMP", "base"),
                      ("DELAY", "base"), ("THRESH", "base")],
        "actions": [("ACTION_Y", "base"), ("HARVEST", "base"), ("REPRODUCE", "new")],
    },
    {
        "tag": "v3", "accent": ACCENT["v3"],
        "sensors": [("CONST", "base"), ("RAND", "base"),
                    ("LOCAL_RESOURCE", "changed"), ("SELF_ENERGY", "base")],
        "internals": [("ADD", "base"), ("MUL", "base"), ("CLAMP", "base"),
                      ("DELAY", "base"), ("THRESH", "base")],
        "actions": [("ACTION_Y", "base"), ("HARVEST", "base"), ("REPRODUCE", "base"),
                    ("MOVE_N", "new"), ("MOVE_S", "new"), ("MOVE_E", "new"),
                    ("MOVE_W", "new")],
    },
]

# tick pipeline growth: (phases, new-from-index)
PIPELINE = [
    ("v0.1", ["clear", "propagate", "evaluate", "swap", "settle: cost"], 4, ACCENT["v0"]),
    ("v1", ["clear", "propagate", "evaluate", "swap", "settle: intake", "settle: cost"], 4, ACCENT["v1"]),
    ("v2", ["clear", "propagate", "evaluate", "swap", "settle: intake", "settle: cost",
            "settle: reproduction"], 6, ACCENT["v2"]),
    ("v3", ["clear", "propagate", "evaluate", "swap", "settle: intake", "settle: cost",
            "settle: reproduction", "settle: movement", "settle: diffusion"], 7, ACCENT["v3"]),
]

# milestones — full story bands
MILESTONES = [
    {
        "id": "v0", "tag": "v0", "accent": ACCENT["v0"],
        "title": "Closed system",
        "date": "2026-06-11 → 06-12",
        "lede": "A closed box. No intake, no reproduction, no space — energy only "
        "drains, via structural decay (entropy, every tick) and activity cost "
        "(work done). The baseline: truthful energy accounting under fixed laws.",
        "commits": [
            ("0a4ee9d", "multi-unit evaluation — one Kernel owns the population in shared flat arrays (ADR 0002)"),
            ("17c994b", "deterministic harness + report-v1; counter-based RNG decouples a unit from population size/order (ADR 0003/0004)"),
            ("c8edcf8", "true MUL semantics — product of the two strongest inputs (ADR 0005)"),
            ("5e7db49", "energy accounting, decay, death; death = energy ≤ 0, derived (ADR 0006)"),
            ("663c24f", "first HTML visualizer + per-unit wiring export (ADR 0007/0008)"),
        ],
        "observed": "Every unit eventually dies. Different wiring persists for "
        "different durations. Quiet (“dormant”) regimes appear on their own "
        "as the emergent absence of self-sustained dynamics — dormancy is never a coded state.",
        "viz": None,
        "note": "No run data to chart yet — in a closed world the only variable is "
        "how long a structure outlasts entropy before energy hits zero.",
    },
    {
        "id": "v1", "tag": "v1", "accent": ACCENT["v1"],
        "title": "Energy intake",
        "date": "2026-06-12",
        "lede": "First sensor that reads the world (RESOURCE, a depletable pool) and "
        "first effector (HARVEST). Energy flows reservoir → units → sink. The kernel "
        "must never grant energy because a unit did something useful — the pool "
        "refills uniformly; the right wiring captures it, a rock does not.",
        "commits": [
            ("4d12194", "open-system energy intake — depletable pool + RESOURCE sensor + HARVEST action (ADR 0009/0010)"),
            ("d94f567", "saturating uptake + storage maintenance — the hoarding fix (ADR 0011)"),
            ("417855c", "uptake capacity = capacity-per-connection × transporter count — heritable, not tuned (ADR 0012)"),
            ("422d2fd", "biological cost model — drop per-connection decay, add fixed BASAL_COST = 0.5 (ADR 0013)"),
        ],
        "degeneracy": {
            "n": 1, "name": "eat-forever hoarding",
            "found": "A unit wired a DELAY self-loop with gain > 1; its harvest "
            "signal diverged geometrically, it monopolized the pool, and coasted "
            "forever because nothing charged it for what it held.",
            "fix": "Saturating (Michaelis-Menten) uptake caps eating rate; a "
            "storage-maintenance leak caps the worth of hoarding. Together they "
            "give an emergent carrying capacity E*; hoard past it and you starve.",
        },
        "observed": "runs/long-seed7.txt (v1, seed 7, 100 units, 20k ticks): 1 "
        "survivor of 100; one cell held 121,458 energy — more than the 100,000 the "
        "whole run started with; 13 of 100 units terminally divergent. After the fix: "
        "the diverger dies, no unit ends above ~1000, survivors graze the pool to its floor.",
        "viz": {
            "kind": "bars",
            "title": "the hoarder (v1, seed 7) — before the fix",
            "unit": " energy",
            "items": [
                ("run started with", 100000, C["dim"]),
                ("one cell held", 121458, C["red"]),
            ],
        },
    },
    {
        "id": "v2", "tag": "v2", "accent": ACCENT["v2"],
        "title": "Reproduction · mutation · aging",
        "date": "2026-06-13",
        "lede": "A replicator: REPRODUCE effector + SELF_ENERGY sensor, plus per-bit "
        "mutation. The kernel never auto-divides — the drive to reproduce must itself "
        "evolve. No seeded ancestor: replication has to arise from the random founder "
        "draw, and most seeds go extinct.",
        "commits": [
            ("0a45888", "SELF_ENERGY sensor + REPRODUCE action nodes (ADR 0014)"),
            ("24ad75a", "per-slot rewritable connection store — a mutated child writes into a freed slot (contract-v2 §12)"),
            ("2716483", "reproduction phase + point mutation; fixed BUILD_COST closes spam (ADR 0015/0016)"),
            ("c5c52c0", "entropic aging + germline renewal — Gompertz upkeep, offspring reset to damage = 0 (ADR 0017)"),
        ],
        "degeneracy": {
            "n": 2, "name": "reproduction spam",
            "found": "Founders that wired REPRODUCE positively divided "
            "indiscriminately into ever-tinier offspring (birth was free, instant, "
            "unlimited). Seed 42: 55,766 births, peak population 6,408.",
            "fix": "A fixed per-birth BUILD_COST = 10 (the biosynthesis analogue of "
            "basal metabolism) makes tiny offspring net-lethal and bounds births by "
            "system energy. Same seed after: 4,950 births, peak 833.",
        },
        "degeneracy2": {
            "n": 3, "name": "the freeze",
            "found": "A unit harvesting its bill sits at equilibrium forever; once "
            "the pool drains, reproducing is locally worse than persisting. Evolution "
            "converged on immortal non-reproducers and froze — seed 10 reached "
            "generation 146, then the winner stopped breeding.",
            "fix": "Entropic aging: damage = lifetime energy dissipated; it feeds "
            "back into upkeep (Gompertz). Offspring are born damage = 0 (germline "
            "renewal) — a lineage escapes entropy only by reproducing. Lifespan is "
            "never coded; it emerges. Mutation lowered 0.001 → 0.0003 to dodge "
            "Eigen's error catastrophe.",
        },
        "observed": "runs/evolve.txt (v2, seed 7, 30 founders, 20k ticks): a single "
        "lineage reproduces continuously to generation 1757 (35,789 births), peak "
        "population only 222 (aging bounds it), energy audit 9.3e-10. The winner "
        "evolved into a harvest specialist — tripled transporters, wired "
        "RESOURCE → HARVEST, kept breeding, dropped SELF_ENERGY — all emergent.",
        "viz": {
            "kind": "multi",
            "blocks": [
                {"title": "spam → BUILD_COST (seed 42, 100 founders, 1000 ticks)", "unit": " births",
                 "items": [("before", 55766, C["red"]), ("after", 4950, C["green"])]},
                {"title": "peak population, same runs", "unit": "",
                 "items": [("before", 6408, C["red"]), ("after", 833, C["green"])]},
                {"title": "the freeze → aging (max generation reached)", "unit": "",
                 "items": [("froze (seed 10)", 146, C["red"]), ("evolving (seed 7)", 1757, C["green"])]},
            ],
        },
        "limit": "Of 30 founders, exactly 1 lineage survived — a monoculture. In a "
        "well-mixed world a strategy competes against the average, so the single "
        "fittest harvester wins everywhere. No niches, no refuges, nowhere for "
        "diversity to hide.",
    },
    {
        "id": "v3", "tag": "v3", "accent": ACCENT["v3"],
        "title": "Space + motility",
        "date": "2026-06-13 → current frontier",
        "lede": "The world becomes a place: a W×W toroidal lattice, one unit per "
        "cell, each cell with its own resource (uniform inflow, spread by diffusion). "
        "RESOURCE → LOCAL_RESOURCE: a unit senses and harvests only its own cell. "
        "Four MOVE effectors add one Moore step per tick. (Implemented in the working "
        "tree; not yet committed on this branch.)",
        "commits": [
            ("ADR 0018", "2D toroidal lattice — one unit per cell; the grid is the slot pool; halt-on-full retired"),
            ("ADR 0019", "per-cell economics — CELL_INFLOW 1.0, CELL_CAPACITY 100, DIFFUSION_RATE 0.1; LOCAL_RESOURCE replaces global RESOURCE"),
            ("ADR 0020", "motility — MOVE_N/S/E/W, one step/tick, MOVE_COST 0.2, blocked-if-occupied; position decoupled from slot (no recompile on move)"),
        ],
        "observed": "runs/baseline.txt (v3, seed 42, 100 units, 100×100 grid, 1000 "
        "ticks): 6 distinct lineages alive at the end — vs the single surviving "
        "lineage of well-mixed v2. 1,228 births, max generation 20, peak population "
        "292, energy audit 2.1e-8. Local depletion makes competition local, so the "
        "monoculture can no longer take over everywhere. Chemotaxis is emergent, "
        "never rewarded — LOCAL_RESOURCE + DELAY let a unit run-and-tumble toward "
        "food while blind to the true gradient, the way bacteria forage.",
        "viz": {"kind": "lineages", "a": ("well-mixed (v2)", 1), "b": ("spatial (v3)", 6)},
    },
]

DEGEN_TABLE = [
    ("1", "eat-forever hoarding", "1 survivor hoards 121,458 (>100k start)",
     "saturating uptake + storage maintenance", "emergent carrying capacity E*", C["green"]),
    ("2", "reproduction spam", "55,766 births, peak 6,408",
     "fixed BUILD_COST = 10", "4,950 births, peak 833", C["purple"]),
    ("3", "freeze-eat-persist", "froze at gen 146",
     "entropic aging + germline renewal", "gen 1757, continuous turnover", C["amber"]),
]


# ---------------------------------------------------------------------------
# svg helpers
# ---------------------------------------------------------------------------
def esc(s):
    return html.escape(str(s))


def fmt(v):
    return f"{v:,}" if isinstance(v, int) else str(v)


def svg_bars(title, items, unit=""):
    """Horizontal bar chart scaled to the max value. items: (label, value, color)."""
    w, rowh, pad_l, pad_t = 500, 34, 165, 30
    h = pad_t + rowh * len(items) + 12
    mx = max(v for _, v, _ in items) or 1
    bar_max = w - pad_l - 110
    out = [f'<svg viewBox="0 0 {w} {h}" width="100%" role="img">']
    out.append(f'<text x="0" y="16" fill="{C["muted"]}" font-size="11" '
               f'letter-spacing=".5">{esc(title)}</text>')
    for i, (label, val, color) in enumerate(items):
        y = pad_t + i * rowh
        bw = max(2, bar_max * val / mx)
        out.append(f'<text x="{pad_l-8}" y="{y+13}" text-anchor="end" '
                   f'fill="{C["text"]}" font-size="11">{esc(label)}</text>')
        out.append(f'<rect x="{pad_l}" y="{y}" width="{bw:.1f}" height="18" '
                   f'rx="2" fill="{color}"/>')
        out.append(f'<text x="{pad_l+bw+6:.1f}" y="{y+13}" fill="{color}" '
                   f'font-size="11" font-weight="600">{esc(fmt(val))}{esc(unit)}</text>')
    out.append("</svg>")
    return "".join(out)


def svg_lineages(a, b):
    """The money shot: 1 block vs 6 blocks, same scale."""
    al, an = a
    bl, bn = b
    cols = [C["green"], C["blue"], C["amber"], C["purple"], C["red"], C["sensor"]]
    cell, gap = 30, 6
    w = 480
    out = [f'<svg viewBox="0 0 {w} 150" width="100%" role="img">']

    def group(x0, label, n, single_color=None):
        s = [f'<text x="{x0}" y="18" fill="{C["muted"]}" font-size="11">{esc(label)}</text>']
        s.append(f'<text x="{x0}" y="140" fill="{C["text"]}" font-size="12" '
                 f'font-weight="600">{n} lineage{"s" if n != 1 else ""}</text>')
        for i in range(n):
            cx = x0 + (i % 3) * (cell + gap)
            cy = 34 + (i // 3) * (cell + gap)
            col = single_color or cols[i % len(cols)]
            s.append(f'<rect x="{cx}" y="{cy}" width="{cell}" height="{cell}" '
                     f'rx="3" fill="{col}" opacity="0.9"/>')
        return "".join(s)

    out.append(group(10, al, an, single_color=C["dim"]))
    out.append(f'<line x1="{w/2}" y1="28" x2="{w/2}" y2="118" '
               f'stroke="{C["border"]}" stroke-dasharray="3 3"/>')
    out.append(group(w / 2 + 24, bl, bn))
    out.append("</svg>")
    return "".join(out)


def svg_gene():
    total = sum(b for _, b, _ in GENE)
    w, x = 900, 0.0
    unitw = w / total
    out = [f'<svg viewBox="0 0 {w} 92" width="100%" role="img">']
    for label, bits, color in GENE:
        seg = bits * unitw
        out.append(f'<rect x="{x:.1f}" y="20" width="{seg-2:.1f}" height="40" '
                   f'rx="3" fill="{color}" opacity="0.85"/>')
        out.append(f'<text x="{x+seg/2:.1f}" y="45" text-anchor="middle" '
                   f'fill="{C["bg"]}" font-size="12" font-weight="700">{esc(label)}</text>')
        out.append(f'<text x="{x+seg/2:.1f}" y="76" text-anchor="middle" '
                   f'fill="{C["muted"]}" font-size="10">{bits} bit{"s" if bits>1 else ""}</text>')
        x += seg
    out.append(f'<text x="0" y="13" fill="{C["dim"]}" font-size="10">bit 31</text>')
    out.append(f'<text x="{w}" y="13" text-anchor="end" fill="{C["dim"]}" '
               f'font-size="10">bit 0</text>')
    out.append("</svg>")
    return "".join(out)


def chip(label, state, accent):
    if state == "new":
        return (f'<span class="chip" style="border-color:{accent};color:{accent};'
                f'box-shadow:0 0 0 1px {accent}55">{esc(label)} +</span>')
    if state == "changed":
        return (f'<span class="chip" style="border-color:{C["amber"]};'
                f'color:{C["amber"]}">{esc(label)} ~</span>')
    return f'<span class="chip chip-base">{esc(label)}</span>'


def substrate_block(m):
    def group(name, nodes, junk):
        chips = "".join(chip(n, s, m["accent"]) for n, s in nodes)
        j = f'<span class="chip chip-junk">+{junk} junk</span>' if junk else ""
        return (f'<div class="sgroup"><div class="sglabel">{name}</div>'
                f'<div class="schips">{chips}{j}</div></div>')

    return (f'<div class="srow"><div class="stag" style="color:{m["accent"]}">{m["tag"]}</div>'
            f'<div class="sgroups">'
            + group("sensors", m["sensors"], 2)
            + group("internals", m["internals"], 3)
            + group("actions", m["actions"], 1)
            + "</div></div>")


def pipeline_block(tag, phases, new_from, accent):
    chips = []
    for i, p in enumerate(phases):
        if i >= new_from:
            chips.append(f'<span class="pchip" style="border-color:{accent};'
                         f'color:{accent}">{esc(p)}</span>')
        else:
            chips.append(f'<span class="pchip pchip-base">{esc(p)}</span>')
    arrow = '<span class="parrow">→</span>'.join(chips)
    return (f'<div class="prow"><div class="stag" style="color:{accent}">{tag}</div>'
            f'<div class="pchips">{arrow}</div>'
            f'<div class="pcount">{len(phases)} phases</div></div>')


# ---------------------------------------------------------------------------
# milestone band
# ---------------------------------------------------------------------------
def milestone_band(m):
    acc = m["accent"]
    commits = "".join(
        f'<li><code style="color:{acc}">{esc(h)}</code> {esc(d)}</li>'
        for h, d in m["commits"]
    )
    parts = [f'<section class="band" id="{m["id"]}" style="--acc:{acc}">']
    parts.append('<div class="band-head">'
                 f'<span class="band-tag" style="background:{acc}">{esc(m["tag"])}</span>'
                 f'<h2>{esc(m["title"])}</h2>'
                 f'<span class="band-date">{esc(m["date"])}</span></div>')
    parts.append(f'<p class="lede">{esc(m["lede"])}</p>')
    parts.append(f'<ul class="commits">{commits}</ul>')

    # degeneracy callouts
    for key in ("degeneracy", "degeneracy2"):
        d = m.get(key)
        if not d:
            continue
        parts.append(
            f'<div class="degen"><div class="degen-h">degeneracy #{d["n"]} — '
            f'{esc(d["name"])}</div>'
            f'<div class="degen-row"><span class="badge badge-bad">found by running</span>'
            f'<span>{esc(d["found"])}</span></div>'
            f'<div class="degen-row"><span class="badge badge-fix">fix (one uniform law)</span>'
            f'<span>{esc(d["fix"])}</span></div></div>'
        )

    # viz
    v = m.get("viz")
    if v:
        if v["kind"] == "bars":
            inner = svg_bars(v["title"], v["items"], v.get("unit", ""))
        elif v["kind"] == "lineages":
            inner = svg_lineages(v["a"], v["b"])
        elif v["kind"] == "multi":
            inner = "".join(
                f'<div class="vizcell">{svg_bars(b["title"], b["items"], b.get("unit",""))}</div>'
                for b in v["blocks"]
            )
        else:
            inner = ""
        parts.append(f'<div class="viz">{inner}</div>')

    if m.get("limit"):
        parts.append(f'<div class="limit"><span class="badge badge-lim">the limit '
                     f'this exposed</span>{esc(m["limit"])}</div>')
    if m.get("note"):
        parts.append(f'<p class="note">{esc(m["note"])}</p>')

    parts.append(f'<div class="observed"><span class="obs-label">observed</span>'
                 f'{esc(m["observed"])}</div>')
    parts.append("</section>")
    return "".join(parts)


# ---------------------------------------------------------------------------
# page assembly
# ---------------------------------------------------------------------------
CSS = """
*{box-sizing:border-box}
body{margin:0;background:%(bg)s;color:%(text)s;
  font-family:ui-monospace,"SF Mono",Menlo,Consolas,monospace;
  font-size:14px;line-height:1.6;-webkit-font-smoothing:antialiased}
a{color:%(blue)s}
.wrap{max-width:1000px;margin:0 auto;padding:0 24px 80px}
header.top{padding:48px 24px 24px;max-width:1000px;margin:0 auto}
header.top .title{font-size:30px;font-weight:700;letter-spacing:3px}
header.top .sub{color:%(muted)s;margin-top:4px}
header.top .span{color:%(dim)s;font-size:12px;margin-top:10px}
header.top .thesis{color:%(text)s;max-width:760px;margin-top:18px;
  border-left:2px solid %(border)s;padding-left:16px}
.rail{position:sticky;top:0;z-index:10;background:%(bg)sf2;
  backdrop-filter:blur(6px);border-bottom:1px solid %(border)s;
  display:flex;gap:8px;padding:12px 24px;max-width:1000px;margin:0 auto;
  flex-wrap:wrap;align-items:center}
.rail .rlabel{color:%(dim)s;font-size:11px;margin-right:4px}
.rail a{text-decoration:none;color:%(muted)s;border:1px solid %(border)s;
  border-radius:999px;padding:4px 12px;font-size:12px;transition:.15s}
.rail a:hover{color:%(text)s;border-color:%(muted)s}
.rail a.active{color:%(bg)s;background:%(text)s;border-color:%(text)s}
.section-title{color:%(muted)s;font-size:11px;text-transform:uppercase;
  letter-spacing:1.5px;margin:44px 0 16px}
.card{background:%(panel)s;border:1px solid %(border)s;border-radius:10px;
  padding:20px 22px;margin-bottom:18px}
.inv-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px}
.inv b{color:%(text)s}.inv span{color:%(muted)s;display:block;font-size:12px}
.gene-cap{color:%(dim)s;font-size:12px;margin-top:6px}
.srow,.prow{display:flex;gap:16px;align-items:flex-start;padding:12px 0;
  border-top:1px solid %(grid)s}
.srow:first-child,.prow:first-child{border-top:0}
.stag{min-width:42px;font-weight:700;font-size:13px;padding-top:2px}
.sgroups{display:flex;gap:18px;flex-wrap:wrap;flex:1}
.sgroup{min-width:0}
.sglabel{color:%(dim)s;font-size:10px;text-transform:uppercase;
  letter-spacing:1px;margin-bottom:6px}
.schips{display:flex;gap:6px;flex-wrap:wrap}
.chip{border:1px solid %(border)s;border-radius:5px;padding:3px 8px;
  font-size:11px;color:%(muted)s;white-space:nowrap}
.chip-base{color:%(muted)s;opacity:.75}
.chip-junk{color:%(junk)s;border-style:dashed;opacity:.6}
.pchips{display:flex;gap:4px;flex-wrap:wrap;align-items:center;flex:1}
.pchip{border:1px solid %(border)s;border-radius:5px;padding:3px 8px;font-size:11px}
.pchip-base{color:%(muted)s;opacity:.7}
.parrow{color:%(dim)s;margin:0 1px}
.pcount{color:%(dim)s;font-size:11px;min-width:64px;text-align:right}
.band{border:1px solid %(border)s;border-left:3px solid var(--acc);
  border-radius:10px;background:%(panel)s;padding:24px 26px;margin-bottom:22px;
  scroll-margin-top:64px}
.band-head{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.band-tag{color:%(bg)s;font-weight:700;font-size:12px;border-radius:5px;
  padding:2px 9px}
.band-head h2{margin:0;font-size:19px}
.band-date{color:%(dim)s;font-size:12px;margin-left:auto}
.lede{color:%(text)s;max-width:820px}
.commits{list-style:none;padding:0;margin:14px 0;border-top:1px solid %(grid)s}
.commits li{padding:7px 0;border-bottom:1px solid %(grid)s;color:%(muted)s;font-size:12.5px}
.commits code{font-size:12px;margin-right:8px}
.degen{background:%(bg)s;border:1px solid %(border)s;border-radius:8px;
  padding:14px 16px;margin:14px 0}
.degen-h{color:%(red)s;font-weight:700;font-size:12px;text-transform:uppercase;
  letter-spacing:.5px;margin-bottom:10px}
.degen-row{display:flex;gap:12px;margin-top:8px;align-items:flex-start}
.degen-row>span:last-child{color:%(text)s;font-size:13px}
.badge{flex:none;font-size:10px;text-transform:uppercase;letter-spacing:.5px;
  border-radius:4px;padding:3px 8px;white-space:nowrap;font-weight:600}
.badge-bad{color:%(red)s;border:1px solid %(red)s44}
.badge-fix{color:%(green)s;border:1px solid %(green)s44}
.badge-lim{color:%(amber)s;border:1px solid %(amber)s44;margin-right:10px}
.viz{margin:18px 0;display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));
  gap:18px}
.viz>svg,.vizcell{background:%(bg)s;border:1px solid %(border)s;border-radius:8px;padding:14px}
.limit{background:%(bg)s;border:1px solid %(amber)s33;border-radius:8px;
  padding:13px 15px;margin:14px 0;color:%(text)s;font-size:13px}
.note{color:%(dim)s;font-size:12.5px;font-style:italic}
.observed{margin-top:16px;border-top:1px solid %(grid)s;padding-top:14px;
  color:%(text)s;font-size:13px}
.obs-label{display:inline-block;color:%(green)s;font-size:10px;text-transform:uppercase;
  letter-spacing:1px;border:1px solid %(green)s44;border-radius:4px;
  padding:2px 8px;margin-right:10px}
table.degen-tbl{width:100%%;border-collapse:collapse;font-size:12.5px}
table.degen-tbl th{color:%(dim)s;text-align:left;font-weight:600;
  text-transform:uppercase;letter-spacing:.5px;font-size:10px;
  padding:0 12px 8px;border-bottom:1px solid %(border)s}
table.degen-tbl td{padding:11px 12px;border-bottom:1px solid %(grid)s;
  vertical-align:top;color:%(muted)s}
table.degen-tbl td.after{color:%(text)s}
footer{color:%(dim)s;font-size:12px;margin-top:40px;border-top:1px solid %(border)s;
  padding-top:18px}
footer code{color:%(muted)s}
""" % C

JS = """
const ids=[...document.querySelectorAll('.rail a')].map(a=>a.getAttribute('href').slice(1));
const links=Object.fromEntries([...document.querySelectorAll('.rail a')]
  .map(a=>[a.getAttribute('href').slice(1),a]));
const io=new IntersectionObserver((es)=>{es.forEach(e=>{
  if(e.isIntersecting){Object.values(links).forEach(l=>l.classList.remove('active'));
    const l=links[e.target.id];if(l)l.classList.add('active');}});},
  {rootMargin:'-45% 0px -50% 0px'});
ids.forEach(id=>{const el=document.getElementById(id);if(el)io.observe(el);});
"""


def build_html():
    rail_items = [("premise", "premise"), ("substrate", "substrate"),
                  ("pipeline", "pipeline")] + [(m["tag"], m["id"]) for m in MILESTONES] + \
                 [("pattern", "pattern")]
    rail = "".join(f'<a href="#{i}">{esc(t)}</a>' for t, i in rail_items)

    inv = "".join(f'<div class="inv"><b>{esc(t)}</b><span>{esc(d)}</span></div>'
                  for t, d in INVARIANTS)

    substrate = "".join(substrate_block(m) for m in SUBSTRATE)
    pipeline = "".join(pipeline_block(t, p, n, a) for t, p, n, a in PIPELINE)
    bands = "".join(milestone_band(m) for m in MILESTONES)

    rows = "".join(
        f'<tr><td style="color:{c};font-weight:700">#{n}</td><td>{esc(name)}</td>'
        f'<td>{esc(found)}</td><td style="color:{c}">{esc(fix)}</td>'
        f'<td class="after">{esc(after)}</td></tr>'
        for n, name, found, fix, after, c in DEGEN_TABLE
    )

    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Simolution — the journey</title><style>{CSS}</style></head><body>
<header class="top">
  <div class="title">{esc(HEADER['title'])}</div>
  <div class="sub">{esc(HEADER['subtitle'])}</div>
  <div class="span">{esc(HEADER['span'])}</div>
  <div class="thesis">{esc(HEADER['thesis'])}</div>
</header>
<nav class="rail"><span class="rlabel">jump:</span>{rail}</nav>
<div class="wrap">

  <div class="section-title" id="premise">the premise</div>
  <div class="card"><div class="inv-grid">{inv}</div></div>
  <div class="card">
    <div class="sglabel">one gene = one 32-bit int = one weighted connection</div>
    {svg_gene()}
    <div class="gene-cap">SrcType 0=sensor 1=internal · DstType 0=internal 1=action ·
      IDs wrap modulo the type count, so every 32-bit int is a legal gene (no mutation
      can be rejected) · Weight = signed int16 × (4.0 / 32767), linear, unclamped.</div>
  </div>

  <div class="section-title" id="substrate">the substrate grew, the laws stayed</div>
  <div class="card">{substrate}</div>

  <div class="section-title" id="pipeline">the tick pipeline (locked order; mechanics added as phases)</div>
  <div class="card">{pipeline}</div>

  <div class="section-title">the milestones</div>
  {bands}

  <div class="section-title" id="pattern">the recurring pattern</div>
  <div class="card">
    <p class="lede">Three degeneracies, each found by running the thing, each closed
      by adding one uniform thermodynamic law — never by tuning toward a desired
      outcome. Then one structural finding: a well-mixed world is a monoculture
      machine, and space is what gives diversity somewhere to hide (1 → 6 lineages).</p>
    <table class="degen-tbl"><thead><tr>
      <th>#</th><th>degeneracy</th><th>observed</th><th>uniform law added</th><th>after</th>
    </tr></thead><tbody>{rows}</tbody></table>
  </div>

  <footer>
    Facts only — narrative left to the writer. Every number is from a committed run
    report, an ADR, or <code>KernelConfig</code>. Sources: <code>docs/story/journey.md</code>,
    <code>docs/contract/contract-v0..v3.md</code>, <code>docs/decisions/0001..0020</code>,
    <code>runs/{{long-seed7,evolve,big,baseline}}.txt</code>.
    Generated by <code>tools/journey.py</code>.
  </footer>
</div>
<script>{JS}</script>
</body></html>"""


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "docs/story/journey.html"
    with open(out, "w", encoding="utf-8") as f:
        f.write(build_html())
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
