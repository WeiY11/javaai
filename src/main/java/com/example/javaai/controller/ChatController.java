package com.example.javaai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 普通对话接口（等待模型完整生成后返回）
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(value = "message", defaultValue = "你好") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * 流式对话接口（Server-Sent Events 格式返回，实现打字机效果）
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam(value = "message", defaultValue = "你好") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
