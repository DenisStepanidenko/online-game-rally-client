package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;


/**
 * Класс лобби для парсинга ответа сервера
 */
@Getter
public class Lobby {

    @JsonProperty("id")
    private int id;
    @JsonProperty("name_of_lobby")
    private String nameOfLobby;

    @JsonProperty("count_of_players")
    private int countOfPlayersInLobby;

    @JsonProperty("player1")
    private String player1;

    @JsonProperty("player2")
    private String player2;

    @JsonProperty("starting_game")
    private boolean isStartingGame;

    @Override
    public String toString() {
        return "Lobby{" +
                "id=" + id +
                ", nameOfLobby='" + nameOfLobby + '\'' +
                ", countOfPlayersInLobby=" + countOfPlayersInLobby +
                ", player1='" + player1 + '\'' +
                ", player2='" + player2 + '\'' +
                '}';
    }
}
