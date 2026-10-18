package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

    private static void assertRejected(final String field, final String value, final String messagePart) {
        final String json = ConfigJson.write(EngineConfig.defaults(1L, 10, 8))
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
        final EngineConfig config = EngineConfig.defaults(1L, 10, 8);

        final IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> config.withWorldWidth(50_000).withMaxGenes(1000));

        assertTrue(e.getMessage().contains("maxGenes"), e.getMessage());
    }

    @Test
    void rejectsBadChemicalTable() {
        final EngineConfig config = EngineConfig.defaults(1L, 10, 8);

        assertThrows(IllegalArgumentException.class, () -> config.withChemicals(List.of()));
    }

    @Test
    void acceptsOneCellAndZeroGenes() {
        EngineConfig.defaults(0L, 1, 0);
    }
}
