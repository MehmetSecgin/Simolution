package com.simolution.kernel.config;

public record Mutation(
        double weightRate,
        double structRate,
        double duplicationRate,
        double deletionRate
) {

    public Mutation {
        Checks.unitInterval("mutationRateWeight", weightRate);
        Checks.unitInterval("mutationRateStruct", structRate);
        Checks.unitInterval("indelRateDup", duplicationRate);
        Checks.unitInterval("indelRateDel", deletionRate);
    }
}
