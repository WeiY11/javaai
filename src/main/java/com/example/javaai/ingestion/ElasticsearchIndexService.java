package com.example.javaai.ingestion;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
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
public class ElasticsearchIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "document_chunk";

    public void indexChunks(List<String> chunks, Long knowledgeBaseId, Long documentId) {
        try {
            List<BulkOperation> operations = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = "chunk_" + documentId + "_" + i;
                Map<String, Object> doc = Map.of(
                        "content", chunks.get(i),
                        "knowledgeBaseId", knowledgeBaseId,
                        "documentId", documentId,
                        "chunkIndex", i
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
            throw new RuntimeException("Elasticsearch indexing failed", e);
        }
    }

    public void deleteByDocumentId(Long documentId) {
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q
                            .term(t -> t
                                    .field("documentId")
                                    .value(documentId)
                            )
                    )
            );
            log.info("Deleted ES index for document {}", documentId);
        } catch (IOException e) {
            log.error("Failed to delete ES index for document {}", documentId, e);
        }
    }

    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q
                            .term(t -> t
                                    .field("knowledgeBaseId")
                                    .value(knowledgeBaseId)
                            )
                    )
            );
            log.info("Deleted ES index for knowledge base {}", knowledgeBaseId);
        } catch (IOException e) {
            log.error("Failed to delete ES index for KB {}", knowledgeBaseId, e);
        }
    }
}
