package com.simolution.sim;

import java.util.Arrays;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.KernelSnapshot;

public final class BirthTracker {

    private final int maxUnits;
    private final int worldWidth;

    private final double[] prevEnergy;
    private final long[] prevLineage;
    private final int[] prevGeneration;
    private final int[] prevCellOccupant;

    private final int[] children;
    private final int[] parents;
    private int births;

    public BirthTracker(final int maxUnits, final int founderCount, final int worldWidth,
                        final int[] founderCells, final EngineConfig config) {
        this.maxUnits = maxUnits;
        this.worldWidth = worldWidth;

        this.prevEnergy = new double[maxUnits];
        this.prevLineage = new long[maxUnits];
        Arrays.fill(prevLineage, -1L);
        this.prevGeneration = new int[maxUnits];
        this.prevCellOccupant = new int[maxUnits];
        Arrays.fill(prevCellOccupant, -1);
        this.children = new int[maxUnits];
        this.parents = new int[maxUnits];

        for (int slot = 0; slot < founderCount; slot++) {
            prevEnergy[slot] = config.initialEnergy();
            prevLineage[slot] = slot;
            prevGeneration[slot] = 0;
            prevCellOccupant[founderCells[slot]] = slot;
        }
    }

    /**
     * Derives this tick's births from snapshot deltas, in ascending child slot
     * order, then rolls the previous-tick buffers forward. Call once per tick.
     */
    public int observe(final KernelSnapshot snapshot) {
        final double[] energy = snapshot.energy;
        final long[] lineage = snapshot.lineageId;
        final int[] generation = snapshot.generation;
        final int[] position = snapshot.position;

        births = 0;
        for (int slot = 0; slot < maxUnits; slot++) {
            final boolean prevAlive = prevEnergy[slot] > 0.0;
            final boolean curAlive = energy[slot] > 0.0;
            final boolean idChanged = prevAlive && curAlive
                    && (prevLineage[slot] != lineage[slot] || prevGeneration[slot] != generation[slot]);
            if ((!prevAlive && curAlive) || idChanged) {
                children[births] = slot;
                parents[births] = findParent(position[slot], lineage[slot], generation[slot], snapshot);
                births++;
            }
        }

        System.arraycopy(energy, 0, prevEnergy, 0, maxUnits);
        System.arraycopy(lineage, 0, prevLineage, 0, maxUnits);
        System.arraycopy(generation, 0, prevGeneration, 0, maxUnits);
        Arrays.fill(prevCellOccupant, -1);
        for (int slot = 0; slot < maxUnits; slot++) {
            if (energy[slot] > 0.0) {
                prevCellOccupant[position[slot]] = slot;
            }
        }
        return births;
    }

    public int birthCount() {
        return births;
    }

    public int birthChild(final int i) {
        return children[i];
    }

    public int birthParent(final int i) {
        return parents[i];
    }

    /**
     * The unique prev-tick occupant of a Moore neighbour of {@code childCell} whose
     * lineage matches and generation is one less, or -1 if none or more than one
     * matches (ambiguous, so unknown rather than guessed).
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
}
