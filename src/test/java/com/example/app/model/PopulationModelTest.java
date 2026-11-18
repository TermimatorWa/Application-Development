package com.example.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Basic tests from plan 5.2: non-negative populations, W0 = 0 behaviour and parameter limits. */
class PopulationModelTest {

    private static ModelParameters central() {
        return ModelParameters.from(Assumptions.central(), Climate.baseline(), false);
    }

    private static Scenario reference(int wolves) {
        return new Scenario(Reference.REFERENCE_START, wolves, 50, central());
    }

    @Test
    void populationsNeverBecomeNegative() {
        Assumptions harsh = Assumptions.central()
                .withAttackRate(AssumptionLevel.HIGH)
                .withPreyPreference(AssumptionLevel.HIGH)
                .withHerbivoreGrowth(AssumptionLevel.LOW)
                .withGrassAvailability(AssumptionLevel.LOW);
        ModelParameters p = ModelParameters.from(harsh, Climate.baseline(), true);
        SimulationResult result = PopulationModel.simulate(
                new Scenario(new PopulationState(20, 5, 5, 0), Scenario.MAX_WOLVES, 100, p));
        for (PopulationState state : result.years()) {
            assertTrue(state.deer() >= 0 && state.cattle() >= 0 && state.horses() >= 0 && state.wolves() >= 0);
        }
    }

    @Test
    void zeroWolvesMeansNoPredation() {
        double[] kills = PopulationModel.killsPerWolf(new double[]{500, 300, 300}, central());
        assertTrue(kills[0] > 0, "a wolf would kill deer");
        SimulationResult noWolves = PopulationModel.simulate(reference(0));
        for (PopulationState state : noWolves.years()) {
            assertEquals(0, state.wolves());
        }
        // Without wolves the dynamic-wolf switch must make no difference.
        ModelParameters dynamic = ModelParameters.from(Assumptions.central(), Climate.baseline(), true);
        SimulationResult dynamicNoWolves = PopulationModel.simulate(reference(0).withParameters(dynamic));
        assertEquals(noWolves.end(), dynamicNoWolves.end());
    }

    @Test
    void withoutWolvesHerdsApproachCarryingCapacity() {
        SimulationResult result = PopulationModel.simulate(
                new Scenario(Reference.REFERENCE_START, 0, 200, central()));
        PopulationState end = result.end();
        double[] growth = PopulationModel.logisticGrowth(new double[]{end.deer(), end.cattle(), end.horses()},
                central());
        for (double g : growth) {
            assertEquals(0, g, 1.0, "the no-wolf model should settle at an equilibrium");
        }
    }

    @Test
    void moreWolvesMeanFewerDeer() {
        double previous = Double.MAX_VALUE;
        for (int w = 0; w <= 10; w += 2) {
            double deer = PopulationModel.simulate(reference(w)).end().deer();
            assertTrue(deer < previous, "deer should decrease as W0 increases");
            previous = deer;
        }
    }

    @Test
    void constantWolvesStayConstant() {
        SimulationResult result = PopulationModel.simulate(reference(6));
        for (PopulationState state : result.years()) {
            assertEquals(6, state.wolves(), 1e-9);
        }
    }

    @Test
    void parameterLimitsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new PopulationState(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> reference(-1));
        assertThrows(IllegalArgumentException.class, () -> reference(Scenario.MAX_WOLVES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Scenario(Reference.REFERENCE_START, 0, 0, central()));
        assertThrows(IllegalArgumentException.class, () -> new Climate(10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModelParameters(
                new double[]{-0.1, 0.2, 0.2}, ModelParameters.CENTRAL_CAPACITY, ModelParameters.COMPETITION,
                0.05, 0.04, new double[]{1, 0.2, 0.3}, 1, false, 0.02, 0.25));
        assertThrows(IllegalArgumentException.class, () -> new ModelParameters(
                ModelParameters.CENTRAL_GROWTH, new double[]{0, 1, 1}, ModelParameters.COMPETITION,
                0.05, 0.04, new double[]{1, 0.2, 0.3}, 1, false, 0.02, 0.25));
        assertThrows(IllegalArgumentException.class, () -> new ModelParameters(
                ModelParameters.CENTRAL_GROWTH, ModelParameters.CENTRAL_CAPACITY, ModelParameters.COMPETITION,
                -0.05, 0.04, new double[]{1, 0.2, 0.3}, 1, false, 0.02, 0.25));
    }

    @Test
    void grassGrowthPotentialFollowsAppendix3() {
        assertEquals(1.0, GrassGrowth.potential(20, 10), 1e-12);
        assertEquals(Math.exp(-0.5), GrassGrowth.potential(30, 10), 1e-12);
        assertEquals(GrassGrowth.potential(15, 7), GrassGrowth.potential(25, 7), 1e-12);
        assertEquals(1.0, Climate.baseline().capacityFactor(), 1e-12);
        assertTrue(Climate.baseline().withDeltaT(2).capacityFactor() > 1, "warming towards 20 C raises GP");
    }

    @Test
    void metricsDetectInstabilityOfUnmanagedReference() {
        ScenarioMetrics metrics = ScenarioMetrics.of(PopulationModel.simulate(reference(0)),
                ScenarioMetrics.Criteria.DEFAULT);
        assertTrue(metrics.persistent());
        assertFalse(metrics.referenceSustained(), "without wolves or culling the herds grow beyond 1,100");
        assertTrue(metrics.yearOutsideReference().isPresent());
    }
}
