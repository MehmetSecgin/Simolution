package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

    @Test
    void roundTripsThroughJson() {
        EngineConfig defaults = EngineConfig.v5Defaults(42L, 100, 64);
        EngineConfig fielded = defaults.withChemical0Inflow(InflowConfig.field(0.2, InflowConfig.Combine.ADD,
                new InflowSource.Noise(11L, 0.06, 0.5, 3.0, 8.0, 5000),
                new InflowSource.Disk(0.7, 0.3, 6, 5.0, 1000, 0, InflowSource.Motion.ORBIT, 0.1, 0.0, 4000),
                new InflowSource.Points(new int[] {1, 5, 9}, 4.0, 0, 0)));

        assertEquals(defaults, ConfigJson.read(ConfigJson.write(defaults)));
        assertEquals(fielded, ConfigJson.read(ConfigJson.write(fielded)));
    }

    @Test
    void rejectsUnknownKey() {
        String json = ConfigJson.write(EngineConfig.v5Defaults(1L, 10, 8)).replace("\"basalCost\"", "\"basalCostt\"");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigJson.read(json));

        assertTrue(e.getMessage().contains("basalCostt"), e.getMessage());
    }

    @Test
    void rejectsMissingKey() {
        String json = ConfigJson.write(EngineConfig.v5Defaults(1L, 10, 8)).replaceAll("\\s*\"growYield\"[^\\n]*\\n", "\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigJson.read(json));

        assertTrue(e.getMessage().contains("growYield"), e.getMessage());
    }

    @Test
    void branchCompatibility() {
        EngineConfig base = EngineConfig.v5Defaults(1L, 100, 64);

        assertFalse(base.branchCompatible(EngineConfig.v5Defaults(1L, 120, 64)));
        assertTrue(base.branchCompatible(EngineConfig.v5Defaults(1L, 100, 64).withSeed(9L)));
        assertTrue(base.branchCompatible(new EngineConfig(1L, 100, 64, base.chemicals(), base.initialEnergy(),
                0.9, base.costPerPropagation(), base.harvestCapacityPerConnection(), base.harvestHalfSaturation(),
                base.harvestMassExponent(), base.moveCostBase(), base.moveCostPerMass(), base.moveDeadzone(),
                base.selfEnergyScale(), base.selfMassScale(), base.initialMass(), base.maintPerMass(),
                base.growMax(), base.growHalfSaturation(), base.growYield(), base.buildCost(),
                base.buildCostPerGene(), base.mutationRateWeight(), base.mutationRateStruct(),
                base.indelRateDup(), base.indelRateDel(), base.agingCost())));
    }
}
