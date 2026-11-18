package com.example.app.model;

/**
 * Grass growth potential from Appendix 3 of the project description:
 * GP = exp[-0.5 * ((t - t0) / var)^2], with t the average temperature and t0 = 20 C for C3 grass.
 * This is a growth potential, not a locally fitted temperature-grass relationship.
 */
public final class GrassGrowth {

    /** Optimum temperature for C3 grass, in degrees Celsius. */
    public static final double T0_C3 = 20.0;

    private GrassGrowth() {
    }

    public static double potential(double temperature, double var) {
        if (var <= 0) {
            throw new IllegalArgumentException("var must be greater than 0");
        }
        double z = (temperature - T0_C3) / var;
        return Math.exp(-0.5 * z * z);
    }

    /**
     * Factor by which carrying capacity changes when the average temperature rises by deltaT
     * from baseTemperature: GP(base + deltaT) / GP(base).
     */
    public static double capacityFactor(double baseTemperature, double deltaT, double var) {
        return potential(baseTemperature + deltaT, var) / potential(baseTemperature, var);
    }
}
