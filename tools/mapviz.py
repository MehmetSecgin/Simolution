#!/usr/bin/env python3
"""Render a Simolution spatial map (.map.txt, report-v7) as a self-contained
HTML scrub player: a W x W lattice with the resource field as a heatmap and
living units coloured by lineage, with play / pause / slider / speed.

Usage:  python3 tools/mapviz.py runs/foo.map.txt   ->  runs/foo.map.html

Stdlib only, no dependencies. Frames are sampled by the run (see MapFrameWriter);
this tool only inlines them and draws.
"""
import json
import sys
from pathlib import Path


def parse(path):
    world = None
    sample_every = None
    frames = []
    cur = None
    with open(path, "r") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line:
                continue
            tag, _, rest = line.partition(" ")
            if tag == "world":
                world = int(rest)
            elif tag == "sample-every":
                sample_every = int(rest)
            elif tag == "t":
                if cur is not None:
                    frames.append(cur)
                cur = {"t": int(rest), "r": "", "o": []}
            elif tag == "r":
                cur["r"] = rest
            elif tag == "u":
                # u <cell> <slot> <lineage> <generation> <energy> <geneCount> <gene...>
                tok = rest.split()
                if len(tok) >= 3:
                    cur["o"].append([int(tok[0]), int(tok[2])])
    if cur is not None:
        frames.append(cur)
    if world is None:
        raise SystemExit("no 'world' header in " + str(path))
    return world, sample_every or 1, frames


HTML = """<!doctype html><html><head><meta charset="utf-8">
<title>Simolution map - {name}</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{ background:#0d1117; color:#c9d1d9; font:14px/1.5 ui-monospace,Menlo,Consolas,monospace;
         margin:0; padding:20px; }}
  h1 {{ font-size:15px; font-weight:600; margin:0 0 12px; color:#e6edf3; }}
  .wrap {{ display:flex; gap:24px; flex-wrap:wrap; align-items:flex-start; }}
  canvas {{ background:#000; image-rendering:pixelated; border:1px solid #30363d; }}
  .panel {{ min-width:240px; }}
  .row {{ display:flex; align-items:center; gap:10px; margin:8px 0; }}
  button {{ background:#21262d; color:#c9d1d9; border:1px solid #30363d; border-radius:6px;
           padding:6px 14px; font:inherit; cursor:pointer; }}
  button:hover {{ background:#30363d; }}
  input[type=range] {{ flex:1; }}
  .stat {{ color:#8b949e; }}
  .stat b {{ color:#e6edf3; font-weight:600; }}
  label {{ color:#8b949e; user-select:none; }}
</style></head><body>
<h1>spatial map &mdash; {name} &mdash; {world}x{world} torus, {nframes} frames (every {every} ticks)</h1>
<div class="wrap">
  <canvas id="cv"></canvas>
  <div class="panel">
    <div class="row">
      <button id="play">&#9654; play</button>
      <button id="step">step &#9654;</button>
    </div>
    <div class="row"><input id="slider" type="range" min="0" max="{maxidx}" value="0"></div>
    <div class="row"><label>speed</label><input id="speed" type="range" min="1" max="60" value="12"><span id="fps" class="stat"></span></div>
    <div class="row"><label><input type="checkbox" id="showres" checked> resource heatmap</label></div>
    <div class="row stat">tick <b id="tick">0</b></div>
    <div class="row stat">frame <b id="frame">0</b> / {maxidx}</div>
    <div class="row stat">alive <b id="alive">0</b></div>
    <div class="row stat">lineages <b id="lin">0</b></div>
    <div class="row stat" style="margin-top:14px">units coloured by lineage; background = resource (dark&rarr;green).</div>
  </div>
</div>
<script>
const W = {world};
const FRAMES = {frames};
const cv = document.getElementById('cv');
const ctx = cv.getContext('2d');
const px = Math.max(2, Math.floor(640 / W));
cv.width = W * px; cv.height = W * px;

function linColor(id) {{
  const h = (id * 137.508) % 360;
  return `hsl(${{h}},72%,58%)`;
}}
function resColor(level) {{
  const t = level / 9;
  return `rgb(${{Math.round(t*18)}},${{Math.round(24+t*108)}},${{Math.round(t*46)}})`;
}}

let idx = 0, playing = false, fps = 12, showRes = true;

function draw() {{
  const f = FRAMES[idx];
  // resource background
  if (showRes) {{
    const r = f.r;
    for (let c = 0; c < r.length; c++) {{
      ctx.fillStyle = resColor(r.charCodeAt(c) - 48);
      ctx.fillRect((c % W) * px, ((c / W) | 0) * px, px, px);
    }}
  }} else {{
    ctx.fillStyle = '#000'; ctx.fillRect(0, 0, cv.width, cv.height);
  }}
  // living units
  const lineages = new Set();
  for (const [cell, lineage] of f.o) {{
    ctx.fillStyle = linColor(lineage);
    ctx.fillRect((cell % W) * px, ((cell / W) | 0) * px, px, px);
    lineages.add(lineage);
  }}
  document.getElementById('tick').textContent = f.t;
  document.getElementById('frame').textContent = idx;
  document.getElementById('alive').textContent = f.o.length;
  document.getElementById('lin').textContent = lineages.size;
  document.getElementById('slider').value = idx;
}}

let last = 0;
function loop(ts) {{
  if (playing) {{
    if (ts - last >= 1000 / fps) {{
      last = ts;
      idx = (idx + 1) % FRAMES.length;
      draw();
    }}
    requestAnimationFrame(loop);
  }}
}}
document.getElementById('play').onclick = (e) => {{
  playing = !playing;
  e.target.innerHTML = playing ? '&#10073;&#10073; pause' : '&#9654; play';
  if (playing) {{ last = 0; requestAnimationFrame(loop); }}
}};
document.getElementById('step').onclick = () => {{ idx = (idx + 1) % FRAMES.length; draw(); }};
document.getElementById('slider').oninput = (e) => {{ idx = +e.target.value; draw(); }};
document.getElementById('speed').oninput = (e) => {{ fps = +e.target.value; document.getElementById('fps').textContent = fps + '/s'; }};
document.getElementById('showres').onchange = (e) => {{ showRes = e.target.checked; draw(); }};
document.getElementById('fps').textContent = fps + '/s';
draw();
</script></body></html>
"""


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: python3 tools/mapviz.py <run>.map.txt")
    path = Path(sys.argv[1])
    world, every, frames = parse(path)
    out = path.with_suffix("")
    if out.suffix == ".map":
        out = out.with_suffix("")
    out = out.parent / (out.name + ".map.html")
    html = HTML.format(
        name=path.name,
        world=world,
        every=every,
        nframes=len(frames),
        maxidx=max(0, len(frames) - 1),
        frames=json.dumps(frames, separators=(",", ":")),
    )
    out.write_text(html)
    alive = frames[-1]["o"].__len__() if frames else 0
    print(f"wrote {out} ({world}x{world}, {len(frames)} frames, {alive} alive in last frame)")


if __name__ == "__main__":
    main()
