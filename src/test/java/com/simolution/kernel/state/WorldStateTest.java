package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.FieldSpec.Dim;
import com.simolution.kernel.state.FieldSpec.OnBirth;
import com.simolution.kernel.state.FieldSpec.OnDeath;
import com.simolution.kernel.state.FieldSpec.Persist;

class WorldStateTest {

    private static WorldState newState() {
        return new WorldState(EngineConfig.defaults(1L, 4, 3));
    }

    @Test
    void fieldsCoverEveryArray() throws IllegalAccessException {
        final WorldState state = newState();
        final List<FieldSpec> specs = state.fields();
        for (final Field field : WorldState.class.getDeclaredFields()) {
            if (!field.getType().isArray()) {
                continue;
            }
            field.setAccessible(true);
            final Object array = field.get(state);
            final long matches = specs.stream().filter(spec -> spec.array() == array).count();
            assertEquals(1, matches, "array field " + field.getName() + " must be declared exactly once");
        }
        assertEquals(specs.size(), specs.stream().map(FieldSpec::name).distinct().count());
    }

    @Test
    void savedFieldsHaveStableOrder() {
        final List<String> saved = new ArrayList<>();
        for (final FieldSpec spec : newState().fields()) {
            if (spec.persist() == Persist.SAVED) {
                saved.add(spec.name());
            }
        }
        assertEquals(List.of("genes", "geneCount", "energy", "mass", "damage", "lineageId", "generation",
                "position", "cellChem", "cellOccupant", "outputsPrev", "delayMemory", "unitChem"), saved);
    }

    @Test
    void resetForBirthZeroesOnlyThatSlot() {
        final WorldState state = newState();
        final int node = 2 * NodeLayout.TOTAL;
        state.outputsPrev[node] = 1f;
        state.outputsNext[node + 1] = 1f;
        state.delayMemory[node + 2] = 1f;
        state.activeThisTick[2] = 5;
        state.damage[2] = 3.0;
        state.outputsPrev[3 * NodeLayout.TOTAL] = 1f;
        state.energy[2] = 7.0;

        state.resetForBirth(2);

        assertEquals(0f, state.outputsPrev[node]);
        assertEquals(0f, state.outputsNext[node + 1]);
        assertEquals(0f, state.delayMemory[node + 2]);
        assertEquals(0, state.activeThisTick[2]);
        assertEquals(0.0, state.damage[2]);
        assertEquals(1f, state.outputsPrev[3 * NodeLayout.TOTAL]);
        assertEquals(7.0, state.energy[2]);
    }

    @Test
    void applyDeathZeroesMassOnly() {
        final WorldState state = newState();
        state.mass[1] = 4.0;
        state.energy[1] = 2.0;
        state.applyDeath(1);
        assertEquals(0.0, state.mass[1]);
        assertEquals(2.0, state.energy[1]);
    }

    @Test
    void swapsExchangeBuffersAndFieldsTrackThem() {
        final WorldState state = newState();
        final float[] prev = state.outputsPrev;
        final double[] chem = state.cellChem;
        state.swapOutputs();
        state.swapChem();
        assertSame(prev, state.outputsNext);
        assertSame(chem, state.cellChemNext);
        final FieldSpec outputs = state.fields().stream().filter(s -> s.name().equals("outputsPrev")).findFirst().orElseThrow();
        assertSame(state.outputsPrev, outputs.array());
        assertTrue(state.cellChem != chem);
    }

    @Test
    void zeroedFieldWithoutPerSlotExtentIsRejected() {
        // arrange
        final FieldSpec misdeclared = new FieldSpec("cellThing", Dim.CELL, Persist.SAVED, OnBirth.RESET_ZERO,
                OnDeath.KEEP, new int[4]);

        // assert
        assertThrows(IllegalStateException.class, () -> WorldState.zeroStride(misdeclared));
    }
}
