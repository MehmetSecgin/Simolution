#!/usr/bin/env bash
# Launch the in-JVM live map server (report-v7) under JDK 25 for the preview panel.
# Used by .claude/launch.json -> preview_start. Edit the --args to change the run.
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
exec ./gradlew run -q --args="--units 300 --ticks 6200 --seed 100 --world 100 --resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --map-from 6000 --map-to 6160 --out runs/live.txt --serve 8090"
