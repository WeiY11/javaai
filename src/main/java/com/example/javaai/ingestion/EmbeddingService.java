package com.example.javaai.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;

    public List<String> embedAndStore(List<String> chunks, Long knowledgeBaseId, Long documentId) {
        List<Document> documents = new java.util.ArrayList<>();
        List<String> vectorIds = new java.util.ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            Document doc = new Document(
                    "chunk_" + documentId + "_" + i,
                    content,
                    Map.of(
                            "knowledgeBaseId", knowledgeBaseId.toString(),
                            "documentId", documentId.toString(),
                            "chunkIndex", String.valueOf(i)
                    )
            );
            documents.add(doc);
            vectorIds.add(doc.getId());
        }

        vectorStore.add(documents);
        log.info("Embedded and stored {} chunks for document {} in KB {}",
                chunks.size(), documentId, knowledgeBaseId);
        return vectorIds;
    }

    public void deleteByDocumentId(Long documentId) {
        vectorStore.delete(List.of("chunk_" + documentId + "_*"));
        log.info("Deleted embeddings for document {}", documentId);
    }

    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        log.info("Deleted embeddings for knowledge base {}", knowledgeBaseId);
    }
}
