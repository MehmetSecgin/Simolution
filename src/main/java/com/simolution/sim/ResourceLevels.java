package com.simolution.sim;

final class ResourceLevels {

    private static final int TOP_LEVEL = 9;

    private ResourceLevels() {
    }

    static int level(final double value, final double capacity) {
        final int level = (int) Math.round(TOP_LEVEL * value / capacity);
        return Math.max(0, Math.min(TOP_LEVEL, level));
    }
}
