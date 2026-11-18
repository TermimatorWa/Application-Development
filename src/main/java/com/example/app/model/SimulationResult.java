package com.example.app.model;

import java.util.List;

/** Yearly populations of one scenario; index 0 is the start (year 0). */
public record SimulationResult(Scenario scenario, List<PopulationState> years) {

    public SimulationResult {
        years = List.copyOf(years);
    }

    public PopulationState start() {
        return years.getFirst();
    }

    public PopulationState end() {
        return years.getLast();
    }

    public int horizon() {
        return years.size() - 1;
    }
}
