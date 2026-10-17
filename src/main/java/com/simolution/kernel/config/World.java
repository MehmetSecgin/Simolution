package com.simolution.kernel.config;

public record World(
        int worldWidth,
        int maxGenes
) {

    public World {
        if (worldWidth < 1) {
            throw new IllegalArgumentException("worldWidth must be at least 1, got " + worldWidth);
        }
        if (maxGenes < 0) {
            throw new IllegalArgumentException("maxGenes must be at least 0, got " + maxGenes);
        }
        if ((long) worldWidth * worldWidth * maxGenes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxGenes " + maxGenes + " with worldWidth " + worldWidth
                    + " needs more gene slots than an int array holds");
        }
    }
}
