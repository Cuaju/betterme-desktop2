package com.betterme.controllers;

import com.betterme.sessionData.AppContext;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;

import java.io.IOException;

public class MainMenuController {
    public void onEvaluateVerificationRequests() {
        try {
            changeView("/views/VerificationRequestsView.fxml");
        }
        catch (IOException e) {
            showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    public void changeView(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
        Parent root = loader.load();
        AppContext.getMainPane().setCenter(root);
    }

    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(AppContext.getMainPane().getScene().getWindow());
        a.showAndWait();
    }

    public void onEvaluateReportedPosts(ActionEvent actionEvent) {
        try {
            changeView("/views/ReportedPostsView.fxml");
        }
        catch (IOException e) {
            showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    public void onCreateModAccount(ActionEvent actionEvent) {
        try {
            changeView("/views/NewModAccountView.fxml");
        }
        catch (IOException e) {
            showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    public void onUnbanAccounts(ActionEvent actionEvent) {
        try {
            changeView("/views/ManageAccountsView.fxml");
        }
        catch (IOException e) {
            showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte. " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }
}
