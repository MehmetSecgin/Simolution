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
 * {@link #run} (parallel, per unit) reads only phase-start state and writes {@code moveIntent[own
 * slot]}: the target cell, or {@code -1} when the unit is dead, its drive is inside the deadzone,
 * the target is occupied at phase start, or
 * {@code energy ≤ MOVE_COST_BASE + MOVE_COST_PER_MASS · mass} (a step never kills, so no cell
 * is freed here). A unit with a target also claims it in {@code cellWinner} through
 * {@link CellClaim}, so after the pass each claimed cell holds its lowest claiming slot: the
 * first intent in ascending slot order, as the contract's serial resolve picks it.
 * <p>
 * The one resolve pass (parallel, per unit) moves the winners: a unit whose
 * {@code cellWinner[target]} is itself moves, pays the cost to its chunk's sink partial (and
 * damage, motility is dissipation) and resets that entry to {@code -1}; losers stay and pay
 * nothing. The writes are disjoint: each winner writes its own slot, the cell it leaves (its own,
 * occupied at phase start) and its target (free at phase start, claimed by it alone). A loser
 * that reads its target's entry after the winner reset it reads {@code -1}, which is not its own
 * slot either. A cell vacated this tick was occupied at phase start, so it is never a valid
 * target and there are no same-tick chains. The slot never changes, so the connection store is
 * untouched and nothing is allocated.
 * <p>
 * Every claimed entry is reset by its winner, so {@code cellWinner} is {@code -1} between
 * phases with no O(cells) clear. {@link #finish} merges the sink partials in ascending chunk
 * index, so the total does not depend on the thread count.
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
        this.moveCostBase = config.motility().fixedCost();
        this.moveCostPerMass = config.motility().costPerMass();
        this.moveDeadzone = config.motility().deadzone();
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
        final int[] cellWinner = state.cellWinner;
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
            CellClaim.claim(cellWinner, target, unit);
        }
    }

    @Override
    public int resolvePasses() {
        return 1;
    }

    @Override
    public void resolve(final int pass, final int from, final int to, final int chunk) {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final int[] moveIntent = state.moveIntent;
        final int[] cellWinner = state.cellWinner;

        double sink = 0.0;
        for (int unit = from; unit < to; unit++) {
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
        state.sinkPartial[chunk] += sink;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
