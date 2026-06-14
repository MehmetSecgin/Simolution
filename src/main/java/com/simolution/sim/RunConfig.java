package com.simolution.sim;

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
        int servePort,
        boolean observe,
        int checkpointEvery
) {

    public static RunConfig parse(String[] args) {
        Integer units = null;
        Integer ticks = null;
        Integer genes = null;
        Integer world = null;
        Integer maxGenes = null;
        Integer mapFrames = null;
        Integer servePort = null;
        Integer checkpointEvery = null;
        long seed = 0L;
        boolean trace = false;
        boolean observe = false;
        String outPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--units" -> units = Integer.parseInt(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--genes" -> genes = Integer.parseInt(args[++i]);
                case "--world" -> world = Integer.parseInt(args[++i]);
                case "--max-genes" -> maxGenes = Integer.parseInt(args[++i]);
                case "--map-frames" -> mapFrames = Integer.parseInt(args[++i]);
                case "--serve" -> servePort = Integer.parseInt(args[++i]);
                case "--checkpoint-every" -> checkpointEvery = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--trace" -> trace = true;
                case "--observe" -> observe = true;
                case "--out" -> outPath = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (observe && outPath == null) {
            throw new IllegalArgumentException("--observe requires --out (the .obs/ dir sits beside it)");
        }

        boolean demo = units == null && genes == null;
        if (demo) {
            return new RunConfig(1, ticks == null ? 10 : ticks, seed, 0, 1, 0, true, true, outPath, 0, 0,
                    observe, checkpointEvery == null ? 2000 : checkpointEvery);
        }
        int u = units == null ? 1 : units;
        int g = genes == null ? 32 : genes;
        int w = world == null ? squareSideFor(u * 4) : world;
        if ((long) w * w < u) {
            throw new IllegalArgumentException(
                    "world " + w + "x" + w + " (" + ((long) w * w) + " cells) cannot hold "
                    + u + " founders");
        }
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
                mapFrames == null ? 120 : mapFrames,
                servePort == null ? 0 : servePort,
                observe,
                checkpointEvery == null ? 2000 : checkpointEvery
        );
    }

    private static int squareSideFor(int cells) {
        int side = (int) Math.ceil(Math.sqrt(cells));
        return Math.max(1, side);
    }
}
