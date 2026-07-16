package com.example.evimind.retrieval;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.service.DocumentPermissionService;

import lombok.extern.slf4j.Slf4j;

/**
 * 混合检索服务 — 协调向量语义检索和关键词检索，并支持查询改写。
 *
 * <p>完整检索链路：Query Rewrite → Parallel Search (pgvector + ES) → RRF Fusion → Reranker
 *
 * <p>面试点： - CompletableFuture 并行执行两个检索后端，1500ms 超时控制 - 三级优雅降级：双引擎 → 单引擎 → 内存关键词回退 - Query Rewrite
 * 在搜索之前执行，由 RagPipeline 传入改写后的查询
 */
@Slf4j
@Service
public class HybridSearchService {

  private final PgVectorSearchService pgVectorSearchService;
  private final ElasticsearchSearchService elasticsearchSearchService;
  private final RrfFusionService rrfFusionService;
  private final SimpleKeywordSearchService simpleKeywordSearchService;
  private final QueryRewriteService queryRewriteService;
  private final DocumentPermissionService documentPermissionService;
  private final Executor retrievalExecutor;

  public HybridSearchService(
      PgVectorSearchService pgVectorSearchService,
      ElasticsearchSearchService elasticsearchSearchService,
      RrfFusionService rrfFusionService,
      @Nullable SimpleKeywordSearchService simpleKeywordSearchService,
      QueryRewriteService queryRewriteService,
      DocumentPermissionService documentPermissionService,
      @Qualifier("retrievalTaskExecutor") Executor retrievalExecutor) {
    this.pgVectorSearchService = pgVectorSearchService;
    this.elasticsearchSearchService = elasticsearchSearchService;
    this.rrfFusionService = rrfFusionService;
    this.simpleKeywordSearchService = simpleKeywordSearchService;
    this.queryRewriteService = queryRewriteService;
    this.documentPermissionService = documentPermissionService;
    this.retrievalExecutor = retrievalExecutor;
  }

  @Value("${custom.rag.search.backend-timeout-ms:1500}")
  private long backendTimeoutMillis = 1500;

  /** 执行混合检索（不含查询改写，保持向后兼容）。 */
  public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
    return search(query, knowledgeBaseId, topK, null);
  }

  /**
   * 执行混合检索，支持对话历史驱动的查询改写。
   *
   * @param query 原始用户查询
   * @param knowledgeBaseId 知识库 ID
   * @param topK 返回结果数量
   * @param conversationHistory 对话历史（null 或空则跳过改写）
   * @return 融合排序后的检索结果
   */
  public List<SearchResult> search(
      String query, Long knowledgeBaseId, int topK, String conversationHistory) {
    // Step 1: Query Rewrite（如果有对话历史）
    String rewritten = queryRewriteService.rewrite(query, conversationHistory);
    String effectiveQuery = (rewritten != null) ? rewritten : query;

    int requestedTopK = Math.max(1, Math.min(topK, 50));
    int candidateK = Math.max(requestedTopK, Math.min(50, requestedTopK * 3));

    // Step 2: Parallel Hybrid Search
    FutureTask<List<SearchResult>> semanticFuture =
        new FutureTask<>(
            () -> pgVectorSearchService.search(effectiveQuery, knowledgeBaseId, candidateK));
    FutureTask<List<SearchResult>> keywordFuture =
        new FutureTask<>(
            () -> elasticsearchSearchService.search(effectiveQuery, knowledgeBaseId, candidateK));
    List<SearchResult> semanticResults;
    List<SearchResult> keywordResults;
    try {
      retrievalExecutor.execute(semanticFuture);
      retrievalExecutor.execute(keywordFuture);

      long deadlineNanos =
          System.nanoTime()
              + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, backendTimeoutMillis));
      semanticResults = awaitResults("PgVector", semanticFuture, deadlineNanos);
      keywordResults = awaitResults("Elasticsearch", keywordFuture, deadlineNanos);
    } catch (RejectedExecutionException e) {
      semanticFuture.cancel(true);
      keywordFuture.cancel(true);
      semanticResults = List.of();
      keywordResults = List.of();
      log.warn("Retrieval executor rejected backend search for KB {}", knowledgeBaseId);
    }
    Set<Long> readableDocumentIds =
        readableDocumentIds(knowledgeBaseId, semanticResults, keywordResults);
    semanticResults = filterReadableResults(semanticResults, readableDocumentIds);
    keywordResults = filterReadableResults(keywordResults, readableDocumentIds);
    if (Thread.currentThread().isInterrupted()) {
      semanticFuture.cancel(true);
      keywordFuture.cancel(true);
      log.debug("Hybrid search interrupted for KB {}", knowledgeBaseId);
      return List.of();
    }
    boolean degraded = semanticResults.isEmpty() || keywordResults.isEmpty();

    // Fallback to simple keyword search if ES returned nothing and we have the local service
    if (keywordResults.isEmpty() && simpleKeywordSearchService != null) {
      try {
        keywordResults =
            simpleKeywordSearchService.search(effectiveQuery, knowledgeBaseId, candidateK);
        keywordResults =
            filterReadableResults(
                keywordResults, readableDocumentIds(knowledgeBaseId, keywordResults));
        log.debug("SimpleKeywordSearch returned {} results as fallback", keywordResults.size());
      } catch (Exception e) {
        log.warn("SimpleKeywordSearch fallback also failed", e);
      }
    }

    if (semanticResults.isEmpty() && keywordResults.isEmpty()) {
      log.warn("Both search backends returned no results for KB {}", knowledgeBaseId);
      return List.of();
    }

    // Step 3: RRF Fusion
    if (degraded) {
      if (semanticResults.isEmpty())
        return rrfFusionService.fuse(List.of(), keywordResults, requestedTopK);
      if (keywordResults.isEmpty())
        return rrfFusionService.fuse(semanticResults, List.of(), requestedTopK);
    }

    return rrfFusionService.fuse(semanticResults, keywordResults, requestedTopK);
  }

  private List<SearchResult> awaitResults(
      String backendName,
      Future<List<SearchResult>> future,
      long deadlineNanos) {
    try {
      long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
      return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      log.debug("{} search interrupted", backendName);
      return List.of();
    } catch (TimeoutException e) {
      future.cancel(true);
      log.warn("{} search timed out after {} ms", backendName, backendTimeoutMillis);
      return List.of();
    } catch (Exception e) {
      log.warn("{} search failed", backendName, e);
      return List.of();
    }
  }

  private Set<Long> readableDocumentIds(
      Long knowledgeBaseId, List<SearchResult>... resultGroups) {
    if (GroupContext.isAdmin()) {
      return Set.of();
    }
    Long userId = GroupContext.getUserId();
    if (userId == null) {
      return Set.of();
    }
    Set<Long> documentIds = new java.util.LinkedHashSet<>();
    for (List<SearchResult> resultGroup : resultGroups) {
      for (SearchResult result : resultGroup) {
        if (result.getDocumentId() != null) {
          documentIds.add(result.getDocumentId());
        }
      }
    }
    return documentPermissionService.findReadableDocumentIds(knowledgeBaseId, documentIds, userId);
  }

  private List<SearchResult> filterReadableResults(
      List<SearchResult> results, Set<Long> readableDocumentIds) {
    if (GroupContext.isAdmin()) {
      return results;
    }
    return results.stream()
        .filter(result -> result.getDocumentId() != null && readableDocumentIds.contains(result.getDocumentId()))
        .toList();
  }

}
