package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

class ChemicalPhaseTest {

    private static final int WORLD_WIDTH = 8;
    private static final int CELLS = WORLD_WIDTH * WORLD_WIDTH;
    private static final double DIFFUSION = 0.1;
    private static final double DECAY = 0.02;

    private static Kernel emptyKernel(final List<ChemicalSpec> chemicals) {
        final EngineConfig config = EngineConfig.v5Defaults(0L, WORLD_WIDTH, 4).withChemicals(chemicals);
        return new Kernel(new int[0][], new int[0], config, 1);
    }

    @Test
    void decayIntoAnotherChemicalConserves() {
        // arrange
        final ChemicalSpec source = new ChemicalSpec("source", InflowConfig.uniform(1.0), 100.0, 50.0, DIFFUSION, DECAY, 1);
        final ChemicalSpec product = new ChemicalSpec("product", InflowConfig.uniform(0.0), 100.0, 0.0, DIFFUSION, 0.0, -1);
        final Kernel kernel = emptyKernel(List.of(source, product));

        // act
        for (int i = 0; i < 50; i++) {
            kernel.tick();
        }

        // assert
        final KernelSnapshot snapshot = kernel.snapshot();
        double productTotal = 0.0;
        for (int cell = 0; cell < CELLS; cell++) {
            productTotal += snapshot.resourceField[CELLS + cell];
        }
        assertTrue(productTotal > 0.0, "chemical 1 must receive the decayed share of chemical 0");
        assertEquals(snapshot.initialResourceTotal + snapshot.cumulativeInflow,
                snapshot.reservoir() + snapshot.energySink, 1.0e-9);
    }

    @Test
    void decayToSinkMatchesV5() {
        // arrange
        final ChemicalSpec resource = new ChemicalSpec("resource", InflowConfig.uniform(0.0), 100.0, 50.0, DIFFUSION, DECAY, -1);
        final Kernel kernel = emptyKernel(List.of(resource));
        final double[] expected = new double[CELLS];
        java.util.Arrays.fill(expected, 50.0);

        // act
        for (int i = 0; i < 30; i++) {
            kernel.tick();
            referenceDiffusion(expected);
            for (int cell = 0; cell < CELLS; cell++) {
                expected[cell] -= expected[cell] * DECAY;
            }
        }

        // assert
        assertArrayEquals(expected, kernel.snapshot().resourceField, 0.0);
    }

    @Test
    void kernelDeclaresThePhasesInOrderWithTheirShapes() {
        // arrange
        final Kernel kernel = emptyKernel(EngineConfig.v5Defaults(0L, WORLD_WIDTH, 4).chemicals());

        // act
        final List<Phase> phases = kernel.phases();

        // assert
        assertEquals(List.of("clear", "propagate", "evaluate", "swap", "intake", "inflow", "cost", "growth",
                "reproduction", "movement", "diffusion", "decay"),
                phases.stream().map(Phase::name).toList());
        assertEquals(List.of(Shape.PER_UNIT, Shape.PER_UNIT, Shape.PER_UNIT, Shape.SERIAL, Shape.PER_UNIT,
                Shape.PER_CELL, Shape.PER_UNIT, Shape.PER_UNIT, Shape.CLAIM, Shape.CLAIM, Shape.PER_CELL,
                Shape.PER_CELL),
                phases.stream().map(Phase::shape).toList());
    }

    @Test
    void chemicalDecayTargetMustBeASinkOrAnotherChemical() {
        // arrange
        final ChemicalSpec selfTarget = new ChemicalSpec("a", InflowConfig.uniform(0.0), 100.0, 0.0, DIFFUSION, DECAY, 0);
        final ChemicalSpec outOfRange = new ChemicalSpec("a", InflowConfig.uniform(0.0), 100.0, 0.0, DIFFUSION, DECAY, 1);

        // act + assert
        assertThrows(IllegalArgumentException.class, () -> emptyKernel(List.of(selfTarget)));
        assertThrows(IllegalArgumentException.class, () -> emptyKernel(List.of(outOfRange)));
    }

    private static void referenceDiffusion(final double[] field) {
        final double[] next = new double[CELLS];
        for (int y = 0; y < WORLD_WIDTH; y++) {
            for (int x = 0; x < WORLD_WIDTH; x++) {
                final double here = field[y * WORLD_WIDTH + x];
                final double left = field[y * WORLD_WIDTH + (x + WORLD_WIDTH - 1) % WORLD_WIDTH];
                final double right = field[y * WORLD_WIDTH + (x + 1) % WORLD_WIDTH];
                final double up = field[((y + WORLD_WIDTH - 1) % WORLD_WIDTH) * WORLD_WIDTH + x];
                final double down = field[((y + 1) % WORLD_WIDTH) * WORLD_WIDTH + x];
                next[y * WORLD_WIDTH + x] = here + DIFFUSION * (left + right + up + down - 4.0 * here);
            }
        }
        System.arraycopy(next, 0, field, 0, CELLS);
    }
}
