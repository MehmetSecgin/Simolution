package com.simolution.kernel.logging;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

public final class ConsoleTableLogger {

    private boolean headerPrinted = false;

    public void log(KernelSnapshot snapshot, int unitsToTrace) {
        if (!headerPrinted) {
            System.out.printf(
                    "%5s %4s | %7s %7s | %7s %7s %7s %7s %7s | %7s | %7s | %9s%n",
                    "Tick", "Unit",
                    "CONST", "RAND",
                    "ADD", "MUL", "CLAMP", "DELAY", "THRESH",
                    "ACTION",
                    "DELAY_M",
                    "ENERGY"
            );
            System.out.println(
                    "-----------+-----------------+-----------------------------------------+---------+---------+----------"
            );
            headerPrinted = true;
        }

        for (int unit = 0; unit < unitsToTrace; unit++) {
            final int base = unit * NodeLayout.TOTAL;

            final int constIdx = base + NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.CONST;
            final int randIdx = base + NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;
            final int addIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
            final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
            final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.CLAMP;
            final int delayIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.DELAY;
            final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.THRESH;
            final int actionIdx = base + NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;

            System.out.printf(
                    "%5d %4d | %7.4f %7.4f | %7.4f %7.4f %7.4f %7.4f %7.4f | %7.4f | %7.4f | %9.3f%n",
                    snapshot.tick,
                    unit,
                    snapshot.outputs[constIdx],
                    snapshot.outputs[randIdx],
                    snapshot.outputs[addIdx],
                    snapshot.outputs[mulIdx],
                    snapshot.outputs[clampIdx],
                    snapshot.outputs[delayIdx],
                    snapshot.outputs[threshIdx],
                    snapshot.outputs[actionIdx],
                    snapshot.delayMemory[delayIdx],
                    snapshot.energy[unit]
            );
        }
    }
}
