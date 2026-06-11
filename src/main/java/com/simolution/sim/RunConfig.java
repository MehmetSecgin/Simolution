package com.simolution.sim;

public record RunConfig(
        int units,
        int ticks,
        long seed,
        int genesPerUnit,
        boolean trace,
        boolean demo,
        String outPath
) {

    public static RunConfig parse(String[] args) {
        Integer units = null;
        Integer ticks = null;
        Integer genes = null;
        long seed = 0L;
        boolean trace = false;
        String outPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--units" -> units = Integer.parseInt(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--genes" -> genes = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--trace" -> trace = true;
                case "--out" -> outPath = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        boolean demo = units == null && genes == null;
        if (demo) {
            return new RunConfig(1, ticks == null ? 10 : ticks, seed, 0, true, true, outPath);
        }
        return new RunConfig(
                units == null ? 1 : units,
                ticks == null ? 1000 : ticks,
                seed,
                genes == null ? 32 : genes,
                trace,
                false,
                outPath
        );
    }
}
