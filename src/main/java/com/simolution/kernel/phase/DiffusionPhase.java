package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 10: diffusion of every chemical (contract v3 §4/§6). A mass-conserving explicit
 * discrete Laplacian over the 4 von-Neumann neighbours on the torus:
 * {@code next = here + rate · (Σ neighbours − 4·here)}, with the rate of each chemical.
 * Double-buffered (the whole previous field is read, the next one written, and the buffers
 * swap in {@link #finish()}) so the update is order-independent and deterministic. It
 * conserves mass exactly on the torus, as every flux leaving a cell enters its neighbour, and
 * is never clamped, so it creates and destroys nothing; only inflow (phase 5) is capped.
 * One O(cells × chemicals) pass per tick, no allocation.
 */
public final class DiffusionPhase implements Phase {

    private final WorldState state;
    private final double[] rate;

    public DiffusionPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.rate = new double[state.chemCount];
        for (int k = 0; k < rate.length; k++) {
            rate[k] = config.chemicals().get(k).diffusionRate();
        }
    }

    @Override
    public String name() {
        return "diffusion";
    }

    @Override
    public Shape shape() {
        return Shape.PER_CELL;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] cellChem = state.cellChem;
        final double[] cellChemNext = state.cellChemNext;

        final int w = state.worldWidth;
        final int cells = state.unitCount;
        for (int k = 0; k < rate.length; k++) {
            final int offset = k * cells;
            final double d = rate[k];
            for (int cell = from; cell < to; cell++) {
                final int y = cell / w;
                final int x = cell - y * w;
                final int row = y * w;
                final int up = (((y - 1) % w) + w) % w * w;
                final int down = ((y + 1) % w) * w;
                final int left = (((x - 1) % w) + w) % w;
                final int right = (x + 1) % w;
                final double here = cellChem[offset + cell];
                final double neighbours = cellChem[offset + row + left]
                        + cellChem[offset + row + right]
                        + cellChem[offset + up + x]
                        + cellChem[offset + down + x];
                cellChemNext[offset + cell] = here + d * (neighbours - 4.0 * here);
            }
        }
    }

    @Override
    public void finish() {
        state.swapChem();
    }
}
