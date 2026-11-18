package com.example.app.ui;

import java.util.OptionalInt;
import java.util.function.Function;

import com.example.app.model.AssumptionLevel;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Small factory helpers shared by the panels. */
final class Controls {

    private Controls() {
    }

    static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
        makeEditable(spinner);
        return spinner;
    }

    static Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
        makeEditable(spinner);
        return spinner;
    }

    /** Editable spinner that also commits typed text when it loses focus, and rejects bad input. */
    private static <T> void makeEditable(Spinner<T> spinner) {
        spinner.setEditable(true);
        spinner.setPrefWidth(110);
        spinner.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commit(spinner);
            }
        });
        spinner.getEditor().setOnAction(e -> commit(spinner));
    }

    private static <T> void commit(Spinner<T> spinner) {
        try {
            spinner.commitValue();
        } catch (RuntimeException e) {
            spinner.getEditor().setText(spinner.getValueFactory().getConverter().toString(spinner.getValue()));
        }
    }

    static ComboBox<AssumptionLevel> levelBox() {
        ComboBox<AssumptionLevel> box = new ComboBox<>(FXCollections.observableArrayList(AssumptionLevel.values()));
        box.setValue(AssumptionLevel.CENTRAL);
        box.setPrefWidth(110);
        return box;
    }

    static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        return label;
    }

    static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("note");
        return label;
    }

    static LineChart<Number, Number> lineChart(String title, String xLabel, String yLabel) {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel(xLabel);
        y.setLabel(yLabel);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        return chart;
    }

    static XYChart.Series<Number, Number> series(String name) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        return series;
    }

    static <S> TableColumn<S, String> column(String title, Function<S, String> value) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }

    static <S> TableView<S> table() {
        TableView<S> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    static String count(double value) {
        return String.format("%,.0f", value);
    }

    static String year(OptionalInt year) {
        return year.isPresent() ? "year " + year.getAsInt() : "never";
    }

    static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
