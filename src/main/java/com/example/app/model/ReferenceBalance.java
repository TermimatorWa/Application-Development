package com.example.app.model;

/**
 * Net change per year if the herbivores were exactly at the management reference
 * (500 deer, 300 cattle, 300 horses) with the given number of wolves. A positive value is the number
 * of animals that would have to be removed each year to hold the reference; a negative value means
 * the reference would shrink without help. Used to compare climate scenarios (subquestion 5).
 */
public final class ReferenceBalance {

    private ReferenceBalance() {
    }

    public static double annualSurplus(ModelParameters p, double wolves) {
        double[] herbivores = Reference.REFERENCE_START.herbivores();
        double[] growth = PopulationModel.logisticGrowth(herbivores, p);
        double[] kills = PopulationModel.killsPerWolf(herbivores, p);
        double surplus = 0;
        for (int i = 0; i < Species.COUNT; i++) {
            surplus += growth[i] - wolves * kills[i];
        }
        return surplus;
    }
}
