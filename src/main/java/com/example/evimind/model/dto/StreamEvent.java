package com.example.evimind.model.dto;

import com.example.evimind.qa.RagResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class StreamEvent {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String token(String text) {
        try {
            return mapper.writeValueAsString(new TokenEvent("token", text));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"token\",\"text\":\"" + escapeJson(text) + "\"}";
        }
    }

    public static String citations(List<RagResponse.Citation> citationList) {
        try {
            return mapper.writeValueAsString(new CitationEvent("citations", citationList));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"citations\",\"citations\":[]}";
        }
    }

    public static String done(Long messageId) {
        return "{\"type\":\"done\",\"messageId\":" + messageId + "}";
    }

    public static String error(String message) {
        try {
            return mapper.writeValueAsString(new ErrorEvent("error", message));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"" + escapeJson(message) + "\"}";
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    record TokenEvent(String type, String text) {}
    record CitationEvent(String type, List<RagResponse.Citation> citations) {}
    record ErrorEvent(String type, String message) {}
}
