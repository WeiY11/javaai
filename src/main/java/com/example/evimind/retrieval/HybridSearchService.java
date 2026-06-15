package com.example.evimind.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 混合检索服务 — 协调向量语义检索和关键词检索，并支持查询改写。
 *
 * 完整检索链路：Query Rewrite → Parallel Search (pgvector + ES) → RRF Fusion → Reranker
 *
 * 面试点：
 * - CompletableFuture 并行执行两个检索后端，1500ms 超时控制
 * - 三级优雅降级：双引擎 → 单引擎 → 内存关键词回退
 * - Query Rewrite 在搜索之前执行，由 RagPipeline 传入改写后的查询
 */
@Slf4j
@Service
public class HybridSearchService {

    @Autowired
    private PgVectorSearchService pgVectorSearchService;
    @Autowired
    private ElasticsearchSearchService elasticsearchSearchService;
    @Autowired
    private RrfFusionService rrfFusionService;
    @Autowired(required = false)
    private SimpleKeywordSearchService simpleKeywordSearchService;
    @Autowired
    private QueryRewriteService queryRewriteService;

    @Value("${custom.rag.search.backend-timeout-ms:1500}")
    private long backendTimeoutMillis = 1500;

    /**
     * 执行混合检索（不含查询改写，保持向后兼容）。
     */
    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        return search(query, knowledgeBaseId, topK, null);
    }

    /**
     * 执行混合检索，支持对话历史驱动的查询改写。
     *
     * @param query               原始用户查询
     * @param knowledgeBaseId     知识库 ID
     * @param topK                返回结果数量
     * @param conversationHistory 对话历史（null 或空则跳过改写）
     * @return 融合排序后的检索结果
     */
    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK, String conversationHistory) {
        // Step 1: Query Rewrite（如果有对话历史）
        String rewritten = queryRewriteService.rewrite(query, conversationHistory);
        String effectiveQuery = (rewritten != null) ? rewritten : query;

        int requestedTopK = Math.max(1, Math.min(topK, 50));
        int candidateK = Math.max(requestedTopK, Math.min(50, requestedTopK * 3));

        // Step 2: Parallel Hybrid Search
        CompletableFuture<List<SearchResult>> semanticFuture = CompletableFuture.supplyAsync(
                () -> pgVectorSearchService.search(effectiveQuery, knowledgeBaseId, candidateK)
        );
        CompletableFuture<List<SearchResult>> keywordFuture = CompletableFuture.supplyAsync(
                () -> elasticsearchSearchService.search(effectiveQuery, knowledgeBaseId, candidateK)
        );

        List<SearchResult> semanticResults = awaitResults("PgVector", semanticFuture);
        List<SearchResult> keywordResults = awaitResults("Elasticsearch", keywordFuture);
        boolean degraded = semanticResults.isEmpty() || keywordResults.isEmpty();

        // Fallback to simple keyword search if ES returned nothing and we have the local service
        if (keywordResults.isEmpty() && simpleKeywordSearchService != null) {
            try {
                keywordResults = simpleKeywordSearchService.search(effectiveQuery, knowledgeBaseId, candidateK);
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
            if (semanticResults.isEmpty()) return rrfFusionService.fuse(List.of(), keywordResults, requestedTopK);
            if (keywordResults.isEmpty()) return rrfFusionService.fuse(semanticResults, List.of(), requestedTopK);
        }

        return rrfFusionService.fuse(semanticResults, keywordResults, requestedTopK);
    }

    private List<SearchResult> awaitResults(String backendName, CompletableFuture<List<SearchResult>> future) {
        try {
            return future.get(backendTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("{} search timed out after {} ms", backendName, backendTimeoutMillis);
            return List.of();
        } catch (Exception e) {
            log.warn("{} search failed", backendName, e);
            return List.of();
        }
    }
}
