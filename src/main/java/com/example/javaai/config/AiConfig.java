package com.example.javaai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AiConfig {

    private final AiProperties aiProperties;

    public AiConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public Map<String, ChatClient> chatClients() {
        Map<String, ChatClient> clients = new HashMap<>();

        if (aiProperties.getProviders() != null) {
            for (Map.Entry<String, AiProperties.ProviderConfig> entry : aiProperties.getProviders().entrySet()) {
                String providerName = entry.getKey();
                AiProperties.ProviderConfig config = entry.getValue();

                OpenAiApi openAiApi = new OpenAiApi(config.getBaseUrl(), config.getApiKey());

                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(config.getTemperature() != null ? config.getTemperature().floatValue() : 0.7f)
                        .build();

                OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);

                ChatClient client = ChatClient.builder(chatModel).build();
                clients.put(providerName, client);
            }
        }
        return clients;
    }
}
