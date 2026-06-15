package com.simolution.sim;

import com.simolution.kernel.config.InflowConfig;

public record RunConfig(
        int units,
        int ticks,
        long seed,
        int genesPerUnit,
        int worldWidth,
        int maxGenes,
        boolean trace,
        boolean demo,
        String outPath,
        int mapFrames,
        boolean observe,
        int checkpointEvery,
        InflowConfig inflow,
        int mapFrom,
        int mapTo
) {

    public static RunConfig parse(String[] args) {
        Integer units = null;
        Integer ticks = null;
        Integer genes = null;
        Integer world = null;
        Integer maxGenes = null;
        Integer mapFrames = null;
        Integer checkpointEvery = null;
        Integer cyclePeriod = null;
        Integer cycleRadius = null;
        Double cyclePeak = null;
        Integer mapFrom = null;
        Integer mapTo = null;
        long seed = 0L;
        boolean trace = false;
        boolean observe = false;
        boolean resourceCycle = false;
        String outPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--units" -> units = Integer.parseInt(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--genes" -> genes = Integer.parseInt(args[++i]);
                case "--world" -> world = Integer.parseInt(args[++i]);
                case "--max-genes" -> maxGenes = Integer.parseInt(args[++i]);
                case "--map-frames" -> mapFrames = Integer.parseInt(args[++i]);
                case "--checkpoint-every" -> checkpointEvery = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--trace" -> trace = true;
                case "--observe" -> observe = true;
                case "--resource-cycle" -> resourceCycle = true;
                case "--cycle-period" -> cyclePeriod = Integer.parseInt(args[++i]);
                case "--cycle-radius" -> cycleRadius = Integer.parseInt(args[++i]);
                case "--cycle-peak" -> cyclePeak = Double.parseDouble(args[++i]);
                case "--map-from" -> mapFrom = Integer.parseInt(args[++i]);
                case "--map-to" -> mapTo = Integer.parseInt(args[++i]);
                case "--out" -> outPath = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (observe && outPath == null) {
            throw new IllegalArgumentException("--observe requires --out (the .obs/ dir sits beside it)");
        }
        if (resourceCycle && observe) {
            throw new IllegalArgumentException(
                    "--resource-cycle with --observe is not supported yet "
                    + "(the replay manifest does not carry the inflow pattern)");
        }
        if (mapFrom != null && (mapTo == null || mapTo < mapFrom)) {
            throw new IllegalArgumentException("--map-from requires --map-to >= --map-from");
        }

        boolean demo = units == null && genes == null;
        if (demo) {
            return new RunConfig(1, ticks == null ? 10 : ticks, seed, 0, 1, 0, true, true, outPath, 0,
                    observe, checkpointEvery == null ? 2000 : checkpointEvery, InflowConfig.UNIFORM, -1, -1);
        }
        int u = units == null ? 1 : units;
        int g = genes == null ? 32 : genes;
        int w = world == null ? squareSideFor(u * 4) : world;
        if ((long) w * w < u) {
            throw new IllegalArgumentException(
                    "world " + w + "x" + w + " (" + ((long) w * w) + " cells) cannot hold "
                    + u + " founders");
        }
        InflowConfig inflow = resourceCycle
                ? InflowConfig.cyclic(
                        cyclePeriod == null ? 2000 : cyclePeriod,
                        cycleRadius == null ? Math.max(1, w / 5) : cycleRadius,
                        cyclePeak == null ? 6.0 : cyclePeak)
                : InflowConfig.UNIFORM;
        return new RunConfig(
                u,
                ticks == null ? 1000 : ticks,
                seed,
                g,
                w,
                maxGenes == null ? g * 2 : maxGenes,
                trace,
                false,
                outPath,
                mapFrames == null ? 0 : mapFrames,
                observe,
                checkpointEvery == null ? 2000 : checkpointEvery,
                inflow,
                mapFrom == null ? -1 : mapFrom,
                mapTo == null ? -1 : mapTo
        );
    }

    private static int squareSideFor(int cells) {
        int side = (int) Math.ceil(Math.sqrt(cells));
        return Math.max(1, side);
    }
}
