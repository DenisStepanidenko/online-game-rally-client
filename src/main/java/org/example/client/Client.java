package org.example.client;

import com.sun.org.slf4j.internal.LoggerFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.serverConfig.ServerConfig;
import sun.security.krb5.internal.PAData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client extends Application {
    private static final String SERVER_ADDRESS = ServerConfig.SERVER_ADDRESS.getValue();
    private static final int SERVER_PORT = Integer.parseInt(ServerConfig.SERVER_PORT.getValue());

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private Stage primaryStage;
    private Stage connectStage;
    private Stage waitingStage;

    private Label timerLabel;
    private Stage authStage;
    private Thread timeThread;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

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
                    throw new RuntimeException(e);
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
        authStage = new Stage();
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
        authStage.setTitle("Rally");
        authStage.setScene(authScene);
        authStage.show();
    }

    private void sendUsername(String userName) {
        new Thread(() -> {


            try {

                output.println("USER/" + userName);


                String response = input.readLine();
                System.out.println(response);
                if ("USER_ACK_CHECK".equals(response)) {
                    Platform.runLater(() -> showPasswordWindow("Подтвердите пароль: "));
                } else if ("USER_ACK_CREATE".equals(response)) {
                    Platform.runLater(() -> showPasswordWindow("Придумайте пароль: "));
                } else {
                    Platform.runLater(() -> {
                        authStage.close();
                        closeConnection();
                        showStartWindow();
                    });
                }


            } catch (IOException e) {
                Platform.runLater(() -> {
                    authStage.close();
                    closeConnection();
                    showStartWindow();
                });

            }


        }).start();
    }

    private void showPasswordWindow(String s) {
        authStage.close();
        VBox authRoot = new VBox(10);
        authRoot.setPadding(new Insets(20));
        authRoot.setAlignment(Pos.CENTER);

        Label authLabel = new Label(s);
        PasswordField passwordField = new PasswordField();
        Button submitButton = new Button("Подветрдить");

        submitButton.setOnAction(e -> {
            String password = passwordField.getText();
            sendPassword(password);
        });

        authRoot.getChildren().addAll(authLabel, passwordField, submitButton);
        Scene authScene = new Scene(authRoot, 1920, 1080);

        authStage.setScene(authScene);
        authStage.show();
    }

    private void sendPassword(String password) {
        new Thread(() -> {
            try {
                output.println("PASS/" + password);

                String response = input.readLine();
                if ("PASS_ACK_SUCESS".equals(response)) {
                    Platform.runLater(this::showGameMenu);
                } else if ("PASS_ACK_FAIL".equals(response)) {
                    Platform.runLater(() -> {
                        showPasswordWindow("Неверный пароль. Попробуйте снова: ");
                    });
                } else {
                    Platform.runLater(() -> {
                        authStage.close();
                        showStartWindow();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    authStage.close();
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
        primaryStage.setTitle("Rally");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Экран ожидания подключения к серверу ( ждём когда выполнится строчка socket = new Socket(...) )
     */
    private void showConnectWindow() {
        primaryStage.close();
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
        VBox menuRoot = new VBox(10);
        menuRoot.setPadding(new Insets(20));
        menuRoot.setAlignment(Pos.CENTER);

        Button enterGameButton = new Button("Вход в игру");
        enterGameButton.setPrefWidth(400);
        menuRoot.getChildren().add(enterGameButton);

        Scene menuScene = new Scene(menuRoot, 1920, 1080);
        primaryStage.setScene(menuScene);
        primaryStage.show();
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