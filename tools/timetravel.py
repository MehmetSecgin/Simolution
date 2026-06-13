#!/usr/bin/env python3
"""Render a Simolution replayed tick (report-v8) as a self-contained HTML
"time-travel" state page: the lattice at tick T (resource heatmap + units by
lineage, click a cell to inspect), a per-unit table, the selected unit's evolved
circuit, and run-wide aggregate charts (population / energy) from metrics.csv
with a marker at T.

Workflow:
    # 1. replay the JVM to dump tick T (lattice + units + circuits)
    ./gradlew run --args="--replay runs/foo.obs --at 600 --snapshot-out runs/foo.obs/snap-600.txt"
    # 2. render it (also reads sibling metrics.csv / events.jsonl in the .obs dir)
    python3 tools/timetravel.py runs/foo.obs/snap-600.txt   ->  snap-600.html

Stdlib only, no dependencies. Aggregate panels can also be queried directly with
DuckDB (see docs/specs/report-v8.md); this page is the visual surface.
"""
import json
import sys
from pathlib import Path


def parse_dump(path):
    tick = world = None
    resource = ""
    units = []
    wires = {}
    with open(path, "r") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line:
                continue
            tag, _, rest = line.partition(" ")
            if tag == "tick":
                tick = int(rest)
            elif tag == "world":
                world = int(rest)
            elif tag == "resource":
                resource = rest
            elif tag == "unit":
                slot, cell, energy, damage, gen, lineage = rest.split()
                units.append({
                    "slot": int(slot), "cell": int(cell), "energy": float(energy),
                    "damage": float(damage), "gen": int(gen), "lineage": int(lineage),
                })
            elif tag == "wire":
                slot, src_local, src_name, dst_local, dst_name, weight = rest.split()
                wires.setdefault(int(slot), []).append({
                    "srcLocal": int(src_local), "src": src_name,
                    "dstLocal": int(dst_local), "dst": dst_name,
                    "weight": float(weight),
                })
    if world is None:
        raise SystemExit("no 'world' header in " + str(path))
    return tick, world, resource, units, wires


def parse_metrics(path):
    if not path.exists():
        return []
    rows = []
    with open(path, "r") as fh:
        header = fh.readline().rstrip("\n").split(",")
        for raw in fh:
            vals = raw.rstrip("\n").split(",")
            if len(vals) != len(header):
                continue
            rows.append({h: float(v) for h, v in zip(header, vals)})
    return rows


def parse_events(path):
    if not path.exists():
        return {"counts": {}, "extinctions": []}
    counts = {}
    extinctions = []
    with open(path, "r") as fh:
        for raw in fh:
            raw = raw.strip()
            if not raw:
                continue
            ev = json.loads(raw)
            counts[ev["ev"]] = counts.get(ev["ev"], 0) + 1
            if ev["ev"] == "EXTINCTION":
                extinctions.append(ev)
    extinctions.sort(key=lambda e: e["lastTick"])
    return {"counts": counts, "extinctions": extinctions[-40:]}


HTML = """<!doctype html><html><head><meta charset="utf-8">
<title>Simolution time-travel - tick {tick}</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{ background:#0d1117; color:#c9d1d9; font:14px/1.5 ui-monospace,Menlo,Consolas,monospace;
         margin:0; padding:20px; }}
  h1 {{ font-size:15px; font-weight:600; margin:0 0 4px; color:#e6edf3; }}
  h2 {{ font-size:13px; font-weight:600; margin:18px 0 8px; color:#e6edf3; }}
  .sub {{ color:#8b949e; margin:0 0 14px; }}
  .wrap {{ display:flex; gap:24px; flex-wrap:wrap; align-items:flex-start; }}
  canvas {{ background:#000; image-rendering:pixelated; border:1px solid #30363d; }}
  .panel {{ min-width:300px; max-width:560px; }}
  .stat b {{ color:#e6edf3; font-weight:600; }}
  .stat {{ color:#8b949e; }}
  table {{ border-collapse:collapse; font-size:12px; width:100%; }}
  th,td {{ text-align:right; padding:2px 8px; border-bottom:1px solid #21262d; }}
  th {{ color:#8b949e; font-weight:600; }}
  tr.sel td {{ background:#1f6feb33; }}
  tr.unit:hover td {{ background:#30363d55; cursor:pointer; }}
  .scroll {{ max-height:320px; overflow:auto; border:1px solid #30363d; border-radius:6px; }}
  .chip {{ display:inline-block; padding:2px 8px; margin:2px; border:1px solid #30363d;
          border-radius:10px; color:#8b949e; }}
  .chip b {{ color:#e6edf3; }}
</style></head><body>
<h1>time-travel &mdash; tick {tick} &mdash; {world}x{world} torus</h1>
<p class="sub">replayed full-fidelity state. click a cell or table row to inspect a unit's evolved circuit.</p>

<div class="wrap">
  <div>
    <canvas id="cv"></canvas>
    <div class="row stat" style="margin-top:8px">
      alive <b id="alive">0</b> &nbsp; lineages <b id="lin">0</b> &nbsp;
      selected slot <b id="selslot">&mdash;</b>
    </div>
  </div>
  <div class="panel">
    <h2>events (whole run)</h2>
    <div id="evchips"></div>
    <h2>population &amp; energy over time</h2>
    <canvas id="chart" width="540" height="180"></canvas>
    <div class="stat">vertical marker = tick {tick}. green = population, amber = total energy (scaled).</div>
  </div>
</div>

<div class="wrap">
  <div class="panel">
    <h2>living units</h2>
    <div class="scroll"><table id="utab"><thead><tr>
      <th>slot</th><th>cell</th><th>lineage</th><th>gen</th><th>energy</th><th>damage</th><th>conns</th>
    </tr></thead><tbody></tbody></table></div>
  </div>
  <div class="panel">
    <h2>circuit of slot <span id="csl">&mdash;</span></h2>
    <canvas id="circuit" width="540" height="260" style="border:1px solid #30363d;background:#000"></canvas>
    <div class="scroll" style="margin-top:8px"><table id="wtab"><thead><tr>
      <th>src</th><th>&rarr; dst</th><th>weight</th>
    </tr></thead><tbody></tbody></table></div>
  </div>
</div>

<script>
const W = {world};
const RES = {resource!r};
const UNITS = {units};
const WIRES = {wires};
const METRICS = {metrics};
const EVENTS = {events};
const T = {tick};

const cv = document.getElementById('cv');
const ctx = cv.getContext('2d');
const px = Math.max(2, Math.floor(640 / W));
cv.width = W * px; cv.height = W * px;
const unitByCell = {{}};
for (const u of UNITS) unitByCell[u.cell] = u;

function linColor(id) {{ const h = (id * 137.508) % 360; return `hsl(${{h}},72%,58%)`; }}
function resColor(level) {{ const t = level/9; return `rgb(${{Math.round(t*18)}},${{Math.round(24+t*108)}},${{Math.round(t*46)}})`; }}

let selected = UNITS.length ? UNITS[0].slot : -1;

function drawLattice() {{
  for (let c = 0; c < RES.length; c++) {{
    ctx.fillStyle = resColor(RES.charCodeAt(c) - 48);
    ctx.fillRect((c % W)*px, ((c/W)|0)*px, px, px);
  }}
  const lineages = new Set();
  for (const u of UNITS) {{
    ctx.fillStyle = linColor(u.lineage);
    ctx.fillRect((u.cell % W)*px, ((u.cell/W)|0)*px, px, px);
    lineages.add(u.lineage);
  }}
  const sel = UNITS.find(u => u.slot === selected);
  if (sel) {{
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2;
    ctx.strokeRect((sel.cell % W)*px+1, ((sel.cell/W)|0)*px+1, px-2, px-2);
  }}
  document.getElementById('alive').textContent = UNITS.length;
  document.getElementById('lin').textContent = lineages.size;
  document.getElementById('selslot').textContent = selected < 0 ? '—' : selected;
}}

cv.onclick = (e) => {{
  const r = cv.getBoundingClientRect();
  const x = Math.floor((e.clientX - r.left)/px), y = Math.floor((e.clientY - r.top)/px);
  const u = unitByCell[y*W + x];
  if (u) {{ selected = u.slot; render(); }}
}};

function nodeKind(local) {{
  // substrate layout: [sensors | internals | actions]; thresholds from NodeLayout
  if (local < 8) return 0;           // sensors region (sensors+junk)
  if (local < 16) return 1;          // internals region
  return 2;                          // actions region
}}

function drawCircuit() {{
  const c = document.getElementById('circuit');
  const g = c.getContext('2d');
  g.clearRect(0,0,c.width,c.height);
  document.getElementById('csl').textContent = selected < 0 ? '—' : selected;
  const ws = WIRES[selected] || [];
  // place each distinct node in a column by kind (sensor/internal/action)
  const cols = [70, 270, 470];
  const colName = ['sensor','internal','action'];
  const seen = {{}}; const colCount = [0,0,0];
  function pos(local, name) {{
    const key = name;
    if (seen[key]) return seen[key];
    const k = nodeKind(local);
    const y = 30 + colCount[k]*30; colCount[k]++;
    const p = {{x: cols[k], y, name, k}};
    seen[key] = p; return p;
  }}
  for (const w of ws) {{ pos(w.srcLocal, w.src); pos(w.dstLocal, w.dst); }}
  // edges
  for (const w of ws) {{
    const a = seen[w.src], b = seen[w.dst];
    if (!a || !b) continue;
    g.strokeStyle = w.weight >= 0 ? 'rgba(63,185,80,0.7)' : 'rgba(248,81,73,0.7)';
    g.lineWidth = Math.min(4, 0.5 + Math.abs(w.weight)*1.5);
    g.beginPath(); g.moveTo(a.x+6, a.y); g.lineTo(b.x-6, b.y); g.stroke();
  }}
  // nodes
  const kindColor = ['#7f77dd','#378add','#1d9e75'];
  for (const key in seen) {{
    const p = seen[key];
    g.fillStyle = kindColor[p.k];
    g.beginPath(); g.arc(p.x, p.y, 5, 0, 7); g.fill();
    g.fillStyle = '#c9d1d9'; g.font = '11px monospace'; g.textAlign = p.k===2?'end':'start';
    g.fillText(p.name, p.k===2 ? p.x-10 : p.x+10, p.y+3);
  }}
  if (!ws.length) {{ g.fillStyle = '#8b949e'; g.fillText('no connections', 20, 20); }}
  // wire table
  const tb = document.querySelector('#wtab tbody'); tb.innerHTML = '';
  for (const w of ws) {{
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${{w.src}}</td><td>&rarr; ${{w.dst}}</td><td>${{w.weight.toFixed(4)}}</td>`;
    tb.appendChild(tr);
  }}
}}

function drawTable() {{
  const tb = document.querySelector('#utab tbody'); tb.innerHTML = '';
  const sorted = UNITS.slice().sort((a,b)=>b.energy-a.energy);
  for (const u of sorted) {{
    const tr = document.createElement('tr');
    tr.className = 'unit' + (u.slot===selected?' sel':'');
    const n = (WIRES[u.slot]||[]).length;
    tr.innerHTML = `<td>${{u.slot}}</td><td>${{u.cell}}</td><td>${{u.lineage}}</td><td>${{u.gen}}</td>`+
      `<td>${{u.energy.toFixed(1)}}</td><td>${{u.damage.toFixed(0)}}</td><td>${{n}}</td>`;
    tr.onclick = () => {{ selected = u.slot; render(); }};
    tb.appendChild(tr);
  }}
}}

function drawChips() {{
  const d = document.getElementById('evchips');
  const c = EVENTS.counts || {{}};
  let h = '';
  for (const k of ['BIRTH','DEATH','EXTINCTION']) h += `<span class="chip">${{k}} <b>${{c[k]||0}}</b></span>`;
  d.innerHTML = h;
}}

function drawChart() {{
  const c = document.getElementById('chart'); const g = c.getContext('2d');
  g.clearRect(0,0,c.width,c.height);
  if (!METRICS.length) {{ g.fillStyle='#8b949e'; g.fillText('no metrics.csv',20,20); return; }}
  const W2=c.width, H=c.height, pad=4;
  const maxT = METRICS[METRICS.length-1].tick;
  const maxPop = Math.max(...METRICS.map(m=>m.population), 1);
  const maxE = Math.max(...METRICS.map(m=>m.energy_total), 1);
  function line(key, max, color) {{
    g.strokeStyle=color; g.lineWidth=1.5; g.beginPath();
    METRICS.forEach((m,i)=>{{
      const x = pad + (m.tick/maxT)*(W2-2*pad);
      const y = H-pad - (m[key]/max)*(H-2*pad);
      i?g.lineTo(x,y):g.moveTo(x,y);
    }});
    g.stroke();
  }}
  line('energy_total', maxE, '#d29922');
  line('population', maxPop, '#3fb950');
  const xm = pad + (T/maxT)*(W2-2*pad);
  g.strokeStyle='#fff'; g.setLineDash([4,3]); g.beginPath();
  g.moveTo(xm,0); g.lineTo(xm,H); g.stroke(); g.setLineDash([]);
}}

function render() {{ drawLattice(); drawCircuit(); drawTable(); }}
drawChips(); drawChart(); render();
</script></body></html>
"""


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: python3 tools/timetravel.py <snapshot-dump>.txt")
    path = Path(sys.argv[1])
    tick, world, resource, units, wires = parse_dump(path)
    obs_dir = path.parent
    metrics = parse_metrics(obs_dir / "metrics.csv")
    events = parse_events(obs_dir / "events.jsonl")
    out = path.with_suffix(".html")
    html = HTML.format(
        tick=tick, world=world, resource=resource,
        units=json.dumps(units, separators=(",", ":")),
        wires=json.dumps(wires, separators=(",", ":")),
        metrics=json.dumps(metrics, separators=(",", ":")),
        events=json.dumps(events, separators=(",", ":")),
    )
    out.write_text(html)
    print(f"wrote {out} (tick {tick}, {world}x{world}, {len(units)} units, "
          f"{len(metrics)} metric rows, {sum(events['counts'].values())} events)")


if __name__ == "__main__":
    main()
