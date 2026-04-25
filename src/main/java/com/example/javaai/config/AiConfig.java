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

    /**
     * 配置基于内存的聊天历史记录
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 根据配置文件动态创建多个大模型的 ChatClient
     */
    @Bean
    public Map<String, ChatClient> chatClients() {
        Map<String, ChatClient> clients = new HashMap<>();

        if (aiProperties.getProviders() != null) {
            for (Map.Entry<String, AiProperties.ProviderConfig> entry : aiProperties.getProviders().entrySet()) {
                String providerName = entry.getKey();
                AiProperties.ProviderConfig config = entry.getValue();

                // 1. 初始化底层的 OpenAiApi
                OpenAiApi openAiApi = new OpenAiApi(config.getBaseUrl(), config.getApiKey());

                // 2. 初始化该模型的专有配置 (如 model 名字和 temperature)
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .withModel(config.getModel())
                        .withTemperature(config.getTemperature() != null ? config.getTemperature().floatValue() : 0.7f)
                        .build();

                // 3. 构造 OpenAiChatModel (实现了 ChatModel 接口)
                OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);

                // 4. 构造 ChatClient，并加入模型池
                ChatClient client = ChatClient.builder(chatModel).build();
                clients.put(providerName, client);
            }
        }
        return clients;
    }
}
