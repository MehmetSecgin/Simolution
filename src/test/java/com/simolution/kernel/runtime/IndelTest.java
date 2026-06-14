package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.sim.GenomeFactory;

/**
 * Conformance for kernel v4 variable-length genomes (contract-v4 / ADR 0026):
 * indels move {@code geneCount} within the soft cap, an empty genome is inert,
 * the per-gene build cost keeps energy conserved, and the whole thing stays
 * deterministic (same seed → identical genomes).
 */
class IndelTest {

    private static final long SEED = 7L;
    private static final int UNITS = 200;
    private static final int WORLD = 50;
    private static final int FOUNDER_GENES = 8;
    private static final int MAX_GENES = 16;
    private static final int TICKS = 3000;

    private static Kernel evolvingKernel() {
        return new Kernel(GenomeFactory.random(SEED, UNITS, FOUNDER_GENES), WORLD, MAX_GENES,
                Kernel.scatterFounders(UNITS, WORLD));
    }

    // contract-v4 §1: MAX_GENES is a hard soft-cap ceiling and 0 the floor —
    // no operator can push a genome outside [0, maxGenes], ever.
    @Test
    void geneCountStaysWithinCapAndFloor() {
        Kernel kernel = evolvingKernel();
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

    // contract-v4 §2: duplication grows length, deletion shrinks it. Over a run
    // with many births, both operators must visibly fire — some living lineage
    // ends up longer than the founder and some shorter.
    @Test
    void duplicationGrowsAndDeletionShrinksLength() {
        Kernel kernel = evolvingKernel();
        boolean sawGrowth = false;
        boolean sawShrink = false;
        for (int t = 0; t < TICKS; t++) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            for (int slot = 0; slot < s.geneCount.length; slot++) {
                if (s.energy[slot] <= 0.0) {
                    continue;
                }
                if (s.geneCount[slot] > FOUNDER_GENES) {
                    sawGrowth = true;
                }
                if (s.geneCount[slot] < FOUNDER_GENES) {
                    sawShrink = true;
                }
            }
        }
        assertTrue(sawGrowth, "tandem duplication never grew a living genome past " + FOUNDER_GENES);
        assertTrue(sawShrink, "deletion never shrank a living genome below " + FOUNDER_GENES);
    }

    // contract-v4 §1: the floor is 0 genes — an empty genome is legal, inert
    // (no connections, no harvest, no motility), and dies of basal cost.
    @Test
    void emptyGenomeIsInertAndMortal() {
        Kernel kernel = new Kernel(new int[][] {{}}, 1, 8, new int[] {0});
        double previous = KernelConfig.INITIAL_ENERGY;
        boolean died = false;
        for (int t = 0; t < 200_000 && !died; t++) {
            kernel.tick();
            double now = kernel.snapshot().energy[0];
            assertTrue(now <= previous + 1.0e-12,
                    "an inert empty genome can never gain energy (no harvest) at tick " + (t + 1));
            previous = now;
            if (now <= 0.0) {
                died = true;
            }
        }
        assertTrue(died, "an empty genome must still die of basal cost");
    }

    // contract-v4 §5 / §3: the per-gene build cost flows to the sink like the
    // fixed BUILD_COST, so the closed-system audit stays exact even as genome
    // length (and thus build cost) varies birth to birth.
    @Test
    void energyConservedWithPerGeneBuildCost() {
        Kernel kernel = evolvingKernel();
        for (int t = 0; t < TICKS; t++) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            double held = 0.0;
            for (double e : s.energy) {
                held += e;
            }
            held += s.reservoir + s.energySink;
            double credited = s.creditedInitialEnergy + s.initialResourceTotal + s.cumulativeInflow;
            assertEquals(credited, held, 1.0e-2,
                    "initial + inflow must equal units + field + sink at tick " + s.tick);
        }
    }

    // determinism (contract-v4 §4): the operation-counter keying is a pure
    // function of (seed, childSlot, birthTick, opIndex), so two independent runs
    // of the same seed evolve byte-identical genomes and energy.
    @Test
    void sameSeedProducesIdenticalGenomes() {
        Kernel a = evolvingKernel();
        Kernel b = evolvingKernel();
        for (int t = 0; t < TICKS; t++) {
            a.tick();
            b.tick();
        }
        KernelSnapshot sa = a.snapshot();
        KernelSnapshot sb = b.snapshot();
        assertArrayEquals(sa.geneCount, sb.geneCount, "geneCount diverged for the same seed");
        assertArrayEquals(sa.genes, sb.genes, "genes diverged for the same seed");
        assertEquals(sa.energySink, sb.energySink, 0.0, "energySink diverged for the same seed");
        assertEquals(sa.birthsTotal, sb.birthsTotal, "birthsTotal diverged for the same seed");
    }
}
