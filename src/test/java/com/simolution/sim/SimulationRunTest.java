package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;

class SimulationRunTest {

    @Test
    void observersRunInArrayOrderEveryTick() throws Exception {
        // arrange
        final List<String> calls = new ArrayList<>();
        final TickObserver a = snapshot -> calls.add("a" + snapshot.tick);
        final TickObserver b = snapshot -> calls.add("b" + snapshot.tick);
        final Founders founders = Founders.create(RunConfig.parse(new String[] {"--units", "3", "--world", "4"}));

        // act
        try (Kernel kernel = founders.newKernel(1)) {
            SimulationRun.execute(kernel, 3, new TickObserver[] {a, b});
        }

        // assert
        assertEquals(List.of("a1", "b1", "a2", "b2", "a3", "b3"), calls);
    }
}
