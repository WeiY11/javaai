package com.example.evimind.retrieval;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 LLM Prompt 的 Reranker 实现（默认实现）。
 *
 * <p>将查询与候选文本以 pair 方式发送给 LLM，请求其为每个候选打分（0-10 的相关性分数）。 采用批量 prompt 策略：一次 LLM 调用评估所有候选，减少 API
 * 调用次数和延迟。
 *
 * <p>面试点： - Cross-Encoder 思路：query + candidate 联合输入，比 Bi-Encoder 的独立编码更准确 - 批量评估 vs 逐条评估的
 * latency-cost tradeoff - 优雅降级：Reranker 失败时保持原始 RRF 排序不变
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "custom.rag.reranker.type",
    havingValue = "prompt",
    matchIfMissing = true)
public class PromptBasedReranker implements Reranker {

  @Autowired private Map<String, ChatClient> chatClients;

  @Value("${custom.rag.reranker.enabled:true}")
  private boolean enabled = true;

  @Value("${custom.rag.reranker.timeout-ms:5000}")
  private long timeoutMs = 5000;

  @Value("${custom.rag.reranker.max-candidates:10}")
  private int maxCandidates = 10;

  @Override
  public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN) {
    if (!enabled) {
      log.debug("Reranker disabled by configuration, returning original order");
      return candidates.stream().limit(topN).toList();
    }

    if (candidates.size() <= 1) {
      return candidates;
    }

    // 限制候选数量，避免 prompt 过长
    List<SearchResult> inputCandidates =
        candidates.size() > maxCandidates ? candidates.subList(0, maxCandidates) : candidates;

    try {
      List<Double> scores = batchScore(query, inputCandidates);
      if (scores != null && scores.size() == inputCandidates.size()) {
        // 用 reranker 分数替换原始分数，并按新分数排序
        List<SearchResult> rescored = new ArrayList<>();
        for (int i = 0; i < inputCandidates.size(); i++) {
          SearchResult original = inputCandidates.get(i);
          rescored.add(
              new SearchResult(
                  original.getChunkId(),
                  original.getDocumentId(),
                  original.getKnowledgeBaseId(),
                  original.getContent(),
                  original.getChunkIndex(),
                  scores.get(i),
                  original.getSource() + "+reranked"));
        }

        rescored.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        List<SearchResult> result = rescored.stream().limit(topN).toList();

        log.info(
            "Reranker rescored {} candidates, top score: {} -> {}",
            inputCandidates.size(),
            String.format("%.3f", candidates.get(0).getScore()),
            String.format("%.3f", result.get(0).getScore()));

        return result;
      }
    } catch (Exception e) {
      log.warn("Reranker failed, falling back to original RRF order: {}", e.getMessage());
    }

    return candidates.stream().limit(topN).toList();
  }

  /** 批量打分：一次 LLM 调用评估所有候选的相关性。 */
  private List<Double> batchScore(String query, List<SearchResult> candidates) {
    ChatClient chatClient = resolveChatClient();
    if (chatClient == null) return null;

    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append("你是一个文档相关性评估专家。请评估以下每个文档片段与用户查询的相关程度。\n\n");
    promptBuilder.append("用户查询：").append(query).append("\n\n");
    promptBuilder.append("候选文档片段：\n");

    for (int i = 0; i < candidates.size(); i++) {
      String content = candidates.get(i).getContent();
      // 截断过长的内容，每个候选最多 300 字
      if (content.length() > 300) {
        content = content.substring(0, 300) + "...";
      }
      promptBuilder.append("[").append(i + 1).append("] ").append(content).append("\n\n");
    }

    promptBuilder.append("请为每个候选打分（0-10 的整数，10 表示完全相关，0 表示完全无关）。\n");
    promptBuilder.append("只输出 JSON 数组格式，例如：[8, 5, 9, 3, 7]\n");
    promptBuilder.append("不要输出任何解释文字，只输出 JSON 数组。");

    String prompt = promptBuilder.toString();

    CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> chatClient.prompt().user(prompt).call().content());

    try {
      String response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
      return parseScores(response, candidates.size());
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      log.warn("Reranker timed out after {} ms", timeoutMs);
      return null;
    } catch (Exception e) {
      log.warn("Reranker LLM call failed", e);
      return null;
    }
  }

  /** 从 LLM 响应中解析分数数组。支持多种格式： - 标准 JSON 数组: [8, 5, 9] - 带解释的响应中提取数组 - 逗号分隔的数字 */
  private List<Double> parseScores(String response, int expectedCount) {
    if (response == null || response.isBlank()) return null;

    // 尝试提取 JSON 数组部分
    String cleaned = response.trim();

    // 查找方括号内的内容
    int start = cleaned.indexOf('[');
    int end = cleaned.lastIndexOf(']');
    if (start >= 0 && end > start) {
      cleaned = cleaned.substring(start, end + 1);
    }

    // 解析数字
    List<Double> scores = new ArrayList<>();
    String[] parts = cleaned.replaceAll("[\\[\\]]", "").split("[,，\\s]+");
    for (String part : parts) {
      part = part.trim();
      if (part.isEmpty()) continue;
      try {
        double score = Double.parseDouble(part);
        // 归一化到 0-1 范围（LLM 输出 0-10）
        scores.add(Math.max(0.0, Math.min(1.0, score / 10.0)));
      } catch (NumberFormatException e) {
        // 跳过非数字
      }
    }

    // 验证：如果解析出的分数数量不匹配，返回 null
    if (scores.size() != expectedCount) {
      log.warn("Reranker score count mismatch: expected {}, got {}", expectedCount, scores.size());
      return null;
    }

    return scores;
  }

  private ChatClient resolveChatClient() {
    if (chatClients == null || chatClients.isEmpty()) return null;
    if (chatClients.containsKey("deepseek")) {
      return chatClients.get("deepseek");
    }
    return chatClients.values().iterator().next();
  }
}
