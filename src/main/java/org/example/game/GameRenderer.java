package org.example.game;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class GameRenderer {
    private static final int CELL_SIZE = 50; // Размер клетки в пикселях
    private final Canvas canvas;
    private final GraphicsContext gc;

    public GameRenderer(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }

    public void renderGameField(int[][] gameField) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight()); // Очистка холста

        for (int y = 0; y < gameField.length; y++) {
            for (int x = 0; x < gameField[y].length; x++) {
                if (gameField[y][x] == 1) {
                    // Отрисовка препятствия
                    gc.setFill(Color.RED);
                    gc.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                } else {
                    // Отрисовка дороги
                    gc.setFill(Color.GRAY);
                    gc.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }
    }

    public void renderCar(int x, int y) {
        // Отрисовка машинки
        gc.save(); // Сохраняем текущие настройки GraphicsContext
        gc.translate(x * CELL_SIZE, y * CELL_SIZE); // Перемещаем начало координат
        drawCar(gc); // Рисуем машинку
        gc.restore(); // Восстанавливаем настройки GraphicsContext
    }

    private void drawCar(GraphicsContext gc) {
        // Кузов машинки (прямоугольник)
        gc.setFill(Color.BLUE);
        gc.fillRect(10, 10, 30, 20);

        // Окна машинки (два маленьких прямоугольника)
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(15, 12, 8, 6); // Левое окно
        gc.fillRect(27, 12, 8, 6); // Правое окно

        // Колёса машинки (два круга)
        gc.setFill(Color.BLACK);
        gc.fillOval(12, 25, 8, 8); // Левое колесо
        gc.fillOval(30, 25, 8, 8); // Правое колесо
    }
}