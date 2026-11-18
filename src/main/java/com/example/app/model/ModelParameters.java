package com.example.app.model;

import java.util.Arrays;

/**
 * All parameters of the population model. Arrays are indexed by {@link Species#ordinal()}.
 * <p>
 * The values in {@link #from(Assumptions, Climate, boolean)} are <b>illustrative assumptions</b>,
 * not fitted facts. The central herbivore values were chosen so that the no-wolf model roughly
 * follows the broad 1992-2017 pattern in the teaching dataset (fast growth, levelling off at
 * about 4,500 animals); the Validation tab shows how close it is.
 *
 * @param growthRate       intrinsic growth rate r of each herbivore (per year)
 * @param carryingCapacity carrying capacity K of each herbivore at the baseline temperature
 * @param competition      competition coefficients alpha[i][j]: effect of species j on species i
 * @param attackRate       wolf attack rate a (per prey animal, per wolf, per year)
 * @param handlingTime     wolf handling time h (years per kill); 1/h is the maximum kills per wolf per year
 * @param preference       relative wolf preference for each prey species
 * @param capacityFactor   multiplier on K from grass availability and temperature
 * @param dynamicWolves    false: wolf numbers stay at W0; true: wolves follow dW/dt = e*kills*W - q*W
 * @param wolfEfficiency   e, wolves gained per prey animal killed
 * @param wolfMortality    q, wolf mortality rate (per year)
 */
public record ModelParameters(double[] growthRate,
                              double[] carryingCapacity,
                              double[][] competition,
                              double attackRate,
                              double handlingTime,
                              double[] preference,
                              double capacityFactor,
                              boolean dynamicWolves,
                              double wolfEfficiency,
                              double wolfMortality) {

    /** Central growth rates r for deer, cattle, horses (per year). */
    public static final double[] CENTRAL_GROWTH = {0.40, 0.24, 0.26};
    /** Central carrying capacities K for deer, cattle, horses at the baseline temperature. */
    public static final double[] CENTRAL_CAPACITY = {3480, 1490, 1890};
    /** Cattle and horses are both grazers and compete more with each other than with deer. */
    public static final double[][] COMPETITION = {
            {1.0, 0.2, 0.2},
            {0.2, 1.0, 0.5},
            {0.2, 0.5, 1.0}};
    public static final double HANDLING_TIME = 0.04;
    public static final double WOLF_EFFICIENCY = 0.02;
    public static final double WOLF_MORTALITY = 0.25;

    public ModelParameters {
        requireLength(growthRate, "growthRate");
        requireLength(carryingCapacity, "carryingCapacity");
        requireLength(preference, "preference");
        if (competition == null || competition.length != Species.COUNT) {
            throw new IllegalArgumentException("competition must be a 3x3 matrix");
        }
        for (int i = 0; i < Species.COUNT; i++) {
            requireLength(competition[i], "competition row");
            if (growthRate[i] < 0 || growthRate[i] > 2) {
                throw new IllegalArgumentException("growth rate must be between 0 and 2 per year");
            }
            if (carryingCapacity[i] <= 0) {
                throw new IllegalArgumentException("carrying capacity must be greater than 0");
            }
            if (preference[i] < 0) {
                throw new IllegalArgumentException("prey preference must not be negative");
            }
            for (double alpha : competition[i]) {
                if (alpha < 0) {
                    throw new IllegalArgumentException("competition coefficients must not be negative");
                }
            }
        }
        if (attackRate < 0 || handlingTime < 0 || wolfEfficiency < 0 || wolfMortality < 0) {
            throw new IllegalArgumentException("wolf parameters must not be negative");
        }
        if (capacityFactor <= 0) {
            throw new IllegalArgumentException("capacity factor must be greater than 0");
        }
        growthRate = growthRate.clone();
        carryingCapacity = carryingCapacity.clone();
        preference = preference.clone();
        competition = Arrays.stream(competition).map(double[]::clone).toArray(double[][]::new);
    }

    /** Builds the parameters for the chosen assumption levels and climate scenario. */
    public static ModelParameters from(Assumptions assumptions, Climate climate, boolean dynamicWolves) {
        double growthFactor = assumptions.herbivoreGrowth().pick(0.75, 1.0, 1.25);
        double[] growth = Arrays.stream(CENTRAL_GROWTH).map(r -> r * growthFactor).toArray();
        double grassFactor = assumptions.grassAvailability().pick(0.75, 1.0, 1.25);
        double attack = assumptions.attackRate().pick(0.025, 0.05, 0.10);
        // Preference for deer stays 1; the level sets how much wolves also take cattle and horses.
        double[] preference = assumptions.preyPreference().pick(
                new double[]{1.0, 0.1, 0.15},
                new double[]{1.0, 0.2, 0.3},
                new double[]{1.0, 0.4, 0.6});
        return new ModelParameters(growth, CENTRAL_CAPACITY, COMPETITION, attack, HANDLING_TIME, preference,
                grassFactor * climate.capacityFactor(), dynamicWolves, WOLF_EFFICIENCY, WOLF_MORTALITY);
    }

    /** Effective carrying capacity of a species after grass availability and temperature. */
    public double effectiveCapacity(Species species) {
        return carryingCapacity[species.ordinal()] * capacityFactor;
    }

    private static void requireLength(double[] values, String name) {
        if (values == null || values.length != Species.COUNT) {
            throw new IllegalArgumentException(name + " must have one value per species");
        }
    }
}
