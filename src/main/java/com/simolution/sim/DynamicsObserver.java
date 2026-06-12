package com.simolution.sim;

import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.kernel.runtime.Noise;

/**
 * Observes a run from outside the kernel (contract v0 §12: interpretation
 * is external to the laws). The kernel exposes only snapshots; this class
 * re-derives the propagation phase from the previous tick's outputs and
 * the compiled wiring. The kernel pays nothing for being watched.
 * <p>
 * Activity and dormancy follow their spec definitions: activity = any
 * connection propagating a non-zero signal (slice §9), dormancy = absence
 * of signal flow (contract v0 §7).
 */
public final class DynamicsObserver {

    private final int unitCount;
    private final CompiledConnection[] connections;

    private final double[] prevOutputs;
    private final double[] prevDelay;
    private final double[] accumulators;
    private final boolean[] activeThisTick;

    private final int[] firstActivityTick;
    private final boolean[] dormantAtEnd;
    private final boolean[] endedAtFixedPoint;
    private final boolean[] reachedNonFinite;
    private final double[] maxAbsOutput;

    private final double[] prevEnergy;
    private final int[] deathTick;
    private final double[] finalEnergy;
    private final double[] peakBurn;
    private final long[] propagationsByUnit;
    private final int[] ticksActiveByUnit;
    private final long[] clampByUnit;
    private final long[] threshFlipsByUnit;
    private final int[] harvestTicksByUnit;

    private long ticksWithAnyActivity;
    private long propagationsTotal;
    private long junkSinkPropagations;
    private long clampSaturationEvents;
    private long threshFlipsTotal;
    private long stateDigest;
    private int ticksObserved;
    private long harvestActiveTicksTotal;

    private double finalEnergyTotal;
    private double finalEnergySink;
    private double finalReservoir;
    private double cumulativeInflow;

    public DynamicsObserver(final int unitCount, final CompiledConnection[] connections) {
        this.unitCount = unitCount;
        this.connections = connections;

        final int totalNodes = unitCount * NodeLayout.TOTAL;
        this.prevOutputs = new double[totalNodes];
        this.prevDelay = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.activeThisTick = new boolean[unitCount];

        this.firstActivityTick = new int[unitCount];
        Arrays.fill(firstActivityTick, -1);
        this.dormantAtEnd = new boolean[unitCount];
        this.endedAtFixedPoint = new boolean[unitCount];
        this.reachedNonFinite = new boolean[unitCount];
        this.maxAbsOutput = new double[unitCount];

        this.prevEnergy = new double[unitCount];
        Arrays.fill(prevEnergy, KernelConfig.INITIAL_ENERGY);
        this.deathTick = new int[unitCount];
        Arrays.fill(deathTick, -1);
        this.finalEnergy = new double[unitCount];
        Arrays.fill(finalEnergy, KernelConfig.INITIAL_ENERGY);
        this.peakBurn = new double[unitCount];
        this.propagationsByUnit = new long[unitCount];
        this.ticksActiveByUnit = new int[unitCount];
        this.clampByUnit = new long[unitCount];
        this.threshFlipsByUnit = new long[unitCount];
        this.harvestTicksByUnit = new int[unitCount];
    }

    /**
     * Call once after every kernel.tick(), with that tick's snapshot.
     * Re-runs the propagation arithmetic against prevOutputs — the same
     * inputs the kernel itself used — so observed signals are exactly
     * the signals that flowed.
     */
    public void observe(final KernelSnapshot snapshot) {
        ticksObserved++;

        Arrays.fill(accumulators, 0.0);
        Arrays.fill(activeThisTick, false);

        boolean anyActivity = false;
        for (final CompiledConnection connection : connections) {
            final int unit = connection.sourceAbsoluteIndex / NodeLayout.TOTAL;
            // mirror the kernel: a unit dead at the start of this tick (its
            // energy in the previous snapshot) propagated nothing this tick
            if (prevEnergy[unit] <= 0.0) {
                continue;
            }

            final double signal = prevOutputs[connection.sourceAbsoluteIndex] * connection.weight;
            accumulators[connection.destinationAbsoluteIndex] += signal;

            if (signal != 0.0) {
                anyActivity = true;
                propagationsTotal++;
                propagationsByUnit[unit]++;

                activeThisTick[unit] = true;
                if (firstActivityTick[unit] < 0) {
                    firstActivityTick[unit] = snapshot.tick;
                }

                final int dstLocal = connection.destinationAbsoluteIndex % NodeLayout.TOTAL;
                if (!NodeLayout.isMeaningful(dstLocal)) {
                    junkSinkPropagations++;
                }
            }
        }
        if (anyActivity) {
            ticksWithAnyActivity++;
        }

        for (int unit = 0; unit < unitCount; unit++) {
            final int base = unit * NodeLayout.TOTAL;

            final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.CLAMP;
            if (Math.abs(accumulators[clampIdx]) > 1.0) {
                clampSaturationEvents++;
                clampByUnit[unit]++;
            }

            final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.THRESH;
            if (snapshot.tick >= 2 && snapshot.outputs[threshIdx] != prevOutputs[threshIdx]) {
                threshFlipsTotal++;
                threshFlipsByUnit[unit]++;
            }

            if (activeThisTick[unit]) {
                ticksActiveByUnit[unit]++;
            }

            final int harvestIdx = base + NodeLayout.ACTION_OFFSET + NodeLayout.Action.HARVEST;
            if (prevEnergy[unit] > 0.0 && snapshot.outputs[harvestIdx] > 0.0) {
                harvestActiveTicksTotal++;
                harvestTicksByUnit[unit]++;
            }

            // energy charged this tick = what left the unit; peak is the
            // hardest single-tick burn over its life
            final double burn = prevEnergy[unit] - snapshot.energy[unit];
            if (burn > peakBurn[unit]) {
                peakBurn[unit] = burn;
            }

            endedAtFixedPoint[unit] = unitStateUnchanged(snapshot, unit);
            dormantAtEnd[unit] = !activeThisTick[unit];

            for (int node = 0; node < NodeLayout.TOTAL; node++) {
                final double value = snapshot.outputs[base + node];
                if (!Double.isFinite(value)) {
                    reachedNonFinite[unit] = true;
                } else {
                    maxAbsOutput[unit] = Math.max(maxAbsOutput[unit], Math.abs(value));
                }
            }

            if (deathTick[unit] < 0 && snapshot.energy[unit] <= 0.0) {
                deathTick[unit] = snapshot.tick;
            }
            finalEnergy[unit] = snapshot.energy[unit];
        }

        foldDigest(snapshot);

        finalEnergyTotal = sum(snapshot.energy);
        finalEnergySink = snapshot.energySink;
        finalReservoir = snapshot.reservoir;
        cumulativeInflow = snapshot.cumulativeInflow;

        System.arraycopy(snapshot.outputs, 0, prevOutputs, 0, prevOutputs.length);
        System.arraycopy(snapshot.delayMemory, 0, prevDelay, 0, prevDelay.length);
        System.arraycopy(snapshot.energy, 0, prevEnergy, 0, prevEnergy.length);
    }

    private static double sum(final double[] values) {
        double total = 0.0;
        for (final double value : values) {
            total += value;
        }
        return total;
    }

    /**
     * Exact fixed-point check: every output and delay memory cell equals
     * last tick's, bit for bit. The RAND sensor's own output is excluded —
     * it changes every tick by construction, but influences nothing unless
     * wired, in which case downstream nodes betray it anyway.
     */
    private boolean unitStateUnchanged(final KernelSnapshot snapshot, final int unit) {
        final int base = unit * NodeLayout.TOTAL;
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;

        for (int node = 0; node < NodeLayout.TOTAL; node++) {
            final int idx = base + node;
            if (idx == randIdx) {
                continue;
            }
            if (snapshot.outputs[idx] != prevOutputs[idx] || snapshot.delayMemory[idx] != prevDelay[idx]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Order-fixed fold over every output and delay cell of every tick
     * (RAND included). Two runs share a digest iff their trajectories are
     * bit-identical — this catches drift below display rounding.
     */
    private void foldDigest(final KernelSnapshot snapshot) {
        long h = stateDigest;
        for (final double v : snapshot.outputs) {
            h = Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (final double v : snapshot.delayMemory) {
            h = Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (final double v : snapshot.energy) {
            h = Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        h = Noise.mix(h ^ Double.doubleToLongBits(snapshot.reservoir));
        h = Noise.mix(h ^ Double.doubleToLongBits(snapshot.cumulativeInflow));
        stateDigest = h;
    }

    public DynamicsSummary summarize() {
        final double[] finalAbsAction = new double[unitCount];
        for (int unit = 0; unit < unitCount; unit++) {
            final int actionIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;
            finalAbsAction[unit] = Math.abs(prevOutputs[actionIdx]);
        }

        return new DynamicsSummary(
                ticksObserved,
                ticksWithAnyActivity,
                propagationsTotal,
                junkSinkPropagations,
                clampSaturationEvents,
                threshFlipsTotal,
                firstActivityTick,
                dormantAtEnd,
                endedAtFixedPoint,
                reachedNonFinite,
                maxAbsOutput,
                finalAbsAction,
                deathTick,
                finalEnergy,
                peakBurn,
                propagationsByUnit,
                ticksActiveByUnit,
                clampByUnit,
                threshFlipsByUnit,
                harvestTicksByUnit,
                finalEnergyTotal,
                finalEnergySink,
                KernelConfig.INITIAL_ENERGY * unitCount,
                KernelConfig.INITIAL_ENERGY,
                KernelConfig.RESOURCE_INITIAL,
                finalReservoir,
                cumulativeInflow,
                harvestActiveTicksTotal,
                stateDigest
        );
    }
}
