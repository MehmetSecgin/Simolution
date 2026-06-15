package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.GenomeFactory;

/**
 * Conformance for contract-v7 programmable inflow field: uniform is unchanged, the
 * cyclic factory still reproduces the v6 central pulsing disk, sources compose via
 * MAX/ADD over a baseline, value noise is deterministic and threshold-controlled,
 * the static bake is faithful, and the closed-system audit still holds (only
 * inflow's distribution changes).
 */
class InflowConfigTest {

    @Test
    void uniformIsConstantCellInflowEverywhere() {
        CompiledInflowField f = InflowConfig.UNIFORM.compile(100);
        assertEquals(KernelConfig.CELL_INFLOW, f.cap(0, 0), 0.0);
        assertEquals(KernelConfig.CELL_INFLOW, f.cap(5050, 999), 0.0);
    }

    @Test
    void cyclicPulsesCentreAndStarvesPeriphery() {
        int w = 100, period = 2000, radius = 20;
        double peak = 6.0;
        CompiledInflowField c = InflowConfig.cyclic(period, radius, peak).compile(w);
        int centre = (w / 2) * w + (w / 2);

        // triangle: trough at tick 0, peak at period/2, back to trough at period
        assertEquals(0.0, c.cap(centre, 0), 1.0e-9, "centre at trough");
        assertEquals(peak, c.cap(centre, period / 2), 1.0e-9, "centre at peak");
        assertEquals(peak / 2.0, c.cap(centre, period / 4), 1.0e-9, "centre mid-rise");

        // periphery is barren at every phase
        assertEquals(0.0, c.cap(0, period / 2), 0.0, "corner barren");
        assertEquals(0.0, c.cap(w * w - 1, period / 2), 0.0, "far corner barren");
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThrows(IllegalArgumentException.class, () -> InflowConfig.cyclic(0, 10, 1.0));
    }

    @Test
    void bakeMatchesNaivePerCellEvaluation() {
        int w = 40;
        InflowSource disk = new InflowSource.Disk(
                0.3, 0.3, 8, 5.0, 0, 0, InflowSource.Motion.STATIC, 0, 0, 0);
        InflowSource noise = new InflowSource.Noise(7L, 0.1, 0.5, 2.0, 4.0, 0);
        InflowConfig cfg = InflowConfig.field(0.5, InflowConfig.Combine.MAX, disk, noise);
        CompiledInflowField compiled = cfg.compile(w);

        for (int cell = 0; cell < w * w; cell++) {
            int x = cell % w, y = cell / w;
            double naive = Math.max(0.5,
                    Math.max(disk.at(x, y, 0, w), noise.at(x, y, 0, w)));
            assertEquals(naive, compiled.cap(cell, 0), 0.0, "cell " + cell);
        }
    }

    @Test
    void combineMaxVsAddOverlappingSources() {
        int w = 20;
        // two overlapping full-grid rects (whole world), peaks 3 and 4
        InflowSource a = new InflowSource.Rect(0, 0, 1, 1, 3.0, 0, 0, 0, 0);
        InflowSource b = new InflowSource.Rect(0, 0, 1, 1, 4.0, 0, 0, 0, 0);
        assertEquals(4.0, InflowConfig.field(0, InflowConfig.Combine.MAX, a, b).compile(w).cap(0, 0), 1e-9);
        assertEquals(7.0, InflowConfig.field(0, InflowConfig.Combine.ADD, a, b).compile(w).cap(0, 0), 1e-9);
    }

    @Test
    void pointsHitExactCellsOnly() {
        int w = 30;
        InflowSource pts = new InflowSource.Points(new int[] { 10, 250, 800 }, 9.0, 0, 0);
        CompiledInflowField f = InflowConfig.field(0, InflowConfig.Combine.MAX, pts).compile(w);
        assertEquals(9.0, f.cap(250, 0), 1e-9);
        assertEquals(0.0, f.cap(251, 0), 1e-9);
    }

    @Test
    void driftMovesTheDiskCentreOverTime() {
        int w = 100;
        // disk starts at (10,50), drifts +1 cell/tick in x; r small
        InflowSource drift = new InflowSource.Disk(
                0.1, 0.5, 2, 5.0, 0, 0, InflowSource.Motion.DRIFT, 1.0, 0.0, 0);
        CompiledInflowField f = InflowConfig.field(0, InflowConfig.Combine.MAX, drift).compile(w);
        int rowStart = 50 * w;
        assertTrue(f.cap(rowStart + 10, 0) > 0.0, "covers x=10 at tick 0");
        assertEquals(0.0, f.cap(rowStart + 10, 30), 1e-9, "left it by tick 30");
        assertTrue(f.cap(rowStart + 40, 30) > 0.0, "now over x=40 at tick 30");
    }

    @Test
    void verticalBandSweepsHorizontallyAndWraps() {
        int w = 100;
        // full-height band, 10 cells wide at the left edge, drifting +1 cell/tick in x
        InflowSource band = new InflowSource.Rect(0.0, 0.0, 0.1, 1.0, 7.0, 0, 0, 1.0, 0.0);
        CompiledInflowField f = InflowConfig.field(0, InflowConfig.Combine.MAX, band).compile(w);
        // full height: lit at any row in the band's x-range
        assertTrue(f.cap(0, 0) > 0.0 && f.cap(50 * w + 0, 0) > 0.0, "full-height band at tick 0, x=0");
        assertEquals(0.0, f.cap(50, 0), 1e-9, "x=50 not yet reached at tick 0");
        // by tick 50 the band has moved to x≈50
        assertTrue(f.cap(50 * w + 50, 50) > 0.0, "band reached x=50 at tick 50");
        assertEquals(0.0, f.cap(50 * w + 0, 50), 1e-9, "band left x=0 by tick 50");
        // wraps: after one full lap (tick 100 on W=100) it is back at the left edge
        assertTrue(f.cap(50 * w + 0, 100) > 0.0, "band wrapped back to x=0 at tick 100");
    }

    @Test
    void valueNoiseIsDeterministicAndSeedSensitive() {
        double a1 = ValueNoise.at(42L, 1.5, 2.5, 0.0);
        double a2 = ValueNoise.at(42L, 1.5, 2.5, 0.0);
        double b = ValueNoise.at(43L, 1.5, 2.5, 0.0);
        assertEquals(a1, a2, 0.0, "same seed+coords → identical");
        assertNotEquals(a1, b, "different seed → different");
        assertTrue(a1 >= 0.0 && a1 < 1.0, "in [0,1)");
    }

    @Test
    void noiseThresholdControlsCoverageAndAnimationMorphs() {
        int w = 60;
        InflowSource.Noise low = new InflowSource.Noise(5L, 0.08, 0.3, 1.0, 4.0, 0);
        InflowSource.Noise high = new InflowSource.Noise(5L, 0.08, 0.7, 1.0, 4.0, 0);
        int litLow = 0, litHigh = 0;
        for (int cell = 0; cell < w * w; cell++) {
            int x = cell % w, y = cell / w;
            if (low.at(x, y, 0, w) > 0) litLow++;
            if (high.at(x, y, 0, w) > 0) litHigh++;
        }
        assertTrue(litLow > litHigh, "lower threshold lights more cells");

        InflowSource.Noise stat = new InflowSource.Noise(5L, 0.08, 0.3, 1.0, 4.0, 0);
        assertEquals(stat.at(3, 4, 0, w), stat.at(3, 4, 9999, w), 0.0, "anim=0 frozen");
        InflowSource.Noise anim = new InflowSource.Noise(5L, 0.08, 0.3, 1.0, 4.0, 8000);
        assertNotEquals(anim.at(3, 4, 0, w), anim.at(3, 4, 4000, w), "anim>0 morphs with tick");
    }

    @Test
    void cyclicRunConservesEnergy() {
        int w = 60, founders = 100;
        Kernel k = new Kernel(GenomeFactory.random(3L, founders, 8), w, 16,
                Kernel.scatterFounders(founders, w));
        k.configureInflow(InflowConfig.cyclic(400, 10, 8.0));
        assertAuditHolds(k);
    }

    @Test
    void noiseFieldRunConservesEnergy() {
        int w = 60, founders = 100;
        Kernel k = new Kernel(GenomeFactory.random(3L, founders, 8), w, 16,
                Kernel.scatterFounders(founders, w));
        k.configureInflow(InflowConfig.field(0.0, InflowConfig.Combine.MAX,
                new InflowSource.Noise(11L, 0.06, 0.5, 3.0, 8.0, 5000),
                new InflowSource.Disk(0.7, 0.3, 6, 5.0, 1000, 0,
                        InflowSource.Motion.DRIFT, 0.01, 0.0, 0)));
        assertAuditHolds(k);
    }

    private static void assertAuditHolds(Kernel k) {
        double maxInflowStep = 0.0, prevInflow = 0.0;
        for (int t = 0; t < 1500; t++) {
            k.tick();
            KernelSnapshot s = k.snapshot();
            double held = 0.0;
            for (double e : s.energy) {
                held += e;
            }
            held += s.reservoir + s.massTotal + s.energySink;
            double credited = s.creditedInitialEnergy + s.initialResourceTotal
                    + s.creditedInitialMass + s.cumulativeInflow;
            assertEquals(credited, held, 1.0e-2, "audit at tick " + s.tick);
            maxInflowStep = Math.max(maxInflowStep, s.cumulativeInflow - prevInflow);
            prevInflow = s.cumulativeInflow;
        }
        assertTrue(maxInflowStep > 0.0, "inflow must admit resource during the run");
    }
}
