package com.simolution.sim;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Noise;

/**
 * Seeded random genomes. Stateless like the RAND sensor: gene (u, i) is a
 * pure hash of (seed, u, i), so genomes never depend on generation order
 * or population size.
 * <p>
 * The seed is salted before hashing so a run's genomes and its RAND noise
 * are decorrelated even though both derive from the same run seed.
 */
public final class GenomeFactory {

    private static final long GENOME_SALT = 0x47454E4F4D4521L;

    private GenomeFactory() {}

    public static int[][] random(long seed, int units, int genesPerUnit) {
        long genomeSeed = seed ^ GENOME_SALT;
        int[][] genomes = new int[units][genesPerUnit];

        for (int unit = 0; unit < units; unit++) {
            for (int gene = 0; gene < genesPerUnit; gene++) {
                long h = Noise.mix(genomeSeed + Noise.GOLDEN_GAMMA * (unit + 1L));
                h = Noise.mix(h + Noise.GOLDEN_GAMMA * (gene + 1L));
                genomes[unit][gene] = (int) (h ^ (h >>> 32));
            }
        }
        return genomes;
    }

    public static int[] demo() {
        short constToAdd = 2048;
        short addToDelay = 512;
        short delayToAdd = 512;
        short addToAction = 512;

        return new int[] {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(constToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(addToDelay)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(delayToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw(addToAction)
                           .build()
        };
    }
}
