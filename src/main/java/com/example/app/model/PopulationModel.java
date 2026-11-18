package com.example.app.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Population model from the project plan (5.2, subquestion 2):
 * <pre>
 * dX_i/dt = r_i X_i (1 - sum_j alpha_ij X_j / K_i)  -  W f_i
 * f_i     = a p_i X_i / (1 + a h sum_j p_j X_j)        kills per wolf per year (Holling type II)
 * dW/dt   = e W sum_i f_i - q W                        only when wolves are dynamic
 * </pre>
 * Logistic growth with shared grass (Lotka-Volterra competition) is the benchmark; the wolf term
 * is the predator-prey extension. With W0 = 0 the wolf term is exactly zero. The equations are
 * integrated with fourth-order Runge-Kutta and reported once per year.
 */
public final class PopulationModel {

    private static final int STEPS_PER_YEAR = 20;

    private PopulationModel() {
    }

    public static SimulationResult simulate(Scenario scenario) {
        ModelParameters p = scenario.parameters();
        double[] x = {
                scenario.start().deer(), scenario.start().cattle(), scenario.start().horses(),
                scenario.initialWolves()};
        List<PopulationState> years = new ArrayList<>();
        years.add(toState(x));
        double dt = 1.0 / STEPS_PER_YEAR;
        for (int year = 1; year <= scenario.horizonYears(); year++) {
            for (int step = 0; step < STEPS_PER_YEAR; step++) {
                x = rungeKuttaStep(x, dt, p);
            }
            years.add(toState(x));
        }
        return new SimulationResult(scenario, years);
    }

    /** Kills per wolf per year of each herbivore species (functional response). */
    public static double[] killsPerWolf(double[] herbivores, ModelParameters p) {
        double encounter = 0;
        for (int j = 0; j < Species.COUNT; j++) {
            encounter += p.preference()[j] * herbivores[j];
        }
        double denominator = 1 + p.attackRate() * p.handlingTime() * encounter;
        double[] kills = new double[Species.COUNT];
        for (int i = 0; i < Species.COUNT; i++) {
            kills[i] = p.attackRate() * p.preference()[i] * herbivores[i] / denominator;
        }
        return kills;
    }

    /** Net growth of each herbivore per year from grass-limited logistic growth, without wolves. */
    public static double[] logisticGrowth(double[] herbivores, ModelParameters p) {
        double[] growth = new double[Species.COUNT];
        for (int i = 0; i < Species.COUNT; i++) {
            double pressure = 0;
            for (int j = 0; j < Species.COUNT; j++) {
                pressure += p.competition()[i][j] * herbivores[j];
            }
            double k = p.carryingCapacity()[i] * p.capacityFactor();
            growth[i] = p.growthRate()[i] * herbivores[i] * (1 - pressure / k);
        }
        return growth;
    }

    private static double[] derivatives(double[] x, ModelParameters p) {
        double[] herbivores = {x[0], x[1], x[2]};
        double wolves = x[3];
        double[] growth = logisticGrowth(herbivores, p);
        double[] kills = killsPerWolf(herbivores, p);
        double[] dx = new double[4];
        double totalKills = 0;
        for (int i = 0; i < Species.COUNT; i++) {
            dx[i] = growth[i] - wolves * kills[i];
            totalKills += kills[i];
        }
        dx[3] = p.dynamicWolves() ? p.wolfEfficiency() * wolves * totalKills - p.wolfMortality() * wolves : 0;
        return dx;
    }

    private static double[] rungeKuttaStep(double[] x, double dt, ModelParameters p) {
        double[] k1 = derivatives(x, p);
        double[] k2 = derivatives(add(x, k1, dt / 2), p);
        double[] k3 = derivatives(add(x, k2, dt / 2), p);
        double[] k4 = derivatives(add(x, k3, dt), p);
        double[] next = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            next[i] = Math.max(0, x[i] + dt / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]));
        }
        return next;
    }

    private static double[] add(double[] x, double[] dx, double factor) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = Math.max(0, x[i] + factor * dx[i]);
        }
        return result;
    }

    private static PopulationState toState(double[] x) {
        return new PopulationState(x[0], x[1], x[2], x[3]);
    }
}
