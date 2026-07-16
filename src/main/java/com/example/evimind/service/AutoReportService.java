package com.example.evimind.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.config.AiClientResolver;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.knowledgebase.KnowledgeBaseService;
import com.example.evimind.mapper.*;
import com.example.evimind.model.entity.*;

import lombok.extern.slf4j.Slf4j;

/** 自动化报告生成服务。 基于知识库的对话历史、文档分析结果和引用网络，生成结构化周报/月报。 */
@Slf4j
@Service
public class AutoReportService {

  private final KnowledgeBaseService knowledgeBaseService;
  private final DocumentMapper documentMapper;
  private final ConversationMapper conversationMapper;
  private final MessageMapper messageMapper;
  private final Map<String, ChatClient> chatClients;
  private final Executor llmExecutor;

  public AutoReportService(
      KnowledgeBaseService knowledgeBaseService,
      DocumentMapper documentMapper,
      ConversationMapper conversationMapper,
      MessageMapper messageMapper,
      Map<String, ChatClient> chatClients,
      @Qualifier("llmTaskExecutor") Executor llmExecutor) {
    this.knowledgeBaseService = knowledgeBaseService;
    this.documentMapper = documentMapper;
    this.conversationMapper = conversationMapper;
    this.messageMapper = messageMapper;
    this.chatClients = chatClients;
    this.llmExecutor = llmExecutor;
  }

  private static final long LLM_TIMEOUT_MS = 120_000;

  /** 生成知识库使用报告。 */
  public String generateReport(Long knowledgeBaseId, String period) {
    KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
    if (kb == null)
      throw new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId);
    if (!GroupContext.isAdmin() && !knowledgeBaseService.isOwner(knowledgeBaseId)) {
      throw new SecurityException("Only the knowledge base owner can generate reports");
    }

    LocalDateTime since =
        "monthly".equals(period)
            ? LocalDateTime.now().minusMonths(1)
            : LocalDateTime.now().minusWeeks(1);

    // 统计数据
    Map<String, Object> stats = collectStats(knowledgeBaseId, since);

    // 构建报告 prompt
    String prompt = buildReportPrompt(kb.getName(), period, stats);

    ChatClient chatClient = resolveChatClient();
    if (chatClient == null) {
      return buildFallbackReport(kb.getName(), period, stats);
    }

    try {
      CompletableFuture<String> future =
          CompletableFuture.supplyAsync(
              () ->
                  chatClient
                      .prompt()
                      .system("你是一个专业的数据分析助手。请根据提供的统计数据，生成一份结构化的知识库使用报告。")
                      .user(prompt)
                      .call()
                      .content(),
              llmExecutor);

      String result = future.get(LLM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      return result != null ? result.trim() : buildFallbackReport(kb.getName(), period, stats);
    } catch (Exception e) {
      log.warn(
          "LLM report generation failed, using fallback ({})",
          e.getClass().getSimpleName());
      return buildFallbackReport(kb.getName(), period, stats);
    }
  }

  private Map<String, Object> collectStats(Long knowledgeBaseId, LocalDateTime since) {
    Map<String, Object> stats = new LinkedHashMap<>();

    // 文档统计
    long totalDocs =
        documentMapper.selectCount(
            new LambdaQueryWrapper<Document>().eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    long newDocs =
        documentMapper.selectCount(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .ge(Document::getCreatedAt, since));
    stats.put("totalDocuments", totalDocs);
    stats.put("newDocuments", newDocs);

    // 对话统计
    List<Conversation> conversations =
        conversationMapper.selectList(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getKnowledgeBaseId, knowledgeBaseId)
                .ge(Conversation::getCreatedAt, since));
    stats.put("newConversations", conversations.size());

    // 消息统计
    if (!conversations.isEmpty()) {
      List<Long> convIds =
          conversations.stream().map(Conversation::getId).collect(Collectors.toList());
      long messageCount =
          messageMapper.selectCount(
              new LambdaQueryWrapper<Message>().in(Message::getConversationId, convIds));
      stats.put("totalMessages", messageCount);
    }

    // 文档格式分布
    List<Document> docs =
        documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    Map<String, Long> formatDist =
        docs.stream()
            .filter(d -> d.getFileFormat() != null)
            .collect(Collectors.groupingBy(Document::getFileFormat, Collectors.counting()));
    stats.put("formatDistribution", formatDist);

    stats.put("period", since.toString() + " ~ " + LocalDateTime.now());

    return stats;
  }

  private String buildReportPrompt(String kbName, String period, Map<String, Object> stats) {
    return """
            请为知识库「%s」生成一份%s使用报告。

            统计数据：
            - 文档总数: %s
            - 新增文档: %s
            - 新增对话: %s
            - 消息总数: %s
            - 文档格式分布: %s
            - 统计周期: %s

            请包含以下章节：
            1. 概览摘要
            2. 文档入库分析
            3. 用户使用分析
            4. 知识覆盖率评估
            5. 改进建议
            """
        .formatted(
            kbName,
            "monthly".equals(period) ? "月度" : "周度",
            stats.get("totalDocuments"),
            stats.get("newDocuments"),
            stats.get("newConversations"),
            stats.get("totalMessages"),
            stats.get("formatDistribution"),
            stats.get("period"));
  }

  private String buildFallbackReport(String kbName, String period, Map<String, Object> stats) {
    String periodLabel = "monthly".equals(period) ? "月度" : "周度";
    return """
            # %s — %s使用报告

            ## 概览
            - 统计周期: %s
            - 文档总数: %s（新增 %s）
            - 新增对话: %s
            - 消息总数: %s

            ## 文档格式分布
            %s

            ---
            *报告由系统自动生成，LLM 服务不可用时使用统计模板。*
            """
        .formatted(
            kbName,
            periodLabel,
            stats.get("period"),
            stats.get("totalDocuments"),
            stats.get("newDocuments"),
            stats.get("newConversations"),
            stats.get("totalMessages"),
            stats.get("formatDistribution"));
  }

  private ChatClient resolveChatClient() {
    return AiClientResolver.resolve(chatClients, null);
  }
}
