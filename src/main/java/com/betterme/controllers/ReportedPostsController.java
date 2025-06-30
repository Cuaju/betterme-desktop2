package com.betterme.controllers;

import com.betterme.ProgramConfigurations;
import com.betterme.managers.MultimediaServiceManager;
import com.betterme.models.Post;
import com.betterme.sessionData.AppContext;
import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportedPostsController implements Initializable {
  private final HttpClient client = HttpClient.newHttpClient();
  public ImageView imageArea;
  public Label titleLabel;
  public TextArea descriptionLabel;

  private String reason = null;
  private Post post = null;
  private String postId = null;
  private String reportId = null;

  private final String reportsUri = ProgramConfigurations.getConfiguration()
      .getProperty("reportsAPI.url");
  private final String postsUri = ProgramConfigurations.getConfiguration()
      .getProperty("postsAPI.url");

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    switch (getNextReportedPost()) {
      case 0:
        setPostInfo();
        break;
      case 1:
        showAlert("No hay publicaciones reportadas.", Alert.AlertType.INFORMATION);
        break;
      case 3:
        showAlert("No se pudo obtener la imagen de la publicación, intente nuevamente", Alert.AlertType.WARNING);
        break;
      case 401 | 403:
        showAlert("No está autorizado para acceder a esta información.", Alert.AlertType.ERROR);
        break;
      case 500:
        showAlert("Ocurrió un error en el servidor, intente nuevamente.", Alert.AlertType.ERROR);
        break;
      default:
        showAlert("Ocurrió un error inesperado, intente nuevamente", Alert.AlertType.ERROR);
    }
  }

  private void setPostInfo() {
    titleLabel.setText(post.getTitle().trim());
    descriptionLabel.setText(post.getDescription().trim());
    showImage();
  }

  private void resetPostInfo() {
    titleLabel.setText("");
    descriptionLabel.setText("");
    imageArea.setImage(null);
  }

  private void showImage() {
    Image image = new Image(new ByteArrayInputStream(post.getMultimedia()));
    imageArea.setImage(image);
  }

  private int getNextReportedPost() {
    HttpRequest reportRequest = HttpRequest.newBuilder()
        .uri(URI.create(reportsUri))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + CurrentUser.jwt)
        .GET()
        .build();

    AtomicInteger result = new AtomicInteger(0);

    client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
        .thenAcceptAsync(response -> {
          if (response.statusCode() == 200) {
            try {
              JsonNode respJson = new ObjectMapper().readTree(response.body());
              reportId = respJson.get("id").asText();
              postId = respJson.get("postId").asText();
              reason = respJson.get("reason").asText();
            } catch (JsonProcessingException e) {
                  result.set(2);
            }
          } else {
            result.set(response.statusCode());
          }
        })
        .exceptionally(ex -> {
          result.set(2);
          return null;
        }).join();

    if (postId == null) {
      return result.get();
    }

    HttpRequest postRequest = HttpRequest.newBuilder()
        .uri(URI.create(postsUri + "/" + postId))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + CurrentUser.jwt)
        .GET()
        .build();

    client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
        .thenAcceptAsync(response -> {
          if (response.statusCode() == 200) {
            try {
              JsonNode respJson = new ObjectMapper().readTree(response.body());
              post = new Post();
              post.setID(respJson.get("id").asText());
              post.setTitle(respJson.get("title").asText());
              post.setDescription(respJson.get("description").asText());
              post.setCategory(respJson.get("category").asText());
              post.setStatus(respJson.get("status").asText());
              post.setTimestamp(respJson.get("timestamp").asText());
              post.setUserID(respJson.get("userId").asText());
            } catch (JsonProcessingException e) {
              result.set(2);
            }
          } else {
            result.set(response.statusCode());
          }
        })
        .exceptionally(ex -> {
          result.set(2);
          return null;
        }).join();

    if (post == null) {
      return result.get();
    }

    MultimediaServiceManager manager = new MultimediaServiceManager();
    try {
      post.setMultimedia(manager.getPostMultimedia(post.getID()));
    } catch (Error | InterruptedException | IOException ex) {
      result.set(3);
    }

    return result.get();
  }

  private void showAlert(String text, Alert.AlertType type) {
    Alert a = new Alert(type, text);
    a.initOwner(AppContext.getMainPane().getScene().getWindow());
    a.showAndWait();
  }

  public void deletePost(ActionEvent actionEvent) {
    dictaminateReport(true);
  }

  public void rejectReport(ActionEvent actionEvent) {
    dictaminateReport(false);
  }

  private void dictaminateReport(boolean accepted) {
    boolean postOk;
    String postStatus;
    String successMessage;

    if (accepted) {
      postOk = false;
      postStatus = "Deleted";
      successMessage = "Publicación borrada";
    } else {
      postOk = true;
      postStatus = "Published";
      successMessage = "Reporte rechazado.";
    }

    if (post == null) {
      return;
    }

    switch (updateReportStatus(postOk)) {
      case 0:
        break;
      case 401 | 403:
        showAlert("No está autorizado para acceder a esta información.", Alert.AlertType.ERROR);
        return;
      case 500:
        showAlert("Ocurrió un error en el servidor, intente nuevamente.", Alert.AlertType.ERROR);
        return;
      default:
        showAlert("Ocurrió un error inesperado, intente nuevamente.", Alert.AlertType.ERROR);
        return;
    }

    switch (updatePostStatus(postStatus)) {
      case 0:
        break;
      case 401 | 403:
        showAlert("No está autorizado para acceder a esta información.", Alert.AlertType.ERROR);
        return;
      case 500:
        showAlert("Ocurrió un error en el servidor, intente nuevamente.", Alert.AlertType.ERROR);
        return;
      default:
        showAlert("Ocurrió un error inesperado, intente nuevamente.", Alert.AlertType.ERROR);
        return;
    }

    showAlert(successMessage, Alert.AlertType.INFORMATION);
    resetPostInfo();
    nextReport();
  }

  private int updatePostStatus(String status) {
    ObjectMapper mapper = new ObjectMapper();

    String jsonBody;
    try {
      JsonNode payload = mapper.createObjectNode()
              .put("state", status);
      jsonBody = mapper.writeValueAsString(payload);
    } catch (Exception e) {
      return 2;
    }

    HttpRequest postRequest = HttpRequest.newBuilder()
            .uri(URI.create(postsUri + "/" + postId))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + CurrentUser.jwt)
            .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

    AtomicInteger result = new AtomicInteger(0);

    client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
            .thenAcceptAsync(response -> {
              if (response.statusCode() == 200) {
                post = null;
              } else {
                result.set(response.statusCode());
              }
            })
            .exceptionally(ex -> {
              result.set(2);
              return null;
            });

    return result.get();
  }

  private int updateReportStatus(boolean postOk) {
    ObjectMapper mapper = new ObjectMapper();

    String jsonBody;
    try {
      JsonNode payload = mapper.createObjectNode()
              .put("ok", postOk);
      jsonBody = mapper.writeValueAsString(payload);
    } catch (Exception e) {
      return 2;
    }

    AtomicInteger result = new AtomicInteger(0);

    HttpRequest reportRequest = HttpRequest.newBuilder()
            .uri(URI.create(reportsUri + "/" + reportId))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + CurrentUser.jwt)
            .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

    client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
            .thenAcceptAsync(response -> {
              if (response.statusCode() == 200) {
                result.set(0);
              } else {
                result.set(response.statusCode());
              }
            })
            .exceptionally(ex -> {
              result.set(2);
              return null;
            });

    return result.get();
  }

  public void returnToMenu(ActionEvent actionEvent) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MainMenuView.fxml"));
      Parent root = loader.load();
      AppContext.getMainPane().setCenter(root);
    } catch (IOException e) {
      showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte.", Alert.AlertType.ERROR);
      e.printStackTrace();
    }
  }

  private void nextReport() {
    getNextReportedPost();
    setPostInfo();
  }
}
