package com.simolution.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class StrictMathLintTest {

    private static final Pattern NON_STRICT =
            Pattern.compile("(?<![A-Za-z])Math\\.(pow|exp|log|sin|cos|tan|fma)\\(");

    @Test
    void kernelUsesNoNonStrictTranscendentals() throws IOException {
        // arrange
        final Path root = Path.of("src/main/java/com/simolution/kernel");

        // act
        final List<String> violations;
        try (Stream<Path> files = Files.walk(root)) {
            violations = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(StrictMathLintTest::violationsIn)
                    .toList();
        }

        // assert
        assertEquals(List.of(), violations);
    }

    private static Stream<String> violationsIn(final Path file) {
        try {
            final List<String> lines = Files.readAllLines(file);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(i -> NON_STRICT.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1) + ": " + lines.get(i).strip());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
