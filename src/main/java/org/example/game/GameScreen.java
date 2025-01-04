package org.example.game;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import lombok.Getter;

@Getter
public class GameScreen {
    private final Canvas canvas;
    private final GameRenderer gameRenderer;
    private final GameController gameController;
    private boolean isGameOver = false;

    public GameScreen(int[][] gameField) {
        this.canvas = new Canvas(1920, 1080); // Размер холста (1920x1080)
        this.gameRenderer = new GameRenderer(canvas);
        this.gameController = new GameController(gameField);

        // Отрисовка начального состояния
        gameRenderer.renderGameField(gameController.getVisibleGameField());
        gameRenderer.renderCar(gameController.getCarX(), gameController.getCarY());
    }

    public Scene getScene() {
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        // Обработка нажатий клавиш
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

                    }
                }
            }
        }.start();

        return scene;
    }
}