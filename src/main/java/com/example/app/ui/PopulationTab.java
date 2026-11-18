package com.example.app.ui;

import java.util.List;

import com.example.app.model.PopulationModel;
import com.example.app.model.PopulationState;
import com.example.app.model.Reference;
import com.example.app.model.ScenarioMetrics;
import com.example.app.model.SimulationResult;
import com.example.app.model.Species;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Main view: the three herbivore population curves (and their total) for the current inputs,
 * against the 1,100-animal reference, with the recorded outcome measures and the stability test.
 */
final class PopulationTab extends Tab {

    private final LineChart<Number, Number> chart =
            Controls.lineChart("Populations over time", "Year", "Number of animals");
    private final Label verdict = new Label();
    private final TableView<Row> table = Controls.table();

    private record Row(String name, String start, String end, String minimum) {
    }

    PopulationTab() {
        super("Populations");
        setClosable(false);
        verdict.setWrapText(true);
        verdict.getStyleClass().add("verdict");

        table.getColumns().addAll(List.of(
                Controls.column("", Row::name),
                Controls.column("Start", Row::start),
                Controls.column("End of horizon", Row::end),
                Controls.column("Minimum", Row::minimum)));
        table.setPrefHeight(185);
        table.setMinHeight(185);

        VBox box = new VBox(8, chart, verdict, table);
        box.setPadding(new Insets(8));
        VBox.setVgrow(chart, Priority.ALWAYS);
        setContent(box);
    }

    void update(ScenarioInputs inputs) {
        SimulationResult result = PopulationModel.simulate(inputs.scenario());
        ScenarioMetrics metrics = ScenarioMetrics.of(result, inputs.criteria());

        chart.getData().clear();
        for (Species species : Species.values()) {
            XYChart.Series<Number, Number> series = Controls.series(species.displayName());
            List<PopulationState> years = result.years();
            for (int t = 0; t < years.size(); t++) {
                series.getData().add(new XYChart.Data<>(t, years.get(t).get(species)));
            }
            chart.getData().add(series);
        }
        XYChart.Series<Number, Number> total = Controls.series("Total herbivores");
        XYChart.Series<Number, Number> reference = Controls.series("Reference 1,100");
        XYChart.Series<Number, Number> wolves = Controls.series("Wolves");
        for (int t = 0; t < result.years().size(); t++) {
            PopulationState state = result.years().get(t);
            total.getData().add(new XYChart.Data<>(t, state.totalHerbivores()));
            reference.getData().add(new XYChart.Data<>(t, Reference.TOTAL));
            wolves.getData().add(new XYChart.Data<>(t, state.wolves()));
        }
        chart.getData().add(total);
        chart.getData().add(reference);
        if (inputs.wolves() > 0) {
            chart.getData().add(wolves);
        }
        reference.getNode().getStyleClass().add("reference-line");

        PopulationState start = result.start();
        PopulationState end = result.end();
        table.getItems().setAll(
                new Row("Red deer", Controls.count(start.deer()), Controls.count(end.deer()),
                        Controls.count(metrics.minimum()[Species.DEER.ordinal()])),
                new Row("Heck cattle", Controls.count(start.cattle()), Controls.count(end.cattle()),
                        Controls.count(metrics.minimum()[Species.CATTLE.ordinal()])),
                new Row("Konik horses", Controls.count(start.horses()), Controls.count(end.horses()),
                        Controls.count(metrics.minimum()[Species.HORSES.ordinal()])),
                new Row("Total herbivores", Controls.count(start.totalHerbivores()),
                        Controls.count(end.totalHerbivores()), Controls.count(metrics.minimumTotal())),
                new Row("Wolves", Controls.count(start.wolves()), Controls.count(end.wolves()), ""));

        boolean holds = metrics.stable() && metrics.referenceSustained();
        verdict.setText(String.format(
                "Over %d years with W₀ = %d: the populations %s (persistence: %s, bounded fluctuations: %s, "
                        + "no sustained decline: %s), and the total %s the reference band of 1,100 ± %.0f%%%s.%n"
                        + "A species first drops below the minimum viable herd in: %s.",
                inputs.horizonYears(), inputs.wolves(),
                metrics.stable() ? "are stable" : "are NOT stable",
                Controls.yesNo(metrics.persistent()), Controls.yesNo(metrics.bounded()),
                Controls.yesNo(metrics.noSustainedDecline()),
                metrics.referenceSustained() ? "stays within" : "does NOT stay within",
                inputs.criteria().tolerance() * 100,
                metrics.referenceSustained() ? "" : " (leaves it in " + Controls.year(metrics.yearOutsideReference()) + ")",
                Controls.year(metrics.yearBelowThreshold())));
        verdict.getStyleClass().removeAll("good", "bad");
        verdict.getStyleClass().add(holds ? "good" : "bad");
    }
}
