package com.example.app.model;

/**
 * Temperature scenario (project plan 5.2, subquestion 5). Warming changes grass growth potential
 * and therefore carrying capacity, through {@link GrassGrowth}.
 *
 * @param baseTemperature average temperature of the reference situation (degrees C, assumption)
 * @param deltaT          temperature change of the scenario (degrees C)
 * @param var             width parameter of the Appendix 3 curve (assumption)
 */
public record Climate(double baseTemperature, double deltaT, double var) {

    public static final double DEFAULT_BASE_TEMPERATURE = 10.5;
    public static final double DEFAULT_VAR = 10.0;

    public Climate {
        if (var <= 0) {
            throw new IllegalArgumentException("var must be greater than 0");
        }
    }

    public static Climate baseline() {
        return new Climate(DEFAULT_BASE_TEMPERATURE, 0, DEFAULT_VAR);
    }

    public Climate withDeltaT(double newDeltaT) {
        return new Climate(baseTemperature, newDeltaT, var);
    }

    public double capacityFactor() {
        return GrassGrowth.capacityFactor(baseTemperature, deltaT, var);
    }
}
