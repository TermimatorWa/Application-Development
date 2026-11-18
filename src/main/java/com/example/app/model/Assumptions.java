package com.example.app.model;

/**
 * The uncertain inputs varied in the sensitivity analysis (project plan 5.2, subquestion 4):
 * prey preference, attack rate, grass availability and herbivore growth.
 */
public record Assumptions(AssumptionLevel preyPreference,
                          AssumptionLevel attackRate,
                          AssumptionLevel grassAvailability,
                          AssumptionLevel herbivoreGrowth) {

    public static Assumptions central() {
        return new Assumptions(AssumptionLevel.CENTRAL, AssumptionLevel.CENTRAL,
                AssumptionLevel.CENTRAL, AssumptionLevel.CENTRAL);
    }

    public Assumptions withPreyPreference(AssumptionLevel level) {
        return new Assumptions(level, attackRate, grassAvailability, herbivoreGrowth);
    }

    public Assumptions withAttackRate(AssumptionLevel level) {
        return new Assumptions(preyPreference, level, grassAvailability, herbivoreGrowth);
    }

    public Assumptions withGrassAvailability(AssumptionLevel level) {
        return new Assumptions(preyPreference, attackRate, level, herbivoreGrowth);
    }

    public Assumptions withHerbivoreGrowth(AssumptionLevel level) {
        return new Assumptions(preyPreference, attackRate, grassAvailability, level);
    }
}
