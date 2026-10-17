package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.layout.NodeSpec;

class EvaluatePhaseTest {

    private static final int WORLD_WIDTH = 8;
    private static final int CELLS = WORLD_WIDTH * WORLD_WIDTH;

    private static ChemicalSpec chemical(final String name, final double capacity) {
        return new ChemicalSpec(name, InflowConfig.uniform(1.0), capacity, 0.0, 0.1, 0.02, -1);
    }

    @Test
    void chemicalSensorTapsItsDeclaredChemical() {
        // arrange
        final EngineConfig config = EngineConfig.defaults(0L, WORLD_WIDTH, 4)
                .withChemicals(List.of(chemical("first", 100.0), chemical("second", 40.0)));
        final List<NodeSpec> sensors = List.of(
                NodeSpec.sensor("CONST", NodeSpec.Role.CONST),
                NodeSpec.chemicalSensor("FIRST_HERE", 0),
                NodeSpec.chemicalSensor("SECOND_HERE", 1));

        // act
        final EvaluatePhase.SensorTaps taps = EvaluatePhase.bindSensors(sensors, config);

        // assert
        assertArrayEquals(new int[] {0, 0, CELLS}, taps.cellOffset());
        assertArrayEquals(new double[] {1.0, 100.0, 40.0}, taps.capacity());
    }

    @Test
    void chemicalSensorOutsideTheConfigTableIsRejectedByName() {
        // arrange
        final EngineConfig config = EngineConfig.defaults(0L, WORLD_WIDTH, 4);
        final List<NodeSpec> sensors = List.of(NodeSpec.chemicalSensor("SECOND_HERE", 1));

        // act
        final IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> EvaluatePhase.bindSensors(sensors, config));

        // assert
        assertTrue(e.getMessage().contains("SECOND_HERE"), e.getMessage());
        assertEquals(1, config.chemicals().size());
    }
}
