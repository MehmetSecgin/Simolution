package com.simolution.kernel.runtime;

/**
 * Stateless counter-based randomness for the RAND sensor.
 * <p>
 * Instead of a mutable RNG stream, each value is a pure hash of
 * {@code (seed, unitIndex, tick)}. Consequences (see ADR 0003):
 * <ul>
 *   <li>a unit's stream never depends on population size or evaluation order</li>
 *   <li>evaluation can be reordered or parallelized without changing results</li>
 *   <li>any tick's value is computable directly — no replay needed</li>
 * </ul>
 */
public final class Noise {

    /**
     * 2^64 / phi, the SplitMix64 increment. Adding multiples of an
     * odd irrational-derived constant spreads consecutive integers
     * (unit 0, 1, 2... / tick 0, 1, 2...) far apart in the hash input
     * space, so neighbouring units and ticks decorrelate.
     */
    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Noise() {}

    /**
     * Returns a deterministic pseudo-random double in {@code [-1, 1)}.
     * <p>
     * The two-step chain (mix seed+unit, then mix in tick) hashes the
     * pair (unit, tick) rather than their sum, so (unit=1, tick=0) and
     * (unit=0, tick=1) produce unrelated values. The {@code +1} offsets
     * keep unit 0 / tick 0 from hashing the raw seed alone.
     * <p>
     * Range mapping: the top 53 bits of the hash (a double's full
     * mantissa precision) scaled by 2^-52 give {@code [0, 2)}, shifted
     * down to {@code [-1, 1)}.
     */
    public static double sample(long seed, int unitIndex, int tick) {
        long h = mix(seed + GOLDEN_GAMMA * (unitIndex + 1L));
        h = mix(h + GOLDEN_GAMMA * (tick + 1L));
        return (h >>> 11) * 0x1.0p-52 - 1.0;
    }

    /**
     * Deterministic pseudo-random double in {@code [0, 1)} for mutation
     * decisions, keyed by the birth event {@code (seed, childSlot, birthTick)}
     * and a per-stream {@code index} (contract v2 §9). Domain-separated from
     * {@link #sample} — which hashes (unit, tick) for the RAND sensor — by
     * folding in the index as a third dimension, so a child's mutation stream
     * never collides with any unit's sensor noise. Same key → same flips,
     * always, so a run's entire genealogy is reproducible.
     */
    public static double mutationUniform(long seed, int childSlot, int birthTick, int index) {
        long h = mix(seed + GOLDEN_GAMMA * (childSlot + 1L));
        h = mix(h + GOLDEN_GAMMA * (birthTick + 1L));
        h = mix(h + GOLDEN_GAMMA * (index + 1L));
        return (h >>> 11) * 0x1.0p-52;
    }

    /**
     * SplitMix64 finalizer (Steele et al.): xor-shift + odd-constant
     * multiplies until every input bit affects every output bit
     * (avalanche). This is what turns nearby inputs into
     * statistically independent outputs.
     */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
