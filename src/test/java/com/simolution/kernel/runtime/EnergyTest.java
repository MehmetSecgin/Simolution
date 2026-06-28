package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
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
        int worldWidth = 71;
        Kernel kernel = new Kernel(genomes, worldWidth, 32,
                java.util.stream.IntStream.range(0, genomes.length).toArray());
        double credited = KernelConfig.INITIAL_ENERGY * 50
                + KernelConfig.CELL_INITIAL * worldWidth * worldWidth
                + KernelConfig.INITIAL_MASS * 50;

        // act + assert: contract v5 §6 — initial energy + initial resource +
        // initial mass + inflow == units + total field + mass + sink
        for (int i = 0; i < 500; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            double held = totalEnergy(snapshot) + snapshot.reservoir + snapshot.massTotal + snapshot.energySink;
            assertEquals(credited + snapshot.cumulativeInflow, held, 1.0e-2,
                    "units + reservoir + mass + sink must equal initial + inflow at tick " + snapshot.tick);
        }
    }

    @Test
    void totalEnergyMonotonicallyDecreases() {
        // arrange
        Kernel kernel = new Kernel(new int[][] {BUSY_GENOME}, 1, 32, new int[] {0});

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
        // arrange: no harvest -> basal + activity + maintenance with no intake
        // guarantees death; generous bound to outlast it
        Kernel kernel = new Kernel(new int[][] {BUSY_GENOME}, 1, 32, new int[] {0});
        int ticksToOutlast = 5000;

        // act
        float[] frozenOutputs = null;
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
    void idleUnitPaysBasalAndMaintenanceThenDies() {
        // arrange: zero connections -> no activity, but basal + storage
        // maintenance still apply (v1 §6/§6a) -> not immortal
        Kernel kernel = new Kernel(new int[][] {{}}, 1, 32, new int[] {0});

        // act
        kernel.tick();
        double afterOne = kernel.snapshot().energy[0];

        // assert: one tick = basal floor + proportional maintenance
        double expected = KernelConfig.INITIAL_ENERGY
                - KernelConfig.BASAL_COST
                - KernelConfig.INITIAL_MASS * KernelConfig.MAINT_PER_MASS;
        assertEquals(expected, afterOne, 1.0e-9, "basal + mass maintenance");

        // the fixed basal floor (unlike a purely proportional leak) drives energy
        // across zero: even a connectionless idle unit eventually dies
        boolean died = false;
        for (int i = 0; i < 100_000 && !died; i++) {
            kernel.tick();
            if (kernel.snapshot().energy[0] <= 0.0) {
                died = true;
            }
        }
        assertTrue(died, "no costless persistence: an idle unit must eventually die");
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
        Kernel kernel = new Kernel(new int[][] {delayBomb}, 1, 32, new int[] {0});

        // act
        double prevEnergy = KernelConfig.INITIAL_ENERGY;
        double maxGain = 0.0;
        boolean died = false;
        for (int i = 0; i < 4000; i++) {
            kernel.tick();
            double e = kernel.snapshot().energy[0];
            maxGain = Math.max(maxGain, e - prevEnergy);
            if (e <= 0.0) {
                died = true;
            }
            prevEnergy = e;
        }

        // assert: one transporter (DELAY->HARVEST), and the unit never grows (no
        // GROW wiring) so its mass stays INITIAL_MASS and the emergent ceiling is
        // CAPACITY_PER_CONNECTION * 1 * INITIAL_MASS^alpha
        double capacity = KernelConfig.HARVEST_CAPACITY_PER_CONNECTION * 1
                * Math.pow(KernelConfig.INITIAL_MASS, KernelConfig.HARVEST_MASS_EXPONENT);
        assertTrue(maxGain <= capacity + 1.0e-9,
                "per-tick intake must not exceed the unit's emergent ceiling " + capacity
                        + ", got " + maxGain);
        // v5 retired the proportional-to-energy storage leak; the diverger still
        // implodes by an honest route — its DELAY self-loop overflows to a
        // non-finite signal, harvest then reads garbage and stops, and basal +
        // mass maintenance + aging starve it.
        assertTrue(died, "the diverger implodes: its signal overflows, intake stops, maintenance kills it");
    }

    @Test
    void deductionClampsToAvailableNeverOverdraws() {
        // arrange
        Kernel kernel = new Kernel(new int[][] {BUSY_GENOME}, 1, 32, new int[] {0});

        // act + assert: energy never goes negative, sink never exceeds what has
        // entered the open system (initial energy+mass+resource + admitted inflow;
        // resource decay routes unconsumed inflow to the sink, contract v7.1)
        for (int i = 0; i < 5000; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            double credited = snapshot.creditedInitialEnergy + snapshot.creditedInitialMass
                    + snapshot.initialResourceTotal + snapshot.cumulativeInflow;
            assertTrue(snapshot.energy[0] >= 0.0, "energy must never be negative");
            assertTrue(snapshot.energySink <= credited + 1.0e-9,
                    "sink must never exceed what entered the system");
        }
    }

    @Test
    void deadUnitsDoNotAffectLivingUnitTrajectory() {
        // arrange: same unit alone vs beside a unit that dies early
        int[][] solo = {BUSY_GENOME};
        int[][] withDying = {BUSY_GENOME, BUSY_GENOME};
        Kernel alone = new Kernel(solo, 1, 32, new int[] {0});
        Kernel paired = new Kernel(withDying, 2, 32, new int[] {0, 1});

        // act + assert
        for (int i = 0; i < 200; i++) {
            alone.tick();
            paired.tick();
            float[] aloneOut = alone.snapshot().outputs;
            float[] pairedOut = paired.snapshot().outputs;
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                assertEquals(aloneOut[n], pairedOut[n], 0.0,
                        "unit 0 trajectory must not depend on unit 1 at tick " + (i + 1));
            }
        }
    }
}
