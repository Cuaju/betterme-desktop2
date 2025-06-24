package com.betterme.controllers;

import com.betterme.ProgramConfigurations;
import com.betterme.models.User;
import com.betterme.sessionData.AppContext;
import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

public class UnbanAccountsView implements Initializable {
    public TableView usersTable;

    private final HttpClient client = HttpClient.newHttpClient();

    private List<User> users;
    private final String usersUri = ProgramConfigurations.getConfiguration()
        .getProperty("usersAPI.url");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getBannedAccounts();

        if (!users.isEmpty()) {
            updateTable();
        }
    }

    private void getBannedAccounts() {
        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(usersUri + "/banned"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            users = new ObjectMapper().readValue(response.body(), new TypeReference<>() {});
                        } catch (JsonProcessingException e) {
                            Platform.runLater(() ->
                                showAlert("Ocurrió un error recibiendo los datos, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    }
                })
                .exceptionally(ex -> {
            Platform.runLater(() ->
                    showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
            );
            return null;
        }).join();
    }

    private void updateTable() {
        TableColumn<User, String> userColumn = new TableColumn<>("Usuario");
        TableColumn<User, String> idColumn = new TableColumn<>("ID");
        TableColumn<User, String> nameColumn = new TableColumn<>("Nombre");

        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        usersTable.getColumns().addAll(idColumn, userColumn, nameColumn);

        TableColumn<User, Void> unbanButtonColumn = new TableColumn<>("Reactivar");

        Callback<TableColumn<User, Void>, TableCell<User, Void>> cellFactory = new Callback<TableColumn<User, Void>, TableCell<User, Void>>() {
            @Override
            public TableCell<User, Void> call(TableColumn<User, Void> userVoidTableColumn) {
                final TableCell<User, Void> cell = new TableCell<User, Void>() {

                    private final Button btn = new Button("Reactivar");

                    {
                        btn.setOnAction((ActionEvent event) -> {
                            User data = getTableView().getItems().get(getIndex());

                            var alert = new Alert(Alert.AlertType.CONFIRMATION);
                            alert.setTitle("Reactivar usuario");
                            alert.setHeaderText("Reactivar usuario");
                            alert.setContentText("¿Está seguro que desea reactivar la cuenta de " + data.getName() + "?");
                            alert.showAndWait().ifPresent((btnType) -> {
                                if (btnType == ButtonType.OK) {
                                    unbanAccount(data.getId());
                                    users.remove(getIndex());
                                    getTableView().getItems().remove(getIndex());
                                }
                            });
                        });
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }
                };
                return cell;
            }
        };

        unbanButtonColumn.setCellFactory(cellFactory);
        usersTable.getColumns().add(unbanButtonColumn);

        ObservableList<User> usersFx = FXCollections.observableArrayList(users);
        usersTable.setItems(usersFx);
    }

    private void unbanAccount(String accountId) {
        User userInfo = getUserInfo(accountId);
        userInfo.setActive(true);

        HttpRequest reportRequest = null;
        try {
            reportRequest = HttpRequest.newBuilder()
                    .uri(URI.create(usersUri + "/" + accountId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + CurrentUser.jwt)
                    .PUT(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(userInfo)))
                    .build();
        } catch (JsonProcessingException e) {
            showAlert("Ocurrió un error enviando los datos, intente nuevamente", Alert.AlertType.ERROR);
        }

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        showAlert("Usuario reactivado correctamente", Alert.AlertType.INFORMATION);
                    } else {
                        showAlert("Ocurrió un error", Alert.AlertType.ERROR);
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();
    }

    private User getUserInfo(String accountId) {
        AtomicReference<User> userInfo = new AtomicReference<>();

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(usersUri + "/" + accountId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            userInfo.set(new ObjectMapper().readValue(response.body(), User.class));
                        } catch (JsonProcessingException e) {
                            Platform.runLater(() ->
                                    showAlert("Ocurrió un error recibiendo los datos, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();

        return userInfo.get();
    }

    private void showAlert(String text, Alert.AlertType type) {
        Alert a = new Alert(type, text);
        a.initOwner(AppContext.getMainPane().getScene().getWindow());
        a.showAndWait();
    }
}
