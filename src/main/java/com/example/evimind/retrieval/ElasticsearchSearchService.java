package com.example.evimind.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ElasticsearchSearchService {

  private final ElasticsearchClient elasticsearchClient;

  public ElasticsearchSearchService(
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          ElasticsearchClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  private static final String INDEX_NAME = "document_chunk";
  private volatile boolean esUnavailableLogged = false;

  public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
    if (elasticsearchClient == null) {
      log.debug("ElasticsearchClient not available, returning empty results");
      return List.of();
    }
    try {
      SearchResponse<Map> response =
          elasticsearchClient.search(
              s ->
                  s.index(INDEX_NAME)
                      .size(topK)
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.must(m -> m.match(t -> t.field("content").query(query)))
                                          .filter(
                                              f ->
                                                  f.term(
                                                      t ->
                                                          t.field("knowledgeBaseId")
                                                              .value(knowledgeBaseId))))),
              Map.class);

      esUnavailableLogged = false; // 连接恢复时重置
      List<SearchResult> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        Map source = hit.source();
        if (source != null) {
          results.add(
              new SearchResult(
                  hit.id(),
                  ((Number) source.get("documentId")).longValue(),
                  ((Number) source.get("knowledgeBaseId")).longValue(),
                  (String) source.get("content"),
                  ((Number) source.get("chunkIndex")).intValue(),
                  hit.score() != null ? hit.score() : 0.0,
                  "elasticsearch"));
        }
      }
      log.debug(
          "Elasticsearch search returned {} results for KB {}", results.size(), knowledgeBaseId);
      return results;
    } catch (Exception e) {
      if (!esUnavailableLogged) {
        log.warn(
            "Elasticsearch unavailable, full-text search disabled ({}). Subsequent failures will be suppressed.",
            e.getMessage());
        esUnavailableLogged = true;
      } else {
        log.debug("Elasticsearch search failed: {}", e.getMessage());
      }
      return List.of();
    }
  }
}
