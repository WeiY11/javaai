package com.example.javaai.assistant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaai.config.PromptTemplateManager;
import com.example.javaai.identity.GroupContext;
import com.example.javaai.mapper.ConversationMapper;
import com.example.javaai.mapper.MessageMapper;
import com.example.javaai.model.entity.Conversation;
import com.example.javaai.model.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int SUMMARY_WINDOW = 10;

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

    public List<Message> getHistory(Long conversationId) {
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
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setStatus("DELETED");
            conversationMapper.updateById(conv);
        }
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
