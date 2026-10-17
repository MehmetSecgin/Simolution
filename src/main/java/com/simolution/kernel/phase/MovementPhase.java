package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 9: motility (contract v3 §9, ADR 0020; mass-scaled cost contract v5 §3). Each living
 * unit reads its four MOVE effectors (from outputsPrev, the swapped current tick) and takes
 * at most one Moore step: {@code dx} from {@code MOVE_E − MOVE_W}, {@code dy} from
 * {@code MOVE_N − MOVE_S} (N = −y, up), each thresholded by {@code MOVE_DEADZONE} so a quiet
 * circuit stays put. Diagonals are emergent (both axes firing). A step is taken only into a
 * free cell ({@code cellOccupant < 0}); a blocked or zero step does nothing and costs
 * nothing. An actual step charges {@code MOVE_COST_BASE + MOVE_COST_PER_MASS · mass} to the
 * sink (and damage, motility is dissipation): a heavier body is sluggish, paying more per
 * step. The afford check ({@code energy > moveCost}) is applied only once a step is actually
 * about to happen and means a step never kills, so no cell is freed here.
 * <p>
 * Units are scanned in ascending slot order with occupancy updated immediately, so two units
 * targeting one free cell resolve by lowest slot, and a cell vacated this tick can be entered
 * this tick, all deterministic. The slot never changes, so the connection store is untouched
 * and nothing is allocated.
 * <p>
 * Shape is {@link Shape#SERIAL}: the in-order occupancy updates are the conflict resolution.
 */
public final class MovementPhase implements Phase {

    private final WorldState state;
    private final double moveCostBase;
    private final double moveCostPerMass;
    private final double moveDeadzone;

    public MovementPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.moveCostBase = config.moveCostBase();
        this.moveCostPerMass = config.moveCostPerMass();
        this.moveDeadzone = config.moveDeadzone();
    }

    @Override
    public String name() {
        return "movement";
    }

    @Override
    public Shape shape() {
        return Shape.SERIAL;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final float[] outputsPrev = state.outputsPrev;

        final int w = state.worldWidth;
        final double theta = moveDeadzone;
        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final int actionBase = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET;
            final double h = outputsPrev[actionBase + NodeLayout.Action.MOVE_E]
                    - outputsPrev[actionBase + NodeLayout.Action.MOVE_W];
            final double v = outputsPrev[actionBase + NodeLayout.Action.MOVE_N]
                    - outputsPrev[actionBase + NodeLayout.Action.MOVE_S];
            int dx = 0;
            if (h > theta) {
                dx = 1;
            } else if (h < -theta) {
                dx = -1;
            }
            int dy = 0;
            if (v > theta) {
                dy = -1;
            } else if (v < -theta) {
                dy = 1;
            }
            if (dx == 0 && dy == 0) {
                continue;
            }
            final int cell = position[unit];
            final int nx = (((cell % w) + dx) % w + w) % w;
            final int ny = (((cell / w) + dy) % w + w) % w;
            final int target = ny * w + nx;
            if (cellOccupant[target] >= 0) {
                continue;
            }
            final double moveCost = moveCostBase
                    + moveCostPerMass * mass[unit];
            if (energy[unit] <= moveCost) {
                continue;
            }
            cellOccupant[cell] = -1;
            position[unit] = target;
            cellOccupant[target] = unit;
            energy[unit] -= moveCost;
            state.energySink += moveCost;
            damage[unit] += moveCost;
        }
    }
}
