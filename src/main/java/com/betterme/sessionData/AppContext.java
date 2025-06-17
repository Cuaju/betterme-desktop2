package com.betterme.sessionData;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;

public class AppContext {
    private static BorderPane mainPane;

    public static void setMainPane(BorderPane pane) {
        mainPane = pane;
    }

    public static BorderPane getMainPane() {
        return mainPane;
    }
}