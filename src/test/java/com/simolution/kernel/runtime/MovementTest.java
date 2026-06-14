package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;

class MovementTest {

    // CONST(=1.0) -> MOVE_E with a weight whose product clears MOVE_DEADZONE
    private static int driveEast(short weightRaw) {
        return GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                .toAction(NodeLayout.Action.MOVE_E)
                .weightRaw(weightRaw)
                .build();
    }

    private static double totalEnergy(KernelSnapshot s) {
        double t = 0.0;
        for (double e : s.energy) {
            t += e;
        }
        return t;
    }

    private static double totalField(KernelSnapshot s) {
        double t = 0.0;
        for (double r : s.resourceField) {
            t += r;
        }
        return t;
    }

    @Test
    void motileUnitStepsEastEachTickAndConservesEnergy() {
        // arrange: one mover on a 4x4 torus, founder at cell 0
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}}, 4, 32, new int[] {0});
        double credited = KernelConfig.INITIAL_ENERGY + KernelConfig.CELL_INITIAL * 16
                + KernelConfig.INITIAL_MASS;

        // act + assert
        // tick 1: CONST only just became 1.0 this tick, so MOVE_E read (outputsPrev)
        // is still 0 -> no step yet
        kernel.tick();
        assertEquals(0, kernel.snapshot().position[0], "no step before the drive propagates");

        // from tick 2 on, MOVE_E drives a +1 x step every tick (cells 1,2,3,0,...)
        for (int expected : new int[] {1, 2, 3, 0, 1}) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            assertEquals(expected, s.position[0], "east step at tick " + s.tick);
            // conservation holds with MOVE_COST flowing unit -> sink (contract v3 §7)
            double held = totalEnergy(s) + totalField(s) + s.massTotal + s.energySink;
            assertEquals(credited + s.cumulativeInflow, held, 1.0e-6,
                    "energy conserved through movement at tick " + s.tick);
        }
    }

    @Test
    void weakDriveInsideDeadzoneDoesNotMove() {
        // arrange: CONST*weight stays below MOVE_DEADZONE, so no step
        short weak = 512; // 512 * 4/32767 ~ 0.0625 < MOVE_DEADZONE 0.1
        assertTrue(weak * KernelConfig.WEIGHT_MULTIPLIER < KernelConfig.MOVE_DEADZONE,
                "test precondition: drive must be inside the deadzone");
        Kernel kernel = new Kernel(new int[][] {{driveEast(weak)}}, 4, 32, new int[] {0});

        // act
        for (int i = 0; i < 10; i++) {
            kernel.tick();
        }

        // assert
        assertEquals(0, kernel.snapshot().position[0], "a sub-deadzone drive never steps");
    }

    @Test
    void stepIntoAnOccupiedCellIsBlocked() {
        // arrange: mover at cell 0 wants to step east into cell 1, held by an
        // inert (empty-genome) unit that never moves
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}, {}}, 4, 32, new int[] {0, 1});

        // act: a few ticks while the blocker is still alive
        for (int i = 0; i < 4; i++) {
            kernel.tick();
        }

        // assert: blocked every tick -> never left cell 0
        KernelSnapshot s = kernel.snapshot();
        assertEquals(0, s.position[0], "mover blocked by the occupant of cell 1");
        assertEquals(1, s.position[1], "blocker stayed put");
    }
}
