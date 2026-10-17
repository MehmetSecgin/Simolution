package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 9: motility as a claim (contract v3 §9, ADR 0020; mass-scaled cost contract v5 §3;
 * engine v6 design §5.6). Each living unit reads its four MOVE effectors (from outputsPrev,
 * the swapped current tick) and proposes at most one Moore step: {@code dx} from
 * {@code MOVE_E − MOVE_W}, {@code dy} from {@code MOVE_N − MOVE_S} (N = −y, up), each
 * thresholded by {@code MOVE_DEADZONE} so a quiet circuit stays put. Diagonals are emergent
 * (both axes firing).
 * <p>
 * {@link #run} (parallel, per unit) reads only phase-start state and writes only
 * {@code moveIntent[own slot]}: the target cell, or {@code -1} when the unit is dead, its
 * drive is inside the deadzone, the target is occupied at phase start, or
 * {@code energy ≤ MOVE_COST_BASE + MOVE_COST_PER_MASS · mass} (a step never kills, so no cell
 * is freed here). {@link #finish} (serial) resolves the claims in ascending slot order: the
 * first intent on a target cell wins ({@code cellWinner}), winners move and pay the cost to
 * the sink (and damage, motility is dissipation), losers stay and pay nothing. A cell vacated
 * this tick was occupied at phase start, so it is never a valid target and there are no
 * same-tick chains. The slot never changes, so the connection store is untouched and nothing
 * is allocated.
 * <p>
 * {@code finish} resets exactly the {@code cellWinner} entries it set, so the resolve costs
 * O(slots) for the scan and no O(cells) clear; {@code cellWinner} is {@code -1} between phases.
 * Charges are summed in ascending slot order into {@code sinkPartial[0]} and merged, so the
 * total does not depend on the thread count.
 * <p>
 * Shape is {@link Shape#CLAIM}.
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
        return Shape.CLAIM;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final int[] moveIntent = state.moveIntent;
        final float[] outputsPrev = state.outputsPrev;

        final int w = state.worldWidth;
        final double theta = moveDeadzone;
        for (int unit = from; unit < to; unit++) {
            moveIntent[unit] = -1;
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
            if (energy[unit] <= moveCostBase + moveCostPerMass * mass[unit]) {
                continue;
            }
            moveIntent[unit] = target;
        }
    }

    @Override
    public void finish() {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final int[] moveIntent = state.moveIntent;
        final int[] cellWinner = state.cellWinner;
        final int slots = state.unitCount;

        for (int unit = 0; unit < slots; unit++) {
            final int target = moveIntent[unit];
            if (target >= 0 && cellWinner[target] < 0) {
                cellWinner[target] = unit;
            }
        }
        double sink = 0.0;
        for (int unit = 0; unit < slots; unit++) {
            final int target = moveIntent[unit];
            if (target < 0 || cellWinner[target] != unit) {
                continue;
            }
            final double moveCost = moveCostBase + moveCostPerMass * mass[unit];
            cellOccupant[position[unit]] = -1;
            position[unit] = target;
            cellOccupant[target] = unit;
            energy[unit] -= moveCost;
            damage[unit] += moveCost;
            sink += moveCost;
            cellWinner[target] = -1;
        }
        state.sinkPartial[0] += sink;
        state.mergePartials();
    }
}
