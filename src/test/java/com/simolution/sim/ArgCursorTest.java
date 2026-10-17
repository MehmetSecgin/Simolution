package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgCursorTest {

    @Test
    void missingValueNamesTheFlag() {
        // act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RunConfig.parse(new String[] {"--units"}));

        // assert
        assertEquals("--units needs a value", e.getMessage());
    }
}
