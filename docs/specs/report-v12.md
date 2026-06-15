# report-v12 — one serverless viewer (live + offline, no bespoke server)

Delta over [report-v11](report-v11.md). There were **two** ways to inspect a run's
spatial map, with different capabilities:

- **live, rich** — `--serve PORT` started an in-JVM `LiveServer` that parsed the
  `.map.txt` into JSON (`/frames?since=N`) for `live.html` (genome panel + circuit
  inspector). Needed a bespoke Java server; only during/after a run that opted in.
- **offline, lite** — `tools/mapviz.py` baked a *separate, simpler* HTML scrub player
  (heatmap only, no genomes/circuits).

report-v12 collapses these into **one rich viewer** (`src/main/resources/live.html`)
that parses the `.map.txt` **client-side**, and **deletes the bespoke server**.

## Why
The two viewers duplicated UI and split capability (the offline one couldn't show
genomes/circuits; the rich one needed a custom server). The frame file already carries
everything; the only thing the Java server did that a browser can't is **tail a growing
local file** — and a *generic* static file server (`python3 -m http.server`) does that
fine. So the bespoke server earns its keep nowhere.

## The viewer (`src/main/resources/live.html`)
Parses the line-oriented `.map.txt` itself (world/sample-every/`t`/`r`/`u`/`end`),
carrying genomes forward per slot (the report-v11 `^` marker) and mapping non-finite
outputs to `null`. Two data feeds, auto-detected:

| feed | how | server |
|------|-----|--------|
| **track a run (live or finished)** | fetch a `.map.txt` by URL and re-fetch via HTTP **Range** to append new bytes as the file grows | any static file server (e.g. `python3 -m http.server`) — **not bespoke** |
| **offline / shareable** | `window.SIMOLUTION_EMBED` baked into the file (frames inlined) | none — opens anywhere, even `file://` |

Source resolution: `window.SIMOLUTION_EMBED` → `?src=NAME` → derive `foo.map.txt` from
the page's own filename `foo.html` → fallback `live.map.txt`.

**The one hard constraint:** a browser cannot tail a growing file over `file://`
(`fetch` of `file://` is blocked). So *following a live run* needs **some** static HTTP
server — but a generic one, nothing we maintain. Pure-offline (baked) needs no server.

## Frame file
`MapFrameWriter.close()` now appends a final **`end`** line so a client tailing the file
knows the run finished (badge → ENDED, stop polling). Otherwise the format is unchanged
from report-v11. The writer still streams + flushes per frame (O(1) RAM).

## Node-name fix (was a latent bug)
`live.html` hard-coded the **kernel-v3** node layout (`TOTAL=22`: 6 sensors / 8 / 8), so
on kernel-v5 genomes the circuit inspector **mislabeled every node** (no `SELF_MASS`, no
`GROW`, shifted offsets). Corrected to the v5 `NodeLayout` (`TOTAL=24`: 7 sensors incl.
`SELF_MASS`, 8 internals, 9 actions incl. `GROW`).

## What changed / was deleted
- **Deleted** `LiveServer.java`, the `--serve` flag (`RunConfig`, `Main`), and the
  `/frames` JSON endpoint. `RunConfig`'s `servePort` field removed (all call sites updated).
- `tools/mapviz.py` repurposed: **bakes** `.map.txt` → self-contained `<base>.map.html`
  by inlining frames into `live.html` (no more separate lite template). Reads pre-v11
  files too.
- `scripts/serve-live.sh`: stages `live.html` into `runs/index.html`, streams a run in the
  background, and serves `runs/` with `python3 -m http.server` (generic).
- Kernel untouched; runs with frames off are byte-identical. Text report `schema:` stays
  report-v10.
