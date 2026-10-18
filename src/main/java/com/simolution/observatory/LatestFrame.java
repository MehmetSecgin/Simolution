package com.simolution.observatory;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.ResourceLevels;
import com.simolution.sim.TickObserver;

public class LatestFrame implements TickObserver {

    private static final long MIN_NANOS_BETWEEN_COPIES = 100_000_000L;

    private static final class Buffer {
        private final byte[] levels;
        private final int[] cells;
        private final int[] slots;
        private final int[] lineages;
        private int tick = -1;
        private int units;

        Buffer(final int cellCount) {
            this.levels = new byte[cellCount];
            this.cells = new int[cellCount];
            this.slots = new int[cellCount];
            this.lineages = new int[cellCount];
        }
    }

    private final int width;
    private final double capacity;
    private final ReentrantLock swap = new ReentrantLock();
    private Buffer front;
    private Buffer back;
    private long lastCopyNanos;

    public LatestFrame(final EngineConfig config) {
        this.width = config.world().worldWidth();
        this.capacity = config.chemicals().get(0).capacity();
        this.front = new Buffer(width * width);
        this.back = new Buffer(width * width);
    }

    @Override
    public void observe(final WorldState snapshot) {
        final long now = System.nanoTime();
        if (now - lastCopyNanos >= MIN_NANOS_BETWEEN_COPIES) {
            publish(snapshot);
            lastCopyNanos = now;
        }
    }

    public void publish(final WorldState snapshot) {
        final Buffer target = back;
        target.tick = snapshot.tick;
        for (int cell = 0; cell < target.levels.length; cell++) {
            target.levels[cell] = (byte) ResourceLevels.level(snapshot.cellChem[cell], capacity);
        }
        int units = 0;
        for (int slot = 0; slot < snapshot.unitCount; slot++) {
            if (snapshot.energy[slot] > 0.0) {
                target.cells[units] = snapshot.position[slot];
                target.slots[units] = slot;
                target.lineages[units] = (int) snapshot.lineageId[slot];
                units++;
            }
        }
        target.units = units;
        swap.lock();
        try {
            back = front;
            front = target;
        } finally {
            swap.unlock();
        }
    }

    public byte[] read() {
        swap.lock();
        try {
            if (front.tick < 0) {
                return null;
            }
            final ByteBuffer out = FrameReader.header(front.tick, width, front.levels, front.units);
            for (int i = 0; i < front.units; i++) {
                out.putInt(front.cells[i]).putInt(front.slots[i]).putInt(front.lineages[i]);
            }
            return out.array();
        } finally {
            swap.unlock();
        }
    }
}
