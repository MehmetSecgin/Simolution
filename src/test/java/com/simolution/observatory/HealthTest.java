package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.observatory.Health.Check;
import com.simolution.observatory.Health.Level;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;
import com.simolution.support.MetricsCsv;

class HealthTest {

    private static final int CELLS = 100;
    private static final RunStatus FINISHED = new RunStatus(RunState.FINISHED, 3, "00", null);

    private static Check check(final String name, final RunStatus status, final MetricsCsv csv,
            final Twins.TwinResult twin, final boolean twinPending) throws IOException {
        final List<Check> checks = Health.of(status, CELLS, csv.table(), twin, twinPending);
        return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private static Level level(final String name, final MetricsCsv csv) throws IOException {
        return check(name, FINISHED, csv, null, false).level();
    }

    @Test
    void aliveIsOkWhileUnitsLive() throws IOException {
        assertEquals(Level.OK, level("Alive", new MetricsCsv().populations(10, 94)));
    }

    @Test
    void aliveWarnsWhenTheWorldIsFull() throws IOException {
        assertEquals(Level.WARN, level("Alive", new MetricsCsv().populations(10, 95)));
    }

    @Test
    void aliveIsBadWhenExtinct() throws IOException {
        // arrange
        final RunStatus extinct = new RunStatus(RunState.EXTINCT, 2, "00", null);

        // act
        final Check alive = check("Alive", extinct, new MetricsCsv().populations(10, 0), null, false);

        // assert
        assertEquals(Level.BAD, alive.level());
        assertEquals("Extinct at tick 2.", alive.text());
    }

    @Test
    void energyAuditGradesTheLargestErrorAgainstSystemEnergy() throws IOException {
        assertEquals(Level.OK, level("Energy audit", new MetricsCsv().row(10, 2, 0.0, 0.001)));
        assertEquals(Level.WARN, level("Energy audit", new MetricsCsv().row(10, 2, 0.0, 0.0011)));
        assertEquals(Level.WARN, level("Energy audit", new MetricsCsv().row(10, 2, 0.0, 1.0)));
        assertEquals(Level.BAD, level("Energy audit", new MetricsCsv().row(10, 2, 0.0, -1.1)));
    }

    @Test
    void oneLineageLeftIsAMonoculture() throws IOException {
        assertEquals(Level.OK, level("Lineages", new MetricsCsv().row(10, 2, 0.5, 0.0)));
        assertEquals(Level.WARN, level("Lineages", new MetricsCsv().row(10, 1, 1.0, 0.0)));
    }

    @Test
    void determinismFollowsTheTwin() throws IOException {
        // arrange
        final MetricsCsv csv = new MetricsCsv().populations(10);

        // act
        final Check matched = check("Determinism", FINISHED, csv, new Twins.TwinResult(true, 3, null), false);
        final Check differed = check("Determinism", FINISHED, csv, new Twins.TwinResult(false, 3, 200), false);
        final Check unchecked = check("Determinism", FINISHED, csv, null, false);
        final Check pending = check("Determinism", FINISHED, csv, null, true);

        // assert
        assertEquals(Level.OK, matched.level());
        assertEquals(Level.BAD, differed.level());
        assertEquals("A re-run differs from checkpoint tick 200: a determinism bug.", differed.text());
        assertEquals(Level.WARN, unchecked.level());
        assertEquals(Level.WARN, pending.level());
    }

    @Test
    void worstLevelSummarisesTheChecks() throws IOException {
        // arrange
        final List<Check> checks = Health.of(FINISHED, CELLS, new MetricsCsv().row(10, 1, 1.0, 0.0).table(),
                new Twins.TwinResult(true, 3, null), false);

        // act
        final Level worst = Health.worst(checks);

        // assert
        assertEquals(Level.WARN, worst);
    }
}
