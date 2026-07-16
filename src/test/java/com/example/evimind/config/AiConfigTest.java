package com.example.evimind.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.RestClient;

class AiConfigTest {

  @Test
  void chatClientsShouldIgnoreProvidersWithoutAnApiKey() {
    AiProperties.ProviderConfig provider = new AiProperties.ProviderConfig();
    provider.setBaseUrl("https://api.example.test/v1");
    provider.setApiKey("   ");
    provider.setModel("example-model");

    AiProperties properties = new AiProperties();
    properties.setProviders(Map.of("example", provider));

    Map<String, ChatClient> clients =
        new AiConfig(properties, new EmbeddingProperties()).chatClients(RestClient.builder());

    assertThat(clients).isEmpty();
  }

  @Test
  void chatClientsShouldIgnoreProvidersWithoutAConfiguredModel() {
    AiProperties.ProviderConfig provider = new AiProperties.ProviderConfig();
    provider.setBaseUrl("https://api.example.test/v1");
    provider.setApiKey("sk-test-secret-token");
    provider.setModel("  ");

    AiProperties properties = new AiProperties();
    properties.setProviders(Map.of("example", provider));

    Map<String, ChatClient> clients =
        new AiConfig(properties, new EmbeddingProperties()).chatClients(RestClient.builder());

    assertThat(clients).isEmpty();
  }
}
