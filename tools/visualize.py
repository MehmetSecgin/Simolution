#!/usr/bin/env python3
"""Render a Simolution run into a self-contained HTML dashboard.

Reads a report (.txt) and its per-unit .units.csv sidecar, emits a single
HTML file with inline data and vanilla-JS canvas charts — no server, no pip
installs, no CDN. Open the file directly in any browser.

Usage:
    python3 tools/visualize.py runs/baseline.txt [out.html]

If the output path is omitted, the report's extension is replaced with .html.
The CSV sidecar is found by replacing the report's extension with .units.csv,
matching the kernel's own output convention (docs/specs/report-v5.md).
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


def _opt_int(row: dict, key: str, default: int = 0) -> int:
    return int(row[key]) if key in row and row[key] != "" else default


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
                    "ht": _opt_int(row, "harvest_ticks"),
                    "fe": float(row.get("final_energy", 0.0) or 0.0),
                    "ec": float(row.get("energy_consumed", 0.0) or 0.0),
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
  .cards { display: flex; flex-wrap: wrap; gap: 10px; padding: 16px 28px; }
  .card { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
          padding: 10px 14px; min-width: 110px; }
  .card .v { font-size: 19px; font-weight: 600; }
  .card .k { color: #7d8590; font-size: 10.5px; text-transform: uppercase;
             letter-spacing: .6px; margin-top: 2px; }
  .card .s { color: #586069; font-size: 10px; margin-top: 1px; }
  .card.accent { border-color: #1f6feb55; }
  .sectit { padding: 18px 28px 0; color: #7d8590; font-size: 11px;
            text-transform: uppercase; letter-spacing: 1px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
          gap: 20px; padding: 12px 28px 16px; }
  .panel { background: #161b22; border: 1px solid #30363d; border-radius: 10px;
           padding: 16px 18px 18px; }
  .panel h2 { margin: 0 0 2px; font-size: 13px; font-weight: 600; }
  .panel .desc { color: #7d8590; font-size: 11px; margin-bottom: 10px; }
  .panel .verdict { font-size: 11.5px; margin-top: 8px; padding: 6px 10px;
                    border-radius: 6px; background: #0d1117; border: 1px solid #21262d; }
  canvas { width: 100%; height: auto; display: block; }
  .legend { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 8px; font-size: 11px; color: #7d8590; }
  .legend span { display: inline-flex; align-items: center; gap: 5px; }
  .dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
  .sw { width: 11px; height: 11px; border-radius: 2px; display: inline-block; }
  footer { color: #484f58; font-size: 11px; padding: 0 28px 32px; }
  b.g{color:#3fb950} b.b{color:#58a6ff} b.r{color:#f85149} b.a{color:#d29922} b.p{color:#bc8cff}
</style>
</head>
<body>
<header>
  <h1>Simolution run — __TITLE__</h1>
  <div class="sub" id="sub"></div>
</header>
<div class="cards" id="cards"></div>

<div class="sectit">Open-system economy — reservoir → units → sink</div>
<div class="grid">
  <div class="panel"><h2>Conservation balance</h2>
    <div class="desc">Where every joule went. Left bar = sources (start + inflow), right = ends (units + reservoir + dissipated). Equal height = energy conserved.</div>
    <canvas id="balance" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="sw" style="background:#58a6ff"></i>unit energy</span>
      <span><i class="sw" style="background:#3fb950"></i>reservoir</span>
      <span><i class="sw" style="background:#d29922"></i>inflow (the sun)</span>
      <span><i class="sw" style="background:#f85149"></i>dissipated to sink</span>
    </div>
    <div class="verdict" id="balverdict"></div></div>
  <div class="panel"><h2>Did harvesting help you survive?</h2>
    <div class="desc">Fraction of each cohort still alive over time. Splits units by whether they EVER drove HARVEST positive. The open-system payoff lives here.</div>
    <canvas id="splitsurv" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#3fb950"></i>ever harvested</span>
      <span><i class="dot" style="background:#7d8590"></i>never harvested</span>
    </div>
    <div class="verdict" id="splitverdict"></div></div>
  <div class="panel"><h2>Harvest participation</h2>
    <div class="desc">Ticks each unit spent feeding (HARVEST output &gt; 0 while alive). Left grey bar = never fed.</div>
    <canvas id="harvhist" width="700" height="320"></canvas></div>
  <div class="panel"><h2>Feeding vs lifespan</h2>
    <div class="desc">Ticks fed (x) against ticks lived (y). Do eaters last? Colour = terminal regime.</div>
    <canvas id="harvscatter" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#3fb950"></i>fixed-point</span>
      <span><i class="dot" style="background:#58a6ff"></i>bounded</span>
      <span><i class="dot" style="background:#f85149"></i>divergent</span>
    </div></div>
</div>

<div class="sectit">Population dynamics</div>
<div class="grid">
  <div class="panel"><h2>Survival curve</h2>
    <div class="desc">Units alive over time. The flask empties; survivors plateau.</div>
    <canvas id="survival" width="700" height="320"></canvas></div>
  <div class="panel"><h2>Lifespan distribution</h2>
    <div class="desc">How long units lived (ticks). Right bar = survivors.</div>
    <canvas id="lifehist" width="700" height="320"></canvas></div>
  <div class="panel"><h2>Net burn rate vs lifespan</h2>
    <div class="desc">Net energy spent per tick (x) vs ticks lived (y). Left of the dashed line = net producers (harvested more than they burned).</div>
    <canvas id="burnscatter" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#3fb950"></i>fixed-point</span>
      <span><i class="dot" style="background:#58a6ff"></i>bounded</span>
      <span><i class="dot" style="background:#f85149"></i>divergent</span>
    </div></div>
  <div class="panel"><h2>Meaningful wiring vs lifespan</h2>
    <div class="desc">Active connections (x) against ticks lived (y). More wiring, more decay, faster death.</div>
    <canvas id="wirescatter" width="700" height="320"></canvas>
    <div class="legend">
      <span><i class="dot" style="background:#bc8cff"></i>rand-wired (noise-driven)</span>
      <span><i class="dot" style="background:#7d8590"></i>autonomous</span>
    </div></div>
  <div class="panel"><h2>Terminal regimes &amp; structure</h2>
    <div class="desc">How runs ended, and how many units are wired for survival.</div>
    <canvas id="regimebar" width="700" height="320"></canvas></div>
</div>

<div class="sectit">Per-unit signature</div>
<div class="grid">
  <div class="panel" style="grid-column: 1 / -1;"><h2>Unit circuit — signal flow</h2>
    <div class="desc">Only nodes this unit actually wires are drawn, placed by <b>signal depth</b> (distance from the sensors), not by node category —
      so position follows the flow. Colour still marks sensor/internal/action. Edge colour = weight sign (green +, red −), thickness = magnitude.
      <b>Feedback edges</b> (DELAY loops, cycles) route below, dashed. The <b class="b">live circuit</b> — the sensor→action core doing work — is bright; dead wiring faded.</div>
    <div style="display:flex; gap:14px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
      <label>unit <select id="usel"></select></label>
      <button id="prev">‹</button><button id="next">›</button>
      <label style="font-size:12px; color:#7d8590;"><input type="checkbox" id="showjunk"> show junk wiring</label>
      <button id="findharv" style="font-size:11px;">jump to a harvester</button>
    </div>
    <svg id="wiring" viewBox="0 0 720 300" width="100%"></svg>
    <div class="legend">
      <span><i class="dot" style="background:#3fb950"></i>positive</span>
      <span><i class="dot" style="background:#f85149"></i>negative</span>
      <span><i class="sw" style="background:#7f77dd"></i>sensor</span>
      <span><i class="sw" style="background:#378add"></i>internal</span>
      <span><i class="sw" style="background:#1d9e75"></i>action</span>
      <span style="color:#586069">dashed = feedback · faded = dead wiring</span>
    </div>
    <div class="verdict" id="uinfo"></div></div>
</div>
<footer>Generated by tools/visualize.py from deterministic run output. No external resources. Schema <span id="schema"></span>.</footer>

<script>
const DATA = __DATA__;
const M = DATA.meta, U = DATA.units;
const TICKS = +(M["ticks"] || 1000), N = U.length;
const COL = { "fixed-point": "#3fb950", "bounded": "#58a6ff", "divergent": "#f85149" };
const num = k => (M[k] === undefined ? NaN : +M[k]);
const fmt = v => !isFinite(v) ? "—" : Math.abs(v) >= 1000 ? Math.round(v).toLocaleString()
                : Math.abs(v) >= 1 ? v.toFixed(1) : v.toPrecision(2);

document.getElementById("sub").textContent =
  `kernel ${M["kernel"]||"?"} · seed ${M["seed"]} · ${M["units"]} units · ${M["ticks"]} ticks · ${M["genome-source"]||""}`;
document.getElementById("schema").textContent = M["schema"]||"?";

// ---- top cards ----
function card(k, v, sub, accent) {
  const d = document.createElement("div"); d.className = "card" + (accent?" accent":"");
  d.innerHTML = `<div class="v">${v}</div><div class="k">${k}</div>` + (sub?`<div class="s">${sub}</div>`:"");
  document.getElementById("cards").appendChild(d);
}
const aliveEnd = U.filter(u => u.al === 1).length;
const deaths = U.filter(u => u.dt >= 0).map(u => u.dt).sort((a,b)=>a-b);
const medianDeath = deaths.length ? deaths[deaths.length>>1] : -1;
const harvesters = U.filter(u => u.ht > 0).length;
card("alive at end", `${aliveEnd}/${N}`, `${(100*aliveEnd/N).toFixed(0)}% survived`, true);
card("intake total", fmt(num("intake-total")), "drawn from reservoir", true);
card("inflow", fmt(num("cumulative-inflow")), "added by the sun", true);
card("dissipated", fmt(num("energy-sink")), "lost to sink");
card("reservoir", `${fmt(num("initial-reservoir"))}→${fmt(num("final-reservoir"))}`, "start → end");
card("units fed", `${harvesters}/${N}`, "ever harvested", true);
card("median death", medianDeath, deaths.length?`first ${deaths[0]} · last ${deaths[deaths.length-1]}`:"none died");
card("divergent", U.filter(u=>u.rg==="divergent").length, `of ${N} units`);
if (M["energy-audit-error"] !== undefined)
  card("audit error", (+M["energy-audit-error"]).toExponential(1), "≈0 = conserved");

// ---- canvas helpers ----
function setup(id) {
  const c = document.getElementById(id), dpr = window.devicePixelRatio || 1;
  const w = c.width, h = c.height;
  c.width = w*dpr; c.height = h*dpr;
  c.style.height = (h * (c.clientWidth / w)) + "px";
  const x = c.getContext("2d"); x.scale(dpr, dpr);
  x.clearRect(0,0,w,h);
  return { x, w, h, pad: 50 };
}
const px = (g,v,vmax,vmin=0)=> g.pad + (g.w-12-g.pad)*(v-vmin)/((vmax-vmin)||1);
const py = (g,v,vmax,vmin=0)=> (g.h-g.pad) - (g.h-g.pad-10)*(v-vmin)/((vmax-vmin)||1);
function axes(g, xlabel, ylabel, xmax, ymax, xmin=0, ymin=0) {
  const { x, w, h, pad } = g;
  x.strokeStyle = "#30363d"; x.fillStyle = "#7d8590"; x.lineWidth = 1;
  x.font = "10px ui-monospace, monospace";
  x.beginPath(); x.moveTo(pad, 10); x.lineTo(pad, h-pad); x.lineTo(w-12, h-pad); x.stroke();
  for (let i=0;i<=4;i++){
    const fx = pad + (w-12-pad)*i/4, fy = h-pad - (h-pad-10)*i/4;
    const xv = xmin + (xmax-xmin)*i/4, yv = ymin + (ymax-ymin)*i/4;
    x.fillText(fmtTick(xv), fx-10, h-pad+14);
    x.fillText(fmtTick(yv), 6, fy+3);
    x.strokeStyle = "#21262d"; x.beginPath(); x.moveTo(pad,fy); x.lineTo(w-12,fy); x.stroke();
  }
  x.fillStyle = "#7d8590";
  x.fillText(xlabel, w/2-20, h-8);
  x.save(); x.translate(12, h/2+20); x.rotate(-Math.PI/2); x.fillText(ylabel, 0, 0); x.restore();
}
function fmtTick(v){
  const a=Math.abs(v);
  if(a>=10000) return (v/1000).toFixed(0)+"k";
  if(a>=1000) return (v/1000).toFixed(1)+"k";
  if(a>=10||a===0) return v.toFixed(0);
  return v.toFixed(1);
}

// ---- conservation balance (stacked bars) ----
(function(){
  const g = setup("balance");
  const iu = num("initial-energy-total"), ir = num("initial-reservoir"), inf = num("cumulative-inflow");
  const fu = num("final-energy-total"), fr = num("final-reservoir"), sink = num("energy-sink");
  const src = [["#58a6ff",iu],["#3fb950",ir],["#d29922",inf]];
  const dst = [["#58a6ff",fu],["#3fb950",fr],["#f85149",sink]];
  const total = Math.max(src.reduce((a,b)=>a+b[1],0), dst.reduce((a,b)=>a+b[1],0)) || 1;
  axes(g, "", "energy", 1, total);
  const bw = (g.w-12-g.pad)/2 * 0.5;
  function stack(cx, segs){
    let acc = 0;
    for (const [c,v] of segs){
      if(!isFinite(v)||v<=0){ acc+=Math.max(0,v||0); continue; }
      const y0 = py(g, acc, total), y1 = py(g, acc+v, total);
      g.x.fillStyle = c; g.x.fillRect(cx-bw/2, y1, bw, y0-y1);
      if (y0-y1 > 13){ g.x.fillStyle="#0d1117"; g.x.font="10px ui-monospace,monospace";
        g.x.fillText(fmt(v), cx-bw/2+4, (y0+y1)/2+3); }
      acc += v;
    }
    return acc;
  }
  const x1 = g.pad + (g.w-12-g.pad)*0.28, x2 = g.pad + (g.w-12-g.pad)*0.72;
  const sa = stack(x1, src), da = stack(x2, dst);
  g.x.fillStyle="#7d8590"; g.x.font="11px ui-monospace,monospace";
  g.x.fillText("sources",  x1-26, g.h-g.pad+28);
  g.x.fillText("ends",     x2-12, g.h-g.pad+28);
  document.getElementById("balverdict").innerHTML =
    `Sources <b class="a">${fmt(sa)}</b> = ends <b class="g">${fmt(da)}</b> · `+
    `units net ${fu>=iu?'<b class="g">gained</b>':'<b class="r">lost</b>'} `+
    `<b>${fmt(Math.abs(fu-iu))}</b> · reservoir drained <b>${fmt(ir+inf-fr)}</b> into units · `+
    `audit error <b>${(+M["energy-audit-error"]).toExponential(1)}</b>.`;
})();

// ---- split survival by harvester ----
function aliveFractionSeries(subset){
  const n = subset.length; if(!n) return null;
  const alive = new Array(TICKS+1).fill(n);
  for (const u of subset) if (u.dt>=0) for (let t=u.dt;t<=TICKS;t++) alive[t]--;
  return alive.map(a=>a/n);
}
(function(){
  const g = setup("splitsurv");
  const fed = U.filter(u=>u.ht>0), starved = U.filter(u=>u.ht===0);
  axes(g, "tick", "% of cohort alive", TICKS, 100);
  function line(series, col){
    if(!series) return;
    g.x.strokeStyle = col; g.x.lineWidth = 2; g.x.beginPath();
    for (let t=0;t<=TICKS;t++){ const X=px(g,t,TICKS), Y=py(g,series[t]*100,100); t?g.x.lineTo(X,Y):g.x.moveTo(X,Y); }
    g.x.stroke();
  }
  const sf = aliveFractionSeries(fed), ss = aliveFractionSeries(starved);
  line(ss, "#7d8590"); line(sf, "#3fb950");
  const endFed = sf? sf[TICKS]*100 : NaN, endStarved = ss? ss[TICKS]*100 : NaN;
  const better = endFed - endStarved;
  document.getElementById("splitverdict").innerHTML =
    `End survival: harvesters <b class="g">${fmt(endFed)}%</b> (${fed.length} units) vs `+
    `non-harvesters <b>${fmt(endStarved)}%</b> (${starved.length}). `+
    (isFinite(better) ? (better>3
        ? `Feeding bought <b class="g">+${fmt(better)} pts</b> of survival.`
        : better<-3 ? `Oddly, harvesters fared <b class="r">${fmt(better)} pts</b> worse — wiring cost outran intake.`
        : `Roughly even — intake hasn't yet separated the cohorts.`) : "");
})();

// ---- harvest participation histogram ----
(function(){
  const g = setup("harvhist"), bins = 20;
  const maxHt = Math.max(1, ...U.map(u=>u.ht));
  const counts = new Array(bins).fill(0);
  let never = 0;
  for (const u of U){ if(u.ht===0){ never++; continue; }
    let b = Math.min(bins-1, Math.floor(u.ht/(maxHt+1)*bins)); counts[b]++; }
  const ymax = Math.max(never, ...counts, 1);
  axes(g, "ticks fed", "units", maxHt, ymax);
  const bw = (g.w-12-g.pad)/bins;
  // never-fed grey bar pinned at x=0
  const ny = py(g, never, ymax);
  g.x.fillStyle = "#484f58"; g.x.fillRect(g.pad+1, ny, Math.max(6,bw-2), (g.h-g.pad)-ny);
  for (let i=1;i<bins;i++){
    const X = g.pad + bw*i, Y = py(g, counts[i], ymax);
    g.x.fillStyle = "#3fb950"; g.x.fillRect(X+1, Y, bw-2, (g.h-g.pad)-Y);
  }
  g.x.fillStyle="#7d8590"; g.x.font="10px ui-monospace,monospace";
  g.x.fillText(never+" never fed", g.pad+2, ny-4);
})();

// ---- generic scatter (supports negative x) ----
function scatter(id, xf, xlabel, xmin, xmax, colorf){
  const g = setup(id), ymax = TICKS;
  axes(g, xlabel, "lifespan", xmax, ymax, xmin, 0);
  if (xmin < 0){
    const zx = px(g,0,xmax,xmin);
    g.x.strokeStyle="#586069"; g.x.setLineDash([4,4]);
    g.x.beginPath(); g.x.moveTo(zx,10); g.x.lineTo(zx,g.h-g.pad); g.x.stroke(); g.x.setLineDash([]);
  }
  for (const u of U){
    const xv = Math.max(xmin, Math.min(xf(u), xmax));
    g.x.fillStyle = colorf(u); g.x.globalAlpha = 0.8;
    g.x.beginPath(); g.x.arc(px(g,xv,xmax,xmin), py(g,u.ls,ymax), 4, 0, 7); g.x.fill();
  }
  g.x.globalAlpha = 1;
}
const maxHt = Math.max(1, ...U.map(u=>u.ht));
scatter("harvscatter", u=>u.ht, "ticks fed", 0, maxHt, u=>COL[u.rg]||"#58a6ff");
const brMin = Math.min(0, ...U.map(u=>u.br)), brMax = Math.max(...U.map(u=>u.br))*1.05;
scatter("burnscatter", u=>u.br, "net burn rate / tick", brMin, brMax, u=>COL[u.rg]||"#58a6ff");
scatter("wirescatter", u=>u.mc, "meaningful connections", 0, Math.max(...U.map(u=>u.mc))+1,
        u=> u.rw? "#bc8cff" : "#7d8590");

// ---- survival curve ----
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

// ---- lifespan histogram ----
(function(){
  const g = setup("lifehist"), bins = 24;
  const counts = new Array(bins).fill(0);
  for (const u of U){ let b = Math.min(bins-1, Math.floor(u.ls/(TICKS+1)*bins)); counts[b]++; }
  const ymax = Math.max(...counts);
  axes(g, "lifespan", "count", TICKS, ymax);
  const bw = (g.w-12-g.pad)/bins;
  for (let i=0;i<bins;i++){
    const X = g.pad + bw*i, Y = py(g, counts[i], ymax);
    g.x.fillStyle = i===bins-1 ? "#58a6ff" : "#3fb950"; g.x.fillRect(X+1, Y, bw-2, (g.h-g.pad)-Y);
  }
})();

// ---- regime & structure bar ----
(function(){
  const g = setup("regimebar");
  const items = [
    ["fixed-point", U.filter(u=>u.rg==="fixed-point").length, "#3fb950"],
    ["bounded",     U.filter(u=>u.rg==="bounded").length,     "#58a6ff"],
    ["divergent",   U.filter(u=>u.rg==="divergent").length,   "#f85149"],
    ["reachable",   U.filter(u=>u.rc===1).length,             "#1d9e75"],
    ["rand-wired",  U.filter(u=>u.rw===1).length,             "#bc8cff"],
    ["harvesters",  harvesters,                               "#d29922"],
  ];
  axes(g, "", "units", 1, N);
  const slot = (g.w-12-g.pad)/items.length;
  g.x.font = "10px ui-monospace,monospace";
  items.forEach(([lab,v,c],i)=>{
    const cx = g.pad + slot*i + slot/2, bw = slot*0.55;
    const y = py(g, v, N);
    g.x.fillStyle = c; g.x.fillRect(cx-bw/2, y, bw, (g.h-g.pad)-y);
    g.x.fillStyle = "#e6edf3"; g.x.fillText(v, cx-(""+v).length*3, y-5);
    g.x.fillStyle = "#7d8590";
    g.x.save(); g.x.translate(cx+3, g.h-g.pad+12); g.x.rotate(0.35);
    g.x.fillText(lab, 0, 0); g.x.restore();
  });
})();

// ---- unit circuit: signal-flow by depth (no fixed categories) ----
const WIRING = DATA.wiring || {};
const NS="http://www.w3.org/2000/svg";
const NFILL={s:"#7f77dd",i:"#378add",a:"#1d9e75"};
const LBL={ACTION_Y:"ACT",HARVEST:"HARV",RESOURCE:"RES"};
const ORDER=["CONST","RAND","RESOURCE","ADD","MUL","CLAMP","DELAY","THRESH","ACTION_Y","HARVEST"];
const SENSORS=new Set(["CONST","RAND","RESOURCE"]), ACTIONS=new Set(["ACTION_Y","HARVEST"]);
const VW=720, VH=300, LPAD=70, RPAD=70, TOP=40, BOT=262;

function role(name){
  if(name.startsWith("JUNK-S")) return {kind:"s",junk:true};
  if(name.startsWith("JUNK-I")) return {kind:"i",junk:true};
  if(name.startsWith("JUNK-A")) return {kind:"a",junk:true};
  if(SENSORS.has(name)) return {kind:"s",junk:false};
  if(ACTIONS.has(name)) return {kind:"a",junk:false};
  return {kind:"i",junk:false};
}
function orderKey(name){ const i=ORDER.indexOf(name); return i<0 ? 100+(parseInt(name.replace(/\D/g,""))||0) : i; }
function srcRank(name){ return SENSORS.has(name)?0:1; } // sensors seed the DFS first
function el(n,a){const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);return e;}

// layer nodes by signal depth (longest path from sources). Cycles broken via
// DFS back-edge detection so DELAY loops don't wreck the layering; the broken
// edges come back as the dashed feedback arcs. Fully deterministic.
function buildLayers(nodes, edges){
  const dir=new Set(); edges.forEach(([s,d])=>{ if(s!==d) dir.add(s+">"+d); });
  const adj={}; nodes.forEach(n=>adj[n]=[]);
  dir.forEach(k=>{ const i=k.indexOf(">"); adj[k.slice(0,i)].push(k.slice(i+1)); });
  nodes.forEach(n=>adj[n].sort((a,b)=>orderKey(a)-orderKey(b)));
  const order=[...nodes].sort((a,b)=> srcRank(a)-srcRank(b) || orderKey(a)-orderKey(b));
  const state={}, back=new Set();
  (function(){ const stack=[];
    function dfs(v){ state[v]=1; stack.push(v);
      for(const w of adj[v]){ if(state[w]===1) back.add(v+">"+w); else if(!state[w]) dfs(w); }
      state[v]=2; stack.pop(); }
    order.forEach(v=>{ if(!state[v]) dfs(v); });
  })();
  const fEdges=[]; dir.forEach(k=>{ if(!back.has(k)){ const i=k.indexOf(">"); fEdges.push([k.slice(0,i),k.slice(i+1)]); } });
  const ind={}; nodes.forEach(n=>ind[n]=0); fEdges.forEach(([,d])=>ind[d]++);
  const layer={}; nodes.forEach(n=>layer[n]=0);
  const q=order.filter(n=>ind[n]===0), topo=[];
  while(q.length){ const v=q.shift(); topo.push(v); for(const [s,d] of fEdges) if(s===v && --ind[d]===0) q.push(d); }
  for(const v of topo) for(const [s,d] of fEdges) if(s===v) layer[d]=Math.max(layer[d],layer[v]+1);
  return {layer, back};
}

function layout(nodes, edges){
  const {layer, back}=buildLayers(nodes, edges);
  const maxL=Math.max(0,...nodes.size?[...nodes].map(n=>layer[n]):[0]);
  const byLayer={}; [...nodes].forEach(n=>{ (byLayer[layer[n]]=byLayer[layer[n]]||[]).push(n); });
  Object.values(byLayer).forEach(c=>c.sort((a,b)=>orderKey(a)-orderKey(b)));
  const pos={};
  const X=l=> LPAD + (maxL ? (VW-LPAD-RPAD)*l/maxL : (VW-LPAD-RPAD)/2);
  const place=()=>{ for(const l in byLayer){ const c=byLayer[l];
    c.forEach((n,i)=>{ pos[n]={x:X(+l), y:c.length>1?TOP+(BOT-TOP)*i/(c.length-1):(TOP+BOT)/2,
      kind:role(n).kind, junk:role(n).junk, layer:+l}; }); } };
  const nbr={}; [...nodes].forEach(n=>nbr[n]=[]);
  edges.forEach(([s,d])=>{ if(s!==d){ if(nbr[s])nbr[s].push(d); if(nbr[d])nbr[d].push(s); } });
  place();
  const bary=n=>{ const ns=nbr[n]; if(!ns.length) return pos[n].y; let s=0; for(const m of ns) s+=pos[m].y; return s/ns.length; };
  for(let pass=0;pass<4;pass++){ for(const l in byLayer){ byLayer[l].sort((a,b)=>(bary(a)-bary(b))||(orderKey(a)-orderKey(b))); } place(); }
  return {pos, back, maxL};
}

// forward/backward reachability over MEANINGFUL edges → the live core
function reach(edges, starts, rev){
  const adj={}; edges.forEach(([s,d,w,m])=>{ if(m!==1) return; const a=rev?d:s,b=rev?s:d; (adj[a]=adj[a]||[]).push(b); });
  const seen=new Set(starts), q=[...starts];
  while(q.length){ const x=q.shift(); for(const y of (adj[x]||[])) if(!seen.has(y)){ seen.add(y); q.push(y); } }
  return seen;
}

const usel=document.getElementById("usel");
const unitKeys=Object.keys(WIRING).map(Number).sort((a,b)=>a-b);
unitKeys.forEach(u=>{ const o=document.createElement("option"); o.value=u; o.textContent="unit "+u; usel.appendChild(o); });

function drawWiring(){
  const u=+usel.value, showJunk=document.getElementById("showjunk").checked;
  const all=WIRING[u]||[];
  const edges=all.filter(e=> showJunk || e[3]===1);
  const svg=document.getElementById("wiring"); svg.innerHTML="";

  const nodes=new Set();
  edges.forEach(([s,d])=>{ nodes.add(s); nodes.add(d); });

  // core = reachable from meaningful sensors AND can reach a meaningful action
  const mEdges=all.filter(e=>e[3]===1);
  const sPresent=[...nodes].filter(n=>SENSORS.has(n));
  const aPresent=[...nodes].filter(n=>ACTIONS.has(n));
  const fwd=reach(mEdges, sPresent, false), bwd=reach(mEdges, aPresent, true);
  const core=new Set([...fwd].filter(n=>bwd.has(n)));
  const isCoreEdge=(s,d,m)=> m===1 && core.has(s) && core.has(d);

  const {pos, back, maxL}=layout(nodes, edges);

  const defs=el("defs",{});
  for(const[c,id]of[["#3fb950","ap"],["#f85149","an"],["#484f58","aj"]]){
    const m=el("marker",{id,markerWidth:7,markerHeight:7,refX:6,refY:3,orient:"auto"});
    m.appendChild(el("path",{d:"M0,0 L6,3 L0,6 Z",fill:c})); defs.appendChild(m);
  }
  svg.appendChild(defs);

  // faint depth ticks
  for(let l=0;l<=maxL;l++){ const x=LPAD+(maxL?(VW-LPAD-RPAD)*l/maxL:(VW-LPAD-RPAD)/2);
    const h=el("text",{x,y:20,"text-anchor":"middle","font-size":9,fill:"#586069","font-family":"ui-monospace,monospace"});
    h.textContent=l===0?"depth 0":l; svg.appendChild(h); }

  const R=16;
  let feedbackCount=0;
  for(const [s,d,w,m] of edges){
    const junk=m!==1, coreEdge=isCoreEdge(s,d,m);
    const col=junk?"#484f58":(w>=0?"#3fb950":"#f85149"), mk=junk?"aj":(w>=0?"ap":"an");
    const op=junk?0.22:(coreEdge?0.95:0.32);
    const sw=junk?0.6:Math.min(5,(0.8+Math.abs(w)*0.7));
    const p1=pos[s], p2=pos[d];
    const isBack = (s!==d) && (back.has(s+">"+d) || p2.layer < p1.layer);
    let path;
    if(s===d){
      feedbackCount++;
      const x=p1.x,y=p1.y;
      path=el("path",{d:`M${x-6},${y-R+2} C${x-40},${y-R-30} ${x+40},${y-R-30} ${x+6},${y-R+2}`,
        fill:"none",stroke:col,"stroke-width":sw,"marker-end":`url(#${mk})`,opacity:op,"stroke-dasharray":"4 3"});
    } else if(isBack){
      feedbackCount++;
      const mx=(p1.x+p2.x)/2, my=Math.max(p1.y,p2.y)+46;
      path=el("path",{d:`M${p1.x},${p1.y+R-3} Q${mx},${my} ${p2.x},${p2.y+R-3}`,
        fill:"none",stroke:col,"stroke-width":sw,"marker-end":`url(#${mk})`,opacity:op,"stroke-dasharray":"5 4"});
    } else {
      const mx=(p1.x+p2.x)/2, my=(p1.y+p2.y)/2 - (Math.abs(p2.layer-p1.layer)>1?28:16);
      path=el("path",{d:`M${p1.x+R-2},${p1.y} Q${mx},${my} ${p2.x-R+1},${p2.y}`,
        fill:"none",stroke:col,"stroke-width":sw,"marker-end":`url(#${mk})`,opacity:op});
    }
    svg.appendChild(path);
  }

  for(const name of nodes){
    const p=pos[name], inCore=core.has(name);
    const op = p.junk?0.4 : (inCore?1:0.5);
    svg.appendChild(el("circle",{cx:p.x,cy:p.y,r:R,fill:p.junk?"#30363d":NFILL[p.kind],
      stroke:inCore?"#e6edf3":"#0d1117","stroke-width":inCore?1.8:1.2,opacity:op}));
    const t=el("text",{x:p.x,y:p.y+3,"text-anchor":"middle","font-size":8.5,
      fill:p.junk?"#7d8590":"#0d1117","font-weight":600,"font-family":"ui-monospace,monospace",opacity:op});
    t.textContent=LBL[name]||name.slice(0,5); svg.appendChild(t);
  }

  const ud=U.find(x=>x.unit===u)||{};
  const hasPath=[...fwd].some(n=>ACTIONS.has(n));
  const drivesHarvest=core.has("HARVEST");
  const yn=(b,t,c)=> `<b class="${b?(c||'g'):'r'}">${b?'✓':'✗'}</b> ${t}`;
  document.getElementById("uinfo").innerHTML =
    `unit ${u} · ${ud.mc} meaningful / ${all.length} total edges · ${nodes.size} nodes · depth ${maxL} · `+
    `lifespan <b>${ud.ls}</b> · ${ud.rg}` + (ud.ht>0?` · fed <b class="a">${ud.ht}</b> ticks`:` · never fed`) + ` &nbsp;|&nbsp; `+
    yn(hasPath,"sensor→action path") + ` · ` +
    yn(feedbackCount>0,"feedback / memory","b") + ` · ` +
    yn(drivesHarvest,"drives HARVEST","a");
}
usel.addEventListener("change",drawWiring);
document.getElementById("showjunk").addEventListener("change",drawWiring);
document.getElementById("prev").onclick=()=>{ usel.selectedIndex=Math.max(0,usel.selectedIndex-1); drawWiring(); };
document.getElementById("next").onclick=()=>{ usel.selectedIndex=Math.min(usel.options.length-1,usel.selectedIndex+1); drawWiring(); };
document.getElementById("findharv").onclick=()=>{
  const best=[...U].filter(x=>WIRING[x.unit]).sort((a,b)=>b.ht-a.ht)[0];
  if(best){ const i=unitKeys.indexOf(best.unit); if(i>=0){ usel.selectedIndex=i; drawWiring(); } }
};
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
