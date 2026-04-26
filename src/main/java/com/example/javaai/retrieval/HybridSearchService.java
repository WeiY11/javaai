package com.example.javaai.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final PgVectorSearchService pgVectorSearchService;
    private final ElasticsearchSearchService elasticsearchSearchService;
    private final RrfFusionService rrfFusionService;

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
            log.warn("Elasticsearch search failed, degrading to semantic-only", e);
            keywordResults = List.of();
            degraded = true;
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
