package com.simolution.sim;

import java.nio.file.Path;

class RunOptions {

    int founders = RunConfig.DEFAULT_FOUNDERS;
    int ticks = RunConfig.DEFAULT_TICKS;
    int genes = RunConfig.DEFAULT_GENES;
    Integer world;
    Integer maxGenes;
    Long seed;
    String configPath;
    Path reportPath;
    int mapFrames = -1;
    Integer mapFrom;
    Integer mapTo;
    int checkpointEvery = RunConfig.DEFAULT_CHECKPOINT_EVERY;
    int threads = Runtime.getRuntime().availableProcessors();
    String inflowSpec;
    boolean trace;
    boolean observe;
    boolean digestEveryTick;

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
        if (mapFrom != null && (mapTo == null || mapTo < mapFrom)) {
            throw new IllegalArgumentException("--map-from requires --map-to >= --map-from");
        }
        if (threads < 1) {
            throw new IllegalArgumentException("--threads must be at least 1, got " + threads);
        }
    }

    MapSampling mapSampling() {
        if (mapFrom != null && mapFrom >= 0) {
            return MapSampling.window(mapFrom, mapTo);
        }
        if (mapFrames < 0) {
            return MapSampling.OFF;
        }
        return mapFrames == 0 ? MapSampling.EVERY_TICK : MapSampling.spread(mapFrames);
    }

    private boolean acceptSwitch(final String flag) {
        switch (flag) {
            case "--trace" -> trace = true;
            case "--observe" -> observe = true;
            case "--digest-every-tick" -> digestEveryTick = true;
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
            default -> throw new IllegalArgumentException("Unknown argument: " + flag);
        }
    }
}
