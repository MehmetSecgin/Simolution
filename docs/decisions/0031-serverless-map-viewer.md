# 0031 — One serverless map viewer (delete LiveServer)

**Status:** accepted · **Date:** 2026-06-15 · **Spec:** [report-v12](../specs/report-v12.md)

## Context

Inspecting a run had two viewers: a rich live one (`live.html`) fed by a bespoke in-JVM
`LiveServer` (`--serve`, `/frames?since=N` JSON) and a separate *lite* offline one
(`tools/mapviz.py`, heatmap-only HTML). Duplicated UI, split capability, and a custom
server to maintain. The owner asked: why have the server at all, and can the offline
viewer track ongoing runs? The frame file already carries everything; the server's only
irreplaceable job was tailing a growing file — which a generic static server does.

## Decisions

1. **Parse `.map.txt` client-side in `live.html`.** Port the line parser + per-slot genome
   carry-forward (report-v11 `^`) + `NaN`→`null` into JS. No `/frames` endpoint.
2. **Two feeds, auto-detected.** (a) Fetch a `.map.txt` and re-fetch via HTTP **Range** to
   append new bytes → follows a live or finished run when served by **any** static server
   (`python3 -m http.server`). (b) `window.SIMOLUTION_EMBED` baked in → offline, opens via
   `file://`. Source resolves embed → `?src=` → filename-derived → `live.map.txt`.
3. **Delete `LiveServer.java` + `--serve`.** `RunConfig.servePort` removed; all call sites
   (Main + 6 tests) updated per the no-shim rule. `scripts/serve-live.sh` becomes a generic
   `python3 -m http.server` over `runs/` (stages `live.html` as `index.html`, streams a run
   in the background).
4. **`MapFrameWriter.close()` writes an `end` line.** A client tailing the file needs an
   end-of-run signal (badge → ENDED, stop polling). Sidecar-only; kernel untouched.
5. **`tools/mapviz.py` → baker.** Inlines frames into `live.html` for a self-contained
   `<base>.map.html`. One UI for live and offline; the lite template is gone. Reads pre-v11
   files too.
6. **Fixed the stale node layout in `live.html`** (kernel-v3 `TOTAL=22` → v5 `TOTAL=24`): it
   had been mislabeling every v5 circuit (missing `SELF_MASS`/`GROW`, wrong offsets).

Verified: live path serves an 84 MB run via `python http.server`, parsed client-side,
circuit shows correct v5 names (`SELF_MASS`, `GROW`); bake path renders self-contained in
embed mode; 64 tests green; kernel byte-identical.

## Rejected

- **Keep a dumb static Java server** (serve the dir, drop the JSON) — still a component to
  own; `python3 -m http.server` is zero-maintenance and already available.
- **Pure `file://`, no server even for live** — impossible: browsers block `fetch` of
  `file://`, so a growing file can't be tailed without an HTTP origin. Bake covers the
  no-server case; live needs a generic static server (the honest browser limit).
- **WebSocket push** — needs a bespoke server again; Range-tailing a flat file is simpler
  and survives as a plain artifact.
