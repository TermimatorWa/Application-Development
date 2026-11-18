package com.example.app;

import com.example.app.ui.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Oostvaardersplassen population model: deer, cattle and horses with grass, wolves and temperature. */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new MainView(), 1280, 860);
        scene.getStylesheets().add(App.class.getResource("app.css").toExternalForm());
        stage.setTitle("Application Development – Oostvaardersplassen population model");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
