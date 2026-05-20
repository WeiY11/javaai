package com.example.evimind.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@ConditionalOnProperty(name = "custom.standalone.search.enabled", havingValue = "true", matchIfMissing = false)
public class SimpleKeywordSearchService {

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    public List<SearchResult> search(String query, Long knowledgeBaseId, int topK) {
        List<String> queryTerms = searchableTerms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> allChunks = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getKnowledgeBaseId, knowledgeBaseId)
        );

        if (allChunks.isEmpty()) return List.of();

        List<SearchResult> scored = new ArrayList<>();
        for (DocumentChunk chunk : allChunks) {
            String content = chunk.getContent() != null ? chunk.getContent().toLowerCase() : "";
            double score = 0.0;

            for (String term : queryTerms) {
                int count = 0;
                int idx = 0;
                while ((idx = content.indexOf(term, idx)) >= 0) {
                    count++;
                    idx += term.length();
                }
                if (count > 0) {
                    double tf = (double) count / Math.max(1, content.length() / 50);
                    score += tf * Math.log(1 + 1.0 / Math.max(1, count));
                }
            }

            if (score > 0) {
                scored.add(new SearchResult(
                        "chunk_" + chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getKnowledgeBaseId(),
                        chunk.getContent(),
                        chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                        Math.min(score / queryTerms.size(), 1.0),
                        "local_keyword"
                ));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (scored.size() > topK) {
            scored = scored.subList(0, topK);
        }

        log.debug("Simple keyword search returned {} results for KB {}", scored.size(), knowledgeBaseId);
        return scored;
    }

    private List<String> searchableTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase().split("[\\s,\\uFF0C\\u3002\\uFF01\\uFF1F!?]+"))
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }
}
