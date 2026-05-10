package com.example.evimind.model.dto;

import lombok.Data;

@Data
public class MessageRequest {
    private String content;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
}
