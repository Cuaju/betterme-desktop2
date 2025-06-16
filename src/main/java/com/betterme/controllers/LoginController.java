package com.betterme.controllers;

import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class LoginController {
    @FXML
    private TextField emailField;
    @FXML private PasswordField passwordField;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @FXML
    private void onLogin() {
        String username = emailField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Both fields are required", Alert.AlertType.WARNING);
            return;
        }

        // this is to create the JSON body of the request
        String jsonBody;
        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("username", username)
                    .put("password", password);
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        // Guys this is to Build HTTP request
        // on later things you will need to pput another header for the token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:6968/api/authentication/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode respJson = mapper.readTree(response.body());
                            String token = respJson.get("accessToken").asText();
                            Platform.runLater(() -> {
                                // Clear fields
                                emailField.clear();
                                passwordField.clear();
                                emailField.setText(token);
                                CurrentUser.jwt = token;
                                showAlert("Logged in! Token:\n" + token, Alert.AlertType.INFORMATION);
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() ->
                                    showAlert("Invalid response from server", Alert.AlertType.ERROR)
                            );
                        }
                    } else if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                                showAlert("Bad credentials", Alert.AlertType.WARNING)
                        );
                    } else {
                        Platform.runLater(() ->
                                showAlert("Server error (" + response.statusCode() + ")", Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Network error: " + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                });
    }

    //shit for alerts you guys alr know this
    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(emailField.getScene().getWindow());
        a.showAndWait();
    }
}
