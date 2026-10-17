package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeCatalog;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.layout.NodeSpec.Kind;
import com.simolution.support.EnergyAudit;
import com.simolution.support.Genes;
import com.simolution.support.Ticks;
import com.simolution.support.Worlds;

class BiomassTest {

    private static final EngineConfig DEFAULTS = EngineConfig.defaults(0L, 1, 1);

    private static final int SELF_MASS_IDX =
            NodeLayout.SENSOR_OFFSET + NodeCatalog.V5.indexOf(Kind.SENSOR, "SELF_MASS");
    private static final int ALWAYS_ON_RAW = 8192;

    @Test
    @DisplayName("contract-v5 §6: mass is crystallized energy, so the audit including mass stays exact")
    void massEntersTheAuditAndEnergyStaysConserved() {
        // arrange
        int worldWidth = 50;
        Kernel kernel = Worlds.randomKernel(13L, 200, 8, EngineConfig.defaults(0L, worldWidth, 16));

        for (int t = 0; t < 2000; t++) {
            // act
            kernel.tick();

            // assert
            EnergyAudit.assertConserved(kernel.snapshot(), 1.0e-2);
        }
    }

    @Test
    @DisplayName("contract-v5 §5: symmetric binary fission, parent and child each end with half the mass")
    void fissionHalvesMassAndEnergy() {
        // arrange
        Kernel kernel = Worlds.kernelAt(EngineConfig.defaults(0L, 3, 8),
                new int[][] {{Genes.constTo(NodeLayout.Action.REPRODUCE, ALWAYS_ON_RAW)}}, 4);

        // act
        int ticksToBirth = Ticks.until(kernel, 10, () -> kernel.snapshot().birthsTotal >= 1);
        KernelSnapshot snapshot = kernel.snapshot();

        // assert
        assertTrue(ticksToBirth > 0, "a CONST->REPRODUCE unit must divide");
        assertEquals(1, snapshot.birthsTotal, "exactly one fission by the first birth tick");
        double living = 0.0;
        double massSum = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] > 0.0) {
                living++;
                massSum += snapshot.mass[slot];
                assertEquals(DEFAULTS.founder().initialMass() / 2.0, snapshot.mass[slot], 1.0e-9,
                        "each daughter carries half the founder mass, slot " + slot);
            }
        }
        assertEquals(2.0, living, "parent + child both alive after fission");
        assertEquals(DEFAULTS.founder().initialMass(), massSum, 1.0e-9, "mass is conserved across the split");
    }

    @Test
    @DisplayName("contract-v5 §2: GROW converts energy into mass, lossily")
    void growConvertsEnergyIntoMass() {
        // arrange
        Kernel kernel = growerKernel();

        // act
        kernel.step(5);

        // assert
        KernelSnapshot snapshot = kernel.snapshot();
        assertTrue(snapshot.mass[0] > DEFAULTS.founder().initialMass(),
                "GROW must raise mass above the founder value, got " + snapshot.mass[0]);
        assertTrue(snapshot.energy[0] < DEFAULTS.founder().initialEnergy(),
                "growth spends energy, got " + snapshot.energy[0]);
    }

    @Test
    @DisplayName("contract-v5 §7: SELF_MASS is min(1, mass at tick start / scale), read before this tick's growth")
    void selfMassSensorReflectsMass() {
        // arrange
        Kernel kernel = growerKernel();
        double previousMass = DEFAULTS.founder().initialMass();

        for (int t = 0; t < 6; t++) {
            // act
            kernel.tick();

            // assert
            KernelSnapshot snapshot = kernel.snapshot();
            double expected = Math.min(1.0, previousMass / DEFAULTS.sensing().selfMassScale());
            assertEquals(expected, snapshot.outputs[SELF_MASS_IDX], 1.0e-6,
                    "SELF_MASS must equal min(1, mass_at_tick_start/scale) at tick " + snapshot.tick);
            previousMass = snapshot.mass[0];
        }
    }

    private static Kernel growerKernel() {
        return Worlds.kernelAt(EngineConfig.defaults(0L, 1, 8),
                new int[][] {{Genes.constTo(NodeLayout.Action.GROW, ALWAYS_ON_RAW)}}, 0);
    }
}
