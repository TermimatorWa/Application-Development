package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;

import com.example.app.model.Climate;
import com.example.app.model.GrassGrowth;
import com.example.app.model.ModelParameters;
import com.example.app.model.PopulationModel;
import com.example.app.model.PopulationState;
import com.example.app.model.ReferenceBalance;
import com.example.app.model.Scenario;
import com.example.app.model.ScenarioMetrics;
import com.example.app.model.SimulationResult;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Question C (plan 5.2, subquestion 5): a small set of illustrative temperature-change scenarios,
 * implemented as a change in carrying capacity through the Appendix 3 grass growth potential.
 * Scenarios where the 1,100 reference cannot be sustained over the horizon are flagged as possible
 * policy-review cases, not verified temperature thresholds.
 */
final class ClimateTab extends Tab {

    private static final double[] DELTAS = {-1, 0, 1, 2, 3, 4, 5};

    private final LineChart<Number, Number> gpChart =
            Controls.lineChart("Grass growth potential (Appendix 3)", "Average temperature (°C)", "GP");
    private final LineChart<Number, Number> totalChart =
            Controls.lineChart("Total herbivores per temperature scenario", "Year", "Number of animals");
    private final TableView<Row> table = Controls.table();
    private double baselineSurplus = 1;

    private record Row(double deltaT, double temperature, double gp, double capacityFactor,
                       ScenarioMetrics m, double surplus) {
    }

    ClimateTab() {
        super("Temperature scenarios");
        setClosable(false);
        gpChart.setCreateSymbols(true);
        table.getColumns().addAll(List.of(
                Controls.column("ΔT", r -> String.format("%+.0f °C", r.deltaT())),
                Controls.column("Temperature", r -> String.format("%.1f °C", r.temperature())),
                Controls.column("GP", r -> String.format("%.3f", r.gp())),
                Controls.column("K vs. now", r -> String.format("%.0f%%", r.capacityFactor() * 100)),
                Controls.column("Total (end)", r -> Controls.count(r.m().end().totalHerbivores())),
                Controls.column("Min total", r -> Controls.count(r.m().minimumTotal())),
                Controls.column("Removal / yr", r -> Controls.count(r.surplus())),
                Controls.column("Removal vs. ΔT 0", r -> String.format("%+.0f%%",
                        (r.surplus() / baselineSurplus - 1) * 100)),
                Controls.column("Leaves band", r -> Controls.year(r.m().yearOutsideReference())),
                Controls.column("Policy review?", r -> r.m().referenceSustained() ? "no" : "possibly")));
        table.setPrefHeight(230);
        table.setMinHeight(150);

        HBox charts = new HBox(8, gpChart, totalChart);
        HBox.setHgrow(gpChart, Priority.ALWAYS);
        HBox.setHgrow(totalChart, Priority.ALWAYS);
        gpChart.setPrefWidth(380);
        Label note = Controls.note("The provided datasets do not establish a numerical temperature–grass "
                + "relationship, so this link is a sensitivity assumption. With the baseline below the 20 °C "
                + "optimum, warming raises grass growth potential. 'Removal / yr' is the net growth minus wolf kills "
                + "at exactly 500 deer, 300 cattle and 300 horses: positive means animals would have to be removed "
                + "each year to hold 1,100, negative means the reference would shrink. 'Policy review?' marks "
                + "scenarios whose total leaves the 1,100 band within the horizon: possible policy-review cases, not "
                + "verified thresholds. Drought is not modelled.");
        VBox box = new VBox(8, charts, note, table);
        box.setPadding(new Insets(8));
        VBox.setVgrow(charts, Priority.ALWAYS);
        setContent(box);
    }

    void update(ScenarioInputs inputs) {
        Climate climate = inputs.climate();
        updateGpChart(climate);

        Scenario base = inputs.scenario();
        baselineSurplus = ReferenceBalance.annualSurplus(
                ModelParameters.from(inputs.assumptions(), climate.withDeltaT(0), inputs.dynamicWolves()),
                inputs.wolves());
        totalChart.getData().clear();
        List<Row> rows = new ArrayList<>();
        for (double delta : DELTAS) {
            Climate scenarioClimate = climate.withDeltaT(delta);
            ModelParameters p = ModelParameters.from(inputs.assumptions(), scenarioClimate, inputs.dynamicWolves());
            SimulationResult result = PopulationModel.simulate(base.withParameters(p));
            ScenarioMetrics metrics = ScenarioMetrics.of(result, inputs.criteria());
            rows.add(new Row(delta, climate.baseTemperature() + delta,
                    GrassGrowth.potential(climate.baseTemperature() + delta, climate.var()),
                    scenarioClimate.capacityFactor(), metrics, ReferenceBalance.annualSurplus(p, inputs.wolves())));
            XYChart.Series<Number, Number> series = Controls.series(String.format("ΔT %+.0f", delta));
            List<PopulationState> years = result.years();
            for (int t = 0; t < years.size(); t++) {
                series.getData().add(new XYChart.Data<>(t, years.get(t).totalHerbivores()));
            }
            totalChart.getData().add(series);
        }
        totalChart.setTitle(String.format("Total herbivores per temperature scenario (W₀ = %d)", inputs.wolves()));
        table.getItems().setAll(rows);
    }

    private void updateGpChart(Climate climate) {
        gpChart.getData().clear();
        XYChart.Series<Number, Number> curve = Controls.series("GP(t)");
        for (double t = -5; t <= 35.001; t += 1) {
            curve.getData().add(new XYChart.Data<>(t, GrassGrowth.potential(t, climate.var())));
        }
        XYChart.Series<Number, Number> marks = Controls.series("Baseline and scenario");
        double base = climate.baseTemperature();
        marks.getData().add(new XYChart.Data<>(base, GrassGrowth.potential(base, climate.var())));
        double scenario = base + climate.deltaT();
        marks.getData().add(new XYChart.Data<>(scenario, GrassGrowth.potential(scenario, climate.var())));
        gpChart.getData().add(curve);
        gpChart.getData().add(marks);
        curve.getNode().getStyleClass().add("no-symbols");
        for (XYChart.Data<Number, Number> d : curve.getData()) {
            d.getNode().setVisible(false);
        }
    }
}
