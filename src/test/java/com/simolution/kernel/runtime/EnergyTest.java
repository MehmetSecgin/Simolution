package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.GenomeFactory;
import com.simolution.support.EnergyAudit;
import com.simolution.support.Genes;
import com.simolution.support.Ticks;
import com.simolution.support.Worlds;

class EnergyTest {

    private static final EngineConfig DEFAULTS = EngineConfig.defaults(0L, 1, 1);

    private static final int[] BUSY_GENOME = {
            Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.ADD, 4096),
            Genes.internalToInternal(NodeLayout.Internal.ADD, NodeLayout.Internal.DELAY, 4096),
            Genes.internalToInternal(NodeLayout.Internal.DELAY, NodeLayout.Internal.ADD, 4096)
    };

    private static final int[] DELAY_BOMB = {
            Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.DELAY, 8192),
            Genes.internalToInternal(NodeLayout.Internal.DELAY, NodeLayout.Internal.DELAY, 16383),
            Genes.internalToAction(NodeLayout.Internal.DELAY, NodeLayout.Action.HARVEST, 16383)
    };

    private static final int DELAY_BOMB_TRANSPORTERS = 1;
    private static final int POPULATION = 50;
    private static final int POPULATION_WORLD_WIDTH = 71;
    private static final double AUDIT_TOLERANCE = 1.0e-2;
    private static final int TICKS_TO_OUTLAST_STARVATION = 5000;
    private static final int TICKS_TO_OUTLAST_IDLE_DRAIN = 100_000;

    @Test
    @DisplayName("contract-v5 §6: initial energy + initial resource + initial mass + inflow == units + total field + mass + sink")
    void openSystemConservesEnergyExactly() {
        // arrange
        final int[][] genomes = GenomeFactory.random(11L, POPULATION, 32);
        final Kernel kernel = Worlds.kernel(POPULATION_WORLD_WIDTH, genomes);
        final double initialCredit = DEFAULTS.founder().initialEnergy() * POPULATION
                + DEFAULTS.chemicals().get(0).initial() * POPULATION_WORLD_WIDTH * POPULATION_WORLD_WIDTH
                + DEFAULTS.founder().initialMass() * POPULATION;

        for (int i = 0; i < 500; i++) {
            // act
            kernel.tick();

            // assert
            EnergyAudit.assertConserved(kernel.snapshot(), initialCredit, AUDIT_TOLERANCE);
        }
    }

    @Test
    void totalEnergyMonotonicallyDecreases() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, BUSY_GENOME);
        double previous = DEFAULTS.founder().initialEnergy();

        for (int i = 0; i < 100; i++) {
            // act
            kernel.tick();

            // assert
            final double now = EnergyAudit.totalEnergy(kernel.snapshot());
            assertTrue(now <= previous, "energy must not increase at tick " + (i + 1));
            previous = now;
        }
    }

    @Test
    void unitDiesAndStaysDeadAndInert() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, BUSY_GENOME);

        // act
        final int ticksToDeath =
                Ticks.until(kernel, TICKS_TO_OUTLAST_STARVATION, () -> kernel.snapshot().energy[0] <= 0.0);
        final float[] frozenOutputs = kernel.snapshot().outputsPrev.clone();

        // assert
        assertTrue(ticksToDeath > 0, "busy unit must die within its energy budget");
        for (int i = ticksToDeath; i < TICKS_TO_OUTLAST_STARVATION; i++) {
            kernel.tick();
            final WorldState snapshot = kernel.snapshot();
            assertEquals(0.0, snapshot.energy[0], 0.0);
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                assertEquals(frozenOutputs[n], snapshot.outputsPrev[n], 0.0,
                        "dead unit node " + n + " must stay frozen");
            }
        }
    }

    @Test
    @DisplayName("contract-v1 §6: basal cost and mass maintenance apply even with zero connections")
    void idleUnitPaysBasalAndMaintenance() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, new int[0]);

        // act
        kernel.tick();

        // assert
        final double expected = DEFAULTS.founder().initialEnergy()
                - DEFAULTS.metabolism().basalCost()
                - DEFAULTS.founder().initialMass() * DEFAULTS.metabolism().maintenancePerMass();
        assertEquals(expected, kernel.snapshot().energy[0], 1.0e-9, "basal + mass maintenance");
    }

    @Test
    @DisplayName("the fixed basal floor drives even a connectionless unit across zero")
    void idleUnitEventuallyDies() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, new int[0]);

        // act
        final int ticksToDeath =
                Ticks.until(kernel, TICKS_TO_OUTLAST_IDLE_DRAIN, () -> kernel.snapshot().energy[0] <= 0.0);

        // assert
        assertTrue(ticksToDeath > 0, "no costless persistence: an idle unit must eventually die");
    }

    @Test
    @DisplayName("saturating uptake caps per-tick intake at capacityPerConnection * transporters * mass^alpha")
    void intakeNeverExceedsTheTransporterCeiling() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, DELAY_BOMB);
        final double ceiling = DEFAULTS.harvest().capacityPerConnection() * DELAY_BOMB_TRANSPORTERS
                * Math.pow(DEFAULTS.founder().initialMass(), DEFAULTS.harvest().massExponent());

        // act
        double previousEnergy = DEFAULTS.founder().initialEnergy();
        double maxGain = 0.0;
        for (int i = 0; i < 4000; i++) {
            kernel.tick();
            final double energy = kernel.snapshot().energy[0];
            maxGain = Math.max(maxGain, energy - previousEnergy);
            previousEnergy = energy;
        }

        // assert
        assertTrue(maxGain <= ceiling + 1.0e-9,
                "per-tick intake must not exceed the unit's emergent ceiling " + ceiling + ", got " + maxGain);
    }

    @Test
    @DisplayName("the diverging harvester overflows to a non-finite signal, intake stops, maintenance kills it")
    void divergentHarvesterEventuallyDies() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, DELAY_BOMB);

        // act
        final int ticksToDeath = Ticks.until(kernel, 4000, () -> kernel.snapshot().energy[0] <= 0.0);

        // assert
        assertTrue(ticksToDeath > 0, "the diverger implodes: its signal overflows, intake stops, maintenance kills it");
    }

    @Test
    @DisplayName("contract-v7.1: energy never negative, sink never exceeds what entered the open system")
    void deductionClampsToAvailableNeverOverdraws() {
        // arrange
        final Kernel kernel = Worlds.kernel(1, BUSY_GENOME);

        for (int i = 0; i < 5000; i++) {
            // act
            kernel.tick();

            // assert
            final WorldState snapshot = kernel.snapshot();
            assertTrue(snapshot.energy[0] >= 0.0, "energy must never be negative");
            assertTrue(snapshot.energySink <= EnergyAudit.credited(snapshot) + 1.0e-9,
                    "sink must never exceed what entered the system");
        }
    }

    @Test
    void deadUnitsDoNotAffectLivingUnitTrajectory() {
        // arrange
        final Kernel alone = Worlds.kernel(1, BUSY_GENOME);
        final Kernel paired = Worlds.kernel(2, BUSY_GENOME, BUSY_GENOME);

        for (int i = 0; i < 200; i++) {
            // act
            alone.tick();
            paired.tick();

            // assert
            final float[] aloneOut = alone.snapshot().outputsPrev;
            final float[] pairedOut = paired.snapshot().outputsPrev;
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                assertEquals(aloneOut[n], pairedOut[n], 0.0,
                        "unit 0 trajectory must not depend on unit 1 at tick " + (i + 1));
            }
        }
    }
}
