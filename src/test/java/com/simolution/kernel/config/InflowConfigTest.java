package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.GenomeFactory;

/**
 * Conformance for contract-v6 patchy/cyclic inflow: uniform is unchanged, the
 * cyclic disk pulses 0→peak→0 only in the centre, the periphery is barren, and
 * the closed-system audit still holds (only inflow's distribution changes).
 */
class InflowConfigTest {

    @Test
    void uniformIsConstantCellInflowEverywhere() {
        assertEquals(KernelConfig.CELL_INFLOW, InflowConfig.UNIFORM.inflowCap(0, 0, 100), 0.0);
        assertEquals(KernelConfig.CELL_INFLOW, InflowConfig.UNIFORM.inflowCap(5050, 999, 100), 0.0);
    }

    @Test
    void cyclicPulsesCentreAndStarvesPeriphery() {
        int w = 100, period = 2000, radius = 20;
        double peak = 6.0;
        InflowConfig c = InflowConfig.cyclic(period, radius, peak);
        int centre = (w / 2) * w + (w / 2);

        // triangle: trough at tick 0, peak at period/2, back to trough at period
        assertEquals(0.0, c.inflowCap(centre, 0, w), 1.0e-9, "centre at trough");
        assertEquals(peak, c.inflowCap(centre, period / 2, w), 1.0e-9, "centre at peak");
        assertEquals(peak / 2.0, c.inflowCap(centre, period / 4, w), 1.0e-9, "centre mid-rise");

        // periphery is barren at every phase
        assertEquals(0.0, c.inflowCap(0, period / 2, w), 0.0, "corner barren");
        assertEquals(0.0, c.inflowCap(w * w - 1, period / 2, w), 0.0, "far corner barren");
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThrows(IllegalArgumentException.class, () -> InflowConfig.cyclic(0, 10, 1.0));
    }

    @Test
    void cyclicRunConservesEnergy() {
        int w = 60, founders = 100;
        Kernel k = new Kernel(GenomeFactory.random(3L, founders, 8), w, 16,
                Kernel.scatterFounders(founders, w));
        k.configureInflow(InflowConfig.cyclic(400, 10, 8.0));

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
        // inflow actually happened (the pulse fed the centre at least once)
        assertTrue(maxInflowStep > 0.0, "cyclic inflow must admit resource during the boom");
    }
}
