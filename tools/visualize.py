#!/usr/bin/env python3
"""Render a Simolution run into a self-contained HTML dashboard.

Reads a report (.txt) and its per-unit .units.csv sidecar, emits a single
HTML file with inline data and vanilla-JS canvas charts — no server, no pip
installs, no CDN. Open the file directly in any browser.

Usage:
    python3 tools/visualize.py runs/baseline.txt [out.html]

If the output path is omitted, the report's extension is replaced with .html.
The CSV sidecar is found by replacing the report's extension with .units.csv,
matching the kernel's own output convention (docs/specs/report-v3.md).
"""

import csv
import json
import sys
from pathlib import Path


def units_csv_for(report_path: Path) -> Path:
    return report_path.with_suffix(".units.csv")


def wiring_csv_for(report_path: Path) -> Path:
    return report_path.with_suffix(".wiring.csv")


def parse_wiring(csv_path: Path) -> dict:
    wiring: dict[int, list] = {}
    if not csv_path.exists():
        return wiring
    with csv_path.open() as f:
        for row in csv.DictReader(f):
            u = int(row["unit"])
            wiring.setdefault(u, []).append(
                [row["src_name"], row["dst_name"], round(float(row["weight"]), 2), int(row["meaningful"])]
            )
    return wiring


def parse_report(report_path: Path) -> dict:
    meta = {}
    for line in report_path.read_text().splitlines():
        if ":" in line and not line.startswith("#") and not line.startswith("##"):
            key, _, value = line.partition(":")
            meta[key.strip()] = value.strip()
    return meta


def parse_units(csv_path: Path) -> list[dict]:
    rows = []
    with csv_path.open() as f:
        for row in csv.DictReader(f):
            rows.append(
                {
                    "unit": int(row["unit"]),
                    "dt": int(row["death_tick"]),
                    "ls": int(row["lifespan"]),
                    "al": int(row["alive"]),
                    "br": float(row["mean_burn_rate"]),
                    "mc": int(row["meaningful_connections"]),
                    "tp": int(row["total_propagations"]),
                    "rg": row["regime"],
                    "rw": int(row["rand_wired"]),
                    "rc": int(row["reachable"]),
                }
            )
    return rows


def build_html(meta: dict, units: list[dict], wiring: dict, report_name: str) -> str:
    payload = json.dumps({"meta": meta, "units": units, "wiring": wiring})
    return _TEMPLATE.replace("__TITLE__", report_name).replace("__DATA__", payload)


_TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Simolution — __TITLE__</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #0d1117; color: #e6edf3;
         font: 14px/1.5 ui-monospace, "SF Mono", Menlo, Consolas, monospace; }
  header { padding: 24px 28px 8px; }
  h1 { margin: 0 0 4px; font-size: 18px; letter-spacing: .5px; }
  .sub { color: #7d8590; font-size: 12px; }
  .cards { display: flex; flex-wrap: wrap; gap: 12px; padding: 16px 28px; }
  .card { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
          padding: 12px 16px; min-width: 120px; }
  .card .v { font-size: 20px; font-weight: 600; }
  .card .k { color: #7d8590; font-size: 11px; text-transform: uppercase;
             letter-spacing: .6px; margin-top: 2px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
          gap: 20px; padding: 12px 28px 40px; }
  .panel { background: #161b22; border: 1px solid #30363d; border-radius: 10px;
           padding: 16px 18px 18px; }
  .panel h2 { margin: 0 0 2px; font-size: 13px; font-weight: 600; }
  .panel .desc { color: #7d8590; font-size: 11px; margin-bottom: 10px; }
  canvas { width: 100%; height: auto; display: block; }
  .legend { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 8px; font-size: 11px; color: #7d8590; }
  .legend span { display: inline-flex; align-items: center; gap: 5px; }
  .dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
  footer { color: #484f58; font-size: 11px; padding: 0 28px 32px; }
</style>
</head>
<body>
<header>
  <h1>Simolution run — __TITLE__</h1>
  <div class="sub" id="sub"></div>
</header>
<div class="cards" id="cards"></div>
<div class="grid">
  <div class="panel"><h2>Survival curve</h2>
    <div class="desc">Units alive over time. The flask empties; survivors plateau.</div>
    <canvas id="survival" width="700" height="320"></canvas></div>
  <div class="panel"><h2>Lifespan distribution</h2>
    <div class="desc">How long units lived (ticks). Right bar = survivors.</div>
    <canvas id="lifehist" width="700" height="320"></canvas></div>
  <div class="panel"><h2>Burn rate vs lifespan</h2>
    <div class="desc">Energy spent per tick (x) against ticks lived (y). Cheap burners last.</div>
    <canvas id="burnscatter" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#3fb950"></i>fixed-point</span>
      <span><i class="dot" style="background:#58a6ff"></i>bounded</span>
      <span><i class="dot" style="background:#f85149"></i>divergent</span>
    </div></div>
  <div class="panel"><h2>Meaningful wiring vs lifespan</h2>
    <div class="desc">Active connections (x) against ticks lived (y). More wiring, faster death.</div>
    <canvas id="wirescatter" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#bc8cff"></i>rand-wired (noise-driven)</span>
      <span><i class="dot" style="background:#7d8590"></i>autonomous</span>
    </div></div>
  <div class="panel" style="grid-column: 1 / -1;"><h2>Unit signature — wiring</h2>
    <div class="desc">Pick a unit to see its connection graph. Sensors left, internals middle, action right.
      Green = positive weight, red = negative; thickness = magnitude; ↻ = self-loop.</div>
    <div style="display:flex; gap:14px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
      <label>unit <select id="usel"></select></label>
      <label style="font-size:12px; color:#7d8590;"><input type="checkbox" id="showjunk"> show junk connections</label>
      <span id="uinfo" style="font-size:12px; color:#7d8590;"></span>
    </div>
    <svg id="wiring" viewBox="0 0 700 240" width="100%"></svg></div>
</div>
<footer>Generated by tools/visualize.py from deterministic run output. No external resources.</footer>

<script>
const DATA = __DATA__;
const M = DATA.meta, U = DATA.units;
const TICKS = +(M["ticks"] || 1000), N = U.length;
const COL = { "fixed-point": "#3fb950", "bounded": "#58a6ff", "divergent": "#f85149" };

document.getElementById("sub").textContent =
  `seed ${M["seed"]} · ${M["units"]} units · ${M["ticks"]} ticks · ${M["genome-source"]||""}`;

function card(k, v) {
  const d = document.createElement("div"); d.className = "card";
  d.innerHTML = `<div class="v">${v}</div><div class="k">${k}</div>`;
  document.getElementById("cards").appendChild(d);
}
const aliveEnd = U.filter(u => u.al === 1).length;
const deaths = U.filter(u => u.dt >= 0).map(u => u.dt).sort((a,b)=>a-b);
const medianDeath = deaths.length ? deaths[deaths.length>>1] : -1;
card("alive at end", `${aliveEnd}/${N}`);
card("median death", medianDeath);
card("first death", deaths.length ? deaths[0] : "—");
card("divergent", U.filter(u=>u.rg==="divergent").length);
if (M["energy-audit-error"] !== undefined) card("audit error", (+M["energy-audit-error"]).toExponential(1));

function setup(id) {
  const c = document.getElementById(id), dpr = window.devicePixelRatio || 1;
  const w = c.width, h = c.height;
  c.width = w*dpr; c.height = h*dpr;
  c.style.height = (h * (c.clientWidth / w)) + "px";
  const x = c.getContext("2d"); x.scale(dpr, dpr);
  x.clearRect(0,0,w,h);
  return { x, w, h, pad: 46 };
}
function axes(g, xlabel, ylabel, xmax, ymax) {
  const { x, w, h, pad } = g;
  x.strokeStyle = "#30363d"; x.fillStyle = "#7d8590"; x.lineWidth = 1;
  x.font = "10px ui-monospace, monospace";
  x.beginPath(); x.moveTo(pad, 10); x.lineTo(pad, h-pad); x.lineTo(w-12, h-pad); x.stroke();
  for (let i=0;i<=4;i++){
    const fx = pad + (w-12-pad)*i/4, fy = h-pad - (h-pad-10)*i/4;
    x.fillText((xmax*i/4).toFixed(xmax<10?1:0), fx-8, h-pad+14);
    x.fillText((ymax*i/4).toFixed(0), 6, fy+3);
    x.strokeStyle = "#21262d"; x.beginPath(); x.moveTo(pad,fy); x.lineTo(w-12,fy); x.stroke();
  }
  x.fillStyle = "#7d8590";
  x.fillText(xlabel, w/2-20, h-8);
  x.save(); x.translate(12, h/2+20); x.rotate(-Math.PI/2); x.fillText(ylabel, 0, 0); x.restore();
}
const px = (g,v,vmax)=> g.pad + (g.w-12-g.pad)*v/vmax;
const py = (g,v,vmax)=> (g.h-g.pad) - (g.h-g.pad-10)*v/vmax;

// survival curve
(function(){
  const g = setup("survival");
  const alive = new Array(TICKS+1).fill(N);
  for (const u of U) if (u.dt>=0) for (let t=u.dt;t<=TICKS;t++) alive[t]--;
  axes(g, "tick", "alive", TICKS, N);
  g.x.strokeStyle = "#58a6ff"; g.x.lineWidth = 2; g.x.beginPath();
  for (let t=0;t<=TICKS;t++){ const X=px(g,t,TICKS), Y=py(g,alive[t],N); t?g.x.lineTo(X,Y):g.x.moveTo(X,Y); }
  g.x.stroke();
  g.x.lineTo(px(g,TICKS,TICKS), g.h-g.pad); g.x.lineTo(px(g,0,TICKS), g.h-g.pad);
  g.x.fillStyle = "rgba(88,166,255,0.10)"; g.x.fill();
})();

// lifespan histogram
(function(){
  const g = setup("lifehist"), bins = 24;
  const counts = new Array(bins).fill(0);
  for (const u of U){ let b = Math.min(bins-1, Math.floor(u.ls/(TICKS+1)*bins)); counts[b]++; }
  const ymax = Math.max(...counts);
  axes(g, "lifespan", "count", TICKS, ymax);
  const bw = (g.w-12-g.pad)/bins;
  for (let i=0;i<bins;i++){
    const X = g.pad + bw*i, Y = py(g, counts[i], ymax);
    g.x.fillStyle = "#3fb950"; g.x.fillRect(X+1, Y, bw-2, (g.h-g.pad)-Y);
  }
})();

function scatter(id, xf, xlabel, xmax, colorf){
  const g = setup(id);
  const ymax = TICKS;
  axes(g, xlabel, "lifespan", xmax, ymax);
  for (const u of U){
    g.x.fillStyle = colorf(u);
    g.x.globalAlpha = 0.8;
    g.x.beginPath(); g.x.arc(px(g, Math.min(xf(u),xmax), xmax), py(g, u.ls, ymax), 4, 0, 7); g.x.fill();
  }
  g.x.globalAlpha = 1;
}
scatter("burnscatter", u=>u.br, "mean burn rate", Math.max(...U.map(u=>u.br))*1.05, u=>COL[u.rg]||"#58a6ff");
scatter("wirescatter", u=>u.mc, "meaningful connections", Math.max(...U.map(u=>u.mc))+1,
        u=> u.rw? "#bc8cff" : "#7d8590");

// wiring signature
const WIRING = DATA.wiring || {};
const NS="http://www.w3.org/2000/svg";
const POS={CONST:[80,55],RAND:[80,170],ADD:[350,32],MUL:[350,84],CLAMP:[350,136],DELAY:[350,188],THRESH:[350,224],ACTION_Y:[620,120]};
const KIND={CONST:"s",RAND:"s",ADD:"i",MUL:"i",CLAMP:"i",DELAY:"i",THRESH:"i",ACTION_Y:"a"};
const NFILL={s:"#7f77dd",i:"#378add",a:"#1d9e75"};
function jpos(name){ // junk nodes laid out along the bottom
  if(POS[name]) return POS[name];
  const cols={"JUNK-S":120,"JUNK-I":350,"JUNK-A":600};
  for(const k in cols){ if(name.startsWith(k)){ const n=+name.slice(k.length); return [cols[k]+n*28, 236]; } }
  return [350,236];
}
function el(n,a){const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);return e;}
const usel=document.getElementById("usel");
Object.keys(WIRING).map(Number).sort((a,b)=>a-b).forEach(u=>{
  const o=document.createElement("option"); o.value=u; o.textContent="unit "+u; usel.appendChild(o);
});
function drawWiring(){
  const u=+usel.value, showJunk=document.getElementById("showjunk").checked;
  const edges=(WIRING[u]||[]).filter(e=> showJunk || e[3]===1);
  const svg=document.getElementById("wiring"); svg.innerHTML="";
  const defs=el("defs",{});
  for(const[c,id]of[["#3fb950","ap"],["#f85149","an"],["#484f58","aj"]]){
    const m=el("marker",{id,markerWidth:7,markerHeight:7,refX:6,refY:3,orient:"auto"});
    m.appendChild(el("path",{d:"M0,0 L6,3 L0,6 Z",fill:c})); defs.appendChild(m);
  }
  svg.appendChild(defs);
  for(const[s,d,w,m] of edges){
    const junk=m!==1, col=junk?"#484f58":(w>=0?"#3fb950":"#f85149"), mk=junk?"aj":(w>=0?"ap":"an");
    const[x1,y1]=jpos(s),[x2,y2]=jpos(d), sw=junk?0.6:(0.8+Math.abs(w)*0.6).toFixed(1);
    let p;
    if(s===d) p=el("path",{d:`M${x1-9},${y1-7} C${x1-44},${y1-44} ${x1+44},${y1-44} ${x1+9},${y1-7}`,fill:"none",stroke:col,"stroke-width":sw,"marker-end":`url(#${mk})`,opacity:junk?.3:.85});
    else { const mx=(x1+x2)/2,my=(y1+y2)/2-20; p=el("path",{d:`M${x1+15},${y1} Q${mx},${my} ${x2-17},${y2}`,fill:"none",stroke:col,"stroke-width":sw,"marker-end":`url(#${mk})`,opacity:junk?.28:.8}); }
    svg.appendChild(p);
  }
  for(const name in POS){ const[x,y]=POS[name];
    svg.appendChild(el("circle",{cx:x,cy:y,r:17,fill:NFILL[KIND[name]],stroke:"#0d1117","stroke-width":1.5}));
    const t=el("text",{x,y:y+3,"text-anchor":"middle","font-size":9,fill:"#0d1117","font-weight":600,"font-family":"ui-monospace,monospace"});
    t.textContent=name==="ACTION_Y"?"ACT":name.slice(0,5); svg.appendChild(t);
  }
  const ud=U.find(x=>x.unit===u)||{};
  document.getElementById("uinfo").textContent=
    `${edges.length} shown · ${ud.mc} meaningful · lifespan ${ud.ls} · ${ud.rg}` +
    (ud.rc? " · reachable":"") + (ud.rw? " · rand-wired":"");
}
usel.addEventListener("change",drawWiring);
document.getElementById("showjunk").addEventListener("change",drawWiring);
if(usel.options.length){ drawWiring(); }
</script>
</body>
</html>
"""


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 1
    report_path = Path(argv[1])
    if not report_path.exists():
        print(f"report not found: {report_path}")
        return 1
    csv_path = units_csv_for(report_path)
    if not csv_path.exists():
        print(f"units csv not found: {csv_path} (run with --out to produce it)")
        return 1
    out_path = Path(argv[2]) if len(argv) > 2 else report_path.with_suffix(".html")

    meta = parse_report(report_path)
    units = parse_units(csv_path)
    wiring = parse_wiring(wiring_csv_for(report_path))
    out_path.write_text(build_html(meta, units, wiring, report_path.name))
    print(f"wrote {out_path} ({len(units)} units, wiring for {len(wiring)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
