package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

class EnergyTest {

    private static final int[] BUSY_GENOME = {
            GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 4096)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                       .toInternal(NodeLayout.Internal.DELAY)
                       .weightRaw((short) 4096)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 4096)
                       .build()
    };

    private static double totalEnergy(KernelSnapshot snapshot) {
        double total = 0.0;
        for (double e : snapshot.energy) {
            total += e;
        }
        return total;
    }

    @Test
    void openSystemConservesEnergyExactly() {
        // arrange
        int[][] genomes = com.simolution.sim.GenomeFactory.random(11L, 50, 32);
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        Kernel kernel = new Kernel(50, connections);
        double credited = KernelConfig.INITIAL_ENERGY * 50 + KernelConfig.RESOURCE_INITIAL;

        // act + assert: contract v1 §7 — initial + inflow == units + reservoir + sink
        for (int i = 0; i < 500; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            double held = totalEnergy(snapshot) + snapshot.reservoir + snapshot.energySink;
            assertEquals(credited + snapshot.cumulativeInflow, held, 1.0e-3,
                    "units + reservoir + sink must equal initial + inflow at tick " + snapshot.tick);
        }
    }

    @Test
    void totalEnergyMonotonicallyDecreases() {
        // arrange
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {BUSY_GENOME}));

        // act + assert
        double previous = KernelConfig.INITIAL_ENERGY;
        for (int i = 0; i < 100; i++) {
            kernel.tick();
            double now = totalEnergy(kernel.snapshot());
            assertTrue(now <= previous, "energy must not increase at tick " + (i + 1));
            previous = now;
        }
    }

    @Test
    void unitDiesAndStaysDeadAndInert() {
        // arrange: tiny budget so death comes fast
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {BUSY_GENOME}));
        int ticksToOutlast = (int) Math.ceil(KernelConfig.INITIAL_ENERGY
                / (BUSY_GENOME.length * KernelConfig.DECAY_PER_CONNECTION)) + 50;

        // act
        double[] frozenOutputs = null;
        boolean everDied = false;
        for (int i = 0; i < ticksToOutlast; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            if (snapshot.energy[0] <= 0.0) {
                if (!everDied) {
                    everDied = true;
                    frozenOutputs = snapshot.outputs.clone();
                } else {
                    // assert: corpse never changes
                    assertEquals(0.0, snapshot.energy[0], 0.0);
                    for (int n = 0; n < NodeLayout.TOTAL; n++) {
                        assertEquals(frozenOutputs[n], snapshot.outputs[n], 0.0,
                                "dead unit node " + n + " must stay frozen");
                    }
                }
            }
        }

        // assert
        assertTrue(everDied, "busy unit must die within its energy budget");
    }

    @Test
    void emptyStructureNeverDecays() {
        // arrange: zero connections -> zero structural decay -> immortal
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {{}}));

        // act
        for (int i = 0; i < 1000; i++) {
            kernel.tick();
        }

        // assert
        assertEquals(KernelConfig.INITIAL_ENERGY, kernel.snapshot().energy[0], 0.0);
    }

    @Test
    void deductionClampsToAvailableNeverOverdraws() {
        // arrange
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {BUSY_GENOME}));
        double initialTotal = KernelConfig.INITIAL_ENERGY;

        // act + assert: energy never goes negative, sink never exceeds initial
        for (int i = 0; i < 5000; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            assertTrue(snapshot.energy[0] >= 0.0, "energy must never be negative");
            assertTrue(snapshot.energySink <= initialTotal + 1.0e-9,
                    "sink must never exceed what existed");
        }
    }

    @Test
    void deadUnitsDoNotAffectLivingUnitTrajectory() {
        // arrange: same unit alone vs beside a unit that dies early
        int[][] solo = {BUSY_GENOME};
        int[][] withDying = {BUSY_GENOME, BUSY_GENOME};
        Kernel alone = new Kernel(1, GenomeCompiler.compileAll(solo));
        Kernel paired = new Kernel(2, GenomeCompiler.compileAll(withDying));

        // act + assert
        for (int i = 0; i < 200; i++) {
            alone.tick();
            paired.tick();
            double[] aloneOut = alone.snapshot().outputs;
            double[] pairedOut = paired.snapshot().outputs;
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                assertEquals(aloneOut[n], pairedOut[n], 0.0,
                        "unit 0 trajectory must not depend on unit 1 at tick " + (i + 1));
            }
        }
    }
}
