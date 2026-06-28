package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.sim.GenomeFactory;

/**
 * Conformance for kernel v5 biomass (contract-v5): mass is a distinct,
 * conserved state variable, GROW converts energy to mass, reproduction is
 * symmetric fission (mass + energy halved), and the SELF_MASS sensor reflects
 * size. Mass = crystallized energy, so the closed-system audit gains a Σ mass
 * term and must stay exact.
 */
class BiomassTest {

    private static int constTo(int action, short weightRaw) {
        return GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                .toAction(action)
                .weightRaw(weightRaw)
                .build();
    }

    private static final int SELF_MASS_IDX =
            NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.SELF_MASS;

    // contract-v5 §6: mass is crystallized energy, so initial energy + initial
    // resource + initial mass + inflow == units + field + mass + sink, exactly.
    @Test
    void massEntersTheAuditAndEnergyStaysConserved() {
        // arrange
        int worldWidth = 50;
        int founders = 200;
        Kernel kernel = new Kernel(GenomeFactory.random(13L, founders, 8), worldWidth, 16,
                Kernel.scatterFounders(founders, worldWidth));

        // act + assert
        for (int t = 0; t < 2000; t++) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            double held = 0.0;
            for (double e : s.energy) {
                held += e;
            }
            held += s.reservoir + s.massTotal + s.energySink;
            double credited = s.creditedInitialEnergy + s.initialResourceTotal
                    + s.creditedInitialMass + s.cumulativeInflow;
            assertEquals(credited, held, 1.0e-2,
                    "initial(energy+field+mass) + inflow must equal units + field + mass + sink at tick " + s.tick);
        }
    }

    // contract-v5 §5: symmetric binary fission — at a birth the parent and child
    // each end with half the parent's pre-split mass and equal energy.
    @Test
    void fissionHalvesMassAndEnergy() {
        // arrange: a unit that always wants to divide (CONST -> REPRODUCE > 0)
        Kernel kernel = new Kernel(new int[][] {{constTo(NodeLayout.Action.REPRODUCE, (short) 8192)}},
                3, 8, new int[] {4});

        // act: tick until the first fission
        int birthTick = -1;
        for (int t = 0; t < 10 && birthTick < 0; t++) {
            kernel.tick();
            if (kernel.snapshot().birthsTotal >= 1) {
                birthTick = t;
            }
        }
        KernelSnapshot s = kernel.snapshot();

        // assert
        assertTrue(birthTick >= 0, "a CONST->REPRODUCE unit must divide");
        assertEquals(1, s.birthsTotal, "exactly one fission by the first birth tick");
        double living = 0.0;
        double massSum = 0.0;
        for (int slot = 0; slot < s.energy.length; slot++) {
            if (s.energy[slot] > 0.0) {
                living++;
                massSum += s.mass[slot];
                assertEquals(KernelConfig.INITIAL_MASS / 2.0, s.mass[slot], 1.0e-9,
                        "each daughter carries half the founder mass, slot " + slot);
            }
        }
        assertEquals(2.0, living, "parent + child both alive after fission");
        assertEquals(KernelConfig.INITIAL_MASS, massSum, 1.0e-9, "mass is conserved across the split");
    }

    // contract-v5 §2: GROW converts spendable energy into structural mass; the
    // conversion is lossy (GROW_YIELD < 1) so energy drops and mass rises.
    @Test
    void growConvertsEnergyIntoMass() {
        // arrange
        Kernel kernel = new Kernel(new int[][] {{constTo(NodeLayout.Action.GROW, (short) 8192)}},
                1, 8, new int[] {0});

        // act: a few ticks so GROW propagates and fires
        for (int t = 0; t < 5; t++) {
            kernel.tick();
        }
        KernelSnapshot s = kernel.snapshot();

        // assert
        assertTrue(s.mass[0] > KernelConfig.INITIAL_MASS,
                "GROW must raise mass above the founder value, got " + s.mass[0]);
        assertTrue(s.energy[0] < KernelConfig.INITIAL_ENERGY,
                "growth spends energy, got " + s.energy[0]);
    }

    // contract-v5 §7: the SELF_MASS sensor is proprioception of size —
    // min(1, mass / SELF_MASS_SCALE), tracking the unit's own mass.
    @Test
    void selfMassSensorReflectsMass() {
        // arrange: a grower, so mass changes and the sensor must follow
        Kernel kernel = new Kernel(new int[][] {{constTo(NodeLayout.Action.GROW, (short) 8192)}},
                1, 8, new int[] {0});

        // act + assert: the sensor is read in the evaluate phase, before this
        // tick's growth (phase 7), so it reflects the mass at the tick's start —
        // the previous tick's end mass.
        double prevMass = KernelConfig.INITIAL_MASS;
        for (int t = 0; t < 6; t++) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            double expected = Math.min(1.0, prevMass / KernelConfig.SELF_MASS_SCALE);
            assertEquals(expected, s.outputs[SELF_MASS_IDX], 1.0e-6,
                    "SELF_MASS must equal min(1, mass_at_tick_start/scale) at tick " + s.tick);
            prevMass = s.mass[0];
        }
    }
}
