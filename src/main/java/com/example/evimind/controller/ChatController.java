package com.example.evimind.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.evimind.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

  private final ChatService chatService;
  private final com.example.evimind.config.AiProperties aiProperties;

  public ChatController(
      ChatService chatService, com.example.evimind.config.AiProperties aiProperties) {
    this.chatService = chatService;
    this.aiProperties = aiProperties;
  }

  /** 流式对话接口，增加了 provider (模型选择) 和 sessionId (对话上下文标识) */
  @Operation(summary = "流式 AI 对话", description = "与选定的 AI 模型进行流式对话，支持上下文记忆")
  @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
  public Flux<String> chatStream(
      @RequestParam(value = "message") String message,
      @RequestParam(value = "provider", defaultValue = "deepseek") String provider,
      @RequestParam(value = "sessionId", defaultValue = "default-session") String sessionId) {

    return chatService.streamChat(provider, message, sessionId);
  }

  @Operation(summary = "获取大模型列表", description = "返回系统已配置的所有 AI 接入接口")
  @GetMapping("/models")
  public com.example.evimind.model.dto.ApiResponse<java.util.List<java.util.Map<String, Object>>>
      getAvailableModels() {
    java.util.List<java.util.Map<String, Object>> models = new java.util.ArrayList<>();
    if (aiProperties.getProviders() != null) {
      aiProperties
          .getProviders()
          .forEach(
              (name, config) -> {
                java.util.Map<String, Object> modelInfo = new java.util.HashMap<>();
                modelInfo.put("provider", name);
                modelInfo.put("model", config.getModel());
                modelInfo.put("baseUrl", config.getBaseUrl());
                // Return masked API key for security but to show it's configured
                String maskedKey =
                    config.getApiKey() != null && config.getApiKey().length() > 8
                        ? config.getApiKey().substring(0, 4)
                            + "..."
                            + config.getApiKey().substring(config.getApiKey().length() - 4)
                        : "***";
                modelInfo.put("apiKey", maskedKey);
                models.add(modelInfo);
              });
    }
    return com.example.evimind.model.dto.ApiResponse.success(models);
  }
}
