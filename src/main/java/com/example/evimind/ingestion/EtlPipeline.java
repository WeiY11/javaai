package com.example.evimind.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.extractor.ExtractionResult;
import com.example.evimind.extractor.metadata.AcademicPdfMetadataExtractor;
import com.example.evimind.extractor.metadata.PaperMetadata;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.DocumentChunk;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.service.FileExtractorService;
import com.example.evimind.storage.MinioStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EtlPipeline {

    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private DocumentChunkMapper documentChunkMapper;
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Autowired(required = false)
    private MinioStorageService minioStorageService;
    @Autowired(required = false)
    private com.example.evimind.storage.LocalFileStorageService localFileStorageService;
    @Autowired
    private FileExtractorService fileExtractorService;
    @Autowired
    private TextCleaner textCleaner;
    @Autowired
    private DocumentChunker documentChunker;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private ElasticsearchIndexService elasticsearchIndexService;
    @Autowired(required = false)
    private AcademicPdfMetadataExtractor academicMetadataExtractor;

    @Autowired
    private com.example.evimind.service.DocumentChunkService documentChunkService;
    
    @Autowired
    private java.util.Map<String, org.springframework.ai.chat.client.ChatClient> chatClients;

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

            if (academicMetadataExtractor != null && "pdf".equalsIgnoreCase(doc.getFileFormat())) {
                try {
                    PaperMetadata metadata = academicMetadataExtractor.extract(rawText, doc.getFileName());
                    if (metadata != null) {
                        if (metadata.getDoi() != null) doc.setDoi(metadata.getDoi());
                        if (metadata.getAuthors() != null) doc.setAuthors(String.join(", ", metadata.getAuthors()));
                        if (metadata.getYear() != null) doc.setPublicationYear(metadata.getYear());
                        if (metadata.getJournal() != null) doc.setJournal(metadata.getJournal());
                        documentMapper.updateById(doc);
                        log.info("Extracted paper metadata for document {}: title={}, doi={}", documentId, metadata.getTitle(), metadata.getDoi());
                    }
                } catch (Exception e) {
                    log.warn("Paper metadata extraction failed for document {}: {}", documentId, e.getMessage());
                }
            }

            updateStatus(doc, "CLEANING");
            String cleanedText = textCleaner.clean(rawText);
            log.info("Cleaned to {} chars from document {}", cleanedText.length(), documentId);
            
            // --- NEW: Generate Document Summary ---
            try {
                org.springframework.ai.chat.client.ChatClient chatClient = chatClients.getOrDefault("deepseek", chatClients.values().stream().findFirst().orElse(null));
                if (chatClient != null && cleanedText.length() > 50) {
                    String contextText = cleanedText.substring(0, Math.min(3000, cleanedText.length()));
                    String summary = chatClient.prompt()
                            .system("你是一个专业的文档分析助手。请根据提供的文档开头内容，提取并凝练出一份简洁的文档简介（控制在 200 字以内）。如果提供的文本看起来全是乱码或无有效内容，请回复：暂无有效摘要。")
                            .user(contextText)
                            .call()
                            .content();
                    if (summary != null && !summary.isBlank()) {
                        doc.setSummary(summary.trim());
                        documentMapper.updateById(doc);
                        log.info("Generated summary for document {}: {}", documentId, summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate summary for document {}: {}", documentId, e.getMessage());
            }

            updateStatus(doc, "CHUNKING");
            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKnowledgeBaseId());
            DocumentChunker.ChunkConfig config = new DocumentChunker.ChunkConfig();
            config.setChunkSize(kb.getChunkSize());
            config.setOverlap(kb.getChunkOverlap());
            config.setStrategy(DocumentChunker.ChunkStrategy.valueOf(kb.getChunkStrategy()));
            List<String> chunks = documentChunker.chunk(cleanedText, config);
            log.info("Chunked into {} pieces for document {}", chunks.size(), documentId);

            List<DocumentChunk> savedChunks = saveChunks(doc, chunks);

            updateStatus(doc, "EMBEDDING");
            embeddingService.embedAndStore(savedChunks);

            updateStatus(doc, "INDEXING");
            List<String> chunkContents = chunks;
            elasticsearchIndexService.indexChunks(chunkContents, doc.getKnowledgeBaseId(), doc.getId());

            doc.setIngestionStatus("COMPLETED");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
            log.info("ETL pipeline completed for document {}", documentId);

        } catch (Exception e) {
            log.error("ETL pipeline failed for document {}", documentId, e);
            doc.setIngestionStatus("FAILED");
            documentMapper.updateById(doc);
        }
    }

    private String extract(Document doc) {
        if (minioStorageService == null && localFileStorageService == null) {
            throw new RuntimeException("No storage service available");
        }
        try {
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("etl_", "_" + doc.getFileName());
            try (InputStream is = minioStorageService != null
                    ? minioStorageService.downloadFile(doc.getStoragePath())
                    : localFileStorageService.downloadFile(doc.getStoragePath())) {
                java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ExtractionResult result = fileExtractorService.extractFile(tempFile, Integer.MAX_VALUE);
            java.nio.file.Files.deleteIfExists(tempFile);
            if (!result.isSuccess()) {
                throw new RuntimeException("Extraction failed: " + result.getErrorMessage());
            }
            return result.getContent();
        } catch (Exception e) {
            throw new RuntimeException("File extraction failed", e);
        }
    }

    private List<DocumentChunk> saveChunks(Document doc, List<String> chunks) {
        List<DocumentChunk> saved = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setKnowledgeBaseId(doc.getKnowledgeBaseId());
            chunk.setContent(chunks.get(i));
            chunk.setChunkIndex(i);
            saved.add(chunk);
        }
        documentChunkService.saveBatch(saved, 100);
        return saved;
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
        if (minioStorageService != null) {
            minioStorageService.deleteFile(doc.getStoragePath());
        } else if (localFileStorageService != null) {
            try {
                localFileStorageService.deleteFile(doc.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to delete local file: {}", doc.getStoragePath(), e);
            }
        }
        documentMapper.deleteById(documentId);
        log.info("Deleted document {} and all associated data", documentId);
    }
}
