package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.game.GameScreen;
import org.example.jsonparser.JsonParser;
import org.example.listener.ServerListener;
import org.example.model.GameState;
import org.example.model.Lobby;
import org.example.model.TopScoresByTime;
import org.example.model.TopScoresByWins;
import org.example.serverConfig.ServerConfig;
import org.example.validator.PasswordValidator;
import org.example.validator.UsernameValidator;
import org.example.validator.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


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

    private Scene primaryScene;

    /**
     * Имя противника в игре
     */
    private String nameOfOpponent;

    private String username;

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
        this.primaryScene = scene;
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
            showPasswordWindow("Вы создаёте новый аккаунт. Придумайте пароль.", true);
        } else if ("USER_ACK_CHECK".equals(response)) {
            showPasswordWindow("Аккаунт с таким никнеймом уже существует. Подтвердите пароль: ", false);
        } else if ("PASS_ACK_FAIL".equals(response)) {
            showPasswordWindow("Неверный пароль. Попробуйте снова: ", false);
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
        } else if (response.startsWith("START")) {
            String json = response.substring(6);

            Optional<GameState> gameState = JsonParser.parseGameState(json);

            if (gameState.isPresent()) {
                logger.info("Стартовое состояние игры успешно распарсилось");

                int[][] gameField = gameState.get().getGameField();

                GameScreen gameScreen = new GameScreen(gameField, serverListener);

                Platform.runLater(() -> primaryStage.setScene(gameScreen.getScene()));


            }
        } else if (response.startsWith("WIN") || response.startsWith("LOSE") || response.startsWith("DRAW")) {
            primaryStage.setScene(primaryScene);
            String[] parts = response.split("/");
            showEndGame(parts);
        } else if (response.startsWith("MULTILPLAY_TOP_SCORES_LIST_BY_WINS")) {
            String json = response.substring(35);

            Optional<List<TopScoresByWins>> optionalTopScoresByWins = JsonParser.parseTopScoresByWins(json);

            if (optionalTopScoresByWins.isPresent()) {
                List<TopScoresByWins> topScoresByWins = optionalTopScoresByWins.get();

                showTopScoresByWins(topScoresByWins);
            }
        } else if (response.startsWith("MULTILPLAY_TOP_SCORES_LIST_BY_TIME")) {
            String json = response.substring(35);

            Optional<List<TopScoresByTime>> optionalTopScoresByTimes = JsonParser.parseTopScoresByTime(json);

            if (optionalTopScoresByTimes.isPresent()) {
                List<TopScoresByTime> topScoresByTimes = optionalTopScoresByTimes.get();

                showTopScoreByTime(topScoresByTimes);
            }
        }
    }

    private void showTopScoreByTime(List<TopScoresByTime> topScoresByTimes) {
        logger.info("Отрисовка топ листа по времени");
        root.getChildren().clear();

        Label typeOfScore = new Label("Время (в секундах)");

        ListView<String> listView = new ListView<>();
        Button back = new Button("Назад");
        back.setOnAction(e -> showGameMenu());

        for (TopScoresByTime score : topScoresByTimes) {
            listView.getItems().add(score.getUsername() + ": " + score.getBestTime());
        }

        root.getChildren().addAll(listView, typeOfScore, back);
    }

    private void showTopScoresByWins(List<TopScoresByWins> topScoresByWins) {
        logger.info("Отрисовка топ листа по победам");
        root.getChildren().clear();

        Label typeOfScore = new Label("Победы");

        ListView<String> listView = new ListView<>();
        Button back = new Button("Назад");
        back.setOnAction(e -> showGameMenu());

        for (TopScoresByWins score : topScoresByWins) {
            listView.getItems().add(score.getUsername() + ": " + score.getWins());
        }

        root.getChildren().addAll(listView, typeOfScore, back);
    }

    /**
     * Окно с результатом игры
     */
    private void showEndGame(String[] parts) {
        logger.info("Отрисовка конца игры");
        root.getChildren().clear();

        String resultOfGame;
        if (parts[0].equals("WIN")) {
            resultOfGame = "Вы победили.";
        } else if (parts[0].equals("LOSE")) {
            resultOfGame = "Вы проиграли.";
        } else {
            resultOfGame = "Ничья.";
        }

        Label resultLabel = new Label(resultOfGame);
        Label yourTime = new Label("Ваше время: " + parts[1]);

        String timeOfOpponent;
        if (parts[2].equals("NO")) {
            timeOfOpponent = "Оппонент " + nameOfOpponent + " ещё не проехал трассу.";
        } else {
            timeOfOpponent = "Время " + nameOfOpponent + " " + parts[2];
        }
        Label timeOfOpponentLabel = new Label(timeOfOpponent);
        nameOfOpponent = null;

        Button backToLobby = new Button("Вернуться в список лобби");

        backToLobby.setOnAction(e -> serverListener.sendMessage("MULTIPLAY"));

        root.getChildren().addAll(resultLabel, yourTime, timeOfOpponentLabel, backToLobby);
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
                username = userName;
                serverListener.sendMessage("USER/" + userName);
            } else {
                // вывод в графике подсказки с требованиями к userName
                errorLabel.setText("Имя пользователя должно содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная или строчная буква\n - цифра\nРазрешены только буквы латинского алфавита.");
            }

        });

        Button returnButton = new Button("Назад");
        returnButton.setOnAction(e -> {
            showStartWindow();
        });

        root.getChildren().addAll(authLabel, usernameField, errorLabel, submitButton, returnButton);
    }

    /**
     * Ввод пользователем пароля
     */
    private void showPasswordWindow(String message, boolean isNewUser) {
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

            // валидация пароля (только если аккаунт создаётся впервые)
            if ((isNewUser & passwordValidator.validate(password) || !(isNewUser))) {
                serverListener.sendMessage("PASS/" + password);
            } else if (errorLabel.getText().isEmpty()) {
                // Вывод в графике подсказки с требованиями к паролю пользователя
                errorLabel.setText("Пароль должен содержать от 5 до 20 символов, среди которых обязательно должны быть:\n - заглавная и строчная буква\n - цифра\n - спецсимвол\nРазрешены только буквы латинского алфавита.");
            }

        });

        Button returnButton = new Button("Назад");
        returnButton.setOnAction(e -> {
            resetUsername();
            serverListener.sendMessage("RETURN_FROM_PASSWORD");
            showUsernameWindow();
        });

        root.getChildren().addAll(authLabel, passwordField, errorLabel, submitButton, returnButton);
    }

    /**
     * Окно с игровым меню
     */
    private void showGameMenu() {
        logger.info("Отрисовка экрана с игровым меню");
        root.getChildren().clear();

        String style = "-fx-alignment: center; -fx-padding: 10px;";

        if (Objects.nonNull(username)) {
            Label greetUser = new Label("Приветствуем, " + username);
            root.getChildren().add(greetUser);
        }

        Button playWithComputer = new Button("Игра с компьютером");

        Button playOnline = new Button("Online режим");
        playOnline.setOnAction(e -> serverListener.sendMessage("MULTIPLAY"));

        Button viewComputerTopList = new Button("Посмотреть топ игроков в игре с компьютером");

        Button viewOnlineTopListWins = new Button("Посмотреть топ игроков online по количеству побед");
        viewOnlineTopListWins.setOnAction(e -> serverListener.sendMessage("MULTILPLAY_TOP_SCORES_LIST_BY_WINS"));

        Button viewOnlineTopListTime = new Button("Посмотреть топ игроков online по лучшему времени");
        viewOnlineTopListTime.setOnAction(e -> serverListener.sendMessage("MULTILPLAY_TOP_SCORES_LIST_BY_TIME"));

        Button exit = new Button("Выход из игры");

        playWithComputer.setPrefWidth(400);
        playWithComputer.setStyle(style);
        playOnline.setPrefWidth(400);
        playOnline.setStyle(style);
        viewComputerTopList.setPrefWidth(400);
        viewComputerTopList.setStyle(style);
        viewOnlineTopListWins.setPrefWidth(400);
        viewOnlineTopListWins.setStyle(style);
        exit.setPrefWidth(400);
        exit.setStyle(style);
        viewOnlineTopListTime.setPrefWidth(400);
        viewOnlineTopListTime.setStyle(style);

        root.getChildren().addAll(playOnline, playWithComputer, viewOnlineTopListWins, viewOnlineTopListTime, viewComputerTopList, exit);
    }

    /**
     * Экран со всеми лобби
     */
    private void showLobbies(List<Lobby> lobbies) {
        root.getChildren().clear();
        logger.info("Началась отрисовка экрана с лобби");

        Label titleLabel = new Label("Доступные лобби: ");
        Button refreshButton = new Button("Обновить");
        Button back = new Button("Назад");
        back.setOnAction(e -> showGameMenu());
        refreshButton.setOnAction(e -> serverListener.sendMessage("MULTIPLAY"));
        root.getChildren().addAll(titleLabel, refreshButton, back);


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
        logger.info("Отображение уведомления с сообщением " + message);

        Stage notificationStage = new Stage();
        notificationStage.initStyle(StageStyle.TRANSPARENT);
        notificationStage.initModality(Modality.APPLICATION_MODAL);
        notificationStage.setAlwaysOnTop(true);


        Label label = new Label(message);
        label.setTextFill(color);


        Button okButton = new Button("OK");
        okButton.setOnAction(event -> notificationStage.close()); // Закрываем окно при нажатии


        VBox vbox = new VBox(10, label, okButton);
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-border-color: black; -fx-border-width: 2;");


        Scene scene = new Scene(vbox);
        scene.setFill(Color.TRANSPARENT);
        notificationStage.setScene(scene);


        notificationStage.show();

        // Закрываем уведомление через 5 секунд, если пользователь не нажал "OK"
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(notificationStage::close);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Окно с ожиданием подключения пользователя в лобби
     */
    private void showWaitingConnectPersonInLobby() {
        logger.info("Отрисовка окна с ожиданием подключения пользователя в лобби");
        root.getChildren().clear();

        Label messageLabel = new Label("Ожидание подключения игрока...");
        Button backButton = new Button("Назад");
        backButton.setOnAction(e -> {
            serverListener.sendMessage("EXIT_WAITING");
            serverListener.sendMessage("MULTIPLAY");
        });

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setStyle("-fx-progress-color: #0078d7;");

        root.getChildren().addAll(messageLabel, progressIndicator, backButton);
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
        Button backButton = new Button("Не готов (вернуться назад в список лобби)");
        backButton.setOnAction(e -> {
            serverListener.sendMessage("NOT_READY");
            serverListener.sendMessage("MULTIPLAY");
        });

        readyButton.setStyle("-fx-font-size: 14px; -fx-background-color: #0078d7; -fx-text-fill: white;");

        readyButton.setOnAction(e -> {
            serverListener.sendMessage("READY");
            showWaitingForReadyOpponent();
        });

        root.getChildren().addAll(opponentLabel, readyButton, backButton);
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
        if (Objects.nonNull(nameOfOpponent)) {
            nameOfOpponent = null;
        }

        resetUsername();

        showStartWindow();
    }

    /**
     * Сброс поля username
     */
    private void resetUsername() {
        if (Objects.nonNull(username)) {
            username = null;
        }
    }

}