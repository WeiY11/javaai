package com.example.javaai.controller;

import com.example.javaai.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式对话接口，增加了 provider (模型选择) 和 sessionId (对话上下文标识)
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam(value = "message") String message,
            @RequestParam(value = "provider", defaultValue = "deepseek") String provider,
            @RequestParam(value = "sessionId", defaultValue = "default-session") String sessionId) {
        
        return chatService.streamChat(provider, message, sessionId);
    }
}
