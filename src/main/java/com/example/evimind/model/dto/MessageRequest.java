package com.example.evimind.model.dto;

import lombok.Data;

@Data
public class MessageRequest {
    private String content;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private String modelName;
    private Boolean thinking;
    private String reasoningEffort;
}
