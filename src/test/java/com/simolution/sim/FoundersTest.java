package com.simolution.sim;

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
}
