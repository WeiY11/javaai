package com.example.evimind.controller;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
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
  private final Map<String, ChatClient> chatClients;

  public ChatController(
      ChatService chatService,
      com.example.evimind.config.AiProperties aiProperties,
      Map<String, ChatClient> chatClients) {
    this.chatService = chatService;
    this.aiProperties = aiProperties;
    this.chatClients = chatClients;
  }

  /** 流式对话接口，增加了 provider (模型选择) 和 sessionId (对话上下文标识) */
  @Operation(summary = "流式 AI 对话", description = "与选定的 AI 模型进行流式对话，支持上下文记忆")
  @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
  public Flux<String> chatStream(
      @RequestParam(value = "message") String message,
      @RequestParam(value = "provider", required = false) String provider,
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
                modelInfo.put("configured", chatClients.containsKey(name));
                models.add(modelInfo);
              });
    }
    return com.example.evimind.model.dto.ApiResponse.success(models);
  }
}
