package com.example.javaai.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorSearchService {

    private final VectorStore vectorStore;

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        try {
            List<org.springframework.ai.vectorstore.SearchResult> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .filterExpression("knowledgeBaseId == '" + knowledgeBaseId + "'")
                            .build()
            );

            List<SearchResult> searchResults = new ArrayList<>();
            for (var result : results) {
                Document doc = result.getDocument();
                searchResults.add(new SearchResult(
                        doc.getId(),
                        Long.parseLong(doc.getMetadata().get("documentId").toString()),
                        Long.parseLong(doc.getMetadata().get("knowledgeBaseId").toString()),
                        doc.getContent(),
                        Integer.parseInt(doc.getMetadata().get("chunkIndex").toString()),
                        result.getScore(),
                        "pgvector"
                ));
            }
            log.debug("PgVector search returned {} results for KB {}", searchResults.size(), knowledgeBaseId);
            return searchResults;
        } catch (Exception e) {
            log.error("PgVector search failed", e);
            return List.of();
        }
    }
}
