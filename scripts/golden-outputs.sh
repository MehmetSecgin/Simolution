#!/usr/bin/env bash
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -eq 1 ] || { echo "usage: $0 <dir>" >&2; exit 2; }
mkdir -p "$1"
OUT="$(cd "$1" && pwd)"
(cd "$REPO" && ./gradlew -q installDist) || exit 1
BIN="$REPO/build/install/Simolution/bin/Simolution"

run() {
    local name=$1; shift
    mkdir -p "$OUT/$name"
    (cd "$OUT" && "$BIN" "$@" >"$name/stdout.raw" 2>"$name/stderr.txt"; echo $? >"$name/exit.txt")
    grep -v '^(wall clock:' "$OUT/$name/stdout.raw" | sed "s#$REPO/examples/baseline.json#examples/baseline.json#g" >"$OUT/$name/stdout.txt"
    rm "$OUT/$name/stdout.raw"
    sed -e "s#$REPO#<repo>#g" -e '/^[[:space:]]*at /d' "$OUT/$name/stderr.txt" >"$OUT/$name/stderr.norm" && mv "$OUT/$name/stderr.norm" "$OUT/$name/stderr.txt"
}

run full --units 100 --ticks 600 --seed 42 --world 100 --map-frames 0 --checkpoint-every 200 --threads 2 --out full
run spread --units 60 --ticks 300 --seed 7 --world 60 --map-frames 25 --genes 24 --max-genes 48 --out spread
run inflow --config "$REPO/examples/baseline.json" --units 150 --ticks 400 --seed 3 --world 80 --inflow "disk orbit=0.15,4000 r=12 peak=8; rect fx0=0 fy0=0 fx1=0.3 fy1=1 peak=5; points cells=100,250,4000 peak=8; noise seed=7 freq=0.05 thr=0.6 gain=3 peak=6 anim=800; combine=max baseline=0.1" --map-from 100 --map-to 140 --out inflow
run cycle --units 50 --ticks 300 --seed 5 --world 60 --inflow "disk r=10 peak=5 period=200" --out cycle
run digest --units 20 --ticks 30 --seed 1 --world 20 --digest-every-tick
run trace --units 5 --ticks 5 --seed 1 --world 10 --trace
run errors/out-names-a-file --out run.txt
run errors/map-from-alone --map-from 5
run errors/threads-zero --threads 0
run errors/bogus-flag --bogus
run errors/inflow-unknown-key-population --units 20 --ticks 20 --seed 1 --world 20 --inflow "disk wat=1"
run errors/inflow-repeated-key-population --units 20 --ticks 20 --seed 1 --world 20 --inflow "disk r=5 peak=3 r=7"
run errors/missing-value --units
