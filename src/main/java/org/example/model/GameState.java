package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class GameState {
    @JsonProperty("game_field")
    private int[][] gameField;
}
