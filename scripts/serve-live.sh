#!/usr/bin/env bash
# Serve the runs/ dir over plain HTTP so the viewer (live.html) can tail a run
# live and scrub it after. No bespoke server — the viewer parses the .map.txt
# client-side (report-v12). Used by .claude/launch.json -> preview_start.
# Edit the --args to change the run.
set -e
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
mkdir -p runs
cp src/main/resources/live.html runs/index.html
# stream a run into runs/live.map.txt in the background; the served viewer tails it
./gradlew run -q --args="--units 300 --ticks 8000 --seed 100 --world 100 --resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --map-frames 200 --out runs/live.txt" &
exec python3 -m http.server 8090 --directory runs
