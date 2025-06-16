package com.betterme.controllers;

import MultimediaService.Multimedia;
import MultimediaService.MultimediaServiceGrpc;
import com.betterme.managers.MultimediaServiceManager;
import com.betterme.models.Post;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class ReportedPostsController {
    private final HttpClient client = HttpClient.newHttpClient();
    public ImageView imageArea;
    public Label titleLabel;
    public TextArea descriptionLabel;

    private String reason = null;
    private Post post = null;
    private String postId = null;

    private final String reportsUri = "http://localhost:6971/reports";
    private final String postsUri = "http://localhost:5017/posts";

    public ReportedPostsController() {
        getNextReportedPost();

        if (post != null) {
            setPostInfo();
        }
    }

    private void setPostInfo() {
        titleLabel.setText(post.getTitle().trim());
        descriptionLabel.setText(post.getDescription().trim());
        imageArea.setImage(new Image(new ByteArrayInputStream(post.getMultimedia())));
    }

    private void getNextReportedPost() {
        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(reportsUri))
                .header("Content-Type", "application/json")
                .header("Authorization", CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode respJson = new ObjectMapper().readTree(response.body());
                            postId = respJson.get("postId").asText();
                            reason = respJson.get("reason").asText();
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
                });

        if (postId == null) {
            return;
        }

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(postsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode respJson = new ObjectMapper().readTree(response.body());
                            post.setID(respJson.get("_id").asText());
                            post.setTitle(respJson.get("title").asText());
                            post.setDescription(respJson.get("description").asText());
                            post.setCategory(respJson.get("category").asText());
                            post.setStatus(respJson.get("status").asText());
                            post.setTimestamp(respJson.get("timestamp").asText());
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
                });

        if (post == null) {
            return;
        }

        MultimediaServiceManager manager = new MultimediaServiceManager();
        try {
            post.setMultimedia(manager.getPostMultimedia(post.getID()));
        } catch (Error ex) {
            showAlert("No se pudo obtener la imagen o video del post", Alert.AlertType.ERROR);
        }
    }
    
    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(imageArea.getScene().getWindow());
        a.showAndWait();
    }

    public void deletePost(ActionEvent actionEvent) {
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
                .header("Authorization", CurrentUser.jwt)
                .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
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
                    }
                })
                .exceptionally(ex -> {
                    return null;
                });

        if (post != null) {
            return;
        }

        try {
            JsonNode payload = mapper.createObjectNode()
                    .put("ok", "false");
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(reportsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", CurrentUser.jwt)
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

        nextReport();
    }

    public void rejectReport(ActionEvent actionEvent) {
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
                .uri(URI.create(reportsUri+"/"+postId))
                .header("Content-Type", "application/json")
                .header("Authorization", CurrentUser.jwt)
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
                .header("Authorization", CurrentUser.jwt)
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

        nextReport();
    }

    public void returnToMenu(ActionEvent actionEvent) {
    }

    private void nextReport() {
        getNextReportedPost();
        setPostInfo();
    }
}
