#!/usr/bin/env python3
"""Bake a Simolution run's spatial map (.map.txt, report-v11/v12) into a
self-contained HTML page.

It inlines the run's frames into the SAME rich viewer used live
(src/main/resources/live.html) — genome panel + force-directed circuit
inspector + colour-by-mass — so the result opens anywhere (even file://) with
no server. There is no separate "lite" viewer anymore; this and the live path
share one UI.

Usage:  python3 tools/mapviz.py runs/foo.map.txt   ->  runs/foo.map.html

For an ONGOING run, don't bake — serve the run dir (scripts/serve-live.sh) and
open the viewer; it tails the growing .map.txt over HTTP. Stdlib only.
"""
import json
import sys
from pathlib import Path

VIEWER = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "live.html"


def _num(tok):
    if tok in ("NaN", "Infinity", "-Infinity"):
        return None
    try:
        return float(tok)
    except ValueError:
        return None


def parse(path):
    """Parse .map.txt into the embed payload, carrying genomes forward per slot
    (the '^' marker, report-v11). Handles pre-v11 files (inline gene count) too."""
    world = None
    every = 1
    frames = []
    cur = None
    slot_genome = {}
    with open(path, "r") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line:
                continue
            tag, _, rest = line.partition(" ")
            if tag == "world":
                world = int(rest)
            elif tag == "sample-every":
                every = int(rest)
            elif tag == "t":
                if cur is not None and cur["r"] is not None:
                    frames.append(cur)
                cur = {"t": int(rest), "r": None, "units": []}
            elif tag == "r":
                if cur is not None:
                    cur["r"] = rest
            elif tag == "u":
                t = rest.split()
                if len(t) < 7:
                    continue
                slot = int(t[1])
                marker = t[6]
                if marker == "*":
                    gc = int(t[7])
                    genes = [int(x) for x in t[8:8 + gc]]
                    slot_genome[slot] = genes
                    ostart = 8 + gc
                elif marker == "^":
                    genes = slot_genome.get(slot, [])
                    ostart = 7
                else:
                    gc = int(t[6])
                    genes = [int(x) for x in t[7:7 + gc]]
                    slot_genome[slot] = genes
                    ostart = 7 + gc
                if cur is not None:
                    cur["units"].append({
                        "cell": int(t[0]), "slot": slot, "lineage": int(t[2]),
                        "gen": int(t[3]), "energy": _num(t[4]), "mass": _num(t[5]),
                        "genes": genes, "o": [_num(x) for x in t[ostart:]],
                    })
            elif tag == "end":
                pass
    if cur is not None and cur["r"] is not None:
        frames.append(cur)
    if world is None:
        raise SystemExit("no 'world' header in " + str(path))
    return {"world": world, "sampleEvery": every, "frames": frames}


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: python3 tools/mapviz.py <run>.map.txt")
    path = Path(sys.argv[1])
    if not VIEWER.exists():
        raise SystemExit("viewer template not found: " + str(VIEWER))
    data = parse(path)
    embed = "<script>window.SIMOLUTION_EMBED=" + json.dumps(data, separators=(",", ":")) + ";</script>\n"
    html = VIEWER.read_text().replace("<script>", embed + "<script>", 1)

    out = path.with_name(path.name.removesuffix(".txt") + ".html")
    out.write_text(html)

    nf = len(data["frames"])
    alive = len(data["frames"][-1]["units"]) if nf else 0
    print(f"wrote {out} ({data['world']}x{data['world']}, {nf} frames, "
          f"{alive} alive in last frame) — self-contained, opens anywhere (even file://)")


if __name__ == "__main__":
    main()
