package com.betterme.controllers;

import MultimediaService.Multimedia;
import MultimediaService.MultimediaServiceGrpc;
import com.betterme.ProgramConfigurations;
import com.betterme.managers.MultimediaServiceManager;
import com.betterme.models.Post;
import com.betterme.sessionData.AppContext;
import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

public class ReportedPostsController implements Initializable  {
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
        getNextReportedPost();

        if (post != null) {
            setPostInfo();
        }
        else {
            showAlert("No hay publicaciones reportadas", Alert.AlertType.INFORMATION);
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

    private void getNextReportedPost() {
        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(reportsUri))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode respJson = new ObjectMapper().readTree(response.body());
                            reportId = respJson.get("id").asText();
                            postId = respJson.get("postId").asText();
                            reason = respJson.get("reason").asText();
                        } catch (JsonProcessingException e) {
                            Platform.runLater(() ->
                                    showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    } else if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                                showAlert("No está autorizado para acceder a esta información: 401", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 403) {
                        Platform.runLater(() ->
                                showAlert("No está autorizado para acceder a esta información: 403", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 404) {
                        Platform.runLater(() ->
                                showAlert("No se encontró el recurso", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 500) {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente más tarde", Alert.AlertType.ERROR)
                        );
                    } else {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error inesperado, intente más tarde. Código: " + response.statusCode(), Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                        showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();

        if (postId == null) {
            showAlert("no se obtuvo el id", Alert.AlertType.ERROR);
            return;
        }

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(postsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " +CurrentUser.jwt)
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
                            Platform.runLater(() ->
                                showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    } else if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                            showAlert("No está autorizado para acceder a esta información", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 500) {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente más tarde", Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                        showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();

        if (post == null) {
            showAlert("no se obtuvo el post", Alert.AlertType.ERROR);
            return;
        }

        MultimediaServiceManager manager = new MultimediaServiceManager();
        try {
            post.setMultimedia(manager.getPostMultimedia(post.getID()));
        } catch (Error | InterruptedException | IOException ex) {
            showAlert("No se pudo obtener la imagen o video del post", Alert.AlertType.ERROR);
        }

        if (post.getMultimedia() == null) {
            showAlert("la imagen es nula", Alert.AlertType.ERROR);
        } else if (post.getMultimedia().length == 0) {
            showAlert("la imagen está vacía", Alert.AlertType.ERROR);
        }
    }
    
    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(AppContext.getMainPane().getScene().getWindow());
        a.showAndWait();
    }

    public void deletePost(ActionEvent actionEvent) {
        if (post == null) {
            showAlert("no se puede borrar la publicación no existente", Alert.AlertType.ERROR);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        String jsonBody;
        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("state", "Deleted");
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(postsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " +CurrentUser.jwt)
                .method("Delete", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        post = null;
                        Platform.runLater(() ->
                            showAlert("Publicación borrada correctamente", Alert.AlertType.INFORMATION)
                        );
                    } else if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 404) {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                        );
                    } else {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error de nuestro lado, intente nuevamente: código " + response.statusCode(), Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    return null;
                }).join();

        if (post != null) {
            showAlert("Error al borrar la publicación", Alert.AlertType.ERROR);
            return;
        }

        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("ok", false);
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(reportsUri+"/"+reportId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " +CurrentUser.jwt)
                .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                            showAlert("No está autorizado para acceder a esta información", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 500) {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente más tarde", Alert.AlertType.ERROR)
                        );
                    } else {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente más tarde: código " + response.statusCode(), Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();

        showAlert("Post borrado", Alert.AlertType.INFORMATION);
        resetPostInfo();
        nextReport();
    }

    public void rejectReport(ActionEvent actionEvent) {
        if (post == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        String jsonBody;
        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("ok", "true");
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(reportsUri+"/"+reportId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " +CurrentUser.jwt)
                .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                                showAlert("No está autorizado para acceder a esta información", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 500) {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error de nuestro lado, intente más tarde", Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                });

        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("state", "Published");
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(postsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " +CurrentUser.jwt)
                .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        post = null;
                    } else if (response.statusCode() == 401) {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                        );
                    } else if (response.statusCode() == 404) {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Ocurrió un error de nuestro lado, intente nuevamente", Alert.AlertType.ERROR)
                    );
                    return null;
                });

        resetPostInfo();
        nextReport();
    }

    public void returnToMenu(ActionEvent actionEvent) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MainMenuView.fxml"));
            Parent root = loader.load();
            AppContext.getMainPane().setCenter(root);
        }
        catch (IOException e) {
            showAlert("Ocurrió un error interno en la aplicación. Contacte a soporte.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void nextReport() {
        getNextReportedPost();
        setPostInfo();
    }
}
