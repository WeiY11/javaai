package com.example.evimind.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.ConversationMapper;
import com.example.evimind.mapper.MessageMapper;
import com.example.evimind.model.entity.Conversation;
import com.example.evimind.model.entity.ScheduledTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据清理任务执行器。
 * 清理过期的对话和消息记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupExecutor implements TaskExecutor {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getTaskType() {
        return "DATA_CLEANUP";
    }

    @Override
    public void execute(ScheduledTask task) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    task.getConfig(), new TypeReference<>() {});
            int retentionDays = config.get("retentionDays") != null
                    ? Integer.parseInt(config.get("retentionDays").toString())
                    : 90;

            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            log.info("DATA_CLEANUP: cleaning conversations older than {} days (before {})", retentionDays, cutoff);

            List<Conversation> oldConversations = conversationMapper.selectList(
                    new LambdaQueryWrapper<Conversation>()
                            .lt(Conversation::getCreatedAt, cutoff));

            if (oldConversations.isEmpty()) {
                log.info("DATA_CLEANUP: no conversations to clean");
                return;
            }

            List<Long> convIds = oldConversations.stream()
                    .map(Conversation::getId)
                    .collect(Collectors.toList());

            long deletedMessages = messageMapper.delete(
                    new LambdaQueryWrapper<com.example.evimind.model.entity.Message>()
                            .in(com.example.evimind.model.entity.Message::getConversationId, convIds));

            long deletedConversations = conversationMapper.delete(
                    new LambdaQueryWrapper<Conversation>()
                            .in(Conversation::getId, convIds));

            log.info("DATA_CLEANUP: deleted {} conversations and {} messages", deletedConversations, deletedMessages);
        } catch (Exception e) {
            log.error("DATA_CLEANUP task {} failed: {}", task.getId(), e.getMessage());
        }
    }
}
