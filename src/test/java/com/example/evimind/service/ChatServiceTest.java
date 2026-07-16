package com.example.evimind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemory;

class ChatServiceTest {

  private static final String MODEL_UNAVAILABLE =
      "AI model not available. Please configure an AI provider.";

  @Test
  void streamChatShouldReturnClearMessageWhenNoChatClientConfigured() {
    ChatService service = new ChatService(Map.of(), new InMemoryChatMemory());

    List<String> chunks = service.streamChat("deepseek", "hello", "session-1").collectList().block();

    assertThat(chunks).containsExactly(MODEL_UNAVAILABLE);
  }

  @Test
  void chatSyncShouldReturnClearMessageWhenNoChatClientConfigured() {
    ChatService service = new ChatService(Map.of(), new InMemoryChatMemory());

    String response = service.chatSync("deepseek", "hello", "session-1");

    assertThat(response).isEqualTo(MODEL_UNAVAILABLE);
  }

  @Test
  void chatSyncShouldRejectAnExplicitlyUnavailableProviderInsteadOfUsingAnotherClient() {
    ChatService service = new ChatService(Map.of("zhipu", mock(org.springframework.ai.chat.client.ChatClient.class)), new InMemoryChatMemory());

    String response = service.chatSync("deepseek", "hello", "session-1");

    assertThat(response).isEqualTo(MODEL_UNAVAILABLE);
  }
}
