package com.example.evimind.ingestion;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.example.evimind.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ElasticsearchIndexService {

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "document_chunk";

    public void indexChunkContents(List<String> chunks, Long knowledgeBaseId, Long documentId) {
        List<DocumentChunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(null);
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunkEntities.add(chunk);
        }
        indexChunks(chunkEntities, knowledgeBaseId, documentId);
    }

    public void indexChunks(List<DocumentChunk> chunks, Long knowledgeBaseId, Long documentId) {
        if (elasticsearchClient == null) {
            log.warn("ElasticsearchClient not available, skipping indexing");
            return;
        }
        try {
            List<BulkOperation> operations = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                Integer chunkIndex = chunk.getChunkIndex() != null ? chunk.getChunkIndex() : i;
                String chunkId = chunk.getId() != null
                        ? "chunk_" + chunk.getId()
                        : "chunk_" + documentId + "_" + chunkIndex;
                Map<String, Object> doc = Map.of(
                        "content", chunk.getContent(),
                        "knowledgeBaseId", knowledgeBaseId,
                        "documentId", documentId,
                        "chunkIndex", chunkIndex,
                        "chunkId", chunkId
                );
                operations.add(BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(chunkId)
                                .document(doc)
                        )
                ));
            }
            elasticsearchClient.bulk(BulkRequest.of(b -> b.operations(operations)));
            log.info("Indexed {} chunks in ES for document {} in KB {}",
                    chunks.size(), documentId, knowledgeBaseId);
        } catch (IOException e) {
            log.error("Failed to index chunks in Elasticsearch", e);
        }
    }

    public void deleteByDocumentId(Long documentId) {
        if (elasticsearchClient == null) return;
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("documentId").value(documentId)))
            );
        } catch (IOException e) {
            log.error("Failed to delete ES index for document {}", documentId, e);
        }
    }

    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        if (elasticsearchClient == null) return;
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("knowledgeBaseId").value(knowledgeBaseId)))
            );
        } catch (IOException e) {
            log.error("Failed to delete ES index for KB {}", knowledgeBaseId, e);
        }
    }
}
