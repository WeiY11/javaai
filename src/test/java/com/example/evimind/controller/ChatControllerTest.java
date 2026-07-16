package com.example.evimind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.example.evimind.config.AiProperties;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.service.ChatService;

class ChatControllerTest {

  @Test
  void getAvailableModelsShouldExposeConfiguredFlagWithoutSensitiveConfiguration() {
    AiProperties.ProviderConfig provider = new AiProperties.ProviderConfig();
    provider.setBaseUrl("https://api.example.test");
    provider.setModel("example-model");
    provider.setApiKey("sk-test-secret-token");

    AiProperties properties = new AiProperties();
    properties.setProviders(Map.of("example", provider));

    ChatController controller =
        new ChatController(mock(ChatService.class), properties, Map.of("example", mock(ChatClient.class)));

    ApiResponse<List<Map<String, Object>>> response = controller.getAvailableModels();

    assertThat(response.getData()).hasSize(1);
    Map<String, Object> model = response.getData().get(0);
    assertThat(model).containsEntry("provider", "example");
    assertThat(model).containsEntry("configured", true);
    assertThat(model).doesNotContainKeys("apiKey", "baseUrl");
    assertThat(model.toString()).doesNotContain("sk-test-secret-token");
  }

  @Test
  void getAvailableModelsShouldMarkAProviderUnavailableWhenItsClientWasNotCreated() {
    AiProperties.ProviderConfig provider = new AiProperties.ProviderConfig();
    provider.setModel("example-model");
    provider.setApiKey("sk-test-secret-token");

    AiProperties properties = new AiProperties();
    properties.setProviders(Map.of("example", provider));

    ChatController controller = new ChatController(mock(ChatService.class), properties, Map.of());

    ApiResponse<List<Map<String, Object>>> response = controller.getAvailableModels();

    assertThat(response.getData().get(0)).containsEntry("configured", false);
  }
}
