package com.simolution.kernel.logging;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

public final class ConsoleTableLogger {

    private boolean headerPrinted = false;

    public void log(KernelSnapshot snapshot) {
        final int base = 0;

        final int constIdx = base + NodeLayout.SENSOR_OFFSET
                             + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET
                            + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        final int addIdx = base + NodeLayout.INTERNAL_OFFSET
                           + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET
                           + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET
                             + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET
                             + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET
                              + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);

        final int actionIdx = base + NodeLayout.ACTION_OFFSET
                              + (NodeLayout.Action.Y * NodeLayout.Action.INSTANCES_PER_TYPE);

        if (!headerPrinted) {
            System.out.printf(
                    "%5s | %7s %7s | %7s %7s %7s %7s %7s | %7s | %7s%n",
                    "Tick",
                    "CONST", "RAND",
                    "ADD", "MUL", "CLAMP", "DELAY", "THRESH",
                    "ACTION",
                    "DELAY_M"
            );
            System.out.println(
                    "------+-----------------+-----------------------------------------+---------+---------"
            );
            headerPrinted = true;
        }

        System.out.printf(
                "%5d | %7.4f %7.4f | %7.4f %7.4f %7.4f %7.4f %7.4f | %7.4f | %7.4f%n",
                snapshot.tick,
                snapshot.outputs[constIdx],
                snapshot.outputs[randIdx],
                snapshot.outputs[addIdx],
                snapshot.outputs[mulIdx],
                snapshot.outputs[clampIdx],
                snapshot.outputs[delayIdx],
                snapshot.outputs[threshIdx],
                snapshot.outputs[actionIdx],
                snapshot.delayMemory[delayIdx]
        );
    }
}
