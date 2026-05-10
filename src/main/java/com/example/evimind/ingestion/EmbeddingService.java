package com.example.evimind.ingestion;

import com.example.evimind.mapper.DocumentChunkEmbeddingMapper;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.entity.DocumentChunk;
import com.example.evimind.model.entity.DocumentChunkEmbedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingService {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @Autowired
    private DocumentChunkEmbeddingMapper embeddingMapper;

    public void embedAndStore(List<DocumentChunk> chunks) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel not available, skipping embedding");
            return;
        }
        if (chunks.isEmpty()) return;

        List<String> texts = chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());

        List<List<Double>> vectors;
        try {
            vectors = embeddingModel.embed(texts);
        } catch (Exception e) {
            log.error("Embedding API call failed", e);
            throw new RuntimeException("Embedding generation failed", e);
        }

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            List<Double> vector = vectors.get(i);

            String vectorStr = formatPgVector(vector);

            DocumentChunkEmbedding emb = new DocumentChunkEmbedding();
            emb.setChunkId(chunk.getId());
            emb.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
            emb.setEmbedding(vectorStr);
            embeddingMapper.insert(emb);

            chunk.setVectorId("emb_" + emb.getId());
            documentChunkMapper.updateById(chunk);
        }

        log.info("Embedded and stored {} chunks", chunks.size());
    }

    public void deleteByDocumentId(Long documentId) {
        embeddingMapper.deleteByDocumentId(documentId);
        log.info("Deleted embeddings for document {}", documentId);
    }

    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        embeddingMapper.deleteByKnowledgeBaseId(knowledgeBaseId);
        log.info("Deleted embeddings for KB {}", knowledgeBaseId);
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
