package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GeneDecoder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.support.EnergyAudit;
import com.simolution.support.Genes;
import com.simolution.support.Worlds;

class MovementTest {

    private static final EngineConfig DEFAULTS = EngineConfig.defaults(0L, 1, 1);

    private static final int CLEARS_DEADZONE_RAW = 8192;
    private static final int SUB_DEADZONE_RAW = 512;
    private static final int TORUS_WIDTH = 4;

    private static int driveEast(int weightRaw) {
        return Genes.constTo(NodeLayout.Action.MOVE_E, weightRaw);
    }

    private static int driveWest(int weightRaw) {
        return Genes.constTo(NodeLayout.Action.MOVE_W, weightRaw);
    }

    @Test
    @DisplayName("contract-v3 §7: a mover steps east every tick once the drive propagates, with energy conserved")
    void motileUnitStepsEastEachTickAndConservesEnergy() {
        // arrange
        Kernel kernel = Worlds.kernelAt(TORUS_WIDTH, new int[][] {{driveEast(CLEARS_DEADZONE_RAW)}}, 0);
        double initialCredit = DEFAULTS.founder().initialEnergy()
                + DEFAULTS.chemicals().get(0).initial() * TORUS_WIDTH * TORUS_WIDTH
                + DEFAULTS.founder().initialMass();

        // act
        kernel.tick();

        // assert
        assertEquals(0, kernel.snapshot().position[0], "no step before the drive propagates");
        for (int expected : new int[] {1, 2, 3, 0, 1}) {
            // act
            kernel.tick();

            // assert
            KernelSnapshot snapshot = kernel.snapshot();
            assertEquals(expected, snapshot.position[0], "east step at tick " + snapshot.tick);
            EnergyAudit.assertConserved(snapshot, initialCredit, 1.0e-6);
        }
    }

    @Test
    void weakDriveInsideDeadzoneDoesNotMove() {
        // arrange
        assertTrue(SUB_DEADZONE_RAW * GeneDecoder.WEIGHT_PER_RAW_UNIT < DEFAULTS.motility().deadzone(),
                "test precondition: drive must be inside the deadzone");
        Kernel kernel = Worlds.kernelAt(TORUS_WIDTH, new int[][] {{driveEast(SUB_DEADZONE_RAW)}}, 0);

        // act
        kernel.step(10);

        // assert
        assertEquals(0, kernel.snapshot().position[0], "a sub-deadzone drive never steps");
    }

    @Test
    void stepIntoAnOccupiedCellIsBlocked() {
        // arrange
        Kernel kernel = Worlds.kernelAt(TORUS_WIDTH, new int[][] {{driveEast(CLEARS_DEADZONE_RAW)}, {}}, 0, 1);

        // act
        kernel.step(4);

        // assert
        KernelSnapshot snapshot = kernel.snapshot();
        assertEquals(0, snapshot.position[0], "mover blocked by the occupant of cell 1");
        assertEquals(1, snapshot.position[1], "blocker stayed put");
    }

    @Test
    void contestedCellGoesToLowestSlot() {
        // arrange
        Kernel kernel = Worlds.kernelAt(TORUS_WIDTH,
                new int[][] {{driveEast(CLEARS_DEADZONE_RAW)}, {driveWest(CLEARS_DEADZONE_RAW)}}, 0, 2);
        kernel.tick();
        double moveCost = DEFAULTS.motility().fixedCost()
                + DEFAULTS.motility().costPerMass() * kernel.snapshot().mass[0];

        // act
        kernel.tick();

        // assert
        KernelSnapshot snapshot = kernel.snapshot();
        assertEquals(1, snapshot.position[0], "slot 0 wins the contested cell");
        assertEquals(2, snapshot.position[1], "slot 1 loses and stays");
        assertEquals(snapshot.energy[1] - moveCost, snapshot.energy[0], 1.0e-9, "only the winner pays the move cost");
    }

    @Test
    void vacatedCellCannotBeEnteredSameTick() {
        // arrange
        Kernel kernel = Worlds.kernelAt(TORUS_WIDTH,
                new int[][] {{driveEast(CLEARS_DEADZONE_RAW)}, {driveEast(CLEARS_DEADZONE_RAW)}}, 1, 0);
        kernel.tick();

        // act
        kernel.tick();

        // assert
        assertEquals(2, kernel.snapshot().position[0], "slot 0 stepped out");
        assertEquals(0, kernel.snapshot().position[1], "slot 1 cannot enter a cell vacated this tick");

        // act
        kernel.tick();

        // assert
        assertEquals(1, kernel.snapshot().position[1], "slot 1 enters on the following tick");
    }
}
