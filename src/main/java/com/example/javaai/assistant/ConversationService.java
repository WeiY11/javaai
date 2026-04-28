package com.example.javaai.assistant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaai.config.PromptTemplateManager;
import com.example.javaai.identity.GroupContext;
import com.example.javaai.mapper.ConversationMapper;
import com.example.javaai.mapper.MessageMapper;
import com.example.javaai.model.dto.StreamEvent;
import com.example.javaai.model.entity.Conversation;
import com.example.javaai.model.entity.Message;
import com.example.javaai.qa.RagPipeline;
import com.example.javaai.qa.RagResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final PromptTemplateManager promptTemplateManager;
    private final RagPipeline ragPipeline;

    private static final int SUMMARY_WINDOW = 10;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Conversation createConversation(Long knowledgeBaseId, String modelProvider) {
        Conversation conv = new Conversation();
        conv.setUserId(GroupContext.getUserId());
        conv.setKnowledgeBaseId(knowledgeBaseId);
        conv.setModelProvider(modelProvider);
        conv.setStatus("ACTIVE");
        conversationMapper.insert(conv);
        return conv;
    }

    @Transactional
    public Message addMessage(Long conversationId, String role, String content, String citations, String toolCalls) {
        requireConversationOwner(conversationId);
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCitations(citations);
        msg.setToolCalls(toolCalls);
        messageMapper.insert(msg);

        checkAndGenerateSummary(conversationId);
        return msg;
    }

    public Flux<String> streamMessage(Long conversationId, String content) {
        Conversation conv = requireConversationOwner(conversationId);

        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        if (conv.getKnowledgeBaseId() == null) {
            messageMapper.deleteById(userMsg.getId());
            return Flux.just(StreamEvent.error("请先选择知识库再发起对话"));
        }

        if (!ragPipeline.isKbMember(conv.getKnowledgeBaseId())) {
            messageMapper.deleteById(userMsg.getId());
            return Flux.just(StreamEvent.error("Access denied: you are not a member of this knowledge base"));
        }

        String modelProvider = conv.getModelProvider() != null ? conv.getModelProvider() : "deepseek";

        StringBuilder fullContent = new StringBuilder();
        String[] citationsHolder = new String[1];

        return ragPipeline.streamQuery(content, conv.getKnowledgeBaseId(), modelProvider)
                .map(event -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = objectMapper.readValue(event, Map.class);
                        String type = (String) map.get("type");
                        if ("token".equals(type)) {
                            String text = (String) map.get("text");
                            if (text != null) fullContent.append(text);
                        } else if ("citations".equals(type)) {
                            citationsHolder[0] = objectMapper.writeValueAsString(map.get("citations"));
                        } else if ("done".equals(type)) {
                            saveAssistantMessage(conversationId, fullContent.toString(), citationsHolder[0]);
                            return "{\"type\":\"done\",\"messageId\":" + map.get("messageId") + "}";
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse stream event", e);
                    }
                    return event;
                })
                .doOnComplete(() -> {
                    if (fullContent.length() > 0) {
                        saveAssistantMessage(conversationId, fullContent.toString(), citationsHolder[0]);
                    }
                    checkAndGenerateSummary(conversationId);
                })
                .doOnError(e -> log.error("Stream error for conversation {}", conversationId, e));
    }

    private void saveAssistantMessage(Long conversationId, String content, String citations) {
        if (content == null || content.isBlank()) return;
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setCitations(citations);
        messageMapper.insert(msg);
    }

    public List<Message> getHistory(Long conversationId) {
        requireConversationOwner(conversationId);
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
        );
    }

    public List<Conversation> listConversations() {
        Long userId = GroupContext.getUserId();
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getStatus, "ACTIVE")
                        .orderByDesc(Conversation::getUpdatedAt)
        );
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        requireConversationOwner(conversationId);
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setStatus("DELETED");
            conversationMapper.updateById(conv);
        }
    }

    private Conversation requireConversationOwner(Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }
        Long userId = GroupContext.getUserId();
        if (userId != null && !userId.equals(conv.getUserId())) {
            throw new SecurityException("Access denied: you do not own this conversation");
        }
        return conv;
    }

    public String buildContext(Long conversationId, String currentQuery) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return currentQuery;

        StringBuilder context = new StringBuilder();

        if (conv.getSummary() != null && !conv.getSummary().isBlank()) {
            context.append("[会话摘要]\n").append(conv.getSummary()).append("\n\n");
        }

        List<Message> recentMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + SUMMARY_WINDOW)
        );
        java.util.Collections.reverse(recentMessages);

        for (Message msg : recentMessages) {
            context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        context.append("user: ").append(currentQuery);
        return context.toString();
    }

    private void checkAndGenerateSummary(Long conversationId) {
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
        );

        if (count > SUMMARY_WINDOW * 2) {
            Conversation conv = conversationMapper.selectById(conversationId);
            if (conv != null && (conv.getSummary() == null || conv.getSummary().isBlank())) {
                List<Message> earlyMessages = messageMapper.selectList(
                        new LambdaQueryWrapper<Message>()
                                .eq(Message::getConversationId, conversationId)
                                .orderByAsc(Message::getCreatedAt)
                                .last("LIMIT " + SUMMARY_WINDOW)
                );

                StringBuilder historyText = new StringBuilder();
                for (Message msg : earlyMessages) {
                    historyText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }

                Map<String, Object> vars = new HashMap<>();
                vars.put("messages", historyText.toString());

                String summaryPrompt = promptTemplateManager.render("summary-prompt", vars);
                conv.setSummary(summaryPrompt);
                conversationMapper.updateById(conv);
                log.info("Generated summary for conversation {}", conversationId);
            }
        }
    }
}
