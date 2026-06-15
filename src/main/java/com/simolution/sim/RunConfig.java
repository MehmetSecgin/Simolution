package com.simolution.sim;

import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.config.InflowSource;

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
        String inflowSpec = null;
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
                case "--inflow" -> inflowSpec = args[++i];
                case "--map-from" -> mapFrom = Integer.parseInt(args[++i]);
                case "--map-to" -> mapTo = Integer.parseInt(args[++i]);
                case "--out" -> outPath = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (observe && outPath == null) {
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
        InflowConfig inflow;
        if (inflowSpec != null) {
            inflow = parseInflowSpec(inflowSpec);
        } else if (resourceCycle) {
            inflow = InflowConfig.cyclic(
                    cyclePeriod == null ? 2000 : cyclePeriod,
                    cycleRadius == null ? Math.max(1, w / 5) : cycleRadius,
                    cyclePeak == null ? 6.0 : cyclePeak);
        } else {
            inflow = InflowConfig.UNIFORM;
        }
        if (observe && inflow != InflowConfig.UNIFORM) {
            throw new IllegalArgumentException(
                    "non-uniform inflow with --observe is not supported yet "
                    + "(the replay manifest does not carry the inflow field)");
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
                mapFrames == null ? 0 : mapFrames,
                observe,
                checkpointEvery == null ? 2000 : checkpointEvery,
                inflow,
                mapFrom == null ? -1 : mapFrom,
                mapTo == null ? -1 : mapTo
        );
    }

    /**
     * Parse a {@code --inflow} field spec into an {@link InflowConfig}. Sources are
     * separated by {@code ;}; each is a keyword ({@code disk|rect|points|noise})
     * followed by {@code key=val} pairs. Bare {@code combine=max|add} and
     * {@code baseline=N} segments set the field-level combine rule and baseline.
     * Coordinates are fractional ({@code fx,fy ∈ [0,1]}) so a spec is
     * world-size-independent; {@code points cells=…} takes absolute cell indices.
     */
    static InflowConfig parseInflowSpec(String spec) {
        InflowConfig.Combine combine = InflowConfig.Combine.MAX;
        double baseline = 0.0;
        List<InflowSource> sources = new ArrayList<>();

        for (String raw : spec.split(";")) {
            String segment = raw.trim();
            if (segment.isEmpty()) {
                continue;
            }
            String[] tokens = segment.split("\\s+");
            String head = tokens[0];
            if (head.startsWith("combine=") || head.startsWith("baseline=")) {
                for (String t : tokens) {
                    if (t.startsWith("combine=")) {
                        combine = InflowConfig.Combine.valueOf(value(t).toUpperCase());
                    } else if (t.startsWith("baseline=")) {
                        baseline = Double.parseDouble(value(t));
                    } else {
                        throw new IllegalArgumentException("--inflow settings segment expects combine=/baseline=, got: " + t);
                    }
                }
                continue;
            }
            sources.add(parseSource(head, tokens));
        }
        return InflowConfig.field(baseline, combine, sources.toArray(new InflowSource[0]));
    }

    private static InflowSource parseSource(String kind, String[] tokens) {
        double fx = 0.5;
        double fy = 0.5;
        double fx1 = 1.0;
        double fy1 = 1.0;
        double radius = 10.0;
        double peak = 6.0;
        int period = 0;
        int phase = 0;
        long seed = 0L;
        double freq = 0.05;
        double thr = 0.5;
        double gain = 1.0;
        int anim = 0;
        int[] cells = new int[0];
        InflowSource.Motion motion = InflowSource.Motion.STATIC;
        double m0 = 0.0;
        double m1 = 0.0;
        int motionPeriod = 0;

        for (int i = 1; i < tokens.length; i++) {
            String key = key(tokens[i]);
            String val = value(tokens[i]);
            switch (key) {
                case "fx" -> fx = Double.parseDouble(val);
                case "fy" -> fy = Double.parseDouble(val);
                case "fx0" -> fx = Double.parseDouble(val);
                case "fy0" -> fy = Double.parseDouble(val);
                case "fx1" -> fx1 = Double.parseDouble(val);
                case "fy1" -> fy1 = Double.parseDouble(val);
                case "r" -> radius = Double.parseDouble(val);
                case "peak" -> peak = Double.parseDouble(val);
                case "period" -> period = Integer.parseInt(val);
                case "phase" -> phase = Integer.parseInt(val);
                case "seed" -> seed = Long.parseLong(val);
                case "freq" -> freq = Double.parseDouble(val);
                case "thr" -> thr = Double.parseDouble(val);
                case "gain" -> gain = Double.parseDouble(val);
                case "anim" -> anim = Integer.parseInt(val);
                case "cells" -> cells = parseCells(val);
                case "orbit" -> {
                    String[] p = val.split(",");
                    motion = InflowSource.Motion.ORBIT;
                    m0 = Double.parseDouble(p[0]);
                    motionPeriod = Integer.parseInt(p[1]);
                }
                case "drift" -> {
                    String[] p = val.split(",");
                    motion = InflowSource.Motion.DRIFT;
                    m0 = Double.parseDouble(p[0]);
                    m1 = Double.parseDouble(p[1]);
                }
                default -> throw new IllegalArgumentException("unknown --inflow key: " + key);
            }
        }

        return switch (kind) {
            case "disk" -> new InflowSource.Disk(fx, fy, radius, peak, period, phase, motion, m0, m1, motionPeriod);
            case "rect" -> new InflowSource.Rect(fx, fy, fx1, fy1, peak, period, phase, m0, m1);
            case "points" -> new InflowSource.Points(cells, peak, period, phase);
            case "noise" -> new InflowSource.Noise(seed, freq, thr, gain, peak, anim);
            default -> throw new IllegalArgumentException("unknown --inflow source: " + kind);
        };
    }

    private static int[] parseCells(String csv) {
        String[] parts = csv.split(",");
        int[] cells = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            cells[i] = Integer.parseInt(parts[i].trim());
        }
        return cells;
    }

    private static String key(String pair) {
        int eq = pair.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("--inflow expects key=value, got: " + pair);
        }
        return pair.substring(0, eq);
    }

    private static String value(String pair) {
        int eq = pair.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("--inflow expects key=value, got: " + pair);
        }
        return pair.substring(eq + 1);
    }

    private static int squareSideFor(int cells) {
        int side = (int) Math.ceil(Math.sqrt(cells));
        return Math.max(1, side);
    }
}
