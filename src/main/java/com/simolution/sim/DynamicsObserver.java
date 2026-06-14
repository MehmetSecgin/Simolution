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
    private final int maxUnits;
    private final CompiledConnection[] connections;

    private final boolean[] lineageSeen;
    private int peakPopulation;
    private int finalPopulation;
    private int distinctLineagesAlive;
    private long birthsTotal;
    private int maxGeneration;
    private double creditedInitialEnergy;
    private double creditedInitialMass;
    private double finalMassTotal;
    private double finalMassMax;

    // per-lineage running aggregates, indexed by lineageId (= the founder slot
    // index a lineage descends from, always < founder count). Bounded by the
    // founder count, never by ticks. Scratch is refilled each tick.
    private final int[] lineagePeak;
    private final int[] lineageFinal;
    private final int[] lineageMaxGen;
    private final int[] lineageFirstTick;
    private final int[] lineageExtinctTick;
    private final double[] lineageFinalEnergy;
    private final int[] lineageAliveScratch;
    private final int[] lineageGenScratch;
    private final double[] lineageEnergyScratch;

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
    private final double[] finalMass;
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
    private double initialReservoir;
    private double cumulativeInflow;

    public DynamicsObserver(final int unitCount, final int maxUnits, final CompiledConnection[] connections) {
        this.unitCount = unitCount;
        this.maxUnits = maxUnits;
        this.connections = connections;
        this.lineageSeen = new boolean[maxUnits];

        this.lineagePeak = new int[unitCount];
        this.lineageFinal = new int[unitCount];
        this.lineageMaxGen = new int[unitCount];
        this.lineageFirstTick = new int[unitCount];
        Arrays.fill(lineageFirstTick, -1);
        this.lineageExtinctTick = new int[unitCount];
        Arrays.fill(lineageExtinctTick, -1);
        this.lineageFinalEnergy = new double[unitCount];
        this.lineageAliveScratch = new int[unitCount];
        this.lineageGenScratch = new int[unitCount];
        this.lineageEnergyScratch = new double[unitCount];

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
        this.finalMass = new double[unitCount];
        Arrays.fill(finalMass, KernelConfig.INITIAL_MASS);
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
            finalMass[unit] = snapshot.mass[unit];
        }

        // population-wide reproduction aggregates over every slot (founders and
        // born children alike), independent of the founder-scoped per-unit
        // arrays above. Running, bounded — no per-tick history kept.
        int population = 0;
        int distinct = 0;
        Arrays.fill(lineageSeen, false);
        Arrays.fill(lineageAliveScratch, 0);
        Arrays.fill(lineageGenScratch, 0);
        Arrays.fill(lineageEnergyScratch, 0.0);
        for (int slot = 0; slot < maxUnits; slot++) {
            if (snapshot.energy[slot] > 0.0) {
                population++;
                final long lineage = snapshot.lineageId[slot];
                if (lineage >= 0 && lineage < unitCount) {
                    final int l = (int) lineage;
                    if (!lineageSeen[l]) {
                        lineageSeen[l] = true;
                        distinct++;
                    }
                    lineageAliveScratch[l]++;
                    if (snapshot.generation[slot] > lineageGenScratch[l]) {
                        lineageGenScratch[l] = snapshot.generation[slot];
                    }
                    lineageEnergyScratch[l] += snapshot.energy[slot];
                }
            }
        }
        for (int l = 0; l < unitCount; l++) {
            final int alive = lineageAliveScratch[l];
            if (alive > 0) {
                if (lineageFirstTick[l] < 0) {
                    lineageFirstTick[l] = snapshot.tick;
                }
                if (alive > lineagePeak[l]) {
                    lineagePeak[l] = alive;
                }
                if (lineageGenScratch[l] > lineageMaxGen[l]) {
                    lineageMaxGen[l] = lineageGenScratch[l];
                }
                lineageFinal[l] = alive;
                lineageFinalEnergy[l] = lineageEnergyScratch[l];
            } else if (lineageFirstTick[l] >= 0 && lineageExtinctTick[l] < 0) {
                lineageExtinctTick[l] = snapshot.tick;
                lineageFinal[l] = 0;
                lineageFinalEnergy[l] = 0.0;
            }
        }
        if (population > peakPopulation) {
            peakPopulation = population;
        }
        finalPopulation = population;
        distinctLineagesAlive = distinct;
        birthsTotal = snapshot.birthsTotal;
        maxGeneration = snapshot.maxGeneration;
        creditedInitialEnergy = snapshot.creditedInitialEnergy;
        creditedInitialMass = snapshot.creditedInitialMass;

        foldDigest(snapshot);

        finalEnergyTotal = sum(snapshot.energy);
        finalMassTotal = snapshot.massTotal;
        finalMassMax = maxLivingMass(snapshot);
        finalEnergySink = snapshot.energySink;
        finalReservoir = snapshot.reservoir;
        initialReservoir = snapshot.initialResourceTotal;
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

    /**
     * Per-lineage aggregates (one entry per founder, indexed by lineageId).
     * Bounded by the founder count; rendered to the .lineage.csv sidecar.
     */
    public LineageSummary lineageSummary() {
        return new LineageSummary(
                lineagePeak, lineageFinal, lineageMaxGen,
                lineageFirstTick, lineageExtinctTick, lineageFinalEnergy);
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
                finalMass,
                finalMassMax
        );
    }
}
