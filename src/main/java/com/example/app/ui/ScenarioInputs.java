package com.example.app.ui;

import java.util.stream.IntStream;

import com.example.app.model.Assumptions;
import com.example.app.model.Climate;
import com.example.app.model.ModelParameters;
import com.example.app.model.PopulationState;
import com.example.app.model.Scenario;
import com.example.app.model.ScenarioMetrics;

/** Everything the user has set in the control panel. */
public record ScenarioInputs(PopulationState start,
                             int wolves,
                             boolean dynamicWolves,
                             int horizonYears,
                             Assumptions assumptions,
                             Climate climate,
                             ScenarioMetrics.Criteria criteria,
                             int gridMax,
                             int gridStep) {

    public ModelParameters parameters() {
        return ModelParameters.from(assumptions, climate, dynamicWolves);
    }

    public Scenario scenario() {
        return new Scenario(start, wolves, horizonYears, parameters());
    }

    /** Illustrative grid of released wolves, always including W0 = 0 (e.g. 0, 2, 4, 6, 8, 10). */
    public int[] wolfGrid() {
        return IntStream.iterate(0, w -> w <= gridMax, w -> w + gridStep).toArray();
    }
}
