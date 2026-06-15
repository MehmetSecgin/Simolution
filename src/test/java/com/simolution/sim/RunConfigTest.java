package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.config.InflowSource;

/** Conformance for the contract-v7 {@code --inflow} field DSL parsing in {@link RunConfig}. */
class RunConfigTest {

    @Test
    void parsesComposedSourcesCombineAndBaseline() {
        InflowConfig cfg = RunConfig.parseInflowSpec(
                "disk fx=0.5 fy=0.5 r=22 peak=10 period=2000; "
                + "rect fx0=0 fy0=0 fx1=0.3 fy1=1 peak=5; "
                + "points cells=100,250,4000 peak=8; "
                + "noise seed=42 freq=0.05 thr=0.6 gain=3 peak=9 anim=8000; "
                + "combine=add baseline=0.2");

        assertEquals(InflowConfig.Combine.ADD, cfg.combine());
        assertEquals(0.2, cfg.baseline(), 0.0);
        assertEquals(4, cfg.sources().length);

        InflowSource.Disk disk = assertInstanceOf(InflowSource.Disk.class, cfg.sources()[0]);
        assertEquals(22.0, disk.radius(), 0.0);
        assertEquals(2000, disk.periodTicks());

        InflowSource.Rect rect = assertInstanceOf(InflowSource.Rect.class, cfg.sources()[1]);
        assertEquals(0.3, rect.fx1(), 0.0);

        InflowSource.Points pts = assertInstanceOf(InflowSource.Points.class, cfg.sources()[2]);
        assertEquals(3, pts.cells().length);
        assertEquals(250, pts.cells()[1]);

        InflowSource.Noise noise = assertInstanceOf(InflowSource.Noise.class, cfg.sources()[3]);
        assertEquals(42L, noise.seed());
        assertEquals(8000, noise.animPeriod());
    }

    @Test
    void parsesDiskMotion() {
        InflowSource.Disk orbit = assertInstanceOf(InflowSource.Disk.class,
                RunConfig.parseInflowSpec("disk orbit=0.15,4000").sources()[0]);
        assertEquals(InflowSource.Motion.ORBIT, orbit.motion());
        assertEquals(0.15, orbit.m0(), 0.0);
        assertEquals(4000, orbit.motionPeriod());

        InflowSource.Disk drift = assertInstanceOf(InflowSource.Disk.class,
                RunConfig.parseInflowSpec("disk drift=0.01,-0.02").sources()[0]);
        assertEquals(InflowSource.Motion.DRIFT, drift.motion());
        assertEquals(-0.02, drift.m1(), 0.0);
    }

    @Test
    void resourceCycleAndInflowAreMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class, () -> RunConfig.parse(new String[] {
                "--units", "100", "--world", "100", "--resource-cycle", "--inflow", "disk r=10"
        }));
    }

    @Test
    void inflowFlagBuildsNonUniformConfig() {
        RunConfig cfg = RunConfig.parse(new String[] {
                "--units", "100", "--world", "100", "--inflow", "noise seed=1 peak=5"
        });
        assertEquals(1, cfg.inflow().sources().length);
        RunConfig uniform = RunConfig.parse(new String[] { "--units", "100", "--world", "100" });
        assertSame(InflowConfig.UNIFORM, uniform.inflow());
    }
}
