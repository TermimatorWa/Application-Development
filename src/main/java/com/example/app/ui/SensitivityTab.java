package com.example.app.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.example.app.model.AssumptionLevel;
import com.example.app.model.Assumptions;
import com.example.app.model.ModelParameters;
import com.example.app.model.PopulationModel;
import com.example.app.model.Scenario;
import com.example.app.model.ScenarioMetrics;
import com.example.app.model.Species;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Uncertainty analysis (plan 5.2, subquestion 4): each uncertain assumption is set to low and high
 * while the others stay at the user's choice, and the wolf scenarios are re-run. The table shows
 * whether the ranking of the wolf scenarios stays the same across assumptions.
 */
final class SensitivityTab extends Tab {

    private static final String END_TOTAL = "Total herbivores at end";
    private static final String MIN_DEER = "Minimum red deer";
    private static final String END_DEER = "Red deer at end";

    private final ComboBox<String> outcome = new ComboBox<>(
            FXCollections.observableArrayList(END_TOTAL, END_DEER, MIN_DEER));
    private final TableView<Row> table = Controls.table();
    private ScenarioInputs last;

    private record Variant(String name, UnaryOperator<Assumptions> change) {
    }

    private record Row(String name, double[] values, String ranking, boolean sameAsBase) {
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("Your settings", a -> a),
            new Variant("Prey preference low", a -> a.withPreyPreference(AssumptionLevel.LOW)),
            new Variant("Prey preference high", a -> a.withPreyPreference(AssumptionLevel.HIGH)),
            new Variant("Attack rate low", a -> a.withAttackRate(AssumptionLevel.LOW)),
            new Variant("Attack rate high", a -> a.withAttackRate(AssumptionLevel.HIGH)),
            new Variant("Grass availability low", a -> a.withGrassAvailability(AssumptionLevel.LOW)),
            new Variant("Grass availability high", a -> a.withGrassAvailability(AssumptionLevel.HIGH)),
            new Variant("Herbivore growth low", a -> a.withHerbivoreGrowth(AssumptionLevel.LOW)),
            new Variant("Herbivore growth high", a -> a.withHerbivoreGrowth(AssumptionLevel.HIGH)));

    SensitivityTab() {
        super("Sensitivity");
        setClosable(false);
        outcome.setValue(END_TOTAL);
        outcome.valueProperty().addListener((obs, o, n) -> {
            if (last != null) {
                update(last);
            }
        });
        Label note = Controls.note("No defensible estimates exist for these parameters yet, so every value is an "
                + "assumption: low / central / high scenarios, not fitted facts. Ranking lists W₀ values from the "
                + "lowest to the highest outcome. Values used: attack rate 0.025 / 0.05 / 0.10; preference for "
                + "cattle & horses 0.1–0.15 / 0.2–0.3 / 0.4–0.6 (deer = 1); grass and growth ×0.75 / "
                + "×1 / ×1.25.");
        VBox box = new VBox(8, new HBox(8, new Label("Outcome:"), outcome), table, note);
        box.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        setContent(box);
    }

    void update(ScenarioInputs inputs) {
        last = inputs;
        int[] grid = inputs.wolfGrid();
        List<Row> rows = new ArrayList<>();
        String baseRanking = null;
        for (Variant variant : VARIANTS) {
            ModelParameters p = ModelParameters.from(variant.change().apply(inputs.assumptions()),
                    inputs.climate(), inputs.dynamicWolves());
            Scenario scenario = inputs.scenario().withParameters(p);
            double[] values = Arrays.stream(grid)
                    .mapToDouble(w -> outcome(ScenarioMetrics.of(PopulationModel.simulate(scenario.withWolves(w)),
                            inputs.criteria())))
                    .toArray();
            String ranking = IntStream.range(0, grid.length).boxed()
                    .sorted(Comparator.comparingDouble((Integer i) -> Math.round(values[i]))
                            .thenComparingInt(i -> i))
                    .map(i -> Integer.toString(grid[i]))
                    .collect(Collectors.joining(" < "));
            if (baseRanking == null) {
                baseRanking = ranking;
            }
            rows.add(new Row(variant.name(), values, ranking, ranking.equals(baseRanking)));
        }

        table.getColumns().clear();
        table.getColumns().add(Controls.column("Assumption", Row::name));
        for (int i = 0; i < grid.length; i++) {
            int index = i;
            TableColumn<Row, String> column = new TableColumn<>("W₀ = " + grid[i]);
            column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Controls.count(c.getValue().values()[index])));
            column.setSortable(false);
            table.getColumns().add(column);
        }
        table.getColumns().add(Controls.column("Ranking of W₀", Row::ranking));
        table.getColumns().add(Controls.column("Same ranking", r -> Controls.yesNo(r.sameAsBase())));
        table.getItems().setAll(rows);
    }

    private double outcome(ScenarioMetrics m) {
        return switch (outcome.getValue()) {
            case MIN_DEER -> m.minimum()[Species.DEER.ordinal()];
            case END_DEER -> m.end().deer();
            default -> m.end().totalHerbivores();
        };
    }
}
