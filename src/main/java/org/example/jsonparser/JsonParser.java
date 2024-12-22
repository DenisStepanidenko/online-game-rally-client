package org.example.jsonparser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Lobby;

import java.util.List;
import java.util.Optional;

public class JsonParser {

    public static Optional<List<Lobby>> parseLobbies(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Optional.of(objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Lobby.class)));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
