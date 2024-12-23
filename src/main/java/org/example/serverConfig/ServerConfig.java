package org.example.serverConfig;

/**
 * Класс с данными для подключения к серверу
 */
public enum ServerConfig {
    SERVER_ADDRESS("192.168.1.49"),
    SERVER_PORT("8082");

    private String value;

    ServerConfig(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
