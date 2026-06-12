package com.simolution.kernel.runtime;

import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class Kernel {

    private double[] outputsPrev;
    private double[] outputsNext;
    private final double[] accumulators;
    private final double[] delayMemory;

    private final CompiledConnection[] sumConnections;
    private final CompiledConnection[] mulConnections;
    private final int[] sumStart;
    private final int[] sumEnd;
    private final int[] mulStart;
    private final int[] mulEnd;

    private final double[] mulTop1;
    private final double[] mulTop2;
    private final int[] mulInDegree;

    private final double[] energy;
    private final int[] connectionCount;
    private final int[] activeThisTick;
    private double energySink;

    private double reservoir;
    private double cumulativeInflow;

    private final int unitCount;

    private int tick = 0;

    /**
     * Structure is split once at construction (a structure-only precompute,
     * allowed by the cache spec):
     * <ul>
     *   <li>MUL needs its two strongest individual inputs, which the summing
     *       accumulator destroys, so MUL-destined connections run a separate
     *       top-2 loop (ADR 0005).</li>
     *   <li>connections arrive grouped by unit (GenomeCompiler.compileAll), so
     *       per-unit [start, end) ranges let a dead unit's whole range be
     *       skipped with one branch instead of one branch per connection
     *       (ADR 0006).</li>
     * </ul>
     */
    public Kernel(final int unitCount, final CompiledConnection[] connections) {

        final int totalNodes = unitCount * NodeLayout.TOTAL;

        this.outputsPrev = new double[totalNodes];
        this.outputsNext = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.delayMemory = new double[totalNodes];

        this.unitCount = unitCount;
        this.mulTop1 = new double[unitCount];
        this.mulTop2 = new double[unitCount];
        this.mulInDegree = new int[unitCount];

        this.energy = new double[unitCount];
        Arrays.fill(energy, KernelConfig.INITIAL_ENERGY);
        this.connectionCount = new int[unitCount];
        this.activeThisTick = new int[unitCount];

        this.reservoir = KernelConfig.RESOURCE_INITIAL;
        this.cumulativeInflow = 0.0;

        int mulCount = 0;
        for (final CompiledConnection connection : connections) {
            if (isMulDestination(connection)) {
                mulCount++;
            }
        }

        this.sumConnections = new CompiledConnection[connections.length - mulCount];
        this.mulConnections = new CompiledConnection[mulCount];
        this.sumStart = new int[unitCount];
        this.sumEnd = new int[unitCount];
        this.mulStart = new int[unitCount];
        this.mulEnd = new int[unitCount];

        int sumCursor = 0;
        int mulCursor = 0;
        for (final CompiledConnection connection : connections) {
            final int unit = connection.sourceAbsoluteIndex / NodeLayout.TOTAL;
            connectionCount[unit]++;
            if (isMulDestination(connection)) {
                mulConnections[mulCursor++] = connection;
                mulInDegree[unit] = Math.min(2, mulInDegree[unit] + 1);
            } else {
                sumConnections[sumCursor++] = connection;
            }
        }

        computeRanges(sumConnections, sumStart, sumEnd);
        computeRanges(mulConnections, mulStart, mulEnd);
    }

    private void computeRanges(final CompiledConnection[] grouped, final int[] start, final int[] end) {
        int cursor = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            start[unit] = cursor;
            while (cursor < grouped.length
                   && grouped[cursor].sourceAbsoluteIndex / NodeLayout.TOTAL == unit) {
                cursor++;
            }
            end[unit] = cursor;
        }
    }

    private static boolean isMulDestination(final CompiledConnection connection) {
        final int dstLocal = connection.destinationAbsoluteIndex % NodeLayout.TOTAL;
        return dstLocal == NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
    }

    public void tick() {
        // Phase 1
        clearAccumulators();
        // Phase 2
        propagateConnections();
        // Phase 3
        evaluateNodes();
        // Phase 4
        swapBuffers();
        // Phase 5
        settleIntake();
        // Phase 6
        settleCost();

        tick++;
    }

    private void clearAccumulators() {
        Arrays.fill(accumulators, 0.0);
        Arrays.fill(mulTop1, 0.0);
        Arrays.fill(mulTop2, 0.0);
    }

    /**
     * Per-unit propagation. Dead units (energy &lt;= 0) are skipped entirely —
     * the one alive-check per unit replaces what would be a branch per
     * connection, and a dying population literally costs less to simulate.
     * Counts non-zero propagations per unit; that count is the activity the
     * energy settle phase charges for (contract v0 §6, fork 1a).
     */
    private void propagateConnections() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                activeThisTick[unit] = 0;
                continue;
            }

            int active = 0;

            for (int i = sumStart[unit]; i < sumEnd[unit]; i++) {
                final CompiledConnection c = sumConnections[i];
                final double signal = outputsPrev[c.sourceAbsoluteIndex] * c.weight;
                accumulators[c.destinationAbsoluteIndex] += signal;
                if (signal != 0.0) {
                    active++;
                }
            }

            for (int i = mulStart[unit]; i < mulEnd[unit]; i++) {
                final CompiledConnection c = mulConnections[i];
                final double signal = outputsPrev[c.sourceAbsoluteIndex] * c.weight;
                if (Math.abs(signal) > Math.abs(mulTop1[unit])) {
                    mulTop2[unit] = mulTop1[unit];
                    mulTop1[unit] = signal;
                } else if (Math.abs(signal) > Math.abs(mulTop2[unit])) {
                    mulTop2[unit] = signal;
                }
                if (signal != 0.0) {
                    active++;
                }
            }

            activeThisTick[unit] = active;
        }
    }

    private void evaluateNodes() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                freezeDeadUnit(unit);
            } else {
                evaluateUnit(unit);
            }
        }
    }

    /**
     * A dead unit computes nothing (contract v0 §9), but the double buffer
     * would otherwise surface its state from two ticks ago after the swap.
     * Carrying the previous outputs forward freezes the corpse at its last
     * living configuration — inert, unchanging.
     */
    private void freezeDeadUnit(final int unit) {
        final int base = unit * NodeLayout.TOTAL;
        System.arraycopy(outputsPrev, base, outputsNext, base, NodeLayout.TOTAL);
    }

    private void evaluateUnit(final int unit) {
        final int base = unit * NodeLayout.TOTAL;

        final int constIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        final int resourceIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RESOURCE * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        outputsNext[constIdx] = 1.0;
        outputsNext[randIdx] = Noise.sample(KernelConfig.RANDOM_SEED, unit, tick);
        outputsNext[resourceIdx] = reservoir / KernelConfig.RESOURCE_CAPACITY;

        final int addIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[addIdx] = accumulators[addIdx];

        // MUL by structural in-degree: 0 inputs -> 0; 1 input -> that signal
        // (the binding slice's own example wires DELAY -> MUL -> ADD, so a
        // lone input must pass through); 2+ inputs -> product of the two
        // strongest (slice section 2, ADR 0005)
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[mulIdx] = switch (mulInDegree[unit]) {
            case 0 -> 0.0;
            case 1 -> mulTop1[unit];
            default -> mulTop1[unit] * mulTop2[unit];
        };

        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final double clampIn = accumulators[clampIdx];
        outputsNext[clampIdx] = Math.max(-1.0, Math.min(1.0, clampIn));

        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[delayIdx] = delayMemory[delayIdx];
        delayMemory[delayIdx] = accumulators[delayIdx];

        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[threshIdx] = accumulators[threshIdx] > 0.0 ? 1.0 : -1.0;

        final int actionIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.Y * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[actionIdx] = accumulators[actionIdx];

        final int harvestIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.HARVEST * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[harvestIdx] = accumulators[harvestIdx];
    }

    private void swapBuffers() {
        final double[] tmp = outputsPrev;
        outputsPrev = outputsNext;
        outputsNext = tmp;
    }

    /**
     * Phase 5 — energy intake (contract v1 §4–§5). Each alive unit demands
     * {@code EFFICIENCY × max(0, harvestOutput)} from the shared reservoir; the
     * {@code max(0, …)} means you cannot un-eat. When total demand exceeds the
     * pool, every unit is scaled by the same {@code min(1, reservoir/demandTotal)}
     * — the contract's {@code f(resourceAvailable)} and its proportional-to-demand
     * allocation are one order-independent factor (ADR 0010). The reservoir is
     * drawn down by exactly what units received, then topped up by a fixed inflow
     * admitted only up to capacity; the admitted amount is the sole energy the
     * audit treats as entering the system (cumulativeInflow), so
     * {@code INITIAL_total + cumulativeInflow == sum(energy) + reservoir + sink}
     * stays exact. Intake runs before cost so a unit can pay this tick's
     * metabolism with this tick's harvest (persistence becomes possible);
     * harvest output is read from outputsPrev, the just-swapped current tick.
     */
    private void settleIntake() {
        double demandTotal = 0.0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            demandTotal += harvestDemand(unit);
        }

        if (demandTotal > 0.0) {
            final double factor = Math.min(1.0, reservoir / demandTotal);
            double drawn = 0.0;
            for (int unit = 0; unit < unitCount; unit++) {
                if (energy[unit] <= 0.0) {
                    continue;
                }
                final double intake = harvestDemand(unit) * factor;
                energy[unit] += intake;
                drawn += intake;
            }
            reservoir -= drawn;
        }

        final double admitted = Math.min(KernelConfig.RESOURCE_INFLOW,
                Math.max(0.0, KernelConfig.RESOURCE_CAPACITY - reservoir));
        reservoir += admitted;
        cumulativeInflow += admitted;
    }

    /**
     * A divergent unit's HARVEST output overflows IEEE doubles to ±Infinity/NaN
     * (the substrate never clamps). A non-finite signal is not a readable harvest
     * demand, so it contributes nothing — otherwise {@code Inf × min(1, R/Inf)}
     * would be {@code Inf × 0 = NaN} and poison the shared reservoir. This guards
     * numerical pathology, not behaviour: the kernel still privileges no strategy
     * (ADR 0010). Finite-but-huge demands are fine — proportional allocation just
     * hands such a unit almost the whole pool.
     */
    private double harvestDemand(final int unit) {
        final int harvestIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                + (NodeLayout.Action.HARVEST * NodeLayout.Action.INSTANCES_PER_TYPE);
        final double output = outputsPrev[harvestIdx];
        return Double.isFinite(output) ? KernelConfig.HARVEST_EFFICIENCY * Math.max(0.0, output) : 0.0;
    }

    /**
     * Phase 6 — the entropy law (contract v0 §3, §4, §6). Each unit that was
     * alive this tick pays structural decay (∝ its connection count, charged
     * regardless of activity) plus activity cost (∝ non-zero propagations).
     * The charge is clamped to available energy so a unit can never overdraw:
     * the sink receives exactly what existed, keeping the audit invariant exact.
     * <p>
     * A unit alive at tick start computes the full tick and pays for it;
     * crossing to zero here makes phases 2–3 skip it from the next tick on,
     * so death takes effect next tick. Because intake (phase 5) ran first, a
     * unit's net energy change this tick is {@code intake − charge}: a strong
     * harvester reaches steady state, a poor one still decays to death.
     */
    private void settleCost() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double decay = connectionCount[unit] * KernelConfig.DECAY_PER_CONNECTION;
            final double activity = activeThisTick[unit] * KernelConfig.COST_PER_PROPAGATION;
            final double charge = Math.min(decay + activity, energy[unit]);
            energy[unit] -= charge;
            energySink += charge;
        }
    }

    public KernelSnapshot snapshot() {
        return new KernelSnapshot(tick, outputsPrev, delayMemory, energy, energySink, reservoir, cumulativeInflow);
    }
}
