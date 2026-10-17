package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.genome.GeneDecoder;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;

class MovementTest {

    private static final EngineConfig DEFAULTS = EngineConfig.v5Defaults(0L, 1, 1);

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
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}}, new int[] {0}, EngineConfig.v5Defaults(0L, 4, 32), 1);
        double credited = DEFAULTS.initialEnergy() + DEFAULTS.chemicals().get(0).initial() * 16
                + DEFAULTS.initialMass();

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
        assertTrue(weak * GeneDecoder.WEIGHT_MULTIPLIER < DEFAULTS.moveDeadzone(),
                "test precondition: drive must be inside the deadzone");
        Kernel kernel = new Kernel(new int[][] {{driveEast(weak)}}, new int[] {0}, EngineConfig.v5Defaults(0L, 4, 32), 1);

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
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}, {}}, new int[] {0, 1}, EngineConfig.v5Defaults(0L, 4, 32), 1);

        // act: a few ticks while the blocker is still alive
        for (int i = 0; i < 4; i++) {
            kernel.tick();
        }

        // assert: blocked every tick -> never left cell 0
        KernelSnapshot s = kernel.snapshot();
        assertEquals(0, s.position[0], "mover blocked by the occupant of cell 1");
        assertEquals(1, s.position[1], "blocker stayed put");
    }

    @Test
    void contestedCellGoesToLowestSlot() {
        // arrange: slot 0 (cell 0) drives east, slot 1 (cell 2) drives west, both into free cell 1
        int driveWest = GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                .toAction(NodeLayout.Action.MOVE_W)
                .weightRaw((short) 8192)
                .build();
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}, {driveWest}}, new int[] {0, 2}, EngineConfig.v5Defaults(0L, 4, 32), 1);
        kernel.tick();
        KernelSnapshot before = kernel.snapshot();
        double moveCost = DEFAULTS.moveCostBase() + DEFAULTS.moveCostPerMass() * before.mass[0];

        // act: the tick in which both drives are live
        kernel.tick();

        // assert: the lowest slot moved and paid; the loser stayed and paid nothing for the move
        KernelSnapshot s = kernel.snapshot();
        assertEquals(1, s.position[0], "slot 0 wins the contested cell");
        assertEquals(2, s.position[1], "slot 1 loses and stays");
        assertEquals(s.energy[1] - moveCost, s.energy[0], 1.0e-9, "only the winner pays the move cost");
    }

    @Test
    void vacatedCellCannotBeEnteredSameTick() {
        // arrange: slot 0 at cell 1 steps east out of cell 1; slot 1 at cell 0 targets cell 1
        Kernel kernel = new Kernel(new int[][] {{driveEast((short) 8192)}, {driveEast((short) 8192)}}, new int[] {1, 0}, EngineConfig.v5Defaults(0L, 4, 32), 1);
        kernel.tick();

        // act + assert: first live tick, the vacated cell is still closed to slot 1
        kernel.tick();
        assertEquals(2, kernel.snapshot().position[0], "slot 0 stepped out");
        assertEquals(0, kernel.snapshot().position[1], "slot 1 cannot enter a cell vacated this tick");

        // next tick the cell is free at phase start
        kernel.tick();
        assertEquals(1, kernel.snapshot().position[1], "slot 1 enters on the following tick");
    }
}
