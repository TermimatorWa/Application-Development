package com.example.app.model;

import java.util.List;
import java.util.OptionalInt;

/**
 * Outcome measures recorded for every scenario (project plan 5.2, subquestions 1 and 3):
 * population at the end, minimum population, time to threshold crossings, and the operational
 * stability test (persistence, bounded fluctuations, no sustained decline).
 *
 * @param end                  populations at the end of the horizon
 * @param minimum              minimum population of each herbivore over the horizon
 * @param minimumTotal         minimum total herbivore count
 * @param yearBelowThreshold   first year in which any herbivore falls below the minimum viable number
 * @param yearOutsideReference first year in which the total leaves the reference band (1,100 +/- tolerance)
 * @param persistent           no herbivore ever falls below the minimum viable number
 * @param bounded              fluctuation of the total over the last window is within the tolerance
 * @param noSustainedDecline   the total does not decline by more than the tolerance over the last window
 */
public record ScenarioMetrics(PopulationState end,
                              double[] minimum,
                              double minimumTotal,
                              OptionalInt yearBelowThreshold,
                              OptionalInt yearOutsideReference,
                              boolean persistent,
                              boolean bounded,
                              boolean noSustainedDecline) {

    /**
     * Criteria of the operational stability definition. The plan says the team agrees on these
     * (and on the horizon) before testing, so they are editable in the application.
     *
     * @param minimumViable minimum number of animals per species for persistence
     * @param tolerance     allowed relative fluctuation / decline, and width of the reference band
     * @param window        number of final years used for the fluctuation and decline checks
     */
    public record Criteria(double minimumViable, double tolerance, int window) {
        public static final Criteria DEFAULT = new Criteria(50, 0.10, 10);

        public Criteria {
            if (minimumViable < 0 || tolerance <= 0 || window < 1) {
                throw new IllegalArgumentException("invalid stability criteria");
            }
        }
    }

    public ScenarioMetrics {
        minimum = minimum.clone();
    }

    public static ScenarioMetrics of(SimulationResult result, Criteria criteria) {
        List<PopulationState> years = result.years();
        double[] minimum = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double minimumTotal = Double.MAX_VALUE;
        Integer belowThreshold = null;
        Integer outsideReference = null;
        double low = Reference.TOTAL * (1 - criteria.tolerance());
        double high = Reference.TOTAL * (1 + criteria.tolerance());
        for (int year = 0; year < years.size(); year++) {
            PopulationState state = years.get(year);
            for (Species species : Species.values()) {
                double n = state.get(species);
                minimum[species.ordinal()] = Math.min(minimum[species.ordinal()], n);
                if (belowThreshold == null && n < criteria.minimumViable()) {
                    belowThreshold = year;
                }
            }
            double total = state.totalHerbivores();
            minimumTotal = Math.min(minimumTotal, total);
            if (outsideReference == null && (total < low || total > high)) {
                outsideReference = year;
            }
        }

        int window = Math.min(criteria.window(), years.size() - 1);
        List<PopulationState> tail = years.subList(years.size() - 1 - window, years.size());
        double max = tail.stream().mapToDouble(PopulationState::totalHerbivores).max().orElse(0);
        double min = tail.stream().mapToDouble(PopulationState::totalHerbivores).min().orElse(0);
        double mean = tail.stream().mapToDouble(PopulationState::totalHerbivores).average().orElse(0);
        boolean bounded = mean > 0 && (max - min) / mean <= criteria.tolerance();
        double first = tail.getFirst().totalHerbivores();
        double last = tail.getLast().totalHerbivores();
        boolean noDecline = first <= 0 ? last >= first : (first - last) / first <= criteria.tolerance();

        return new ScenarioMetrics(result.end(), minimum, minimumTotal,
                belowThreshold == null ? OptionalInt.empty() : OptionalInt.of(belowThreshold),
                outsideReference == null ? OptionalInt.empty() : OptionalInt.of(outsideReference),
                belowThreshold == null, bounded, noDecline);
    }

    /** Stable in the operational sense used by the project plan. */
    public boolean stable() {
        return persistent && bounded && noSustainedDecline;
    }

    /** True when the total stays inside the reference band for the whole horizon. */
    public boolean referenceSustained() {
        return yearOutsideReference.isEmpty();
    }
}
