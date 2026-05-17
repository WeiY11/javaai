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
    public Map<String, ChatClient> chatClients(org.springframework.web.client.RestClient.Builder restClientBuilder) {
        Map<String, ChatClient> clients = new HashMap<>();

        if (aiProperties.getProviders() != null) {
            for (Map.Entry<String, AiProperties.ProviderConfig> entry : aiProperties.getProviders().entrySet()) {
                String providerName = entry.getKey();
                AiProperties.ProviderConfig config = entry.getValue();

                try {
                    org.springframework.web.client.RestClient.Builder rcBuilder = restClientBuilder.clone();
                    org.springframework.web.reactive.function.client.WebClient.Builder wcBuilder = org.springframework.web.reactive.function.client.WebClient.builder();

                    if ("deepseek".equalsIgnoreCase(providerName)) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule().setSerializerModifier(
                                new com.fasterxml.jackson.databind.ser.BeanSerializerModifier() {
                                    @Override
                                    public com.fasterxml.jackson.databind.JsonSerializer<?> modifySerializer(
                                            com.fasterxml.jackson.databind.SerializationConfig config,
                                            com.fasterxml.jackson.databind.BeanDescription beanDesc,
                                            com.fasterxml.jackson.databind.JsonSerializer<?> serializer) {
                                        
                                        if (beanDesc.getBeanClass().getName().contains("ChatCompletionRequest")) {
                                            return new com.fasterxml.jackson.databind.JsonSerializer<Object>() {
                                                @SuppressWarnings("unchecked")
                                                @Override
                                                public void serialize(Object value, com.fasterxml.jackson.core.JsonGenerator gen,
                                                                      com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                                                    com.fasterxml.jackson.databind.util.TokenBuffer tb = new com.fasterxml.jackson.databind.util.TokenBuffer(mapper, false);
                                                    ((com.fasterxml.jackson.databind.JsonSerializer<Object>) serializer).serialize(value, tb, serializers);
                                                    
                                                    com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(tb.asParser());
                                                    if (rootNode.isObject() && rootNode.has("model")) {
                                                        String modelStr = rootNode.get("model").asText();
                                                        if (modelStr != null && modelStr.contains("|thinking:enabled")) {
                                                            com.fasterxml.jackson.databind.node.ObjectNode objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rootNode;
                                                            String[] parts = modelStr.split("\\|");
                                                            String actualModel = parts[0];
                                                            String effort = null;
                                                            for (String part : parts) {
                                                                if (part.startsWith("effort:")) {
                                                                    effort = part.substring("effort:".length());
                                                                }
                                                            }
                                                            objectNode.put("model", actualModel);
                                                            com.fasterxml.jackson.databind.node.ObjectNode thinkingNode = mapper.createObjectNode();
                                                            thinkingNode.put("type", "enabled");
                                                            objectNode.set("thinking", thinkingNode);
                                                            if (effort != null && !effort.isBlank()) {
                                                                objectNode.put("reasoning_effort", effort);
                                                            }
                                                        }
                                                    }
                                                    gen.writeTree(rootNode);
                                                }
                                            };
                                        }
                                        return serializer;
                                    }
                                }
                        ));

                        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter = 
                                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper);
                        rcBuilder.messageConverters(converters -> {
                            converters.removeIf(c -> c instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter);
                            converters.add(converter);
                        });

                        wcBuilder.exchangeStrategies(org.springframework.web.reactive.function.client.ExchangeStrategies.builder()
                                .codecs(configurer -> {
                                    configurer.defaultCodecs().jackson2JsonEncoder(new org.springframework.http.codec.json.Jackson2JsonEncoder(mapper));
                                    configurer.defaultCodecs().jackson2JsonDecoder(new org.springframework.http.codec.json.Jackson2JsonDecoder(mapper));
                                })
                                .build());
                    }
                    
                    OpenAiApi openAiApi = new OpenAiApi(config.getBaseUrl(), config.getApiKey(), rcBuilder, wcBuilder);

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
