package com.simolution.sim;

public record StructuralStats(
        long connectionsCompiled,
        long connectionsMeaningful,
        long duplicatePairs,
        double weightAbsMean,
        double weightAbsMax,
        double weightPositiveFraction,
        int unitsSensorActionReachable,
        int unitsRandWired
) {

    public long connectionsJunkTouching() {
        return connectionsCompiled - connectionsMeaningful;
    }
}
