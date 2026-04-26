package com.example.javaai.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PgVectorSearchService {

    private final VectorStore vectorStore;

    public PgVectorSearchService(@org.springframework.beans.factory.annotation.Autowired(required = false) VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore not available, returning empty results");
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.query(query)
                    .withTopK(topK)
                    .withSimilarityThreshold(0.0);

            List<Document> results = vectorStore.similaritySearch(request);

            List<SearchResult> searchResults = new ArrayList<>();
            for (Document doc : results) {
                searchResults.add(new SearchResult(
                        doc.getId(),
                        doc.getMetadata().containsKey("documentId") ? Long.parseLong(doc.getMetadata().get("documentId").toString()) : 0L,
                        doc.getMetadata().containsKey("knowledgeBaseId") ? Long.parseLong(doc.getMetadata().get("knowledgeBaseId").toString()) : knowledgeBaseId,
                        doc.getContent(),
                        doc.getMetadata().containsKey("chunkIndex") ? Integer.parseInt(doc.getMetadata().get("chunkIndex").toString()) : 0,
                        1.0,
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
