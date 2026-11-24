package com.example.app.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.app.data.HerbivoreWorkbook.ObservationRow;
import com.example.app.data.HistoricalDataValidator.ContextEvent;
import com.example.app.data.HistoricalDataValidator.Diagnostic;
import com.example.app.data.ObservedCounts;
import com.example.app.model.ModelParameters;
import com.example.app.model.PopulationModel;
import com.example.app.model.PopulationState;
import com.example.app.model.Scenario;
import com.example.app.model.SimulationResult;
import com.example.app.model.Species;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

/**
 * Historical Excel data review and the existing CSV comparison for the no-wolf model.
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
    private final Label excelStatus = Controls.note("Excel · No workbook selected.");
    private final Label excelSummary = Controls.note("");
    private final TableView<ObservationRow> population = Controls.table();
    private final TableView<Diagnostic> diagnostics = Controls.table();
    private final VBox sourceEvents = new VBox(8);
    private final LineChart<Number, Number> historicalChart =
            Controls.lineChart("Historical herbivore populations", "Year", "Number of animals");
    private final Label historicalChartStatus = Controls.note("");
    private HistoricalReview excelReview;
    private Task<HistoricalReview> pendingExcel;
    private final Spinner<Integer> endYear = Controls.intSpinner(1990, 2100, 2017, 1);
    private final Spinner<Integer> holdOut = Controls.intSpinner(0, 20, 5, 1);
    private ObservedCounts observed;
    private ScenarioInputs last;

    ValidationTab() {
        super("Validation");
        setClosable(false);
        TabPane views = new TabPane(new Tab("Excel data quality", excelPane()), new Tab("CSV comparison", csvPane()));
        views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setContent(views);
    }

    private VBox csvPane() {
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

        Label note = Controls.note("Simulations use the illustrative parameters selected on the left, "
                + "baseline temperature and W₀ = 0. The default end year is 2017, before the severe winter "
                + "mortality and later management interventions. Both comparison periods use the selected parameters.");
        HBox controls = new HBox(8, load, new Label("Up to year"), endYear, new Label("Hold back last"), holdOut,
                new Label("years for checking"));
        VBox box = new VBox(8, controls, status, chart, errors, note);
        box.setPadding(new Insets(8));
        VBox.setVgrow(chart, Priority.ALWAYS);
        if (Files.exists(DEFAULT_FILE)) {
            load(DEFAULT_FILE);
        } else {
            status.setText("CSV · No file loaded. Export the 'Number of large herbivores' sheet to CSV "
                    + "(columns Year, Heck cattle, Konik horses, Red deer) and load it, or save it as "
                    + DEFAULT_FILE + ".");
        }
        return box;
    }

    private VBox excelPane() {
        Button browse = new Button("Browse / Select Excel File");
        browse.setOnAction(event -> chooseExcelFile());
        population.getColumns().setAll(List.of(
                Controls.column("Year", row -> HistoricalReview.cellText(row.yearCell())),
                Controls.column("Heck cattle", row -> HistoricalReview.cellText(row.speciesCells().get(Species.CATTLE))),
                Controls.column("Konik horses", row -> HistoricalReview.cellText(row.speciesCells().get(Species.HORSES))),
                Controls.column("Red deer", row -> HistoricalReview.cellText(row.speciesCells().get(Species.DEER))),
                Controls.column("Reported total", row -> HistoricalReview.cellText(row.reportedTotal())),
                Controls.column("Source row", row -> HistoricalReview.location(row.worksheet(), row.yearCell().address()))));
        population.setPlaceholder(new Label("No Excel population observations to display."));
        diagnostics.getColumns().setAll(List.of(
                Controls.column("Severity", row -> row.severity().name()),
                Controls.column("Diagnostic code", row -> row.code().name()),
                Controls.column("Data block", row -> row.type() == null ? "" : row.type().name()),
                Controls.column("Year", row -> row.year().isPresent() ? Integer.toString(row.year().getAsInt()) : ""),
                Controls.column("Species", row -> row.species() == null ? "" : row.species().displayName()),
                Controls.column("Source cell", row -> HistoricalReview.location(row.worksheet(), row.address())),
                Controls.column("Description", Diagnostic::description)));
        diagnostics.setPlaceholder(new Label("No diagnostics to display."));
        TextArea detail = new TextArea();
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(3);
        detail.setPromptText("Select a diagnostic to read its full description and consequence.");
        detail.setAccessibleText("Selected diagnostic details");
        diagnostics.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> detail.setText(
                selected == null ? "" : selected.description() + "\n\n" + selected.consequence()));
        VBox diagnosticPane = new VBox(8, diagnostics, detail);
        VBox.setVgrow(diagnostics, Priority.ALWAYS);
        sourceEvents.setPadding(new Insets(8));
        ScrollPane notes = new ScrollPane(sourceEvents);
        notes.setFitToWidth(true);
        TabPane tables = new TabPane(new Tab("Observed populations", population),
                new Tab("Diagnostics", diagnosticPane), new Tab("Source events", notes),
                new Tab("Population chart", historicalChartPane()));
        tables.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label note = Controls.note("Historical observations are shown for inspection. Data-quality warnings do not "
                + "automatically invalidate observations. Ecological model parameters have not been calibrated using these data.");
        VBox pane = new VBox(8, Controls.heading("Historical Data Quality"), browse, excelStatus, excelSummary, note, tables);
        pane.setPadding(new Insets(8));
        VBox.setVgrow(tables, Priority.ALWAYS);
        return pane;
    }

    private VBox historicalChartPane() {
        historicalChart.setLegendVisible(false);
        historicalChart.setCreateSymbols(true);
        historicalChart.setAxisSortingPolicy(LineChart.SortingPolicy.NONE);
        var years = (NumberAxis) historicalChart.getXAxis();
        years.setAutoRanging(false);
        years.setMinorTickVisible(false);
        years.setTickLabelFormatter(new NumberAxis.DefaultFormatter(years, null, null) {
            @Override
            public String toString(Number value) {
                return Long.toString(value.longValue());
            }
        });
        FlowPane legend = new FlowPane(14, 5);
        for (Species species : HistoricalChartData.SPECIES_ORDER) {
            legend.getChildren().add(new Label(species.displayName(), chartMarker(species, false)));
        }
        legend.getChildren().add(new Label("Qualitative source warning", chartMarker(Species.CATTLE, true)));
        Label explanation = Controls.note("Historical observations are displayed without interpolation or "
                + "parameter fitting. Source reliability warnings are qualitative.");
        VBox pane = new VBox(8, Controls.heading("Observed historical data"), legend, explanation,
                historicalChartStatus, historicalChart);
        pane.setPadding(new Insets(8));
        VBox.setVgrow(historicalChart, Priority.ALWAYS);
        showHistoricalChart(null);
        return pane;
    }

    private void showHistoricalChart(HistoricalReview review) {
        historicalChart.getData().clear();
        HistoricalChartData data = HistoricalChartData.from(review);
        boolean available = data.unavailableReason() == null;
        historicalChart.setVisible(available);
        historicalChart.setManaged(available);
        historicalChartStatus.setText(available ? "" : data.unavailableReason());
        historicalChartStatus.setManaged(!available);
        if (!available) {
            return;
        }
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (HistoricalChartData.Segment segment : data.segments()) {
            Species species = segment.species();
            var series = Controls.series(species.displayName());
            for (HistoricalChartData.Point point : segment.points()) {
                var plotted = new XYChart.Data<Number, Number>(point.year(), point.count());
                var marker = chartMarker(species, point.reliabilityWarning() != null);
                String description = point.year() + " · " + species.displayName() + " · " + point.count()
                        + "\n" + HistoricalReview.location(point.source().worksheet(), point.source().address());
                if (point.reliabilityWarning() != null) {
                    description += "\n" + point.reliabilityWarning();
                }
                marker.setAccessibleHelp(description);
                Tooltip.install(marker, new Tooltip(description));
                plotted.setNode(marker);
                series.getData().add(plotted);
                first = Math.min(first, point.year());
                last = Math.max(last, point.year());
            }
            historicalChart.getData().add(series);
            series.getNode().setStyle("-fx-stroke: " + SPECIES_COLOURS[species.ordinal()] + ";");
        }
        var years = (NumberAxis) historicalChart.getXAxis();
        int step = Math.max(1, (int) Math.ceil(((double) last - first) / 45) * 5);
        years.setTickUnit(step);
        years.setLowerBound(first - 1.0);
        years.setUpperBound(last + 1.0);
    }

    private static StackPane chartMarker(Species species, boolean flagged) {
        var colour = Color.web(SPECIES_COLOURS[species.ordinal()]);
        var shape = flagged ? new Rectangle(8, 8, colour) : new Circle(3, colour);
        shape.setStroke(Color.WHITE);
        if (flagged) {
            shape.setRotate(45);
        }
        StackPane marker = new StackPane(shape);
        marker.setStyle("-fx-background-color: transparent; -fx-padding: 3;");
        return marker;
    }

    private void chooseExcelFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select historical herbivore workbook");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbooks", "*.xlsx"));
        Path rawDirectory = Path.of("data", "raw");
        if (Files.isDirectory(rawDirectory)) {
            chooser.setInitialDirectory(rawDirectory.toFile());
        }
        File selected = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (selected != null) {
            loadExcel(selected.toPath());
        }
    }

    private void loadExcel(Path selectedPath) {
        if (pendingExcel != null) {
            pendingExcel.cancel();
        }
        showExcelReview(null);
        String source = "Excel · " + selectedPath.getFileName() + " · ";
        excelStatus.setText(source + "Loading…");
        Task<HistoricalReview> task = new Task<>() {
            @Override
            protected HistoricalReview call() throws IOException {
                return HistoricalReview.load(selectedPath);
            }
        };
        pendingExcel = task;
        task.setOnSucceeded(event -> {
            if (pendingExcel == task) {
                pendingExcel = null;
                showExcelReview(task.getValue());
                excelStatus.setText(source + excelReview.reviewStatus());
            }
        });
        task.setOnFailed(event -> {
            if (pendingExcel == task) {
                pendingExcel = null;
                excelStatus.setText(source + "Could not load workbook. " + task.getException().getMessage());
            }
        });
        Thread worker = new Thread(task, "historical-excel-review");
        worker.setDaemon(true);
        worker.start();
    }

    private void showExcelReview(HistoricalReview review) {
        excelReview = review;
        showHistoricalChart(review);
        population.getItems().clear();
        diagnostics.getItems().clear();
        sourceEvents.getChildren().clear();
        excelSummary.setText("");
        if (review == null) {
            return;
        }
        excelSummary.setText(review.summary());
        population.getItems().setAll(review.population());
        diagnostics.getItems().setAll(review.report().diagnostics());
        for (ContextEvent event : review.report().events()) {
            var note = event.note();
            String location = HistoricalReview.location(note.worksheet(), note.address());
            String dates = HistoricalReview.dates(note);
            sourceEvents.getChildren().add(Controls.heading(dates.isEmpty() ? location : location + " · " + dates));
            sourceEvents.getChildren().add(Controls.note(note.text()));
        }
        if (review.report().events().isEmpty()) {
            sourceEvents.getChildren().add(Controls.note("No contextual events reported."));
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
            status.setText("CSV · " + file.getFileName() + " · Loaded " + observed.rows().size() + " years.");
        } catch (IOException e) {
            observed = null;
            status.setText("CSV · " + file.getFileName() + " · Could not load. " + e.getMessage());
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
        double[] comparison = rmse(rows, result, first.year(), first.year() + 1, checkFrom - 1);
        double[] check = rmse(rows, result, first.year(), checkFrom, endYear.getValue());
        errors.setText(String.format(
                "Root-mean-square error of the total herbivore count: earlier comparison %d–%d: %s (%d years); "
                        + "check period %d–%d: %s (%d years).",
                first.year() + 1, checkFrom - 1, comparison[1] > 0 ? Controls.count(comparison[0]) : "n/a",
                (int) comparison[1],
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
