package com.simolution.kernel.config;

import java.util.List;

public class CompiledInflowField {

    private final double baseline;
    private final double[] baked;
    private final InflowSource[] dynamic;
    private final InflowConfig.Combine combine;
    private final int worldWidth;

    CompiledInflowField(final InflowConfig config, final int worldWidth) {
        this.baseline = config.baseline();
        this.combine = config.combine();
        this.worldWidth = worldWidth;
        this.dynamic = select(config.sources(), false);
        this.baked = bake(select(config.sources(), true));
    }

    public double cap(final int cell, final int tick) {
        double acc = baked != null ? baked[cell] : baseline;
        if (dynamic.length == 0) {
            return acc;
        }
        final int x = x(cell);
        final int y = y(cell);
        for (InflowSource s : dynamic) {
            acc = combine.apply(acc, s.capAt(x, y, tick, worldWidth));
        }
        return acc;
    }

    private static InflowSource[] select(final List<InflowSource> sources, final boolean wantStatic) {
        int count = 0;
        for (InflowSource s : sources) {
            if (s.isStatic() == wantStatic) {
                count++;
            }
        }
        final InflowSource[] selected = new InflowSource[count];
        int next = 0;
        for (InflowSource s : sources) {
            if (s.isStatic() == wantStatic) {
                selected[next++] = s;
            }
        }
        return selected;
    }

    private double[] bake(final InflowSource[] fixed) {
        if (fixed.length == 0) {
            return null;
        }
        final double[] layer = new double[worldWidth * worldWidth];
        for (int cell = 0; cell < layer.length; cell++) {
            layer[cell] = bakedCapAt(cell, fixed);
        }
        return layer;
    }

    private double bakedCapAt(final int cell, final InflowSource[] fixed) {
        double acc = baseline;
        for (InflowSource s : fixed) {
            acc = combine.apply(acc, s.capAt(x(cell), y(cell), 0, worldWidth));
        }
        return acc;
    }

    private int x(final int cell) {
        return cell % worldWidth;
    }

    private int y(final int cell) {
        return cell / worldWidth;
    }
}
