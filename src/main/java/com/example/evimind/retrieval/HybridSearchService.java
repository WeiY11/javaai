package com.example.evimind.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Value("${custom.rag.search.backend-timeout-ms:1500}")
    private long backendTimeoutMillis = 1500;

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        int requestedTopK = Math.max(1, Math.min(topK, 50));
        int candidateK = Math.max(requestedTopK, Math.min(50, requestedTopK * 3));
        CompletableFuture<List<SearchResult>> semanticFuture = CompletableFuture.supplyAsync(
                () -> pgVectorSearchService.search(query, knowledgeBaseId, candidateK)
        );
        CompletableFuture<List<SearchResult>> keywordFuture = CompletableFuture.supplyAsync(
                () -> elasticsearchSearchService.search(query, knowledgeBaseId, candidateK)
        );

        List<SearchResult> semanticResults;
        List<SearchResult> keywordResults;
        boolean degraded = false;

        semanticResults = awaitResults("PgVector", semanticFuture);
        keywordResults = awaitResults("Elasticsearch", keywordFuture);
        degraded = semanticResults.isEmpty() || keywordResults.isEmpty();

        // Fallback to simple keyword search if ES returned nothing and we have the local service
        if (keywordResults.isEmpty() && simpleKeywordSearchService != null) {
            try {
                keywordResults = simpleKeywordSearchService.search(query, knowledgeBaseId, candidateK);
                log.debug("SimpleKeywordSearch returned {} results as fallback", keywordResults.size());
            } catch (Exception e) {
                log.warn("SimpleKeywordSearch fallback also failed", e);
            }
        }

        if (semanticResults.isEmpty() && keywordResults.isEmpty()) {
            log.warn("Both search backends returned no results for KB {}", knowledgeBaseId);
            return List.of();
        }

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
