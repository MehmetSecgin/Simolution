package com.simolution.sim;

import java.nio.file.Path;

final class RunOptions {

    Integer founders;
    Integer ticks;
    Integer genes;
    Integer world;
    Integer maxGenes;
    Long seed;
    String configPath;
    Path reportPath;
    Integer mapFrames;
    Integer mapFrom;
    Integer mapTo;
    Integer checkpointEvery;
    Integer threads;
    String inflowSpec;
    Integer cyclePeriod;
    Integer cycleRadius;
    Double cyclePeak;
    boolean trace;
    boolean observe;
    boolean digestEveryTick;
    boolean resourceCycle;

    private RunOptions() {
    }

    static RunOptions read(final String[] args) {
        final RunOptions options = new RunOptions();
        final ArgCursor cursor = new ArgCursor(args);
        while (cursor.hasNext()) {
            final String flag = cursor.next();
            if (!options.acceptSwitch(flag)) {
                options.acceptValue(flag, cursor);
            }
        }
        return options;
    }

    void validate() {
        if (observe && reportPath == null) {
            throw new IllegalArgumentException("--observe requires --out (the .obs/ dir sits beside it)");
        }
        if (resourceCycle && inflowSpec != null) {
            throw new IllegalArgumentException(
                    "--resource-cycle and --inflow are mutually exclusive "
                    + "(--resource-cycle is sugar for a single central pulsing disk)");
        }
        if (mapFrom != null && (mapTo == null || mapTo < mapFrom)) {
            throw new IllegalArgumentException("--map-from requires --map-to >= --map-from");
        }
        if (threads != null && threads < 1) {
            throw new IllegalArgumentException("--threads must be at least 1, got " + threads);
        }
    }

    int threadCount() {
        return threads == null ? RunConfig.allCores() : threads;
    }

    MapSampling mapSampling() {
        if (mapFrom != null && mapFrom >= 0) {
            return new MapSampling.Window(mapFrom, mapTo);
        }
        if (mapFrames == null || mapFrames < 0) {
            return MapSampling.OFF;
        }
        return mapFrames == 0 ? MapSampling.EVERY_TICK : new MapSampling.Spread(mapFrames);
    }

    private boolean acceptSwitch(final String flag) {
        switch (flag) {
            case "--trace" -> trace = true;
            case "--observe" -> observe = true;
            case "--digest-every-tick" -> digestEveryTick = true;
            case "--resource-cycle" -> resourceCycle = true;
            default -> {
                return false;
            }
        }
        return true;
    }

    private void acceptValue(final String flag, final ArgCursor cursor) {
        switch (flag) {
            case "--units" -> founders = cursor.intValue(flag);
            case "--ticks" -> ticks = cursor.intValue(flag);
            case "--genes" -> genes = cursor.intValue(flag);
            case "--world" -> world = cursor.intValue(flag);
            case "--max-genes" -> maxGenes = cursor.intValue(flag);
            case "--seed" -> seed = cursor.longValue(flag);
            case "--config" -> configPath = cursor.value(flag);
            case "--out" -> reportPath = Path.of(cursor.value(flag));
            case "--map-frames" -> mapFrames = cursor.intValue(flag);
            case "--map-from" -> mapFrom = cursor.intValue(flag);
            case "--map-to" -> mapTo = cursor.intValue(flag);
            case "--checkpoint-every" -> checkpointEvery = cursor.intValue(flag);
            case "--threads" -> threads = cursor.intValue(flag);
            case "--inflow" -> inflowSpec = cursor.value(flag);
            case "--cycle-period" -> cyclePeriod = cursor.intValue(flag);
            case "--cycle-radius" -> cycleRadius = cursor.intValue(flag);
            case "--cycle-peak" -> cyclePeak = cursor.doubleValue(flag);
            default -> throw new IllegalArgumentException("Unknown argument: " + flag);
        }
    }
}
