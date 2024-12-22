package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.jsonparser.JsonParser;
import org.example.model.Lobby;
import org.example.serverConfig.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Client extends Application {
    private static final String SERVER_ADDRESS = ServerConfig.SERVER_ADDRESS.getValue();
    private static final int SERVER_PORT = Integer.parseInt(ServerConfig.SERVER_PORT.getValue());

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private Stage startStage;
    private Stage connectStage;
    private Stage waitingStage;
    private Stage gameMenu;
    private Stage lobbiesStage;

    private Label timerLabel;
    private Stage enteringUsernameStage;
    private Stage enteringPasswordStage;
    private Thread timeThread;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.startStage = primaryStage;

        showStartWindow();
    }


    private void connectToServer() {
        showConnectWindow();


        // обработка начала подключения соединения, ловим ответ на запрос CONNECT
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                output = new PrintWriter(socket.getOutputStream(), true);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Platform.runLater(() -> {
                    connectStage.close();
                    showWaitingWindow();
                });

                output.println("CONNECT");

                String response = input.readLine();
                if ("CONNECT_ACK".equals(response)) {
                    Platform.runLater(() -> {
                        waitingStage.close();
                        startAuthentication();
                    });
                } else {
                    Platform.runLater(() -> {
                        waitingStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (waitingStage != null) {
                        waitingStage.close();
                    }
                    connectStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        }).start();

        // Таймер для ожидания ответа от сервера
        timeThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 30000) {
                long remainingTime = 30000 - (System.currentTimeMillis() - startTime);

                Platform.runLater(() -> timerLabel.setText("Осталось: " + (remainingTime / 1000) + " сек"));

                if (!waitingStage.isShowing()) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Platform.runLater(() -> {
                        waitingStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            }

            if (waitingStage.isShowing()) {
                Platform.runLater(() -> {
                    waitingStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        });

    }

    private void startAuthentication() {
        Platform.runLater(this::showUsernameWindow);
    }

    private void showUsernameWindow() {
        enteringUsernameStage = new Stage();
        VBox authRoot = new VBox(10);
        authRoot.setPadding(new Insets(20));
        authRoot.setAlignment(Pos.CENTER);

        Label authLabel = new Label("Введите имя пользователя");
        TextField usernameField = new TextField();
        Button submitButton = new Button("Подтвердить");
        submitButton.setOnAction(e -> {
            String userName = usernameField.getText();
            sendUsername(userName);
        });

        authRoot.getChildren().addAll(authLabel, usernameField, submitButton);

        Scene authScene = new Scene(authRoot, 1920, 1080);
        enteringUsernameStage.setTitle("Rally");
        enteringUsernameStage.setScene(authScene);
        enteringUsernameStage.show();
    }

    private void sendUsername(String userName) {
        new Thread(() -> {
            try {

                output.println("USER/" + userName);

                String response = input.readLine();
                if ("USER_ACK_CHECK".equals(response)) {
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        showPasswordWindow("Аккаунт с таким никнеймом уже существует. Подтвердите пароль: ");
                    });
                } else if ("USER_ACK_CREATE".equals(response)) {
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        showPasswordWindow("Вы создаёт новый аккаунт. Придумайте пароль: ");
                    });
                } else {
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }


            } catch (IOException e) {
                Platform.runLater(() -> {
                    enteringUsernameStage.close();
                    closeConnection();
                    showStartWindow();
                });

            }


        }).start();
    }

    private void showPasswordWindow(String message) {
        enteringPasswordStage = new Stage();
        VBox enteringPasswordRoot = new VBox(10);
        enteringPasswordRoot.setPadding(new Insets(20));
        enteringPasswordRoot.setAlignment(Pos.CENTER);

        Label authLabel = new Label(message);
        PasswordField passwordField = new PasswordField();
        Button submitButton = new Button("Подветрдить");

        submitButton.setOnAction(e -> {
            String password = passwordField.getText();
            sendPassword(password);
        });

        enteringPasswordRoot.getChildren().addAll(authLabel, passwordField, submitButton);
        Scene authScene = new Scene(enteringPasswordRoot, 1920, 1080);

        enteringPasswordStage.setScene(authScene);
        enteringPasswordStage.setTitle("Rally");
        enteringPasswordStage.show();
    }

    private void getLobbiesFromMultiplay() {
        new Thread(() -> {
            try {
                output.println("MULTIPLAY");

                String response = input.readLine();
                if (response.startsWith("MULTIPLAY_ACK_FAIL")) {
                    Platform.runLater(() -> {
                        gameMenu.close();
                        showGameMenu();
                    });
                } else if (response.startsWith("MULTIPLAY_ACK_SUCCESS")) {
                    String json = response.substring(22);

                    Optional<List<Lobby>> optionalLobbies = JsonParser.parseLobbies(json);

                    if (optionalLobbies.isPresent()) {
                        Platform.runLater(() -> {
                            gameMenu.close();
                            showLobbies(optionalLobbies.get());
                        });
                    } else {
                        Platform.runLater(() -> {
                            gameMenu.close();
                            showGameMenu();
                        });
                    }

                } else {
                    Platform.runLater(() -> {
                        gameMenu.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    gameMenu.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        }).start();

    }

    private void showLobbies(List<Lobby> lobbies) {
        lobbiesStage = new Stage();
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Доступные лобби: ");
        root.getChildren().add(titleLabel);

        for (Lobby lobby : lobbies) {
            HBox lobbyBox = createLobbyButton(lobby);
            root.getChildren().add(lobbyBox);
        }

        Scene scene = new Scene(root, 1920, 1080);
        lobbiesStage.setTitle("Rally");
        lobbiesStage.setScene(scene);
        lobbiesStage.show();
    }

    private HBox createLobbyButton(Lobby lobby) {
        HBox lobbyBox = new HBox(10);
        lobbyBox.setAlignment(Pos.CENTER_LEFT);
        lobbyBox.setPadding(new Insets(10));

        Label iconLabel = new Label("\uD83C\uDFAE");

        VBox infoBox = new VBox(5);
        Label nameLabel = new Label("Название лобби: " + lobby.getNameOfLobby());
        Label playersLabel = new Label("Количество игроков: " + lobby.getCountOfPlayersInLobby() + "/2");
        Optional<String> nameOfPlayers = parseNameOfPlayers(lobby);
        if (nameOfPlayers.isPresent()) {
            Label nameOfPlayersLabel = new Label(nameOfPlayers.get());
            infoBox.getChildren().add(nameOfPlayersLabel);
        }
        infoBox.getChildren().addAll(nameLabel, playersLabel);

        Button selectButton = new Button("Выбрать");

        selectButton.setOnAction(e -> {
            sendLobbyIdToServer(lobby.getId());
        });

        lobbyBox.getChildren().addAll(iconLabel, infoBox, selectButton);

        return lobbyBox;
    }

    private void sendLobbyIdToServer(int id) {
    }

    private Optional<String> parseNameOfPlayers(Lobby lobby) {
        if (Objects.isNull(lobby.getPlayer1()) && Objects.isNull(lobby.getPlayer2())) {
            return Optional.empty();
        } else if (Objects.isNull(lobby.getPlayer1())) {
            String players = "Игроки: " + lobby.getPlayer2();
            return Optional.of(players);
        } else if (Objects.isNull(lobby.getPlayer2())) {
            String players = "Игроки: " + lobby.getPlayer1();
            return Optional.of(players);
        } else {
            String players = "Игроки: " + lobby.getPlayer1() + ", " + lobby.getPlayer2();
            return Optional.of(players);
        }
    }

    private void sendPassword(String password) {
        new Thread(() -> {
            try {

                output.println("PASS/" + password);

                String response = input.readLine();
                if ("PASS_ACK_SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        enteringPasswordStage.close();
                        showGameMenu();
                    });
                } else if ("PASS_ACK_FAIL".equals(response)) {
                    Platform.runLater(() -> {
                        showPasswordWindow("Неверный пароль. Попробуйте снова: ");
                    });
                } else {
                    Platform.runLater(() -> {
                        enteringPasswordStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    enteringPasswordStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        }).start();

    }

    /**
     * Начальный экран, доступна кнопка подключиться
     */
    private void showStartWindow() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Button connectionButton = new Button("Подключиться");
        connectionButton.setPrefWidth(400);
        connectionButton.setAlignment(Pos.CENTER);
        connectionButton.setOnAction(e -> connectToServer());

        root.getChildren().add(connectionButton);

        Scene scene = new Scene(root, 1920, 1080);
        startStage.setTitle("Rally");
        startStage.setScene(scene);
        startStage.show();
    }

    /**
     * Экран ожидания подключения к серверу ( ждём когда выполнится строчка socket = new Socket(...) )
     */
    private void showConnectWindow() {
        startStage.close();
        connectStage = new Stage();
        VBox waitingRoot = new VBox(10);
        waitingRoot.setPadding(new Insets(20));
        waitingRoot.setAlignment(Pos.CENTER);

        Label waitingLabel = new Label("Ожидание подключения к серверу...");
        waitingRoot.getChildren().addAll(waitingLabel);

        Scene waitingScene = new Scene(waitingRoot, 1920, 1080);
        connectStage.setTitle("Rally");
        connectStage.setScene(waitingScene);
        connectStage.show();
    }

    /**
     * Экран ожидания ответа от сервера на сообщение "CONNECT"
     */
    private void showWaitingWindow() {
        waitingStage = new Stage();
        VBox waitingRoot = new VBox(10);
        waitingRoot.setPadding(new Insets(20));
        waitingRoot.setAlignment(Pos.CENTER);

        Label waitingLabel = new Label("Ожидание ответа от сервера...");
        timerLabel = new Label("До разрыва подключения осталось: 60 сек");
        waitingRoot.getChildren().addAll(waitingLabel, timerLabel);

        Scene waitingScene = new Scene(waitingRoot, 1920, 1080);
        waitingStage.setTitle("Rally");
        waitingStage.setScene(waitingScene);
        waitingStage.show();
        timeThread.start();
    }

    /**
     * Экран с игровым меню
     */
    private void showGameMenu() {
        gameMenu = new Stage();
        VBox menuRoot = new VBox(10);
        menuRoot.setPadding(new Insets(20));
        menuRoot.setAlignment(Pos.CENTER);

        Button playWithComputer = new Button("Игра с компьютером");
        Button playOnline = new Button("Online режим");
        playOnline.setOnAction(e -> getLobbiesFromMultiplay());
        Button viewComputerTopList = new Button("Посмотреть топ игроков в игре с компьютером");
        Button viewOnlineTopList = new Button("Посмотреть топ игроков в игре online");
        Button exit = new Button("Выход из игры");

        playWithComputer.setPrefWidth(400);
        playOnline.setPrefWidth(400);
        viewComputerTopList.setPrefWidth(400);
        viewOnlineTopList.setPrefWidth(400);
        exit.setPrefWidth(400);

        menuRoot.getChildren().addAll(playOnline, playWithComputer, viewOnlineTopList, viewComputerTopList, exit);

        Scene menuScene = new Scene(menuRoot, 1920, 1080);
        gameMenu.setTitle("Rally");
        gameMenu.setScene(menuScene);
        gameMenu.show();
    }


    /**
     * Закрытие соединения
     */
    private void closeConnection() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


}