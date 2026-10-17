package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.config.InflowSource;

class InflowSpecParserTest {

    @Test
    void parsesComposedSourcesCombineAndBaseline() {
        InflowConfig cfg = InflowSpecParser.parse(
                "disk fx=0.5 fy=0.5 r=22 peak=10 period=2000; "
                        + "rect fx0=0 fy0=0 fx1=0.3 fy1=1 peak=5; "
                        + "points cells=100,250,4000 peak=8; "
                        + "noise seed=42 freq=0.05 thr=0.6 gain=3 peak=9 anim=8000; "
                        + "combine=add baseline=0.2");

        assertEquals(InflowConfig.Combine.ADD, cfg.combine());
        assertEquals(0.2, cfg.baseline(), 0.0);
        assertEquals(4, cfg.sources().size());

        InflowSource.Disk disk = assertInstanceOf(InflowSource.Disk.class, cfg.sources().get(0));
        assertEquals(22.0, disk.radius(), 0.0);
        assertEquals(2000, disk.periodTicks());

        InflowSource.Rect rect = assertInstanceOf(InflowSource.Rect.class, cfg.sources().get(1));
        assertEquals(0.3, rect.fx1(), 0.0);

        InflowSource.Points pts = assertInstanceOf(InflowSource.Points.class, cfg.sources().get(2));
        assertEquals(3, pts.cells().length);
        assertEquals(250, pts.cells()[1]);

        InflowSource.Noise noise = assertInstanceOf(InflowSource.Noise.class, cfg.sources().get(3));
        assertEquals(42L, noise.seed());
        assertEquals(8000, noise.animPeriod());
    }

    @Test
    void parsesDiskMotion() {
        InflowSource.Disk orbit = assertInstanceOf(InflowSource.Disk.class,
                InflowSpecParser.parse("disk orbit=0.15,4000").sources().get(0));
        assertEquals(InflowSource.Motion.ORBIT, orbit.motion());
        assertEquals(0.15, orbit.m0(), 0.0);
        assertEquals(4000, orbit.motionPeriod());

        InflowSource.Disk drift = assertInstanceOf(InflowSource.Disk.class,
                InflowSpecParser.parse("disk drift=0.01,-0.02").sources().get(0));
        assertEquals(InflowSource.Motion.DRIFT, drift.motion());
        assertEquals(-0.02, drift.m1(), 0.0);
    }

    @Test
    void noiseSourceWithoutSeedDefaultsToZero() {
        InflowSource.Noise noise = assertInstanceOf(InflowSource.Noise.class,
                InflowSpecParser.parse("noise peak=5").sources().get(0));
        assertEquals(0L, noise.seed());
    }

    @Test
    void lastRepeatedKeyWins() {
        // act
        InflowSource.Disk disk = assertInstanceOf(InflowSource.Disk.class,
                InflowSpecParser.parse("disk r=5 r=9").sources().get(0));

        // assert
        assertEquals(9.0, disk.radius(), 0.0);
    }

    @Test
    void aliasesShareOneLastWinsSlot() {
        // act
        InflowSource.Rect rect = assertInstanceOf(InflowSource.Rect.class,
                InflowSpecParser.parse("rect fx0=0.2 fx=0.4 fy=0.1 fy0=0.3").sources().get(0));

        // assert
        assertEquals(0.4, rect.fx0(), 0.0);
        assertEquals(0.3, rect.fy0(), 0.0);
    }

    @Test
    void motionKeysOverwriteOnlyTheComponentsTheySet() {
        // act
        InflowSource.Disk disk = assertInstanceOf(InflowSource.Disk.class,
                InflowSpecParser.parse("disk drift=0.01,-0.02 orbit=0.15,4000").sources().get(0));

        // assert
        assertEquals(InflowSource.Motion.ORBIT, disk.motion());
        assertEquals(0.15, disk.m0(), 0.0);
        assertEquals(-0.02, disk.m1(), 0.0);
        assertEquals(4000, disk.motionPeriod());
    }

    @Test
    void unknownKeyIsRejectedByName() {
        // act
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> InflowSpecParser.parse("disk wat=1"));

        // assert
        assertEquals("unknown --inflow key: wat", e.getMessage());
    }

    @Test
    void badValueFailsBeforeALaterUnknownKey() {
        // assert
        assertThrows(NumberFormatException.class, () -> InflowSpecParser.parse("disk r=abc wat=1"));
    }
}
