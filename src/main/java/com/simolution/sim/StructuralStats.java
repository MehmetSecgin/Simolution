package com.simolution.sim;

public record StructuralStats(
        long connectionsCompiled,
        long connectionsMeaningful,
        long duplicatePairs,
        double weightAbsMean,
        double weightAbsMax,
        double weightPositiveFraction,
        boolean[] sensorActionReachable,
        boolean[] randWired
) {

    public int countReachable() {
        return count(sensorActionReachable);
    }

    public int countRandWired() {
        return count(randWired);
    }

    private static int count(boolean[] flags) {
        int n = 0;
        for (boolean flag : flags) {
            if (flag) {
                n++;
            }
        }
        return n;
    }
}
