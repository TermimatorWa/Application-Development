package com.example.app.model;

/** Numbers of deer, cattle, horses and wolves at a moment in time. */
public record PopulationState(double deer, double cattle, double horses, double wolves) {

    public PopulationState {
        if (deer < 0 || cattle < 0 || horses < 0 || wolves < 0) {
            throw new IllegalArgumentException("populations must not be negative");
        }
    }

    public double get(Species species) {
        return switch (species) {
            case DEER -> deer;
            case CATTLE -> cattle;
            case HORSES -> horses;
        };
    }

    /** Total number of large herbivores (deer + cattle + horses). */
    public double totalHerbivores() {
        return deer + cattle + horses;
    }

    double[] herbivores() {
        return new double[]{deer, cattle, horses};
    }
}
