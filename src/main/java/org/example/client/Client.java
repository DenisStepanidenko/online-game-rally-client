package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.jsonparser.JsonParser;
import org.example.model.Lobby;
import org.example.serverConfig.ServerConfig;
import org.example.validator.PasswordValidator;
import org.example.validator.UsernameValidator;
import org.example.validator.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final Validator usernameValidator = new UsernameValidator();
    private static final Validator passwordValidator = new PasswordValidator();

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private Stage startStage;
    private Stage connectStage;
    private Stage waitingStage;
    private Stage gameMenu;
    private Stage lobbiesStage;
    private Stage waitingConnectPersonInLobbyStage;
    private Stage readyForStartStage;
    private Stage readyForOpponentStage;

    private Label timerLabel;
    private Stage enteringUsernameStage;
    private Stage enteringPasswordStage;
    private Thread timeThread;
    private String nameOfOpponent;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.startStage = primaryStage;
        logger.info("Приложение запущено.");

        showStartWindow();
    }


    private void connectToServer() {
        showConnectWindow();


        // обработка начала подключения соединения, ловим ответ на запрос CONNECT
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                logger.info("Подключение к серверу по адресу " + socket.getInetAddress() + " установлено.");
                output = new PrintWriter(socket.getOutputStream(), true);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Platform.runLater(() -> {
                    connectStage.close();
                    showWaitingWindow();
                });

                output.println("CONNECT");
                logger.info("Запрос на подключение (CONNECT) к серверу отправлен.");

                String response = input.readLine();
                if ("CONNECT_ACK".equals(response)) {
                    logger.info("Подключение к серверу подтверждено (CONNECT_ACK).");
                    Platform.runLater(() -> {
                        waitingStage.close();
                        startAuthentication();
                    });
                } else {
                    logger.info("Подтверждение подключения (CONNECT_ACK) от сервера не получено.");
                    Platform.runLater(() -> {
                        waitingStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                logger.error("Произошла ошибка при подключении к серверу " + e.getMessage());
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
        usernameField.setMaxWidth(250);
        Label errorLabel = new Label(); // label для отображения ошибки
        errorLabel.setStyle("-fx-text-fill: red;"); // красный цвет текста ошибки
        Button submitButton = new Button("Подтвердить");
        submitButton.setOnAction(e -> {
            String userName = usernameField.getText();
            if (usernameValidator.validate(userName)) {
                sendUsername(userName);
            } else {
                // вывод в графике подсказки с требованиями к userName
                errorLabel.setText("Имя пользователя должно содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная или строчная буква\n - цифра\nРазрешены только буквы латинского алфавита.");
            }

        });

        authRoot.getChildren().addAll(authLabel, usernameField, errorLabel, submitButton);

        Scene authScene = new Scene(authRoot, 1920, 1080);
        enteringUsernameStage.setTitle("Rally");
        enteringUsernameStage.setScene(authScene);
        enteringUsernameStage.show();
    }

    private void sendUsername(String userName) {
        new Thread(() -> {
            try {

                output.println("USER/" + userName);
                logger.info("Имя пользователя (USER/...) отправлено на сервер.");

                String response = input.readLine();
                if ("USER_ACK_CHECK".equals(response)) {
                    logger.info("Подтверждение существования пользователя (USER_ACK_CHECK) получено от сервера.");
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        showPasswordWindow("Аккаунт с таким никнеймом уже существует. Подтвердите пароль: ");
                    });
                } else if ("USER_ACK_CREATE".equals(response)) {
                    logger.info("Подтверждение создания пользователя (USER_ACK_CREATE) получено от сервера.");
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        showPasswordWindow("Вы создаёте новый аккаунт. Придумайте пароль: ");
                    });
                } else {
                    logger.info("Подтверждение существования (USER_ACK_CHECK)/создания (USER_ACK_CREATE) пользователя от сервера не получено.");
                    Platform.runLater(() -> {
                        enteringUsernameStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }


            } catch (IOException e) {
                logger.error("Произошла ошибка при отправке имени пользователя на сервер. " + e.getMessage());
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
        passwordField.setMaxWidth(250);
        Label errorLabel = new Label(); // label для отображения ошибки
        errorLabel.setStyle("-fx-text-fill: red;"); // красный цвет текста ошибки
        Button submitButton = new Button("Подтвердить");

        submitButton.setOnAction(e -> {
            String password = passwordField.getText();
            if (passwordValidator.validate(password)) {
                sendPassword(password);
            } else if (errorLabel.getText().isEmpty()) {
                // Вывод в графике подсказки с требованиями к паролю пользователя
                errorLabel.setText("Пароль должен содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная и строчная буква\n - цифра\n - спецсимвол\nРазрешены только буквы латинского алфавита.");
            }

        });

        enteringPasswordRoot.getChildren().addAll(authLabel, passwordField, errorLabel, submitButton);
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
                        if (gameMenu.isShowing()) {
                            gameMenu.close();
                        }
                        showGameMenu();
                    });
                } else if (response.startsWith("MULTIPLAY_ACK_SUCCESS")) {
                    String json = response.substring(22);

                    Optional<List<Lobby>> optionalLobbies = JsonParser.parseLobbies(json);

                    if (optionalLobbies.isPresent()) {
                        Platform.runLater(() -> {
                            if (gameMenu.isShowing()) {
                                gameMenu.close();
                            }
                            showLobbies(optionalLobbies.get());
                        });
                    } else {
                        Platform.runLater(() -> {
                            if (gameMenu.isShowing()) {
                                gameMenu.close();
                            }
                            showGameMenu();
                        });
                    }

                } else {
                    Platform.runLater(() -> {
                        if (gameMenu.isShowing()) {
                            gameMenu.close();
                        }
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (gameMenu.isShowing()) {
                        gameMenu.close();
                    }
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
        Label startGame;
        if (lobby.isStartingGame()) {
            startGame = new Label("Игра идёт");
        } else {
            startGame = new Label("Игра не началась");
        }
        infoBox.getChildren().addAll(nameLabel, playersLabel, startGame);

        Button selectButton = new Button("Выбрать");

        selectButton.setOnAction(e -> {
            sendLobbyIdToServer(lobby.getId());
        });

        lobbyBox.getChildren().addAll(iconLabel, infoBox, selectButton);

        return lobbyBox;
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

    private void sendLobbyIdToServer(int id) {
        new Thread(() -> {
            try {
                output.println("JOIN_LOBBY_ID/" + id);

                String response = input.readLine();
                if (response.equals("FULL_LOBBY_ERR") || response.equals("LOBBY_START_GAME")) {
                    Platform.runLater(() -> {
                        lobbiesStage.close();
                        getLobbiesFromMultiplay();
                    });
                } else if (response.equals("JOIN_LOBBY_ID_ACK_SUCCESS")) {
                    Platform.runLater(() -> {
                        lobbiesStage.close();
                        showWaitingConnectPersonInLobby(null);
                    });
                } else {
                    Platform.runLater(() -> {
                        lobbiesStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    lobbiesStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        }).start();
    }

    private void showWaitingConnectPersonInLobby(String message) {
        waitingConnectPersonInLobbyStage = new Stage();
        waitingConnectPersonInLobbyStage.setTitle("Rally");

        Label messageLabel = new Label("Ожидание подключения игрока...");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");
        VBox vBox = new VBox(messageLabel, progressIndicator);

        if (!Objects.isNull(messageLabel)) {
            Label info = new Label(message);
            vBox.getChildren().add(info);
        }
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vBox, 1920, 1080);

        waitingConnectPersonInLobbyStage.setScene(scene);
        waitingConnectPersonInLobbyStage.show();

        waitingConnectPerson();
    }

    private void waitingConnectPerson() {
        new Thread(() -> {
            try {
                String response = input.readLine();

                if (response.startsWith("PLAYER_JOINED/")) {
                    String[] parts = response.split("/");
                    String usernameOfOpponent = parts[1];
                    this.nameOfOpponent = usernameOfOpponent;
                    Platform.runLater(() -> {
                        waitingConnectPersonInLobbyStage.close();
                        showReadyForStartStage(usernameOfOpponent);
                    });
                } else {
                    Platform.runLater(() -> {
                        waitingConnectPersonInLobbyStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    waitingConnectPersonInLobbyStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }
        }).start();
    }

    private boolean readyButtonFlag = false;

    private void showReadyForStartStage(String usernameOfOpponent) {
        readyForStartStage = new Stage();
        readyForStartStage.setTitle("Готовность к игре");


        Label opponentLabel = new Label("Против вас будет играть: " + usernameOfOpponent);
        opponentLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");


        Button readyButton = new Button("Готов играть");
        readyButton.setStyle("-fx-font-size: 14px; -fx-background-color: #0078d7; -fx-text-fill: white;");

        readyButton.setOnAction(e -> {
            sendReadyStatus();
        });


        VBox vbox = new VBox(opponentLabel, readyButton);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));


        Scene scene = new Scene(vbox, 1920, 1080);
        readyForStartStage.setScene(scene);
        readyForStartStage.show();

    }

    private void sendReadyStatus() {
        new Thread(() -> {
            Platform.runLater(() -> {
                readyForStartStage.close();
                showWaitingForReadeOpponent();
            });

            output.println("READY");
            try {
                String response = input.readLine();
                if (response.equals("AFK_TIMEOUT")) {
                    Platform.runLater(() -> {
                        readyForOpponentStage.close();
                        getLobbiesFromMultiplay();
                    });

                } else if (response.equals("LEFT_JOINED")) {
                    Platform.runLater(() -> {
                        readyForOpponentStage.close();
                        showWaitingConnectPersonInLobby("Игрок " + this.nameOfOpponent + " не подтвердил готовность к игре");
                    });
                } else {
                    Platform.runLater(() -> {
                        readyForOpponentStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    readyForOpponentStage.close();
                    closeConnection();
                    showStartWindow();
                });
            }

        }).start();
    }

    private void showWaitingForReadeOpponent() {
        readyForOpponentStage = new Stage();
        readyForOpponentStage.setTitle("Rally");


        Label waitingLabel = new Label("Ожидаем подтверждения от игрока: " + this.nameOfOpponent);
        waitingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");


        VBox vbox = new VBox(waitingLabel, progressIndicator);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));


        Scene scene = new Scene(vbox, 1920, 1080);
        readyForOpponentStage.setScene(scene);
        readyForOpponentStage.show();


    }


    private void showGame() {
        Stage test = new Stage();
        test.setTitle("Rally");


        Label waitingLabel = new Label("Должна быть игра");
        waitingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");


        VBox vbox = new VBox(waitingLabel, progressIndicator);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));

        Scene scene = new Scene(vbox, 1920, 1080);
        test.setScene(scene);
        test.show();
    }

    private void sendPassword(String password) {
        new Thread(() -> {
            try {

                output.println("PASS/" + password);
                logger.info("Пароль пользователя (PASS/...) отправлен на сервер.");

                String response = input.readLine();
                if ("PASS_ACK_SUCCESS".equals(response)) {
                    logger.info("Подтверждение пароля (PASS_ACK_SUCCESS) получено от сервера.");
                    Platform.runLater(() -> {
                        enteringPasswordStage.close();
                        showGameMenu();
                    });
                } else if ("PASS_ACK_FAIL".equals(response)) {
                    logger.info("Подтверждение пароля (PASS_ACK_FAIL) получено от сервера: Неверный пароль.");
                    Platform.runLater(() -> {
                        enteringPasswordStage.close();
                        showPasswordWindow("Неверный пароль. Попробуйте снова: ");
                    });
                } else {
                    logger.info("Подтверждение верности (PASS_ACK_SUCCESS)/неверности (PASS_ACK_FAIL) пароля от сервера не получено.");
                    Platform.runLater(() -> {
                        enteringPasswordStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                logger.error("Произошла ошибка при отправке пароля. " + e.getMessage());
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
                this.nameOfOpponent = null;

                socket.close();
                logger.info("Сокет закрыт.");

                input.close();
                logger.info("Поток ввода закрыт.");

                output.close();
                logger.info("Поток вывода закрыт.");
            } catch (IOException e) {
                logger.error("Произошла ошибка при закрытии сокета. " + e.getMessage());
            }
        }
    }


}