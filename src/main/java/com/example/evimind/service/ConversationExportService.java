package com.example.evimind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.model.entity.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationExportService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String exportAsMarkdown(Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return "";

        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(conv.getTitle() != null ? conv.getTitle() : "Conversation " + conv.getId()).append("\n\n");
        sb.append("> Model: ").append(conv.getModelProvider() != null ? conv.getModelProvider() : "unknown").append("\n");
        sb.append("> Created: ").append(conv.getCreatedAt()).append("\n\n---\n\n");

        for (Message msg : messages) {
            sb.append("**").append(msg.getRole().toUpperCase()).append("**:\n\n");
            sb.append(msg.getContent()).append("\n\n");
            if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
                sb.append("> Citations: ").append(msg.getCitations()).append("\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    public String exportAsJson(Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return "{}";

        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
        );

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    java.util.Map.of("conversation", conv, "messages", messages)
            );
        } catch (Exception e) {
            log.error("Failed to export conversation as JSON", e);
            return "{\"error\": \"Export failed\"}";
        }
    }
}
