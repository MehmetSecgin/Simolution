package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;

class WorldStateScalarsTest {

    private static final Set<String> SCRATCH_SCALARS = Set.of("birthCount");

    private static Kernel newKernel() {
        return new Kernel(new int[0][], new int[0], EngineConfig.defaults(3L, 4, 4), 1);
    }

    private static List<Field> scalarFields() {
        final List<Field> scalars = new ArrayList<>();
        for (final Field field : WorldState.class.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && !field.getType().isArray()) {
                scalars.add(field);
            }
        }
        return scalars;
    }

    private static void poke(final Field field, final WorldState state) throws IllegalAccessException {
        if (field.getType() == int.class) {
            field.setInt(state, 12345);
        } else if (field.getType() == long.class) {
            field.setLong(state, 12345L);
        } else if (field.getType() == double.class) {
            field.setDouble(state, 12345.5);
        } else {
            throw new IllegalStateException("unhandled scalar type " + field.getType() + " of " + field.getName());
        }
    }

    @Test
    void everyPersistentScalarReachesTheDigestAndTheCheckpoint() throws Exception {
        for (final Field field : scalarFields()) {
            if (SCRATCH_SCALARS.contains(field.getName())) {
                continue;
            }
            // arrange
            final Kernel kernel = newKernel();
            final long before = kernel.stateDigest();

            // act
            poke(field, kernel.state());
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CheckpointCodec.write(kernel, bytes);
            final Kernel restored = CheckpointCodec.read(new ByteArrayInputStream(bytes.toByteArray()), 1);

            // assert
            assertNotEquals(before, kernel.stateDigest(), field.getName() + " is missing from StateDigest");
            assertEquals(field.get(kernel.state()), field.get(restored.state()),
                    field.getName() + " is missing from CheckpointCodec");
            kernel.close();
            restored.close();
        }
    }

    @Test
    void scratchScalarsStayOutOfTheDigest() throws Exception {
        for (final Field field : scalarFields()) {
            if (!SCRATCH_SCALARS.contains(field.getName())) {
                continue;
            }
            // arrange
            final Kernel kernel = newKernel();
            final long before = kernel.stateDigest();

            // act
            poke(field, kernel.state());

            // assert
            assertEquals(before, kernel.stateDigest(), field.getName() + " is scratch and must not be digested");
            kernel.close();
        }
    }
}
