package com.simolution.sim.run;

import java.util.concurrent.atomic.AtomicReference;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.Noise;
import com.simolution.kernel.state.WorldState;

/**
 * Latest-frame-wins publisher: {@link #publish} never waits for a reader and allocates nothing.
 * A {@link Frame} stays intact only until the publish after the next one, so readers copy it promptly.
 * {@code genomeHash} is a 64-bit content hash of the occupant's genes, and 0 for an empty cell.
 */
public final class LatestFrame {

    public static final class Frame {

        private int tick;
        private final int[] cellOccupant;
        private final long[] genomeHash;
        private final double[] chemicals;

        private Frame(final int cells, final int chemCount) {
            this.cellOccupant = new int[cells];
            this.genomeHash = new long[cells];
            this.chemicals = new double[chemCount * cells];
        }

        public int tick() {
            return tick;
        }

        public int[] cellOccupant() {
            return cellOccupant;
        }

        public long[] genomeHash() {
            return genomeHash;
        }

        public double[] chemicals() {
            return chemicals;
        }
    }

    private final Frame[] buffers;
    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private int next;

    public LatestFrame(final EngineConfig config) {
        final int cells = config.world().worldWidth() * config.world().worldWidth();
        this.buffers = new Frame[] {
            new Frame(cells, config.chemicals().size()), new Frame(cells, config.chemicals().size())};
    }

    public void publish(final Kernel kernel) {
        final WorldState state = kernel.state();
        final Frame frame = buffers[next];
        frame.tick = state.tick;
        System.arraycopy(state.cellOccupant, 0, frame.cellOccupant, 0, frame.cellOccupant.length);
        System.arraycopy(state.cellChem, 0, frame.chemicals, 0, frame.chemicals.length);
        for (int cell = 0; cell < frame.cellOccupant.length; cell++) {
            final int slot = frame.cellOccupant[cell];
            frame.genomeHash[cell] = slot < 0 ? 0L : genomeHash(state, slot);
        }
        latest.set(frame);
        next ^= 1;
    }

    public Frame latest() {
        return latest.get();
    }

    private static long genomeHash(final WorldState state, final int slot) {
        final int base = slot * state.maxGenes;
        final int count = state.geneCount[slot];
        long h = Noise.mix(count);
        for (int g = 0; g < count; g++) {
            h = Noise.mix(h ^ state.genes[base + g]);
        }
        return h == 0L ? 1L : h;
    }
}
