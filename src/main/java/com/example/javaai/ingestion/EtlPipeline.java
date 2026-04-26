package com.example.javaai.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaai.extractor.ExtractionResult;
import com.example.javaai.extractor.FileContentExtractor;
import com.example.javaai.mapper.DocumentChunkMapper;
import com.example.javaai.mapper.DocumentMapper;
import com.example.javaai.model.entity.Document;
import com.example.javaai.model.entity.DocumentChunk;
import com.example.javaai.model.entity.KnowledgeBase;
import com.example.javaai.mapper.KnowledgeBaseMapper;
import com.example.javaai.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlPipeline {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final MinioStorageService minioStorageService;
    private final FileContentExtractor fileContentExtractor;
    private final TextCleaner textCleaner;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final ElasticsearchIndexService elasticsearchIndexService;

    @Transactional
    public void processDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }

        try {
            updateStatus(doc, "EXTRACTING");
            String rawText = extract(doc);
            log.info("Extracted {} chars from document {}", rawText.length(), documentId);

            updateStatus(doc, "CLEANING");
            String cleanedText = textCleaner.clean(rawText);
            log.info("Cleaned to {} chars from document {}", cleanedText.length(), documentId);

            updateStatus(doc, "CHUNKING");
            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKnowledgeBaseId());
            DocumentChunker.ChunkConfig config = new DocumentChunker.ChunkConfig();
            config.setChunkSize(kb.getChunkSize());
            config.setOverlap(kb.getChunkOverlap());
            config.setStrategy(DocumentChunker.ChunkStrategy.valueOf(kb.getChunkStrategy()));
            List<String> chunks = documentChunker.chunk(cleanedText, config);
            log.info("Chunked into {} pieces for document {}", chunks.size(), documentId);

            saveChunks(doc, chunks);

            updateStatus(doc, "EMBEDDING");
            List<String> vectorIds = embeddingService.embedAndStore(chunks, doc.getKnowledgeBaseId(), doc.getId());

            updateStatus(doc, "INDEXING");
            elasticsearchIndexService.indexChunks(chunks, doc.getKnowledgeBaseId(), doc.getId());

            doc.setIngestionStatus("COMPLETED");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
            log.info("ETL pipeline completed for document {}", documentId);

        } catch (Exception e) {
            log.error("ETL pipeline failed for document {}", documentId, e);
            doc.setIngestionStatus("FAILED");
            documentMapper.updateById(doc);
            throw new RuntimeException("ETL pipeline failed", e);
        }
    }

    private String extract(Document doc) {
        try (InputStream is = minioStorageService.downloadFile(doc.getStoragePath())) {
            ExtractionResult result = fileContentExtractor.extractFile(
                    Path.of(doc.getFileName()), Integer.MAX_VALUE);
            if (!result.isSuccess()) {
                throw new RuntimeException("Extraction failed: " + result.getErrorMessage());
            }
            return result.getContent();
        } catch (Exception e) {
            throw new RuntimeException("File extraction failed", e);
        }
    }

    private void saveChunks(Document doc, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setKnowledgeBaseId(doc.getKnowledgeBaseId());
            chunk.setContent(chunks.get(i));
            chunk.setChunkIndex(i);
            documentChunkMapper.insert(chunk);
        }
    }

    private void updateStatus(Document doc, String status) {
        doc.setIngestionStatus(status);
        documentMapper.updateById(doc);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) return;

        documentChunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, documentId)
        );
        embeddingService.deleteByDocumentId(documentId);
        elasticsearchIndexService.deleteByDocumentId(documentId);
        minioStorageService.deleteFile(doc.getStoragePath());
        documentMapper.deleteById(documentId);
        log.info("Deleted document {} and all associated data", documentId);
    }
}
