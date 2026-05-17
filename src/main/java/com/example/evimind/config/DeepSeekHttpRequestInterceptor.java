package com.example.evimind.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class DeepSeekHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (body.length > 0 && request.getURI().getHost().contains("deepseek")) {
            try {
                JsonNode rootNode = objectMapper.readTree(body);
                if (rootNode.isObject() && rootNode.has("model")) {
                    String modelStr = rootNode.get("model").asText();
                    if (modelStr != null && modelStr.contains("|thinking:enabled")) {
                        ObjectNode objectNode = (ObjectNode) rootNode;
                        
                        // Parse encoded string
                        String[] parts = modelStr.split("\\|");
                        String actualModel = parts[0];
                        String effort = null;
                        for (String part : parts) {
                            if (part.startsWith("effort:")) {
                                effort = part.substring("effort:".length());
                            }
                        }

                        // Modify JSON
                        objectNode.put("model", actualModel);
                        ObjectNode thinkingNode = objectMapper.createObjectNode();
                        thinkingNode.put("type", "enabled");
                        objectNode.set("thinking", thinkingNode);
                        
                        if (effort != null && !effort.isBlank()) {
                            objectNode.put("reasoning_effort", effort);
                        }

                        byte[] newBody = objectMapper.writeValueAsBytes(objectNode);
                        return execution.execute(request, newBody);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to intercept and modify DeepSeek request payload", e);
            }
        }
        return execution.execute(request, body);
    }
}
