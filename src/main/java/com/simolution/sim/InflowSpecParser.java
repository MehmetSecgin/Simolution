package com.simolution.sim;

import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.config.InflowSource;

public final class InflowSpecParser {

    private static final double CENTER = 0.5;
    private static final double FAR_EDGE = 1.0;
    private static final double PEAK = 6.0;

    private InflowSpecParser() {
    }

    public static InflowConfig parse(final String spec) {
        final Settings settings = new Settings();
        final List<InflowSource> sources = new ArrayList<>();
        for (final String raw : spec.split(";")) {
            final String segment = raw.trim();
            if (segment.isEmpty()) {
                continue;
            }
            final String[] tokens = segment.split("\\s+");
            if (Settings.starts(tokens[0])) {
                settings.apply(tokens);
            } else {
                sources.add(source(tokens[0], Params.of(tokens)));
            }
        }
        return InflowConfig.field(settings.baseline, settings.combine, sources.toArray(new InflowSource[0]));
    }

    private static InflowSource source(final String kind, final Params p) {
        return switch (kind) {
            case "disk" -> disk(p);
            case "rect" -> rect(p);
            case "points" -> points(p);
            case "noise" -> noise(p);
            case "ring" -> ring(p);
            default -> throw new IllegalArgumentException("unknown --inflow source: " + kind);
        };
    }

    private static InflowSource disk(final Params p) {
        return new InflowSource.Disk(p.fx, p.fy, p.radius, p.peak, p.period, p.phase,
                p.motion, p.m0, p.m1, p.motionPeriod);
    }

    private static InflowSource rect(final Params p) {
        return new InflowSource.Rect(p.fx, p.fy, p.fx1, p.fy1, p.peak, p.period, p.phase, p.m0, p.m1);
    }

    private static InflowSource points(final Params p) {
        return new InflowSource.Points(p.cells, p.peak, p.period, p.phase);
    }

    private static InflowSource noise(final Params p) {
        return new InflowSource.Noise(p.seed, p.frequency, p.threshold, p.gain, p.peak, p.animPeriod);
    }

    private static InflowSource ring(final Params p) {
        return new InflowSource.Ring(p.fx, p.fy, p.wavelength, p.speed, p.peak, p.width);
    }

    private static String key(final String pair) {
        return pair.substring(0, separator(pair));
    }

    private static String value(final String pair) {
        return pair.substring(separator(pair) + 1);
    }

    private static int separator(final String pair) {
        final int eq = pair.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("--inflow expects key=value, got: " + pair);
        }
        return eq;
    }

    private static final class Settings {

        private InflowConfig.Combine combine = InflowConfig.Combine.MAX;
        private double baseline = 0.0;

        static boolean starts(final String head) {
            return head.startsWith("combine=") || head.startsWith("baseline=");
        }

        void apply(final String[] tokens) {
            for (final String token : tokens) {
                if (token.startsWith("combine=")) {
                    combine = InflowConfig.Combine.valueOf(value(token).toUpperCase());
                } else if (token.startsWith("baseline=")) {
                    baseline = Double.parseDouble(value(token));
                } else {
                    throw new IllegalArgumentException(
                            "--inflow settings segment expects combine=/baseline=, got: " + token);
                }
            }
        }
    }

    private static final class Params {

        private double fx = CENTER;
        private double fy = CENTER;
        private double fx1 = FAR_EDGE;
        private double fy1 = FAR_EDGE;
        private double radius = 10.0;
        private double peak = PEAK;
        private double frequency = 0.05;
        private double threshold = 0.5;
        private double gain = 1.0;
        private double wavelength = 30.0;
        private double speed = 0.05;
        private double width = 6.0;
        private int period = 0;
        private int phase = 0;
        private int animPeriod = 0;
        private long seed = 0L;
        private int[] cells = new int[0];
        private InflowSource.Motion motion = InflowSource.Motion.STATIC;
        private double m0 = 0.0;
        private double m1 = 0.0;
        private int motionPeriod = 0;

        static Params of(final String[] tokens) {
            final Params params = new Params();
            for (int i = 1; i < tokens.length; i++) {
                params.put(tokens[i]);
            }
            return params;
        }

        private void put(final String pair) {
            final String key = key(pair);
            final String value = value(pair);
            switch (key) {
                case "fx", "fx0" -> fx = Double.parseDouble(value);
                case "fy", "fy0" -> fy = Double.parseDouble(value);
                case "fx1" -> fx1 = Double.parseDouble(value);
                case "fy1" -> fy1 = Double.parseDouble(value);
                case "r" -> radius = Double.parseDouble(value);
                case "peak" -> peak = Double.parseDouble(value);
                case "freq" -> frequency = Double.parseDouble(value);
                case "thr" -> threshold = Double.parseDouble(value);
                case "gain" -> gain = Double.parseDouble(value);
                case "wavelength" -> wavelength = Double.parseDouble(value);
                case "speed" -> speed = Double.parseDouble(value);
                case "width" -> width = Double.parseDouble(value);
                case "period" -> period = Integer.parseInt(value);
                case "phase" -> phase = Integer.parseInt(value);
                case "anim" -> animPeriod = Integer.parseInt(value);
                case "seed" -> seed = Long.parseLong(value);
                case "cells" -> cells = cells(value);
                case "orbit" -> orbit(value.split(","));
                case "drift" -> drift(value.split(","));
                default -> throw new IllegalArgumentException("unknown --inflow key: " + key);
            }
        }

        private void orbit(final String[] radiusAndPeriod) {
            motion = InflowSource.Motion.ORBIT;
            m0 = Double.parseDouble(radiusAndPeriod[0]);
            motionPeriod = Integer.parseInt(radiusAndPeriod[1]);
        }

        private void drift(final String[] dxAndDy) {
            motion = InflowSource.Motion.DRIFT;
            m0 = Double.parseDouble(dxAndDy[0]);
            m1 = Double.parseDouble(dxAndDy[1]);
        }

        private static int[] cells(final String csv) {
            final String[] parts = csv.split(",");
            final int[] cells = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                cells[i] = Integer.parseInt(parts[i].trim());
            }
            return cells;
        }
    }
}
