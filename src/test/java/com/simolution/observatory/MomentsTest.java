package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.support.MetricsCsv;

class MomentsTest {

    private static List<Moments.Moment> marked(final MetricsCsv csv, final String mark, final int targetTicks,
            final int founders) throws IOException {
        return Moments.detect(csv.table(), targetTicks, founders).stream().filter(m -> m.mark().equals(mark))
                .toList();
    }

    private static List<Integer> ticks(final List<Moments.Moment> moments) {
        return moments.stream().map(Moments.Moment::tick).toList();
    }

    @Test
    void peakIsTheFirstMaximum() throws IOException {
        assertEquals(List.of(2), ticks(marked(new MetricsCsv().populations(10, 50, 40, 50), "P", 100, 2)));
    }

    @Test
    void crashIsAThirtyPercentFallWithinFivePercentOfTheRun() throws IOException {
        // arrange
        final MetricsCsv csv = new MetricsCsv().repeat(200, 100).repeat(10, 70);

        // act
        final List<Moments.Moment> crashes = marked(csv, "C", 1000, 2);

        // assert
        assertEquals(List.of(201), ticks(crashes));
    }

    @Test
    void twentyNinePercentFallIsNoCrash() throws IOException {
        assertEquals(List.of(), marked(new MetricsCsv().repeat(200, 100).repeat(10, 71), "C", 1000, 2));
    }

    @Test
    void slowFallOverMoreThanFivePercentIsNoCrash() throws IOException {
        // arrange
        final MetricsCsv csv = new MetricsCsv().repeat(100, 100);
        for (int population = 99; population >= 60; population--) {
            csv.repeat(2, population);
        }

        // act
        final List<Moments.Moment> crashes = marked(csv, "C", 1000, 2);

        // assert
        assertEquals(List.of(), crashes);
    }

    @Test
    void smallPopulationNeverCrashes() throws IOException {
        assertEquals(List.of(), marked(new MetricsCsv().repeat(100, 29).repeat(10, 5), "C", 1000, 2));
    }

    @Test
    void crashesAreAtLeastTenPercentOfTheRunApart() throws IOException {
        // arrange
        final MetricsCsv csv = new MetricsCsv().repeat(100, 100).repeat(20, 60).repeat(20, 100).repeat(20, 60)
                .repeat(100, 100).repeat(20, 60);

        // act
        final List<Moments.Moment> crashes = marked(csv, "C", 1000, 2);

        // assert
        assertEquals(List.of(101, 261), ticks(crashes));
    }

    @Test
    void takeoverIsTheFirstTickOneLineageHoldsOverHalf() throws IOException {
        // arrange
        final MetricsCsv csv = new MetricsCsv().row(40, 3, 0.5, 0.0).row(40, 3, 0.6, 0.0).row(40, 3, 0.7, 0.0);

        // act
        final List<Moments.Moment> takeovers = marked(csv, "T", 100, 2);

        // assert
        assertEquals(List.of(2), ticks(takeovers));
    }

    @Test
    void takeoverNeedsThirtyUnitsAndTwoFounders() throws IOException {
        assertEquals(List.of(), marked(new MetricsCsv().row(29, 1, 0.9, 0.0), "T", 100, 2));
        assertEquals(List.of(), marked(new MetricsCsv().row(40, 1, 1.0, 0.0), "T", 100, 1));
    }

    @Test
    void extinctionIsTheTickThePopulationReachesZero() throws IOException {
        assertEquals(List.of(3), ticks(marked(new MetricsCsv().populations(5, 2, 0), "X", 100, 2)));
    }
}
