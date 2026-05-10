package com.example.evimind.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        CompletableFuture<List<SearchResult>> semanticFuture = CompletableFuture.supplyAsync(
                () -> pgVectorSearchService.search(query, knowledgeBaseId, topK)
        );
        CompletableFuture<List<SearchResult>> keywordFuture = CompletableFuture.supplyAsync(
                () -> elasticsearchSearchService.search(query, knowledgeBaseId, topK)
        );

        List<SearchResult> semanticResults;
        List<SearchResult> keywordResults;
        boolean degraded = false;

        try {
            semanticResults = semanticFuture.get();
        } catch (Exception e) {
            log.warn("PgVector search failed, degrading to keyword-only", e);
            semanticResults = List.of();
            degraded = true;
        }

        try {
            keywordResults = keywordFuture.get();
        } catch (Exception e) {
            log.warn("Elasticsearch search failed", e);
            keywordResults = List.of();
            degraded = true;
        }

        // Fallback to simple keyword search if ES returned nothing and we have the local service
        if (keywordResults.isEmpty() && simpleKeywordSearchService != null) {
            try {
                keywordResults = simpleKeywordSearchService.search(query, knowledgeBaseId, topK);
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
            if (semanticResults.isEmpty()) return keywordResults;
            if (keywordResults.isEmpty()) return semanticResults;
        }

        return rrfFusionService.fuse(semanticResults, keywordResults, topK);
    }
}
