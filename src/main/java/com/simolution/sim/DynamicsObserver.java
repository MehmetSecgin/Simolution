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

    private KernelSnapshot last;
    private int peakPopulation;
    private long birthsTotal;
    private int maxGeneration;
    private double creditedInitialEnergy;
    private double creditedInitialMass;

    private float[] prevOutputs;
    private float[] prevDelay;
    private float[] olderOutputs;
    private float[] olderDelay;
    private final double[] accumulators;
    private final boolean[] activeThisTick;

    private final int[] firstActivityTick;
    private final boolean[] reachedNonFinite;
    private final double[] maxAbsOutput;

    private final double[] prevEnergy;
    private final int[] deathTick;
    private final int[] harvestTicksByUnit;

    private long ticksWithAnyActivity;
    private long propagationsTotal;
    private long junkSinkPropagations;
    private long clampSaturationEvents;
    private long threshFlipsTotal;
    private long stateDigest;
    private int ticksObserved;
    private long harvestActiveTicksTotal;

    private double finalEnergySink;
    private double initialReservoir;
    private double cumulativeInflow;

    public DynamicsObserver(final int unitCount, final CompiledConnection[] connections) {
        this.unitCount = unitCount;
        this.connections = connections;

        final int totalNodes = unitCount * NodeLayout.TOTAL;
        this.prevOutputs = new float[totalNodes];
        this.prevDelay = new float[totalNodes];
        this.olderOutputs = new float[totalNodes];
        this.olderDelay = new float[totalNodes];
        this.accumulators = new double[totalNodes];
        this.activeThisTick = new boolean[unitCount];

        this.firstActivityTick = new int[unitCount];
        Arrays.fill(firstActivityTick, -1);
        this.reachedNonFinite = new boolean[unitCount];
        this.maxAbsOutput = new double[unitCount];

        this.prevEnergy = new double[unitCount];
        Arrays.fill(prevEnergy, KernelConfig.INITIAL_ENERGY);
        this.deathTick = new int[unitCount];
        Arrays.fill(deathTick, -1);
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
        last = snapshot;

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
            }

            final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.THRESH;
            if (snapshot.tick >= 2 && snapshot.outputs[threshIdx] != prevOutputs[threshIdx]) {
                threshFlipsTotal++;
            }

            final int harvestIdx = base + NodeLayout.ACTION_OFFSET + NodeLayout.Action.HARVEST;
            if (prevEnergy[unit] > 0.0 && snapshot.outputs[harvestIdx] > 0.0) {
                harvestActiveTicksTotal++;
                harvestTicksByUnit[unit]++;
            }

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
        }

        final int population = livingCount(snapshot);
        if (population > peakPopulation) {
            peakPopulation = population;
        }
        birthsTotal = snapshot.birthsTotal;
        maxGeneration = snapshot.maxGeneration;
        creditedInitialEnergy = snapshot.creditedInitialEnergy;
        creditedInitialMass = snapshot.creditedInitialMass;

        foldDigest(snapshot);

        finalEnergySink = snapshot.energySink;
        initialReservoir = snapshot.initialResourceTotal;
        cumulativeInflow = snapshot.cumulativeInflow;

        final float[] recycledOutputs = olderOutputs;
        olderOutputs = prevOutputs;
        prevOutputs = recycledOutputs;
        final float[] recycledDelay = olderDelay;
        olderDelay = prevDelay;
        prevDelay = recycledDelay;
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

    private static int livingCount(final KernelSnapshot snapshot) {
        int count = 0;
        for (final double energy : snapshot.energy) {
            if (energy > 0.0) {
                count++;
            }
        }
        return count;
    }

    private int distinctLineagesAlive(final KernelSnapshot snapshot) {
        final boolean[] seen = new boolean[unitCount];
        int distinct = 0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            final long lineage = snapshot.lineageId[slot];
            if (snapshot.energy[slot] > 0.0 && lineage >= 0 && lineage < unitCount && !seen[(int) lineage]) {
                seen[(int) lineage] = true;
                distinct++;
            }
        }
        return distinct;
    }

    /**
     * Largest mass among the living (energy &gt; 0) over the whole slot pool —
     * descendants included, unlike the founder-indexed per-unit arrays. One
     * O(slots) pass, like {@link #sum}; the last tick's value is the final max.
     */
    private static double maxLivingMass(final KernelSnapshot snapshot) {
        double max = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] > 0.0 && snapshot.mass[slot] > max) {
                max = snapshot.mass[slot];
            }
        }
        return max;
    }

    /**
     * Exact fixed-point check: every output and delay memory cell equals
     * last tick's, bit for bit. Two sensor outputs are excluded because they
     * track exogenous, ever-drifting scalars by construction and so would
     * never let any unit register a fixed point, yet they influence nothing
     * unless wired (in which case downstream nodes betray them anyway):
     * RAND (counter noise) and SELF_ENERGY (the unit's own energy, which the
     * metabolic bill moves every tick).
     */
    private boolean unitStateUnchanged(final KernelSnapshot snapshot, final int unit) {
        final int base = unit * NodeLayout.TOTAL;
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;
        final int selfEnergyIdx = base + NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.SELF_ENERGY;

        for (int node = 0; node < NodeLayout.TOTAL; node++) {
            final int idx = base + node;
            if (idx == randIdx || idx == selfEnergyIdx) {
                continue;
            }
            if (snapshot.outputs[idx] != olderOutputs[idx] || snapshot.delayMemory[idx] != olderDelay[idx]) {
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
        for (final double v : snapshot.mass) {
            h = Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (final double v : snapshot.resourceField) {
            h = Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (final int p : snapshot.position) {
            h = Noise.mix(h ^ p);
        }
        h = Noise.mix(h ^ Double.doubleToLongBits(snapshot.cumulativeInflow));
        stateDigest = h;
    }

    /** Reads the last observed snapshot's live kernel arrays, so call it before the kernel ticks again. */
    public DynamicsSummary summarize() {
        final double[] finalAbsAction = new double[unitCount];
        final boolean[] dormantAtEnd = new boolean[unitCount];
        final boolean[] endedAtFixedPoint = new boolean[unitCount];
        final double[] finalEnergy = new double[unitCount];
        Arrays.fill(finalEnergy, KernelConfig.INITIAL_ENERGY);
        for (int unit = 0; unit < unitCount; unit++) {
            final int actionIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;
            finalAbsAction[unit] = Math.abs(prevOutputs[actionIdx]);
            if (last != null) {
                dormantAtEnd[unit] = !activeThisTick[unit];
                endedAtFixedPoint[unit] = unitStateUnchanged(last, unit);
                finalEnergy[unit] = last.energy[unit];
            }
        }

        final int finalPopulation = last == null ? 0 : livingCount(last);
        final int distinctLineagesAlive = last == null ? 0 : distinctLineagesAlive(last);
        final double finalEnergyTotal = last == null ? 0.0 : sum(last.energy);
        final double finalMassTotal = last == null ? 0.0 : last.massTotal;
        final double finalMassMax = last == null ? 0.0 : maxLivingMass(last);
        final double finalReservoir = last == null ? 0.0 : last.reservoir;

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
                harvestTicksByUnit,
                finalEnergyTotal,
                finalEnergySink,
                creditedInitialEnergy,
                KernelConfig.INITIAL_ENERGY,
                initialReservoir,
                finalReservoir,
                cumulativeInflow,
                harvestActiveTicksTotal,
                stateDigest,
                peakPopulation,
                finalPopulation,
                distinctLineagesAlive,
                birthsTotal,
                maxGeneration,
                creditedInitialMass,
                finalMassTotal,
                finalMassMax
        );
    }
}
