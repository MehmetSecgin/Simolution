#!/usr/bin/env python3
"""Bake a Simolution run's lean spatial frames (.frames + .catalog, report-v13)
into a self-contained HTML page.

It inlines the run's frames + genome catalog into the SAME rich viewer used live
(src/main/resources/live.html) — spatial scrub + force-directed circuit inspector
+ colour by genome/mass/action — so the result opens anywhere (even file://) with
no server.

Usage:  python3 tools/mapviz.py runs/foo.frames   ->  runs/foo.map.html
        (reads runs/foo.catalog alongside)

For an ONGOING run, don't bake — serve the run dir (scripts/serve-live.sh) and
open the viewer; it tails the growing .frames + .catalog over HTTP. Stdlib only.
"""
import json
import sys
from pathlib import Path

VIEWER = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "live.html"


def parse_catalog(path):
    """genomeId -> genes[], from 'g <id> <count> <gene...>' lines."""
    cat = {}
    if not path.exists():
        return cat
    with open(path, "r") as fh:
        for raw in fh:
            t = raw.split()
            if len(t) >= 3 and t[0] == "g":
                gid, gc = int(t[1]), int(t[2])
                cat[gid] = [int(x) for x in t[3:3 + gc]]
    return cat


def parse_frames(path):
    """Parse .frames into the embed payload. u line: cell slot mass action genomeId."""
    world = None
    every = 1
    frames = []
    cur = None
    with open(path, "r") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line:
                continue
            tag, _, rest = line.partition(" ")
            if tag == "format":
                pass
            elif tag == "world":
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
                if len(t) < 5 or cur is None:
                    continue
                cur["units"].append({
                    "cell": int(t[0]), "slot": int(t[1]),
                    "mass": float(t[2]), "act": t[3], "gid": int(t[4]),
                })
            elif tag == "end":
                pass
    if cur is not None and cur["r"] is not None:
        frames.append(cur)
    if world is None:
        raise SystemExit("no 'world' header in " + str(path))
    return world, every, frames


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: python3 tools/mapviz.py <run>.frames")
    path = Path(sys.argv[1])
    if not VIEWER.exists():
        raise SystemExit("viewer template not found: " + str(VIEWER))
    world, every, frames = parse_frames(path)
    catalog = parse_catalog(path.with_suffix(".catalog"))
    data = {"world": world, "sampleEvery": every,
            "catalog": {str(k): v for k, v in catalog.items()}, "frames": frames}

    embed = "<script>window.SIMOLUTION_EMBED=" + json.dumps(data, separators=(",", ":")) + ";</script>\n"
    html = VIEWER.read_text().replace("<script>", embed + "<script>", 1)

    stem = path.name
    for suffix in (".frames",):
        stem = stem.removesuffix(suffix)
    out = path.with_name(stem + ".map.html")
    out.write_text(html)

    nf = len(frames)
    alive = len(frames[-1]["units"]) if nf else 0
    print(f"wrote {out} ({world}x{world}, {nf} frames, {len(catalog)} catalogued genomes, "
          f"{alive} alive in last frame) — self-contained, opens anywhere (even file://)")


if __name__ == "__main__":
    main()
