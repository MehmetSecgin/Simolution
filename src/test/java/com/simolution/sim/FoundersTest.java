package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoundersTest {

    @Test
    void maxGenesWidensToTheLongestFounder() {
        // arrange
        RunConfig config = RunConfig.parse(new String[] {"--units", "2", "--max-genes", "8", "--genes", "24"});

        // act
        Founders founders = Founders.create(config);

        // assert
        assertEquals(24, founders.engine().world().maxGenes());
    }

    @Test
    void demoHasOneFeedbackGenome() {
        // arrange + act
        Founders founders = Founders.create(RunConfig.parse(new String[0]));

        // assert
        assertEquals(1, founders.genomes().length);
        assertEquals(4, founders.genomes()[0].length);
        assertArrayEquals(GenomeFactory.demo(), founders.genomes()[0]);
    }
}
