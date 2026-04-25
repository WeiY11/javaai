package com.example.javaai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
public class ChatService {

    private final Map<String, ChatClient> chatClients;
    private final ChatMemory chatMemory;

    public ChatService(Map<String, ChatClient> chatClients, ChatMemory chatMemory) {
        this.chatClients = chatClients;
        this.chatMemory = chatMemory;
    }

    /**
     * 根据 provider 选择对应的 ChatClient，并加上历史上下文进行流式对话
     */
    public Flux<String> streamChat(String provider, String message, String sessionId) {
        ChatClient baseClient = chatClients.get(provider);
        if (baseClient == null) {
            // 如果传入的 provider 不存在，默认使用 map 里的第一个或者抛出异常
            baseClient = chatClients.values().iterator().next();
        }

        // 每次调用时通过 mutate 增加 advisor，这样可以保证请求间的隔离，
        // 并且利用 sessionId 读取属于该用户的聊天记录（默认保留最近的 100 条）
        return baseClient.mutate()
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 100))
                .build()
                .prompt()
                .user(message)
                .stream()
                .content();
    }
}
