package com.example.app.ui;

import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/** Inputs on the left, results in tabs on the right. Every change in the inputs re-runs the model. */
public final class MainView extends BorderPane {

    private final PopulationTab populationTab = new PopulationTab();
    private final WolfScenarioTab wolfTab = new WolfScenarioTab();
    private final ClimateTab climateTab = new ClimateTab();
    private final SensitivityTab sensitivityTab = new SensitivityTab();
    private final ValidationTab validationTab = new ValidationTab();
    private final ControlPanel controls = new ControlPanel(this::refresh);

    public MainView() {
        TabPane tabs = new TabPane(populationTab, wolfTab, climateTab, sensitivityTab, validationTab,
                new LimitationsTab());
        setLeft(controls);
        setCenter(tabs);
        refresh();
    }

    private void refresh() {
        ScenarioInputs inputs = controls.inputs();
        populationTab.update(inputs);
        wolfTab.update(inputs);
        climateTab.update(inputs);
        sensitivityTab.update(inputs);
        validationTab.update(inputs);
    }
}
