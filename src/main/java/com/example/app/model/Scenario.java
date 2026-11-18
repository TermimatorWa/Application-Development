package com.example.app.model;

/**
 * One model run: starting herbivore numbers, the number of released wolves W0, the study horizon
 * and the parameters.
 */
public record Scenario(PopulationState start, int initialWolves, int horizonYears, ModelParameters parameters) {

    public static final int MAX_WOLVES = 100;
    public static final int MAX_HORIZON = 200;

    public Scenario {
        if (initialWolves < 0 || initialWolves > MAX_WOLVES) {
            throw new IllegalArgumentException("W0 must be between 0 and " + MAX_WOLVES);
        }
        if (horizonYears < 1 || horizonYears > MAX_HORIZON) {
            throw new IllegalArgumentException("horizon must be between 1 and " + MAX_HORIZON + " years");
        }
    }

    public Scenario withWolves(int wolves) {
        return new Scenario(start, wolves, horizonYears, parameters);
    }

    public Scenario withParameters(ModelParameters newParameters) {
        return new Scenario(start, initialWolves, horizonYears, newParameters);
    }
}
