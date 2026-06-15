#!/usr/bin/env python3
"""Bake a Simolution run's spatial frames (.frames.zst + .catalog.gz, report-v14)
into a self-contained HTML page.

It decodes the chunked-zstd frame stream + the gzipped genome catalog and inlines
them into the SAME rich viewer used live (src/main/resources/live.html) — spatial
scrub + force-directed circuit inspector + colour by genome — so the result opens
anywhere (even file://) with no server and no zstd decoder in the browser (the
frames are pre-decoded here).

Usage:  python3 tools/mapviz.py runs/foo.frames.zst   ->  runs/foo.map.html
        (reads runs/foo.frames.idx + runs/foo.catalog.gz alongside)

Needs the `zstd` CLI on PATH (the run was produced with zstd-jni; this is the
offline-bake convenience path). For an ONGOING run, don't bake — serve the run dir
(scripts/serve-live.sh) and open the viewer; it tails the growing files over HTTP.
"""
import gzip
import json
import subprocess
import sys
from pathlib import Path

VIEWER = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "live.html"


def read_header(idx_path):
    """world, sample-every from the .frames.idx 'format v14 world W sample-every K chunk N' line."""
    world, every = None, 1
    with open(idx_path, "r") as fh:
        first = fh.readline().split()
    for i, tok in enumerate(first):
        if tok == "world":
            world = int(first[i + 1])
        elif tok == "sample-every":
            every = int(first[i + 1])
    if world is None:
        raise SystemExit("no 'format ... world W' header in " + str(idx_path))
    return world, every


def parse_catalog(path):
    """genomeId -> genes[], from gzipped 'g <id> <count> <gene...>' lines."""
    cat = {}
    if not path.exists():
        return cat
    with gzip.open(path, "rt") as fh:
        for raw in fh:
            t = raw.split()
            if len(t) >= 3 and t[0] == "g":
                gid, gc = int(t[1]), int(t[2])
                cat[gid] = [int(x) for x in t[3:3 + gc]]
    return cat


def parse_frames(zst_path):
    """zstd -d the whole frame stream, parse into the embed payload. u line: cell slot genomeId."""
    out = subprocess.run(["zstd", "-d", "-c", str(zst_path)],
                         capture_output=True, check=True).stdout.decode("utf-8")
    frames = []
    cur = None
    for line in out.split("\n"):
        if not line:
            continue
        tag, _, rest = line.partition(" ")
        if tag == "t":
            if cur is not None:
                frames.append(cur)
            cur = {"t": int(rest), "r": None, "units": []}
        elif tag == "r" and cur is not None:
            cur["r"] = rest
        elif tag == "u" and cur is not None:
            t = rest.split()
            if len(t) >= 3:
                cur["units"].append({"cell": int(t[0]), "slot": int(t[1]), "gid": int(t[2])})
    if cur is not None:
        frames.append(cur)
    return frames


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: python3 tools/mapviz.py <run>.frames.zst")
    path = Path(sys.argv[1])
    if not VIEWER.exists():
        raise SystemExit("viewer template not found: " + str(VIEWER))

    base = path.name
    for suffix in (".frames.zst", ".frames", ".zst"):
        base = base.removesuffix(suffix)
    idx_path = path.with_name(base + ".frames.idx")
    cat_path = path.with_name(base + ".catalog.gz")

    world, every = read_header(idx_path)
    frames = parse_frames(path)
    catalog = parse_catalog(cat_path)
    data = {"world": world, "sampleEvery": every,
            "catalog": {str(k): v for k, v in catalog.items()}, "frames": frames}

    embed = "<script>window.SIMOLUTION_EMBED=" + json.dumps(data, separators=(",", ":")) + ";</script>\n"
    html = VIEWER.read_text()
    # offline bake pre-decodes frames -> drop the fzstd <script src> (not served, not needed)
    html = html.replace('<script src="fzstd.min.js"></script>\n', "")
    html = html.replace("<script>", embed + "<script>", 1)

    out = path.with_name(base + ".map.html")
    out.write_text(html)

    nf = len(frames)
    alive = len(frames[-1]["units"]) if nf else 0
    print(f"wrote {out} ({world}x{world}, {nf} frames, {len(catalog)} catalogued genomes, "
          f"{alive} alive in last frame) — self-contained, opens anywhere (even file://)")


if __name__ == "__main__":
    main()
