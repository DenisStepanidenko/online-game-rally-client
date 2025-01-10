package org.example.listener;

import javafx.application.Platform;
import org.example.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;


/**
 * Обработчик и отправитель сообщений серверу
 */
public class ServerListener implements Runnable {
    private Logger logger = LoggerFactory.getLogger(ServerListener.class);
    private BufferedReader input;
    private Socket socket;
    private PrintWriter output;
    private Client client;

    public ServerListener(Socket socket, Client client) {
        this.client = client;

        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);
            this.socket = socket;
            logger.info("Потоки ввода/вывода с сервером " + socket.getInetAddress() + " успешно открыты");
        } catch (IOException e) {
            logger.info("Произошла ошибка" + e.getMessage() + " при открытии потока ввода/вывода с сервером " + socket.getInetAddress());
            Platform.runLater(client::handleServerError);
            closeConnection();
        }
    }


    @Override
    public void run() {
        try {
            while (true) {
                String response = input.readLine();
                logger.info("Получено сообщение " + response + " от сервера " + socket.getInetAddress());



                if (Objects.isNull(response)) {
                    closeConnection();
                    Platform.runLater(client::handleServerError);
                    break;
                } else {
                    Platform.runLater(() -> client.handleServerResponse(response));
                    if(response.equals("DISCONNECT_ACK")){
                        closeConnection();
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            logger.info("Произошла ошибка при работе с сервером " + socket.getInetAddress());
            Platform.runLater(client::handleServerError);
            closeConnection();
        }
    }

    public void sendMessage(String message) {
        if (Objects.nonNull(output)) {
            output.println(message);
            logger.info("Отправлено сообщение " + message + " серверу " + socket.getInetAddress());
        } else {
            logger.info("Попытка отправить сообщение, когда поток вывода не инициализирован");
            Platform.runLater(client::handleServerError);
            closeConnection();
        }
    }

    public void closeConnection() {
        if (Objects.nonNull(socket) && !socket.isClosed()) {
            try {
                socket.close();
                logger.info("Сокет с сервером " + socket.getInetAddress() + " закрыт");
                input.close();
                logger.info("Поток ввода с сервером " + socket.getInetAddress() + " закрыт");
                output.close();
                logger.info("Поток вывода с сервером " + socket.getInetAddress() + " закрыт");
            } catch (IOException e) {
                logger.info("Произошла ошибка при закрытии соединения");
            }

        }
    }


}
