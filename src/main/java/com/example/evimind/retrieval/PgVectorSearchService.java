package com.example.evimind.retrieval;

import com.example.evimind.mapper.DocumentChunkEmbeddingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PgVectorSearchService {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired
    private DocumentChunkEmbeddingMapper embeddingMapper;

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        if (embeddingModel == null) {
            log.debug("EmbeddingModel not available, returning empty results");
            return List.of();
        }
        try {
            List<Double> queryVector = embedQuery(query);
            String vectorStr = formatPgVector(queryVector);

            List<DocumentChunkEmbeddingMapper.ChunkSimilarityResult> rows =
                    embeddingMapper.findSimilarChunks(vectorStr, knowledgeBaseId, topK);

            List<SearchResult> results = new ArrayList<>();
            for (var row : rows) {
                results.add(new SearchResult(
                        "chunk_" + row.getChunkId(),
                        row.getDocumentId(),
                        row.getKnowledgeBaseId(),
                        row.getContent(),
                        row.getChunkIndex() != null ? row.getChunkIndex() : 0,
                        row.getScore() != null ? row.getScore() : 0.0,
                        "pgvector"
                ));
            }
            log.debug("PgVector search returned {} results for KB {}", results.size(), knowledgeBaseId);
            return results;
        } catch (Exception e) {
            log.error("PgVector search failed", e);
            return List.of();
        }
    }

    private List<Double> embedQuery(String query) {
        return embeddingModel.embed(query);
    }

    private String formatPgVector(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
