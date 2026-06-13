package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams exact life-cycle events to {@code events.jsonl}, one JSON object per
 * line (report-v8). The kernel exposes no event hook (it stays pure); this
 * writer <b>derives</b> every event from snapshot deltas and the state it can
 * already see:
 * <ul>
 *   <li><b>BIRTH</b> — a slot that was empty (or held a different occupant) last
 *       tick now holds a living unit. The parent is the prev-tick occupant of a
 *       Moore neighbour of the child's cell whose lineage matches and whose
 *       generation is one less; reproduction (phase 7) places the child in a free
 *       neighbour of the parent's <i>start-of-tick</i> cell, so prev positions are
 *       the right ones to test (movement is a later phase). The exact mutated bit
 *       count is {@code popcount(child ⊕ parent)} over the genes. If the parent is
 *       ambiguous (no unique match — a rare same-tick tie) {@code parentSlot} and
 *       {@code mutations} are emitted as {@code -1}: an honest "unknown", never a
 *       guess.</li>
 *   <li><b>DEATH</b> — a slot that held a living unit last tick is now empty (or
 *       reused), i.e. it crossed {@code energy ≤ 0} (contract §9). {@code lifespan}
 *       is ticks since that occupant's birth.</li>
 *   <li><b>EXTINCTION</b> — a lineage's living count fell to zero, never to recover
 *       within this slot pool (it cannot: lineage ids are founder-rooted and never
 *       reissued). Emitted once, with the lineage's peak membership and deepest
 *       generation.</li>
 * </ul>
 * Founders are recorded in the manifest, not as events; there is no abiogenesis in
 * the current kernel. Births/deaths within a single tick that reuse a slot for two
 * individuals of the same {@code (lineage, generation)} are the one delta-invisible
 * case — vanishingly rare and noted; the exact cumulative counts still live in
 * {@code metrics.csv} (kernel-tracked {@code birthsTotal}).
 * <p>
 * All buffers are sized once in the constructor; {@link #observe} allocates only
 * the JSONL text it streams, and only on an actual event (events are rare relative
 * to ticks).
 */
public final class EventLogWriter implements Closeable {

    private final Writer out;
    private final int maxUnits;
    private final int founderCount;
    private final int worldWidth;
    private final StringBuilder line;

    private final double[] prevEnergy;
    private final long[] prevLineage;
    private final int[] prevGeneration;
    private final int[] prevPosition;
    private final int[] prevCellOccupant;
    private final int[] birthTick;

    private final int[] lineageCount;
    private final int[] lineagePrevCount;
    private final int[] lineagePeak;
    private final int[] lineageMaxGen;
    private final boolean[] lineageExtinct;

    public EventLogWriter(final Writer out, final int maxUnits, final int founderCount,
                          final int worldWidth, final int[] founderCells) throws IOException {
        this.out = out;
        this.maxUnits = maxUnits;
        this.founderCount = founderCount;
        this.worldWidth = worldWidth;
        this.line = new StringBuilder(160);

        this.prevEnergy = new double[maxUnits];
        this.prevLineage = new long[maxUnits];
        Arrays.fill(prevLineage, -1L);
        this.prevGeneration = new int[maxUnits];
        this.prevPosition = new int[maxUnits];
        Arrays.fill(prevPosition, -1);
        this.prevCellOccupant = new int[maxUnits];
        Arrays.fill(prevCellOccupant, -1);
        this.birthTick = new int[maxUnits];

        this.lineageCount = new int[founderCount];
        this.lineagePrevCount = new int[founderCount];
        this.lineagePeak = new int[founderCount];
        this.lineageMaxGen = new int[founderCount];
        this.lineageExtinct = new boolean[founderCount];

        // tick-0 state: founders alive at their scattered cells, lineage == slot,
        // generation 0, born at tick 0. This is the "previous" state the first
        // observed tick (tick 1) is differenced against.
        for (int slot = 0; slot < founderCount; slot++) {
            prevEnergy[slot] = KernelConfig.INITIAL_ENERGY;
            prevLineage[slot] = slot;
            prevGeneration[slot] = 0;
            final int cell = founderCells[slot];
            prevPosition[slot] = cell;
            prevCellOccupant[cell] = slot;
            lineagePrevCount[slot] = 1;
            lineagePeak[slot] = 1;
        }
    }

    public void observe(final KernelSnapshot snapshot) throws IOException {
        final int tick = snapshot.tick;
        final double[] energy = snapshot.energy;
        final long[] lineage = snapshot.lineageId;
        final int[] generation = snapshot.generation;
        final int[] position = snapshot.position;

        Arrays.fill(lineageCount, 0);

        for (int slot = 0; slot < maxUnits; slot++) {
            final boolean prevAlive = prevEnergy[slot] > 0.0;
            final boolean curAlive = energy[slot] > 0.0;

            if (curAlive) {
                final long l = lineage[slot];
                if (l >= 0 && l < founderCount) {
                    final int li = (int) l;
                    lineageCount[li]++;
                    if (generation[slot] > lineageMaxGen[li]) {
                        lineageMaxGen[li] = generation[slot];
                    }
                }
            }

            final boolean idChanged = prevAlive && curAlive
                    && (prevLineage[slot] != lineage[slot] || prevGeneration[slot] != generation[slot]);

            if ((prevAlive && !curAlive) || idChanged) {
                emitDeath(tick, slot);
            }
            if ((!prevAlive && curAlive) || idChanged) {
                emitBirth(tick, slot, snapshot);
            }
        }

        // peak membership and extinction edges, per founder-rooted lineage
        for (int l = 0; l < founderCount; l++) {
            if (lineageCount[l] > lineagePeak[l]) {
                lineagePeak[l] = lineageCount[l];
            }
            if (lineagePrevCount[l] > 0 && lineageCount[l] == 0 && !lineageExtinct[l]) {
                lineageExtinct[l] = true;
                emitExtinction(tick, l);
            }
        }

        // roll current state into the prev buffers for the next tick's deltas
        System.arraycopy(energy, 0, prevEnergy, 0, maxUnits);
        System.arraycopy(lineage, 0, prevLineage, 0, maxUnits);
        System.arraycopy(generation, 0, prevGeneration, 0, maxUnits);
        System.arraycopy(position, 0, prevPosition, 0, maxUnits);
        System.arraycopy(lineageCount, 0, lineagePrevCount, 0, founderCount);
        Arrays.fill(prevCellOccupant, -1);
        for (int slot = 0; slot < maxUnits; slot++) {
            if (energy[slot] > 0.0) {
                prevCellOccupant[position[slot]] = slot;
            }
        }
    }

    private void emitBirth(final int tick, final int child, final KernelSnapshot s) throws IOException {
        final int cell = s.position[child];
        final long childLineage = s.lineageId[child];
        final int childGen = s.generation[child];
        final int parent = findParent(cell, childLineage, childGen, s);
        final int mutations = parent < 0 ? -1 : countMutations(child, parent, s);
        final long parentLineage = parent < 0 ? -1 : s.lineageId[parent];

        birthTick[child] = tick;

        line.setLength(0);
        line.append("{\"t\":").append(tick)
            .append(",\"ev\":\"BIRTH\",\"slot\":").append(child)
            .append(",\"lineage\":").append(childLineage)
            .append(",\"gen\":").append(childGen)
            .append(",\"cell\":").append(cell)
            .append(",\"parentSlot\":").append(parent)
            .append(",\"parentLineage\":").append(parentLineage)
            .append(",\"mutations\":").append(mutations)
            .append("}\n");
        out.write(line.toString());
        out.flush();
    }

    private void emitDeath(final int tick, final int slot) throws IOException {
        final int lifespan = tick - birthTick[slot];
        line.setLength(0);
        line.append("{\"t\":").append(tick)
            .append(",\"ev\":\"DEATH\",\"slot\":").append(slot)
            .append(",\"lineage\":").append(prevLineage[slot])
            .append(",\"gen\":").append(prevGeneration[slot])
            .append(",\"cell\":").append(prevPosition[slot])
            .append(",\"lifespan\":").append(lifespan)
            .append("}\n");
        out.write(line.toString());
        out.flush();
    }

    private void emitExtinction(final int tick, final int lineage) throws IOException {
        line.setLength(0);
        line.append("{\"t\":").append(tick)
            .append(",\"ev\":\"EXTINCTION\",\"lineage\":").append(lineage)
            .append(",\"lastTick\":").append(tick)
            .append(",\"peakMembers\":").append(lineagePeak[lineage])
            .append(",\"maxGeneration\":").append(lineageMaxGen[lineage])
            .append("}\n");
        out.write(line.toString());
        out.flush();
    }

    /**
     * The unique prev-tick occupant of a Moore neighbour of {@code childCell}
     * whose lineage matches and generation is one less, or -1 if none or more
     * than one matches (ambiguous → unknown, not guessed).
     */
    private int findParent(final int childCell, final long childLineage, final int childGen,
                           final KernelSnapshot s) {
        final int w = worldWidth;
        final int x = childCell % w;
        final int y = childCell / w;
        int found = -1;
        for (int dy = -1; dy <= 1; dy++) {
            final int ny = (((y + dy) % w) + w) % w;
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                final int nx = (((x + dx) % w) + w) % w;
                final int p = prevCellOccupant[ny * w + nx];
                if (p < 0) {
                    continue;
                }
                if (s.lineageId[p] == childLineage && s.generation[p] == childGen - 1) {
                    if (found >= 0) {
                        return -1;
                    }
                    found = p;
                }
            }
        }
        return found;
    }

    private int countMutations(final int child, final int parent, final KernelSnapshot s) {
        final int childCount = s.geneCount[child];
        final int parentCount = s.geneCount[parent];
        if (childCount != parentCount) {
            return -1;
        }
        final int childBase = child * s.maxGenes;
        final int parentBase = parent * s.maxGenes;
        int bits = 0;
        for (int g = 0; g < childCount; g++) {
            bits += Integer.bitCount(s.genes[childBase + g] ^ s.genes[parentBase + g]);
        }
        return bits;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
