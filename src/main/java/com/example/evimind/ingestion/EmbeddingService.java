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
    private com.example.evimind.service.DocumentChunkService documentChunkService;

    @Autowired
    private com.example.evimind.service.DocumentChunkEmbeddingService documentChunkEmbeddingService;

    @Autowired
    private com.example.evimind.mapper.DocumentChunkEmbeddingMapper embeddingMapper;

    public void embedAndStore(List<DocumentChunk> chunks) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel not available, skipping embedding");
            return;
        }
        if (chunks.isEmpty()) return;

        final int BATCH_SIZE = 100;
        List<DocumentChunkEmbedding> allEmbeddings = new java.util.ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            List<DocumentChunk> batchChunks = chunks.subList(i, end);
            
            List<String> texts = batchChunks.stream()
                    .map(DocumentChunk::getContent)
                    .collect(Collectors.toList());

            List<List<Double>> vectors;
            try {
                vectors = embeddingModel.embed(texts);
            } catch (Exception e) {
                log.error("Embedding API call failed for batch {}-{}", i, end, e);
                throw new RuntimeException("Embedding generation failed", e);
            }

            for (int j = 0; j < batchChunks.size(); j++) {
                DocumentChunk chunk = batchChunks.get(j);
                List<Double> vector = vectors.get(j);

                String vectorStr = formatPgVector(vector);

                DocumentChunkEmbedding emb = new DocumentChunkEmbedding();
                emb.setChunkId(chunk.getId());
                emb.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
                emb.setEmbedding(vectorStr);
                allEmbeddings.add(emb);
            }
        }

        // Batch insert embeddings
        documentChunkEmbeddingService.saveBatch(allEmbeddings, 100);

        // Update chunks with vector IDs
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setVectorId("emb_" + allEmbeddings.get(i).getId());
        }
        documentChunkService.updateBatchById(chunks, 100);

        log.info("Embedded and stored {} chunks in batches", chunks.size());
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
