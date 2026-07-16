package com.example.evimind.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import com.example.evimind.config.AiClientResolver;

import reactor.core.publisher.Flux;

@Service
public class ChatService {

  private static final String MODEL_UNAVAILABLE =
      "AI model not available. Please configure an AI provider.";

  private final Map<String, ChatClient> chatClients;
  private final ChatMemory chatMemory;

  public ChatService(Map<String, ChatClient> chatClients, ChatMemory chatMemory) {
    this.chatClients = chatClients;
    this.chatMemory = chatMemory;
  }

  public Flux<String> streamChat(String provider, String message, String sessionId) {
    ChatClient baseClient = resolveClient(provider);
    if (baseClient == null) {
      return Flux.just(MODEL_UNAVAILABLE);
    }

    return baseClient
        .mutate()
        .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 100))
        .build()
        .prompt()
        .system(
            "你是一个具有本地数据文件管理、内容检索和历史分析结果查询能力的智能 AI 助手。你可以而且应该优先使用提供的工具函数（如 listDirectory, readFileContent, queryAnalysisHistory）来获取本地文件的实际状态和内容，然后再给用户作答。不要凭空捏造文件列表或内容。")
        .user(message)
        .stream()
        .content();
  }

  public String chatSync(String provider, String message, String sessionId) {
    ChatClient baseClient = resolveClient(provider);
    if (baseClient == null) {
      return MODEL_UNAVAILABLE;
    }

    return baseClient
        .mutate()
        .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 100))
        .build()
        .prompt()
        .system("你是一个具有本地数据文件管理、内容检索和历史分析结果查询能力的智能 AI 助手。")
        .user(message)
        .call()
        .content();
  }

  private ChatClient resolveClient(String provider) {
    return AiClientResolver.resolve(chatClients, provider);
  }
}
