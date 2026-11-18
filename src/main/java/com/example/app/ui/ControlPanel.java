package com.example.app.ui;

import com.example.app.model.AssumptionLevel;
import com.example.app.model.Assumptions;
import com.example.app.model.Climate;
import com.example.app.model.PopulationState;
import com.example.app.model.Reference;
import com.example.app.model.Scenario;
import com.example.app.model.ScenarioMetrics;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Left-hand input panel. The plan (5.2, Validation and application) asks that the interface allows
 * changing wolf numbers, starting populations and key assumptions; temperature and the stability
 * criteria are added for subquestions 5 and 1.
 */
public final class ControlPanel extends ScrollPane {

    private final Runnable onChange;
    private boolean suppress;

    private final Spinner<Integer> wolves = Controls.intSpinner(0, Scenario.MAX_WOLVES, 0, 1);
    private final CheckBox dynamicWolves = new CheckBox("Let wolf numbers change (dW/dt)");
    private final Spinner<Integer> deer = Controls.intSpinner(0, 20000, 500, 10);
    private final Spinner<Integer> cattle = Controls.intSpinner(0, 20000, 300, 10);
    private final Spinner<Integer> horses = Controls.intSpinner(0, 20000, 300, 10);
    private final Slider deltaT = new Slider(-2, 6, 0);
    private final Label deltaTLabel = new Label();
    private final Spinner<Double> baseTemperature =
            Controls.doubleSpinner(-5, 30, Climate.DEFAULT_BASE_TEMPERATURE, 0.5);
    private final Spinner<Double> gpVar = Controls.doubleSpinner(1, 30, Climate.DEFAULT_VAR, 0.5);
    private final ComboBox<AssumptionLevel> preyPreference = Controls.levelBox();
    private final ComboBox<AssumptionLevel> attackRate = Controls.levelBox();
    private final ComboBox<AssumptionLevel> grassAvailability = Controls.levelBox();
    private final ComboBox<AssumptionLevel> herbivoreGrowth = Controls.levelBox();
    private final Spinner<Integer> horizon = Controls.intSpinner(5, Scenario.MAX_HORIZON, 50, 5);
    private final Spinner<Integer> minimumViable = Controls.intSpinner(0, 1000, 50, 10);
    private final Spinner<Integer> tolerance = Controls.intSpinner(1, 50, 10, 1);
    private final Spinner<Integer> window = Controls.intSpinner(1, 50, 10, 1);
    private final Spinner<Integer> gridMax = Controls.intSpinner(1, Scenario.MAX_WOLVES, 10, 1);
    private final Spinner<Integer> gridStep = Controls.intSpinner(1, 20, 2, 1);

    public ControlPanel(Runnable onChange) {
        this.onChange = onChange;

        deltaT.setMajorTickUnit(1);
        deltaT.setMinorTickCount(1);
        deltaT.setSnapToTicks(true);
        deltaT.setShowTickMarks(true);
        deltaT.setShowTickLabels(true);
        deltaT.setBlockIncrement(0.5);

        Button referenceButton = new Button("Reference 1,100");
        referenceButton.setTooltip(new Tooltip("500 deer, 300 cattle, 300 horses (the 600 split evenly)"));
        referenceButton.setOnAction(e -> setStart(Reference.REFERENCE_START));
        Button countsButton = new Button("Counts 2025");
        countsButton.setTooltip(new Tooltip("Teaching data, 1 January 2025: 770 deer, 380 cattle, 420 horses"));
        countsButton.setOnAction(e -> setStart(Reference.COUNTS_2025));
        Button reset = new Button("Reset all to defaults");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(e -> resetDefaults());

        dynamicWolves.setTooltip(new Tooltip(
                "Off (plan default): wolves are held constant at W0 to isolate its effect.\n"
                        + "On: dW/dt = e * kills * W - q * W (Lotka-Volterra extension)."));

        VBox content = new VBox(8,
                Controls.heading("Wolves (main focus)"),
                grid(row("Released wolves W₀", wolves)),
                dynamicWolves,
                Controls.heading("Starting populations"),
                grid(row("Red deer", deer), row("Heck cattle", cattle), row("Konik horses", horses)),
                new HBox(6, referenceButton, countsButton),
                Controls.heading("Temperature (question C)"),
                new HBox(6, new Label("Temperature change"), deltaTLabel),
                deltaT,
                grid(row("Baseline temperature (°C)", baseTemperature),
                        row("GP curve width 'var'", gpVar)),
                Controls.note("GP = exp[−0.5((t − 20)/var)²], Appendix 3. "
                        + "Baseline temperature and var are assumptions."),
                Controls.heading("Key assumptions (low / central / high)"),
                grid(row("Wolf preference for cattle & horses", preyPreference),
                        row("Wolf attack rate", attackRate),
                        row("Grass availability", grassAvailability),
                        row("Herbivore growth rates", herbivoreGrowth)),
                Controls.heading("Study horizon and stability"),
                grid(row("Horizon (years)", horizon),
                        row("Minimum viable herd", minimumViable),
                        row("Tolerance (%)", tolerance),
                        row("Final window (years)", window)),
                Controls.note("Stable = every species stays above the minimum viable herd, the total "
                        + "fluctuates less than the tolerance and does not decline by more than the "
                        + "tolerance over the final window."),
                Controls.heading("Wolf scenario grid"),
                grid(row("Up to W₀ =", gridMax), row("Step", gridStep)),
                new Separator(),
                reset);
        content.setPadding(new Insets(12));
        content.setPrefWidth(330);
        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setMinWidth(340);

        for (Spinner<?> spinner : new Spinner<?>[]{wolves, deer, cattle, horses, baseTemperature, gpVar,
                horizon, minimumViable, tolerance, window, gridMax, gridStep}) {
            spinner.valueProperty().addListener((obs, o, n) -> changed());
        }
        for (ComboBox<?> box : new ComboBox<?>[]{preyPreference, attackRate, grassAvailability, herbivoreGrowth}) {
            box.valueProperty().addListener((obs, o, n) -> changed());
        }
        dynamicWolves.selectedProperty().addListener((obs, o, n) -> changed());
        deltaT.valueProperty().addListener((obs, o, n) -> {
            updateDeltaTLabel();
            changed();
        });
        updateDeltaTLabel();
    }

    public ScenarioInputs inputs() {
        return new ScenarioInputs(
                new PopulationState(deer.getValue(), cattle.getValue(), horses.getValue(), 0),
                wolves.getValue(),
                dynamicWolves.isSelected(),
                horizon.getValue(),
                new Assumptions(preyPreference.getValue(), attackRate.getValue(),
                        grassAvailability.getValue(), herbivoreGrowth.getValue()),
                new Climate(baseTemperature.getValue(), deltaT.getValue(), gpVar.getValue()),
                new ScenarioMetrics.Criteria(minimumViable.getValue(), tolerance.getValue() / 100.0,
                        window.getValue()),
                gridMax.getValue(),
                gridStep.getValue());
    }

    private void setStart(PopulationState start) {
        suppress = true;
        deer.getValueFactory().setValue((int) start.deer());
        cattle.getValueFactory().setValue((int) start.cattle());
        horses.getValueFactory().setValue((int) start.horses());
        suppress = false;
        changed();
    }

    private void resetDefaults() {
        suppress = true;
        wolves.getValueFactory().setValue(0);
        dynamicWolves.setSelected(false);
        deltaT.setValue(0);
        baseTemperature.getValueFactory().setValue(Climate.DEFAULT_BASE_TEMPERATURE);
        gpVar.getValueFactory().setValue(Climate.DEFAULT_VAR);
        for (ComboBox<AssumptionLevel> box : java.util.List.of(preyPreference, attackRate,
                grassAvailability, herbivoreGrowth)) {
            box.setValue(AssumptionLevel.CENTRAL);
        }
        horizon.getValueFactory().setValue(50);
        minimumViable.getValueFactory().setValue(50);
        tolerance.getValueFactory().setValue(10);
        window.getValueFactory().setValue(10);
        gridMax.getValueFactory().setValue(10);
        gridStep.getValueFactory().setValue(2);
        suppress = false;
        setStart(Reference.REFERENCE_START);
    }

    private void updateDeltaTLabel() {
        deltaTLabel.setText(String.format("%+.1f °C", deltaT.getValue()));
        deltaTLabel.setStyle("-fx-font-weight: bold;");
    }

    private void changed() {
        if (!suppress) {
            onChange.run();
        }
    }

    private record Row(String label, javafx.scene.Node control) {
    }

    private static Row row(String label, javafx.scene.Node control) {
        return new Row(label, control);
    }

    private static GridPane grid(Row... rows) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        for (int i = 0; i < rows.length; i++) {
            Label label = new Label(rows[i].label());
            label.setWrapText(true);
            label.setPrefWidth(170);
            grid.add(label, 0, i);
            grid.add(rows[i].control(), 1, i);
        }
        return grid;
    }
}
