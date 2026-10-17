package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.support.EnergyAudit;
import com.simolution.support.Worlds;

class IndelTest {

    private static final EngineConfig DEFAULTS = EngineConfig.defaults(0L, 1, 1);

    private static final long SEED = 7L;
    private static final int UNITS = 1500;
    private static final int WORLD = 80;
    private static final int FOUNDER_GENES = 8;
    private static final int MAX_GENES = 16;
    private static final int TICKS = 4000;

    private static Kernel evolvingKernel() {
        return Worlds.randomKernel(SEED, UNITS, FOUNDER_GENES, Worlds.foodRich(WORLD, MAX_GENES));
    }

    @Test
    @DisplayName("contract-v4 §1: no operator pushes a genome outside [0, maxGenes]")
    void geneCountStaysWithinCapAndFloor() {
        // arrange
        Kernel kernel = evolvingKernel();

        // act + assert
        for (int t = 0; t < TICKS; t++) {
            kernel.tick();
            int[] geneCount = kernel.snapshot().geneCount;
            for (int slot = 0; slot < geneCount.length; slot++) {
                assertTrue(geneCount[slot] >= 0 && geneCount[slot] <= MAX_GENES,
                        "slot " + slot + " geneCount " + geneCount[slot]
                                + " escaped [0, " + MAX_GENES + "] at tick " + (t + 1));
            }
        }
    }

    @Test
    @DisplayName("contract-v4 §2: duplication grows length and deletion shrinks it, both visible over many births")
    void duplicationGrowsAndDeletionShrinksLength() {
        // arrange
        Kernel kernel = evolvingKernel();
        boolean sawGrowth = false;
        boolean sawShrink = false;

        // act
        for (int t = 0; t < TICKS; t++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            for (int slot = 0; slot < snapshot.geneCount.length; slot++) {
                if (snapshot.energy[slot] <= 0.0) {
                    continue;
                }
                sawGrowth |= snapshot.geneCount[slot] > FOUNDER_GENES;
                sawShrink |= snapshot.geneCount[slot] < FOUNDER_GENES;
            }
        }

        // assert
        assertTrue(sawGrowth, "tandem duplication never grew a living genome past " + FOUNDER_GENES);
        assertTrue(sawShrink, "deletion never shrank a living genome below " + FOUNDER_GENES);
    }

    @Test
    @DisplayName("contract-v4 §1: the floor is 0 genes, an empty genome is inert and dies of basal cost")
    void emptyGenomeIsInertAndMortal() {
        // arrange
        Kernel kernel = Worlds.kernel(EngineConfig.defaults(0L, 1, 8), new int[0]);
        double previous = DEFAULTS.founder().initialEnergy();
        boolean died = false;

        // act + assert
        for (int t = 0; t < 200_000 && !died; t++) {
            kernel.tick();
            double now = kernel.snapshot().energy[0];
            assertTrue(now <= previous + 1.0e-12,
                    "an inert empty genome can never gain energy (no harvest) at tick " + (t + 1));
            previous = now;
            died = now <= 0.0;
        }
        assertTrue(died, "an empty genome must still die of basal cost");
    }

    @Test
    @DisplayName("contract-v4 §5: the per-gene build cost flows to the sink, so the audit stays exact")
    void energyConservedWithPerGeneBuildCost() {
        // arrange
        Kernel kernel = evolvingKernel();

        // act + assert
        for (int t = 0; t < TICKS; t++) {
            kernel.tick();
            EnergyAudit.assertConserved(kernel.snapshot(), 1.0e-2);
        }
    }

    @Test
    void sameSeedProducesIdenticalGenomes() {
        // arrange
        Kernel a = evolvingKernel();
        Kernel b = evolvingKernel();

        // act
        for (int t = 0; t < TICKS; t++) {
            a.tick();
            b.tick();
        }

        // assert
        KernelSnapshot sa = a.snapshot();
        KernelSnapshot sb = b.snapshot();
        assertArrayEquals(sa.geneCount, sb.geneCount, "geneCount diverged for the same seed");
        assertArrayEquals(sa.genes, sb.genes, "genes diverged for the same seed");
        assertEquals(sa.energySink, sb.energySink, 0.0, "energySink diverged for the same seed");
        assertEquals(sa.birthsTotal, sb.birthsTotal, "birthsTotal diverged for the same seed");
    }
}
