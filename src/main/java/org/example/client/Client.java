package org.example.client;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.jsonparser.JsonParser;
import org.example.listener.ServerListener;
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
import java.util.Properties;

public class Client extends Application {

    /**
     * Адрес сервера
     */
    private static final String SERVER_ADDRESS = ServerConfig.SERVER_ADDRESS.getValue();

    /**
     * Порт сервера
     */
    private static final int SERVER_PORT = Integer.parseInt(ServerConfig.SERVER_PORT.getValue());

    /**
     * Логирование
     */
    private final Logger logger = LoggerFactory.getLogger(Client.class);

    /**
     * Валидатор для имени
     */
    private static final Validator usernameValidator = new UsernameValidator();

    /**
     * Валидатор для пароля
     */
    private static final Validator passwordValidator = new PasswordValidator();

    /**
     * Основный объект для JavaFx, в котором просто будет меняться scene
     */
    private Stage primaryStage;

    /**
     * Список объектов на scene
     */
    private VBox root;

    /**
     * Объект, который слушает и отправляет сообщения серверу
     */
    private ServerListener serverListener;

    /**
     * Имя противника в игре
     */
    private String nameOfOpponent;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        this.root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 1920, 1080);
        primaryStage.setTitle("Rally");
        primaryStage.setScene(scene);
        primaryStage.show();

        logger.info("Приложение запущено.");
        showStartWindow();
    }

    /**
     * Начальный экран, доступна кнопка подключиться
     */
    private void showStartWindow() {
        logger.info("Началась отрисовка начального экрана");
        root.getChildren().clear();

        Button connectionButton = new Button("Подключиться");
        connectionButton.setPrefWidth(400);
        connectionButton.setAlignment(Pos.CENTER);
        connectionButton.setOnAction(e -> connectToServer());

        root.getChildren().add(connectionButton);
    }

    /**
     * Экран ожидания подключения к серверу ( ждём когда выполнится строчка socket = new Socket(...) )
     */
    private void showConnectWindow() {
        logger.info("Началась отрисовка экрана ожидания подключения");
        root.getChildren().clear();

        Label waitingLabel = new Label("Ожидания подключения к серверу");
        root.getChildren().add(waitingLabel);
    }

    /**
     * Функция инициализирует сокеты
     */
    private void connectToServer() {
        showConnectWindow();

        new Thread(() -> {
            try {
                logger.info("Ожидается подключение к серверу");
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                serverListener = new ServerListener(socket, this);

                new Thread(serverListener).start();
                serverListener.sendMessage("CONNECT");
            } catch (IOException e) {
                Platform.runLater(this::showStartWindow);
                logger.info("Произошла ошибка при подключении к серверу");
            }
        }).start();

    }

    /**
     * Обработчик сообщений от сервера
     *
     * @param response сообщение от сервера
     */
    public void handleServerResponse(String response) {
        if ("CONNECT_ACK".equals(response)) {
            startAuthentication();
        } else if ("USER_ACK_CREATE".equals(response)) {
            showPasswordWindow("Вы создаёте новый аккаунт. Придумайте пароль.");
        } else if ("USER_ACK_CHECK".equals(response)) {
            showPasswordWindow("Аккаунт с таким никнеймом уже существует. Подтвердите пароль: ");
        } else if ("PASS_ACK_FAIL".equals(response)) {
            showPasswordWindow("Неверный пароль. Попробуйте снова: ");
        } else if ("PASS_ACK_SUCCESS".equals(response)) {
            showGameMenu();
        } else if (response.startsWith("MULTIPLAY_ACK_SUCCESS")) {
            String json = response.substring(22);
            Optional<List<Lobby>> optionalLobbies = JsonParser.parseLobbies(json);

            if (optionalLobbies.isPresent()) {
                logger.info("Лобби успешно распарсились");
                showLobbies(optionalLobbies.get());
            } else {
                showGameMenu();
            }
        } else if ("MULTIPLAY_ACK_FAIL".equals(response)) {
            showGameMenu();
        } else if ("LOBBY_START_GAME".equals(response)) {
            showNotification("Игра в лобби уже идёт!", Color.RED);
        } else if ("FULL_LOBBY_ERR".equals(response)) {
            showNotification("Лобби переполнено", Color.RED);
        } else if ("JOIN_LOBBY_ID_ACK_SUCCESS".equals(response)) {
            showWaitingConnectPersonInLobby();
        } else if (response.startsWith("PLAYER_JOINED")) {
            String[] parts = response.split("/");
            this.nameOfOpponent = parts[1];

            showReadyForStartStage();
        } else if ("LEFT_JOINED".equals(response)) {
            showNotification("Игрок " + this.nameOfOpponent + " не подтвердил готовность к игре", Color.RED);
            this.nameOfOpponent = null;
            showWaitingConnectPersonInLobby();
        } else if ("AFK_TIMEOUT".equals(response)) {
            this.nameOfOpponent = null;
            showNotification("Вы не подтвердили готовность к игре в течении 30 секунд", Color.RED);
            serverListener.sendMessage("MULTIPLAY");
        }
    }

    /**
     * Аутентификация пользователя
     */
    private void startAuthentication() {
        logger.info("Начало аутентификации");
        Platform.runLater(this::showUsernameWindow);
    }

    /**
     * Ввод пользователем имени
     */
    private void showUsernameWindow() {
        logger.info("Отрисовка экрана ввода имени пользователя");
        root.getChildren().clear();

        Label authLabel = new Label("Введите имя пользователя");
        TextField usernameField = new TextField();
        usernameField.setMaxWidth(250);
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        Button submitButton = new Button("Подтвердить");
        submitButton.setOnAction(e -> {
            String userName = usernameField.getText();

            // валидация имени пользователя
            if (usernameValidator.validate(userName)) {
                serverListener.sendMessage("USER/" + userName);
            } else {
                // вывод в графике подсказки с требованиями к userName
                errorLabel.setText("Имя пользователя должно содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная или строчная буква\n - цифра\nРазрешены только буквы латинского алфавита.");
            }

        });

        root.getChildren().addAll(authLabel, usernameField, errorLabel, submitButton);
    }

    /**
     * Ввод пользователем пароля
     */
    private void showPasswordWindow(String message) {
        logger.info("Отрисовка экрана с вводом пароля");
        root.getChildren().clear();

        Label authLabel = new Label(message);
        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(250);
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        Button submitButton = new Button("Подтвердить");

        submitButton.setOnAction(e -> {
            String password = passwordField.getText();

            // валидация пароля (только если аккаунт создаёт впервые)
            if (passwordValidator.validate(password)) {
                serverListener.sendMessage("PASS/" + password);
            } else if (errorLabel.getText().isEmpty()) {
                // Вывод в графике подсказки с требованиями к паролю пользователя
                errorLabel.setText("Пароль должен содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная и строчная буква\n - цифра\n - спецсимвол\nРазрешены только буквы латинского алфавита.");
            }

        });

        root.getChildren().addAll(authLabel, passwordField, errorLabel, submitButton);
    }

    /**
     * Окно с игровым меню
     */
    private void showGameMenu() {
        logger.info("Отрисовка экрана с игровым меню");
        root.getChildren().clear();

        String style = "-fx-alignment: center; -fx-padding: 10px;";

        Button playWithComputer = new Button("Игра с компьютером");
        Button playOnline = new Button("Online режим");
        playOnline.setOnAction(e -> serverListener.sendMessage("MULTIPLAY"));
        Button viewComputerTopList = new Button("Посмотреть топ игроков в игре с компьютером");
        Button viewOnlineTopList = new Button("Посмотреть топ игроков в игре online");
        Button exit = new Button("Выход из игры");

        playWithComputer.setPrefWidth(400);
        playWithComputer.setStyle(style);
        playOnline.setPrefWidth(400);
        playOnline.setStyle(style);
        viewComputerTopList.setPrefWidth(400);
        viewComputerTopList.setStyle(style);
        viewOnlineTopList.setPrefWidth(400);
        viewOnlineTopList.setStyle(style);
        exit.setPrefWidth(400);
        exit.setStyle(style);


        root.getChildren().addAll(playOnline, playWithComputer, viewOnlineTopList, viewComputerTopList, exit);
    }

    /**
     * Экран со всеми лобби
     */
    private void showLobbies(List<Lobby> lobbies) {
        root.getChildren().clear();
        logger.info("Началась отрисовка экрана с лобби");

        Label titleLabel = new Label("Доступные лобби: ");
        Button refreshButton = new Button("Обновить");
        refreshButton.setOnAction(e -> serverListener.sendMessage("MULTIPLAY"));
        root.getChildren().addAll(titleLabel, refreshButton);


        for (Lobby lobby : lobbies) {
            HBox lobbyBox = createLobbyButton(lobby);
            root.getChildren().add(lobbyBox);
        }

    }


    /**
     * Создания кнопок для участия в игре в лобби
     */
    private HBox createLobbyButton(Lobby lobby) {
        logger.info("Началась отрисовка лобби с id = " + lobby.getId());
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
            serverListener.sendMessage("JOIN_LOBBY_ID/" + lobby.getId());
        });

        lobbyBox.getChildren().addAll(iconLabel, infoBox, selectButton);

        return lobbyBox;
    }

    /**
     * Правильный парсинг имён игроков в лобби
     */
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

    /**
     * Метод для отображения уведомлений
     */
    private void showNotification(String message, Color color) {



        Label notificationLabel = new Label(message);
        notificationLabel.setStyle(
                "-fx-background-color: " + toRgbaString(color) + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 10px;" +
                        "-fx-background-radius: 5px;"
        );


        root.getChildren().add(notificationLabel);
        notificationLabel.toFront();

        notificationLabel.setTranslateX((root.getWidth() - notificationLabel.getWidth()) / 2);
        notificationLabel.setTranslateY(root.getHeight() - 50);


        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> {
            // Убираем уведомление после завершения задержки
            root.getChildren().remove(notificationLabel);
        });
        pause.play();
    }

    /**
     * Метод для преобразования Color в строку RGBA
     */
    private String toRgbaString(Color color) {
        return String.format(
                "rgba(%d, %d, %d, %.2f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity()
        );
    }

    /**
     * Окно с ожиданием подключения пользователя в лобби
     */
    private void showWaitingConnectPersonInLobby() {
        logger.info("Отрисовка окна с ожиданием подключения пользователя в лобби");
        root.getChildren().clear();

        Label messageLabel = new Label("Ожидание подключения игрока...");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");

        root.getChildren().addAll(messageLabel, progressIndicator);
    }

    /**
     * Окно с ожиданием готовности к игре
     */
    private void showReadyForStartStage() {
        logger.info("Отрисовка экрана с ожиданием готовности к игре");
        root.getChildren().clear();

        Label opponentLabel = new Label("Против вас будет играть: " + this.nameOfOpponent);
        opponentLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");


        Button readyButton = new Button("Готов играть (нужно подтвердить готовность в течении 30 секунд.");
        readyButton.setStyle("-fx-font-size: 14px; -fx-background-color: #0078d7; -fx-text-fill: white;");

        readyButton.setOnAction(e -> {
            serverListener.sendMessage("READY");
            showWaitingForReadyOpponent();
        });

        root.getChildren().addAll(opponentLabel, readyButton);
    }


    /**
     * Окно с ожидание подтверждения готовности от оппонента
     */
    private void showWaitingForReadyOpponent() {
        logger.info("Отрисока окна с ожидание подтверждения готовности от оппонента");
        root.getChildren().clear();

        Label waitingLabel = new Label("Ожидаем подтверждения от игрока: " + this.nameOfOpponent);
        waitingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");

        root.getChildren().addAll(waitingLabel, progressIndicator);
    }

    /**
     * Обработка ошибки при работе сервера
     */
    public void handleServerError() {
        showNotification("Произошла ошибка при работе сервера", Color.RED);
        showStartWindow();
    }


}