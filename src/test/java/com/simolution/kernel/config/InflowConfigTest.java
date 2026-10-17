package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.support.EnergyAudit;
import com.simolution.support.Worlds;

class InflowConfigTest {

    @Test
    void uniformIsConstantCellInflowEverywhere() {
        CompiledInflowField f = InflowConfig.uniform(1.0).compile(100);
        assertEquals(1.0, f.cap(0, 0), 0.0);
        assertEquals(1.0, f.cap(5050, 999), 0.0);
    }

    @Test
    void cyclicPulsesCentreAndStarvesPeriphery() {
        int w = 100, period = 2000, radius = 20;
        double peak = 6.0;
        CompiledInflowField c = InflowConfig.cyclic(period, radius, peak).compile(w);
        int centre = (w / 2) * w + (w / 2);

        assertEquals(0.0, c.cap(centre, 0), 1.0e-9, "centre at trough");
        assertEquals(peak, c.cap(centre, period / 2), 1.0e-9, "centre at peak");
        assertEquals(peak / 2.0, c.cap(centre, period / 4), 1.0e-9, "centre mid-rise");

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
                    Math.max(disk.capAt(x, y, 0, w), noise.capAt(x, y, 0, w)));
            assertEquals(naive, compiled.cap(cell, 0), 0.0, "cell " + cell);
        }
    }

    @Test
    void combineMaxVsAddOverlappingSources() {
        int w = 20;
        InflowSource a = new InflowSource.Rect(0, 0, 1, 1, 3.0, 0, 0, 0, 0);
        InflowSource b = new InflowSource.Rect(0, 0, 1, 1, 4.0, 0, 0, 0, 0);
        assertEquals(4.0, InflowConfig.field(0, InflowConfig.Combine.MAX, a, b).compile(w).cap(0, 0), 1e-9);
        assertEquals(7.0, InflowConfig.field(0, InflowConfig.Combine.ADD, a, b).compile(w).cap(0, 0), 1e-9);
    }

    @Test
    void pointsHitExactCellsOnly() {
        int w = 30;
        InflowSource pts = new InflowSource.Points(new int[] {10, 250, 800}, 9.0, 0, 0);
        CompiledInflowField f = InflowConfig.field(0, InflowConfig.Combine.MAX, pts).compile(w);
        assertEquals(9.0, f.cap(250, 0), 1e-9);
        assertEquals(0.0, f.cap(251, 0), 1e-9);
    }

    @Test
    void driftMovesTheDiskCentreOverTime() {
        int w = 100;
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
        InflowSource band = new InflowSource.Rect(0.0, 0.0, 0.1, 1.0, 7.0, 0, 0, 1.0, 0.0);
        CompiledInflowField f = InflowConfig.field(0, InflowConfig.Combine.MAX, band).compile(w);
        assertTrue(f.cap(0, 0) > 0.0 && f.cap(50 * w + 0, 0) > 0.0, "full-height band at tick 0, x=0");
        assertEquals(0.0, f.cap(50, 0), 1e-9, "x=50 not yet reached at tick 0");
        assertTrue(f.cap(50 * w + 50, 50) > 0.0, "band reached x=50 at tick 50");
        assertEquals(0.0, f.cap(50 * w + 0, 50), 1e-9, "band left x=0 by tick 50");
        assertTrue(f.cap(50 * w + 0, 100) > 0.0, "band wrapped back to x=0 at tick 100");
    }

    @Test
    void ringWaveExpandsOutwardFromCentre() {
        int w = 120;
        InflowSource.Ring ring = new InflowSource.Ring(0.5, 0.5, 40.0, 1.0, 10.0, 4.0);
        int cx = 60, cy = 60;
        assertTrue(ring.capAt(cx, cy, 0, w) > 0.0, "crest at centre at tick 0");
        assertEquals(0.0, ring.capAt(cx + 20, cy, 0, w), 1e-9, "trough 20 cells out at tick 0");
        assertTrue(ring.capAt(cx + 20, cy, 20, w) > 0.0, "crest reached r=20 at tick 20");
        assertEquals(ring.capAt(cx + 20, cy, 20, w), ring.capAt(cx, cy + 20, 20, w), 1e-9, "radially symmetric");
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
            if (low.capAt(x, y, 0, w) > 0) litLow++;
            if (high.capAt(x, y, 0, w) > 0) litHigh++;
        }
        assertTrue(litLow > litHigh, "lower threshold lights more cells");

        InflowSource.Noise stat = new InflowSource.Noise(5L, 0.08, 0.3, 1.0, 4.0, 0);
        assertEquals(stat.capAt(3, 4, 0, w), stat.capAt(3, 4, 9999, w), 0.0, "anim=0 frozen");
        InflowSource.Noise anim = new InflowSource.Noise(5L, 0.08, 0.3, 1.0, 4.0, 8000);
        assertNotEquals(anim.capAt(3, 4, 0, w), anim.capAt(3, 4, 4000, w), "anim>0 morphs with tick");
    }

    @Test
    void cyclicRunConservesEnergy() {
        int w = 60, founders = 100;
        Kernel k = Worlds.randomKernel(3L, founders, 8,
                EngineConfig.defaults(0L, w, 16).withResourceInflow(InflowConfig.cyclic(400, 10, 8.0)));
        assertAuditHolds(k);
    }

    @Test
    void noiseFieldRunConservesEnergy() {
        int w = 60, founders = 100;
        Kernel k = Worlds.randomKernel(3L, founders, 8,
                EngineConfig.defaults(0L, w, 16).withResourceInflow(InflowConfig.field(0.0, InflowConfig.Combine.MAX,
                        new InflowSource.Noise(11L, 0.06, 0.5, 3.0, 8.0, 5000),
                        new InflowSource.Disk(0.7, 0.3, 6, 5.0, 1000, 0,
                                InflowSource.Motion.DRIFT, 0.01, 0.0, 0))));
        assertAuditHolds(k);
    }

    @Test
    void capValuesAreBitIdenticalToBefore() throws IOException {
        // arrange
        int w = 40;
        List<String> expected = readResourceLines("/inflow-caps-pre-readability.txt");
        List<String> actual = new ArrayList<>();
        Random random = new Random(5);
        int[][] triples = new int[64][];
        for (int i = 0; i < triples.length; i++) {
            triples[i] = new int[] {random.nextInt(w), random.nextInt(w), random.nextInt(5000)};
        }

        // act
        for (Map.Entry<String, InflowSource> source : capCaseSources().entrySet()) {
            for (int[] t : triples) {
                actual.add(source.getKey() + " " + t[0] + " " + t[1] + " " + t[2] + " "
                        + Double.toHexString(source.getValue().capAt(t[0], t[1], t[2], w)));
            }
        }

        // assert
        assertEquals(expected, actual);
    }

    @Test
    void jsonRoundTripIsUnchangedForEverySourceKind() {
        // arrange
        InflowConfig inflow = InflowConfig.field(0.2, InflowConfig.Combine.ADD,
                capCaseSources().values().toArray(new InflowSource[0]));
        EngineConfig config = EngineConfig.defaults(1L, 40, 16).withResourceInflow(inflow);

        // act
        String json = ConfigJson.write(config);

        // assert
        assertEquals(config, ConfigJson.read(json));
        assertEquals(inflow.sources(), ConfigJson.read(json).chemicals().get(0).inflow().sources());
        assertTrue(json.contains("\"m0\"") && json.contains("\"m1\"") && json.contains("\"motionPeriod\""));
        assertFalse(json.contains("orbitRadiusFraction") || json.contains("driftX") || json.contains("driftY"));
    }

    private static List<String> readResourceLines(String name) throws IOException {
        try (InputStream in = InflowConfigTest.class.getResourceAsStream(name)) {
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();
        }
    }

    private static Map<String, InflowSource> capCaseSources() {
        int[] cells = new int[229];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i * 7;
        }
        Map<String, InflowSource> sources = new LinkedHashMap<>();
        sources.put("diskStatic",
                new InflowSource.Disk(0.3, 0.6, 9.0, 5.0, 0, 0, InflowSource.Motion.STATIC, 0.0, 0.0, 0));
        sources.put("diskOrbit",
                new InflowSource.Disk(0.5, 0.5, 7.0, 6.0, 300, 17, InflowSource.Motion.ORBIT, 0.25, 0.0, 500));
        sources.put("diskDrift",
                new InflowSource.Disk(0.9, 0.1, 8.0, 4.0, 150, -30, InflowSource.Motion.DRIFT, 0.03, -0.02, 0));
        sources.put("rectStatic", new InflowSource.Rect(0.2, 0.1, 0.7, 0.8, 3.0, 0, 0, 0.0, 0.0));
        sources.put("rectDrifting", new InflowSource.Rect(0.1, 0.0, 0.3, 1.0, 5.0, 200, 11, 0.05, -0.01));
        sources.put("points", new InflowSource.Points(cells, 4.5, 90, 5));
        sources.put("noiseAnimated", new InflowSource.Noise(11L, 0.08, 0.4, 2.5, 6.0, 700));
        sources.put("ring", new InflowSource.Ring(0.4, 0.6, 12.0, 0.05, 6.0, 4.0));
        return sources;
    }

    private static void assertAuditHolds(Kernel k) {
        double maxInflowStep = 0.0, prevInflow = 0.0;
        for (int t = 0; t < 1500; t++) {
            k.tick();
            KernelSnapshot s = k.snapshot();
            EnergyAudit.assertConserved(s, 1.0e-2);
            maxInflowStep = Math.max(maxInflowStep, s.cumulativeInflow - prevInflow);
            prevInflow = s.cumulativeInflow;
        }
        assertTrue(maxInflowStep > 0.0, "inflow must admit resource during the run");
    }
}
