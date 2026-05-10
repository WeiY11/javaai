package com.example.evimind.config;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AiConfig {

    private final AiProperties aiProperties;
    private final EmbeddingProperties embeddingProperties;

    public AiConfig(AiProperties aiProperties, EmbeddingProperties embeddingProperties) {
        this.aiProperties = aiProperties;
        this.embeddingProperties = embeddingProperties;
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

                try {
                    OpenAiApi openAiApi = new OpenAiApi(config.getBaseUrl(), config.getApiKey());

                    OpenAiChatOptions options = OpenAiChatOptions.builder()
                            .withModel(config.getModel())
                            .withTemperature(config.getTemperature() != null ? config.getTemperature().floatValue() : 0.7f)
                            .build();

                    OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
                    ChatClient client = ChatClient.builder(chatModel).build();
                    clients.put(providerName, client);
                } catch (Exception e) {
                    LoggerFactory.getLogger(AiConfig.class)
                            .warn("Failed to create ChatClient for provider '{}': {}", providerName, e.getMessage());
                }
            }
        }
        return clients;
    }

    @Bean
    @ConditionalOnProperty(name = "custom.ai.embedding.enabled", havingValue = "true")
    public OpenAiEmbeddingModel embeddingModel() {
        String baseUrl = embeddingProperties.getBaseUrl();
        String apiKey = embeddingProperties.getApiKey();
        String model = embeddingProperties.getModel();

        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            LoggerFactory.getLogger(AiConfig.class)
                    .warn("Embedding config incomplete, embedding model will not be available");
            return null;
        }

        OpenAiApi embeddingApi = new OpenAiApi(baseUrl, apiKey);
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED);
        LoggerFactory.getLogger(AiConfig.class)
                .info("Embedding model created: model={}, dimension={}", model, embeddingProperties.getDimension());
        return embeddingModel;
    }

    @Bean
    public int embeddingDimension() {
        return embeddingProperties.getDimension();
    }
}
