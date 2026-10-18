package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.state.WorldState;

/** Streams birth, death and extinction events to events.jsonl; format in docs/specs/report-v8.md. */
public class EventLogWriter implements TickObserver, Closeable {

    private final Writer out;
    private final int slotCount;
    private final int founderCount;
    private final StringBuilder line = new StringBuilder(160);

    private final double[] prevEnergy;
    private final long[] prevLineage;
    private final int[] prevGeneration;
    private final int[] prevPosition;
    private final int[] birthTickBySlot;

    private final int[] lineageCount;
    private final int[] lineagePrevCount;
    private final int[] lineagePeak;
    private final int[] lineageMaxGeneration;
    private final boolean[] lineageExtinct;

    public EventLogWriter(final Writer out, final int slotCount, final int founderCount,
            final int[] founderCells, final double founderEnergy) {
        this.out = out;
        this.slotCount = slotCount;
        this.founderCount = founderCount;

        this.prevEnergy = new double[slotCount];
        this.prevLineage = new long[slotCount];
        Arrays.fill(prevLineage, -1L);
        this.prevGeneration = new int[slotCount];
        this.prevPosition = new int[slotCount];
        Arrays.fill(prevPosition, -1);
        this.birthTickBySlot = new int[slotCount];

        this.lineageCount = new int[founderCount];
        this.lineagePrevCount = new int[founderCount];
        this.lineagePeak = new int[founderCount];
        this.lineageMaxGeneration = new int[founderCount];
        this.lineageExtinct = new boolean[founderCount];

        seedFounderState(founderCells, founderEnergy);
    }

    private void seedFounderState(final int[] founderCells, final double founderEnergy) {
        for (int slot = 0; slot < founderCount; slot++) {
            prevEnergy[slot] = founderEnergy;
            prevLineage[slot] = slot;
            prevGeneration[slot] = 0;
            prevPosition[slot] = founderCells[slot];
            lineagePrevCount[slot] = 1;
            lineagePeak[slot] = 1;
        }
    }

    @Override
    public void observe(final WorldState snapshot) throws IOException {
        tallyLivingLineages(snapshot);
        diffSlots(snapshot);
        emitExtinctions(snapshot.tick);
        rememberCurrentState(snapshot);
    }

    private void tallyLivingLineages(final WorldState snapshot) {
        Arrays.fill(lineageCount, 0);
        for (int slot = 0; slot < slotCount; slot++) {
            if (snapshot.energy[slot] <= 0.0) {
                continue;
            }
            final long lineageId = snapshot.lineageId[slot];
            if (lineageId >= 0 && lineageId < founderCount) {
                final int lineageIndex = (int) lineageId;
                lineageCount[lineageIndex]++;
                lineageMaxGeneration[lineageIndex] =
                        Math.max(lineageMaxGeneration[lineageIndex], snapshot.generation[slot]);
            }
        }
    }

    private void diffSlots(final WorldState snapshot) throws IOException {
        int nextBirth = 0;
        for (int slot = 0; slot < slotCount; slot++) {
            final boolean wasAlive = prevEnergy[slot] > 0.0;
            final boolean isAlive = snapshot.energy[slot] > 0.0;
            final boolean slotReceivedBirth = nextBirth < snapshot.birthCount && snapshot.birthChild[nextBirth] == slot;
            if (wasAlive && (!isAlive || slotReceivedBirth)) {
                emitDeath(snapshot.tick, slot);
            }
            if (slotReceivedBirth) {
                emitBirth(snapshot.tick, slot, snapshot.birthParent[nextBirth], snapshot);
                nextBirth++;
            }
        }
    }

    private void emitExtinctions(final int tick) throws IOException {
        for (int lineage = 0; lineage < founderCount; lineage++) {
            lineagePeak[lineage] = Math.max(lineagePeak[lineage], lineageCount[lineage]);
            if (lineagePrevCount[lineage] > 0 && lineageCount[lineage] == 0 && !lineageExtinct[lineage]) {
                lineageExtinct[lineage] = true;
                emitExtinction(tick, lineage);
            }
        }
    }

    private void rememberCurrentState(final WorldState snapshot) {
        System.arraycopy(snapshot.energy, 0, prevEnergy, 0, slotCount);
        System.arraycopy(snapshot.lineageId, 0, prevLineage, 0, slotCount);
        System.arraycopy(snapshot.generation, 0, prevGeneration, 0, slotCount);
        System.arraycopy(snapshot.position, 0, prevPosition, 0, slotCount);
        System.arraycopy(lineageCount, 0, lineagePrevCount, 0, founderCount);
    }

    private void emitBirth(final int tick, final int child, final int parent,
            final WorldState snapshot) throws IOException {
        birthTickBySlot[child] = tick;
        line.setLength(0);
        line.append("{\"t\":").append(tick)
                .append(",\"ev\":\"BIRTH\",\"slot\":").append(child)
                .append(",\"lineage\":").append(snapshot.lineageId[child])
                .append(",\"gen\":").append(snapshot.generation[child])
                .append(",\"cell\":").append(snapshot.position[child])
                .append(",\"parentSlot\":").append(parent)
                .append(",\"parentLineage\":").append(snapshot.lineageId[parent])
                .append(",\"mutations\":").append(countMutations(child, parent, snapshot))
                .append("}\n");
        flushLine();
    }

    private void emitDeath(final int tick, final int slot) throws IOException {
        line.setLength(0);
        line.append("{\"t\":").append(tick)
                .append(",\"ev\":\"DEATH\",\"slot\":").append(slot)
                .append(",\"lineage\":").append(prevLineage[slot])
                .append(",\"gen\":").append(prevGeneration[slot])
                .append(",\"cell\":").append(prevPosition[slot])
                .append(",\"lifespan\":").append(tick - birthTickBySlot[slot])
                .append("}\n");
        flushLine();
    }

    private void emitExtinction(final int tick, final int lineage) throws IOException {
        line.setLength(0);
        line.append("{\"t\":").append(tick)
                .append(",\"ev\":\"EXTINCTION\",\"lineage\":").append(lineage)
                .append(",\"lastTick\":").append(tick)
                .append(",\"peakMembers\":").append(lineagePeak[lineage])
                .append(",\"maxGeneration\":").append(lineageMaxGeneration[lineage])
                .append("}\n");
        flushLine();
    }

    private void flushLine() throws IOException {
        out.write(line.toString());
        out.flush();
    }

    private int countMutations(final int child, final int parent, final WorldState snapshot) {
        final int childCount = snapshot.geneCount[child];
        if (childCount != snapshot.geneCount[parent]) {
            return -1;
        }
        final int childBase = child * snapshot.maxGenes;
        final int parentBase = parent * snapshot.maxGenes;
        int bits = 0;
        for (int g = 0; g < childCount; g++) {
            bits += Integer.bitCount(snapshot.genes[childBase + g] ^ snapshot.genes[parentBase + g]);
        }
        return bits;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
