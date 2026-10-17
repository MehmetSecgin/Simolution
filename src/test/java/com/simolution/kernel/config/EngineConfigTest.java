package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    private static void assertRejected(final String field, final String value, final String messagePart) {
        final String json = ConfigJson.write(EngineConfig.v5Defaults(1L, 10, 8))
                .replaceAll("\"" + field + "\"\\s*:\\s*[^,\\n]*", "\"" + field + "\" : " + value);

        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigJson.read(json));

        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    @Test
    void rejectsProbabilitiesOutsideUnitInterval() {
        assertRejected("mutationRateWeight", "1.5", "mutationRateWeight");
        assertRejected("mutationRateStruct", "-0.1", "mutationRateStruct");
        assertRejected("indelRateDup", "2.0", "indelRateDup");
        assertRejected("indelRateDel", "-1.0", "indelRateDel");
        assertRejected("diffusionRate", "1.5", "chemicals[0].diffusionRate");
        assertRejected("decayRate", "-0.5", "chemicals[0].decayRate");
    }

    @Test
    void rejectsNegativeCostsAndRates() {
        assertRejected("buildCost", "-1.0", "buildCost");
        assertRejected("buildCostPerGene", "-0.1", "buildCostPerGene");
        assertRejected("basalCost", "-0.5", "basalCost");
        assertRejected("agingCost", "-0.5", "agingCost");
        assertRejected("maintPerMass", "-0.5", "maintPerMass");
        assertRejected("initialEnergy", "-1.0", "initialEnergy");
        assertRejected("growYield", "1.5", "growYield");
    }

    @Test
    void rejectsNonPositiveScalesAndCapacities() {
        assertRejected("capacity", "0.0", "chemicals[0].capacity");
        assertRejected("selfEnergyScale", "0.0", "selfEnergyScale");
        assertRejected("selfMassScale", "-1.0", "selfMassScale");
    }

    @Test
    void rejectsNonPositiveWorldAndNegativeMaxGenes() {
        assertRejected("worldWidth", "0", "worldWidth");
        assertRejected("maxGenes", "-1", "maxGenes");
    }

    @Test
    void rejectsGeneArrayThatOverflowsAnInt() {
        final EngineConfig config = EngineConfig.v5Defaults(1L, 10, 8);

        final IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> config.withWorldWidth(50_000).withMaxGenes(1000));

        assertTrue(e.getMessage().contains("maxGenes"), e.getMessage());
    }

    @Test
    void rejectsBadChemicalTable() {
        final EngineConfig config = EngineConfig.v5Defaults(1L, 10, 8);
        final ChemicalSpec first = config.chemicals().get(0);
        final ChemicalSpec selfDecay = new ChemicalSpec("a", first.inflow(), 1.0, 0.0, 0.1, 0.1, 0);
        final ChemicalSpec outOfRange = new ChemicalSpec("a", first.inflow(), 1.0, 0.0, 0.1, 0.1, 3);

        assertThrows(IllegalArgumentException.class, () -> config.withChemicals(List.of()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> config.withChemicals(List.of(selfDecay)))
                .getMessage().contains("decayTarget"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> config.withChemicals(List.of(outOfRange)))
                .getMessage().contains("decayTarget"));
    }

    @Test
    void acceptsTheDemoShape() {
        EngineConfig.v5Defaults(0L, 1, 0);
    }
}
