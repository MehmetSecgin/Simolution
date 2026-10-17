package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 6: the entropy law (contract v0 §3, §4, §6). Each unit that was alive this tick pays
 * its metabolic bill, clamped to available energy so a unit can never overdraw: the sink
 * receives exactly what existed, keeping the audit invariant exact.
 * <p>
 * A unit alive at tick start computes the full tick and pays for it; crossing to zero here
 * makes phases 2 and 3 skip it from the next tick on, so death takes effect next tick.
 * Because intake (phase 5) ran first, a unit's net energy change this tick is
 * {@code intake − charge}: a strong harvester reaches steady state, a poor one still decays
 * to death.
 * <p>
 * The charge is modelled on real maintenance energy (Pirt) rather than a per-gene tax
 * (contract v1 §6/§6a, ADR 0013; mass-based maintenance contract v5 §3):
 * <ul>
 *   <li><b>basal</b> ({@code BASAL_COST}): the fixed, irreducible cost of staying organized.
 *       Charged to every living unit regardless of size or activity; this floor is what makes
 *       a dormant unit actually die instead of decaying asymptotically.</li>
 *   <li><b>activity</b> (∝ propagations): you pay for signal you move, not for wiring you
 *       merely carry, so silent or junk structure is nearly free, as in biology.</li>
 *   <li><b>maintenance</b> (∝ mass): upkeep proportional to body size, so a large body needs
 *       a proportionally larger harvest to stay alive.</li>
 *   <li><b>aging</b> ({@code damage · AGING_COST}): senescence, upkeep that rises with
 *       accumulated wear (contract v2 §7, ADR 0017). {@code damage} is the unit's lifetime
 *       dissipated energy (incremented below by this very charge, so older cells pay more and
 *       the cost accelerates). Since every cell pays at least {@code BASAL_COST}, damage only
 *       ever grows while the harvest ceiling is bounded, so no cell is immortal; offspring
 *       reset {@code damage} to 0 (germline renewal), so the lineage outruns entropy only by
 *       reproducing.</li>
 * </ul>
 * No term scales with connection count: cost lives on what a unit does and holds, not on what
 * it has.
 * <p>
 * Shape is {@link Shape#PER_UNIT}: a unit writes only its own energy, damage and (on death)
 * its own slot's death fields and its own cell's occupancy. Charges and death mass go to the
 * chunk's {@code sinkPartial}, merged into {@code energySink} in {@link #finish()}.
 */
public class CostPhase implements Phase {

    private final WorldState state;
    private final double basalCost;
    private final double costPerPropagation;
    private final double maintPerMass;
    private final double agingCost;

    public CostPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.basalCost = config.metabolism().basalCost();
        this.costPerPropagation = config.metabolism().costPerPropagation();
        this.maintPerMass = config.metabolism().maintenancePerMass();
        this.agingCost = config.metabolism().agingCost();
    }

    @Override
    public String name() {
        return "cost";
    }

    @Override
    public Shape shape() {
        return Shape.PER_UNIT;
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
        final int[] activeThisTick = state.activeThisTick;
        double sink = 0.0;

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double activity = activeThisTick[unit] * costPerPropagation;
            final double maintenance = mass[unit] * maintPerMass;
            final double aging = damage[unit] * agingCost;
            final double charge = Math.min(
                    basalCost + activity + maintenance + aging, energy[unit]);
            energy[unit] -= charge;
            sink += charge;
            damage[unit] += charge;
            if (energy[unit] <= 0.0) {
                sink += state.die(unit);
            }
        }
        state.sinkPartial[chunk] += sink;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
