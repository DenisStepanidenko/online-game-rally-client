package org.example.car;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class CarGenerator extends Application {

    private static final int CELL_SIZE = 50; // Размер клетки (и машинки)

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(CELL_SIZE, CELL_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Отрисовка машинки
        drawCar(gc);

        Group root = new Group(canvas);
        Scene scene = new Scene(root, CELL_SIZE, CELL_SIZE);
        primaryStage.setTitle("Машинка");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Отрисовка машинки
     *
     * @param gc GraphicsContext для отрисовки
     */
    private void drawCar(GraphicsContext gc) {
        // Очистка холста
        gc.clearRect(0, 0, CELL_SIZE, CELL_SIZE);

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

    public static void main(String[] args) {
        launch(args);
    }
}