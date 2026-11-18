package com.example.app.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.app.data.ObservedCounts;
import com.example.app.model.ModelParameters;
import com.example.app.model.PopulationModel;
import com.example.app.model.PopulationState;
import com.example.app.model.Scenario;
import com.example.app.model.SimulationResult;
import com.example.app.model.Species;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Validation (plan 5.2): observed against simulated trajectories for the no-wolf model, starting
 * from the first year with counts for all three species. The last years before the end year are
 * held back for checking, and the error is reported separately for the fit and check periods.
 */
final class ValidationTab extends Tab {

    /** Default location of the CSV export; the folder is git-ignored because the data may not be distributed. */
    static final Path DEFAULT_FILE = Path.of("data", "herbivores.csv");

    /** Same colours as series 0-2 in app.css (deer, cattle, horses). */
    private static final String[] SPECIES_COLOURS = {"#c0661f", "#3b3b3b", "#2f7ec1"};

    private final LineChart<Number, Number> chart =
            Controls.lineChart("Observed (dots) vs simulated (lines), no wolves", "Year", "Number of animals");
    private final Label status = new Label();
    private final Label errors = new Label();
    private final Spinner<Integer> endYear = Controls.intSpinner(1990, 2100, 2017, 1);
    private final Spinner<Integer> holdOut = Controls.intSpinner(0, 20, 5, 1);
    private ObservedCounts observed;
    private ScenarioInputs last;

    ValidationTab() {
        super("Validation");
        setClosable(false);
        chart.setCreateSymbols(true);
        ((NumberAxis) chart.getXAxis()).setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number value) {
                return Integer.toString(value.intValue());
            }

            @Override
            public Number fromString(String text) {
                return Integer.parseInt(text);
            }
        });
        Button load = new Button("Load observed counts (CSV)…");
        load.setOnAction(e -> chooseFile());
        status.setWrapText(true);
        errors.setWrapText(true);
        errors.getStyleClass().add("verdict");
        endYear.valueProperty().addListener((obs, o, n) -> refresh());
        holdOut.valueProperty().addListener((obs, o, n) -> refresh());

        Label note = Controls.note("Uses the herbivore settings from the left panel at the baseline temperature, "
                + "with W₀ = 0. The default end year is 2017 because the 2017–2018 winter die-off, the "
                + "2018–2023 culls and the moving of horses are management events the model leaves out. "
                + "A model that fits a historical curve is not automatically a reliable forecast.");
        HBox controls = new HBox(8, load, new Label("Up to year"), endYear, new Label("Hold back last"), holdOut,
                new Label("years for checking"));
        VBox box = new VBox(8, controls, status, chart, errors, note);
        box.setPadding(new Insets(8));
        VBox.setVgrow(chart, Priority.ALWAYS);
        setContent(box);

        if (Files.exists(DEFAULT_FILE)) {
            load(DEFAULT_FILE);
        } else {
            status.setText("No data loaded. Export the 'Number of large herbivores' sheet to CSV "
                    + "(columns Year, Heck cattle, Konik horses, Red deer) and load it, or save it as "
                    + DEFAULT_FILE + ".");
        }
    }

    void update(ScenarioInputs inputs) {
        last = inputs;
        refresh();
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open herbivore counts");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            load(file.toPath());
            refresh();
        }
    }

    private void load(Path file) {
        try {
            observed = ObservedCounts.read(file);
            status.setText("Loaded " + observed.rows().size() + " years from " + file.getFileName() + ".");
        } catch (IOException e) {
            observed = null;
            status.setText("Could not read " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void refresh() {
        chart.getData().clear();
        errors.setText("");
        if (observed == null || last == null) {
            return;
        }
        List<ObservedCounts.Row> rows = observed.rows().stream()
                .filter(r -> r.year() <= endYear.getValue()).toList();
        ObservedCounts.Row first = rows.stream().filter(ObservedCounts.Row::complete).findFirst().orElse(null);
        if (first == null) {
            errors.setText("No year with counts for all three species up to " + endYear.getValue() + ".");
            return;
        }
        int years = endYear.getValue() - first.year();
        if (years < 1) {
            errors.setText("The end year must be after " + first.year() + ".");
            return;
        }
        ModelParameters p = ModelParameters.from(last.assumptions(), last.climate().withDeltaT(0), false);
        SimulationResult result = PopulationModel.simulate(
                new Scenario(first.toState(), 0, Math.min(years, Scenario.MAX_HORIZON), p));

        for (Species species : Species.values()) {
            XYChart.Series<Number, Number> sim = Controls.series(species.displayName() + " (model)");
            List<PopulationState> states = result.years();
            for (int t = 0; t < states.size(); t++) {
                sim.getData().add(new XYChart.Data<>(first.year() + t, states.get(t).get(species)));
            }
            chart.getData().add(sim);
            sim.getNode().setStyle("-fx-stroke: " + SPECIES_COLOURS[species.ordinal()] + ";");
            for (XYChart.Data<Number, Number> d : sim.getData()) {
                d.getNode().setVisible(false);
            }
        }
        for (Species species : Species.values()) {
            XYChart.Series<Number, Number> obs = Controls.series(species.displayName() + " (observed)");
            for (ObservedCounts.Row row : rows) {
                double value = switch (species) {
                    case DEER -> row.deer();
                    case CATTLE -> row.cattle();
                    case HORSES -> row.horses();
                };
                if (!Double.isNaN(value)) {
                    obs.getData().add(new XYChart.Data<>(row.year(), value));
                }
            }
            chart.getData().add(obs);
            // Observed counts: dots only, in the same colour as the species' model line.
            obs.getNode().setStyle("-fx-stroke: transparent;");
            String colour = SPECIES_COLOURS[species.ordinal()];
            for (XYChart.Data<Number, Number> d : obs.getData()) {
                d.getNode().setStyle("-fx-background-color: " + colour + ", " + colour + ";");
            }
        }
        for (javafx.scene.Node item : chart.lookupAll(".chart-legend-item")) {
            if (item instanceof Label label && label.getGraphic() != null) {
                for (Species species : Species.values()) {
                    if (label.getText().startsWith(species.displayName())) {
                        String colour = SPECIES_COLOURS[species.ordinal()];
                        String inner = label.getText().endsWith("(observed)") ? colour : "white";
                        label.getGraphic().setStyle("-fx-background-color: " + colour + ", " + inner + ";");
                    }
                }
            }
        }
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(rows.getFirst().year() - 1);
        xAxis.setUpperBound(endYear.getValue() + 1);
        xAxis.setTickUnit(5);

        int checkFrom = endYear.getValue() - holdOut.getValue() + 1;
        double[] fit = rmse(rows, result, first.year(), first.year() + 1, checkFrom - 1);
        double[] check = rmse(rows, result, first.year(), checkFrom, endYear.getValue());
        errors.setText(String.format(
                "Root-mean-square error of the total herbivore count: fit period %d–%d: %s (%d years); "
                        + "check period %d–%d: %s (%d years).",
                first.year() + 1, checkFrom - 1, fit[1] > 0 ? Controls.count(fit[0]) : "n/a", (int) fit[1],
                checkFrom, endYear.getValue(), check[1] > 0 ? Controls.count(check[0]) : "n/a", (int) check[1]));
    }

    /** Returns {rmse, number of years} of the total herbivore count for years in [from, to]. */
    private static double[] rmse(List<ObservedCounts.Row> rows, SimulationResult result, int startYear,
                                 int from, int to) {
        double sum = 0;
        int n = 0;
        for (ObservedCounts.Row row : rows) {
            int t = row.year() - startYear;
            if (row.year() < from || row.year() > to || !row.complete() || t < 0 || t >= result.years().size()) {
                continue;
            }
            double diff = result.years().get(t).totalHerbivores() - row.toState().totalHerbivores();
            sum += diff * diff;
            n++;
        }
        return new double[]{n == 0 ? 0 : Math.sqrt(sum / n), n};
    }
}
