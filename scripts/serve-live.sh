#!/usr/bin/env bash
# Serve the runs/ dir over plain HTTP so the viewer (live.html) can tail a run
# live and scrub it after. No bespoke server — the viewer parses the .frames +
# .catalog client-side (report-v13). Used by .claude/launch.json -> preview_start.
# Edit the --args to change the run.
set -e
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
mkdir -p runs
cp src/main/resources/live.html runs/index.html
# stream a run into runs/live.frames (+ runs/live.catalog) in the background;
# the served viewer tails both. --map-frames 0 = a frame EVERY tick (cheap now).
./gradlew run -q --args="--units 300 --ticks 8000 --seed 100 --world 100 --resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --map-frames 0 --out runs/live.txt" &
# Range-capable static server (scripts/serve.py) — the stdlib http.server ignores
# Range, which would make the viewer pull the whole .frames file per request.
exec python3 scripts/serve.py 8090 runs
