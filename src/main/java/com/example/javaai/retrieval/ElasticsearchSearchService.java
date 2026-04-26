package com.example.javaai.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "document_chunk";

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m
                                                    .match(t -> t
                                                            .field("content")
                                                            .query(query)
                                                    )
                                            )
                                            .filter(f -> f
                                                    .term(t -> t
                                                            .field("knowledgeBaseId")
                                                            .value(knowledgeBaseId)
                                                    )
                                            )
                                    )
                            ),
                    Map.class
            );

            List<SearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source != null) {
                    results.add(new SearchResult(
                            hit.id(),
                            ((Number) source.get("documentId")).longValue(),
                            ((Number) source.get("knowledgeBaseId")).longValue(),
                            (String) source.get("content"),
                            ((Number) source.get("chunkIndex")).intValue(),
                            hit.score() != null ? hit.score() : 0.0,
                            "elasticsearch"
                    ));
                }
            }
            log.debug("Elasticsearch search returned {} results for KB {}", results.size(), knowledgeBaseId);
            return results;
        } catch (IOException e) {
            log.error("Elasticsearch search failed", e);
            return List.of();
        }
    }
}
