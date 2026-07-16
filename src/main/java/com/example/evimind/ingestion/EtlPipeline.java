package com.example.evimind.ingestion;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.extractor.ExtractionResult;
import com.example.evimind.extractor.metadata.AcademicPdfMetadataExtractor;
import com.example.evimind.extractor.metadata.PaperMetadata;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.DocumentChunk;
import com.example.evimind.model.entity.KnowledgeBase;
import com.example.evimind.service.FileExtractorService;
import com.example.evimind.storage.MinioStorageService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EtlPipeline {

  private static final List<String> RUNNING_STAGES =
      List.of(
          "EXTRACTING",
          "CLEANING",
          "CHUNKING",
          "PERSISTING",
          "EMBEDDING",
          "INDEXING",
          "ENRICHING");

  @Autowired private DocumentMapper documentMapper;
  @Autowired private DocumentChunkMapper documentChunkMapper;
  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired(required = false)
  private MinioStorageService minioStorageService;

  @Autowired(required = false)
  private com.example.evimind.storage.LocalFileStorageService localFileStorageService;

  @Autowired private FileExtractorService fileExtractorService;
  @Autowired private TextCleaner textCleaner;
  @Autowired private DocumentChunker documentChunker;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private ElasticsearchIndexService elasticsearchIndexService;

  @Autowired(required = false)
  private AcademicPdfMetadataExtractor academicMetadataExtractor;

  @Autowired(required = false)
  private com.example.evimind.service.CitationNetworkService citationNetworkService;

  @Autowired(required = false)
  private EntityRelationExtractor entityRelationExtractor;

  @Autowired(required = false)
  private io.micrometer.core.instrument.MeterRegistry meterRegistry;

  @Autowired private com.example.evimind.service.DocumentChunkService documentChunkService;

  @Autowired
  private java.util.Map<String, org.springframework.ai.chat.client.ChatClient> chatClients;

  public void processDocument(Long documentId) {
    int claimed = documentMapper.claimIngestion(documentId);
    if (claimed == 0) {
      Document current = documentMapper.selectById(documentId);
      if (current == null) {
        throw new IllegalArgumentException("Document not found: " + documentId);
      }
      if (RUNNING_STAGES.contains(current.getIngestionStatus())) {
        log.info(
            "Document {} ingestion is already running at stage {}",
            documentId,
            current.getIngestionStatus());
        return;
      }
      throw new IllegalStateException("Unable to claim ingestion for document " + documentId);
    }

    Document doc = documentMapper.selectById(documentId);
    if (doc == null) {
      throw new IllegalArgumentException("Document not found: " + documentId);
    }

    String currentStage = "EXTRACTING";
    try {
      long etlStart = System.nanoTime();
      String rawText = extract(doc);
      log.info("Extracted {} chars from document {}", rawText.length(), documentId);

      currentStage = "CLEANING";
      updateStatus(doc, currentStage);
      String cleanedText = textCleaner.clean(rawText);
      log.info("Cleaned to {} chars from document {}", cleanedText.length(), documentId);

      currentStage = "CHUNKING";
      updateStatus(doc, currentStage);
      KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKnowledgeBaseId());
      if (kb == null) {
        throw new IllegalStateException("Knowledge base not found: " + doc.getKnowledgeBaseId());
      }
      DocumentChunker.ChunkConfig config = new DocumentChunker.ChunkConfig();
      config.setChunkSize(kb.getChunkSize());
      config.setOverlap(kb.getChunkOverlap());
      config.setStrategy(DocumentChunker.ChunkStrategy.valueOf(kb.getChunkStrategy()));
      List<String> chunks = documentChunker.chunk(cleanedText, config);
      log.info("Chunked into {} pieces for document {}", chunks.size(), documentId);

      currentStage = "PERSISTING";
      updateStatus(doc, currentStage);
      List<DocumentChunk> savedChunks = saveChunks(doc, chunks);

      currentStage = "EMBEDDING";
      updateStatus(doc, currentStage);
      embeddingService.embedAndStore(savedChunks);

      currentStage = "INDEXING";
      updateStatus(doc, currentStage);
      elasticsearchIndexService.indexChunks(savedChunks, doc.getKnowledgeBaseId(), doc.getId());

      currentStage = "ENRICHING";
      updateStatus(doc, currentStage);
      enrichDocument(doc, rawText, cleanedText);

      doc.setIngestionStatus("COMPLETED");
      doc.setFailedStage(null);
      doc.setErrorCode(null);
      doc.setErrorMessage(null);
      doc.setChunkCount(chunks.size());
      doc.setActiveIngestionVersion(doc.getIngestionVersion());
      doc.setFinishedAt(LocalDateTime.now());
      documentMapper.updateById(doc);
      cleanupInactiveVersions(doc);

      if (meterRegistry != null) {
        meterRegistry
            .timer("etl.document.duration")
            .record(System.nanoTime() - etlStart, java.util.concurrent.TimeUnit.NANOSECONDS);
        meterRegistry.counter("etl.document.status", "status", "SUCCESS").increment();
      }
      log.info("ETL pipeline completed for document {}", documentId);
    } catch (Exception e) {
      log.error("ETL pipeline failed for document {} ({})", documentId, e.getClass().getSimpleName());
      markFailed(doc, currentStage, e);
      if (meterRegistry != null) {
        meterRegistry.counter("etl.document.status", "status", "FAILED").increment();
      }
      throw e instanceof RuntimeException
          ? (RuntimeException) e
          : new RuntimeException("ETL pipeline failed", e);
    }
  }

  private String extract(Document doc) {
    if (minioStorageService == null && localFileStorageService == null) {
      throw new RuntimeException("No storage service available");
    }
    java.nio.file.Path tempFile = null;
    try {
      tempFile =
          java.nio.file.Files.createTempFile(
              "etl_", "_" + doc.getId() + "." + doc.getFileFormat());
      try (InputStream is =
          minioStorageService != null
              ? minioStorageService.downloadFile(doc.getStoragePath())
              : localFileStorageService.downloadFile(doc.getStoragePath())) {
        java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      ExtractionResult result = fileExtractorService.extractFile(tempFile, Integer.MAX_VALUE);
      if (!result.isSuccess()) {
        throw new RuntimeException("Extraction failed: " + result.getErrorMessage());
      }
      return result.getContent();
    } catch (Exception e) {
      throw new RuntimeException("File extraction failed", e);
    } finally {
      if (tempFile != null) {
        try {
          java.nio.file.Files.deleteIfExists(tempFile);
        } catch (Exception cleanupError) {
          log.warn("Failed to delete ETL temp file {}", tempFile, cleanupError);
        }
      }
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
      chunk.setIngestionVersion(doc.getIngestionVersion());
      saved.add(chunk);
    }
    documentChunkService.saveBatch(saved, 100);
    return saved;
  }

  private void updateStatus(Document doc, String status) {
    doc.setIngestionStatus(status);
    documentMapper.updateById(doc);
  }

  private void markFailed(Document doc, String failedStage, Exception e) {
    doc.setIngestionStatus("FAILED");
    doc.setFailedStage(failedStage);
    doc.setErrorCode(e.getClass().getSimpleName());
    doc.setErrorMessage("Ingestion failed during " + failedStage + ". Please retry.");
    doc.setFinishedAt(LocalDateTime.now());
    documentMapper.updateById(doc);
  }

  private void enrichDocument(Document doc, String rawText, String cleanedText) {
    extractPaperMetadata(doc, rawText);
    extractCitations(doc, rawText);
    generateSummary(doc, cleanedText);
    extractKnowledgeGraph(doc, cleanedText);
  }

  private void extractPaperMetadata(Document doc, String rawText) {
    if (academicMetadataExtractor == null || !"pdf".equalsIgnoreCase(doc.getFileFormat())) {
      return;
    }
    try {
      PaperMetadata metadata = academicMetadataExtractor.extract(rawText, doc.getFileName());
      if (metadata != null) {
        if (metadata.getDoi() != null) doc.setDoi(metadata.getDoi());
        if (metadata.getAuthors() != null) {
          doc.setAuthors(String.join(", ", metadata.getAuthors()));
        }
        if (metadata.getYear() != null) doc.setPublicationYear(metadata.getYear());
        if (metadata.getJournal() != null) doc.setJournal(metadata.getJournal());
        documentMapper.updateById(doc);
        log.info(
            "Extracted paper metadata for document {}: title={}, doi={}",
            doc.getId(),
            metadata.getTitle(),
            metadata.getDoi());
      }
    } catch (Exception e) {
      log.warn("Paper metadata extraction failed for document {}: {}", doc.getId(), e.getMessage());
    }
  }

  private void extractCitations(Document doc, String rawText) {
    if (citationNetworkService == null || !"pdf".equalsIgnoreCase(doc.getFileFormat())) {
      return;
    }
    try {
      int citationCount =
          citationNetworkService.extractAndSaveCitations(
              doc.getId(), rawText, doc.getKnowledgeBaseId());
      if (citationCount > 0) {
        log.info("Extracted {} citation links from document {}", citationCount, doc.getId());
      }
    } catch (Exception e) {
      log.warn("Citation extraction failed for document {}: {}", doc.getId(), e.getMessage());
    }
  }

  private void generateSummary(Document doc, String cleanedText) {
    try {
      if (chatClients == null || chatClients.isEmpty()) {
        return;
      }
      org.springframework.ai.chat.client.ChatClient chatClient =
          chatClients.getOrDefault(
              "deepseek", chatClients.values().stream().findFirst().orElse(null));
      if (chatClient != null && cleanedText.length() > 50) {
        String contextText = cleanedText.substring(0, Math.min(3000, cleanedText.length()));
        String summary =
            chatClient
                .prompt()
                .system(
                    "Summarize the document in no more than 200 Chinese characters. "
                        + "If the text is unreadable, reply: \u6682\u65e0\u6709\u6548\u6458\u8981\u3002")
                .user(contextText)
                .call()
                .content();
        if (summary != null && !summary.isBlank()) {
          doc.setSummary(summary.trim());
          documentMapper.updateById(doc);
          log.info("Generated summary for document {}", doc.getId());
        }
      }
    } catch (Exception e) {
      log.warn("Failed to generate summary for document {}: {}", doc.getId(), e.getMessage());
    }
  }

  private void extractKnowledgeGraph(Document doc, String cleanedText) {
    if (entityRelationExtractor == null || cleanedText.length() <= 100) {
      return;
    }
    try {
      int triples =
          entityRelationExtractor.extractAndSave(doc.getId(), doc.getKnowledgeBaseId(), cleanedText);
      if (triples > 0) {
        log.info("Extracted {} knowledge graph triples from document {}", triples, doc.getId());
      }
    } catch (Exception e) {
      log.warn("Knowledge graph extraction failed for document {}: {}", doc.getId(), e.getMessage());
    }
  }

  private void cleanupInactiveVersions(Document doc) {
    try {
      elasticsearchIndexService.deleteInactiveVersions(doc.getId(), doc.getActiveIngestionVersion());
    } catch (Exception e) {
      log.warn("Failed to delete stale ES entries for document {}: {}", doc.getId(), e.getMessage());
    }
    try {
      int deleted =
          documentChunkMapper.deleteInactiveVersions(doc.getId(), doc.getActiveIngestionVersion());
      if (deleted > 0) {
        log.info("Deleted {} inactive chunks for document {}", deleted, doc.getId());
      }
    } catch (Exception e) {
      log.warn("Failed to delete inactive chunks for document {}: {}", doc.getId(), e.getMessage());
    }
  }

  public void deleteDocument(Long documentId) {
    Document doc = documentMapper.selectById(documentId);
    if (doc == null) return;

    documentChunkMapper.delete(
        new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
    if (citationNetworkService != null) {
      try {
        citationNetworkService.deleteCitationsForDocument(documentId);
      } catch (Exception e) {
        log.warn("Failed to delete citation links for document {}: {}", documentId, e.getMessage());
      }
    }
    if (entityRelationExtractor != null) {
      try {
        entityRelationExtractor.cleanExisting(documentId);
      } catch (Exception e) {
        log.warn("Failed to delete KG data for document {}: {}", documentId, e.getMessage());
      }
    }
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
