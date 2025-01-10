package org.example.game;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import org.example.listener.ServerListener;


/**
 * Игровой цикл
 */
@Getter
public class GameScreen {
    private final Canvas canvas;
    private final GameRenderer gameRenderer;
    private final GameController gameController;
    private boolean isGameOver = false;
    private long startTime;
    private ServerListener serverListener;

    public GameScreen(int[][] gameField, ServerListener serverListener) {
        this.canvas = new Canvas(1920, 1080);
        this.gameRenderer = new GameRenderer(canvas);
        this.gameController = new GameController(gameField, serverListener);
        this.serverListener = serverListener;

        // Отрисовка начального состояния
        gameRenderer.renderGameField(gameController.getVisibleGameField());
        gameRenderer.renderCar(gameController.getCarX(), gameController.getCarY());

        startTime = System.currentTimeMillis();
    }

    public Scene getScene() {
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);


        scene.setOnKeyPressed(gameController::handleKeyPress);

        // Игровой цикл
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isGameOver) {
                    gameController.update();
                    gameRenderer.renderGameField(gameController.getVisibleGameField());
                    gameRenderer.renderCar(gameController.getCarX(), gameController.getCarY());

                    // Проверка на финиш
                    if (gameController.isGameOver()) {
                        isGameOver = true;

                        long finishTime = System.currentTimeMillis() - startTime;
                        serverListener.sendMessage("FINISH/" + finishTime);

                        root.getChildren().clear();
                        GraphicsContext gc = canvas.getGraphicsContext2D();
                        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    }
                }
            }
        }.start();


        return scene;
    }
}