package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class TopScoresByTime {
    private String username;

    @JsonProperty("best_time")
    private Double bestTime;
}
