package org.example.jsonparser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.GameState;
import org.example.model.Lobby;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class JsonParser {

    private static final Logger logger = LoggerFactory.getLogger(JsonParser.class);

    public static Optional<List<Lobby>> parseLobbies(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Optional.of(objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Lobby.class)));
        } catch (JsonProcessingException e) {
            logger.info("Произошла ошибка " + e.getMessage() + " при парсинге лобби");
            return Optional.empty();
        }
    }

    public static Optional<GameState> parseGameState(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Optional.of(objectMapper.readValue(json, GameState.class));
        } catch (JsonProcessingException e) {
            logger.info("Произошла ошибка " + e.getMessage() + " при парсинге лобби");
            return Optional.empty();
        }
    }
}
