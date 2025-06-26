package com.betterme.controllers;

import com.betterme.ProgramConfigurations;
import com.betterme.models.User;
import com.betterme.models.UserAccount;
import com.betterme.sessionData.AppContext;
import com.betterme.sessionData.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

public class UnbanAccountsController implements Initializable {
    public TableView usersTable;

    private final HttpClient client = HttpClient.newHttpClient();
    public TextField emailTextBox;

    private List<UserAccount> users;
    private final String usersUri = ProgramConfigurations.getConfiguration()
        .getProperty("usersAPI.url");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getBannedAccounts();

        if (users != null && !users.isEmpty()) {
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
                            System.out.println(response.body());
                            users = new ObjectMapper().readValue(response.body(), new TypeReference<>() {});
                        } catch (JsonProcessingException e) {
                            System.out.println(e.getMessage());
                            Platform.runLater(() ->
                                showAlert("Ocurrió un error recibiendo los datos, intente nuevamente: ", Alert.AlertType.ERROR)
                            );
                        }
                    } else {
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error recibiendo los datos, intente nuevamente: ", Alert.AlertType.ERROR)
                        );
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
        TableColumn<UserAccount, String> userColumn = new TableColumn<>("Usuario");
        TableColumn<UserAccount, String> idColumn = new TableColumn<>("ID");
        TableColumn<UserAccount, String> nameColumn = new TableColumn<>("Nombre");

        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        usersTable.getColumns().addAll(idColumn, userColumn, nameColumn);

        TableColumn<UserAccount, Void> unbanButtonColumn = new TableColumn<>("Reactivar");

        Callback<TableColumn<UserAccount, Void>, TableCell<UserAccount, Void>> cellFactory = new Callback<TableColumn<UserAccount, Void>, TableCell<UserAccount, Void>>() {
            @Override
            public TableCell<UserAccount, Void> call(TableColumn<UserAccount, Void> userVoidTableColumn) {
                final TableCell<UserAccount, Void> cell = new TableCell<UserAccount, Void>() {

                    private final Button btn = new Button("Reactivar");

                    {
                        btn.setOnAction((ActionEvent event) -> {
                            UserAccount data = getTableView().getItems().get(getIndex());

                            var alert = new Alert(Alert.AlertType.CONFIRMATION);
                            alert.setTitle("Reactivar usuario");
                            alert.setHeaderText("Reactivar usuario");
                            alert.setContentText("¿Está seguro que desea reactivar la cuenta de " + data.getName() + "?");
                            alert.showAndWait().ifPresent((btnType) -> {
                                if (btnType == ButtonType.OK) {
                                    updateAccountState(data.getId(), true);
                                    users.remove(getIndex());
                                    getTableView().getItems().remove(getIndex());
                                    alert.close();
                                } else {
                                    alert.close();
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

        ObservableList<UserAccount> usersFx = FXCollections.observableArrayList(users);
        usersTable.setItems(usersFx);
    }

    private void updateAccountState(String accountId, boolean state) {
        JsonNode payload = new ObjectMapper().createObjectNode()
                .put("active", state);

        HttpRequest reportRequest = null;
        try {
            reportRequest = HttpRequest.newBuilder()
                    .uri(URI.create(usersUri + "/" + accountId + "/state"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + CurrentUser.jwt)
                    .PUT(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(payload)))
                    .build();
        } catch (JsonProcessingException e) {
            showAlert("Ocurrió un error enviando los datos, intente nuevamente", Alert.AlertType.ERROR);
        }

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        Platform.runLater(() ->
                            showAlert("Usuario modificado correctamente", Alert.AlertType.INFORMATION)
                        );
                    } else {
                        System.out.println(response.body());
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error: " + response.body(), Alert.AlertType.ERROR)
                        );
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
                            JsonNode respJson = new ObjectMapper().readTree(response.body());
                            User user = new User();
                            user.setVerified(respJson.get("verified").asBoolean());
                            userInfo.set(user);
                        } catch (JsonProcessingException e) {
                            Platform.runLater(() ->
                                    showAlert("Ocurrió un error recibiendo los datos, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    } else {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error recibiendo los datos, intente nuevamente: " + response.body(), Alert.AlertType.ERROR)
                        );
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

    public void onSearchButtonPressed(ActionEvent actionEvent) {
        if (emailTextBox.getText().isEmpty()) {
            return;
        }

        String email = emailTextBox.getText().trim();
        AtomicReference<UserAccount> account = new AtomicReference<>();

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(usersUri + "/email/" + email))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + CurrentUser.jwt)
                .GET()
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            System.out.println(response.body());
                            JsonNode respJson = new ObjectMapper().readTree(response.body());
                            UserAccount accountInfo = new UserAccount();
                            accountInfo.setUsername(respJson.get("username").asText());
                            accountInfo.setEmail(respJson.get("email").asText());
                            accountInfo.setName(respJson.get("name").asText());
                            accountInfo.setActive(respJson.get("active").asBoolean());
                            accountInfo.setId(respJson.get("_id").asText());
                            account.set(accountInfo);
                        } catch (JsonProcessingException e) {
                            Platform.runLater(() ->
                                    showAlert("Ocurrió un error recibiendo los datos, intente nuevamente", Alert.AlertType.ERROR)
                            );
                        }
                    } else if (response.statusCode() == 404) {
                        Platform.runLater(() ->
                            showAlert("No se encontró ningun usuario con ese correo", Alert.AlertType.INFORMATION)
                        );
                    } else {
                        System.out.println(response.body());
                        Platform.runLater(() ->
                            showAlert("Ocurrió un error, intente de nuevo: " + response.body(), Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();

        if (account.get() == null) {
            return;
        }

        UserAccount userAccount = account.get();

        User user = getUserInfo(userAccount.getId());
        if (user == null) {
            showAlert("No se pudo obtener el usuario", Alert.AlertType.ERROR);
            return;
        }

        var alert = new Alert(Alert.AlertType.CONFIRMATION);

        if (userAccount.isActive()) {
            if (user.isVerified()) {
                alert.setTitle("Remover verificación de usuario");
                alert.setHeaderText("Remover verificación de usuario");
                alert.setContentText("¿Está seguro que desea eliminar la verificación de " + userAccount.getName() + "?");
                alert.showAndWait().ifPresent((btnType) -> {
                    if (btnType == ButtonType.OK) {
                        removeVerification(userAccount.getId());
                        alert.close();
                    } else {
                        alert.close();
                    }
                });
            } else {
                alert.setTitle("Suspender usuario");
                alert.setHeaderText("Suspender usuario");
                alert.setContentText("¿Está seguro que desea suspender a " + userAccount.getName() + "?\nUsuario: " + userAccount.getUsername());
                alert.showAndWait().ifPresent((btnType) -> {
                    if (btnType == ButtonType.OK) {
                        updateAccountState(userAccount.getId(), false);
                        alert.close();
                    } else {
                        alert.close();
                    }
                });
            }
        } else {
            alert.setTitle("Reactivar usuario");
            alert.setHeaderText("Reactivar usuario");
            alert.setContentText("¿Está seguro que desea reactivar la cuenta de " + userAccount.getName() + "?");
            alert.showAndWait().ifPresent((btnType) -> {
                if (btnType == ButtonType.OK) {
                    updateAccountState(userAccount.getId(), true);
                    alert.close();
                } else {
                    alert.close();
                }
            });
        }
    }

    private void removeVerification(String id) {
        String jsonBody;
        try {
            JsonNode payload = new ObjectMapper().createObjectNode()
                    .put("state", "Deleted");
            jsonBody = new ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            showAlert("Internal error: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(URI.create(usersUri + "/" + id + "/verification"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + CurrentUser.jwt)
                .method("Patch", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        client.sendAsync(reportRequest, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    if (response.statusCode() == 200) {
                        Platform.runLater(() ->
                                showAlert("Actualización de usuario correcta", Alert.AlertType.INFORMATION)
                        );
                    } else if (response.statusCode() == 404) {
                        Platform.runLater(() ->
                                showAlert("No se encontró el usuario", Alert.AlertType.INFORMATION)
                        );
                    } else {
                        Platform.runLater(() ->
                                showAlert("Ocurrió un error, intente de nuevo", Alert.AlertType.ERROR)
                        );
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            showAlert("Error de red" + ex.getMessage(), Alert.AlertType.ERROR)
                    );
                    return null;
                }).join();
    }

    public void returnToMainMenu(ActionEvent actionEvent) {
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
}
