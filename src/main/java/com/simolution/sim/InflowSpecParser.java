package com.simolution.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return new InflowSource.Disk(p.dbl("fx", CENTER), p.dbl("fy", CENTER), p.dbl("r", 10.0), p.dbl("peak", PEAK),
                p.integer("period", 0), p.integer("phase", 0),
                p.motion(), p.dbl("m0", 0.0), p.dbl("m1", 0.0), p.integer("motionPeriod", 0));
    }

    private static InflowSource rect(final Params p) {
        return new InflowSource.Rect(p.dbl("fx", CENTER), p.dbl("fy", CENTER),
                p.dbl("fx1", FAR_EDGE), p.dbl("fy1", FAR_EDGE), p.dbl("peak", PEAK),
                p.integer("period", 0), p.integer("phase", 0), p.dbl("m0", 0.0), p.dbl("m1", 0.0));
    }

    private static InflowSource points(final Params p) {
        return new InflowSource.Points(p.cells(), p.dbl("peak", PEAK), p.integer("period", 0), p.integer("phase", 0));
    }

    private static InflowSource noise(final Params p) {
        return new InflowSource.Noise(p.lng("seed", 0L), p.dbl("freq", 0.05), p.dbl("thr", 0.5), p.dbl("gain", 1.0),
                p.dbl("peak", PEAK), p.integer("anim", 0));
    }

    private static InflowSource ring(final Params p) {
        return new InflowSource.Ring(p.dbl("fx", CENTER), p.dbl("fy", CENTER), p.dbl("wavelength", 30.0),
                p.dbl("speed", 0.05), p.dbl("peak", PEAK), p.dbl("width", 6.0));
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

        private final Map<String, Object> values = new HashMap<>();

        static Params of(final String[] tokens) {
            final Params params = new Params();
            for (int i = 1; i < tokens.length; i++) {
                params.put(tokens[i]);
            }
            return params;
        }

        double dbl(final String key, final double fallback) {
            return (Double) values.getOrDefault(key, fallback);
        }

        int integer(final String key, final int fallback) {
            return (Integer) values.getOrDefault(key, fallback);
        }

        long lng(final String key, final long fallback) {
            return (Long) values.getOrDefault(key, fallback);
        }

        int[] cells() {
            return (int[]) values.getOrDefault("cells", new int[0]);
        }

        InflowSource.Motion motion() {
            return (InflowSource.Motion) values.getOrDefault("motion", InflowSource.Motion.STATIC);
        }

        private void put(final String pair) {
            final String key = key(pair);
            final String value = value(pair);
            switch (key) {
                case "fx", "fx0" -> values.put("fx", Double.parseDouble(value));
                case "fy", "fy0" -> values.put("fy", Double.parseDouble(value));
                case "fx1", "fy1", "r", "peak", "freq", "thr", "gain", "wavelength", "speed", "width" ->
                        values.put(key, Double.parseDouble(value));
                case "period", "phase", "anim" -> values.put(key, Integer.parseInt(value));
                case "seed" -> values.put(key, Long.parseLong(value));
                case "cells" -> values.put(key, cells(value));
                case "orbit" -> orbit(value.split(","));
                case "drift" -> drift(value.split(","));
                default -> throw new IllegalArgumentException("unknown --inflow key: " + key);
            }
        }

        private void orbit(final String[] radiusAndPeriod) {
            values.put("motion", InflowSource.Motion.ORBIT);
            values.put("m0", Double.parseDouble(radiusAndPeriod[0]));
            values.put("motionPeriod", Integer.parseInt(radiusAndPeriod[1]));
        }

        private void drift(final String[] dxAndDy) {
            values.put("motion", InflowSource.Motion.DRIFT);
            values.put("m0", Double.parseDouble(dxAndDy[0]));
            values.put("m1", Double.parseDouble(dxAndDy[1]));
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
