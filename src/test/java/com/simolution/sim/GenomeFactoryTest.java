package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GenomeFactoryTest {

    @Test
    void sameSeedProducesIdenticalGenomes() {
        // arrange + act
        int[][] first = GenomeFactory.random(42L, 10, 16);
        int[][] second = GenomeFactory.random(42L, 10, 16);

        // assert
        assertEquals(true, Arrays.deepEquals(first, second));
    }

    @Test
    void differentSeedsProduceDifferentGenomes() {
        // arrange + act
        int[][] first = GenomeFactory.random(1L, 4, 8);
        int[][] second = GenomeFactory.random(2L, 4, 8);

        // assert
        assertFalse(Arrays.deepEquals(first, second));
    }

    @Test
    void unitGenomesAreInvariantUnderPopulationSize() {
        // arrange + act
        int[][] small = GenomeFactory.random(7L, 2, 8);
        int[][] large = GenomeFactory.random(7L, 100, 8);

        // assert
        assertEquals(true, Arrays.equals(small[0], large[0]));
        assertEquals(true, Arrays.equals(small[1], large[1]));
    }
}
