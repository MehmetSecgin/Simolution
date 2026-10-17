package com.simolution.kernel.config;

public record Growth(double max, double halfSaturation, double yield) {

    public Growth {
        Checks.nonNegative("growMax", max);
        Checks.nonNegative("growHalfSaturation", halfSaturation);
        Checks.unitInterval("growYield", yield);
    }
}
