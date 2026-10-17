package com.simolution.kernel.genome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class GeneLayoutTest {

    @Test
    void packThenReadReturnsEveryField() {
        // arrange
        final Random random = new Random(7);

        for (int i = 0; i < 10_000; i++) {
            final boolean sourceInternal = random.nextBoolean();
            final int sourceId = random.nextInt();
            final boolean destinationAction = random.nextBoolean();
            final int destinationId = random.nextInt();
            final short weightRaw = (short) random.nextInt();

            // act
            final int gene = GeneBuilder.pack(sourceInternal, sourceId, destinationAction, destinationId, weightRaw);

            // assert
            assertEquals(sourceInternal, GeneLayout.sourceIsInternal(gene));
            assertEquals(sourceId & GeneLayout.ID_MASK, GeneLayout.sourceId(gene));
            assertEquals(destinationAction, GeneLayout.destinationIsAction(gene));
            assertEquals(destinationId & GeneLayout.ID_MASK, GeneLayout.destinationId(gene));
            assertEquals(weightRaw, GeneLayout.weightRaw(gene));
        }
    }

    @Test
    void minRawWeightDecodesJustBeyondMinusFour() {
        // arrange
        final int gene = GeneBuilder.pack(false, 0, false, 0, Short.MIN_VALUE);

        // act
        final double weight = GeneDecoder.weight(gene);

        // assert
        assertEquals(-32768 * GeneDecoder.WEIGHT_PER_RAW_UNIT, weight, 0.0);
        assertTrue(weight < -4.0);
    }
}
