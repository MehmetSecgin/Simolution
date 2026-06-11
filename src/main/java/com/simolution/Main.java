package com.simolution;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.logging.ConsoleTableLogger;
import com.simolution.kernel.runtime.Kernel;

public class Main {

    static void main() {
        short wConstToAdd   = 2048; // ~ +0.25
        short wAddToDelay   = 512;  // ~ +0.0625
        short wDelayToAdd   = 512;  // ~ +0.0625
        short wAddToAction  = 512;  // ~ +0.0625

        int[] genome = {

                // CONST -> ADD
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wConstToAdd)
                        .build(),

                // ADD -> DELAY
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(wAddToDelay)
                        .build(),

                // DELAY -> ADD (feedback loop, 1-tick delayed)
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wDelayToAdd)
                        .build(),

                // ADD -> ACTION
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw(wAddToAction)
                        .build()
        };

        CompiledConnection[] connections = GenomeCompiler.compileAll(new int[][] {genome});

        Kernel kernel = new Kernel(1, connections);
        ConsoleTableLogger logger = new ConsoleTableLogger();

        for (int i = 0; i < 10; i++) {
            kernel.tick();
            logger.log(kernel.snapshot());
        }
    }

}
