package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.GenomeFactory;

/**
 * Reproduction as a claim phase (engine v6 design §5.6): contested child cells go to the
 * lowest parent slot, an unaffordable build burns nothing, the child genome is keyed by the
 * parent (not the slot it lands in), and the engine publishes an exact per-tick birth list.
 */
class ReproductionClaimTest {

    private static final EngineConfig DEFAULTS = EngineConfig.v5Defaults(0L, 1, 1);

    private static int reproduce(final int weightRaw) {
        return GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                .toAction(NodeLayout.Action.REPRODUCE)
                .weightRaw((short) weightRaw)
                .build();
    }

    private static EngineConfig config(final int world, final int maxGenes, final double buildCost,
            final double buildCostPerGene, final double mutationRate, final double indelRate) {
        final EngineConfig d = EngineConfig.v5Defaults(0L, world, maxGenes);
        return new EngineConfig(d.seed(), d.worldWidth(), d.maxGenes(), d.chemicals(), d.initialEnergy(),
                d.basalCost(), d.costPerPropagation(), d.harvestCapacityPerConnection(),
                d.harvestHalfSaturation(), d.harvestMassExponent(), d.moveCostBase(), d.moveCostPerMass(),
                d.moveDeadzone(), d.selfEnergyScale(), d.selfMassScale(), d.initialMass(), d.maintPerMass(),
                d.growMax(), d.growHalfSaturation(), d.growYield(), buildCost, buildCostPerGene,
                mutationRate, mutationRate, indelRate, indelRate, d.agingCost());
    }

    private static void tickUntilFirstBirth(final Kernel kernel) {
        for (int t = 0; t < 10 && kernel.state().birthsTotal == 0; t++) {
            kernel.tick();
        }
        assertTrue(kernel.state().birthsTotal > 0, "a CONST->REPRODUCE parent must divide within 10 ticks");
    }

    @Test
    void contestedChildCellGoesToLowestParent() {
        // arrange
        final int[][] genomes = {{reproduce(8192)}, {reproduce(8192)}, {}, {}, {}, {}, {}, {}};
        final int[] cells = {6, 8, 0, 1, 2, 3, 4, 5};
        final Kernel kernel = new Kernel(genomes, cells, EngineConfig.v5Defaults(0L, 5, 8), 1);

        // act
        tickUntilFirstBirth(kernel);
        final WorldState s = kernel.state();

        // assert
        assertEquals(1, s.birthsTotal, "the losing parent does not fall back to another cell");
        final int child = s.cellOccupant[7];
        assertTrue(child >= 0, "the contested cell is filled");
        assertEquals(0L, s.lineageId[child], "the lower parent slot wins the contested cell");
        assertEquals(-1, s.cellOccupant[9], "the loser's next free neighbour stays empty");
        assertEquals(1, s.birthCount, "one birth published this tick");
        assertEquals(0, s.birthParent[0], "the published parent is the winner");
        assertEquals(child, s.birthChild[0], "the published child is the installed slot");
        assertEquals(7, s.birthChildCell[0], "the published cell is the contested cell");
    }

    @Test
    void unaffordableBuildBurnsNoSlot() {
        // arrange
        final int[][] genomes = {{reproduce(8192)}};
        final int[] cells = {4};
        final Kernel attempting = new Kernel(genomes, cells, config(3, 8, 500.0, 1000.0, 0.0, 0.0), 1);
        final Kernel reference = new Kernel(genomes, cells, config(3, 8, 1.0e9, 1000.0, 0.0, 0.0), 1);

        for (int t = 0; t < 10; t++) {
            // act
            attempting.tick();
            reference.tick();

            // assert
            final WorldState a = attempting.state();
            final WorldState r = reference.state();
            assertEquals(0, a.birthsTotal, "an unaffordable parent never divides");
            assertArrayEquals(r.energy, a.energy, 0.0, "an unaffordable parent pays nothing, tick " + a.tick);
            assertArrayEquals(r.genes, a.genes, "no slot receives a child genome, tick " + a.tick);
            assertArrayEquals(r.geneCount, a.geneCount, "no slot's gene count changes, tick " + a.tick);
        }
    }

    @Test
    void childGenomeIndependentOfSlot() {
        // arrange
        final int world = 31;
        final int[] parentGenome = new int[8];
        for (int g = 0; g < parentGenome.length; g++) {
            parentGenome[g] = reproduce(8192 + g);
        }
        final EngineConfig high = config(world, 16, 10.0, 0.25, 0.05, 0.1);
        final Kernel few = new Kernel(withFillers(parentGenome, 4), fillerCells(4, world), high, 1);
        final Kernel many = new Kernel(withFillers(parentGenome, 899), fillerCells(899, world), high, 1);

        // act
        tickUntilFirstBirth(few);
        tickUntilFirstBirth(many);

        // assert
        final WorldState a = few.state();
        final WorldState b = many.state();
        assertEquals(a.tick, b.tick, "both parents divide on the same tick");
        assertEquals(5, a.cellOccupant[1], "the child takes free slot 5");
        assertEquals(900, b.cellOccupant[1], "the child takes free slot 900");
        final int[] childA = Arrays.copyOfRange(a.genes, 5 * a.maxGenes, 5 * a.maxGenes + a.geneCount[5]);
        final int[] childB = Arrays.copyOfRange(b.genes, 900 * b.maxGenes, 900 * b.maxGenes + b.geneCount[900]);
        assertArrayEquals(childA, childB, "the child genome depends on the parent, not on the slot");
        assertFalse(Arrays.equals(parentGenome, childA), "the high mutation rates changed the child");
    }

    private static int[][] withFillers(final int[] parentGenome, final int fillers) {
        final int[][] genomes = new int[1 + fillers][];
        genomes[0] = parentGenome;
        for (int i = 1; i < genomes.length; i++) {
            genomes[i] = new int[0];
        }
        return genomes;
    }

    private static int[] fillerCells(final int fillers, final int world) {
        final int[] parentMoore = {1, world - 1, world, world + 1, 2 * world - 1,
                world * world - world, world * world - world + 1, world * world - 1};
        final int[] cells = new int[1 + fillers];
        cells[0] = 0;
        int next = 1;
        for (int cell = 1; next < cells.length; cell++) {
            final int c = cell;
            if (Arrays.stream(parentMoore).noneMatch(m -> m == c)) {
                cells[next++] = cell;
            }
        }
        return cells;
    }

    @Test
    void birthListMatchesPopulationDelta() {
        // arrange
        final int world = 40;
        final int founders = 400;
        final Kernel kernel = new Kernel(GenomeFactory.random(11L, founders, 16),
                Kernel.scatterFounders(0L, founders, world),
                EngineConfig.v5Defaults(0L, world, 32).withChemical0Inflow(
                        InflowConfig.field(8.0, InflowConfig.Combine.MAX)), 1);

        // act + assert
        long listed = 0;
        for (int t = 0; t < 500; t++) {
            kernel.tick();
            final WorldState s = kernel.state();
            for (int i = 0; i < s.birthCount; i++) {
                final int parent = s.birthParent[i];
                final int child = s.birthChild[i];
                assertEquals(s.lineageId[parent], s.lineageId[child], "child inherits the parent's lineage");
                assertEquals(s.generation[parent] + 1, s.generation[child], "child is one generation deeper");
                assertEquals(s.birthChildCell[i], s.position[child], "child sits in the published cell");
                assertTrue(s.energy[child] > 0.0, "a listed child is alive");
            }
            listed += s.birthCount;
        }
        assertTrue(listed > 0, "the run reproduces");
        assertEquals(kernel.state().birthsTotal, listed, "the birth list accounts for every birth");
    }

    @Test
    void skipSamplingRate() {
        // arrange: 10^6 bits at rate 1e-3, gaps drawn from one keyed mutation stream
        final double rate = 1.0e-3;
        final double logKeep = StrictMath.log(1.0 - rate);
        final long bits = 1_000_000L;
        final long prefix = Noise.mutationPrefix(0L, 7, 3);

        // act
        int op = 0;
        long flips = 0;
        long position = Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeep);
        while (position < bits) {
            flips++;
            position += 1 + Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeep);
        }

        // assert
        final double mean = bits * rate;
        final double sd = Math.sqrt(bits * rate * (1.0 - rate));
        assertTrue(Math.abs(flips - mean) <= 4.0 * sd,
                "flip count " + flips + " is within 4 sd of " + mean);
        assertEquals(Long.MAX_VALUE, Noise.geometricGap(0.5, StrictMath.log(1.0 - 0.0)), "rate 0 never flips");
        assertEquals(0L, Noise.geometricGap(0.5, StrictMath.log(1.0 - 1.0)), "rate 1 flips every bit");
        assertEquals(0L, Noise.geometricGap(0.0, StrictMath.log(1.0 - 1.0)), "rate 1 with u = 1 is not NaN");
    }
}
