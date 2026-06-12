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
    void idleUnitLeaksProportionally() {
        // arrange: zero connections -> no structural decay, but storage
        // maintenance (leak proportional to energy) still applies (v1 §6a)
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {{}}));

        // act
        kernel.tick();
        double afterOne = kernel.snapshot().energy[0];

        // assert: exactly one leak charge of energy * STORAGE_LEAK_RATE
        assertEquals(KernelConfig.INITIAL_ENERGY * (1.0 - KernelConfig.STORAGE_LEAK_RATE),
                afterOne, 1.0e-9, "one tick of proportional storage leak");

        double previous = afterOne;
        for (int i = 0; i < 200; i++) {
            kernel.tick();
            double now = kernel.snapshot().energy[0];
            assertTrue(now < previous, "idle store must keep leaking, not stay immortal");
            previous = now;
        }
        assertTrue(previous < KernelConfig.INITIAL_ENERGY, "no costless persistence");
    }

    @Test
    void intakeSaturatesAndDivergentHarvesterImplodes() {
        // arrange: a DELAY self-loop (gain ~2) feeding HARVEST — the runaway
        // "eat everything" strategy. Saturating uptake must cap its per-tick
        // intake at INTAKE_MAX, and storage leak must stop it hoarding.
        int[] delayBomb = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.DELAY).weightRaw((short) 8192).build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.DELAY).weightRaw((short) 16383).build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toAction(NodeLayout.Action.HARVEST).weightRaw((short) 16383).build()
        };
        Kernel kernel = new Kernel(1, GenomeCompiler.compileAll(new int[][] {delayBomb}));

        // act
        double prevEnergy = KernelConfig.INITIAL_ENERGY;
        double maxEnergy = prevEnergy;
        double maxGain = 0.0;
        boolean died = false;
        for (int i = 0; i < 4000; i++) {
            kernel.tick();
            double e = kernel.snapshot().energy[0];
            maxGain = Math.max(maxGain, e - prevEnergy);
            maxEnergy = Math.max(maxEnergy, e);
            if (e <= 0.0) {
                died = true;
            }
            prevEnergy = e;
        }

        // assert: one transporter (DELAY->HARVEST), so the emergent ceiling is
        // CAPACITY_PER_CONNECTION * 1
        double capacity = KernelConfig.HARVEST_CAPACITY_PER_CONNECTION * 1;
        assertTrue(maxGain <= capacity + 1.0e-9,
                "per-tick intake must not exceed the unit's emergent ceiling " + capacity
                        + ", got " + maxGain);
        double carryingCap = capacity / KernelConfig.STORAGE_LEAK_RATE;
        double bound = Math.max(KernelConfig.INITIAL_ENERGY, carryingCap) + 1.0;
        assertTrue(maxEnergy < bound,
                "a saturated harvester cannot hoard past its carrying capacity " + carryingCap
                        + " (or its starting bank), reached " + maxEnergy);
        assertTrue(died, "the diverger implodes: once its signal overflows, intake stops and leak kills it");
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
