package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.runtime.Kernel;

class ChemicalPhaseTest {

    private static final int WORLD_WIDTH = 8;
    private static final int CELLS = WORLD_WIDTH * WORLD_WIDTH;
    private static final double DIFFUSION = 0.1;
    private static final double DECAY = 0.02;

    private static Kernel emptyKernel(final List<ChemicalSpec> chemicals) {
        final EngineConfig config = EngineConfig.defaults(0L, WORLD_WIDTH, 4).withChemicals(chemicals);
        return new Kernel(new int[0][], new int[0], config, 1);
    }

    @Test
    void decayToSinkMatchesV5() {
        // arrange
        final ChemicalSpec resource =
                new ChemicalSpec("resource", InflowConfig.uniform(0.0), 100.0, 50.0, DIFFUSION, DECAY);
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
        assertArrayEquals(expected, kernel.snapshot().cellChem, 0.0);
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
