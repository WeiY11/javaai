package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AiClientResolverTest {

  @Test
  void resolveShouldOnlyUseTheRequestedProviderWhenOneIsSpecified() {
    ChatClient zhipu = mock(ChatClient.class);

    assertThat(AiClientResolver.resolve(Map.of("zhipu", zhipu), "deepseek")).isNull();
  }

  @Test
  void resolveShouldChooseAStableDefaultWhenTheProviderIsOmitted() {
    ChatClient openai = mock(ChatClient.class);
    ChatClient zhipu = mock(ChatClient.class);
    Map<String, ChatClient> clients = new HashMap<>();
    clients.put("zhipu", zhipu);
    clients.put("openai", openai);

    assertThat(AiClientResolver.resolve(clients, null)).isSameAs(openai);
    assertThat(AiClientResolver.resolveProviderName(clients, null)).contains("openai");
  }
}
