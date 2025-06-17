package com.betterme.controllers;

import com.betterme.sessionData.AppContext;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

public class MainWindow {
    public BorderPane mainPane;

    @FXML
    public void initialize() {
        AppContext.setMainPane(mainPane);
    }
}
