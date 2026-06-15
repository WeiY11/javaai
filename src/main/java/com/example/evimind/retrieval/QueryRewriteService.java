package com.example.evimind.retrieval;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.evimind.config.PromptTemplateManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Query Rewrite Service — 多轮对话查询改写（Coreference Resolution）。
 *
 * <p>利用已有的 query-rewrite-prompt.st 模板，将对话历史 + 当前问题传给 LLM， 生成一个独立、完整的查询，消除指代并补充上下文信息。
 *
 * <p>面试点： - Coreference Resolution 是多轮 RAG 对话的关键能力 - 异步执行 + 超时控制，避免改写步骤成为性能瓶颈 - 优雅降级：改写失败时回退到原始查询
 */
@Slf4j
@Service
public class QueryRewriteService {

  @Autowired private PromptTemplateManager promptTemplateManager;

  @Autowired private Map<String, ChatClient> chatClients;

  @Value("${custom.rag.query-rewrite.enabled:true}")
  private boolean enabled = true;

  @Value("${custom.rag.query-rewrite.timeout-ms:3000}")
  private long timeoutMs = 3000;

  @Value("${custom.rag.query-rewrite.max-history-messages:10}")
  private int maxHistoryMessages = 10;

  /**
   * 根据对话历史改写用户查询。
   *
   * @param originalQuery 原始用户查询
   * @param conversationHistory 对话历史（role: content 格式，每行一条消息）
   * @return 改写后的查询；如果历史为空、改写禁用或失败，返回原始查询
   */
  public String rewrite(String originalQuery, String conversationHistory) {
    if (!enabled) {
      log.debug("Query rewrite disabled by configuration");
      return originalQuery;
    }

    if (conversationHistory == null || conversationHistory.isBlank()) {
      log.debug("No conversation history, skipping query rewrite");
      return originalQuery;
    }

    try {
      String truncatedHistory = truncateHistory(conversationHistory);
      String rewritten = doRewrite(originalQuery, truncatedHistory);

      if (rewritten != null && !rewritten.isBlank() && !rewritten.equals(originalQuery)) {
        log.info("Query rewritten: '{}' -> '{}'", originalQuery, rewritten);
        return rewritten.trim();
      }

      log.debug("Query rewrite returned same result, using original query");
      return originalQuery;
    } catch (Exception e) {
      log.warn("Query rewrite failed, falling back to original query: {}", e.getMessage());
      return originalQuery;
    }
  }

  private String doRewrite(String query, String history) {
    ChatClient chatClient = resolveChatClient();
    if (chatClient == null) {
      log.warn("No ChatClient available for query rewrite");
      return query;
    }

    Map<String, Object> vars = new HashMap<>();
    vars.put("history", history);
    vars.put("query", query);

    String prompt = promptTemplateManager.render("query-rewrite-prompt", vars);

    // 异步执行 LLM 调用，带超时控制
    CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> chatClient.prompt().user(prompt).call().content());

    try {
      String result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
      return result != null ? result.trim() : query;
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      log.warn("Query rewrite timed out after {} ms", timeoutMs);
      return query;
    } catch (Exception e) {
      log.warn("Query rewrite LLM call failed", e);
      return query;
    }
  }

  /** 截断对话历史，只保留最近 N 条消息，避免 prompt 过长。 */
  private String truncateHistory(String history) {
    String[] lines = history.split("\n");
    if (lines.length <= maxHistoryMessages) {
      return history;
    }

    // 保留最近的 maxHistoryMessages 行
    StringBuilder sb = new StringBuilder();
    int start = lines.length - maxHistoryMessages;
    for (int i = start; i < lines.length; i++) {
      if (i > start) sb.append("\n");
      sb.append(lines[i]);
    }
    return sb.toString();
  }

  private ChatClient resolveChatClient() {
    if (chatClients == null || chatClients.isEmpty()) return null;
    // 优先使用 deepseek（成本低、速度快），否则取第一个可用的
    if (chatClients.containsKey("deepseek")) {
      return chatClients.get("deepseek");
    }
    return chatClients.values().iterator().next();
  }
}
