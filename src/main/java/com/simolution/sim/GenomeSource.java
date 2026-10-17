package com.simolution.sim;

public sealed interface GenomeSource permits GenomeSource.Demo, GenomeSource.Random {

    int genesPerFounder();

    record Demo() implements GenomeSource {

        @Override
        public int genesPerFounder() {
            return GenomeFactory.demo().length;
        }
    }

    record Random(int genesPerFounder) implements GenomeSource {
    }
}
