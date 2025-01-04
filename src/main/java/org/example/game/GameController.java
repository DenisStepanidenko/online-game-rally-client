package org.example.game;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class GameController {
    private int carX = 0;
    private int carY = 0;
    private int[][] gameField;
    private double speed = 1.0;
    private long slowdownTimer = 0;
    private static final long SLOWDOWN_DURATION = 2000;
    private long lastUpdateTime = System.currentTimeMillis();
    private boolean isGameOver = false;
    private int currentRow = 0;

    public GameController(int[][] gameField) {
        this.gameField = gameField;
    }

    public void handleKeyPress(KeyEvent event) {
        if (isGameOver) return; // Если игра завершена, управление отключено

        KeyCode keyCode = event.getCode();
        int newX = carX;
        int newY = carY;

        // Определение новой позиции машинки
        if (keyCode == KeyCode.LEFT) {
            newX--;
        } else if (keyCode == KeyCode.RIGHT) {
            newX++;
        }

        System.out.println("Y = " + (currentRow) + ", X = " + newX);
        // Проверка на выход за границы поля и столкновение с препятствием
        if (isValidMove(newX, newY)) {
            carX = newX;
            carY = newY;

            if(gameField[currentRow + carY][carX] == 1){
                speed = 0.2; // Замедление
                slowdownTimer = System.currentTimeMillis() + SLOWDOWN_DURATION;
            }
        }

    }

    private boolean isValidMove(int x, int y) {
        return x >= 0 && x < gameField[0].length && y >= 0 && y < gameField.length;
    }

    public void update() {
        if (isGameOver) return;

        long currentTime = System.currentTimeMillis();

        if(gameField[currentRow + carY][carX] == 1){
            speed = 0.2; // Замедление
            slowdownTimer = System.currentTimeMillis() + SLOWDOWN_DURATION;
        }

        // Обновление позиции машинки и поля в зависимости от скорости
        if (currentTime - lastUpdateTime > 1000 / speed) {
            currentRow++; // Прокручиваем поле вверх

            lastUpdateTime = currentTime;
        }

        // Проверка, закончилось ли замедление
        if (currentTime > slowdownTimer) {
            speed = 1.0;
        }

        // Проверка на финиш
        if (currentRow + carY >= gameField.length) {
            isGameOver = true;
        }
    }

    public int getCarX() {
        return carX;
    }

    public int getCarY() {
        return carY;
    }

    public int[][] getVisibleGameField() {
        int visibleRows = 10;
        int[][] visibleField = new int[visibleRows][gameField[0].length];

        for (int y = 0; y < visibleRows; y++) {
            int row = currentRow + y;
            if (row < gameField.length) {
                System.arraycopy(gameField[row], 0, visibleField[y], 0, gameField[row].length);
            }
        }

        return visibleField;
    }

    public boolean isGameOver() {
        return isGameOver;
    }
}