package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;

import com.example.app.model.PopulationModel;
import com.example.app.model.PopulationState;
import com.example.app.model.Scenario;
import com.example.app.model.ScenarioMetrics;
import com.example.app.model.SimulationResult;
import com.example.app.model.Species;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Scenario comparison for question B (main focus, plan 5.2 subquestion 3): the same starting
 * situation run for every W0 in an illustrative grid, including W0 = 0. For each scenario it
 * records each herbivore over time, the total, the minimum population and the time to threshold
 * crossings.
 */
final class WolfScenarioTab extends Tab {

    private static final String TOTAL = "Total herbivores";

    private final ComboBox<String> shown = new ComboBox<>(FXCollections.observableArrayList(
            TOTAL, Species.DEER.displayName(), Species.CATTLE.displayName(), Species.HORSES.displayName()));
    private final LineChart<Number, Number> chart =
            Controls.lineChart("Wolf scenarios", "Year", "Number of animals");
    private final TableView<Row> table = Controls.table();
    private ScenarioInputs last;

    private record Row(int wolves, ScenarioMetrics m) {
    }

    WolfScenarioTab() {
        super("Wolf scenarios");
        setClosable(false);
        shown.setValue(TOTAL);
        shown.valueProperty().addListener((obs, o, n) -> {
            if (last != null) {
                update(last);
            }
        });

        table.getColumns().addAll(List.of(
                Controls.column("W₀", r -> Integer.toString(r.wolves())),
                Controls.column("Deer (end)", r -> Controls.count(r.m().end().deer())),
                Controls.column("Cattle (end)", r -> Controls.count(r.m().end().cattle())),
                Controls.column("Horses (end)", r -> Controls.count(r.m().end().horses())),
                Controls.column("Total (end)", r -> Controls.count(r.m().end().totalHerbivores())),
                Controls.column("Min deer", r -> Controls.count(r.m().minimum()[0])),
                Controls.column("Min cattle", r -> Controls.count(r.m().minimum()[1])),
                Controls.column("Min horses", r -> Controls.count(r.m().minimum()[2])),
                Controls.column("Min total", r -> Controls.count(r.m().minimumTotal())),
                Controls.column("< min herd", r -> Controls.year(r.m().yearBelowThreshold())),
                Controls.column("Leaves band", r -> Controls.year(r.m().yearOutsideReference())),
                Controls.column("Stable", r -> Controls.yesNo(r.m().stable()))));
        table.setPrefHeight(230);
        table.setMinHeight(150);

        Label hint = Controls.note("Illustrative grid; adjust the range in the left panel after checking "
                + "ecological plausibility. Wolves are held constant at W₀ unless 'Let wolf numbers change' "
                + "is ticked. '< min herd': first year a species drops below the minimum viable herd. "
                + "'Leaves band': first year the total is outside 1,100 ± tolerance.");
        VBox box = new VBox(8, new HBox(8, new Label("Show:"), shown), chart, hint, table);
        box.setPadding(new Insets(8));
        VBox.setVgrow(chart, Priority.ALWAYS);
        setContent(box);
    }

    void update(ScenarioInputs inputs) {
        last = inputs;
        Scenario base = inputs.scenario();
        chart.getData().clear();
        List<Row> rows = new ArrayList<>();
        for (int w : inputs.wolfGrid()) {
            SimulationResult result = PopulationModel.simulate(base.withWolves(w));
            rows.add(new Row(w, ScenarioMetrics.of(result, inputs.criteria())));
            XYChart.Series<Number, Number> series = Controls.series("W₀ = " + w);
            List<PopulationState> years = result.years();
            for (int t = 0; t < years.size(); t++) {
                series.getData().add(new XYChart.Data<>(t, value(years.get(t))));
            }
            chart.getData().add(series);
        }
        chart.setTitle(shown.getValue() + " for different numbers of released wolves");
        table.getItems().setAll(rows);
    }

    private double value(PopulationState state) {
        String selected = shown.getValue();
        for (Species species : Species.values()) {
            if (species.displayName().equals(selected)) {
                return state.get(species);
            }
        }
        return state.totalHerbivores();
    }
}
