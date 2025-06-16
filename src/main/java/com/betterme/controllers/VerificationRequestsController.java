package com.betterme.controllers;

import com.betterme.ProgramConfigurations;
import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

public class VerificationRequestsController implements Initializable {
    @FXML
    private Label requestDateLabel;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label fullNameLabel;
    @FXML
    private Label ageLabel;
    @FXML
    private Label emailLabel;
    @FXML
    private ImageView identificationImageView;
    @FXML
    private ImageView certificateImageView;
    @FXML
    private VBox verificationRequestVBox;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private String token = CurrentUser.jwt;
    private String verificationRequestId;
    private String applicantUserId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadNextVerificationRequest();
    }

    private Image getImageFromProtectedUrl(String url) throws IOException, InterruptedException {
        String verificationsApiUrl = ProgramConfigurations.getConfiguration()
                                                          .getProperty("verificationsAPI.url");
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(verificationsApiUrl.concat("/").concat(url)))
                                         .header("Authorization", "Bearer " + this.token)
                                         .build();
        HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new Image(response.body());
    }

    private void loadVerificationRequestInformation() throws InterruptedException, IOException {
        String verificationsApiUrl = ProgramConfigurations.getConfiguration()
                                                          .getProperty("verificationsAPI.url");
        JsonNode payload = mapper.createObjectNode()
                                 .put("count", 1);
        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        HttpRequest getVerificationRequest = HttpRequest.newBuilder()
                                                        .uri(URI.create(verificationsApiUrl))
                                                        .method("GET", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                                                        .header("Content-Type", "application/json")
                                                        .header("Authorization", "Bearer ".concat(this.token))
                                                        .build();

        HttpResponse<String> response = client.send(getVerificationRequest, HttpResponse.BodyHandlers.ofString());

        switch (response.statusCode()) {
            case 200:
                JsonNode respJson;
                try {
                    respJson = mapper.readTree(response.body());
                }
                catch (JsonProcessingException error) {
                    showAlert("Ocurrió un error en el servidor", Alert.AlertType.ERROR);
                    return;
                }

                JsonNode verificationRequest = respJson.get("verificationRequests").get(0);
                if (verificationRequest == null) {
                    this.verificationRequestVBox.setVisible(false);
                    Platform.runLater(() -> showAlert("No hay solicitudes pendientes por evaluar", Alert.AlertType.INFORMATION));
                    return;
                }

                this.verificationRequestId = verificationRequest.get("_id").asText();
                this.applicantUserId = verificationRequest.get("userId").asText();
                this.requestDateLabel.setText(verificationRequest.get("date")
                                                                 .asText()
                                                                 .substring(0, 10));
                this.identificationImageView.setImage(getImageFromProtectedUrl(verificationRequest.get("identificationUrl").asText()));
                this.certificateImageView.setImage(getImageFromProtectedUrl(verificationRequest.get("certificateUrl").asText()));
                break;
            case 401:
                showAlert("Es necesario volver a iniciar sesión", Alert.AlertType.WARNING);
                break;
            default:
                showAlert("Ocurrió un error con la aplicación. Inténtelo de nuevo más tarde", Alert.AlertType.WARNING);
                break;
        }
    }

    private void loadApplicantInformation() {
        if (this.applicantUserId == null) {
            return;
        }

        String usersAPIUrl = ProgramConfigurations.getConfiguration()
                                                          .getProperty("usersAPI.url");

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(usersAPIUrl.concat("/").concat(this.applicantUserId)))
                                         .GET()
                                         .header("Authorization", "Bearer ".concat(this.token))
                                         .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenAcceptAsync(response -> {
                  switch (response.statusCode()) {
                      case 200:
                          JsonNode respJson;
                          try {
                              respJson = mapper.readTree(response.body());
                          }
                          catch (JsonProcessingException error) {
                              Platform.runLater(() -> showAlert("Ocurrió un error en el servidor", Alert.AlertType.ERROR));
                              return;
                          }

                          this.ageLabel.setText(respJson.get("birthday").asText().substring(0,10));
                          JsonNode accounInfo = respJson.get("account");
                          this.usernameLabel.setText(accounInfo.get("username").asText());
                          this.emailLabel.setText(accounInfo.get("email").asText());
                          this.fullNameLabel.setText(accounInfo.get("name").asText());
                          break;
                      case 404:
                          Platform.runLater(() -> showAlert("No fue posible cargar la información del usuario", Alert.AlertType.INFORMATION));
                          break;
                      case 401:
                          Platform.runLater(() -> showAlert("Es necesario volver a iniciar sesión 1", Alert.AlertType.INFORMATION));
                          break;
                      default:
                          Platform.runLater(() -> showAlert("Ocurrió un error con la aplicación. Inténtelo de nuevo más tarde", Alert.AlertType.WARNING));
                          break;
                  }
              })
              .exceptionally(ex -> {
                  Platform.runLater(() -> showAlert("No fue posible conectarse al servidor", Alert.AlertType.ERROR));
                  return null;
              });
    }

    @FXML
    private void onApproveVerification() {
        try {
            updateVerificationRequestState(true);
        }
        catch (IOException e) {
            showAlert("No fue posible conectarse con el servidor. Inténtelo más tarde", Alert.AlertType.WARNING);
            return;
        }
        catch (InterruptedException e) {
            showAlert("No fue posible completar la operación. Inténtelo más tarde", Alert.AlertType.WARNING);
            return;
        }
        loadNextVerificationRequest();
    }

    @FXML
    private void onDeniedVerification() {
        try {
            updateVerificationRequestState(false);
        }
        catch (IOException e) {
            showAlert("No fue posible conectarse con el servidor. Inténtelo más tarde", Alert.AlertType.WARNING);
            return;
        }
        catch (InterruptedException e) {
            showAlert("No fue posible completar la operación. Inténtelo más tarde", Alert.AlertType.WARNING);
            return;
        }
        loadNextVerificationRequest();
    }

    private void updateVerificationRequestState(boolean approved) throws IOException, InterruptedException {
        String jsonBody;
        try {
            JsonNode payload = mapper.createObjectNode()
                                     .put("approved", approved);
            jsonBody = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            showAlert("Ocurrió un error interno en la aplicación. Intente de nuevo más tarde", Alert.AlertType.ERROR);
            return;
        }

        String url = ProgramConfigurations.getConfiguration()
                                          .getProperty("verificationsAPI.url")
                                          .concat("/")
                                          .concat(this.verificationRequestId);
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(url))
                                         .header("Content-Type", "application/json")
                                         .header("Authorization", "Bearer ".concat(this.token))
                                         .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                                         .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        switch (response.statusCode()) {
           case 200:
               showAlert("La solicitud de verificación se ha " + (approved? "aprobado" : "rechazado") + " correctamente", Alert.AlertType.INFORMATION);
               break;
           case 401:
               showAlert("Es necesario volver a iniciar sesión 2", Alert.AlertType.WARNING);
               break;
           case 409:
               showAlert("La evaluación ya ha sido evaluada por otro moderador. En breve se cargará otra", Alert.AlertType.INFORMATION);
               break;
           default:
               showAlert("Ocurrió un error con la aplicación. Inténtelo de nuevo más tarde", Alert.AlertType.WARNING);
               break;
        }
    }

    private void loadNextVerificationRequest()  {
        try {
            loadVerificationRequestInformation();
        }
        catch (IOException e) {
            Platform.runLater(() -> showAlert("No fue posible conectarse con el servidor. Inténtelo más tarde", Alert.AlertType.WARNING));
            return;
        }
        catch (InterruptedException e) {
            Platform.runLater(() -> showAlert("No fue posible cargar la siguiente solicitud. Inténtelo más tarde", Alert.AlertType.WARNING));
            return;
        }

        loadApplicantInformation();
    }

    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(requestDateLabel.getScene().getWindow());
        a.showAndWait();
    }
}
