package com.betterme.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class NewModAccountController {
    @FXML private DatePicker dpBirthday;
    @FXML private TextField tfFullName;
    @FXML private TextField tfUsername;
    @FXML private PasswordField pfPassword;
    @FXML private PasswordField pfRepeatPassword;
    @FXML private TextField tfEmail;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    private static final Pattern PASSWORD_REGEX = Pattern.compile(
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).{8,}$"
    );

    @FXML
    public void onCreateAccount(ActionEvent actionEvent) {
        if (!validateInputs()) {
            return;
        }

        Map<String,Object> payload = new HashMap<>();
        payload.put("username",  tfUsername.getText().trim());
        payload.put("password",  pfPassword.getText());
        payload.put("email",     tfEmail.getText().trim());
        payload.put("name",      tfFullName.getText().trim());
        payload.put("birthday",  dpBirthday.getValue().format(dateFmt));
        // optional fields
        payload.put("description", "");
        payload.put("phone",       "");
        payload.put("website",     "");

        try {
            String json = mapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:6969/api/users/moderator"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response.statusCode() == 201) {
                            showAlert("¡Éxito!", "Usuario moderador creado correctamente", Alert.AlertType.INFORMATION);
                        } else {
                            showAlert("Error", "API respondió: " + response.statusCode(), Alert.AlertType.ERROR);
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() ->
                                showAlert("Error", "Fallo al conectar: " + ex.getMessage(), Alert.AlertType.ERROR)
                        );
                        return null;
                    });
        } catch (Exception e) {
            showAlert("Error", "No se pudo serializar datos: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private boolean validateInputs() {
        String user = tfUsername.getText();
        String email = tfEmail.getText();
        String pwd  = pfPassword.getText();
        String rpt  = pfRepeatPassword.getText();
        String name = tfFullName.getText();
        var date    = dpBirthday.getValue();

        if (isBlank(user) || isBlank(email) || isBlank(pwd) || isBlank(rpt) || isBlank(name) || date == null) {
            showAlert("Error", "Todos los campos son obligatorios", Alert.AlertType.ERROR);
            return false;
        }
        if (!EMAIL_REGEX.matcher(email.trim()).matches()) {
            showAlert("Error", "Email no válido", Alert.AlertType.ERROR);
            return false;
        }
        if (!PASSWORD_REGEX.matcher(pwd).matches()) {
            showAlert("Error",
                    "La contraseña debe tener mínimo 8 caracteres,\n" +
                            "1 mayúscula, 1 minúscula, 1 dígito y 1 carácter especial",
                    Alert.AlertType.ERROR);
            return false;
        }
        if (!pwd.equals(rpt)) {
            showAlert("Error", "Las contraseñas no coinciden", Alert.AlertType.ERROR);
            return false;
        }
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
