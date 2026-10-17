package com.simolution.sim;

final class ResourceLevels {

    private static final int TOP_LEVEL = 9;

    private final double capacity;

    ResourceLevels(final double capacity) {
        this.capacity = capacity;
    }

    int level(final double value) {
        final int level = (int) Math.round(TOP_LEVEL * value / capacity);
        return Math.max(0, Math.min(TOP_LEVEL, level));
    }
}
